package dev.squirrel.vpn;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

final class NodeQrImage {
    private static final int EXPORT_SIZE = 1024;
    private static final int MAX_DECODE_SIDE = 2560;

    static void write(ContentResolver resolver, Uri uri, BitMatrix modules) throws IOException {
        int scale = EXPORT_SIZE / modules.getWidth();
        int size = modules.getWidth() * scale;
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            Paint ink = new Paint();
            ink.setColor(Color.BLACK);
            NodeQr.drawModules(canvas, modules, 0, 0, scale, ink);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, png))
                throw new IOException("Could not encode the QR image.");
        } finally { bitmap.recycle(); }
        try (OutputStream output = resolver.openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("Could not open the destination.");
            png.writeTo(output);
        }
    }

    static String read(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        if (options.outWidth < 1 || options.outHeight < 1) throw new IOException("Choose a readable image file.");
        int longest = Math.max(options.outWidth, options.outHeight);
        options.inSampleSize = 1;
        while ((long) MAX_DECODE_SIDE * options.inSampleSize < longest) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream input = resolver.openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) throw new IOException("Choose a readable image file.");
        LuminanceSource image;
        try { image = luminance(bitmap); }
        finally { bitmap.recycle(); }
        Map<DecodeHintType, ?> hints = Collections.singletonMap(DecodeHintType.TRY_HARDER, true);
        QRCodeReader reader = new QRCodeReader();
        for (LuminanceSource source : new LuminanceSource[]{image, image.invert()}) {
            try { return reader.decode(new BinaryBitmap(new HybridBinarizer(source)), hints).getText(); }
            catch (ReaderException ignored) { /* Also try light modules on a dark background. */ }
        }
        throw new IOException("No readable QR code found. Try cropping the image around the code.");
    }

    private static LuminanceSource luminance(Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        byte[] gray = new byte[width * height];
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int pixel = row[x];
                int brightness = (Color.red(pixel) + 2 * Color.green(pixel) + Color.blue(pixel)) / 4;
                // Treat transparent margins in QR artwork as white paper.
                gray[y * width + x] = (byte) (255 - (255 - brightness) * Color.alpha(pixel) / 255);
            }
        }
        return new PlanarYUVLuminanceSource(gray, width, height, 0, 0, width, height, false);
    }

    private NodeQrImage() {}
}
