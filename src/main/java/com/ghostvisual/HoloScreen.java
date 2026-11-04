package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Ekran z wolnym kursorem, przez ktory sterujesz panelem 3D (sam panel jest rysowany w swiecie). */
public class HoloScreen extends Screen {
	public HoloScreen() {
		super(Text.literal("GhostVisual"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		Holo.pick(MinecraftClient.getInstance(), mx, my);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (MusicPanel.click(mx, my)) {
			return true;
		}
		if (button == 0) {
			Holo.pick(MinecraftClient.getInstance(), mx, my);
			Holo.pressed = true;
			Holo.down = true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		Holo.down = false;
		Holo.dragId = 0;
		Config.get().save();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		Holo.scroll += (float) vertical;
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (GhostVisualClient.openMenuKey.matchesKey(keyCode, scanCode)) {
			Holo.close(mc);
			return true;
		}
		if (GhostVisualClient.lockKey.matchesKey(keyCode, scanCode)) {
			Holo.pin(mc);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** ESC zamyka panel calkowicie. */
	@Override
	public void close() {
		Holo.close(MinecraftClient.getInstance());
	}

	@Override
	public void removed() {
		Holo.interactive = false;
		Holo.down = false;
		Holo.dragId = 0;
		Config.get().save();
	}
}
