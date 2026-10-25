package com.visualtarget;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punkt startowy moda (tylko klient).
 * Rejestruje klawisz otwierajacy menu i renderery efektow.
 */
public class VisualTargetClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("visualtarget");

	/** Klawisz menu. Domyslnie Prawy Shift - da sie zmienic w Opcje > Sterowanie. */
	public static KeyBinding openMenuKey;

	@Override
	public void onInitializeClient() {
		VisualConfig.load();

		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.visualtarget.open_menu",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"category.visualtarget"
		));

		// Co tick sprawdzamy, czy klawisz zostal nacisniety
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new VisualsScreen());
				}
			}
		});

		SkyRenderer.register();
		TargetRenderer.register();
		ExtrasRenderer.register();
		RainRenderer.register();
		PetRenderer.register();
		LOGGER.info("Visual Target zaladowany");
	}
}
