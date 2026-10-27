package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Xray: swiecacy szkielet gracza widoczny przez sciany i przez model. */
public final class Xray {
	private Xray() {
	}

	/** Stan chodu kazdego gracza: [faza, szybkosc]. */
	private static final Map<UUID, double[]> walk = new HashMap<>();

	public static void tick(MinecraftClient mc) {
		if (mc.world == null) {
			walk.clear();
			return;
		}
		for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
			double dx = p.getX() - p.prevX, dz = p.getZ() - p.prevZ;
			double d = Math.sqrt(dx * dx + dz * dz);
			double[] w = walk.computeIfAbsent(p.getUuid(), k -> new double[]{0, 0});
			w[1] += (Math.min(1.0, d * 4.0) - w[1]) * 0.4;
			w[0] += d * 4.0;
		}
		if (walk.size() > 200) {
			walk.clear();
		}
	}

	/** Ruchomy uklad: obrot wokol Y (yaw) i opcjonalnie wokol osi X w punkcie pivot (pitch). */
	private static final class Rig {
		final double ox, oy, oz, cy, sy, ys;
		boolean hasPitch;
		double px, py, pz, cp, sp;

		Rig(double ox, double oy, double oz, double yawDeg, double ys) {
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			double r = Math.toRadians(yawDeg);
			this.cy = Math.cos(r);
			this.sy = Math.sin(r);
			this.ys = ys;
		}

		Rig pitch(double pivotX, double pivotY, double pivotZ, double angle) {
			Rig r = new Rig(ox, oy, oz, 0, ys, cy, sy);
			r.hasPitch = true;
			r.px = pivotX;
			r.py = pivotY;
			r.pz = pivotZ;
			r.cp = Math.cos(angle);
			r.sp = Math.sin(angle);
			return r;
		}

		private Rig(double ox, double oy, double oz, double unused, double ys, double cy, double sy) {
			this.ox = ox;
			this.oy = oy;
			this.oz = oz;
			this.ys = ys;
			this.cy = cy;
			this.sy = sy;
		}

		double[] w(double x, double y, double z) {
			if (hasPitch) {
				double dy = y - py, dz = z - pz;
				double ny = dy * cp - dz * sp;
				double nz = dy * sp + dz * cp;
				y = py + ny;
				z = pz + nz;
			}
			double wx = x * cy - z * sy;
			double wz = x * sy + z * cy;
			return new double[]{ox + wx, oy + y * ys, oz + wz};
		}
	}

	public static boolean applies(Config cfg, boolean self) {
		return switch (cfg.xray) {
			case 1 -> self;
			case 2 -> !self;
			case 3 -> true;
			default -> false;
		};
	}

	public static void draw(Draw d, AbstractClientPlayerEntity p, float td, Config cfg, Theme th, double t) {
		EntityPose pose = p.getPose();
		if (pose != EntityPose.STANDING && pose != EntityPose.CROUCHING) {
			return;
		}
		Vec3d pos = p.getLerpedPos(td);
		double ys = p.getHeight() / 1.8;
		float bodyYaw = MathHelper.lerpAngleDegrees(td, p.prevBodyYaw, p.bodyYaw);
		float headYaw = MathHelper.lerpAngleDegrees(td, p.prevHeadYaw, p.headYaw);
		float headPitch = MathHelper.lerp(td, p.prevPitch, p.getPitch());

		double[] w = walk.getOrDefault(p.getUuid(), new double[]{0, 0});
		double swing = Math.cos(w[0] * 0.6662) * w[1];

		int core = Gfx.lerp(th.accent(cfg.animateColors), 0xFFFFFF, 0.35f);
		int halo = th.accent(cfg.animateColors);
		float al = cfg.xrayAlpha;

		Rig body = new Rig(pos.x, pos.y, pos.z, bodyYaw, ys);
		Rig head = new Rig(pos.x, pos.y, pos.z, headYaw, ys).pitch(0, 1.40, 0, Math.toRadians(headPitch));
		Rig armL = body.pitch(0.31, 1.37, 0, -swing * 1.0);
		Rig armR = body.pitch(-0.31, 1.37, 0, swing * 1.0);
		Rig legL = body.pitch(0.10, 0.75, 0, swing * 1.2);
		Rig legR = body.pitch(-0.10, 0.75, 0, -swing * 1.2);

		double yawRad = Math.toRadians(bodyYaw);
		double[] u = {Math.cos(yawRad), 0, Math.sin(yawRad)};
		double[] v = {-Math.sin(yawRad), 0, Math.cos(yawRad)};

		// kregoslup
		for (int i = 0; i < 9; i++) {
			double y = 0.80 + i * 0.065;
			joint(d, body, 0, y, -0.03, 0.034, core, halo, al);
		}
		bar(d, body, 0, 0.78, -0.03, 0, 1.40, -0.03, 0.012, core, halo, al, u, v);
		// szyja
		bar(d, head, 0, 1.38, 0, 0, 1.46, 0, 0.018, core, halo, al, u, v);

		// czaszka: trzy wielkie kola, dwa rownoleznikowe i oczy
		double hy = 1.63;
		loop(d, head, 0, hy, 0, 1, 0, 0, 0, 0, 1, 0.20, 0.20, 0.03, core, halo, al);
		loop(d, head, 0, hy + 0.10, 0, 1, 0, 0, 0, 0, 1, 0.16, 0.16, 0.03, core, halo, al);
		loop(d, head, 0, hy - 0.10, 0, 1, 0, 0, 0, 0, 1, 0.15, 0.15, 0.03, core, halo, al);
		loop(d, head, 0, hy, 0, 1, 0, 0, 0, 1, 0, 0.20, 0.20, 0.03, core, halo, al);
		loop(d, head, 0, hy, 0, 0, 0, 1, 0, 1, 0, 0.20, 0.20, 0.03, core, halo, al);
		loop(d, head, 0.085, hy + 0.01, 0.17, 1, 0, 0, 0, 1, 0, 0.055, 0.055, 0.03, core, halo, al);
		loop(d, head, -0.085, hy + 0.01, 0.17, 1, 0, 0, 0, 1, 0, 0.055, 0.055, 0.03, core, halo, al);
		// zeby / szczeka
		loop(d, head, 0, hy - 0.12, 0.10, 1, 0, 0, 0, 0, 1, 0.09, 0.07, 0.025, core, halo, al);

		// zebra
		for (int i = 0; i < 5; i++) {
			double y = 1.31 - i * 0.075;
			double rx = 0.20 - i * 0.012;
			loop(d, body, 0, y, 0, 1, 0, 0, 0, 0, 1, rx, 0.105, 0.045, core, halo, al);
		}
		// mostek
		bar(d, body, 0, 1.33, 0.10, 0, 0.98, 0.105, 0.014, core, halo, al, u, v);
		// obojczyki
		bar(d, body, -0.21, 1.36, 0.02, 0.21, 1.36, 0.02, 0.012, core, halo, al, u, v);
		// miednica
		loop(d, body, 0, 0.79, 0, 1, 0, 0, 0, 0, 1, 0.20, 0.09, 0.05, core, halo, al);
		joint(d, body, 0.10, 0.76, 0, 0.05, core, halo, al);
		joint(d, body, -0.10, 0.76, 0, 0.05, core, halo, al);

		// rece
		limb(d, armL, 0.31, 1.37, 0.31, 1.06, 0.31, 0.74, 0.016, core, halo, al, u, v);
		limb(d, armR, -0.31, 1.37, -0.31, 1.06, -0.31, 0.74, 0.016, core, halo, al, u, v);
		// nogi
		limb(d, legL, 0.10, 0.75, 0.10, 0.38, 0.10, 0.03, 0.02, core, halo, al, u, v);
		limb(d, legR, -0.10, 0.75, -0.10, 0.38, -0.10, 0.03, 0.02, core, halo, al, u, v);
		bar(d, legL, 0.10, 0.03, 0, 0.10, 0.03, 0.14, 0.014, core, halo, al, u, v);
		bar(d, legR, -0.10, 0.03, 0, -0.10, 0.03, 0.14, 0.014, core, halo, al, u, v);
	}

	// ---- elementy

	private static void joint(Draw d, Rig r, double x, double y, double z, double rad, int core, int halo, float al) {
		double[] c = r.w(x, y, z);
		d.sphere(c[0], c[1], c[2], rad * 2.1, halo, al * 0.22f, 6);
		d.sphere(c[0], c[1], c[2], rad, core, al, 6);
	}

	private static void bar(Draw d, Rig r, double x1, double y1, double z1, double x2, double y2, double z2,
							double w, int core, int halo, float al, double[] u, double[] v) {
		double[] a = r.w(x1, y1, z1), b = r.w(x2, y2, z2);
		d.bar(a, b, u, v, w * 2.6, halo, al * 0.22f);
		d.bar(a, b, u, v, w, core, al);
	}

	/** Kosc ramienia / nogi: dwie kosci i trzy stawy. */
	private static void limb(Draw d, Rig r, double x1, double y1, double x2, double y2, double x3, double y3,
							 double w, int core, int halo, float al, double[] u, double[] v) {
		bar(d, r, x1, y1, 0, x2, y2, 0, w, core, halo, al, u, v);
		bar(d, r, x2, y2, 0, x3, y3, 0, w, core, halo, al, u, v);
		joint(d, r, x1, y1, 0, w * 2.6, core, halo, al);
		joint(d, r, x2, y2, 0, w * 2.2, core, halo, al);
		joint(d, r, x3, y3, 0, w * 2.4, core, halo, al);
	}

	/**
	 * Pierscien (elipsa) o srodku c w plaszczyznie rozpietej przez u i v, z "grubosc" n
	 * (sciana rurki w kierunku trzeciej osi).
	 */
	private static void loop(Draw d, Rig r, double cx, double cy, double cz,
							 double ux, double uy, double uz, double vx, double vy, double vz,
							 double ru, double rv, double thick, int core, int halo, float al) {
		// trzecia os = u x v
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		int seg = 20;
		for (int pass = 0; pass < 2; pass++) {
			double th2 = pass == 0 ? thick * 2.2 : thick;
			int rgb = pass == 0 ? halo : core;
			float a = pass == 0 ? al * 0.2f : al * 0.95f;
			for (int i = 0; i < seg; i++) {
				double a0 = i * Math.PI * 2.0 / seg, a1 = (i + 1) * Math.PI * 2.0 / seg;
				double[] p0 = pt(r, cx, cy, cz, ux, uy, uz, vx, vy, vz, ru, rv, a0, nx, ny, nz, -th2 / 2);
				double[] p1 = pt(r, cx, cy, cz, ux, uy, uz, vx, vy, vz, ru, rv, a1, nx, ny, nz, -th2 / 2);
				double[] p2 = pt(r, cx, cy, cz, ux, uy, uz, vx, vy, vz, ru, rv, a1, nx, ny, nz, th2 / 2);
				double[] p3 = pt(r, cx, cy, cz, ux, uy, uz, vx, vy, vz, ru, rv, a0, nx, ny, nz, th2 / 2);
				d.quad(p0, p1, p2, p3, rgb, a);
			}
		}
	}

	private static double[] pt(Rig r, double cx, double cy, double cz,
							   double ux, double uy, double uz, double vx, double vy, double vz,
							   double ru, double rv, double ang, double nx, double ny, double nz, double off) {
		double ca = Math.cos(ang) * ru, sa = Math.sin(ang) * rv;
		return r.w(cx + ux * ca + vx * sa + nx * off,
			cy + uy * ca + vy * sa + ny * off,
			cz + uz * ca + vz * sa + nz * off);
	}
}
