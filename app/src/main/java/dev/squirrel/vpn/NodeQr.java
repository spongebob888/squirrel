package dev.squirrel.vpn;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.IOException;

/** Draw whole modules at integer pixel sizes, including ZXing's four-module quiet zone. */
final class NodeQr extends View {
    private final BitMatrix modules;
    private final Paint ink = new Paint();

    static BitMatrix encode(String link) throws IOException {
        try { return new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 1, 1); }
        catch (WriterException e) { throw new IOException("This node link is too large for one QR code."); }
    }

    static AlertDialog show(DiagnosticsUi theme, String tag, BitMatrix modules) {
        LinearLayout content = theme.column();
        content.setPadding(theme.dp(20), theme.dp(8), theme.dp(20), 0);
        content.addView(theme.text("Includes the node username and password.", 14, theme.secondary, false));
        NodeQr qr = new NodeQr(theme.context, modules, tag);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = theme.dp(12);
        content.addView(qr, params);
        AlertDialog dialog = new AlertDialog.Builder(theme.context).setTitle(tag).setView(content)
            .setPositiveButton("Close", null).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        return dialog;
    }

    private NodeQr(Context context, BitMatrix modules, String tag) {
        super(context);
        this.modules = modules;
        ink.setColor(Color.BLACK);
        setBackgroundColor(Color.WHITE);
        setContentDescription("ShadowQUIC node QR code for " + tag);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override protected void onMeasure(int width, int height) {
        int w = MeasureSpec.getSize(width);
        int h = resolveSize(w, height);
        setMeasuredDimension(w, Math.min(w, h));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = modules.getWidth();
        int scale = Math.min(getWidth(), getHeight()) / size;
        int left = (getWidth() - size * scale) / 2;
        int top = (getHeight() - size * scale) / 2;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (modules.get(x, y)) canvas.drawRect(left + x * scale, top + y * scale,
                left + (x + 1) * scale, top + (y + 1) * scale, ink);
        }
    }
}
