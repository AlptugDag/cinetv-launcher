package com.cinetv.launcher.remote;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * A minimal client for the TV's own adb daemon (127.0.0.1:5555, the "network debugging" CineTV is
 * installed with). It only runs one shell command: starting {@link KeyInjector}. The first time, the
 * TV asks "Allow debugging?" for CineTV's key; with "Always allow" it never asks again.
 */
public final class LocalAdb {
    private static final String TAG = "CineTvAdb";
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final byte[] SHA1_DIGEST_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private LocalAdb() {}

    /** Runs a shell command on the TV through its own adb; true once adb accepted us and ran it. */
    public static boolean shell(Context ctx, String command, int approvalTimeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 1500);
            s.setSoTimeout(approvalTimeoutMs);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            KeyPair keys = keys(ctx);
            write(out, CNXN, 0x01000001, 256 * 1024, "host::\0".getBytes(StandardCharsets.UTF_8));
            boolean sentSignature = false;
            while (true) {
                Msg m = read(in);
                if (m.cmd == CNXN) break;
                if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) return false;
                if (!sentSignature) {
                    write(out, AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
                    sentSignature = true;
                } else {
                    // not known yet: offer the key, the TV asks the viewer once
                    write(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKey((RSAPublicKey) keys.getPublic()));
                }
            }
            write(out, OPEN, 1, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            s.setSoTimeout(4000);
            long deadline = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < deadline) {
                Msg m = read(in);
                if (m.cmd == WRTE) write(out, OKAY, 1, m.arg0, new byte[0]);
                else if (m.cmd == CLSE) break;
            }
            return true;
        } catch (Throwable t) {
            Log.d(TAG, "local adb: " + t);
            return false;
        }
    }

    private static final class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    private static void write(OutputStream out, int cmd, int a0, int a1, byte[] data) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        b.putInt(cmd).putInt(a0).putInt(a1).putInt(data.length).putInt(sum).putInt(~cmd).put(data);
        out.write(b.array());
        out.flush();
    }

    private static Msg read(InputStream in) throws Exception {
        DataInputStream d = new DataInputStream(in);
        byte[] h = new byte[24];
        d.readFully(h);
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > 1 << 20) throw new IllegalStateException("bad length");
        m.data = new byte[len];
        d.readFully(m.data);
        return m;
    }

    /** PKCS#1 v1.5 signature of the 20-byte token as an SHA-1 digest, as adbd expects. */
    private static byte[] sign(RSAPrivateKey key, byte[] token) {
        int k = (key.getModulus().bitLength() + 7) / 8;
        byte[] em = new byte[k];
        em[1] = 0x01;
        int t = SHA1_DIGEST_INFO.length + token.length;
        for (int i = 2; i < k - t - 1; i++) em[i] = (byte) 0xff;
        System.arraycopy(SHA1_DIGEST_INFO, 0, em, k - t, SHA1_DIGEST_INFO.length);
        System.arraycopy(token, 0, em, k - token.length, token.length);
        byte[] sig = new BigInteger(1, em).modPow(key.getPrivateExponent(), key.getModulus()).toByteArray();
        if (sig.length == k) return sig;
        byte[] fixed = new byte[k];
        System.arraycopy(sig, Math.max(0, sig.length - k), fixed, Math.max(0, k - sig.length), Math.min(k, sig.length));
        return fixed;
    }

    /** The key in adb's own format: mincrypt RSAPublicKey, base64, then a name. */
    private static byte[] publicKey(RSAPublicKey pub) {
        final int words = 64;
        BigInteger n = pub.getModulus(), r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 32).modPow(BigInteger.valueOf(2), n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words).putInt(n0inv.intValue());
        for (int i = 0; i < words; i++) b.putInt(n.shiftRight(32 * i).mod(r32).intValue());
        for (int i = 0; i < words; i++) b.putInt(rr.shiftRight(32 * i).mod(r32).intValue());
        b.putInt(pub.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " CineTV@" + android.os.Build.MODEL.replace(' ', '_') + "\0";
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static synchronized KeyPair keys(Context ctx) throws Exception {
        File priv = new File(ctx.getFilesDir(), "adbkey"), pub = new File(ctx.getFilesDir(), "adbkey.pub");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readAll(pub))), kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        try (FileOutputStream o = new FileOutputStream(priv)) {
            o.write(kp.getPrivate().getEncoded());
        }
        try (FileOutputStream o = new FileOutputStream(pub)) {
            o.write(kp.getPublic().getEncoded());
        }
        return kp;
    }

    private static byte[] readAll(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            new DataInputStream(in).readFully(b);
            return b;
        }
    }
}
