package dev.squirrel.vpn;

import android.content.Context;
import java.io.IOException;
import org.json.JSONArray;

/** One process-wide cleanup; recreation observes its result without replaying deletion. */
final class StorageCleanup {
    static final class Operation {
        volatile boolean finished;
        int removed;
        String error;
    }
    private static Operation active;

    static synchronized Operation pending() { return active != null && !active.finished ? active : null; }

    static synchronized Operation start(Context context) {
        if (pending() != null) return active;
        Operation operation = new Operation();
        active = operation;
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try { operation.removed = clear(app); }
            catch (Exception | LinkageError error) { operation.error = SquirrelVpnService.message(error); }
            finally { operation.finished = true; }
        }, "storage-cleanup").start();
        return operation;
    }

    static int clear(Context context) throws IOException {
        if (AppState.get().busy()) throw new IOException("Disconnect the VPN before clearing databases and UI HTML.");
        JSONArray configurations = new JSONArray();
        synchronized (ConfigProfiles.class) {
            for (ConfigProfiles.Profile profile : ConfigProfiles.list(context).profiles) {
                configurations.put(ConfigProfiles.read(context, profile.id));
            }
            return NativeBridge.clearDatabases(configurations.toString(), context.getFilesDir().getAbsolutePath());
        }
    }

    private StorageCleanup() {}
}
