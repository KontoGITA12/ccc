package com.visualtarget;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Pety (tylko u Ciebie na ekranie): nietoperz, kot, kura, pies.
 * Chodza za Toba (nietoperz lata dookola). Modele to kolorowe klocki rysowane wlasna geometria.
 */
public final class PetRenderer {
	private PetRenderer() {}

	// Stan peta (aktualizowany co tick)
	private static boolean init = false;
	private static Vec3d prev = Vec3d.ZERO;
	private static Vec3d cur = Vec3d.ZERO;
	private static float prevHeadingDeg = 0f;
	private static float headingDeg = 0f;
	private static double walkPhase = 0.0;
	private static double moveAmt = 0.0;
	private static double prevMoveAmt = 0.0;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PetRenderer::onTick);
		WorldRenderEvents.AFTER_ENTITIES.register(PetRenderer::onRender);
	}

	// ------------------------------------------------------------------
	// Ruch (co tick)
	// ------------------------------------------------------------------

	private static void onTick(MinecraftClient mc) {
		VisualConfig cfg = VisualConfig.get();
		ClientPlayerEntity pl = mc.player;
		if (!cfg.petEnabled || pl == null || mc.world == null) {
			init = false;
			return;
		}

		Vec3d pp = pl.getPos();
		double yaw = Math.toRadians(pl.getYaw());
		double time = mc.world.getTime() / 20.0;

		if (!init || cur.distanceTo(pp) > 16.0) {
			Vec3d start = pp.add(Math.sin(yaw) * 1.4, 0.0, -Math.cos(yaw) * 1.4);
			cur = start;
			prev = start;
			headingDeg = pl.getYaw();
			prevHeadingDeg = headingDeg;
			walkPhase = 0.0;
			moveAmt = 0.0;
			prevMoveAmt = 0.0;
			init = true;
		}

		prev = cur;
		prevHeadingDeg = headingDeg;
		prevMoveAmt = moveAmt;

		if (cfg.petType == 0) {
			tickBat(pl, pp, time);
		} else {
			tickGround(mc, pl, pp, yaw);
		}
	}

	private static void tickBat(ClientPlayerEntity pl, Vec3d pp, double time) {
		double a = time * 0.9;
		double tx = pp.x + Math.cos(a) * 1.3;
		double tz = pp.z + Math.sin(a) * 1.3;
		double ty = pp.y + 1.9 + 0.35 * Math.sin(time * 1.7);

		double nx = cur.x + (tx - cur.x) * 0.18;
		double ny = cur.y + (ty - cur.y) * 0.18;
		double nz = cur.z + (tz - cur.z) * 0.18;

		double dx = nx - cur.x;
		double dz = nz - cur.z;
		if (dx * dx + dz * dz > 1.0e-5) {
			float target = (float) Math.toDegrees(Math.atan2(dx, dz));
			headingDeg = MathHelper.lerpAngleDegrees(0.3f, headingDeg, target);
		}
		cur = new Vec3d(nx, ny, nz);
		moveAmt = 1.0;
	}

	private static void tickGround(MinecraftClient mc, ClientPlayerEntity pl, Vec3d pp, double yaw) {
		// Cel: za graczem i troche z boku
		double behindX = Math.sin(yaw), behindZ = -Math.cos(yaw);
		double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
		double tx = pp.x + behindX * 1.5 + rightX * 0.9;
		double tz = pp.z + behindZ * 1.5 + rightZ * 0.9;

		double dx = tx - cur.x;
		double dz = tz - cur.z;
		double dist = Math.sqrt(dx * dx + dz * dz);

		double nx = cur.x;
		double nz = cur.z;
		double speed = 0.0;
		if (dist > 0.45) {
			speed = MathHelper.clamp(dist * 0.16, 0.06, 0.42);
			nx += dx / dist * speed;
			nz += dz / dist * speed;
			float target = (float) Math.toDegrees(Math.atan2(dx, dz));
			headingDeg = MathHelper.lerpAngleDegrees(0.35f, headingDeg, target);
		} else if (dist > 0.1) {
			// Stoi i patrzy w strone gracza
			float target = (float) Math.toDegrees(Math.atan2(pp.x - cur.x, pp.z - cur.z));
			headingDeg = MathHelper.lerpAngleDegrees(0.08f, headingDeg, target);
		}

		// Wysokosc terenu pod petem
		double groundY = findGround(mc, nx, Math.max(cur.y, pp.y), nz, pp.y);
		double ny = cur.y + (groundY - cur.y) * 0.4;

		cur = new Vec3d(nx, ny, nz);
		walkPhase += speed * 9.0;
		moveAmt = MathHelper.clamp(speed * 4.0, 0.0, 1.0);
	}

	/** Szuka gornej powierzchni bloku pod punktem (do 8 blokow w dol od poziomu gracza). */
	private static double findGround(MinecraftClient mc, double x, double fromY, double z, double fallback) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int startY = MathHelper.floor(fromY + 1.2);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = startY; y > startY - 9; y--) {
			pos.set(bx, y, bz);
			var state = mc.world.getBlockState(pos);
			var shape = state.getCollisionShape(mc.world, pos);
			if (!shape.isEmpty()) {
				return y + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
			}
		}
		return fallback;
	}

	// ------------------------------------------------------------------
	// Rysowanie
	// ------------------------------------------------------------------

	private static void onRender(WorldRenderContext ctx) {
		VisualConfig cfg = VisualConfig.get();
		if (!cfg.petEnabled || !init) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return;

		MatrixStack ms = ctx.matrixStack();
		VertexConsumerProvider consumers = ctx.consumers();
		if (ms == null || consumers == null) return;

		float td = ctx.tickCounter().getTickDelta(false);
		Vec3d cam = ctx.camera().getPos();
		double x = MathHelper.lerp(td, prev.x, cur.x) - cam.x;
		double y = MathHelper.lerp(td, prev.y, cur.y) - cam.y;
		double z = MathHelper.lerp(td, prev.z, cur.z) - cam.z;
		float heading = MathHelper.lerpAngleDegrees(td, prevHeadingDeg, headingDeg);
		double move = MathHelper.lerp(td, prevMoveAmt, moveAmt);
		double time = (mc.world.getTime() + td) / 20.0;

		Theme theme = cfg.theme();
		int accent = theme.ui(0.2);

		VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());

		ms.push();
		ms.translate(x, y, z);
		ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(heading));
		float s = cfg.petScale;
		ms.scale(s, s, s);

		Draw d = new Draw(vc, ms);
		switch (cfg.petType) {
			case 0 -> bat(d, time, theme);
			case 1 -> cat(d, time, move, accent);
			case 2 -> chicken(d, time, move, accent);
			default -> dog(d, time, move, accent);
		}
		ms.pop();

		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLightning());
		}
	}

	// ------------------------------------------------------------------
	// Modele (przod = +Z, stopy na y = 0)
	// ------------------------------------------------------------------

	private static void cat(Draw d, double time, double move, int collar) {
		int fur = 0xE0903A, light = 0xF4E8D4, dark = 0xB5671F;
		double sw = Math.sin(walkPhase) * 0.8 * move;

		// nogi (przod/tyl, lewa/prawa na zmiane)
		leg(d, -0.06, 0.19, 0.14, 0.025, 0.19, sw, dark);
		leg(d, 0.06, 0.19, 0.14, 0.025, 0.19, -sw, dark);
		leg(d, -0.06, 0.19, -0.16, 0.025, 0.19, -sw, dark);
		leg(d, 0.06, 0.19, -0.16, 0.025, 0.19, sw, dark);

		d.box(-0.08, 0.19, -0.22, 0.08, 0.35, 0.20, fur);
		d.box(-0.05, 0.19, -0.10, 0.05, 0.2, 0.18, light);      // brzuch
		d.box(-0.082, 0.28, -0.22, 0.082, 0.35, -0.05, dark);   // pregi na grzbiecie

		// glowa
		d.box(-0.08, 0.30, 0.19, 0.08, 0.46, 0.35, fur);
		d.box(-0.045, 0.30, 0.34, 0.045, 0.37, 0.375, light);   // pyszczek
		d.box(-0.015, 0.355, 0.375, 0.015, 0.375, 0.385, 0xE58FA0); // nos
		d.box(-0.072, 0.46, 0.22, -0.03, 0.52, 0.27, fur);       // uszy
		d.box(0.03, 0.46, 0.22, 0.072, 0.52, 0.27, fur);
		d.box(-0.062, 0.46, 0.235, -0.04, 0.495, 0.25, 0xE58FA0);
		d.box(0.04, 0.46, 0.235, 0.062, 0.495, 0.25, 0xE58FA0);
		d.box(-0.062, 0.40, 0.349, -0.036, 0.425, 0.356, 0x59D96B); // oczy
		d.box(0.036, 0.40, 0.349, 0.062, 0.425, 0.356, 0x59D96B);
		d.box(-0.052, 0.405, 0.356, -0.046, 0.42, 0.36, 0x101010);
		d.box(0.046, 0.405, 0.356, 0.052, 0.42, 0.36, 0x101010);

		// obroza w kolorze motywu
		d.box(-0.085, 0.31, 0.185, 0.085, 0.34, 0.215, collar);

		// ogon
		d.push();
		d.translate(0, 0.32, -0.22);
		d.rotX(0.9 + 0.22 * Math.sin(time * 3.0));
		d.box(-0.016, -0.016, -0.22, 0.016, 0.016, 0.0, fur);
		d.box(-0.017, -0.017, -0.22, 0.017, 0.017, -0.16, dark);
		d.pop();
	}

	private static void dog(Draw d, double time, double move, int collar) {
		int fur = 0x9A6A3A, light = 0xE7D3B0, dark = 0x5E3B1C;
		double sw = Math.sin(walkPhase) * 0.85 * move;

		leg(d, -0.07, 0.22, 0.17, 0.03, 0.22, sw, dark);
		leg(d, 0.07, 0.22, 0.17, 0.03, 0.22, -sw, dark);
		leg(d, -0.07, 0.22, -0.18, 0.03, 0.22, -sw, dark);
		leg(d, 0.07, 0.22, -0.18, 0.03, 0.22, sw, dark);

		d.box(-0.10, 0.22, -0.26, 0.10, 0.42, 0.24, fur);
		d.box(-0.06, 0.22, -0.12, 0.06, 0.235, 0.20, light);

		// glowa
		d.box(-0.09, 0.36, 0.22, 0.09, 0.54, 0.40, fur);
		d.box(-0.05, 0.36, 0.39, 0.05, 0.45, 0.50, light);       // pysk
		d.box(-0.02, 0.43, 0.50, 0.02, 0.46, 0.515, 0x151515);   // nos
		d.box(-0.12, 0.40, 0.27, -0.09, 0.52, 0.34, dark);       // klapniete uszy
		d.box(0.09, 0.40, 0.27, 0.12, 0.52, 0.34, dark);
		d.box(-0.07, 0.46, 0.399, -0.04, 0.49, 0.406, 0xFFFFFF); // oczy
		d.box(0.04, 0.46, 0.399, 0.07, 0.49, 0.406, 0xFFFFFF);
		d.box(-0.058, 0.465, 0.406, -0.046, 0.485, 0.41, 0x101010);
		d.box(0.046, 0.465, 0.406, 0.058, 0.485, 0.41, 0x101010);

		d.box(-0.105, 0.35, 0.215, 0.105, 0.385, 0.255, collar);

		// ogon macha
		d.push();
		d.translate(0, 0.40, -0.26);
		d.rotX(0.7);
		d.rotY(0.4 * Math.sin(time * 9.0));
		d.box(-0.022, -0.022, -0.2, 0.022, 0.022, 0.0, fur);
		d.pop();
	}

	private static void chicken(Draw d, double time, double move, int collar) {
		int white = 0xFAFAF4, grey = 0xE2E2DA, yellow = 0xF2B93B, red = 0xD93636;
		double sw = Math.sin(walkPhase * 1.4) * 0.9 * move;

		leg(d, -0.04, 0.14, 0.0, 0.012, 0.14, sw, yellow);
		leg(d, 0.04, 0.14, 0.0, 0.012, 0.14, -sw, yellow);

		d.box(-0.09, 0.14, -0.13, 0.09, 0.30, 0.12, white);
		d.box(-0.092, 0.14, -0.13, 0.092, 0.20, 0.0, grey);
		d.box(-0.04, 0.26, -0.18, 0.04, 0.34, -0.12, white);      // ogon

		// glowa
		d.box(-0.045, 0.28, 0.09, 0.045, 0.39, 0.17, white);
		d.box(-0.02, 0.32, 0.17, 0.02, 0.35, 0.21, yellow);       // dziob
		d.box(-0.012, 0.39, 0.105, 0.012, 0.44, 0.15, red);       // grzebien
		d.box(-0.01, 0.29, 0.17, 0.01, 0.32, 0.19, red);          // korale
		d.box(-0.047, 0.34, 0.14, -0.04, 0.36, 0.15, 0x101010);   // oczy
		d.box(0.04, 0.34, 0.14, 0.047, 0.36, 0.15, 0x101010);
		d.box(-0.05, 0.275, 0.095, 0.05, 0.295, 0.115, collar);

		// skrzydla (lekko macha)
		double flap = 0.12 + 0.12 * Math.sin(time * 6.0) + 0.5 * move * Math.abs(Math.sin(walkPhase * 1.4));
		d.push();
		d.translate(0.095, 0.27, 0.0);
		d.rotZ(-flap);
		d.box(0.0, -0.09, -0.08, 0.015, 0.0, 0.08, grey);
		d.pop();
		d.push();
		d.translate(-0.095, 0.27, 0.0);
		d.rotZ(flap);
		d.box(-0.015, -0.09, -0.08, 0.0, 0.0, 0.08, grey);
		d.pop();
	}

	private static void bat(Draw d, double time, Theme theme) {
		int body = 0x2B2B33;
		int wing = Theme.mix(0x1A1A22, theme.uiB, 0.4);
		double flap = Math.sin(time * 18.0) * 0.75;
		double bob = 0.03 * Math.sin(time * 9.0);

		d.push();
		d.translate(0, bob, 0);

		d.box(-0.05, -0.06, -0.05, 0.05, 0.06, 0.05, body);
		d.box(-0.04, -0.03, 0.05, 0.04, 0.05, 0.12, body);        // glowa
		d.box(-0.035, 0.05, 0.06, -0.012, 0.10, 0.085, body);     // uszy
		d.box(0.012, 0.05, 0.06, 0.035, 0.10, 0.085, body);
		d.box(-0.03, 0.0, 0.119, -0.012, 0.018, 0.125, 0xFF4D4D); // oczy
		d.box(0.012, 0.0, 0.119, 0.03, 0.018, 0.125, 0xFF4D4D);
		d.box(-0.03, -0.045, 0.119, -0.02, -0.03, 0.125, 0xFFFFFF); // kly
		d.box(0.02, -0.045, 0.119, 0.03, -0.03, 0.125, 0xFFFFFF);

		// prawe skrzydlo
		d.push();
		d.translate(0.05, 0.02, 0.0);
		d.rotZ(flap);
		d.box(0.0, -0.006, -0.07, 0.15, 0.006, 0.07, wing);
		d.translate(0.15, 0, 0);
		d.rotZ(flap * 0.7);
		d.box(0.0, -0.005, -0.05, 0.13, 0.005, 0.05, wing);
		d.pop();

		// lewe skrzydlo
		d.push();
		d.translate(-0.05, 0.02, 0.0);
		d.rotZ(-flap);
		d.box(-0.15, -0.006, -0.07, 0.0, 0.006, 0.07, wing);
		d.translate(-0.15, 0, 0);
		d.rotZ(-flap * 0.7);
		d.box(-0.13, -0.005, -0.05, 0.0, 0.005, 0.05, wing);
		d.pop();

		d.pop();
	}

	/** Noga zawieszona w biodrze (pivot) o dlugosci len, wychylana wokol osi X. */
	private static void leg(Draw d, double x, double hipY, double z, double half, double len, double swing, int rgb) {
		d.push();
		d.translate(x, hipY, z);
		d.rotX(swing);
		d.box(-half, -len, -half, half, 0.0, half, rgb);
		d.pop();
	}

	// ------------------------------------------------------------------
	// Pomocnik rysowania klockow
	// ------------------------------------------------------------------

	private static final class Draw {
		private final VertexConsumer vc;
		private final MatrixStack ms;

		Draw(VertexConsumer vc, MatrixStack ms) {
			this.vc = vc;
			this.ms = ms;
		}

		void push() { ms.push(); }

		void pop() { ms.pop(); }

		void translate(double x, double y, double z) { ms.translate(x, y, z); }

		void rotX(double rad) { ms.multiply(RotationAxis.POSITIVE_X.rotation((float) rad)); }

		void rotY(double rad) { ms.multiply(RotationAxis.POSITIVE_Y.rotation((float) rad)); }

		void rotZ(double rad) { ms.multiply(RotationAxis.POSITIVE_Z.rotation((float) rad)); }

		void box(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
			Matrix4f m = ms.peek().getPositionMatrix();
			float ax = (float) x0, ay = (float) y0, az = (float) z0;
			float bx = (float) x1, by = (float) y1, bz = (float) z1;

			// gora
			face(m, rgb, 1.0f, ax, by, bz, bx, by, bz, bx, by, az, ax, by, az);
			// dol
			face(m, rgb, 0.5f, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
			// przod (+Z)
			face(m, rgb, 0.82f, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
			// tyl (-Z)
			face(m, rgb, 0.82f, bx, ay, az, ax, ay, az, ax, by, az, bx, by, az);
			// prawa (+X)
			face(m, rgb, 0.68f, bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz);
			// lewa (-X)
			face(m, rgb, 0.68f, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
		}

		private void face(Matrix4f m, int rgb, float shade,
						  float x1, float y1, float z1, float x2, float y2, float z2,
						  float x3, float y3, float z3, float x4, float y4, float z4) {
			int r = (int) (((rgb >> 16) & 0xFF) * shade);
			int g = (int) (((rgb >> 8) & 0xFF) * shade);
			int b = (int) ((rgb & 0xFF) * shade);
			vc.vertex(m, x1, y1, z1).color(r, g, b, 255);
			vc.vertex(m, x2, y2, z2).color(r, g, b, 255);
			vc.vertex(m, x3, y3, z3).color(r, g, b, 255);
			vc.vertex(m, x4, y4, z4).color(r, g, b, 255);
		}
	}
}
