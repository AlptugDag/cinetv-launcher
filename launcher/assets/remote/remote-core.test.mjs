import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  HAPTIC_PROFILES,
  resolveKeyHapticProfile,
  computeAirMouseHumPattern,
  computeScrollHumPattern,
  createHapticController,
  classifyCacheRequest,
  normalizePosterCacheKey,
  getPosterMirrorFallbacks,
  normalizeMetadataCacheKey,
  buildOfflinePosterSvg,
  synthesizeMetadataFallback
} from "./remote-core.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test("Nuanced Haptics: OK tap emits sharp pulse and long-press OK emits hold pulse", () => {
  const okTap = resolveKeyHapticProfile(23, { isHold: false });
  assert.equal(okTap.type, "ok-sharp");
  assert.deepEqual(okTap.pattern, [22, 18, 10]);

  const enterTap = resolveKeyHapticProfile(66, { isHold: false });
  assert.equal(enterTap.type, "ok-sharp");
  assert.deepEqual(enterTap.pattern, [22, 18, 10]);

  const okHold = resolveKeyHapticProfile(23, { isHold: true });
  assert.equal(okHold.type, "ok-hold");
  assert.deepEqual(okHold.pattern, [28, 30, 42]);
});

test("Nuanced Haptics: Distinct profiles for D-pad, Volume/Channel rockers, Navigation, Media, and Numpad", () => {
  assert.equal(resolveKeyHapticProfile(19).type, "dpad-step");
  assert.equal(resolveKeyHapticProfile(20, { isRepeat: true }).type, "dpad-hum");
  assert.equal(resolveKeyHapticProfile(24).type, "rocker-detent");
  assert.equal(resolveKeyHapticProfile(25, { isRepeat: true }).type, "repeat-hum");
  assert.equal(resolveKeyHapticProfile(4).type, "nav-action");
  assert.equal(resolveKeyHapticProfile(3).type, "nav-action");
  assert.equal(resolveKeyHapticProfile(85).type, "media-transport");
  assert.equal(resolveKeyHapticProfile(8).type, "numpad-tap");
});

test("Nuanced Haptics: Continuous soft hum for air mouse and scrolling with cadence throttling", () => {
  const calls = [];
  const states = [];
  const haptics = createHapticController({
    vibrate: pattern => calls.push(pattern),
    onStateChange: st => states.push(st),
    humIntervalMs: 50
  });

  // 1. Air mouse move triggers soft hum immediately
  const m1 = haptics.airMouseMove(4, 3, 1000);
  assert.equal(m1.triggered, true);
  assert.equal(m1.type, "airmouse-hum");
  assert.equal(m1.humActive, true);
  assert.deepEqual(calls[0], [4, 14, 4]);

  // 2. Subsequent move within 50ms throttle window stays in hum state without spamming vibrate
  const m2 = haptics.airMouseMove(5, 4, 1020);
  assert.equal(m2.triggered, false);
  assert.equal(m2.humActive, true);
  assert.equal(calls.length, 1);

  // 3. Fast air mouse move after throttle window emits vivid soft hum
  const m3 = haptics.airMouseMove(18, 12, 1060);
  assert.equal(m3.triggered, true);
  assert.deepEqual(calls[1], [6, 12, 5]);

  // 4. Scroll move emits scroll-hum and scroll-step
  const s1 = haptics.scrollMove(10, false, 1120);
  assert.equal(s1.triggered, true);
  assert.equal(s1.type, "scroll-hum");
  assert.deepEqual(calls[2], [5, 16, 6]);

  const s2 = haptics.scrollMove(20, true, 1135);
  assert.equal(s2.triggered, true);
  assert.equal(s2.type, "scroll-step");
  assert.deepEqual(calls[3], [8, 14, 6]);

  // 5. OK tap fires sharp pulse and stopHum immediately after does not cancel it
  haptics.okTap(1200);
  assert.deepEqual(calls[4], [22, 18, 10]);
  haptics.stopHum(1210);
  assert.equal(calls.length, 5);
});

