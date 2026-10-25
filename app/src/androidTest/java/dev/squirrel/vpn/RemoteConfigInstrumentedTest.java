package dev.squirrel.vpn;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.viewpager2.widget.ViewPager2;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public final class RemoteConfigInstrumentedTest extends InstrumentationTestCase {
    private static final String CONFIG = "# Remote fixture\noutbounds: [{type: direct, tag: direct}]\n";
    private MainActivity activity;

    public void testDownloaderFollowsRedirectsAndReadsUtf8() throws Exception {
        try (Server server = new Server()) {
            assertEquals(CONFIG, new ConfigDownloader().download(server.url("/redirect")));
            assertEquals("# 配置\n" + CONFIG, new ConfigDownloader().download(server.url("/unicode")));
        }
    }

    public void testDownloaderRejectsHttpErrorsOversizeInvalidUtf8AndRedirectLoops() throws Exception {
        try (Server server = new Server()) {
            for (String path : new String[]{"/missing", "/oversize", "/invalid-utf8", "/loop"}) {
                try {
                    new ConfigDownloader().download(server.url(path));
                    fail("Invalid response accepted: " + path);
                } catch (IOException expected) {
                    String message = expected.getMessage();
                    assertTrue(message, message.contains("HTTP 404") || message.contains("1 MiB")
                        || message.contains("UTF-8") || message.contains("redirects"));
                }
            }
        }
        try {
            new ConfigDownloader().download("file:///etc/hosts");
            fail("A non-HTTP URL was accepted");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("HTTP")); }
    }

    public void testSwipesAndTabClicksPreserveDraftAcrossRecreation() throws Exception {
        launch();
        swipe(true);
        awaitPage(MainActivity.PROXIES);
        swipe(true);
        awaitPage(MainActivity.CONFIGURATION);
        String draft = "# Unsaved draft\noutbounds: []\n";
        onMain(() -> { editor().setText(draft); return null; });
        swipe(true);
        awaitPage(MainActivity.LOGS);
        swipe(false);
        awaitPage(MainActivity.CONFIGURATION);
        assertEquals(draft, onMain(() -> editor().getText().toString()));
        onMain(() -> { activity.findViewById(R.id.logs_tab).performClick(); return null; });
        awaitPage(MainActivity.LOGS);
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        onMain(() -> { activity.recreate(); return null; });
        MainActivity replacement = (MainActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull("Activity was not recreated", replacement);
        activity = replacement;
        awaitReady();
        awaitPage(MainActivity.LOGS);
        assertEquals(draft, onMain(() -> editor().getText().toString()));
        onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
        awaitPage(MainActivity.CONFIGURATION);
    }

    public void testRemoteImportValidatesBeforeReplacingEditorAndDoesNotSave() throws Exception {
        launch();
        android.content.Context context = getInstrumentation().getTargetContext();
        String saved = ConfigStore.read(context);
        ConfigProfiles.Profile original = ConfigProfiles.list(context).selected();
        try (Server server = new Server()) {
            onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
            awaitPage(MainActivity.CONFIGURATION);
            String draft = "# Existing draft\n";
            onMain(() -> { editor().setText(draft); return null; });
            importUrl(server.url("/invalid-yaml"));
            awaitNotice("Download failed");
            assertEquals(draft, onMain(() -> editor().getText().toString()));
            clickAccessible("android:id/button1"); // Dismiss the error dialog.
            importUrl(server.url("/redirect"));
            awaitNotice("Downloaded and validated");
            assertEquals(CONFIG, onMain(() -> editor().getText().toString()));
            assertEquals(saved, ConfigStore.read(getInstrumentation().getTargetContext()));
        } finally { ConfigProfiles.setDownloadUrl(context, original.id, original.downloadUrl); }
    }

    public void testDownloadUrlsRemainPerProfileAfterFreshLaunchAndCanBeReused() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        String original = ConfigProfiles.list(context).selectedId;
        String suffix = Long.toString(System.nanoTime());
        String nameA = "URL A " + suffix, nameB = "URL B " + suffix;
        String a = ConfigProfiles.create(context, nameA, CONFIG).selectedId;
        String b = null;
        try (Server server = new Server()) {
            b = ConfigProfiles.create(context, nameB, "# B\n" + CONFIG).selectedId;
            ConfigProfiles.select(context, a);
            launch();
            onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
            awaitPage(MainActivity.CONFIGURATION);
            String urlA = server.url("/redirect"), urlB = server.url("/config");
            importUrl(urlA);
            awaitNotice("Downloaded and validated");
            assertEquals(urlA, ConfigProfiles.list(context).find(a).downloadUrl);
            switchProfile(nameB);
            openDownload();
            assertDownloadUrl("");
            clickAccessible("android:id/button2");
            importUrl(urlB);
            awaitNotice("Downloaded and validated");
            assertEquals(urlB, ConfigProfiles.list(context).find(b).downloadUrl);
            switchProfile(nameA);
            openDownload();
            assertDownloadUrl(urlA);
            clickAccessible("android:id/button2");
            onMain(() -> { activity.finish(); activity = null; return null; });
            getInstrumentation().waitForIdleSync();
            launch(); // Fresh Activity without retained instance state.
            onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
            awaitPage(MainActivity.CONFIGURATION);
            openDownload();
            assertDownloadUrl(urlA);
            clickAccessible("android:id/button1"); // Reuse the saved URL without entering it again.
            awaitNotice("Downloaded and validated");
            assertEquals(CONFIG, onMain(() -> editor().getText().toString()));
            switchProfile(nameB);
            openDownload();
            assertDownloadUrl(urlB);
            clickAccessible("android:id/button2");
            String failedUrl = server.url("/missing");
            importUrl(failedUrl);
            awaitNotice("Download failed");
            clickAccessible("android:id/button1");
            assertEquals(failedUrl, ConfigProfiles.list(context).find(b).downloadUrl);
            openDownload();
            assertDownloadUrl(failedUrl);
            clickAccessible("android:id/button2");
            assertEquals(CONFIG, ConfigProfiles.read(context, a));
            assertEquals("# B\n" + CONFIG, ConfigProfiles.read(context, b));
        } finally {
            if (activity != null) onMain(() -> { activity.finish(); activity = null; return null; });
            ConfigProfiles.select(context, original);
            ConfigProfiles.delete(context, a);
            if (b != null) ConfigProfiles.delete(context, b);
        }
    }

    public void testProfileSwitchingPreservesSeparateDraftsAndRestoresSelection() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        String original = ConfigProfiles.list(context).selectedId;
        String suffix = Long.toString(System.nanoTime());
        String nameA = "Test A " + suffix, nameB = "Test B " + suffix;
        String a = ConfigProfiles.create(context, nameA, CONFIG).selectedId;
        String b = null;
        try {
            b = ConfigProfiles.create(context, nameB, "# B\n" + CONFIG).selectedId;
            ConfigProfiles.select(context, a);
            launch();
            onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
            awaitPage(MainActivity.CONFIGURATION);
            String draftA = "# Draft A\n" + CONFIG;
            String draftB = "# Draft B\n" + CONFIG;
            onMain(() -> { editor().setText(draftA); return null; });
            switchProfile(nameB);
            assertEquals("# B\n" + CONFIG, onMain(() -> editor().getText().toString()));
            onMain(() -> { editor().setText(draftB); return null; });
            switchProfile(nameA);
            assertEquals(draftA, onMain(() -> editor().getText().toString()));
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
            onMain(() -> { activity.recreate(); return null; });
            activity = (MainActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            getInstrumentation().removeMonitor(monitor);
            assertNotNull(activity);
            awaitReady();
            assertEquals(draftA, onMain(() -> editor().getText().toString()));
            switchProfile(nameB);
            assertEquals(draftB, onMain(() -> editor().getText().toString()));
            assertEquals(b, ConfigProfiles.list(context).selectedId);
            assertEquals(CONFIG, ConfigProfiles.read(context, a));
            assertEquals("# B\n" + CONFIG, ConfigProfiles.read(context, b));
        } finally {
            if (activity != null) onMain(() -> { activity.finish(); activity = null; return null; });
            ConfigProfiles.select(context, original);
            ConfigProfiles.delete(context, a);
            if (b != null) ConfigProfiles.delete(context, b);
        }
    }

    public void testProfileManagementDialogsCreateDuplicateRenameAndDelete() throws Exception {
        android.content.Context context = getInstrumentation().getTargetContext();
        String original = ConfigProfiles.list(context).selectedId;
        String prefix = "UI " + System.nanoTime() + " ";
        try {
            String base = ConfigProfiles.create(context, prefix + "base", CONFIG).selectedId;
            launch();
            onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
            awaitPage(MainActivity.CONFIGURATION);
            String copy = "# Unsaved copy source\n" + CONFIG;
            onMain(() -> { editor().setText(copy); return null; });
            manageProfile("Duplicate current", prefix + "copy");
            String duplicate = ConfigProfiles.list(context).selectedId;
            assertEquals(copy, ConfigProfiles.read(context, duplicate));
            assertEquals(CONFIG, ConfigProfiles.read(context, base));
            manageProfile("Rename", prefix + "renamed");
            assertEquals(duplicate, ConfigProfiles.list(context).selectedId);
            manageProfile("New configuration", prefix + "new");
            String created = ConfigProfiles.list(context).selectedId;
            assertEquals(ConfigStore.asset(context, "example.yaml"), ConfigProfiles.read(context, created));
            onMain(() -> { activity.findViewById(R.id.manage_profiles).performClick(); return null; });
            clickText("Delete");
            clickAccessible("android:id/button1");
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (ConfigProfiles.list(context).find(created) != null && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            assertNull(ConfigProfiles.list(context).find(created));
            awaitReady();
        } finally {
            if (activity != null) onMain(() -> { activity.finish(); activity = null; return null; });
            ConfigProfiles.select(context, original);
            for (ConfigProfiles.Profile profile : ConfigProfiles.list(context).profiles) {
                if (profile.name.startsWith(prefix)) ConfigProfiles.delete(context, profile.id);
            }
        }
    }

    private void manageProfile(String action, String name) throws Exception {
        onMain(() -> { activity.findViewById(R.id.manage_profiles).performClick(); return null; });
        clickText(action);
        AccessibilityNodeInfo field = accessible("dev.squirrel.vpn:id/profile_name");
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, name);
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text));
        field.recycle();
        clickAccessible("android:id/button1");
        awaitProfile(name);
    }

    private void clickText(String text) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                boolean clicked = false;
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    if (text.contentEquals(node.getText())) clicked |= node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    node.recycle();
                }
                root.recycle();
                if (clicked) return;
            }
            Thread.sleep(50);
        }
        fail("Cannot click " + text);
    }
    private void switchProfile(String name) throws Exception {
        onMain(() -> { WorkspaceTestViews.workspace(activity).closeEditor(); activity.findViewById(R.id.config_profile).performClick(); return null; });
        clickText(name);
        awaitProfile(name);
    }
    private void awaitProfile(String name) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(() -> editor().isEnabled() && ((TextView) activity.findViewById(R.id.config_profile))
                .getText().toString().equals(name))) return;
            Thread.sleep(50);
        }
        fail("Profile did not switch to " + name);
    }

    private void launch() throws Exception {
        Intent intent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) getInstrumentation().startActivitySync(intent);
        AccessibilityServiceInfo info = getInstrumentation().getUiAutomation().getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        getInstrumentation().getUiAutomation().setServiceInfo(info);
        awaitReady();
    }

    private EditText editor() throws Exception { return WorkspaceTestViews.workspace(activity).editor; }
    private ViewPager2 pager() { return activity.findViewById(R.id.pages); }
    private void awaitReady() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(() -> editor() != null && editor().isEnabled())) return;
            Thread.sleep(50);
        }
        fail("Configuration editor did not become ready");
    }
    private void awaitPage(int page) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(() -> pager().getCurrentItem() == page && pager().getScrollState() == ViewPager2.SCROLL_STATE_IDLE)) return;
            Thread.sleep(50);
        }
        fail("Pager did not switch to tab " + page);
    }
    private void awaitNotice(String text) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 6000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(() -> ((TextView) activity.findViewById(R.id.config_notice)).getText().toString().contains(text))) return;
            Thread.sleep(50);
        }
        fail("Missing status: " + text);
    }
    private void importUrl(String url) throws Exception {
        openDownload();
        AccessibilityNodeInfo field = accessible("dev.squirrel.vpn:id/remote_url");
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, url);
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text));
        field.recycle();
        clickAccessible("android:id/button1");
    }
    private void openDownload() throws Exception {
        onMain(() -> { WorkspaceTestViews.workspace(activity).closeEditor(); activity.findViewById(R.id.download_config).performClick(); return null; });
    }
    private void assertDownloadUrl(String expected) throws Exception {
        AccessibilityNodeInfo field = accessible("dev.squirrel.vpn:id/remote_url");
        if (expected.isEmpty()) assertTrue(field.isShowingHintText() || field.getText() == null || field.getText().length() == 0);
        else assertEquals(expected, field.getText().toString());
        field.recycle();
    }
    private void clickAccessible(String id) throws Exception {
        AccessibilityNodeInfo node = accessible(id);
        assertTrue("Cannot click " + id, node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        node.recycle();
    }
    private AccessibilityNodeInfo accessible(String id) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                root.recycle();
                if (!nodes.isEmpty()) return nodes.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Missing view: " + id);
    }
    private void swipe(boolean left) throws Exception {
        int[] bounds = onMain(() -> {
            int[] location = new int[2];
            pager().getLocationOnScreen(location);
            return new int[]{location[0], location[1], pager().getWidth(), pager().getHeight()};
        });
        float start = bounds[0] + bounds[2] * (left ? 0.85f : 0.15f);
        float end = bounds[0] + bounds[2] * (left ? 0.15f : 0.85f);
        float y = bounds[1] + bounds[3] * 0.65f;
        long down = SystemClock.uptimeMillis();
        pointer(down, MotionEvent.ACTION_DOWN, start, y);
        for (int i = 1; i <= 15; i++) {
            Thread.sleep(12);
            pointer(down, MotionEvent.ACTION_MOVE, start + (end - start) * i / 15, y);
        }
        pointer(down, MotionEvent.ACTION_UP, end, y);
    }
    private void pointer(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        getInstrumentation().sendPointerSync(event);
        event.recycle();
    }
    private <T> T onMain(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { result.set(action.call()); }
            catch (Exception e) { error.set(e); }
        });
        if (error.get() != null) throw error.get();
        return result.get();
    }
    @Override protected void tearDown() throws Exception {
        if (activity != null) onMain(() -> { activity.finish(); return null; });
        super.tearDown();
    }

    private static final class Server implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private volatile boolean closed;
        Server() throws IOException {
            server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(() -> {
                while (!closed) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(3000);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        String request = reader.readLine();
                        if (request == null) continue;
                        String path = request.split(" ")[1];
                        while (true) { String line = reader.readLine(); if (line == null || line.isEmpty()) break; }
                        byte[] body = CONFIG.getBytes(StandardCharsets.UTF_8);
                        int status = 200;
                        String location = "";
                        long size = -1;
                        if (path.equals("/redirect")) { status = 302; location = "Location: /config\r\n"; body = new byte[0]; }
                        if (path.equals("/loop")) { status = 302; location = "Location: /loop\r\n"; body = new byte[0]; }
                        if (path.equals("/missing")) status = 404;
                        if (path.equals("/oversize")) size = ConfigStore.MAX_BYTES + 1;
                        if (path.equals("/unicode")) body = ("\uFEFF# 配置\n" + CONFIG).getBytes(StandardCharsets.UTF_8);
                        if (path.equals("/invalid-utf8")) body = new byte[]{(byte) 0xFF};
                        if (path.equals("/invalid-yaml")) body = "not: [valid yaml".getBytes(StandardCharsets.UTF_8);
                        String headers = "HTTP/1.1 " + status + " Response\r\n" + location
                            + "Content-Length: " + (size < 0 ? body.length : size) + "\r\nConnection: close\r\n\r\n";
                        socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().write(body);
                    } catch (IOException expectedDisconnect) { /* Clients may reject headers before reading a body. */ }
                }
            });
            worker.start();
        }
        String url(String path) { return "http://127.0.0.1:" + server.getLocalPort() + path; }
        @Override public void close() throws Exception {
            closed = true;
            server.close();
            worker.join(4000);
        }
    }
}
