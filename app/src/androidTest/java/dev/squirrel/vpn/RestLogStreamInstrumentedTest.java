package dev.squirrel.vpn;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.FileDescriptor;
import java.nio.file.Files;
import org.json.JSONObject;

@SuppressWarnings("deprecation")
public final class RestLogStreamInstrumentedTest extends InstrumentationTestCase {
    private Context context;
    private File root;

    @Override protected void setUp() throws Exception {
        super.setUp();
        assertFalse("Do not interrupt an active VPN", AppState.get().busy());
        Context target = getInstrumentation().getTargetContext();
        root = new File(target.getCacheDir(), "rest-log-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        context = new ContextWrapper(target) {
            @Override public File getFilesDir() { return root; }
            @Override public Context getApplicationContext() { return this; }
        };
    }

    public void testStructuredFieldsSpansAndLagNoticesRemainReadable() throws Exception {
        String json = "{\"timestamp\":\"2026-11-21T08:00:00Z\",\"level\":\"TRACE\",\"target\":\"shadowquic::test\","
            + "\"event\":\"fixture:record\",\"fields\":{\"message\":\"Synthetic record\",\"bytes\":0},"
            + "\"spans\":[{\"name\":\"request\",\"fields\":{\"req_id\":17}}]}";
        AppLog.writeRest(context, json);
        AppLog.writeRest(context, "{\"event\":\"api:logs_lagged\",\"dropped\":12}");
        String output = AppLog.read(context, true);
        assertTrue(output.contains("2026-11-21T08:00:00Z TRACE shadowquic::test fixture:record Synthetic record"));
        assertTrue(output.contains("\"bytes\":0"));
        assertTrue(output.contains("\"req_id\":17"));
        assertTrue(output.contains("WARN REST log stream dropped 12 records"));
        try { AppLog.writeRest(context, "{}"); fail("Invalid record accepted"); }
        catch (org.json.JSONException expected) { }
        assertEquals(output, AppLog.read(context, true));
    }

    public void testRestRetentionRotatesAndFullExportIsIndependentOfTail() throws Exception {
        String message = new String(new char[64000]).replace('\0', 'x');
        JSONObject record = new JSONObject().put("timestamp", "2026-11-21T08:00:00Z")
            .put("level", "INFO").put("target", "shadowquic::test");
        for (int i = 0; i < 20; i++) {
            record.put("fields", new JSONObject().put("message", "fixture-" + i + " " + message));
            AppLog.writeRest(context, record.toString());
        }
        File logs = AppLog.directory(context);
        assertTrue(new File(logs, "rest.log").length() <= 1024 * 1024);
        assertTrue(new File(logs, "rest.log.1").isFile());
        assertTrue(new File(logs, "rest.log.1").length() <= 1024 * 1024);
        String exported = AppLog.read(context, true), tail = AppLog.read(context, false);
        assertTrue(exported.contains("fixture-0 "));
        assertTrue(exported.contains("fixture-19 "));
        assertTrue(exported.length() > tail.length());
    }

    public void testAuthenticatedStreamReconnectsAndClosePreventsFurtherWrites() throws Exception {
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        int port;
        try (java.net.ServerSocket available = new java.net.ServerSocket(0)) { port = available.getLocalPort(); }
        String yaml = "outbounds: [{tag: direct, type: direct}]\nrestful-api: {bind-addr: '127.0.0.1:"
            + port + "', secret: synthetic-reconnect-secret}\n";
        RestLogStream stream = null;
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core)) {
            NativeBridge.start(descriptor.getFd(), yaml);
            stream = new RestLogStream(context, new ShadowQuicApi());
            await(stream, true);
            NativeBridge.stop();
            await(stream, false);
            NativeBridge.start(descriptor.getFd(), yaml);
            await(stream, true);
            assertFalse(AppLog.read(context, true).contains("synthetic-reconnect-secret"));
            stream.close();
            String snapshot = AppLog.read(context, true);
            NativeBridge.stop();
            NativeBridge.start(descriptor.getFd(), yaml);
            Thread.sleep(800);
            assertFalse(stream.isConnected());
            assertEquals(snapshot, AppLog.read(context, true));
        } finally {
            if (stream != null) stream.close();
            NativeBridge.stop();
            Os.close(app);
            Os.close(core);
        }
    }

    public void testInvalidAuthorizationHeaderDoesNotExposeTheSecret() throws Exception {
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core)) {
            NativeBridge.start(descriptor.getFd(), "outbounds: [{tag: direct, type: direct}]\n"
                + "restful-api: {bind-addr: '127.0.0.1:9090', secret: \"sensitive-fixture\\nprivate\"}\n");
            try {
                new ShadowQuicApi().logStreamRequest();
                fail("Invalid authorization header accepted");
            } catch (java.io.IOException expected) {
                assertFalse(expected.getMessage().contains("sensitive-fixture"));
                assertNull(expected.getCause());
            }
        } finally {
            NativeBridge.stop();
            Os.close(app);
            Os.close(core);
        }
    }

    private void await(RestLogStream stream, boolean connected) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 7000;
        while (stream.isConnected() != connected && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
        assertEquals(AppLog.read(context, true), connected, stream.isConnected());
    }

    @Override protected void tearDown() throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(root.toPath())) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(path);
        } finally { super.tearDown(); }
    }
}
