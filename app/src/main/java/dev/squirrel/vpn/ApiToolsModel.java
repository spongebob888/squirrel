package dev.squirrel.vpn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Retained worker, with application context only. Commands are never replayed on recreation. */
final class ApiToolsModel {
    interface RemoteClient {
        String call(String config, String tag, String operation, String user, String password) throws Exception;
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final RemoteClient remote;
    private Runnable observer;
    private boolean closed;
    private String config = "";
    ConfigProfiles.State profiles;
    String profileId, profileName = "Choose a saved profile", tag = "";
    JSONArray outbounds = new JSONArray();
    boolean busy, error, interrupted;
    String status = "Choose an API operation.", result = "No request sent.", resultTitle = "Result";

    ApiToolsModel(Context context) { this(context, NativeBridge::adminRequest); }
    ApiToolsModel(Context context, RemoteClient remote) {
        this.context = context.getApplicationContext();
        this.remote = remote;
    }

    void observe(Runnable observer) { this.observer = observer; if (observer != null) observer.run(); }
    void detach() { observer = null; }
    void close() { closed = true; observer = null; worker.shutdown(); }

    void loadProfile(String id) {
        if (busy) return;
        begin("Reading saved configuration…");
        worker.execute(() -> {
            try {
                ConfigProfiles.State state = ConfigProfiles.list(context);
                String selected = id == null ? state.selectedId : id;
                ConfigProfiles.Profile profile = state.find(selected);
                if (profile == null) throw new IOException("Configuration no longer exists");
                post(() -> { profiles = state; profileId = selected; profileName = profile.name; changed(); });
                String text = ConfigProfiles.read(context, selected);
                JSONArray found = new JSONObject(NativeBridge.adminOutbounds(text)).getJSONArray("outbounds");
                post(() -> {
                    profiles = state;
                    profileId = selected;
                    profileName = profile.name;
                    config = text;
                    outbounds = found;
                    boolean same = false;
                    for (int i = 0; i < found.length(); i++) if (tag.equals(found.optJSONObject(i).optString("tag"))) same = true;
                    if (!same) tag = found.length() == 0 ? "" : found.optJSONObject(0).optString("tag");
                    result = interrupted ? "A previous request was interrupted. Check the server state before retrying; changes may already have been applied."
                        : "No request sent for this saved profile.";
                    resultTitle = "Result";
                    finish(interrupted ? result : found.length() == 0 ? "No ShadowQUIC or SunnyQUIC outbounds in this profile. Choose another profile."
                        : "Saved configuration loaded. Select the server outbound.", interrupted);
                    interrupted = false;
                });
            } catch (Exception | LinkageError e) {
                post(() -> { config = ""; tag = ""; outbounds = new JSONArray();
                    result = "No request sent. Fix this saved profile or choose another one."; resultTitle = "Result";
                    finish(message(e), true); });
            }
        });
    }

    void chooseOutbound(String tag) {
        if (busy) return;
        this.tag = tag;
        result = "No request sent for this server.";
        resultTitle = "Result";
        finish("Server outbound selected.", false);
    }

    boolean canAdminister() {
        for (int i = 0; i < outbounds.length(); i++) {
            JSONObject item = outbounds.optJSONObject(i);
            if (tag.equals(item.optString("tag"))) return item.optBoolean("admin");
        }
        return false;
    }

    void remote(String operation, String username, String password) {
        if (busy) return;
        if (config.isEmpty() || tag.isEmpty()) { finish("Choose a saved profile and server outbound first.", true); return; }
        if (!canAdminister()) { finish("The configured outbound username must start with admin.", true); return; }
        final String target = tag, text = config, profile = profileName;
        begin("Running " + operation + " on " + target + "…");
        resultTitle = operation + " · " + identifier(target) + " · " + profile;
        result = "Request in progress…";
        changed();
        worker.execute(() -> {
            try {
                JSONObject response = new JSONObject(remote.call(text, target, operation, username, password));
                post(() -> { result = formatRemote(operation, username, response); finish("Completed. Remote server response.", false); });
            } catch (Exception | LinkageError e) {
                String safe = message(e);
                if (!password.isEmpty()) safe = safe.replace(password, "[redacted]");
                final String failure = safe;
                post(() -> { result = "The request did not return a successful response.\n\n" + failure; finish(failure, true); });
            }
        });
    }

