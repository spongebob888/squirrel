package dev.squirrel.vpn;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.io.File;
import java.io.FileDescriptor;
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
            assertEquals("24", label(R.id.tcp_metric));
            assertEquals("TCP sessions: 24", activity.findViewById(R.id.tcp_metric).getContentDescription());
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
            assertNotNull(described(row, "Uploaded: —"));
            assertNotNull(described(row, "Duration: —"));
            assertTrue(hasText(row, "Half closed"));
            Bundle saved = new Bundle();
            ui.saveFilters(saved);
            DiagnosticsUi restored = new DiagnosticsUi(activity, () -> {}, (tag, selector, member) -> {}, tag -> {});
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
            assertNotNull(described(original, "Selected relay-b, 3 outbounds"));
            ((EditText) activity.findViewById(R.id.proxy_filter)).setText("route-34");
            assertEquals(4, list.getAdapter().getItemCount());
            ui.unavailable(MainActivity.PROXIES, "Synthetic unavailable response.");
            assertEquals(0, list.getAdapter().getItemCount());
            assertTrue(label(R.id.proxy_empty).contains("unavailable"));
            return null;
        });
    }

    public void testSelectorsShowMembersFirstAndRoundTransportRtt() throws Exception {
        launch();
        AtomicReference<String> selection = new AtomicReference<>();
        AtomicReference<String> probed = new AtomicReference<>();
        onMain(() -> {
            ui = new DiagnosticsUi(activity, () -> {},
                (tag, selector, member) -> selection.set(tag + ":" + member), probed::set);
            activity.setContentView(ui.proxiesPage());
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.renderOutbounds(proxyFixture());
            return null;
        });
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            RecyclerView list = rows(R.id.proxy_rows);
            View header = list.findViewHolderForAdapterPosition(0).itemView;
            assertTrue(hasText(header, "Proxy"));
            assertNotNull(described(header, "Selected relay-a, 3 outbounds"));
            View selected = list.findViewHolderForAdapterPosition(1).itemView;
            assertTrue(hasText(selected, "relay-a"));
            assertTrue(hasText(selected, "36 ms · 0.20%"));
            assertTrue(hasText(selected, "41 ms · 0.15%"));
            assertTrue(hasText(selected, "48 ms"));
            described(selected, "Probe latency for relay-a").performClick();
            assertEquals("relay-a", probed.get());
            assertFalse(described(selected, "Selected relay-a for Proxy").isEnabled());
            View member = list.findViewHolderForAdapterPosition(2).itemView;
            assertTrue(hasText(member, "relay-b"));
            assertTrue(hasText(member, "0 ms · 0.00%"));
            assertTrue(hasText(member, "— · —"));
            described(member, "Select relay-b for Proxy").performClick();
            assertEquals("Proxy:relay-b", selection.get());
            ((EditText) activity.findViewById(R.id.proxy_filter)).setText("relay-b");
            // Both selector groups retain context, followed by the matching ordinary outbound.
            assertEquals(6, list.getAdapter().getItemCount());
            ((EditText) activity.findViewById(R.id.proxy_filter)).setText("Proxy");
            assertEquals(4, list.getAdapter().getItemCount());
            return null;
        });
    }

    public void testProxyGridReflowsAndKeepsSelectorHeadingsFullWidth() throws Exception {
        launch();
        page(MainActivity.PROXIES);
        onMain(() -> {
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.renderOutbounds(proxyFixture());
            RecyclerView list = rows(R.id.proxy_rows);
            ViewGroup.LayoutParams params = list.getLayoutParams();
            params.width = ui.dp(720);
            list.setLayoutParams(params);
            return null;
        });
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            RecyclerView list = rows(R.id.proxy_rows);
            GridLayoutManager grid = (GridLayoutManager) list.getLayoutManager();
            assertTrue(grid.getSpanCount() >= 2);
            View header = list.findViewHolderForAdapterPosition(0).itemView;
            View first = list.findViewHolderForAdapterPosition(1).itemView;
            View second = list.findViewHolderForAdapterPosition(2).itemView;
            assertEquals(list.getWidth(), header.getWidth());
            assertEquals(first.getTop(), second.getTop());
            assertTrue(first.getWidth() < header.getWidth());
            View select = described(first, "Selected relay-a for Proxy");
            View probe = described(first, "Probe latency for relay-a");
            assertTrue(select.getWidth() >= ui.dp(48) && select.getHeight() >= ui.dp(48));
            assertTrue(probe.getWidth() >= ui.dp(48) && probe.getHeight() >= ui.dp(48));
            View direct = list.findViewHolderForAdapterPosition(3).itemView;
            int[] probeLocation = new int[2], directLocation = new int[2];
            probe.getLocationInWindow(probeLocation);
            described(direct, "Probe latency for direct").getLocationInWindow(directLocation);
            assertEquals("Probe actions align despite different metric content", probeLocation[1], directLocation[1]);
            // A following selector starts a new row even after a partially filled group.
            assertEquals(grid.getSpanCount(), grid.getSpanSizeLookup().getSpanSize(4));
            ViewGroup.LayoutParams params = list.getLayoutParams();
            params.width = ui.dp(320);
            list.setLayoutParams(params);
            return null;
        });
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            RecyclerView list = rows(R.id.proxy_rows);
            assertEquals(1, ((GridLayoutManager) list.getLayoutManager()).getSpanCount());
            assertEquals(list.getWidth(), list.findViewHolderForAdapterPosition(1).itemView.getWidth());
            return null;
        });
    }

    public void testProxyReviewScreenshots() throws Exception {
        launch();
        onMain(() -> {
            Field headerField = MainActivity.class.getDeclaredField("appHeader");
            headerField.setAccessible(true);
            ((TextView) ((ViewGroup) headerField.get(activity)).getChildAt(1)).setText("SYNTHETIC DATA");
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.renderOutbounds(proxyFixture());
            return null;
        });
        page(MainActivity.PROXIES);
        capture("proxies-compact");
        capture("polish-proxies");
        onMain(() -> {
            ui.probePending("relay-a", true);
            ui.probePending("Proxy", true);
            ui.selectionPending("Proxy", true);
            return null;
        });
        capture("polish-proxies-pending");
        onMain(() -> {
            assertFalse(described(rows(R.id.proxy_rows), "Probing Proxy").isEnabled());
            assertFalse(described(rows(R.id.proxy_rows), "Choose proxy for Proxy").isEnabled());
            ui.probePending("relay-a", false);
            ui.probePending("Proxy", false);
            ui.selectionPending("Proxy", false);
            rows(R.id.proxy_rows).scrollToPosition(rows(R.id.proxy_rows).getAdapter().getItemCount() - 1);
            return null;
        });
        capture("polish-proxies-long");
        onMain(() -> { ((EditText) activity.findViewById(R.id.proxy_filter)).setText("no-match"); return null; });
        capture("proxies-empty");
        capture("polish-proxies-filter");
        onMain(() -> {
            ((EditText) activity.findViewById(R.id.proxy_filter)).setText("");
            ui.renderOutbounds(new JSONArray());
            return null;
        });
        capture("polish-proxies-empty");
        onMain(() -> { ui.unavailable(MainActivity.PROXIES, "Synthetic unavailable response."); return null; });
        capture("polish-proxies-unavailable");
        onMain(() -> {
            ui.renderState(new AppState.Snapshot(AppState.Phase.DISCONNECTED, "Synthetic fixture"), false, "Synthetic fixture");
            return null;
        });
        capture("polish-proxies-disconnected");
        page(MainActivity.SESSIONS);
        onMain(() -> { ui.renderState(connected(), false, "Synthetic fixture"); ui.renderSessions(flows()); return null; });
        capture("polish-sessions");
        onMain(() -> {
            View first = rows(R.id.session_rows).findViewHolderForAdapterPosition(0).itemView;
            assertNotNull(described(first, "Uploaded: 16.0 KiB"));
            assertNotNull(described(first, "Downloaded: 189.8 KiB"));
            assertNotNull(described(first, "First response: 42 ms"));
            assertNotNull(described(first, "Duration: 15 s"));
            rows(R.id.session_rows).scrollToPosition(2);
            return null;
        });
        capture("polish-sessions-long");
        onMain(() -> { protocol("UDP"); return null; });
        capture("polish-sessions-udp");
        onMain(() -> {
            assertEquals("1 / 3", label(R.id.session_summary));
            assertEquals("1 of 3 sessions, live", activity.findViewById(R.id.session_summary).getContentDescription());
            ((EditText) activity.findViewById(R.id.session_filter)).setText("no-match");
            return null;
        });
        capture("polish-sessions-filter");
        onMain(() -> { ui.unavailable(MainActivity.SESSIONS, "Synthetic unavailable response."); return null; });
        capture("polish-sessions-unavailable");
        onMain(() -> {
            ((EditText) activity.findViewById(R.id.session_filter)).setText("");
            protocol("All");
            ui.renderSessions(new JSONArray());
            return null;
        });
        capture("polish-sessions-empty");
        onMain(() -> {
            ui.renderState(new AppState.Snapshot(AppState.Phase.DISCONNECTED, "Synthetic fixture"), false, "Synthetic fixture");
            return null;
        });
        capture("polish-sessions-disconnected");
    }

    public void testInlineMemberSelectionUpdatesLocalCoreAndCheckmark() throws Exception {
        launch();
        AppState.Snapshot previous = AppState.get();
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        android.content.Context context = getInstrumentation().getTargetContext();
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core)) {
            NativeBridge.start(descriptor.getFd(), ConfigStore.forRuntime(context,
                "outbounds: [{tag: direct, type: direct}, {tag: backup, type: direct}, "
                    + "{tag: 'choice / one', type: selector, outbounds: [direct, backup]}]\n"),
                new File(context.getCacheDir(), "inline-selection-test.log").getPath());
            ShadowQuicApi api = new ShadowQuicApi(context);
            JSONObject response = null;
            for (int attempt = 0; attempt < 80; attempt++) {
                try { response = api.get("/outbounds"); break; }
                catch (Exception ignored) { Thread.sleep(50); }
            }
            assertNotNull(response);
            JSONObject snapshot = response;
            onMain(() -> {
                AppState.set(AppState.Phase.CONNECTED, "Synthetic local selector fixture");
                ui.renderState(AppState.get(), false, "Synthetic fixture");
                ui.renderOutbounds(snapshot.getJSONArray("outbounds"));
                return null;
            });
            page(MainActivity.PROXIES);
            long deadline = SystemClock.uptimeMillis() + 5000;
            View select;
            do {
                select = onMain(() -> described(rows(R.id.proxy_rows), "Select backup for choice / one"));
                if (select == null) Thread.sleep(50);
            } while (select == null && SystemClock.uptimeMillis() < deadline);
            assertNotNull(select);
            View control = select;
            onMain(() -> { control.performClick(); return null; });
            String path = "/outbounds/" + ShadowQuicApi.segment("choice / one") + "/selection";
            deadline = SystemClock.uptimeMillis() + 5000;
            while (!"backup".equals(api.get(path).optString("selected")) && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            assertEquals("backup", api.get(path).getString("selected"));
            deadline = SystemClock.uptimeMillis() + 5000;
            while (onMain(() -> described(rows(R.id.proxy_rows), "Selected backup for choice / one")) == null
                    && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            assertNotNull(onMain(() -> described(rows(R.id.proxy_rows), "Selected backup for choice / one")));
        } finally {
            NativeBridge.stop();
            AppState.set(previous.phase, previous.detail);
            Os.close(app); Os.close(core);
        }
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
        onMain(() -> {
            assertEquals("Disconnect VPN", ui.connect.getContentDescription());
            activity.findViewById(R.id.connection_info).performClick();
            assertEquals(View.VISIBLE, activity.findViewById(R.id.connection_help).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.tcp_metric).getVisibility());
            return null;
        });
        capture("connection-info");
        onMain(() -> {
            Bundle saved = new Bundle();
            ui.saveFilters(saved);
            assertTrue(saved.getBoolean("diagnostics.connectionHelp"));
            activity.findViewById(R.id.connection_info).performClick();
            assertEquals(View.GONE, activity.findViewById(R.id.connection_help).getVisibility());
            ui.restoreFilters(saved);
            assertEquals(View.VISIBLE, activity.findViewById(R.id.connection_help).getVisibility());
            activity.findViewById(R.id.connection_info).performClick();
            return null;
        });
        onMain(() -> {
            ui.renderNetworkStats(new JSONObject().put("traffic", new JSONObject().put("inbounds", new JSONArray())));
            return null;
        });
        capture("connection-partial");
        onMain(() -> {
            ui.unavailable(MainActivity.CONNECTION, "Synthetic unavailable response.");
            return null;
        });
        capture("connection-unavailable");
        onMain(() -> {
            ui.renderState(new AppState.Snapshot(AppState.Phase.DISCONNECTED, "Ready when you are"), true, "Synthetic fixture");
            assertEquals("Checking configuration", ui.connect.getContentDescription());
            ui.connect.setEnabled(false);
            return null;
        });
        capture("connection-checking");
        onMain(() -> {
            ui.renderState(new AppState.Snapshot(AppState.Phase.CONNECTING, "Starting ShadowQUIC"), false, "Synthetic fixture");
            ui.connect.setEnabled(true);
            return null;
        });
        capture("connection-starting");
        onMain(() -> {
            ui.renderState(new AppState.Snapshot(AppState.Phase.STOPPING, "Closing VPN"), false, "Synthetic fixture");
            ui.connect.setEnabled(false);
            return null;
        });
        capture("connection-stopping");
        onMain(() -> {
            ui.renderState(connected(), false, "Synthetic fixture");
            ui.connect.setEnabled(true);
            return null;
        });
        page(MainActivity.PROXIES);
        onMain(() -> { ui.renderOutbounds(proxyFixture()); return null; });
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
            if (onMain(() -> activity.findViewById(R.id.config_edit).isEnabled())) break;
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

    private View described(View view, String description) {
        if (android.text.TextUtils.equals(description, view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = described(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
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

    private static JSONArray proxyFixture() throws Exception {
        JSONObject quality = new JSONObject("{\"probe\":{\"status\":\"ok\",\"latency_ms\":48.1},\"sq_conn_stats\":{"
            + "\"uplink\":{\"rtt\":36.49,\"sent_packets\":1000,\"lost_packets\":2},"
            + "\"downlink\":{\"rtt\":40.5,\"sent_packets\":2000,\"lost_packets\":3}}}");
        return new JSONArray()
            .put(new JSONObject().put("tag", "relay-a").put("quality", quality))
            .put(new JSONObject().put("tag", "direct"))
            .put(new JSONObject().put("tag", "Proxy").put("selector", new JSONObject()
                .put("selected", "relay-a").put("outbounds", new JSONArray("[\"relay-a\",\"relay-b\",\"direct\"]"))))
            .put(new JSONObject().put("tag", "relay-b").put("quality", new JSONObject(
                "{\"probe\":{\"status\":\"timeout\"},\"sq_conn_stats\":{\"uplink\":{\"rtt\":0,\"sent_packets\":100,\"lost_packets\":0},\"downlink\":{\"rtt\":\"invalid\"}}}")))
            .put(new JSONObject().put("tag", "Backup").put("selector", new JSONObject()
                .put("selected", "relay-b").put("outbounds", new JSONArray("[\"relay-b\",\"singapore-relay-with-a-long-outbound-name\"]"))))
            .put(new JSONObject().put("tag", "singapore-relay-with-a-long-outbound-name").put("quality", quality));
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
