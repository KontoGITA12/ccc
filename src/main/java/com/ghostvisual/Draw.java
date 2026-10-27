package com.ghostvisual;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Pomocnik do rysowania ksztaltow w swiecie (wspolrzedne swiata).
 * Kazda klatka jest rysowana w kilku przebiegach (mode). Wywolanie rysuje sie tylko
 * wtedy, gdy jego cel (quadTarget albo LINES) zgadza sie z aktualnym przebiegiem.
 */
final class Draw {
	static final int SOLID = 0;  // nieprzezroczyste, z testem glebi
	static final int ADD = 1;    // swiecace (addytywne), z testem glebi
	static final int XRAY = 2;   // przez sciany (bez testu glebi)
	static final int LINES = 3;  // linie

	private final MatrixStack.Entry entry;
	private final Matrix4f mat;
	final double cx, cy, cz;
	private VertexConsumer vc;
	private int mode;
	/** Do jakiego przebiegu naleza teraz rysowane czworokaty. */
	int quadTarget = ADD;

	Draw(MatrixStack ms, Vec3d cam) {
		this.entry = ms.peek();
		this.mat = entry.getPositionMatrix();
		this.cx = cam.x;
		this.cy = cam.y;
		this.cz = cam.z;
	}

	void begin(VertexConsumer vc, int mode) {
		this.vc = vc;
		this.mode = mode;
	}

	private void v(double x, double y, double z, int rgb, float a) {
		vc.vertex(mat, (float) (x - cx), (float) (y - cy), (float) (z - cz))
			.color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, Math.round(a * 255f))));
	}

	/** Czworokat dwustronny. Wierzcholki 0,1 maja alfe a01, wierzcholki 2,3 alfe a23. */
	void quad(double[] p0, double[] p1, double[] p2, double[] p3, int rgb, float a01, float a23) {
		if (mode != quadTarget) {
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

	/** Czworokat dwustronny z osobnym kolorem dla kazdego wierzcholka. */
	void quadV(double[] p0, double[] p1, double[] p2, double[] p3, int c0, int c1, int c2, int c3, float a) {
		if (mode != quadTarget) {
			return;
		}
		v(p0[0], p0[1], p0[2], c0, a);
		v(p1[0], p1[1], p1[2], c1, a);
		v(p2[0], p2[1], p2[2], c2, a);
		v(p3[0], p3[1], p3[2], c3, a);
		v(p3[0], p3[1], p3[2], c3, a);
		v(p2[0], p2[1], p2[2], c2, a);
		v(p1[0], p1[1], p1[2], c1, a);
		v(p0[0], p0[1], p0[2], c0, a);
	}

	void line(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float a) {
		if (mode != LINES) {
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

	/** Kula z czworokatow (przyblizona). */
	void sphere(double x, double y, double z, double r, int rgb, float a, int seg) {
		int lat = Math.max(3, seg / 2);
		for (int i = 0; i < lat; i++) {
			double e0 = -Math.PI / 2 + Math.PI * i / lat, e1 = -Math.PI / 2 + Math.PI * (i + 1) / lat;
			for (int j = 0; j < seg; j++) {
				double a0 = j * Math.PI * 2.0 / seg, a1 = (j + 1) * Math.PI * 2.0 / seg;
				quad(sp(x, y, z, r, e0, a0), sp(x, y, z, r, e0, a1), sp(x, y, z, r, e1, a1), sp(x, y, z, r, e1, a0), rgb, a);
			}
		}
	}

	private static double[] sp(double x, double y, double z, double r, double e, double a) {
		return new double[]{x + Math.cos(e) * Math.cos(a) * r, y + Math.sin(e) * r, z + Math.cos(e) * Math.sin(a) * r};
	}

	/**
	 * Prety bar (dwie skrzyzowane plaszczyzny) od punktu a do b.
	 * u i v to dwa prostopadle wektory jednostkowe szerokosci.
	 */
	void bar(double[] a, double[] b, double[] u, double[] v, double w, int rgb, float alpha) {
		quad(new double[]{a[0] - u[0] * w, a[1] - u[1] * w, a[2] - u[2] * w},
			new double[]{a[0] + u[0] * w, a[1] + u[1] * w, a[2] + u[2] * w},
			new double[]{b[0] + u[0] * w, b[1] + u[1] * w, b[2] + u[2] * w},
			new double[]{b[0] - u[0] * w, b[1] - u[1] * w, b[2] - u[2] * w}, rgb, alpha);
		quad(new double[]{a[0] - v[0] * w, a[1] - v[1] * w, a[2] - v[2] * w},
			new double[]{a[0] + v[0] * w, a[1] + v[1] * w, a[2] + v[2] * w},
			new double[]{b[0] + v[0] * w, b[1] + v[1] * w, b[2] + v[2] * w},
			new double[]{b[0] - v[0] * w, b[1] - v[1] * w, b[2] - v[2] * w}, rgb, alpha);
	}

	/** Pelny prostopadloscian od start do end z osiami szerokosci n i c, z cieniowaniem scian. */
	void solidBox(double[] s, double[] e, double[] n, double[] c, double w, int rgb) {
		double[][] p = new double[8][];
		int k = 0;
		for (int end = 0; end < 2; end++) {
			double[] o = end == 0 ? s : e;
			for (int sn = -1; sn <= 1; sn += 2) {
				for (int sc = -1; sc <= 1; sc += 2) {
					p[k++] = new double[]{
						o[0] + n[0] * w * sn + c[0] * w * sc,
						o[1] + n[1] * w * sn + c[1] * w * sc,
						o[2] + n[2] * w * sn + c[2] * w * sc};
				}
			}
		}
		// indeksy: koniec*4 + (sn==1?2:0) + (sc==1?1:0)
		quad(p[0], p[1], p[3], p[2], shade(rgb, 0.65f), 1f);   // poczatek
		quad(p[4], p[5], p[7], p[6], shade(rgb, 1.0f), 1f);    // koniec
		quad(p[0], p[1], p[5], p[4], shade(rgb, 0.8f), 1f);    // sn = -1
		quad(p[2], p[3], p[7], p[6], shade(rgb, 0.8f), 1f);    // sn = +1
		quad(p[0], p[2], p[6], p[4], shade(rgb, 0.55f), 1f);   // sc = -1
		quad(p[1], p[3], p[7], p[5], shade(rgb, 0.9f), 1f);    // sc = +1
	}

	private static int shade(int rgb, float f) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 255) * f));
		int g = Math.min(255, Math.round(((rgb >> 8) & 255) * f));
		int b = Math.min(255, Math.round((rgb & 255) * f));
		return (r << 16) | (g << 8) | b;
	}
}
