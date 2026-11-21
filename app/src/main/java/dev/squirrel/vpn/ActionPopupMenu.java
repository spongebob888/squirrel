package dev.squirrel.vpn;

import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListPopupWindow;
import android.widget.TextView;
import java.util.ArrayList;

/** Dashboard-themed actions with native popup positioning, scrolling and dismissal. */
final class ActionPopupMenu extends ListPopupWindow {
    private record Item(String label, int icon, Runnable action, boolean destructive, boolean enabled, boolean divider) {}

    private final DiagnosticsUi theme;
    private final String title;
    private final ArrayList<Item> items = new ArrayList<>();
    private boolean nextDivider;

    ActionPopupMenu(DiagnosticsUi theme, View anchor, String title) {
        super(theme.context);
        this.theme = theme;
        this.title = title;
        setAnchorView(anchor);
        setDropDownGravity(Gravity.END);
        setVerticalOffset(theme.dp(6));
        setModal(true);
        setInputMethodMode(INPUT_METHOD_NOT_NEEDED);
        setBackgroundDrawable(theme.context.getDrawable(R.drawable.bg_action_menu));
        setListSelector(new RippleDrawable(ColorStateList.valueOf(theme.outline),
            theme.shape(theme.container, 8), null));
        setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Item getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) { return getItem(position).enabled; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                Item item = getItem(position);
                int tint = !item.enabled ? theme.secondary : item.destructive ? theme.error : theme.ink;
                TextView label = theme.text(item.label, 14, tint, false);
                label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                label.setMinHeight(theme.dp(item.divider ? 61 : 48));
                label.setPadding(theme.dp(12), theme.dp(item.divider ? 25 : 12), theme.dp(12), theme.dp(12));
                Drawable separator = null;
                if (item.divider) {
                    LayerDrawable line = new LayerDrawable(new Drawable[]{new ColorDrawable(theme.outline)});
                    line.setLayerGravity(0, Gravity.TOP | Gravity.FILL_HORIZONTAL);
                    line.setLayerHeight(0, theme.dp(1));
                    line.setLayerInset(0, theme.dp(12), theme.dp(6), theme.dp(12), 0);
                    separator = line;
                }
                label.setBackground(new RippleDrawable(ColorStateList.valueOf(theme.outline),
                    separator, theme.shape(theme.container, 8)));
                Drawable icon = theme.context.getDrawable(item.icon).mutate();
                icon.setTint(item.destructive ? theme.error : theme.secondary);
                icon.setBounds(0, 0, theme.dp(20), theme.dp(20));
                label.setCompoundDrawablesRelative(icon, null, null, null);
                label.setCompoundDrawablePadding(theme.dp(12));
                label.setOnClickListener(view -> activate(position));
                label.setEnabled(item.enabled);
                return label;
            }
        });
        setOnItemClickListener((parent, view, position, id) -> activate(position));
    }

    private void activate(int position) {
        if (!isShowing()) return;
        Item item = items.get(position);
        if (!item.enabled) return;
        dismiss();
        item.action.run();
    }

    ActionPopupMenu add(String label, int icon, Runnable action) {
        return add(label, icon, action, false, true);
    }

    ActionPopupMenu add(String label, int icon, Runnable action, boolean destructive, boolean enabled) {
        items.add(new Item(label, icon, action, destructive, enabled, nextDivider && !items.isEmpty()));
        nextDivider = false;
        return this;
    }

    ActionPopupMenu divider() {
        nextDivider = true;
        return this;
    }

    @Override public void show() {
        Rect frame = new Rect();
        getAnchorView().getWindowVisibleDisplayFrame(frame);
        TextView measure = theme.text("", 14, theme.ink, false);
        int desired = theme.dp(248);
        for (Item item : items) {
            desired = Math.max(desired, (int) Math.ceil(measure.getPaint().measureText(item.label)) + theme.dp(68));
        }
        setWidth(Math.min(Math.min(desired, theme.dp(320)), Math.max(1, frame.width() - theme.dp(32))));
        super.show();
        getListView().setDivider(null);
        if (Build.VERSION.SDK_INT >= 28) getListView().setAccessibilityPaneTitle(title);
    }
}
