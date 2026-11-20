package dev.squirrel.vpn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The single-node URI described in RealBikiniBottom/QuicProxy discussion 2. */
final class ShadowQuicLink {
    private static final List<String> ALPNS = Arrays.asList("h3", "h2", "http/1.1");
    final String name;
    final Map<String, Object> outbound;
    final boolean fallbackCongestion;

    private ShadowQuicLink(String name, Map<String, Object> outbound, boolean fallbackCongestion) {
        this.name = name;
        this.outbound = outbound;
        this.fallbackCongestion = fallbackCongestion;
    }

    static ShadowQuicLink parse(String text) throws IOException {
        URI uri;
        try { uri = new URI(text); }
        catch (URISyntaxException e) { throw new IOException("Invalid ShadowQUIC link encoding."); }
        if (!"sq".equalsIgnoreCase(uri.getScheme()) && !"shadowquic".equalsIgnoreCase(uri.getScheme()))
            throw new IOException("Scan an sq:// or shadowquic:// node QR code.");
        if (!"".equals(uri.getRawPath()) && !"/".equals(uri.getRawPath()))
            throw new IOException("A node link must contain a server address without a path.");
        String authority = uri.getRawAuthority();
        int at = authority == null ? -1 : authority.indexOf('@');
        if (at < 0 || at != authority.lastIndexOf('@')) throw new IOException("The node needs one username and password.");
        String[] login = authority.substring(0, at).split(":", 2);
        if (login.length != 2) throw new IOException("The node needs a username and password.");
        String address = address(authority.substring(at + 1), true);
        Map<String, String> query = new HashMap<>();
        if (uri.getRawQuery() != null) for (String parameter : uri.getRawQuery().split("&")) {
            String[] parts = parameter.split("=", 2);
            String key = decode(parts[0]);
            if (query.put(key, parts.length == 1 ? "" : decode(parts[1])) != null)
                throw new IOException("The node link repeats a query parameter.");
        }

        Map<String, Object> node = new LinkedHashMap<>();
        String name = decode(uri.getRawFragment() == null ? "" : uri.getRawFragment());
        if (name.trim().isEmpty()) name = address;
        node.put("tag", name);
        node.put("type", "shadowquic");
        node.put("addr", address);
        node.put("username", required(decode(login[0]), "username"));
        node.put("password", required(decode(login[1]), "password"));
        node.put("server-name", required(query.get("sni"), "sni"));
        String mode = query.getOrDefault("udp_mode", "stream");
        if (!mode.equals("stream") && !mode.equals("datagram")) throw new IOException("Unknown UDP mode.");
        node.put("over-stream", mode.equals("stream"));
        node.put("zero-rtt", query.containsKey("zero_rtt"));
        int mtu = number(query.getOrDefault("mtu", "1280"), 1200, "MTU");
        node.put("initial-mtu", mtu);
        node.put("min-mtu", mtu);
        List<String> alpn = new ArrayList<>();
        String protocols = query.getOrDefault("alpn", "");
        if (!protocols.isEmpty()) alpn.addAll(Arrays.asList(protocols.split(",", -1)));
        if (!ALPNS.containsAll(alpn)) throw new IOException("Unsupported ALPN protocol.");
        node.put("alpn", alpn);
        String congestion = query.getOrDefault("congestion", "bbr");
        switch (congestion) {
            case "bbr": case "cubic": node.put("congestion-control", congestion); break;
            case "bbrv3": node.put("congestion-control", "bbr3"); break;
            case "brutal": node.put("congestion-control", Collections.singletonMap("brutal", Collections.emptyMap())); break;
            case "copa": node.put("congestion-control", "bbr"); break;
            default: throw new IOException("Unknown congestion algorithm.");
        }
        return new ShadowQuicLink(name, node, congestion.equals("copa"));
    }

