package dev.squirrel.vpn;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

@SuppressWarnings("deprecation")
public final class ApiToolsInstrumentedTest extends InstrumentationTestCase {
    private static final String CONFIG = "outbounds: [{tag: review-server, type: shadowquic, addr: '127.0.0.1:9', username: admin_review, password: synthetic-only, server-name: localhost}]\n";
    private static final String STATS = "{\"stats\":[{\"username\":\"alice\",\"conn_num\":2,\"tcp_conns\":3,\"udp_conns\":1,\"tcp_sent\":4096,\"tcp_recv\":2048,\"udp_sent\":777,\"udp_recv\":512}]}";
    private ApiToolsActivity activity;
    private String fixtureId, previousId;

    public void testOverflowOpensStandaloneToolsAndBackKeepsMainTab() throws Exception {
        MainActivity main = (MainActivity) getInstrumentation().startActivitySync(new Intent(
            getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            long focusDeadline = SystemClock.uptimeMillis() + 3000;
            while (!main.getWindow().getDecorView().hasWindowFocus() && SystemClock.uptimeMillis() < focusDeadline) Thread.sleep(30);
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> {
                ((androidx.viewpager2.widget.ViewPager2) main.findViewById(R.id.pages)).setCurrentItem(MainActivity.PROXIES, false);
                main.findViewById(R.id.api_tools_menu).performClick();
            });
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(ApiToolsActivity.class.getName(), null, false);
            long deadline = SystemClock.uptimeMillis() + 3000; boolean opened = false;
            while (SystemClock.uptimeMillis() < deadline && !opened) {
                AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText("API tools")) {
                    AccessibilityNodeInfo target = node;
                    for (int depth = 0; target != null && depth < 5; depth++, target = target.getParent()) {
                        if (target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { opened = true; break; }
                    }
                    if (opened) break;
                }
                if (!opened) Thread.sleep(30);
            }
            assertTrue(opened);
            activity = (ApiToolsActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            getInstrumentation().removeMonitor(monitor); assertNotNull(activity); awaitIdle();
            onMain(() -> { activity.finish(); return null; }); getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> {
                androidx.viewpager2.widget.ViewPager2 pager = main.findViewById(R.id.pages);
                assertEquals(5, pager.getAdapter().getItemCount()); assertEquals(MainActivity.PROXIES, pager.getCurrentItem());
            });
        } finally { getInstrumentation().runOnMainSync(main::finish); }
    }

    public void testInvalidSavedProfileCanRecoverAndPasswordErrorsAreRedacted() throws Exception {
        launch((config, tag, op, user, password) -> { throw new java.io.IOException("Synthetic transport failure " + password); });
        ConfigProfiles.save(getInstrumentation().getTargetContext(), fixtureId, "outbounds: []\n");
        onMain(() -> { activity.model.loadProfile(fixtureId); return null; }); awaitIdle();
        assertTrue(onMain(() -> activity.profile.isEnabled())); assertFalse(onMain(() -> activity.run.isEnabled()));
        ConfigProfiles.save(getInstrumentation().getTargetContext(), fixtureId, CONFIG);
        onMain(() -> { activity.model.loadProfile(fixtureId); return null; }); awaitIdle();
        assertTrue(onMain(() -> activity.run.isEnabled())); operation(1);
        onMain(() -> { activity.username.setText("alice"); activity.password.setText("synthetic-password"); activity.run.performClick(); return null; });
        dialogButton(true); awaitIdle();
        assertTrue(onMain(() -> activity.model.error)); assertFalse(onMain(() -> activity.model.result.contains("synthetic-password")));
        assertFalse(onMain(() -> activity.model.status.contains("synthetic-password")));
    }

