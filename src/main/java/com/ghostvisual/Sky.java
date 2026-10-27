package com.ghostvisual;

import net.minecraft.client.MinecraftClient;

import java.util.Random;

/**
 * Wlasne nieba: kopula rysowana na pikselach, gdzie nic nie ma (testem glebi),
 * wiec zakrywa niebo z texture packa / zwykle niebo, slonce, ksiezyc i gwiazdy.
 */
public final class Sky {
	private Sky() {
	}

	public static final String[] NAMES = {"Domyślne", "Aurora", "Mgławica", "Zmierzch", "Fiolet", "Szare"};

	private static final int LAT = 24;
	private static final int LON = 48;
	private static final int N_STARS = 420;
	/** Gwiazdy: [dx,dy,dz, t1x,t1y,t1z, t2x,t2y,t2z, faza, rozmiar]. */
	private static final double[][] STARS = new double[N_STARS][];

	static {
		Random r = new Random(77);
		for (int i = 0; i < N_STARS; i++) {
			double y = r.nextDouble() * 2 - 1;
			double a = r.nextDouble() * Math.PI * 2;
			double rr = Math.sqrt(1 - y * y);
			double dx = rr * Math.cos(a), dz = rr * Math.sin(a);
			// t1 = dir x (0,1,0)
			double t1x = -dz, t1y = 0, t1z = dx;
			double l = Math.sqrt(t1x * t1x + t1z * t1z);
			if (l < 1e-4) {
				t1x = 1;
				t1z = 0;
				l = 1;
			}
			t1x /= l;
			t1z /= l;
			// t2 = dir x t1
			double t2x = y * t1z - dz * t1y;
			double t2y = dz * t1x - dx * t1z;
			double t2z = dx * t1y - y * t1x;
			STARS[i] = new double[]{dx, y, dz, t1x, t1y, t1z, t2x, t2y, t2z, r.nextDouble() * Math.PI * 2, 0.6 + r.nextDouble()};
		}
	}

