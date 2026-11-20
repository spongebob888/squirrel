package dev.squirrel.vpn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.api.lowlevel.Present;
import org.snakeyaml.engine.v2.api.lowlevel.Serialize;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.constructor.StandardConstructor;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;
import org.snakeyaml.engine.v2.schema.CoreSchema;

/** Edit YAML nodes structurally, retaining comments, scalar styles and document order. */
final class NodeConfiguration {
    private static final LoadSettings LOAD = LoadSettings.builder().setSchema(new CoreSchema())
        .setParseComments(true).setCodePointLimit(ConfigStore.MAX_BYTES).build();
    private final MappingNode root;
    private final SequenceNode outbounds;

    NodeConfiguration(String text) throws IOException {
        if (text.getBytes(StandardCharsets.UTF_8).length > ConfigStore.MAX_BYTES)
            throw new IOException("Configuration exceeds 1 MiB.");
        Node document = new Compose(LOAD).composeString(text).orElse(null);
        if (!(document instanceof MappingNode)) throw new IOException("Configuration must be a YAML mapping.");
        root = (MappingNode) document;
        Node entries = field(root, "outbounds");
        String key = "outbounds";
        if (field(root, "outbound") != null) {
            if (entries != null) throw new IOException("Use either outbound or outbounds.");
            key = "outbound";
            entries = field(root, key);
        }
        if (entries instanceof SequenceNode) {
            outbounds = (SequenceNode) entries;
        } else {
            outbounds = new SequenceNode(Tag.SEQ, new ArrayList<>(), FlowStyle.BLOCK);
            if (entries != null) {
                if (!(entries instanceof MappingNode)) throw new IOException("Invalid outbound configuration.");
                if (field(entries, "tag") == null) put((MappingNode) entries, "tag", string("outbound"));
                outbounds.getValue().add(entries);
            }
            put(root, key, outbounds);
        }
    }

    Map<String, Node> shadowQuicNodes() throws IOException {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Node entry : outbounds.getValue()) {
            if (!"shadowquic".equals(value(field(entry, "type")))) continue;
            String tag = value(field(entry, "tag"));
            if (tag.isEmpty() || nodes.put(tag, entry) != null) throw new IOException("ShadowQUIC nodes need unique tags.");
        }
        return nodes;
    }

    static String link(Node node, String tag) throws IOException {
        Object values = new StandardConstructor(LOAD).constructSingleDocument(Optional.of(node));
        return ShadowQuicLink.encode((Map<?, ?>) values, tag);
    }

    String insert(ShadowQuicLink link, boolean template) throws IOException {
        Map<String, Node> existing = shadowQuicNodes();
        Node placeholder = template ? existing.get("proxy") : null;
        if (template && placeholder == null) throw new IOException("The template is missing its proxy outbound.");
        Set<String> used = new HashSet<>();
        for (Node entry : outbounds.getValue()) if (entry != placeholder) used.add(value(field(entry, "tag")));
        for (String key : new String[]{"inbounds", "inbound", "dns"}) collectTags(field(root, key), used);
        collectTags(field(field(root, "router"), "database"), used);
        Node api = field(root, "restful-api");
        collectTags(api, used);
        if (api == null || (api instanceof MappingNode && field(api, "tag") == null)) used.add("restful-api");
        Node inbound = field(root, "inbound");
        if (inbound instanceof MappingNode && field(inbound, "tag") == null) used.add("inbound");
        used.add("default-system");
        Node listeners = inbound != null ? inbound : field(root, "inbounds");
        boolean hasTun = "tun".equals(value(field(listeners, "type")));
        if (listeners instanceof SequenceNode) for (Node item : ((SequenceNode) listeners).getValue())
            hasTun |= "tun".equals(value(field(item, "type")));
        if (!hasTun) used.add("squirrel-tun");
        String candidate = link.name;
        for (int n = 2; used.contains(candidate); n++) candidate = link.name + " (" + n + ")";
        String tag = candidate;

        DumpSettings dump = DumpSettings.builder().setSchema(new CoreSchema())
            .setDefaultFlowStyle(FlowStyle.BLOCK).setDumpComments(true).setSplitLines(false).build();
        StandardRepresenter representer = new StandardRepresenter(dump);
        MappingNode node = template ? (MappingNode) placeholder : new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
        for (Map.Entry<String, Object> entry : link.outbound.entrySet()) {
            Object data = entry.getKey().equals("tag") ? tag : entry.getValue();
            put(node, entry.getKey(), data instanceof String ? string((String) data) : representer.represent(data));
        }

        // The bundled router references its selector. Preserve the default route when
        // appending to a profile, and leave membership to the user for multiple selectors.
        List<SequenceNode> selectors = new ArrayList<>();
        for (Node entry : outbounds.getValue()) {
            if ("selector".equals(value(field(entry, "type"))) && field(entry, "outbounds") instanceof SequenceNode)
                selectors.add((SequenceNode) field(entry, "outbounds"));
        }
        if (template) {
            for (SequenceNode members : selectors) members.getValue().replaceAll(member ->
                "proxy".equals(value(member)) ? string(tag) : member);
        } else {
            outbounds.getValue().add(node);
            if (selectors.size() == 1) selectors.get(0).getValue().add(string(tag));
        }
        return new Present(dump).emitToString(new Serialize(dump).serializeOne(root).iterator());
    }

    private static void collectTags(Node node, Set<String> tags) {
        if (node instanceof SequenceNode) {
            for (Node entry : ((SequenceNode) node).getValue()) tags.add(value(field(entry, "tag")));
        } else if (node instanceof MappingNode) tags.add(value(field(node, "tag")));
    }

    private static String value(Node node) {
        return node instanceof ScalarNode ? ((ScalarNode) node).getValue() : "";
    }

    private static Node field(Node node, String key) {
        if (node instanceof MappingNode) for (NodeTuple entry : ((MappingNode) node).getValue())
            if (key.equals(value(entry.getKeyNode()))) return entry.getValueNode();
        return null;
    }

    private static ScalarNode string(String text) {
        return new ScalarNode(Tag.STR, text, ScalarStyle.DOUBLE_QUOTED);
    }

    private static void put(MappingNode node, String key, Node data) {
        List<NodeTuple> entries = node.getValue();
        for (int i = 0; i < entries.size(); i++) {
            NodeTuple old = entries.get(i);
            if (key.equals(value(old.getKeyNode()))) {
                entries.set(i, new NodeTuple(old.getKeyNode(), data));
                return;
            }
        }
        entries.add(new NodeTuple(new ScalarNode(Tag.STR, key, ScalarStyle.PLAIN), data));
    }
}
