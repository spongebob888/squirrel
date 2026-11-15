package dev.squirrel.vpn;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.function.Consumer;

/** A secure, bounded profile chooser shared by Connection and Configuration. */
final class ProfilePickerDialog extends Dialog {
    final ListView list;
    private final Activity owner;
    private final DiagnosticsUi theme;
    private final LinearLayout content;
    private final boolean bottomSheet;
    private final int selected;
    private boolean closing;

    ProfilePickerDialog(Activity owner, DiagnosticsUi theme, ConfigProfiles.State profiles,
            String selectedId, boolean connected, Consumer<String> select) {
        super(theme.context);
        this.owner = owner;
        this.theme = theme;
        setOwnerActivity(owner);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCanceledOnTouchOutside(true);
        bottomSheet = owner.getResources().getConfiguration().screenWidthDp < 600;
        int current = 0;
        for (int i = 0; i < profiles.profiles.size(); i++) {
            if (profiles.profiles.get(i).id.equals(selectedId)) current = i;
        }
        selected = current;
        content = theme.column();
        content.setId(R.id.profile_picker);
        content.setPadding(theme.dp(20), theme.dp(20), theme.dp(20), theme.dp(20));
        content.setBackground(theme.shape(theme.panel, 24));
        content.setClipToOutline(true);
        LinearLayout header = theme.row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = theme.column();
        TextView title = theme.text("Configurations", 20, theme.ink, true);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        identity.addView(title);
        TextView count = theme.text(profiles.profiles.size() + (profiles.profiles.size() == 1
            ? " saved profile" : " saved profiles"), 13, theme.secondary, false);
        identity.addView(count);
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton close = theme.iconAction("Close profile picker", () -> exit(null));
        close.setId(R.id.profile_picker_close);
        close.setImageResource(R.drawable.ic_close);
        header.addView(close, new LinearLayout.LayoutParams(theme.dp(48), theme.dp(48)));
        content.addView(header);

        int heightDp = owner.getResources().getConfiguration().screenHeightDp;
        int maxListHeight = theme.dp(Math.max(96, Math.min(Math.round(heightDp * 0.55f), heightDp - 200)));
        list = new ListView(theme.context) {
            @Override protected void onMeasure(int width, int height) {
                int mode = View.MeasureSpec.getMode(height);
                int available = mode == View.MeasureSpec.UNSPECIFIED ? maxListHeight
                    : Math.min(maxListHeight, View.MeasureSpec.getSize(height));
                // Measure at the final row width so wrapped names contribute their full height.
                int rowWidth = View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(width)
                    - getPaddingLeft() - getPaddingRight(), View.MeasureSpec.EXACTLY);
                int desired = getPaddingTop() + getPaddingBottom();
                for (int i = 0; i < getAdapter().getCount() && desired < available; i++) {
                    View row = getAdapter().getView(i, null, this);
                    row.setLayoutDirection(getLayoutDirection());
                    row.measure(rowWidth, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    desired += row.getMeasuredHeight() + (i == 0 ? 0 : getDividerHeight());
                }
                super.onMeasure(width, mode == View.MeasureSpec.EXACTLY ? height
                    : View.MeasureSpec.makeMeasureSpec(Math.min(desired, available), View.MeasureSpec.EXACTLY));
            }
        };
        list.setId(R.id.profile_picker_list);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(theme.dp(8));
        list.setDrawSelectorOnTop(true);
        list.setSelector(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(theme.outline), null, theme.shape(theme.container, 12)));
        list.setAdapter(new ArrayAdapter<ConfigProfiles.Profile>(theme.context, 0, profiles.profiles) {
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                CheckedTextView row = recycled instanceof CheckedTextView
                    ? (CheckedTextView) recycled : createRow();
                row.animate().cancel();
                row.setAlpha(1);
                row.setTranslationY(0);
                row.setText(getItem(position).name);
                row.setChecked(list.isItemChecked(position));
                return row;
            }
        });
        list.setItemChecked(selected, true);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (closing) return;
            list.setItemChecked(position, true);
            exit(() -> select.accept(profiles.profiles.get(position).id));
        });
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = theme.dp(16);
        content.addView(list, listParams);
        if (connected) {
            TextView hint = theme.text("Changes apply after reconnecting.", 12, theme.secondary, false);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
            hintParams.topMargin = theme.dp(12);
            content.addView(hint, hintParams);
        }
        setContentView(content);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.32f);
        window.setWindowAnimations(0);
        setOnDismissListener(dialog -> {
            closing = true;
            content.animate().cancel();
            for (int i = 0; i < list.getChildCount(); i++) list.getChildAt(i).animate().cancel();
        });
    }

    private CheckedTextView createRow() {
        CheckedTextView row = new CheckedTextView(theme.context);
        row.setTextSize(16);
        row.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.setTextColor(theme.ink);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(theme.dp(12), theme.dp(12), theme.dp(12), theme.dp(12));
        GradientDrawable selectedBackground = theme.shape(theme.container, 12);
        selectedBackground.setStroke(theme.dp(1), theme.primary);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_checked}, selectedBackground);
        background.addState(new int[]{}, theme.fieldBackground());
        row.setBackground(background);
        Drawable icon = theme.context.getDrawable(R.drawable.ic_configuration).mutate();
        icon.setTint(theme.primary);
        LayerDrawable badge = new LayerDrawable(new Drawable[]{theme.shape(theme.container, 12), icon});
        badge.setLayerInset(1, theme.dp(10), theme.dp(10), theme.dp(10), theme.dp(10));
        badge.setBounds(0, 0, theme.dp(40), theme.dp(40));
        row.setCompoundDrawablesRelative(badge, null, null, null);
        row.setCompoundDrawablePadding(theme.dp(12));
        Drawable checked = theme.context.getDrawable(R.drawable.ic_check_circle).mutate();
        checked.setTint(theme.primary);
        Drawable unchecked = theme.context.getDrawable(R.drawable.ic_circle).mutate();
        unchecked.setTint(theme.secondary);
        StateListDrawable mark = new StateListDrawable();
        mark.addState(new int[]{android.R.attr.state_checked}, checked);
        mark.addState(new int[]{}, unchecked);
        row.setCheckMarkDrawable(mark);
        row.setMinHeight(theme.dp(72));
        return row;
    }

    @Override public void show() {
        super.show();
        int widthDp = owner.getResources().getConfiguration().screenWidthDp;
        Window window = getWindow();
        window.setLayout(theme.dp(Math.min(480, widthDp - 32)), WindowManager.LayoutParams.WRAP_CONTENT);
        window.setGravity(bottomSheet ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        if (bottomSheet) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = theme.dp(16);
            window.setAttributes(params);
        }
        list.post(() -> {
            if (isShowing() && !closing && selected > list.getLastVisiblePosition()) {
                list.setSelection(selected);
            }
        });
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        content.setAlpha(0.7f);
        if (bottomSheet) content.setTranslationY(theme.dp(24));
        else { content.setScaleX(0.98f); content.setScaleY(0.98f); }
        content.animate().alpha(1).translationY(0).scaleX(1).scaleY(1)
            .setDuration(220).setInterpolator(new DecelerateInterpolator(1.5f)).start();
        list.post(() -> {
            if (!isShowing() || closing) return;
            for (int i = 0; i < Math.min(4, list.getChildCount()); i++) {
                View row = list.getChildAt(i);
                row.setAlpha(0.7f);
                row.setTranslationY(theme.dp(8));
                row.animate().alpha(1).translationY(0).setStartDelay(i * 20L).setDuration(160)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();
            }
        });
    }

    private void exit(Runnable selectedAction) {
        if (closing) return;
        closing = true;
        list.setEnabled(false);
        Runnable finish = () -> {
            if (!isShowing()) return;
            dismiss();
            if (selectedAction != null && !owner.isFinishing() && !owner.isDestroyed()) selectedAction.run();
        };
        if (!ValueAnimator.areAnimatorsEnabled()) { finish.run(); return; }
        content.animate().alpha(0).translationY(bottomSheet ? theme.dp(12) : 0)
            .setDuration(120).setStartDelay(0).withEndAction(finish).start();
    }
}
