import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = 3000;

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.ttf': 'font/ttf'
};

const SAMPLE_MOVIES = [
  {
    title: 'In the Mood for Love',
    originalTitle: 'Fa yeung nin wa',
    year: 2000,
    rating: 8.1,
    type: 'movie',
    tmdbId: 843,
    imdbId: 'tt0118694',
    poster: 'https://image.tmdb.org/t/p/w500/iYypPT4bhqXfq1b6EnmxvRt6b2Y.jpg',
    backdrop: 'https://image.tmdb.org/t/p/w1280/n4GJFGzsc7NinI1VeGDXIcQjtU2.jpg',
    overview: '1962 Hong Kongunda yan yana dairelere taşınan iki komşu, eşlerinin kendilerini aldattığını fark eder.',
    directors: [
      { id: 12453, name: 'Wong Kar-wai', role: 'Yönetmen', photo: 'https://image.tmdb.org/t/p/w185/w7p3K3b3x3yF1vP2N5k9R8xJ2c.jpg' }
    ],
    cast: [
      { id: 1337, name: 'Tony Leung Chiu-wai', role: 'Chow Mo-wan', photo: 'https://image.tmdb.org/t/p/w185/c7864252512.jpg' },
      { id: 1338, name: 'Maggie Cheung', role: 'Su Li-zhen', photo: 'https://image.tmdb.org/t/p/w185/maggie.jpg' }
    ]
  },
  {
    title: 'Oldboy',
    originalTitle: 'Oldeuboi',
    year: 2003,
    rating: 8.3,
    type: 'movie',
    tmdbId: 670,
    imdbId: 'tt0364569',
    poster: 'https://image.tmdb.org/t/p/w500/pWDtjs568ZfOTMbURQBYuT4Qxka.jpg',
    backdrop: 'https://image.tmdb.org/t/p/w1280/47L2A2yP6Y0F3.jpg',
    overview: 'Sebebini bilmeden 15 yıl boyunca bir odada tutsak edilen Oh Dae-su, serbest bırakıldığında intikam ve gerçeğin peşine düşer.',
    directors: [
      { id: 10099, name: 'Park Chan-wook', role: 'Yönetmen', photo: 'https://image.tmdb.org/t/p/w185/park.jpg' }
    ],
    cast: [
      { id: 6890, name: 'Choi Min-sik', role: 'Oh Dae-su', photo: 'https://image.tmdb.org/t/p/w185/choi.jpg' },
      { id: 10100, name: 'Yoo Ji-tae', role: 'Lee Woo-jin', photo: 'https://image.tmdb.org/t/p/w185/yoo.jpg' },
      { id: 10101, name: 'Kang Hye-jung', role: 'Mi-do', photo: 'https://image.tmdb.org/t/p/w185/kang.jpg' }
    ]
  },
  {
    title: 'Prisoners',
    originalTitle: 'Prisoners',
    year: 2013,
    rating: 8.1,
    type: 'movie',
    tmdbId: 146233,
    imdbId: 'tt1392214',
    poster: 'https://image.tmdb.org/t/p/w500/tuZhZ6biFMr5n9YSVuHOJnNL1uU.jpg',
    backdrop: 'https://image.tmdb.org/t/p/w1280/prisoners.jpg',
    overview: 'Küçük kızı kaçırılan çaresiz bir baba, polis soruşturması sonuçsuz kalınca adaleti kendi elleriyle sağlamaya karar verir.',
    directors: [
      { id: 137427, name: 'Denis Villeneuve', role: 'Yönetmen', photo: 'https://image.tmdb.org/t/p/w185/denis.jpg' }
    ],
    cast: [
      { id: 6968, name: 'Hugh Jackman', role: 'Keller Dover', photo: 'https://image.tmdb.org/t/p/w185/hugh.jpg' },
      { id: 131, name: 'Jake Gyllenhaal', role: 'Detective Loki', photo: 'https://image.tmdb.org/t/p/w185/jake.jpg' }
    ]
  },
  {
    title: 'Blade Runner 2049',
    originalTitle: 'Blade Runner 2049',
    year: 2017,
    rating: 8.0,
    type: 'movie',
    tmdbId: 335984,
    imdbId: 'tt1856101',
    poster: 'https://image.tmdb.org/t/p/w500/gajva2L0rPYkEWjzgFlBXCAVBE5.jpg',
    backdrop: 'https://image.tmdb.org/t/p/w1280/ilRyazdMJwN05exqhwK4tMKBYZs.jpg',
    overview: 'Genç bir Blade Runner olan K, toplumu kaosa sürükleyebilecek uzun süredir gömülü bir sırrı ortaya çıkarır.',
    directors: [
      { id: 137427, name: 'Denis Villeneuve', role: 'Yönetmen', photo: 'https://image.tmdb.org/t/p/w185/denis.jpg' }
    ],
    cast: [
      { id: 30614, name: 'Ryan Gosling', role: 'K', photo: 'https://image.tmdb.org/t/p/w185/ryan.jpg' },
      { id: 3, name: 'Harrison Ford', role: 'Rick Deckard', photo: 'https://image.tmdb.org/t/p/w185/harrison.jpg' }
    ]
  }
];

