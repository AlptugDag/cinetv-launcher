package com.cinetv.launcher.trailer;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/**
 * High-performance OkHttp Downloader implementation for NewPipeExtractor.
 */
public final class OkHttpDownloader extends Downloader {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private final OkHttpClient client;

    public OkHttpDownloader(OkHttpClient client) {
        this.client = client;
    }

    public static OkHttpClient createClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        String httpMethod = request.httpMethod();
        String url = request.url();
        Map<String, List<String>> headers = request.headers();
        byte[] dataToSend = request.dataToSend();

        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);

        boolean hasUa = false;
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                for (String value : entry.getValue()) {
                    builder.addHeader(entry.getKey(), value);
                    if ("user-agent".equalsIgnoreCase(entry.getKey())) hasUa = true;
                }
            }
        }
        if (!hasUa) {
            builder.header("User-Agent", USER_AGENT);
        }

        RequestBody body = null;
        if (dataToSend != null) {
            body = RequestBody.create(dataToSend, null);
        } else if ("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)) {
            body = RequestBody.create(new byte[0], null);
        }
        builder.method(httpMethod, body);

        try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
            int code = response.code();
            String message = response.message();
            String latestUrl = response.request().url().toString();

            Map<String, List<String>> responseHeaders = new HashMap<>(response.headers().toMultimap());

            ResponseBody responseBody = response.body();
            String responseBodyString = responseBody != null ? responseBody.string() : "";

            if (code == 429) {
                throw new ReCaptchaException("reCaptcha / rate-limit challenge on YouTube", url);
            }

            return new Response(code, message, responseHeaders, responseBodyString, latestUrl);
        }
    }
}
