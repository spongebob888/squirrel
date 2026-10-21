package dev.squirrel.vpn;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.FileDescriptor;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Exercises the real JNI library and TUN packet engine with a datagram socket pair. */
@SuppressWarnings("deprecation")
public final class CoreInstrumentedTest extends InstrumentationTestCase {
    public void testUdpPacketRoundTripAndRestart() throws Exception {
        udpPacketRoundTripAndRestart("outbounds: [{tag: direct, type: direct}]\nlog-level: debug\n");
    }

    public void testUdpPacketRoundTripAndRestartWithIpv6Disabled() throws Exception {
        String config = "outbounds: [{tag: direct, type: direct}]\n"
            + "inbounds: [{type: tun, tag: phone, ipv6: null}]\nlog-level: debug\n";
        JSONObject settings = new JSONObject(NativeBridge.validate(config));
        assertFalse(settings.has("ipv6"));
        assertFalse(settings.has("ipv6Prefix"));
        udpPacketRoundTripAndRestart(config);
    }

    private void udpPacketRoundTripAndRestart(String config) throws Exception {
        FileDescriptor app = new FileDescriptor(), core = new FileDescriptor();
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, app, core);
        File log = new File(getInstrumentation().getTargetContext().getCacheDir(), "native-test.log");
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.dup(core);
             DatagramSocket echo = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            echo.setSoTimeout(5000);
            assertTrue(NativeBridge.validate(config).contains("172.19.0.1"));
            // Each restart must release Rust-owned descriptors and runtime tasks.
            for (int attempt = 0; attempt < 2; attempt++) {
                AtomicReference<Throwable> echoError = new AtomicReference<>();
                Thread server = new Thread(() -> {
                    try {
                        DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
                        echo.receive(packet);
                        echo.send(packet);
                    } catch (Throwable e) { echoError.set(e); }
                });
                server.start();
                NativeBridge.start(descriptor.getFd(), config, log.getPath());
                assertEquals("", NativeBridge.status());
                byte[] payload = ("squirrel-udp-" + attempt).getBytes(StandardCharsets.UTF_8);
                byte[] packet = udpPacket(echo.getLocalPort(), payload);
                Os.write(app, packet, 0, packet.length);
                StructPollfd poll = new StructPollfd();
                poll.fd = app;
                poll.events = (short) OsConstants.POLLIN;
                assertEquals("No UDP reply from the TUN stack; log: " + new String(java.nio.file.Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8),
                    1, Os.poll(new StructPollfd[]{poll}, 5000));
                byte[] reply = new byte[2048];
                int count = Os.read(app, reply, 0, reply.length);
                assertTrue(count >= 28 + payload.length);
                assertEquals(17, reply[9]);
                assertTrue(Arrays.equals(payload, Arrays.copyOfRange(reply, 28, count)));
                server.join(5500);
                assertNull(echoError.get());
                NativeBridge.stop();
                // Native shutdown must not close Java's descriptor.
                assertTrue(Os.fstat(descriptor.getFileDescriptor()).st_mode != 0);
            }
            assertTrue(log.length() > 0);
        } finally {
            NativeBridge.stop();
            Os.close(app);
            Os.close(core);
        }
    }

    public void testInvalidConfigurationDoesNotStartCore() throws Exception {
        try {
            NativeBridge.start(-1, "outbounds: [{type: direct, tag: d}]\nandroid: {dns-servers: []}", "/unused");
            fail("Invalid configuration accepted");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("DNS"));
        }
        assertFalse(NativeBridge.status().isEmpty());
    }

    public void testConfigImportRejectsOversizeAndInvalidUtf8() throws Exception {
        for (byte[] bytes : new byte[][]{new byte[ConfigStore.MAX_BYTES + 1], new byte[]{(byte) 0xFF}}) {
            try {
                ConfigStore.readText(new java.io.ByteArrayInputStream(bytes));
                fail("Invalid import accepted");
            } catch (java.io.IOException expected) { /* expected */ }
        }
    }

    private byte[] udpPacket(int destinationPort, byte[] payload) {
        byte[] packet = new byte[28 + payload.length];
        ByteBuffer b = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) 0x45).put((byte) 0).putShort((short) packet.length)
            .putShort((short) 1).putShort((short) 0).put((byte) 64).put((byte) 17).putShort((short) 0)
            .put(new byte[]{(byte) 172, 19, 0, 2}).put(new byte[]{127, 0, 0, 1})
            .putShort((short) 31000).putShort((short) destinationPort)
            .putShort((short) (8 + payload.length)).putShort((short) 0).put(payload);
        int sum = 0;
        for (int i = 0; i < 20; i += 2) sum += (packet[i] & 255) * 256 + (packet[i + 1] & 255);
        while ((sum >>> 16) != 0) sum = (sum & 65535) + (sum >>> 16);
        ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN).putShort(10, (short) ~sum);
        return packet;
    }
}
