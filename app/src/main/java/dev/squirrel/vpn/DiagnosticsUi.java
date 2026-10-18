package dev.squirrel.vpn;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native, presentation-only views for the three runtime diagnostic screens. */
final class DiagnosticsUi {
    interface ChooseOutbound { void choose(String tag, JSONObject selector); }

    final Context context;
    final int surface, ink, secondary, primary, onPrimary, container, outline, success, error, download, panel, header;
    private final boolean compact;
    final Button connect;
    private final TextView state, detail, profile, uploaded, downloaded, tcp, udp, aliveTasks, resident, allocated;
    private final TextView connectionNotice;
    private final LinearLayout connection;
    private final RuntimeList proxies, sessions;
    private final ChooseOutbound choose;
    private final java.util.function.Consumer<String> probe;
    private AppState.Snapshot lastSnapshot;

    DiagnosticsUi(Context activity, Runnable toggle, ChooseOutbound choose,
            java.util.function.Consumer<String> probe) {
        boolean dark = (activity.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        context = new ContextThemeWrapper(activity, dark ? R.style.DiagnosticsTheme_Dark : R.style.DiagnosticsTheme);
        compact = activity.getResources().getConfiguration().screenHeightDp < 480;
        surface = color(R.color.diagnostic_surface);
        panel = color(R.color.diagnostic_panel);
        header = panel;
        ink = color(R.color.diagnostic_on_surface);
        secondary = color(R.color.diagnostic_secondary);
        primary = color(R.color.diagnostic_primary);
        onPrimary = color(R.color.diagnostic_on_primary);
        container = color(R.color.diagnostic_container);
        outline = color(R.color.diagnostic_outline);
        success = color(R.color.diagnostic_success);
        error = color(R.color.diagnostic_error);
        download = color(R.color.diagnostic_download);
        this.choose = choose;
        this.probe = probe;

        connection = column();
        title(connection, "Connection");
        if (!compact) add(connection, text("A little clarity for your network.", 14, secondary, false), 4, 18);
        LinearLayout content = compact ? row() : column();
        add(connection, content, compact ? 12 : 0, 0);
        LinearLayout controls = panel();
        LinearLayout measurements = column();
        if (compact) {
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1);
            left.setMarginEnd(dp(16));
            content.addView(controls, left);
            content.addView(measurements, new LinearLayout.LayoutParams(0, -2, 1.5f));
        } else {
            content.addView(controls, new LinearLayout.LayoutParams(-1, -2));
            add(content, measurements, 16, 0);
        }
        TextView networkTitle = text("Network status", 15, ink, true);
        icon(networkTitle, R.drawable.ic_network, primary);
        if (!compact) controls.addView(networkTitle);
        if (compact) controls.setPadding(dp(12), dp(12), dp(12), dp(12));
        state = text("Disconnected", 20, secondary, true);
        state.setId(R.id.connection_state);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(controls, state, compact ? 0 : 16, 0);
        detail = text("Ready when you are", 13, secondary, false);

        profile = text("Loading configuration…", 13, secondary, false);
        profile.setId(R.id.connection_profile);
        if (!compact) {
            add(controls, detail, 4, 0);
            add(controls, profile, 12, 0);
        }
        connect = action("Connect", toggle, true);
        connect.setId(R.id.connect_vpn);
        icon(connect, R.drawable.ic_power, onPrimary);
        // Keep the primary control above wrapping detail text in short viewports.
        add(controls, connect, compact ? 8 : 16, 0);
        if (compact) {
            add(controls, detail, 8, 0);
            add(controls, profile, 4, 0);
        }

        LinearLayout totals = row();
        LinearLayout down = panel(), up = panel();
        TextView downLabel = text("Downloaded", 12, secondary, false);
        TextView upLabel = text("Uploaded", 12, secondary, false);
        icon(downLabel, R.drawable.ic_download, primary);
        icon(upLabel, R.drawable.ic_upload, download);
        add(down, downLabel, 0, 12);
        add(up, upLabel, 0, 12);
        uploaded = number("—", 24, ink);
        downloaded = number("—", 24, ink);
        uploaded.setId(R.id.upload_total);
        downloaded.setId(R.id.download_total);
        up.addView(uploaded);
        down.addView(downloaded);
        add(up, text("Total uploaded", 11, secondary, false), 8, 0);
        add(down, text("Total downloaded", 11, secondary, false), 8, 0);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.setMarginEnd(dp(12));
        totals.addView(down, first);
        totals.addView(up, new LinearLayout.LayoutParams(0, -2, 1));
        measurements.addView(totals);
        LinearLayout engine = panel();
        add(measurements, engine, 16, 0);
        TextView engineTitle = text("Live engine", 16, ink, true);
        icon(engineTitle, R.drawable.ic_engine, primary);
        add(engine, engineTitle, 0, 8);
        tcp = metricRow(engine, "TCP sessions", R.id.tcp_metric);
        udp = metricRow(engine, "UDP sessions", R.id.udp_metric);
        aliveTasks = metricRow(engine, "Alive tasks", R.id.alive_tasks_metric);
        resident = metricRow(engine, "Resident memory", R.id.resident_metric);
        allocated = metricRow(engine, "Allocated memory", R.id.allocated_metric);
        connectionNotice = text("Connect to load live traffic and memory.", 12, secondary, false);
        connectionNotice.setId(R.id.connection_metrics_notice);
        add(engine, connectionNotice, 12, 0);
        add(connection, text("VPN active reports the local engine state. Remote availability is verified by traffic and probes.",
            12, secondary, false), 16, 0);
        add(connection, text("App bypass always enabled · TCP + UDP · IPv4 + IPv6", 12, secondary, false), 8, 16);
        proxies = new RuntimeList(false);
        sessions = new RuntimeList(true);
    }

