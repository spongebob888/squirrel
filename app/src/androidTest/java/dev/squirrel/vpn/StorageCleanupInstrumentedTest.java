package dev.squirrel.vpn;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Deletion runs only inside isolated fixtures, never against the user's app files. */
@SuppressWarnings("deprecation")
public final class StorageCleanupInstrumentedTest extends InstrumentationTestCase {
    private static final String DIRECT = "outbounds: [{type: direct, tag: direct}]\n";
    private Context target, fixture;
    private File root;
    private SettingsActivity settings;

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertFalse("Do not interrupt an active VPN", AppState.get().busy());
        target = getInstrumentation().getTargetContext();
        root = new File(target.getCacheDir(), "storage-cleanup-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        fixture = new ContextWrapper(target) {
            @Override public File getFilesDir() { return root; }
            @Override public Context getApplicationContext() { return this; }
        };
    }

    public void testAllProfilesAndOrphanFilesClearWithoutChangingConfiguration() throws Exception {
        String first = DIRECT + "router: {database: [{path: custom/first.binary}]}\n";
        String second = DIRECT + "router: {database: [{path: '" + new File(root, "custom/second.binary") + "'}]}\n";
        String one = ConfigProfiles.create(fixture, "First fixture", first).selectedId;
        String two = ConfigProfiles.create(fixture, "Second fixture", second, "https://config.example.test/source").selectedId;
        String[] removed = {"custom/first.binary", "custom/second.binary", "data/old.mmdb", "old/geosite.redb", "dashboard/index.html"};
        for (String file : removed) write(file);
        File certificate = write("data/client.pem"), log = write("logs/core.log");
        assertEquals(removed.length, StorageCleanup.clear(fixture));
        for (String file : removed) assertFalse(new File(root, file).exists());
        assertTrue(certificate.exists());
        assertTrue(log.exists());
        assertEquals(first, ConfigProfiles.read(fixture, one));
        assertEquals(second, ConfigProfiles.read(fixture, two));
        assertEquals(two, ConfigProfiles.list(fixture).selectedId);
        assertEquals("https://config.example.test/source", ConfigProfiles.list(fixture).selected().downloadUrl);
        assertEquals(0, StorageCleanup.clear(fixture));
    }

    public void testNativeSessionBlocksCleanupEvenWithoutJavaBusyState() throws Exception {
        File database = write("data/geosite.redb");
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core)) {
            NativeBridge.start(descriptor.getFd(), DIRECT + "restful-api: null\n");
            try {
                StorageCleanup.clear(fixture);
                fail("Cleanup must reject a live native session");
            } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("Disconnect")); }
            assertTrue(database.exists());
            assertEquals("", NativeBridge.status());
        } finally {
            NativeBridge.stop();
            Os.close(app);
            Os.close(core);
        }
    }

    public void testConfirmationCancelAndBusyStatesDoNotDeleteFiles() throws Exception {
        launch();
        onMain(() -> { settings.findViewById(R.id.settings_clear_databases).performClick(); return null; });
        AlertDialog dialog = onMain(this::confirmation);
        assertTrue(onMain(dialog::isShowing));
        capture("confirmation", dialog);
        onMain(() -> { dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); return null; });
        assertNull(StorageCleanup.pending());
        AppState.Snapshot previous = AppState.get();
        try {
            for (AppState.Phase phase : new AppState.Phase[]{AppState.Phase.CONNECTING, AppState.Phase.CONNECTED, AppState.Phase.STOPPING}) {
                AppState.set(phase, "Synthetic cleanup guard fixture");
                await(() -> !settings.findViewById(R.id.settings_clear_databases).isEnabled());
                onMain(() -> { settings.findViewById(R.id.settings_clear_databases).performClick(); return null; });
                assertFalse(onMain(dialog::isShowing));
                assertTrue(onMain(() -> status().contains("Disconnect")));
            }
            capture("busy", null);
        } finally { AppState.set(previous.phase, previous.detail); }
    }

    public void testBackgroundCleanupAndResultSurviveRecreationWithoutReplay() throws Exception {
        write("data/geosite.redb");
        write("ui/index.html");
        StorageCleanup.Operation operation;
        synchronized (ConfigProfiles.class) {
            operation = StorageCleanup.start(fixture);
            assertSame(operation, StorageCleanup.start(fixture));
            launch();
            assertFalse(onMain(() -> settings.findViewById(R.id.settings_clear_databases).isEnabled()));
            capture("clearing", null);
            Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(SettingsActivity.class.getName(), null, false);
            onMain(() -> { settings.recreate(); return null; });
            settings = (SettingsActivity) getInstrumentation().waitForMonitorWithTimeout(monitor, 5000);
            getInstrumentation().removeMonitor(monitor);
            assertNotNull(settings);
            assertTrue(onMain(() -> status().contains("Clearing")));
        }
        await(() -> operation.finished && status().equals("Cleared 2 files."));
        assertNull(operation.error);
        assertTrue(onMain(() -> settings.findViewById(R.id.settings_clear_databases).isEnabled()));
        assertEquals("http://draft.example.test/", onMain(() -> ((EditText) settings.findViewById(R.id.settings_probe_url)).getText().toString()));
        assertFalse(new File(root, "data/geosite.redb").exists());
        capture("success", null);
        // An empty cleanup succeeds and reports that there was nothing to remove.
        onMain(() -> { settings.finish(); return null; });
        synchronized (ConfigProfiles.class) {
            StorageCleanup.start(fixture);
            launch();
        }
        await(() -> status().equals("No databases or UI HTML to clear."));
        capture("empty", null);
    }

    public void testFailureIsVisibleAndAllowsRetry() throws Exception {
        assertTrue(new File(root, "directory").mkdir());
        ConfigProfiles.saveSelected(fixture, DIRECT + "router: {database: [{path: directory}]}\n");
        synchronized (ConfigProfiles.class) {
            StorageCleanup.start(fixture);
            launch();
        }
        await(() -> status().contains("Could not remove"));
        assertTrue(onMain(() -> settings.findViewById(R.id.settings_clear_databases).isEnabled()));
        capture("failure", null);
    }

    private File write(String path) throws Exception {
        File file = new File(root, path);
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), "synthetic fixture".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private void launch() throws Exception {
        settings = (SettingsActivity) getInstrumentation().startActivitySync(new Intent(target, SettingsActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        onMain(() -> {
            ((EditText) settings.findViewById(R.id.settings_probe_url)).setText("http://draft.example.test/");
            return null;
        });
        getInstrumentation().waitForIdleSync();
    }

    private AlertDialog confirmation() throws Exception {
        Field field = SettingsActivity.class.getDeclaredField("clearConfirmation");
        field.setAccessible(true);
        return (AlertDialog) field.get(settings);
    }
    private String status() { return ((TextView) settings.findViewById(R.id.settings_storage_status)).getText().toString(); }
    private void await(Callable<Boolean> ready) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline && !onMain(ready)) Thread.sleep(50);
        assertTrue(onMain(ready));
    }

    private void capture(String name, AlertDialog dialog) throws Exception {
        onMain(() -> {
            settings.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if (dialog != null) dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            android.view.View button = settings.findViewById(R.id.settings_clear_databases);
            android.graphics.Rect rect = new android.graphics.Rect();
            button.getDrawingRect(rect);
            button.requestRectangleOnScreen(rect, true);
            return null;
        });
        try {
            Thread.sleep(350);
            Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            Configuration config = settings.getResources().getConfiguration();
            String suffix = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? "-dark" : "";
            if (config.fontScale > 1.1f) suffix += "-large";
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) suffix += "-landscape";
            File directory = new File(target.getExternalFilesDir(null), "storage-review");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, name + suffix + ".png"))) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            screenshot.recycle();
        } finally {
            onMain(() -> {
                settings.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                if (dialog != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                return null;
            });
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
            if (settings != null) onMain(() -> { settings.finish(); return null; });
            long deadline = SystemClock.uptimeMillis() + 5000;
            while (StorageCleanup.pending() != null && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
                for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        } finally { super.tearDown(); }
    }
}
