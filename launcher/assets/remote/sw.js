// CineTV Service Worker v1.0.0.54
// Implements resilient caching for movie posters (Cache-First + Stale-While-Revalidate + Mirror Fallback)
// and movie metadata/catalog endpoints (Network-First with fast timeout + Stale-While-Revalidate + Catalog Synthesis)
// so the CineTV launcher and companion remote remain functional and responsive on unstable connections.

const SW_VERSION = "1.0.0.54";
const SHELL_CACHE = `cinetv-shell-v${SW_VERSION}`;
const METADATA_CACHE = `cinetv-metadata-v${SW_VERSION}`;
const POSTER_CACHE = `cinetv-posters-v${SW_VERSION}`;
const VALID_CACHES = [SHELL_CACHE, METADATA_CACHE, POSTER_CACHE];

const MAX_POSTER_ENTRIES = 300;
const MAX_METADATA_ENTRIES = 200;
const METADATA_TIMEOUT_MS = 2200;
const POSTER_TIMEOUT_MS = 4500;
const SHELL_TIMEOUT_MS = 2500;
const CATALOG_SNAPSHOT_URL = "https://cinetv.local/__catalog_snapshot__";

const SHELL_PRECACHE_URLS = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./remote-core.mjs",
  "./icon-192.png?v=20",
  "./icon.png?v=20",
  "./apple-touch-icon.png?v=20",
  "/windows"
];

const LIVE_BYPASS_PATHS = [
  "/api/key",
  "/api/mouse",
  "/api/text",
  "/api/power",
  "/api/sleep",
  "/api/open",
  "/api/tv/",
  "/api/browse/",
  "/api/shot",
  "/api/pair/",
  "/api/party/",
  "/api/files/upload",
  "/api/files/upload64",
  "/api/files/delete",
  "/api/files/rename",
  "/api/files/mkdir",
  "/api/wallpaper",
  "/api/apk/install"
];

const METADATA_PATH_PREFIXES = [
  "/api/catalog/trending",
  "/api/catalog/search",
  "/api/catalog/details",
  "/api/catalog/similar",
  "/api/catalog/person",
  "/api/watchlist",
  "/api/info",
  "/api/hello"
];

function classifyRequest(request) {
  if (!request || request.method !== "GET") return "bypass";
  let parsed;
  try {
    parsed = new URL(request.url);
  } catch (_) {
    return "bypass";
  }

  const host = parsed.hostname.toLowerCase();
  const pathname = parsed.pathname;

  for (const prefix of LIVE_BYPASS_PATHS) {
    if (pathname === prefix || pathname.startsWith(prefix)) {
      return "bypass";
    }
  }

  if (
    host === "image.tmdb.org" ||
    host === "media.themoviedb.org" ||
    host.endsWith(".metahub.space") ||
    pathname === "/api/poster" ||
    pathname.startsWith("/api/poster/")
  ) {
    return "poster";
  }

  for (const metaPrefix of METADATA_PATH_PREFIXES) {
    if (pathname === metaPrefix || pathname.startsWith(metaPrefix + "/")) {
      return "metadata";
    }
  }

  if (
    host === "api.themoviedb.org" ||
    host === "v3-cinemeta.strem.io" ||
    host === "cinemeta-catalogs.strem.io"
  ) {
    return "metadata";
  }

  if (
    request.destination === "image" ||
    /\.(jpe?g|webp|avif)(\?.*)?$/i.test(pathname)
  ) {
    return "poster";
  }

  if (
    request.mode === "navigate" ||
    pathname === "/" ||
    pathname === "/index.html" ||
    pathname === "/remote" ||
    pathname === "/remote/" ||
    pathname === "/windows" ||
    pathname === "/windows/" ||
    pathname === "/windows/index.html" ||
    pathname.endsWith("/manifest.webmanifest") ||
    pathname.endsWith("/remote-core.mjs") ||
    /\.(png|svg|ico|css|woff2?)(\?.*)?$/i.test(pathname)
  ) {
    return "shell";
  }

  return "bypass";
}

