package dev.squirrel.vpn;

import android.content.Context;
import java.net.URI;
import java.net.URISyntaxException;

final class AppSettings {
    static final String DEFAULT_PROBE_URL = "http://example.com/";
    static final int PROBE_TIMEOUT_MS = 10000;

    static String probeUrl(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("probe_url", DEFAULT_PROBE_URL);
    }

    static void saveProbeUrl(Context context, String value) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("probe_url", validateProbeUrl(value)).apply();
    }

    static String validateProbeUrl(String value) {
        String url = value.trim();
        try {
            URI parsed = new URI(url);
            if ("http".equalsIgnoreCase(parsed.getScheme()) && parsed.getHost() != null
                    && parsed.getHost().length() <= 255 && parsed.getRawUserInfo() == null
                    && parsed.getPort() >= -1 && parsed.getPort() <= 65535) return url;
        } catch (URISyntaxException ignored) { }
        throw new IllegalArgumentException("Use an absolute HTTP URL without credentials");
    }

    private AppSettings() {}
}
