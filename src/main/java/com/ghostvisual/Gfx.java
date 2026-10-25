package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Male pomocniki do rysowania 2D (zaokraglone prostokaty, tekst, ikony). */
public final class Gfx {
	private Gfx() {
	}

	public static final Identifier LOGO = Identifier.of("ghostvisual", "textures/gui/logo.png");

	public static TextRenderer tr() {
		return MinecraftClient.getInstance().textRenderer;
	}

	public static int argb(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int lerp(int c1, int c2, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
		int g = Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
		int b = Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
		return (r << 16) | (g << 8) | b;
	}

	private static int inset(int r, int i) {
		double dy = r - i - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	public static void roundRect(DrawContext c, int x, int y, int w, int h, int radius, int color) {
		int r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0) {
			c.fill(x, y, x + w, y + h, color);
			return;
		}
		c.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			c.fill(x + in, y + i, x + w - in, y + i + 1, color);
			c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
		}
	}

	public static void roundBorder(DrawContext c, int x, int y, int w, int h, int radius, int color) {
		int r = Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0) {
			c.fill(x, y, x + w, y + 1, color);
			c.fill(x, y + h - 1, x + w, y + h, color);
			c.fill(x, y + 1, x + 1, y + h - 1, color);
			c.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
			return;
		}
		// boki
		c.fill(x, y + r, x + 1, y + h - r, color);
		c.fill(x + w - 1, y + r, x + w, y + h - r, color);
		// gora i dol
		c.fill(x + r, y, x + w - r, y + 1, color);
		c.fill(x + r, y + h - 1, x + w - r, y + h, color);
		// rogi
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			int next = i + 1 < r ? inset(r, i + 1) : r;
			int len = Math.max(1, next - in);
			c.fill(x + in, y + i, x + in + len, y + i + 1, color);
			c.fill(x + w - in - len, y + i, x + w - in, y + i + 1, color);
			c.fill(x + in, y + h - 1 - i, x + in + len, y + h - i, color);
			c.fill(x + w - in - len, y + h - 1 - i, x + w - in, y + h - i, color);
		}
	}

	public static void text(DrawContext c, String s, int x, int y, int color) {
		c.drawText(tr(), s, x, y, color, false);
	}

	public static void text(DrawContext c, Text t, int x, int y, int color) {
		c.drawText(tr(), t, x, y, color, false);
	}

	public static void textScaled(DrawContext c, Text t, float x, float y, float scale, int color) {
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate(x, y, 0f);
		m.scale(scale, scale, 1f);
		c.drawText(tr(), t, 0, 0, color, false);
		m.pop();
	}

	public static void textScaled(DrawContext c, String s, float x, float y, float scale, int color) {
		textScaled(c, Text.literal(s), x, y, scale, color);
	}

	public static int width(String s) {
		return tr().getWidth(s);
	}

	public static void textCentered(DrawContext c, String s, int cx, int y, int color) {
		c.drawText(tr(), s, cx - width(s) / 2, y, color, false);
	}

	/** Rysuje kwadratowa teksture o rozmiarze texSize jako size x size pikseli. */
	public static void image(DrawContext c, Identifier id, int x, int y, int size, int texSize) {
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate((float) x, (float) y, 0f);
		float s = size / (float) texSize;
		m.scale(s, s, 1f);
		c.drawTexture(RenderLayer::getGuiTextured, id, 0, 0, 0f, 0f, texSize, texSize, texSize, texSize);
		m.pop();
	}

	// ikony odtwarzacza (cx, cy = srodek)
	public static void iconPlay(DrawContext c, int cx, int cy, int h, int color) {
		int w = h * 3 / 4 + 1;
		int x0 = cx - w / 2;
		for (int col = 0; col < w; col++) {
			int len = Math.max(1, Math.round(h * (1f - col / (float) w)));
			int top = cy - len / 2;
			c.fill(x0 + col, top, x0 + col + 1, top + len, color);
		}
	}

	public static void iconPause(DrawContext c, int cx, int cy, int h, int color) {
		int bar = Math.max(2, h / 3);
		c.fill(cx - bar - 1, cy - h / 2, cx - 1, cy - h / 2 + h, color);
		c.fill(cx + 1, cy - h / 2, cx + bar + 1, cy - h / 2 + h, color);
	}

	public static void iconNext(DrawContext c, int cx, int cy, int h, int color) {
		iconPlay(c, cx - 2, cy, h, color);
		c.fill(cx + h / 3, cy - h / 2, cx + h / 3 + 2, cy - h / 2 + h, color);
	}

	public static void iconPrev(DrawContext c, int cx, int cy, int h, int color) {
		int w = h * 3 / 4 + 1;
		int x0 = cx + 2 - w / 2;
		for (int col = 0; col < w; col++) {
			int len = Math.max(1, Math.round(h * (col / (float) w + 0.05f)));
			int top = cy - len / 2;
			c.fill(x0 + col, top, x0 + col + 1, top + len, color);
		}
		c.fill(cx - h / 3 - 2, cy - h / 2, cx - h / 3, cy - h / 2 + h, color);
	}

	public static String time(double seconds) {
		long s = Math.max(0, (long) seconds);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}
}
