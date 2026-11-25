package dev.gh0st.client.config;

import dev.gh0st.client.Gh0stClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds a way to configure each mod:
 *  1. the mod's own settings screen (through Mod Menu, if installed), or
 *  2. its config files in the config folder, edited with Gh0st's editor.
 */
public final class ModConfigs {
    private static final Set<String> HIDDEN = Set.of("minecraft", "java", "fabricloader", "mixinextras", Gh0stClient.MOD_ID);
    private static final Set<String> EDITABLE = Set.of("json", "json5", "toml", "properties", "cfg", "ini", "yml", "yaml", "conf");

    private static Method hasConfigScreen;
    private static Method getConfigScreen;
    private static boolean modMenuChecked;

    private ModConfigs() {}

    /** Mods worth showing: skips Minecraft itself, libraries bundled inside other mods, and Fabric API's sub-modules. */
    public static List<ModContainer> listMods() {
        List<ModContainer> out = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            if (HIDDEN.contains(id)) continue;
            if (mod.getContainingMod().isPresent()) continue;
            if (id.startsWith("fabric-") && !id.equals("fabric-api")) continue;
            out.add(mod);
        }
        out.sort(Comparator.comparing(m -> m.getMetadata().getName().toLowerCase(Locale.ROOT)));
        return out;
    }

    // ---- Mod Menu bridge (reflection, so Mod Menu stays optional) ----

    private static void initModMenu() {
        if (modMenuChecked) return;
        modMenuChecked = true;
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return;
        try {
            Class<?> c = Class.forName("com.terraformersmc.modmenu.ModMenu");
            hasConfigScreen = c.getMethod("hasConfigScreen", String.class);
            getConfigScreen = c.getMethod("getConfigScreen", String.class, Screen.class);
        } catch (Throwable t) {
            Gh0stClient.LOGGER.warn("Mod Menu found but its API couldn't be used", t);
        }
    }

    public static boolean hasOwnScreen(String modId) {
        initModMenu();
        if (hasConfigScreen == null) return false;
        try {
            return (boolean) hasConfigScreen.invoke(null, modId);
        } catch (Throwable t) {
            return false;
        }
    }

    public static Screen ownScreen(String modId, Screen parent) {
        initModMenu();
        if (getConfigScreen == null) return null;
        try {
            return (Screen) getConfigScreen.invoke(null, modId, parent);
        } catch (Throwable t) {
            Gh0stClient.LOGGER.warn("Couldn't open the config screen for {}", modId, t);
            return null;
        }
    }

    // ---- Config files ----

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /** Config files that belong to a mod, matched by mod id or name. */
    public static List<Path> findFiles(ModContainer mod) {
        String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
        String name = mod.getMetadata().getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        List<Path> out = new ArrayList<>();
        Path dir = configDir();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.walk(dir, 3)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                String file = p.getFileName().toString().toLowerCase(Locale.ROOT);
                int dot = file.lastIndexOf('.');
                if (dot < 0 || !EDITABLE.contains(file.substring(dot + 1))) return;
                String base = file.substring(0, dot).replaceAll("[^a-z0-9]", "");
                String rel = dir.relativize(p).toString().toLowerCase(Locale.ROOT);
                String cleanId = id.replaceAll("[^a-z0-9]", "");
                boolean match = base.startsWith(cleanId)
                        || (!name.isEmpty() && name.length() >= 4 && base.startsWith(name))
                        || rel.startsWith(id + "/") || rel.startsWith(id + "\\");
                if (match) out.add(p);
            });
        } catch (Exception e) {
            Gh0stClient.LOGGER.warn("Couldn't scan the config folder", e);
        }
        out.sort(Comparator.comparing(Path::toString));
        return out;
    }
}
