const { createProxyServer, setCA } = require('./proxy');
const { createDnsServer, getLocalIP } = require('./dns');
const { createWebServer } = require('./web');
const { ensureCA } = require('./certs');

const PROXY_PORT = parseInt(process.env.PROXY_PORT || '8080');
const WEB_PORT = parseInt(process.env.WEB_PORT || '8899');
const DNS_PORT = parseInt(process.env.DNS_PORT || '53');
const ENABLE_DNS = process.argv.includes('--dns') || process.env.ENABLE_DNS === '1';

const localIP = getLocalIP();

function main() {
  const ca = ensureCA();
  setCA(ca);

  createProxyServer(PROXY_PORT);
  createWebServer(WEB_PORT);

  if (ENABLE_DNS) {
    createDnsServer({ port: DNS_PORT, localIP });
  }

  setTimeout(() => {
    const line = '─'.repeat(44);
    console.log(`\n${line}`);
    console.log(` 本机 IP：${localIP}`);
    console.log(`${line}`);
    console.log(` 📱 手机设置步骤（手机与电脑连同一 WiFi）`);
    console.log(`    1. 进入 WiFi 详情 → 代理 → 手动`);
    console.log(`       服务器: ${localIP}`);
    console.log(`       端口:   ${PROXY_PORT}`);
    console.log(`    2. 安装 CA 证书（抓 HTTPS 必须）`);
    console.log(`       证书文件：${ca.certPath}`);
    console.log(`       Android：设置 > 安全 > 加密与凭据 > 安装证书`);
    if (ENABLE_DNS) {
      console.log(`    3. WiFi → DNS 改为：${localIP}`);
    }
    console.log(`${line}`);
    console.log(` 🌐 打开浏览器查看抓包数据：`);
    console.log(`    http://localhost:${WEB_PORT}`);
    console.log(`    http://${localIP}:${WEB_PORT}`);
    console.log(`${line}\n`);
  }, 300);
}

main();
