package dev.squirrel.vpn;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int IMPORT = 10, EXPORT_LOG = 11, VPN_PERMISSION = 12, EXPORT_CONFIG = 13;
    static final int CONNECTION = 0, PROXIES = 1, CONFIGURATION = 2, LOGS = 3, SESSIONS = 4;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService apiExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService apiActionExecutor = Executors.newSingleThreadExecutor();
    private ConfigDownloader downloader;
    private final AtomicBoolean logRead = new AtomicBoolean(), apiRead = new AtomicBoolean();
    private TextView configNotice, brand;
    private DiagnosticsUi diagnostics;
    private WorkspaceUi workspace;
    private LinearLayout root, appHeader;
    private boolean expandedNavigation, bottomNavigation;
    private EditText editor;
    private Button connectionTab, configTab, proxyTab, sessionsTab, logsTab, downloadButton, profileButton;
    private LinearLayout configActions, configTools, profileActions;
    private HorizontalScrollView tabScroll;
    private ViewPager2 pager;
    private boolean showingLogs, preparing, loadingConfig = true, downloading;
    private String exportedConfig, remoteUrl = "";
    private ConfigProfiles.State profileState;
    private String profileId;
    private final HashMap<String, String> profileDrafts = new HashMap<>(), profileUrls = new HashMap<>();

    private static final class Draft {
        final HashMap<String, String> texts, urls;
        final int page;
        Draft(HashMap<String, String> texts, HashMap<String, String> urls, int page) {
            this.texts = new HashMap<>(texts); this.urls = new HashMap<>(urls); this.page = page;
        }
    }
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            renderStatus();
            refreshApi();
            if (showingLogs && workspace.needsLogRead() && logRead.compareAndSet(false, true)) {
                io.execute(() -> {
                    try {
                        String text = AppLog.read(MainActivity.this, false);
                        post(() -> workspace.renderLogs(text));
                    } catch (Exception e) { post(() -> workspace.logReadError(SquirrelVpnService.message(e))); }
                    finally { logRead.set(false); }
                });
            }
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Configs may contain credentials; keep them out of recents and screenshots.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        diagnostics = new DiagnosticsUi(this, this::toggleVpn, this::chooseOutbound, this::probeOutbound);
        workspace = new WorkspaceUi(diagnostics, this::chooseProfile, this::manageProfiles,
            this::importFile, this::downloadConfig, () -> save(false), this::exportConfig,
            () -> loadExample("example.yaml"), () -> loadExample("direct.yaml"),
            () -> createDocument(EXPORT_LOG, "squirrel-logs.txt", "text/plain"));
        expandedNavigation = getResources().getConfiguration().screenWidthDp >= 840
            && getResources().getConfiguration().screenHeightDp >= 480;
        bottomNavigation = getResources().getConfiguration().screenWidthDp < 600;
        root = column();
        appHeader = row();
        appHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        appHeader.setPadding(dp(22), dp(6), dp(22), dp(6));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), 0, insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
            appHeader.setPadding(dp(22), dp(6) + insets.getSystemWindowInsetTop(), dp(22), dp(6));
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        brand = text("Squirrel", 20, diagnostics.ink);
        android.graphics.drawable.Drawable logo = getDrawable(R.drawable.ic_squirrelboard);
        brand.setCompoundDrawablesRelativeWithIntrinsicBounds(logo, null, null, null);
        brand.setCompoundDrawablePadding(dp(10));
        brand.setTypeface(null, Typeface.BOLD);
        appHeader.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView core = text("SHADOWQUIC", 9, diagnostics.secondary);
        core.setLetterSpacing(0.08f);
        appHeader.addView(core);
        android.widget.ImageButton tools = new android.widget.ImageButton(diagnostics.context);
        tools.setId(R.id.api_tools_menu);
        tools.setImageResource(R.drawable.ic_more_vert);
        tools.setImageTintList(android.content.res.ColorStateList.valueOf(diagnostics.secondary));
        tools.setContentDescription("More options");
        tools.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(diagnostics.outline), null, null));
        tools.setOnClickListener(view -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(diagnostics.context, tools);
            menu.getMenu().add("API tools").setOnMenuItemClickListener(item -> {
                startActivity(new Intent(this, ApiToolsActivity.class));
                return true;
            });
            menu.show();
        });
        appHeader.addView(tools, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(appHeader);
        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(true);
        LinearLayout tabs = expandedNavigation ? column() : row();
        tabs.setPadding(dp(bottomNavigation ? 4 : 12), dp(6), dp(bottomNavigation ? 4 : 12), dp(6));
        connectionTab = button("Connection", () -> showPage(CONNECTION));
        configTab = button(bottomNavigation ? "Config" : "Configuration", () -> showPage(CONFIGURATION));
        proxyTab = button("Proxies", () -> showPage(PROXIES));
        sessionsTab = button("Sessions", () -> showPage(SESSIONS));
        logsTab = button("Logs", () -> showPage(LOGS));
        connectionTab.setId(R.id.connection_tab);
        configTab.setId(R.id.configuration_tab);
        proxyTab.setId(R.id.proxies_tab);
        sessionsTab.setId(R.id.sessions_tab);
        logsTab.setId(R.id.logs_tab);
        for (Button tab : new Button[]{connectionTab, proxyTab, configTab, logsTab, sessionsTab}) {
            tab.setStateListAnimator(null);
            tab.setElevation(0);
            tab.setTextSize(bottomNavigation ? 11 : 13);
            tab.setPadding(dp(bottomNavigation ? 2 : 12), dp(8), dp(bottomNavigation ? 2 : 12), dp(8));
            tab.setMinHeight(dp(48));
            tab.setMinimumHeight(dp(48));
            tab.setGravity(expandedNavigation ? android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL
                : android.view.Gravity.CENTER);
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(bottomNavigation ? 0 : expandedNavigation ? -1 : -2, -2, bottomNavigation ? 1 : 0);
            if (expandedNavigation) tabParams.bottomMargin = dp(8);
            else if (!bottomNavigation) tabParams.setMarginEnd(dp(4));
            tabs.addView(tab, tabParams);
        }
        if (bottomNavigation) {
            tabs.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -2));
            configTab.setContentDescription("Configuration");
        }
        tabScroll.addView(tabs);
        LinearLayout navigationBody = row();
        if (expandedNavigation) {
            tabScroll.setFillViewport(false);
            navigationBody.setBackgroundColor(getColor(R.color.diagnostic_sidebar));
            navigationBody.addView(tabScroll, new LinearLayout.LayoutParams(dp(184), -1));
            root.addView(navigationBody, new LinearLayout.LayoutParams(-1, 0, 1));
        } else if (!bottomNavigation) root.addView(tabScroll);

        pager = new ViewPager2(this);
        pager.setId(R.id.pages);
        pager.setOffscreenPageLimit(4);
        View[] pages = {diagnostics.connectionPage(), diagnostics.proxiesPage(), configurationPage(),
            logsPage(), diagnostics.sessionsPage()};
        pager.setAdapter(new PageAdapter(pages));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int page) { selectTab(page); }
        });
        if (expandedNavigation) navigationBody.addView(pager, new LinearLayout.LayoutParams(0, -1, 1));
        else root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        if (bottomNavigation) root.addView(tabScroll);
        int page = saved == null ? CONNECTION : saved.getInt("selectedPage", CONNECTION);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof Draft) {
            Draft draft = (Draft) retained;
            profileDrafts.putAll(draft.texts);
            profileUrls.putAll(draft.urls);
            page = draft.page;
        }
        loadProfile(() -> ConfigProfiles.list(this), null);
        if (page < 0 || page >= pages.length) page = CONNECTION;
        pager.setCurrentItem(page, false);
        selectTab(page);
        if (saved != null) { diagnostics.restoreFilters(saved); workspace.restoreState(saved); }
        renderStatus();
    }

    private View configurationPage() {
        editor = workspace.editor;
        configNotice = workspace.configNotice;
        profileButton = workspace.profileButton;
        downloadButton = workspace.downloadButton;
        profileActions = workspace.profileActions;
        configActions = workspace.configActions;
        configTools = workspace.configTools;
        return workspace.configurationPage();
    }

    private View logsPage() { return workspace.logsPage(); }

    private static final class PageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View[] pages;
        PageAdapter(View[] pages) { this.pages = pages; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout holder = new FrameLayout(parent.getContext());
            holder.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
            return new RecyclerView.ViewHolder(holder) {};
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            FrameLayout container = (FrameLayout) holder.itemView;
            container.removeAllViews();
            View page = pages[position];
            if (page.getParent() != null) ((ViewGroup) page.getParent()).removeView(page);
            container.addView(page, new FrameLayout.LayoutParams(-1, -1));
        }
        @Override public int getItemCount() { return pages.length; }
    }

    private void showPage(int page) { pager.setCurrentItem(page, true); }
    private void selectTab(int page) {
        showingLogs = page == LOGS;
        root.setBackgroundColor(diagnostics.surface);
        appHeader.setBackgroundColor(diagnostics.header);
        brand.setTextColor(diagnostics.ink);
        tabScroll.setBackgroundColor(getColor(R.color.diagnostic_sidebar));
        getWindow().setStatusBarColor(diagnostics.header);
        getWindow().setNavigationBarColor(diagnostics.surface);
        int visibility = getWindow().getDecorView().getSystemUiVisibility();
        boolean lightNavigation = (getResources().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        getWindow().getDecorView().setSystemUiVisibility(lightNavigation
            ? visibility | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            : visibility & ~(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));
        Button[] tabs = {connectionTab, proxyTab, configTab, logsTab, sessionsTab};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == page;
            tabs[i].setSelected(selected);
            tabs[i].setTextColor(selected ? diagnostics.primary : diagnostics.secondary);
            android.graphics.drawable.GradientDrawable background = diagnostics.shape(
                selected ? diagnostics.container : getColor(R.color.diagnostic_sidebar), 8);
            tabs[i].setBackgroundTintList(null);
            tabs[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            tabs[i].setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(diagnostics.outline), background, null));
            int[] icons = {R.drawable.ic_overview, R.drawable.ic_proxies, R.drawable.ic_configuration,
                R.drawable.ic_logs, R.drawable.ic_sessions};
            diagnostics.icon(tabs[i], icons[i], selected ? diagnostics.primary : diagnostics.secondary);
            if (bottomNavigation) {
                android.graphics.drawable.Drawable icon = tabs[i].getCompoundDrawablesRelative()[0];
                tabs[i].setCompoundDrawablesRelative(null, icon, null, null);
                tabs[i].setCompoundDrawablePadding(dp(4));
            }
        }
        if (!expandedNavigation && !bottomNavigation) tabs[page].post(() -> tabScroll.smoothScrollTo(tabs[page].getLeft() - dp(12), 0));
        if (page != LOGS) workspace.clearLogFilterFocus();
        if (page != CONFIGURATION) {
            editor.clearFocus();
            diagnostics.clearFilterFocus();
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
    }

    private void renderStatus() {
        AppState.Snapshot state = AppState.get();
        diagnostics.renderState(state, preparing, profileState == null ? null : profileState.selected().name);
        boolean ready = !loadingConfig && !downloading && !preparing && profileId != null;
        diagnostics.connect.setEnabled(state.phase != AppState.Phase.STOPPING && (state.busy() || ready));
        editor.setEnabled(ready);
        enableActions(configActions, ready);
        enableActions(configTools, ready);
        enableActions(profileActions, ready && profileState != null);
        downloadButton.setText(downloading ? "Downloading…" : "Download URL");
    }

    private interface ProfileOperation { ConfigProfiles.State run() throws Exception; }

    private void rememberDraft() {
        if (profileId != null) {
            profileDrafts.put(profileId, editor.getText().toString());
            profileUrls.put(profileId, remoteUrl);
        }
    }

    private void loadProfile(ProfileOperation operation, String deletedId) {
        rememberDraft();
        loadingConfig = true;
        renderStatus();
        io.execute(() -> {
            try {
                ConfigProfiles.State state = operation.run();
                String config = ConfigProfiles.read(this, state.selectedId);
                post(() -> {
                    if (deletedId != null) { profileDrafts.remove(deletedId); profileUrls.remove(deletedId); }
                    profileState = state;
                    profileId = state.selectedId;
                    editor.setText(profileDrafts.containsKey(profileId) ? profileDrafts.get(profileId) : config);
                    remoteUrl = profileUrls.containsKey(profileId) ? profileUrls.get(profileId) : "";
                    profileButton.setText(state.selected().name);
                    profileButton.setContentDescription("Select configuration. Current: " + state.selected().name);
                    setConfigNotice("Save or Connect to apply. Switching profiles keeps drafts until the app closes.", false);
                    loadingConfig = false;
                    renderStatus();
                });
            } catch (Exception error) {
                post(() -> { loadingConfig = false; renderStatus(); showError(error); });
            }
        });
    }

    private void chooseProfile() {
        if (profileState == null || loadingConfig || preparing || downloading) return;
        ConfigProfiles.State state = profileState;
        String[] names = new String[state.profiles.size()];
        int selected = 0;
        for (int i = 0; i < names.length; i++) {
            ConfigProfiles.Profile profile = state.profiles.get(i);
            names[i] = profile.name;
            if (profile.id.equals(profileId)) selected = i;
        }
        new AlertDialog.Builder(diagnostics.context).setTitle("Configurations").setSingleChoiceItems(names, selected, (dialog, which) -> {
            dialog.dismiss();
            String id = state.profiles.get(which).id;
            if (!id.equals(profileId)) loadProfile(() -> ConfigProfiles.select(this, id), null);
        }).setNegativeButton("Cancel", null).show();
    }

    private void manageProfiles() {
        if (profileState == null || loadingConfig || preparing || downloading) return;
        String[] actions = profileState.profiles.size() > 1
            ? new String[]{"New configuration", "Duplicate current", "Rename", "Delete"}
            : new String[]{"New configuration", "Duplicate current", "Rename"};
        new AlertDialog.Builder(diagnostics.context).setTitle("Manage configurations").setItems(actions, (dialog, which) -> {
            if (which < 3) nameProfile(which);
            else {
                String id = profileId;
                new AlertDialog.Builder(diagnostics.context).setTitle("Delete " + profileState.selected().name + "?")
                    .setMessage("This removes the saved configuration and its draft. An active VPN keeps running until you disconnect.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (confirm, button) ->
                        loadProfile(() -> ConfigProfiles.delete(this, id), id)).show();
            }
        }).show();
    }

    private void nameProfile(int action) {
        EditText name = new EditText(diagnostics.context);
        name.setId(R.id.profile_name);
        name.setSingleLine(true);
        name.setHint("Configuration name");
        name.setText(action == 2 ? profileState.selected().name : action == 1
            ? profileState.selected().name + " copy" : "Configuration " + (profileState.profiles.size() + 1));
        name.selectAll();
        String title = action == 2 ? "Rename configuration" : action == 1 ? "Duplicate configuration" : "New configuration";
        AlertDialog dialog = new AlertDialog.Builder(diagnostics.context).setTitle(title).setView(name)
            .setMessage(action == 0 ? "Start with the proxy template, then edit, import, or download your configuration." : null)
            .setNegativeButton("Cancel", null).setPositiveButton(action == 2 ? "Rename" : "Create", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String label = name.getText().toString().trim();
            if (label.isEmpty() || label.length() > 64) { name.setError("Use a name of 1–64 characters"); return; }
            for (ConfigProfiles.Profile profile : profileState.profiles) {
                if ((action != 2 || !profile.id.equals(profileId)) && profile.name.equalsIgnoreCase(label)) {
                    name.setError("That configuration name already exists"); return;
                }
            }
            String id = profileId;
            String copy = editor.getText().toString();
            dialog.dismiss();
            loadProfile(() -> action == 2 ? ConfigProfiles.rename(this, id, label)
                : ConfigProfiles.create(this, label, action == 1 ? copy : ConfigStore.asset(this, "example.yaml")), null);
        }));
        dialog.show();
    }

    private void refreshApi() {
        if (pager == null) return;
        AppState.Snapshot connection = AppState.get();
        if (connection.phase != AppState.Phase.CONNECTED) return;
        int page = pager.getCurrentItem();
        if (page != CONNECTION && page != PROXIES && page != SESSIONS) return;
        if (!apiRead.compareAndSet(false, true)) return;
        apiExecutor.execute(() -> {
            try {
                ShadowQuicApi api = new ShadowQuicApi(this);
                JSONObject value = page == CONNECTION
                    ? new JSONObject().put("memory", api.get("/memory"))
                        .put("traffic", api.get("/traffic"))
                    : api.get(page == PROXIES ? "/outbounds" : "/connections");
                post(() -> {
                    if (!acceptApiResult(connection, page)) return;
                    if (page == CONNECTION) diagnostics.renderNetworkStats(value);
                    else if (page == PROXIES) diagnostics.renderOutbounds(value.optJSONArray("outbounds"));
                    else diagnostics.renderSessions(value.optJSONArray("connections"));
                });
            } catch (Exception error) {
                post(() -> {
                    if (acceptApiResult(connection, page)) diagnostics.unavailable(page, SquirrelVpnService.message(error));
                });
            } finally { apiRead.set(false); }
        });
    }

    private boolean acceptApiResult(AppState.Snapshot connection, int page) {
        // Snapshot identity also rejects a previous run that stopped and reconnected in the meantime.
        return AppState.get() == connection && connection.phase == AppState.Phase.CONNECTED
            && pager.getCurrentItem() == page;
    }

    private void chooseOutbound(String selectorTag, JSONObject selector) {
        AppState.Snapshot connection = AppState.get();
        if (connection.phase != AppState.Phase.CONNECTED || selector == null) return;
        JSONArray options = selector.optJSONArray("outbounds");
        if (options == null || options.length() == 0) return;
        String[] labels = new String[options.length()];
        int selected = -1;
        for (int i = 0; i < options.length(); i++) {
            labels[i] = options.optString(i);
            if (labels[i].equals(selector.optString("selected"))) selected = i;
        }
        new AlertDialog.Builder(diagnostics.context).setTitle("Select outbound · " + selectorTag)
            .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                dialog.dismiss();
                if (AppState.get() != connection) return;
                diagnostics.outboundAction("Selecting " + labels[which] + "…", false);
                apiActionExecutor.execute(() -> {
                    try {
                        if (AppState.get() != connection) return;
                        new ShadowQuicApi(this).put("/outbounds/" + ShadowQuicApi.segment(selectorTag) + "/selection",
                            new JSONObject().put("selected", labels[which]));
                        post(() -> {
                            if (AppState.get() != connection) return;
                            diagnostics.outboundAction("Selected " + labels[which] + " for new sessions.", false);
                            refreshApi();
                        });
                    } catch (Exception error) {
                        post(() -> { if (AppState.get() == connection)
                            diagnostics.outboundAction("Selection failed: " + SquirrelVpnService.message(error), true); });
                    }
                });
            }).setNegativeButton("Cancel", null).show();
    }

    private void probeOutbound(String tag) {
        AppState.Snapshot connection = AppState.get();
        if (connection.phase != AppState.Phase.CONNECTED) return;
        EditText url = new EditText(diagnostics.context);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setText("http://example.com/");
        url.setContentDescription("HTTP URL for latency probe");
        LinearLayout content = new LinearLayout(diagnostics.context);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        content.addView(url, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(diagnostics.context).setTitle("Probe " + tag)
            .setMessage("Measure an HTTP request through this outbound.")
            .setView(content).setNegativeButton("Cancel", null).setPositiveButton("Probe", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String target = url.getText().toString().trim();
            Uri parsed = Uri.parse(target);
            if (!("http".equalsIgnoreCase(parsed.getScheme()) || "https".equalsIgnoreCase(parsed.getScheme()))
                    || parsed.getHost() == null) {
                url.setError("Enter an HTTP or HTTPS URL");
                return;
            }
            dialog.dismiss();
            if (AppState.get() != connection) return;
            diagnostics.probePending(tag, true);
            diagnostics.outboundAction("Probing " + tag + "…", false);
            apiActionExecutor.execute(() -> {
                try {
                    if (AppState.get() != connection) return;
                    new ShadowQuicApi(this).post("/outbounds/" + ShadowQuicApi.segment(tag) + "/probe",
                        new JSONObject().put("url", target).put("timeout_ms", 10000));
                    post(() -> {
                        if (AppState.get() != connection) return;
                        diagnostics.outboundAction("Probe completed for " + tag + ".", false);
                        refreshApi();
                    });
                } catch (Exception error) {
                    post(() -> { if (AppState.get() == connection)
                        diagnostics.outboundAction("Probe failed: " + SquirrelVpnService.message(error), true); });
                } finally { post(() -> { if (AppState.get() == connection) diagnostics.probePending(tag, false); }); }
            });
        }));
        dialog.show();
    }

    private void toggleVpn() {
        if (AppState.get().busy()) {
            startService(new Intent(this, SquirrelVpnService.class).setAction(SquirrelVpnService.STOP));
            return;
        }
        save(true);
    }
    private void save(boolean start) {
        if (preparing || downloading || loadingConfig || profileId == null) return;
        preparing = true;
        renderStatus();
        String config = editor.getText().toString();
        String id = profileId;
        io.execute(() -> {
            try {
                NativeBridge.validate(ConfigStore.forRuntime(this, config));
                ConfigProfiles.save(this, id, config);
                post(() -> {
                    preparing = false;
                    setConfigNotice("Saved and validated. Changes apply on the next connection.", false);
                    if (start) prepareVpn();
                    else Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
                    renderStatus();
                });
            } catch (Exception | LinkageError e) {
                post(() -> { preparing = false; renderStatus(); showError(e); });
            }
        });
    }
    private void prepareVpn() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            return;
        }
        requestVpnConsent();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1) requestVpnConsent();
    }
    private void requestVpnConsent() {
        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission, VPN_PERMISSION);
        else startVpn();
    }
    private void startVpn() {
        try { startForegroundService(new Intent(this, SquirrelVpnService.class).setAction(SquirrelVpnService.START)); }
        catch (Exception e) { showError(e); }
    }
    private void importFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, IMPORT);
    }
    private void downloadConfig() {
        if (downloading || preparing || loadingConfig) return;
        EditText address = new EditText(diagnostics.context);
        address.setId(R.id.remote_url);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setSingleLine(true);
        address.setHint("https://example.com/config.yaml");
        address.setText(remoteUrl);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), 0);
        content.addView(address);
        AlertDialog dialog = new AlertDialog.Builder(diagnostics.context).setTitle("Download configuration")
            .setMessage("Enter an HTTP or HTTPS URL. The downloaded YAML replaces the editor after validation. Save or Connect to apply it.")
            .setView(content).setNegativeButton("Cancel", null).setPositiveButton("Download", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String url = address.getText().toString().trim();
            if (url.isEmpty()) { address.setError("Enter a configuration URL"); return; }
            remoteUrl = url;
            dialog.dismiss();
            downloading = true;
            setConfigNotice("Downloading and validating configuration…", false);
            renderStatus();
            ConfigDownloader download = new ConfigDownloader();
            downloader = download;
            network.execute(() -> {
                try {
                    String config = download.download(url);
                    NativeBridge.validate(ConfigStore.forRuntime(this, config));
                    post(() -> {
                        editor.setText(config);
                        setConfigNotice("Downloaded and validated. Save or Connect to use this configuration.", false);
                        finishDownload();
                        showPage(CONFIGURATION);
                    });
                } catch (Exception | LinkageError error) {
                    post(() -> {
                        setConfigNotice("Download failed. Your editor contents are preserved.", true);
                        finishDownload();
                        showError(error);
                    });
                }
            });
        }));
        dialog.show();
    }
    private void finishDownload() {
        downloading = false;
        downloader = null;
        renderStatus();
    }
    private void exportConfig() {
        exportedConfig = editor.getText().toString();
        createDocument(EXPORT_CONFIG, "squirrel-config.yaml", "application/yaml");
    }
    private void createDocument(int request, String name, String mime) {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime)
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name), request);
    }
    private void loadExample(String name) {
        if (preparing || downloading || loadingConfig) return;
        new AlertDialog.Builder(diagnostics.context).setTitle("Load " + (name.equals("direct.yaml") ? "direct test" : "proxy template") + "?")
            .setMessage("This replaces the text in the editor. Your saved configuration stays available until you save or connect.")
            .setNegativeButton("Cancel", null).setPositiveButton("Load", (dialog, which) -> {
                loadingConfig = true;
                renderStatus();
                io.execute(() -> {
                    try {
                        String text = ConfigStore.asset(this, name);
                        post(() -> {
                            editor.setText(text);
                            setConfigNotice("Example loaded. Edit, then save or connect.", false);
                            loadingConfig = false;
                            renderStatus();
                        });
                    } catch (Exception e) {
                        post(() -> { loadingConfig = false; renderStatus(); showError(e); });
                    }
                });
            }).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VPN_PERMISSION) {
            if (result == RESULT_OK) startVpn();
            else Toast.makeText(this, "VPN permission was not granted", Toast.LENGTH_LONG).show();
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String configCopy = exportedConfig != null ? exportedConfig : editor.getText().toString();
        if (request == IMPORT) { loadingConfig = true; renderStatus(); }
        io.execute(() -> {
            try {
                if (request == IMPORT) {
                    String text;
                    try (InputStream stream = getContentResolver().openInputStream(uri)) { text = ConfigStore.readText(stream); }
                    post(() -> {
                        editor.setText(text);
                        setConfigNotice("Imported into editor. Save or connect to validate and use this file.", false);
                        loadingConfig = false;
                        renderStatus();
                        showPage(CONFIGURATION);
                    });
                } else if (request == EXPORT_LOG || request == EXPORT_CONFIG) {
                    String text = request == EXPORT_LOG ? AppLog.read(this, true) : configCopy;
                    if (text == null) throw new java.io.IOException("Export interrupted. Try exporting again.");
                    try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                        if (stream == null) throw new java.io.IOException("Unable to write the selected file");
                        stream.write(text.getBytes(StandardCharsets.UTF_8));
                    }
                    post(() -> Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                post(() -> {
                    if (request == IMPORT) { loadingConfig = false; renderStatus(); }
                    showError(e);
                });
            }
        });
    }
    @Override protected void onResume() { super.onResume(); main.post(refresh); }
    @Override protected void onPause() { main.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("selectedPage", pager.getCurrentItem());
        diagnostics.saveFilters(state);
        workspace.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (downloader != null) downloader.cancel();
        main.removeCallbacksAndMessages(null);
        network.shutdownNow();
        apiExecutor.shutdownNow();
        apiActionExecutor.shutdownNow();
        io.shutdown();
        super.onDestroy();
    }
    @Override public Object onRetainNonConfigurationInstance() {
        rememberDraft();
        return new Draft(profileDrafts, profileUrls, pager.getCurrentItem());
    }
    private void post(Runnable action) {
        main.post(() -> { if (!isFinishing() && !isDestroyed()) action.run(); });
    }
    private void enableActions(View view, boolean enabled) {
        if (view instanceof Button) view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enableActions(group.getChildAt(i), enabled);
        }
    }
    private void setConfigNotice(String message, boolean failed) {
        configNotice.setText(message);
        configNotice.setTextColor(failed ? diagnostics.error : diagnostics.secondary);
    }
    private void showError(Throwable e) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(diagnostics.context).setTitle("Squirrel").setMessage(SquirrelVpnService.message(e))
            .setPositiveButton("OK", null).show();
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(diagnostics.context); view.setText(label); view.setAllCaps(false); view.setTextSize(13);
        view.setPadding(dp(4), 0, dp(4), 0); view.setMinWidth(0); view.setMinimumWidth(0);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