test("Service Worker Caching Strategy: Request classification separates posters, metadata, shell, and live bypass", () => {
  assert.equal(
    classifyCacheRequest({ url: "https://image.tmdb.org/t/p/w500/abc.jpg", method: "GET", destination: "image" }),
    "poster"
  );
  assert.equal(
    classifyCacheRequest({ url: "https://images.metahub.space/poster/medium/tt0060827/img", method: "GET" }),
    "poster"
  );
  assert.equal(
    classifyCacheRequest({ url: "http://localhost:3000/api/catalog/trending?k=secret&type=movie&page=1", method: "GET" }),
    "metadata"
  );
  assert.equal(
    classifyCacheRequest({ url: "http://localhost:3000/api/catalog/details?k=secret&tmdb=680", method: "GET" }),
    "metadata"
  );
  assert.equal(
    classifyCacheRequest({ url: "https://api.themoviedb.org/3/trending/movie/week?api_key=xyz", method: "GET" }),
    "metadata"
  );
  assert.equal(
    classifyCacheRequest({ url: "http://localhost:3000/api/key?k=secret&c=23", method: "GET" }),
    "bypass"
  );
  assert.equal(
    classifyCacheRequest({ url: "http://localhost:3000/api/mouse?k=secret&dx=4&dy=2", method: "GET" }),
    "bypass"
  );
  assert.equal(
    classifyCacheRequest({ url: "http://localhost:3000/", method: "GET", mode: "navigate" }),
    "shell"
  );
  assert.equal(
    classifyCacheRequest({ url: "http://localhost:3000/windows", method: "GET", mode: "navigate" }),
    "shell"
  );
});

test("Service Worker Caching Strategy: Canonical key normalization and offline metadata synthesis", () => {
  const k1 = normalizeMetadataCacheKey("http://localhost:3000/api/catalog/trending?k=tokenA&page=1&type=movie&seed=12");
  const k2 = normalizeMetadataCacheKey("http://localhost:3000/api/catalog/trending?type=movie&k=tokenB&page=1&fresh=1");
  assert.equal(k1, k2);
  assert.equal(k1, "http://localhost:3000/api/catalog/trending?page=1&type=movie");

  const p1 = normalizePosterCacheKey("https://media.themoviedb.org/t/p/w500/xyz.jpg?v=1");
  assert.equal(p1, "https://image.tmdb.org/t/p/w500/xyz.jpg");

  const mirrors = getPosterMirrorFallbacks("https://image.tmdb.org/t/p/w500/xyz.jpg");
  assert.ok(mirrors.includes("https://media.themoviedb.org/t/p/w500/xyz.jpg"));
  assert.ok(mirrors.includes("https://image.tmdb.org/t/p/w342/xyz.jpg"));

  const sampleCatalog = [
    { tmdb: 680, imdb: "tt0110912", title: "Ucuz Roman", year: 1994, rating: 8.9, overview: "Tarantino klasiği" },
    { tmdb: 157336, imdb: "tt0816692", title: "Yıldızlararası", year: 2014, rating: 8.7, overview: "Uzay solucan deliği" }
  ];
  const det = synthesizeMetadataFallback("/api/catalog/details", { tmdb: "157336" }, sampleCatalog);
  assert.equal(det.ok, true);
  assert.equal(det.offlineCached, true);
  assert.equal(det.title, "Yıldızlararası");

  const srch = synthesizeMetadataFallback("/api/catalog/search", { q: "uzay" }, sampleCatalog);
  assert.equal(srch.ok, true);
  assert.equal(srch.results.length, 1);
  assert.equal(srch.results[0].title, "Yıldızlararası");

  const svg = buildOfflinePosterSvg("Yıldızlararası");
  assert.ok(svg.includes("<svg"));
  assert.ok(svg.includes("Yıldızlararası"));
});

