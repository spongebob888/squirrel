package dev.squirrel.vpn;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Configuration and log workspaces sharing the app's native dashboard components. */
final class WorkspaceUi {
    final EditText editor, logFilter;
    final Button profileButton, downloadButton, more, editButton;
    final YamlEditorPanel editorPanel;
    final LinearLayout profileActions, configActions, configTools;
    final TextView configNotice, logs, logStatus;
    final Switch live;
    private final DiagnosticsUi theme;
    private final LinearLayout configuration, logPage;
    private final ScrollView logScroll;
    private final LogFormatter logFormatter;
    private Spanned formattedLogs, renderedSnapshot;
    private String rawLogs = "", logError = "";
    private boolean hasSnapshot;

    WorkspaceUi(android.app.Activity activity, DiagnosticsUi theme, Runnable chooseProfile, Runnable manageProfiles,
            Runnable importFile, Runnable download, Runnable save, Runnable exportConfig,
            Runnable proxyTemplate, Runnable directTemplate, Runnable exportLogs) {
        this.theme = theme;
        logFormatter = new LogFormatter(theme);
        boolean wide = theme.context.getResources().getConfiguration().screenWidthDp >= 600;
        configuration = theme.column();
        configuration.setId(R.id.configuration_page);
        configuration.setBackgroundColor(theme.surface);
        configuration.setPadding(theme.dp(22), theme.dp(24), theme.dp(22), theme.dp(12));
        ScrollView configScroll = new ScrollView(theme.context);
        configuration.addView(configScroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout controls = theme.column();
        TextView configTitle = heading("Configuration");
        controls.addView(configTitle);
        TextView profileLabel = theme.text("Selected profile", 12, theme.secondary, false);
        add(controls, profileLabel, 14, 6);
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
        weighted(configActions, theme.action("Import file", importFile, false), 1, true);
        downloadButton = theme.action("Download URL", download, false);
        downloadButton.setId(R.id.download_config);
        weighted(configActions, downloadButton, 1, false);
        Button saveConfig = theme.action("Save", save, false);
        saveConfig.setId(R.id.config_save);
        configNotice = theme.text("Import or edit YAML, then Save or Connect to apply.", 13, theme.secondary, false);
        configNotice.setId(R.id.config_notice);
        configNotice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        configTools = theme.row();
        configTools.setGravity(Gravity.CENTER_VERTICAL);
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
        editorPanel = new YamlEditorPanel(activity, theme, editor, save);
        editButton = theme.action("Edit YAML", this::openEditor, true);
        editButton.setId(R.id.config_edit);
        theme.icon(editButton, R.drawable.ic_configuration, theme.onPrimary);
        weighted(configTools, editButton, 1.8f, true);
        weighted(configTools, saveConfig, 1, true);
        weighted(configTools, more, 1, false);
        add(controls, configTools, 12, 0);
        add(controls, configNotice, 12, 0);
        add(controls, configActions, 24, 0);
        android.widget.FrameLayout.LayoutParams controlsParams = new android.widget.FrameLayout.LayoutParams(
            wide ? theme.dp(Math.min(720, theme.context.getResources().getConfiguration().screenWidthDp - 44)) : -1, -2,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        configScroll.addView(controls, controlsParams);

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
    void openEditor() {
        editorPanel.ready(editor.isEnabled(), false, profileButton.getText());
        editorPanel.open();
    }
    void renderEditorState(boolean ready, boolean saving) {
        editButton.setEnabled(ready);
        editorPanel.ready(ready, saving, profileButton.getText());
    }
    void renderConfigNotice(String message, boolean failed) {
        configNotice.setText(message);
        configNotice.setTextColor(failed ? theme.error : theme.secondary);
        editorPanel.notice(message, failed);
    }
    void closeEditor() { editorPanel.close(); }
    void destroy() { editorPanel.dialog.dismiss(); }
    View logsPage() { return logPage; }
    boolean needsLogRead() { return live.isChecked() || !hasSnapshot; }
    void clearLogFilterFocus() { logFilter.clearFocus(); }

    void renderLogs(String text) {
        if (hasSnapshot && !live.isChecked()) return;
        if (hasSnapshot && rawLogs.equals(text) && logError.isEmpty()) return;
        rawLogs = text;
        formattedLogs = logFormatter.format(text);
        hasSnapshot = true;
        logError = "";
        renderLogView(false);
    }

    void logReadError(String message) {
        logError = "Updates unavailable. " + message + " Retrying when live.";
        renderLogView(false);
    }

    void saveState(Bundle state) {
        editorPanel.saveState(state);
        state.putString("workspace.logFilter", logFilter.getText().toString());
        state.putBoolean("workspace.liveLogs", live.isChecked());
    }

    void restoreState(Bundle state) {
        editorPanel.restoreState(state);
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
        List<int[]> retained = new ArrayList<>();
        String plain = formattedLogs.toString();
        int offset = 0, headerStart = -1, headerEnd = 0, emittedHeader = -1;
        int total = 0, matches = 0;
        boolean empty = plain.startsWith("No logs yet.");
        for (String line : plain.split("\n", -1)) {
            int start = offset, end = Math.min(plain.length(), start + line.length() + 1);
            offset = end;
            if (line.startsWith("— ") && line.endsWith(" —")) { headerStart = start; headerEnd = end; continue; }
            if (line.isEmpty() || empty) continue;
            total++;
            if (!line.toLowerCase(Locale.ROOT).contains(query)) continue;
            matches++;
            if (headerStart >= 0 && headerStart != emittedHeader) {
                retained.add(new int[]{headerStart, headerEnd});
                emittedHeader = headerStart;
            }
            retained.add(new int[]{start, end});
        }
        CharSequence view = formattedLogs;
        if (!query.isEmpty()) {
            if (matches == 0) view = "No matching log lines. Clear the filter to view all logs.";
            else {
                // Delete omitted ranges from the end so multi-line ANSI spans retain their styles.
                SpannableStringBuilder filtered = new SpannableStringBuilder(formattedLogs);
                int end = filtered.length();
                for (int i = retained.size() - 1; i >= 0; i--) {
                    int[] range = retained.get(i);
                    filtered.delete(range[1], end);
                    end = range[0];
                }
                filtered.delete(0, end);
                view = filtered;
            }
        }
        boolean follow = !logScroll.canScrollVertically(1);
        int position = logScroll.getScrollY();
        if (!TextUtils.equals(logs.getText(), view) || renderedSnapshot != formattedLogs) {
            logs.setText(view, TextView.BufferType.SPANNABLE);
            renderedSnapshot = formattedLogs;
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