let partyState = {
  v: 1,
  mode: 'none',
  phase: 'lobby',
  hostId: '',
  you: '',
  players: [],
  chambers: [
    { title: '', owner: '', ownerName: '', poster: '', year: 0 },
    { title: '', owner: '', ownerName: '', poster: '', year: 0 },
    { title: '', owner: '', ownerName: '', poster: '', year: 0 },
    { title: '', owner: '', ownerName: '', poster: '', year: 0 },
    { title: '', owner: '', ownerName: '', poster: '', year: 0 },
    { title: '', owner: '', ownerName: '', poster: '', year: 0 }
  ],
  empty: 6,
  turnId: '',
  turnName: '',
  turnLeft: 30,
  chosenFilm: '',
  lastShot: -1
};

let uploadedFiles = [
  { name: 'Nuvio-Mod.apk', path: '/sdcard/Download/Nuvio-Mod.apk', isDir: false, size: 18450200, modified: Date.now() - 120000, ext: 'apk' },
  { name: 'CineTV-Profile.json', path: '/sdcard/Download/CineTV-Profile.json', isDir: false, size: 4210, modified: Date.now() - 360000, ext: 'json' }
];

let savedShots = [];

function sendJson(res, obj, status = 200) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*'
  });
  res.end(body);
}

const server = http.createServer((req, res) => {
  const parsedUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  let urlPath = decodeURIComponent(parsedUrl.pathname);
  const q = Object.fromEntries(parsedUrl.searchParams.entries());

  if (urlPath.startsWith('/api/')) {
    if (urlPath === '/api/ping') return sendJson(res, { ok: true });
    if (urlPath === '/api/info') {
      return sendJson(res, {
        ok: true,
        paired: true,
        token: 'preview-token',
        name: 'CineTV Salon',
        version: '2.4',
        securePort: 0,
        hasTmdb: true,
        hasAi: true,
        userName: 'Alptuğ'
      });
    }
    if (urlPath === '/api/hello') {
      return sendJson(res, {
        ok: true,
        shot: 1,
        tmdb: true,
        calibration: { supported: true },
        inputFocused: false
      });
    }
    if (urlPath === '/api/status') {
      return sendJson(res, {
        ok: true,
        sleep: 0,
        partyMode: partyState.mode,
        partyV: partyState.v
      });
    }
    if (urlPath === '/api/catalog/trending') {
      return sendJson(res, { ok: true, results: SAMPLE_MOVIES });
    }
    if (urlPath === '/api/catalog/search') {
      const query = (q.q || '').toLowerCase();
      const filtered = query
        ? SAMPLE_MOVIES.filter(m =>
            m.title.toLowerCase().includes(query) ||
            m.overview.toLowerCase().includes(query) ||
            (query.includes('baba') && query.includes('intikam') && (m.tmdbId === 146233 || m.tmdbId === 670))
          )
        : SAMPLE_MOVIES;
      return sendJson(res, {
        ok: true,
        title: q.q ? `${q.q} Seçkisi` : 'Öne Çıkanlar',
        results: filtered.length ? filtered : SAMPLE_MOVIES,
        more: false
      });
    }
    if (urlPath === '/api/catalog/details') {
      const tmdb = Number(q.tmdb || 0);
      const title = (q.title || '').toLowerCase();
      const found = SAMPLE_MOVIES.find(m => m.tmdbId === tmdb || m.title.toLowerCase() === title) || SAMPLE_MOVIES[0];
      return sendJson(res, {
        ok: true,
        tmdbId: found.tmdbId,
        imdbId: found.imdbId,
        title: found.title,
        originalTitle: found.originalTitle,
        year: found.year,
        rating: found.rating,
        type: found.type,
        poster: found.poster,
        backdrop: found.backdrop,
        overview: found.overview,
        directors: found.directors || [],
        cast: found.cast || []
      });
    }
    if (urlPath === '/api/catalog/person') {
      const personId = Number(q.id || 0);
      const personName = q.name || 'Sanatçı';
      const credits = SAMPLE_MOVIES.filter(m =>
        (m.directors || []).some(d => d.id === personId || d.name === personName) ||
        (m.cast || []).some(c => c.id === personId || c.name === personName)
      );
      return sendJson(res, {
        ok: true,
        id: personId,
        name: personName,
        results: credits.length ? credits : SAMPLE_MOVIES
      });
    }
    if (urlPath === '/api/catalog/similar') {
      return sendJson(res, { ok: true, title: q.title || 'Benzer Filmler', results: SAMPLE_MOVIES });
    }
    if (urlPath === '/api/files/list') {
      return sendJson(res, {
        ok: true,
        dir: '/sdcard/Download',
        root: '/sdcard',
        parent: '/sdcard',
        freeMb: 12480,
        totalMb: 32768,
        items: uploadedFiles,
        screenshotsDir: '/sdcard/CineTV_Screenshots'
      });
    }
    if (urlPath === '/api/files/upload' && req.method === 'POST') {
      let size = 0;
      req.on('data', chunk => { size += chunk.length; });
      req.on('end', () => {
        const name = (q.name || `upload_${Date.now()}.apk`).replace(/[/\\:*?"<>|]/g, '_');
        const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : '';
        const item = {
          name,
          path: `/sdcard/Download/${name}`,
          isDir: false,
          size: size || 1024,
          modified: Date.now(),
          ext
        };
        uploadedFiles = [item, ...uploadedFiles.filter(f => f.name !== name)];
        const installNow = q.install === '1' && ext === 'apk';
        sendJson(res, {
          ok: true,
          path: item.path,
          name: item.name,
          size: item.size,
          installing: installNow
        });
      });
      return;
    }
    if (urlPath === '/api/files/upload64') {
      const name = (q.name || `upload_${Date.now()}.apk`).replace(/[/\\:*?"<>|]/g, '_');
      const ext = name.includes('.') ? name.split('.').pop().toLowerCase() : '';
      const b64Len = (q.data || '').length;
      const chunkBytes = Math.max(0, Math.floor((b64Len * 3) / 4));
      const existing = uploadedFiles.find(f => f.name === name);
      const size = q.append === '1' && existing ? existing.size + chunkBytes : (chunkBytes || 1024);
      const item = {
        name,
        path: `/sdcard/Download/${name}`,
        isDir: false,
        size,
        modified: Date.now(),
        ext
      };
      uploadedFiles = [item, ...uploadedFiles.filter(f => f.name !== name)];
      const installNow = q.install === '1' && ext === 'apk';
      return sendJson(res, {
        ok: true,
        path: item.path,
        name: item.name,
        size: item.size,
        installing: installNow
      });
    }
    if (urlPath === '/api/files/delete') {
      const targetPath = q.path || '';
      uploadedFiles = uploadedFiles.filter(f => f.path !== targetPath && f.name !== targetPath);
      return sendJson(res, { ok: true });
    }
    if (urlPath === '/api/apk/install' || urlPath === '/api/files/open') {
      return sendJson(res, { ok: true, action: 'installing_apk', path: q.path || '' });
    }
    if (urlPath === '/api/shot/list') {
      return sendJson(res, { ok: true, dir: '/sdcard/CineTV_Screenshots', items: savedShots });
    }
    if (urlPath === '/api/shot/take') {
      const name = `CineTV-${Date.now()}.jpg`;
      const shotPath = `/sdcard/CineTV_Screenshots/${name}`;
      savedShots = [{ name, path: shotPath, size: 42100, modified: Date.now() }, ...savedShots];
      return sendJson(res, { ok: true, name, path: shotPath, jpg: '/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=' });
    }
    if (urlPath === '/api/party/join') {
      const id = q.id || 'p1';
      const name = q.name || 'Oyuncu';
      let p = partyState.players.find(x => x.id === id);
      if (!p) {
        p = { id, name, score: 0, color: '#38bdf8', host: partyState.players.length === 0, away: false };
        partyState.players.push(p);
      } else {
        p.name = name;
        p.away = false;
      }
      if (!partyState.hostId) partyState.hostId = p.id;
      partyState.v++;
      return sendJson(res, { ...partyState, you: id });
    }
    if (urlPath === '/api/party/leave') {
      const id = q.id || '';
      partyState.players = partyState.players.filter(x => x.id !== id);
      if (partyState.hostId === id) {
        partyState.hostId = partyState.players[0]?.id || '';
        if (partyState.players[0]) partyState.players[0].host = true;
      }
      if (partyState.players.length === 0) {
        partyState.mode = 'none';
        partyState.phase = 'lobby';
      }
      partyState.v++;
      return sendJson(res, { ok: true, remaining: partyState.players.length });
    }
    if (urlPath === '/api/party/open') {
      partyState.mode = q.game || 'roulette';
      partyState.phase = partyState.mode === 'roulette' ? 'load' : 'lobby';
      partyState.v++;
      return sendJson(res, { ok: true });
    }
    if (urlPath === '/api/party/close') {
      partyState.mode = 'none';
      partyState.phase = 'lobby';
      partyState.v++;
      return sendJson(res, { ok: true });
    }
    if (urlPath === '/api/party/roulette/fill') {
      const mood = (q.mood || '').trim();
      partyState.chambers = SAMPLE_MOVIES.concat(SAMPLE_MOVIES).slice(0, 6).map((m, idx) => ({
        title: mood ? `${m.title}` : m.title,
        owner: 'gamze',
        ownerName: mood ? `Ruh Hali: ${mood}` : 'CineTV',
        poster: m.poster,
        year: m.year
      }));
      partyState.empty = 0;
      partyState.v++;
      return sendJson(res, { ok: true, mood });
    }
    if (urlPath === '/api/party/state') {
      return sendJson(res, { ...partyState, you: q.id || partyState.players[0]?.id || '' });
    }
    return sendJson(res, { ok: true });
  }

  let filePath;
  if (urlPath === '/' || urlPath === '/index.html' || urlPath === '/remote' || urlPath === '/remote/') {
    filePath = path.join(__dirname, 'launcher', 'assets', 'remote', 'index.html');
  } else if (urlPath.startsWith('/static/')) {
    const assetName = urlPath.slice('/static/'.length);
    const candidate1 = path.join(__dirname, 'launcher', 'assets', 'remote', assetName);
    const candidate2 = path.join(__dirname, 'docs', 'remote', 'static', assetName);
    filePath = fs.existsSync(candidate1) ? candidate1 : candidate2;
  } else if (urlPath === '/docs' || urlPath === '/docs/') {
    filePath = path.join(__dirname, 'docs', 'index.html');
  } else if (urlPath === '/windows' || urlPath === '/windows/' || urlPath.startsWith('/windows/')) {
    const sub = urlPath.replace(/^\/windows\/?/, '');
    filePath = sub ? path.join(__dirname, 'windows', sub) : path.join(__dirname, 'windows', 'index.html');
  } else {
    const candidateRemote = path.join(__dirname, 'launcher', 'assets', 'remote', urlPath.replace(/^\/+/, ''));
    const candidateDocs = path.join(__dirname, 'docs', urlPath.replace(/^\/+/, ''));
    const candidateWindows = path.join(__dirname, 'windows', urlPath.replace(/^\/+/, ''));
    filePath = fs.existsSync(candidateRemote) ? candidateRemote : (fs.existsSync(candidateDocs) ? candidateDocs : candidateWindows);
  }

  fs.stat(filePath, (err, stats) => {
    if (!err && stats.isDirectory()) {
      filePath = path.join(filePath, 'index.html');
    }
    fs.readFile(filePath, (readErr, content) => {
      if (readErr) {
        const fallback = path.join(__dirname, 'launcher', 'assets', 'remote', 'index.html');
        fs.readFile(fallback, (fbErr, fbContent) => {
          if (fbErr) {
            res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('Not found');
            return;
          }
          res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
          res.end(fbContent);
        });
        return;
      }
      const ext = path.extname(filePath).toLowerCase();
      res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'application/octet-stream' });
      res.end(content);
    });
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`CineTV server listening on http://0.0.0.0:${PORT}`);
});
