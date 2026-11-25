package dev.gh0st.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.gh0st.client.Gh0stClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Gh0st's own settings, stored in config/gh0st.json. */
public final class Gh0stConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static boolean customMainMenu = true;

    private Gh0stConfig() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("gh0st.json");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject o = GSON.fromJson(r, JsonObject.class);
            if (o != null && o.has("customMainMenu")) customMainMenu = o.get("customMainMenu").getAsBoolean();
        } catch (Exception e) {
            Gh0stClient.LOGGER.warn("Could not read gh0st.json, using defaults", e);
        }
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("customMainMenu", customMainMenu);
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                GSON.toJson(o, w);
            }
        } catch (Exception e) {
            Gh0stClient.LOGGER.warn("Could not save gh0st.json", e);
        }
    }
}
