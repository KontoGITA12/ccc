package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Panel muzyki w lewym gornym rogu. Rysowany jako HUD, wiec zostaje widoczny
 * rowniez gdy otwarty jest czat (T). Na czacie mozna klikac przyciski i pasek.
 */
public final class MusicPanel {
	private MusicPanel() {
	}

	private static final int X = 6, Y = 6, W = 204, H = 60;
	private static final int PX = X + 58, PW = W - 58 - 10, PY = Y + 33;
	private static final int BY = Y + 49;
	private static boolean visible = false;

	public static void render(DrawContext c, MinecraftClient mc) {
		Config cfg = Config.get();
		visible = false;
		if (!cfg.musicPanel || !MediaControl.available || mc.options.hudHidden) {
			return;
		}
		visible = true;
		Theme th = cfg.theme();
		int accent = th.accent(cfg.animateColors);
		int rad = cfg.rounded ? 7 : 0;

		Gfx.roundRect(c, X, Y, W, H, rad, Gfx.argb(th.bg, Math.min(1f, cfg.panelAlpha + 0.05f)));
		Gfx.roundBorder(c, X, Y, W, H, rad, Gfx.argb(accent, 0.65f));

		// okladka (albo logo ducha gdy brak)
		Gfx.roundBorder(c, X + 6, Y + 6, 44, 44, 2, Gfx.argb(accent, 0.9f));
		if (MediaControl.hasCover) {
			Gfx.image(c, MediaControl.COVER, X + 7, Y + 7, 42, MediaControl.COVER_TEX);
		} else {
			Gfx.image(c, Gfx.LOGO, X + 7, Y + 7, 42, 128);
		}

		String title = Gfx.tr().trimToWidth(MediaControl.title, W - 58 - 10);
		String artist = Gfx.tr().trimToWidth(MediaControl.artist, W - 58 - 10);
		Gfx.text(c, title, PX, Y + 8, 0xFFFFFFFF);
		Gfx.text(c, artist, PX, Y + 19, Gfx.argb(0xFFFFFF, 0.55f));

		// pasek postepu
		double dur = MediaControl.duration;
		double pos = MediaControl.livePosition();
		float frac = dur > 0 ? (float) Math.min(1.0, pos / dur) : 0f;
		c.fill(PX, PY, PX + PW, PY + 4, Gfx.argb(0xFFFFFF, 0.14f));
		int fw = Math.round(PW * frac);
		c.fill(PX, PY, PX + fw, PY + 4, Gfx.argb(accent, 1f));
		c.fill(PX + fw - 1, PY - 1, PX + fw + 2, PY + 5, 0xFFFFFFFF);

		// czasy
		Gfx.textScaled(c, Gfx.time(pos), PX, Y + 40, 0.75f, Gfx.argb(0xFFFFFF, 0.6f));
		String total = Gfx.time(dur);
		Gfx.textScaled(c, total, PX + PW - Gfx.width(total) * 0.75f, Y + 40, 0.75f, Gfx.argb(0xFFFFFF, 0.6f));

		// przyciski
		int cx = PX + PW / 2;
		Gfx.iconPrev(c, cx - 22, BY, 8, 0xFFFFFFFF);
		if (MediaControl.playing) {
			Gfx.iconPause(c, cx, BY, 9, Gfx.argb(accent, 1f));
		} else {
			Gfx.iconPlay(c, cx, BY, 9, Gfx.argb(accent, 1f));
		}
		Gfx.iconNext(c, cx + 22, BY, 8, 0xFFFFFFFF);
	}

	/** Obsluga klikniecia (wspolrzedne GUI). Zwraca true, gdy klik trafil w panel. */
	public static boolean click(double mx, double my) {
		if (!visible || mx < X || mx > X + W || my < Y || my > Y + H) {
			return false;
		}
		int cx = PX + PW / 2;
		if (my >= BY - 7 && my <= BY + 7) {
			if (Math.abs(mx - (cx - 22)) <= 9) {
				MediaControl.send("prev");
			} else if (Math.abs(mx - cx) <= 9) {
				MediaControl.send("toggle");
			} else if (Math.abs(mx - (cx + 22)) <= 9) {
				MediaControl.send("next");
			}
			return true;
		}
		if (my >= PY - 4 && my <= PY + 9 && mx >= PX - 2 && mx <= PX + PW + 2) {
			double frac = Math.max(0, Math.min(1, (mx - PX) / PW));
			MediaControl.seek(frac * MediaControl.duration);
		}
		return true;
	}
}
