package com.visualtarget;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Miekkie, polprzezroczyste ksztalty: swiecace kule, pierscienie na ziemi, ksztalty z gradientem.
 * Wspolrzedne podajemy WZGLEDEM KAMERY. Kolory w formacie ARGB.
 */
final class Fx {
	private static final double[] C8 = new double[9];
	private static final double[] S8 = new double[9];

	static {
		for (int k = 0; k <= 8; k++) {
			C8[k] = Math.cos(k * Math.PI / 4.0);
			S8[k] = Math.sin(k * Math.PI / 4.0);
		}
	}

	private final VertexConsumer vc;
	private final Matrix4f m;
	private final VertexConsumerProvider provider;
	final Theme theme = VisualConfig.get().theme();
	/** Prawo i gora kamery (do ksztaltow zwroconych w strone gracza). */
	final float rx, ry, rz, ux, uy, uz;

	private Fx(VertexConsumer vc, Matrix4f m, VertexConsumerProvider provider, Vector3f r, Vector3f u) {
		this.vc = vc;
		this.m = m;
		this.provider = provider;
		this.rx = r.x;
		this.ry = r.y;
		this.rz = r.z;
		this.ux = u.x;
		this.uy = u.y;
		this.uz = u.z;
	}

	static Fx begin(WorldRenderContext ctx) {
		MatrixStack ms = ctx.matrixStack();
		VertexConsumerProvider consumers = ctx.consumers();
		if (ms == null || consumers == null) return null;
		Quaternionf q = ctx.camera().getRotation();
		Vector3f r = new Vector3f(1f, 0f, 0f).rotate(q);
		Vector3f u = new Vector3f(0f, 1f, 0f).rotate(q);
		return new Fx(consumers.getBuffer(RenderLayer.getLightning()), ms.peek().getPositionMatrix(), consumers, r, u);
	}

	void end() {
		if (provider instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLightning());
		}
	}

	// ------------------------------------------------------------------
	// Podstawy
	// ------------------------------------------------------------------

	void v(double x, double y, double z, int argb) {
		vc.vertex(m, (float) x, (float) y, (float) z)
				.color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
	}

	/** Czworokat (jednostronny). */
	void quad(double x1, double y1, double z1, int c1, double x2, double y2, double z2, int c2,
			  double x3, double y3, double z3, int c3, double x4, double y4, double z4, int c4) {
		v(x1, y1, z1, c1);
		v(x2, y2, z2, c2);
		v(x3, y3, z3, c3);
		v(x4, y4, z4, c4);
	}

	/** Czworokat widoczny z obu stron. */
	void quad2(double x1, double y1, double z1, int c1, double x2, double y2, double z2, int c2,
			   double x3, double y3, double z3, int c3, double x4, double y4, double z4, int c4) {
		quad(x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3, x4, y4, z4, c4);
		quad(x4, y4, z4, c4, x3, y3, z3, c3, x2, y2, z2, c2, x1, y1, z1, c1);
	}

	/** Okragla, miekka plamka zwrocona do kamery: srodek w kolorze, brzeg przezroczysty. */
	void disc(double x, double y, double z, double radius, int centerArgb) {
		int rim = centerArgb & 0x00FFFFFF;
		for (int j = 0; j < 4; j++) {
			int k0 = 2 * j;
			int k1 = k0 + 1;
			int k2 = k0 + 2;
			v(x, y, z, centerArgb);
			v(x + (rx * C8[k0] + ux * S8[k0]) * radius, y + (ry * C8[k0] + uy * S8[k0]) * radius, z + (rz * C8[k0] + uz * S8[k0]) * radius, rim);
			v(x + (rx * C8[k1] + ux * S8[k1]) * radius, y + (ry * C8[k1] + uy * S8[k1]) * radius, z + (rz * C8[k1] + uz * S8[k1]) * radius, rim);
			v(x + (rx * C8[k2] + ux * S8[k2]) * radius, y + (ry * C8[k2] + uy * S8[k2]) * radius, z + (rz * C8[k2] + uz * S8[k2]) * radius, rim);
		}
	}

	/** Pierscien (lub kolo, gdy rIn = 0) lezacy plasko na ziemi, z gradientem od srodka na zewnatrz. */
	void groundRing(double cx, double cy, double cz, double rIn, double rOut, int segs, int cIn, int cOut) {
		if (rOut <= rIn) return;
		double px0 = Math.cos(0), pz0 = Math.sin(0);
		for (int j = 0; j < segs; j++) {
			double a0 = 2.0 * Math.PI * j / segs;
			double a1 = 2.0 * Math.PI * (j + 1) / segs;
			double c0 = Math.cos(a0), s0 = Math.sin(a0);
			double c1 = Math.cos(a1), s1 = Math.sin(a1);
			quad2(cx + c0 * rIn, cy, cz + s0 * rIn, cIn,
					cx + c1 * rIn, cy, cz + s1 * rIn, cIn,
					cx + c1 * rOut, cy, cz + s1 * rOut, cOut,
					cx + c0 * rOut, cy, cz + s0 * rOut, cOut);
		}
	}

	// ------------------------------------------------------------------
	// Narzedzia
	// ------------------------------------------------------------------

	/** ARGB z alfa 0..255 (liczba, obcinana) i kolorem RGB. */
	static int argb(double alpha, int rgb) {
		int a = (int) Math.round(alpha);
		if (a < 0) a = 0;
		if (a > 255) a = 255;
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	static double smooth(double e0, double e1, double x) {
		double t = clamp01((x - e0) / (e1 - e0));
		return t * t * (3.0 - 2.0 * t);
	}

	/** Dodatnia reszta z dzielenia. */
	static double mod(double v, double m) {
		return ((v % m) + m) % m;
	}

	/** Deterministyczny "losowy" ulamek 0..1 dla (indeks, sol). */
	static double hash(int i, int salt) {
		int h = i * 374761393 + salt * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}
}
