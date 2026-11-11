package com.cinetv.launcher.remote;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.cinetv.launcher.service.Http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

/**
 * HTTPS for the phone page without a "not secure" warning. local-ip.sh resolves names like
 * 192-168-1-128.local-ip.sh to that address and publishes a real Let's Encrypt certificate for
 * *.local-ip.sh (its key is public by design: it only proves the name, which is all a page on the
 * home network needs). The TV fetches both once, refreshes them before they expire, and serves the
 * phone page at https://&lt;its-ip-with-dashes&gt;.local-ip.sh:8443.
 */
public final class TlsCert {

    private static final String TAG = "CineTvTls";
    private static final String DOMAIN = "local-ip.sh";
    private static final String CERT_URL = "https://local-ip.sh/server.pem";
    private static final String KEY_URL = "https://local-ip.sh/server.key";
    private static final long REFRESH_BEFORE_MS = 20L * 24 * 60 * 60 * 1000;

    private TlsCert() {}

    /** The secure page address for this TV, e.g. https://192-168-1-128.local-ip.sh:8443 . */
    public static String secureBase(String ip, int port) {
        return ip == null ? null : "https://" + ip.replace('.', '-') + "." + DOMAIN + ":" + port;
    }

    public static boolean isSecureHost(String host, String ip) {
        return ip != null && host != null && host.equalsIgnoreCase(ip.replace('.', '-') + "." + DOMAIN);
    }

    /** Blocks (network on first use or refresh). Null when HTTPS cannot be offered. */
    public static SSLServerSocketFactory factory(Context ctx) {
        try {
            File dir = new File(ctx.getFilesDir(), "tls");
            File certFile = new File(dir, "server.pem"), keyFile = new File(dir, "server.key");
            if (!certFile.isFile() || !keyFile.isFile() || expiresSoon(certFile)) {
                dir.mkdirs();
                Http.download(CERT_URL, certFile, 15000);
                Http.download(KEY_URL, keyFile, 15000);
            }
            if (!certFile.isFile() || !keyFile.isFile()) return null;
            List<Certificate> chain = certificates(certFile);
            PrivateKey key = privateKey(new String(Files.readAllBytes(keyFile.toPath()), StandardCharsets.US_ASCII));
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setKeyEntry("cinetv", key, new char[0], chain.toArray(new Certificate[0]));
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(kmf.getKeyManagers(), null, null);
            return tls.getServerSocketFactory();
        } catch (Throwable t) {
            Log.w(TAG, "HTTPS unavailable: " + t);
            return null;
        }
    }

    private static boolean expiresSoon(File certFile) {
        try {
            X509Certificate leaf = (X509Certificate) certificates(certFile).get(0);
            return leaf.getNotAfter().getTime() - System.currentTimeMillis() < REFRESH_BEFORE_MS;
        } catch (Throwable t) {
            return true;
        }
    }

    private static List<Certificate> certificates(File pem) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> all = cf.generateCertificates(new ByteArrayInputStream(Files.readAllBytes(pem.toPath())));
        return new ArrayList<>(all);
    }

    /** PKCS#8 as is; PKCS#1 ("BEGIN RSA PRIVATE KEY") wrapped into PKCS#8 first. */
    static PrivateKey privateKey(String pem) throws Exception {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String b64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.decode(b64, Base64.DEFAULT);
        if (pkcs1) der = wrapPkcs1(der);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static byte[] wrapPkcs1(byte[] pkcs1) throws Exception {
        // PrivateKeyInfo ::= SEQUENCE { INTEGER 0, AlgorithmIdentifier rsaEncryption NULL, OCTET STRING pkcs1 }
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithm = {0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octets = tlv(0x04, pkcs1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(version);
        body.write(algorithm);
        body.write(octets);
        return tlv(0x30, body.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int n = value.length;
        if (n < 128) {
            out.write(n);
        } else {
            int bytes = n > 0xFFFFFF ? 4 : n > 0xFFFF ? 3 : n > 0xFF ? 2 : 1;
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) out.write((n >> (8 * i)) & 0xFF);
        }
        out.write(value, 0, n);
        return out.toByteArray();
    }
}
