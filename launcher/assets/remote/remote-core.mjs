// CineTV Remote Control Haptics & Service Worker Caching Core Logic
// Pure, zero-dependency module testable in Node.js (node:test) and usable in browser/SW contexts.

export const HAPTIC_PROFILES = Object.freeze({
  "ok-sharp": Object.freeze([22, 18, 10]),
  "ok-hold": Object.freeze([28, 30, 42]),
  "airmouse-hum": Object.freeze([4, 14, 4]),
  "airmouse-hum-fast": Object.freeze([6, 12, 5]),
  "scroll-hum": Object.freeze([5, 16, 6]),
  "scroll-step": Object.freeze([8, 14, 6]),
  "repeat-hum": Object.freeze([5, 18, 5]),
  "dpad-step": Object.freeze([11]),
  "dpad-hum": Object.freeze([6, 16, 5]),
  "nav-action": Object.freeze([12, 20, 8]),
  "rocker-detent": Object.freeze([9, 14, 12]),
  "media-transport": Object.freeze([15, 18, 12]),
  "mode-toggle-on": Object.freeze([10, 22, 18]),
  "mode-toggle-off": Object.freeze([16, 20, 8]),
  "numpad-tap": Object.freeze([7]),
  "utility-tap": Object.freeze([8, 12, 6]),
  "power-arm": Object.freeze([26, 36, 26]),
  "power-off": Object.freeze([40, 45, 65]),
  "voice-start": Object.freeze([14, 24, 22])
});

/**
 * Maps an Android TV keycode and interaction context to a nuanced haptic profile.
 */
export function resolveKeyHapticProfile(keyCode, options = {}) {
  const code = Number(keyCode);
  const { isHold = false, isRepeat = false } = options;

  if (code === 23 || code === 66) {
    const type = isHold ? "ok-hold" : "ok-sharp";
    return { type, pattern: Array.from(HAPTIC_PROFILES[type]) };
  }
  if (code >= 19 && code <= 22) {
    const type = isRepeat ? "dpad-hum" : "dpad-step";
    return { type, pattern: Array.from(HAPTIC_PROFILES[type]) };
  }
  if (code === 24 || code === 25 || code === 164 || code === 166 || code === 167) {
    const type = isRepeat ? "repeat-hum" : "rocker-detent";
    return { type, pattern: Array.from(HAPTIC_PROFILES[type]) };
  }
  if (code === 85 || code === 86 || code === 87 || code === 88 || code === 89 || code === 90) {
    const type = isRepeat ? "repeat-hum" : "media-transport";
    return { type, pattern: Array.from(HAPTIC_PROFILES[type]) };
  }
  if (code === 3 || code === 4 || code === 82 || code === 165 || code === 172 || code === 178) {
    return { type: "nav-action", pattern: Array.from(HAPTIC_PROFILES["nav-action"]) };
  }
  if ((code >= 7 && code <= 16) || code === 67) {
    return { type: "numpad-tap", pattern: Array.from(HAPTIC_PROFILES["numpad-tap"]) };
  }
  return { type: "utility-tap", pattern: Array.from(HAPTIC_PROFILES["utility-tap"]) };
}

/**
 * Computes a velocity-modulated continuous soft hum pattern for the air mouse.
 */
export function computeAirMouseHumPattern(dx = 0, dy = 0) {
  const mag = Math.hypot(Number(dx) || 0, Number(dy) || 0);
  if (mag < 0.35) {
    return { type: "airmouse-hum", pattern: [3, 16, 3], intensity: "subtle" };
  }
  if (mag > 14) {
    return { type: "airmouse-hum", pattern: Array.from(HAPTIC_PROFILES["airmouse-hum-fast"]), intensity: "vivid" };
  }
  return { type: "airmouse-hum", pattern: Array.from(HAPTIC_PROFILES["airmouse-hum"]), intensity: "smooth" };
}

/**
 * Computes a continuous soft hum pattern when scrolling (two-finger scroll, touchpad drag scroll, or deck scroll).
 */
