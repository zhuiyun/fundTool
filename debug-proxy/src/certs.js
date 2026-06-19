const forge = require('node-forge');
const fs = require('fs');
const path = require('path');

const CERTS_DIR = path.join(__dirname, '../.certs');
const CA_KEY_PATH = path.join(CERTS_DIR, 'ca.key.pem');
const CA_CERT_PATH = path.join(CERTS_DIR, 'ca.crt.pem');

function ensureCA() {
  if (!fs.existsSync(CERTS_DIR)) fs.mkdirSync(CERTS_DIR, { recursive: true });

  if (fs.existsSync(CA_KEY_PATH) && fs.existsSync(CA_CERT_PATH)) {
    return {
      key: fs.readFileSync(CA_KEY_PATH, 'utf8'),
      cert: fs.readFileSync(CA_CERT_PATH, 'utf8'),
      certPath: CA_CERT_PATH,
    };
  }

  console.log('首次运行：生成 CA 根证书...');
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = '01';
  cert.validity.notBefore = new Date();
  cert.validity.notAfter = new Date(Date.now() + 10 * 365 * 86400000);

  const attrs = [
    { name: 'commonName', value: 'MobileDebugProxy CA' },
    { name: 'organizationName', value: 'MobileDebugProxy' },
  ];
  cert.setSubject(attrs);
  cert.setIssuer(attrs);
  cert.setExtensions([
    { name: 'basicConstraints', cA: true, critical: true },
    { name: 'keyUsage', keyCertSign: true, cRLSign: true, critical: true },
    { name: 'subjectKeyIdentifier' },
  ]);
  cert.sign(keys.privateKey, forge.md.sha256.create());

  const keyPem = forge.pki.privateKeyToPem(keys.privateKey);
  const certPem = forge.pki.certificateToPem(cert);
  fs.writeFileSync(CA_KEY_PATH, keyPem);
  fs.writeFileSync(CA_CERT_PATH, certPem);

  return { key: keyPem, cert: certPem, certPath: CA_CERT_PATH };
}

const domainCache = new Map();

function getCertForHost(hostname, ca) {
  const host = hostname.split(':')[0];
  if (domainCache.has(host)) return domainCache.get(host);

  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = Date.now().toString(16);
  cert.validity.notBefore = new Date();
  cert.validity.notAfter = new Date(Date.now() + 365 * 86400000);

  const caKey = forge.pki.privateKeyFromPem(ca.key);
  const caCert = forge.pki.certificateFromPem(ca.cert);

  cert.setSubject([{ name: 'commonName', value: host }]);
  cert.setIssuer(caCert.subject.attributes);
  cert.setExtensions([
    { name: 'basicConstraints', cA: false },
    { name: 'subjectAltName', altNames: [{ type: 2, value: host }] },
  ]);
  cert.sign(caKey, forge.md.sha256.create());

  const result = {
    key: forge.pki.privateKeyToPem(keys.privateKey),
    cert: forge.pki.certificateToPem(cert),
  };
  domainCache.set(host, result);
  return result;
}

module.exports = { ensureCA, getCertForHost };
