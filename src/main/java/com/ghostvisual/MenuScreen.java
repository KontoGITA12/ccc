package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Menu GhostVisual: pasek boczny z zakladkami i zawartosc po prawej. */
public class MenuScreen extends Screen {
	public static final String VERSION = "1.1.0";
	private static final String[] TABS = {"Cel", "Efekty", "Pety", "China Hat", "Muzyka", "Motywy", "Ustawienia", "Profil"};
	private static final int SIDE = 130;
	private static int lastTab = 0;

	private int tab = lastTab;
	private int scroll = 0;
	private int px, py, pw, ph;
	private int cx, cy, cw, ch;
	private final List<Row> rows = new ArrayList<>();
	private int totalH = 0;
	private Row dragging = null;

	public MenuScreen() {
		super(Text.literal("GhostVisual"));
	}

	@Override
	protected void init() {
		pw = Math.min(width - 24, 620);
		ph = Math.min(height - 20, 370);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		cx = px + SIDE + 16;
		cy = py + 42;
		cw = pw - SIDE - 32;
		ch = ph - 42 - 12;
		buildRows();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		Config.get().save();
	}

	// ------------------------------------------------------------ zakladki

	private void buildRows() {
		rows.clear();
		scroll = 0;
		Config c = Config.get();
		switch (tab) {
			case 0 -> {
				rows.add(new Toggle("Efekty celu", () -> c.targetEnabled, v -> c.targetEnabled = v));
				rows.add(new Toggle("Tylko gracze", () -> c.playersOnly, v -> c.playersOnly = v));
				rows.add(new Slider("Zasięg", 3, 64, () -> c.range, v -> c.range = (float) v, "%.0f m"));
				rows.add(new Slider("Prędkość animacji", 0.2, 3, () -> c.speed, v -> c.speed = (float) v, "%.1fx"));
				rows.add(new Toggle("Pierścień", () -> c.ring, v -> c.ring = v));
				rows.add(new Toggle("Skaner", () -> c.scanner, v -> c.scanner = v));
				rows.add(new Toggle("Orbita", () -> c.orbit, v -> c.orbit = v));
				rows.add(new Toggle("Poświata", () -> c.glow, v -> c.glow = v));
				rows.add(new Toggle("Ramka (box)", () -> c.box, v -> c.box = v));
				rows.add(new Toggle("Panel celu (nick i HP)", () -> c.targetPanel, v -> c.targetPanel = v));
			}
			case 1 -> {
				rows.add(new Toggle("Okrąg przy skoku", () -> c.jumpCircle, v -> c.jumpCircle = v));
				rows.add(new Toggle("Ślady za Tobą", () -> c.trails, v -> c.trails = v));
				rows.add(new Slider("Czas śladów", 0.5, 6, () -> c.trailSeconds, v -> c.trailSeconds = (float) v, "%.1f s"));
				rows.add(new Toggle("Deszcz", () -> c.rain, v -> c.rain = v));
				rows.add(new Slider("Gęstość deszczu", 0, 1, () -> c.rainDensity, v -> c.rainDensity = (float) v, "%.0f%%", 100));
				rows.add(new Toggle("Zamieć", () -> c.blizzard, v -> c.blizzard = v));
				rows.add(new Slider("Gęstość zamieci", 0, 1, () -> c.blizzardDensity, v -> c.blizzardDensity = (float) v, "%.0f%%", 100));
				rows.add(new Toggle("Świetliki", () -> c.fireflies, v -> c.fireflies = v));
				rows.add(new Slider("Ilość świetlików", 0, 1, () -> c.fireflyCount, v -> c.fireflyCount = (float) v, "%.0f%%", 100));
				rows.add(new Toggle("Kolorowe niebo", () -> c.sky, v -> c.sky = v));
				rows.add(new Slider("Intensywność nieba", 0, 1, () -> c.skyIntensity, v -> c.skyIntensity = (float) v, "%.0f%%", 100));
				rows.add(new Slider("Prędkość nieba", 0, 1, () -> c.skySpeed, v -> c.skySpeed = (float) v, "%.0f%%", 100));
			}
			case 2 -> {
				rows.add(new Toggle("Pet włączony", () -> c.petEnabled, v -> c.petEnabled = v));
				rows.add(new Choice("Rodzaj peta", PetManager.NAMES, () -> c.petType, v -> c.petType = v));
				rows.add(new Slider("Rozmiar", 0.4, 2, () -> c.petScale, v -> c.petScale = (float) v, "%.1fx"));
				rows.add(new Info(2, () -> "Pety to zwykłe moby z Minecrafta. Widzisz je tylko Ty, nie istnieją na serwerze. "
					+ "Chodzące idą za Tobą, latające (papuga, nietoperz, allay, pszczoła) unoszą się przy głowie."));
			}
			case 3 -> {
				rows.add(new Choice("China Hat", new String[]{"Wyłączony", "Tylko ja", "Wszyscy"}, () -> c.chinaHat, v -> c.chinaHat = v));
				rows.add(new Slider("Rozmiar kapelusza", 0.5, 2, () -> c.chinaHatScale, v -> c.chinaHatScale = (float) v, "%.1fx"));
				rows.add(new Info(3, () -> "Tylko ja: kapelusz jest widoczny tylko na Tobie (w widoku z trzeciej osoby). "
					+ "Wszyscy: kapelusz pojawia się na każdym graczu wokół Ciebie. Nie zależy już od tego, w kogo celujesz."));
			}
			case 4 -> {
				rows.add(new Toggle("Panel muzyki", () -> c.musicPanel, v -> {
					c.musicPanel = v;
					MediaControl.sync(v);
				}));
				rows.add(new Info(3, () -> {
					if (!MediaControl.supported()) {
						return "Odczyt muzyki działa tylko na Windows (systemowy odtwarzacz, np. Spotify).";
					}
					return MediaControl.available
						? "Teraz gra: " + MediaControl.title + " - " + MediaControl.artist
						: "Nic nie gra. Włącz Spotify albo inny odtwarzacz, a panel pojawi się w lewym górnym rogu.";
				}));
				rows.add(new Info(3, () -> "Panel zostaje na ekranie także przy otwartym czacie (T). "
					+ "Na czacie klikasz przyciski wstecz / pauza / dalej oraz pasek, żeby przewijać utwór. "
					+ "Klawisze ustawisz w Sterowaniu (kategoria GhostVisual)."));
			}
			case 5 -> {
				rows.add(new ThemeGrid());
				rows.add(new Toggle("Animowane kolory", () -> c.animateColors, v -> c.animateColors = v));
			}
			case 6 -> {
				rows.add(new Slider("Przezroczystość paneli", 0.3, 1, () -> c.panelAlpha, v -> c.panelAlpha = (float) v, "%.0f%%", 100));
				rows.add(new Toggle("Zaokrąglone rogi", () -> c.rounded, v -> c.rounded = v));
				rows.add(new Info(2, () -> "Ustawienia zapisują się automatycznie w config/ghostvisual.json."));
			}
			default -> {
			}
		}
		layoutRows();
	}

