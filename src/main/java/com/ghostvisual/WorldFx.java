package com.ghostvisual;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Efekty 3D: efekty celu, China Hat, slady i okrag przy skoku. */
public final class WorldFx {
	private WorldFx() {
	}

	private static final List<double[]> trail = new ArrayList<>();   // x, y, z, czas
	private static final List<double[]> jumps = new ArrayList<>();   // x, y, z, czas
	private static boolean wasOnGround = true;

	public static void tick(MinecraftClient mc) {
		long now = System.currentTimeMillis();
		Config cfg = Config.get();
		if (mc.player == null || mc.world == null) {
			trail.clear();
			jumps.clear();
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

		d.begin(vcp.getBuffer(RenderLayer.getLightning()), true);
		scene(d, mc, camera, td);
		if (vcp instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw(RenderLayer.getLightning());
		}

		d.begin(vcp.getBuffer(RenderLayer.getLines()), false);
		scene(d, mc, camera, td);
		if (vcp instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw(RenderLayer.getLines());
		}
	}

	private static void scene(Draw d, MinecraftClient mc, Camera camera, float td) {
		Config cfg = Config.get();
		Theme th = cfg.theme();
		double t = System.nanoTime() / 1.0e9 * cfg.speed;
		int col = th.accent(cfg.animateColors);
		int col2 = th.accent2;

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
			long now = System.currentTimeMillis();
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
			long now = System.currentTimeMillis();
			for (double[] j : jumps) {
				double age = (now - j[3]) / 800.0;
				if (age >= 1) {
					continue;
				}
				d.ring(j[0], j[1] + 0.03, j[2], 0.3 + 1.7 * age, 0.12, col, (float) (0.85 * (1 - age)), 40);
			}
		}
	}

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

	// ------------------------------------------------------------------ rysowanie

	/** Maly pomocnik do rysowania ksztaltow w swiecie (wspolrzedne swiata). */
	private static final class Draw {
		private final MatrixStack.Entry entry;
		private final Matrix4f mat;
		private final double cx, cy, cz;
		private VertexConsumer vc;
		private boolean quadsPass;

		Draw(MatrixStack ms, Vec3d cam) {
			this.entry = ms.peek();
			this.mat = entry.getPositionMatrix();
			this.cx = cam.x;
			this.cy = cam.y;
			this.cz = cam.z;
		}

		void begin(VertexConsumer vc, boolean quadsPass) {
			this.vc = vc;
			this.quadsPass = quadsPass;
		}

		private void v(double x, double y, double z, int rgb, float a) {
			vc.vertex(mat, (float) (x - cx), (float) (y - cy), (float) (z - cz))
				.color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, Math.round(a * 255f))));
		}

		/** Czworokat dwustronny. Wierzcholki 0,1 maja alfe a01, wierzcholki 2,3 alfe a23. */
		void quad(double[] p0, double[] p1, double[] p2, double[] p3, int rgb, float a01, float a23) {
			if (!quadsPass) {
				return;
			}
			v(p0[0], p0[1], p0[2], rgb, a01);
			v(p1[0], p1[1], p1[2], rgb, a01);
			v(p2[0], p2[1], p2[2], rgb, a23);
			v(p3[0], p3[1], p3[2], rgb, a23);
			v(p3[0], p3[1], p3[2], rgb, a23);
			v(p2[0], p2[1], p2[2], rgb, a23);
			v(p1[0], p1[1], p1[2], rgb, a01);
			v(p0[0], p0[1], p0[2], rgb, a01);
		}

		void quad(double[] p0, double[] p1, double[] p2, double[] p3, int rgb, float a) {
			quad(p0, p1, p2, p3, rgb, a, a);
		}

		void line(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float a) {
			if (quadsPass) {
				return;
			}
			float nx = (float) (x2 - x1), ny = (float) (y2 - y1), nz = (float) (z2 - z1);
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-6f) {
				return;
			}
			nx /= len;
			ny /= len;
			nz /= len;
			int al = Math.max(0, Math.min(255, Math.round(a * 255f)));
			vc.vertex(mat, (float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz))
				.color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, al).normal(entry, nx, ny, nz);
			vc.vertex(mat, (float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz))
				.color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, al).normal(entry, nx, ny, nz);
		}

		/** Plaski pierscien (wstega) w poziomie. */
		void ring(double x, double y, double z, double r, double width, int rgb, float a, int seg) {
			double r1 = Math.max(0.01, r - width / 2), r2 = r + width / 2;
			for (int i = 0; i < seg; i++) {
				double a0 = i * Math.PI * 2.0 / seg, a1 = (i + 1) * Math.PI * 2.0 / seg;
				quad(new double[]{x + Math.cos(a0) * r1, y, z + Math.sin(a0) * r1},
					new double[]{x + Math.cos(a1) * r1, y, z + Math.sin(a1) * r1},
					new double[]{x + Math.cos(a1) * r2, y, z + Math.sin(a1) * r2},
					new double[]{x + Math.cos(a0) * r2, y, z + Math.sin(a0) * r2}, rgb, a);
			}
		}

		/** Pionowa sciana walca z gradientem alfy (dol -> gora). */
		void wall(double x, double y, double z, double r, double h, int rgb, float aBottom, float aTop, int seg) {
			for (int i = 0; i < seg; i++) {
				double a0 = i * Math.PI * 2.0 / seg, a1 = (i + 1) * Math.PI * 2.0 / seg;
				quad(new double[]{x + Math.cos(a0) * r, y, z + Math.sin(a0) * r},
					new double[]{x + Math.cos(a1) * r, y, z + Math.sin(a1) * r},
					new double[]{x + Math.cos(a1) * r, y + h, z + Math.sin(a1) * r},
					new double[]{x + Math.cos(a0) * r, y + h, z + Math.sin(a0) * r}, rgb, aBottom, aTop);
			}
		}

		void cube(double x, double y, double z, double size, int rgb, float a) {
			double s = size / 2;
			boxFaces(new Box(x - s, y - s, z - s, x + s, y + s, z + s), rgb, a);
		}

		void boxFaces(Box b, int rgb, float a) {
			double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
			quad(new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x0, y0, z1}, rgb, a);
			quad(new double[]{x0, y1, z0}, new double[]{x1, y1, z0}, new double[]{x1, y1, z1}, new double[]{x0, y1, z1}, rgb, a);
			quad(new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y1, z0}, new double[]{x0, y1, z0}, rgb, a);
			quad(new double[]{x0, y0, z1}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x0, y1, z1}, rgb, a);
			quad(new double[]{x0, y0, z0}, new double[]{x0, y0, z1}, new double[]{x0, y1, z1}, new double[]{x0, y1, z0}, rgb, a);
			quad(new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x1, y1, z0}, rgb, a);
		}

		void boxLines(Box b, int rgb, float a) {
			double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
			line(x0, y0, z0, x1, y0, z0, rgb, a); line(x1, y0, z0, x1, y0, z1, rgb, a);
			line(x1, y0, z1, x0, y0, z1, rgb, a); line(x0, y0, z1, x0, y0, z0, rgb, a);
			line(x0, y1, z0, x1, y1, z0, rgb, a); line(x1, y1, z0, x1, y1, z1, rgb, a);
			line(x1, y1, z1, x0, y1, z1, rgb, a); line(x0, y1, z1, x0, y1, z0, rgb, a);
			line(x0, y0, z0, x0, y1, z0, rgb, a); line(x1, y0, z0, x1, y1, z0, rgb, a);
			line(x1, y0, z1, x1, y1, z1, rgb, a); line(x0, y0, z1, x0, y1, z1, rgb, a);
		}
	}
}
