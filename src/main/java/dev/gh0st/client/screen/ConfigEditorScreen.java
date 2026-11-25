package dev.gh0st.client.screen;

import com.mojang.blaze3d.Blaze3D;
import dev.gh0st.client.config.ConfigDocument;
import dev.gh0st.client.config.ConfigDocument.Entry;
import dev.gh0st.client.config.ConfigDocument.Kind;
import dev.gh0st.client.config.ModConfigs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits a mod's config files in game: true/false values become toggles,
 * everything else becomes a text box.
 */
public class ConfigEditorScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int LIST_TOP = 58;
    private static final int ACCENT = 0xFFFF7A2F;
    private static final int TEXT = 0xFFE9ECF2;
    private static final int MUTED = 0xFF8A93A6;
    private static final int GOOD = 0xFF3DDC97;
    private static final int BAD = 0xFFFF4D5E;

    private final Screen parent;
    private final String modName;
    private final List<Path> files;
    private int fileIndex;
    private ConfigDocument doc;
    private int page;

    private final List<Button> toggles = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();
    private Button prev;
    private Button next;
    private Button fileButton;
    private boolean updating;
    private boolean confirmDiscard;
    private String status = "";
    private int statusColor = MUTED;

    public ConfigEditorScreen(Screen parent, String modName, List<Path> files) {
        super(Component.literal(modName + " settings"));
        this.parent = parent;
        this.modName = modName;
        this.files = files;
        this.doc = ConfigDocument.load(files.get(0));
    }

    private int left() {
        return this.width / 2 - 190;
    }

    private int rowsPerPage() {
        return Math.max(1, (this.height - LIST_TOP - 46) / ROW_H);
    }

    @Override
    protected void init() {
        toggles.clear();
        boxes.clear();

        fileButton = this.addRenderableWidget(Button.builder(fileLabel(), b -> switchFile())
                .pos(this.width / 2 - 190, 28).size(380, 20).build());
        fileButton.active = files.size() > 1;

        int valueX = left() + 220;
        for (int i = 0; i < rowsPerPage(); i++) {
            final int row = i;
            int y = LIST_TOP + i * ROW_H;
            Button t = Button.builder(Component.literal(""), b -> toggle(row)).pos(valueX, y).size(160, 20).build();
            toggles.add(this.addRenderableWidget(t));

            EditBox box = new EditBox(this.font, valueX + 1, y + 1, 158, 18, null, Component.literal("Value"));
            box.setMaxLength(4096);
            box.setResponder(v -> {
                if (updating) return;
                Entry e = entryAt(row);
                if (e != null) {
                    e.value = v;
                    confirmDiscard = false;
                }
            });
            boxes.add(this.addRenderableWidget(box));
        }

        int by = this.height - 28;
        prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refresh(); })
                .pos(this.width / 2 - 190, by).size(20, 20).build());
        next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refresh(); })
                .pos(this.width / 2 - 166, by).size(20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
                .pos(this.width / 2 - 140, by).size(100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Open in editor"),
                b -> Blaze3D.openUri(doc.path.toUri())).pos(this.width / 2 - 36, by).size(110, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
                .pos(this.width / 2 + 78, by).size(112, 20).build());

        refresh();
    }

    private Component fileLabel() {
        String rel = ModConfigs.configDir().relativize(doc.path).toString();
        String suffix = files.size() > 1 ? "   (" + (fileIndex + 1) + "/" + files.size() + ", click to switch)" : "";
        return Component.literal("File: " + rel + suffix);
    }

    private void switchFile() {
        if (doc.hasChanges() && !confirmDiscard) {
            setStatus("You have unsaved changes. Click again to switch without saving.", BAD);
            confirmDiscard = true;
            return;
        }
        confirmDiscard = false;
        fileIndex = (fileIndex + 1) % files.size();
        doc = ConfigDocument.load(files.get(fileIndex));
        page = 0;
        fileButton.setMessage(fileLabel());
        setStatus("", MUTED);
        refresh();
    }

    private @Nullable Entry entryAt(int row) {
        int idx = page * rowsPerPage() + row;
        return idx < doc.entries.size() ? doc.entries.get(idx) : null;
    }

    private void toggle(int row) {
        Entry e = entryAt(row);
        if (e == null || e.kind != Kind.BOOLEAN) return;
        boolean on = !Boolean.parseBoolean(e.value.trim());
        // keep the file's style: TRUE/FALSE stays upper case
        boolean upper = e.value.equals(e.value.toUpperCase(Locale.ROOT));
        e.value = upper ? String.valueOf(on).toUpperCase(Locale.ROOT) : String.valueOf(on);
        confirmDiscard = false;
        refresh();
    }

    private void refresh() {
        int pages = Math.max(1, (doc.entries.size() + rowsPerPage() - 1) / rowsPerPage());
        page = Math.max(0, Math.min(page, pages - 1));
        updating = true;
        for (int i = 0; i < toggles.size(); i++) {
            Entry e = entryAt(i);
            Button t = toggles.get(i);
            EditBox box = boxes.get(i);
            boolean isBool = e != null && e.kind == Kind.BOOLEAN;
            t.visible = isBool;
            box.visible = e != null && !isBool;
            if (isBool) {
                boolean on = Boolean.parseBoolean(e.value.trim());
                t.setMessage(Component.literal(on ? "§aON" : "§cOFF"));
            } else if (e != null) {
                box.setValue(e.value);
            }
        }
        updating = false;
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    private void save() {
        try {
            doc.save();
            doc = ConfigDocument.load(doc.path);
            refresh();
            setStatus("Saved! Some mods only pick up changes after a restart.", GOOD);
        } catch (Exception ex) {
            setStatus("Couldn't save: " + ex.getMessage(), BAD);
        }
    }

    private void setStatus(String s, int color) {
        status = s;
        statusColor = color;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, Component.literal(modName.toUpperCase(Locale.ROOT) + " SETTINGS"), this.width / 2, 12, ACCENT);

        int left = left();
        if (doc.error != null) {
            g.centeredText(this.font, Component.literal(doc.error), this.width / 2, LIST_TOP + 10, BAD);
        } else if (doc.entries.isEmpty()) {
            g.centeredText(this.font, Component.literal("No editable settings found. Try \"Open in editor\"."), this.width / 2, LIST_TOP + 10, MUTED);
        }

        for (int i = 0; i < toggles.size(); i++) {
            Entry e = entryAt(i);
            if (e == null) break;
            int y = LIST_TOP + i * ROW_H;
            g.fill(left - 4, y - 2, left + 384, y + 22, (i % 2 == 0) ? 0x50000000 : 0x30000000);
            String label = this.font.plainSubstrByWidth(prettify(e.label), 210);
            g.text(this.font, Component.literal(label).getVisualOrderText(), left, y + 6, e.changed() ? ACCENT : TEXT, true);
        }

        int pages = Math.max(1, (doc.entries.size() + rowsPerPage() - 1) / rowsPerPage());
        g.text(this.font, Component.literal("Page " + (page + 1) + "/" + pages).getVisualOrderText(), left, this.height - 42, MUTED, false);
        if (!status.isEmpty()) {
            g.text(this.font, Component.literal(this.font.plainSubstrByWidth(status, 300)).getVisualOrderText(), left + 70, this.height - 42, statusColor, false);
        }
    }

    /** "render.max_fps" -> "Render > Max fps" */
    private static String prettify(String key) {
        String[] parts = key.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ').trim();
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" > ");
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    @Override
    public void onClose() {
        if (doc.hasChanges() && !confirmDiscard) {
            setStatus("You have unsaved changes. Click Back again to leave without saving.", BAD);
            confirmDiscard = true;
            return;
        }
        this.minecraft.gui.setScreen(this.parent);
    }
}
