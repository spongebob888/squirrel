package dev.squirrel.vpn;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int IMPORT = 10, EXPORT_LOG = 11, VPN_PERMISSION = 12, EXPORT_CONFIG = 13;
    private static final int CONNECTION = 0, CONFIGURATION = 1, LOGS = 2;
    private static final int GREEN = 0xFFB7E88C, MUTED = 0xFFA9B4A5, WHITE = 0xFFE7EDE2;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private ConfigDownloader downloader;
    private final AtomicBoolean logRead = new AtomicBoolean();
    private TextView status, detail, logs, configNotice;
    private EditText editor;
    private Button connect, connectionTab, configTab, logsTab, downloadButton;
    private LinearLayout configActions, configSamples;
    private ViewPager2 pager;
    private boolean showingLogs, preparing, loadingConfig = true, downloading;
    private String exportedConfig, remoteUrl = "";

    private static final class Draft {
        final String text, url;
        final int page;
        Draft(String text, String url, int page) { this.text = text; this.url = url; this.page = page; }
    }
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            renderStatus();
            if (showingLogs && logRead.compareAndSet(false, true)) {
                io.execute(() -> {
                    try {
                        String text = AppLog.read(MainActivity.this, false);
                        post(() -> { if (!text.contentEquals(logs.getText())) logs.setText(text); });
                    } catch (Exception e) { post(() -> logs.setText(SquirrelVpnService.message(e))); }
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
        LinearLayout root = column();
        root.setPadding(dp(20), dp(12), dp(20), 0);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(20), dp(12) + insets.getSystemWindowInsetTop(), dp(20), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        TextView brand = text("SQUIRREL  /  SHADOWQUIC", 12, GREEN);
        brand.setLetterSpacing(0.16f);
        root.addView(brand);
        LinearLayout tabs = row();
        tabs.setPadding(0, dp(12), 0, dp(10));
        connectionTab = button("Connection", () -> showPage(CONNECTION));
        configTab = button("Configuration", () -> showPage(CONFIGURATION));
        logsTab = button("Logs", () -> showPage(LOGS));
        connectionTab.setId(R.id.connection_tab);
        configTab.setId(R.id.configuration_tab);
        logsTab.setId(R.id.logs_tab);
        tabs.addView(connectionTab, weight());
        tabs.addView(configTab, weight());
        tabs.addView(logsTab, weight());
        root.addView(tabs);

        pager = new ViewPager2(this);
        pager.setId(R.id.pages);
        pager.setOffscreenPageLimit(2);
        View[] pages = {connectionPage(), configurationPage(), logsPage()};
        pager.setAdapter(new PageAdapter(pages));
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int page) { selectTab(page); }
        });
        root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        int page = saved == null ? CONNECTION : saved.getInt("selectedPage", CONNECTION);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof Draft) {
            Draft draft = (Draft) retained;
            editor.setText(draft.text);
            remoteUrl = draft.url;
            page = draft.page;
            loadingConfig = false;
        } else {
            io.execute(() -> {
                try {
                    String config = ConfigStore.read(this);
                    post(() -> { editor.setText(config); loadingConfig = false; renderStatus(); });
                } catch (Exception e) {
                    post(() -> { loadingConfig = false; renderStatus(); showError(e); });
                }
            });
        }
        pager.setCurrentItem(page, false);
        selectTab(page);
        renderStatus();
    }

    private LinearLayout connectionPage() {
        LinearLayout page = column();
        page.setFocusableInTouchMode(true);
        TextView title = text("Your network. Your rules.", 26, WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(24), 0, dp(24));
        page.addView(title);
        LinearLayout card = column();
        card.setPadding(dp(18), dp(20), dp(18), dp(18));
        card.setBackground(background(0xFF202C20, 18));
        status = text("Disconnected", 21, WHITE);
        status.setTypeface(null, Typeface.BOLD);
        detail = text("Ready when you are", 13, MUTED);
        detail.setPadding(0, dp(8), 0, dp(18));
        card.addView(status);
        card.addView(detail);
        connect = button("Connect", this::toggleVpn);
        connect.setBackgroundTintList(android.content.res.ColorStateList.valueOf(GREEN));
        connect.setTextColor(0xFF15220F);
        card.addView(connect);
        TextView bypass = text("✓  App bypass always enabled", 12, GREEN);
        bypass.setPadding(0, dp(14), 0, 0);
        card.addView(bypass);
        page.addView(card);
        TextView help = text("Add your configuration in the next tab, then connect here. Swipe between tabs to edit your config or follow the live logs.", 14, MUTED);
        help.setPadding(0, dp(24), 0, dp(20));
        page.addView(help);
        View space = new View(this);
        page.addView(space, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = text("TCP + UDP  ·  IPv4 + IPv6  ·  ShadowQUIC core", 11, MUTED);
        footer.setPadding(0, dp(12), 0, dp(16));
        page.addView(footer);
        return page;
    }

    private LinearLayout configurationPage() {
        LinearLayout page = column();
        page.setFocusableInTouchMode(true);
        page.addView(heading("Configuration"));
        configNotice = text("Import a file, download a URL, or edit YAML below. Save or Connect to apply.", 12, MUTED);
        configNotice.setId(R.id.config_notice);
        configNotice.setPadding(0, dp(6), 0, dp(8));
        page.addView(configNotice);
        configActions = column();
        LinearLayout imports = row();
        imports.addView(button("Import file", this::importFile), weight());
        downloadButton = button("Download URL", this::downloadConfig);
        downloadButton.setId(R.id.download_config);
        imports.addView(downloadButton, weight());
        configActions.addView(imports);
        LinearLayout actions = row();
        actions.addView(button("Save", () -> save(false)), weight());
        actions.addView(button("Export", this::exportConfig), weight());
        configActions.addView(actions);
        page.addView(configActions);
        configSamples = row();
        configSamples.addView(button("Proxy template", () -> loadExample("example.yaml")), weight());
        configSamples.addView(button("Direct test", () -> loadExample("direct.yaml")), weight());
        page.addView(configSamples);
        editor = new EditText(this);
        editor.setId(R.id.config_editor);
        editor.setTextSize(13);
        editor.setTextColor(WHITE);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(true);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setBackground(background(0xFF182018, 12));
        editor.setSaveEnabled(false);
        EditorScrollHost editorHost = new EditorScrollHost(this);
        editorHost.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        page.addView(editorHost, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private LinearLayout logsPage() {
        LinearLayout page = column();
        page.setFocusableInTouchMode(true);
        page.addView(heading("Logs"));
        TextView caption = text("Live service + core logs · rotated at 1 MiB per file", 12, MUTED);
        caption.setPadding(0, dp(6), 0, dp(8));
        page.addView(caption);
        page.addView(button("Export logs", () -> createDocument(EXPORT_LOG, "squirrel-logs.txt", "text/plain")));
        ScrollView scroller = new ScrollView(this);
        logs = text("Loading logs…", 11, WHITE);
        logs.setId(R.id.log_text);
        logs.setTypeface(Typeface.MONOSPACE);
        logs.setTextIsSelectable(true);
        logs.setPadding(dp(10), dp(10), dp(10), dp(10));
        scroller.setBackground(background(0xFF182018, 12));
        scroller.addView(logs);
        page.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

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
        Button[] tabs = {connectionTab, configTab, logsTab};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == page;
            tabs[i].setSelected(selected);
            tabs[i].setTextColor(selected ? GREEN : MUTED);
            tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected ? 0xFF283A25 : 0xFF182018));
        }
        if (page != CONFIGURATION) {
            editor.clearFocus();
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
    }

    private void renderStatus() {
        AppState.Snapshot state = AppState.get();
        switch (state.phase) {
            case CONNECTED: status.setText("●  VPN active"); status.setTextColor(GREEN); break;
            case CONNECTING: status.setText("Connecting…"); status.setTextColor(WHITE); break;
            case STOPPING: status.setText("Disconnecting…"); status.setTextColor(WHITE); break;
            case ERROR: status.setText("Connection failed"); status.setTextColor(0xFFFFB4A5); break;
            default: status.setText("Disconnected"); status.setTextColor(WHITE);
        }
        detail.setText(state.detail);
        connect.setText(preparing ? "Checking configuration…" : state.busy() ? "Disconnect" : "Connect");
        boolean ready = !loadingConfig && !downloading && !preparing;
        connect.setEnabled(state.phase != AppState.Phase.STOPPING && (state.busy() || ready));
        editor.setEnabled(ready);
        enableActions(configActions, ready);
        enableActions(configSamples, ready);
        downloadButton.setText(downloading ? "Downloading…" : "Download URL");
    }
    private void toggleVpn() {
        if (AppState.get().busy()) {
            startService(new Intent(this, SquirrelVpnService.class).setAction(SquirrelVpnService.STOP));
            return;
        }
        save(true);
    }
    private void save(boolean start) {
        if (preparing || downloading || loadingConfig) return;
        preparing = true;
        renderStatus();
        String config = editor.getText().toString();
        io.execute(() -> {
            try {
                NativeBridge.validate(ConfigStore.forRuntime(this, config));
                ConfigStore.save(this, config);
                post(() -> {
                    preparing = false;
                    configNotice.setText("Saved and validated. Changes apply on the next connection.");
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
        EditText address = new EditText(this);
        address.setId(R.id.remote_url);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setSingleLine(true);
        address.setHint("https://example.com/config.yaml");
        address.setText(remoteUrl);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), 0);
        content.addView(address);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Download configuration")
            .setMessage("Enter an HTTP or HTTPS URL. The downloaded YAML replaces the editor after validation. Save or Connect to apply it.")
            .setView(content).setNegativeButton("Cancel", null).setPositiveButton("Download", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String url = address.getText().toString().trim();
            if (url.isEmpty()) { address.setError("Enter a configuration URL"); return; }
            remoteUrl = url;
            dialog.dismiss();
            downloading = true;
            configNotice.setText("Downloading and validating configuration…");
            renderStatus();
            ConfigDownloader download = new ConfigDownloader();
            downloader = download;
            network.execute(() -> {
                try {
                    String config = download.download(url);
                    NativeBridge.validate(ConfigStore.forRuntime(this, config));
                    post(() -> {
                        editor.setText(config);
                        configNotice.setText("Downloaded and validated. Save or Connect to use this configuration.");
                        finishDownload();
                        showPage(CONFIGURATION);
                    });
                } catch (Exception | LinkageError error) {
                    post(() -> {
                        configNotice.setText("Download failed. Your editor contents are preserved.");
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
        new AlertDialog.Builder(this).setTitle("Load " + (name.equals("direct.yaml") ? "direct test" : "proxy template") + "?")
            .setMessage("This replaces the text in the editor. Your saved configuration stays available until you save or connect.")
            .setNegativeButton("Cancel", null).setPositiveButton("Load", (dialog, which) -> io.execute(() -> {
                try {
                    String text = ConfigStore.asset(this, name);
                    post(() -> { editor.setText(text); configNotice.setText("Example loaded. Edit, then save or connect."); });
                } catch (Exception e) { post(() -> showError(e)); }
            })).show();
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
        io.execute(() -> {
            try {
                if (request == IMPORT) {
                    String text;
                    try (InputStream stream = getContentResolver().openInputStream(uri)) { text = ConfigStore.readText(stream); }
                    post(() -> {
                        editor.setText(text);
                        configNotice.setText("Imported into editor. Save or connect to validate and use this file.");
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
            } catch (Exception e) { post(() -> showError(e)); }
        });
    }
    @Override protected void onResume() { super.onResume(); main.post(refresh); }
    @Override protected void onPause() { main.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("selectedPage", pager.getCurrentItem());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (downloader != null) downloader.cancel();
        main.removeCallbacksAndMessages(null);
        network.shutdownNow();
        io.shutdown();
        super.onDestroy();
    }
    @Override public Object onRetainNonConfigurationInstance() {
        return new Draft(editor.getText().toString(), remoteUrl, pager.getCurrentItem());
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
    private TextView heading(String label) {
        TextView view = text(label, 22, WHITE);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(6), 0, dp(4));
        return view;
    }
    private void showError(Throwable e) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle("Squirrel").setMessage(SquirrelVpnService.message(e))
            .setPositiveButton("OK", null).show();
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private Button button(String label, Runnable action) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(13);
        view.setPadding(dp(4), 0, dp(4), 0); view.setMinWidth(0); view.setMinimumWidth(0);
        view.setOnClickListener(v -> action.run()); return view;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
