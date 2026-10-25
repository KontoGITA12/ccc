package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.LivingEntity;

public final class Hud {
	private Hud() {
	}

	public static void render(DrawContext c, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}
		Config cfg = Config.get();
		Overlay.render(c, mc);
		if (!mc.options.hudHidden) {
			targetPanel(c, mc, cfg, tick.getTickDelta(false));
			MusicPanel.render(c, mc);
		}
	}

	private static void targetPanel(DrawContext c, MinecraftClient mc, Config cfg, float td) {
		LivingEntity e = Targets.shown;
		float f = Targets.fade(td);
		if (!cfg.targetEnabled || !cfg.targetPanel || e == null || f < 0.05f) {
			return;
		}
		Theme th = cfg.theme();
		int accent = th.accent(cfg.animateColors);
		String name = e.getName().getString();
		int w = Math.max(90, Gfx.width(name) + 20);
		int h = 26;
		int x = c.getScaledWindowWidth() / 2 - w / 2;
		int y = c.getScaledWindowHeight() / 2 + 26;
		int rad = cfg.rounded ? 5 : 0;
		Gfx.roundRect(c, x, y, w, h, rad, Gfx.argb(th.bg, cfg.panelAlpha * f));
		Gfx.roundBorder(c, x, y, w, h, rad, Gfx.argb(accent, 0.7f * f));
		Gfx.textCentered(c, name, x + w / 2, y + 4, Gfx.argb(0xFFFFFF, f));
		float hp = Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
		c.fill(x + 8, y + 17, x + w - 8, y + 20, Gfx.argb(0xFFFFFF, 0.15f * f));
		c.fill(x + 8, y + 17, x + 8 + Math.round((w - 16) * hp), y + 20, Gfx.argb(accent, f));
	}
}