function normalizePosterCacheKey(rawUrl) {
  try {
    const u = new URL(rawUrl);
    if (u.hostname === "media.themoviedb.org" && u.pathname.startsWith("/t/p/")) {
      u.hostname = "image.tmdb.org";
    }
    u.searchParams.delete("_t");
    u.searchParams.delete("v");
    return u.toString();
  } catch (_) {
    return String(rawUrl || "");
  }
}

function getPosterMirrorFallbacks(rawUrl) {
  const out = [];
  const s = String(rawUrl || "");
  if (!s) return out;
  if (s.includes("image.tmdb.org/t/p/")) {
    out.push(s.replace("image.tmdb.org/t/p/", "media.themoviedb.org/t/p/"));
    if (s.includes("/w500/")) {
      out.push(s.replace("/w500/", "/w342/"));
      out.push(s.replace("image.tmdb.org/t/p/w500/", "media.themoviedb.org/t/p/w342/"));
    }
  } else if (s.includes("media.themoviedb.org/t/p/")) {
    out.push(s.replace("media.themoviedb.org/t/p/", "image.tmdb.org/t/p/"));
    if (s.includes("/w500/")) {
      out.push(s.replace("/w500/", "/w342/"));
    }
  }
  return out;
}

function normalizeMetadataCacheKey(rawUrl) {
  try {
    const u = new URL(rawUrl);
    const kept = [];
    for (const [key, val] of u.searchParams.entries()) {
      if (key === "k" || key === "api_key" || key === "_t" || key === "fresh" || key === "seed") continue;
      kept.push([key, val]);
    }
    kept.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : a[1].localeCompare(b[1])));
    const canonicalSearch = new URLSearchParams(kept).toString();
    return `${u.origin}${u.pathname}${canonicalSearch ? "?" + canonicalSearch : ""}`;
  } catch (_) {
    return String(rawUrl || "");
  }
}