    void local(String method, String path, JSONObject body, int timeoutMs) {
        if (busy) return;
        AppState.Snapshot session = AppState.get();
        if (session.phase != AppState.Phase.CONNECTED) { finish("Connect the VPN to use the local runtime API.", true); return; }
        begin("Requesting local runtime…");
        resultTitle = method + " " + path;
        result = "Request in progress…";
        changed();
        worker.execute(() -> {
            try {
                if (AppState.get() != session) throw new IOException("The VPN session changed. Run the request again.");
                JSONObject response = new ShadowQuicApi(context).request(method, path, body, timeoutMs);
                String pretty = response.toString(2);
                post(() -> {
                    if (AppState.get() != session) { result = "Response discarded because the VPN session changed."; finish(result, true); }
                    else { result = pretty; finish("Completed. Local runtime snapshot.", false); }
                });
            } catch (Exception e) { post(() -> { result = message(e); finish(result, true); }); }
        });
    }

    private void begin(String text) { busy = true; error = false; status = text; changed(); }
    private void finish(String text, boolean failed) { busy = false; status = text; error = failed; changed(); }
    private void changed() { if (observer != null) observer.run(); }
    private void post(Runnable action) { main.post(() -> { if (!closed) action.run(); }); }
    private static String message(Throwable e) { String text = e.getMessage(); return text == null ? "API request failed" : text; }

    static String formatRemote(String operation, String user, JSONObject value) {
        JSONArray users = value.optJSONArray("users");
        if (users != null) {
            if (users.length() == 0) return "No users returned by this server.";
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < users.length(); i++) {
                if (i > 0) text.append('\n');
                text.append(identifier(users.optString(i)));
            }
            return text.toString();
        }
        JSONArray stats = value.optJSONArray("stats");
        if (stats != null) {
            if (stats.length() == 0) return "No user statistics returned by this server.";
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < stats.length(); i++) {
                JSONObject s = stats.optJSONObject(i);
                if (s == null) continue;
                if (text.length() > 0) text.append("\n\n");
                text.append("User: ").append(identifier(s.optString("username", user)))
                    .append("\nQUIC connections: ").append(s.optString("conn_num", "—"))
                    .append("\nTCP streams: ").append(s.optString("tcp_conns", "—"))
                    .append("\nUDP associations: ").append(s.optString("udp_conns", "—"))
                    .append("\nTCP sent: ").append(s.optString("tcp_sent", "—")).append(" B")
                    .append("\nTCP received: ").append(s.optString("tcp_recv", "—")).append(" B")
                    .append("\nUDP sent: ").append(s.optString("udp_sent", "—")).append(" B")
                    .append("\nUDP received: ").append(s.optString("udp_recv", "—")).append(" B");
            }
            return text + "\n\nSent: server to client.\nReceived: client to server.";
        }
        if (!value.optBoolean("ok")) return "Unexpected server API response.";
        switch (operation) {
            case "add-user": return "User added or password updated: " + identifier(user);
            case "remove-user": return "User removed and connections closed: " + identifier(user);
            case "kill-conn": return "Connections closed: " + identifier(user) + "\nThe user can reconnect with the same credentials.";
            case "clear-stats": return (user.isEmpty() ? "All users'" : "User " + identifier(user) + "'s") + " traffic byte counters cleared.\nLive connection counts are unchanged.";
            default: return "Completed.";
        }
    }

    /** JSON spelling makes exact keys, including invisible whitespace, unambiguous. */
    static String identifier(String value) {
        String quoted = JSONObject.quote(value);
        StringBuilder visible = new StringBuilder();
        for (int i = 0; i < quoted.length(); i++) {
            char c = quoted.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) visible.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
            else visible.append(c);
        }
        return visible.toString();
    }

    static boolean hasWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isWhitespace(value.charAt(i)) || Character.isSpaceChar(value.charAt(i))) return true;
        return false;
    }
}
