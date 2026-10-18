package dev.squirrel.vpn;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

@SuppressWarnings("deprecation")
public final class DiagnosticsInstrumentedTest extends InstrumentationTestCase {
    private MainActivity activity;
    private DiagnosticsUi ui;

    public void testMissingMetricsStayUnknownAndDisconnectClearsLiveValues() throws Exception {
        launch();
        onMain(() -> {
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.renderNetworkStats(metrics());
            assertEquals("28.4 MiB", label(R.id.upload_total));
            assertEquals("194.8 MiB", label(R.id.download_total));
            assertTrue(label(R.id.tcp_metric).startsWith("24\n"));
            assertEquals("32", label(R.id.alive_tasks_metric));
            ui.renderNetworkStats(new JSONObject().put("traffic", new JSONObject().put("inbounds", new JSONArray())));
            assertEquals("0 B", label(R.id.upload_total));
            assertEquals("—", label(R.id.resident_metric));
            assertEquals("—", label(R.id.alive_tasks_metric));
            ui.renderNetworkStats(metrics());
            ui.renderState(new AppState.Snapshot(AppState.Phase.DISCONNECTED, "Synthetic review data"), false, "Synthetic fixture");
            for (int id : new int[]{R.id.upload_total, R.id.download_total, R.id.tcp_metric, R.id.udp_metric,
                    R.id.alive_tasks_metric, R.id.resident_metric, R.id.allocated_metric}) assertEquals("—", label(id));
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.renderNetworkStats(metrics());
            ui.unavailable(MainActivity.CONNECTION, "Synthetic unavailable response.");
            assertEquals("—", label(R.id.upload_total));
            assertEquals("—", label(R.id.alive_tasks_metric));
            assertTrue(label(R.id.connection_metrics_notice).contains("Retrying"));
            return null;
        });
    }

    public void testAliveTasksDistinguishesZeroFromMissingAndInvalidCounts() throws Exception {
        launch();
        onMain(() -> {
            ui.renderState(connected(), false, "Synthetic fixture");
            for (Object value : new Object[]{0L, 71L, JSONObject.NULL, "invalid", -1L, 1.5}) {
                JSONObject response = metrics().put("tokio_metric", new JSONObject().put("num_alive_tasks", value));
                ui.renderNetworkStats(response);
                assertEquals(value.equals(0L) ? "0" : value.equals(71L) ? "71" : "—", label(R.id.alive_tasks_metric));
                assertEquals("28.4 MiB", label(R.id.upload_total));
                assertEquals("42.6 MiB", label(R.id.resident_metric));
            }
            JSONObject response = metrics();
            response.remove("tokio_metric");
            ui.renderNetworkStats(response);
            assertEquals("—", label(R.id.alive_tasks_metric));
            assertEquals("28.4 MiB", label(R.id.upload_total));
            assertTrue(label(R.id.connection_metrics_notice).contains("Some metrics"));
            ui.renderNetworkStats(metrics());
            assertEquals("32", label(R.id.alive_tasks_metric));
            assertTrue(label(R.id.connection_metrics_notice).contains("Updates every second"));
            return null;
        });
    }

