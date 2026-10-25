package com.ghostvisual;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GhostVisualClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ghostvisual");

	public static KeyBinding openMenuKey;
	public static KeyBinding musicPrevKey;
	public static KeyBinding musicToggleKey;
	public static KeyBinding musicNextKey;

	@Override
	public void onInitializeClient() {
		Config.load();

		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.ghostvisual.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.ghostvisual"));
		musicPrevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.ghostvisual.music_prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.ghostvisual"));
		musicToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.ghostvisual.music_toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.ghostvisual"));
		musicNextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.ghostvisual.music_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.ghostvisual"));

		ClientTickEvents.END_CLIENT_TICK.register(GhostVisualClient::tick);
		HudRenderCallback.EVENT.register(Hud::render);
		WorldRenderEvents.AFTER_TRANSLUCENT.register(WorldFx::render);
		WorldRenderEvents.AFTER_ENTITIES.register(PetManager::render);

		// panel muzyki dziala (klikanie) rowniez przy otwartym czacie
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof ChatScreen) {
				ScreenMouseEvents.allowMouseClick(screen).register(
					(s, mouseX, mouseY, button) -> !(button == 0 && MusicPanel.click(mouseX, mouseY)));
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MediaControl.stop());
		MediaControl.sync(Config.get().musicPanel);

		LOGGER.info("GhostVisual zaladowany (made by shvga)");
	}

	private static void tick(MinecraftClient client) {
		while (openMenuKey.wasPressed()) {
			client.setScreen(new MenuScreen());
		}
		while (musicPrevKey.wasPressed()) {
			MediaControl.send("prev");
		}
		while (musicToggleKey.wasPressed()) {
			MediaControl.send("toggle");
		}
		while (musicNextKey.wasPressed()) {
			MediaControl.send("next");
		}
		Targets.tick(client);
		WorldFx.tick(client);
		PetManager.tick(client);
	}
}
