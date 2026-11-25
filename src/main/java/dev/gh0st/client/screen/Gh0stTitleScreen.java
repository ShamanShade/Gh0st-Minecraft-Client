package dev.gh0st.client.screen;

import dev.gh0st.client.Gh0stClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** The Gh0st main menu: a side panel of buttons over custom artwork with drifting wisps. */
public class Gh0stTitleScreen extends Screen {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Gh0stClient.MOD_ID, "textures/gui/background.png");
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(Gh0stClient.MOD_ID, "textures/gui/logo.png");

    private static final int PANEL_WIDTH = 240;
    private static final int BUTTON_WIDTH = 180;
    private static final int LOGO_W = 180;
    private static final int LOGO_H = 45; // logo.png is 4:1

    private static final int ACCENT = 0xFFFF7A2F;
    private static final int MUTED = 0xFF8A93A6;

    private final String mcVersion;
    private final int modCount;

    public Gh0stTitleScreen() {
        super(Component.literal("Gh0st"));
        this.mcVersion = FabricLoader.getInstance().getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        this.modCount = dev.gh0st.client.config.ModConfigs.listMods().size();
    }

    private int buttonX() {
        return (PANEL_WIDTH - BUTTON_WIDTH) / 2;
    }

    private int firstButtonY() {
        return this.height / 2 - 30;
    }

    @Override
    protected void init() {
        int x = buttonX();
        int y = firstButtonY();

        this.addRenderableWidget(Button.builder(Component.translatable("menu.singleplayer"),
                b -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))).pos(x, y).size(BUTTON_WIDTH, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.multiplayer"),
                b -> this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this))).pos(x, y).size(BUTTON_WIDTH, 20).build());
        y += 24;
        this.addRenderableWidget(Button.builder(Component.literal("Gh0st Menu"),
                b -> this.minecraft.gui.setScreen(new Gh0stMenuScreen(this))).pos(x, y).size(BUTTON_WIDTH, 20).build());
        y += 32;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.options"),
                b -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))).pos(x, y).size(BUTTON_WIDTH / 2 - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"),
                b -> this.minecraft.stop()).pos(x + BUTTON_WIDTH / 2 + 2, y).size(BUTTON_WIDTH / 2 - 2, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Artwork, stretched to the window
        g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height, this.width, this.height, 0xFFFFFFFF);

        // Drifting ember wisps
        long t = System.currentTimeMillis();
        for (int i = 0; i < 46; i++) {
            int speed = 18 + (i * 7) % 23;
            int travel = this.height + 40;
            int y = this.height + 10 - (int) ((t / speed + i * 131L) % travel);
            int sway = (int) (Math.sin((t / 900.0) + i) * 6);
            int x = (int) ((i * 2654435761L) % Math.max(1, this.width)) + sway;
            int size = 1 + i % 3;
            int alpha = (int) (60 + 150 * Math.max(0, Math.min(1, (double) y / this.height)));
            int color = (alpha << 24) | (i % 2 == 0 ? 0xFFB347 : 0xFF7A2F);
            g.fill(x, y, x + size, y + size, color);
        }

        // Side panel
        g.fill(0, 0, PANEL_WIDTH, this.height, 0xB00B0D12);
        g.fill(PANEL_WIDTH, 0, PANEL_WIDTH + 1, this.height, 0x40FF7A2F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        int logoX = (PANEL_WIDTH - LOGO_W) / 2;
        int logoY = firstButtonY() - LOGO_H - 34;
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0.0F, 0.0F, LOGO_W, LOGO_H, LOGO_W, LOGO_H, 0xFFFFFFFF);
        g.centeredText(this.font, Component.literal("CLIENT"), PANEL_WIDTH / 2, logoY + LOGO_H + 6, ACCENT);

        g.text(this.font, Component.literal("Minecraft " + mcVersion).getVisualOrderText(), 8, this.height - 22, MUTED, true);
        g.text(this.font, Component.literal(modCount + " mods  ·  Right Shift in game for the Gh0st menu").getVisualOrderText(),
                8, this.height - 11, MUTED, true);
    }
}
