package dev.squirrel.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SquirrelVpnService extends VpnService {
    static final String START = "dev.squirrel.vpn.START";
    static final String STOP = "dev.squirrel.vpn.STOP";
    private static final String CHANNEL = "vpn";
    private static final int NOTIFICATION = 1;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started;

    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "VPN connection", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            requestStop();
            if (!started) stopSelf();
            return START_NOT_STICKY;
        }
        if (!START.equals(intent.getAction()) || started) return START_NOT_STICKY;
        started = true;
        stopping.set(false);
        AppState.set(AppState.Phase.CONNECTING, "Starting ShadowQUIC");
        try {
            Notification notification = notification("Connecting…");
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else startForeground(NOTIFICATION, notification);
            worker.execute(this::runVpn);
        } catch (Exception e) {
            AppState.set(AppState.Phase.ERROR, message(e));
            AppLog.write(this, "Cannot start VPN: " + message(e));
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void runVpn() {
        ParcelFileDescriptor tunnel = null;
        String failure = null;
        try {
            String config = ConfigStore.readForVpn(this);
            JSONObject settings = new JSONObject(NativeBridge.validate(config));
            if (stopping.get()) return;
            if (VpnService.prepare(this) != null) throw new IOException("VPN permission was revoked. Connect again.");
            // Keep ::/0 captured when IPv6 is disabled; the core drops it.
            // An IPv6 DNS server must not enable traffic to bypass the VPN.
            Builder builder = new Builder().setSession("Squirrel · ShadowQUIC")
                .setMtu(settings.getInt("mtu"))
                .addAddress(settings.getString("ipv4"), settings.getInt("ipv4Prefix"))
                .addRoute("0.0.0.0", 0).addRoute("::", 0)
                .setBlocking(false)
                .setConfigureIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            if (!settings.isNull("ipv6")) {
                builder.addAddress(settings.getString("ipv6"), settings.getInt("ipv6Prefix"));
            }
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);
            // A bypass whitelist: ALL sockets from our UID, including Rust and DNS,
            // use the underlying network instead of re-entering this TUN.
            // Fail the connection if this exclusion cannot be installed.
            builder.addDisallowedApplication(getPackageName());
            JSONArray dns = settings.getJSONArray("dns");
            for (int i = 0; i < dns.length(); i++) builder.addDnsServer(dns.getString(i));
            tunnel = builder.establish();
            if (tunnel == null) throw new IOException("Android did not establish the VPN interface");
            AppLog.write(this, "VPN interface established; bypass whitelist: " + getPackageName());
            NativeBridge.start(tunnel.getFd(), config, new File(AppLog.directory(this), "core.log").getPath());
            if (!stopping.get()) {
                AppState.set(AppState.Phase.CONNECTED, "VPN active · this app bypasses the tunnel");
                main.post(() -> {
                    if (!stopping.get()) getSystemService(NotificationManager.class)
                        .notify(NOTIFICATION, notification("VPN active · app bypass enabled"));
                });
                AppLog.write(this, "ShadowQUIC packet engine running");
            }
            while (!stopping.get()) {
                String status = NativeBridge.status();
                if (!status.isEmpty()) throw new IOException(status);
                Thread.sleep(500);
            }
        } catch (Exception | LinkageError e) {
            if (!stopping.get()) {
                failure = message(e);
                AppLog.write(this, "VPN error: " + failure);
            }
        } finally {
            try { NativeBridge.stop(); }
            catch (Exception | LinkageError e) { AppLog.write(this, "Core cleanup: " + message(e)); }
            if (tunnel != null) {
                try { tunnel.close(); } catch (IOException e) { AppLog.write(this, "Interface cleanup: " + message(e)); }
            }
            AppLog.write(this, "VPN interface closed");
            final String error = failure;
            main.post(() -> {
                AppState.set(error == null ? AppState.Phase.DISCONNECTED : AppState.Phase.ERROR,
                    error == null ? "Disconnected" : error);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
        }
    }

    private Notification notification(String detail) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, SquirrelVpnService.class).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setContentTitle("Squirrel VPN").setContentText(detail)
            .setSmallIcon(R.drawable.ic_squirrel).setContentIntent(open).setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(new Notification.Action.Builder(null, "Disconnect", stop).build()).build();
    }

    private void requestStop() {
        stopping.set(true);
        if (started) AppState.set(AppState.Phase.STOPPING, "Closing VPN");
    }
    @Override public void onRevoke() { requestStop(); super.onRevoke(); }
    @Override public void onDestroy() {
        stopping.set(true);
        worker.shutdown();
        super.onDestroy();
    }
    static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
