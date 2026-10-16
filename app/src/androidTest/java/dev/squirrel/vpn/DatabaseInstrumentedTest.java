package dev.squirrel.vpn;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.FileDescriptor;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Exercises config-file loading and the real native database downloader. */
@SuppressWarnings("deprecation")
public final class DatabaseInstrumentedTest extends InstrumentationTestCase {
    public void testRelativeDatabaseDownloadsIntoPrivateStorageAndSurvivesRestart() throws Exception {
        Context target = getInstrumentation().getTargetContext();
        File root = new File(target.getFilesDir(), "database-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        Context context = new ContextWrapper(target) {
            @Override public File getFilesDir() { return root; }
        };
        File database = new File(root, "data/geosite.redb");
        File log = new File(root, "core.log");
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        Thread responder = null;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core)) {
            server.setSoTimeout(5000);
            responder = new Thread(() -> {
                try (Socket client = server.accept()) {
                    client.setSoTimeout(5000);
                    java.io.InputStream input = client.getInputStream();
                    StringBuilder headers = new StringBuilder();
                    while (!headers.toString().endsWith("\r\n\r\n")) {
                        int value = input.read();
                        if (value < 0) throw new java.io.IOException("Incomplete request");
                        headers.append((char) value);
                    }
                    byte[] body = "lists:\n  - name: test\n    rules: ['full:example.com']\n".getBytes(StandardCharsets.UTF_8);
                    client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    client.getOutputStream().write(body);
                    client.getOutputStream().flush();
                } catch (Throwable error) { serverError.set(error); }
            });
            responder.start();
            String source = "# Keep portable paths in the saved YAML.\noutbounds: [{type: direct, tag: direct}]\n"
                + "log-level: debug\nandroid: {dns-servers: [9.9.9.9]}\nrouter:\n"
                + "  database:\n    - type: geosite\n      tag: test-db\n      url: http://127.0.0.1:"
                + server.getLocalPort() + "/database\n      path: data/geosite.redb\n"
                + "  src: |\n    return function(ctx) return 'direct' end\n";
            ConfigStore.save(context, source);
            String config = ConfigStore.readForVpn(context);
            assertEquals(source, ConfigStore.read(context));
            assertEquals(database.getPath(), new JSONObject(config).getJSONObject("router")
                .getJSONArray("database").getJSONObject(0).getString("path"));
            assertEquals("9.9.9.9", new JSONObject(NativeBridge.validate(config))
                .getJSONArray("dns").getString(0));
            NativeBridge.start(descriptor.getFd(), config, log.getPath());
            String output = "";
            long deadline = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < deadline) {
                output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
                if (output.contains("router database ready") || output.contains("download/import failed")) break;
                Thread.sleep(50);
            }
            responder.join(1000);
            assertNull(serverError.get());
            assertTrue(output, database.isFile() && output.contains("router database ready"));
            NativeBridge.stop();
            server.close();
            // The source is now unavailable. A restart must open the private
            // database already on disk without attempting another download.
            NativeBridge.start(descriptor.getFd(), ConfigStore.readForVpn(context), log.getPath());
            assertEquals("", NativeBridge.status());
            Thread.sleep(200);
            assertFalse(new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8)
                .contains("download/import failed"));
        } finally {
            NativeBridge.stop();
            if (responder != null) responder.join(5500);
            Os.close(app);
            Os.close(core);
            deleteTree(root);
        }
    }

    public void testNormalizationPreservesAbsolutePathsAndRejectsEscapes() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        String source = "outbounds: [{type: direct, tag: direct}]\nrouter:\n  database:\n"
            + "    - {type: country, tag: db, path: '" + new File(context.getFilesDir(), "existing.mmdb").getPath() + "'}\n";
        String normalized = ConfigStore.forRuntime(context, source);
        assertEquals(new File(context.getFilesDir(), "existing.mmdb").getPath(), new JSONObject(normalized)
            .getJSONObject("router").getJSONArray("database").getJSONObject(0).getString("path"));
        try {
            ConfigStore.forRuntime(context, source.replace(new File(context.getFilesDir(), "existing.mmdb").getPath(), "../escape.mmdb"));
            fail("A relative database path escaped private storage");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("escapes"));
        }
    }

    private void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        assertTrue("Cannot remove test file " + file, file.delete());
    }
}