    static String encode(Map<?, ?> node, String name) throws IOException {
        String endpoint = address(field(node, "addr"), false);
        StringBuilder link = new StringBuilder("sq://").append(escape(field(node, "username")))
            .append(':').append(escape(field(node, "password"))).append('@').append(endpoint)
            .append("?sni=").append(escape(field(node, "server-name")));
        // These are the pinned core's YAML defaults. Several differ from the URI defaults.
        if (!bool(node, "over-stream", false)) link.append("&udp_mode=datagram");
        if (bool(node, "zero-rtt", true)) link.append("&zero_rtt=true");
        int mtu = number(String.valueOf(node.containsKey("initial-mtu") ? node.get("initial-mtu") : 1300), 1200, "MTU");
        if (mtu != 1280) link.append("&mtu=").append(mtu);
        Object alpn = node.containsKey("alpn") ? node.get("alpn") : Collections.singletonList("h3");
        if (!(alpn instanceof List<?>) || !ALPNS.containsAll((List<?>) alpn)) throw new IOException("Unsupported ALPN protocol.");
        List<String> values = new ArrayList<>();
        for (Object protocol : (List<?>) alpn) values.add(escape((String) protocol));
        if (!values.isEmpty()) link.append("&alpn=").append(String.join(",", values));
        Object congestion = node.containsKey("congestion-control") ? node.get("congestion-control") : "bbr";
        if (congestion instanceof Map && ((Map<?, ?>) congestion).containsKey("brutal")) link.append("&congestion=brutal");
        else if ("bbr3".equals(congestion)) link.append("&congestion=bbrv3");
        else if ("cubic".equals(congestion)) link.append("&congestion=cubic");
        else if (!"bbr".equals(congestion)) throw new IOException("The node's congestion algorithm has no share-link equivalent.");
        return link.append('#').append(escape(name)).toString();
    }

    private static String field(Map<?, ?> node, String key) throws IOException {
        Object value = node.get(key);
        return required(value instanceof String ? (String) value : null, key);
    }

    private static String required(String value, String field) throws IOException {
        if (value == null || value.isEmpty()) throw new IOException("Missing node field: " + field);
        return value;
    }

    private static boolean bool(Map<?, ?> node, String key, boolean fallback) throws IOException {
        if (!node.containsKey(key)) return fallback;
        if (!(node.get(key) instanceof Boolean)) throw new IOException("Invalid node field: " + key);
        return (Boolean) node.get(key);
    }

    private static String address(String input, boolean defaultPort) throws IOException {
        int split = input.startsWith("[") ? input.indexOf(']') + 1 : input.indexOf(':');
        if (split == -1) split = input.length();
        String host = input.substring(0, split);
        try {
            if (host.startsWith("[")) {
                if (new URI("sq://" + host).getHost() == null) throw new IllegalArgumentException();
            } else {
                host = IDN.toASCII(decode(host));
                if (!host.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | URISyntaxException e) { throw new IOException("Invalid server host."); }
        if (split == input.length() && defaultPort) return host + ":443";
        if (split >= input.length() || input.charAt(split) != ':') throw new IOException("Invalid server address or port.");
        return host + ":" + number(input.substring(split + 1), 1, "port");
    }

    private static int number(String text, int minimum, String field) throws IOException {
        try {
            if (text.matches("[0-9]+")) {
                int value = Integer.parseInt(text);
                if (value >= minimum && value <= 65535) return value;
            }
        } catch (NumberFormatException ignored) { }
        throw new IOException("Invalid node " + field + ".");
    }

    private static String escape(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    // RFC 3986 treats '+' literally in userinfo, query components and fragments.
    private static String decode(String raw) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < raw.length();) {
            if (raw.charAt(i) != '%') { result.append(raw.charAt(i++)); continue; }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            while (i < raw.length() && raw.charAt(i) == '%') {
                if (i + 2 >= raw.length()) throw new IOException("Invalid percent encoding.");
                int high = Character.digit(raw.charAt(i + 1), 16), low = Character.digit(raw.charAt(i + 2), 16);
                if (high < 0 || low < 0) throw new IOException("Invalid percent encoding.");
                bytes.write(high * 16 + low);
                i += 3;
            }
            try { result.append(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes.toByteArray()))); }
            catch (CharacterCodingException e) { throw new IOException("Node fields must be UTF-8 text."); }
        }
        for (int i = 0; i < result.length(); i++) if (Character.isISOControl(result.charAt(i)))
            throw new IOException("Node fields must be single-line text.");
        return result.toString();
    }
}
