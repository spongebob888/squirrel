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
        awaitPage(1);
        String draft = "# Unsaved draft\noutbounds: []\n";
        onMain(() -> { editor().setText(draft); return null; });
        swipe(true);
        awaitPage(2);
        swipe(false);
        awaitPage(1);
        assertEquals(draft, onMain(() -> editor().getText().toString()));
        onMain(() -> { activity.findViewById(R.id.logs_tab).performClick(); return null; });
        awaitPage(2);
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
        onMain(() -> { activity.recreate(); return null; });
        MainActivity replacement = (MainActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
        getInstrumentation().removeMonitor(monitor);
        assertNotNull("Activity was not recreated", replacement);
        activity = replacement;
        awaitReady();
        awaitPage(2);
        assertEquals(draft, onMain(() -> editor().getText().toString()));
        onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
        awaitPage(1);
    }

    public void testRemoteImportValidatesBeforeReplacingEditorAndDoesNotSave() throws Exception {
        launch();
        String saved = ConfigStore.read(getInstrumentation().getTargetContext());
        try (Server server = new Server()) {
            onMain(() -> { activity.findViewById(R.id.configuration_tab).performClick(); return null; });
            awaitPage(1);
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
        }
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

    private EditText editor() { return activity.findViewById(R.id.config_editor); }
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
        fail("Pager did not switch to tab " + page + "; editor scroll=" + onMain(() -> editor().getScrollX()) + "; editor width=" + onMain(() -> editor().getWidth()) + "; line width=" + onMain(() -> editor().getLayout().getLineWidth(0)) + "; pager height=" + onMain(() -> pager().getHeight()));
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
        onMain(() -> { activity.findViewById(R.id.download_config).performClick(); return null; });
        AccessibilityNodeInfo field = accessible("dev.squirrel.vpn:id/remote_url");
        Bundle text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, url);
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text));
        field.recycle();
        clickAccessible("android:id/button1");
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