	public static void scene(Draw d, MinecraftClient mc, Config cfg) {
		int style = cfg.skyStyle;
		if (style <= 0 || mc.world == null || !mc.world.getDimension().hasSkyLight()) {
			return;
		}
		double R = Math.max(80, Math.min(380, mc.options.getClampedViewDistance() * 16 * 0.92));
		double t = System.nanoTime() / 1.0e9;
		double cx = d.cx, cy = d.cy, cz = d.cz;

		// --- kopula
		d.quadTarget = Draw.SOLID;
		int[][] col = new int[LAT + 1][LON + 1];
		double[] elev = new double[LAT + 1];
		for (int i = 0; i <= LAT; i++) {
			double s = (i / (double) LAT) * 2 - 1;
			elev[i] = Math.signum(s) * (Math.PI / 2) * Math.pow(Math.abs(s), 1.6);
			for (int j = 0; j <= LON; j++) {
				double az = j * Math.PI * 2 / LON;
				col[i][j] = color(style, elev[i], az, t);
			}
		}
		for (int i = 0; i < LAT; i++) {
			for (int j = 0; j < LON; j++) {
				double a0 = j * Math.PI * 2 / LON, a1 = (j + 1) * Math.PI * 2 / LON;
				d.quadV(dir(cx, cy, cz, R, elev[i], a0), dir(cx, cy, cz, R, elev[i], a1),
					dir(cx, cy, cz, R, elev[i + 1], a1), dir(cx, cy, cz, R, elev[i + 1], a0),
					col[i][j], col[i][j + 1], col[i + 1][j + 1], col[i + 1][j], 1f);
			}
		}

		// --- gwiazdy
		d.quadTarget = Draw.ADD;
		if (style != 5) {
			double fade = style == 3 ? 0.35 : 1.0;
			double rs = R * 0.97;
			double base = R * 0.0034;
			for (double[] s : STARS) {
				if (s[1] < -0.05) {
					continue;
				}
				float a = (float) ((0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * 1.4 + s[9]))) * fade);
				double sz = base * s[10];
				double px = cx + s[0] * rs, py = cy + s[1] * rs, pz = cz + s[2] * rs;
				d.quad(new double[]{px - s[3] * sz - s[6] * sz, py - s[4] * sz - s[7] * sz, pz - s[5] * sz - s[8] * sz},
					new double[]{px + s[3] * sz - s[6] * sz, py + s[4] * sz - s[7] * sz, pz + s[5] * sz - s[8] * sz},
					new double[]{px + s[3] * sz + s[6] * sz, py + s[4] * sz + s[7] * sz, pz + s[5] * sz + s[8] * sz},
					new double[]{px - s[3] * sz + s[6] * sz, py - s[4] * sz + s[7] * sz, pz - s[5] * sz + s[8] * sz},
					0xDDE8FF, a);
			}
		}

		// --- aurora: swietlne kurtyny
		if (style == 1) {
			double rs = R * 0.95;
			int cols = 40;
			for (int k = 0; k < 3; k++) {
				double baseAz = 0.4 + k * 2.0;
				double span = 1.7;
				for (int j = 0; j < cols; j++) {
					double az0 = baseAz + span * j / cols, az1 = baseAz + span * (j + 1) / cols;
					double eb0 = 0.16 + 0.10 * wave(az0, t, k) + 0.07 * k;
					double eb1 = 0.16 + 0.10 * wave(az1, t, k) + 0.07 * k;
					double et0 = eb0 + 0.40 + 0.18 * Math.sin(az0 * 2 + t * 0.3 + k);
					double et1 = eb1 + 0.40 + 0.18 * Math.sin(az1 * 2 + t * 0.3 + k);
					float inten = (float) (0.55 + 0.45 * Math.sin(az0 * 5 + t * 0.6 + k * 1.7));
					inten = Math.max(0.12f, inten);
					float mix = (float) (0.5 + 0.5 * Math.sin(az0 * 2 + t * 0.2 + k));
					int c = Gfx.lerp(0x2DFFA5, 0x9F5BFF, mix);
					d.quad(dir(cx, cy, cz, rs, eb0, az0), dir(cx, cy, cz, rs, eb1, az1),
						dir(cx, cy, cz, rs, et1, az1), dir(cx, cy, cz, rs, et0, az0), c, 0.6f * inten, 0f);
				}
			}
		}

		// --- zmierzch: slonce przy horyzoncie
		if (style == 3) {
			double rs = R * 0.96;
			double[] c = dir(cx, cy, cz, rs, 0.05, 0.0);
			double[] c2 = dir(cx, cy, cz, rs, 0.05, 0.01);
			sun(d, c, c2, rs * 0.055, 0xFFE3A6, 0.85f);
			sun(d, c, c2, rs * 0.14, 0xFF9A4A, 0.22f);
		}
	}

	private static void sun(Draw d, double[] c, double[] other, double size, int rgb, float a) {
		// plaszczyzna prostopadla do kierunku patrzenia na slonce (poziomy bok + pion)
		double dx = c[0] - other[0], dz = c[2] - other[2];
		double l = Math.sqrt(dx * dx + dz * dz);
		if (l < 1e-9) {
			dx = 1;
			dz = 0;
			l = 1;
		}
		dx /= l;
		dz /= l;
		d.quad(new double[]{c[0] - dx * size, c[1] - size, c[2] - dz * size},
			new double[]{c[0] + dx * size, c[1] - size, c[2] + dz * size},
			new double[]{c[0] + dx * size, c[1] + size, c[2] + dz * size},
			new double[]{c[0] - dx * size, c[1] + size, c[2] - dz * size}, rgb, a);
	}

	private static double wave(double az, double t, int k) {
		return Math.sin(az * 3 + t * 0.35 + k) * 0.5 + Math.sin(az * 7 - t * 0.5 + k * 2) * 0.25;
	}

	private static double[] dir(double cx, double cy, double cz, double r, double e, double az) {
		return new double[]{cx + Math.cos(e) * Math.cos(az) * r, cy + Math.sin(e) * r, cz + Math.cos(e) * Math.sin(az) * r};
	}

	// ---- kolory

	private static int ramp(double x, int[] colors, double[] stops) {
		if (x <= stops[0]) {
			return colors[0];
		}
		for (int i = 1; i < stops.length; i++) {
			if (x <= stops[i]) {
				double f = (x - stops[i - 1]) / (stops[i] - stops[i - 1]);
				return Gfx.lerp(colors[i - 1], colors[i], (float) f);
			}
		}
		return colors[colors.length - 1];
	}

	private static int color(int style, double e, double az, double t) {
		double h = e / (Math.PI / 2);
		double up = Math.max(0, h);
		switch (style) {
			case 1: // aurora
				if (h < 0) {
					return 0x050D12;
				}
				return ramp(up, new int[]{0x0B2A33, 0x04122A, 0x01030F}, new double[]{0, 0.35, 1});
			case 2: { // mgławica
				int base = h < 0 ? 0x05030C : ramp(up, new int[]{0x0A0618, 0x05030F}, new double[]{0, 1});
				double n1 = 0.5 + 0.5 * Math.sin(az * 2 + h * 4 + t * 0.03) * Math.sin(az * 3.1 - h * 2.2 + 1.7 + t * 0.02);
				double n2 = 0.5 + 0.5 * Math.sin(az * 4.3 + 2.0 - h * 5 - t * 0.025) * Math.cos(az * 1.7 + h * 3);
				int c = Gfx.lerp(base, 0x7B2CBF, (float) (n1 * n1 * 0.75));
				c = Gfx.lerp(c, 0xE0359A, (float) (n2 * n2 * n1 * 0.8));
				c = Gfx.lerp(c, 0x2A6BFF, (float) ((1 - n1) * n2 * n2 * 0.5));
				return c;
			}
			case 3: // zmierzch
				if (h < 0) {
					return Gfx.lerp(0xE0763C, 0x2A1530, (float) Math.min(1, -h * 4));
				}
				return ramp(up, new int[]{0xFFD28A, 0xFF8A3D, 0xC2457A, 0x5A2D82, 0x14183F},
					new double[]{0, 0.05, 0.2, 0.5, 1});
			case 4: { // fiolet
				if (h < 0) {
					return 0x2A0A4A;
				}
				int c = ramp(up, new int[]{0xC44BD6, 0x7A28C4, 0x2E0A66, 0x15042E}, new double[]{0, 0.25, 0.6, 1});
				float pulse = (float) (0.5 + 0.5 * Math.sin(t * 0.4 + az * 2));
				return Gfx.lerp(c, 0xFF7BE8, pulse * 0.12f);
			}
			default: // szare
				if (h < 0) {
					return 0x9EA2A6;
				}
				return ramp(up, new int[]{0xC4C7C9, 0xA1A5A9, 0x80858B}, new double[]{0, 0.4, 1});
		}
	}
}
