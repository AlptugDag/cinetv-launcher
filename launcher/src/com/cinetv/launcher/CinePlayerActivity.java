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

    /** Playback profiles, from everything on to the safest; a profile that fails hands over to the next. */
    private static final String[] PROFILE_NAMES = {"Tam kalite", "Dolby Vision kapalı", "Güvenli mod"};
    private static final String PROFILE_PREF = "cineplayer_profile_";

    private Intent source;
    private Uri uri;
    private String title;
    private OkHttpDataSource.Factory http;
    private int profile;
    private boolean firstFrame;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Sound but no picture (the classic tunnelling / Dolby Vision / HDR decoder clash): try the next profile. */
    private final Runnable blackCheck = () -> {
        if (player != null && !firstFrame && player.isPlaying() && hasVideo()) fallback("görüntü gelmedi");
    };

    private void start(Intent in, Uri uri) {
        this.source = in;
        this.uri = uri;
        title = in.getStringExtra("title");
        if (title == null) title = in.getStringExtra(Intent.EXTRA_TITLE);
        if (title == null) title = uri.getLastPathSegment();
        resumeKey = RESUME_PREFIX + Integer.toHexString((title + "|" + uri.getHost()).hashCode());

        // HTTP headers, MX Player style: ["Key1", "Value1", "Key2", "Value2"]
        Map<String, String> headers = new HashMap<>();
        String[] h = in.getStringArrayExtra("headers");
        for (int i = 0; h != null && i + 1 < h.length; i += 2) headers.put(h[i], h[i + 1]);
        http = new OkHttpDataSource.Factory(new okhttp3.OkHttpClient());
        if (headers.containsKey("User-Agent")) http.setUserAgent(headers.get("User-Agent"));
        http.setDefaultRequestProperties(headers);

        view = buildScreen();

        // Where to start: the position the caller asks for, else where the viewer left this film here.
        long start = in.getIntExtra("position", -1);
        if (start < 0) start = in.getLongExtra("extra_position", -1);
        if (start < 0) start = Prefs.get(this).getLong(resumeKey, 0);
        // The profile that last worked on this TV for this kind of stream comes first.
        profile = Prefs.get(this).getInt(PROFILE_PREF + streamKind(), 0);
        build(Math.max(0, start));

        // CineSUB and Gamze know the film playing.
        com.cinetv.launcher.service.MovieCatalog.Movie m = new com.cinetv.launcher.service.MovieCatalog.Movie();
        m.title = title == null ? "" : title;
        com.cinetv.launcher.service.NowPlaying.set(this, m);
    }

    private String streamKind() {
        String t = source.getType() == null ? "" : source.getType();
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(java.util.Locale.ROOT);
        if (t.contains("mpegurl") || path.endsWith(".m3u8")) return "hls";
        if (t.contains("dash") || path.endsWith(".mpd")) return "dash";
        return "file";
    }

    /**
     * Builds the player for the current profile. Tunnelled playback is never used (on many TVs it plays the sound
     * without the picture). 0: HDR10/HDR10+/HLG/Dolby Vision and Dolby/DTS passthrough when the TV has them;
     * 1: no Dolby Vision tracks (HEVC/AVC instead) and audio decoded here; 2: up to 1080p, AVC first, stereo.
     */
    private void build(long position) {
        if (player != null) {
            player.release();
            player = null;
        }
        firstFrame = false;
        androidx.media3.exoplayer.DefaultRenderersFactory renderers = new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector tracks = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this);
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder pb = tracks.buildUponParameters()
                .setTunnelingEnabled(false)
                .setPreferredAudioLanguages("tr", java.util.Locale.getDefault().getLanguage(), "en")
                .setPreferredTextLanguages("tr", java.util.Locale.getDefault().getLanguage())
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowAudioMixedMimeTypeAdaptiveness(true)
                .setExceedRendererCapabilitiesIfNecessary(false);
        if (profile >= 1) {
            pb.setPreferredVideoMimeTypes(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264)
                    .setPreferredAudioMimeTypes(MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_AAC);
        }
        if (profile >= 2) {
            pb.setMaxVideoSize(1920, 1080).setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264)
                    .setMaxAudioChannelCount(2).setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC);
        }
        tracks.setParameters(pb);
        player = new ExoPlayer.Builder(this, renderers)
                .setTrackSelector(tracks)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .setSeekBackIncrementMs(SEEK_MS).setSeekForwardIncrementMs(SEEK_MS)
                .build();
        player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);

        MediaItem.Builder item = new MediaItem.Builder().setUri(uri);
        String type = source.getType();
        if (type != null && type.contains("mpegurl")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if (type != null && type.contains("dash")) item.setMimeType(MimeTypes.APPLICATION_MPD);
        item.setSubtitleConfigurations(subtitles(source));
        player.setMediaItem(item.build());
        if (position > 0) player.seekTo(position);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_READY) {
                    ui.removeCallbacks(blackCheck);
                    ui.postDelayed(blackCheck, 8000);
                }
                if (s == Player.STATE_ENDED) {
                    completed = true;
                    Prefs.get(CinePlayerActivity.this).edit().remove(resumeKey).apply();
                    finishWithResult();
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                firstFrame = true;
                ui.removeCallbacks(blackCheck);
                // this profile works here: remember it for the next film of the same kind
                Prefs.get(CinePlayerActivity.this).edit().putInt(PROFILE_PREF + streamKind(), profile).apply();
            }

            @Override
            public void onTracksChanged(androidx.media3.common.Tracks t) {
                if (profile >= 1) dropDolbyVision(t);
                matchFrameRate(t);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                fallback(e.getErrorCodeName());
            }
        });
        player.prepare();
        player.play();
        view.setPlayer(player);
    }

    private boolean hasVideo() {
        for (androidx.media3.common.Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() == C.TRACK_TYPE_VIDEO && g.isSelected()) return true;
        }
        return false;
    }

    /** From profile 1: a Dolby Vision video track is replaced by the stream's HDR10/SDR one when there is one. */
    private void dropDolbyVision(androidx.media3.common.Tracks t) {
        for (androidx.media3.common.Tracks.Group g : t.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSelected(i) || !MimeTypes.VIDEO_DOLBY_VISION.equals(g.getTrackFormat(i).sampleMimeType)) continue;
                for (int j = 0; j < g.length; j++) {
                    if (j != i && g.isTrackSupported(j) && !MimeTypes.VIDEO_DOLBY_VISION.equals(g.getTrackFormat(j).sampleMimeType)) {
                        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                                .setOverrideForType(new androidx.media3.common.TrackSelectionOverride(g.getMediaTrackGroup(), j)).build());
                        return;
                    }
                }
            }
        }
    }

    /** The current profile failed: carry on from the same second with the next safer one, or give up politely. */
    private void fallback(String why) {
        ui.removeCallbacks(blackCheck);
        long pos = player == null ? 0 : Math.max(0, player.getCurrentPosition());
        if (profile < PROFILE_NAMES.length - 1) {
            profile++;
            android.widget.Toast.makeText(this, "AlptugPlayer: " + PROFILE_NAMES[profile] + " ile sürüyor (" + why + ")",
                    android.widget.Toast.LENGTH_SHORT).show();
            build(pos);
            return;
        }
        android.widget.Toast.makeText(this, "Oynatılamadı: " + why, android.widget.Toast.LENGTH_LONG).show();
        finishWithResult();
    }

    /** The player view under the AlptugPlayer wordmark, in CineTV's lettering (shown with the controls). */
    private PlayerView buildScreen() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.BLACK);
        final PlayerView pv = new PlayerView(this);
        pv.setShowSubtitleButton(true);
        pv.setKeepScreenOn(true);
        pv.setControllerShowTimeoutMs(3500);
        root.addView(pv, new android.widget.FrameLayout.LayoutParams(-1, -1));
        final android.widget.TextView mark = new android.widget.TextView(this);
        android.text.SpannableString w = new android.text.SpannableString("AlptugPlayer");
        w.setSpan(new android.text.style.ForegroundColorSpan(com.cinetv.launcher.ui.Brand.TV_RED), 6, 12, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mark.setText(w);
        mark.setTextColor(android.graphics.Color.WHITE);
        mark.setTypeface(com.cinetv.launcher.FontManager.getBlack(this));
        float d = getResources().getDisplayMetrics().density;
        mark.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30 * d);
        mark.setLetterSpacing(0.04f);
        mark.setShadowLayer(16 * d, 0, 0, android.graphics.Color.argb(140, 204, 0, 41));
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP | android.view.Gravity.START);
        lp.setMargins((int) (40 * d), (int) (28 * d), 0, 0);
        root.addView(mark, lp);
        // with the controls, and for the first moments of the film
        pv.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v ->
                mark.animate().alpha(v == android.view.View.VISIBLE ? 1f : 0f).setDuration(220).start());
        mark.postDelayed(() -> { if (!pv.isControllerFullyVisible()) mark.animate().alpha(0f).setDuration(400).start(); }, 3500);
        setContentView(root);
        return pv;
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
        ui.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
