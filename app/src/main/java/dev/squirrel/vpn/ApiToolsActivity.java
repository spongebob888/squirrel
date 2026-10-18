package dev.squirrel.vpn;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONObject;

/** Infrequent control-plane operations, deliberately outside the main tab pager. */
public final class ApiToolsActivity extends Activity {
    static final String[] REMOTE = {"list-users", "add-user", "remove-user", "kill-conn", "get-stats", "clear-stats"};
    private static final String[] REMOTE_LABELS = {"List users", "Add or update user", "Remove user", "Disconnect user", "Get user stats", "Clear user stats"};
    private static final String[] LOCAL_LABELS = {"Get outbounds", "Get connections", "Get traffic", "Get memory", "Get selector", "Set selector", "Probe outbound"};
    ApiToolsModel model;
    RadioGroup mode;
    Spinner remoteOperation, localOperation;
    EditText username, password, outboundTag, selectedMember, probeUrl, probeTimeout;
    CheckBox allUsers;
    Button profile, reload, outbound, run;
    TextView status, result, resultTitle, help, scope;
    private LinearLayout remoteForm, localForm, userField, passwordField, localTagField, memberField, urlField, timeoutField;
    private int surface, ink, secondary, primary, onPrimary, container, outline, error, panel;
    private boolean restoring;
    private AlertDialog confirmation;

