package com.visualtarget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Menu "Visuals" - w calosci rysowane recznie (ciemny panel, przelaczniki, jeden suwak koloru).
 * Kolor akcentu menu zmienia sie razem z wybranym kolorem efektow.
 */
public class VisualsScreen extends Screen {
	private static final int W = 340;
	private static final int H = 218;

	private static final int COL_TEXT = 0xFFE8E8F0;
	private static final int COL_OFF = 0xFFA0A0B0;
	private static final int COL_DIM = 0xFF70708A;
	private static final int COL_TRACK = 0xFF34343F;

	private final VisualConfig cfg = VisualConfig.get();
	private final List<Item> items = new ArrayList<>();
	private Item dragging;
	private int px, py;

	public VisualsScreen() {
		super(Text.literal("Visuals"));
	}

	@Override
	protected void init() {
		items.clear();
		dragging = null;

		px = (width - W) / 2;
		py = Math.max(4, (height - H) / 2);

		int lx = px + 14;        // lewa kolumna
		int rx = px + 178;       // prawa kolumna
		int cw = 148;

		// Naglowek: wlacznik calego moda
		items.add(new Toggle(px + W - 14 - 92, py + 7, 92, 14, "Enabled", () -> cfg.enabled, v -> cfg.enabled = v));

		// Lewa kolumna: efekty
		int y = py + 50;
		items.add(new Toggle(lx, y, cw, 20, "Box", () -> cfg.box, v -> cfg.box = v));
		y += 22;
		items.add(new Toggle(lx, y, cw, 20, "Ring", () -> cfg.ring, v -> cfg.ring = v));
		y += 22;
		items.add(new Toggle(lx, y, cw, 20, "Scanner", () -> cfg.scanner, v -> cfg.scanner = v));
		y += 22;
		items.add(new Toggle(lx, y, cw, 20, "Orbit", () -> cfg.orbit, v -> cfg.orbit = v));
		y += 22;
		items.add(new Toggle(lx, y, cw, 20, "Ghost (wind)", () -> cfg.ghost, v -> cfg.ghost = v));

		// Lewa kolumna: opcje
		items.add(new Toggle(lx, py + 178, cw, 20, "Glow", () -> cfg.glow, v -> cfg.glow = v));

		// Prawa kolumna: kolor
		items.add(new ColorBar(rx, py + 54, cw, 14));
		items.add(new Toggle(rx, py + 76, cw, 20, "Rainbow", () -> cfg.rainbow, v -> cfg.rainbow = v));

		// Prawa kolumna: ustawienia
		items.add(new Slider(rx, py + 116, cw, 24, "Range", 4, 64, 0,
				() -> cfg.range, v -> cfg.range = (float) v));
		items.add(new Slider(rx, py + 142, cw, 24, "Speed", 0.2, 3.0, 1,
				() -> cfg.speed, v -> cfg.speed = (float) v));
		items.add(new Segment(rx, py + 172, cw, 20, "All targets", "Players only",
				() -> cfg.playersOnly, v -> cfg.playersOnly = v));
	}

	// ------------------------------------------------------------------
	// Rysowanie
	// ------------------------------------------------------------------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int accent = 0xFF000000 | cfg.displayRgb();

		// Przyciemnienie gry w tle
		ctx.fill(0, 0, width, height, 0x99000000);

		// Ramka w kolorze akcentu + panel
		rect(ctx, px - 1, py - 1, W + 2, H + 2, (accent & 0xFFFFFF) | 0x99000000);
		rect(ctx, px, py, W, H, 0xFF101016);
		ctx.fill(px + 1, py + 1, px + W - 1, py + 28, 0xFF16161E);

		// Naglowek
		ctx.fill(px + 14, py + 9, px + 17, py + 20, accent);
		text(ctx, "VISUAL TARGET", px + 23, py + 10, COL_TEXT);
		ctx.fill(px + 14, py + 28, px + W - 14, py + 29, 0xFF2A2A36);

		// Podpisy sekcji
		text(ctx, "EFFECTS", px + 14, py + 38, COL_DIM);
		text(ctx, "OPTIONS", px + 14, py + 166, COL_DIM);
		text(ctx, "COLOR", px + 178, py + 38, COL_DIM);
		text(ctx, "SETTINGS", px + 178, py + 104, COL_DIM);

