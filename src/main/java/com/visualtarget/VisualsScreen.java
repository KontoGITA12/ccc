package com.visualtarget;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Menu "Visuals" - otwierane klawiszem. Lewa kolumna: przelaczniki, prawa: suwaki i kolor.
 */
public class VisualsScreen extends Screen {
	private static final int COL_W = 150;
	private static final int GAP = 10;
	private static final int ROW = 24;

	private final VisualConfig cfg = VisualConfig.get();

	private int panelX, panelY, panelW, panelH;
	private int previewX, previewY;

	public VisualsScreen() {
		super(Text.literal("Visuals"));
	}

	@Override
	protected void init() {
		int totalW = COL_W * 2 + GAP;
		int left = (width - totalW) / 2;
		int right = left + COL_W + GAP;
		int rows = 7;
		int top = Math.max(40, (height - (rows * ROW + 60)) / 2 + 30);
		int doneY = top + rows * ROW + 4;

		panelX = left - 12;
		panelY = top - 34;
		panelW = totalW + 24;
		panelH = (doneY + 20 + 10) - panelY;

		// ---- Lewa kolumna: przelaczniki ----
		int y = top;
		addDrawableChild(toggle(left, y, "Enabled", () -> cfg.enabled, v -> cfg.enabled = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "Box", () -> cfg.box, v -> cfg.box = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "Ring", () -> cfg.ring, v -> cfg.ring = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "Scanner", () -> cfg.scanner, v -> cfg.scanner = v));
		y += ROW;
		addDrawableChild(toggle(left, y, "Orbit", () -> cfg.orbit, v -> cfg.orbit = v));
		y += ROW;
		addDrawableChild(ButtonWidget.builder(targetsLabel(), b -> {
			cfg.playersOnly = !cfg.playersOnly;
			b.setMessage(targetsLabel());
		}).dimensions(left, y, COL_W, 20).build());
		y += ROW;
		addDrawableChild(toggle(left, y, "Rainbow", () -> cfg.rainbow, v -> cfg.rainbow = v));

		// ---- Prawa kolumna: suwaki ----
		int ry = top;
		addDrawableChild(new ValueSlider(right, ry, COL_W, 20, "Red", 0, 255, cfg.red, 0,
				v -> cfg.red = (int) Math.round(v)));
		ry += ROW;
		addDrawableChild(new ValueSlider(right, ry, COL_W, 20, "Green", 0, 255, cfg.green, 0,
				v -> cfg.green = (int) Math.round(v)));
		ry += ROW;
		addDrawableChild(new ValueSlider(right, ry, COL_W, 20, "Blue", 0, 255, cfg.blue, 0,
				v -> cfg.blue = (int) Math.round(v)));
		ry += ROW;
		addDrawableChild(new ValueSlider(right, ry, COL_W, 20, "Range", 4, 64, cfg.range, 0,
				v -> cfg.range = (float) Math.round(v)));
		ry += ROW;
		addDrawableChild(new ValueSlider(right, ry, COL_W, 20, "Speed", 0.2, 3.0, cfg.speed, 1,
				v -> cfg.speed = (float) (Math.round(v * 10.0) / 10.0)));
		ry += ROW;

		// Podglad koloru (rysowany w render())
		previewX = right;
		previewY = ry;

		// ---- Przycisk Done ----
		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
				.dimensions(width / 2 - 75, doneY, 150, 20).build());
	}

	// --- Rysowanie ---

	@Override
	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Wlasne tlo (bez rozmycia), zeby nie wywolac blura dwa razy w klatce
		ctx.fill(0, 0, width, height, 0x90000000);
		ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101018);
		ctx.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF000000 | previewColor());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Visuals"), width / 2, panelY + 12, 0xFFFFFFFF);

		// Podglad koloru
		ctx.fill(previewX - 1, previewY - 1, previewX + COL_W + 1, previewY + 21, 0xFFFFFFFF);
		ctx.fill(previewX, previewY, previewX + COL_W, previewY + 20, 0xFF000000 | previewColor());
	}

	private int previewColor() {
		if (cfg.rainbow) {
			float hue = (System.currentTimeMillis() % 4000L) / 4000f;
			return MathHelper.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
		}
		return (cfg.red << 16) | (cfg.green << 8) | cfg.blue;
	}

	// --- Sterowanie ---

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

	// --- Pomocnicy ---

	private static Text label(String name, boolean on) {
		return Text.literal(name + ": ")
				.append(Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED));
	}

	private Text targetsLabel() {
		return Text.literal("Targets: ")
				.append(Text.literal(cfg.playersOnly ? "Players" : "All").formatted(Formatting.YELLOW));
	}

	private static ButtonWidget toggle(int x, int y, String name, BooleanSupplier getter, Consumer<Boolean> setter) {
		return ButtonWidget.builder(label(name, getter.getAsBoolean()), b -> {
			setter.accept(!getter.getAsBoolean());
			b.setMessage(label(name, getter.getAsBoolean()));
		}).dimensions(x, y, COL_W, 20).build();
	}

	/** Suwak z nazwa i wartoscia na przycisku. */
	private static class ValueSlider extends SliderWidget {
		private final String name;
		private final double min;
		private final double max;
		private final int decimals;
		private final DoubleConsumer onChange;

		ValueSlider(int x, int y, int w, int h, String name, double min, double max, double current,
					int decimals, DoubleConsumer onChange) {
			super(x, y, w, h, Text.empty(), (current - min) / (max - min));
			this.name = name;
			this.min = min;
			this.max = max;
			this.decimals = decimals;
			this.onChange = onChange;
			updateMessage();
		}

		private double real() {
			return min + value * (max - min);
		}

		@Override
		protected void updateMessage() {
			if (name == null) return;
			setMessage(Text.literal(name + ": " + String.format(Locale.ROOT, "%." + decimals + "f", real())));
		}

		@Override
		protected void applyValue() {
			onChange.accept(real());
		}
	}
}