test("Service Worker Runtime: Caches posters and metadata when online and serves cached responses when connection is unstable/offline", async () => {
  const swCode = fs.readFileSync(path.join(__dirname, "sw.js"), "utf8");
  const listeners = {};
  const stores = new Map();
  let networkOnline = true;

  const mockCaches = {
    async open(name) {
      if (!stores.has(name)) stores.set(name, new Map());
      const map = stores.get(name);
      return {
        async match(reqOrUrl) {
          const key = typeof reqOrUrl === "string" ? reqOrUrl : reqOrUrl.url;
          const res = map.get(key);
          return res ? res.clone() : undefined;
        },
        async put(reqOrUrl, res) {
          const key = typeof reqOrUrl === "string" ? reqOrUrl : reqOrUrl.url;
          map.set(key, res.clone());
        },
        async add(url) {
          const full = new URL(url, "http://localhost:3000/").toString();
          map.set(full, new Response("shell", { status: 200 }));
        },
        async keys() {
          return Array.from(map.keys()).map(url => ({ url }));
        },
        async delete(reqOrKey) {
          const key = typeof reqOrKey === "string" ? reqOrKey : reqOrKey.url;
          return map.delete(key);
        }
      };
    },
    async keys() {
      return Array.from(stores.keys());
    },
    async delete(name) {
      return stores.delete(name);
    }
  };

  const mockFetch = async (reqOrUrl) => {
    if (!networkOnline) {
      throw new Error("Network unstable / disconnected");
    }
    const url = typeof reqOrUrl === "string" ? reqOrUrl : reqOrUrl.url;
    if (url.includes("image.tmdb.org") || url.includes("media.themoviedb.org")) {
      return new Response("MOCK_JPEG_BYTES", { status: 200, headers: { "Content-Type": "image/jpeg" } });
    }
    if (url.includes("/api/catalog/trending")) {
      return new Response(
        JSON.stringify({
          ok: true,
          results: [
            { tmdb: 27205, title: "Başlangıç", year: 2010, rating: 8.8, poster: "https://image.tmdb.org/t/p/w500/inc.jpg", overview: "Rüya içinde rüya" }
          ]
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      );
    }
    return new Response("OK", { status: 200 });
  };

  const sandbox = {
    self: {
      location: new URL("http://localhost:3000/sw.js"),
      skipWaiting: () => Promise.resolve(),
      clients: { claim: () => Promise.resolve() },
      addEventListener(type, fn) {
        listeners[type] = fn;
      }
    },
    caches: mockCaches,
    fetch: mockFetch,
    Response,
    Request,
    URL,
    URLSearchParams,
    AbortController,
    setTimeout,
    clearTimeout,
    Promise
  };

  vm.runInNewContext(swCode, sandbox);

  // 1. Online metadata request populates cinetv-metadata-v1.0.0.52
  let pendingWaitUntil = [];
  let respondedPromise = null;
  const reqMetaOnline = new Request("http://localhost:3000/api/catalog/trending?k=tok1&type=movie&page=1");
  listeners.fetch({
    request: reqMetaOnline,
    waitUntil: p => pendingWaitUntil.push(p),
    respondWith: p => { respondedPromise = p; }
  });
  const resOnline = await respondedPromise;
  await Promise.all(pendingWaitUntil);
  const dataOnline = await resOnline.json();
  assert.equal(dataOnline.ok, true);
  assert.equal(dataOnline.results[0].title, "Başlangıç");

  // 2. Online poster request populates cinetv-posters-v1.0.0.52
  pendingWaitUntil = [];
  respondedPromise = null;
  const reqPosterOnline = new Request("https://image.tmdb.org/t/p/w500/inc.jpg");
  listeners.fetch({
    request: reqPosterOnline,
    waitUntil: p => pendingWaitUntil.push(p),
    respondWith: p => { respondedPromise = p; }
  });
  const resPosterOnline = await respondedPromise;
  await Promise.all(pendingWaitUntil);
  assert.equal(await resPosterOnline.text(), "MOCK_JPEG_BYTES");

  // 3. Simulate unstable/dropped internet connection
  networkOnline = false;

  // Metadata request with a different token still hits canonical metadata cache
  pendingWaitUntil = [];
  respondedPromise = null;
  const reqMetaOffline = new Request("http://localhost:3000/api/catalog/trending?k=tok2&type=movie&page=1");
  listeners.fetch({
    request: reqMetaOffline,
    waitUntil: p => pendingWaitUntil.push(p),
    respondWith: p => { respondedPromise = p; }
  });
  const resOffline = await respondedPromise;
  const dataOffline = await resOffline.json();
  assert.equal(dataOffline.ok, true);
  assert.equal(dataOffline.results[0].title, "Başlangıç");

  // Uncached details request when offline synthesizes movie details from cached catalog snapshot
  pendingWaitUntil = [];
  respondedPromise = null;
  const reqDetailsOffline = new Request("http://localhost:3000/api/catalog/details?k=tok2&tmdb=27205");
  listeners.fetch({
    request: reqDetailsOffline,
    waitUntil: p => pendingWaitUntil.push(p),
    respondWith: p => { respondedPromise = p; }
  });
  const resDetailsOffline = await respondedPromise;
  const dataDetailsOffline = await resDetailsOffline.json();
  assert.equal(dataDetailsOffline.ok, true);
  assert.equal(dataDetailsOffline.offlineCached, true);
  assert.equal(dataDetailsOffline.title, "Başlangıç");

  // Cached poster request when offline returns cached image bytes immediately
  pendingWaitUntil = [];
  respondedPromise = null;
  listeners.fetch({
    request: new Request("https://media.themoviedb.org/t/p/w500/inc.jpg"),
    waitUntil: p => pendingWaitUntil.push(p),
    respondWith: p => { respondedPromise = p; }
  });
  const resPosterOffline = await respondedPromise;
  assert.equal(await resPosterOffline.text(), "MOCK_JPEG_BYTES");

  // Uncached poster request when offline returns SVG placeholder instead of broken image
  pendingWaitUntil = [];
  respondedPromise = null;
  listeners.fetch({
    request: new Request("https://image.tmdb.org/t/p/w500/never_cached.jpg"),
    waitUntil: p => pendingWaitUntil.push(p),
    respondWith: p => { respondedPromise = p; }
  });
  const resUncachedPoster = await respondedPromise;
  assert.equal(resUncachedPoster.headers.get("X-CineTV-Cache"), "fallback-svg");
  assert.ok((await resUncachedPoster.text()).includes("<svg"));
});
