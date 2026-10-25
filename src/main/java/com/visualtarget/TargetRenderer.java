package com.visualtarget;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Znajduje moba/gracza, w ktorego celujesz, i rysuje wokol niego efekty.
 * Efekty sa rysowane liniami, wiec chowaja sie za blokami.
 * Pojawiaja sie i znikaja plynnie (fade).
 */
public final class TargetRenderer {
	private TargetRenderer() {}

	private static LivingEntity lastTarget;
	private static float fade = 0f;
	private static long lastNanos = System.nanoTime();

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(TargetRenderer::onRender);
	}

	private static void onRender(WorldRenderContext ctx) {
		VisualConfig cfg = VisualConfig.get();

		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;

		boolean anyEffect = cfg.box || cfg.ring || cfg.scanner || cfg.orbit || cfg.ghost;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!cfg.targetEnabled || !anyEffect || mc.world == null || mc.player == null) {
			fade = 0f;
			lastTarget = null;
			return;
		}

		float tickDelta = ctx.tickCounter().getTickDelta(false);

		// Plynne pojawianie sie / znikanie
		LivingEntity found = findTarget(mc, tickDelta, cfg);
		if (found != null) {
			lastTarget = found;
			fade = Math.min(1f, fade + dt * 6f);
		} else {
			fade = Math.max(0f, fade - dt * 5f);
		}

		LivingEntity target = lastTarget;
		if (target != null && (target.isRemoved() || !target.isAlive())) {
			lastTarget = null;
			fade = 0f;
			return;
		}
		if (target == null || fade <= 0.01f) {
			if (fade <= 0.01f) lastTarget = null;
			return;
		}

		Painter p = Painter.begin(ctx);
		if (p == null) return;

		float ease = fade * fade * (3f - 2f * fade);

		// Pozycja stop celu wzgledem kamery
		Vec3d cam = ctx.camera().getPos();
		Vec3d feet = target.getLerpedPos(tickDelta).subtract(cam);
		double width = target.getWidth();
		double height = target.getHeight();
		double half = width / 2.0;
		double grow = 0.75 + 0.25 * ease;

		// Czas animacji w sekundach * predkosc
		double t = (mc.world.getTime() + tickDelta) / 20.0 * cfg.speed;
		p.t = t;
		p.mult = ease;

		// 1) Obrys hitboxa
		if (cfg.box) {
			double pad = 0.02;
			p.box(feet.x - half - pad, feet.y - pad, feet.z - half - pad,
					feet.x + half + pad, feet.y + height + pad, feet.z + half + pad, 255);
		}

		// 2) Pierscien pod nogami + obracajace sie znaczniki
		if (cfg.ring) {
			double r = (half + 0.45 + 0.08 * Math.sin(t * 3.0)) * grow;
			p.ring(feet.x, feet.y + 0.02, feet.z, r, 56, 255, 0.0);
			p.ring(feet.x, feet.y + 0.02, feet.z, r + 0.18, 56, 110, 0.5);
			for (int k = 0; k < 4; k++) {
				double a = t * 1.5 + k * Math.PI / 2.0;
				double cx = Math.cos(a);
				double cz = Math.sin(a);
				p.line(
						feet.x + cx * (r - 0.15), feet.y + 0.02, feet.z + cz * (r - 0.15),
						feet.x + cx * (r + 0.33), feet.y + 0.02, feet.z + cz * (r + 0.33),
						k / 4.0, 255);
			}
		}

		// 3) Pierscien skanujacy, ktory jezdzi w gore i w dol (z "ogonem")
		if (cfg.scanner) {
			double radius = (half + 0.25) * grow;
			for (int k = 0; k < 4; k++) {
				double tt = t - k * 0.1;
				double y = feet.y + height * (0.5 + 0.5 * Math.sin(tt * 2.5));
				int alpha = k == 0 ? 255 : (k == 1 ? 130 : (k == 2 ? 70 : 30));
				p.ring(feet.x, y, feet.z, radius, 44, alpha, 0.0);
			}
		}

		// 4) Swiecace punkty krazace wokol celu (z ogonami)
		if (cfg.orbit) {
			drawOrbit(p, feet, half * grow, height, t);
		}

		// 5) GHOST - wiatr krecacy sie spiralnie wokol celu
		if (cfg.ghost) {
			drawGhost(p, feet, half * grow, height, t);
		}

		p.end();
	}

	/** Wiatr: kilka smug, ktore spiralnie wznosza sie wokol celu i znikaja na koncach. */
	private static void drawGhost(Painter p, Vec3d feet, double half, double height, double t) {
		int streaks = 7;
		int segs = 22;
		double trail = 0.45;               // dlugosc smugi (czesc wysokosci)
		double span = height + 0.5;

		for (int s = 0; s < streaks; s++) {
			double seed = s * 1.618;
			double speed = 0.30 + 0.07 * (s % 3);
			double cycle = t * speed + s / (double) streaks;
			double head = (cycle - Math.floor(cycle)) * (1.0 + trail);
			double baseAngle = seed * 2.399;
			double turns = 1.6 + 0.35 * (s % 2);

			boolean havePrev = false;
			double px = 0, py = 0, pz = 0;
			for (int k = 0; k <= segs; k++) {
				double prog = head - trail * k / segs;
				if (prog < 0.0 || prog > 1.0) {
					havePrev = false;
					continue;
				}
				double ang = baseAngle + prog * turns * Math.PI * 2.0 + t * 1.4;
				double rad = half + 0.28 + 0.14 * Math.sin(prog * Math.PI * 2.0 + seed);
				double x = feet.x + Math.cos(ang) * rad;
				double y = feet.y - 0.15 + prog * span;
				double z = feet.z + Math.sin(ang) * rad;

				if (havePrev) {
					double tail = 1.0 - (double) k / segs;                       // koniec smugi blaknie
					double edge = Math.min(1.0, Math.sin(prog * Math.PI) * 2.0); // gora i dol blakna
					int alpha = (int) (235 * tail * tail * edge);
					if (alpha > 6) {
						p.line(px, py, pz, x, y, z, prog * 0.4 + s * 0.05, alpha);
					}
				}
				px = x;
				py = y;
				pz = z;
				havePrev = true;
			}
		}
	}

	/** Cztery swiecace punkty, ktore krazy wokol celu zostawiajac krotki ogon. */
	private static void drawOrbit(Painter p, Vec3d feet, double half, double height, double t) {
		int count = 4;
		int trailSegs = 10;
		double radius = half + 0.5;

		for (int i = 0; i < count; i++) {
			double dir = (i % 2 == 0) ? 1.0 : -1.0;
			double baseAngle = i * (2.0 * Math.PI / count);
			double px = 0, py = 0, pz = 0;
			for (int k = 0; k <= trailSegs; k++) {
				double tt = t - k * 0.035;
				double ang = baseAngle + dir * tt * 2.2;
				double y = feet.y + height * (0.5 + 0.42 * Math.sin(tt * 1.6 + i * 1.7));
				double x = feet.x + Math.cos(ang) * radius;
				double z = feet.z + Math.sin(ang) * radius;
				double phase = i / (double) count;
				if (k == 0) {
					p.cross(x, y, z, 0.07, phase, 255);
				} else {
					int alpha = (int) (230 * (1.0 - (double) k / (trailSegs + 1)));
					p.line(px, py, pz, x, y, z, phase, alpha);
				}
				px = x;
				py = y;
				pz = z;
			}
		}
	}

	/** Raycast od kamery - zwraca zywa istote, w ktora celujesz (i ktorej nie zaslania blok). */
	private static LivingEntity findTarget(MinecraftClient mc, float tickDelta, VisualConfig cfg) {
		Entity cam = mc.getCameraEntity();
		if (cam == null) return null;

		double range = cfg.range;
		Vec3d start = cam.getCameraPosVec(tickDelta);
		Vec3d dir = cam.getRotationVec(tickDelta);
		Vec3d end = start.add(dir.multiply(range));

		// Jesli blok jest na drodze, cel za nim sie nie liczy
		double maxDistSq = range * range;
		HitResult blockHit = mc.world.raycast(new RaycastContext(
				start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, cam));
		if (blockHit.getType() != HitResult.Type.MISS) {
			maxDistSq = blockHit.getPos().squaredDistanceTo(start);
		}

		Box searchBox = cam.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.raycast(
				cam, start, end, searchBox,
				e -> e instanceof LivingEntity
						&& !e.isSpectator()
						&& e.canHit()
						&& (!cfg.playersOnly || e instanceof PlayerEntity),
				maxDistSq);

		if (hit != null && hit.getEntity() instanceof LivingEntity living) {
			return living;
		}
		return null;
	}
}
