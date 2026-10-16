package dev.squirrel.vpn;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Lets long editor lines scroll before handing a horizontal drag to the pager. */
final class EditorScrollHost extends FrameLayout {
    private final int slop;
    private float startX, startY;
    private int maxScrollX;
    private boolean handingOff;

    EditorScrollHost(Context context) {
        super(context);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            startX = event.getX();
            startY = event.getY();
            handingOff = false;
            // Horizontally scrolling EditText uses a very wide layout even
            // for short lines. Measure text rather than canScrollHorizontally.
            TextView editor = (TextView) getChildAt(0);
            float widest = 0;
            if (editor.getLayout() != null) {
                for (int i = 0; i < editor.getLayout().getLineCount(); i++) {
                    widest = Math.max(widest, editor.getLayout().getLineWidth(i));
                }
            }
            int visible = editor.getWidth() - editor.getCompoundPaddingLeft() - editor.getCompoundPaddingRight();
            maxScrollX = Math.max(0, (int) Math.ceil(widest) - visible);
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - startX, dy = event.getY() - startY;
            if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                View editor = getChildAt(0);
                boolean scrollingText = dx > 0 ? editor.getScrollX() > 0 : editor.getScrollX() < maxScrollX;
                handingOff = Math.abs(dx) > Math.abs(dy) && !scrollingText;
                requestDisallowInterceptTouchEvent(!handingOff);
            }
        }
        boolean handled = super.dispatchTouchEvent(event);
        // ViewGroup resets its interception flags while dispatching DOWN.
        // Set this afterwards so a later handoff also reaches the ancestors.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) requestDisallowInterceptTouchEvent(true);
        return handled;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        return handingOff || super.onInterceptTouchEvent(event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        return handingOff || super.onTouchEvent(event);
    }
}
