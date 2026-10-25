package com.visualtarget;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tryb deszczu: pada wszedzie dookola, ale mokre robia sie tylko bloki, po ktorych chodzisz
 * (ciemniejsze, blyszczace, z kregami na wodzie). Skok = chlapniecie z kroplami i kregiem.
 */
public final class RainRenderer {
	private RainRenderer() {}

	private static final int WET_TICKS = 20 * 50;
	private static final int SPLASH_TICKS = 34;

	private static final class Puddle {
		final int x, y, z;
		final double top;
		long lastWalk;

		Puddle(int x, int y, int z, double top, long lastWalk) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.top = top;
			this.lastWalk = lastWalk;
		}
	}

	private static final class Splash {
		final double x, y, z, power;
		final long start;
		final int seed = (int) (Math.random() * 100000.0);

		Splash(double x, double y, double z, long start, double power) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.start = start;
			this.power = power;
		}
	}

	private static final Map<Long, Puddle> puddles = new HashMap<>();
	private static final List<Splash> splashes = new ArrayList<>();
	private static boolean wasOnGround = true;
	private static double lastVy = 0.0;
	private static ClientWorld lastWorld;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(RainRenderer::onTick);
		WorldRenderEvents.AFTER_ENTITIES.register(RainRenderer::onRender);
	}

	// ------------------------------------------------------------------
	// Logika co tick
	// ------------------------------------------------------------------

	private static void onTick(MinecraftClient mc) {
		VisualConfig cfg = VisualConfig.get();
		ClientPlayerEntity pl = mc.player;
		if (!cfg.rain || pl == null || mc.world == null) {
			if (!puddles.isEmpty() || !splashes.isEmpty()) {
				puddles.clear();
				splashes.clear();
			}
			return;
		}
		if (mc.world != lastWorld) {
			lastWorld = mc.world;
			puddles.clear();
			splashes.clear();
		}

		long tick = mc.world.getTime();
		boolean onGround = pl.isOnGround();
		Vec3d pos = pl.getPos();

		if (onGround) {
			// bloki pod stopami (srodek i cztery rogi sylwetki)
			markWalked(mc, pos.x, pos.y, pos.z, tick);
			markWalked(mc, pos.x + 0.3, pos.y, pos.z + 0.3, tick);
			markWalked(mc, pos.x - 0.3, pos.y, pos.z + 0.3, tick);
			markWalked(mc, pos.x + 0.3, pos.y, pos.z - 0.3, tick);
			markWalked(mc, pos.x - 0.3, pos.y, pos.z - 0.3, tick);
		}

		double vy = pl.getVelocity().y;
		if (wasOnGround && !onGround && vy > 0.05) {
			splashes.add(new Splash(pos.x, pos.y, pos.z, tick, 0.85));
		} else if (!wasOnGround && onGround && lastVy < -0.4) {
			splashes.add(new Splash(pos.x, pos.y, pos.z, tick, Math.min(1.6, 0.6 - lastVy)));
		}
		wasOnGround = onGround;
		lastVy = vy;

		splashes.removeIf(s -> tick - s.start > SPLASH_TICKS);
		puddles.values().removeIf(p -> tick - p.lastWalk > WET_TICKS);
	}

	private static void markWalked(MinecraftClient mc, double x, double y, double z, long tick) {
		int bx = MathHelper.floor(x);
		int bz = MathHelper.floor(z);
		int startY = MathHelper.floor(y + 0.1);
		BlockPos.Mutable bp = new BlockPos.Mutable();
		for (int yy = startY; yy >= startY - 2; yy--) {
			bp.set(bx, yy, bz);
			var state = mc.world.getBlockState(bp);
			if (!state.getFluidState().isEmpty()) return;
			var shape = state.getCollisionShape(mc.world, bp);
			if (shape.isEmpty()) continue;

			double top = yy + shape.getMax(Direction.Axis.Y);
			long key = bp.asLong();
			Puddle p = puddles.get(key);
			if (p == null) {
				puddles.put(key, new Puddle(bx, yy, bz, top, tick));
			} else {
				p.lastWalk = tick;
			}
			return;
		}
	}

	// ------------------------------------------------------------------
	// Rysowanie
	// ------------------------------------------------------------------

	private static void onRender(WorldRenderContext ctx) {
		VisualConfig cfg = VisualConfig.get();
		if (!cfg.rain) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;

		Fx fx = Fx.begin(ctx);
		if (fx == null) return;

		float td = ctx.tickCounter().getTickDelta(false);
		Vec3d cam = ctx.camera().getPos();
		Vec3d pc = mc.player.getLerpedPos(td);
		double tickD = mc.world.getTime() + td;
		double time = tickD / 20.0;

		drawPuddles(fx, cam, pc, tickD, time);
		drawSplashes(fx, cam, tickD);
		drawRain(fx, mc, cfg, cam, pc, time);

		fx.end();
	}

	/** Mokre bloki: ciemna tafla z miekkim brzegiem, blask nieba i kregi od kropel. */
	private static void drawPuddles(Fx fx, Vec3d cam, Vec3d pc, double tickD, double time) {
		for (Puddle p : puddles.values()) {
			double dx = p.x + 0.5 - pc.x;
			double dz = p.z + 0.5 - pc.z;
			if (dx * dx + dz * dz > 26.0 * 26.0) continue;

			double wet = Fx.clamp01(1.0 - (tickD - p.lastWalk) / WET_TICKS);
			double w = Fx.smooth(0.0, 0.35, wet);
			if (w <= 0.01) continue;

			int hk = p.x * 73856093 ^ p.z * 19349663 ^ p.y * 83492791;
			double inset = 0.08 + 0.12 * Fx.hash(hk, 1);

			double x0 = p.x - cam.x;
			double y = p.top - cam.y + 0.006;
			double z0 = p.z - cam.z;

			// ciemna woda
			wetSquare(fx, x0, y, z0, inset, Fx.argb(125 * w, 0x0A1424), Fx.argb(65 * w, 0x0A1424));
			// blask nieba na tafli
			wetSquare(fx, x0, y + 0.003, z0, inset + 0.08, Fx.argb(38 * w, 0x7C9CC8), Fx.argb(0, 0x7C9CC8));

			// kregi od kropel
			for (int k = 0; k < 2; k++) {
				double phase = Fx.mod(time * 0.75 + Fx.hash(hk, 10 + k), 1.0);
				double cx = x0 + 0.3 + 0.4 * Fx.hash(hk, 20 + k);
				double cz = z0 + 0.3 + 0.4 * Fx.hash(hk, 30 + k);
				double r = phase * 0.3;
				double a = Math.pow(1.0 - phase, 2.0) * 190.0 * w;
				double hw = 0.012 + 0.012 * phase;
				int c = Fx.argb(a, 0xE6F2FF);
				fx.groundRing(cx, y + 0.006, cz, Math.max(0.0, r - hw), r + hw, 16, c, c);
			}
		}
	}

	/** Kwadrat z gradientem: srodek (inset od brzegu) mocniejszy, brzeg slabszy. */
	private static void wetSquare(Fx fx, double x0, double y, double z0, double inset, int cIn, int cOut) {
		double x1 = x0 + 1.0;
		double z1 = z0 + 1.0;
		double ix0 = x0 + inset, ix1 = x1 - inset, iz0 = z0 + inset, iz1 = z1 - inset;

		fx.quad2(ix0, y, iz0, cIn, ix1, y, iz0, cIn, ix1, y, iz1, cIn, ix0, y, iz1, cIn);
		fx.quad2(x0, y, z0, cOut, x1, y, z0, cOut, ix1, y, iz0, cIn, ix0, y, iz0, cIn);
		fx.quad2(x1, y, z0, cOut, x1, y, z1, cOut, ix1, y, iz1, cIn, ix1, y, iz0, cIn);
		fx.quad2(x1, y, z1, cOut, x0, y, z1, cOut, ix0, y, iz1, cIn, ix1, y, iz1, cIn);
		fx.quad2(x0, y, z1, cOut, x0, y, z0, cOut, ix0, y, iz0, cIn, ix0, y, iz1, cIn);
	}

	/** Chlapniecie: krag na wodzie, korona z kropel lecacych lukiem i opadajacych. */
	private static void drawSplashes(Fx fx, Vec3d cam, double tickD) {
		for (Splash s : splashes) {
			double age = (tickD - s.start) / 20.0;
			if (age < 0.0 || age > 1.7) continue;

			double x = s.x - cam.x;
			double y = s.y - cam.y;
			double z = s.z - cam.z;
			double pw = s.power;

			// krag na wodzie
			double ringP = Fx.clamp01(age / 0.9);
			double r = (0.15 + ringP * 1.1) * (0.8 + 0.4 * pw);
			double ringA = Math.pow(1.0 - ringP, 1.6) * 230.0;
			if (ringA > 2.0) {
				int c = Fx.argb(ringA, 0xE6F2FF);
				int c0 = Fx.argb(ringA * 0.15, 0xE6F2FF);
				fx.groundRing(x, y + 0.012, z, Math.max(0.0, r - 0.09), r, 32, c0, c);
				fx.groundRing(x, y + 0.014, z, Math.max(0.0, r * 0.55 - 0.05), r * 0.55, 28, c0, Fx.argb(ringA * 0.7, 0xE6F2FF));
			}

			// krople
			int n = (int) (14 + 14 * Math.min(1.0, pw));
			for (int k = 0; k < n; k++) {
				double ang = Fx.hash(k, s.seed) * Math.PI * 2.0;
				double hs = (0.5 + 1.3 * Fx.hash(k, s.seed + 1)) * (0.7 + 0.5 * pw);
				double vy = (1.9 + 2.4 * Fx.hash(k, s.seed + 2)) * (0.75 + 0.4 * pw);

				double life = 2.0 * vy / 9.8;
				if (age > life) continue;

				double dx = Math.cos(ang) * hs * age;
				double dz = Math.sin(ang) * hs * age;
				double dy = vy * age - 4.9 * age * age;
				if (dy < 0.0) continue;

				double fade = 1.0 - age / life;
				double size = 0.035 + 0.03 * Fx.hash(k, s.seed + 3);
				int alpha = (int) (235 * Math.sqrt(fade));
				fx.disc(x + dx, y + 0.03 + dy, z + dz, size, Fx.argb(alpha, 0xD6E8FF));

				// lekki "ogon" ruchu
				double a2 = Math.max(0.0, age - 0.035);
				double dy2 = vy * a2 - 4.9 * a2 * a2;
				fx.disc(x + Math.cos(ang) * hs * a2, y + 0.03 + Math.max(0.0, dy2), z + Math.sin(ang) * hs * a2,
						size * 0.8, Fx.argb(alpha * 0.45, 0xD6E8FF));
			}
		}
	}

	/** Padajace smugi deszczu wokol gracza (tylko tam, gdzie nic nie przeslania nieba). */
	private static void drawRain(Fx fx, MinecraftClient mc, VisualConfig cfg, Vec3d cam, Vec3d pc, double time) {
		int count = (int) (240 * cfg.rainDensity);
		double radius = 14.0;
		double size = radius * 2.0;
		double heightSpan = 18.0;

		double gx = 1.2 + 0.7 * Math.sin(time * 0.31);
		double gz = 0.5 + 0.5 * Math.sin(time * 0.19 + 2.0);
		double slantX = gx * 0.05;
		double slantZ = gz * 0.05;
		double dl = Math.sqrt(slantX * slantX + 1.0 + slantZ * slantZ);
		double dirX = slantX / dl, dirY = -1.0 / dl, dirZ = slantZ / dl;

		for (int i = 0; i < count; i++) {
			double fall = 15.0 + 5.0 * Fx.hash(i, 4);

			double ux = Fx.hash(i, 1) * size + gx * time;
			double uz = Fx.hash(i, 2) * size + gz * time;
			double uy = Fx.hash(i, 3) * heightSpan - fall * time;

			double x = pc.x - radius + Fx.mod(ux - (pc.x - radius), size);
			double z = pc.z - radius + Fx.mod(uz - (pc.z - radius), size);
			double y = pc.y - 4.0 + Fx.mod(uy - (pc.y - 4.0), heightSpan);

			int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
			if (y < top) continue;

			double d = Math.hypot(x - pc.x, z - pc.z);
			double edge = 1.0 - Fx.smooth(9.0, 14.0, d);
			if (edge <= 0.0) continue;

			double len = 0.55 + 0.45 * Fx.hash(i, 5);
			double hx = x - cam.x, hy = y - cam.y, hz = z - cam.z;
			double tx = hx - dirX * len, ty = hy - dirY * len, tz = hz - dirZ * len;

			double w = 0.0065;
			double wx = fx.rx * w, wy = fx.ry * w, wz = fx.rz * w;
			int head = Fx.argb(140 * edge, 0xC4DCF5);
			int tail = Fx.argb(0, 0xC4DCF5);
			fx.quad(hx - wx, hy - wy, hz - wz, head,
					hx + wx, hy + wy, hz + wz, head,
					tx + wx, ty + wy, tz + wz, tail,
					tx - wx, ty - wy, tz - wz, tail);
		}
	}
}
