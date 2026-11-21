package dev.squirrel.vpn;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import com.google.zxing.common.BitMatrix;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.snakeyaml.engine.v2.nodes.Node;

@SuppressWarnings("deprecation")
public final class NodeSharingInstrumentedTest extends InstrumentationTestCase {
    static final String LINK = "shadowquic://u%40+p:p%3A%2F%23%26%25@[2001:db8::1]:8443"
        + "?sni=cdn.example.test&udp_mode=datagram&zero_rtt=false&mtu=1400"
        + "&alpn=h3,h2,http%2F1.1&congestion=bbrv3#%E6%9D%B1%20%2B%20West";

    public void testUriMapsDefaultsAndRoundTripsEscapedComponents() throws Exception {
        ShadowQuicLink parsed = ShadowQuicLink.parse(LINK);
        Map<String, Object> fields = parsed.outbound;
        assertEquals("東 + West", parsed.name);
        assertEquals("u@+p", fields.get("username"));
        assertEquals("p:/#&%", fields.get("password"));
        assertEquals("[2001:db8::1]:8443", fields.get("addr"));
        assertEquals(false, fields.get("over-stream"));
        assertEquals(true, fields.get("zero-rtt"));
        assertEquals(1400, fields.get("min-mtu"));
        assertEquals(1400, fields.get("initial-mtu"));
        assertEquals(Arrays.asList("h3", "h2", "http/1.1"), fields.get("alpn"));
        assertEquals("bbr3", fields.get("congestion-control"));
        String encoded = ShadowQuicLink.encode(fields, parsed.name);
        assertTrue(encoded.startsWith("sq://"));
        assertEquals(fields, ShadowQuicLink.parse(encoded).outbound);

        parsed = ShadowQuicLink.parse("sq://u:p@example.test?sni=cdn.example.test");
        assertEquals("example.test:443", parsed.name);
        assertEquals(false, parsed.outbound.get("zero-rtt"));
        assertEquals(true, parsed.outbound.get("over-stream"));
        assertEquals(1280, parsed.outbound.get("min-mtu"));
        assertEquals(Collections.emptyList(), parsed.outbound.get("alpn"));
        assertEquals("bbr", parsed.outbound.get("congestion-control"));

        String minimal = "outbound: {type: shadowquic, addr: 'host:443', username: u, password: p, server-name: host}";
        Map<String, Node> legacy = new NodeConfiguration(minimal).shadowQuicNodes();
        parsed = ShadowQuicLink.parse(NodeConfiguration.link(legacy.get("outbound"), "outbound"));
        assertEquals(false, parsed.outbound.get("over-stream"));
        assertEquals(true, parsed.outbound.get("zero-rtt"));
        assertEquals(1300, parsed.outbound.get("initial-mtu"));
        assertEquals(Collections.singletonList("h3"), parsed.outbound.get("alpn"));
    }

    public void testRejectsInvalidLinksWithoutLeakingCredentials() throws Exception {
        for (String address : new String[]{"host", "host:0?sni=x", "host:65536?sni=x", "host?sni=",
                "host?sni=x&mtu=1199", "host?sni=x&alpn=unknown", "host?sni=x&udp_mode=tcp",
                "host?sni=x&congestion=other", "host/path?sni=x", "host?sni=%FF", "host?sni=x#%0A",
                "host?sni=x&sni=y", "host?sni=%XX", "[::broken]?sni=x"}) {
            try { ShadowQuicLink.parse("sq://u:private-fixture@" + address); fail("Invalid link accepted"); }
            catch (IOException e) { assertFalse(e.getMessage().contains("private-fixture")); }
        }
        for (String uri : new String[]{"https://server.test/list", "sq://:pass@host?sni=x",
                "sq://user:@host?sni=x", "sq://host?sni=x", "sq://u:p@a@b?sni=x"}) {
            try { ShadowQuicLink.parse(uri); fail("Invalid credentials or scheme accepted"); }
            catch (IOException expected) { }
        }
    }

    public void testImportAppendsOrReplacesTemplateProxyAndValidates() throws Exception {
        String template = ConfigStore.asset(getInstrumentation().getTargetContext(), "example.yaml");
        JSONObject original = runtime(template);
        for (boolean replace : new boolean[]{true, false}) {
            ShadowQuicLink parsed = ShadowQuicLink.parse(LINK);
            String yaml = new NodeConfiguration(template).insert(parsed, replace);
            JSONObject config = runtime(yaml);
            assertEquals(original.getJSONObject("router").toString(), config.getJSONObject("router").toString());
            assertEquals(original.getJSONArray("dns").toString(), config.getJSONArray("dns").toString());
            assertTrue(yaml.contains("# GeoLite2 Country"));
            JSONArray out = config.getJSONArray("outbounds");
            JSONArray members = out.getJSONObject(0).getJSONArray("outbounds");
            assertEquals(replace ? 3 : 4, out.length());
            assertEquals(parsed.name, members.getString(replace ? 0 : 2));
            if (replace) assertEquals("tls-dns", out.getJSONObject(1).getString("addr-resolver"));
            else assertEquals("proxy", members.getString(0));
            Map<String, Node> nodes = new NodeConfiguration(yaml).shadowQuicNodes();
            String shared = NodeConfiguration.link(nodes.get(parsed.name), parsed.name);
            assertEquals(parsed.outbound, ShadowQuicLink.parse(shared).outbound);
        }
    }

