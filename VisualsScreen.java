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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Menu moda: pasek boczny z zakladkami (Themes / Target / Extras) i tresc po prawej.
 * Wszystko rysowane recznie. Kolory pochodza z wybranego motywu.
 */
public class VisualsScreen extends Screen {
	private static final int W = 420;
	private static final int H = 254;
	private static final int SIDE_W = 104;

	private static final int COL_TEXT = 0xFFEDEDF5;
	private static final int COL_OFF = 0xFFA6A6B8;
	private static final int COL_DIM = 0xFF6F6F86;
	private static final int COL_TRACK = 0xFF2E2E3A;

	private static final String[] TABS = {"Themes", "Target", "Extras", "Sky", "Pets"};
	private static final String[] SUBTITLES = {
			"Theme (accent + panel tint)",
			"Effects around the entity you look at",
			"Extra effects in the world",
			"Sky shader (Overworld)",
			"A pet that follows you"
	};

	/** Ostatnio otwarta zakladka (pamietana miedzy otwarciami menu). */
	private static int currentTab = 0;

	private final VisualConfig cfg = VisualConfig.get();
	private final List<Item> items = new ArrayList<>();
	private Item dragging;
	private int pendingTab = -1;
	private int px, py;   // lewy gorny rog panelu
	private int cx, cw;   // obszar tresci (x i szerokosc)

	public VisualsScreen() {
		super(Text.literal("Visuals"));
	}

	@Override
	protected void init() {
		px = (width - W) / 2;
		py = Math.max(2, (height - H) / 2);
		cx = px + 8 + SIDE_W + 14;
		cw = W - (8 + SIDE_W + 14) - 14;
		rebuild();
	}

	// ------------------------------------------------------------------
	// Budowanie zakladek
	// ------------------------------------------------------------------

	private void rebuild() {
		items.clear();
		dragging = null;

		for (int i = 0; i < TABS.length; i++) {
			items.add(new TabButton(px + 14, py + 58 + i * 24, SIDE_W - 12, 20, i, TABS[i]));
		}

		switch (currentTab) {
			case 0 -> buildThemes();
			case 1 -> buildTarget();
			case 2 -> buildExtras();
			case 3 -> buildSky();
			default -> buildPets();
		}
	}

	private void buildSky() {
		String[] names = {"Off", "Aurora", "Nebula", "Galaxy", "Caustics", "Black hole"};
		int colW = (cw - 8) / 2;
		for (int i = 0; i < names.length; i++) {
			int col = i % 2;
			int row = i / 2;
			final int value = i;
			items.add(new Choice(cx + col * (colW + 8), py + 54 + row * 26, colW, 22, names[i],
					() -> cfg.sky, v -> cfg.sky = v, value));
		}
		items.add(new Slider(cx, py + 138, cw, 24, "Intensity", 0.2, 1.0, 2, 100.0, 0, "%",
				() -> cfg.skyIntensity, v -> cfg.skyIntensity = (float) v));
		items.add(new Slider(cx, py + 166, cw, 24, "Speed", 0.2, 3.0, 1, 1.0, 1, "x",
				() -> cfg.skySpeed, v -> cfg.skySpeed = (float) v));
	}

	private void buildPets() {
		items.add(new Toggle(cx, py + 54, cw, 20, "Pet enabled", () -> cfg.petEnabled, v -> cfg.petEnabled = v));
		String[] names = {"Bat", "Cat", "Chicken", "Dog"};
		int colW = (cw - 8) / 2;
		for (int i = 0; i < names.length; i++) {
			int col = i % 2;
			int row = i / 2;
			final int value = i;
			items.add(new Choice(cx + col * (colW + 8), py + 84 + row * 26, colW, 22, names[i],
					() -> cfg.petType, v -> cfg.petType = v, value));
		}
		items.add(new Slider(cx, py + 144, cw, 24, "Size", 0.6, 2.0, 1, 1.0, 1, "x",
				() -> cfg.petScale, v -> cfg.petScale = (float) v));
	}

	private void buildThemes() {
		int cardW = (cw - 16) / 3;
		int count = Theme.values().length;
		for (int i = 0; i < count; i++) {
			items.add(new ThemeCard(cx + (i % 3) * (cardW + 8), py + 54 + (i / 3) * 40, cardW, 34, i));
		}
		items.add(new Slider(cx, py + 176, cw, 24, "Panel opacity", 0.35, 1.0, 2, 100.0, 0, "%",
				() -> cfg.panelAlpha, v -> cfg.panelAlpha = (float) v));
		items.add(new Toggle(cx, py + 202, cw, 20, "Rounded corners", () -> cfg.rounded, v -> cfg.rounded = v));
		items.add(new Toggle(cx, py + 224, cw, 20, "Animate colors", () -> cfg.animateColors, v -> cfg.animateColors = v));
	}

