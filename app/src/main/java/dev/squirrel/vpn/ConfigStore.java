package dev.squirrel.vpn;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class ConfigStore {
    static final int MAX_BYTES = 1024 * 1024;
    static File file(Context context) { return new File(context.getFilesDir(), "config.yaml"); }
    static String forRuntime(Context context, String text) throws IOException {
        return NativeBridge.normalizeDatabasePaths(text, context.getFilesDir().getAbsolutePath());
    }
    static String readForVpn(Context context) throws IOException {
        return forRuntime(context, read(context));
    }
    static synchronized String read(Context context) throws IOException {
        AtomicFile file = new AtomicFile(file(context));
        try (InputStream stream = file.openRead()) { return readText(stream); }
        catch (FileNotFoundException e) {
            if (!file.getBaseFile().exists()) return asset(context, "example.yaml");
            throw e;
        }
    }
    static String asset(Context context, String name) throws IOException {
        try (InputStream stream = context.getAssets().open(name)) { return readText(stream); }
    }
    static String readText(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Unable to open file");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) != -1) {
            if (buffer.size() + count > MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB");
            buffer.write(chunk, 0, count);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(buffer.toByteArray())).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException e) { throw new IOException("Configuration must be UTF-8 text", e); }
    }
    static synchronized void save(Context context, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB");
        AtomicFile file = new AtomicFile(file(context));
        FileOutputStream stream = file.startWrite();
        try { stream.write(bytes); file.finishWrite(stream); }
        catch (IOException e) { file.failWrite(stream); throw e; }
    }
    private ConfigStore() {}
}
