package dev.squirrel.vpn;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.WindowManager;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("deprecation")
public final class ProfilePickerInstrumentedTest extends InstrumentationTestCase {
    private MainActivity activity;
    private DiagnosticsUi theme;
    private ProfilePickerDialog picker;
    private final AtomicReference<String> selected = new AtomicReference<>();
    private final AtomicInteger callbacks = new AtomicInteger();

    public void testCheckedSelectionDeliversOnlyOneCallback() throws Exception {
        launch();
        show(4);
        onMain(() -> {
            assertEquals(1, picker.list.getCheckedItemPosition());
            View row = picker.list.getChildAt(0);
            picker.list.performItemClick(row, 0, 0);
            picker.list.performItemClick(row, 0, 0);
            return null;
        });
        awaitDismissed();
        assertEquals("fixture-0", selected.get());
        assertEquals(1, callbacks.get());
    }

    public void testBackAndCloseDoNotChangeSelection() throws Exception {
        launch();
        show(4);
        Thread.sleep(300);
        assertTrue(getInstrumentation().getUiAutomation().performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
        awaitDismissed();
        show(4);
        onMain(() -> { picker.findViewById(R.id.profile_picker_close).performClick(); return null; });
        awaitDismissed();
        assertNull(selected.get());
        assertEquals(0, callbacks.get());
    }

    public void testDisabledAnimationsCommitSelectionImmediately() throws Exception {
        launch();
        String previous = shell("settings get global animator_duration_scale").trim();
        try {
            shell("settings put global animator_duration_scale 0");
            long deadline = SystemClock.uptimeMillis() + 3000;
            while (SystemClock.uptimeMillis() < deadline && onMain(ValueAnimator::areAnimatorsEnabled)) Thread.sleep(50);
            assertFalse(onMain(ValueAnimator::areAnimatorsEnabled));
            show(4);
            onMain(() -> {
                picker.list.performItemClick(picker.list.getChildAt(0), 0, 0);
                assertFalse(picker.isShowing());
                assertEquals("fixture-0", selected.get());
                return null;
            });
        } finally {
            shell(previous.equals("null") ? "settings delete global animator_duration_scale"
                : "settings put global animator_duration_scale " + Float.parseFloat(previous));
        }
    }

    public void testReviewScreenshots() throws Exception {
        launch();
        show(4);
        assertEquals(0, (int) onMain(() -> picker.list.getFirstVisiblePosition()));
        if (activity.getResources().getConfiguration().screenHeightDp >= 600) {
            onMain(() -> {
                assertEquals(3, picker.list.getLastVisiblePosition());
                View last = picker.list.getChildAt(picker.list.getChildCount() - 1);
                assertTrue("Short lists must show the whole last profile: " + last.getBottom()
                    + " in " + picker.list.getHeight() + " at width " + picker.list.getWidth(),
                    last.getBottom() <= picker.list.getHeight());
                return null;
            });
        }
        capture("picker");
        onMain(() -> { picker.dismiss(); return null; });
        show(24);
        assertTrue(onMain(() -> picker.list.canScrollVertically(1)));
        onMain(() -> { picker.list.setSelection(23); return null; });
        getInstrumentation().waitForIdleSync();
        assertEquals(23, (int) onMain(() -> picker.list.getLastVisiblePosition()));
        capture("picker-many");
    }

    private void launch() throws Exception {
        assertFalse("Do not interrupt an active VPN for UI fixtures", AppState.get().busy());
        activity = (MainActivity) getInstrumentation().startActivitySync(new Intent(
            getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline && !onMain(() -> activity.findViewById(R.id.config_edit).isEnabled())) Thread.sleep(50);
        onMain(() -> {
            Field handler = MainActivity.class.getDeclaredField("main");
            handler.setAccessible(true);
            ((Handler) handler.get(activity)).removeCallbacksAndMessages(null);
            Field field = MainActivity.class.getDeclaredField("diagnostics");
            field.setAccessible(true);
            theme = (DiagnosticsUi) field.get(activity);
            theme.renderState(new AppState.Snapshot(AppState.Phase.DISCONNECTED, "Synthetic fixture"), false, "Synthetic preview profile");
            return null;
        });
    }

    private void show(int count) throws Exception {
        onMain(() -> {
            ArrayList<ConfigProfiles.Profile> profiles = new ArrayList<>();
            String[] names = {"Default · synthetic", "Home network · synthetic", "Office · synthetic",
                "Travel configuration with a long name · synthetic preview"};
            for (int i = 0; i < count; i++) profiles.add(new ConfigProfiles.Profile("fixture-" + i,
                i < names.length ? names[i] : "Synthetic profile " + (i + 1)));
            picker = new ProfilePickerDialog(activity, theme, new ConfigProfiles.State(profiles, "fixture-1"),
                "fixture-1", false, id -> { selected.set(id); callbacks.incrementAndGet(); });
            picker.show();
            return null;
        });
        getInstrumentation().waitForIdleSync();
    }

    private void awaitDismissed() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 3000;
        while (SystemClock.uptimeMillis() < deadline && onMain(() -> picker.isShowing())) Thread.sleep(50);
        assertFalse(onMain(() -> picker.isShowing()));
    }

    private void capture(String name) throws Exception {
        onMain(() -> {
            assertTrue((picker.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            picker.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            return null;
        });
        try {
            Thread.sleep(350);
            Bitmap shot = getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(shot);
            Configuration config = activity.getResources().getConfiguration();
            String suffix = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "-dark" : "";
            if (config.fontScale > 1.1f) suffix += "-large";
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) suffix += "-landscape";
            File directory = new File(activity.getExternalFilesDir(null), "profile-picker-review");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, name + suffix + ".png"))) {
                assertTrue(shot.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            shot.recycle();
        } finally {
            onMain(() -> {
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                picker.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                return null;
            });
        }
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor pipe = getInstrumentation().getUiAutomation().executeShellCommand(command);
                FileInputStream input = new FileInputStream(pipe.getFileDescriptor())) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private <T> T onMain(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { result.set(action.call()); } catch (Throwable e) { error.set(e); }
        });
        if (error.get() instanceof Exception) throw (Exception) error.get();
        if (error.get() instanceof Error) throw (Error) error.get();
        return result.get();
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null) onMain(() -> {
                if (picker != null) picker.dismiss();
                activity.finish();
                return null;
            });
        } finally { super.tearDown(); }
    }
}