export function computeScrollHumPattern(delta = 0, stepped = false) {
  if (stepped) {
    return { type: "scroll-step", pattern: Array.from(HAPTIC_PROFILES["scroll-step"]) };
  }
  const abs = Math.abs(Number(delta) || 0);
  if (abs > 16) {
    return { type: "scroll-hum", pattern: [6, 14, 6] };
  }
  return { type: "scroll-hum", pattern: Array.from(HAPTIC_PROFILES["scroll-hum"]) };
}

/**
 * Creates a stateful haptic controller that manages discrete pulses and continuous soft hums
 * with cadence throttling so hums feel smooth and never cancel sharp OK pulses.
 */
export function createHapticController(options = {}) {
  const vibrateFn = typeof options.vibrate === "function" ? options.vibrate : () => false;
  const onStateChange = typeof options.onStateChange === "function" ? options.onStateChange : () => {};
  const humIntervalMs = Number(options.humIntervalMs) > 0 ? Number(options.humIntervalMs) : 52;

  let lastHumAt = -Infinity;
  let lastDiscreteAt = -Infinity;
  let humActive = false;
  let lastType = "idle";
  let lastPattern = [];

  function emit(type, pattern, nowMs = Date.now(), isHum = false) {
    const cleanPattern = Array.isArray(pattern) ? pattern.map(n => Math.max(1, Math.round(Number(n) || 1))) : [10];
    lastType = type;
    lastPattern = cleanPattern;
    if (isHum) {
      humActive = true;
      lastHumAt = nowMs;
    } else {
      humActive = false;
      lastDiscreteAt = nowMs;
    }
    try {
      vibrateFn(cleanPattern);
    } catch (_) {}
    onStateChange({
      type: lastType,
      pattern: lastPattern,
      humActive
    });
    return { type: lastType, pattern: lastPattern, humActive, triggered: true };
  }

  return {
    okTap(nowMs = Date.now()) {
      return emit("ok-sharp", HAPTIC_PROFILES["ok-sharp"], nowMs, false);
    },
    okHold(nowMs = Date.now()) {
      return emit("ok-hold", HAPTIC_PROFILES["ok-hold"], nowMs, false);
    },
    keyPress(keyCode, opts = {}, nowMs = Date.now()) {
      const prof = resolveKeyHapticProfile(keyCode, opts);
      if (opts && opts.isRepeat) {
        if (nowMs - lastHumAt < humIntervalMs) {
          return { type: prof.type, pattern: prof.pattern, humActive: true, triggered: false };
        }
        return emit(prof.type, prof.pattern, nowMs, true);
      }
      return emit(prof.type, prof.pattern, nowMs, false);
    },
    airMouseMove(dx, dy, nowMs = Date.now()) {
      if (nowMs - lastHumAt < humIntervalMs) {
        return { type: "airmouse-hum", pattern: lastPattern, humActive: true, triggered: false };
      }
      const hum = computeAirMouseHumPattern(dx, dy);
      return emit(hum.type, hum.pattern, nowMs, true);
    },
    scrollMove(delta, stepped = false, nowMs = Date.now()) {
      if (!stepped && nowMs - lastHumAt < humIntervalMs) {
        return { type: "scroll-hum", pattern: lastPattern, humActive: true, triggered: false };
      }
      const hum = computeScrollHumPattern(delta, stepped);
      return emit(hum.type, hum.pattern, nowMs, !stepped);
    },
    modeToggle(enabled, nowMs = Date.now()) {
      const type = enabled ? "mode-toggle-on" : "mode-toggle-off";
      return emit(type, HAPTIC_PROFILES[type], nowMs, false);
    },
    powerTap(armed, nowMs = Date.now()) {
      const type = armed ? "power-off" : "power-arm";
      return emit(type, HAPTIC_PROFILES[type], nowMs, false);
    },
    voiceTap(nowMs = Date.now()) {
      return emit("voice-start", HAPTIC_PROFILES["voice-start"], nowMs, false);
    },
    stopHum(nowMs = Date.now()) {
      const wasHum = humActive;
      humActive = false;
      // Only send a 0 cancellation if a hum was active and we did not just fire a discrete pulse
      if (wasHum && nowMs - lastDiscreteAt > 90) {
        try {
          vibrateFn(0);
        } catch (_) {}
      }
      onStateChange({
        type: lastType,
        pattern: lastPattern,
        humActive: false
      });
      return { wasHum, humActive: false };
    },
    getState() {
      return {
        type: lastType,
        pattern: lastPattern.slice(),
        humActive
      };
    }
  };
}

