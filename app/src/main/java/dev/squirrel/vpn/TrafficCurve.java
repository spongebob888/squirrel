package dev.squirrel.vpn;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A single direction's measured speed history, with its own scale and tonal fill. */
final class TrafficCurve extends View {
    private final TrafficHistory history;
    private final boolean upload;
    private final int color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path(), fill = new Path();
    private final float stroke;

    TrafficCurve(Context context, TrafficHistory history, boolean upload, int color) {
        super(context);
        this.history = history;
        this.upload = upload;
        this.color = color;
        stroke = 2 * getResources().getDisplayMetrics().density;
        paint.setStrokeWidth(stroke);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private double value(int index) { return upload ? history.uploadAt(index) : history.downloadAt(index); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = history.size();
        if (count == 0) return;
        double peak = 1;
        for (int i = 0; i < count; i++) peak = Math.max(peak, value(i));
        float bottom = getHeight() - stroke;
        float previousX = 0, previousY = 0;
        line.reset();
        for (int i = 0; i < count; i++) {
            float x = count == 1 ? getWidth() - stroke
                : stroke + (getWidth() - 2 * stroke) * i / (count - 1);
            float y = bottom - (float) (value(i) / peak) * Math.max(0, getHeight() - 3 * stroke);
            if (i == 0) line.moveTo(x, y);
            else {
                float middle = (previousX + x) / 2;
                line.cubicTo(middle, previousY, middle, y, x, y);
            }
            previousX = x;
            previousY = y;
        }
        if (count > 1) {
            fill.set(line);
            fill.lineTo(getWidth() - stroke, bottom);
            fill.lineTo(stroke, bottom);
            fill.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((color & 0x00FFFFFF) | 0x18000000);
            canvas.drawPath(fill, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        if (count == 1) canvas.drawPoint(previousX, previousY, paint);
        else canvas.drawPath(line, paint);
    }
}
