package com.cinetv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.cinetv.launcher.service.Prefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CineTV's own film player. Nuvio (or any app) can hand it a stream as its external player; nothing on the TV is
 * changed to make that happen, the viewer picks it in Nuvio. It understands the MX Player / VLC conventions
 * (title, start position, subtitles, HTTP headers) and returns the position when it closes, so Nuvio keeps the
 * watch progress. While it plays, CineSUB (the yellow key) and Gamze know exactly which film it is.
 */
public class CinePlayerActivity extends Activity {

    /** True while a film plays here: the key service then leaves Back to the player. */
    public static volatile boolean active;

    private static final String RESUME_PREFIX = "cineplayer_pos_";
    private static final long SEEK_MS = 10_000L;

    private ExoPlayer player;
    private PlayerView view;
    private String resumeKey;
    private boolean completed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Intent in = getIntent();
        Uri uri = in == null ? null : in.getData();
        if (uri == null) {
            finish();
            return;
        }
        try {
            start(in, uri);
        } catch (Throwable t) {
            // a stream it cannot open never takes CineTV down with it
            android.widget.Toast.makeText(this, "Oynatılamadı: " + t.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void start(Intent in, Uri uri) {
        String title = in.getStringExtra("title");
        if (title == null) title = in.getStringExtra(Intent.EXTRA_TITLE);
        if (title == null) title = uri.getLastPathSegment();
        resumeKey = RESUME_PREFIX + Integer.toHexString((title + "|" + uri.getHost()).hashCode());

        // HTTP headers, MX Player style: ["Key1", "Value1", "Key2", "Value2"]
        Map<String, String> headers = new HashMap<>();
        String[] h = in.getStringArrayExtra("headers");
        for (int i = 0; h != null && i + 1 < h.length; i += 2) headers.put(h[i], h[i + 1]);
        OkHttpDataSource.Factory http = new OkHttpDataSource.Factory(new okhttp3.OkHttpClient());
        if (headers.containsKey("User-Agent")) http.setUserAgent(headers.get("User-Agent"));
        http.setDefaultRequestProperties(headers);

        // HDR10, HDR10+, HLG and Dolby Vision play whenever the TV has the decoder (the track selector only keeps
        // tracks the hardware can render); a missing decoder falls back to the next one instead of failing.
        // Dolby / DTS audio goes to the TV or soundbar as is when HDMI reports it can take it.
        androidx.media3.exoplayer.DefaultRenderersFactory renderers = new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector tracks = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this);
        tracks.setParameters(tracks.buildUponParameters()
                .setPreferredAudioLanguages("tr", java.util.Locale.getDefault().getLanguage(), "en")
                .setPreferredTextLanguages("tr", java.util.Locale.getDefault().getLanguage())
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true));
        player = new ExoPlayer.Builder(this, renderers)
                .setTrackSelector(tracks)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .setSeekBackIncrementMs(SEEK_MS).setSeekForwardIncrementMs(SEEK_MS)
                .build();
        player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);

        MediaItem.Builder item = new MediaItem.Builder().setUri(uri);
        String type = in.getType();
        if (type != null && type.contains("mpegurl")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if (type != null && type.contains("dash")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        item.setSubtitleConfigurations(subtitles(in));
        player.setMediaItem(item.build());

        // Where to start: the position the caller asks for, else where the viewer left this film here.
        long start = in.getIntExtra("position", -1);
        if (start < 0) start = in.getLongExtra("extra_position", -1);
        if (start < 0) start = Prefs.get(this).getLong(resumeKey, 0);
        if (start > 0) player.seekTo(start);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_ENDED) {
                    completed = true;
                    Prefs.get(CinePlayerActivity.this).edit().remove(resumeKey).apply();
                    finishWithResult();
                }
            }

