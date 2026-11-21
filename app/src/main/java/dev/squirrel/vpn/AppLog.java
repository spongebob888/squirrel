package dev.squirrel.vpn;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.json.JSONException;
import org.json.JSONObject;

final class AppLog {
    private static final long LIMIT = 1024 * 1024;
    static File directory(Context context) {
        File directory = new File(context.getFilesDir(), "logs");
        if (!directory.isDirectory() && !directory.mkdirs()) Log.e("Squirrel", "Cannot create log directory");
        return directory;
    }
    static synchronized void write(Context context, String message) {
        Log.i("Squirrel", message);
        try {
            append(context, "service.log", Instant.now() + " " + message + "\n");
        } catch (IOException e) { Log.e("Squirrel", "Cannot write service log", e); }
    }

    /** Only records received from the core's authenticated REST WebSocket enter this file. */
    static synchronized void writeRest(Context context, String text) throws IOException, JSONException {
        append(context, "rest.log", restLine(new JSONObject(text)) + "\n");
    }

    static String restLine(JSONObject record) throws JSONException {
        if ("api:logs_lagged".equals(record.optString("event"))) {
            return Instant.now() + " WARN REST log stream dropped " + record.getLong("dropped") + " records (consumer lagged).";
        }
        StringBuilder line = new StringBuilder(record.getString("timestamp")).append(' ')
            .append(record.getString("level")).append(' ').append(record.getString("target"));
        if (!record.isNull("event")) line.append(' ').append(record.getString("event"));
        JSONObject fields = record.optJSONObject("fields");
        if (fields != null) {
            JSONObject remaining = new JSONObject(fields.toString());
            Object message = remaining.remove("message");
            if (message != null && message != JSONObject.NULL) line.append(' ').append(message);
            if (remaining.length() > 0) line.append(' ').append(remaining);
        }
        org.json.JSONArray spans = record.optJSONArray("spans");
        if (spans != null && spans.length() > 0) line.append(" spans=").append(spans);
        return line.toString();
    }

    private static void append(Context context, String name, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LIMIT) throw new IOException("Log record exceeds 1 MiB");
        File file = new File(directory(context), name);
        if (file.length() + bytes.length > LIMIT) Files.move(file.toPath(), new File(file.getPath() + ".1").toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(file.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static synchronized String read(Context context, boolean full) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String name : new String[]{"service.log.1", "service.log", "rest.log.1", "rest.log"}) {
            File file = new File(directory(context), name);
            if (!file.isFile()) continue;
            byte[] bytes;
            boolean partial;
            try (RandomAccessFile stream = new RandomAccessFile(file, "r")) {
                long offset = full ? 0 : Math.max(0, stream.length() - 24000);
                partial = offset > 0;
                bytes = new byte[(int) (stream.length() - offset)];
                stream.seek(offset);
                stream.readFully(bytes);
            }
            int start = 0;
            // Tail views discard the first partial line (and partial UTF-8 sequence).
            if (partial) { while (start < bytes.length && bytes[start++] != '\n') {} }
            text.append("— ").append(name).append(" —\n")
                .append(new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8)).append('\n');
        }
        return text.length() == 0 ? "No logs yet. Connect to start a session." : text.toString();
    }
    private AppLog() {}
}
