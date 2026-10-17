package dev.squirrel.vpn;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class ShadowQuicApi {
    private static final String BASE = "http://127.0.0.1:19090/api/v1";
    private final String secret;

    ShadowQuicApi(Context context) {
        secret = context.getSharedPreferences("runtime", Context.MODE_PRIVATE).getString("local_api_secret", "");
    }

    JSONObject get(String path) throws Exception { return request("GET", path, null); }
    JSONObject put(String path, JSONObject body) throws Exception { return request("PUT", path, body); }
    JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body); }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(1800);
        connection.setReadTimeout(3000);
        connection.setRequestProperty("Authorization", "Bearer " + secret);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = stream == null ? "{}" : read(stream);
        connection.disconnect();
        JSONObject json = new JSONObject(response);
        if (status < 200 || status >= 300) {
            JSONObject error = json.optJSONObject("error");
            throw new IOException(error == null ? "ShadowQUIC API returned HTTP " + status
                : error.optString("message", "ShadowQUIC API returned HTTP " + status));
        }
        return json;
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 2 * 1024 * 1024) throw new IOException("ShadowQUIC API response is too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static String segment(String value) { return android.net.Uri.encode(value); }
}