	private void layoutRows() {
		int y = cy;
		for (Row r : rows) {
			r.x = cx;
			r.y = y;
			r.w = cw;
			y += r.h + 5;
		}
		totalH = y - cy;
	}

	private void setTab(int t) {
		tab = t;
		lastTab = t;
		buildRows();
	}

	// ------------------------------------------------------------ rysowanie

	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		Config cfg = Config.get();
		Theme th = cfg.theme();
		int accent = th.accent(cfg.animateColors);
		int rad = cfg.rounded ? 10 : 0;

		c.fill(0, 0, width, height, 0x66000000);
		Gfx.roundRect(c, px, py, pw, ph, rad, Gfx.argb(th.bg, cfg.panelAlpha));
		Gfx.roundBorder(c, px, py, pw, ph, rad, Gfx.argb(accent, 0.55f));

		renderSidebar(c, cfg, th, accent, mx, my);

		// naglowek zakladki
		Gfx.textScaled(c, Text.literal(TABS[tab]).formatted(Formatting.BOLD), cx, py + 14, 1.25f, 0xFFFFFFFF);
		c.fill(cx, py + 31, cx + 26, py + 33, Gfx.argb(accent, 1f));

		if (tab == 7) {
			renderProfile(c, cfg, th, accent);
		} else {
			int maxScroll = Math.max(0, totalH - ch);
			scroll = Math.max(0, Math.min(maxScroll, scroll));
			c.enableScissor(cx - 2, cy - 2, cx + cw + 2, cy + ch);
			for (Row r : rows) {
				int oy = r.y;
				r.y = oy - scroll;
				if (r.y + r.h >= cy - 2 && r.y <= cy + ch) {
					r.render(c, accent, th, cfg, mx, my);
				}
				r.y = oy;
			}
			c.disableScissor();
			if (maxScroll > 0) {
				int trackH = ch;
				int barH = Math.max(18, trackH * ch / Math.max(ch, totalH));
				int barY = cy + (trackH - barH) * scroll / maxScroll;
				c.fill(cx + cw + 4, barY, cx + cw + 6, barY + barH, Gfx.argb(accent, 0.7f));
			}
		}
	}

	private void renderSidebar(DrawContext c, Config cfg, Theme th, int accent, int mx, int my) {
		int rad = cfg.rounded ? 8 : 0;
		int sx = px + 6, sy = py + 6, sw = SIDE - 4, sh = ph - 12;
		Gfx.roundRect(c, sx, sy, sw, sh, rad, Gfx.argb(0x000000, 0.28f));
		Gfx.roundBorder(c, sx, sy, sw, sh, rad, Gfx.argb(0xFFFFFF, 0.07f));

		// logo + nazwa
		Gfx.roundBorder(c, px + 12, py + 12, 30, 30, 2, Gfx.argb(accent, 0.9f));
		Gfx.image(c, Gfx.LOGO, px + 13, py + 13, 28, 128);
		Gfx.textScaled(c, Text.literal("GhostVisual").formatted(Formatting.BOLD), px + 48, py + 15, 0.95f, 0xFFFFFFFF);
		Gfx.textScaled(c, "v" + VERSION, px + 48, py + 28, 0.75f, Gfx.argb(0xFFFFFF, 0.5f));

		// zakladki
		for (int i = 0; i < TABS.length; i++) {
			int bx = px + 12, by = py + 54 + i * 28, bw = SIDE - 16, bh = 24;
			boolean active = i == tab;
			boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
			if (active) {
				Gfx.roundRect(c, bx, by, bw, bh, cfg.rounded ? 6 : 0, Gfx.argb(accent, 0.14f));
				Gfx.roundBorder(c, bx, by, bw, bh, cfg.rounded ? 6 : 0, Gfx.argb(accent, 0.9f));
				c.fill(bx + 3, by + 5, bx + 5, by + bh - 5, Gfx.argb(accent, 1f));
			} else if (hover) {
				Gfx.roundRect(c, bx, by, bw, bh, cfg.rounded ? 6 : 0, Gfx.argb(0xFFFFFF, 0.06f));
			}
			c.fill(bx + 12, by + 10, bx + 16, by + 14, Gfx.argb(active ? accent : 0xFFFFFF, active ? 1f : 0.35f));
			Gfx.text(c, TABS[i], bx + 24, by + 8, active ? 0xFFFFFFFF : Gfx.argb(0xFFFFFF, 0.65f));
		}

		// podpis
		Gfx.textScaled(c, "made by shvga", px + 14, py + ph - 20, 0.85f, Gfx.argb(0xFFFFFF, 0.55f));
	}

	private int pill(DrawContext c, String text, int x, int y, int fill, float fillAlpha, int textColor) {
		int w = Gfx.width(text) * 8 / 10 + 12;
		Gfx.roundRect(c, x, y, w, 12, 6, Gfx.argb(fill, fillAlpha));
		Gfx.textScaled(c, text, x + 6, y + 3, 0.8f, textColor);
		return w;
	}

	private void renderProfile(DrawContext c, Config cfg, Theme th, int accent) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int rad = cfg.rounded ? 8 : 0;
		int x = cx, y = cy, w = cw;

		// karta profilu
		Gfx.roundRect(c, x, y, w, 88, rad, Gfx.argb(accent, 0.07f));
		Gfx.roundBorder(c, x, y, w, 88, rad, Gfx.argb(accent, 0.8f));
		Gfx.roundBorder(c, x + 12, y + 12, 64, 64, 3, Gfx.argb(accent, 1f));
		Gfx.image(c, Gfx.LOGO, x + 13, y + 13, 62, 128);

		int tx = x + 90;
		Gfx.textScaled(c, Text.literal("shvga").formatted(Formatting.BOLD), tx, y + 10, 2.0f, 0xFFFFFFFF);
		Gfx.text(c, "Twórca GhostVisual", tx, y + 32, Gfx.argb(0xFFFFFF, 0.88f));
		int bx = tx;
		bx += pill(c, "OWNER", bx, y + 46, accent, 0.95f, 0xFF04110E) + 5;
		bx += pill(c, "AUTOR", bx, y + 46, accent, 0.22f, Gfx.argb(accent, 1f)) + 5;
		pill(c, "v" + VERSION, bx, y + 46, 0xFFFFFF, 0.12f, Gfx.argb(0xFFFFFF, 0.75f));
		String user = mc.getSession() != null ? mc.getSession().getUsername() : "?";
		Gfx.textScaled(c, "Zalogowano jako " + user + "  •  Minecraft 1.21.4  •  Fabric", tx, y + 66, 0.75f, Gfx.argb(0xFFFFFF, 0.5f));

		// kafelki statystyk
		int gap = 8;
		int tw = (w - 2 * gap) / 3;
		long secs = (System.currentTimeMillis() - Targets.startMillis) / 1000;
		stat(c, x, y + 98, tw, String.valueOf(Targets.targetsSeen), "Cele w sesji", accent, cfg);
		stat(c, x + tw + gap, y + 98, tw, String.valueOf(cfg.activeEffects()), "Aktywne efekty", accent, cfg);
		stat(c, x + 2 * (tw + gap), y + 98, tw, (secs / 60) + ":" + String.format("%02d", secs % 60), "Czas sesji", accent, cfg);

		// opis
		int dy = y + 98 + 46;
		int dh = Math.max(40, ch - (dy - cy));
		Gfx.roundRect(c, x, dy, w, dh, rad, Gfx.argb(0xFFFFFF, 0.05f));
		Gfx.roundBorder(c, x, dy, w, dh, rad, Gfx.argb(0xFFFFFF, 0.08f));
		Gfx.text(c, Text.literal("GhostVisual").formatted(Formatting.BOLD), x + 10, dy + 8, 0xFFFFFFFF);
		List<OrderedText> lines = Gfx.tr().wrapLines(Text.literal(
			"Efekty wizualne celu, pety z Minecrafta, China Hat dla Ciebie lub wszystkich oraz panel muzyki z okładką. "
				+ "Made by shvga."), w - 20);
		int ly = dy + 22;
		for (OrderedText l : lines) {
			if (ly > dy + dh - 10) {
				break;
			}
			c.drawText(Gfx.tr(), l, x + 10, ly, Gfx.argb(0xFFFFFF, 0.6f), false);
			ly += 11;
		}
	}

	private void stat(DrawContext c, int x, int y, int w, String value, String label, int accent, Config cfg) {
		Gfx.roundRect(c, x, y, w, 38, cfg.rounded ? 6 : 0, Gfx.argb(0xFFFFFF, 0.06f));
		c.fill(x + 1, y + 6, x + 3, y + 32, Gfx.argb(accent, 1f));
		Gfx.textScaled(c, value, x + 12, y + 6, 1.6f, Gfx.argb(accent, 1f));
		Gfx.textScaled(c, label, x + 12, y + 26, 0.8f, Gfx.argb(0xFFFFFF, 0.55f));
	}

	// ------------------------------------------------------------ mysz

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return super.mouseClicked(mx, my, button);
		}
		if (MusicPanel.click(mx, my)) {
			return true;
		}
		for (int i = 0; i < TABS.length; i++) {
			int bx = px + 12, by = py + 54 + i * 28, bw = SIDE - 16, bh = 24;
			if (mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
				setTab(i);
				return true;
			}
		}
		if (tab != 7 && mx >= cx && mx < cx + cw && my >= cy && my < cy + ch) {
			for (Row r : rows) {
				int oy = r.y;
				r.y = oy - scroll;
				boolean hit = r.contains(mx, my) && r.click(mx, my);
				if (hit && r.draggable()) {
					dragging = r;
				}
				r.y = oy;
				if (hit) {
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging != null) {
			dragging.drag(mx);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragging != null) {
			dragging = null;
			Config.get().save();
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		scroll -= (int) (vertical * 18);
		int maxScroll = Math.max(0, totalH - ch);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		return true;
	}

	// ------------------------------------------------------------ wiersze

	private abstract static class Row {
		int x, y, w;
		final int h;

		Row(int h) {
			this.h = h;
		}

		abstract void render(DrawContext c, int accent, Theme th, Config cfg, int mx, int my);

		boolean click(double mx, double my) {
			return false;
		}

		boolean draggable() {
			return false;
		}

		void drag(double mx) {
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		void card(DrawContext c, Config cfg, int mx, int my) {
			boolean hover = contains(mx, my);
			Gfx.roundRect(c, x, y, w, h, cfg.rounded ? 6 : 0, Gfx.argb(0xFFFFFF, hover ? 0.10f : 0.06f));
		}
	}

	private static final class Toggle extends Row {
		private final String label;
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;

		Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
			super(24);
			this.label = label;
			this.get = get;
			this.set = set;
		}

		@Override
		void render(DrawContext c, int accent, Theme th, Config cfg, int mx, int my) {
			card(c, cfg, mx, my);
			Gfx.text(c, label, x + 10, y + 8, 0xFFFFFFFF);
			boolean on = get.getAsBoolean();
			int sw = 28, sh = 12;
			int sx = x + w - sw - 10, sy = y + (h - sh) / 2;
			Gfx.roundRect(c, sx, sy, sw, sh, 6, on ? Gfx.argb(accent, 0.95f) : Gfx.argb(0xFFFFFF, 0.2f));
			int kx = on ? sx + sw - 11 : sx + 1;
			Gfx.roundRect(c, kx, sy + 1, 10, 10, 5, 0xFFFFFFFF);
		}

		@Override
		boolean click(double mx, double my) {
			set.accept(!get.getAsBoolean());
			return true;
		}
	}

	private static final class Slider extends Row {
		private final String label, fmt;
		private final double min, max, mul;
		private final java.util.function.DoubleSupplier get;
		private final DoubleConsumer set;

		Slider(String label, double min, double max, java.util.function.DoubleSupplier get, DoubleConsumer set, String fmt) {
			this(label, min, max, get, set, fmt, 1);
		}

		Slider(String label, double min, double max, java.util.function.DoubleSupplier get, DoubleConsumer set, String fmt, double mul) {
			super(24);
			this.label = label;
			this.min = min;
			this.max = max;
			this.get = get;
			this.set = set;
			this.fmt = fmt;
			this.mul = mul;
		}

		private int tx() {
			return x + (int) (w * 0.42);
		}

		private int tw() {
			return x + w - 58 - tx();
		}

		@Override
		void render(DrawContext c, int accent, Theme th, Config cfg, int mx, int my) {
			card(c, cfg, mx, my);
			Gfx.text(c, label, x + 10, y + 8, 0xFFFFFFFF);
			double v = get.getAsDouble();
			float f = (float) ((v - min) / (max - min));
			f = Math.max(0f, Math.min(1f, f));
			int tx = tx(), tw = tw(), ty = y + h / 2 - 1;
			c.fill(tx, ty, tx + tw, ty + 3, Gfx.argb(0xFFFFFF, 0.2f));
			c.fill(tx, ty, tx + Math.round(tw * f), ty + 3, Gfx.argb(accent, 1f));
			int kx = tx + Math.round(tw * f);
			Gfx.roundRect(c, kx - 3, ty - 3, 7, 9, 3, 0xFFFFFFFF);
			String val = String.format(fmt, v * mul);
			Gfx.text(c, val, x + w - 10 - Gfx.width(val), y + 8, Gfx.argb(accent, 1f));
		}

		@Override
		boolean click(double mx, double my) {
			if (mx >= tx() - 6 && mx <= tx() + tw() + 6) {
				drag(mx);
				return true;
			}
			return false;
		}

		@Override
		boolean draggable() {
			return true;
		}

		@Override
		void drag(double mx) {
			double f = Math.max(0, Math.min(1, (mx - tx()) / (double) tw()));
			set.accept(min + (max - min) * f);
		}
	}

	private static final class Choice extends Row {
		private final String label;
		private final String[] options;
		private final IntSupplier get;
		private final IntConsumer set;

		Choice(String label, String[] options, IntSupplier get, IntConsumer set) {
			super(24);
			this.label = label;
			this.options = options;
			this.get = get;
			this.set = set;
		}

		@Override
		void render(DrawContext c, int accent, Theme th, Config cfg, int mx, int my) {
			card(c, cfg, mx, my);
			Gfx.text(c, label, x + 10, y + 8, 0xFFFFFFFF);
			String v = options[Math.max(0, Math.min(options.length - 1, get.getAsInt()))];
			int bx = x + w - 130;
			Gfx.text(c, "<", bx + 4, y + 8, Gfx.argb(accent, 1f));
			Gfx.text(c, ">", bx + 120 - 10, y + 8, Gfx.argb(accent, 1f));
			Gfx.textCentered(c, v, bx + 60, y + 8, 0xFFFFFFFF);
		}

		@Override
		boolean click(double mx, double my) {
			int bx = x + w - 130;
			if (mx < bx) {
				return false;
			}
			int i = get.getAsInt() + (mx < bx + 60 ? -1 : 1);
			set.accept((i + options.length) % options.length);
			return true;
		}
	}

	private static final class Info extends Row {
		private final Supplier<String> text;

		Info(int lines, Supplier<String> text) {
			super(lines * 11 + 12);
			this.text = text;
		}

		@Override
		void render(DrawContext c, int accent, Theme th, Config cfg, int mx, int my) {
			Gfx.roundRect(c, x, y, w, h, cfg.rounded ? 6 : 0, Gfx.argb(0xFFFFFF, 0.035f));
			List<OrderedText> lines = Gfx.tr().wrapLines(Text.literal(text.get()), w - 20);
			int ly = y + 7;
			for (OrderedText l : lines) {
				if (ly + 9 > y + h) {
					break;
				}
				c.drawText(Gfx.tr(), l, x + 10, ly, Gfx.argb(0xFFFFFF, 0.6f), false);
				ly += 11;
			}
		}
	}

	private static final class ThemeGrid extends Row {
		ThemeGrid() {
			super(3 * 50 + 4);
		}

		private int cardW() {
			return (w - 16) / 3;
		}

		@Override
		void render(DrawContext c, int accent, Theme th, Config cfg, int mx, int my) {
			Theme[] all = Theme.values();
			int cw = cardW();
			for (int i = 0; i < all.length; i++) {
				int bx = x + (i % 3) * (cw + 8);
				int by = y + (i / 3) * 50;
				Theme t = all[i];
				boolean sel = cfg.theme == i;
				boolean hover = mx >= bx && mx < bx + cw && my >= by && my < by + 44;
				Gfx.roundRect(c, bx, by, cw, 44, cfg.rounded ? 6 : 0, Gfx.argb(t.bg, 1f));
				Gfx.roundBorder(c, bx, by, cw, 44, cfg.rounded ? 6 : 0, Gfx.argb(t.accent, sel ? 1f : (hover ? 0.7f : 0.35f)));
				Gfx.roundRect(c, bx + 8, by + 8, 14, 14, 3, Gfx.argb(t.accent, 1f));
				Gfx.roundRect(c, bx + 26, by + 8, 14, 14, 3, Gfx.argb(t.accent2, 1f));
				Gfx.text(c, t.label, bx + 8, by + 28, 0xFFFFFFFF);
				if (sel) {
					Gfx.roundRect(c, bx + cw - 16, by + 8, 8, 8, 4, Gfx.argb(t.accent, 1f));
				}
			}
		}

		@Override
		boolean click(double mx, double my) {
			Theme[] all = Theme.values();
			int cw = cardW();
			for (int i = 0; i < all.length; i++) {
				int bx = x + (i % 3) * (cw + 8);
				int by = y + (i / 3) * 50;
				if (mx >= bx && mx < bx + cw && my >= by && my < by + 44) {
					Config.get().theme = i;
					return true;
				}
			}
			return false;
		}
	}
}