    public void testSessionFiltersComposeAndSurviveSavedState() throws Exception {
        launch();
        page(MainActivity.SESSIONS);
        onMain(() -> {
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.renderSessions(flows());
            assertEquals(3, rows(R.id.session_rows).getAdapter().getItemCount());
            ((EditText) activity.findViewById(R.id.session_filter)).setText("example.test");
            assertEquals(1, rows(R.id.session_rows).getAdapter().getItemCount());
            protocol("UDP");
            assertEquals(0, rows(R.id.session_rows).getAdapter().getItemCount());
            assertTrue(label(R.id.session_empty).contains("No matches"));
            ((EditText) activity.findViewById(R.id.session_filter)).setText("");
            assertEquals(1, rows(R.id.session_rows).getAdapter().getItemCount());
            return null;
        });
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            View row = rows(R.id.session_rows).getChildAt(0);
            assertNotNull(row);
            assertTrue(hasText(row, "[2001:db8::53]:53"));
            assertTrue(hasText(row, "Upload\n—"));
            assertTrue(hasText(row, "Duration  — · Half closed"));
            Bundle saved = new Bundle();
            ui.saveFilters(saved);
            DiagnosticsUi restored = new DiagnosticsUi(activity, () -> {}, (tag, selector) -> {}, tag -> {});
            restored.restoreFilters(saved);
            restored.renderState(connected(), false, "Synthetic fixture");
            restored.renderSessions(flows());
            assertEquals("UDP", saved.getString("diagnostics.protocol"));
            View restoredPage = restored.sessionsPage();
            assertEquals(1, ((RecyclerView) restoredPage.findViewById(R.id.session_rows)).getAdapter().getItemCount());
            ui.renderState(new AppState.Snapshot(AppState.Phase.DISCONNECTED, "Synthetic review data"), false, "Synthetic fixture");
            assertEquals(0, rows(R.id.session_rows).getAdapter().getItemCount());
            return null;
        });
    }

    public void testOutboundRefreshPreservesScrollAndUpdatesSelectedRoute() throws Exception {
        launch();
        page(MainActivity.PROXIES);
        onMain(() -> {
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.renderOutbounds(outbounds(35, "relay-a"));
            ((LinearLayoutManager) rows(R.id.proxy_rows).getLayoutManager()).scrollToPositionWithOffset(12, 0);
            return null;
        });
        getInstrumentation().waitForIdleSync();
        View original = onMain(() -> rows(R.id.proxy_rows).findViewHolderForAdapterPosition(12).itemView);
        onMain(() -> { ui.renderOutbounds(outbounds(35, "relay-b")); return null; });
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            RecyclerView list = rows(R.id.proxy_rows);
            assertSame(original, list.findViewHolderForAdapterPosition(12).itemView);
            assertEquals(12, ((LinearLayoutManager) list.getLayoutManager()).findFirstVisibleItemPosition());
            assertTrue(hasText(original, "Selected · relay-b"));
            ((EditText) activity.findViewById(R.id.proxy_filter)).setText("route-34");
            assertEquals(1, list.getAdapter().getItemCount());
            ui.unavailable(MainActivity.PROXIES, "Synthetic unavailable response.");
            assertEquals(0, list.getAdapter().getItemCount());
            assertTrue(label(R.id.proxy_empty).contains("unavailable"));
            return null;
        });
    }

    public void testPreviousRunAndOtherTabResponsesAreRejected() throws Exception {
        launch();
        AppState.Snapshot previous = AppState.get();
        try {
            onMain(() -> {
                Method accept = MainActivity.class.getDeclaredMethod("acceptApiResult", AppState.Snapshot.class, int.class);
                accept.setAccessible(true);
                AppState.set(AppState.Phase.CONNECTED, "Synthetic fixture");
                AppState.Snapshot first = AppState.get();
                assertEquals(true, accept.invoke(activity, first, MainActivity.CONNECTION));
                assertEquals(false, accept.invoke(activity, first, MainActivity.PROXIES));
                AppState.set(AppState.Phase.DISCONNECTED, "Synthetic fixture");
                assertEquals(false, accept.invoke(activity, first, MainActivity.CONNECTION));
                AppState.set(AppState.Phase.CONNECTED, "Synthetic fixture");
                assertEquals(false, accept.invoke(activity, first, MainActivity.CONNECTION));
                assertEquals(true, accept.invoke(activity, AppState.get(), MainActivity.CONNECTION));
                return null;
            });
        } finally { AppState.set(previous.phase, previous.detail); }
    }

    /** Captures only synthetic diagnostic screens; production FLAG_SECURE remains enabled. */
    public void testReviewScreenshots() throws Exception {
        launch();
        assertTrue(onMain(() -> (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0));
        onMain(() -> {
            Field headerField = MainActivity.class.getDeclaredField("appHeader");
            headerField.setAccessible(true);
            ViewGroup header = (ViewGroup) headerField.get(activity);
            ((TextView) header.getChildAt(1)).setText("SYNTHETIC DATA");
            ui.renderState(new AppState.Snapshot(AppState.Phase.DISCONNECTED, "Synthetic review data"), false, "Synthetic fixture");
            return null;
        });
        capture("disconnected");
        onMain(() -> { ui.renderState(connected(), false, "Synthetic fixture"); ui.renderNetworkStats(metrics()); return null; });
        capture("phone");
        android.widget.ScrollView connectionScroll = onMain(() -> {
            View view = activity.findViewById(R.id.alive_tasks_metric);
            while (!(view instanceof android.widget.ScrollView)) view = (View) view.getParent();
            android.widget.ScrollView scroll = (android.widget.ScrollView) view;
            scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            View value = activity.findViewById(R.id.alive_tasks_metric);
            android.graphics.Rect rect = new android.graphics.Rect();
            value.getDrawingRect(rect);
            value.requestRectangleOnScreen(rect, true);
            assertTrue(value.getGlobalVisibleRect(rect));
            assertEquals(value.getHeight(), rect.height());
            return scroll;
        });
        capture("engine");
        onMain(() -> { connectionScroll.scrollTo(0, 0); return null; });
        page(MainActivity.PROXIES);
        onMain(() -> { ui.renderOutbounds(outbounds(3, "relay-a")); return null; });
        capture("proxies");
        page(MainActivity.SESSIONS);
        onMain(() -> { ui.renderSessions(flows()); return null; });
        capture("sessions");
        onMain(() -> { ((EditText) activity.findViewById(R.id.session_filter)).setText("no-match"); return null; });
        capture("empty-filter");
        onMain(() -> { ui.unavailable(MainActivity.SESSIONS, "Synthetic unavailable response."); return null; });
        capture("unavailable");
        onMain(() -> {
            ((EditText) activity.findViewById(R.id.session_filter)).setText("");
            ui.renderSessions(new JSONArray());
            return null;
        });
        capture("empty");
        page(MainActivity.CONNECTION);
        onMain(() -> { ui.renderState(new AppState.Snapshot(AppState.Phase.ERROR,
            "Synthetic failure: configuration could not be loaded. Check Configuration, then reconnect."), false, "Synthetic fixture"); return null; });
        capture("error");
    }

    private void launch() throws Exception {
        assertFalse("Do not interrupt a real VPN session for UI fixtures", AppState.get().busy());
        Intent intent = new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) getInstrumentation().startActivitySync(intent);
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(() -> ((EditText) activity.findViewById(R.id.config_editor)).isEnabled())) break;
            Thread.sleep(50);
        }
        onMain(() -> {
            Field handler = MainActivity.class.getDeclaredField("main");
            handler.setAccessible(true);
            ((Handler) handler.get(activity)).removeCallbacksAndMessages(null);
            Field field = MainActivity.class.getDeclaredField("diagnostics");
            field.setAccessible(true);
            ui = (DiagnosticsUi) field.get(activity);
            return null;
        });
        getInstrumentation().waitForIdleSync();
    }

    private void page(int page) throws Exception {
        onMain(() -> { ((ViewPager2) activity.findViewById(R.id.pages)).setCurrentItem(page, false); return null; });
        getInstrumentation().waitForIdleSync();
    }

    private void protocol(String label) {
        RadioGroup group = activity.findViewById(R.id.protocol_filter);
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton button = (RadioButton) group.getChildAt(i);
            if (label.contentEquals(button.getText())) group.check(button.getId());
        }
    }

    private RecyclerView rows(int id) { return activity.findViewById(id); }
    private String label(int id) { return ((TextView) activity.findViewById(id)).getText().toString(); }

    private boolean hasText(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }

    private void capture(String name) throws Exception {
        // Clear secure capture only in instrumentation, on the three known synthetic pages.
        // The YAML editor and stored profiles are never included in these captures.
        onMain(() -> {
            int page = ((ViewPager2) activity.findViewById(R.id.pages)).getCurrentItem();
            assertTrue(page == MainActivity.CONNECTION || page == MainActivity.PROXIES || page == MainActivity.SESSIONS);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            return null;
        });
        try {
            Thread.sleep(350);
            getInstrumentation().waitForIdleSync();
            Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            Configuration config = activity.getResources().getConfiguration();
            String suffix = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "-dark" : "";
            if (config.fontScale > 1.1f) suffix += "-large-text";
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) suffix += "-landscape";
            File directory = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "diagnostic-review");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, name + suffix + ".png"))) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            screenshot.recycle();
        } finally {
            onMain(() -> { activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); return null; });
        }
    }

    private static AppState.Snapshot connected() {
        return new AppState.Snapshot(AppState.Phase.CONNECTED, "Synthetic review data · local engine fixture");
    }

    private static JSONObject metrics() throws Exception {
        return new JSONObject("{\"tokio_metric\":{\"num_alive_tasks\":32},\"memory\":{\"resident_bytes\":44669337,\"allocated_bytes\":29884416},"
            + "\"traffic\":{\"inbounds\":[{\"tcp_conns\":24,\"udp_conns\":6,\"tcp_upload_bytes\":29360128,"
            + "\"udp_upload_bytes\":419430,\"tcp_download_bytes\":203423744,\"udp_download_bytes\":838860}]}}");
    }

    private static JSONArray flows() throws Exception {
        return new JSONArray("[{\"id\":\"tcp:1\",\"protocol\":\"tcp\",\"inbound\":\"squirrel-tun\",\"outbound\":\"proxy\","
            + "\"dst_domain\":\"example.test\",\"dst_port\":443,\"upload_bytes\":16384,\"download_bytes\":194400,"
            + "\"duration_ms\":15000,\"first_response_ms\":42},"
            + "{\"id\":\"udp:2\",\"protocol\":\"udp\",\"inbound\":\"squirrel-tun\",\"outbound\":\"direct\","
            + "\"dst_domain\":null,\"dst_ip\":\"2001:db8::53\",\"dst_port\":53,\"upload_bytes\":null,"
            + "\"download_bytes\":null,\"duration_ms\":null,\"first_response_ms\":null,\"half_closed\":true},"
            + "{\"id\":\"tcp:3\",\"protocol\":\"tcp\",\"inbound\":\"squirrel-tun\",\"outbound\":\"proxy\","
            + "\"dst_domain\":\"a-long-service-name.for-layout-and-overflow.synthetic-fixture.test\",\"dst_port\":443,"
            + "\"upload_bytes\":44032,\"download_bytes\":3000000,\"duration_ms\":83000,\"first_response_ms\":61}]");
    }

    private static JSONArray outbounds(int count, String selected) throws Exception {
        JSONArray items = new JSONArray();
        for (int i = 0; i < count; i++) items.put(new JSONObject()
            .put("tag", "route-" + i)
            .put("selector", new JSONObject().put("selected", selected).put("outbounds", new JSONArray("[\"relay-a\",\"relay-b\",\"direct\"]")))
            .put("quality", new JSONObject("{\"probe\":{\"status\":\"ok\",\"latency_ms\":48},\"sq_conn_stats\":{"
                + "\"uplink\":{\"rtt\":36,\"sent_packets\":1000,\"lost_packets\":2},"
                + "\"downlink\":{\"rtt\":41,\"sent_packets\":2000,\"lost_packets\":3}}}")));
        return items;
    }

    private <T> T onMain(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { result.set(action.call()); } catch (Throwable failure) { error.set(failure); }
        });
        if (error.get() instanceof Error) throw (Error) error.get();
        if (error.get() != null) throw (Exception) error.get();
        return result.get();
    }

    @Override protected void tearDown() throws Exception {
        try { if (activity != null) onMain(() -> { activity.finish(); return null; }); }
        finally { super.tearDown(); }
    }
}
