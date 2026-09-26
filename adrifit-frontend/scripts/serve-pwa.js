// Serves the production build (service worker enabled: installable PWA + push notifications)
// on http://localhost:4300 with SPA fallback and an /api proxy to the backend.
//   npm run start:pwa            (build + serve)
//   PORT=4300 API_PORT=8080      (optional env vars)
// Browsers only allow service workers on https or on localhost.
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '../dist/adrifit-frontend/browser');
const PORT = Number(process.env.PORT || 4300);
const API_PORT = Number(process.env.API_PORT || 8080);
const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
  '.webmanifest': 'application/manifest+json', '.png': 'image/png', '.jpg': 'image/jpeg', '.ico': 'image/x-icon',
  '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.txt': 'text/plain',
};

if (!fs.existsSync(path.join(ROOT, 'index.html'))) {
  console.error(`No build found in ${ROOT}. Run "npm run build" first.`);
  process.exit(1);
}

http.createServer((req, res) => {
  if (req.url.startsWith('/api')) {
    const upstream = http.request(
      { host: 'localhost', port: API_PORT, path: req.url, method: req.method, headers: req.headers },
      (r) => { res.writeHead(r.statusCode, r.headers); r.pipe(res); }
    );
    upstream.on('error', () => { if (!res.headersSent) res.writeHead(502); res.end(); });
    req.pipe(upstream);
    return;
  }
  let file = path.join(ROOT, decodeURIComponent(req.url.split('?')[0]));
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    file = path.join(ROOT, 'index.html');
  }
  res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
}).listen(PORT, () => console.log(`AdriFitt PWA on http://localhost:${PORT} (API -> :${API_PORT})`));
