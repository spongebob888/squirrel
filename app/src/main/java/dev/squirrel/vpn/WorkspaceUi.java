package dev.squirrel.vpn;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;

/** Configuration and log workspaces sharing the app's native dashboard components. */
final class WorkspaceUi {
    final EditText editor, logFilter;
    final Button profileButton, downloadButton, more;
    final LinearLayout profileActions, configActions, configTools;
    final TextView configNotice, logs, logStatus;
    final Switch live;
    private final DiagnosticsUi theme;
    private final LinearLayout configuration, logPage;
    private final ScrollView logScroll;
    private String rawLogs = "", logError = "";
    private boolean hasSnapshot;

    WorkspaceUi(DiagnosticsUi theme, Runnable chooseProfile, Runnable manageProfiles,
            Runnable importFile, Runnable download, Runnable save, Runnable exportConfig,
            Runnable proxyTemplate, Runnable directTemplate, Runnable exportLogs) {
        this.theme = theme;
        boolean wide = theme.context.getResources().getConfiguration().screenWidthDp >= 600;
        configuration = new EditorPage(wide);
        configuration.setId(R.id.configuration_page);
        LinearLayout controls = theme.column();
        TextView configTitle = heading("Configuration");
        controls.addView(configTitle);
        TextView profileLabel = theme.text("Selected profile", 12, theme.secondary, false);
        add(controls, profileLabel, 14, 6);
        ((EditorPage) configuration).heading = configTitle;
        ((EditorPage) configuration).profileLabel = profileLabel;
        profileActions = theme.row();
        profileButton = theme.action("Loading profiles…", chooseProfile, false);
        profileButton.setId(R.id.config_profile);
        profileButton.setMaxLines(1);
        profileButton.setEllipsize(TextUtils.TruncateAt.END);
        android.graphics.drawable.Drawable chevron = theme.context.getDrawable(R.drawable.ic_expand_more);
        chevron.setTint(theme.primary);
        profileButton.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, chevron, null);
        profileButton.setCompoundDrawablePadding(theme.dp(8));
        weighted(profileActions, profileButton, 1.8f, true);
        Button manage = theme.action("Manage", manageProfiles, false);
        manage.setId(R.id.manage_profiles);
        weighted(profileActions, manage, 1, false);
        controls.addView(profileActions);

        configActions = theme.row();
        weighted(configActions, theme.action("Import file", importFile, false), 1.05f, true);
        downloadButton = theme.action("Download URL", download, false);
        downloadButton.setId(R.id.download_config);
        weighted(configActions, downloadButton, 1.3f, true);
        weighted(configActions, theme.action("Save", save, true), 0.75f, false);
        add(controls, configActions, 12, 0);
        configNotice = theme.text("Import or edit YAML, then Save or Connect to apply.", 13, theme.secondary, false);
        configNotice.setId(R.id.config_notice);
        configNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(controls, configNotice, 12, wide ? 12 : 0);