	private void buildTarget() {
		int colW = (cw - 12) / 2;
		int lx = cx;
		int rx = cx + colW + 12;
		int y = py + 54;
		items.add(new Toggle(lx, y, colW, 20, "Enabled", () -> cfg.targetEnabled, v -> cfg.targetEnabled = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Box", () -> cfg.box, v -> cfg.box = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Ring", () -> cfg.ring, v -> cfg.ring = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Scanner", () -> cfg.scanner, v -> cfg.scanner = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Orbit", () -> cfg.orbit, v -> cfg.orbit = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Ghost (wind)", () -> cfg.ghost, v -> cfg.ghost = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Glow", () -> cfg.glow, v -> cfg.glow = v));

		items.add(new Slider(rx, py + 54, colW, 24, "Range", 4, 64, 0, 1.0, 0, "",
				() -> cfg.range, v -> cfg.range = (float) v));
		items.add(new Slider(rx, py + 82, colW, 24, "Speed", 0.2, 3.0, 1, 1.0, 1, "x",
				() -> cfg.speed, v -> cfg.speed = (float) v));
		items.add(new Segment(rx, py + 114, colW, 20, "All", "Players",
				() -> cfg.playersOnly, v -> cfg.playersOnly = v));
	}

	private void buildExtras() {
		int colW = (cw - 12) / 2;
		int lx = cx;
		int rx = cx + colW + 12;
		int y = py + 54;
		items.add(new Toggle(lx, y, colW, 20, "Jump circle", () -> cfg.jumpCircle, v -> cfg.jumpCircle = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Trails", () -> cfg.trails, v -> cfg.trails = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Blizzard", () -> cfg.blizzard, v -> cfg.blizzard = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Fireflies", () -> cfg.fireflies, v -> cfg.fireflies = v));
		y += 22;
		items.add(new Toggle(lx, y, colW, 20, "Rain", () -> cfg.rain, v -> cfg.rain = v));

		items.add(new Slider(rx, py + 54, colW, 24, "Trail length", 0.5, 3.0, 1, 1.0, 1, "s",
				() -> cfg.trailSeconds, v -> cfg.trailSeconds = (float) v));
		items.add(new Slider(rx, py + 82, colW, 24, "Snow density", 0.3, 2.0, 1, 1.0, 1, "x",
				() -> cfg.blizzardDensity, v -> cfg.blizzardDensity = (float) v));
		items.add(new Slider(rx, py + 110, colW, 24, "Fireflies", 4, 80, 0, 1.0, 0, "",
				() -> cfg.fireflyCount, v -> cfg.fireflyCount = (float) v));
		items.add(new Slider(rx, py + 138, colW, 24, "Rain density", 0.3, 2.0, 1, 1.0, 1, "x",
				() -> cfg.rainDensity, v -> cfg.rainDensity = (float) v));
	}

	// ------------------------------------------------------------------
	// Rysowanie
	// ------------------------------------------------------------------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		if (pendingTab >= 0) {
			currentTab = pendingTab;
			pendingTab = -1;
			rebuild();
		}

		Theme theme = cfg.theme();
		int accent = accent();

		// Przyciemnienie gry w tle
		ctx.fill(0, 0, width, height, 0x88000000);

		// Ramka i panel
		int alphaByte = Math.max(0, Math.min(255, (int) (cfg.panelAlpha * 255f)));
		rrect(ctx, px - 1, py - 1, W + 2, H + 2, (accent & 0xFFFFFF) | 0x88000000, rad(5));
		rrect(ctx, px, py, W, H, (alphaByte << 24) | theme.panel, rad(4));

		// Pasek boczny
		rrect(ctx, px + 8, py + 8, SIDE_W, H - 16, 0x66000000, rad(4));
		rrect(ctx, px + 16, py + 16, 26, 26, accent, rad(5));
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("V"), px + 29, py + 25, 0xFF0B0B10);
		text(ctx, "VISUAL", px + 48, py + 18, COL_TEXT);
		text(ctx, "TARGET v1.1", px + 48, py + 29, COL_DIM);

