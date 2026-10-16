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

final class AppLog {
    private static final long LIMIT = 1024 * 1024;
    static File directory(Context context) {
        File directory = new File(context.getFilesDir(), "logs");
        if (!directory.isDirectory() && !directory.mkdirs()) Log.e("Squirrel", "Cannot create log directory");
        return directory;
    }
    static synchronized void write(Context context, String message) {
        Log.i("Squirrel", message);
        File file = new File(directory(context), "service.log");
        try {
            if (file.length() > LIMIT) Files.move(file.toPath(), new File(file.getPath() + ".1").toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.write(file.toPath(), (Instant.now() + " " + message + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { Log.e("Squirrel", "Cannot write service log", e); }
    }
    static String read(Context context, boolean full) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String name : new String[]{"service.log.1", "service.log", "core.log.1", "core.log"}) {
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
