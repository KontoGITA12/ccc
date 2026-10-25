package com.visualtarget;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Matrix4f;

/**
 * Niebo: Aurora, Nebula, Galaxy, Caustics, Black hole.
 * Rysowane jako kopula z kolorowanych czworokatow wokol kamery (tylko w Overworld).
 * Kolory pochodza z wybranego motywu.
 */
public final class SkyRenderer {
	private SkyRenderer() {}

	private static final float R = 120f;
	private static final int AZ = 88;
	private static final int EL = 30;
	private static final double EL_MIN = Math.toRadians(4.0);
	private static final double EL_MAX = Math.toRadians(90.0);

	// Gotowe kierunki wierzcholkow kopuly
	private static final float[] DX = new float[(EL + 1) * (AZ + 1)];
	private static final float[] DY = new float[(EL + 1) * (AZ + 1)];
	private static final float[] DZ = new float[(EL + 1) * (AZ + 1)];
	private static final double[] ELV = new double[(EL + 1) * (AZ + 1)];
	private static final double[] AZV = new double[(EL + 1) * (AZ + 1)];
	private static final int[] COL = new int[(EL + 1) * (AZ + 1)];

	static {
		for (int i = 0; i <= EL; i++) {
			double el = EL_MIN + (EL_MAX - EL_MIN) * (i / (double) EL);
			for (int j = 0; j <= AZ; j++) {
				double az = Math.PI * 2.0 * j / AZ;
				int k = i * (AZ + 1) + j;
				DX[k] = (float) (Math.cos(el) * Math.sin(az));
				DY[k] = (float) Math.sin(el);
				DZ[k] = (float) (-Math.cos(el) * Math.cos(az));
				ELV[k] = el;
				AZV[k] = az;
			}
		}
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(SkyRenderer::onRender);
	}

