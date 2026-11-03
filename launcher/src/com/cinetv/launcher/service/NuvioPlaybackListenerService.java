package com.cinetv.launcher.service;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.util.Log;

import com.cinetv.launcher.service.MovieCatalog.Movie;

import java.util.List;
import java.util.regex.Pattern;

/** Reads playback metadata only from user-approved Nuvio/Stremio MediaSessions. */
public final class NuvioPlaybackListenerService extends NotificationListenerService {
    private static final String TAG = "NuvioProgress";
    private static final long POLL_MS = 5_000L;
    private static final long SAVE_MS = 10_000L;
    private static final Pattern EPISODE = Pattern.compile("(?i)\\bS\\d{1,2}[: ]?E\\d{1,3}\\b");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastSaveAt;
    private String lastSavedKey = "";
    private int lastPlaybackState = PlaybackState.STATE_NONE;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            readActiveSession();
            handler.postDelayed(this, POLL_MS);
        }
    };

    public static boolean hasNotificationAccess(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName component = new ComponentName(context, NuvioPlaybackListenerService.class);
        for (String item : enabled.split(":")) {
            if (component.flattenToString().equals(item) || component.flattenToShortString().equals(item)) return true;
        }
        return false;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    public void onListenerDisconnected() {
        handler.removeCallbacks(poll);
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    public void onNotificationRemoved(android.service.notification.StatusBarNotification sbn) {
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poll);
        super.onDestroy();
    }

    private void readActiveSession() {
        if (!hasNotificationAccess(this)) return;
        try {
            MediaSessionManager manager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) return;
            List<MediaController> sessions = manager.getActiveSessions(
                    new ComponentName(this, NuvioPlaybackListenerService.class));
            MediaController chosen = null;
            int chosenState = PlaybackState.STATE_NONE;
            for (MediaController controller : sessions) {
                if (!isNuvioPackage(controller.getPackageName())) continue;
                PlaybackState state = controller.getPlaybackState();
                if (state == null) continue;
                int value = state.getState();
                if (value != PlaybackState.STATE_PLAYING && value != PlaybackState.STATE_PAUSED
                        && value != PlaybackState.STATE_BUFFERING) continue;
                if (chosen == null || value == PlaybackState.STATE_PLAYING) {
                    chosen = controller;
                    chosenState = value;
                    if (value == PlaybackState.STATE_PLAYING) break;
                }
            }
            if (chosen != null) saveSession(chosen, chosenState);
        } catch (SecurityException e) {
            Log.w(TAG, "Notification access was revoked while reading Nuvio playback", e);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Could not read active Nuvio playback sessions", e);
        }
    }

    private void saveSession(MediaController controller, int playbackState) {
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        if (metadata == null || state == null) return;

        String title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (TextUtils.isEmpty(title)) title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (TextUtils.isEmpty(title)) return;

        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = state.getPosition();
        if (position < 0 || duration <= 0) return;
        if (playbackState == PlaybackState.STATE_PLAYING) {
            long updatedAt = state.getLastPositionUpdateTime();
            if (updatedAt > 0) {
                float speed = state.getPlaybackSpeed();
                if (speed > 0 && !Float.isInfinite(speed) && !Float.isNaN(speed)) {
                    position += (long) ((android.os.SystemClock.elapsedRealtime() - updatedAt) * speed);
                }
            }
        }
        position = Math.min(position, duration);

        String series = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String subtitle = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        String mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        String mediaType = customMetadataString(metadata, "media_type", "mediaType", "type");
        boolean isSeries = isSeriesType(mediaType)
                || (mediaId != null && (mediaId.startsWith("series:") || mediaId.startsWith("tv:")));
        boolean isEpisode = isSeries || EPISODE.matcher(title).find()
                || (!TextUtils.isEmpty(subtitle) && EPISODE.matcher(subtitle).find());
        String catalogTitle = isEpisode && !TextUtils.isEmpty(series) ? series : title;
        Movie movie = new Movie();
        movie.title = catalogTitle.trim();
        movie.type = isEpisode ? "series" : "movie";

        String contentId = mediaId;
        if (contentId != null && (contentId.startsWith("series:") || contentId.startsWith("movie:") || contentId.startsWith("tv:"))) {
            contentId = contentId.substring(contentId.indexOf(':') + 1);
        }
        if (contentId != null && contentId.startsWith("tt")) movie.imdbId = contentId;
        else if (contentId != null && contentId.startsWith("tmdb:")) {
            try {
                movie.tmdbId = Integer.parseInt(contentId.substring("tmdb:".length()));
            } catch (NumberFormatException ignored) {
                Log.d(TAG, "Nuvio session has a non-numeric TMDB media id");
            }
        }

        String artwork = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
        if (TextUtils.isEmpty(artwork)) artwork = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        if (TextUtils.isEmpty(artwork)) artwork = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        if (artwork != null && (artwork.startsWith("https://") || artwork.startsWith("http://"))) {
            movie.posterUrl = artwork;
        }

        String key = !movie.imdbId.isEmpty() ? movie.imdbId
                : movie.tmdbId > 0 ? "tmdb:" + movie.tmdbId : movie.type + ":" + movie.title.toLowerCase(java.util.Locale.ROOT);
        long now = android.os.SystemClock.elapsedRealtime();
        boolean changedItem = !key.equals(lastSavedKey);
        boolean pausedTransition = playbackState != PlaybackState.STATE_PLAYING && playbackState != lastPlaybackState;
        if (playbackState == PlaybackState.STATE_PLAYING) {
            NowPlaying.setFromPlayback(this, movie, title, subtitle);
        }
        if (position >= 60_000L && (changedItem || pausedTransition || now - lastSaveAt >= SAVE_MS)) {
            WatchHistory.noteNuvioPlayback(this, movie, title, position, duration);
            lastSavedKey = key;
            lastSaveAt = now;
        }
        lastPlaybackState = playbackState;
    }

    private static boolean isNuvioPackage(String packageName) {
        if (packageName == null) return false;
        for (String allowed : AppLauncher.NUVIO_PACKAGES) {
            if (allowed.equals(packageName)) return true;
        }
        return packageName.toLowerCase(java.util.Locale.ROOT).contains("nuvio")
                || packageName.toLowerCase(java.util.Locale.ROOT).contains("stremio");
    }

    private static String customMetadataString(MediaMetadata metadata, String... keys) {
        for (String key : keys) {
            try {
                String value = metadata.getString(key);
                if (!TextUtils.isEmpty(value)) return value;
            } catch (ClassCastException e) {
                Log.d(TAG, "Ignoring non-text session metadata: " + key, e);
            }
        }
        return "";
    }

    private static boolean isSeriesType(String mediaType) {
        String type = mediaType.toLowerCase(java.util.Locale.ROOT);
        return type.contains("series") || type.contains("episode") || type.contains("show") || type.equals("tv");
    }
}
