package com.cinetv.launcher.trailer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.OkHttpClient;

/**
 * Singleton disk cache and low-priority stream prefetcher for trailers.
 * Caches segments into SimpleCache so repeated loops and returns hit local storage instantly.
 */
public final class TrailerCache {
    private static final String TAG = "CineTvTrailerCache";
    private static final long MAX_CACHE_BYTES = 100L * 1024 * 1024; // 100 MB LRU disk cache
    private static final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor();

    private static volatile SimpleCache instance;
    private static volatile CacheDataSource.Factory cacheDataSourceFactory;
    private static volatile Future<?> activePrefetch;
    private static volatile String activePrefetchKey;

    private TrailerCache() {}

    public static SimpleCache get(Context ctx) {
        if (instance == null) {
            synchronized (TrailerCache.class) {
                if (instance == null) {
                    Context app = ctx.getApplicationContext();
                    File cacheDir = new File(app.getCacheDir(), "trailer_cache");
                    LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES);
                    StandaloneDatabaseProvider dbProvider = new StandaloneDatabaseProvider(app);
                    instance = new SimpleCache(cacheDir, evictor, dbProvider);
                }
            }
        }
        return instance;
    }

    public static DataSource.Factory getDataSourceFactory(Context ctx, OkHttpClient client) {
        if (cacheDataSourceFactory == null) {
            synchronized (TrailerCache.class) {
                if (cacheDataSourceFactory == null) {
                    SimpleCache cache = get(ctx);
                    OkHttpDataSource.Factory upstream = new OkHttpDataSource.Factory(client)
                            .setUserAgent(OkHttpDownloader.USER_AGENT);
                    cacheDataSourceFactory = new CacheDataSource.Factory()
                            .setCache(cache)
                            .setUpstreamDataSourceFactory(upstream)
                            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
                }
            }
        }
        return cacheDataSourceFactory;
    }

    /**
     * Prefetches initial bytes (e.g. 1.5 - 2 MB) of a stream into SimpleCache in background.
     * Cancels any previously active prefetch if a new key is requested.
     */
    public static synchronized void prefetch(Context ctx, String videoId, int itag, Uri uri, long bytesToPrefetch) {
        final String cacheKey = "yt_" + videoId + "_" + itag;
        if (cacheKey.equals(activePrefetchKey)) return;

        if (activePrefetch != null && !activePrefetch.isDone()) {
            activePrefetch.cancel(true);
        }
        activePrefetchKey = cacheKey;

        final Context app = ctx.getApplicationContext();
        activePrefetch = prefetchExecutor.submit(() -> {
            try {
                SimpleCache cache = get(app);
                // Check if already cached
                if (cache.isCached(cacheKey, 0, Math.min(bytesToPrefetch, 512 * 1024))) {
                    return;
                }
                DataSource.Factory dsFactory = getDataSourceFactory(app, OkHttpDownloader.createClient());
                CacheDataSource cacheDs = (CacheDataSource) dsFactory.createDataSource();
                DataSpec spec = new DataSpec.Builder()
                        .setUri(uri)
                        .setKey(cacheKey)
                        .setLength(bytesToPrefetch)
                        .build();

                CacheWriter writer = new CacheWriter(cacheDs, spec, null, null);
                writer.cache();
                Log.d(TAG, "Prefetched " + bytesToPrefetch + " bytes for " + cacheKey);
            } catch (Throwable t) {
                // Prefetch is best-effort: silently ignore interruptions or network drops
            }
        });
    }

    public static synchronized void cancelPrefetch() {
        if (activePrefetch != null) {
            activePrefetch.cancel(true);
            activePrefetch = null;
        }
        activePrefetchKey = null;
    }
}