	private static void onRender(WorldRenderContext ctx) {
		VisualConfig cfg = VisualConfig.get();
		if (cfg.sky == 0) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;
		if (mc.world.getRegistryKey() != World.OVERWORLD) return;

		MatrixStack ms = ctx.matrixStack();
		VertexConsumerProvider consumers = ctx.consumers();
		if (ms == null || consumers == null) return;

		float td = ctx.tickCounter().getTickDelta(false);
		double t = (mc.world.getTime() + td) / 20.0 * cfg.skySpeed;
		Theme theme = cfg.theme();
		int ca = theme.uiA;
		int cb = theme.uiB;
		double inten = cfg.skyIntensity;

		VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
		Matrix4f m = ms.peek().getPositionMatrix();

		if (cfg.sky == 5) {
			drawBlackHole(vc, m, t, ca, cb, inten);
		} else {
			for (int k = 0; k < COL.length; k++) {
				double dx = DX[k], dy = DY[k], dz = DZ[k];
				double el = ELV[k];
				int argb;
				switch (cfg.sky) {
					case 1 -> argb = aurora(dx, dy, dz, AZV[k], el, t, ca, cb, inten);
					case 2 -> argb = nebula(dx, dy, dz, t, ca, cb, inten);
					case 3 -> argb = galaxy(dx, dy, dz, t, ca, cb, inten);
					default -> argb = caustics(dx, dy, dz, t, ca, cb, inten);
				}
				// Przy horyzoncie niebo znika plynnie
				double edge = smooth(EL_MIN, EL_MIN + 0.28, el);
				int a = (int) (((argb >>> 24) & 0xFF) * edge);
				COL[k] = (a << 24) | (argb & 0xFFFFFF);
			}
			for (int i = 0; i < EL; i++) {
				for (int j = 0; j < AZ; j++) {
					int k00 = i * (AZ + 1) + j;
					int k10 = k00 + 1;
					int k01 = k00 + (AZ + 1);
					int k11 = k01 + 1;
					if ((COL[k00] >>> 24) + (COL[k10] >>> 24) + (COL[k01] >>> 24) + (COL[k11] >>> 24) == 0) continue;
					v(vc, m, k00);
					v(vc, m, k10);
					v(vc, m, k11);
					v(vc, m, k01);
				}
			}
			if (cfg.sky == 3) drawStars(vc, m, t, ca, cb, inten);
		}

		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLightning());
		}
	}

	private static void v(VertexConsumer vc, Matrix4f m, int k) {
		int c = COL[k];
		vc.vertex(m, DX[k] * R, DY[k] * R, DZ[k] * R)
				.color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
	}

	// ------------------------------------------------------------------
	// Style nieba (zwracaja ARGB)
	// ------------------------------------------------------------------

	private static int aurora(double dx, double dy, double dz, double az, double el, double t,
							  int ca, int cb, double inten) {
		double wob = 0.16 * Math.sin(az * 3.0 + t * 0.25) + 0.08 * (noise3(dx * 3, dz * 3, t * 0.15) - 0.5) * 2.0;
		double alpha = 0.0;
		double hue = 0.0;
		for (int layer = 0; layer < 2; layer++) {
			double base = 0.30 + wob + layer * 0.34;
			double x = el - base;
			double prof = x < 0 ? Math.exp(x * 24.0) : Math.exp(-x * 3.4);
			double rays = noise3(az * 9.0 + layer * 3.7, t * 0.35, 1.3) * 0.6
					+ noise3(az * 22.0, t * 0.55, 4.1 + layer) * 0.4;
			rays = Math.pow(clamp01(rays * 1.5), 1.6);
			double a = prof * (0.25 + rays) * (layer == 0 ? 1.0 : 0.55);
			if (a > alpha) {
				alpha = a;
				hue = clamp01(x * 2.2 + 0.25 + 0.3 * rays);
			}
		}
		int rgb = Theme.mix(ca, cb, hue);
		rgb = Theme.mix(rgb, 0xFFFFFF, 0.12 * alpha);
		return argb(alpha * 0.9 * inten, rgb);
	}

	private static int nebula(double dx, double dy, double dz, double t, int ca, int cb, double inten) {
		double n = fbm3(dx * 2.2 + t * 0.02, dy * 2.2, dz * 2.2, 4);
		double m = fbm3(dx * 4.5 - t * 0.015 + 9, dy * 4.5, dz * 4.5 + 3, 3);
		double d = smooth(0.36, 0.78, n);
		double alpha = Math.max(0.2, d * 0.85 + m * 0.1);
		int rgb = Theme.mix(Theme.mix(ca, 0x000000, 0.55), Theme.mix(cb, ca, 0.4), clamp01(m * 1.2));
		rgb = Theme.mix(rgb, 0xFFFFFF, 0.28 * d * d);
		return argb(alpha * inten, rgb);
	}

	private static int galaxy(double dx, double dy, double dz, double t, int ca, int cb, double inten) {
		// pas drogi mlecznej wzdluz duzego kola
		double nx = 0.35, ny = 0.80, nz = 0.50;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		double dist = (dx * nx + dy * ny + dz * nz) / len;
		double band = Math.exp(-(dist * dist) / 0.05);
		double clouds = fbm3(dx * 3.0, dy * 3.0, dz * 3.0 + t * 0.01, 4);
		double alpha = 0.55 + band * 0.3;
		int base = Theme.mix(0x04040C, Theme.mix(ca, 0x000000, 0.7), 0.5);
		int rgb = Theme.mix(base, Theme.mix(ca, cb, clouds), clamp01(band * (0.35 + 0.9 * clouds)));
		return argb(alpha * inten, rgb);
	}

	private static int caustics(double dx, double dy, double dz, double t, int ca, int cb, double inten) {
		double a1 = fbm3(dx * 3.0 + t * 0.08, dy * 3.0, dz * 3.0 - t * 0.06, 3);
		double a2 = fbm3(dx * 5.5 - t * 0.05 + 7, dy * 5.5 + 3, dz * 5.5 + t * 0.07, 3);
		double r1 = Math.pow(1.0 - Math.abs(2.0 * a1 - 1.0), 7.0);
		double r2 = Math.pow(1.0 - Math.abs(2.0 * a2 - 1.0), 7.0);
		double lines = clamp01(r1 + 0.6 * r2);
		int dark = Theme.mix(cb, 0x000000, 0.62);
		int bright = Theme.mix(ca, 0xFFFFFF, 0.5);
		return argb((0.3 + 0.65 * lines) * inten, Theme.mix(dark, bright, lines));
	}

	// ------------------------------------------------------------------
	// Gwiazdy (Galaxy)
	// ------------------------------------------------------------------

	private static void drawStars(VertexConsumer vc, Matrix4f m, double t, int ca, int cb, double inten) {
		int n = 320;
		double rr = R * 0.985;
		for (int i = 0; i < n; i++) {
			double az = hash01(i, 1) * Math.PI * 2.0;
			double el = Math.asin(0.07 + 0.93 * hash01(i, 2));
			double dx = Math.cos(el) * Math.sin(az);
			double dy = Math.sin(el);
			double dz = -Math.cos(el) * Math.cos(az);

			double tw = 0.55 + 0.45 * Math.sin(t * (1.0 + hash01(i, 3) * 2.5) + i);
			double a = (0.35 + 0.65 * hash01(i, 4)) * tw * Math.min(1.0, inten + 0.15);
			double size = rr * (0.0022 + 0.0035 * hash01(i, 5) * hash01(i, 5));
			double pick = hash01(i, 6);
			int rgb = pick < 0.6 ? 0xFFFFFF : (pick < 0.8 ? Theme.mix(ca, 0xFFFFFF, 0.5) : Theme.mix(cb, 0xFFFFFF, 0.5));

			// kierunek "w prawo" i "w gore" na kopule
			double rx = dz * 0 - dy * 0 + (-dz);
			double ry = 0;
			double rz = dx;
			double rl = Math.sqrt(rx * rx + rz * rz);
			if (rl < 1e-6) continue;
			rx /= rl;
			rz /= rl;
			// up2 = cross(right, d)
			double ux = ry * dz - rz * dy;
			double uy = rz * dx - rx * dz;
			double uz = rx * dy - ry * dx;

			double cx = dx * rr, cy = dy * rr, cz = dz * rr;
			int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cbb = rgb & 0xFF;
			int ai = (int) (255 * clamp01(a));
			vc.vertex(m, (float) (cx - rx * size - ux * size), (float) (cy - ry * size - uy * size), (float) (cz - rz * size - uz * size)).color(cr, cg, cbb, ai);
			vc.vertex(m, (float) (cx + rx * size - ux * size), (float) (cy + ry * size - uy * size), (float) (cz + rz * size - uz * size)).color(cr, cg, cbb, ai);
			vc.vertex(m, (float) (cx + rx * size + ux * size), (float) (cy + ry * size + uy * size), (float) (cz + rz * size + uz * size)).color(cr, cg, cbb, ai);
			vc.vertex(m, (float) (cx - rx * size + ux * size), (float) (cy - ry * size + uy * size), (float) (cz - rz * size + uz * size)).color(cr, cg, cbb, ai);
		}
	}

	// ------------------------------------------------------------------
	// Black hole - siatka biegunowa wokol stalego kierunku na niebie
	// ------------------------------------------------------------------

	private static void drawBlackHole(VertexConsumer vc, Matrix4f m, double t, int ca, int cb, double inten) {
		double az0 = 0.6;
		double el0 = 0.75;
		double cxd = Math.cos(el0) * Math.sin(az0);
		double cyd = Math.sin(el0);
		double czd = -Math.cos(el0) * Math.cos(az0);

		// e1 = cross(c, up) znormalizowany, e2 = cross(e1, c)
		double e1x = -czd, e1y = 0, e1z = cxd;
		double l = Math.sqrt(e1x * e1x + e1z * e1z);
		e1x /= l;
		e1z /= l;
		double e2x = e1y * czd - e1z * cyd;
		double e2y = e1z * cxd - e1x * czd;
		double e2z = e1x * cyd - e1y * cxd;

		int nr = 56;
		int ns = 96;
		double maxRad = 0.55;
		double tilt = 0.35;
		double ct = Math.cos(tilt), st = Math.sin(tilt);

		int[][] cols = new int[nr + 1][ns + 1];
		float[][][] pos = new float[nr + 1][ns + 1][3];

		for (int i = 0; i <= nr; i++) {
			double rad = maxRad * Math.pow(i / (double) nr, 1.45);
			for (int j = 0; j <= ns; j++) {
				double phi = Math.PI * 2.0 * j / ns;
				double u = rad * Math.cos(phi);
				double w = rad * Math.sin(phi);

				double dx = cxd + (e1x * u + e2x * w);
				double dy = cyd + (e1y * u + e2y * w);
				double dz = czd + (e1z * u + e2z * w);
				double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
				dx /= dl;
				dy /= dl;
				dz /= dl;
				pos[i][j][0] = (float) (dx * R);
				pos[i][j][1] = (float) (dy * R);
				pos[i][j][2] = (float) (dz * R);

				// uklad dysku (obrocony o tilt)
				double uu = u * ct + w * st;
				double vv = -u * st + w * ct;
				double ax = Math.abs(uu);

				double horizon = 0.085;
				double photon = Math.exp(-sq((rad - 0.1) / 0.011));
				double thin = Math.exp(-sq(vv / 0.013)) * smooth(0.085, 0.14, ax) * Math.exp(-(ax - 0.09) * 5.0);
				double swirl = 0.7 + 0.3 * Math.sin(Math.atan2(vv, uu) * 3.0 + t * 2.2 + ax * 40.0);
				double hot = Math.max(photon * 0.9, thin * swirl);
				double glow = Math.exp(-rad * 9.0) * 0.55;

				int rgb = Theme.mix(ca, cb, 0.5 + 0.5 * Math.sin(Math.atan2(w, u) * 2.0 + t));
				rgb = Theme.mix(rgb, 0xFFFFFF, clamp01(hot * 0.85));
				double a = clamp01(hot + glow) * inten;
				if (rad < horizon) {
					a = 0.97;
					rgb = 0x000000;
				} else {
					double k = smooth(horizon, horizon + 0.01, rad);
					a = a * k + 0.97 * (1.0 - k);
					rgb = Theme.mix(0x000000, rgb, k);
				}
				// brzeg siatki znika
				a *= 1.0 - smooth(maxRad * 0.7, maxRad, rad);
				cols[i][j] = argb(a, rgb);
			}
		}

		for (int i = 0; i < nr; i++) {
			for (int j = 0; j < ns; j++) {
				if ((cols[i][j] >>> 24) + (cols[i + 1][j] >>> 24) + (cols[i + 1][j + 1] >>> 24) + (cols[i][j + 1] >>> 24) == 0) continue;
				pv(vc, m, pos, cols, i, j);
				pv(vc, m, pos, cols, i + 1, j);
				pv(vc, m, pos, cols, i + 1, j + 1);
				pv(vc, m, pos, cols, i, j + 1);
			}
		}
	}

	private static void pv(VertexConsumer vc, Matrix4f m, float[][][] pos, int[][] cols, int i, int j) {
		int c = cols[i][j];
		float[] p = pos[i][j];
		vc.vertex(m, p[0], p[1], p[2]).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
	}

	// ------------------------------------------------------------------
	// Matematyka pomocnicza
	// ------------------------------------------------------------------

	private static int argb(double alpha, int rgb) {
		int a = (int) Math.round(255.0 * clamp01(alpha));
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	private static double sq(double v) {
		return v * v;
	}

	private static double smooth(double e0, double e1, double x) {
		double t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3.0 - 2.0 * t);
	}

	private static double hash01(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}

	private static double lat(int x, int y, int z) {
		int h = x * 374761393 ^ y * 668265263 ^ z * 1274126177;
		h = (h ^ (h >>> 13)) * 1103515245;
		h ^= h >>> 16;
		return (h & 0xFFFF) / 65535.0;
	}

	/** Szum wartosciowy 3D (0..1). */
	private static double noise3(double x, double y, double z) {
		int xi = MathHelper.floor(x), yi = MathHelper.floor(y), zi = MathHelper.floor(z);
		double fx = x - xi, fy = y - yi, fz = z - zi;
		fx = fx * fx * (3 - 2 * fx);
		fy = fy * fy * (3 - 2 * fy);
		fz = fz * fz * (3 - 2 * fz);
		double c00 = lerp(lat(xi, yi, zi), lat(xi + 1, yi, zi), fx);
		double c10 = lerp(lat(xi, yi + 1, zi), lat(xi + 1, yi + 1, zi), fx);
		double c01 = lerp(lat(xi, yi, zi + 1), lat(xi + 1, yi, zi + 1), fx);
		double c11 = lerp(lat(xi, yi + 1, zi + 1), lat(xi + 1, yi + 1, zi + 1), fx);
		return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
	}

	private static double fbm3(double x, double y, double z, int oct) {
		double sum = 0, amp = 0.5, norm = 0;
		for (int i = 0; i < oct; i++) {
			sum += amp * noise3(x, y, z);
			norm += amp;
			x *= 2.03;
			y *= 2.03;
			z *= 2.03;
			amp *= 0.5;
		}
		return sum / norm;
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
