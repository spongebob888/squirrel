package dev.squirrel.vpn;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Serialized profile transactions; YAML and profile metadata are app-private. */
final class ConfigProfiles {
    static final class Profile {
        final String id, name, downloadUrl;
        Profile(String id, String name) { this(id, name, ""); }
        Profile(String id, String name, String downloadUrl) {
            this.id = id; this.name = name; this.downloadUrl = downloadUrl;
        }
    }

    static final class State {
        final List<Profile> profiles;
        final String selectedId;
        State(List<Profile> profiles, String selectedId) {
            this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
            this.selectedId = selectedId;
        }
        Profile selected() { return find(selectedId); }
        Profile find(String id) {
            for (Profile profile : profiles) if (profile.id.equals(id)) return profile;
            return null;
        }
    }

    static synchronized State list(Context context) throws IOException { return load(context); }
    static synchronized String readSelected(Context context) throws IOException {
        State state = load(context);
        return readFile(context, state.selectedId);
    }
    static synchronized String read(Context context, String id) throws IOException {
        require(load(context), id);
        return readFile(context, id);
    }
    static synchronized void saveSelected(Context context, String text) throws IOException {
        save(context, load(context).selectedId, text);
    }
    static synchronized void save(Context context, String id, String text) throws IOException {
        require(load(context), id);
        write(yaml(context, id), text);
    }
    static synchronized void setDownloadUrl(Context context, String id, String url) throws IOException {
        State state = load(context);
        require(state, id);
        List<Profile> profiles = new ArrayList<>();
        for (Profile profile : state.profiles) profiles.add(profile.id.equals(id)
            ? new Profile(id, profile.name, url.trim()) : profile);
        writeIndex(context, new State(profiles, state.selectedId));
    }
    static synchronized State select(Context context, String id) throws IOException {
        State state = load(context);
        require(state, id);
        readFile(context, id); // A missing or unreadable file must not change selection.
        State next = new State(state.profiles, id);
        writeIndex(context, next);
        return next;
    }
    static synchronized State create(Context context, String name, String text) throws IOException {
        return create(context, name, text, "");
    }
    static synchronized State create(Context context, String name, String text, String downloadUrl) throws IOException {
        State state = load(context);
        name = checkedName(state, null, name);
        String id = UUID.randomUUID().toString();
        AtomicFile file = yaml(context, id);
        write(file, text);
        List<Profile> profiles = new ArrayList<>(state.profiles);
        profiles.add(new Profile(id, name, downloadUrl));
        State next = new State(profiles, id);
        try { writeIndex(context, next); }
        catch (IOException error) { file.delete(); throw error; }
        return next;
    }
    static synchronized State rename(Context context, String id, String name) throws IOException {
        State state = load(context);
        require(state, id);
        name = checkedName(state, id, name);
        List<Profile> profiles = new ArrayList<>();
        for (Profile profile : state.profiles) profiles.add(profile.id.equals(id)
            ? new Profile(id, name, profile.downloadUrl) : profile);
        State next = new State(profiles, state.selectedId);
        writeIndex(context, next);
        return next;
    }
    static synchronized State delete(Context context, String id) throws IOException {
        State state = load(context);
        require(state, id);
        if (state.profiles.size() == 1) throw new IOException("Keep at least one configuration");
        List<Profile> profiles = new ArrayList<>();
        for (Profile profile : state.profiles) if (!profile.id.equals(id)) profiles.add(profile);
        String selected = state.selectedId.equals(id) ? profiles.get(0).id : state.selectedId;
        readFile(context, selected);
        State next = new State(profiles, selected);
        // Commit the index first: interrupted deletion can only leave an unused file.
        writeIndex(context, next);
        yaml(context, id).delete();
        return next;
    }

    private static State load(Context context) throws IOException {
        AtomicFile index = index(context);
        String text;
        try (InputStream input = index.openRead()) { text = ConfigStore.readText(input); }
        catch (java.io.FileNotFoundException error) {
            if (index.getBaseFile().exists()) throw error;
            String legacy;
            AtomicFile old = new AtomicFile(ConfigStore.file(context));
            try (InputStream input = old.openRead()) { legacy = ConfigStore.readText(input); }
            catch (java.io.FileNotFoundException missing) {
                if (old.getBaseFile().exists()) throw missing;
                legacy = ConfigStore.asset(context, "example.yaml");
            }
            write(yaml(context, "default"), legacy);
            State initial = new State(Collections.singletonList(new Profile("default", "Default")), "default");
            writeIndex(context, initial);
            return initial;
        }
        try {
            JSONObject json = new JSONObject(text);
            if (json.getInt("version") != 1) throw new IOException("Unsupported configuration profile version");
            JSONArray entries = json.getJSONArray("profiles");
            List<Profile> profiles = new ArrayList<>();
            HashSet<String> ids = new HashSet<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                String id = entry.getString("id");
                if (!validId(id) || !ids.add(id)) throw new IOException("Invalid configuration profile ID");
                String name = checkedName(new State(profiles, ""), null, entry.getString("name"));
                profiles.add(new Profile(id, name, entry.optString("downloadUrl", "")));
            }
            State state = new State(profiles, json.getString("selected"));
            require(state, state.selectedId);
            return state;
        } catch (JSONException error) { throw new IOException("Cannot read configuration profiles", error); }
    }
    private static String checkedName(State state, String id, String name) throws IOException {
        name = name.trim();
        if (name.isEmpty() || name.length() > 64) throw new IOException("Use a configuration name of 1–64 characters");
        for (int i = 0; i < name.length(); i++) if (Character.isISOControl(name.charAt(i))) {
            throw new IOException("Configuration names must be a single line");
        }
        for (Profile profile : state.profiles) if (!profile.id.equals(id) && profile.name.equalsIgnoreCase(name)) {
            throw new IOException("A configuration with that name already exists");
        }
        return name;
    }
    private static void require(State state, String id) throws IOException {
        if (state.find(id) == null) throw new IOException("Configuration no longer exists");
    }
    private static boolean validId(String id) {
        return id.equals("default") || id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    private static AtomicFile index(Context context) { return new AtomicFile(new File(context.getFilesDir(), "profiles.json")); }
    private static AtomicFile yaml(Context context, String id) throws IOException {
        if (!validId(id)) throw new IOException("Invalid configuration profile ID");
        File directory = new File(context.getFilesDir(), "configs");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create configuration directory");
        return new AtomicFile(new File(directory, id + ".yaml"));
    }
    private static String readFile(Context context, String id) throws IOException {
        try (InputStream input = yaml(context, id).openRead()) { return ConfigStore.readText(input); }
    }
    private static void writeIndex(Context context, State state) throws IOException {
        try {
            JSONArray profiles = new JSONArray();
            for (Profile profile : state.profiles) profiles.put(new JSONObject().put("id", profile.id)
                .put("name", profile.name).put("downloadUrl", profile.downloadUrl));
            write(index(context), new JSONObject().put("version", 1).put("selected", state.selectedId)
                .put("profiles", profiles).toString());
        } catch (JSONException error) { throw new IOException("Cannot save configuration profiles", error); }
    }
    private static void write(AtomicFile file, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ConfigStore.MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB");
        FileOutputStream output = file.startWrite();
        try { output.write(bytes); file.finishWrite(output); }
        catch (IOException error) { file.failWrite(output); throw error; }
    }
    private ConfigProfiles() {}
}
