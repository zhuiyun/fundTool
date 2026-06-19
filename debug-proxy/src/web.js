const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');
const store = require('./store');

function createWebServer(port) {
  const htmlPath = path.join(__dirname, '../public/index.html');

  const server = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/api/traffic') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(store.entries));
      return;
    }
    if (req.method === 'POST' && req.url === '/api/clear') {
      store.clear();
      res.writeHead(200);
      res.end('ok');
      return;
    }

    // Serve the UI
    fs.readFile(htmlPath, (err, data) => {
      if (err) {
        res.writeHead(404);
        res.end('Not found');
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(data);
    });
  });

  const wss = new WebSocketServer({ server });
  const clients = new Set();

  wss.on('connection', (ws) => {
    clients.add(ws);
    ws.send(JSON.stringify({ type: 'init', data: store.entries }));
    ws.on('close', () => clients.delete(ws));
    ws.on('error', () => clients.delete(ws));
  });

  function broadcast(msg) {
    const data = JSON.stringify(msg);
    for (const client of clients) {
      if (client.readyState === 1) client.send(data);
    }
  }

  store.on('entry', entry => broadcast({ type: 'entry', data: entry }));
  store.on('update', entry => broadcast({ type: 'update', data: entry }));
  store.on('clear', () => broadcast({ type: 'clear' }));

  server.listen(port, '0.0.0.0', () => {
    console.log(`Web UI 已启动，端口 ${port}`);
  });

  return server;
}

module.exports = { createWebServer };
