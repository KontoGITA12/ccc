package com.visualtarget;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dodatki: Jump circle, Trails, Blizzard, Fireflies.
 * Miekkie, polprzezroczyste ksztalty w kolorach aktualnego motywu.
 */
public final class ExtrasRenderer {
	private ExtrasRenderer() {}

	private static final int CIRCLE_TICKS = 26;

	private static final class Circle {
		final double x, y, z;
		final int seed = (int) (Math.random() * 100000.0);
		int age = 0;

		Circle(Vec3d p) {
			this.x = p.x;
			this.y = p.y;
			this.z = p.z;
		}
	}

	private static final List<Circle> circles = new ArrayList<>();
	private static final List<Vec3d> trail = new ArrayList<>();
	private static boolean wasOnGround = true;
	private static ClientWorld lastWorld;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ExtrasRenderer::onTick);
		WorldRenderEvents.AFTER_ENTITIES.register(ExtrasRenderer::onRender);
	}

	// ------------------------------------------------------------------
	// Logika co tick (skoki i zapis sladu)
	// ------------------------------------------------------------------

	private static void onTick(MinecraftClient mc) {
		ClientPlayerEntity pl = mc.player;
		if (pl == null || mc.world == null) {
			circles.clear();
			trail.clear();
			lastWorld = null;
			return;
		}
		if (mc.world != lastWorld) {
			lastWorld = mc.world;
			circles.clear();
			trail.clear();
		}

		VisualConfig cfg = VisualConfig.get();

		boolean onGround = pl.isOnGround();
		if (cfg.jumpCircle && wasOnGround && !onGround && pl.getVelocity().y > 0.05) {
			circles.add(new Circle(pl.getPos()));
		}
		wasOnGround = onGround;

		Iterator<Circle> it = circles.iterator();
		while (it.hasNext()) {
			Circle c = it.next();
			c.age++;
			if (c.age >= CIRCLE_TICKS) it.remove();
		}

		if (cfg.trails) {
			Vec3d pos = pl.getPos();
			if (!trail.isEmpty() && trail.get(trail.size() - 1).squaredDistanceTo(pos) > 64.0) {
				trail.clear(); // teleport
			}
			trail.add(pos);
			int max = (int) (cfg.trailSeconds * 20f) + 2;
			while (trail.size() > max) trail.remove(0);
		} else {
			trail.clear();
		}
	}

	// ------------------------------------------------------------------
	// Rysowanie
	// ------------------------------------------------------------------

	private static void onRender(WorldRenderContext ctx) {
		VisualConfig cfg = VisualConfig.get();
		if (!(cfg.jumpCircle || cfg.trails || cfg.blizzard || cfg.fireflies)) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;

		Fx fx = Fx.begin(ctx);
		if (fx == null) return;

		float td = ctx.tickCounter().getTickDelta(false);
		Vec3d cam = ctx.camera().getPos();
		double time = (mc.world.getTime() + td) / 20.0;

		if (cfg.jumpCircle) drawCircles(fx, cam, td);
		if (cfg.trails) drawTrail(fx, mc, cam, td, time);
		if (cfg.blizzard) drawBlizzard(fx, mc, cfg, cam, td, time);
		if (cfg.fireflies) drawFireflies(fx, mc, cfg, cam, td, time);

		fx.end();
	}

	/**
	 * Jump circle: fala uderzeniowa na ziemi (jasna krawedz + zanikajacy ogon), druga mniejsza fala,
	 * miekka poswiata i klebki kurzu rozchodzace sie na boki.
	 */
	private static void drawCircles(Fx fx, Vec3d cam, float td) {
		Theme th = fx.theme;
		for (Circle c : circles) {
			double prog = Math.min(1.0, (c.age + td) / CIRCLE_TICKS);
			double ease = 1.0 - Math.pow(1.0 - prog, 3.0);
			double fade = 1.0 - prog;

			double x = c.x - cam.x;
			double y = c.y - cam.y + 0.02;
			double z = c.z - cam.z;

			double ph = c.age * 0.02;
			int body = Theme.mix(th.fx(ph), th.glow(ph), 0.5);
			int edge = Theme.mix(th.glow(ph), 0xFFFFFF, 0.35);
			int dust = Theme.mix(0x8E8E99, th.glow(ph), 0.25);

			// poswiata na ziemi
			fx.groundRing(x, y, z, 0.0, 0.3 + ease * 2.4, 40,
					Fx.argb(95 * fade * fade, body), Fx.argb(0, body));

			// glowna fala: jasna krawedz z wyciagnietym ogonem do srodka
			double r = 0.3 + ease * 2.7;
			double w = 0.65 * (1.0 - prog * 0.55);
			fx.groundRing(x, y + 0.004, z, Math.max(0.0, r - w), r, 56,
					Fx.argb(0, body), Fx.argb(235 * Math.pow(fade, 1.3), body));
			fx.groundRing(x, y + 0.008, z, r - 0.045, r, 56,
					Fx.argb(110 * fade, edge), Fx.argb(255 * fade, edge));

			// druga fala, lekko opozniona
			if (prog > 0.1) {
				double p2 = (prog - 0.1) / 0.9;
				double e2 = 1.0 - Math.pow(1.0 - p2, 3.0);
				double r2 = 0.2 + e2 * 1.7;
				double w2 = 0.35 * (1.0 - p2 * 0.6);
				double f2 = 1.0 - p2;
				fx.groundRing(x, y + 0.004, z, Math.max(0.0, r2 - w2), r2, 48,
						Fx.argb(0, body), Fx.argb(150 * f2 * f2, edge));
			}

			// kurz
			for (int k = 0; k < 12; k++) {
				double a = Fx.hash(k, c.seed) * Math.PI * 2.0;
				double dist = ease * (1.2 + 1.1 * Fx.hash(k, c.seed + 1));
				double hgt = 0.04 + 0.55 * prog * Fx.hash(k, c.seed + 2);
				double sz = 0.10 + 0.32 * prog * (0.6 + 0.4 * Fx.hash(k, c.seed + 3));
				fx.disc(x + Math.cos(a) * dist, y + hgt, z + Math.sin(a) * dist, sz,
						Fx.argb(95 * fade * fade, dust));
			}
		}
	}

	/**
	 * Trails: gladka wstega za graczem (Catmull-Rom), zwezajaca sie klinem ku ogonowi,
	 * z jasna linia przy ziemi.
	 */
	private static void drawTrail(Fx fx, MinecraftClient mc, Vec3d cam, float td, double time) {
		int n = trail.size();
		if (n < 2) return;

		Vec3d head = mc.player.getLerpedPos(td);
		int total = n + 1;
		int sub = 3;
		int cnt = (total - 1) * sub + 1;
		double[] px = new double[cnt];
		double[] py = new double[cnt];
		double[] pz = new double[cnt];

		for (int i = 0; i < total - 1; i++) {
			Vec3d p0 = pt(i - 1, n, head);
			Vec3d p1 = pt(i, n, head);
			Vec3d p2 = pt(i + 1, n, head);
			Vec3d p3 = pt(i + 2, n, head);
			for (int s = 0; s < sub; s++) {
				double u = s / (double) sub;
				int k = i * sub + s;
				px[k] = catmull(p0.x, p1.x, p2.x, p3.x, u);
				py[k] = catmull(p0.y, p1.y, p2.y, p3.y, u);
				pz[k] = catmull(p0.z, p1.z, p2.z, p3.z, u);
			}
		}
		px[cnt - 1] = head.x;
		py[cnt - 1] = head.y;
		pz[cnt - 1] = head.z;

		Theme th = fx.theme;
		for (int k = 0; k < cnt - 1; k++) {
			double f0 = k / (double) (cnt - 1);
			double f1 = (k + 1) / (double) (cnt - 1);
			double a0 = Math.pow(f0, 1.5);
			double a1 = Math.pow(f1, 1.5);
			double top0 = 0.10 + 0.95 * f0;
			double top1 = 0.10 + 0.95 * f1;
			int c0 = Theme.mix(th.fx(f0 * 0.8 + time * 0.1), th.glow(f0), 0.35);
			int c1 = Theme.mix(th.fx(f1 * 0.8 + time * 0.1), th.glow(f1), 0.35);

			double x0 = px[k] - cam.x, y0 = py[k] - cam.y, z0 = pz[k] - cam.z;
			double x1 = px[k + 1] - cam.x, y1 = py[k + 1] - cam.y, z1 = pz[k + 1] - cam.z;

			// glowna wstega: przy ziemi mocniej, ku gorze zanika
			fx.quad2(x0, y0 + 0.03, z0, Fx.argb(150 * a0, c0),
					x1, y1 + 0.03, z1, Fx.argb(150 * a1, c1),
					x1, y1 + top1, z1, Fx.argb(0, c1),
					x0, y0 + top0, z0, Fx.argb(0, c0));

			// jasna linia przy ziemi
			int e0 = Theme.mix(th.glow(f0), 0xFFFFFF, 0.3);
			int e1 = Theme.mix(th.glow(f1), 0xFFFFFF, 0.3);
			fx.quad2(x0, y0 + 0.02, z0, Fx.argb(230 * a0, e0),
					x1, y1 + 0.02, z1, Fx.argb(230 * a1, e1),
					x1, y1 + 0.10, z1, Fx.argb(0, e1),
					x0, y0 + 0.10, z0, Fx.argb(0, e0));
		}
	}

	private static Vec3d pt(int i, int n, Vec3d head) {
		if (i < 0) i = 0;
		if (i >= n) return head;
		return trail.get(i);
	}

	private static double catmull(double p0, double p1, double p2, double p3, double u) {
		double u2 = u * u;
		double u3 = u2 * u;
		return 0.5 * ((2.0 * p1) + (-p0 + p2) * u + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2
				+ (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3);
	}

	/**
	 * Blizzard: miekkie platki sniegu (okragle plamki), porywy wiatru, kolysanie,
	 * znikaja z daleka i pod dachem.
	 */
	private static void drawBlizzard(Fx fx, MinecraftClient mc, VisualConfig cfg, Vec3d cam, float td, double time) {
		Vec3d pc = mc.player.getLerpedPos(td);
		int count = (int) (380 * cfg.blizzardDensity);
		double radius = 16.0;
		double size = radius * 2.0;
		double heightSpan = 16.0;

		double gx = 2.6 + 1.8 * Math.sin(time * 0.37);
		double gz = 0.9 + 1.2 * Math.sin(time * 0.23 + 1.0);
		int tint = Theme.mix(0xFFFFFF, fx.theme.glow(0.3), 0.18);

		for (int i = 0; i < count; i++) {
			double rs = Fx.hash(i, 4);
			double fall = 2.0 + 1.6 * rs;
			double wind = 0.7 + 0.6 * rs;

			double ux = Fx.hash(i, 1) * size + gx * wind * time;
			double uz = Fx.hash(i, 2) * size + gz * wind * time;
			double uy = Fx.hash(i, 3) * heightSpan - fall * time;

			double x = pc.x - radius + Fx.mod(ux - (pc.x - radius), size);
			double z = pc.z - radius + Fx.mod(uz - (pc.z - radius), size);
			double y = pc.y - 4.0 + Fx.mod(uy - (pc.y - 4.0), heightSpan);

			x += Math.sin(time * (0.8 + rs) + i) * 0.35;
			z += Math.cos(time * (0.7 + rs) + i * 1.3) * 0.35;

			int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
			if (y < top) continue;

			double d = Math.hypot(x - pc.x, z - pc.z);
			double edge = 1.0 - Fx.smooth(11.0, 16.0, d);
			if (edge <= 0.0) continue;

			double flake = 0.025 + 0.04 * Fx.hash(i, 7);
			int alpha = (int) ((150 + 90 * Fx.hash(i, 6)) * edge);
			fx.disc(x - cam.x, y - cam.y, z - cam.z, flake, Fx.argb(alpha, tint));
		}
	}

	/**
	 * Fireflies: ciepłe zolto-zielone swietliki, plynnie bladzace nad terenem,
	 * pulsujace blask z halo. Wieczorem i w nocy swieca mocniej.
	 */
	private static void drawFireflies(Fx fx, MinecraftClient mc, VisualConfig cfg, Vec3d cam, float td, double time) {
		Vec3d pc = mc.player.getLerpedPos(td);
		int count = (int) cfg.fireflyCount;
		double radius = 18.0;
		double size = radius * 2.0;

		double night = nightFactor(mc);
		double visibility = 0.35 + 0.65 * night;

		for (int i = 0; i < count; i++) {
			double h1 = Fx.hash(i, 14);
			double h2 = Fx.hash(i, 15);

			double ax = pc.x - radius + Fx.mod(Fx.hash(i, 10) * size - (pc.x - radius), size);
			double az = pc.z - radius + Fx.mod(Fx.hash(i, 11) * size - (pc.z - radius), size);

			double x = ax + Math.sin(time * 0.35 * (0.6 + h1) + i * 1.7) * 1.8
					+ Math.sin(time * 0.9 + i) * 0.3;
			double z = az + Math.cos(time * 0.30 * (0.6 + h2) + i * 1.3) * 1.8
					+ Math.cos(time * 0.8 + i * 0.7) * 0.3;

			int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
			double y = top + 0.6 + 1.1 * (0.5 + 0.5 * Math.sin(time * 0.4 * (0.5 + h1) + i * 2.1));

			double d = Math.hypot(x - pc.x, z - pc.z);
			double edge = 1.0 - Fx.smooth(13.0, 18.0, d);
			if (edge <= 0.0) continue;

			double pulse = 0.5 + 0.5 * Math.sin(time * (0.9 + 1.1 * Fx.hash(i, 17)) + i * 2.3);
			double glow = pulse * pulse * pulse;
			double a = (0.25 + 0.75 * glow) * visibility * edge;

			int color = Theme.mix(0xC9F050, fx.theme.glow(Fx.hash(i, 18)), 0.2);
			double px = x - cam.x, py = y - cam.y, pz = z - cam.z;
			double s = 0.035 + 0.02 * glow;

			fx.disc(px, py, pz, s * 6.0, Fx.argb(70 * a, color));
			fx.disc(px, py, pz, s * 2.2, Fx.argb(190 * a, color));
			fx.disc(px, py, pz, s * 0.9, Fx.argb(255 * a, Theme.mix(color, 0xFFFFFF, 0.6)));
		}
	}

	/** 0 = dzien, 1 = noc (z plynnym przejsciem o zmierzchu i switie). */
	private static double nightFactor(MinecraftClient mc) {
		long tod = mc.world.getTimeOfDay() % 24000L;
		if (tod < 12000L) return 0.0;
		if (tod < 13800L) return (tod - 12000L) / 1800.0;
		if (tod < 22200L) return 1.0;
		return (24000L - tod) / 1800.0;
	}
}
