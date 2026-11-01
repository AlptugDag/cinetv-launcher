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
    /** The player on screen (for the yellow key: CineSUB needs a frame it can see). */
    static volatile CinePlayerActivity current;
    /** CineSUB asked for the picture: tunnelled video never reaches a screenshot, so this session plays untunnelled. */
    private boolean noTunnel, cinesubWaiting;

    /** The yellow key over AlptugPlayer: CineSUB looks at the film (untunnelling first when needed). */
    public static boolean cinesub() {
        final CinePlayerActivity a = current;
        if (a == null || a.player == null) return false;
        a.runOnUiThread(() -> {
            if (a.tunnel) {
                a.noTunnel = true;
                a.cinesubWaiting = true;
                a.build(Math.max(0, a.player.getCurrentPosition()));
            } else {
                com.cinetv.launcher.ui.CineSubOverlay.trigger(a);
            }
        });
        return true;
    }

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
        http = new OkHttpDataSource.Factory(new okhttp3.OkHttpClient.Builder()
                // local torrent servers take a while to hand over the first bytes
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .followSslRedirects(true).build());
        if (!headers.containsKey("User-Agent")) http.setUserAgent("Mozilla/5.0 (Linux; Android TV) AlptugPlayer");
        if (headers.containsKey("User-Agent")) http.setUserAgent(headers.get("User-Agent"));
        http.setDefaultRequestProperties(headers);

        view = buildScreen();

        // Where to start: the position the caller asks for, else where the viewer left this film here.
        long start = in.getIntExtra("position", -1);
        if (start < 0) start = in.getLongExtra("extra_position", -1);
        if (start < 0) start = Prefs.get(this).getLong(resumeKey, 0);
        // The profile that last worked on this TV for this kind of stream comes first.
        profile = Prefs.get(this).getInt(PROFILE_PREF + streamKind(), 0);
        if (profile == 0 && !displayHasDolbyVision()) profile = 1;
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
    private static final String SW_PREFIX = "cineplayer_sw_";

    /** Hardware decoders first; a codec whose hardware decoder failed on this TV gets the software one first. */
    private androidx.media3.exoplayer.mediacodec.MediaCodecSelector codecSelector() {
        final SharedPreferences prefs = Prefs.get(this);
        return (mime, secure, tunneling) -> {
            List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> all = new ArrayList<>(
                    androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunneling));
            final boolean swFirst = prefs.getBoolean(SW_PREFIX + mime, false);
            java.util.Collections.sort(all, (a, b) -> {
                if (a.hardwareAccelerated == b.hardwareAccelerated) return 0;
                return (a.hardwareAccelerated != swFirst) ? -1 : 1;
            });
            return all;
        };
    }

    /** Dolby Vision only when the screen can show it (else the HDR10/SDR track of the stream from the start). */
    private boolean displayHasDolbyVision() {
        try {
            android.view.Display.HdrCapabilities hdr = getWindowManager().getDefaultDisplay().getHdrCapabilities();
            if (hdr == null) return false;
            for (int t : hdr.getSupportedHdrTypes()) if (t == android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean tunnel;
    private static final String TUNNEL_BAD = "cineplayer_tunnel_bad";

    /** Tunnelled playback (smoother A/V sync, less CPU) when this TV's decoder offers it and it has not failed here. */
    private boolean tunnelSupported() {
        if (Prefs.get(this).getBoolean(TUNNEL_BAD, false)) return false;
        try {
            for (android.media.MediaCodecInfo ci : new android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (ci.isEncoder()) continue;
                for (String t : ci.getSupportedTypes()) {
                    if (!t.equalsIgnoreCase("video/hevc") && !t.equalsIgnoreCase("video/avc")) continue;
                    if (ci.getCapabilitiesForType(t).isFeatureSupported(android.media.MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void build(long position) {
        tunnel = !noTunnel && profile <= 1 && tunnelSupported();
        if (player != null) {
            player.release();
            player = null;
        }
        firstFrame = false;
        androidx.media3.exoplayer.DefaultRenderersFactory renderers = new androidx.media3.exoplayer.DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setMediaCodecSelector(codecSelector())
                .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        com.cinetv.launcher.device.PerformanceProfile.Tier tier = com.cinetv.launcher.device.PerformanceProfile.tier(this);
        // Strong TVs on Android 12+ feed the decoder asynchronously (smoother 4K); others keep the classic path.
        if (tier == com.cinetv.launcher.device.PerformanceProfile.Tier.HIGH && android.os.Build.VERSION.SDK_INT >= 31) {
            renderers.forceEnableMediaCodecAsynchronousQueueing();
        }
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector tracks = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this);
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder pb = tracks.buildUponParameters()
                .setTunnelingEnabled(tunnel)
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
        // Buffer by what the TV can hold: small on weak boxes, long (with a back-buffer for instant rewinds) on strong ones.
        int minMs, maxMs, backMs;
        switch (tier) {
            case LOW: minMs = 15_000; maxMs = 30_000; backMs = 0; break;
            case MID: minMs = 25_000; maxMs = 60_000; backMs = 10_000; break;
            default: minMs = 40_000; maxMs = 120_000; backMs = 30_000; break;
        }
        androidx.media3.exoplayer.DefaultLoadControl load = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(minMs, maxMs, 2_000, 4_000)
                .setBackBuffer(backMs, true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
        player = new ExoPlayer.Builder(this, renderers)
                .setLoadControl(load)
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
                if (cinesubWaiting) {
                    cinesubWaiting = false;
                    ui.postDelayed(() -> com.cinetv.launcher.ui.CineSubOverlay.trigger(CinePlayerActivity.this), 600);
                }
                ui.removeCallbacks(blackCheck);
                // this profile works here: remember it for the next film of the same kind
                Prefs.get(CinePlayerActivity.this).edit().putInt(PROFILE_PREF + streamKind(), profile).apply();
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                if (playButton != null) playButton.setImageResource(playing ? com.cinetv.launcher.R.drawable.ic_player_pause : com.cinetv.launcher.R.drawable.ic_player_play);
            }

            @Override
            public void onTracksChanged(androidx.media3.common.Tracks t) {
                if (profile >= 1) dropDolbyVision(t);
                matchFrameRate(t);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                // A hardware decoder that cannot start or decode this codec: software first for it on this TV.
                int c = e.errorCode;
                if (c == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED || c == PlaybackException.ERROR_CODE_DECODING_FAILED
                        || c == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED || c == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) {
                    androidx.media3.common.Format f = player == null ? null : player.getVideoFormat();
                    String mime = f == null ? null : f.sampleMimeType;
                    if (mime != null && !Prefs.get(CinePlayerActivity.this).getBoolean(SW_PREFIX + mime, false)) {
                        Prefs.get(CinePlayerActivity.this).edit().putBoolean(SW_PREFIX + mime, true).apply();
                        build(Math.max(0, player.getCurrentPosition()));
                        return;
                    }
                }
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
        if (tunnel) {
            // tunnelling is what failed on this TV: never again here, same profile without it
            Prefs.get(this).edit().putBoolean(TUNNEL_BAD, true).apply();
            build(pos);
            return;
        }
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
        // subtitles in the TV's own caption style (size, colour, background from its accessibility settings)
        if (pv.getSubtitleView() != null) {
            pv.getSubtitleView().setUserDefaultStyle();
            pv.getSubtitleView().setUserDefaultTextSize();
        }
        fit = Prefs.get(this).getInt("cineplayer_fit", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        pv.setResizeMode(fit);
        float d = getResources().getDisplayMetrics().density;
        glassControls(pv, d);
        pv.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) vis -> {
            if (vis == android.view.View.VISIBLE && playButton != null) playButton.post(playButton::requestFocus);
        });
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

    /**
     * visionOS-style controls: one floating glass ornament at the bottom holding the timeline, the times, the
     * buttons and a small half-transparent AlptugPlayer signature, plus a glass capsule for play/seek in the
     * centre. Everything appears and disappears together with the controls.
     */
    private void glassControls(PlayerView pv, float d) {
        try {
            android.view.View scrim = pv.findViewById(androidx.media3.ui.R.id.exo_controls_background);
            if (scrim != null) scrim.setBackgroundColor(android.graphics.Color.argb(60, 0, 0, 0));
            int side = Math.round(56 * d), bottom = Math.round(28 * d), barH = Math.round(104 * d);

            android.view.View barView = pv.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);
            if (barView instanceof android.view.ViewGroup) {
                android.view.ViewGroup bar = (android.view.ViewGroup) barView;
                bar.setBackground(glass(d, 30));
                android.view.ViewGroup.LayoutParams raw = bar.getLayoutParams();
                if (raw instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams m = (android.view.ViewGroup.MarginLayoutParams) raw;
                    m.setMargins(side, 0, side, bottom);
                    m.height = barH;
                    bar.setLayoutParams(m);
                }
                // the timeline sits in the upper half of the ornament, the times and buttons in the lower half
                bar.setPadding(Math.round(20 * d), Math.round(44 * d), Math.round(16 * d), 0);
                // AlptugPlayer: small, half transparent, centred in the ornament's lower half
                android.widget.TextView mark = new android.widget.TextView(this);
                android.text.SpannableString w = new android.text.SpannableString("AlptugPlayer");
                w.setSpan(new android.text.style.ForegroundColorSpan(com.cinetv.launcher.ui.Brand.TV_RED), 6, 12, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                mark.setText(w);
                mark.setTextColor(android.graphics.Color.WHITE);
                mark.setTypeface(com.cinetv.launcher.FontManager.getBlack(this));
                mark.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 14 * d);
                mark.setLetterSpacing(0.04f);
                mark.setAlpha(0.5f);
                brand = mark; // placed after the times below, clear of the buttons
            }
            // Audio, quality, picture and speed as round glass icon buttons next to subtitles and settings
            android.view.View basic = pv.findViewById(androidx.media3.ui.R.id.exo_basic_controls);
            if (basic instanceof android.view.ViewGroup) {
                android.view.ViewGroup row = (android.view.ViewGroup) basic;
                int[] icons = {com.cinetv.launcher.R.drawable.ic_player_audio, com.cinetv.launcher.R.drawable.ic_player_quality,
                        com.cinetv.launcher.R.drawable.ic_player_aspect, com.cinetv.launcher.R.drawable.ic_player_speed};
                String[] names = {"Ses", "Kalite", "Görüntü", "Hız"};
                for (int i = 0; i < icons.length; i++) {
                    final int which = i;
                    android.widget.ImageView b = iconButton(icons[i], names[i], d, v -> {
                        if (which == 0) chooseTrack(C.TRACK_TYPE_AUDIO);
                        else if (which == 1) chooseTrack(C.TRACK_TYPE_VIDEO);
                        else if (which == 2) chooseFit();
                        else chooseSpeed();
                    });
                    row.addView(b, i, iconParams(d));
                }
            }
            // "Bitiş 23:41": when the film will end in the real world, next to the times
            android.view.View times = pv.findViewById(androidx.media3.ui.R.id.exo_time);
            if (times instanceof android.view.ViewGroup) {
                // play / pause and ten seconds back and forth on the left of the ornament
                android.view.ViewGroup t = (android.view.ViewGroup) times;
                t.addView(iconButton(com.cinetv.launcher.R.drawable.ic_player_back10, "10 sn geri", d, v -> player.seekBack()), 0, iconParams(d));
                playButton = iconButton(com.cinetv.launcher.R.drawable.ic_player_pause, "Oynat / Duraklat", d, v -> {
                    if (player.isPlaying()) player.pause();
                    else player.play();
                });
                t.addView(playButton, 1, iconParams(d));
                t.addView(iconButton(com.cinetv.launcher.R.drawable.ic_player_fwd10, "10 sn ileri", d, v -> player.seekForward()), 2, iconParams(d));
                android.view.View gap = new android.view.View(this);
                t.addView(gap, 3, new android.widget.LinearLayout.LayoutParams(Math.round(12 * d), 1));
                endLabel = new android.widget.TextView(this);
                endLabel.setTextColor(android.graphics.Color.argb(200, 255, 255, 255));
                endLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 14 * d);
                endLabel.setPadding(Math.round(14 * d), 0, 0, 0);
                endLabel.setGravity(android.view.Gravity.CENTER_VERTICAL);
                android.widget.LinearLayout.LayoutParams elp = new android.widget.LinearLayout.LayoutParams(-2, -2);
                elp.gravity = android.view.Gravity.CENTER_VERTICAL;
                ((android.view.ViewGroup) times).addView(endLabel, elp);
                if (brand != null) {
                    brand.setPadding(Math.round(24 * d), 0, 0, 0);
                    ((android.view.ViewGroup) times).addView(brand);
                }
            }
            // the timeline is exactly as wide as the ornament's inside, never longer than the bar
            android.view.View progress = pv.findViewById(androidx.media3.ui.R.id.exo_progress);
            if (progress != null && progress.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                android.view.ViewGroup.MarginLayoutParams m = (android.view.ViewGroup.MarginLayoutParams) progress.getLayoutParams();
                m.setMargins(side + Math.round(22 * d), 0, side + Math.round(22 * d), bottom + barH - Math.round(46 * d));
                progress.setLayoutParams(m);
            }
            if (progress instanceof androidx.media3.ui.DefaultTimeBar) {
                androidx.media3.ui.DefaultTimeBar t = (androidx.media3.ui.DefaultTimeBar) progress;
                t.setPlayedColor(android.graphics.Color.WHITE);
                t.setScrubberColor(android.graphics.Color.WHITE);
                t.setBufferedColor(android.graphics.Color.argb(110, 255, 255, 255));
                t.setUnplayedColor(android.graphics.Color.argb(45, 255, 255, 255));
            }
            // nothing in the middle of the picture: the transport lives in the ornament
            android.view.View center = pv.findViewById(androidx.media3.ui.R.id.exo_center_controls);
            // (removed, not hidden: the controller would show a hidden one again with every reveal)
            if (center != null && center.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) center.getParent()).removeView(center);
            // the signature: small and quiet, centred just above the ornament, coming and going with it
            android.view.View controller = pv.findViewById(androidx.media3.ui.R.id.exo_controller);
            if (brand != null && controller instanceof android.widget.FrameLayout) {
                ((android.view.ViewGroup) brand.getParent()).removeView(brand);
                brand.setPadding(0, 0, 0, 0);
                brand.setAlpha(0.45f);
                android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
                blp.bottomMargin = bottom + barH + Math.round(10 * d);
                ((android.widget.FrameLayout) controller).addView(brand, blp);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Audio tracks (language, channels, codec) or video qualities (resolution, bitrate), with "Otomatik" for video. */
    private void chooseTrack(final int type) {
        if (player == null) return;
        final List<String> labels = new ArrayList<>();
        final List<androidx.media3.common.TrackSelectionOverride> picks = new ArrayList<>();
        if (type == C.TRACK_TYPE_VIDEO) {
            labels.add("Otomatik (bağlantıya göre)");
            picks.add(null);
        }
        int checked = type == C.TRACK_TYPE_VIDEO ? 0 : -1;
        for (androidx.media3.common.Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                androidx.media3.common.Format f = g.getTrackFormat(i);
                String l;
                if (type == C.TRACK_TYPE_VIDEO) {
                    l = (f.height > 0 ? f.height + "p" : "?") + (f.frameRate > 0 ? " · " + Math.round(f.frameRate) + " fps" : "")
                            + (f.bitrate > 0 ? " · " + String.format(java.util.Locale.ROOT, "%.1f Mbps", f.bitrate / 1e6) : "")
                            + (MimeTypes.VIDEO_DOLBY_VISION.equals(f.sampleMimeType) ? " · Dolby Vision" : f.colorInfo != null && f.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ? " · HDR10" : "");
                } else {
                    String lang = f.language == null ? "" : new java.util.Locale(f.language).getDisplayLanguage(new java.util.Locale("tr"));
                    String ch = f.channelCount == 6 ? "5.1" : f.channelCount == 8 ? "7.1" : f.channelCount == 2 ? "Stereo" : f.channelCount > 0 ? f.channelCount + " kanal" : "";
                    String codec = f.sampleMimeType == null ? "" : f.sampleMimeType.contains("eac3") ? "Dolby Digital+" : f.sampleMimeType.contains("ac3") ? "Dolby Digital"
                            : f.sampleMimeType.contains("dts") ? "DTS" : f.sampleMimeType.contains("true-hd") ? "Dolby TrueHD" : f.sampleMimeType.contains("mp4a") ? "AAC" : "";
                    l = (f.label != null ? f.label : lang.isEmpty() ? "Ses " + (labels.size() + 1) : lang) + (ch.isEmpty() ? "" : " · " + ch) + (codec.isEmpty() ? "" : " · " + codec);
                }
                if (g.isTrackSelected(i) && !(type == C.TRACK_TYPE_VIDEO && player.getTrackSelectionParameters().overrides.isEmpty())) checked = labels.size();
                labels.add(l);
                picks.add(new androidx.media3.common.TrackSelectionOverride(g.getMediaTrackGroup(), i));
            }
        }
        if (labels.isEmpty()) {
            android.widget.Toast.makeText(this, "Seçilecek başka parça yok", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(type == C.TRACK_TYPE_VIDEO ? "Kalite" : "Ses")
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (dlg, which) -> {
                    androidx.media3.common.TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(type);
                    if (picks.get(which) != null) b.setOverrideForType(picks.get(which));
                    player.setTrackSelectionParameters(b.build());
                    dlg.dismiss();
                }).show();
    }

    private int fit = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private android.widget.TextView endLabel, brand;

    /** Keeps "Bitiş HH:mm" current (speed included); once a second while the player is on screen. */
    private final Runnable endClock = new Runnable() {
        @Override
        public void run() {
            ui.postDelayed(this, 1000);
            if (endLabel == null || player == null) return;
            long dur = player.getDuration(), pos = player.getCurrentPosition();
            if (dur <= 0 || dur == C.TIME_UNSET) {
                endLabel.setText("");
                return;
            }
            float speed = Math.max(0.1f, player.getPlaybackParameters().speed);
            long end = System.currentTimeMillis() + (long) ((dur - pos) / speed);
            endLabel.setText("· Bitiş " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(end)));
        }
    };

    /** Picture: whole frame (Sığdır), fill the screen (Doldur, crops the edges) or stretch-free zoom (Yakınlaştır). */
    private void chooseFit() {
        final int[] modes = {androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT, androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH};
        int checked = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == fit) checked = i;
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Görüntü")
                .setSingleChoiceItems(new String[]{"Sığdır (tüm kare)", "Doldur (kenarlar kırpılır)", "Genişliğe yay"}, checked, (dlg, which) -> {
                    fit = modes[which];
                    view.setResizeMode(fit);
                    Prefs.get(this).edit().putInt("cineplayer_fit", fit).apply();
                    dlg.dismiss();
                }).show();
    }

    /** Playback speed, pitch kept. */
    private void chooseSpeed() {
        final float[] speeds = {0.75f, 1f, 1.25f, 1.5f, 2f};
        int checked = 1;
        for (int i = 0; i < speeds.length; i++) if (Math.abs(speeds[i] - player.getPlaybackParameters().speed) < 0.01f) checked = i;
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hız")
                .setSingleChoiceItems(new String[]{"0.75×", "Normal", "1.25×", "1.5×", "2×"}, checked, (dlg, which) -> {
                    player.setPlaybackSpeed(speeds[which]);
                    dlg.dismiss();
                }).show();
    }

    private android.widget.ImageView playButton;

    private android.widget.LinearLayout.LayoutParams iconParams(float d) {
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(Math.round(40 * d), Math.round(40 * d));
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        lp.setMarginEnd(Math.round(6 * d));
        return lp;
    }

    /** A round visionOS glass button with a white symbol; white with a dark symbol when focused. */
    private android.widget.ImageView iconButton(int icon, String label, float d, android.view.View.OnClickListener click) {
        final android.widget.ImageView b = new android.widget.ImageView(this);
        b.setImageResource(icon);
        b.setContentDescription(label);
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.round(9 * d);
        b.setPadding(pad, pad, pad, pad);
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(android.graphics.Color.argb(46, 255, 255, 255));
        b.setBackground(bg);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? android.graphics.Color.argb(235, 255, 255, 255) : android.graphics.Color.argb(46, 255, 255, 255));
            b.setColorFilter(has ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(140).start();
        });
        b.setColorFilter(android.graphics.Color.WHITE);
        return b;
    }

    /** visionOS glass: a light frosted fill, brighter at the top, with a thin luminous rim. */
    private static android.graphics.drawable.GradientDrawable glass(float d, float radiusDp) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{android.graphics.Color.argb(92, 120, 120, 128), android.graphics.Color.argb(70, 60, 60, 68)});
        g.setCornerRadius(radiusDp * d);
        g.setStroke(Math.max(1, Math.round(d)), android.graphics.Color.argb(80, 255, 255, 255));
        return g;
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
        current = this;
        ui.post(endClock);
    }

    @Override
    protected void onPause() {
        active = false;
        if (current == this) current = null;
        ui.removeCallbacks(endClock);
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
