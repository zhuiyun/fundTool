const http = require('http');
const https = require('https');
const tls = require('tls');
const { URL } = require('url');
const store = require('./store');
const { getCertForHost } = require('./certs');

let ca;

function setCA(caData) {
  ca = caData;
}

function tryDecode(buf) {
  if (!buf || buf.length === 0) return '';
  try {
    return buf.toString('utf8');
  } catch {
    return `<binary ${buf.length} bytes>`;
  }
}

function handleRequest(req, res, isHttps, overrideHost) {
  const proto = isHttps ? 'https' : 'http';
  const host = overrideHost || req.headers.host || 'unknown';
  const rawUrl = req.url.startsWith('http') ? req.url : `${proto}://${host}${req.url}`;

  let parsedUrl;
  try {
    parsedUrl = new URL(rawUrl);
  } catch {
    res.writeHead(400);
    res.end('Bad Request');
    return;
  }

  const reqChunks = [];
  req.on('data', chunk => reqChunks.push(chunk));
  req.on('end', () => {
    const reqBody = Buffer.concat(reqChunks);

    const entry = store.add({
      method: req.method,
      url: rawUrl,
      host: parsedUrl.hostname,
      path: parsedUrl.pathname + parsedUrl.search,
      protocol: proto,
      reqHeaders: { ...req.headers },
      reqBody: tryDecode(reqBody),
      status: null,
      resHeaders: null,
      resBody: '',
      duration: null,
    });

    const startTime = Date.now();

    const headers = { ...req.headers };
    delete headers['proxy-connection'];

    const options = {
      hostname: parsedUrl.hostname,
      port: parsedUrl.port || (isHttps ? 443 : 80),
      path: parsedUrl.pathname + parsedUrl.search,
      method: req.method,
      headers,
      rejectUnauthorized: false,
    };

    const lib = isHttps ? https : http;
    const proxyReq = lib.request(options, (proxyRes) => {
      const resChunks = [];
      proxyRes.on('data', c => resChunks.push(c));
      proxyRes.on('end', () => {
        const resBody = Buffer.concat(resChunks);
        store.update(entry.id, {
          status: proxyRes.statusCode,
          resHeaders: proxyRes.headers,
          resBody: tryDecode(resBody),
          duration: Date.now() - startTime,
        });

        // Remove hop-by-hop headers before forwarding
        const resHeaders = { ...proxyRes.headers };
        delete resHeaders['transfer-encoding'];

        res.writeHead(proxyRes.statusCode, resHeaders);
        res.end(resBody);
      });
    });

    proxyReq.on('error', (err) => {
      store.update(entry.id, {
        status: 0,
        resBody: `Error: ${err.message}`,
        duration: Date.now() - startTime,
      });
      if (!res.headersSent) {
        res.writeHead(502);
        res.end(`Proxy Error: ${err.message}`);
      }
    });

    proxyReq.end(reqBody);
  });
}

function createProxyServer(port) {
  const server = http.createServer((req, res) => {
    handleRequest(req, res, false, null);
  });

  server.on('connect', (req, clientSocket, head) => {
    const [hostname] = req.url.split(':');

    let certData;
    try {
      certData = getCertForHost(hostname, ca);
    } catch (err) {
      clientSocket.write('HTTP/1.1 500 Internal Server Error\r\n\r\n');
      clientSocket.destroy();
      return;
    }

    clientSocket.write('HTTP/1.1 200 Connection Established\r\nProxy-agent: mobile-debug-proxy\r\n\r\n');

    const tlsSocket = new tls.TLSSocket(clientSocket, {
      isServer: true,
      key: certData.key,
      cert: certData.cert,
    });

    if (head && head.length > 0) tlsSocket.unshift(head);

    // Feed the decrypted TLS stream into a fresh HTTP parser
    const innerServer = http.createServer((innerReq, innerRes) => {
      if (!innerReq.headers.host) innerReq.headers.host = hostname;
      handleRequest(innerReq, innerRes, true, hostname);
    });

    innerServer.emit('connection', tlsSocket);

    tlsSocket.on('error', () => {});
    clientSocket.on('error', () => {});
  });

  server.on('error', (err) => console.error('代理错误:', err.message));

  server.listen(port, '0.0.0.0', () => {
    console.log(`HTTP/HTTPS 代理已启动，端口 ${port}`);
  });

  return server;
}

module.exports = { createProxyServer, setCA };
