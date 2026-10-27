package com.ghostvisual;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Efekty 3D: niebo, efekty celu, xray, China Hat, slady, shockwave, palka i piorun. */
public final class WorldFx {
	private WorldFx() {
	}

	private static final List<double[]> trail = new ArrayList<>();   // x, y, z, czas
	private static final List<double[]> jumps = new ArrayList<>();   // x, y, z, czas
	private static final List<double[]> shocks = new ArrayList<>();  // x, y, z, start, maxR
	private static final List<double[]> kills = new ArrayList<>();   // x, y, z, dx, dz, start, club, lightning, boltStart, seed
	private static boolean wasOnGround = true;

	private static final long SHOCK_MS = 750;

	public static void addShock(double x, double y, double z, double maxR) {
		addShock(x, y, z, maxR, 0);
	}

	public static void addShock(double x, double y, double z, double maxR, long delayMs) {
		if (shocks.size() > 24) {
			shocks.remove(0);
		}
		shocks.add(new double[]{x, y, z, System.currentTimeMillis() + delayMs, maxR});
	}

	public static void addKill(double x, double y, double z, double dx, double dz, boolean club, boolean lightning) {
		long now = System.currentTimeMillis();
		long boltStart = club ? now + 450 : now;
		if (club) {
			addShock(x, y, z, 3.6, 450);
		}
		if (kills.size() > 8) {
			kills.remove(0);
		}
		kills.add(new double[]{x, y, z, dx, dz, now, club ? 1 : 0, lightning ? 1 : 0, boltStart, now % 100000});
	}