function buildOfflinePosterSvg(title = "CineTV") {
  const safe = String(title || "CineTV")
    .replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]))
    .slice(0, 36);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 600" width="400" height="600">
    <defs>
      <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#111526"/>
        <stop offset="55%" stop-color="#090b14"/>
        <stop offset="100%" stop-color="#05060b"/>
      </linearGradient>
    </defs>
    <rect width="400" height="600" fill="url(#bg)"/>
    <rect x="18" y="18" width="364" height="564" rx="20" fill="none" stroke="rgba(255,255,255,0.14)" stroke-width="2"/>
    <circle cx="200" cy="240" r="54" fill="rgba(139,92,246,0.14)" stroke="rgba(196,181,253,0.35)" stroke-width="2"/>
    <polygon points="188,216 188,264 226,240" fill="rgba(248,250,252,0.78)"/>
    <text x="200" y="345" text-anchor="middle" fill="#f8fafc" font-family="Poppins,system-ui,sans-serif" font-size="20" font-weight="700">${safe}</text>
    <text x="200" y="378" text-anchor="middle" fill="rgba(226,232,240,0.55)" font-family="Poppins,system-ui,sans-serif" font-size="13" font-weight="600">CEVRIMDISI ONBELLEK</text>
  </svg>`;
}

async function trimCache(cacheName, maxEntries) {
  try {
    const cache = await caches.open(cacheName);
    const keys = await cache.keys();
    if (keys.length <= maxEntries) return;
    const excess = keys.length - maxEntries;
    for (let i = 0; i < excess; i++) {
      if (keys[i].url === CATALOG_SNAPSHOT_URL) continue;
      await cache.delete(keys[i]);
    }
  } catch (_) {}
}

function fetchWithTimeout(requestOrUrl, timeoutMs, fetchOpts = {}) {
  return new Promise((resolve, reject) => {
    const ctl = typeof AbortController !== "undefined" ? new AbortController() : null;
    const timer = setTimeout(() => {
      if (ctl) {
        try { ctl.abort(); } catch (_) {}
      }
      reject(new Error("timeout"));
    }, timeoutMs);

    const opts = ctl ? Object.assign({}, fetchOpts, { signal: ctl.signal }) : fetchOpts;
    fetch(requestOrUrl, opts)
      .then(res => {
        clearTimeout(timer);
        resolve(res);
      })
      .catch(err => {
        clearTimeout(timer);
        reject(err);
      });
  });
}

async function fetchPosterWithMirrors(url) {
  const candidates = [url, ...getPosterMirrorFallbacks(url)];
  for (const candidate of candidates) {
    try {
      const isCrossOrigin = !candidate.startsWith(self.location.origin);
      const res = await fetchWithTimeout(
        candidate,
        POSTER_TIMEOUT_MS,
        isCrossOrigin ? { mode: "no-cors", credentials: "omit" } : { credentials: "same-origin" }
      );
      if (res && (res.ok || res.type === "opaque")) {
        return res;
      }
    } catch (_) {}
  }
  throw new Error("poster-unreachable");
}

async function handlePosterRequest(request, event) {
  const cacheKey = normalizePosterCacheKey(request.url);
  const cache = await caches.open(POSTER_CACHE);
  const cached = await cache.match(cacheKey);

  if (cached) {
    // Background Stale-While-Revalidate refresh so cached posters stay fresh without blocking UI
    event.waitUntil(
      fetchPosterWithMirrors(request.url)
        .then(fresh => {
          if (fresh && (fresh.ok || fresh.type === "opaque")) {
            return cache.put(cacheKey, fresh.clone());
          }
        })
        .catch(() => {})
    );
    return cached;
  }

  try {
    const fresh = await fetchPosterWithMirrors(request.url);
    if (fresh && (fresh.ok || fresh.type === "opaque")) {
      event.waitUntil(
        cache.put(cacheKey, fresh.clone()).then(() => trimCache(POSTER_CACHE, MAX_POSTER_ENTRIES))
      );
      return fresh;
    }
  } catch (_) {}

  const svg = buildOfflinePosterSvg("CineTV");
  return new Response(svg, {
    status: 200,
    headers: {
      "Content-Type": "image/svg+xml; charset=utf-8",
      "Cache-Control": "no-store",
      "X-CineTV-Cache": "fallback-svg"
    }
  });
}

async function updateCatalogSnapshot(cache, payload) {
  try {
    const items = Array.isArray(payload?.results)
      ? payload.results
      : (Array.isArray(payload?.items) ? payload.items : []);
    if (!items.length) return;

    let existing = [];
    const snapRes = await cache.match(CATALOG_SNAPSHOT_URL);
    if (snapRes) {
      try {
        existing = await snapRes.json();
      } catch (_) {}
    }
    const mergedMap = new Map();
    for (const m of [...items, ...(Array.isArray(existing) ? existing : [])]) {
      if (!m || !m.title) continue;
      const key = `${m.type || "movie"}:${m.tmdb || m.tmdbId || m.id || m.imdb || m.imdbId || m.title}`;
      if (!mergedMap.has(key)) mergedMap.set(key, m);
      if (mergedMap.size >= 120) break;
    }
    const snapshot = Array.from(mergedMap.values());
    await cache.put(
      CATALOG_SNAPSHOT_URL,
      new Response(JSON.stringify(snapshot), {
        status: 200,
        headers: { "Content-Type": "application/json; charset=utf-8" }
      })
    );
  } catch (_) {}
}

async function synthesizeFromSnapshot(cache, rawUrl) {
  try {
    const u = new URL(rawUrl);
    const snapRes = await cache.match(CATALOG_SNAPSHOT_URL);
    const list = snapRes ? await snapRes.json() : [];
    const movies = Array.isArray(list) ? list : [];
    const params = Object.fromEntries(u.searchParams.entries());

    if (u.pathname.endsWith("/catalog/details")) {
      const tmdb = Number(params.tmdb || 0);
      const imdb = String(params.imdb || "").toLowerCase();
      const title = String(params.title || "").toLowerCase();
      const found = movies.find(m =>
        (tmdb > 0 && (Number(m.tmdb) === tmdb || Number(m.tmdbId) === tmdb || Number(m.id) === tmdb)) ||
        (imdb && String(m.imdb || m.imdbId || "").toLowerCase() === imdb) ||
        (title && String(m.title || "").toLowerCase() === title)
      ) || movies[0];
      if (!found) return null;
      return {
        ok: true,
        offlineCached: true,
        tmdb: found.tmdb || found.tmdbId || found.id || tmdb,
        imdb: found.imdb || found.imdbId || imdb,
        title: found.title || params.title || "",
        year: found.year || 2024,
        rating: found.rating || 8.0,
        type: found.type || params.type || "movie",
        poster: found.poster || "",
        backdrop: found.backdrop || "",
        overview: found.overview || "",
        directors: Array.isArray(found.directors) ? found.directors : [],
        cast: Array.isArray(found.cast) ? found.cast : []
      };
    }

    if (u.pathname.endsWith("/catalog/search")) {
      if (!movies.length) return null;
      const q = String(params.q || "").toLowerCase().trim();
      const filtered = q
        ? movies.filter(m =>
            String(m.title || "").toLowerCase().includes(q) ||
            String(m.originalTitle || m.original || "").toLowerCase().includes(q) ||
            String(m.overview || "").toLowerCase().includes(q)
          )
        : movies;
      return {
        ok: true,
        offlineCached: true,
        title: params.q ? `${params.q} (Önbellek)` : "Önbellekteki Seçki",
        results: filtered.length ? filtered : movies,
        more: false
      };
    }

    if (
      u.pathname.endsWith("/catalog/trending") ||
      u.pathname.endsWith("/catalog/similar") ||
      u.pathname.endsWith("/catalog/person")
    ) {
      if (!movies.length) return null;
      return {
        ok: true,
        offlineCached: true,
        results: movies
      };
    }
  } catch (_) {}
  return null;
}

async function storeMetadataResponse(cache, cacheKey, response) {
  try {
    const text = await response.clone().text();
    const parsed = JSON.parse(text);
    if (parsed && parsed.error) return;
    const stored = new Response(text, {
      status: 200,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "X-CineTV-Cache": "hit-fallback",
        "X-CineTV-Cached-At": String(Date.now())
      }
    });
    await cache.put(cacheKey, stored);
    await updateCatalogSnapshot(cache, parsed);
    await trimCache(METADATA_CACHE, MAX_METADATA_ENTRIES);
  } catch (_) {}
}

async function handleMetadataRequest(request, event) {
  const cacheKey = normalizeMetadataCacheKey(request.url);
  const cache = await caches.open(METADATA_CACHE);

  // Start network request so even if unstable timeout fires, it can finish and update cache in background
  const networkPromise = fetch(request.clone()).then(res => {
    if (res && res.ok) {
      event.waitUntil(storeMetadataResponse(cache, cacheKey, res.clone()));
    }
    return res;
  });

  try {
    const timeoutPromise = new Promise((_, reject) =>
      setTimeout(() => reject(new Error("metadata-timeout")), METADATA_TIMEOUT_MS)
    );
    const netRes = await Promise.race([networkPromise, timeoutPromise]);
    if (netRes && netRes.ok) {
      return netRes;
    }
  } catch (_) {
    // Keep the background network promise alive for Stale-While-Revalidate update
    event.waitUntil(networkPromise.catch(() => {}));
  }

  // Fallback 1: Exact normalized metadata cache match
  const cached = await cache.match(cacheKey);
  if (cached) {
    return cached;
  }

  // Fallback 2: Synthesize from cached catalog snapshot so UI stays interactive
  const synthesized = await synthesizeFromSnapshot(cache, request.url);
  if (synthesized) {
    return new Response(JSON.stringify(synthesized), {
      status: 200,
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        "X-CineTV-Cache": "synthesized-fallback"
      }
    });
  }

  // Final attempt: wait for original network promise if no cache existed yet
  return networkPromise;
}

async function handleShellRequest(request, event) {
  const cache = await caches.open(SHELL_CACHE);
  try {
    const netRes = await fetchWithTimeout(request, SHELL_TIMEOUT_MS);
    if (netRes && netRes.ok) {
      event.waitUntil(cache.put(request, netRes.clone()).catch(() => {}));
      return netRes;
    }
  } catch (_) {}

  const directMatch = await cache.match(request, { ignoreSearch: true });
  if (directMatch) return directMatch;

  if (request.mode === "navigate") {
    const url = new URL(request.url);
    if (url.pathname.startsWith("/windows")) {
      const winFallback = await cache.match("/windows");
      if (winFallback) return winFallback;
    }
    const indexFallback = (await cache.match("./index.html")) || (await cache.match("./"));
    if (indexFallback) return indexFallback;
  }

  return fetch(request);
}

self.addEventListener("install", event => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then(cache => Promise.allSettled(SHELL_PRECACHE_URLS.map(u => cache.add(u))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches
      .keys()
      .then(keys =>
        Promise.all(
          keys
            .filter(key => !VALID_CACHES.includes(key))
            .map(oldKey => caches.delete(oldKey))
        )
      )
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", event => {
  const kind = classifyRequest(event.request);
  if (kind === "bypass") return;

  if (kind === "poster") {
    event.respondWith(handlePosterRequest(event.request, event));
    return;
  }

  if (kind === "metadata") {
    event.respondWith(handleMetadataRequest(event.request, event));
    return;
  }

  if (kind === "shell") {
    event.respondWith(handleShellRequest(event.request, event));
  }
});

self.addEventListener("message", event => {
  const data = event.data || {};
  if (data.type === "SKIP_WAITING") {
    self.skipWaiting();
    return;
  }

  if (data.type === "CACHE_METADATA" && data.url && data.payload) {
    event.waitUntil(
      (async () => {
        const metaCache = await caches.open(METADATA_CACHE);
        const key = normalizeMetadataCacheKey(data.url);
        const body = JSON.stringify(data.payload);
        await metaCache.put(
          key,
          new Response(body, {
            status: 200,
            headers: {
              "Content-Type": "application/json; charset=utf-8",
              "X-CineTV-Cache": "client-seeded",
              "X-CineTV-Cached-At": String(Date.now())
            }
          })
        );
        await updateCatalogSnapshot(metaCache, data.payload);
        await trimCache(METADATA_CACHE, MAX_METADATA_ENTRIES);
      })()
    );
    return;
  }

  if (data.type === "WARM_POSTERS" && Array.isArray(data.urls)) {
    event.waitUntil(
      (async () => {
        const posterCache = await caches.open(POSTER_CACHE);
        const uniqueUrls = Array.from(new Set(data.urls.filter(Boolean))).slice(0, 36);
        for (const rawUrl of uniqueUrls) {
          try {
            const key = normalizePosterCacheKey(rawUrl);
            const exists = await posterCache.match(key);
            if (!exists) {
              const res = await fetchPosterWithMirrors(rawUrl);
              if (res && (res.ok || res.type === "opaque")) {
                await posterCache.put(key, res);
              }
            }
          } catch (_) {}
        }
        await trimCache(POSTER_CACHE, MAX_POSTER_ENTRIES);
      })()
    );
    return;
  }

  if (data.type === "GET_CACHE_STATS") {
    event.waitUntil(
      (async () => {
        const [shellC, metaC, posterC] = await Promise.all([
          caches.open(SHELL_CACHE),
          caches.open(METADATA_CACHE),
          caches.open(POSTER_CACHE)
        ]);
        const [shellKeys, metaKeys, posterKeys] = await Promise.all([
          shellC.keys(),
          metaC.keys(),
          posterC.keys()
        ]);
        const stats = {
          ok: true,
          version: SW_VERSION,
          caches: {
            shell: shellKeys.length,
            metadata: metaKeys.length,
            posters: posterKeys.length
          }
        };
        if (event.ports && event.ports[0]) {
          event.ports[0].postMessage(stats);
        } else if (event.source && event.source.postMessage) {
          event.source.postMessage({ type: "CACHE_STATS", ...stats });
        }
      })()
    );
  }
});