            @Override
            public void onTracksChanged(androidx.media3.common.Tracks t) {
                matchFrameRate(t);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                android.widget.Toast.makeText(CinePlayerActivity.this,
                        "Oynatılamadı: " + e.getErrorCodeName(), android.widget.Toast.LENGTH_LONG).show();
                finishWithResult();
            }
        });
        player.prepare();
        player.play();

        view = new PlayerView(this);
        view.setPlayer(player);
        view.setShowSubtitleButton(true);
        view.setKeepScreenOn(true);
        view.setControllerShowTimeoutMs(3500);
        setContentView(view);

        // CineSUB and Gamze know the film playing.
        com.cinetv.launcher.service.MovieCatalog.Movie m = new com.cinetv.launcher.service.MovieCatalog.Movie();
        m.title = title == null ? "" : title;
        com.cinetv.launcher.service.NowPlaying.set(this, m);
    }

    /**
     * The TV switches to the film's own refresh rate (23.976 / 24 / 25 / 50 / 60 Hz) at the same resolution, so
     * pans do not judder; when no mode fits, the screen stays as it is.
     */
    private void matchFrameRate(androidx.media3.common.Tracks t) {
        try {
            float fps = 0;
            for (androidx.media3.common.Tracks.Group g : t.getGroups()) {
                if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
                for (int i = 0; i < g.length; i++) if (g.isTrackSelected(i)) fps = g.getTrackFormat(i).frameRate;
            }
            if (fps <= 0) return;
            android.view.Display d = getWindowManager().getDefaultDisplay();
            android.view.Display.Mode cur = d.getMode(), best = null;
            for (android.view.Display.Mode m : d.getSupportedModes()) {
                if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
                float r = m.getRefreshRate();
                // an exact rate or a whole multiple of it (48 for 24, 50 for 25)
                float k = Math.round(r / fps);
                if (k < 1 || Math.abs(r - fps * k) > 0.05f * k) continue;
                if (best == null || r < best.getRefreshRate()) best = m;
            }
            if (best == null || best.getModeId() == cur.getModeId()) return;
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            getWindow().setAttributes(lp);
        } catch (Throwable ignored) {
        }
    }

    private static List<MediaItem.SubtitleConfiguration> subtitles(Intent in) {
        List<MediaItem.SubtitleConfiguration> out = new ArrayList<>();
        Parcelable[] subs = in.getParcelableArrayExtra("subs");
        String[] names = in.getStringArrayExtra("subs.name");
        for (int i = 0; subs != null && i < subs.length; i++) {
            if (!(subs[i] instanceof Uri)) continue;
            Uri s = (Uri) subs[i];
            String path = s.getLastPathSegment() == null ? "" : s.getLastPathSegment().toLowerCase(java.util.Locale.ROOT);
            String mime = path.endsWith(".vtt") ? MimeTypes.TEXT_VTT : path.endsWith(".ass") || path.endsWith(".ssa") ? MimeTypes.TEXT_SSA : MimeTypes.APPLICATION_SUBRIP;
            out.add(new MediaItem.SubtitleConfiguration.Builder(s).setMimeType(mime)
                    .setLabel(names != null && i < names.length ? names[i] : null).build());
        }
        return out;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (player == null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && !view.isControllerFullyVisible()) {
            // With the controls hidden: left/right seek ten seconds, OK pauses or plays and shows the bar.
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                player.seekBack();
                view.showController();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                player.seekForward();
                view.showController();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (player.isPlaying()) player.pause();
                else player.play();
                view.showController();
                return true;
            }
        }
        if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            if (view.isControllerFullyVisible()) {
                view.hideController();
                return true;
            }
            finishWithResult();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) return true;
        return view.dispatchKeyEvent(e) || super.dispatchKeyEvent(e);
    }

    /** MX Player and VLC result conventions, so the caller saves where the viewer stopped. */
    private void finishWithResult() {
        if (player == null) {
            finish();
            return;
        }
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        savePosition();
        Intent r = new Intent("com.mxtech.intent.result.VIEW");
        r.putExtra("position", (int) pos).putExtra("duration", (int) Math.max(0, dur))
                .putExtra("end_by", completed ? "playback_completion" : "user")
                .putExtra("extra_position", pos).putExtra("extra_duration", Math.max(0, dur));
        setResult(RESULT_OK, r);
        finish();
    }

    private void savePosition() {
        if (player == null || completed) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        SharedPreferences.Editor ed = Prefs.get(this).edit();
        // Not worth resuming the first half minute, nor the end credits.
        if (pos > 30_000 && (dur <= 0 || pos < dur - 90_000)) ed.putLong(resumeKey, pos);
        else ed.remove(resumeKey);
        ed.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        active = true;
    }

    @Override
    protected void onPause() {
        active = false;
        savePosition();
        if (player != null) player.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        active = false;
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
