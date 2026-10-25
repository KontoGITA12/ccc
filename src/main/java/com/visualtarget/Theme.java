package com.visualtarget;

/**
 * Motywy kolorystyczne: kolor menu (ui) i kolory efektow w swiecie (fx).
 * Kazdy motyw to gradient z dwoch kolorow.
 */
public enum Theme {
	//     nazwa          panel     uiA       uiB       fxA       fxB       glow(-1 = jak fx)
	BLACK("Black",        0x09090B, 0xF0F0F6, 0x8E8E9C, 0x0B0B0E, 0x34343E, 0xEDEDF7),
	PURPLE_PINK("Purple-Pink", 0x140A1F, 0xA855F7, 0xF472B6, 0x9D4EFF, 0xFF4FB5, -1),
	DARK_PURPLE("Dark Purple", 0x0C0716, 0x7C3AED, 0x5B21B6, 0x5B21B6, 0x7E3FE8, 0x8B5CF6),
	BLUE("Blue",          0x08111F, 0x60A5FA, 0x2563EB, 0x2563EB, 0x38BDF8, 0x7CC4FF),
	RED("Red",            0x1A0A0C, 0xF87171, 0xDC2626, 0xDC2626, 0xFF5A5A, 0xFF8A8A),
	GREEN("Green",        0x08150E, 0x4ADE80, 0x16A34A, 0x16A34A, 0x4ADE80, 0x86EFAC),
	ICE("Ice",            0x08141A, 0x67E8F9, 0xA5F3FC, 0x22D3EE, 0xBAE6FD, 0xCFFAFE),
	ORANGE("Orange",      0x1A0F06, 0xFB923C, 0xF97316, 0xF97316, 0xFBBF24, 0xFFB366),
	GOLD("Gold",          0x16120A, 0xFACC15, 0xEAB308, 0xCA8A04, 0xFDE047, 0xFEF08A);

	public final String label;
	public final int panel;
	public final int uiA, uiB;
	public final int fxA, fxB;
	public final int glowRgb;

	Theme(String label, int panel, int uiA, int uiB, int fxA, int fxB, int glowRgb) {
		this.label = label;
		this.panel = panel;
		this.uiA = uiA;
		this.uiB = uiB;
		this.fxA = fxA;
		this.fxB = fxB;
		this.glowRgb = glowRgb;
	}

	public static Theme byIndex(int i) {
		Theme[] all = values();
		if (i < 0 || i >= all.length) return PURPLE_PINK;
		return all[i];
	}

	/** Kolor menu dla fazy (gradient tam i z powrotem). */
	public int ui(double phase) {
		return mix(uiA, uiB, pingPong(phase));
	}

	/** Kolor menu liniowo od uiA (0) do uiB (1) - do podgladu na kartach. */
	public int uiLinear(double f) {
		return mix(uiA, uiB, f);
	}

	/** Kolor efektu w swiecie dla fazy. */
	public int fx(double phase) {
		return mix(fxA, fxB, pingPong(phase));
	}

	/** Kolor poswiaty wokol linii. */
	public int glow(double phase) {
		return glowRgb >= 0 ? glowRgb : fx(phase);
	}

	private static double pingPong(double p) {
		p = p - Math.floor(p);
		return p < 0.5 ? p * 2.0 : (1.0 - p) * 2.0;
	}

	/** Mieszanie dwoch kolorow RGB (t = 0..1). */
	public static int mix(int a, int b, double t) {
		t = Math.max(0.0, Math.min(1.0, t));
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int r = (int) Math.round(ar + (br - ar) * t);
		int g = (int) Math.round(ag + (bg - ag) * t);
		int bl = (int) Math.round(ab + (bb - ab) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
