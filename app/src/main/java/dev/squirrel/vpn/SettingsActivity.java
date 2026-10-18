package dev.squirrel.vpn;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** App-wide preferences, separate from editable VPN profiles. */
public final class SettingsActivity extends Activity {
    private EditText probeUrl;
    private TextView status;
    private int ink, secondary, primary, panel, outline;

    @Override public void onCreate(Bundle saved) {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? R.style.DiagnosticsTheme_Dark : R.style.DiagnosticsTheme);
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        int surface = getColor(R.color.diagnostic_surface);
        ink = getColor(R.color.diagnostic_on_surface);
        secondary = getColor(R.color.diagnostic_secondary);
        primary = getColor(R.color.diagnostic_primary);
        panel = getColor(R.color.diagnostic_panel);
        outline = getColor(R.color.diagnostic_outline);
        getWindow().setStatusBarColor(panel);
        getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0
            : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = column();
        root.setBackgroundColor(surface);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(panel);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), 0,
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            header.setPadding(dp(10), dp(4) + insets.getSystemWindowInsetTop(), dp(22), dp(4));
            return insets.consumeSystemWindowInsets();
        });
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setImageTintList(ColorStateList.valueOf(primary));
        back.setContentDescription("Back");
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(outline), null, null));
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Settings", 23, ink, true);
        title.setId(R.id.settings_title);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        header.addView(title);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.settings_scroll);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setFocusableInTouchMode(true);
        content.setPadding(dp(22), dp(24), dp(22), dp(24));
        TextView section = text("Latency probes", 16, ink, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) section.setAccessibilityHeading(true);
        add(content, section, 0);
        add(content, text("Probe latency uses this URL immediately. API tools prefills it for individual requests.",
            14, secondary, false), 8);
        TextView label = text("Default probe URL", 13, secondary, false);
        label.setLabelFor(R.id.settings_probe_url);
        add(content, label, 24);
        probeUrl = new EditText(this);
        probeUrl.setId(R.id.settings_probe_url);
        probeUrl.setContentDescription("Default probe URL");
        probeUrl.setSingleLine(true);
        probeUrl.setTextSize(14);
        probeUrl.setTextColor(ink);
        probeUrl.setHintTextColor(secondary);
        probeUrl.setHint(AppSettings.DEFAULT_PROBE_URL);
        probeUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        probeUrl.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        probeUrl.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        probeUrl.setPadding(dp(14), dp(10), dp(14), dp(10));
        probeUrl.setMinimumHeight(dp(48));
        GradientDrawable field = shape(panel, 12);
        field.setStroke(dp(1), outline);
        probeUrl.setBackground(field);
        probeUrl.setText(saved == null ? AppSettings.probeUrl(this)
            : saved.getString("probeUrl", AppSettings.probeUrl(this)));
        add(content, probeUrl, 6);
        add(content, text("Use an HTTP URL without a username or password. HTTPS probes are not supported by the core.",
            12, secondary, false), 8);
        Button save = new Button(this);
        save.setId(R.id.settings_save);
        save.setText("Save");
        save.setAllCaps(false);
        save.setTextSize(13);
        save.setTextColor(getColor(R.color.diagnostic_on_primary));
        save.setMinHeight(dp(48));
        save.setMinimumHeight(dp(48));
        save.setPadding(dp(12), dp(10), dp(12), dp(10));
        save.setStateListAnimator(null);
        save.setElevation(0);
        save.setBackgroundTintList(null);
        save.setBackground(new RippleDrawable(ColorStateList.valueOf(outline), shape(primary, 8), null));
        save.setOnClickListener(view -> save());
        add(content, save, 24);
        status = text("", 13, secondary, false);
        status.setId(R.id.settings_status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(content, status, 12);
        probeUrl.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                status.setText("");
                probeUrl.setError(null);
            }
            @Override public void afterTextChanged(android.text.Editable text) { }
        });
        probeUrl.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            save();
            return true;
        });
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void save() {
        try {
            String url = AppSettings.validateProbeUrl(probeUrl.getText().toString());
            AppSettings.saveProbeUrl(this, url);
            probeUrl.setText(url);
            probeUrl.setError(null);
            probeUrl.clearFocus();
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(probeUrl.getWindowToken(), 0);
            status.setText("Saved. New probes use this URL.");
        } catch (IllegalArgumentException error) {
            status.setText("");
            probeUrl.setError(error.getMessage());
            probeUrl.requestFocus();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("probeUrl", probeUrl.getText().toString());
        super.onSaveInstanceState(state);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }
    private void add(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top);
        parent.addView(view, params);
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