    @Override public void onCreate(Bundle saved) {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? R.style.DiagnosticsTheme_Dark : R.style.DiagnosticsTheme);
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        panel = getColor(R.color.diagnostic_panel);
        surface = getColor(R.color.diagnostic_surface); ink = getColor(R.color.diagnostic_on_surface);
        secondary = getColor(R.color.diagnostic_secondary); primary = getColor(R.color.diagnostic_primary);
        onPrimary = getColor(R.color.diagnostic_on_primary); container = getColor(R.color.diagnostic_container);
        outline = getColor(R.color.diagnostic_outline); error = getColor(R.color.diagnostic_error);
        getWindow().setStatusBarColor(panel); getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        build();
        Object retained = getLastNonConfigurationInstance();
        model = retained instanceof ApiToolsModel ? (ApiToolsModel) retained : new ApiToolsModel(this);
        if (retained == null && saved != null) model.tag = saved.getString("remoteTag", "");
        restoring = true;
        if (saved != null) {
            mode.check(saved.getBoolean("local") ? R.id.api_local_mode : R.id.api_remote_mode);
            remoteOperation.setSelection(saved.getInt("remoteOperation"));
            localOperation.setSelection(saved.getInt("localOperation"));
            username.setText(saved.getString("username", "")); allUsers.setChecked(saved.getBoolean("allUsers"));
            outboundTag.setText(saved.getString("tag", "")); selectedMember.setText(saved.getString("member", ""));
            probeUrl.setText(saved.getString("url", "http://example.com/")); probeTimeout.setText(saved.getString("timeout", "10000"));
        }
        restoring = false;
        model.observe(this::render);
        if (retained == null) {
            if (saved != null && saved.getBoolean("busy")) {
                model.interrupted = true;
            }
            model.loadProfile(saved == null ? null : saved.getString("profileId"));
        }
        updateFields();
    }

    private void build() {
        LinearLayout root = column(); root.setBackgroundColor(surface);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        LinearLayout header = row(); header.setBackgroundColor(panel); header.setPadding(dp(10), dp(4), dp(22), dp(4));
        ImageButton back = new ImageButton(this); back.setImageResource(R.drawable.ic_arrow_back);
        back.setImageTintList(ColorStateList.valueOf(primary)); back.setContentDescription("Back");
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(outline), null, null)); back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("API tools", 23, ink, true); title.setId(R.id.api_title);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column(); content.setPadding(dp(22), dp(12), dp(22), dp(24)); scroll.addView(content);
        mode = new RadioGroup(this); mode.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < 2; i++) {
            RadioButton choice = new RadioButton(this); choice.setText(i == 0 ? "Remote server" : "Local runtime");
            choice.setTextColor(ink); choice.setTextSize(14); choice.setMinHeight(dp(48));
            choice.setId(i == 0 ? R.id.api_remote_mode : R.id.api_local_mode);
            mode.addView(choice, new RadioGroup.LayoutParams(0, -2, 1));
        }
        mode.check(R.id.api_remote_mode); add(content, mode, 0);
        scope = text("", 13, secondary, false); add(content, scope, 8);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout body = wide ? row() : column(); body.setGravity(Gravity.TOP); add(content, body, 20);
        LinearLayout controls = column(), output = column();
        if (wide) {
            controls.setPadding(0, 0, dp(24), 0);
            body.addView(controls, new LinearLayout.LayoutParams(0, -2, 1));
            body.addView(output, new LinearLayout.LayoutParams(0, -2, 1.2f));
        } else { body.addView(controls); body.addView(output); }
        remoteForm = column(); controls.addView(remoteForm);
        add(remoteForm, text("Saved profile", 13, secondary, false), 0);
        LinearLayout profiles = row(); profile = action("Loading…", this::chooseProfile, false); profile.setId(R.id.api_profile);
        profile.setSingleLine(true); profile.setEllipsize(android.text.TextUtils.TruncateAt.END);
        picker(profile);
        reload = action("Reload", () -> model.loadProfile(model.profileId), false);
        profiles.addView(profile, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams reloadParams = new LinearLayout.LayoutParams(-2, -2); reloadParams.leftMargin = dp(8); profiles.addView(reload, reloadParams);
        add(remoteForm, profiles, 6);
        add(remoteForm, text("Server outbound", 13, secondary, false), 16);
        outbound = action("Choose server", this::chooseOutbound, false); outbound.setId(R.id.api_outbound);
        picker(outbound);
        outbound.setSingleLine(true); outbound.setEllipsize(android.text.TextUtils.TruncateAt.END); add(remoteForm, outbound, 6);
        add(remoteForm, text("Operation", 13, secondary, false), 16);
        remoteOperation = spinner(REMOTE_LABELS); remoteOperation.setId(R.id.api_remote_operation);
        remoteOperation.setContentDescription("Remote server operation"); add(remoteForm, remoteOperation, 4);
        allUsers = new CheckBox(this); allUsers.setText("All users"); allUsers.setTextSize(14); allUsers.setTextColor(ink);
        allUsers.setMinHeight(dp(48)); allUsers.setId(R.id.api_all_users); add(remoteForm, allUsers, 4);
        username = input("Username", false); username.setId(R.id.api_username); userField = field(remoteForm, "Username", username);
        identifierPreview(userField, username, "Exact username");
        password = input("New password", true); password.setId(R.id.api_password); passwordField = field(remoteForm, "New password", password);

        localForm = column(); controls.addView(localForm);
        add(localForm, text("Operation", 13, secondary, false), 0);
        localOperation = spinner(LOCAL_LABELS); localOperation.setId(R.id.api_local_operation);
        localOperation.setContentDescription("Local runtime operation"); add(localForm, localOperation, 4);
        outboundTag = input("Exact outbound tag", false); outboundTag.setId(R.id.api_local_tag); localTagField = field(localForm, "Outbound tag", outboundTag);
        identifierPreview(localTagField, outboundTag, "Exact outbound tag");
        selectedMember = input("Member outbound tag", false); memberField = field(localForm, "Selected member", selectedMember);
        identifierPreview(memberField, selectedMember, "Exact member tag");
        probeUrl = input("http://example.com/", false); probeUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        probeUrl.setText("http://example.com/"); urlField = field(localForm, "HTTP probe URL", probeUrl);
        probeTimeout = input("10000", false); probeTimeout.setInputType(InputType.TYPE_CLASS_NUMBER); probeTimeout.setText("10000");
        timeoutField = field(localForm, "Probe timeout (ms, 1–60000)", probeTimeout);
        help = text("", 13, secondary, false); add(controls, help, 12);
        run = action("List users", this::execute, true); run.setId(R.id.api_run); add(controls, run, 16);
        status = text("", 13, secondary, false); status.setId(R.id.api_status); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(controls, status, 12);
        resultTitle = text("Result", 18, ink, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) resultTitle.setAccessibilityHeading(true);
        add(output, resultTitle, wide ? 0 : 24);
        result = text("No request sent.", 12, ink, false); result.setId(R.id.api_result); result.setTypeface(Typeface.MONOSPACE);
        result.setTextIsSelectable(true); result.setSaveEnabled(false); result.setLineSpacing(dp(3), 1);
        result.setPadding(dp(14), dp(14), dp(14), dp(14)); GradientDrawable pane = fieldShape(); pane.setStroke(dp(1), outline);
        result.setBackground(pane); add(output, result, 12);
        mode.setOnCheckedChangeListener((group, id) -> {
            password.setText("");
            if (!restoring && model != null && !model.busy) {
                model.result = "No request sent in this scope."; model.resultTitle = "Result";
                model.status = id == R.id.api_local_mode ? "Connect the VPN to use local runtime tools." : "Choose a server API operation.";
                model.error = false;
            }
            updateFields();
        });
        allUsers.setOnCheckedChangeListener((button, checked) -> updateFields());
        AdapterView.OnItemSelectedListener selected = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { password.setText(""); updateFields(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        remoteOperation.setOnItemSelectedListener(selected); localOperation.setOnItemSelectedListener(selected);
    }

    private void updateFields() {
        if (restoring || model == null) return;
        boolean local = mode.getCheckedRadioButtonId() == R.id.api_local_mode;
        int op = remoteOperation.getSelectedItemPosition(), localOp = localOperation.getSelectedItemPosition();
        remoteForm.setVisibility(local ? View.GONE : View.VISIBLE); localForm.setVisibility(local ? View.VISIBLE : View.GONE);
        scope.setText(local ? "Controls the currently running VPN engine. Connect first; results are snapshots."
            : "Uses a saved profile, independent of the VPN. Admin login required. Reload to read saved changes.");
        allUsers.setVisibility(op >= 4 ? View.VISIBLE : View.GONE);
        userField.setVisibility(op == 0 || (op >= 4 && allUsers.isChecked()) ? View.GONE : View.VISIBLE);
        passwordField.setVisibility(op == 1 ? View.VISIBLE : View.GONE);
        localTagField.setVisibility(localOp >= 4 ? View.VISIBLE : View.GONE);
        memberField.setVisibility(localOp == 5 ? View.VISIBLE : View.GONE);
        urlField.setVisibility(localOp == 6 ? View.VISIBLE : View.GONE); timeoutField.setVisibility(localOp == 6 ? View.VISIBLE : View.GONE);
        String[] remoteHelp = {"Returns usernames configured on the remote server.",
            "Adds a user or replaces an existing user's password. The password is sent only when you confirm.",
            "Removes the user and closes their active connections.",
            "Closes the user's QUIC connections. They can reconnect with the same credentials.",
            "Remote user traffic in bytes and active connection counts. Sent and received are from the server's perspective.",
            "Resets cumulative traffic bytes. Live connection counts are unchanged. Requires a compatible server."};
        help.setText(local ? (localOp == 5 ? "Changes apply to new sessions; existing sessions keep their target. Restarting restores the configuration."
            : localOp == 6 ? "HTTP only. Measures through the named outbound; timeout includes connection and response headers."
            : "Returns the local REST API response as JSON. Outbound tags are case-sensitive.") : remoteHelp[op]);
        run.setText(local ? LOCAL_LABELS[localOp] : REMOTE_LABELS[op]);
        render();
    }

    void render() {
        if (model == null) return;
        profile.setText(model.profileName); profile.setContentDescription("Saved profile: " + model.profileName);
        outbound.setText(model.tag.isEmpty() ? "No server outbound" : ApiToolsModel.identifier(model.tag));
        outbound.setContentDescription("Server outbound: " + ApiToolsModel.identifier(model.tag));
        status.setText(model.status); status.setTextColor(model.error ? error : secondary);
        resultTitle.setText(model.resultTitle); if (!model.result.contentEquals(result.getText())) result.setText(model.result);
        boolean enabled = !model.busy;
        profile.setEnabled(enabled && model.profiles != null); reload.setEnabled(enabled);
        outbound.setEnabled(enabled && model.outbounds.length() > 0);
        remoteOperation.setEnabled(enabled); localOperation.setEnabled(enabled); allUsers.setEnabled(enabled);
        for (EditText input : new EditText[]{username, password, outboundTag, selectedMember, probeUrl, probeTimeout}) input.setEnabled(enabled);
        for (int i = 0; i < mode.getChildCount(); i++) mode.getChildAt(i).setEnabled(enabled);
        boolean local = mode.getCheckedRadioButtonId() == R.id.api_local_mode;
        run.setEnabled(enabled && (local || model.canAdminister()));
        if (!local && !model.busy && !model.tag.isEmpty() && !model.canAdminister() && !model.error) {
            status.setText("This outbound's login is not an admin account. Its username must start with admin.");
        }
    }

    private void chooseProfile() {
        if (model.busy || model.profiles == null) return;
        String[] names = new String[model.profiles.profiles.size()]; int selected = -1;
        for (int i = 0; i < names.length; i++) {
            ConfigProfiles.Profile item = model.profiles.profiles.get(i); names[i] = item.name;
            if (item.id.equals(model.profileId)) selected = i;
        }
        new AlertDialog.Builder(this).setTitle("Saved profile").setSingleChoiceItems(names, selected, (dialog, which) -> {
            String id = model.profiles.profiles.get(which).id; dialog.dismiss(); password.setText(""); model.loadProfile(id);
        }).setNegativeButton("Cancel", null).show();
    }

    private void chooseOutbound() {
        String[] names = new String[model.outbounds.length()]; int selected = -1;
        for (int i = 0; i < names.length; i++) {
            JSONObject item = model.outbounds.optJSONObject(i);
            names[i] = ApiToolsModel.identifier(item.optString("tag")) + " · " + item.optString("protocol") + " · " + ApiToolsModel.identifier(item.optString("username"));
            if (item.optString("tag").equals(model.tag)) selected = i;
        }
        new AlertDialog.Builder(this).setTitle("Server outbound").setSingleChoiceItems(names, selected, (dialog, which) -> {
            model.chooseOutbound(model.outbounds.optJSONObject(which).optString("tag")); password.setText(""); dialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }

    private void execute() {
        if (model.busy) return;
        if (mode.getCheckedRadioButtonId() == R.id.api_local_mode) { executeLocal(); return; }
        int index = remoteOperation.getSelectedItemPosition();
        String operation = REMOTE[index];
        String user = index == 0 || (index >= 4 && allUsers.isChecked()) ? "" : username.getText().toString();
        String pass = index == 1 ? password.getText().toString() : "";
        if (index != 0 && !(index >= 4 && allUsers.isChecked()) && user.isEmpty()) { username.setError("Enter a username or explicitly select All users"); username.requestFocus(); return; }
        if (index == 1 && pass.isEmpty()) { password.setError("Enter a new password"); password.requestFocus(); return; }
        Runnable send = () -> { password.setText(""); model.remote(operation, user, pass); };
        if (index == 0 || index == 4) { send.run(); return; }
        String target = user.isEmpty() ? "all users" : "user " + ApiToolsModel.identifier(user);
        String effects = index == 1 ? "Adds the user or replaces their existing password."
            : index == 2 ? "Deletes the user and closes their active connections."
            : index == 3 ? "Closes their QUIC connections. The user can reconnect."
            : "Clears traffic byte counters for " + target + ". Live connection counts stay unchanged.";
        confirmation = new AlertDialog.Builder(this).setTitle(REMOTE_LABELS[index] + "?")
            .setMessage("Server outbound: " + ApiToolsModel.identifier(model.tag) + "\nSaved profile: " + model.profileName + "\nTarget: " + target + "\n\n" + effects)
            .setNegativeButton("Cancel", (dialog, which) -> password.setText(""))
            .setPositiveButton(index == 1 ? "Add / update" : index == 2 ? "Remove user" : index == 3 ? "Disconnect" : "Clear stats", (dialog, which) -> send.run())
            .setOnCancelListener(dialog -> password.setText("")).create();
        confirmation.setOnDismissListener(dialog -> { password.setText(""); confirmation = null; });
        confirmation.show();
    }

    private void executeLocal() {
        int op = localOperation.getSelectedItemPosition();
        String tag = outboundTag.getText().toString();
        if (op >= 4 && tag.isEmpty()) { outboundTag.setError("Enter the exact outbound tag"); return; }
        String[] paths = {"/outbounds", "/connections", "/traffic", "/memory"};
        String path = op < 4 ? paths[op] : "/outbounds/" + ShadowQuicApi.segment(tag) + (op == 6 ? "/probe" : "/selection");
        String method = op == 5 ? "PUT" : op == 6 ? "POST" : "GET";
        JSONObject body = null; int timeout = 3000;
        String member = selectedMember.getText().toString();
        try {
            if (op == 5) {
                if (member.isEmpty()) { selectedMember.setError("Enter a member outbound tag"); return; }
                body = new JSONObject().put("selected", member);
            } else if (op == 6) {
                String url = probeUrl.getText().toString().trim(); android.net.Uri parsed = android.net.Uri.parse(url);
                if (!"http".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null || parsed.getUserInfo() != null) {
                    probeUrl.setError("Use an absolute HTTP URL without credentials"); return;
                }
                int milliseconds;
                try { milliseconds = Integer.parseInt(probeTimeout.getText().toString()); } catch (NumberFormatException e) { milliseconds = 0; }
                if (milliseconds < 1 || milliseconds > 60000) { probeTimeout.setError("Use 1–60000 milliseconds"); return; }
                body = new JSONObject().put("url", url).put("timeout_ms", milliseconds); timeout = milliseconds + 3000;
            }
        } catch (org.json.JSONException e) { return; }
        JSONObject requestBody = body; int requestTimeout = timeout;
        Runnable send = () -> model.local(method, path, requestBody, requestTimeout);
        if (op == 5) new AlertDialog.Builder(this).setTitle("Change selector?")
            .setMessage("Local outbound: " + ApiToolsModel.identifier(tag) + "\nSelected member: " + ApiToolsModel.identifier(member) + "\n\nNew sessions use this member. Existing sessions keep their target.")
            .setNegativeButton("Cancel", null).setPositiveButton("Change", (dialog, which) -> send.run()).show();
        else send.run();
    }

    @Override public Object onRetainNonConfigurationInstance() { return model; }
    @Override protected void onPause() {
        password.setText("");
        if (confirmation != null) confirmation.dismiss();
        super.onPause();
    }
    @Override protected void onDestroy() { model.detach(); if (!isChangingConfigurations()) model.close(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("local", mode.getCheckedRadioButtonId() == R.id.api_local_mode);
        state.putInt("remoteOperation", remoteOperation.getSelectedItemPosition()); state.putInt("localOperation", localOperation.getSelectedItemPosition());
        state.putString("username", username.getText().toString()); state.putBoolean("allUsers", allUsers.isChecked());
        state.putString("tag", outboundTag.getText().toString()); state.putString("member", selectedMember.getText().toString());
        state.putString("url", probeUrl.getText().toString()); state.putString("timeout", probeTimeout.getText().toString());
        state.putString("profileId", model.profileId); state.putBoolean("busy", model.busy);
        state.putString("remoteTag", model.tag);
        super.onSaveInstanceState(state);
    }

    private LinearLayout field(LinearLayout parent, String label, EditText input) {
        LinearLayout field = column(); add(field, text(label, 13, secondary, false), 0); add(field, input, 6); add(parent, field, 12); return field;
    }
    private void identifierPreview(LinearLayout parent, EditText input, String label) {
        TextView preview = text("", 13, secondary, false); preview.setVisibility(View.GONE); add(parent, preview, 6);
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String value = s.toString(); preview.setVisibility(ApiToolsModel.hasWhitespace(value) ? View.VISIBLE : View.GONE);
                preview.setText(label + ": " + ApiToolsModel.identifier(value));
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }
    private EditText input(String hint, boolean secret) {
        EditText field = new EditText(this); field.setTextSize(14); field.setTextColor(ink); field.setHintTextColor(secondary);
        field.setHint(hint); field.setContentDescription(hint); field.setSingleLine(true); field.setMinHeight(dp(48));
        field.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setSaveEnabled(false); field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        field.setPadding(dp(14), dp(10), dp(14), dp(10)); field.setBackground(fieldShape()); return field;
    }
    private Spinner spinner(String[] labels) {
        Spinner spinner = new Spinner(this); spinner.setMinimumHeight(dp(48));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); return spinner;
    }
    private void picker(Button button) {
        android.graphics.drawable.Drawable chevron = getDrawable(R.drawable.ic_expand_more).mutate();
        chevron.setTint(primary); button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, chevron, null);
        button.setCompoundDrawablePadding(dp(8));
    }
    private Button action(String label, Runnable action, boolean filled) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(13);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(dp(48)); button.setMinimumHeight(dp(48));
        button.setPadding(dp(14), dp(10), dp(14), dp(10)); button.setElevation(0); button.setStateListAnimator(null);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{secondary, filled ? onPrimary : ink}));
        android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{-android.R.attr.state_enabled}, shape(surface, 8)); background.addState(new int[]{}, buttonShape(filled));
        button.setBackgroundTintList(null); button.setBackground(new RippleDrawable(ColorStateList.valueOf(outline), background, null));
        button.setOnClickListener(v -> action.run()); return button;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL); return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = column(); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private GradientDrawable fieldShape() { GradientDrawable background = shape(panel, 12); background.setStroke(dp(1), outline); return background; }
    private GradientDrawable buttonShape(boolean filled) { GradientDrawable background = shape(filled ? primary : panel, 8); if (!filled) background.setStroke(dp(1), outline); return background; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape; }
    private void add(LinearLayout parent, View view, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(view, p); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