	public static void tick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		Config cfg = Config.get();
		shocks.removeIf(s -> now - s[3] > SHOCK_MS + 100);
		kills.removeIf(k -> now - k[5] > 2500);
		if (mc.player == null || mc.world == null) {
			trail.clear();
			jumps.clear();
			shocks.clear();
			kills.clear();
			return;
		}
		var p = mc.player;
		if (cfg.trails) {
			if (trail.isEmpty() || p.getPos().squaredDistanceTo(trail.get(trail.size() - 1)[0],
				trail.get(trail.size() - 1)[1], trail.get(trail.size() - 1)[2]) > 0.01) {
				trail.add(new double[]{p.getX(), p.getY(), p.getZ(), now});
			}
		}
		double maxAge = cfg.trailSeconds * 1000.0;
		trail.removeIf(t -> now - t[3] > maxAge);
		if (!cfg.trails) {
			trail.clear();
		}
		if (cfg.jumpCircle && wasOnGround && !p.isOnGround() && p.getVelocity().y > 0.2) {
			jumps.add(new double[]{p.getX(), p.getY(), p.getZ(), now});
		}
		wasOnGround = p.isOnGround();
		jumps.removeIf(j -> now - j[3] > 800);
	}

	// ------------------------------------------------------------------ render

	public static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) {
			return;
		}
		MatrixStack ms = ctx.matrixStack();
		VertexConsumerProvider vcp = ctx.consumers();
		if (ms == null || vcp == null) {
			return;
		}
		Camera camera = ctx.camera();
		float td = ctx.tickCounter().getTickDelta(false);
		Draw d = new Draw(ms, camera.getPos());

		for (int mode = 0; mode < 4; mode++) {
			RenderLayer layer = switch (mode) {
				case Draw.SOLID -> RenderLayer.getGui();
				case Draw.ADD -> RenderLayer.getLightning();
				case Draw.XRAY -> RenderLayer.getGuiOverlay();
				default -> RenderLayer.getLines();
			};
			d.begin(vcp.getBuffer(layer), mode);
			d.quadTarget = Draw.ADD;
			scene(d, mc, camera, td);
			if (vcp instanceof VertexConsumerProvider.Immediate imm) {
				imm.draw(layer);
			}
		}
	}

	private static void scene(Draw d, MinecraftClient mc, Camera camera, float td) {
		Config cfg = Config.get();
		Theme th = cfg.theme();
		double t = System.nanoTime() / 1.0e9 * cfg.speed;
		int col = th.accent(cfg.animateColors);
		int col2 = th.accent2;
		long now = System.currentTimeMillis();

		// --- niebo (kopula + gwiazdy + aurora)
		Sky.scene(d, mc, cfg);
		d.quadTarget = Draw.ADD;

		// --- palka po zabiciu (nieprzezroczysta)
		d.quadTarget = Draw.SOLID;
		for (double[] k : kills) {
			if (k[6] > 0.5) {
				club(d, k, now);
			}
		}
		d.quadTarget = Draw.ADD;

		// --- efekty celu
		LivingEntity e = Targets.shown;
		float f = Targets.fade(td);
		if (cfg.targetEnabled && e != null && f > 0.01f) {
			Vec3d p = e.getLerpedPos(td);
			double w = e.getWidth();
			double h = e.getHeight();
			double r = w * 0.5 + 0.35;
			if (cfg.ring) {
				d.ring(p.x, p.y + 0.03, p.z, r + 0.05 * Math.sin(t * 3), 0.14, col, 0.8f * f, 48);
				d.ring(p.x, p.y + 0.031, p.z, r * 0.72, 0.05, col2, 0.55f * f, 48);
			}
			if (cfg.scanner) {
				double yy = p.y + (0.5 + 0.5 * Math.sin(t * 2.0)) * h;
				d.ring(p.x, yy, p.z, r * 0.9, 0.06, col2, 0.85f * f, 40);
				d.wall(p.x, yy - 0.12, p.z, r * 0.9, 0.12, col2, 0f, 0.5f * f, 40);
			}
			if (cfg.orbit) {
				for (int k = 0; k < 3; k++) {
					double ang = t * 2.2 + k * Math.PI * 2.0 / 3.0;
					double ox = p.x + Math.cos(ang) * (r + 0.15);
					double oz = p.z + Math.sin(ang) * (r + 0.15);
					double oy = p.y + h * 0.5 + Math.sin(t * 1.5 + k * 2.1) * h * 0.3;
					d.cube(ox, oy, oz, 0.09, col, 0.95f * f);
					d.cube(ox, oy, oz, 0.16, col2, 0.25f * f);
				}
			}
			if (cfg.glow || cfg.box) {
				Box b = e.getBoundingBox().offset(p.x - e.getX(), p.y - e.getY(), p.z - e.getZ()).expand(0.04);
				if (cfg.glow) {
					d.boxFaces(b, col, (0.12f + 0.05f * (float) Math.sin(t * 3)) * f);
				}
				if (cfg.box) {
					d.boxLines(b, col, 0.95f * f);
				}
			}
		}

		// --- China Hat
		if (cfg.chinaHat != 0) {
			for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
				if (pl.isSpectator() || pl.isInvisible()) {
					continue;
				}
				boolean self = pl == mc.player;
				if (cfg.chinaHat == 1 && !self) {
					continue;
				}
				if (self && !camera.isThirdPerson()) {
					continue;
				}
				hat(d, pl, td, cfg, th, t);
			}
		}

		// --- slady
		if (cfg.trails && trail.size() > 1) {
			double maxAge = cfg.trailSeconds * 1000.0;
			for (int i = 0; i < trail.size() - 1; i++) {
				double[] a = trail.get(i);
				double[] b = trail.get(i + 1);
				float fa = (float) Math.max(0, 1.0 - (now - a[3]) / maxAge);
				float fb = (float) Math.max(0, 1.0 - (now - b[3]) / maxAge);
				int c = Gfx.lerp(col, col2, (i % 40) / 40f);
				d.quad(new double[]{a[0], a[1] + 0.05, a[2]}, new double[]{b[0], b[1] + 0.05, b[2]},
					new double[]{b[0], b[1] + 0.95, b[2]}, new double[]{a[0], a[1] + 0.95, a[2]},
					c, 0.5f * fa, 0.05f * fb);
			}
		}

		// --- okrag przy skoku
		if (cfg.jumpCircle) {
			for (double[] j : jumps) {
				double age = (now - j[3]) / 800.0;
				if (age >= 1) {
					continue;
				}
				d.ring(j[0], j[1] + 0.03, j[2], 0.3 + 1.7 * age, 0.12, col, (float) (0.85 * (1 - age)), 40);
			}
		}

		// --- shockwave
		for (double[] s : shocks) {
			double age = (now - s[3]) / (double) SHOCK_MS;
			if (age < 0 || age > 1) {
				continue;
			}
			double ease = 1 - (1 - age) * (1 - age);
			double r = s[4] * ease;
			float a = (float) Math.pow(1 - age, 1.3);
			d.ring(s[0], s[1] + 0.05, s[2], r, 0.25 + s[4] * 0.04 * (1 - age), col, 0.9f * a, 56);
			d.ring(s[0], s[1] + 0.06, s[2], r * 0.7, 0.12, 0xFFFFFF, 0.55f * a, 48);
			d.wall(s[0], s[1], s[2], r, 0.2 + 0.8 * (1 - age), col2, 0.6f * a, 0f, 56);
		}

		// --- piorun po zabiciu
		for (double[] k : kills) {
			if (k[7] > 0.5) {
				bolt(d, k, now);
			}
		}

		// --- xray (kosci przez sciany)
		if (cfg.xray != 0) {
			d.quadTarget = Draw.XRAY;
			double range = cfg.xrayRange;
			for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
				if (pl.isSpectator()) {
					continue;
				}
				boolean self = pl == mc.player;
				if (!Xray.applies(cfg, self)) {
					continue;
				}
				if (self && !camera.isThirdPerson()) {
					continue;
				}
				if (!self && pl.squaredDistanceTo(mc.player) > range * range) {
					continue;
				}
				Xray.draw(d, pl, td, cfg, th, t);
			}
			d.quadTarget = Draw.ADD;
		}
	}

	// ------------------------------------------------------------------ elementy

	private static void hat(Draw d, AbstractClientPlayerEntity p, float td, Config cfg, Theme th, double t) {
		Vec3d pos = p.getLerpedPos(td);
		double y0 = pos.y + p.getHeight() + 0.01;
		double s = cfg.chinaHatScale;
		double radius = 0.55 * s;
		double height = 0.36 * s;
		int segs = 36;
		double[] apex = {pos.x, y0 + height, pos.z};
		for (int i = 0; i < segs; i++) {
			double a0 = i * Math.PI * 2.0 / segs;
			double a1 = (i + 1) * Math.PI * 2.0 / segs;
			double[] p0 = {pos.x + Math.cos(a0) * radius, y0, pos.z + Math.sin(a0) * radius};
			double[] p1 = {pos.x + Math.cos(a1) * radius, y0, pos.z + Math.sin(a1) * radius};
			float mix = (float) (0.5 + 0.5 * Math.sin(t * 2.0 + i * 0.35));
			int c = Gfx.lerp(th.accent, th.accent2, cfg.animateColors ? mix : (i % 2 == 0 ? 0f : 0.35f));
			d.quad(apex, apex, p1, p0, c, 0.55f, 0.9f);
			if (i % 6 == 0) {
				d.line(apex[0], apex[1], apex[2], p0[0], p0[1], p0[2], 0xFFFFFF, 0.55f);
			}
		}
		d.ring(pos.x, y0 + 0.005, pos.z, radius, 0.05 * s, th.accent2, 0.95f, segs);
	}

	/** Duza palka spadajaca na miejsce zabicia. k = {x,y,z,dx,dz,start,club,lightning,boltStart,seed} */
	private static void club(Draw d, double[] k, long now) {
		double age = (now - k[5]) / 1000.0;
		if (age < 0 || age > 1.7) {
			return;
		}
		double theta;
		if (age < 0.45) {
			double u = age / 0.45;
			theta = -0.7 + (2.0 + 0.7) * u * u;
		} else if (age < 1.2) {
			theta = 2.0 + 0.03 * Math.sin((age - 0.45) * 60) * Math.max(0, 1 - (age - 0.45) * 3);
		} else {
			double u = (age - 1.2) / 0.5;
			theta = 2.0 - 2.7 * u * u;
		}
		double L = 2.6;
		double dx = k[3], dz = k[4];
		double sinF = Math.sin(2.0), cosF = Math.cos(2.0);
		// bufor: glowa palki ladunje 0.5 nad ziemia przy theta = 2.0
		double px = k[0] - dx * sinF * L;
		double py = k[1] + 0.5 - cosF * L;
		double pz = k[2] - dz * sinF * L;
		double ax = dx * Math.sin(theta), ay = Math.cos(theta), az = dz * Math.sin(theta);
		double[] n = {-dz, 0, dx};
		double cxv = ay * n[2] - az * n[1];
		double cyv = az * n[0] - ax * n[2];
		double czv = ax * n[1] - ay * n[0];
		double cl = Math.sqrt(cxv * cxv + cyv * cyv + czv * czv);
		if (cl < 1e-6) {
			return;
		}
		double[] c = {cxv / cl, cyv / cl, czv / cl};
		double[] start = {px, py, pz};
		double[] mid = {px + ax * L * 0.72, py + ay * L * 0.72, pz + az * L * 0.72};
		double[] end = {px + ax * L, py + ay * L, pz + az * L};
		double[] tail = {px - ax * 0.18, py - ay * 0.18, pz - az * 0.18};
		d.solidBox(tail, mid, n, c, 0.055, 0x8A5A2B);
		d.solidBox(mid, end, n, c, 0.19, 0x4A4A55);
		double[] ring1 = {px + ax * L * 0.8, py + ay * L * 0.8, pz + az * L * 0.8};
		double[] ring2 = {px + ax * L * 0.86, py + ay * L * 0.86, pz + az * L * 0.86};
		d.solidBox(ring1, ring2, n, c, 0.22, 0x2B2B33);
	}

	/** Piorun: poszarpana linia z nieba z poswiata. */
	private static void bolt(Draw d, double[] k, long now) {
		double b = (now - k[8]) / 1000.0;
		if (b < 0 || b > 0.6) {
			return;
		}
		double inten;
		if (b < 0.08) {
			inten = 1;
		} else if (b < 0.16) {
			inten = 0.3;
		} else if (b < 0.30) {
			inten = 0.95;
		} else {
			inten = Math.max(0, 1 - (b - 0.30) / 0.30);
		}
		if (inten <= 0.01) {
			return;
		}
		Random r = new Random((long) k[9] * 7919L + 13);
		int n = 16;
		double top = k[1] + 60;
		double[][] path = new double[n + 1][];
		double ox = 0, oz = 0;
		for (int i = 0; i <= n; i++) {
			double frac = i / (double) n;
			if (i == n) {
				ox = 0;
				oz = 0;
			} else if (i > 0) {
				ox += (r.nextDouble() - 0.5) * (2.2 - 1.4 * frac);
				oz += (r.nextDouble() - 0.5) * (2.2 - 1.4 * frac);
			}
			path[i] = new double[]{k[0] + ox, top + (k[1] - top) * frac, k[2] + oz};
		}
		double[] u = {1, 0, 0};
		double[] v = {0, 0, 1};
		drawPath(d, path, u, v, inten, 1.0);
		// odnogi
		for (int br = 0; br < 2; br++) {
			int from = 5 + br * 4;
			double ang = r.nextDouble() * Math.PI * 2;
			double[][] bp = new double[6][];
			double bx = path[from][0], by = path[from][1], bz = path[from][2];
			for (int i = 0; i < 6; i++) {
				bp[i] = new double[]{bx, by, bz};
				bx += Math.cos(ang) * 1.6 + (r.nextDouble() - 0.5);
				bz += Math.sin(ang) * 1.6 + (r.nextDouble() - 0.5);
				by -= 3.0;
			}
			drawPath(d, bp, u, v, inten * 0.8, 0.45);
		}
		// blysk na ziemi
		d.ring(k[0], k[1] + 0.06, k[2], 2.2, 1.6, 0xBFAFFF, (float) (0.35 * inten), 40);
		d.ring(k[0], k[1] + 0.07, k[2], 1.0, 0.9, 0xFFFFFF, (float) (0.5 * inten), 32);
	}

	private static void drawPath(Draw d, double[][] path, double[] u, double[] v, double inten, double scale) {
		for (int i = 0; i < path.length - 1; i++) {
			d.bar(path[i], path[i + 1], u, v, 0.55 * scale, 0xA070FF, (float) (0.28 * inten));
			d.bar(path[i], path[i + 1], u, v, 0.22 * scale, 0xC9B4FF, (float) (0.6 * inten));
			d.bar(path[i], path[i + 1], u, v, 0.07 * scale, 0xFFFFFF, (float) inten);
		}
	}
}
