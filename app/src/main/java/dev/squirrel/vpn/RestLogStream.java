package dev.squirrel.vpn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** Collects the core's live REST log stream for retained viewing and export. */
final class RestLogStream implements AutoCloseable {
    private final Context context;
    private final Request request;
    private final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(1800, TimeUnit.MILLISECONDS).readTimeout(3000, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    private final Handler retries = new Handler(Looper.getMainLooper());
    private final Runnable reconnect = this::connect;
    private WebSocket socket;
    private boolean closed, connected;
    private int attempts;
    private String lastError = "";

    RestLogStream(Context context, ShadowQuicApi api) throws java.io.IOException {
        this.context = context.getApplicationContext();
        // Resolve the native session's address and secret on the service worker,
        // never from the main-thread reconnect callback.
        request = api.logStreamRequest();
        connect();
    }

    private synchronized void connect() {
        if (closed || socket != null) return;
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket current, Response response) {
                synchronized (RestLogStream.this) {
                    if (closed || socket != current) { current.cancel(); return; }
                    connected = true;
                    attempts = 0;
                    lastError = "";
                    AppLog.write(context, "REST log stream connected");
                }
            }

            @Override public void onMessage(WebSocket current, String text) {
                synchronized (RestLogStream.this) {
                    if (closed || socket != current) return;
                    try { AppLog.writeRest(context, text); }
                    catch (org.json.JSONException error) { AppLog.write(context, "Ignored an invalid REST log record"); }
                    catch (java.io.IOException error) { failed(current, "Cannot retain REST logs. Retrying."); }
                }
            }

            @Override public void onClosing(WebSocket current, int code, String reason) {
                current.close(code, null);
            }

            @Override public void onClosed(WebSocket current, int code, String reason) {
                failed(current, "REST log stream closed. Retrying.");
            }

            @Override public void onFailure(WebSocket current, Throwable error, Response response) {
                failed(current, response == null ? "REST log stream unavailable. Retrying."
                    : "REST log stream returned HTTP " + response.code() + ". Retrying.");
            }
        });
    }

    private synchronized void failed(WebSocket current, String message) {
        if (closed || socket != current) return;
        connected = false;
        socket = null;
        current.cancel();
        if (!lastError.equals(message)) { AppLog.write(context, message); lastError = message; }
        retries.removeCallbacks(reconnect);
        retries.postDelayed(reconnect, Math.min(5000, 250L << Math.min(attempts++, 5)));
    }

    synchronized boolean isConnected() { return connected; }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        connected = false;
        retries.removeCallbacksAndMessages(null);
        if (socket != null) { socket.cancel(); socket = null; }
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
