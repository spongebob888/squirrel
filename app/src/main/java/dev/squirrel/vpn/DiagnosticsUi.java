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
import android.widget.ImageButton;
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
    interface ChooseOutbound { void choose(String tag, JSONObject selector, String member); }

    final Context context;
    final int surface, ink, secondary, primary, onPrimary, container, outline, success, error, download, panel, header;
    private final boolean compact;
    final Button connect;
    private final TextView state, detail, profile, uploaded, downloaded, tcp, udp, aliveTasks, resident, allocated;
    private final TextView tcpUploaded, tcpDownloaded, udpUploaded, udpDownloaded;
    private final TextView connectionNotice;
    private final TextView connectionHelp;
    private final ImageButton connectionInfo;
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
        LinearLayout content = compact ? row() : column();
        add(connection, content, 16, 0);
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
        if (compact) controls.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout statusRow = row();
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout statusText = column();
        statusText.setPadding(0, 0, dp(12), 0);
        state = text("Disconnected", 20, secondary, true);
        state.setId(R.id.connection_state);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        statusText.addView(state);
        add(statusText, text("Local engine", 12, secondary, false), 4, 0);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1));
        connect = action("Connect", toggle, true);
        connect.setId(R.id.connect_vpn);
        connect.setMinHeight(dp(96));
        connect.setMinimumHeight(dp(96));
        statusRow.addView(connect, new LinearLayout.LayoutParams(dp(112), -2));
        controls.addView(statusRow);
        detail = text("", 13, secondary, false);
        detail.setVisibility(View.GONE);
        add(controls, detail, 12, 0);
        LinearLayout profileRow = column();
        add(controls, profileRow, 16, 0);
        divider(profileRow);
        profile = text("Loading configuration…", 13, secondary, false);
        profile.setId(R.id.connection_profile);
        icon(profile, R.drawable.ic_configuration, secondary);
        add(profileRow, profile, 12, 0);

        LinearLayout transfers = panel();
        LinearLayout totals = pairedColumns(transfers);
        LinearLayout down = (LinearLayout) totals.getChildAt(0);
        LinearLayout up = (LinearLayout) totals.getChildAt(1);
        TextView downLabel = text("Download", 12, secondary, false);
        TextView upLabel = text("Upload", 12, secondary, false);
        icon(downLabel, R.drawable.ic_download, primary);
        icon(upLabel, R.drawable.ic_upload, download);
        add(down, downLabel, 0, 8);
        add(up, upLabel, 0, 8);
        uploaded = number("—", 24, ink);
        downloaded = number("—", 24, ink);
        uploaded.setId(R.id.upload_total);
        downloaded.setId(R.id.download_total);
        up.addView(uploaded);
        down.addView(downloaded);
        measurements.addView(transfers);
        LinearLayout engine = panel();
        add(measurements, engine, 16, 0);
        LinearLayout engineHeader = row();
        engineHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView engineTitle = text("Runtime", 16, ink, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) engineTitle.setAccessibilityHeading(true);
        engineHeader.addView(engineTitle, new LinearLayout.LayoutParams(0, -2, 1));
        connectionInfo = iconAction("About connection metrics", this::toggleConnectionInfo);
        connectionInfo.setId(R.id.connection_info);
        engineHeader.addView(connectionInfo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        engine.addView(engineHeader);
        connectionHelp = text("VPN active means the local engine is running. Use traffic and proxy probes to check remote availability."
            + "\n\nTotals cover this engine run and refresh every second. Arrows show upload and download; TCP and UDP count sessions. A dash means a measurement is unavailable."
            + "\n\nTasks counts live Tokio tasks. Resident is process memory held in RAM; Allocated is memory reported by the allocator."
            + "\n\nThe app always bypasses the VPN. IPv4 and IPv6 TCP/UDP are supported; ICMP/ping is unsupported.",
            13, secondary, false);
        connectionHelp.setId(R.id.connection_help);
        add(engine, connectionHelp, 0, 12);
        setConnectionInfoExpanded(false);
        LinearLayout protocols = pairedColumns(engine);
        LinearLayout tcpColumn = (LinearLayout) protocols.getChildAt(0);
        LinearLayout udpColumn = (LinearLayout) protocols.getChildAt(1);
        tcp = protocolMetric(tcpColumn, "TCP", R.id.tcp_metric);
        udp = protocolMetric(udpColumn, "UDP", R.id.udp_metric);
        tcpUploaded = transferMetric(tcpColumn, R.drawable.ic_upload, download);
        tcpDownloaded = transferMetric(tcpColumn, R.drawable.ic_download, primary);
        udpUploaded = transferMetric(udpColumn, R.drawable.ic_upload, download);
        udpDownloaded = transferMetric(udpColumn, R.drawable.ic_download, primary);
        View spacer = new View(context);
        engine.addView(spacer, new LinearLayout.LayoutParams(-1, dp(16)));
        divider(engine);
        aliveTasks = metricRow(engine, "Tasks", "Alive tasks", R.id.alive_tasks_metric, R.drawable.ic_engine);
        resident = metricRow(engine, "Resident", "Resident memory", R.id.resident_metric, R.drawable.ic_memory);
        allocated = metricRow(engine, "Allocated", "Allocated memory", R.id.allocated_metric, R.drawable.ic_allocation);
        connectionNotice = text("", 12, secondary, false);
        connectionNotice.setId(R.id.connection_metrics_notice);
        connectionNotice.setVisibility(View.GONE);
        add(engine, connectionNotice, 12, 0);
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
        state.putBoolean("diagnostics.connectionHelp", connectionHelp.getVisibility() == View.VISIBLE);
    }

    void restoreFilters(android.os.Bundle state) {
        setConnectionInfoExpanded(state.getBoolean("diagnostics.connectionHelp", false));
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
        detail.setTextColor(snapshot.phase == AppState.Phase.ERROR ? error : secondary);
        detail.setVisibility(snapshot.phase == AppState.Phase.ERROR
            || snapshot.phase == AppState.Phase.CONNECTING || snapshot.phase == AppState.Phase.STOPPING
            ? View.VISIBLE : View.GONE);
        set(profile, selectedProfile == null ? "Loading configuration…" : selectedProfile);
        profile.setContentDescription(selectedProfile == null ? "Loading configuration" : "Profile: " + selectedProfile);
        profile.setTooltipText(profile.getContentDescription());
        set(connect, preparing ? "Checking…" : snapshot.busy() ? "Disconnect" : "Connect");
        connect.setContentDescription(preparing ? "Checking configuration" : snapshot.busy() ? "Disconnect VPN" : "Connect VPN");
        android.graphics.drawable.Drawable power = context.getDrawable(preparing
            || snapshot.phase == AppState.Phase.CONNECTING || snapshot.phase == AppState.Phase.STOPPING
            ? R.drawable.ic_hourglass : R.drawable.ic_power).mutate();
        power.setTintList(connect.getTextColors());
        power.setBounds(0, 0, dp(28), dp(28));
        connect.setCompoundDrawablesRelative(null, power, null, null);
        connect.setCompoundDrawablePadding(dp(8));
        if (lastSnapshot == snapshot) return;
        lastSnapshot = snapshot;
        clearMetrics();
        String message = snapshot.phase == AppState.Phase.CONNECTED ? "Loading live metrics…"
            : snapshot.phase == AppState.Phase.CONNECTING ? "Waiting for the local engine…"
            : snapshot.phase == AppState.Phase.STOPPING ? "Closing the local engine…"
            : "";
        notice(connectionNotice, message, false);
        connectionNotice.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        proxies.reset(snapshot.phase);
        sessions.reset(snapshot.phase);
    }

    void renderNetworkStats(JSONObject response) {
        JSONObject memory = response.optJSONObject("memory"), traffic = response.optJSONObject("traffic");
        long tcpCount = 0, udpCount = 0, tcpUp = 0, tcpDown = 0, udpUp = 0, udpDown = 0;
        JSONArray inbounds = traffic == null ? null : traffic.optJSONArray("inbounds");
        if (inbounds == null) {
            set(uploaded, "—"); set(downloaded, "—"); set(tcp, "—"); set(udp, "—");
            for (TextView value : new TextView[]{tcpUploaded, tcpDownloaded, udpUploaded, udpDownloaded}) set(value, "—");
        } else {
            for (int i = 0; i < inbounds.length(); i++) {
                JSONObject item = inbounds.optJSONObject(i);
                if (item == null) continue;
                tcpCount += item.optLong("tcp_conns"); udpCount += item.optLong("udp_conns");
                tcpUp += item.optLong("tcp_upload_bytes"); tcpDown += item.optLong("tcp_download_bytes");
                udpUp += item.optLong("udp_upload_bytes"); udpDown += item.optLong("udp_download_bytes");
            }
            set(uploaded, bytes(tcpUp + udpUp)); set(downloaded, bytes(tcpDown + udpDown));
            set(tcp, Long.toString(tcpCount));
            set(udp, Long.toString(udpCount));
            set(tcpUploaded, bytes(tcpUp)); set(tcpDownloaded, bytes(tcpDown));
            set(udpUploaded, bytes(udpUp)); set(udpDownloaded, bytes(udpDown));
        }
        describeMetrics();
        set(resident, byteMetric(memory, "resident_bytes"));
        set(allocated, byteMetric(memory, "allocated_bytes"));
        set(aliveTasks, countMetric(response.optJSONObject("tokio_metric"), "num_alive_tasks"));
        boolean incomplete = memory == null || inbounds == null || "—".contentEquals(aliveTasks.getText());
        notice(connectionNotice, incomplete
            ? "Some metrics are unavailable. Retrying automatically."
            : "Live totals for this engine run. Updates every second.", incomplete);
        connectionNotice.setVisibility(incomplete ? View.VISIBLE : View.GONE);
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
            if (tag.equals(proxies.adapter.items.get(i).item.optString("tag"))) proxies.adapter.notifyItemChanged(i);
        }
    }

    void selectionPending(String tag, boolean pending) {
        if (pending) proxies.pendingSelections.add(tag);
        else proxies.pendingSelections.remove(tag);
        for (int i = 0; i < proxies.adapter.items.size(); i++) {
            DisplayItem entry = proxies.adapter.items.get(i);
            if (tag.equals(entry.item.optString("tag"))
                    || entry.parent != null && tag.equals(entry.parent.optString("tag"))) {
                proxies.adapter.notifyItemChanged(i);
            }
        }
    }

    private void clearMetrics() {
        for (TextView view : new TextView[]{uploaded, downloaded, tcp, udp, aliveTasks, resident, allocated,
                tcpUploaded, tcpDownloaded, udpUploaded, udpDownloaded}) set(view, "—");
        describeMetrics();
    }

    private void describeMetrics() {
        describe(uploaded, "Total uploaded"); describe(downloaded, "Total downloaded");
        describe(tcp, "TCP sessions"); describe(udp, "UDP sessions");
        describe(tcpUploaded, "TCP uploaded"); describe(tcpDownloaded, "TCP downloaded");
        describe(udpUploaded, "UDP uploaded"); describe(udpDownloaded, "UDP downloaded");
    }

    private void describe(TextView value, String label) {
        value.setContentDescription(label + ": " + value.getText());
        value.setTooltipText(value.getContentDescription());
    }

    private void toggleConnectionInfo() {
        setConnectionInfoExpanded(connectionHelp.getVisibility() != View.VISIBLE);
    }

    private void setConnectionInfoExpanded(boolean expanded) {
        connectionHelp.setVisibility(expanded ? View.VISIBLE : View.GONE);
        connectionInfo.setImageResource(expanded ? R.drawable.ic_close : R.drawable.ic_info);
        connectionInfo.setContentDescription(expanded ? "Hide connection details" : "About connection metrics");
        connectionInfo.setTooltipText(connectionInfo.getContentDescription());
        if (android.os.Build.VERSION.SDK_INT >= 30) connectionInfo.setStateDescription(expanded ? "Expanded" : "Collapsed");
    }

    private LinearLayout pairedColumns(LinearLayout parent) {
        LinearLayout pair = row();
        LinearLayout left = column(), right = column();
        left.setPadding(0, 0, dp(12), 0);
        right.setPadding(dp(12), 0, 0, 0);
        pair.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        pair.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        parent.addView(pair);
        return pair;
    }

    private TextView protocolMetric(LinearLayout parent, String label, int id) {
        TextView name = text(label, 12, secondary, false);
        icon(name, R.drawable.ic_sessions, secondary);
        add(parent, name, 4, 6);
        TextView count = number("—", 20, ink);
        count.setId(id);
        add(parent, count, 0, 4);
        return count;
    }

    private TextView transferMetric(LinearLayout parent, int icon, int tint) {
        TextView value = text("—", 12, secondary, false);
        value.setFontFeatureSettings("tnum");
        icon(value, icon, tint);
        add(parent, value, 4, 0);
        return value;
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
        final java.util.Set<String> pendingSelections = new java.util.HashSet<>();
        List<JSONObject> all = new ArrayList<>();
        String protocol = "All";
        boolean available;

        RuntimeList(boolean isSessions) {
            this.isSessions = isSessions;
            root.setBackgroundColor(surface);
            root.setFocusableInTouchMode(true);
            root.setPadding(dp(22), dp(compact ? 12 : 24), dp(22), 0);
            LinearLayout heading = row();
            heading.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout identity = compact ? column() : row();
            identity.setGravity(Gravity.CENTER_VERTICAL);
            title(identity, isSessions ? "Sessions" : "Proxies");
            summary.setId(isSessions ? R.id.session_summary : R.id.proxy_summary);
            icon(summary, isSessions ? R.drawable.ic_sessions : R.drawable.ic_proxies, secondary);
            if (compact) {
                add(identity, summary, 2, 0);
                identity.setPadding(0, 0, dp(16), 0);
                heading.addView(identity, new LinearLayout.LayoutParams(-2, -2));
            } else {
                TextView pageTitle = (TextView) identity.getChildAt(0);
                pageTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
                identity.addView(summary);
                heading.addView(identity, new LinearLayout.LayoutParams(-1, -2));
            }
            root.addView(heading, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout filterArea = compact && isSessions ? row() : column();
            if (compact) heading.addView(filterArea, new LinearLayout.LayoutParams(0, -2, 1));
            else add(root, filterArea, 16, 0);
            filter.setId(isSessions ? R.id.session_filter : R.id.proxy_filter);
            filter.setTextColor(ink);
            filter.setHintTextColor(secondary);
            filter.setTextSize(14);
            filter.setSingleLine(true);
            filter.setSelectAllOnFocus(false);
            filter.setHint(isSessions ? "Filter sessions" : "Filter outbounds");
            filter.setContentDescription(isSessions ? "Filter sessions by destination, address, or route"
                : "Filter outbounds by tag");
            filter.setPadding(dp(14), dp(8), dp(14), dp(8));
            filter.setBackground(fieldBackground());
            icon(filter, R.drawable.ic_search, secondary);
            filter.setMinimumHeight(dp(48));
            if (compact && isSessions) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMarginEnd(dp(16));
                filterArea.addView(filter, params);
            } else add(filterArea, filter, 0, compact ? 0 : 8);
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
            heading.setPadding(0, 0, 0, dp(8));
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
            pendingSelections.clear();
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
            List<DisplayItem> visible = new ArrayList<>();
            java.util.Set<String> matching = new java.util.HashSet<>();
            if (!isSessions) {
                java.util.Map<String, JSONObject> byTag = new java.util.HashMap<>();
                for (JSONObject item : all) byTag.put(item.optString("tag"), item);
                // Keep every selector and its members ahead of the ordinary outbound list.
                for (JSONObject item : all) {
                    JSONObject selector = item.optJSONObject("selector");
                    if (selector == null) continue;
                    List<DisplayItem> members = new ArrayList<>();
                    boolean groupMatches = matches(value(item, "tag"), query);
                    JSONArray tags = selector.optJSONArray("outbounds");
                    java.util.Set<String> seen = new java.util.HashSet<>();
                    for (int i = 0; tags != null && i < tags.length(); i++) {
                        String tag = tags.optString(i, "");
                        if (tag.isEmpty() || !seen.add(tag)) continue;
                        if (!groupMatches && !matches(tag, query)) continue;
                        JSONObject member = byTag.get(tag);
                        if (member == null) {
                            member = new JSONObject();
                            try { member.put("tag", tag); } catch (org.json.JSONException ignored) { }
                        }
                        members.add(new DisplayItem(member, item));
                        if (byTag.containsKey(tag)) matching.add(tag);
                    }
                    if (groupMatches || !members.isEmpty()) {
                        visible.add(new DisplayItem(item, null));
                        visible.addAll(members);
                        matching.add(value(item, "tag"));
                    }
                }
            }
            boolean outboundHeading = !isSessions && !visible.isEmpty();
            for (JSONObject item : all) {
                String searchable = isSessions ? value(item, "dst_domain") + " " + value(item, "dst_ip")
                    + " " + value(item, "dst_port") + " " + value(item, "inbound") + " " + value(item, "outbound")
                    : value(item, "tag");
                if (!query.isEmpty() && !searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
                if (isSessions && !protocol.equals("All") && !protocol.equalsIgnoreCase(item.optString("protocol"))) continue;
                if (!isSessions && item.optJSONObject("selector") != null) continue;
                if (outboundHeading) {
                    visible.add(new DisplayItem("All outbounds"));
                    outboundHeading = false;
                }
                visible.add(new DisplayItem(item, null));
                matching.add(value(item, "tag"));
            }
            adapter.submit(visible);
            int count = isSessions ? visible.size() : matching.size();
            boolean filtered = !query.isEmpty() || isSessions && !protocol.equals("All");
            set(summary, available ? filtered ? count + " / " + all.size() : Integer.toString(all.size()) : "—");
            summary.setContentDescription(available ? count + " of " + all.size() + " " + noun() + ", live"
                : "Live " + noun() + " pending");
            summary.setTooltipText(summary.getContentDescription());
            empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
            if (available && visible.isEmpty()) notice(empty, all.isEmpty()
                ? isSessions ? "No active sessions. TCP and UDP flows will appear when traffic starts."
                    : "No outbounds are configured. Add an outbound in Configuration, then reconnect."
                : "No matches. Clear the text filter" + (isSessions ? " or select All protocols." : "."), false);
        }
    }

    private static boolean matches(String tag, String query) {
        return tag.toLowerCase(Locale.ROOT).contains(query);
    }

    private static final class DisplayItem {
        final JSONObject item, parent;
        final String section;
        DisplayItem(JSONObject item, JSONObject parent) { this.item = item; this.parent = parent; section = null; }
        DisplayItem(String section) { item = new JSONObject(); parent = null; this.section = section; }
        String key(String field) {
            if (section != null) return "section:" + section;
            // Length-prefixed names distinguish shared members and arbitrary punctuation in tags.
            String group = parent == null ? "" : value(parent, "tag");
            return (parent == null ? "outbound:" : "member:") + group.length() + ":" + group + value(item, field);
        }
        String content() { return section != null ? section : item.toString() + (parent == null ? "" : parent.toString()); }
    }

    private final class Rows extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final RuntimeList owner;
        List<DisplayItem> items = new ArrayList<>();
        Rows(RuntimeList owner) { this.owner = owner; }

        void submit(List<DisplayItem> next) {
            List<DisplayItem> previous = items;
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return previous.size(); }
                @Override public int getNewListSize() { return next.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                    String key = owner.isSessions ? "id" : "tag";
                    return previous.get(oldPosition).key(key).equals(next.get(newPosition).key(key));
                }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return previous.get(oldPosition).content().equals(next.get(newPosition).content());
                }
            }, false);
            items = next;
            diff.dispatchUpdatesTo(this);
        }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 1) {
                TextView heading = text("", 16, ink, true);
                heading.setPadding(dp(4), dp(16), dp(4), dp(8));
                heading.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
                return new RecyclerView.ViewHolder(heading) {};
            }
            return owner.isSessions ? new SessionRow() : new ProxyRow(owner);
        }
        @Override public int getItemViewType(int position) { return items.get(position).section == null ? 0 : 1; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder row, int position) {
            if (items.get(position).section != null) set((TextView) row.itemView, items.get(position).section);
            else if (row instanceof ProxyRow) ((ProxyRow) row).bind(items.get(position));
            else ((SessionRow) row).bind(items.get(position).item);
        }
        @Override public int getItemCount() { return items.size(); }
    }

    private final class SessionRow extends RecyclerView.ViewHolder {
        final TextView title, route, info, up, down, latency, halfClosed;

        SessionRow() {
            super(column());
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
            icon(route, R.drawable.ic_route, primary);
            add(identity, route, 5, 8);
            LinearLayout pair = row();
            up = number("", 14, download);
            down = number("", 14, primary);
            icon(up, R.drawable.ic_upload, download);
            icon(down, R.drawable.ic_download, primary);
            up.setPadding(0, dp(6), dp(8), dp(6));
            down.setPadding(dp(8), dp(6), 0, dp(6));
            pair.addView(up, new LinearLayout.LayoutParams(0, -2, 1));
            pair.addView(down, new LinearLayout.LayoutParams(0, -2, 1));
            measurements.addView(pair);
            latency = text("", 13, secondary, false);
            latency.setFontFeatureSettings("tnum");
            icon(latency, R.drawable.ic_probe, secondary);
            info = text("", 12, secondary, false);
            info.setFontFeatureSettings("tnum");
            icon(info, R.drawable.ic_clock, secondary);
            LinearLayout timing = row();
            latency.setPadding(0, dp(6), dp(8), dp(6));
            info.setPadding(dp(8), dp(6), 0, dp(6));
            timing.addView(latency, new LinearLayout.LayoutParams(0, -2, 1));
            timing.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            add(measurements, timing, 0, 8);
            halfClosed = text("Half closed", 12, secondary, false);
            icon(halfClosed, R.drawable.ic_half_closed, secondary);
            add(measurements, halfClosed, 0, 12);
        }

        void bind(JSONObject item) {
            String domain = item.isNull("dst_domain") ? "" : item.optString("dst_domain").trim();
            String destination = domain.isEmpty() ? value(item, "dst_ip") : domain;
            if (domain.isEmpty() && destination.contains(":")) destination = "[" + destination + "]";
            set(title, destination + ":" + value(item, "dst_port"));
            set(route, value(item, "protocol").toUpperCase(Locale.ROOT) + " · "
                + value(item, "inbound") + " → " + value(item, "outbound"));
            route.setContentDescription("Protocol " + value(item, "protocol").toUpperCase(Locale.ROOT)
                + ", inbound " + value(item, "inbound") + ", outbound " + value(item, "outbound"));
            route.setTooltipText(route.getContentDescription());
            set(up, byteMetric(item, "upload_bytes"));
            set(down, byteMetric(item, "download_bytes"));
            set(latency, metric(item, "first_response_ms", " ms"));
            set(info, item.isNull("duration_ms") ? "—" : item.optLong("duration_ms") / 1000 + " s");
            describe(up, "Uploaded"); describe(down, "Downloaded");
            describe(latency, "First response"); describe(info, "Duration");
            halfClosed.setVisibility(item.optBoolean("half_closed") ? View.VISIBLE : View.GONE);
        }
    }

    private final class ProxyRow extends RecyclerView.ViewHolder {
        final RuntimeList owner;
        final TextView name, subtitle, members, up, down;
        final LinearLayout quality;
        final ImageButton select, test;
        DisplayItem entry;

        ProxyRow(RuntimeList owner) {
            super(column());
            this.owner = owner;
            LinearLayout body = (LinearLayout) itemView;
            body.setPadding(dp(12), dp(8), dp(12), dp(8));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            body.setLayoutParams(params);
            LinearLayout heading = row();
            heading.setGravity(Gravity.CENTER_VERTICAL);
            select = iconAction("", () -> {
                JSONObject parent = entry.parent == null ? entry.item : entry.parent;
                choose.choose(value(parent, "tag"), parent.optJSONObject("selector"),
                    entry.parent == null ? null : value(entry.item, "tag"));
            });
            heading.addView(select, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout identity = column();
            identity.setPadding(dp(8), 0, dp(8), 0);
            name = text("", 15, ink, true);
            identity.addView(name);
            subtitle = text("", 12, secondary, false);
            subtitle.setFontFeatureSettings("tnum");
            LinearLayout subtitleRow = row();
            subtitleRow.setGravity(Gravity.CENTER_VERTICAL);
            subtitleRow.addView(subtitle, new LinearLayout.LayoutParams(0, -2, 1));
            members = text("", 12, secondary, false);
            members.setPadding(dp(8), 0, 0, 0);
            icon(members, R.drawable.ic_proxies, secondary);
            subtitleRow.addView(members);
            add(identity, subtitleRow, 2, 0);
            heading.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
            test = iconAction("", () -> probe.accept(value(entry.item, "tag")));
            heading.addView(test, new LinearLayout.LayoutParams(dp(48), dp(48)));
            body.addView(heading);
            quality = row();
            up = text("", 13, download, false);
            down = text("", 13, primary, false);
            for (TextView measurement : new TextView[]{up, down}) {
                measurement.setFontFeatureSettings("tnum");
                measurement.setPadding(0, dp(4), dp(8), 0);
                quality.addView(measurement, new LinearLayout.LayoutParams(0, -2, 1));
            }
            icon(up, R.drawable.ic_upload, download);
            icon(down, R.drawable.ic_download, primary);
            body.addView(quality);
        }

        void bind(DisplayItem entry) {
            this.entry = entry;
            String tag = value(entry.item, "tag");
            JSONObject selector = entry.item.optJSONObject("selector");
            boolean heading = entry.parent == null && selector != null;
            JSONObject parentSelector = entry.parent == null ? null : entry.parent.optJSONObject("selector");
            boolean selected = parentSelector != null && tag.equals(parentSelector.optString("selected"));
            boolean selecting = owner.pendingSelections.contains(entry.parent == null ? tag : value(entry.parent, "tag"));
            set(name, tag);
            name.setContentDescription(heading ? tag + ", selector" : tag);
            if (android.os.Build.VERSION.SDK_INT >= 28) name.setAccessibilityHeading(heading);
            GradientDrawable background = fieldBackground();
            if (selected) background.setStroke(dp(1), primary);
            itemView.setBackground(heading ? null : background);
            select.setVisibility(heading || entry.parent != null ? View.VISIBLE : View.GONE);
            JSONArray options = selector == null ? null : selector.optJSONArray("outbounds");
            select.setEnabled(!selecting && !selected && (!heading || options != null && options.length() > 0));
            select.setSelected(selected);
            select.setImageResource(selecting ? R.drawable.ic_hourglass : heading ? R.drawable.ic_proxies
                : selected ? R.drawable.ic_check_circle : R.drawable.ic_circle);
            select.setContentDescription(heading ? "Choose proxy for " + tag
                : (selected ? "Selected " : "Select ") + tag + " for " + value(entry.parent, "tag"));
            select.setTooltipText(select.getContentDescription());
            boolean pending = owner.pendingProbes.contains(tag);
            test.setEnabled(!pending);
            test.setImageResource(pending ? R.drawable.ic_hourglass : R.drawable.ic_refresh);
            test.setContentDescription((pending ? "Probing " : "Probe latency for ") + tag);
            test.setTooltipText(test.getContentDescription());
            JSONObject measurements = entry.item.optJSONObject("quality");
            JSONObject sample = measurements == null ? null : measurements.optJSONObject("sq_conn_stats");
            JSONObject result = measurements == null ? null : measurements.optJSONObject("probe");
            members.setVisibility(heading ? View.VISIBLE : View.GONE);
            if (heading) {
                JSONArray members = selector.optJSONArray("outbounds");
                set(subtitle, value(selector, "selected"));
                subtitle.setContentDescription("Selected " + value(selector, "selected") + ", "
                    + (members == null ? 0 : members.length()) + " outbounds");
                set(this.members, Integer.toString(members == null ? 0 : members.length()));
                this.members.setContentDescription((members == null ? 0 : members.length()) + " outbounds");
                this.members.setTooltipText(this.members.getContentDescription());
                icon(subtitle, R.drawable.ic_check_circle, primary);
            } else {
                String latency = result == null ? "Untested" : "ok".equals(result.optString("status"))
                    ? milliseconds(result, "latency_ms") : "timeout".equals(result.optString("status")) ? "Timed out" : "Failed";
                set(subtitle, latency);
                subtitle.setContentDescription("Probe latency: " + latency);
                icon(subtitle, R.drawable.ic_probe, secondary);
            }
            subtitle.setTooltipText(subtitle.getContentDescription());
            quality.setVisibility(!heading && sample != null ? View.VISIBLE : View.GONE);
            JSONObject uplink = sample == null ? null : sample.optJSONObject("uplink");
            JSONObject downlink = sample == null ? null : sample.optJSONObject("downlink");
            set(up, milliseconds(uplink, "rtt") + " · " + loss(uplink));
            set(down, milliseconds(downlink, "rtt") + " · " + loss(downlink));
            up.setContentDescription("Upload RTT " + milliseconds(uplink, "rtt") + ", packet loss " + loss(uplink));
            down.setContentDescription("Download RTT " + milliseconds(downlink, "rtt") + ", packet loss " + loss(downlink));
            up.setTooltipText(up.getContentDescription());
            down.setTooltipText(down.getContentDescription());
        }
    }

    private ImageButton iconAction(String label, Runnable action) {
        ImageButton button = new ImageButton(context);
        button.setContentDescription(label);
        button.setMinimumHeight(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setImageTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_selected},
            new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{primary, secondary, primary}));
        button.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(outline), null, shape(container, 8)));
        button.setOnClickListener(view -> action.run());
        return button;
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

    private TextView metricRow(LinearLayout parent, String label, String description, int id, int icon) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 13, secondary, false);
        name.setContentDescription(description);
        name.setTooltipText(description);
        icon(name, icon, secondary);
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

    private static String milliseconds(JSONObject item, String key) {
        Object raw = item == null ? null : item.opt(key);
        if (!(raw instanceof Number)) return "—";
        double value = ((Number) raw).doubleValue();
        return Double.isFinite(value) && value >= 0 ? Math.round(value) + " ms" : "—";
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
