package dev.squirrel.vpn;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Real JNI/HTTP checks for YAML REST settings and session cleanup. */
@SuppressWarnings("deprecation")
public final class RestConfigInstrumentedTest extends InstrumentationTestCase {
    private static final String DIRECT = "outbounds: [{tag: direct, type: direct}]\n";

    public void testConfiguredListenersAuthenticationAndDashboardSurviveRestarts() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        File ui = new File(context.getCacheDir(), "rest-ui-" + System.nanoTime());
        assertTrue(ui.mkdir());
        File index = new File(ui, "index.html");
        String html = "<html><body>Synthetic REST dashboard</body></html>";
        Files.write(index.toPath(), html.getBytes(StandardCharsets.UTF_8));
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core)) {
            String[][] cases = {
                {"127.0.0.1", "127.0.0.1", ", secret: synthetic-test-only"},
                {"0.0.0.0", "127.0.0.1", ""},
                {"[::1]", "[::1]", ", secret: ''"},
                {"[::]", "[::1]", ", secret: synthetic-test-only"},
            };
            for (String[] entry : cases) {
                int port;
                try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getByName(entry[1]))) {
                    port = available.getLocalPort();
                }
                String yaml = DIRECT + "restful-api: {bind-addr: '" + entry[0] + ":" + port
                    + "', tag: dashboard-test, ui-path: '" + ui.getAbsolutePath()
                    + "', ui-download-url: 'https://example.com/unused.html'" + entry[2] + "}\n";
                try {
                    NativeBridge.start(descriptor.getFd(), ConfigStore.forRuntime(context, yaml),
                        new File(context.getCacheDir(), "rest-config-test.log").getPath());
                    ShadowQuicApi api = new ShadowQuicApi();
                    JSONObject traffic = null;
                    for (int attempt = 0; attempt < 80; attempt++) {
                        try { traffic = api.get("/traffic"); break; }
                        catch (IOException ignored) { Thread.sleep(50); }
                    }
                    assertNotNull("Configured listener did not become ready: " + entry[0], traffic);
                    String base = "http://" + entry[1] + ":" + port;
                    assertEquals(base + "/api/v1", new JSONObject(NativeBridge.runtimeApiSettings()).getString("baseUrl"));
                    boolean authenticated = entry[2].contains("synthetic-test-only");
                    HttpURLConnection request = (HttpURLConnection) new URL(base + "/api/v1/traffic").openConnection();
                    try {
                        request.setConnectTimeout(2000); request.setReadTimeout(2000);
                        assertEquals(authenticated ? 401 : 200, request.getResponseCode());
                    } finally { request.disconnect(); }
                    HttpURLConnection dashboard = (HttpURLConnection) new URL(base + "/").openConnection();
                    try {
                        dashboard.setConnectTimeout(2000); dashboard.setReadTimeout(2000);
                        assertEquals(200, dashboard.getResponseCode());
                        try (java.io.InputStream body = dashboard.getInputStream();
                                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                            byte[] buffer = new byte[1024]; int count;
                            while ((count = body.read(buffer)) != -1) bytes.write(buffer, 0, count);
                            assertEquals(html, bytes.toString(StandardCharsets.UTF_8.name()));
                        }
                    } finally { dashboard.disconnect(); }
                } finally { NativeBridge.stop(); }
                expectUnavailable("not running");
            }
        } finally {
            NativeBridge.stop(); Os.close(app); Os.close(core);
            Files.deleteIfExists(index.toPath()); Files.deleteIfExists(ui.toPath());
        }
    }

    public void testMissingRestUsesDefaultAndExplicitNullOrEphemeralSettingsArePreserved() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core)) {
            for (String section : new String[]{"", "restful-api: null\n", "restful-api: {bind-addr: '127.0.0.1:0'}\n"}) {
                try {
                    NativeBridge.start(descriptor.getFd(), ConfigStore.forRuntime(context, DIRECT + section),
                        new File(context.getCacheDir(), "rest-disabled-test.log").getPath());
                    assertEquals("", NativeBridge.status());
                    if (section.isEmpty()) {
                        JSONObject settings = new JSONObject(NativeBridge.runtimeApiSettings());
                        assertEquals("http://127.0.0.1:9090/api/v1", settings.getString("baseUrl"));
                        assertEquals("", settings.getString("secret"));
                        ShadowQuicApi api = new ShadowQuicApi();
                        JSONObject traffic = null;
                        for (int attempt = 0; attempt < 80; attempt++) {
                            try { traffic = api.get("/traffic"); break; }
                            catch (IOException ignored) { Thread.sleep(50); }
                        }
                        assertNotNull("Default REST listener did not become ready", traffic);
                    } else if (section.contains("bind-addr")) {
                        expectUnavailable("fixed restful-api port");
                    } else {
                        assertEquals("null", NativeBridge.runtimeApiSettings());
                        expectUnavailable("disabled");
                    }
                } finally { NativeBridge.stop(); }
            }
        } finally { NativeBridge.stop(); Os.close(app); Os.close(core); }
    }

    private void expectUnavailable(String message) throws Exception {
        try { new ShadowQuicApi(); fail("Expected unavailable REST client"); }
        catch (IOException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
}
