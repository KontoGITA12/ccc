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
 * Efekty sa rysowane liniami, wiec chowaja sie za blokami (zero "wallhacka").
 */
public final class TargetRenderer {
	private TargetRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(TargetRenderer::onRender);
	}

	private static void onRender(WorldRenderContext ctx) {
		VisualConfig cfg = VisualConfig.get();
		if (!cfg.enabled || !(cfg.box || cfg.ring || cfg.scanner || cfg.orbit)) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;

		MatrixStack matrices = ctx.matrixStack();
		VertexConsumerProvider consumers = ctx.consumers();
		if (matrices == null || consumers == null) return;

		float tickDelta = ctx.tickCounter().getTickDelta(false);

		LivingEntity target = findTarget(mc, tickDelta, cfg);
		if (target == null) return;

		// Pozycja stop celu wzgledem kamery (swiat jest przesuniety o -kamera)
		Vec3d cam = ctx.camera().getPos();
		Vec3d feet = target.getLerpedPos(tickDelta).subtract(cam);
		double width = target.getWidth();
		double height = target.getHeight();
		double half = width / 2.0;

		// Czas animacji w sekundach * predkosc
		double t = (mc.world.getTime() + tickDelta) / 20.0 * cfg.speed;

		Painter p = new Painter(consumers.getBuffer(RenderLayer.getLines()), matrices.peek(), cfg, t);

		// 1) Obrys hitboxa
		if (cfg.box) {
			p.box(feet.x - half, feet.y, feet.z - half, feet.x + half, feet.y + height, feet.z + half, 255);
		}

		// 2) Pierscien pod nogami + obracajace sie znaczniki
		if (cfg.ring) {
			double r = half + 0.45 + 0.08 * Math.sin(t * 3.0);
			p.ring(feet.x, feet.y + 0.02, feet.z, r, 48, 255, 0.0);
			p.ring(feet.x, feet.y + 0.02, feet.z, r + 0.18, 48, 110, 0.5);
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
			double radius = half + 0.25;
			for (int k = 0; k < 3; k++) {
				double tt = t - k * 0.12;
				double y = feet.y + height * (0.5 + 0.5 * Math.sin(tt * 2.5));
				int alpha = k == 0 ? 255 : (k == 1 ? 110 : 50);
				p.ring(feet.x, y, feet.z, radius, 40, alpha, 0.0);
			}
		}

		// 4) Male krzyzyki krazace wokol celu
		if (cfg.orbit) {
			int count = 6;
			double orbitRadius = half + 0.45;
			for (int i = 0; i < count; i++) {
				double ang = t * 2.0 + i * (2.0 * Math.PI / count);
				double y = feet.y + height * (0.5 + 0.4 * Math.sin(t * 1.5 + i * 1.1));
				p.cross(
						feet.x + Math.cos(ang) * orbitRadius, y, feet.z + Math.sin(ang) * orbitRadius,
						0.07, p.rgb(i / (double) count), 255);
			}
		}

		// Od razu wypchnij linie na ekran
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
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

	/** Pomocnik do rysowania linii, pierscieni i boxow w wybranym kolorze. */
	private static final class Painter {
		private final VertexConsumer vc;
		private final MatrixStack.Entry entry;
		private final VisualConfig cfg;
		private final double t;

		Painter(VertexConsumer vc, MatrixStack.Entry entry, VisualConfig cfg, double t) {
			this.vc = vc;
			this.entry = entry;
			this.cfg = cfg;
			this.t = t;
		}

		/** Kolor RGB; w trybie rainbow zalezy od czasu i fazy (0..1). */
		int rgb(double phase) {
			if (cfg.rainbow) {
				float hue = (float) (((t * 0.25 + phase) % 1.0 + 1.0) % 1.0);
				return MathHelper.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
			}
			return (cfg.red << 16) | (cfg.green << 8) | cfg.blue;
		}

		void line(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, int alpha) {
			float dx = (float) (x2 - x1);
			float dy = (float) (y2 - y1);
			float dz = (float) (z2 - z1);
			float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1.0e-6f) return;
			dx /= len;
			dy /= len;
			dz /= len;

			int r = (rgb >> 16) & 0xFF;
			int g = (rgb >> 8) & 0xFF;
			int b = rgb & 0xFF;

			vc.vertex(entry, (float) x1, (float) y1, (float) z1).color(r, g, b, alpha).normal(entry, dx, dy, dz);
			vc.vertex(entry, (float) x2, (float) y2, (float) z2).color(r, g, b, alpha).normal(entry, dx, dy, dz);
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
