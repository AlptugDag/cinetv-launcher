package com.cinetv.launcher.service;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * API keys (Gemini/Claude, TMDB) encrypted with an AES-256-GCM key that never leaves the Android Keystore.
 *
 * Older builds kept these keys in Settings.Secure, which any app on the TV can read. On every start
 * {@link #migrateLegacy} moves such values (also ones provisioned with {@code adb shell settings put secure ...})
 * into this store and blanks the world-readable copy once the encrypted copy reads back intact.
 */
public final class SecretStore {

    public static final String AI_KEY = "ai_api_key";
    public static final String TMDB_KEY = "tmdb_api_key";
    public static final String TMDB_READ_TOKEN = "tmdb_read_token";

    private static final String TAG = "CineTvSecrets";
    private static final String FILE = "cinetv_secrets";
    private static final String KEY_ALIAS = "cinetv_secret_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String PLAIN_PREFIX = "p:";

    private static final String[][] LEGACY_SECURE_KEYS = {
            {"tv_ai_api_key", AI_KEY},
            {"tv_ai_curator_api_key", AI_KEY},
            {"tv_gemini_api_key", AI_KEY},
            {"tv_tmdb_api_key", TMDB_KEY},
            {"tv_tmdb_read_token", TMDB_READ_TOKEN},
    };
    private static final String[][] LEGACY_PREF_KEYS = {
            {"ai_api_key", AI_KEY},
            {"gemini_api_key", AI_KEY},
            {"tmdb_api_key", TMDB_KEY},
    };

    private SecretStore() {}

    public static synchronized String get(Context ctx, String name) {
        String stored = prefs(ctx).getString(name, null);
        if (stored == null || stored.isEmpty()) return null;
        if (stored.startsWith(PLAIN_PREFIX)) return stored.substring(PLAIN_PREFIX.length());
        try {
            int sep = stored.indexOf(':');
            byte[] iv = Base64.decode(stored.substring(0, sep), Base64.NO_WRAP);
            byte[] ct = Base64.decode(stored.substring(sep + 1), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Log.w(TAG, "Could not decrypt " + name + ": " + t.getClass().getSimpleName());
            return null;
        }
    }

    public static synchronized boolean put(Context ctx, String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            prefs(ctx).edit().remove(name).apply();
            return true;
        }
        String clean = value.trim();
        String encoded;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(clean.getBytes(StandardCharsets.UTF_8));
            encoded = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Throwable t) {
            // Some TV keystores are broken; app-private storage is still sandboxed from other apps.
            Log.w(TAG, "Keystore unavailable, storing " + name + " in private storage: " + t.getClass().getSimpleName());
            encoded = PLAIN_PREFIX + clean;
        }
        return prefs(ctx).edit().putString(name, encoded).commit() && clean.equals(get(ctx, name));
    }

    public static boolean has(Context ctx, String name) {
        String v = get(ctx, name);
        return v != null && !v.isEmpty();
    }

    /** Moves keys from world-readable Settings.Secure and legacy plain prefs into the encrypted store. */
    public static synchronized void migrateLegacy(Context ctx) {
        ContentResolver cr = ctx.getContentResolver();
        for (String[] pair : LEGACY_SECURE_KEYS) {
            try {
                String legacy = Settings.Secure.getString(cr, pair[0]);
                if (legacy == null || legacy.trim().isEmpty()) continue;
                if (put(ctx, pair[1], legacy)) {
                    Settings.Secure.putString(cr, pair[0], "");
                    Log.i(TAG, "Migrated " + pair[0] + " into the encrypted store");
                }
            } catch (Throwable t) {
                Log.w(TAG, "Legacy key migration skipped for " + pair[0] + ": " + t.getMessage());
            }
        }
        SharedPreferences legacyPrefs = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor cleanup = legacyPrefs.edit();
        for (String[] pair : LEGACY_PREF_KEYS) {
            String legacy = legacyPrefs.getString(pair[0], null);
            if (legacy == null || legacy.trim().isEmpty()) continue;
            if (has(ctx, pair[1]) || put(ctx, pair[1], legacy)) cleanup.remove(pair[0]);
        }
        cleanup.apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }
}
