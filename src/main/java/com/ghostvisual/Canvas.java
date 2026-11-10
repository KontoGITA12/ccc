package com.ghostvisual;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Lista polecen rysowania w jednostkach panelu (zapisywana co klatke, rysowana w swiecie 3D). */
final class Canvas {
	record Rect(float x, float y, float w, float h, int argb, float z) {
	}

	record Txt(String s, float x, float y, float scale, int argb, boolean bold, float z) {
	}

	record Img(Identifier id, float x, float y, float w, float h, float z) {
	}

	/** Aktualna glebokosc warstwy (w blokach, + w strone gracza). */
	float z = 0f;

	final List<Rect> rects = new ArrayList<>();
	final List<Txt> texts = new ArrayList<>();
	final List<Img> imgs = new ArrayList<>();

	void rect(float x, float y, float w, float h, int argb) {
		if ((argb >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		rects.add(new Rect(x, y, w, h, argb, z));
	}

	private static int inset(int r, int i) {
		double dy = r - i - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	void rrect(float x, float y, float w, float h, int radius, int argb) {
		int r = (int) Math.min(radius, Math.min(w, h) / 2);
		if (r <= 0) {
			rect(x, y, w, h, argb);
			return;
		}
		rect(x, y + r, w, h - 2 * r, argb);
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			rect(x + in, y + i, w - 2 * in, 1, argb);
			rect(x + in, y + h - 1 - i, w - 2 * in, 1, argb);
		}
	}

	void rborder(float x, float y, float w, float h, int radius, int argb) {
		int r = (int) Math.min(radius, Math.min(w, h) / 2);
		rect(x + r, y, w - 2 * r, 1, argb);
		rect(x + r, y + h - 1, w - 2 * r, 1, argb);
		rect(x, y + r, 1, h - 2 * r, argb);
		rect(x + w - 1, y + r, 1, h - 2 * r, argb);
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			int next = i + 1 < r ? inset(r, i + 1) : r;
			int len = Math.max(1, next - in);
			rect(x + in, y + i, len, 1, argb);
			rect(x + w - in - len, y + i, len, 1, argb);
			rect(x + in, y + h - 1 - i, len, 1, argb);
			rect(x + w - in - len, y + h - 1 - i, len, 1, argb);
		}
	}

	void text(String s, float x, float y, float scale, int argb, boolean bold) {
		if (s == null || s.isEmpty()) {
			return;
		}
		texts.add(new Txt(s, x, y, scale, argb, bold, z));
	}

	void text(String s, float x, float y, float scale, int argb) {
		text(s, x, y, scale, argb, false);
	}

	float width(String s, float scale) {
		return Gfx.width(s) * scale;
	}

	void textRight(String s, float xRight, float y, float scale, int argb) {
		text(s, xRight - width(s, scale), y, scale, argb, false);
	}

	void textCenter(String s, float xCenter, float y, float scale, int argb, boolean bold) {
		text(s, xCenter - width(s, scale) / 2f, y, scale, argb, bold);
	}

	void img(Identifier id, float x, float y, float w, float h) {
		imgs.add(new Img(id, x, y, w, h, z));
	}
}
