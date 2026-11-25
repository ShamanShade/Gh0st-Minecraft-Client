package dev.gh0st.client.screen;

import com.mojang.blaze3d.Blaze3D;
import dev.gh0st.client.config.Gh0stConfig;
import dev.gh0st.client.config.ModConfigs;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Right Shift menu: every installed mod with a button to change its settings.
 */
public class Gh0stMenuScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int LIST_TOP = 62;
    private static final int ACCENT = 0xFFFF7A2F;
    private static final int TEXT = 0xFFE9ECF2;
    private static final int MUTED = 0xFF8A93A6;

    private final @Nullable Screen parent;
    private final List<ModContainer> allMods = ModConfigs.listMods();
    private final Map<String, Boolean> ownScreen = new HashMap<>();
    private final Map<String, List<Path>> files = new HashMap<>();

    private List<ModContainer> shown = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();
    private EditBox search;
    private Button prev;
    private Button next;
    private int page;
    private String query = "";

    public Gh0stMenuScreen(@Nullable Screen parent) {
        super(Component.literal("Gh0st Menu"));
        this.parent = parent;
        for (ModContainer m : allMods) {
            String id = m.getMetadata().getId();
            ownScreen.put(id, ModConfigs.hasOwnScreen(id));
            files.put(id, ModConfigs.findFiles(m));
        }
    }

    private int listLeft() {
        return this.width / 2 - 180;
    }

    private int rowsPerPage() {
        return Math.max(1, (this.height - LIST_TOP - 40) / ROW_H);
    }

    @Override
    protected void init() {
        this.search = new EditBox(this.font, this.width / 2 - 180, 32, 360, 20, this.search, Component.literal("Search mods"));
        this.search.setHint(Component.literal("Search your mods…"));
        this.search.setResponder(s -> {
            this.query = s;
            this.page = 0;
            refresh();
        });
        this.addRenderableWidget(this.search);

        rowButtons.clear();
        int x = listLeft() + 250;
        for (int i = 0; i < rowsPerPage(); i++) {
            final int row = i;
            Button b = Button.builder(Component.literal("Settings"), btn -> openConfig(row))
                    .pos(x, LIST_TOP + i * ROW_H).size(110, 20).build();
            rowButtons.add(this.addRenderableWidget(b));
        }

        int by = this.height - 28;
        prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
                .pos(this.width / 2 - 180, by).size(20, 20).build());
        next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
                .pos(this.width / 2 - 156, by).size(20, 20).build());
        this.addRenderableWidget(Button.builder(mainMenuLabel(), b -> {
            Gh0stConfig.customMainMenu = !Gh0stConfig.customMainMenu;
            Gh0stConfig.save();
            b.setMessage(mainMenuLabel());
        }).pos(this.width / 2 - 130, by).size(130, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Config folder"),
                b -> Blaze3D.openUri(ModConfigs.configDir().toUri())).pos(this.width / 2 + 4, by).size(86, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .pos(this.width / 2 + 94, by).size(86, 20).build());

        refresh();
    }

    private static Component mainMenuLabel() {
        return Component.literal("Gh0st main menu: " + (Gh0stConfig.customMainMenu ? "ON" : "OFF"));
    }

    private void refresh() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (ModContainer m : allMods) {
            if (q.isEmpty()
                    || m.getMetadata().getName().toLowerCase(Locale.ROOT).contains(q)
                    || m.getMetadata().getId().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(m);
            }
        }
        int pages = Math.max(1, (shown.size() + rowsPerPage() - 1) / rowsPerPage());
        page = Math.max(0, Math.min(page, pages - 1));

        for (int i = 0; i < rowButtons.size(); i++) {
            Button b = rowButtons.get(i);
            ModContainer m = modAt(i);
            b.visible = m != null;
            if (m == null) continue;
            String id = m.getMetadata().getId();
            if (ownScreen.getOrDefault(id, false)) {
                b.setMessage(Component.literal("Mod settings"));
                b.active = true;
            } else if (!files.getOrDefault(id, List.of()).isEmpty()) {
                b.setMessage(Component.literal("Edit config"));
                b.active = true;
            } else {
                b.setMessage(Component.literal("No settings"));
                b.active = false;
            }
        }
        if (prev != null) prev.active = page > 0;
        if (next != null) next.active = page < pages - 1;
    }

    private @Nullable ModContainer modAt(int row) {
        int idx = page * rowsPerPage() + row;
        return idx < shown.size() ? shown.get(idx) : null;
    }

    private void openConfig(int row) {
        ModContainer m = modAt(row);
        if (m == null) return;
        String id = m.getMetadata().getId();
        Screen target = null;
        if (ownScreen.getOrDefault(id, false)) target = ModConfigs.ownScreen(id, this);
        if (target == null) {
            List<Path> f = files.getOrDefault(id, List.of());
            if (!f.isEmpty()) target = new ConfigEditorScreen(this, m.getMetadata().getName(), f);
        }
        if (target != null) this.minecraft.gui.setScreen(target);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        int left = listLeft();
        g.centeredText(this.font, Component.literal("GH0ST MENU"), this.width / 2, 12, ACCENT);

        for (int i = 0; i < rowButtons.size(); i++) {
            ModContainer m = modAt(i);
            if (m == null) break;
            int y = LIST_TOP + i * ROW_H;
            g.fill(left - 4, y - 2, left + 364, y + 22, (i % 2 == 0) ? 0x50000000 : 0x30000000);
            String name = this.font.plainSubstrByWidth(m.getMetadata().getName(), 240);
            String version = this.font.plainSubstrByWidth(m.getMetadata().getVersion().getFriendlyString(), 240);
            g.text(this.font, Component.literal(name).getVisualOrderText(), left, y + 1, TEXT, true);
            g.text(this.font, Component.literal(version).getVisualOrderText(), left, y + 11, MUTED, false);
        }

        if (shown.isEmpty()) {
            g.centeredText(this.font, Component.literal(allMods.isEmpty() ? "No mods installed" : "No mods match your search"),
                    this.width / 2, LIST_TOP + 10, MUTED);
        }

        int pages = Math.max(1, (shown.size() + rowsPerPage() - 1) / rowsPerPage());
        g.text(this.font, Component.literal((page + 1) + "/" + pages).getVisualOrderText(), this.width / 2 - 180, this.height - 40, MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
