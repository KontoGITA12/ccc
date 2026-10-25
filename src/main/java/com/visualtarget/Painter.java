package com.visualtarget;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Wspolny pomocnik do rysowania linii, pierscieni i boxow w kolorach aktualnego motywu.
 * Wspolrzedne podajemy WZGLEDEM KAMERY (pozycja w swiecie minus pozycja kamery).
 */
final class Painter {
	private final VertexConsumer vc;
	private final MatrixStack.Entry entry;
	private final VertexConsumerProvider provider;
	private final VisualConfig cfg = VisualConfig.get();
	private final Theme theme = cfg.theme();

	/** Czas animacji (sekundy) - ustawiany przez rysujacego. */
	double t = 0.0;
	/** Mnoznik przezroczystosci 0..1 (np. do plynnego znikania). */
	float mult = 1f;

	private Painter(VertexConsumer vc, MatrixStack.Entry entry, VertexConsumerProvider provider) {
		this.vc = vc;
		this.entry = entry;
		this.provider = provider;
	}

	/** Zaczyna rysowanie w swiecie; zwraca null, gdy nie da sie rysowac. */
	static Painter begin(WorldRenderContext ctx) {
		MatrixStack matrices = ctx.matrixStack();
		VertexConsumerProvider consumers = ctx.consumers();
		if (matrices == null || consumers == null) return null;
		return new Painter(consumers.getBuffer(RenderLayer.getLines()), matrices.peek(), consumers);
	}

	/** Wypycha narysowane linie na ekran. */
	void end() {
		if (provider instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getLines());
		}
	}

	Theme theme() {
		return theme;
	}

	private double shift(double phase) {
		return cfg.animateColors ? phase + t * 0.12 : phase;
	}

	/** Kolor efektu dla fazy 0..1 (z gradientu motywu). */
	int rgb(double phase) {
		return theme.fx(shift(phase));
	}

	/** Linia w kolorze motywu (z opcjonalna poswiata). */
	void line(double x1, double y1, double z1, double x2, double y2, double z2, double phase, int alpha) {
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

		double sp = shift(phase);
		raw(x1, y1, z1, x2, y2, z2, theme.fx(sp), a, dx, dy, dz);

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
			int ga = (int) (a * 0.45f);
			if (ga <= 0) return;
			int gc = theme.glow(sp);
			raw(x1 + p1x * e, y1 + p1y * e, z1 + p1z * e, x2 + p1x * e, y2 + p1y * e, z2 + p1z * e, gc, ga, dx, dy, dz);
			raw(x1 - p1x * e, y1 - p1y * e, z1 - p1z * e, x2 - p1x * e, y2 - p1y * e, z2 - p1z * e, gc, ga, dx, dy, dz);
			raw(x1 + p2x * e, y1 + p2y * e, z1 + p2z * e, x2 + p2x * e, y2 + p2y * e, z2 + p2z * e, gc, ga, dx, dy, dz);
			raw(x1 - p2x * e, y1 - p2y * e, z1 - p2z * e, x2 - p2x * e, y2 - p2y * e, z2 - p2z * e, gc, ga, dx, dy, dz);
		}
	}

	/** Cienka linia w dowolnym kolorze RGB, bez poswiaty (np. platki sniegu). */
	void lineThin(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, int alpha) {
		int a = Math.max(0, Math.min(255, (int) (alpha * mult)));
		if (a <= 0) return;
		float dx = (float) (x2 - x1);
		float dy = (float) (y2 - y1);
		float dz = (float) (z2 - z1);
		float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-6f) return;
		raw(x1, y1, z1, x2, y2, z2, rgb, a, dx / len, dy / len, dz / len);
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
			line(px, cy, pz, nx, cy, nz, phaseOffset + (double) i / segments, alpha);
			px = nx;
			pz = nz;
		}
	}

	void box(double x0, double y0, double z0, double x1, double y1, double z1, int alpha) {
		double bottom = 0.0;
		double top = 0.5;
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

	void cross(double x, double y, double z, double s, double phase, int alpha) {
		line(x - s, y, z, x + s, y, z, phase, alpha);
		line(x, y - s, z, x, y + s, z, phase, alpha);
		line(x, y, z - s, x, y, z + s, phase, alpha);
	}
}