        LinearLayout editorPane = theme.column();
        configTools = theme.row();
        configTools.setGravity(Gravity.CENTER_VERTICAL);
        TextView yaml = theme.text("YAML · UTF-8", 12, theme.secondary, true);
        configTools.addView(yaml, new LinearLayout.LayoutParams(0, -2, 1));
        more = theme.action("More", () -> {}, false);
        more.setId(R.id.config_more);
        more.setContentDescription("More configuration actions");
        more.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(theme.context, more);
            menu.getMenu().add(0, 1, 0, "Export configuration");
            menu.getMenu().add(0, 2, 1, "Proxy template");
            menu.getMenu().add(0, 3, 2, "Direct test");
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) exportConfig.run();
                else if (item.getItemId() == 2) proxyTemplate.run();
                else directTemplate.run();
                return true;
            });
            menu.show();
        });
        configTools.addView(more, new LinearLayout.LayoutParams(theme.dp(88), -2));
        add(editorPane, configTools, wide ? 0 : 8, 8);
        editor = new EditText(theme.context);
        editor.setId(R.id.config_editor);
        editor.setTextSize(13);
        editor.setTextColor(theme.ink);
        editor.setHintTextColor(theme.secondary);
        editor.setHint("Import or paste ShadowQUIC YAML");
        editor.setContentDescription("YAML configuration editor");
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setHorizontallyScrolling(true);
        editor.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
            | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        editor.setPadding(theme.dp(14), theme.dp(14), theme.dp(14), theme.dp(14));
        editor.setLineSpacing(theme.dp(2), 1);
        editor.setHighlightColor((theme.primary & 0x00FFFFFF) | 0x33000000);
        StateListDrawable editorBackground = new StateListDrawable();
        GradientDrawable focused = theme.fieldBackground();
        focused.setStroke(theme.dp(2), theme.primary);
        GradientDrawable resting = theme.fieldBackground();
        resting.setStroke(theme.dp(1), theme.outline);
        editorBackground.addState(new int[]{android.R.attr.state_focused}, focused);
        editorBackground.addState(new int[]{}, resting);
        editor.setBackground(editorBackground);
        editor.setSaveEnabled(false);
        EditorScrollHost host = new EditorScrollHost(theme.context);
        host.addView(editor, new android.widget.FrameLayout.LayoutParams(-1, -1));
        editorPane.addView(host, new LinearLayout.LayoutParams(-1, 0, 1));
        if (wide) {
            ScrollView sidebar = new ScrollView(theme.context);
            sidebar.addView(controls);
            int sidebarWidth = Math.min(360, theme.context.getResources().getConfiguration().screenWidthDp / 2 - 36);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(theme.dp(sidebarWidth), -1);
            params.setMarginEnd(theme.dp(24));
            configuration.addView(sidebar, params);
            configuration.addView(editorPane, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            configuration.addView(controls);
            configuration.addView(editorPane, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        logPage = new LogPage();
        logPage.setPadding(theme.dp(22), theme.dp(wide ? 12 : 24), theme.dp(22), theme.dp(12));
        TextView logTitle = heading("Logs");
        logPage.addView(logTitle);
        ((LogPage) logPage).heading = logTitle;
        LinearLayout logActions = theme.row();
        logActions.setGravity(Gravity.CENTER_VERTICAL);
        logFilter = new EditText(theme.context);
        logFilter.setId(R.id.log_filter);
        logFilter.setSingleLine(true);
        logFilter.setTextSize(14);
        logFilter.setTextColor(theme.ink);
        logFilter.setHintTextColor(theme.secondary);
        logFilter.setHint("Filter logs");
        logFilter.setContentDescription("Filter displayed log lines");
        logFilter.setPadding(theme.dp(14), theme.dp(8), theme.dp(14), theme.dp(8));
        logFilter.setMinimumHeight(theme.dp(48));
        logFilter.setBackground(theme.fieldBackground());
        weighted(logActions, logFilter, 1, true);
        live = new Switch(theme.context);
        live.setId(R.id.log_live);
        live.setText("Live updates");
        live.setTextSize(13);
        live.setTextColor(theme.ink);
        live.setMinHeight(theme.dp(48));
        live.setSwitchPadding(theme.dp(12));
        live.setChecked(true);
        if (wide) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginEnd(theme.dp(20));
            logActions.addView(live, params);
        }
        logActions.addView(theme.action("Export logs", exportLogs, false), new LinearLayout.LayoutParams(-2, -2));
        add(logPage, logActions, 12, 4);
        if (!wide) add(logPage, live, 0, 0);
        logStatus = theme.text("Loading service and core logs…", 13, theme.secondary, false);
        logStatus.setId(R.id.log_status);
        add(logPage, logStatus, 6, 12);
        logScroll = new ScrollView(theme.context);
        logScroll.setId(R.id.log_scroll);
        logScroll.setFillViewport(true);
        GradientDrawable logBackground = theme.fieldBackground();
        logBackground.setStroke(theme.dp(1), theme.outline);
        logScroll.setBackground(logBackground);
        logScroll.setClipToOutline(true);
        logs = theme.text("Loading logs…", 12, theme.ink, false);
        logs.setId(R.id.log_text);
        logs.setTypeface(Typeface.MONOSPACE);
        logs.setLineSpacing(theme.dp(3), 1);
        logs.setTextIsSelectable(true);
        logs.setPadding(theme.dp(14), theme.dp(14), theme.dp(14), theme.dp(14));
        logs.setHighlightColor((theme.primary & 0x00FFFFFF) | 0x33000000);
        logScroll.addView(logs);
        logPage.addView(logScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add(logPage, theme.text("Latest lines shown. Export includes all retained logs.", 12, theme.secondary, false), 10, 0);
        logFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { renderLogView(true); }
            @Override public void afterTextChanged(Editable s) {}
        });
        live.setOnCheckedChangeListener((button, checked) -> renderLogView(false));
    }

    View configurationPage() { return configuration; }
    View logsPage() { return logPage; }
    boolean needsLogRead() { return live.isChecked() || !hasSnapshot; }
    void clearLogFilterFocus() { logFilter.clearFocus(); }

    void renderLogs(String text) {
        if (hasSnapshot && !live.isChecked()) return;
        if (hasSnapshot && rawLogs.equals(text) && logError.isEmpty()) return;
        rawLogs = text;
        hasSnapshot = true;
        logError = "";
        renderLogView(false);
    }

    void logReadError(String message) {
        logError = "Updates unavailable. " + message + " Retrying when live.";
        renderLogView(false);
    }

    void saveState(Bundle state) {
        state.putString("workspace.logFilter", logFilter.getText().toString());
        state.putBoolean("workspace.liveLogs", live.isChecked());
    }

    void restoreState(Bundle state) {
        logFilter.setText(state.getString("workspace.logFilter", ""));
        live.setChecked(state.getBoolean("workspace.liveLogs", true));
    }

    private void renderLogView(boolean filterChanged) {
        if (!hasSnapshot) {
            logStatus.setText(logError.isEmpty() ? "Loading service and core logs…" : logError);
            logStatus.setTextColor(logError.isEmpty() ? theme.secondary : theme.error);
            return;
        }
        String query = logFilter.getText().toString().trim().toLowerCase(Locale.ROOT);
        StringBuilder filtered = new StringBuilder();
        String header = "", emittedHeader = "";
        int total = 0, matches = 0;
        boolean empty = rawLogs.startsWith("No logs yet.");
        for (String line : rawLogs.split("\n")) {
            if (line.startsWith("— ") && line.endsWith(" —")) { header = line; continue; }
            if (line.isEmpty() || empty) continue;
            total++;
            if (!line.toLowerCase(Locale.ROOT).contains(query)) continue;
            matches++;
            if (!header.equals(emittedHeader)) {
                filtered.append(header).append('\n');
                emittedHeader = header;
            }
            filtered.append(line).append('\n');
        }
        String view = query.isEmpty() ? rawLogs : matches == 0
            ? "No matching log lines. Clear the filter to view all logs." : filtered.toString();
        boolean follow = !logScroll.canScrollVertically(1);
        int position = logScroll.getScrollY();
        if (!TextUtils.equals(logs.getText(), view)) {
            logs.setText(view);
            logScroll.post(() -> {
                if (filterChanged) logScroll.scrollTo(0, 0);
                else if (follow) logScroll.scrollTo(0, Math.max(0, logs.getHeight() - logScroll.getHeight()));
                else logScroll.scrollTo(0, position);
            });
        }
        logStatus.setText(logError.isEmpty() ? (live.isChecked() ? "Live" : "Paused")
            + " · " + matches + " of " + total + " lines · service + core" : logError);
        logStatus.setTextColor(logError.isEmpty() ? theme.secondary : theme.error);
    }

    private final class EditorPage extends LinearLayout {
        final boolean wide;
        View heading, profileLabel;
        EditorPage(boolean wide) {
            super(theme.context);
            this.wide = wide;
            setOrientation(wide ? HORIZONTAL : VERTICAL);
            setBackgroundColor(theme.surface);
            setFocusableInTouchMode(true);
        }
        @Override protected void onMeasure(int width, int height) {
            boolean shortViewport = !wide && View.MeasureSpec.getSize(height) < theme.dp(500);
            heading.setVisibility(shortViewport ? GONE : VISIBLE);
            profileLabel.setVisibility(shortViewport ? GONE : VISIBLE);
            setPadding(theme.dp(22), theme.dp(shortViewport || wide ? 12 : 24), theme.dp(22), theme.dp(12));
            super.onMeasure(width, height);
        }
    }

    private final class LogPage extends LinearLayout {
        View heading;
        LogPage() {
            super(theme.context);
            setOrientation(VERTICAL);
            setBackgroundColor(theme.surface);
            setFocusableInTouchMode(true);
        }
        @Override protected void onMeasure(int width, int height) {
            heading.setVisibility(View.MeasureSpec.getSize(height) < theme.dp(450) ? GONE : VISIBLE);
            super.onMeasure(width, height);
        }
    }

    private TextView heading(String text) {
        boolean compact = theme.context.getResources().getConfiguration().screenHeightDp < 480;
        TextView title = theme.text(text, compact ? 22 : 26, theme.ink, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        return title;
    }

    private void weighted(LinearLayout row, View view, float weight, boolean gap) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, weight);
        if (gap) params.setMarginEnd(theme.dp(8));
        row.addView(view, params);
    }

    private void add(LinearLayout parent, View view, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = theme.dp(top);
        params.bottomMargin = theme.dp(bottom);
        parent.addView(view, params);
    }
}
