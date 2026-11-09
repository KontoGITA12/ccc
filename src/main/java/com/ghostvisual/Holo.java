package com.ghostvisual;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel GUI w swiecie 3D. Stoi w jednym miejscu w swiecie. Moze byc:
 *  - interaktywny (otwarty HoloScreen, wolny kursor, trafiasz promieniem w panel),
 *  - przypiety (kursor wraca do gry, chodzisz, a panel zostaje w miejscu).
 */
public final class Holo {
	private Holo() {
	}

	public static final String VERSION = "1.3.2";
	static final int W = 560, H = 336;
	static final double S = 0.0062;

	public static boolean open = false;
	public static boolean interactive = false;

	// polozenie panelu w swiecie
	static double cx, cy, cz;   // srodek
	static double ux, uz;       // wektor "w prawo" (poziomy)
	static double nx, nz;       // normalna w strone gracza

	// wejscie (wspolrzedne panelu)
	static float mx = -999, my = -999;
	static boolean hasMouse = false;
	static boolean pressed = false;
	static boolean down = false;
	static float scroll = 0;
	static int dragId = 0;

	// stan interfejsu
	private static int tab = 0, cat = 0, page = 0, selected = 0;

	private static final String[] TABS = {"Visuals", "Style", "Config", "Profil"};
	private static final String[] CATS = {"Wszystkie", "Cel", "Świat", "Gracz", "Walka", "HUD"};
	private static final String[] NAMES = {
		"Efekty celu", "Xray (kości)", "Glass", "China Hat", "Pety", "Okrąg przy skoku", "Ślady",
		"Deszcz", "Zamieć", "Świetliki",
		"Niebo: Aurora", "Niebo: Mgławica", "Niebo: Zmierzch", "Niebo: Fiolet", "Niebo: Szare",
		"Shockwave", "Palka po zabiciu", "Piorun po zabiciu",
		"Panel muzyki", "Tekst muzyki na niebie", "Panel FPS i ping", "Panel celu",
		"Dźwięk trafienia", "Dźwięk zabicia"};
	private static final int[] CAT = {1, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 4, 4, 4, 5, 5, 5, 5, 4, 4};
	private static final String[] DESC = {
		"Pierścień, skaner, orbita i poświata wokół gracza lub moba, w którego celujesz.",
		"Świecący szkielet widoczny przez model gracza i przez ściany.",
		"Przezroczysta skóra graczy i itemów leżących na ziemi.",
		"Kapelusz nad głową: tylko na Tobie albo na wszystkich graczach.",
		"Zwykły mob z Minecrafta, który chodzi za Tobą. Widzisz go tylko Ty.",
		"Okrąg rozchodzący się pod nogami przy skoku.",
		"Świecący ślad za Tobą podczas biegu.",
		"Deszcz rysowany na ekranie.", "Śnieżna zamieć rysowana na ekranie.", "Świecące świetliki na ekranie.",
		"Zielone i fioletowe zorze na nocnym niebie. Zastępuje niebo z packa.",
		"Kolorowa mgławica i gwiazdy. Zastępuje niebo z packa.",
		"Zachód słońca z ciepłym horyzontem. Zastępuje niebo z packa.",
		"Fioletowe niebo z pulsującym blaskiem. Zastępuje niebo z packa.",
		"Pochmurne, szare niebo. Zastępuje niebo z packa.",
		"Fala uderzeniowa po trafieniu albo po zabiciu.",
		"Wielka palka spada na miejsce zabicia. Tylko wizualnie.",
		"Piorun uderza w miejsce zabicia. Tylko wizualnie.",
		"Panel w lewym górnym rogu z okładką i sterowaniem muzyką.",
		"Tytuł i wykonawca grającej muzyki jako napis na niebie.",
		"Mała pigułka z nickiem, pingiem i FPS u góry ekranu.",
		"Nick i pasek HP celu pod celownikiem.",
		"Dźwięk przy każdym trafieniu w gracza lub moba. Słyszysz go tylko Ty.",
		"Dźwięk anime przy zabiciu. Słyszysz go tylko Ty."};

	// ------------------------------------------------------------ stan i polozenie

	public static void open(MinecraftClient mc, boolean interact) {
		if (mc.player == null) {
			return;
		}
		anchor(mc);
		open = true;
		interactive = interact;
		if (interact) {
			mc.setScreen(new HoloScreen());
		}
	}

	/** Ustawia panel przed graczem (w jego aktualnym kierunku patrzenia). */
	public static void anchor(MinecraftClient mc) {
		var p = mc.player;
		Vec3d eye = p.getEyePos();
		double yaw = Math.toRadians(p.getYaw());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		cx = eye.x + fx * 2.9;
		cy = eye.y - 0.05;
		cz = eye.z + fz * 2.9;
		ux = -Math.cos(yaw);
		uz = -Math.sin(yaw);
		nx = -fx;
		nz = -fz;
	}

