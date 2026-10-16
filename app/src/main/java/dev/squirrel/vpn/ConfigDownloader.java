package dev.squirrel.vpn;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** One cancellable download, using the platform's normal TLS verification. */
final class ConfigDownloader {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile HttpURLConnection connection;

    String download(String address) throws IOException {
        URL url;
        try { url = new URI(address.trim()).toURL(); }
        catch (URISyntaxException | IllegalArgumentException e) {
            throw new IOException("Enter a complete HTTP or HTTPS URL", e);
        }
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!(url.getProtocol().equals("https") || url.getProtocol().equals("http"))
                || url.getHost().isEmpty() || url.getUserInfo() != null) {
                throw new IOException("Use an HTTP or HTTPS URL without embedded username/password");
            }
            checkCancelled();
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            connection = request;
            try {
                checkCancelled();
                request.setConnectTimeout(10000);
                request.setReadTimeout(15000);
                request.setInstanceFollowRedirects(false);
                request.setUseCaches(false);
                request.setRequestProperty("Accept", "application/yaml, text/yaml, text/plain, */*");
                int status = request.getResponseCode();
                checkCancelled();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = request.getHeaderField("Location");
                    if (location == null || location.isEmpty()) throw new IOException("Redirect has no destination URL");
                    URL next = new URL(url, location);
                    if (url.getProtocol().equals("https") && next.getProtocol().equals("http")) {
                        throw new IOException("The configuration URL redirects from HTTPS to HTTP");
                    }
                    url = next;
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("Configuration server returned HTTP " + status);
                if (request.getContentLengthLong() > ConfigStore.MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB");
                try (InputStream stream = request.getInputStream()) {
                    String text = ConfigStore.readText(stream);
                    checkCancelled();
                    return text;
                }
            } finally {
                request.disconnect();
                connection = null;
            }
        }
        throw new IOException("Too many configuration URL redirects");
    }

    void cancel() {
        cancelled.set(true);
        HttpURLConnection request = connection;
        if (request != null) request.disconnect();
    }

    private void checkCancelled() throws IOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new IOException("Download cancelled");
    }
}