		String keyName = VisualTargetClient.openMenuKey != null
				? VisualTargetClient.openMenuKey.getBoundKeyLocalizedText().getString()
				: "key";
		text(ctx, "Close: " + keyName, px + 16, py + H - 26, COL_DIM);

		// Naglowek tresci
		text(ctx, TABS[currentTab], cx, py + 16, COL_TEXT);
		ctx.fill(cx, py + 28, cx + 22, py + 30, accent);
		text(ctx, SUBTITLES[currentTab], cx, py + 36, COL_DIM);

		for (Item it : items) {
			it.render(ctx, mouseX, mouseY);
		}
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

	/** Kolor akcentu menu (w gradiencie motywu, plynie w czasie). */
	private int accent() {
		double ph = cfg.animateColors ? (System.currentTimeMillis() % 6000L) / 6000.0 : 0.0;
		return 0xFF000000 | cfg.theme().ui(ph);
	}

	/** Promien zaokraglenia (0 gdy wylaczone zaokraglone rogi). */
	private int rad(int r) {
		return cfg.rounded ? r : 0;
	}

	/** Prostokat z zaokraglonymi rogami (promien r). */
	private static void rrect(DrawContext ctx, int x, int y, int w, int h, int color, int r) {
		if (r <= 0 || w < 2 * r + 1 || h < 2 * r + 1) {
			ctx.fill(x, y, x + w, y + h, color);
			return;
		}
		ctx.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	private void text(DrawContext ctx, String s, int x, int y, int color) {
		ctx.drawTextWithShadow(textRenderer, Text.literal(s), x, y, color);
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

		abstract void render(DrawContext ctx, int mx, int my);

		void click(double mx, double my) {}

		void drag(double mx, double my) {}

		boolean draggable() {
			return false;
		}
	}

	/** Zakladka w pasku bocznym. */
	private class TabButton extends Item {
		private final int index;
		private final String label;

		TabButton(int x, int y, int w, int h, int index, String label) {
			super(x, y, w, h);
			this.index = index;
			this.label = label;
		}

		@Override
		void render(DrawContext ctx, int mx, int my) {
			boolean sel = index == currentTab;
			int accent = accent();
			if (sel) {
				rrect(ctx, x, y, w, h, (accent & 0xFFFFFF) | 0x44000000, rad(4));
				rrect(ctx, x - 4, y + 4, 2, h - 8, accent, rad(1));
			} else if (hit(mx, my)) {
				rrect(ctx, x, y, w, h, 0x22FFFFFF, rad(4));
			}
			text(ctx, label, x + 10, y + (h - 8) / 2, sel ? COL_TEXT : COL_OFF);
		}

		@Override
		void click(double mx, double my) {
			if (index != currentTab) pendingTab = index;
		}
	}

	/** Karta motywu z podgladem gradientu. */
	private class ThemeCard extends Item {
		private final int index;

		ThemeCard(int x, int y, int w, int h, int index) {
			super(x, y, w, h);
			this.index = index;
		}

		@Override
		void render(DrawContext ctx, int mx, int my) {
			Theme th = Theme.byIndex(index);
			boolean sel = cfg.theme == index;
			int accent = accent();

			if (sel) {
				rrect(ctx, x - 1, y - 1, w + 2, h + 2, accent, rad(5));
			}
			rrect(ctx, x, y, w, h, hit(mx, my) ? 0xFF22222E : 0xFF171720, rad(4));

			// gradient w pastylce
			int sx = x + 5;
			int sy = y + 5;
			int sw = w - 10;
			int sh = 10;
			for (int i = 0; i < sw; i++) {
				ctx.fill(sx + i, sy, sx + i + 1, sy + sh, 0xFF000000 | th.uiLinear(i / (double) (sw - 1)));
			}
			text(ctx, th.label, x + 5, y + 20, sel ? COL_TEXT : COL_OFF);
		}

		@Override
		void click(double mx, double my) {
			cfg.theme = index;
			cfg.save();
		}
	}

	/** Przelacznik ON/OFF (pastylka z animacja). */
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
		void render(DrawContext ctx, int mx, int my) {
			boolean on = getter.getAsBoolean();
			if (anim < 0f) anim = on ? 1f : 0f;
			anim += ((on ? 1f : 0f) - anim) * 0.35f;

			if (hit(mx, my)) {
				rrect(ctx, x, y, w, h, 0x18FFFFFF, rad(3));
			}
			text(ctx, label, x + 6, y + (h - 8) / 2, on ? COL_TEXT : COL_OFF);

			int pw = 26;
			int ph = 12;
			int pillX = x + w - pw - 4;
			int pillY = y + (h - ph) / 2;
			int accent = accent();
			int track = 0xFF000000 | Theme.mix(COL_TRACK & 0xFFFFFF, accent & 0xFFFFFF, anim);
			rrect(ctx, pillX, pillY, pw, ph, track, rad(5));
			int knobX = pillX + 2 + Math.round(anim * (pw - 4 - 8));
			rrect(ctx, knobX, pillY + 2, 8, 8, 0xFFFFFFFF, rad(3));
		}

		@Override
		void click(double mx, double my) {
			setter.accept(!getter.getAsBoolean());
			cfg.save();
		}
	}