	public static void close(MinecraftClient mc) {
		open = false;
		interactive = false;
		if (mc.currentScreen instanceof HoloScreen) {
			mc.setScreen(null);
		}
	}

	/** Przypina panel: kursor wraca do gry, panel zostaje w swiecie. */
	public static void pin(MinecraftClient mc) {
		interactive = false;
		if (mc.currentScreen instanceof HoloScreen) {
			mc.setScreen(null);
		}
	}

	public static void toggleOpen(MinecraftClient mc) {
		if (open) {
			close(mc);
		} else {
			open(mc, true);
		}
	}

	public static void toggleLock(MinecraftClient mc) {
		if (!open) {
			open(mc, true);
		} else if (mc.currentScreen instanceof HoloScreen) {
			pin(mc);
		} else if (mc.currentScreen == null) {
			interactive = true;
			mc.setScreen(new HoloScreen());
		}
	}

	// ------------------------------------------------------------ celowanie kursorem

	private static org.joml.Matrix4f invProj, invView;

	/** Pozycja kursora (wsp. GUI) -> wsp. panelu. */
	public static void pick(MinecraftClient mc, double sx, double sy) {
		double w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		pickNdc(mc, 2 * sx / w - 1, 1 - 2 * sy / h);
	}

	/** Promien z kamery przez punkt ekranu (NDC) na plaszczyzne panelu, dokladnie wg macierzy renderu. */
	static void pickNdc(MinecraftClient mc, double ndx, double ndy) {
		hasMouse = false;
		if (invProj == null || invView == null) {
			return;
		}
		Vec3d o = mc.gameRenderer.getCamera().getPos();
		org.joml.Vector4f e = new org.joml.Vector4f((float) ndx, (float) ndy, 1f, 1f).mul(invProj);
		e.div(e.w);
		org.joml.Vector4f wd = new org.joml.Vector4f(e.x, e.y, e.z, 0f).mul(invView);
		double dx = wd.x, dy = wd.y, dz = wd.z;
		double denom = dx * nx + dz * nz;
		if (Math.abs(denom) < 1e-6) {
			return;
		}
		double t = ((cx - o.x) * nx + (cz - o.z) * nz) / denom;
		if (t <= 0) {
			return;
		}
		double px = o.x + dx * t - cx, py = o.y + dy * t - cy, pz = o.z + dz * t - cz;
		mx = (float) ((px * ux + pz * uz) / S + W / 2.0);
		my = (float) (H / 2.0 - py / S);
		hasMouse = mx >= 0 && mx <= W && my >= 0 && my <= H;
	}

	/** Wywolywane co tick: klikanie panelu lewym przyciskiem, gdy jest przypiety (celownik jako kursor). */
	public static void tickPinned(MinecraftClient mc) {
		if (!open || interactive || mc.currentScreen != null || mc.player == null) {
			return;
		}
		var atk = mc.options.attackKey;
		if (hasMouse) {
			boolean any = false;
			while (atk.wasPressed()) {
				pressed = true;
				any = true;
			}
			atk.setPressed(false);
			boolean now = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			if (any || now) {
				down = true;
			}
			if (!now && !any) {
				if (down) {
					Config.get().save();
				}
				down = false;
				dragId = 0;
			}
		} else if (down) {
			boolean now = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			if (!now) {
				down = false;
				dragId = 0;
				Config.get().save();
			}
		}
	}

	private static double[] P(double u, double v) {
		double a = (u - W / 2.0) * S;
		return new double[]{cx + ux * a, cy + (H / 2.0 - v) * S, cz + uz * a};
	}

	// ------------------------------------------------------------ rysowanie w swiecie

