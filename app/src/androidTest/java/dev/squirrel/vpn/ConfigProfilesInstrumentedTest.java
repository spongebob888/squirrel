package dev.squirrel.vpn;

import android.content.Context;
import android.content.ContextWrapper;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@SuppressWarnings("deprecation")
public final class ConfigProfilesInstrumentedTest extends InstrumentationTestCase {
    private Context context;
    private File root;
    private static final String CONFIG = "# A portable config\noutbounds: [{type: direct, tag: direct}]\n";

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context target = getInstrumentation().getTargetContext();
        root = new File(target.getFilesDir(), "profiles-test-" + System.nanoTime());
        assertTrue(root.mkdirs());
        context = new ContextWrapper(target) {
            @Override public File getFilesDir() { return root; }
        };
    }

    public void testMigratesLegacyConfigWithoutChangingYamlOrBackup() throws Exception {
        Files.write(ConfigStore.file(context).toPath(), CONFIG.getBytes(StandardCharsets.UTF_8));
        ConfigProfiles.State state = ConfigProfiles.list(context);
        assertEquals(1, state.profiles.size());
        assertEquals("Default", state.selected().name);
        assertEquals(CONFIG, ConfigStore.read(context));
        assertEquals(CONFIG, new String(Files.readAllBytes(ConfigStore.file(context).toPath()), StandardCharsets.UTF_8));
        ConfigProfiles.rename(context, state.selectedId, "家庭 / Home");
        assertEquals("家庭 / Home", ConfigProfiles.list(context).selected().name);
        assertEquals(CONFIG, ConfigStore.read(context));
    }

    public void testFreshProfileUsesCnTemplateAndPreservesSavedEdits() throws Exception {
        String template = ConfigStore.asset(context, "example.yaml");
        assertEquals(template, ConfigStore.read(context));
        String runtime = ConfigStore.forRuntime(context, template);
        org.json.JSONObject config = new org.json.JSONObject(runtime);
        org.json.JSONObject vpn = new org.json.JSONObject(NativeBridge.validate(runtime));
        assertEquals("tun-in", config.getJSONArray("inbounds").getJSONObject(1).getString("tag"));
        assertEquals("1.1.1.1", vpn.getJSONArray("dns").getString(0));
        assertEquals(new File(root, "data/geosite.redb").getAbsolutePath(),
            config.getJSONObject("router").getJSONArray("database").getJSONObject(1).getString("path"));
        assertEquals("127.0.0.1:19090", config.getJSONObject("restful-api").getString("bind-addr"));
        ConfigStore.save(context, CONFIG);
        assertEquals(CONFIG, ConfigStore.read(context));
    }

    public void testProfilesSaveSelectRenameAndDeleteIndependently() throws Exception {
        ConfigProfiles.State initial = ConfigProfiles.list(context);
        String first = initial.selectedId;
        ConfigProfiles.save(context, first, CONFIG);
        ConfigProfiles.State second = ConfigProfiles.create(context, "Travel", "# Travel\n" + CONFIG);
        String id = second.selectedId;
        assertEquals("# Travel\n" + CONFIG, ConfigStore.read(context));
        ConfigProfiles.save(context, first, "# Changed home\n" + CONFIG);
        assertEquals(id, ConfigProfiles.list(context).selectedId);
        assertEquals("# Travel\n" + CONFIG, ConfigStore.read(context));
        ConfigProfiles.select(context, first);
        assertEquals("# Changed home\n" + CONFIG, ConfigStore.read(context));
        ConfigStore.save(context, CONFIG);
        assertEquals(CONFIG, ConfigProfiles.read(context, first));
        assertEquals("# Travel\n" + CONFIG, ConfigProfiles.read(context, id));
        ConfigProfiles.select(context, id);
        ConfigProfiles.rename(context, id, "Work");
        assertEquals(id, ConfigProfiles.list(context).selectedId);
        assertEquals("Work", ConfigProfiles.list(context).selected().name);
        ConfigProfiles.State after = ConfigProfiles.delete(context, id);
        assertEquals(first, after.selectedId);
        assertEquals(CONFIG, ConfigStore.read(context));
        assertFalse(new File(root, "configs/" + id + ".yaml").exists());
        assertEquals(1, ConfigProfiles.list(context).profiles.size());
    }

    public void testRejectsInvalidOperationsWithoutChangingSelectionOrData() throws Exception {
        String first = ConfigProfiles.list(context).selectedId;
        ConfigProfiles.save(context, first, CONFIG);
        expectFailure(() -> ConfigProfiles.delete(context, first));
        expectFailure(() -> ConfigProfiles.create(context, "default", CONFIG));
        expectFailure(() -> ConfigProfiles.create(context, "  ", CONFIG));
        expectFailure(() -> ConfigProfiles.create(context, "bad\nname", CONFIG));
        expectFailure(() -> ConfigProfiles.read(context, "../../config"));
        expectFailure(() -> ConfigProfiles.save(context, first, new String(new char[ConfigStore.MAX_BYTES + 1])));
        String second = ConfigProfiles.create(context, "Travel", CONFIG).selectedId;
        ConfigProfiles.select(context, first);
        assertTrue(new File(root, "configs/" + second + ".yaml").delete());
        expectFailure(() -> ConfigProfiles.select(context, second));
        assertEquals(first, ConfigProfiles.list(context).selectedId);
        assertEquals(CONFIG, ConfigStore.read(context));
        ConfigProfiles.delete(context, second);
        assertEquals(1, ConfigProfiles.list(context).profiles.size());
    }

    public void testCorruptIndexDoesNotResetOrOverwriteSavedProfiles() throws Exception {
        ConfigStore.save(context, CONFIG);
        Files.write(new File(root, "profiles.json").toPath(), "broken".getBytes(StandardCharsets.UTF_8));
        expectFailure(() -> ConfigProfiles.list(context));
        assertEquals(CONFIG, new String(Files.readAllBytes(new File(root, "configs/default.yaml").toPath()), StandardCharsets.UTF_8));
    }

    private interface Operation { void run() throws Exception; }
    private void expectFailure(Operation operation) throws Exception {
        try { operation.run(); fail("Invalid profile operation succeeded"); }
        catch (IOException expected) { assertNotNull(expected.getMessage()); }
    }
    @Override protected void tearDown() throws Exception {
        deleteTree(root);
        super.tearDown();
    }
    private void deleteTree(File file) {
        File[] files = file.listFiles();
        if (files != null) for (File child : files) deleteTree(child);
        assertTrue(file.delete());
    }
}
