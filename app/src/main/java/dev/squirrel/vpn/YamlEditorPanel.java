package dev.squirrel.vpn;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A secure, keyboard-resizing editing window over the configuration workspace. */
final class YamlEditorPanel {
    final Dialog dialog;
    final Button save;
    private final Activity owner;
    private final DiagnosticsUi theme;
    private final EditText editor;
    private final TextView profile, notice;
    private boolean restoreOpen, restoreFocus, restoreIme;
    private int selectionStart = -1, selectionEnd, scrollX, scrollY;

    YamlEditorPanel(Activity owner, DiagnosticsUi theme, EditText editor, Runnable saveAction) {
        this.owner = owner;
        this.theme = theme;
        this.editor = editor;
        dialog = new Dialog(theme.context);
        dialog.setOwnerActivity(owner);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_DIM_BEHIND
            | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        profile = theme.text("", 12, theme.secondary, false);
        profile.setMaxLines(2);
        profile.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notice = theme.text("", 13, theme.secondary, false);
        notice.setId(R.id.yaml_panel_notice);
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout root = new LinearLayout(theme.context) {
            @Override protected void onMeasure(int width, int height) {
                boolean shortViewport = View.MeasureSpec.getSize(height) - getPaddingTop() - getPaddingBottom() < theme.dp(300);
                profile.setVisibility(shortViewport ? GONE : VISIBLE);
                notice.setVisibility(shortViewport ? GONE : VISIBLE);
                super.onMeasure(width, height);
            }
        };
        root.setOrientation(LinearLayout.VERTICAL);
        root.setId(R.id.yaml_panel);
        root.setFocusableInTouchMode(true);
        boolean wide = owner.getResources().getConfiguration().screenWidthDp >= 600
            && owner.getResources().getConfiguration().screenHeightDp >= 480;
        root.setBackground(theme.shape(theme.panel, wide ? 12 : 0));
        root.setPadding(theme.dp(16), theme.dp(12), theme.dp(16), theme.dp(16));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(theme.dp(16) + insets.getSystemWindowInsetLeft(),
                theme.dp(12) + insets.getSystemWindowInsetTop(),
                theme.dp(16) + insets.getSystemWindowInsetRight(),
                theme.dp(16) + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout header = theme.row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = theme.column();
        TextView title = theme.text("Edit YAML", 20, theme.ink, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        identity.addView(title);
        identity.addView(profile);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        save = theme.action("Save", saveAction, true);
        save.setId(R.id.yaml_panel_save);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-2, -2);
        saveParams.setMargins(theme.dp(8), 0, theme.dp(8), 0);
        header.addView(save, saveParams);
        ImageButton close = theme.iconAction("Close YAML editor, keep draft", this::close);
        close.setId(R.id.yaml_panel_close);
        close.setImageResource(R.drawable.ic_close);
        close.setTooltipText("Close editor · keep draft");
        header.addView(close, new LinearLayout.LayoutParams(theme.dp(48), theme.dp(48)));
        root.addView(header);
        LinearLayout.LayoutParams noticeParams = new LinearLayout.LayoutParams(-1, -2);
        noticeParams.setMargins(0, theme.dp(8), 0, theme.dp(12));
        root.addView(notice, noticeParams);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        android.widget.FrameLayout.LayoutParams contentParams = new android.widget.FrameLayout.LayoutParams(-1, -1);
        if (wide) contentParams.setMargins(0, theme.dp(24), 0, theme.dp(24));
        dialog.setContentView(root, contentParams);
        dialog.setOnDismissListener(ignored -> {
            restoreOpen = false;
            editor.clearFocus();
        });
    }

    void open() {
        if (dialog.isShowing() || owner.isFinishing() || owner.isDestroyed()) return;
        dialog.show();
        Window window = dialog.getWindow();
        android.content.res.Configuration config = owner.getResources().getConfiguration();
        boolean wide = config.screenWidthDp >= 600 && config.screenHeightDp >= 480;
        window.setLayout(wide ? theme.dp(Math.min(900, config.screenWidthDp - 48)) : -1, -1);
        window.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.32f;
        window.setAttributes(attributes);
        window.getDecorView().setSystemUiVisibility(owner.getWindow().getDecorView().getSystemUiVisibility());
        window.setStatusBarColor(theme.panel);
        window.setNavigationBarColor(theme.panel);
        if (selectionStart >= 0) editor.post(() -> {
            int length = editor.length();
            editor.setSelection(Math.min(selectionStart, length), Math.min(selectionEnd, length));
            editor.scrollTo(scrollX, scrollY);
            if (restoreFocus) {
                editor.requestFocus();
                if (restoreIme) owner.getSystemService(InputMethodManager.class).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }
            restoreFocus = false;
            restoreIme = false;
            selectionStart = -1;
        });
    }

    void close() {
        restoreOpen = false;
        if (!dialog.isShowing()) return;
        owner.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(editor.getWindowToken(), 0);
        dialog.dismiss();
    }

    void ready(boolean ready, boolean saving, CharSequence selectedProfile) {
        save.setEnabled(ready);
        save.setText(saving ? "Saving…" : "Save");
        profile.setText(selectedProfile);
        profile.setTooltipText(selectedProfile);
        if (ready && restoreOpen) { restoreOpen = false; open(); }
    }

    void notice(String message, boolean failed) {
        notice.setText(message);
        notice.setTextColor(failed ? theme.error : theme.secondary);
    }

    void saveState(Bundle state) {
        state.putBoolean("workspace.yamlOpen", dialog.isShowing() || restoreOpen);
        state.putInt("workspace.yamlStart", editor.getSelectionStart());
        state.putInt("workspace.yamlEnd", editor.getSelectionEnd());
        state.putInt("workspace.yamlX", editor.getScrollX());
        state.putInt("workspace.yamlY", editor.getScrollY());
        state.putBoolean("workspace.yamlFocus", editor.hasFocus());
        android.view.WindowInsets insets = editor.getRootWindowInsets();
        state.putBoolean("workspace.yamlIme", insets != null && (android.os.Build.VERSION.SDK_INT >= 30
            ? insets.isVisible(android.view.WindowInsets.Type.ime()) : insets.getSystemWindowInsetBottom() > theme.dp(100)));
    }

    void restoreState(Bundle state) {
        restoreOpen = state.getBoolean("workspace.yamlOpen", false);
        selectionStart = state.getInt("workspace.yamlStart", -1);
        selectionEnd = state.getInt("workspace.yamlEnd", selectionStart);
        scrollX = state.getInt("workspace.yamlX", 0);
        scrollY = state.getInt("workspace.yamlY", 0);
        restoreFocus = state.getBoolean("workspace.yamlFocus", false);
        restoreIme = state.getBoolean("workspace.yamlIme", false);
    }
}
