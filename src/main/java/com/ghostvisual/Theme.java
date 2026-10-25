package com.ghostvisual;

public enum Theme {
	BLACK("Czarny", 0xE6E6E6, 0x8A8A8A, 0x0B0B0D),
	BLUE("Niebieski", 0x3D8BFF, 0x00D4FF, 0x0A1224),
	DARK_PURPLE("Ciemny fiolet", 0x8E44FF, 0x5B1FB5, 0x120A1F),
	GOLD("Złoty", 0xFFC83D, 0xFF9E1F, 0x1A1408),
	GREEN("Zielony", 0x1FD6A0, 0x36F08A, 0x08201C),
	ORANGE("Pomarańczowy", 0xFF7A29, 0xFFB037, 0x1D0F07),
	PURPLE_PINK("Fiolet i róż", 0xFF4FD8, 0xA05BFF, 0x1A0A1C);

	public final String label;
	public final int accent;
	public final int accent2;
	public final int bg;

	Theme(String label, int accent, int accent2, int bg) {
		this.label = label;
		this.accent = accent;
		this.accent2 = accent2;
		this.bg = bg;
	}

	public static Theme byIndex(int i) {
		Theme[] v = values();
		return v[Math.max(0, Math.min(v.length - 1, i))];
	}

	/** Kolor akcentu, opcjonalnie plynnie przechodzacy miedzy accent i accent2. */
	public int accent(boolean animate) {
		if (!animate) {
			return accent;
		}
		return Gfx.lerp(accent, accent2, pingPong(4000L, 0));
	}

	/** Kolor z przesunieciem fazy (0..1), uzywany w efektach swiata. */
	public int accentPhase(boolean animate, double phase) {
		if (!animate) {
			return accent;
		}
		return Gfx.lerp(accent, accent2, pingPong(4000L, phase));
	}

	public static float pingPong(long periodMs, double phase) {
		double t = (System.currentTimeMillis() % periodMs) / (double) periodMs + phase;
		return (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2.0));
	}
}