    public void testRemoteCommandsValidateAndConfirmExactScope() throws Exception {
        AtomicInteger calls = new AtomicInteger(); AtomicReference<String> last = new AtomicReference<>();
        launch((config, tag, op, user, password) -> {
            assertFalse(LooperCheck.isMainThread()); calls.incrementAndGet(); last.set(op + ":" + user);
            if (op.equals("get-stats")) return STATS;
            if (op.equals("list-users")) return "{\"users\":[\"admin_review\",\"alice\"]}";
            return "{\"ok\":true}";
        });
        onMain(() -> { activity.run.performClick(); return null; }); awaitIdle(); assertEquals("list-users:", last.get());
        operation(4);
        onMain(() -> { activity.run.performClick(); return null; }); assertEquals(1, calls.get());
        onMain(() -> { activity.allUsers.setChecked(true); activity.run.performClick(); return null; }); awaitIdle();
        assertEquals("get-stats:", last.get()); assertTrue(onMain(() -> activity.result.getText().toString().contains("TCP sent: 4096 B")));
        for (int op : new int[]{1, 2, 3, 5}) {
            operation(op);
            onMain(() -> { activity.allUsers.setChecked(false); activity.username.setText("alice"); activity.password.setText("synthetic-password"); activity.run.performClick(); return null; });
            int before = calls.get(); dialogButton(false); assertEquals(before, calls.get());
            onMain(() -> { activity.password.setText("synthetic-password"); activity.run.performClick(); return null; });
            dialogButton(true); awaitIdle(); assertEquals(ApiToolsActivity.REMOTE[op] + ":alice", last.get());
            assertEquals("", onMain(() -> activity.password.getText().toString()));
        }
        operation(5);
        onMain(() -> { activity.allUsers.setChecked(true); activity.run.performClick(); return null; }); dialogButton(true); awaitIdle();
        assertEquals("clear-stats:", last.get()); assertTrue(onMain(() -> activity.result.getText().toString().contains("All users'")));
        assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
    }

    public void testIdentifiersStayExactAndWhitespaceIsVisible() throws Exception {
        java.util.List<String> targets = new java.util.concurrent.CopyOnWriteArrayList<>();
        launch((config, tag, op, user, password) -> { targets.add(tag + ":" + user); return "{\"ok\":true}"; });
        ConfigProfiles.save(getInstrumentation().getTargetContext(), fixtureId, CONFIG.replace("tag: review-server", "tag: ' review-server '"));
        onMain(() -> { activity.model.loadProfile(fixtureId); return null; }); awaitIdle(); operation(2);
        onMain(() -> {
            activity.model.profileName = "Synthetic admin profile"; activity.render();
            activity.username.setText(" alice "); return null;
        });
        assertEquals("\"\\u0020review-server\\u0020\"", onMain(() -> activity.outbound.getText().toString()));
        capture("identifier-target");
        onMain(() -> { activity.run.performClick(); return null; });
        getInstrumentation().waitForIdleSync();
        AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
        assertNotNull(root); assertFalse(root.findAccessibilityNodeInfosByText("\\u0020alice\\u0020").isEmpty());
        capture("identifier-confirm"); dialogButton(true); awaitIdle();
        assertEquals(" review-server : alice ", targets.get(0));
        assertTrue(onMain(() -> activity.model.result.contains("\"\\u0020alice\\u0020\"")));
        onMain(() -> { activity.username.setText("alice"); activity.run.performClick(); return null; }); dialogButton(true); awaitIdle();
        assertEquals(" review-server :alice", targets.get(1));
        assertEquals("\"\\u0020alice\\u0020\"\n\"alice\"\n\"bob\\u0020\"", ApiToolsModel.formatRemote("list-users", "",
            new JSONObject().put("users", new JSONArray().put(" alice ").put("alice").put("bob "))));
        assertTrue(ApiToolsModel.formatRemote("get-stats", " alice ", new JSONObject(STATS.replace("alice", " alice "))).contains("User: \"\\u0020alice\\u0020\""));
        operation(4); onMain(() -> { activity.allUsers.setChecked(false); activity.username.setText(""); activity.run.performClick(); return null; });
        assertEquals(2, targets.size());
    }