	/** Suwak liczbowy z podpisem i wartoscia. */
	private class Slider extends Item {
		private final String label;
		private final double min, max;
		private final int decimals;
		private final double dispMul;
		private final int dispDec;
		private final String suffix;
		private final DoubleSupplier getter;
		private final DoubleConsumer setter;

		Slider(int x, int y, int w, int h, String label, double min, double max, int decimals,
			   double dispMul, int dispDec, String suffix, DoubleSupplier getter, DoubleConsumer setter) {
			super(x, y, w, h);
			this.label = label;
			this.min = min;
			this.max = max;
			this.decimals = decimals;
			this.dispMul = dispMul;
			this.dispDec = dispDec;
			this.suffix = suffix;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		void render(DrawContext ctx, int mx, int my) {
			double v = MathHelper.clamp(getter.getAsDouble(), min, max);
			double f = (v - min) / (max - min);
			int accent = accent();

			if (hit(mx, my) || dragging == this) {
				rrect(ctx, x, y, w, h, 0x18FFFFFF, rad(3));
			}
			text(ctx, label, x + 6, y + 3, COL_TEXT);
			String value = String.format(Locale.ROOT, "%." + dispDec + "f", v * dispMul) + suffix;
			text(ctx, value, x + w - 6 - textRenderer.getWidth(value), y + 3, COL_OFF);

			int tx = x + 6;
			int tw = w - 12;
			int ty = y + h - 8;
			rrect(ctx, tx, ty, tw, 4, 0xFF000000 | COL_TRACK, rad(2));
			int filled = (int) Math.round(tw * f);
			if (filled > 0) {
				rrect(ctx, tx, ty, filled, 4, accent, rad(2));
			}
			rrect(ctx, tx + filled - 4, ty - 2, 8, 8, 0xFFFFFFFF, rad(4));
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
		void render(DrawContext ctx, int mx, int my) {
			boolean sec = getter.getAsBoolean();
			int half = w / 2;
			int accent = accent();

			rrect(ctx, x, y, w, h, 0xFF1C1C25, rad(4));
			rrect(ctx, x + (sec ? half : 0) + 1, y + 1, half - 2, h - 2, (accent & 0xFFFFFF) | 0x77000000, rad(3));

			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(first),
					x + half / 2, y + (h - 8) / 2, sec ? COL_OFF : COL_TEXT);
			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(second),
					x + half + half / 2, y + (h - 8) / 2, sec ? COL_TEXT : COL_OFF);
		}

		@Override
		void click(double mx, double my) {
			setter.accept(mx >= x + w / 2);
			cfg.save();
		}
	}

	/** Przycisk wyboru jednej wartosci z listy (np. rodzaj nieba albo peta). */
	private class Choice extends Item {
		private final String label;
		private final IntSupplier getter;
		private final IntConsumer setter;
		private final int value;

		Choice(int x, int y, int w, int h, String label, IntSupplier getter, IntConsumer setter, int value) {
			super(x, y, w, h);
			this.label = label;
			this.getter = getter;
			this.setter = setter;
			this.value = value;
		}

		@Override
		void render(DrawContext ctx, int mx, int my) {
			boolean sel = getter.getAsInt() == value;
			int accent = accent();
			if (sel) {
				rrect(ctx, x - 1, y - 1, w + 2, h + 2, accent, rad(5));
			}
			rrect(ctx, x, y, w, h, sel ? 0xFF1B1B26 : (hit(mx, my) ? 0xFF22222E : 0xFF171720), rad(4));
			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
					x + w / 2, y + (h - 8) / 2, sel ? COL_TEXT : COL_OFF);
		}

		@Override
		void click(double mx, double my) {
			setter.accept(value);
			cfg.save();
		}
	}
}
