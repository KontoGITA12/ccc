package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/** Efekty na ekranie: deszcz, zamiec, swietliki i kolorowe niebo. */
public final class Overlay {
	private Overlay() {
	}

	private static final int N = 320;
	private static final float[] rx = new float[N], ry = new float[N], rs = new float[N];
	private static final float[] sx = new float[N], sy = new float[N], ss = new float[N];
	private static final float[] fx = new float[N], fy = new float[N];
	private static long last = System.nanoTime();

	static {
		Random r = new Random(1337);
		for (int i = 0; i < N; i++) {
			rx[i] = r.nextFloat();
			ry[i] = r.nextFloat();
			rs[i] = 0.8f + r.nextFloat() * 0.8f;
			sx[i] = r.nextFloat();
			sy[i] = r.nextFloat();
			ss[i] = 0.4f + r.nextFloat() * 0.8f;
			fx[i] = r.nextFloat();
			fy[i] = r.nextFloat();
		}
	}

	public static void render(DrawContext c, MinecraftClient mc) {
		Config cfg = Config.get();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1.0e9f);
		last = now;
		if (mc.options.hudHidden) {
			return;
		}
		int w = c.getScaledWindowWidth();
		int h = c.getScaledWindowHeight();
		double t = now / 1.0e9;
		Theme th = cfg.theme();

		if (cfg.rain) {
			int n = (int) (30 + cfg.rainDensity * (N - 30));
			for (int i = 0; i < n; i++) {
				ry[i] += dt * rs[i] * 1.3f;
				if (ry[i] > 1f) {
					ry[i] -= 1f;
					rx[i] = (rx[i] + 0.37f) % 1f;
				}
				int px = (int) (rx[i] * w);
				int py = (int) (ry[i] * h);
				int len = (int) (6 + rs[i] * 6);
				c.fill(px, py, px + 1, py + len, Gfx.argb(0xA8C8FF, 0.32f));
			}
		}

		if (cfg.blizzard) {
			int n = (int) (30 + cfg.blizzardDensity * (N - 30));
			for (int i = 0; i < n; i++) {
				sy[i] += dt * ss[i] * 0.22f;
				if (sy[i] > 1f) {
					sy[i] -= 1f;
					sx[i] = (sx[i] + 0.41f) % 1f;
				}
				int px = (int) (sx[i] * w + Math.sin(t * 1.3 + i) * 14);
				int py = (int) (sy[i] * h);
				int size = ss[i] > 0.9f ? 3 : (ss[i] > 0.6f ? 2 : 1);
				c.fill(px, py, px + size, py + size, Gfx.argb(0xFFFFFF, 0.7f));
			}
		}

		if (cfg.fireflies) {
			int n = (int) (6 + cfg.fireflyCount * 50);
			int col = th.accent(cfg.animateColors);
			for (int i = 0; i < n; i++) {
				int px = (int) ((fx[i] + 0.07 * Math.sin(t * 0.35 + i * 1.7)) * w);
				int py = (int) ((fy[i] + 0.06 * Math.sin(t * 0.28 + i * 2.3)) * h);
				float a = (float) (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * 1.6 + i)));
				c.fill(px - 3, py - 3, px + 4, py + 4, Gfx.argb(col, 0.12f * a));
				c.fill(px - 1, py - 1, px + 2, py + 2, Gfx.argb(col, a));
			}
		}
	}
}
