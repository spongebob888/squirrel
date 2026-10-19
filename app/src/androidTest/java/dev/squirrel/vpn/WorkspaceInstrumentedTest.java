package dev.squirrel.vpn;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import androidx.viewpager2.widget.ViewPager2;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public final class WorkspaceInstrumentedTest extends InstrumentationTestCase {
    private static final String YAML = "# Synthetic review fixture\n"
        + "outbounds:\n  - type: selector\n    tag: proxy\n    outbounds: [direct]\n"
        + "  - type: direct\n    tag: direct\n\n"
        + "android:\n  dns-servers: [1.1.1.1, 1.0.0.1]\n";
    private static final String LOGS = "— service.log —\n"
        + "2026-10-18T08:42:01Z INFO  Synthetic review fixture\n"
        + "2026-10-18T08:42:01Z INFO  Loading configuration\n"
        + "2026-10-18T08:42:02Z INFO  Local engine started\n\n"
        + "— core.log —\n"
        + "08:42:02 INFO  Outbound direct initialized\n"
        + "08:42:03 INFO  TCP example.test:443 connected\n"
        + "08:42:03 WARN  Synthetic probe timed out; retry pending\n"
        + "08:42:04 INFO  UDP [2001:db8::53]:53 forwarded\n"
        + "08:42:05 INFO  Configuration remains unchanged\n";
    private MainActivity activity;
    private WorkspaceUi workspace;

    private static final String RICH_LOGS = "— service.log —\n"
        + "2026-10-19T08:42:01Z Synthetic review fixture\n"
        + "2026-10-19T08:42:02Z Local engine started\n\n"
        + "— core.log —\n"
        + "08:42:02 INFO  Outbound direct initialized\n"
        + "08:42:03 DEBUG TCP example.test:443 connected\n"
        + "08:42:04 WARN  Probe timed out; retry pending\n"
        + "08:42:05 ERROR Synthetic connection failure\n"
        + "08:42:06 TRACE session_id=42 protocol=udp\n"
        + "08:42:07 INFO  \u001B[1mBold\u001B[22m, \u001B[3mitalic\u001B[23m, \u001B[4munderlined\u001B[24m\n"
        + "08:42:08 INFO  \u001B[36mANSI color\u001B[0m and \u001B[38;2;138;75;168mRGB color\u001B[0m\n";

    public void testRichLogFilteringAndStyleOnlyRefreshPreserveFormatting() throws Exception {
        launch();
        page(MainActivity.LOGS);
        onMain(() -> {
            String raw = "— core.log —\n\u001B[32mINFO keep first\nWARN omit\nERROR keep last\u001B[0m\n";
            workspace.renderLogs(raw);
            assertFalse(workspace.logs.getText().toString().contains("\u001B"));
            workspace.logFilter.setText("keep");
            assertEquals("— core.log —\nINFO keep first\nERROR keep last\n", workspace.logs.getText().toString());
            Spanned text = (Spanned) workspace.logs.getText();
            int first = text.toString().indexOf("keep first"), last = text.toString().indexOf("keep last");
            ForegroundColorSpan[] firstSpans = text.getSpans(first, first + 10, ForegroundColorSpan.class);
            ForegroundColorSpan[] lastSpans = text.getSpans(last, last + 9, ForegroundColorSpan.class);
            assertEquals(1, firstSpans.length);
            assertEquals(1, lastSpans.length);
            int previousColor = firstSpans[0].getForegroundColor();
            assertEquals(previousColor, lastSpans[0].getForegroundColor());
            assertTrue(text.getSpans(0, text.length(), StyleSpan.class).length > 0);
            workspace.live.setChecked(false);
            workspace.renderLogs(raw.replace("[32m", "[31m"));
            assertSame(text, workspace.logs.getText());
            workspace.live.setChecked(true);
            workspace.renderLogs(raw.replace("[32m", "[31m"));
            Spanned updated = (Spanned) workspace.logs.getText();
            assertEquals(text.toString(), updated.toString());
            assertTrue(previousColor != updated.getSpans(first, first + 10, ForegroundColorSpan.class)[0].getForegroundColor());
            workspace.logReadError("Synthetic read failure");
            assertSame(updated, workspace.logs.getText());
            return null;
        });
    }

    public void testRichLogReviewScreenshots() throws Exception {
        launch();
        page(MainActivity.LOGS);
        onMain(() -> { workspace.renderLogs(RICH_LOGS); return null; });
        capture("logs-rich", null);
        onMain(() -> { workspace.logFilter.setText("WARN"); return null; });
        capture("logs-rich-filtered", null);
    }

    public void testLogFilterPauseAndReadFailurePreserveSnapshot() throws Exception {
        launch();
        page(MainActivity.LOGS);
        onMain(() -> {
            workspace.renderLogs(LOGS);
            assertEquals(LOGS, workspace.logs.getText().toString());
            workspace.logFilter.requestFocus();
            workspace.logFilter.setText("warn");
            assertTrue(workspace.logs.getText().toString().contains("WARN"));
            assertFalse(workspace.logs.getText().toString().contains("UDP"));
            assertTrue(workspace.logStatus.getText().toString().contains("1 of 8"));
            workspace.live.setChecked(false);
            assertFalse(workspace.needsLogRead());
            workspace.renderLogs("Synthetic replacement\n");
            workspace.logFilter.setText("");
            assertEquals(LOGS, workspace.logs.getText().toString());
            assertTrue(workspace.logStatus.getText().toString().startsWith("Paused"));
            workspace.logReadError("Synthetic read failure.");
            assertEquals(LOGS, workspace.logs.getText().toString());
            assertTrue(workspace.logStatus.getText().toString().contains("unavailable"));
            workspace.live.setChecked(true);
            workspace.renderLogs(LOGS + "08:42:06 INFO  Synthetic recovery\n");
            assertTrue(workspace.logs.getText().toString().contains("Synthetic recovery"));
            assertFalse(workspace.logStatus.getText().toString().contains("unavailable"));
            workspace.logFilter.setText("no-match");
            assertTrue(workspace.logs.getText().toString().contains("No matching log lines"));
            workspace.logFilter.setText("");
            assertTrue(workspace.logs.getText().toString().contains("Synthetic recovery"));
            return null;
        });
        getInstrumentation().waitForIdleSync();
        assertTrue("Log updates stole filter focus", onMain(() -> workspace.logFilter.hasFocus()));
    }

    public void testEditorKeepsSpaceWithKeyboardAndMoreActionsPreserveDraft() throws Exception {
        launch();
        page(MainActivity.CONFIGURATION);
        String previous = shell("settings get secure show_ime_with_hard_keyboard").trim();
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1");
            int originalHeight = onMain(() -> workspace.editor.getHeight());
            showKeyboard();
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (SystemClock.uptimeMillis() < deadline
                    && onMain(() -> workspace.editor.getHeight()) >= originalHeight) Thread.sleep(50);
            onMain(() -> {
                assertTrue("IME did not resize the editor", workspace.editor.getHeight() < originalHeight);
                assertTrue("Editor lost usable space to its controls", workspace.editor.getHeight() >= dp(80));
                assertEquals(YAML, workspace.editor.getText().toString());
                activity.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(workspace.editor.getWindowToken(), 0);
                workspace.editor.clearFocus();
                return null;
            });
            Thread.sleep(300);
            onMain(() -> { workspace.more.performClick(); return null; });
            assertAccessibleText("Export configuration");
            assertAccessibleText("Proxy template");
            assertAccessibleText("Direct test");
            clickText("Proxy template");
            assertAccessibleText("Load proxy template?");
            clickText("Cancel");
            assertEquals(YAML, onMain(() -> workspace.editor.getText().toString()));
        } finally {
            shell(previous.equals("null") ? "settings delete secure show_ime_with_hard_keyboard"
                : "settings put secure show_ime_with_hard_keyboard " + previous);
        }
    }

    /** Real native screenshots, with all visible editor/log values replaced by labeled fixtures. */
    public void testReviewScreenshots() throws Exception {
        launch();
        page(MainActivity.CONFIGURATION);
        assertTrue(onMain(() -> (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0));
        capture("configuration", null);
        Configuration config = activity.getResources().getConfiguration();
        boolean normalPortrait = config.fontScale <= 1.1f && config.orientation == Configuration.ORIENTATION_PORTRAIT
            && (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
        if (normalPortrait) {
            capture("configuration-more", () -> workspace.more.performClick());
            back();
            capture("configuration-manage", () -> activity.findViewById(R.id.manage_profiles).performClick());
            back();
            capture("configuration-download", () -> {
                workspace.downloadButton.performClick();
            });
            back();
            String previous = shell("settings get secure show_ime_with_hard_keyboard").trim();
            try {
                shell("settings put secure show_ime_with_hard_keyboard 1");
                showKeyboard();
                capture("configuration-keyboard", null);
            } finally {
                onMain(() -> {
                    activity.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(workspace.editor.getWindowToken(), 0);
                    workspace.editor.clearFocus();
                    return null;
                });
                shell(previous.equals("null") ? "settings delete secure show_ime_with_hard_keyboard"
                    : "settings put secure show_ime_with_hard_keyboard " + previous);
            }
        }
        page(MainActivity.LOGS);
        onMain(() -> { workspace.renderLogs(LOGS); return null; });
        capture("logs", null);
        onMain(() -> { workspace.logFilter.setText("WARN"); return null; });
        capture("logs-filtered", null);
        onMain(() -> { workspace.live.setChecked(false); return null; });
        capture("logs-paused", null);
        onMain(() -> { workspace.logFilter.setText("no-match"); return null; });
        capture("logs-no-match", null);
        onMain(() -> {
            workspace.live.setChecked(true);
            workspace.logFilter.setText("");
            workspace.renderLogs("No logs yet. Connect to start a session.");
            return null;
        });
        capture("logs-empty", null);
        onMain(() -> {
            workspace.renderLogs(LOGS);
            workspace.logReadError("Synthetic read failure.");
            assertEquals(LOGS, workspace.logs.getText().toString());
            return null;
        });
        capture("logs-error", null);
    }

    private void launch() throws Exception {
        assertFalse("Do not interrupt an active VPN for synthetic UI review", AppState.get().busy());
        activity = (MainActivity) getInstrumentation().startActivitySync(new Intent(
            getInstrumentation().getTargetContext(), MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(() -> ((EditText) activity.findViewById(R.id.config_editor)).isEnabled())) break;
            Thread.sleep(50);
        }
        onMain(() -> {
            assertTrue("Configuration did not finish loading", ((EditText) activity.findViewById(R.id.config_editor)).isEnabled());
            Field main = MainActivity.class.getDeclaredField("main");
            main.setAccessible(true);
            ((Handler) main.get(activity)).removeCallbacksAndMessages(null);
            Field field = MainActivity.class.getDeclaredField("workspace");
            field.setAccessible(true);
            workspace = (WorkspaceUi) field.get(activity);
            workspace.editor.setText(YAML);
            workspace.profileButton.setText("Synthetic fixture");
            workspace.profileButton.setContentDescription("Select synthetic fixture");
            workspace.configNotice.setText("Synthetic data. Save or Connect to apply. Switching profiles keeps drafts until the app closes.");
            Field remoteUrl = MainActivity.class.getDeclaredField("remoteUrl");
            remoteUrl.setAccessible(true);
            remoteUrl.set(activity, "https://config.example.test/squirrel.yaml");
            Field header = MainActivity.class.getDeclaredField("appHeader");
            header.setAccessible(true);
            ((TextView) ((ViewGroup) header.get(activity)).getChildAt(1)).setText("SYNTHETIC DATA");
            return null;
        });
        getInstrumentation().waitForIdleSync();
    }

    private void showKeyboard() throws Exception {
        long focusDeadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < focusDeadline && !onMain(() -> activity.hasWindowFocus())) Thread.sleep(50);
        assertTrue("Activity has not regained input focus", onMain(() -> activity.hasWindowFocus()));
        int originalHeight = onMain(() -> workspace.editor.getHeight());
        onMain(() -> {
            workspace.editor.requestFocus();
            workspace.editor.setSelection(workspace.editor.getText().length());
            activity.getSystemService(InputMethodManager.class).showSoftInput(workspace.editor, InputMethodManager.SHOW_IMPLICIT);
            return null;
        });
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline && onMain(() -> workspace.editor.getHeight()) >= originalHeight) {
            onMain(() -> {
                activity.getSystemService(InputMethodManager.class).showSoftInput(workspace.editor, InputMethodManager.SHOW_IMPLICIT);
                return null;
            });
            Thread.sleep(100);
        }
        getInstrumentation().waitForIdleSync();
        assertTrue("Software keyboard did not reduce the editor viewport", onMain(() -> workspace.editor.getHeight()) < originalHeight);
    }

    private void page(int page) throws Exception {
        onMain(() -> { ((ViewPager2) activity.findViewById(R.id.pages)).setCurrentItem(page, false); return null; });
        getInstrumentation().waitForIdleSync();
    }

    private void capture(String name, Runnable showOverlay) throws Exception {
        onMain(() -> {
            int page = ((ViewPager2) activity.findViewById(R.id.pages)).getCurrentItem();
            assertTrue(page == MainActivity.CONFIGURATION || page == MainActivity.LOGS);
            assertTrue("Editor contains non-fixture data", YAML.equals(workspace.editor.getText().toString()));
            assertTrue("Profile contains non-fixture data", "Synthetic fixture".contentEquals(workspace.profileButton.getText()));
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (showOverlay != null) showOverlay.run();
            return null;
        });
        try {
            Thread.sleep(350);
            getInstrumentation().waitForIdleSync();
            Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(image);
            Configuration config = activity.getResources().getConfiguration();
            String suffix = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "-dark" : "";
            if (config.fontScale > 1.1f) suffix += "-large-text";
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) suffix += "-landscape";
            File dir = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "workspace-review");
            assertTrue(dir.isDirectory() || dir.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(dir, name + suffix + ".png"))) {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            image.recycle();
        } finally { onMain(() -> { activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); return null; }); }
    }

    private void back() throws Exception {
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
    }

    private void assertAccessibleText(String text) throws Exception {
        AccessibilityNodeInfo node = findText(text);
        assertNotNull(text, node);
        node.recycle();
    }

    private void clickText(String text) throws Exception {
        AccessibilityNodeInfo node = findText(text);
        while (node != null && !node.isClickable()) {
            AccessibilityNodeInfo parent = node.getParent();
            node.recycle();
            node = parent;
        }
        assertNotNull(text, node);
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        node.recycle();
        getInstrumentation().waitForIdleSync();
    }

    private AccessibilityNodeInfo findText(String text) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
                root.recycle();
                if (!nodes.isEmpty()) return nodes.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Missing text: " + text);
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
                java.io.InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

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
