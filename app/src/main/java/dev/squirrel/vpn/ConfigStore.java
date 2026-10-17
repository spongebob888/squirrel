package dev.squirrel.vpn;

import android.content.Context;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

final class ConfigStore {
    static final int MAX_BYTES = 1024 * 1024;
    static File file(Context context) { return new File(context.getFilesDir(), "config.yaml"); }
    static String forRuntime(Context context, String text) throws IOException {
        return NativeBridge.prepareConfig(text, context.getFilesDir().getAbsolutePath(), apiSecret(context));
    }
    private static String apiSecret(Context context) {
        final String key = "local_api_secret";
        android.content.SharedPreferences preferences = context.getSharedPreferences("runtime", Context.MODE_PRIVATE);
        String secret = preferences.getString(key, null);
        if (secret == null) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            preferences.edit().putString(key, secret).apply();
        }
        return secret;
    }
    static String readForVpn(Context context) throws IOException {
        return forRuntime(context, read(context));
    }
    static String read(Context context) throws IOException { return ConfigProfiles.readSelected(context); }
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
    static void save(Context context, String text) throws IOException { ConfigProfiles.saveSelected(context, text); }
    private ConfigStore() {}
}
