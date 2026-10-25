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
 * Wszystko rysowane liniami w kolorach aktualnego motywu.
 */
public final class ExtrasRenderer {
	private ExtrasRenderer() {}

	private static final int CIRCLE_TICKS = 22;

	private static final class Circle {
		final Vec3d pos;
		int age = 0;

		Circle(Vec3d pos) {
			this.pos = pos;
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
			// Zmiana swiata / wymiaru - zaczynamy od zera
			lastWorld = mc.world;
			circles.clear();
			trail.clear();
		}

		VisualConfig cfg = VisualConfig.get();

		// Skok = byl na ziemi, teraz w powietrzu i leci w gore
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

		// Slad
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

		Painter p = Painter.begin(ctx);
		if (p == null) return;

		float td = ctx.tickCounter().getTickDelta(false);
		Vec3d cam = ctx.camera().getPos();
		double time = (mc.world.getTime() + td) / 20.0;
		p.t = time;

		if (cfg.jumpCircle) drawCircles(p, cam, td);
		if (cfg.trails) drawTrail(p, mc, cam, td);
		if (cfg.blizzard) drawBlizzard(p, mc, cfg, cam, td, time);
		if (cfg.fireflies) drawFireflies(p, mc, cfg, td, cam, time);

		p.end();
	}

	/** Jump circle: pierscien rozchodzacy sie od miejsca skoku. */
	private static void drawCircles(Painter p, Vec3d cam, float td) {
		for (Circle c : circles) {
			double prog = Math.min(1.0, (c.age + td) / CIRCLE_TICKS);
			double ease = 1.0 - (1.0 - prog) * (1.0 - prog);
			double r = 0.35 + ease * 2.2;
			int alpha = (int) (255 * (1.0 - prog));
			double x = c.pos.x - cam.x;
			double y = c.pos.y - cam.y + 0.03;
			double z = c.pos.z - cam.z;
			p.ring(x, y, z, r, 40, alpha, 0.0);
			p.ring(x, y, z, r * 0.72, 40, alpha / 2, 0.5);
		}
	}

	/** Trails: swiecacy slad za graczem na trzech wysokosciach. */
	private static void drawTrail(Painter p, MinecraftClient mc, Vec3d cam, float td) {
		int n = trail.size();
		if (n < 1) return;
		Vec3d head = mc.player.getLerpedPos(td);
		int total = n + 1;
		double[] heights = {0.08, 0.9, 1.75};
		double[] weights = {1.0, 0.6, 0.8};

		for (int h = 0; h < heights.length; h++) {
			for (int i = 1; i < total; i++) {
				Vec3d a = trail.get(i - 1);
				Vec3d b = (i < n) ? trail.get(i) : head;
				double frac = i / (double) total;
				int alpha = (int) (255 * frac * frac * weights[h]);
				if (alpha < 6) continue;
				p.line(a.x - cam.x, a.y - cam.y + heights[h], a.z - cam.z,
						b.x - cam.x, b.y - cam.y + heights[h], b.z - cam.z,
						frac * 0.6, alpha);
			}
		}
	}

	/** Blizzard: snieg i wiatr wokol gracza (platki widac tylko nad powierzchnia, nie pod dachem). */
	private static void drawBlizzard(Painter p, MinecraftClient mc, VisualConfig cfg, Vec3d cam, float td, double time) {
		Vec3d pc = mc.player.getLerpedPos(td);
		int count = (int) (140 * cfg.blizzardDensity);
		double radius = 14.0;
		double size = radius * 2.0;
		double heightSpan = 14.0;
		int snow = Theme.mix(0xFFFFFF, p.theme().fx(0.3), 0.3);

		for (int i = 0; i < count; i++) {
			double rs = hash(i, 4);
			double fall = 4.0 + 3.0 * rs;
			double wx = 2.5 + 1.5 * rs;
			double wz = 0.8 + 1.6 * hash(i, 5);

			double ux = hash(i, 1) * size + wx * time;
			double uz = hash(i, 2) * size + wz * time;
			double uy = hash(i, 3) * heightSpan - fall * time;

			double x = pc.x - radius + mod(ux - (pc.x - radius), size);
			double z = pc.z - radius + mod(uz - (pc.z - radius), size);
			double y = pc.y - 4.0 + mod(uy - (pc.y - 4.0), heightSpan);

			int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
			if (y < top) continue;

			double tx = x - wx * 0.06;
			double ty = y + fall * 0.06;
			double tz = z - wz * 0.06;
			int alpha = 120 + (int) (90 * hash(i, 6));
			p.lineThin(x - cam.x, y - cam.y, z - cam.z, tx - cam.x, ty - cam.y, tz - cam.z, snow, alpha);
		}
	}

	/** Fireflies: swiecace migajace krzyzyki blakajace sie nad terenem wokol gracza. */
	private static void drawFireflies(Painter p, MinecraftClient mc, VisualConfig cfg, float td, Vec3d cam, double time) {
		Vec3d pc = mc.player.getLerpedPos(td);
		int count = (int) cfg.fireflyCount;
		double radius = 16.0;
		double size = radius * 2.0;

		for (int i = 0; i < count; i++) {
			double vx = (hash(i, 12) - 0.5) * 0.6;
			double vz = (hash(i, 13) - 0.5) * 0.6;
			double ux = hash(i, 10) * size + vx * time;
			double uz = hash(i, 11) * size + vz * time;

			double x = pc.x - radius + mod(ux - (pc.x - radius), size);
			double z = pc.z - radius + mod(uz - (pc.z - radius), size);
			x += Math.sin(time * (0.7 + hash(i, 14)) + i) * 0.8;
			z += Math.cos(time * (0.6 + hash(i, 15)) + i * 1.3) * 0.8;

			int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
			double y = top + 0.5 + 1.2 * (0.5 + 0.5 * Math.sin(time * (0.5 + hash(i, 16)) + i * 2.1));

			double s = Math.sin(time * (1.2 + hash(i, 17) * 1.5) + i * 1.7);
			double flicker = Math.pow(Math.max(0.0, s), 2.0);
			int alpha = (int) (255 * (0.12 + 0.88 * flicker));
			double size3 = 0.045 + 0.02 * flicker;

			p.cross(x - cam.x, y - cam.y, z - cam.z, size3, hash(i, 18), alpha);
		}
	}

	// ------------------------------------------------------------------

	/** Dodatnia reszta z dzielenia. */
	private static double mod(double v, double m) {
		return ((v % m) + m) % m;
	}

	/** Prosty deterministyczny "losowy" ulamek 0..1 dla (indeks, sol). */
	private static double hash(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}
}
