package dev.gh0st.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.gh0st.client.config.Gh0stConfig;
import dev.gh0st.client.screen.Gh0stMenuScreen;
import dev.gh0st.client.screen.Gh0stTitleScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gh0st Client: a custom main menu and a Right Shift menu for configuring your mods.
 */
public class Gh0stClient implements ClientModInitializer {
    public static final String MOD_ID = "gh0st";
    public static final Logger LOGGER = LoggerFactory.getLogger("Gh0st");

    public static KeyMapping menuKey;

    @Override
    public void onInitializeClient() {
        Gh0stConfig.load();

        // Rebindable in Options > Controls > Key Binds > Miscellaneous
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.gh0st.menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(Gh0stClient::onEndTick);
        LOGGER.info("Gh0st Client loaded. Press Right Shift in game to open the Gh0st menu.");
    }

    private static void onEndTick(Minecraft client) {
        Screen current = client.gui.screen();

        // Swap the vanilla title screen for ours.
        if (Gh0stConfig.customMainMenu && current instanceof TitleScreen) {
            client.gui.setScreen(new Gh0stTitleScreen());
            return;
        }

        while (menuKey.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new Gh0stMenuScreen(null));
            }
        }
    }
}