		// Elementy
		for (Item it : items) {
			it.render(ctx, mouseX, mouseY, accent);
		}

		// Podpowiedz na dole
		String keyName = VisualTargetClient.openMenuKey != null
				? VisualTargetClient.openMenuKey.getBoundKeyLocalizedText().getString()
				: "key";
		text(ctx, "Press " + keyName + " to close", px + 178, py + 204, COL_DIM);
	}

	// ------------------------------------------------------------------
	// Sterowanie
	// ------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (Item it : items) {
				if (it.hit(mouseX, mouseY)) {
					it.click(mouseX, mouseY);
					if (it.draggable()) {
						dragging = it;
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging != null) {
			dragging.drag(mouseX, mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging != null) {
			dragging = null;
			cfg.save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Ten sam klawisz co otwiera menu, tez je zamyka
		if (VisualTargetClient.openMenuKey != null && VisualTargetClient.openMenuKey.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		cfg.save();
		super.close();
	}

	// ------------------------------------------------------------------
	// Pomocnicy rysowania
	// ------------------------------------------------------------------

	/** Prostokat z lekko zaokraglonymi rogami. */
	private static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x + 1, y, x + w - 1, y + h, color);
		ctx.fill(x, y + 1, x + w, y + h - 1, color);
	}

	private void text(DrawContext ctx, String s, int x, int y, int color) {
		ctx.drawTextWithShadow(textRenderer, Text.literal(s), x, y, color);
	}

	private static int lerpColor(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int r = (int) (ar + (br - ar) * t);
		int g = (int) (ag + (bg - ag) * t);
		int bl = (int) (ab + (bb - ab) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	// ------------------------------------------------------------------
	// Elementy menu
	// ------------------------------------------------------------------

	private abstract class Item {
		final int x, y, w, h;

		Item(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		boolean hit(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		abstract void render(DrawContext ctx, int mx, int my, int accent);

		void click(double mx, double my) {}

		void drag(double mx, double my) {}

		boolean draggable() {
			return false;
		}
	}

	/** Przelacznik ON/OFF (suwak-pastylka z animacja). */
	private class Toggle extends Item {
		private final String label;
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;
		private float anim = -1f;

		Toggle(int x, int y, int w, int h, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
			super(x, y, w, h);
			this.label = label;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		void render(DrawContext ctx, int mx, int my, int accent) {
			boolean on = getter.getAsBoolean();
			if (anim < 0f) anim = on ? 1f : 0f;
			anim += ((on ? 1f : 0f) - anim) * 0.35f;

			if (hit(mx, my)) {
				rect(ctx, x, y, w, h, 0x18FFFFFF);
			}
			text(ctx, label, x + 6, y + (h - 8) / 2, on ? COL_TEXT : COL_OFF);

			int pw = 24;
			int ph = 12;
			int pillX = x + w - pw - 4;
			int pillY = y + (h - ph) / 2;
			rect(ctx, pillX, pillY, pw, ph, lerpColor(COL_TRACK, accent, anim));
			int knobX = pillX + 2 + Math.round(anim * (pw - 4 - 8));
			rect(ctx, knobX, pillY + 2, 8, 8, 0xFFFFFFFF);
		}

		@Override
		void click(double mx, double my) {
			setter.accept(!getter.getAsBoolean());
		}
	}

	/** Zwykly suwak liczbowy (zasieg, predkosc). */
	private class Slider extends Item {
		private final String label;
		private final double min, max;
		private final int decimals;
		private final DoubleSupplier getter;
		private final DoubleConsumer setter;

		Slider(int x, int y, int w, int h, String label, double min, double max, int decimals,
			   DoubleSupplier getter, DoubleConsumer setter) {
			super(x, y, w, h);
			this.label = label;
			this.min = min;
			this.max = max;
			this.decimals = decimals;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		void render(DrawContext ctx, int mx, int my, int accent) {
			double v = MathHelper.clamp(getter.getAsDouble(), min, max);
			double f = (v - min) / (max - min);

			if (hit(mx, my) || dragging == this) {
				rect(ctx, x, y, w, h, 0x18FFFFFF);
			}
			text(ctx, label, x + 6, y + 3, COL_TEXT);
			String value = String.format(Locale.ROOT, "%." + decimals + "f", v);
			text(ctx, value, x + w - 6 - textRenderer.getWidth(value), y + 3, COL_OFF);

			int tx = x + 6;
			int tw = w - 12;
			int ty = y + h - 8;
			rect(ctx, tx, ty, tw, 3, COL_TRACK);
			int filled = (int) Math.round(tw * f);
			if (filled > 0) {
				rect(ctx, tx, ty, filled, 3, accent);
			}
			rect(ctx, tx + filled - 3, ty - 3, 6, 9, 0xFFFFFFFF);
		}

		@Override
		void click(double mx, double my) {
			setFromMouse(mx);
		}

		@Override
		void drag(double mx, double my) {
			setFromMouse(mx);
		}

		@Override
		boolean draggable() {
			return true;
		}

		private void setFromMouse(double mx) {
			double f = MathHelper.clamp((mx - (x + 6)) / (double) (w - 12), 0.0, 1.0);
			double v = min + f * (max - min);
			double scale = Math.pow(10, decimals);
			setter.accept(Math.round(v * scale) / scale);
		}
	}

	/** Jeden suwak koloru: biel na poczatku, potem cala teczka. */
	private class ColorBar extends Item {
		ColorBar(int x, int y, int w, int h) {
			super(x, y, w, h);
		}

		@Override
		boolean hit(double mx, double my) {
			return mx >= x - 3 && mx < x + w + 3 && my >= y - 4 && my < y + h + 4;
		}

		@Override
		void render(DrawContext ctx, int mx, int my, int accent) {
			rect(ctx, x - 1, y - 1, w + 2, h + 2, 0xFF2A2A36);
			for (int i = 0; i < w; i++) {
				float v = i / (float) (w - 1);
				ctx.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | VisualConfig.colorFromPos(v));
			}
			if (cfg.rainbow) {
				// W trybie rainbow suwak jest nieaktywny
				ctx.fill(x, y, x + w, y + h, 0xB0101016);
				return;
			}
			int kx = x + Math.round(cfg.colorPos * (w - 1));
			ctx.fill(kx - 2, y - 3, kx + 3, y + h + 3, 0xFFFFFFFF);
			ctx.fill(kx - 1, y - 2, kx + 2, y + h + 2, 0xFF000000 | VisualConfig.colorFromPos(cfg.colorPos));
		}

		@Override
		void click(double mx, double my) {
			setFromMouse(mx);
		}

		@Override
		void drag(double mx, double my) {
			setFromMouse(mx);
		}

		@Override
		boolean draggable() {
			return true;
		}

		private void setFromMouse(double mx) {
			if (cfg.rainbow) return;
			cfg.colorPos = (float) MathHelper.clamp((mx - x) / (double) (w - 1), 0.0, 1.0);
		}
	}

	/** Wybor jednej z dwoch opcji (np. wszyscy / tylko gracze). */
	private class Segment extends Item {
		private final String first, second;
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;

		Segment(int x, int y, int w, int h, String first, String second,
				BooleanSupplier getter, Consumer<Boolean> setter) {
			super(x, y, w, h);
			this.first = first;
			this.second = second;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		void render(DrawContext ctx, int mx, int my, int accent) {
			boolean sec = getter.getAsBoolean();
			int half = w / 2;

			rect(ctx, x, y, w, h, 0xFF1C1C25);
			int selX = x + (sec ? half : 0) + 1;
			rect(ctx, selX, y + 1, half - 2, h - 2, (accent & 0xFFFFFF) | 0x66000000);

			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(first),
					x + half / 2, y + (h - 8) / 2, sec ? COL_OFF : COL_TEXT);
			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(second),
					x + half + half / 2, y + (h - 8) / 2, sec ? COL_TEXT : COL_OFF);
		}

		@Override
		void click(double mx, double my) {
			setter.accept(mx >= x + w / 2);
		}
	}
}
