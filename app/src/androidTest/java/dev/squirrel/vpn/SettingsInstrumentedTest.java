package dev.squirrel.vpn;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.viewpager2.widget.ViewPager2;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

@SuppressWarnings("deprecation")
public final class SettingsInstrumentedTest extends InstrumentationTestCase {
    private MainActivity main;
    private SettingsActivity settings;
    private ApiToolsActivity tools;
    private String previousUrl;
    private Context context;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        previousUrl = preferences.getString("probe_url", null);
        preferences.edit().remove("probe_url").commit();
    }

    public void testSettingsSaveValidationAndBackPreserveMainDraft() throws Exception {
        launchMain();
        onMain(() -> {
            ((ViewPager2) main.findViewById(R.id.pages)).setCurrentItem(MainActivity.PROXIES, false);
            ((EditText) main.findViewById(R.id.config_editor)).setText("# Unsaved settings navigation fixture\n");
            return null;
        });
        openSettings();
        assertEquals(AppSettings.DEFAULT_PROBE_URL, onMain(() -> address().getText().toString()));
        for (String invalid : new String[]{"", "https://probe.example.test/", "/relative",
                "http://user:password@probe.example.test/", "http://bad host/", "http://probe.example.test:99999/"}) {
            onMain(() -> { address().setText(invalid); settings.findViewById(R.id.settings_save).performClick(); return null; });
            assertNotNull(onMain(() -> address().getError()));
            assertEquals(AppSettings.DEFAULT_PROBE_URL, AppSettings.probeUrl(context));
        }
        String url = "http://probe.example.test/latency?source=settings";
        onMain(() -> { address().setText("  " + url + "  "); settings.findViewById(R.id.settings_save).performClick(); return null; });
        assertEquals(url, AppSettings.probeUrl(context));
        assertEquals(url, onMain(() -> address().getText().toString()));
        assertTrue(onMain(() -> ((TextView) settings.findViewById(R.id.settings_status)).getText().toString().startsWith("Saved")));
        onMain(() -> { settings.finish(); return null; });
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            assertEquals(MainActivity.PROXIES, ((ViewPager2) main.findViewById(R.id.pages)).getCurrentItem());
            assertEquals("# Unsaved settings navigation fixture\n", ((EditText) main.findViewById(R.id.config_editor)).getText().toString());
            return null;
        });
        openSettings();
        assertEquals(url, onMain(() -> address().getText().toString()));
        onMain(() -> { address().setText("http://draft.example.test/unsaved"); return null; });
        assertEquals("", onMain(() -> ((TextView) settings.findViewById(R.id.settings_status)).getText().toString()));
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(SettingsActivity.class.getName(), null, false);
        onMain(() -> { settings.recreate(); return null; });
        SettingsActivity replacement = (SettingsActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull(replacement);
        settings = replacement;
        assertEquals("http://draft.example.test/unsaved", onMain(() -> address().getText().toString()));
        assertEquals(url, AppSettings.probeUrl(context));
        onMain(() -> { settings.finish(); return null; });
        tools = (ApiToolsActivity) getInstrumentation().startActivitySync(new Intent(context, ApiToolsActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertEquals(url, onMain(() -> tools.probeUrl.getText().toString()));
        onMain(() -> { tools.probeUrl.setText("http://override.example.test/"); return null; });
        assertEquals(url, AppSettings.probeUrl(context));
    }

    public void testProbeButtonUsesLatestSavedUrlAndWaitsForCoreTimeout() throws Exception {
        assertFalse("Do not interrupt an active VPN", AppState.get().busy());
        launchMain();
        AppState.Snapshot previous = AppState.get();
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core);
                java.net.ServerSocket server = new java.net.ServerSocket(0)) {
            server.setSoTimeout(8000);
            NativeBridge.start(descriptor.getFd(), ConfigStore.forRuntime(context,
                "outbounds: [{tag: direct, type: direct}]\n"),
                new File(context.getCacheDir(), "settings-probe-test.log").getPath());
            ShadowQuicApi api = new ShadowQuicApi(context);
            JSONObject outbounds = null;
            for (int attempt = 0; attempt < 80; attempt++) {
                try { outbounds = api.get("/outbounds"); break; }
                catch (Exception ignored) { Thread.sleep(50); }
            }
            assertNotNull(outbounds);
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/latency?source=settings";
            // Save after MainActivity was created, as when returning from Settings.
            AppSettings.saveProbeUrl(context, url);
            CountDownLatch received = new CountDownLatch(1);
            AtomicReference<String> requestLine = new AtomicReference<>();
            AtomicReference<Throwable> serverError = new AtomicReference<>();
            Thread responder = new Thread(() -> {
                try (java.net.Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    requestLine.set(reader.readLine());
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    received.countDown();
                    Thread.sleep(3300); // Longer than the old REST client's 3s read timeout.
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                } catch (Throwable error) { serverError.set(error); }
            });
            responder.start();
            JSONObject snapshot = outbounds;
            onMain(() -> {
                Field handler = MainActivity.class.getDeclaredField("main"); handler.setAccessible(true);
                ((Handler) handler.get(main)).removeCallbacksAndMessages(null);
                AppState.set(AppState.Phase.CONNECTED, "Synthetic local probe fixture");
                Field field = MainActivity.class.getDeclaredField("diagnostics"); field.setAccessible(true);
                DiagnosticsUi ui = (DiagnosticsUi) field.get(main);
                ui.renderState(AppState.get(), false, "Synthetic fixture");
                ui.renderOutbounds(snapshot.getJSONArray("outbounds"));
                ((ViewPager2) main.findViewById(R.id.pages)).setCurrentItem(MainActivity.PROXIES, false);
                return null;
            });
            getInstrumentation().waitForIdleSync();
            long layoutDeadline = SystemClock.uptimeMillis() + 5000;
            View visibleProbe;
            do {
                visibleProbe = onMain(() -> findProbe(main.findViewById(R.id.proxy_rows)));
                if (visibleProbe == null) Thread.sleep(50);
            } while (visibleProbe == null && SystemClock.uptimeMillis() < layoutDeadline);
            View probe = visibleProbe;
            assertNotNull(probe);
            onMain(() -> { probe.performClick(); return null; });
            assertTrue("One tap must start the probe without a URL dialog", received.await(5, TimeUnit.SECONDS));
            assertEquals("GET /latency?source=settings HTTP/1.1", requestLine.get());
            onMain(() -> {
                assertFalse(probe.isEnabled());
                // Even a duplicate programmatic submission must not queue another request.
                java.lang.reflect.Method method = MainActivity.class.getDeclaredMethod("probeOutbound", String.class);
                method.setAccessible(true); method.invoke(main, "direct");
                return null;
            });
            long deadline = SystemClock.uptimeMillis() + 8000;
            while (SystemClock.uptimeMillis() < deadline && !onMain(() -> probe.isEnabled())) Thread.sleep(50);
            assertTrue(onMain(() -> probe.isEnabled()));
            responder.join(6000);
            assertFalse(responder.isAlive());
            assertNull(serverError.get());
            JSONObject result = api.get("/outbounds").getJSONArray("outbounds").getJSONObject(0)
                .getJSONObject("quality").getJSONObject("probe");
            assertEquals(url, result.getString("url"));
            assertEquals("ok", result.getString("status"));
            assertTrue(onMain(() -> main.findViewById(R.id.proxy_summary).getContentDescription().toString().contains("live")));
        } finally {
            NativeBridge.stop();
            AppState.set(previous.phase, previous.detail);
            Os.close(app); Os.close(core);
        }
    }

    public void testReviewScreenshots() throws Exception {
        settings = (SettingsActivity) getInstrumentation().startActivitySync(new Intent(context, SettingsActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertTrue(onMain(() -> (settings.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0));
        onMain(() -> { address().setText("http://probe.example.test/latency"); return null; });
        capture("settings");
        onMain(() -> { address().setText("https://probe.example.test/"); settings.findViewById(R.id.settings_save).performClick(); return null; });
        capture("settings-error");
        onMain(() -> { address().setText("http://probe.example.test/latency"); settings.findViewById(R.id.settings_save).performClick(); return null; });
        capture("settings-saved");
        Configuration config = settings.getResources().getConfiguration();
        if (config.fontScale <= 1.1f && config.orientation == Configuration.ORIENTATION_PORTRAIT
                && (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES) {
            String original = shell("settings get secure show_ime_with_hard_keyboard").trim();
            try {
                shell("settings put secure show_ime_with_hard_keyboard 1");
                int height = onMain(() -> settings.findViewById(R.id.settings_scroll).getHeight());
                long deadline = SystemClock.uptimeMillis() + 4000;
                do {
                    onMain(() -> {
                        address().requestFocus();
                        settings.getSystemService(android.view.inputmethod.InputMethodManager.class)
                            .showSoftInput(address(), android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                        return null;
                    });
                    Thread.sleep(100);
                } while (SystemClock.uptimeMillis() < deadline
                    && onMain(() -> settings.findViewById(R.id.settings_scroll).getHeight()) >= height - 100);
                assertTrue("IME must reduce the settings viewport",
                    onMain(() -> settings.findViewById(R.id.settings_scroll).getHeight()) < height - 100);
                onMain(() -> {
                    View save = settings.findViewById(R.id.settings_save);
                    android.graphics.Rect rect = new android.graphics.Rect();
                    save.getDrawingRect(rect);
                    save.requestRectangleOnScreen(rect, true);
                    return null;
                });
                getInstrumentation().waitForIdleSync();
                onMain(() -> {
                    View save = settings.findViewById(R.id.settings_save);
                    android.graphics.Rect rect = new android.graphics.Rect();
                    assertTrue(save.getGlobalVisibleRect(rect));
                    assertEquals(save.getHeight(), rect.height());
                    return null;
                });
                capture("settings-keyboard");
                onMain(() -> { address().onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE); return null; });
                assertEquals("http://probe.example.test/latency", AppSettings.probeUrl(context));
            } finally {
                onMain(() -> {
                    settings.getSystemService(android.view.inputmethod.InputMethodManager.class)
                        .hideSoftInputFromWindow(address().getWindowToken(), 0);
                    return null;
                });
                shell(original.equals("null") ? "settings delete secure show_ime_with_hard_keyboard"
                    : "settings put secure show_ime_with_hard_keyboard " + (original.equals("1") ? "1" : "0"));
            }
        }
    }

    private void launchMain() throws Exception {
        main = (MainActivity) getInstrumentation().startActivitySync(new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline && !onMain(() -> main.findViewById(R.id.config_editor).isEnabled())) Thread.sleep(50);
        assertTrue(onMain(() -> main.findViewById(R.id.config_editor).isEnabled()));
    }

    private void openSettings() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < deadline && !onMain(main::hasWindowFocus)) Thread.sleep(30);
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(SettingsActivity.class.getName(), null, false);
        onMain(() -> { main.findViewById(R.id.api_tools_menu).performClick(); return null; });
        boolean opened = false;
        deadline = SystemClock.uptimeMillis() + 3000;
        while (!opened && SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("Settings")) {
                AccessibilityNodeInfo target = node;
                for (int depth = 0; target != null && depth < 5; depth++, target = target.getParent()) {
                    if (target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { opened = true; break; }
                }
                if (opened) break;
            }
            if (!opened) Thread.sleep(30);
        }
        assertTrue("Settings must be available in the header overflow", opened);
        settings = (SettingsActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull(settings);
    }

    private View findProbe(View view) {
        if (view.getContentDescription() != null && view.getContentDescription().toString().startsWith("Probe latency for ")) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findProbe(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private EditText address() { return settings.findViewById(R.id.settings_probe_url); }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
                java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    private void capture(String name) throws Exception {
        // This activity contains only the known synthetic URL, never real profile content.
        onMain(() -> { settings.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); return null; });
        try {
            Thread.sleep(350);
            getInstrumentation().waitForIdleSync();
            Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            Configuration config = settings.getResources().getConfiguration();
            String suffix = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "-dark" : "";
            if (config.fontScale > 1.1f) suffix += "-large-text";
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) suffix += "-landscape";
            File directory = new File(context.getExternalFilesDir(null), "settings-review");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, name + suffix + ".png"))) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            screenshot.recycle();
        } finally {
            onMain(() -> { settings.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); return null; });
        }
    }

    private <T> T onMain(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { result.set(action.call()); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() instanceof Error) throw (Error) failure.get();
        if (failure.get() != null) throw new Exception(failure.get());
        return result.get();
    }

    @Override protected void tearDown() throws Exception {
        try {
            onMain(() -> {
                for (Activity activity : new Activity[]{settings, tools, main}) if (activity != null) activity.finish();
                return null;
            });
            SharedPreferences.Editor edit = context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit();
            if (previousUrl == null) edit.remove("probe_url"); else edit.putString("probe_url", previousUrl);
            edit.commit();
        } finally { super.tearDown(); }
    }
}
