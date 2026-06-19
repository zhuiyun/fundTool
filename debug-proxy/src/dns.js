const dgram = require('dgram');
const dns = require('dns');
const os = require('os');

function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const iface of Object.values(interfaces)) {
    for (const addr of iface) {
      if (addr.family === 'IPv4' && !addr.internal) return addr.address;
    }
  }
  return '127.0.0.1';
}

// Minimal DNS packet parser/builder for A record queries
function parseDnsQuestion(buf) {
  let offset = 12; // skip header
  let name = '';
  while (offset < buf.length) {
    const len = buf[offset++];
    if (len === 0) break;
    if (name) name += '.';
    name += buf.slice(offset, offset + len).toString('ascii');
    offset += len;
  }
  const type = buf.readUInt16BE(offset);
  return { name: name.toLowerCase(), type, questionEnd: offset + 4 };
}

function buildARecord(request, ip, ttl = 10) {
  const response = Buffer.from(request);
  // Set response flags: QR=1, AA=1, RD=1, RA=1
  response[2] = 0x81;
  response[3] = 0x80;
  // Answer count = 1
  response[6] = 0x00;
  response[7] = 0x01;

  const parts = ip.split('.').map(Number);
  const answer = Buffer.alloc(16);
  answer.writeUInt16BE(0xc00c, 0); // pointer to question name
  answer.writeUInt16BE(1, 2);      // type A
  answer.writeUInt16BE(1, 4);      // class IN
  answer.writeUInt32BE(ttl, 6);    // TTL
  answer.writeUInt16BE(4, 10);     // rdlength
  answer[12] = parts[0];
  answer[13] = parts[1];
  answer[14] = parts[2];
  answer[15] = parts[3];

  return Buffer.concat([response, answer]);
}

function buildNxDomain(request) {
  const response = Buffer.from(request);
  response[2] = 0x81;
  response[3] = 0x83; // NXDOMAIN
  return response;
}

function createDnsServer(options = {}) {
  const {
    port = 53,
    upstream = '8.8.8.8',
    redirectDomains = [],
    redirectAll = false,
    localIP = getLocalIP(),
  } = options;

  const server = dgram.createSocket('udp4');

  server.on('message', (msg, rinfo) => {
    let question;
    try {
      question = parseDnsQuestion(msg);
    } catch {
      return;
    }

    const shouldRedirect =
      question.type === 1 && // A record
      (redirectAll || redirectDomains.some(d => question.name === d || question.name.endsWith('.' + d)));

    if (shouldRedirect) {
      const response = buildARecord(msg, localIP);
      server.send(response, rinfo.port, rinfo.address);
      return;
    }

    // Forward to upstream DNS
    const client = dgram.createSocket('udp4');
    const timeout = setTimeout(() => {
      client.close();
      const nx = buildNxDomain(msg);
      server.send(nx, rinfo.port, rinfo.address);
    }, 3000);

    client.once('message', (res) => {
      clearTimeout(timeout);
      client.close();
      server.send(res, rinfo.port, rinfo.address);
    });

    client.send(msg, 53, upstream, (err) => {
      if (err) {
        clearTimeout(timeout);
        client.close();
      }
    });
  });

  server.on('error', (err) => {
    if (err.code === 'EACCES') {
      console.warn('DNS 代理：端口 53 需要 root 权限，已跳过。可用 sudo node src/index.js --dns 启用');
    } else {
      console.error('DNS 代理错误:', err.message);
    }
  });

  server.bind(port, '0.0.0.0', () => {
    console.log(`DNS 代理已启动，端口 ${port}（上游: ${upstream}）`);
  });

  return server;
}

module.exports = { createDnsServer, getLocalIP };
