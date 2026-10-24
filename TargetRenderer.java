package com.visualtarget;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
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
		if (!cfg.enabled || !anyEffect || mc.world == null || mc.player == null) {
			fade = 0f;
			lastTarget = null;
			return;
		}

		MatrixStack matrices = ctx.matrixStack();
		VertexConsumerProvider consumers = ctx.consumers();
		if (matrices == null || consumers == null) return;

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

		Painter p = new Painter(consumers.getBuffer(RenderLayer.getLines()), matrices.peek(), cfg, t, ease);

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
						p.rgb(k / 4.0), 255);
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

		// Od razu wypchnij linie na ekran
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}
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
					double tail = 1.0 - (double) k / segs;                   // koniec smugi blaknie
					double edge = Math.min(1.0, Math.sin(prog * Math.PI) * 2.0); // gora i dol blakna
					int alpha = (int) (235 * tail * tail * edge);
					if (alpha > 6) {
						p.line(px, py, pz, x, y, z, p.rgb(prog * 0.4 + s * 0.05), alpha);
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
				int color = p.rgb(i / (double) count);
				if (k == 0) {
					p.cross(x, y, z, 0.07, color, 255);
				} else {
					int alpha = (int) (230 * (1.0 - (double) k / (trailSegs + 1)));
					p.line(px, py, pz, x, y, z, color, alpha);
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

	/** Pomocnik do rysowania linii, pierscieni i boxow w wybranym kolorze (z opcjonalnym glow). */
	private static final class Painter {
		private final VertexConsumer vc;
		private final MatrixStack.Entry entry;
		private final VisualConfig cfg;
		private final double t;
		private final float mult;
		private final int baseRgb;

		Painter(VertexConsumer vc, MatrixStack.Entry entry, VisualConfig cfg, double t, float mult) {
			this.vc = vc;
			this.entry = entry;
			this.cfg = cfg;
			this.t = t;
			this.mult = mult;
			this.baseRgb = VisualConfig.colorFromPos(cfg.colorPos);
		}

		/** Kolor RGB; w trybie rainbow zalezy od czasu i fazy (0..1). */
		int rgb(double phase) {
			if (cfg.rainbow) {
				float hue = (float) (((t * 0.25 + phase) % 1.0 + 1.0) % 1.0);
				return MathHelper.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
			}
			return baseRgb;
		}

		void line(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, int alpha) {
			int a = Math.max(0, Math.min(255, (int) (alpha * mult)));
			if (a <= 0) return;

			float dx = (float) (x2 - x1);
			float dy = (float) (y2 - y1);
			float dz = (float) (z2 - z1);
			float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1.0e-6f) return;
			dx /= len;
			dy /= len;
			dz /= len;

			raw(x1, y1, z1, x2, y2, z2, rgb, a, dx, dy, dz);

			if (cfg.glow) {
				// Dwie osie prostopadle do linii -> 4 slabsze kopie dookola = miekka poswiata
				float rx = Math.abs(dy) > 0.9f ? 1f : 0f;
				float ry = Math.abs(dy) > 0.9f ? 0f : 1f;
				float p1x = -dz * ry;
				float p1y = dz * rx;
				float p1z = dx * ry - dy * rx;
				float l1 = MathHelper.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
				if (l1 < 1.0e-6f) return;
				p1x /= l1;
				p1y /= l1;
				p1z /= l1;
				float p2x = dy * p1z - dz * p1y;
				float p2y = dz * p1x - dx * p1z;
				float p2z = dx * p1y - dy * p1x;

				double e = 0.011;
				int ga = a / 3;
				if (ga <= 0) return;
				raw(x1 + p1x * e, y1 + p1y * e, z1 + p1z * e, x2 + p1x * e, y2 + p1y * e, z2 + p1z * e, rgb, ga, dx, dy, dz);
				raw(x1 - p1x * e, y1 - p1y * e, z1 - p1z * e, x2 - p1x * e, y2 - p1y * e, z2 - p1z * e, rgb, ga, dx, dy, dz);
				raw(x1 + p2x * e, y1 + p2y * e, z1 + p2z * e, x2 + p2x * e, y2 + p2y * e, z2 + p2z * e, rgb, ga, dx, dy, dz);
				raw(x1 - p2x * e, y1 - p2y * e, z1 - p2z * e, x2 - p2x * e, y2 - p2y * e, z2 - p2z * e, rgb, ga, dx, dy, dz);
			}
		}

		private void raw(double x1, double y1, double z1, double x2, double y2, double z2,
						 int rgb, int a, float nx, float ny, float nz) {
			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;
			vc.vertex(entry, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(entry, nx, ny, nz);
			vc.vertex(entry, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(entry, nx, ny, nz);
		}

		void ring(double cx, double cy, double cz, double radius, int segments, int alpha, double phaseOffset) {
			double px = cx + radius;
			double pz = cz;
			for (int i = 1; i <= segments; i++) {
				double a = 2.0 * Math.PI * i / segments;
				double nx = cx + Math.cos(a) * radius;
				double nz = cz + Math.sin(a) * radius;
				line(px, cy, pz, nx, cy, nz, rgb(phaseOffset + (double) i / segments), alpha);
				px = nx;
				pz = nz;
			}
		}

		void box(double x0, double y0, double z0, double x1, double y1, double z1, int alpha) {
			int bottom = rgb(0.0);
			int top = rgb(0.5);
			// dol
			line(x0, y0, z0, x1, y0, z0, bottom, alpha);
			line(x1, y0, z0, x1, y0, z1, bottom, alpha);
			line(x1, y0, z1, x0, y0, z1, bottom, alpha);
			line(x0, y0, z1, x0, y0, z0, bottom, alpha);
			// gora
			line(x0, y1, z0, x1, y1, z0, top, alpha);
			line(x1, y1, z0, x1, y1, z1, top, alpha);
			line(x1, y1, z1, x0, y1, z1, top, alpha);
			line(x0, y1, z1, x0, y1, z0, top, alpha);
			// pionowe krawedzie
			line(x0, y0, z0, x0, y1, z0, bottom, alpha);
			line(x1, y0, z0, x1, y1, z0, bottom, alpha);
			line(x1, y0, z1, x1, y1, z1, bottom, alpha);
			line(x0, y0, z1, x0, y1, z1, bottom, alpha);
		}

		void cross(double x, double y, double z, double s, int rgb, int alpha) {
			line(x - s, y, z, x + s, y, z, rgb, alpha);
			line(x, y - s, z, x, y + s, z, rgb, alpha);
			line(x, y, z - s, x, y, z + s, rgb, alpha);
		}
	}
}