    View connectionPage() {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        connection.setPadding(dp(22), dp(compact ? 12 : 24), dp(22), dp(12));
        scroll.addView(connection);
        scroll.setBackgroundColor(surface);
        return scroll;
    }

    View proxiesPage() { return proxies.root; }
    View sessionsPage() { return sessions.root; }

    void clearFilterFocus() {
        proxies.filter.clearFocus();
        sessions.filter.clearFocus();
    }

    void saveFilters(android.os.Bundle state) {
        state.putString("diagnostics.proxyFilter", proxies.filter.getText().toString());
        state.putString("diagnostics.sessionFilter", sessions.filter.getText().toString());
        state.putString("diagnostics.protocol", sessions.protocol);
    }

    void restoreFilters(android.os.Bundle state) {
        proxies.filter.setText(state.getString("diagnostics.proxyFilter", ""));
        sessions.filter.setText(state.getString("diagnostics.sessionFilter", ""));
        RadioGroup group = sessions.root.findViewById(R.id.protocol_filter);
        String protocol = state.getString("diagnostics.protocol", "All");
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton button = (RadioButton) group.getChildAt(i);
            if (protocol.contentEquals(button.getText())) group.check(button.getId());
        }
    }

    void renderState(AppState.Snapshot snapshot, boolean preparing, String selectedProfile) {
        String label;
        switch (snapshot.phase) {
            case CONNECTED: label = "VPN active"; break;
            case CONNECTING: label = "Connecting…"; break;
            case STOPPING: label = "Disconnecting…"; break;
            case ERROR: label = "Connection failed"; break;
            default: label = "Disconnected";
        }
        set(state, label);
        state.setTextColor(snapshot.phase == AppState.Phase.CONNECTED ? success
            : snapshot.phase == AppState.Phase.ERROR ? error : ink);
        set(detail, snapshot.detail);
        set(profile, selectedProfile == null ? "Loading configuration…" : "Profile · " + selectedProfile);
        set(connect, preparing ? "Checking configuration…" : snapshot.busy() ? "Disconnect" : "Connect");
        if (lastSnapshot == snapshot) return;
        lastSnapshot = snapshot;
        clearMetrics();
        String message = snapshot.phase == AppState.Phase.CONNECTED ? "Loading live metrics…"
            : snapshot.phase == AppState.Phase.CONNECTING ? "Waiting for the local engine…"
            : snapshot.phase == AppState.Phase.STOPPING ? "Closing the local engine…"
            : "Connect to load live traffic and memory.";
        notice(connectionNotice, message, false);
        proxies.reset(snapshot.phase);
        sessions.reset(snapshot.phase);
    }

    void renderNetworkStats(JSONObject response) {
        JSONObject memory = response.optJSONObject("memory"), traffic = response.optJSONObject("traffic");
        long tcpCount = 0, udpCount = 0, tcpUp = 0, tcpDown = 0, udpUp = 0, udpDown = 0;
        JSONArray inbounds = traffic == null ? null : traffic.optJSONArray("inbounds");
        if (inbounds == null) {
            set(uploaded, "—"); set(downloaded, "—"); set(tcp, "—"); set(udp, "—");
        } else {
            for (int i = 0; i < inbounds.length(); i++) {
                JSONObject item = inbounds.optJSONObject(i);
                if (item == null) continue;
                tcpCount += item.optLong("tcp_conns"); udpCount += item.optLong("udp_conns");
                tcpUp += item.optLong("tcp_upload_bytes"); tcpDown += item.optLong("tcp_download_bytes");
                udpUp += item.optLong("udp_upload_bytes"); udpDown += item.optLong("udp_download_bytes");
            }
            set(uploaded, bytes(tcpUp + udpUp)); set(downloaded, bytes(tcpDown + udpDown));
            set(tcp, tcpCount + "\nUp " + bytes(tcpUp) + " · Down " + bytes(tcpDown));
            set(udp, udpCount + "\nUp " + bytes(udpUp) + " · Down " + bytes(udpDown));
        }
        set(resident, byteMetric(memory, "resident_bytes"));
        set(allocated, byteMetric(memory, "allocated_bytes"));
        set(aliveTasks, countMetric(response.optJSONObject("tokio_metric"), "num_alive_tasks"));
        boolean incomplete = memory == null || inbounds == null || "—".contentEquals(aliveTasks.getText());
        notice(connectionNotice, incomplete
            ? "Some metrics are unavailable. Retrying automatically."
            : "Live totals for this engine run. Updates every second.", incomplete);
    }

    void renderOutbounds(JSONArray items) { proxies.update(items); }
    void renderSessions(JSONArray items) { sessions.update(items); }

    void unavailable(int page, String message) {
        if (page == MainActivity.CONNECTION) {
            clearMetrics();
            notice(connectionNotice, "Metrics unavailable. " + message + " Retrying automatically.", true);
        } else if (page == MainActivity.PROXIES) proxies.unavailable(message);
        else if (page == MainActivity.SESSIONS) sessions.unavailable(message);
    }

    void outboundAction(String message, boolean failed) { notice(proxies.actionNotice, message, failed); }

    void probePending(String tag, boolean pending) {
        if (pending) proxies.pendingProbes.add(tag);
        else proxies.pendingProbes.remove(tag);
        for (int i = 0; i < proxies.adapter.items.size(); i++) {
            if (tag.equals(proxies.adapter.items.get(i).optString("tag"))) proxies.adapter.notifyItemChanged(i);
        }
    }

    private void clearMetrics() {
        for (TextView view : new TextView[]{uploaded, downloaded, tcp, udp, aliveTasks, resident, allocated}) set(view, "—");
    }

    private final class RuntimeList {
        final boolean isSessions;
        final LinearLayout root = column();
        final EditText filter = new EditText(context);
        final TextView summary = text("", 13, secondary, false);
        final TextView empty = text("", 14, secondary, false);
        final TextView actionNotice = text("", 13, primary, false);
        final Rows adapter = new Rows(this);
        final java.util.Set<String> pendingProbes = new java.util.HashSet<>();
        List<JSONObject> all = new ArrayList<>();
        String protocol = "All";
        boolean available;

        RuntimeList(boolean isSessions) {
            this.isSessions = isSessions;
            root.setBackgroundColor(surface);
            root.setFocusableInTouchMode(true);
            root.setPadding(dp(22), dp(compact ? 12 : 24), dp(22), 0);
            title(root, isSessions ? "Sessions" : "Proxies");
            if (!compact) add(root, text(isSessions ? "Live TCP and UDP flows, route by route."
                : "Choose an outbound. Check its quality.", 14, secondary, false), 6, 16);
            LinearLayout filterArea = compact && isSessions ? row() : column();
            add(root, filterArea, compact ? 8 : 0, 0);
            filter.setId(isSessions ? R.id.session_filter : R.id.proxy_filter);
            filter.setTextColor(ink);
            filter.setHintTextColor(secondary);
            filter.setTextSize(14);
            filter.setSingleLine(true);
            filter.setSelectAllOnFocus(false);
            filter.setHint(isSessions ? "Filter destination or route" : "Filter outbounds");
            filter.setContentDescription(isSessions ? "Filter sessions by destination, address, or route"
                : "Filter outbounds by tag");
            filter.setPadding(dp(14), dp(8), dp(14), dp(8));
            filter.setBackground(fieldBackground());
            filter.setMinimumHeight(dp(48));
            if (compact && isSessions) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMarginEnd(dp(16));
                filterArea.addView(filter, params);
            } else add(filterArea, filter, 0, 8);
            filter.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { publish(); }
                @Override public void afterTextChanged(Editable e) {}
            });
            if (isSessions) {
                RadioGroup choices = new RadioGroup(context);
                choices.setOrientation(LinearLayout.HORIZONTAL);
                choices.setId(R.id.protocol_filter);
                for (String value : new String[]{"All", "TCP", "UDP"}) {
                    RadioButton button = new RadioButton(context);
                    button.setId(View.generateViewId());
                    button.setText(value);
                    button.setTextSize(13);
                    button.setTextColor(ink);
                    button.setButtonTintList(ColorStateList.valueOf(primary));
                    button.setMinHeight(dp(48));
                    choices.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
                    if (value.equals("All")) button.setChecked(true);
                }
                choices.setOnCheckedChangeListener((group, id) -> {
                    protocol = ((RadioButton) group.findViewById(id)).getText().toString();
                    publish();
                });
                if (compact) filterArea.addView(choices, new LinearLayout.LayoutParams(0, -2, 1));
                else filterArea.addView(choices);
            }
            summary.setId(isSessions ? R.id.session_summary : R.id.proxy_summary);
            add(root, summary, 8, 8);
            actionNotice.setVisibility(View.GONE);
            actionNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            add(root, actionNotice, 0, 8);
            empty.setId(isSessions ? R.id.session_empty : R.id.proxy_empty);
            empty.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            add(root, empty, 18, 16);
            RecyclerView list = new RecyclerView(context);
            list.setId(isSessions ? R.id.session_rows : R.id.proxy_rows);
            list.setLayoutManager(new LinearLayoutManager(context));
            list.setAdapter(adapter);
            list.setItemAnimator(null);
            list.setClipToPadding(false);
            list.setPadding(0, 0, 0, dp(20));
            // Let vertical lists keep their gesture; horizontal swipes still belong to ViewPager2.
            list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                private float x, y;
                @Override public boolean onInterceptTouchEvent(RecyclerView view, android.view.MotionEvent event) {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                        x = event.getX(); y = event.getY();
                    } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE
                            && Math.abs(event.getY() - y) > Math.abs(event.getX() - x)) {
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return false;
                }
            });
            root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        void reset(AppState.Phase phase) {
            available = false;
            all = new ArrayList<>();
            pendingProbes.clear();
            actionNotice.setVisibility(View.GONE);
            publish();
            String message = phase == AppState.Phase.CONNECTED ? "Loading " + noun() + "…"
                : phase == AppState.Phase.CONNECTING ? "Waiting for the local engine…"
                : phase == AppState.Phase.STOPPING ? "Closing the local engine…"
                : isSessions ? "Connect the VPN to inspect active TCP and UDP flows."
                : "Connect the VPN to load outbounds, selector state, and quality measurements.";
            notice(empty, message, false);
        }

        String noun() { return isSessions ? "sessions" : "outbounds"; }

        void update(JSONArray items) {
            if (items == null) { unavailable("The runtime returned no " + noun() + " data."); return; }
            available = true;
            all = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) if (items.optJSONObject(i) != null) all.add(items.optJSONObject(i));
            publish();
        }

        void unavailable(String message) {
            available = false;
            all = new ArrayList<>();
            publish();
            notice(empty, "Live " + noun() + " unavailable. " + message + " Retrying automatically.", true);
        }

        void publish() {
            String query = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<JSONObject> visible = new ArrayList<>();
            for (JSONObject item : all) {
                String searchable = isSessions ? value(item, "dst_domain") + " " + value(item, "dst_ip")
                    + " " + value(item, "dst_port") + " " + value(item, "inbound") + " " + value(item, "outbound")
                    : value(item, "tag");
                if (!query.isEmpty() && !searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
                if (isSessions && !protocol.equals("All") && !protocol.equalsIgnoreCase(item.optString("protocol"))) continue;
                visible.add(item);
            }
            adapter.submit(visible);
            set(summary, available ? visible.size() + " of " + all.size() + " " + noun() + " · live"
                : "Live data pending");
            empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
            if (available && visible.isEmpty()) notice(empty, all.isEmpty()
                ? isSessions ? "No active sessions. TCP and UDP flows will appear when traffic starts."
                    : "No outbounds are configured. Add an outbound in Configuration, then reconnect."
                : "No matches. Clear the text filter" + (isSessions ? " or select All protocols." : "."), false);
        }
    }

    private final class Rows extends RecyclerView.Adapter<Row> {
        final RuntimeList owner;
        List<JSONObject> items = new ArrayList<>();
        Rows(RuntimeList owner) { this.owner = owner; }

        void submit(List<JSONObject> next) {
            List<JSONObject> previous = items;
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return previous.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    String key = owner.isSessions ? "id" : "tag";
                    return value(previous.get(oldPosition), key).equals(value(next.get(newPosition), key));
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return previous.get(oldPosition).toString().equals(next.get(newPosition).toString());
                }
            }, false);
            items = next;
            diff.dispatchUpdatesTo(this);
        }

        @Override public Row onCreateViewHolder(ViewGroup parent, int type) { return new Row(owner); }
        @Override public void onBindViewHolder(Row row, int position) { row.bind(items.get(position)); }
        @Override public int getItemCount() { return items.size(); }
    }

    private final class Row extends RecyclerView.ViewHolder {
        final RuntimeList owner;
        final TextView title, route, info, up, down, latency;
        final Button select, test;
        JSONObject item;

        Row(RuntimeList owner) {
            super(column());
            this.owner = owner;
            LinearLayout body = (LinearLayout) itemView;
            body.setPadding(dp(16), dp(16), dp(16), dp(4));
            body.setBackground(fieldBackground());
            RecyclerView.LayoutParams rowParams = new RecyclerView.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(12);
            body.setLayoutParams(rowParams);
            LinearLayout content = compact ? row() : body;
            LinearLayout identity = compact ? column() : body;
            LinearLayout measurements = compact ? column() : body;
            if (compact) {
                identity.setPadding(0, 0, dp(20), 0);
                measurements.setPadding(0, 0, dp(20), 0);
                content.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
                content.addView(measurements, new LinearLayout.LayoutParams(0, -2, 1.3f));
                body.addView(content);
            }
            title = text("", 16, ink, true);
            title.setTextIsSelectable(true);
            identity.addView(title);
            route = text("", 13, primary, true);
            add(identity, route, 5, 8);
            if (!owner.isSessions) {
                route.setPadding(dp(10), dp(7), dp(10), dp(7));
                route.setBackground(shape(container, 8));
            }
            LinearLayout pair = row();
            up = number("", 14, download);
            down = number("", 14, primary);
            up.setPadding(0, dp(6), dp(8), dp(6));
            down.setPadding(dp(8), dp(6), 0, dp(6));
            pair.addView(up, new LinearLayout.LayoutParams(0, -2, 1));
            pair.addView(down, new LinearLayout.LayoutParams(0, -2, 1));
            measurements.addView(pair);
            latency = text("", 13, secondary, false);
            latency.setFontFeatureSettings("tnum");
            add(measurements, latency, 6, 0);
            info = text("", 12, secondary, false);
            add(measurements, info, 6, 12);
            if (owner.isSessions) {
                select = null; test = null;
            } else {
                LinearLayout actions = compact ? column() : row();
                select = action("Choose proxy", () -> choose.choose(value(item, "tag"), item.optJSONObject("selector")), false);
                test = action("Probe latency", () -> probe.accept(value(item, "tag")), false);
                if (compact) {
                    add(actions, select, 0, 8);
                    add(actions, test, 0, 14);
                    content.addView(actions, new LinearLayout.LayoutParams(0, -2, 0.8f));
                } else {
                    LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(0, -2, 1);
                    selectParams.setMarginEnd(dp(8));
                    actions.addView(select, selectParams);
                    actions.addView(test, new LinearLayout.LayoutParams(0, -2, 1));
                    add(body, actions, 0, 14);
                }
            }
        }

        void bind(JSONObject item) {
            this.item = item;
            if (owner.isSessions) {
                String domain = item.isNull("dst_domain") ? "" : item.optString("dst_domain").trim();
                String destination = domain.isEmpty() ? value(item, "dst_ip") : domain;
                if (domain.isEmpty() && destination.contains(":")) destination = "[" + destination + "]";
                set(title, destination + ":" + value(item, "dst_port"));
                set(route, value(item, "protocol").toUpperCase(Locale.ROOT) + " · "
                    + value(item, "inbound") + " → " + value(item, "outbound"));
                set(up, "Upload\n" + byteMetric(item, "upload_bytes"));
                set(down, "Download\n" + byteMetric(item, "download_bytes"));
                set(latency, "First response  " + metric(item, "first_response_ms", " ms"));
                set(info, "Duration  " + (item.isNull("duration_ms") ? "—" : item.optLong("duration_ms") / 1000 + " s")
                    + (item.optBoolean("half_closed") ? " · Half closed" : ""));
            } else {
                String tag = value(item, "tag");
                set(title, tag);
                JSONObject selector = item.optJSONObject("selector");
                set(route, selector == null ? "Outbound" : "Selected · " + value(selector, "selected"));
                select.setVisibility(selector == null ? View.GONE : View.VISIBLE);
                JSONArray members = selector == null ? null : selector.optJSONArray("outbounds");
                select.setEnabled(members != null && members.length() > 0);
                select.setContentDescription("Choose proxy for " + tag);
                boolean pending = owner.pendingProbes.contains(tag);
                set(test, pending ? "Probing…" : "Probe latency");
                test.setEnabled(!pending);
                test.setContentDescription("Probe latency for " + tag);
                JSONObject quality = item.optJSONObject("quality");
                JSONObject sample = quality == null ? null : quality.optJSONObject("sq_conn_stats");
                JSONObject probeResult = quality == null ? null : quality.optJSONObject("probe");
                JSONObject uplink = sample == null ? null : sample.optJSONObject("uplink");
                JSONObject downlink = sample == null ? null : sample.optJSONObject("downlink");
                set(up, "Upload RTT\n" + metric(uplink, "rtt", " ms"));
                set(down, "Download RTT\n" + metric(downlink, "rtt", " ms"));
                set(latency, "Probe latency  " + (probeResult == null ? "Not probed"
                    : "ok".equals(probeResult.optString("status")) ? metric(probeResult, "latency_ms", " ms") : "Unavailable"));
                set(info, "Packet loss · Up " + loss(uplink) + " / Down " + loss(downlink));
            }
        }
    }

    Button action(String label, Runnable action, boolean filled) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        int[][] states = {new int[]{-android.R.attr.state_enabled}, new int[]{}};
        button.setTextColor(new ColorStateList(states, new int[]{secondary, filled ? onPrimary : ink}));
        android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, shape(surface, 8));
        background.addState(new int[]{}, buttonShape(filled));
        button.setBackgroundTintList(null);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(outline), background, null));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    void title(LinearLayout root, String label) {
        TextView view = text(label, compact ? 22 : 26, ink, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
        root.addView(view);
    }

    private TextView metricRow(LinearLayout parent, String label, int id) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 14, secondary, false);
        name.setPadding(0, dp(13), dp(12), dp(13));
        TextView value = number("—", 14, ink);
        value.setId(id);
        value.setGravity(Gravity.END);
        value.setPadding(0, dp(13), 0, dp(13));
        line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        line.addView(value, new LinearLayout.LayoutParams(0, -2, 1.4f));
        parent.addView(line);
        divider(parent);
        return value;
    }

    TextView text(String label, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private TextView number(String label, int size, int color) {
        TextView view = text(label, size, color, true);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout row() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private void add(LinearLayout parent, View child, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top); params.bottomMargin = dp(bottom);
        parent.addView(child, params);
    }

    private void divider(LinearLayout parent) {
        View line = new View(context);
        line.setBackgroundColor(outline);
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    LinearLayout panel() {
        LinearLayout view = column();
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        view.setBackground(fieldBackground());
        return view;
    }

    GradientDrawable fieldBackground() {
        GradientDrawable background = shape(panel, 12);
        background.setStroke(dp(1), outline);
        return background;
    }

    private GradientDrawable buttonShape(boolean filled) {
        GradientDrawable background = shape(filled ? primary : panel, 8);
        if (!filled) background.setStroke(dp(1), outline);
        return background;
    }

    void icon(TextView view, int resource, int tint) {
        android.graphics.drawable.Drawable drawable = context.getDrawable(resource).mutate();
        drawable.setTint(tint);
        drawable.setBounds(0, 0, dp(18), dp(18));
        view.setCompoundDrawablesRelative(drawable, null, null, null);
        view.setCompoundDrawablePadding(dp(8));
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius));
        return shape;
    }

    private void notice(TextView view, String message, boolean failed) {
        set(view, message);
        view.setTextColor(failed ? error : secondary);
        view.setVisibility(View.VISIBLE);
    }

    private static void set(TextView view, String value) {
        if (!TextUtils.equals(view.getText(), value)) view.setText(value);
    }

    private int color(int id) { return context.getColor(id); }
    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private static String value(JSONObject item, String key) {
        return item == null || item.isNull(key) ? "—" : item.optString(key, "—");
    }

    private static String metric(JSONObject item, String key, String suffix) {
        return item == null || item.isNull(key) ? "—" : item.optString(key) + suffix;
    }

    private static String byteMetric(JSONObject item, String key) {
        return item == null || item.isNull(key) ? "—" : bytes(item.optLong(key));
    }

    private static String countMetric(JSONObject item, String key) {
        Object value = item == null ? null : item.opt(key);
        if (!(value instanceof Number)) return "—";
        Number number = (Number) value;
        long count = number.longValue();
        return count >= 0 && number.doubleValue() == count ? Long.toString(count) : "—";
    }

    private static String loss(JSONObject item) {
        if (item == null || item.optLong("sent_packets") == 0) return "—";
        return String.format(Locale.ROOT, "%.2f%%", 100.0 * item.optLong("lost_packets") / item.optLong("sent_packets"));
    }

    private static String bytes(long value) {
        if (value < 1024) return value + " B";
        if (value < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", value / 1024.0);
        if (value < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GiB", value / (1024.0 * 1024 * 1024));
    }
}