    public void testLegacyProfilesCollisionsAndSupportedCongestionSettings() throws Exception {
        for (String cc : new String[]{"bbr", "bbrv3", "brutal", "cubic", "copa"}) {
            ShadowQuicLink link = ShadowQuicLink.parse("sq://1:2@host?sni=x&congestion=" + cc + "#outbound");
            String yaml = new NodeConfiguration("outbound: {type: direct}\n").insert(link, false);
            NativeBridge.validate(yaml);
            assertEquals(cc.equals("copa"), link.fallbackCongestion);
            Map<String, Node> nodes = new NodeConfiguration(yaml).shadowQuicNodes();
            String tag = "outbound (2)";
            assertTrue(nodes.containsKey(tag));
            assertEquals(link.outbound.get("congestion-control"),
                ShadowQuicLink.parse(NodeConfiguration.link(nodes.get(tag), tag)).outbound.get("congestion-control"));
        }
    }

    public void testPngExportKeepsWholeModulesAndQuietZone() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        File image = File.createTempFile("node-", ".png", context.getCacheDir());
        try {
            BitMatrix modules = NodeQr.encode(LINK);
            Uri uri = Uri.fromFile(image);
            NodeQrImage.write(context.getContentResolver(), uri, modules);
            Bitmap png = BitmapFactory.decodeFile(image.getPath());
            assertNotNull(png);
            try {
                int size = png.getWidth();
                assertEquals(size, png.getHeight());
                assertEquals(0, size % modules.getWidth());
                int scale = size / modules.getWidth();
                assertEquals(Color.WHITE, png.getPixel(4 * scale - 1, 4 * scale));
                assertEquals(Color.BLACK, png.getPixel(4 * scale, 4 * scale));
            } finally { png.recycle(); }
            assertEquals(LINK, NodeQrImage.read(context.getContentResolver(), uri));
        } finally { java.nio.file.Files.deleteIfExists(image.toPath()); }
    }

    public void testScanLargeRotatedLightOnDarkQrImage() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        File image = File.createTempFile("node-image-", ".jpg", context.getCacheDir());
        Bitmap photo = Bitmap.createBitmap(3200, 1200, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(photo);
            canvas.drawColor(Color.DKGRAY);
            canvas.rotate(90, 1800, 600);
            BitMatrix modules = NodeQr.encode(LINK);
            Paint ink = new Paint();
            ink.setColor(Color.WHITE);
            NodeQr.drawModules(canvas, modules, 1450, 250, 12, ink);
            try (FileOutputStream output = new FileOutputStream(image)) {
                assertTrue(photo.compress(Bitmap.CompressFormat.JPEG, 85, output));
            }
            assertEquals(LINK, NodeQrImage.read(context.getContentResolver(), Uri.fromFile(image)));
        } finally {
            photo.recycle();
            java.nio.file.Files.deleteIfExists(image.toPath());
        }
    }

    public void testUnreadableFileAndImageWithoutQrReportDifferentErrors() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        File image = File.createTempFile("no-node-", ".png", context.getCacheDir());
        try {
            assertImageError(context, image, "readable image file");
            Bitmap blank = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            try (FileOutputStream out = new FileOutputStream(image)) {
                blank.eraseColor(Color.WHITE);
                assertTrue(blank.compress(Bitmap.CompressFormat.PNG, 100, out));
            } finally { blank.recycle(); }
            assertImageError(context, image, "No readable QR code");
        } finally { java.nio.file.Files.deleteIfExists(image.toPath()); }
    }

    private void assertImageError(Context context, File image, String message) throws Exception {
        try {
            NodeQrImage.read(context.getContentResolver(), Uri.fromFile(image));
            fail("Expected unreadable QR image");
        } catch (IOException error) { assertTrue(error.getMessage(), error.getMessage().contains(message)); }
    }

    private JSONObject runtime(String yaml) throws Exception {
        String prepared = ConfigStore.forRuntime(getInstrumentation().getTargetContext(), yaml);
        NativeBridge.validate(prepared);
        return new JSONObject(prepared);
    }
}