	public static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!open || mc.player == null || mc.world == null) {
			return;
		}
		MatrixStack ms = ctx.matrixStack();
		VertexConsumerProvider vcp = ctx.consumers();
		if (ms == null || vcp == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		double ddx = cx - cam.x, ddz = cz - cam.z;
		if (ddx * ddx + ddz * ddz > 80 * 80) {
			return;
		}
		try {
			invProj = new Matrix4f(ctx.projectionMatrix()).invert();
			invView = new Matrix4f().rotation(ctx.camera().getRotation());
		} catch (Exception ignored) {
		}
		if (!interactive && mc.currentScreen == null) {
			pickNdc(mc, 0, 0);
		} else if (!interactive) {
			hasMouse = false;
		}
		Canvas c = new Canvas();
		build(c, mc, Config.get());
		pressed = false;
		scroll = 0;

		// 1) prostokaty (przez RenderLayer bez testu glebi, w kolejnosci rysowania)
		Draw d = new Draw(ms, cam);
		RenderLayer layer = RenderLayer.getGuiOverlay();
		d.begin(vcp.getBuffer(layer), Draw.XRAY);
		d.quadTarget = Draw.XRAY;
		for (Canvas.Rect r : c.rects) {
			float a = (r.argb() >>> 24) / 255f;
			d.quad(P(r.x(), r.y()), P(r.x() + r.w(), r.y()), P(r.x() + r.w(), r.y() + r.h()), P(r.x(), r.y() + r.h()),
				r.argb() & 0xFFFFFF, a);
		}
		if (vcp instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw(layer);
		}

		// 2) obrazki (logo)
		Matrix4f mat = ms.peek().getPositionMatrix();
		for (Canvas.Img im : c.imgs) {
			RenderLayer il = RenderLayer.getGuiTextured(im.id());
			VertexConsumer vc = vcp.getBuffer(il);
			double[][] p = {P(im.x(), im.y()), P(im.x() + im.w(), im.y()), P(im.x() + im.w(), im.y() + im.h()), P(im.x(), im.y() + im.h())};
			float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
			for (int pass = 0; pass < 2; pass++) {
				for (int k = 0; k < 4; k++) {
					int i = pass == 0 ? k : 3 - k;
					vc.vertex(mat, (float) (p[i][0] - cam.x), (float) (p[i][1] - cam.y), (float) (p[i][2] - cam.z))
						.texture(uv[i][0], uv[i][1]).color(255, 255, 255, 255);
				}
			}
			if (vcp instanceof VertexConsumerProvider.Immediate imm) {
				imm.draw(il);
			}
		}

		// 3) tekst
		TextRenderer tr = mc.textRenderer;
		for (Canvas.Txt t : c.texts) {
			double[] o = P(t.x(), t.y());
			double s = S * t.scale();
			Matrix4f m = new Matrix4f(mat);
			Matrix4f local = new Matrix4f().set(
				(float) (ux * s), 0f, (float) (uz * s), 0f,
				0f, (float) -s, 0f, 0f,
				(float) (-nx * s), 0f, (float) (-nz * s), 0f,
				(float) (o[0] - cam.x), (float) (o[1] - cam.y), (float) (o[2] - cam.z), 1f);
			m.mul(local);
			Text text = t.bold() ? Text.literal(t.s()).formatted(Formatting.BOLD) : Text.literal(t.s());
			tr.draw(text, 0f, 0f, t.argb(), false, m, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0,
				LightmapTextureManager.MAX_LIGHT_COORDINATE);
		}
		if (vcp instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw();
		}
	}

	// ------------------------------------------------------------ narzedzia UI (tryb natychmiastowy)

	private static boolean hover(float x, float y, float w, float h) {
		return hasMouse && mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static boolean clicked(float x, float y, float w, float h) {
		return pressed && hover(x, y, w, h);
	}

	private static int col(int rgb, float a) {
		return Gfx.argb(rgb, a);
	}

	private static void sw(Canvas c, float x, float y, boolean on, int ac) {
		c.rrect(x, y, 26, 12, 6, on ? col(ac, 0.95f) : col(0xFFFFFF, 0.2f));
		c.rrect(on ? x + 15 : x + 1, y + 1, 10, 10, 5, 0xFFFFFFFF);
	}

	private static List<String> wrap(String s, float maxW, float scale) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String test = line.length() == 0 ? word : line + " " + word;
			if (Gfx.width(test) * scale > maxW && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}

	private static String fit(String s, float maxW, float scale) {
		return Gfx.tr().trimToWidth(s, (int) (maxW / scale));
	}

	// pole robocze panelu szczegolow
	private static float fx, fy, fw;
	private static int accent;

	private static boolean tog(Canvas c, String label, boolean v) {
		if (clicked(fx - 2, fy, fw + 4, 18)) {
			v = !v;
		}
		c.text(label, fx, fy + 5, 0.85f, hover(fx - 2, fy, fw + 4, 18) ? 0xFFFFFFFF : col(0xFFFFFF, 0.85f));
		sw(c, fx + fw - 26, fy + 3, v, accent);
		fy += 21;
		return v;
	}

	private static float sld(Canvas c, String label, float v, float min, float max, String fmt, float mul) {
		int id = (label.hashCode() * 31 + (int) fy) | 1;
		if (pressed && hover(fx - 2, fy + 8, fw + 4, 12)) {
			dragId = id;
		}
		if (down && dragId == id && hasMouse) {
			v = min + (max - min) * Math.max(0f, Math.min(1f, (mx - fx) / fw));
		}
		c.text(label, fx, fy + 1, 0.85f, col(0xFFFFFF, 0.85f));
		String val = String.format(fmt, v * mul);
		c.textRight(val, fx + fw, fy + 1, 0.85f, col(accent, 1f));
		float f = (v - min) / (max - min);
		f = Math.max(0f, Math.min(1f, f));
		c.rrect(fx, fy + 13, fw, 3, 1, col(0xFFFFFF, 0.2f));
		c.rrect(fx, fy + 13, Math.max(2f, fw * f), 3, 1, col(accent, 1f));
		c.rrect(fx + fw * f - 3, fy + 11, 7, 7, 3, 0xFFFFFFFF);
		fy += 25;
		return v;
	}

	private static int cho(Canvas c, String label, String[] o, int i) {
		float bx = fx + fw - 104;
		if (clicked(fx - 2, fy, fw + 4, 18)) {
			i = (i + (mx < bx + 52 ? -1 : 1) + o.length) % o.length;
		}
		c.text(label, fx, fy + 5, 0.85f, col(0xFFFFFF, 0.85f));
		c.rrect(bx, fy + 1, 104, 16, 5, col(0xFFFFFF, hover(bx, fy, 104, 18) ? 0.12f : 0.07f));
		c.text("<", bx + 5, fy + 5, 0.85f, col(accent, 1f));
		c.text(">", bx + 104 - 10, fy + 5, 0.85f, col(accent, 1f));
		c.textCenter(fit(o[i], 70, 0.8f), bx + 52, fy + 5, 0.8f, 0xFFFFFFFF, false);
		fy += 21;
		return i;
	}

	private static void note(Canvas c, String s) {
		for (String l : wrap(s, fw, 0.72f)) {
			c.text(l, fx, fy, 0.72f, col(0xFFFFFF, 0.55f));
			fy += 9;
		}
		fy += 3;
	}

	// ------------------------------------------------------------ moduly

	private static boolean on(int m, Config c) {
		return switch (m) {
			case 0 -> c.targetEnabled;
			case 1 -> c.xray != 0;
			case 2 -> c.glassSelf || c.glassPlayers || c.glassItems;
			case 3 -> c.chinaHat != 0;
			case 4 -> c.petEnabled;
			case 5 -> c.jumpCircle;
			case 6 -> c.trails;
			case 7 -> c.rain;
			case 8 -> c.blizzard;
			case 9 -> c.fireflies;
			case 10, 11, 12, 13, 14 -> c.skyStyle == m - 9;
			case 15 -> c.shockwave != 0;
			case 16 -> c.killClub;
			case 17 -> c.killLightning;
			case 18 -> c.musicPanel;
			case 19 -> c.musicSky;
			case 20 -> c.infoPill;
			case 22 -> c.hitSound != 0;
			case 23 -> c.killSound != 0;
			default -> c.targetPanel;
		};
	}

	private static void set(int m, Config c, boolean v) {
		switch (m) {
			case 0 -> c.targetEnabled = v;
			case 1 -> c.xray = v ? (c.xray == 0 ? 3 : c.xray) : 0;
			case 2 -> {
				c.glassSelf = v;
				c.glassPlayers = v;
				c.glassItems = v;
			}
			case 3 -> c.chinaHat = v ? (c.chinaHat == 0 ? 1 : c.chinaHat) : 0;
			case 4 -> c.petEnabled = v;
			case 5 -> c.jumpCircle = v;
			case 6 -> c.trails = v;
			case 7 -> c.rain = v;
			case 8 -> c.blizzard = v;
			case 9 -> c.fireflies = v;
			case 10, 11, 12, 13, 14 -> c.skyStyle = v ? m - 9 : 0;
			case 15 -> c.shockwave = v ? (c.shockwave == 0 ? 3 : c.shockwave) : 0;
			case 16 -> c.killClub = v;
			case 17 -> c.killLightning = v;
			case 18 -> {
				c.musicPanel = v;
				MediaControl.sync(v);
			}
			case 19 -> c.musicSky = v;
			case 20 -> c.infoPill = v;
			case 22 -> c.hitSound = v ? (c.hitSound == 0 ? 1 : c.hitSound) : 0;
			case 23 -> c.killSound = v ? (c.killSound == 0 ? 1 : c.killSound) : 0;
			default -> c.targetPanel = v;
		}
	}

	private static void detail(Canvas c, int m, Config cfg) {
		switch (m) {
			case 0 -> {
				cfg.playersOnly = tog(c, "Tylko gracze", cfg.playersOnly);
				cfg.range = sld(c, "Zasięg", cfg.range, 3, 64, "%.0f m", 1);
				cfg.speed = sld(c, "Prędkość", cfg.speed, 0.2f, 3, "%.1fx", 1);
				cfg.ring = tog(c, "Pierścień", cfg.ring);
				cfg.scanner = tog(c, "Skaner", cfg.scanner);
				cfg.orbit = tog(c, "Orbita", cfg.orbit);
				cfg.glow = tog(c, "Poświata", cfg.glow);
				cfg.box = tog(c, "Ramka", cfg.box);
			}
			case 1 -> {
				cfg.xray = cho(c, "Kogo", new String[]{"Tylko ja", "Inni gracze", "Wszyscy"}, Math.max(0, cfg.xray - 1)) + 1;
				cfg.xrayAlpha = sld(c, "Jasność", cfg.xrayAlpha, 0.2f, 1, "%.0f%%", 100);
				cfg.xrayRange = sld(c, "Zasięg", cfg.xrayRange, 8, 64, "%.0f m", 1);
				note(c, "Na sobie widać kości w widoku z trzeciej osoby.");
			}
			case 2 -> {
				cfg.glassSelf = tog(c, "Na mnie", cfg.glassSelf);
				cfg.glassPlayers = tog(c, "Na innych graczach", cfg.glassPlayers);
				cfg.glassItems = tog(c, "Na itemach na ziemi", cfg.glassItems);
				cfg.glassAlpha = sld(c, "Widoczność", cfg.glassAlpha, 0.05f, 0.9f, "%.0f%%", 100);
				note(c, "Pancerz i trzymane przedmioty zostają widoczne.");
			}
			case 3 -> {
				cfg.chinaHat = cho(c, "Kogo", new String[]{"Tylko ja", "Wszyscy"}, Math.max(0, cfg.chinaHat - 1)) + 1;
				cfg.chinaHatScale = sld(c, "Rozmiar", cfg.chinaHatScale, 0.5f, 2, "%.1fx", 1);
				note(c, "Na sobie widać kapelusz w widoku z trzeciej osoby.");
			}
			case 4 -> {
				cfg.petType = cho(c, "Rodzaj", PetManager.NAMES, cfg.petType);
				cfg.petScale = sld(c, "Rozmiar", cfg.petScale, 0.4f, 2, "%.1fx", 1);
			}
			case 6 -> cfg.trailSeconds = sld(c, "Czas śladu", cfg.trailSeconds, 0.5f, 6, "%.1f s", 1);
			case 7 -> cfg.rainDensity = sld(c, "Gęstość", cfg.rainDensity, 0, 1, "%.0f%%", 100);
			case 8 -> cfg.blizzardDensity = sld(c, "Gęstość", cfg.blizzardDensity, 0, 1, "%.0f%%", 100);
			case 9 -> cfg.fireflyCount = sld(c, "Ilość", cfg.fireflyCount, 0, 1, "%.0f%%", 100);
			case 15 -> {
				cfg.shockwave = cho(c, "Kiedy", new String[]{"Po trafieniu", "Po zabiciu", "Oba"}, Math.max(0, cfg.shockwave - 1)) + 1;
				cfg.killPlayersOnly = tog(c, "Zabicie tylko gracza", cfg.killPlayersOnly);
			}
			case 16, 17 -> {
				cfg.killPlayersOnly = tog(c, "Tylko gdy zabijesz gracza", cfg.killPlayersOnly);
				note(c, "Efekt widzisz tylko Ty. Nie wpływa na gracza ani serwer.");
			}
			case 22 -> {
				cfg.hitSound = cho(c, "Dźwięk", Sounds.HIT, cfg.hitSound);
				cfg.soundVolume = sld(c, "Głośność", cfg.soundVolume, 0.1f, 1, "%.0f%%", 100);
			}
			case 23 -> {
				cfg.killSound = cho(c, "Dźwięk", Sounds.KILL, cfg.killSound);
				cfg.soundVolume = sld(c, "Głośność", cfg.soundVolume, 0.1f, 1, "%.0f%%", 100);
				note(c, "Zagra się też po zabiciu, nawet bez efektów wizualnych.");
			}
			case 19 -> note(c, "Napis pojawia się na niebie po zmianie utworu. Działa tylko na Windows.");
			default -> {
			}
		}
	}

	// ------------------------------------------------------------ budowa interfejsu

	private static void build(Canvas c, MinecraftClient mc, Config cfg) {
		Theme th = cfg.theme();
		int ac = th.accent(cfg.animateColors);
		accent = ac;

		// poswiata i tlo
		for (int i = 3; i >= 1; i--) {
			c.rrect(-i * 3, -i * 3, W + i * 6, H + i * 6, 12 + i * 2, col(ac, 0.04f));
		}
		c.rrect(0, 0, W, H, 10, col(th.bg, cfg.panelAlpha));
		c.rrect(1, 1, W - 2, 40, 9, col(0xFFFFFF, 0.025f));
		c.rborder(0, 0, W, H, 10, col(ac, 0.55f));
		c.rect(10, 1, W - 20, 1, col(0xFFFFFF, 0.14f));

		// naglowek: logo, nazwa, zakladki
		c.rborder(13, 8, 28, 28, 3, col(ac, 0.9f));
		c.img(Gfx.LOGO, 14, 9, 26, 26);
		c.text("GhostVisual", 48, 10, 1.15f, 0xFFFFFFFF, true);
		c.text("v" + VERSION, 48, 24, 0.7f, col(0xFFFFFF, 0.5f));
		float tx = 190;
		for (int i = 0; i < TABS.length; i++) {
			float tw = c.width(TABS[i], 0.95f) + 22;
			boolean act = i == tab;
			if (clicked(tx, 8, tw, 28)) {
				tab = i;
				page = 0;
			}
			if (act) {
				c.rrect(tx, 10, tw, 24, 6, col(ac, 0.12f));
				c.rect(tx + 10, 33, tw - 20, 2, col(ac, 1f));
			} else if (hover(tx, 8, tw, 28)) {
				c.rrect(tx, 10, tw, 24, 6, col(0xFFFFFF, 0.05f));
			}
			c.text(TABS[i], tx + 11, 17, 0.95f, act ? 0xFFFFFFFF : col(0xFFFFFF, 0.6f));
			tx += tw + 4;
		}
		c.rect(0, 41, W, 1, col(0xFFFFFF, 0.08f));

		switch (tab) {
			case 0 -> buildVisuals(c, cfg, ac);
			case 1 -> buildStyle(c, cfg, th, ac);
			case 2 -> buildConfig(c, mc, cfg, ac);
			default -> buildProfile(c, mc, cfg, ac);
		}

		// stopka
		c.text("made by shvga", 16, H - 15, 0.8f, col(0xFFFFFF, 0.5f));
		String hint = interactive
			? "[" + keyName(GhostVisualClient.lockKey) + "] przypnij  [" + keyName(GhostVisualClient.openMenuKey) + "] zamknij"
			: "LPM na celowniku klika  [" + keyName(GhostVisualClient.lockKey) + "] odblokuj kursor";
		if (hasMouse) {
			c.rrect(mx - 2.5f, my - 2.5f, 5, 5, 2, 0xFFFFFFFF);
			c.rborder(mx - 3.5f, my - 3.5f, 7, 7, 3, col(ac, 1f));
		}
		c.textRight(hint, W - 16, H - 15, 0.75f, col(0xFFFFFF, 0.5f));
		if (interactive) {
			c.text(String.format("%s %.0f,%.0f", hasMouse ? "OK" : "--", mx, my), 130, H - 15, 0.7f, col(0xFFFFFF, 0.35f));
		}
	}

	private static String keyName(net.minecraft.client.option.KeyBinding k) {
		return k.getBoundKeyLocalizedText().getString();
	}

	private static void buildVisuals(Canvas c, Config cfg, int ac) {
		// kategorie
		float px = 16;
		for (int i = 0; i < CATS.length; i++) {
			float pw = c.width(CATS[i], 0.85f) + 16;
			boolean act = i == cat;
			if (clicked(px, 50, pw, 14)) {
				cat = i;
				page = 0;
			}
			c.rrect(px, 50, pw, 14, 7, act ? col(ac, 0.22f) : col(0xFFFFFF, hover(px, 50, pw, 14) ? 0.1f : 0.05f));
			if (act) {
				c.rborder(px, 50, pw, 14, 7, col(ac, 0.8f));
			}
			c.text(CATS[i], px + 8, 54, 0.85f, act ? 0xFFFFFFFF : col(0xFFFFFF, 0.65f));
			px += pw + 5;
		}

		// lista modulow
		List<Integer> list = new ArrayList<>();
		int enabled = 0;
		for (int m = 0; m < NAMES.length; m++) {
			if (on(m, cfg)) {
				enabled++;
			}
			if (cat == 0 || CAT[m] == cat) {
				list.add(m);
			}
		}
		int per = 14;
		int pages = Math.max(1, (list.size() + per - 1) / per);
		if (hasMouse && mx < 345 && my > 68 && my < 276 && scroll != 0) {
			page += scroll < 0 ? 1 : -1;
		}
		page = Math.max(0, Math.min(pages - 1, page));
		for (int i = 0; i < per; i++) {
			int idx = page * per + i;
			if (idx >= list.size()) {
				break;
			}
			int m = list.get(i + page * per);
			float x = 16 + (i % 2) * 164, y = 72 + (i / 2) * 28, w = 156, h = 24;
			boolean sel = m == selected;
			boolean hv = hover(x, y, w, h);
			boolean isOn = on(m, cfg);
			c.rrect(x, y, w, h, 6, sel ? col(ac, 0.14f) : col(0xFFFFFF, hv ? 0.09f : 0.05f));
			if (sel) {
				c.rborder(x, y, w, h, 6, col(ac, 0.85f));
			}
			c.rrect(x + 5, y + 4, 16, 16, 4, col(ac, isOn ? 0.35f : 0.16f));
			c.textCenter(NAMES[m].substring(0, 1), x + 13, y + 8, 0.85f, 0xFFFFFFFF, true);
			c.text(fit(NAMES[m], 96, 0.85f), x + 27, y + 8, 0.85f, isOn ? 0xFFFFFFFF : col(0xFFFFFF, 0.7f));
			float sx = x + w - 32, sy = y + 6;
			sw(c, sx, sy, isOn, ac);
			if (clicked(sx - 3, y, 34, h)) {
				set(m, cfg, !isOn);
			} else if (clicked(x, y, w, h)) {
				selected = m;
			}
		}

		// stopka listy: licznik i strony
		c.text(NAMES.length + " modułów · " + enabled + " włączonych", 16, H - 31, 0.8f, col(0xFFFFFF, 0.55f));
		float bx = 200;
		if (clicked(bx, H - 34, 14, 14)) {
			page = Math.max(0, page - 1);
		}
		if (clicked(bx + 56, H - 34, 14, 14)) {
			page = Math.min(pages - 1, page + 1);
		}
		c.rrect(bx, H - 34, 14, 14, 4, col(0xFFFFFF, 0.07f));
		c.rrect(bx + 56, H - 34, 14, 14, 4, col(0xFFFFFF, 0.07f));
		c.textCenter("<", bx + 7, H - 31, 0.85f, 0xFFFFFFFF, false);
		c.textCenter(">", bx + 63, H - 31, 0.85f, 0xFFFFFFFF, false);
		c.textCenter((page + 1) + "/" + pages, bx + 35, H - 31, 0.85f, col(0xFFFFFF, 0.8f), false);

		// panel szczegolow
		float dx = 352, dy = 50, dw = 192, dh = H - 50 - 40;
		c.rrect(dx, dy, dw, dh, 8, col(0x000000, 0.28f));
		c.rborder(dx, dy, dw, dh, 8, col(0xFFFFFF, 0.08f));
		c.rrect(dx + 8, dy + 8, 16, 16, 4, col(ac, 0.3f));
		c.textCenter(NAMES[selected].substring(0, 1), dx + 16, dy + 12, 0.85f, 0xFFFFFFFF, true);
		c.text(fit(NAMES[selected], dw - 60, 0.95f), dx + 30, dy + 12, 0.95f, 0xFFFFFFFF, true);
		fx = dx + 10;
		fy = dy + 30;
		fw = dw - 20;
		for (String l : wrap(DESC[selected], fw, 0.72f)) {
			c.text(l, fx, fy, 0.72f, col(0xFFFFFF, 0.55f));
			fy += 9;
		}
		fy += 5;
		c.rect(fx, fy, fw, 1, col(0xFFFFFF, 0.08f));
		fy += 7;
		detail(c, selected, cfg);
	}

	private static void buildStyle(Canvas c, Config cfg, Theme cur, int ac) {
		Theme[] all = Theme.values();
		float cw = (W - 32 - 3 * 8) / 4f;
		for (int i = 0; i < all.length; i++) {
			Theme t = all[i];
			float x = 16 + (i % 4) * (cw + 8), y = 54 + (i / 4) * 62, h = 54;
			boolean sel = cfg.theme == i;
			boolean hv = hover(x, y, cw, h);
			if (clicked(x, y, cw, h)) {
				cfg.theme = i;
			}
			c.rrect(x, y, cw, h, 6, col(t.bg, 1f));
			c.rborder(x, y, cw, h, 6, col(t.accent, sel ? 1f : (hv ? 0.7f : 0.3f)));
			c.rrect(x + 10, y + 10, 18, 18, 4, col(t.accent, 1f));
			c.rrect(x + 32, y + 10, 18, 18, 4, col(t.accent2, 1f));
			c.text(t.label, x + 10, y + 36, 0.9f, 0xFFFFFFFF);
			if (sel) {
				c.rrect(x + cw - 18, y + 10, 8, 8, 4, col(t.accent, 1f));
			}
		}
		fx = 16;
		fy = 190;
		fw = W - 32;
		cfg.panelAlpha = sld(c, "Przezroczystość panelu", cfg.panelAlpha, 0.3f, 1, "%.0f%%", 100);
		cfg.animateColors = tog(c, "Animowane kolory", cfg.animateColors);
	}

	private static void buildConfig(Canvas c, MinecraftClient mc, Config cfg, int ac) {
		fx = 16;
		fy = 56;
		fw = W - 32;
		c.text("Sterowanie", fx, fy, 1.0f, 0xFFFFFFFF, true);
		fy += 16;
		note(c, "[" + keyName(GhostVisualClient.openMenuKey) + "] otwiera i zamyka panel. Panel pojawia się przed Tobą i zostaje w tym miejscu w świecie.");
		note(c, "[" + keyName(GhostVisualClient.lockKey) + "] przypina panel: kursor wraca do gry, możesz chodzić, a panel zostaje w miejscu. Wciśnij ponownie, żeby znów go używać.");
		note(c, "Klawisze muzyki (poprzedni, pauza, dalej) ustawisz w Sterowaniu Minecrafta, kategoria GhostVisual.");
		fy += 6;
		float bx = fx, by = fy, bw = 190, bh = 22;
		boolean hv = hover(bx, by, bw, bh);
		if (clicked(bx, by, bw, bh)) {
			anchor(mc);
		}
		c.rrect(bx, by, bw, bh, 6, col(ac, hv ? 0.3f : 0.18f));
		c.rborder(bx, by, bw, bh, 6, col(ac, 0.8f));
		c.textCenter("Ustaw panel przed sobą", bx + bw / 2, by + 7, 0.9f, 0xFFFFFFFF, false);
	}

	private static void buildProfile(Canvas c, MinecraftClient mc, Config cfg, int ac) {
		float x = 16, y = 54, w = W - 32;
		c.rrect(x, y, w, 92, 8, col(ac, 0.07f));
		c.rborder(x, y, w, 92, 8, col(ac, 0.8f));
		c.rborder(x + 12, y + 12, 68, 68, 3, col(ac, 1f));
		c.img(Gfx.LOGO, x + 13, y + 13, 66, 66);
		float tx = x + 94;
		c.text("shvga", tx, y + 10, 2.0f, 0xFFFFFFFF, true);
		c.text("Twórca GhostVisual", tx, y + 32, 0.95f, col(0xFFFFFF, 0.88f));
		float bx = tx;
		bx += pill(c, "OWNER", bx, y + 48, ac, 0.95f, 0xFF04110E) + 5;
		bx += pill(c, "AUTOR", bx, y + 48, ac, 0.22f, col(ac, 1f)) + 5;
		pill(c, "v" + VERSION, bx, y + 48, 0xFFFFFF, 0.12f, col(0xFFFFFF, 0.75f));
		String user = mc.getSession() != null ? mc.getSession().getUsername() : "?";
		c.text("Zalogowano jako " + user + "  •  Minecraft 1.21.4  •  Fabric", tx, y + 68, 0.75f, col(0xFFFFFF, 0.5f));

		float gap = 8, tw = (w - 2 * gap) / 3f;
		long secs = (System.currentTimeMillis() - Targets.startMillis) / 1000;
		stat(c, x, y + 102, tw, String.valueOf(Targets.targetsSeen), "Cele w sesji", ac);
		stat(c, x + tw + gap, y + 102, tw, String.valueOf(cfg.activeEffects()), "Aktywne efekty", ac);
		stat(c, x + 2 * (tw + gap), y + 102, tw, (secs / 60) + ":" + String.format("%02d", secs % 60), "Czas sesji", ac);

		float dy = y + 102 + 50;
		float dh = H - 40 - dy;
		c.rrect(x, dy, w, dh, 8, col(0xFFFFFF, 0.05f));
		c.rborder(x, dy, w, dh, 8, col(0xFFFFFF, 0.08f));
		c.text("GhostVisual", x + 10, dy + 8, 0.95f, 0xFFFFFFFF, true);
		float ly = dy + 22;
		for (String l : wrap("Efekty wizualne celu, xray, nieba, pety z Minecrafta, China Hat, glass, efekty po zabiciu "
			+ "oraz panel muzyki z okładką. Made by shvga.", w - 20, 0.8f)) {
			c.text(l, x + 10, ly, 0.8f, col(0xFFFFFF, 0.6f));
			ly += 10;
		}
	}

	private static int pill(Canvas c, String text, float x, float y, int fill, float fillA, int textColor) {
		int w = (int) (c.width(text, 0.8f) + 12);
		c.rrect(x, y, w, 12, 6, col(fill, fillA));
		c.text(text, x + 6, y + 3, 0.8f, textColor);
		return w;
	}

	private static void stat(Canvas c, float x, float y, float w, String value, String label, int ac) {
		c.rrect(x, y, w, 42, 6, col(0xFFFFFF, 0.06f));
		c.rect(x + 1, y + 7, 2, 28, col(ac, 1f));
		c.text(value, x + 12, y + 6, 1.6f, col(ac, 1f));
		c.text(label, x + 12, y + 29, 0.8f, col(0xFFFFFF, 0.55f));
	}
}
