import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const PORT = 3000;
const HOST = '0.0.0.0';

const REMOTE_DIR = '/app/applet/launcher/assets/remote';
const DOCS_DIR = '/app/applet/docs';

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
};

function resolveFile(urlPath) {
  let cleanPath = urlPath.split('?')[0].split('#')[0];
  if (cleanPath === '/' || cleanPath === '' || cleanPath === '/remote' || cleanPath === '/remote/') {
    return path.join(REMOTE_DIR, 'index.html');
  }

  // Remove leading /remote if present
  let relPath = cleanPath.startsWith('/remote/') ? cleanPath.slice('/remote/'.length) : cleanPath.slice(1);

  // Try in REMOTE_DIR first
  let target = path.join(REMOTE_DIR, relPath);
  if (fs.existsSync(target) && fs.statSync(target).isFile()) {
    return target;
  }

  // Try in DOCS_DIR
  target = path.join(DOCS_DIR, relPath);
  if (fs.existsSync(target) && fs.statSync(target).isFile()) {
    return target;
  }

  // Fallback to index.html for SPA-like routes
  return path.join(REMOTE_DIR, 'index.html');
}

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // Handle health check
  if (req.url === '/health' || req.url === '/_health') {
    res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    if (req.method === 'HEAD') {
      res.end();
    } else {
      res.end('OK\n');
    }
    return;
  }

  try {
    const filePath = resolveFile(req.url);
    if (!fs.existsSync(filePath)) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Not Found\n');
      return;
    }

    const stat = fs.statSync(filePath);
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    res.writeHead(200, {
      'Content-Type': contentType,
      'Content-Length': stat.size,
      'Cache-Control': 'no-cache',
    });

    if (req.method === 'HEAD') {
      res.end();
      return;
    }

    const readStream = fs.createReadStream(filePath);
    readStream.on('error', (err) => {
      console.error('ReadStream error:', err);
      if (!res.headersSent) {
        res.writeHead(500, { 'Content-Type': 'text/plain' });
      }
      res.end('Internal Server Error\n');
    });
    readStream.pipe(res);
  } catch (err) {
    console.error('Request error:', err);
    if (!res.headersSent) {
      res.writeHead(500, { 'Content-Type': 'text/plain' });
    }
    res.end('Internal Server Error\n');
  }
});

server.on('error', (err) => {
  console.error('Server error:', err);
});

server.listen(PORT, HOST, () => {
  console.log(`CineTV Dev Server listening on http://${HOST}:${PORT}`);
});