// ============================================================================
// Service Worker Caching Strategy Pure Helpers
// ============================================================================

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
  "/api/catalog/details",
  "/api/catalog/similar",
  "/api/catalog/person",
  "/api/watchlist",
  "/api/info",
  "/api/hello"
];

/**
 * Classifies an incoming request into 'poster', 'metadata', 'shell', or 'bypass'.
 */
export function classifyCacheRequest(reqInfo) {
  if (!reqInfo || !reqInfo.url) return "bypass";
  const method = String(reqInfo.method || "GET").toUpperCase();
  if (method !== "GET") return "bypass";

  let parsed;
  try {
    parsed = new URL(reqInfo.url, "http://localhost:3000");
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

  // 1. Movie Posters, Backdrops & Cast Photos
  if (
    host === "image.tmdb.org" ||
    host === "media.themoviedb.org" ||
    host.endsWith(".metahub.space") ||
    pathname === "/api/poster" ||
    pathname.startsWith("/api/poster/")
  ) {
    return "poster";
  }

  // 2. Movie Metadata & Catalog Endpoints (Local TV API + TMDB + Cinemeta)
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

  // 3. Static Poster/Image Assets (excluding icons which belong to shell or poster)
  if (
    reqInfo.destination === "image" ||
    /\.(jpe?g|webp|avif)(\?.*)?$/i.test(pathname)
  ) {
    return "poster";
  }

  // 4. App Shell (HTML navigation, manifest, icons, fonts, core scripts)
  if (
    reqInfo.mode === "navigate" ||
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

/**
 * Normalizes a poster URL for high-hit-rate cache lookups across sessions.
 */
export function normalizePosterCacheKey(rawUrl) {
  try {
    const u = new URL(rawUrl, "http://localhost:3000");
    // Normalize media.themoviedb.org mirror to image.tmdb.org canonical key
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

/**
 * Returns alternative mirror URLs for a TMDB poster when the primary CDN is unstable.
 */
export function getPosterMirrorFallbacks(rawUrl) {
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

/**
 * Normalizes a metadata request URL by removing ephemeral pairing/auth keys ('k', 'api_key', '_t')
 * and sorting semantic query parameters so identical catalog requests always hit the same cache entry.
 */
export function normalizeMetadataCacheKey(rawUrl) {
  try {
    const u = new URL(rawUrl, "http://localhost:3000");
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

/**
 * Generates a sleek offline SVG poster placeholder when a poster has never been cached and network is down.
 */
export function buildOfflinePosterSvg(title = "CineTV") {
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

/**
 * Synthesizes a fallback metadata response from cached catalog movies when a specific search/detail/person
 * request fails on an unstable connection and has no exact cache match yet.
 */
export function synthesizeMetadataFallback(pathname, searchParams, cachedMovies = []) {
  const list = Array.isArray(cachedMovies) ? cachedMovies : [];
  const params = searchParams instanceof URLSearchParams
    ? Object.fromEntries(searchParams.entries())
    : (searchParams || {});

  if (pathname.endsWith("/catalog/details")) {
    const tmdb = Number(params.tmdb || 0);
    const imdb = String(params.imdb || "").toLowerCase();
    const title = String(params.title || "").toLowerCase();
    const found = list.find(m =>
      (tmdb > 0 && (Number(m.tmdb) === tmdb || Number(m.tmdbId) === tmdb || Number(m.id) === tmdb)) ||
      (imdb && (String(m.imdb || m.imdbId || "").toLowerCase() === imdb)) ||
      (title && String(m.title || "").toLowerCase() === title)
    ) || list[0] || {
      tmdb: tmdb || 0,
      title: params.title || "CineTV",
      overview: "İnternet bağlantısı kararsız; önbellekteki film bilgileri gösteriliyor.",
      directors: [],
      cast: []
    };
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

  if (pathname.endsWith("/catalog/search")) {
    return null;
  }

  return {
    ok: true,
    offlineCached: true,
    results: list
  };
}

/**
 * Automated resilient background reconnection controller for CineTV Remote.
 * Manages connection states (connected, reconnecting, offline),
 * exponential backoff with jitter, battery-optimized background throttling,
 * and seamless background recovery without user intervention.
 */
export class ReconnectionController {
  constructor(options = {}) {
    this.probeFn = options.probeFn || (async () => true);
    this.onStateChange = options.onStateChange || (() => {});
    this.onRecovered = options.onRecovered || (() => {});
    this.minBackoffMs = options.minBackoffMs || 400;
    this.maxBackoffMs = options.maxBackoffMs || 8000;
    this.backgroundBackoffMs = options.backgroundBackoffMs || 15000;
    this.failureThreshold = options.failureThreshold || 1;
    this.jitterRatio = typeof options.jitterRatio === "number" ? options.jitterRatio : 0.2;

    this.isOnline = true;
    this.isReconnecting = false;
    this.consecutiveFailures = 0;
    this.currentBackoffMs = this.minBackoffMs;
    this.timer = null;
    this.probing = false;
    this.destroyed = false;
  }

  notifySuccess(metadata = {}) {
    if (this.destroyed) return;
    const wasOffline = !this.isOnline || this.isReconnecting;
    this.isOnline = true;
    this.isReconnecting = false;
    this.consecutiveFailures = 0;
    this.currentBackoffMs = this.minBackoffMs;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (wasOffline) {
      this.onStateChange({ status: "connected", isOnline: true, isReconnecting: false });
      try {
        this.onRecovered(metadata);
      } catch (_) {}
    }
  }

  notifyFailure(error = null, options = {}) {
    if (this.destroyed) return;
    this.consecutiveFailures++;
    const isFatal = options.fatal || this.consecutiveFailures >= this.failureThreshold;
    if (isFatal && (this.isOnline || !this.isReconnecting)) {
      this.isOnline = false;
      this.isReconnecting = true;
      this.onStateChange({
        status: "reconnecting",
        isOnline: false,
        isReconnecting: true,
        consecutiveFailures: this.consecutiveFailures,
        error
      });
    }
    this.scheduleProbe(false, options.isBackground);
  }

  calculateBackoff(isBackground = false) {
    if (isBackground) {
      return this.backgroundBackoffMs;
    }
    const base = Math.min(this.maxBackoffMs, this.currentBackoffMs);
    const jitter = Math.floor(Math.random() * (base * this.jitterRatio));
    return base + jitter;
  }

  scheduleProbe(immediate = false, isBackground = false) {
    if (this.destroyed) return;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    const delay = immediate ? 50 : this.calculateBackoff(isBackground);
    if (!immediate && !isBackground) {
      this.currentBackoffMs = Math.min(this.maxBackoffMs, Math.round(this.currentBackoffMs * 1.5));
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      this.triggerProbe(isBackground);
    }, delay);
  }

  async triggerProbe(isBackground = false) {
    if (this.destroyed || this.probing) return;
    this.probing = true;
    if (!this.isOnline && !this.isReconnecting) {
      this.isReconnecting = true;
      this.onStateChange({ status: "reconnecting", isOnline: false, isReconnecting: true });
    }
    try {
      const res = await this.probeFn();
      if (res) {
        this.notifySuccess(typeof res === "object" ? res : {});
      } else {
        this.notifyFailure(new Error("probe_returned_false"), { isBackground });
      }
    } catch (err) {
      this.notifyFailure(err, { isBackground });
    } finally {
      this.probing = false;
    }
  }

  destroy() {
    this.destroyed = true;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}