    public void testInFlightMutationSurvivesRecreationWithoutReplayOrPasswordRestore() throws Exception {
        AtomicInteger calls = new AtomicInteger(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        launch((config, tag, op, user, password) -> {
            calls.incrementAndGet(); entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); return "{\"ok\":true}";
        });
        try {
            operation(1);
            onMain(() -> { activity.username.setText("alice"); activity.password.setText("synthetic-password"); activity.run.performClick(); return null; }); dialogButton(true);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            ApiToolsModel retained = onMain(() -> activity.model);
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(ApiToolsActivity.class.getName(), null, false);
            onMain(() -> { activity.recreate(); return null; });
            ApiToolsActivity replacement = (ApiToolsActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            getInstrumentation().removeMonitor(monitor); assertNotNull(replacement); activity = replacement;
            getInstrumentation().waitForIdleSync();
            assertSame(retained, onMain(() -> activity.model)); assertEquals(1, calls.get());
            assertFalse(onMain(() -> activity.run.isEnabled())); assertEquals("", onMain(() -> activity.password.getText().toString()));
            release.countDown(); awaitIdle(); assertEquals(1, calls.get());
            assertTrue(onMain(() -> activity.result.getText().toString().contains("alice")));
        } finally { release.countDown(); }
    }

    public void testSoftwareKeyboardKeepsTheFormActionReachable() throws Exception {
        launch((config, tag, op, user, password) -> "{\"ok\":true}"); operation(1);
        String original = shell("settings get secure show_ime_with_hard_keyboard").trim();
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1");
            long focusDeadline = SystemClock.uptimeMillis() + 3000;
            while (!onMain(() -> activity.getWindow().getDecorView().hasWindowFocus()) && SystemClock.uptimeMillis() < focusDeadline) Thread.sleep(30);
            int height = onMain(() -> scroll().getHeight());
            onMain(() -> {
                activity.username.setText("alice"); activity.password.requestFocus();
                activity.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(activity.password, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                return null;
            });
            long deadline = SystemClock.uptimeMillis() + 4000;
            while (SystemClock.uptimeMillis() < deadline && onMain(() -> scroll().getHeight()) >= height - 100) {
                onMain(() -> { activity.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(activity.password, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); return null; });
                Thread.sleep(100);
            }
            assertTrue("IME did not reduce the scrollable form viewport", onMain(() -> scroll().getHeight()) < height - 100);
            onMain(() -> {
                android.graphics.Rect rect = new android.graphics.Rect(); activity.run.getDrawingRect(rect); activity.run.requestRectangleOnScreen(rect, true);
                return null;
            }); getInstrumentation().waitForIdleSync();
            onMain(() -> {
                android.graphics.Rect visible = new android.graphics.Rect(); assertTrue(activity.run.getGlobalVisibleRect(visible));
                assertEquals(activity.run.getHeight(), visible.height());
                ((android.widget.TextView) activity.findViewById(R.id.api_title)).setText("API tools · synthetic"); return null;
            });
            capture("remote-keyboard");
        } finally {
            onMain(() -> { activity.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(activity.password.getWindowToken(), 0); return null; });
            shell(original.equals("null") ? "settings delete secure show_ime_with_hard_keyboard" : "settings put secure show_ime_with_hard_keyboard " + (original.equals("1") ? "1" : "0"));
        }
    }

    public void testLocalRestEndpointsAndProbeTimeoutWithRealCore() throws Exception {
        assertFalse(AppState.get().busy());
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        AppState.Snapshot previous = AppState.get();
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core); ServerSocket http = new ServerSocket(0)) {
            String config = "outbounds: [{tag: direct, type: direct}, {tag: ' direct ', type: direct}, {tag: ' choice / one ', type: selector, outbounds: [direct, ' direct ']}]\n";
            NativeBridge.start(descriptor.getFd(), ConfigStore.forRuntime(getInstrumentation().getTargetContext(), config),
                new File(getInstrumentation().getTargetContext().getCacheDir(), "api-local-test.log").getPath());
            ShadowQuicApi api = new ShadowQuicApi(getInstrumentation().getTargetContext());
            boolean ready = false;
            for (int i = 0; i < 40; i++) {
                try { api.get("/traffic"); ready = true; break; } catch (Exception e) { Thread.sleep(50); }
            }
            assertTrue(ready);
            for (String path : new String[]{"/outbounds", "/connections", "/traffic", "/memory"}) assertNotNull(api.get(path));
            JSONObject engine = api.connectionStats();
            assertTrue(engine.getJSONObject("tokio_metric").getLong("num_alive_tasks") > 0);
            assertTrue(engine.getJSONObject("tokio_metric").getLong("num_workers") > 0);
            assertNotNull(engine.getJSONObject("memory"));
            assertNotNull(engine.getJSONObject("traffic"));
            String selection = "/outbounds/" + ShadowQuicApi.segment(" choice / one ") + "/selection";
            assertEquals("direct", api.get(selection).getString("selected"));
            assertEquals("direct", api.put(selection, new JSONObject().put("selected", "direct")).getString("selected"));
            AtomicReference<Throwable> serverError = new AtomicReference<>();
            Thread responder = new Thread(() -> {
                try (java.net.Socket socket = http.accept()) {
                    socket.setSoTimeout(5000); java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    while (true) { String line = reader.readLine(); if (line == null || line.isEmpty()) break; }
                    Thread.sleep(3300);
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                } catch (Throwable e) { serverError.set(e); }
            });
            responder.start();
            JSONObject probe = api.request("POST", "/outbounds/direct/probe", new JSONObject().put("url", "http://127.0.0.1:" + http.getLocalPort() + "/").put("timeout_ms", 5000), 8000);
            assertNotNull(probe); responder.join(6000); assertNull(serverError.get());
            launch((text, tag, op, user, password) -> "{\"users\":[]}");
            AppState.set(AppState.Phase.CONNECTED, "Synthetic local runtime fixture");
            onMain(() -> { activity.model.local("GET", "/memory", null, 3000); return null; }); awaitIdle();
            assertTrue(onMain(() -> activity.model.result.contains("jemalloc")));
            onMain(() -> { activity.mode.check(R.id.api_local_mode); activity.localOperation.setSelection(5); return null; });
            getInstrumentation().waitForIdleSync();
            onMain(() -> { activity.outboundTag.setText(" choice / one "); activity.selectedMember.setText(" direct "); activity.run.performClick(); return null; });
            getInstrumentation().waitForIdleSync();
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            assertNotNull(root); assertFalse(root.findAccessibilityNodeInfosByText("\\u0020direct\\u0020").isEmpty());
            dialogButton(true); awaitIdle();
            assertEquals(" direct ", api.get(selection).getString("selected"));
            AppState.set(AppState.Phase.DISCONNECTED, "Synthetic fixture");
            onMain(() -> { activity.model.local("GET", "/traffic", null, 3000); return null; });
            assertTrue(onMain(() -> activity.model.status.contains("Connect")));
        } finally { NativeBridge.stop(); AppState.set(previous.phase, previous.detail); Os.close(app); Os.close(core); }
    }

    public void testReviewScreenshots() throws Exception {
        launch((config, tag, op, user, password) -> op.equals("get-stats") ? STATS : "{\"users\":[\"admin_review\",\"alice\",\"bob\"]}");
        onMain(() -> { activity.run.performClick(); return null; }); awaitIdle(); capture("remote-users");
        operation(1); onMain(() -> { activity.username.setText("alice"); return null; }); capture("remote-add");
        operation(4); onMain(() -> { activity.username.setText("alice"); activity.run.performClick(); return null; }); awaitIdle(); capture("remote-stats");
        capture("remote-stats-result");
        operation(5); onMain(() -> { activity.allUsers.setChecked(true); return null; }); capture("remote-clear-all");
        Configuration config = activity.getResources().getConfiguration();
        boolean normal = config.fontScale <= 1.1f && config.orientation == Configuration.ORIENTATION_PORTRAIT
            && (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        if (normal) {
            onMain(() -> { activity.run.performClick(); return null; }); capture("remote-confirm"); dialogButton(false);
            onMain(() -> {
                activity.model.busy = true; activity.model.status = "Running clear-stats on review-server…";
                activity.model.resultTitle = "clear-stats · review-server · Synthetic admin profile";
                activity.model.result = "Synthetic request in progress…"; activity.render(); return null;
            }); capture("remote-busy");
            onMain(() -> { activity.model.busy = false; activity.render(); return null; });
        }
        onMain(() -> {
            activity.model.status = "NotAvailable: this server does not support the operation. Check its version.";
            activity.model.resultTitle = "clear-stats · review-server · Synthetic admin profile";
            activity.model.result = "The request did not return a successful response.\n\n" + activity.model.status;
            activity.model.error = true; activity.render(); return null;
        });
        capture("remote-error");
        onMain(() -> { activity.mode.check(R.id.api_local_mode); activity.run.performClick(); return null; }); capture("local-offline");
        onMain(() -> { activity.localOperation.setSelection(6); return null; }); getInstrumentation().waitForIdleSync();
        onMain(() -> { activity.outboundTag.setText("review-server"); return null; }); capture("local-probe");
        onMain(() -> {
            activity.mode.check(R.id.api_remote_mode); activity.remoteOperation.setSelection(0);
            activity.model.outbounds = new JSONArray(); activity.model.tag = "";
            activity.model.status = "No ShadowQUIC or SunnyQUIC outbounds in this profile. Choose another profile.";
            activity.model.resultTitle = "Result"; activity.model.result = "No request sent."; activity.model.error = false; activity.render(); return null;
        }); getInstrumentation().waitForIdleSync(); capture("remote-empty");
    }

    private void launch(ApiToolsModel.RemoteClient client) throws Exception {
        assertFalse("Do not disturb an active VPN", AppState.get().busy());
        android.content.Context context = getInstrumentation().getTargetContext();
        previousId = ConfigProfiles.list(context).selectedId;
        fixtureId = ConfigProfiles.create(context, "API synthetic " + System.nanoTime(), CONFIG).selectedId;
        activity = (ApiToolsActivity) getInstrumentation().startActivitySync(new Intent(context, ApiToolsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitIdle();
        onMain(() -> { activity.model.close(); activity.model = new ApiToolsModel(context, client); activity.model.observe(activity::render); activity.model.loadProfile(fixtureId); return null; });
        awaitIdle();
        onMain(() -> { activity.model.profileName = "Synthetic admin profile"; activity.render(); return null; });
    }
    private void operation(int index) throws Exception { onMain(() -> { activity.remoteOperation.setSelection(index); return null; }); getInstrumentation().waitForIdleSync(); }
    private void awaitIdle() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 6000;
        while (SystemClock.uptimeMillis() < deadline) { if (onMain(() -> activity.model != null && !activity.model.busy)) { getInstrumentation().waitForIdleSync(); return; } Thread.sleep(30); }
        fail("API worker did not finish");
    }
    private void dialogButton(boolean positive) throws Exception {
        getInstrumentation().waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(positive ? "android:id/button1" : "android:id/button2")) {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { getInstrumentation().waitForIdleSync(); return; }
            }
            Thread.sleep(30);
        }
        fail("Confirmation button missing");
    }
    private void capture(String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            assertTrue(activity.model.profileName.startsWith("Synthetic"));
            activity.scope.setText("Synthetic review data. " + (activity.mode.getCheckedRadioButtonId() == R.id.api_local_mode ? "Local runtime fixture." : "Saved profile; remote server controls."));
            android.view.ViewParent parent = activity.result.getParent();
            while (parent != null && !(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
            android.widget.ScrollView scroll = (android.widget.ScrollView) parent;
            if (scroll != null && !name.contains("keyboard")) {
                int target = 0;
                if (name.endsWith("-result")) {
                    int[] titleLocation = new int[2], scrollLocation = new int[2];
                    activity.resultTitle.getLocationOnScreen(titleLocation); scroll.getLocationOnScreen(scrollLocation);
                    target = titleLocation[1] - scrollLocation[1] + scroll.getScrollY();
                }
                scroll.scrollTo(0, target);
            }
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); return null;
        });
        try {
            getInstrumentation().waitForIdleSync(); Thread.sleep(400);
            Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(screenshot);
            Configuration config = activity.getResources().getConfiguration();
            String suffix = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "-dark" : "";
            if (config.fontScale > 1.1f) suffix += "-large-text";
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) suffix += "-landscape";
            File directory = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "api-review"); assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, name + suffix + ".png"))) { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)); }
            screenshot.recycle();
        } finally { onMain(() -> { activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); return null; }); }
    }
    private android.widget.ScrollView scroll() {
        android.view.ViewParent parent = activity.result.getParent();
        while (parent != null && !(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
        return (android.widget.ScrollView) parent;
    }
    private <T> T onMain(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>(); AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { result.set(action.call()); } catch (Throwable e) { failure.set(e); } });
        if (failure.get() instanceof Error) throw (Error) failure.get();
        if (failure.get() != null) throw new Exception(failure.get());
        return result.get();
    }
    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
             java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null) onMain(() -> { activity.finish(); return null; });
            if (fixtureId != null) {
                android.content.Context context = getInstrumentation().getTargetContext();
                ConfigProfiles.select(context, previousId); ConfigProfiles.delete(context, fixtureId);
            }
        } finally { super.tearDown(); }
    }
    private static final class LooperCheck { static boolean isMainThread() { return android.os.Looper.myLooper() == android.os.Looper.getMainLooper(); } }
}
