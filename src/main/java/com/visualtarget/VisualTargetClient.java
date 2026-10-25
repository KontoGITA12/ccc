package com.visualtarget;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Visual Target - caly mod w jednym pliku.
 * Punkt startowy (tylko klient): rejestruje klawisze i wszystkie renderery.
 */
public class VisualTargetClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("visualtarget");

	/** Klawisz menu. Domyslnie Prawy Shift - da sie zmienic w Opcje > Sterowanie. */
	public static KeyBinding openMenuKey;
	/** Klawisze muzyki (domyslnie nieprzypisane - ustaw w Opcje > Sterowanie). */
	public static KeyBinding musicPrevKey;
	public static KeyBinding musicToggleKey;
	public static KeyBinding musicNextKey;

	@Override
	public void onInitializeClient() {
		VisualConfig.load();

		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.visualtarget.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.visualtarget"));
		musicPrevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.visualtarget.music_prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.visualtarget"));
		musicToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.visualtarget.music_toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.visualtarget"));
		musicNextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.visualtarget.music_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.visualtarget"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new VisualsScreen());
				}
			}

			boolean media = VisualConfig.get().music;
			MediaControl.sync(media);
			while (musicPrevKey.wasPressed()) {
				if (media) MediaControl.send("prev");
			}
			while (musicToggleKey.wasPressed()) {
				if (media) MediaControl.send("toggle");
			}
			while (musicNextKey.wasPressed()) {
				if (media) MediaControl.send("next");
			}
		});

		SkyRenderer.register();
		TargetRenderer.register();
		ExtrasRenderer.register();
		RainRenderer.register();
		PetRenderer.register();
		Hud.register();
		LOGGER.info("Visual Target zaladowany");
	}

	/**
	 * Motywy kolorystyczne: kolor menu (ui) i kolory efektow w swiecie (fx).
	 * Kazdy motyw to gradient z dwoch kolorow.
	 */
	enum Theme {
		//     nazwa          panel     uiA       uiB       fxA       fxB       glow(-1 = jak fx)
		BLACK("Black",        0x09090B, 0xF0F0F6, 0x8E8E9C, 0x0B0B0E, 0x34343E, 0xEDEDF7),
		PURPLE_PINK("Purple-Pink", 0x140A1F, 0xA855F7, 0xF472B6, 0x9D4EFF, 0xFF4FB5, -1),
		DARK_PURPLE("Dark Purple", 0x0C0716, 0x7C3AED, 0x5B21B6, 0x5B21B6, 0x7E3FE8, 0x8B5CF6),
		BLUE("Blue",          0x08111F, 0x60A5FA, 0x2563EB, 0x2563EB, 0x38BDF8, 0x7CC4FF),
		RED("Red",            0x1A0A0C, 0xF87171, 0xDC2626, 0xDC2626, 0xFF5A5A, 0xFF8A8A),
		GREEN("Green",        0x08150E, 0x4ADE80, 0x16A34A, 0x16A34A, 0x4ADE80, 0x86EFAC),
		ICE("Ice",            0x08141A, 0x67E8F9, 0xA5F3FC, 0x22D3EE, 0xBAE6FD, 0xCFFAFE),
		ORANGE("Orange",      0x1A0F06, 0xFB923C, 0xF97316, 0xF97316, 0xFBBF24, 0xFFB366),
		GOLD("Gold",          0x16120A, 0xFACC15, 0xEAB308, 0xCA8A04, 0xFDE047, 0xFEF08A);

		public final String label;
		public final int panel;
		public final int uiA, uiB;
		public final int fxA, fxB;
		public final int glowRgb;

		Theme(String label, int panel, int uiA, int uiB, int fxA, int fxB, int glowRgb) {
			this.label = label;
			this.panel = panel;
			this.uiA = uiA;
			this.uiB = uiB;
			this.fxA = fxA;
			this.fxB = fxB;
			this.glowRgb = glowRgb;
		}

		public static Theme byIndex(int i) {
			Theme[] all = values();
			if (i < 0 || i >= all.length) return PURPLE_PINK;
			return all[i];
		}

		/** Kolor menu dla fazy (gradient tam i z powrotem). */
		public int ui(double phase) {
			return mix(uiA, uiB, pingPong(phase));
		}

		/** Kolor menu liniowo od uiA (0) do uiB (1) - do podgladu na kartach. */
		public int uiLinear(double f) {
			return mix(uiA, uiB, f);
		}

		/** Kolor efektu w swiecie dla fazy. */
		public int fx(double phase) {
			return mix(fxA, fxB, pingPong(phase));
		}

		/** Kolor poswiaty wokol linii. */
		public int glow(double phase) {
			return glowRgb >= 0 ? glowRgb : fx(phase);
		}

		private static double pingPong(double p) {
			p = p - Math.floor(p);
			return p < 0.5 ? p * 2.0 : (1.0 - p) * 2.0;
		}

		/** Mieszanie dwoch kolorow RGB (t = 0..1). */
		public static int mix(int a, int b, double t) {
			t = Math.max(0.0, Math.min(1.0, t));
			int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
			int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
			int r = (int) Math.round(ar + (br - ar) * t);
			int g = (int) Math.round(ag + (bg - ag) * t);
			int bl = (int) Math.round(ab + (bb - ab) * t);
			return (r << 16) | (g << 8) | bl;
		}
	}

	/**
	 * Ustawienia moda. Zapisywane w config/visualtarget.json.
	 */
	static class VisualConfig {
		// --- Motyw i wyglad menu ---
		public int theme = 1;                // 0 = Black, 1 = Purple-Pink, 2 = Dark Purple
		public float panelAlpha = 0.9f;      // przezroczystosc panelu menu
		public boolean rounded = true;       // zaokraglone rogi menu
		public boolean animateColors = true; // gradient kolorow plynie w czasie

		// --- Efekty wokol celu ---
		public boolean targetEnabled = true;
		public boolean box = true;
		public boolean ring = true;
		public boolean scanner = false;
		public boolean orbit = false;
		public boolean ghost = true;         // wiatr krazacy wokol celu
		public boolean glow = true;          // miekka poswiata na liniach
		public boolean spinCircle = true;    // obracajace sie luki zwrocone do kamery
		public boolean chinaHat = false;     // stozek nad glowa celu

		// --- HUD ---
		public boolean targetHud = true;     // mini panel celu (glowa, nick, zdrowie)
		public boolean topBar = true;        // pasek na gorze: zegar, muzyka, FPS i ping
		public boolean music = false;        // integracja z odtwarzaczem (Spotify) - Windows
		public boolean musicHud = false;     // mini odtwarzacz na ekranie
		public boolean playersOnly = false;
		public float range = 24f;
		public float speed = 1f;

		// --- Dodatki ---
		public boolean jumpCircle = true;
		public boolean trails = false;
		public boolean blizzard = false;
		public boolean fireflies = false;
		public float trailSeconds = 1.5f;
		public float blizzardDensity = 1f;
		public float fireflyCount = 24f;
		public boolean rain = false;
		public float rainDensity = 1f;

		// --- Niebo: 0 = wylaczone, 1 = Aurora, 2 = Nebula, 3 = Galaxy, 4 = Caustics, 5 = Black hole ---
		public int sky = 0;
		public float skyIntensity = 0.85f;
		public float skySpeed = 1f;

		// --- Pety: 0 = Bat, 1 = Cat, 2 = Chicken, 3 = Dog ---
		public boolean petEnabled = false;
		public int petType = 1;
		public float petScale = 1f;

		public Theme theme() {
			return Theme.byIndex(theme);
		}

		// -------------------------------------------------------------

		private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
		private static VisualConfig instance = new VisualConfig();

		public static VisualConfig get() {
			return instance;
		}

		private static Path path() {
			return FabricLoader.getInstance().getConfigDir().resolve("visualtarget.json");
		}

		public static void load() {
			Path p = path();
			if (!Files.exists(p)) {
				instance.save();
				return;
			}
			try (Reader reader = Files.newBufferedReader(p)) {
				VisualConfig loaded = GSON.fromJson(reader, VisualConfig.class);
				if (loaded != null) {
					loaded.clamp();
					instance = loaded;
				}
			} catch (Exception e) {
				VisualTargetClient.LOGGER.warn("Nie udalo sie wczytac configu, uzywam domyslnego", e);
			}
		}

		public void save() {
			clamp();
			try (Writer writer = Files.newBufferedWriter(path())) {
				GSON.toJson(this, writer);
			} catch (IOException e) {
				VisualTargetClient.LOGGER.warn("Nie udalo sie zapisac configu", e);
			}
		}

		private void clamp() {
			theme = MathHelper.clamp(theme, 0, Theme.values().length - 1);
			panelAlpha = MathHelper.clamp(panelAlpha, 0.35f, 1f);
			range = MathHelper.clamp(range, 4f, 64f);
			speed = MathHelper.clamp(speed, 0.2f, 3f);
			trailSeconds = MathHelper.clamp(trailSeconds, 0.5f, 3f);
			blizzardDensity = MathHelper.clamp(blizzardDensity, 0.3f, 2f);
			fireflyCount = MathHelper.clamp(fireflyCount, 4f, 80f);
			rainDensity = MathHelper.clamp(rainDensity, 0.3f, 2f);
			sky = MathHelper.clamp(sky, 0, 5);
			skyIntensity = MathHelper.clamp(skyIntensity, 0.2f, 1f);
			skySpeed = MathHelper.clamp(skySpeed, 0.2f, 3f);
			petType = MathHelper.clamp(petType, 0, 3);
			petScale = MathHelper.clamp(petScale, 0.6f, 2f);
		}
	}

	/**
	 * Miekkie, polprzezroczyste ksztalty: swiecace kule, pierscienie na ziemi, ksztalty z gradientem.
	 * Wspolrzedne podajemy WZGLEDEM KAMERY. Kolory w formacie ARGB.
	 */
	static final class Fx {
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

		/** Fragment pierscienia na ziemi (kat a0..a1, w radianach). */
		void groundArc(double cx, double cy, double cz, double rIn, double rOut, double a0, double a1,
					   int segs, int cIn, int cOut) {
			if (rOut <= rIn) return;
			for (int j = 0; j < segs; j++) {
				double aa = a0 + (a1 - a0) * j / segs;
				double bb = a0 + (a1 - a0) * (j + 1) / segs;
				double c0 = Math.cos(aa), s0 = Math.sin(aa);
				double c1 = Math.cos(bb), s1 = Math.sin(bb);
				quad2(cx + c0 * rIn, cy, cz + s0 * rIn, cIn,
						cx + c1 * rIn, cy, cz + s1 * rIn, cIn,
						cx + c1 * rOut, cy, cz + s1 * rOut, cOut,
						cx + c0 * rOut, cy, cz + s0 * rOut, cOut);
			}
		}

		/**
		 * Luk zwrocony do kamery (kolko "celownika"): miekki na brzegach, konce lagodnie znikaja.
		 * rMid = promien srodka, halfW = polowa szerokosci, alpha 0..255.
		 */
		void billboardArc(double x, double y, double z, double rMid, double halfW, double a0, double a1,
						  int segs, int rgb, double alpha) {
			for (int j = 0; j < segs; j++) {
				double u0 = j / (double) segs;
				double u1 = (j + 1) / (double) segs;
				double e0 = Math.sin(Math.PI * u0);
				double e1 = Math.sin(Math.PI * u1);
				double aa = a0 + (a1 - a0) * u0;
				double bb = a0 + (a1 - a0) * u1;
				double ca = Math.cos(aa), sa = Math.sin(aa), cb = Math.cos(bb), sb = Math.sin(bb);

				double rIn = rMid - halfW, rOut = rMid + halfW;
				int edge = argb(0, rgb);
				int m0 = argb(alpha * e0, rgb);
				int m1 = argb(alpha * e1, rgb);

				double ix0 = x + (rx * ca + ux * sa) * rIn, iy0 = y + (ry * ca + uy * sa) * rIn, iz0 = z + (rz * ca + uz * sa) * rIn;
				double ix1 = x + (rx * cb + ux * sb) * rIn, iy1 = y + (ry * cb + uy * sb) * rIn, iz1 = z + (rz * cb + uz * sb) * rIn;
				double mx0 = x + (rx * ca + ux * sa) * rMid, my0 = y + (ry * ca + uy * sa) * rMid, mz0 = z + (rz * ca + uz * sa) * rMid;
				double mx1 = x + (rx * cb + ux * sb) * rMid, my1 = y + (ry * cb + uy * sb) * rMid, mz1 = z + (rz * cb + uz * sb) * rMid;
				double ox0 = x + (rx * ca + ux * sa) * rOut, oy0 = y + (ry * ca + uy * sa) * rOut, oz0 = z + (rz * ca + uz * sa) * rOut;
				double ox1 = x + (rx * cb + ux * sb) * rOut, oy1 = y + (ry * cb + uy * sb) * rOut, oz1 = z + (rz * cb + uz * sb) * rOut;

				quad2(ix0, iy0, iz0, edge, ix1, iy1, iz1, edge, mx1, my1, mz1, m1, mx0, my0, mz0, m0);
				quad2(mx0, my0, mz0, m0, mx1, my1, mz1, m1, ox1, oy1, oz1, edge, ox0, oy0, oz0, edge);
			}
		}

		/** Pionowa "sciana" w ksztalcie pierscienia (cylinder) z trzema wysokosciami i kolorami. */
		void wall(double cx, double cy, double cz, double r, double y0, double y1, double y2,
				  int segs, int c0, int c1, int c2) {
			for (int j = 0; j < segs; j++) {
				double a = 2.0 * Math.PI * j / segs;
				double b = 2.0 * Math.PI * (j + 1) / segs;
				double xa = cx + Math.cos(a) * r, za = cz + Math.sin(a) * r;
				double xb = cx + Math.cos(b) * r, zb = cz + Math.sin(b) * r;
				quad2(xa, y0, za, c0, xb, y0, zb, c0, xb, y1, zb, c1, xa, y1, za, c1);
				quad2(xa, y1, za, c1, xb, y1, zb, c1, xb, y2, zb, c2, xa, y2, za, c2);
			}
		}

		/** Stozek (np. chinski kapelusz): wierzcholek nad podstawa. */
		void cone(double cx, double yApex, double cz, double yBase, double rBase, int segs, int cApex, int cBase) {
			for (int j = 0; j < segs; j++) {
				double a = 2.0 * Math.PI * j / segs;
				double b = 2.0 * Math.PI * (j + 1) / segs;
				quad2(cx, yApex, cz, cApex, cx, yApex, cz, cApex,
						cx + Math.cos(b) * rBase, yBase, cz + Math.sin(b) * rBase, cBase,
						cx + Math.cos(a) * rBase, yBase, cz + Math.sin(a) * rBase, cBase);
			}
		}

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

	/**
	 * Wspolny pomocnik do rysowania linii, pierscieni i boxow w kolorach aktualnego motywu.
	 * Wspolrzedne podajemy WZGLEDEM KAMERY (pozycja w swiecie minus pozycja kamery).
	 */
	static final class Painter {
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

	/** Male pomocnicze funkcje rysowania interfejsu (zaokraglone prostokaty, ikony, mini odtwarzacz). */
	static final class Ui {
		private Ui() {}

		public static final int COL_TEXT = 0xFFEDEDF5;
		public static final int COL_OFF = 0xFFA6A6B8;
		public static final int COL_DIM = 0xFF6F6F86;

		/** Kolor ARGB z RGB i alfa 0..1. */
		public static int a(int rgb, double alpha) {
			int al = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, alpha)));
			return (al << 24) | (rgb & 0xFFFFFF);
		}

		/** Mnozy kanal alfa koloru ARGB. */
		public static int fadeArgb(int argb, double mul) {
			int al = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0, Math.min(1.0, mul)));
			return (al << 24) | (argb & 0xFFFFFF);
		}

		/** Prostokat z zaokraglonymi rogami (promien r). */
		public static void rrect(DrawContext ctx, int x, int y, int w, int h, int color, int r) {
			if (r <= 0 || w < 2 * r + 1 || h < 2 * r + 1) {
				ctx.fill(x, y, x + w, y + h, color);
				return;
			}
			ctx.fill(x, y + r, x + w, y + h - r, color);
			for (int i = 0; i < r; i++) {
				double dy = r - i - 0.5;
				int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
				ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
				ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
			}
		}

		/** Wypelnione kolo. */
		public static void circle(DrawContext ctx, int cx, int cy, int radius, int color) {
			for (int dy = -radius; dy <= radius; dy++) {
				int dx = (int) Math.round(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
				ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
			}
		}

		/** Trojkat skierowany w prawo (right = true) lub w lewo. */
		public static void tri(DrawContext ctx, int x, int cy, int w, int halfH, boolean right, int color) {
			for (int dx = 0; dx < w; dx++) {
				int half = Math.max(0, (int) Math.round(halfH * (1.0 - dx / (double) w)));
				int px = right ? x + dx : x + (w - 1 - dx);
				ctx.fill(px, cy - half, px + 1, cy + half + 1, color);
			}
		}

		public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
			ctx.drawTextWithShadow(tr, Text.literal(s), x, y, color);
		}

		/** Animowany korektor (rownowaznik "slupkow" muzyki). */
		public static void equalizer(DrawContext ctx, int x, int bottom, int bars, int barW, int maxH,
									 int color, boolean animate, double time) {
			for (int i = 0; i < bars; i++) {
				double f = animate ? 0.35 + 0.65 * Math.abs(Math.sin(time * (2.2 + i * 0.7) + i * 1.9)) : 0.25;
				int h = Math.max(2, (int) Math.round(maxH * f));
				ctx.fill(x + i * (barW + 1), bottom - h, x + i * (barW + 1) + barW, bottom, color);
			}
		}

		/** Ikona sygnalu (4 slupki) z liczba "zapelnionych" 1..4. */
		public static void signal(DrawContext ctx, int x, int bottom, int filled, int on, int off) {
			for (int i = 0; i < 4; i++) {
				int h = 3 + i * 2;
				ctx.fill(x + i * 3, bottom - h, x + i * 3 + 2, bottom, i < filled ? on : off);
			}
		}

		// ------------------------------------------------------------------
		// Mini odtwarzacz (karta)
		// ------------------------------------------------------------------

		/** Czesci karty do klikania: 1 = poprzedni, 2 = graj/pauza, 3 = nastepny, 0 = nic. */
		public static int musicHit(int x, int y, int w, int h, double mx, double my) {
			int cy = y + h / 2 - 2;
			int nextX = x + w - 14;
			int toggleX = nextX - 26;
			int prevX = toggleX - 26;
			if (Math.abs(my - cy) > 11) return 0;
			if (Math.abs(mx - toggleX) <= 11) return 2;
			if (Math.abs(mx - nextX) <= 10) return 3;
			if (Math.abs(mx - prevX) <= 10) return 1;
			return 0;
		}

		public static void musicCard(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h,
									 Theme theme, double alpha, double time, boolean radius, boolean enabled) {
			int r = radius ? 5 : 0;
			int accent = 0xFF000000 | theme.ui((time * 0.1) % 1.0);
			rrect(ctx, x, y, w, h, a(theme.panel, 0.88 * alpha), r);
			rrect(ctx, x, y, w, 1, fadeArgb(accent, 0.55 * alpha), 0);

			boolean has = enabled && MediaControl.available && !MediaControl.title.isEmpty();
			boolean playing = has && MediaControl.playing;

			// okladka (placeholder z gradientem motywu i korektorem)
			int cs = h - 12;
			int cx0 = x + 6, cy0 = y + 6;
			for (int i = 0; i < cs; i++) {
				ctx.fill(cx0, cy0 + i, cx0 + cs, cy0 + i + 1,
						fadeArgb(0xFF000000 | theme.uiLinear(i / (double) Math.max(1, cs - 1)), 0.55 * alpha));
			}
			equalizer(ctx, cx0 + cs / 2 - 8, cy0 + cs - 4, 4, 3, cs - 10, a(0xFFFFFF, 0.9 * alpha), playing, time);

			int tx = cx0 + cs + 7;
			int maxW = (x + w - 82) - tx;
			String title = has ? MediaControl.title : (enabled ? "Nothing playing" : "Media is off");
			String artist = has ? MediaControl.artist : (enabled ? "Open Spotify and press play" : "Enable it in the HUD tab");
			text(ctx, tr, tr.trimToWidth(title, Math.max(20, maxW)), tx, y + 7, fadeArgb(COL_TEXT, alpha));
			text(ctx, tr, tr.trimToWidth(artist, Math.max(20, maxW)), tx, y + 19, fadeArgb(COL_DIM, alpha));

			// przyciski
			int cy = y + h / 2 - 2;
			int nextX = x + w - 14;
			int toggleX = nextX - 26;
			int prevX = toggleX - 26;
			int icon = fadeArgb(has ? COL_TEXT : COL_DIM, alpha);

			// poprzedni: pasek + trojkat w lewo
			ctx.fill(prevX - 5, cy - 4, prevX - 4, cy + 5, icon);
			tri(ctx, prevX - 3, cy, 7, 4, false, icon);
			// graj / pauza w bialym kole
			circle(ctx, toggleX, cy, 9, fadeArgb(has ? 0xFFFFFFFF : 0xFF55556A, alpha));
			int dark = fadeArgb(0xFF101018, alpha);
			if (playing) {
				ctx.fill(toggleX - 3, cy - 4, toggleX - 1, cy + 5, dark);
				ctx.fill(toggleX + 1, cy - 4, toggleX + 3, cy + 5, dark);
			} else {
				tri(ctx, toggleX - 2, cy, 7, 4, true, dark);
			}
			// nastepny: trojkat w prawo + pasek
			tri(ctx, nextX - 4, cy, 7, 4, true, icon);
			ctx.fill(nextX + 4, cy - 4, nextX + 5, cy + 5, icon);

			// postep utworu
			if (has && MediaControl.duration > 0) {
				double f = Math.max(0.0, Math.min(1.0, MediaControl.livePosition() / MediaControl.duration));
				int bx = x + 6, bw = w - 12, by = y + h - 4;
				ctx.fill(bx, by, bx + bw, by + 2, a(0xFFFFFF, 0.12 * alpha));
				ctx.fill(bx, by, bx + (int) Math.round(bw * f), by + 2, fadeArgb(accent, alpha));
			}
		}
	}

	/**
	 * Odczyt i sterowanie aktualnie grajaca muzyka (Spotify) przez systemowe sterowanie multimediami Windows
	 * (to samo, z czego korzystaja klawisze multimedialne i panel glosnosci).
	 * Dziala tylko na Windows i tylko gdy opcja "Media" jest wlaczona. Uruchamia PowerShell z wbudowanym skryptem.
	 */
	static final class MediaControl {
		private MediaControl() {}

		public static volatile boolean available = false;
		public static volatile boolean playing = false;
		public static volatile String title = "";
		public static volatile String artist = "";
		public static volatile double position = 0.0;   // sekundy (w chwili ostatniego odczytu)
		public static volatile double duration = 0.0;
		public static volatile long updatedAt = 0L;     // System.currentTimeMillis()

		private static Process poller;
		private static Thread reader;

		public static boolean supported() {
			return System.getProperty("os.name", "").toLowerCase().contains("win");
		}

		/** Wywolywane co tick: uruchamia lub zatrzymuje odczyt zaleznie od ustawien. */
		public static void sync(boolean wanted) {
			if (wanted && supported()) {
				if (poller == null || !poller.isAlive()) start();
			} else {
				stop();
			}
		}

		public static synchronized void start() {
			if (!supported()) return;
			if (poller != null && poller.isAlive()) return;
			try {
				ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
						"-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encode(pollScript()));
				pb.redirectErrorStream(true);
				final Process proc = pb.start();
				poller = proc;
				reader = new Thread(() -> readLoop(proc), "visualtarget-media");
				reader.setDaemon(true);
				reader.start();
				Runtime.getRuntime().addShutdownHook(new Thread(MediaControl::stop));
			} catch (Exception e) {
				VisualTargetClient.LOGGER.warn("Nie udalo sie uruchomic odczytu muzyki", e);
			}
		}

		public static synchronized void stop() {
			if (poller != null) {
				poller.destroy();
				poller = null;
			}
			available = false;
			playing = false;
			title = "";
			artist = "";
		}

		/** Polecenia: "toggle", "next", "prev". */
		public static void send(String cmd) {
			if (!supported()) return;
			Thread th = new Thread(() -> {
				try {
					String method = switch (cmd) {
						case "next" -> "TrySkipNextAsync";
						case "prev" -> "TrySkipPreviousAsync";
						default -> "TryTogglePlayPauseAsync";
					};
					ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
							"-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encode(cmdScript(method)));
					pb.redirectErrorStream(true);
					Process p = pb.start();
					p.getInputStream().readAllBytes();
					p.waitFor();
				} catch (Exception e) {
					VisualTargetClient.LOGGER.warn("Nie udalo sie wyslac polecenia do odtwarzacza", e);
				}
			}, "visualtarget-media-cmd");
			th.setDaemon(true);
			th.start();
			// natychmiastowa, optymistyczna zmiana stanu play/pause w menu
			if ("toggle".equals(cmd)) playing = !playing;
		}

		/** Pozycja utworu z ekstrapolacja miedzy odczytami. */
		public static double livePosition() {
			if (!playing) return position;
			double p = position + (System.currentTimeMillis() - updatedAt) / 1000.0;
			return duration > 0 ? Math.min(p, duration) : p;
		}

		private static void readLoop(Process proc) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.startsWith("S|")) {
						String[] p = line.split("\\|", -1);
						if (p.length >= 6) {
							title = decode(p[2]);
							artist = decode(p[3]);
							playing = "4".equals(p[1]);
							position = parse(p[4]);
							duration = parse(p[5]);
							updatedAt = System.currentTimeMillis();
							available = true;
						}
					} else if (line.startsWith("N")) {
						available = false;
						playing = false;
						title = "";
						artist = "";
					}
				}
			} catch (Exception ignored) {
				// proces zakonczony
			}
			available = false;
		}

		private static double parse(String s) {
			try {
				return Double.parseDouble(s);
			} catch (NumberFormatException e) {
				return 0.0;
			}
		}

		private static String decode(String b64) {
			try {
				return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
			} catch (IllegalArgumentException e) {
				return "";
			}
		}

		private static String encode(String script) {
			return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		}

		// ------------------------------------------------------------------
		// Skrypty PowerShell
		// ------------------------------------------------------------------

		private static final String PREAMBLE = String.join("\n",
				"Add-Type -AssemblyName System.Runtime.WindowsRuntime",
				"$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]",
				"function Await($op, $type) { $t = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }",
				"[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
				"$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
				"function Pick { $s = $null; foreach ($x in $mgr.GetSessions()) { if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x; break } }; if ($s -eq $null) { $s = $mgr.GetCurrentSession() }; return $s }"
		);

		private static String pollScript() {
			return PREAMBLE + "\n" + String.join("\n",
					"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
					"function B($v) { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$v)) }",
					"while ($true) {",
					"  try {",
					"    $s = Pick",
					"    if ($s -ne $null) {",
					"      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
					"      $i = $s.GetPlaybackInfo(); $tl = $s.GetTimelineProperties()",
					"      [Console]::Out.WriteLine('S|' + [int]$i.PlaybackStatus + '|' + (B $p.Title) + '|' + (B $p.Artist) + '|' + [int]$tl.Position.TotalSeconds + '|' + [int]$tl.EndTime.TotalSeconds)",
					"    } else { [Console]::Out.WriteLine('N') }",
					"  } catch { [Console]::Out.WriteLine('N') }",
					"  [Console]::Out.Flush()",
					"  Start-Sleep -Milliseconds 1000",
					"}");
		}

		private static String cmdScript(String method) {
			return PREAMBLE + "\n" + String.join("\n",
					"$s = Pick",
					"if ($s -ne $null) { [void](Await ($s." + method + "()) ([bool])) }");
		}
	}

	/**
	 * Znajduje moba/gracza, w ktorego celujesz, i rysuje wokol niego efekty:
	 * Box (linie), Ring, Scanner, Orbit, Ghost, China hat, Spin circle (miekkie ksztalty).
	 * Efekty pojawiaja sie i znikaja plynnie (fade). Udostepnia tez cel dla panelu HUD.
	 */
	static final class TargetRenderer {
		private TargetRenderer() {}

		private static LivingEntity lastTarget;
		private static float fade = 0f;
		private static long lastNanos = System.nanoTime();

		public static void register() {
			WorldRenderEvents.AFTER_ENTITIES.register(TargetRenderer::onRender);
		}

		/** Aktualny cel (do panelu HUD) albo null. */
		public static LivingEntity hudTarget() {
			return (lastTarget != null && fade > 0.05f && lastTarget.isAlive()) ? lastTarget : null;
		}

		public static float hudFade() {
			return fade * fade * (3f - 2f * fade);
		}

		private static void onRender(WorldRenderContext ctx) {
			VisualConfig cfg = VisualConfig.get();

			long now = System.nanoTime();
			float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
			lastNanos = now;

			boolean anyFx = cfg.box || cfg.ring || cfg.scanner || cfg.orbit || cfg.ghost || cfg.spinCircle || cfg.chinaHat;
			MinecraftClient mc = MinecraftClient.getInstance();
			if (!cfg.targetEnabled || !(anyFx || cfg.targetHud) || mc.world == null || mc.player == null) {
				fade = 0f;
				lastTarget = null;
				return;
			}

			float td = ctx.tickCounter().getTickDelta(false);

			LivingEntity found = findTarget(mc, td, cfg);
			if (found != null) {
				if (found != lastTarget) fade = Math.min(fade, 0.3f);
				lastTarget = found;
				fade = Math.min(1f, fade + dt * 6f);
			} else {
				fade = Math.max(0f, fade - dt * 5f);
			}

			LivingEntity target = lastTarget;
			if (target != null && (target.isRemoved() || !target.isAlive())) {
				lastTarget = null;
				fade = 0f;
				return;
			}
			if (target == null || fade <= 0.01f) {
				if (fade <= 0.01f) lastTarget = null;
				return;
			}
			if (!anyFx) return;

			float ease = fade * fade * (3f - 2f * fade);

			Vec3d cam = ctx.camera().getPos();
			Vec3d feet = target.getLerpedPos(td).subtract(cam);
			double width = target.getWidth();
			double height = target.getHeight();
			double half = width / 2.0;
			double grow = 0.75 + 0.25 * ease;
			double t = (mc.world.getTime() + td) / 20.0 * cfg.speed;

			// Box rysujemy liniami
			if (cfg.box) {
				Painter p = Painter.begin(ctx);
				if (p != null) {
					p.t = t;
					p.mult = ease;
					double pad = 0.02;
					p.box(feet.x - half - pad, feet.y - pad, feet.z - half - pad,
							feet.x + half + pad, feet.y + height + pad, feet.z + half + pad, 255);
					p.end();
				}
			}

			Fx fx = Fx.begin(ctx);
			if (fx == null) return;
			Theme th = fx.theme;
			double e = ease;

			if (cfg.ring) drawRing(fx, th, feet, half * grow, t, e);
			if (cfg.scanner) drawScanner(fx, th, feet, half * grow, height, t, e);
			if (cfg.orbit) drawOrbit(fx, th, feet, half * grow, height, t, e);
			if (cfg.ghost) drawGhost(fx, th, feet, half * grow, height, t, e);
			if (cfg.chinaHat) drawChinaHat(fx, th, feet, height, t, e);
			if (cfg.spinCircle) drawSpinCircle(fx, th, feet, half, height, t, e);

			fx.end();
		}

		// ------------------------------------------------------------------
		// Kolory
		// ------------------------------------------------------------------

		/** Jasny, nasycony kolor efektu dla fazy (widoczny tez na czarnym motywie). */
		private static int col(Theme th, double phase) {
			return Theme.mix(th.fx(phase), th.glow(phase), 0.6);
		}

		// ------------------------------------------------------------------
		// Ring: miekki pierscien na ziemi z obracajacymi sie lukami
		// ------------------------------------------------------------------

		private static void drawRing(Fx fx, Theme th, Vec3d feet, double half, double t, double e) {
			double r = (half + 0.55 + 0.05 * Math.sin(t * 3.0));
			double y = feet.y + 0.03;
			int c = col(th, t * 0.1);
			int c2 = col(th, t * 0.1 + 0.5);
			double pulse = 0.8 + 0.2 * Math.sin(t * 3.0);

			// miekka poswiata od srodka
			fx.groundRing(feet.x, y, feet.z, 0.0, r + 0.25, 56, Fx.argb(55 * e, c), Fx.argb(0, c));
			// glowny pierscien z gradientem do srodka
			fx.groundRing(feet.x, y + 0.004, feet.z, r - 0.30, r, 64, Fx.argb(0, c), Fx.argb(170 * e * pulse, c));
			// jasna, cienka krawedz
			fx.groundRing(feet.x, y + 0.008, feet.z, r - 0.03, r + 0.01, 64, Fx.argb(255 * e, c2), Fx.argb(255 * e, c2));
			// zewnetrzna poswiata
			fx.groundRing(feet.x, y + 0.004, feet.z, r + 0.01, r + 0.22, 64, Fx.argb(110 * e, c), Fx.argb(0, c));

			// obracajace sie jasne luki
			for (int k = 0; k < 3; k++) {
				double a0 = t * 1.6 + k * (Math.PI * 2.0 / 3.0);
				fx.groundArc(feet.x, y + 0.012, feet.z, r - 0.16, r + 0.07, a0, a0 + 0.9, 14,
						Fx.argb(0, c2), Fx.argb(235 * e, c2));
			}
			for (int k = 0; k < 2; k++) {
				double a0 = -t * 1.1 + k * Math.PI;
				fx.groundArc(feet.x, y + 0.012, feet.z, r - 0.52, r - 0.46, a0, a0 + 1.4, 14,
						Fx.argb(190 * e, c), Fx.argb(190 * e, c));
			}
		}

		// ------------------------------------------------------------------
		// Scanner: swietlisty pierscien "skanujacy" cialo w gore i w dol
		// ------------------------------------------------------------------

		private static void drawScanner(Fx fx, Theme th, Vec3d feet, double half, double height, double t, double e) {
			double r = half + 0.32;
			double s = Math.sin(t * 1.8);
			double dir = Math.cos(t * 1.8) >= 0 ? 1.0 : -1.0;     // kierunek ruchu
			double y = feet.y - 0.1 + (height + 0.2) * (0.5 + 0.5 * s);
			double trail = 0.55;                                    // dlugosc smugi za pierscieniem
			int c = col(th, 0.2 + 0.15 * s);
			int c2 = Theme.mix(c, 0xFFFFFF, 0.45);

			// smuga (sciana) za pierscieniem
			double yb = y - dir * trail;
			fx.wall(feet.x, 0, feet.z, r, yb, (y + yb) * 0.5, y, 56,
					Fx.argb(0, c), Fx.argb(70 * e, c), Fx.argb(170 * e, c));
			// pierscien i poswiata w poziomie
			fx.groundRing(feet.x, y, feet.z, r - 0.02, r + 0.02, 56, Fx.argb(255 * e, c2), Fx.argb(255 * e, c2));
			fx.groundRing(feet.x, y, feet.z, r + 0.02, r + 0.30, 56, Fx.argb(130 * e, c), Fx.argb(0, c));
			fx.groundRing(feet.x, y, feet.z, Math.max(0.0, r - 0.30), r - 0.02, 56, Fx.argb(0, c), Fx.argb(90 * e, c));
		}

		// ------------------------------------------------------------------
		// Orbit: swiecace kule z ogonami krazace wokol celu
		// ------------------------------------------------------------------

		private static void drawOrbit(Fx fx, Theme th, Vec3d feet, double half, double height, double t, double e) {
			int count = 5;
			int tail = 18;
			for (int i = 0; i < count; i++) {
				double dir = (i % 2 == 0) ? 1.0 : -1.0;
				double base = i * (2.0 * Math.PI / count);
				double radius = half + 0.55 + 0.10 * Math.sin(i * 1.7);
				double phase = i / (double) count;
				int c = col(th, phase);
				int core = Theme.mix(c, 0xFFFFFF, 0.7);

				for (int k = tail; k >= 0; k--) {
					double tt = t - k * 0.028;
					double ang = base + dir * tt * (1.6 + 0.15 * i);
					double y = feet.y + height * (0.52 + 0.36 * Math.sin(tt * 1.1 + i * 1.3));
					double x = feet.x + Math.cos(ang) * radius;
					double z = feet.z + Math.sin(ang) * radius;
					double f = 1.0 - k / (double) (tail + 1);

					if (k == 0) {
						fx.disc(x, y, z, 0.20, Fx.argb(90 * e, c));
						fx.disc(x, y, z, 0.09, Fx.argb(230 * e, c));
						fx.disc(x, y, z, 0.035, Fx.argb(255 * e, core));
					} else {
						fx.disc(x, y, z, 0.012 + 0.07 * f * f, Fx.argb(150 * f * f * e, c));
					}
				}
			}
		}

		// ------------------------------------------------------------------
		// Ghost: szerokie, miekkie smugi wiatru owijajace sie wokol ciala
		// ------------------------------------------------------------------

		private static void drawGhost(Fx fx, Theme th, Vec3d feet, double half, double height, double t, double e) {
			for (int s = 0; s < 3; s++) {
				double sgn = (s % 2 == 0) ? 1.0 : -1.0;
				double baseY = feet.y + height * (0.36 + 0.17 * s);
				double rad = half + 0.40 + 0.12 * s;
				double tilt = 0.30 * sgn;
				double rot = t * (1.1 + 0.25 * s) * sgn;
				double len = Math.PI * (1.35 + 0.15 * s);
				int segs = 44;
				int c = col(th, 0.15 * s + t * 0.05);
				int bright = Theme.mix(c, 0xFFFFFF, 0.25);

				for (int j = 0; j < segs; j++) {
					double u0 = j / (double) segs;
					double u1 = (j + 1) / (double) segs;
					double a0 = rot + u0 * len;
					double a1 = rot + u1 * len;
					double env0 = Math.sin(Math.PI * u0);
					double env1 = Math.sin(Math.PI * u1);

					double w0 = height * 0.12 * (0.55 + 0.45 * env0);
					double w1 = height * 0.12 * (0.55 + 0.45 * env1);

					double x0 = feet.x + Math.cos(a0) * rad, z0 = feet.z + Math.sin(a0) * rad;
					double x1 = feet.x + Math.cos(a1) * rad, z1 = feet.z + Math.sin(a1) * rad;
					double y0 = baseY + tilt * Math.sin(a0) * rad * 0.9 + 0.04 * Math.sin(a0 * 2.0 + t);
					double y1 = baseY + tilt * Math.sin(a1) * rad * 0.9 + 0.04 * Math.sin(a1 * 2.0 + t);

					int m0 = Fx.argb(200 * env0 * e, bright);
					int m1 = Fx.argb(200 * env1 * e, bright);
					int edge = Fx.argb(0, c);

					fx.quad2(x0, y0 - w0, z0, edge, x1, y1 - w1, z1, edge, x1, y1, z1, m1, x0, y0, z0, m0);
					fx.quad2(x0, y0, z0, m0, x1, y1, z1, m1, x1, y1 + w1, z1, edge, x0, y0 + w0, z0, edge);
				}
			}
		}

		// ------------------------------------------------------------------
		// China hat: polprzezroczysty stozek nad glowa
		// ------------------------------------------------------------------

		private static void drawChinaHat(Fx fx, Theme th, Vec3d feet, double height, double t, double e) {
			double scale = Math.max(0.6, height / 1.8);
			double baseY = feet.y + height - 0.02 + 0.015 * Math.sin(t * 2.0);
			double apexY = baseY + 0.34 * scale;
			double rBase = 0.62 * scale;
			int c = col(th, 0.1);
			int c2 = col(th, 0.6);
			int rim = Theme.mix(c2, 0xFFFFFF, 0.35);

			fx.cone(feet.x, apexY, feet.z, baseY, rBase, 48, Fx.argb(235 * e, c2), Fx.argb(150 * e, c));
			// obrzeze kapelusza
			fx.groundRing(feet.x, baseY, feet.z, rBase - 0.035, rBase, 56, Fx.argb(255 * e, rim), Fx.argb(255 * e, rim));
			fx.groundRing(feet.x, baseY, feet.z, rBase, rBase + 0.14, 56, Fx.argb(110 * e, c), Fx.argb(0, c));
		}

		// ------------------------------------------------------------------
		// Spin circle: obracajace sie luki zwrocone do kamery (jak lock-on)
		// ------------------------------------------------------------------

		private static void drawSpinCircle(Fx fx, Theme th, Vec3d feet, double half, double height, double t, double e) {
			double cx = feet.x;
			double cy = feet.y + height * 0.55;
			double cz = feet.z;
			double radius = Math.max(half + 0.30, height * 0.36);
			int c = col(th, 0.0);
			int c2 = col(th, 0.5);

			// delikatny pelny okrag
			fx.billboardArc(cx, cy, cz, radius, 0.012, 0.0, Math.PI * 2.0, 48, c, 50 * e);
			// cztery grube, obracajace sie luki z miekkimi koncami
			for (int k = 0; k < 4; k++) {
				double a0 = t * 2.2 + k * (Math.PI / 2.0);
				fx.billboardArc(cx, cy, cz, radius, 0.055, a0, a0 + 0.95, 14, k % 2 == 0 ? c : c2, 255 * e);
			}
			// wewnetrzne, przeciwbiezne
			for (int k = 0; k < 2; k++) {
				double a0 = -t * 1.5 + k * Math.PI;
				fx.billboardArc(cx, cy, cz, radius * 0.72, 0.03, a0, a0 + 1.2, 12, c2, 190 * e);
			}
		}

		// ------------------------------------------------------------------
		// Wykrywanie celu
		// ------------------------------------------------------------------

		/** Raycast od kamery - zwraca zywa istote, w ktora celujesz (i ktorej nie zaslania blok). */
		private static LivingEntity findTarget(MinecraftClient mc, float tickDelta, VisualConfig cfg) {
			Entity cam = mc.getCameraEntity();
			if (cam == null) return null;

			double range = cfg.range;
			Vec3d start = cam.getCameraPosVec(tickDelta);
			Vec3d dir = cam.getRotationVec(tickDelta);
			Vec3d end = start.add(dir.multiply(range));

			double maxDistSq = range * range;
			HitResult blockHit = mc.world.raycast(new RaycastContext(
					start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, cam));
			if (blockHit.getType() != HitResult.Type.MISS) {
				maxDistSq = blockHit.getPos().squaredDistanceTo(start);
			}

			Box searchBox = cam.getBoundingBox().stretch(dir.multiply(range)).expand(1.0);
			EntityHitResult hit = ProjectileUtil.raycast(
					cam, start, end, searchBox,
					ent -> ent instanceof LivingEntity
							&& !ent.isSpectator()
							&& ent.canHit()
							&& (!cfg.playersOnly || ent instanceof PlayerEntity),
					maxDistSq);

			if (hit != null && hit.getEntity() instanceof LivingEntity living) {
				return living;
			}
			return null;
		}
	}

	/**
	 * Dodatki: Jump circle, Trails, Blizzard, Fireflies.
	 * Miekkie, polprzezroczyste ksztalty w kolorach aktualnego motywu.
	 */
	static final class ExtrasRenderer {
		private ExtrasRenderer() {}

		private static final int CIRCLE_TICKS = 26;

		private static final class Circle {
			final double x, y, z;
			final int seed = (int) (Math.random() * 100000.0);
			int age = 0;

			Circle(Vec3d p) {
				this.x = p.x;
				this.y = p.y;
				this.z = p.z;
			}
		}

		private static final List<Circle> circles = new ArrayList<>();
		private static final List<Vec3d> trail = new ArrayList<>();
		private static boolean wasOnGround = true;
		private static ClientWorld lastWorld;

		public static void register() {
			ClientTickEvents.END_CLIENT_TICK.register(ExtrasRenderer::onTick);
			WorldRenderEvents.AFTER_ENTITIES.register(ExtrasRenderer::onRender);
		}

		// ------------------------------------------------------------------
		// Logika co tick (skoki i zapis sladu)
		// ------------------------------------------------------------------

		private static void onTick(MinecraftClient mc) {
			ClientPlayerEntity pl = mc.player;
			if (pl == null || mc.world == null) {
				circles.clear();
				trail.clear();
				lastWorld = null;
				return;
			}
			if (mc.world != lastWorld) {
				lastWorld = mc.world;
				circles.clear();
				trail.clear();
			}

			VisualConfig cfg = VisualConfig.get();

			boolean onGround = pl.isOnGround();
			if (cfg.jumpCircle && wasOnGround && !onGround && pl.getVelocity().y > 0.05) {
				circles.add(new Circle(pl.getPos()));
			}
			wasOnGround = onGround;

			Iterator<Circle> it = circles.iterator();
			while (it.hasNext()) {
				Circle c = it.next();
				c.age++;
				if (c.age >= CIRCLE_TICKS) it.remove();
			}

			if (cfg.trails) {
				Vec3d pos = pl.getPos();
				if (!trail.isEmpty() && trail.get(trail.size() - 1).squaredDistanceTo(pos) > 64.0) {
					trail.clear(); // teleport
				}
				trail.add(pos);
				int max = (int) (cfg.trailSeconds * 20f) + 2;
				while (trail.size() > max) trail.remove(0);
			} else {
				trail.clear();
			}
		}

		// ------------------------------------------------------------------
		// Rysowanie
		// ------------------------------------------------------------------

		private static void onRender(WorldRenderContext ctx) {
			VisualConfig cfg = VisualConfig.get();
			if (!(cfg.jumpCircle || cfg.trails || cfg.blizzard || cfg.fireflies)) return;

			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world == null || mc.player == null) return;

			Fx fx = Fx.begin(ctx);
			if (fx == null) return;

			float td = ctx.tickCounter().getTickDelta(false);
			Vec3d cam = ctx.camera().getPos();
			double time = (mc.world.getTime() + td) / 20.0;

			if (cfg.jumpCircle) drawCircles(fx, cam, td);
			if (cfg.trails) drawTrail(fx, mc, cam, td, time);
			if (cfg.blizzard) drawBlizzard(fx, mc, cfg, cam, td, time);
			if (cfg.fireflies) drawFireflies(fx, mc, cfg, cam, td, time);

			fx.end();
		}

		/**
		 * Jump circle: fala uderzeniowa na ziemi (jasna krawedz + zanikajacy ogon), druga mniejsza fala,
		 * miekka poswiata i klebki kurzu rozchodzace sie na boki.
		 */
		private static void drawCircles(Fx fx, Vec3d cam, float td) {
			Theme th = fx.theme;
			for (Circle c : circles) {
				double prog = Math.min(1.0, (c.age + td) / CIRCLE_TICKS);
				double ease = 1.0 - Math.pow(1.0 - prog, 3.0);
				double fade = 1.0 - prog;

				double x = c.x - cam.x;
				double y = c.y - cam.y + 0.02;
				double z = c.z - cam.z;

				double ph = c.age * 0.02;
				int body = Theme.mix(th.fx(ph), th.glow(ph), 0.5);
				int edge = Theme.mix(th.glow(ph), 0xFFFFFF, 0.35);
				int dust = Theme.mix(0x8E8E99, th.glow(ph), 0.25);

				// poswiata na ziemi
				fx.groundRing(x, y, z, 0.0, 0.3 + ease * 2.4, 40,
						Fx.argb(95 * fade * fade, body), Fx.argb(0, body));

				// glowna fala: jasna krawedz z wyciagnietym ogonem do srodka
				double r = 0.3 + ease * 2.7;
				double w = 0.65 * (1.0 - prog * 0.55);
				fx.groundRing(x, y + 0.004, z, Math.max(0.0, r - w), r, 56,
						Fx.argb(0, body), Fx.argb(235 * Math.pow(fade, 1.3), body));
				fx.groundRing(x, y + 0.008, z, r - 0.045, r, 56,
						Fx.argb(110 * fade, edge), Fx.argb(255 * fade, edge));

				// druga fala, lekko opozniona
				if (prog > 0.1) {
					double p2 = (prog - 0.1) / 0.9;
					double e2 = 1.0 - Math.pow(1.0 - p2, 3.0);
					double r2 = 0.2 + e2 * 1.7;
					double w2 = 0.35 * (1.0 - p2 * 0.6);
					double f2 = 1.0 - p2;
					fx.groundRing(x, y + 0.004, z, Math.max(0.0, r2 - w2), r2, 48,
							Fx.argb(0, body), Fx.argb(150 * f2 * f2, edge));
				}

				// kurz
				for (int k = 0; k < 12; k++) {
					double a = Fx.hash(k, c.seed) * Math.PI * 2.0;
					double dist = ease * (1.2 + 1.1 * Fx.hash(k, c.seed + 1));
					double hgt = 0.04 + 0.55 * prog * Fx.hash(k, c.seed + 2);
					double sz = 0.10 + 0.32 * prog * (0.6 + 0.4 * Fx.hash(k, c.seed + 3));
					fx.disc(x + Math.cos(a) * dist, y + hgt, z + Math.sin(a) * dist, sz,
							Fx.argb(95 * fade * fade, dust));
				}
			}
		}

		/**
		 * Trails: gladka wstega za graczem (Catmull-Rom), zwezajaca sie klinem ku ogonowi,
		 * z jasna linia przy ziemi.
		 */
		private static void drawTrail(Fx fx, MinecraftClient mc, Vec3d cam, float td, double time) {
			int n = trail.size();
			if (n < 2) return;

			Vec3d head = mc.player.getLerpedPos(td);
			int total = n + 1;
			int sub = 3;
			int cnt = (total - 1) * sub + 1;
			double[] px = new double[cnt];
			double[] py = new double[cnt];
			double[] pz = new double[cnt];

			for (int i = 0; i < total - 1; i++) {
				Vec3d p0 = pt(i - 1, n, head);
				Vec3d p1 = pt(i, n, head);
				Vec3d p2 = pt(i + 1, n, head);
				Vec3d p3 = pt(i + 2, n, head);
				for (int s = 0; s < sub; s++) {
					double u = s / (double) sub;
					int k = i * sub + s;
					px[k] = catmull(p0.x, p1.x, p2.x, p3.x, u);
					py[k] = catmull(p0.y, p1.y, p2.y, p3.y, u);
					pz[k] = catmull(p0.z, p1.z, p2.z, p3.z, u);
				}
			}
			px[cnt - 1] = head.x;
			py[cnt - 1] = head.y;
			pz[cnt - 1] = head.z;

			Theme th = fx.theme;
			for (int k = 0; k < cnt - 1; k++) {
				double f0 = k / (double) (cnt - 1);
				double f1 = (k + 1) / (double) (cnt - 1);
				double a0 = Math.pow(f0, 1.5);
				double a1 = Math.pow(f1, 1.5);
				double top0 = 0.10 + 0.95 * f0;
				double top1 = 0.10 + 0.95 * f1;
				int c0 = Theme.mix(th.fx(f0 * 0.8 + time * 0.1), th.glow(f0), 0.35);
				int c1 = Theme.mix(th.fx(f1 * 0.8 + time * 0.1), th.glow(f1), 0.35);

				double x0 = px[k] - cam.x, y0 = py[k] - cam.y, z0 = pz[k] - cam.z;
				double x1 = px[k + 1] - cam.x, y1 = py[k + 1] - cam.y, z1 = pz[k + 1] - cam.z;

				// glowna wstega: przy ziemi mocniej, ku gorze zanika
				fx.quad2(x0, y0 + 0.03, z0, Fx.argb(150 * a0, c0),
						x1, y1 + 0.03, z1, Fx.argb(150 * a1, c1),
						x1, y1 + top1, z1, Fx.argb(0, c1),
						x0, y0 + top0, z0, Fx.argb(0, c0));

				// jasna linia przy ziemi
				int e0 = Theme.mix(th.glow(f0), 0xFFFFFF, 0.3);
				int e1 = Theme.mix(th.glow(f1), 0xFFFFFF, 0.3);
				fx.quad2(x0, y0 + 0.02, z0, Fx.argb(230 * a0, e0),
						x1, y1 + 0.02, z1, Fx.argb(230 * a1, e1),
						x1, y1 + 0.10, z1, Fx.argb(0, e1),
						x0, y0 + 0.10, z0, Fx.argb(0, e0));
			}
		}

		private static Vec3d pt(int i, int n, Vec3d head) {
			if (i < 0) i = 0;
			if (i >= n) return head;
			return trail.get(i);
		}

		private static double catmull(double p0, double p1, double p2, double p3, double u) {
			double u2 = u * u;
			double u3 = u2 * u;
			return 0.5 * ((2.0 * p1) + (-p0 + p2) * u + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2
					+ (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3);
		}

		/**
		 * Blizzard: miekkie platki sniegu (okragle plamki), porywy wiatru, kolysanie,
		 * znikaja z daleka i pod dachem.
		 */
		private static void drawBlizzard(Fx fx, MinecraftClient mc, VisualConfig cfg, Vec3d cam, float td, double time) {
			Vec3d pc = mc.player.getLerpedPos(td);
			int count = (int) (380 * cfg.blizzardDensity);
			double radius = 16.0;
			double size = radius * 2.0;
			double heightSpan = 16.0;

			double gx = 2.6 + 1.8 * Math.sin(time * 0.37);
			double gz = 0.9 + 1.2 * Math.sin(time * 0.23 + 1.0);
			int tint = Theme.mix(0xFFFFFF, fx.theme.glow(0.3), 0.18);

			for (int i = 0; i < count; i++) {
				double rs = Fx.hash(i, 4);
				double fall = 2.0 + 1.6 * rs;
				double wind = 0.7 + 0.6 * rs;

				double ux = Fx.hash(i, 1) * size + gx * wind * time;
				double uz = Fx.hash(i, 2) * size + gz * wind * time;
				double uy = Fx.hash(i, 3) * heightSpan - fall * time;

				double x = pc.x - radius + Fx.mod(ux - (pc.x - radius), size);
				double z = pc.z - radius + Fx.mod(uz - (pc.z - radius), size);
				double y = pc.y - 4.0 + Fx.mod(uy - (pc.y - 4.0), heightSpan);

				x += Math.sin(time * (0.8 + rs) + i) * 0.35;
				z += Math.cos(time * (0.7 + rs) + i * 1.3) * 0.35;

				int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
				if (y < top) continue;

				double d = Math.hypot(x - pc.x, z - pc.z);
				double edge = 1.0 - Fx.smooth(11.0, 16.0, d);
				if (edge <= 0.0) continue;

				double flake = 0.025 + 0.04 * Fx.hash(i, 7);
				int alpha = (int) ((150 + 90 * Fx.hash(i, 6)) * edge);
				fx.disc(x - cam.x, y - cam.y, z - cam.z, flake, Fx.argb(alpha, tint));
			}
		}

		/**
		 * Fireflies: ciepłe zolto-zielone swietliki, plynnie bladzace nad terenem,
		 * pulsujace blask z halo. Wieczorem i w nocy swieca mocniej.
		 */
		private static void drawFireflies(Fx fx, MinecraftClient mc, VisualConfig cfg, Vec3d cam, float td, double time) {
			Vec3d pc = mc.player.getLerpedPos(td);
			int count = (int) cfg.fireflyCount;
			double radius = 18.0;
			double size = radius * 2.0;

			double night = nightFactor(mc);
			double visibility = 0.35 + 0.65 * night;

			for (int i = 0; i < count; i++) {
				double h1 = Fx.hash(i, 14);
				double h2 = Fx.hash(i, 15);

				double ax = pc.x - radius + Fx.mod(Fx.hash(i, 10) * size - (pc.x - radius), size);
				double az = pc.z - radius + Fx.mod(Fx.hash(i, 11) * size - (pc.z - radius), size);

				double x = ax + Math.sin(time * 0.35 * (0.6 + h1) + i * 1.7) * 1.8
						+ Math.sin(time * 0.9 + i) * 0.3;
				double z = az + Math.cos(time * 0.30 * (0.6 + h2) + i * 1.3) * 1.8
						+ Math.cos(time * 0.8 + i * 0.7) * 0.3;

				int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
				double y = top + 0.6 + 1.1 * (0.5 + 0.5 * Math.sin(time * 0.4 * (0.5 + h1) + i * 2.1));

				double d = Math.hypot(x - pc.x, z - pc.z);
				double edge = 1.0 - Fx.smooth(13.0, 18.0, d);
				if (edge <= 0.0) continue;

				double pulse = 0.5 + 0.5 * Math.sin(time * (0.9 + 1.1 * Fx.hash(i, 17)) + i * 2.3);
				double glow = pulse * pulse * pulse;
				double a = (0.25 + 0.75 * glow) * visibility * edge;

				int color = Theme.mix(0xC9F050, fx.theme.glow(Fx.hash(i, 18)), 0.2);
				double px = x - cam.x, py = y - cam.y, pz = z - cam.z;
				double s = 0.035 + 0.02 * glow;

				fx.disc(px, py, pz, s * 6.0, Fx.argb(70 * a, color));
				fx.disc(px, py, pz, s * 2.2, Fx.argb(190 * a, color));
				fx.disc(px, py, pz, s * 0.9, Fx.argb(255 * a, Theme.mix(color, 0xFFFFFF, 0.6)));
			}
		}

		/** 0 = dzien, 1 = noc (z plynnym przejsciem o zmierzchu i switie). */
		private static double nightFactor(MinecraftClient mc) {
			long tod = mc.world.getTimeOfDay() % 24000L;
			if (tod < 12000L) return 0.0;
			if (tod < 13800L) return (tod - 12000L) / 1800.0;
			if (tod < 22200L) return 1.0;
			return (24000L - tod) / 1800.0;
		}
	}

	/**
	 * Tryb deszczu (jak na zdjeciu): cienkie smugi, mokra ciemna ziemia jako jedna ciagla powierzchnia
	 * (bez szwow miedzy blokami) i kregi od kropel na calej ziemi wokol Ciebie. Skok = chlapniecie.
	 */
	static final class RainRenderer {
		private RainRenderer() {}

		private static final int SPLASH_TICKS = 34;
		private static final int WET_R = 12;                 // promien mokrej powierzchni (bloki)
		private static final int GRID = WET_R * 2 + 1;
		private static final int[][] TOPS = new int[GRID][GRID];

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

		private static final List<Splash> splashes = new ArrayList<>();
		private static boolean wasOnGround = true;
		private static double lastVy = 0.0;
		private static ClientWorld lastWorld;

		public static void register() {
			ClientTickEvents.END_CLIENT_TICK.register(RainRenderer::onTick);
			WorldRenderEvents.AFTER_ENTITIES.register(RainRenderer::onRender);
		}

		// ------------------------------------------------------------------
		// Skoki -> chlapniecia
		// ------------------------------------------------------------------

		private static void onTick(MinecraftClient mc) {
			VisualConfig cfg = VisualConfig.get();
			ClientPlayerEntity pl = mc.player;
			if (!cfg.rain || pl == null || mc.world == null) {
				splashes.clear();
				return;
			}
			if (mc.world != lastWorld) {
				lastWorld = mc.world;
				splashes.clear();
			}

			long tick = mc.world.getTime();
			boolean onGround = pl.isOnGround();
			Vec3d pos = pl.getPos();
			double vy = pl.getVelocity().y;

			if (wasOnGround && !onGround && vy > 0.05) {
				splashes.add(new Splash(pos.x, pos.y, pos.z, tick, 0.85));
			} else if (!wasOnGround && onGround && lastVy < -0.4) {
				splashes.add(new Splash(pos.x, pos.y, pos.z, tick, Math.min(1.6, 0.6 - lastVy)));
			}
			wasOnGround = onGround;
			lastVy = vy;

			splashes.removeIf(s -> tick - s.start > SPLASH_TICKS);
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

			drawWetGround(fx, mc, cam, pc);
			drawRipples(fx, mc, cfg, cam, pc, time);
			drawSplashes(fx, cam, tickD);
			drawRain(fx, mc, cfg, cam, pc, time);

			fx.end();
		}

		/**
		 * Mokra ziemia: jedna ciagla, ciemna i blyszczaca powierzchnia dookola gracza.
		 * Przezroczystosc plynnie maleje z odlegloscia, wiec nie ma widocznych brzegow blokow.
		 */
		private static void drawWetGround(Fx fx, MinecraftClient mc, Vec3d cam, Vec3d pc) {
			int px = MathHelper.floor(pc.x);
			int pz = MathHelper.floor(pc.z);

			for (int gx = 0; gx < GRID; gx++) {
				for (int gz = 0; gz < GRID; gz++) {
					TOPS[gx][gz] = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, px - WET_R + gx, pz - WET_R + gz);
				}
			}

			for (int gx = 0; gx < GRID; gx++) {
				for (int gz = 0; gz < GRID; gz++) {
					int top = TOPS[gx][gz];
					if (top > pc.y + 3.0 || top < pc.y - 12.0) continue;

					int bx = px - WET_R + gx;
					int bz = pz - WET_R + gz;
					double x0 = bx - cam.x, x1 = x0 + 1.0;
					double z0 = bz - cam.z, z1 = z0 + 1.0;
					double y = top - cam.y + 0.005;

					int a00 = darkAlpha(bx, bz, pc), a10 = darkAlpha(bx + 1, bz, pc);
					int a11 = darkAlpha(bx + 1, bz + 1, pc), a01 = darkAlpha(bx, bz + 1, pc);
					if (a00 + a10 + a11 + a01 == 0) continue;

					fx.quad2(x0, y, z0, Fx.argb(a00, 0x070E1A), x1, y, z0, Fx.argb(a10, 0x070E1A),
							x1, y, z1, Fx.argb(a11, 0x070E1A), x0, y, z1, Fx.argb(a01, 0x070E1A));

					// delikatny blask nieba na mokrej powierzchni
					double y2 = y + 0.003;
					fx.quad2(x0, y2, z0, Fx.argb(a00 * 0.28, 0x6F8DB8), x1, y2, z0, Fx.argb(a10 * 0.28, 0x6F8DB8),
							x1, y2, z1, Fx.argb(a11 * 0.28, 0x6F8DB8), x0, y2, z1, Fx.argb(a01 * 0.28, 0x6F8DB8));
				}
			}
		}

		private static int darkAlpha(int cx, int cz, Vec3d pc) {
			double d = Math.hypot(cx - pc.x, cz - pc.z);
			return (int) (95.0 * (1.0 - Fx.smooth(WET_R * 0.45, WET_R, d)));
		}

		/** Kregi od kropel rozlozone na calej ziemi (cienkie, jasne obwodki jak na zdjeciu). */
		private static void drawRipples(Fx fx, MinecraftClient mc, VisualConfig cfg, Vec3d cam, Vec3d pc, double time) {
			int count = (int) (190 * cfg.rainDensity);
			double radius = 24.0;
			double size = radius * 2.0;

			for (int i = 0; i < count; i++) {
				double speed = 0.7 + 0.6 * Fx.hash(i, 3);
				double cyc = time * speed + Fx.hash(i, 4);
				int idx = (int) Math.floor(cyc);
				double ph = cyc - idx;

				double ox = Fx.hash(i * 31 + idx, 5) * size;
				double oz = Fx.hash(i * 31 + idx, 6) * size;
				double x = pc.x - radius + Fx.mod(ox - (pc.x - radius), size);
				double z = pc.z - radius + Fx.mod(oz - (pc.z - radius), size);

				int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
				if (top > pc.y + 3.0 || top < pc.y - 12.0) continue;

				double d = Math.hypot(x - pc.x, z - pc.z);
				double edge = 1.0 - Fx.smooth(16.0, radius, d);
				if (edge <= 0.0) continue;

				double r = ph * (0.18 + 0.22 * Fx.hash(i, 7));
				double a = Math.pow(1.0 - ph, 1.6) * 215.0 * edge;
				double hw = 0.006 + 0.008 * ph;
				double y = top - cam.y + 0.012;
				int c = Fx.argb(a, 0xDCEBFF);
				fx.groundRing(x - cam.x, y, z - cam.z, Math.max(0.0, r - hw), r + hw, 18, c, c);

				if (ph > 0.18) {
					double r2 = (ph - 0.18) * (0.14 + 0.16 * Fx.hash(i, 8));
					int c2 = Fx.argb(a * 0.55, 0xDCEBFF);
					fx.groundRing(x - cam.x, y, z - cam.z, Math.max(0.0, r2 - hw), r2 + hw, 14, c2, c2);
				}
			}
		}

		/** Chlapniecie: krag na wodzie i krople lecace lukiem. */
		private static void drawSplashes(Fx fx, Vec3d cam, double tickD) {
			for (Splash s : splashes) {
				double age = (tickD - s.start) / 20.0;
				if (age < 0.0 || age > 1.7) continue;

				double x = s.x - cam.x;
				double y = s.y - cam.y;
				double z = s.z - cam.z;
				double pw = s.power;

				double ringP = Fx.clamp01(age / 0.9);
				double r = (0.15 + ringP * 1.1) * (0.8 + 0.4 * pw);
				double ringA = Math.pow(1.0 - ringP, 1.6) * 230.0;
				if (ringA > 2.0) {
					int c = Fx.argb(ringA, 0xE6F2FF);
					int c0 = Fx.argb(ringA * 0.15, 0xE6F2FF);
					fx.groundRing(x, y + 0.014, z, Math.max(0.0, r - 0.09), r, 32, c0, c);
					fx.groundRing(x, y + 0.016, z, Math.max(0.0, r * 0.55 - 0.05), r * 0.55, 28, c0, Fx.argb(ringA * 0.7, 0xE6F2FF));
				}

				int n = (int) (14 + 14 * Math.min(1.0, pw));
				for (int k = 0; k < n; k++) {
					double ang = Fx.hash(k, s.seed) * Math.PI * 2.0;
					double hs = (0.5 + 1.3 * Fx.hash(k, s.seed + 1)) * (0.7 + 0.5 * pw);
					double vy = (1.9 + 2.4 * Fx.hash(k, s.seed + 2)) * (0.75 + 0.4 * pw);

					double life = 2.0 * vy / 9.8;
					if (age > life) continue;

					double dy = vy * age - 4.9 * age * age;
					if (dy < 0.0) continue;

					double fade = 1.0 - age / life;
					double size = 0.035 + 0.03 * Fx.hash(k, s.seed + 3);
					int alpha = (int) (235 * Math.sqrt(fade));
					fx.disc(x + Math.cos(ang) * hs * age, y + 0.03 + dy, z + Math.sin(ang) * hs * age, size,
							Fx.argb(alpha, 0xD6E8FF));

					double a2 = Math.max(0.0, age - 0.035);
					double dy2 = vy * a2 - 4.9 * a2 * a2;
					fx.disc(x + Math.cos(ang) * hs * a2, y + 0.03 + Math.max(0.0, dy2), z + Math.sin(ang) * hs * a2,
							size * 0.8, Fx.argb(alpha * 0.45, 0xD6E8FF));
				}
			}
		}

		/** Cienkie, jasne smugi deszczu (tylko tam, gdzie nic nie przeslania nieba). */
		private static void drawRain(Fx fx, MinecraftClient mc, VisualConfig cfg, Vec3d cam, Vec3d pc, double time) {
			int count = (int) (280 * cfg.rainDensity);
			double radius = 16.0;
			double size = radius * 2.0;
			double heightSpan = 20.0;

			double gx = 1.2 + 0.7 * Math.sin(time * 0.31);
			double gz = 0.5 + 0.5 * Math.sin(time * 0.19 + 2.0);
			double slantX = gx * 0.05;
			double slantZ = gz * 0.05;
			double dl = Math.sqrt(slantX * slantX + 1.0 + slantZ * slantZ);
			double dirX = slantX / dl, dirY = -1.0 / dl, dirZ = slantZ / dl;

			for (int i = 0; i < count; i++) {
				double fall = 16.0 + 6.0 * Fx.hash(i, 4);

				double ux = Fx.hash(i, 1) * size + gx * time;
				double uz = Fx.hash(i, 2) * size + gz * time;
				double uy = Fx.hash(i, 3) * heightSpan - fall * time;

				double x = pc.x - radius + Fx.mod(ux - (pc.x - radius), size);
				double z = pc.z - radius + Fx.mod(uz - (pc.z - radius), size);
				double y = pc.y - 5.0 + Fx.mod(uy - (pc.y - 5.0), heightSpan);

				int top = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));
				if (y < top) continue;

				double d = Math.hypot(x - pc.x, z - pc.z);
				double edge = 1.0 - Fx.smooth(10.0, radius, d);
				if (edge <= 0.0) continue;

				double len = 0.7 + 0.9 * Fx.hash(i, 5);
				double hx = x - cam.x, hy = y - cam.y, hz = z - cam.z;
				double tx = hx - dirX * len, ty = hy - dirY * len, tz = hz - dirZ * len;

				double w = 0.0042 + 0.003 * Fx.hash(i, 9);
				double wx = fx.rx * w, wy = fx.ry * w, wz = fx.rz * w;
				int head = Fx.argb((120 + 60 * Fx.hash(i, 8)) * edge, 0xD5E6F8);
				int tail = Fx.argb(0, 0xD5E6F8);
				fx.quad(hx - wx, hy - wy, hz - wz, head,
						hx + wx, hy + wy, hz + wz, head,
						tx + wx, ty + wy, tz + wz, tail,
						tx - wx, ty - wy, tz - wz, tail);
			}
		}
	}

	/**
	 * Niebo: Aurora, Nebula, Galaxy, Caustics, Black hole.
	 * Pelna, nieprzezroczysta kopula wokol kamery CALKOWICIE zastepuje zwykle niebo
	 * (slonce, ksiezyc, gwiazdy i chmury znikaja). Tylko Overworld.
	 */
	static final class SkyRenderer {
		private SkyRenderer() {}

		private static final float R = 190f;
		private static final int AZ = 96;
		private static final int EL = 40;
		private static final double EL_MIN = Math.toRadians(-35.0);
		private static final double EL_MAX = Math.toRadians(90.0);

		private static final int N = (EL + 1) * (AZ + 1);
		private static final float[] DX = new float[N];
		private static final float[] DY = new float[N];
		private static final float[] DZ = new float[N];
		private static final double[] ELV = new double[N];
		private static final double[] AZV = new double[N];
		private static final int[] COL = new int[N];

		static {
			for (int i = 0; i <= EL; i++) {
				double el = EL_MIN + (EL_MAX - EL_MIN) * (i / (double) EL);
				for (int j = 0; j <= AZ; j++) {
					double az = Math.PI * 2.0 * j / AZ;
					int k = i * (AZ + 1) + j;
					DX[k] = (float) (Math.cos(el) * Math.sin(az));
					DY[k] = (float) Math.sin(el);
					DZ[k] = (float) (-Math.cos(el) * Math.cos(az));
					ELV[k] = el;
					AZV[k] = az;
				}
			}
		}

		private static boolean cloudsHidden = false;
		private static CloudRenderMode savedClouds = null;

		public static void register() {
			WorldRenderEvents.AFTER_ENTITIES.register(SkyRenderer::onRender);
			ClientTickEvents.END_CLIENT_TICK.register(mc -> updateClouds(mc, wantsSky(mc)));
			ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> updateClouds(mc, false));
		}

		private static boolean wantsSky(MinecraftClient mc) {
			return VisualConfig.get().sky != 0 && mc.world != null && mc.world.getRegistryKey() == World.OVERWORLD;
		}

		/** Gdy niebo jest aktywne, chmury sa wylaczone (po wylaczeniu nieba wracaja). */
		private static void updateClouds(MinecraftClient mc, boolean hide) {
			if (mc.options == null) return;
			var opt = mc.options.getCloudRenderMode();
			if (hide) {
				if (!cloudsHidden) {
					savedClouds = opt.getValue();
					cloudsHidden = true;
				}
				if (opt.getValue() != CloudRenderMode.OFF) opt.setValue(CloudRenderMode.OFF);
			} else if (cloudsHidden) {
				if (savedClouds != null) opt.setValue(savedClouds);
				cloudsHidden = false;
			}
		}

		private static void onRender(WorldRenderContext ctx) {
			VisualConfig cfg = VisualConfig.get();
			if (cfg.sky == 0) return;

			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.world == null || mc.player == null) return;
			if (mc.world.getRegistryKey() != World.OVERWORLD) return;

			MatrixStack ms = ctx.matrixStack();
			VertexConsumerProvider consumers = ctx.consumers();
			if (ms == null || consumers == null) return;

			float td = ctx.tickCounter().getTickDelta(false);
			double t = (mc.world.getTime() + td) / 20.0 * cfg.skySpeed;
			Theme theme = cfg.theme();
			int ca = theme.uiA;
			int cb = theme.uiB;
			double inten = cfg.skyIntensity;

			VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
			Matrix4f m = ms.peek().getPositionMatrix();

			for (int k = 0; k < N; k++) {
				double dx = DX[k], dy = DY[k], dz = DZ[k];
				double el = ELV[k];
				int rgb;
				switch (cfg.sky) {
					case 1 -> rgb = aurora(dx, dy, dz, AZV[k], el, t, ca, cb, inten);
					case 2 -> rgb = nebula(dx, dy, dz, el, t, ca, cb, inten);
					case 3 -> rgb = galaxy(dx, dy, dz, el, t, ca, cb, inten);
					case 4 -> rgb = caustics(dx, dy, dz, el, t, ca, cb, inten);
					default -> rgb = Theme.mix(0x010108, Theme.mix(0x0A0A1A, ca, 0.12), glowLow(el));
				}
				if (el < 0.0) rgb = Theme.mix(rgb, 0x000000, 0.45 * Math.min(1.0, -el / 0.3));
				COL[k] = 0xFF000000 | (rgb & 0xFFFFFF);
			}

			for (int i = 0; i < EL; i++) {
				for (int j = 0; j < AZ; j++) {
					int k00 = i * (AZ + 1) + j;
					int k10 = k00 + 1;
					int k01 = k00 + (AZ + 1);
					int k11 = k01 + 1;
					v(vc, m, k00);
					v(vc, m, k10);
					v(vc, m, k11);
					v(vc, m, k01);
				}
			}

			if (cfg.sky == 3 || cfg.sky == 5) drawStars(vc, m, t, ca, cb, inten, cfg.sky == 3 ? 360 : 260);
			if (cfg.sky == 5) drawBlackHole(vc, m, t, ca, cb, inten);

			if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
				immediate.draw(RenderLayer.getLightning());
			}
		}

		private static void v(VertexConsumer vc, Matrix4f m, int k) {
			int c = COL[k];
			vc.vertex(m, DX[k] * R, DY[k] * R, DZ[k] * R)
					.color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, 255);
		}

		/** Lekka poswiata tuz nad horyzontem (0..1). */
		private static double glowLow(double el) {
			return Math.pow(Math.max(0.0, 1.0 - Math.max(0.0, el) / 0.9), 3.0);
		}

		// ------------------------------------------------------------------
		// Style nieba (zwracaja kolor RGB, nieprzezroczysty)
		// ------------------------------------------------------------------

		private static int aurora(double dx, double dy, double dz, double az, double el, double t,
								  int ca, int cb, double inten) {
			int base = Theme.mix(0x02030A, Theme.mix(0x0A1630, ca, 0.12), glowLow(el) * 0.9);
			if (el < 0.0) return base;

			double wob = 0.16 * Math.sin(az * 3.0 + t * 0.25) + 0.16 * (noise3(dx * 3, dz * 3, t * 0.15) - 0.5);
			double alpha = 0.0;
			double hue = 0.0;
			for (int layer = 0; layer < 2; layer++) {
				double b = 0.30 + wob + layer * 0.34;
				double x = el - b;
				double prof = x < 0 ? Math.exp(x * 24.0) : Math.exp(-x * 3.4);
				double rays = noise3(az * 9.0 + layer * 3.7, t * 0.35, 1.3) * 0.6
						+ noise3(az * 22.0, t * 0.55, 4.1 + layer) * 0.4;
				rays = Math.pow(clamp01(rays * 1.5), 1.6);
				double a = prof * (0.25 + rays) * (layer == 0 ? 1.0 : 0.55);
				if (a > alpha) {
					alpha = a;
					hue = clamp01(x * 2.2 + 0.25 + 0.3 * rays);
				}
			}
			int fx = Theme.mix(Theme.mix(ca, cb, hue), 0xFFFFFF, 0.12 * alpha);
			return Theme.mix(base, fx, clamp01(alpha * 0.95 * inten));
		}

		private static int nebula(double dx, double dy, double dz, double el, double t, int ca, int cb, double inten) {
			int base = Theme.mix(0x05030D, Theme.mix(ca, 0x000000, 0.8), 0.5);
			double n = fbm3(dx * 2.2 + t * 0.02, dy * 2.2, dz * 2.2, 4);
			double m = fbm3(dx * 4.5 - t * 0.015 + 9, dy * 4.5, dz * 4.5 + 3, 3);
			double d = smooth(0.36, 0.78, n);
			int cloud = Theme.mix(Theme.mix(ca, 0x000000, 0.55), Theme.mix(cb, ca, 0.4), clamp01(m * 1.2));
			cloud = Theme.mix(cloud, 0xFFFFFF, 0.28 * d * d);
			return Theme.mix(base, cloud, clamp01((0.25 + d * 0.8) * inten));
		}

		private static int galaxy(double dx, double dy, double dz, double el, double t, int ca, int cb, double inten) {
			double nx = 0.35, ny = 0.80, nz = 0.50;
			double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
			double dist = (dx * nx + dy * ny + dz * nz) / len;
			double band = Math.exp(-(dist * dist) / 0.05);
			double clouds = fbm3(dx * 3.0, dy * 3.0, dz * 3.0 + t * 0.01, 4);
			int base = Theme.mix(0x02020A, Theme.mix(ca, 0x000000, 0.85), 0.4);
			int glow = Theme.mix(ca, cb, clouds);
			return Theme.mix(base, glow, clamp01(band * (0.35 + 0.9 * clouds) * inten));
		}

		private static int caustics(double dx, double dy, double dz, double el, double t, int ca, int cb, double inten) {
			double a1 = fbm3(dx * 3.0 + t * 0.08, dy * 3.0, dz * 3.0 - t * 0.06, 3);
			double a2 = fbm3(dx * 5.5 - t * 0.05 + 7, dy * 5.5 + 3, dz * 5.5 + t * 0.07, 3);
			double r1 = Math.pow(1.0 - Math.abs(2.0 * a1 - 1.0), 7.0);
			double r2 = Math.pow(1.0 - Math.abs(2.0 * a2 - 1.0), 7.0);
			double lines = clamp01(r1 + 0.6 * r2);
			int dark = Theme.mix(cb, 0x000000, 0.75);
			int bright = Theme.mix(ca, 0xFFFFFF, 0.5);
			return Theme.mix(dark, bright, clamp01(lines * (0.4 + 0.6 * inten)));
		}

		// ------------------------------------------------------------------
		// Gwiazdy (Galaxy, Black hole)
		// ------------------------------------------------------------------

		private static void drawStars(VertexConsumer vc, Matrix4f m, double t, int ca, int cb, double inten, int n) {
			double rr = R * 0.985;
			for (int i = 0; i < n; i++) {
				double az = hash01(i, 1) * Math.PI * 2.0;
				double el = Math.asin(0.07 + 0.93 * hash01(i, 2));
				double dx = Math.cos(el) * Math.sin(az);
				double dy = Math.sin(el);
				double dz = -Math.cos(el) * Math.cos(az);

				double tw = 0.55 + 0.45 * Math.sin(t * (1.0 + hash01(i, 3) * 2.5) + i);
				double a = (0.35 + 0.65 * hash01(i, 4)) * tw * Math.min(1.0, inten + 0.15);
				double size = rr * (0.0022 + 0.0035 * hash01(i, 5) * hash01(i, 5));
				double pick = hash01(i, 6);
				int rgb = pick < 0.6 ? 0xFFFFFF : (pick < 0.8 ? Theme.mix(ca, 0xFFFFFF, 0.5) : Theme.mix(cb, 0xFFFFFF, 0.5));

				// prawo = cross(d, up), gora = cross(prawo, d)
				double rx = -dz, rz = dx;
				double rl = Math.sqrt(rx * rx + rz * rz);
				if (rl < 1e-6) continue;
				rx /= rl;
				rz /= rl;
				double ux = -rz * dy;
				double uy = rz * dx - rx * dz;
				double uz = rx * dy;

				double cx = dx * rr, cy = dy * rr, cz = dz * rr;
				int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cbb = rgb & 0xFF;
				int ai = (int) (255 * clamp01(a));
				vc.vertex(m, (float) (cx - rx * size - ux * size), (float) (cy - uy * size), (float) (cz - rz * size - uz * size)).color(cr, cg, cbb, ai);
				vc.vertex(m, (float) (cx + rx * size - ux * size), (float) (cy - uy * size), (float) (cz + rz * size - uz * size)).color(cr, cg, cbb, ai);
				vc.vertex(m, (float) (cx + rx * size + ux * size), (float) (cy + uy * size), (float) (cz + rz * size + uz * size)).color(cr, cg, cbb, ai);
				vc.vertex(m, (float) (cx - rx * size + ux * size), (float) (cy + uy * size), (float) (cz - rz * size + uz * size)).color(cr, cg, cbb, ai);
			}
		}

		// ------------------------------------------------------------------
		// Black hole - siatka biegunowa wokol stalego kierunku na niebie
		// ------------------------------------------------------------------

		private static void drawBlackHole(VertexConsumer vc, Matrix4f m, double t, int ca, int cb, double inten) {
			double az0 = 0.6;
			double el0 = 0.75;
			double cxd = Math.cos(el0) * Math.sin(az0);
			double cyd = Math.sin(el0);
			double czd = -Math.cos(el0) * Math.cos(az0);

			double e1x = -czd, e1y = 0, e1z = cxd;
			double l = Math.sqrt(e1x * e1x + e1z * e1z);
			e1x /= l;
			e1z /= l;
			double e2x = e1y * czd - e1z * cyd;
			double e2y = e1z * cxd - e1x * czd;
			double e2z = e1x * cyd - e1y * cxd;

			int nr = 56;
			int ns = 96;
			double maxRad = 0.55;
			double tilt = 0.35;
			double ct = Math.cos(tilt), st = Math.sin(tilt);
			double rr = R * 0.99;

			int[][] cols = new int[nr + 1][ns + 1];
			float[][][] pos = new float[nr + 1][ns + 1][3];

			for (int i = 0; i <= nr; i++) {
				double rad = maxRad * Math.pow(i / (double) nr, 1.45);
				for (int j = 0; j <= ns; j++) {
					double phi = Math.PI * 2.0 * j / ns;
					double u = rad * Math.cos(phi);
					double w = rad * Math.sin(phi);

					double dx = cxd + (e1x * u + e2x * w);
					double dy = cyd + (e1y * u + e2y * w);
					double dz = czd + (e1z * u + e2z * w);
					double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
					dx /= dl;
					dy /= dl;
					dz /= dl;
					pos[i][j][0] = (float) (dx * rr);
					pos[i][j][1] = (float) (dy * rr);
					pos[i][j][2] = (float) (dz * rr);

					double uu = u * ct + w * st;
					double vv = -u * st + w * ct;
					double ax = Math.abs(uu);

					double horizon = 0.085;
					double photon = Math.exp(-sq((rad - 0.1) / 0.011));
					double thin = Math.exp(-sq(vv / 0.013)) * smooth(0.085, 0.14, ax) * Math.exp(-(ax - 0.09) * 5.0);
					double swirl = 0.7 + 0.3 * Math.sin(Math.atan2(vv, uu) * 3.0 + t * 2.2 + ax * 40.0);
					double hot = Math.max(photon * 0.9, thin * swirl);
					double glow = Math.exp(-rad * 9.0) * 0.55;

					int rgb = Theme.mix(ca, cb, 0.5 + 0.5 * Math.sin(Math.atan2(w, u) * 2.0 + t));
					rgb = Theme.mix(rgb, 0xFFFFFF, clamp01(hot * 0.85));
					double a = clamp01(hot + glow) * inten;
					if (rad < horizon) {
						a = 1.0;
						rgb = 0x000000;
					} else {
						double k = smooth(horizon, horizon + 0.01, rad);
						a = a * k + 1.0 * (1.0 - k);
						rgb = Theme.mix(0x000000, rgb, k);
					}
					a *= 1.0 - smooth(maxRad * 0.7, maxRad, rad);
					cols[i][j] = argb(a, rgb);
				}
			}

			for (int i = 0; i < nr; i++) {
				for (int j = 0; j < ns; j++) {
					if ((cols[i][j] >>> 24) + (cols[i + 1][j] >>> 24) + (cols[i + 1][j + 1] >>> 24) + (cols[i][j + 1] >>> 24) == 0) continue;
					pv(vc, m, pos, cols, i, j);
					pv(vc, m, pos, cols, i + 1, j);
					pv(vc, m, pos, cols, i + 1, j + 1);
					pv(vc, m, pos, cols, i, j + 1);
				}
			}
		}

		private static void pv(VertexConsumer vc, Matrix4f m, float[][][] pos, int[][] cols, int i, int j) {
			int c = cols[i][j];
			float[] p = pos[i][j];
			vc.vertex(m, p[0], p[1], p[2]).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
		}

		// ------------------------------------------------------------------
		// Matematyka pomocnicza
		// ------------------------------------------------------------------

		private static int argb(double alpha, int rgb) {
			int a = (int) Math.round(255.0 * clamp01(alpha));
			return (a << 24) | (rgb & 0xFFFFFF);
		}

		private static double clamp01(double v) {
			return v < 0 ? 0 : (v > 1 ? 1 : v);
		}

		private static double sq(double v) {
			return v * v;
		}

		private static double smooth(double e0, double e1, double x) {
			double t = clamp01((x - e0) / (e1 - e0));
			return t * t * (3.0 - 2.0 * t);
		}

		private static double hash01(int i, int salt) {
			int h = i * 374761393 + salt * 668265263;
			h = (h ^ (h >>> 13)) * 1274126177;
			h ^= h >>> 16;
			return (h & 0xFFFFFF) / (double) 0x1000000;
		}

		private static double lat(int x, int y, int z) {
			int h = x * 374761393 ^ y * 668265263 ^ z * 1274126177;
			h = (h ^ (h >>> 13)) * 1103515245;
			h ^= h >>> 16;
			return (h & 0xFFFF) / 65535.0;
		}

		/** Szum wartosciowy 3D (0..1). */
		private static double noise3(double x, double y, double z) {
			int xi = MathHelper.floor(x), yi = MathHelper.floor(y), zi = MathHelper.floor(z);
			double fx = x - xi, fy = y - yi, fz = z - zi;
			fx = fx * fx * (3 - 2 * fx);
			fy = fy * fy * (3 - 2 * fy);
			fz = fz * fz * (3 - 2 * fz);
			double c00 = lerp(lat(xi, yi, zi), lat(xi + 1, yi, zi), fx);
			double c10 = lerp(lat(xi, yi + 1, zi), lat(xi + 1, yi + 1, zi), fx);
			double c01 = lerp(lat(xi, yi, zi + 1), lat(xi + 1, yi, zi + 1), fx);
			double c11 = lerp(lat(xi, yi + 1, zi + 1), lat(xi + 1, yi + 1, zi + 1), fx);
			return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
		}

		private static double fbm3(double x, double y, double z, int oct) {
			double sum = 0, amp = 0.5, norm = 0;
			for (int i = 0; i < oct; i++) {
				sum += amp * noise3(x, y, z);
				norm += amp;
				x *= 2.03;
				y *= 2.03;
				z *= 2.03;
				amp *= 0.5;
			}
			return sum / norm;
		}

		private static double lerp(double a, double b, double t) {
			return a + (b - a) * t;
		}
	}

	/**
	 * Pety (tylko u Ciebie na ekranie): nietoperz, kot, kura, pies.
	 * Chodza za Toba (nietoperz lata dookola). Modele to kolorowe klocki rysowane wlasna geometria.
	 */
	static final class PetRenderer {
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

	/** HUD: mini panel celu, pasek na gorze ekranu (zegar, muzyka, FPS, ping) i mini odtwarzacz. */
	static final class Hud {
		private Hud() {}

		private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
		private static final Locale PL = Locale.forLanguageTag("pl-PL");

		public static void register() {
			HudRenderCallback.EVENT.register((ctx, tickCounter) -> render(ctx));
		}

		private static void render(DrawContext ctx) {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
			if (mc.currentScreen != null) return;

			VisualConfig cfg = VisualConfig.get();
			Theme theme = cfg.theme();
			TextRenderer tr = mc.textRenderer;
			double time = System.currentTimeMillis() / 1000.0;
			int accent = 0xFF000000 | theme.ui((time * 0.1) % 1.0);

			if (cfg.topBar) drawTopBar(ctx, mc, tr, cfg, theme, accent, time);
			if (cfg.musicHud) {
				Ui.musicCard(ctx, tr, 8, 8, 196, 38, theme, 1.0, time, cfg.rounded, cfg.music);
			}
			if (cfg.targetHud) drawTargetPanel(ctx, mc, tr, cfg, theme, accent);
		}

		// ------------------------------------------------------------------
		// Panel celu
		// ------------------------------------------------------------------

		private static void drawTargetPanel(DrawContext ctx, MinecraftClient mc, TextRenderer tr,
											VisualConfig cfg, Theme theme, int accent) {
			LivingEntity target = TargetRenderer.hudTarget();
			if (target == null) return;
			double alpha = TargetRenderer.hudFade();
			if (alpha <= 0.02) return;

			int w = 176, h = 42;
			int x = (ctx.getScaledWindowWidth() - w) / 2;
			int y = ctx.getScaledWindowHeight() / 2 + 26;
			int r = cfg.rounded ? 6 : 0;

			Ui.rrect(ctx, x - 1, y - 1, w + 2, h + 2, Ui.fadeArgb(accent, 0.45 * alpha), r);
			Ui.rrect(ctx, x, y, w, h, Ui.a(theme.panel, 0.92 * alpha), r);

			// glowa
			int hs = 28;
			int hx = x + 7, hy = y + 6;
			if (target instanceof AbstractClientPlayerEntity ap) {
				PlayerSkinDrawer.draw(ctx, ap.getSkinTextures(), hx, hy, hs);
			} else {
				Ui.rrect(ctx, hx, hy, hs, hs, Ui.a(0x2A2A38, alpha), cfg.rounded ? 4 : 0);
				String letter = target.getName().getString().isEmpty() ? "?" : target.getName().getString().substring(0, 1).toUpperCase();
				ctx.drawCenteredTextWithShadow(tr, net.minecraft.text.Text.literal(letter), hx + hs / 2, hy + hs / 2 - 4, Ui.fadeArgb(Ui.COL_TEXT, alpha));
			}

			// nick i zdrowie
			boolean player = target instanceof net.minecraft.entity.player.PlayerEntity;
			String tag = player ? "Player" : "Mob";
			int tagW = tr.getWidth(tag) + 12;
			int tx = hx + hs + 8;
			int nameMax = w - (tx - x) - tagW - 14;
			Ui.text(ctx, tr, tr.trimToWidth(target.getName().getString(), Math.max(20, nameMax)), tx, y + 8, Ui.fadeArgb(Ui.COL_TEXT, alpha));

			float hp = target.getHealth();
			float max = Math.max(1f, target.getMaxHealth());
			Ui.text(ctx, tr, String.format(PL, "%.1f", hp), tx, y + 20, Ui.fadeArgb(Ui.COL_DIM, alpha));

			// plakietka
			int bx = x + w - tagW - 7;
			Ui.rrect(ctx, bx, y + 7, tagW, 14, Ui.a(0x2A2A38, 0.9 * alpha), cfg.rounded ? 6 : 0);
			Ui.text(ctx, tr, tag, bx + 6, y + 10, Ui.fadeArgb(Ui.COL_OFF, alpha));

			// pasek zycia (gradient motywu)
			int barX = x + 7, barY = y + h - 8, barW = w - 14;
			ctx.fill(barX, barY, barX + barW, barY + 3, Ui.a(0xFFFFFF, 0.10 * alpha));
			int fill = (int) Math.round(barW * Math.max(0f, Math.min(1f, hp / max)));
			for (int i = 0; i < fill; i++) {
				int c = 0xFF000000 | theme.uiLinear(i / (double) Math.max(1, barW - 1));
				ctx.fill(barX + i, barY, barX + i + 1, barY + 3, Ui.fadeArgb(c, alpha));
			}
		}

		// ------------------------------------------------------------------
		// Pasek na gorze
		// ------------------------------------------------------------------

		private static void drawTopBar(DrawContext ctx, MinecraftClient mc, TextRenderer tr, VisualConfig cfg,
									   Theme theme, int accent, double time) {
			String clock = LocalTime.now().format(CLOCK);
			boolean showMusic = cfg.music && MediaControl.available && !MediaControl.title.isEmpty();
			String song = showMusic ? tr.trimToWidth(MediaControl.title, 96) : "";
			int fps = mc.getCurrentFps();

			int ping = 0;
			if (mc.getNetworkHandler() != null) {
				PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
				if (entry != null) ping = entry.getLatency();
			}
			int bars = ping < 60 ? 4 : (ping < 120 ? 3 : (ping < 220 ? 2 : 1));
			String pingText = String.valueOf(ping);

			int pad = 10;
			int gap = 10;
			int w = pad + tr.getWidth(clock);
			if (showMusic) {
				w += gap + 14 + 4 + tr.getWidth(song) + 6 + 17;   // ikona + tytul + korektor
			}
			w += gap + tr.getWidth(fps + " fps");
			w += gap + tr.getWidth(pingText) + 4 + 11;
			w += pad;

			int h = 20;
			int x = (ctx.getScaledWindowWidth() - w) / 2;
			int y = 6;
			int r = cfg.rounded ? 8 : 0;

			Ui.rrect(ctx, x - 1, y - 1, w + 2, h + 2, Ui.fadeArgb(accent, 0.40), r);
			Ui.rrect(ctx, x, y, w, h, Ui.a(theme.panel, 0.90), r);

			int cx = x + pad;
			int ty = y + (h - 8) / 2;
			Ui.text(ctx, tr, clock, cx, ty, Ui.COL_TEXT);
			cx += tr.getWidth(clock) + gap;

			if (showMusic) {
				// mala okladka
				for (int i = 0; i < 12; i++) {
					ctx.fill(cx, y + 4 + i, cx + 12, y + 5 + i,
							0xFF000000 | theme.uiLinear(i / 11.0));
				}
				cx += 12 + 4;
				Ui.text(ctx, tr, song, cx, ty, Ui.COL_TEXT);
				cx += tr.getWidth(song) + 6;
				Ui.equalizer(ctx, cx, y + h - 5, 4, 3, 10, accent, MediaControl.playing, time);
				cx += 17 + gap;
			}

			Ui.text(ctx, tr, fps + " fps", cx, ty, Ui.COL_OFF);
			cx += tr.getWidth(fps + " fps") + gap;
			Ui.text(ctx, tr, pingText, cx, ty, Ui.COL_TEXT);
			cx += tr.getWidth(pingText) + 4;
			Ui.signal(ctx, cx, y + h - 5, bars, accent, 0x55FFFFFF);
		}
	}

	/**
	 * Menu moda: pasek boczny z zakladkami (Themes / Target / Extras) i tresc po prawej.
	 * Wszystko rysowane recznie. Kolory pochodza z wybranego motywu.
	 */
	static class VisualsScreen extends Screen {
		private static final int W = 420;
		private static final int H = 254;
		private static final int SIDE_W = 104;

		private static final int COL_TEXT = 0xFFEDEDF5;
		private static final int COL_OFF = 0xFFA6A6B8;
		private static final int COL_DIM = 0xFF6F6F86;
		private static final int COL_TRACK = 0xFF2E2E3A;

		private static final String[] TABS = {"Themes", "Target", "Extras", "Sky", "Pets", "HUD"};
		private static final String[] SUBTITLES = {
				"Theme (accent + panel tint)",
				"Effects around the entity you look at",
				"Extra effects in the world",
				"Replaces the vanilla sky (Overworld)",
				"A pet that follows you",
				"Panels on the screen and music"
		};

		/** Ostatnio otwarta zakladka (pamietana miedzy otwarciami menu). */
		private static int currentTab = 0;

		private final VisualConfig cfg = VisualConfig.get();
		private final List<Item> items = new ArrayList<>();
		private Item dragging;
		private int pendingTab = -1;
		private int px, py;   // lewy gorny rog panelu
		private int cx, cw;   // obszar tresci (x i szerokosc)

		public VisualsScreen() {
			super(Text.literal("Visuals"));
		}

		@Override
		protected void init() {
			px = (width - W) / 2;
			py = Math.max(2, (height - H) / 2);
			cx = px + 8 + SIDE_W + 14;
			cw = W - (8 + SIDE_W + 14) - 14;
			rebuild();
		}

		// ------------------------------------------------------------------
		// Budowanie zakladek
		// ------------------------------------------------------------------

		private void rebuild() {
			items.clear();
			dragging = null;

			for (int i = 0; i < TABS.length; i++) {
				items.add(new TabButton(px + 14, py + 58 + i * 24, SIDE_W - 12, 20, i, TABS[i]));
			}

			switch (currentTab) {
				case 0 -> buildThemes();
				case 1 -> buildTarget();
				case 2 -> buildExtras();
				case 3 -> buildSky();
				case 4 -> buildPets();
				default -> buildHud();
			}
		}

		private void buildHud() {
			items.add(new Toggle(cx, py + 54, cw, 20, "Target panel", () -> cfg.targetHud, v -> cfg.targetHud = v));
			items.add(new Toggle(cx, py + 76, cw, 20, "Top bar (clock, fps, ping)", () -> cfg.topBar, v -> cfg.topBar = v));
			items.add(new Toggle(cx, py + 98, cw, 20, "Media (Spotify)", () -> cfg.music, v -> cfg.music = v));
			items.add(new Toggle(cx, py + 120, cw, 20, "Music player on screen", () -> cfg.musicHud, v -> cfg.musicHud = v));
			items.add(new MusicCard(cx, py + 152, cw, 40));
		}

		private void buildSky() {
			String[] names = {"Off", "Aurora", "Nebula", "Galaxy", "Caustics", "Black hole"};
			int colW = (cw - 8) / 2;
			for (int i = 0; i < names.length; i++) {
				int col = i % 2;
				int row = i / 2;
				final int value = i;
				items.add(new Choice(cx + col * (colW + 8), py + 54 + row * 26, colW, 22, names[i],
						() -> cfg.sky, v -> cfg.sky = v, value));
			}
			items.add(new Slider(cx, py + 138, cw, 24, "Intensity", 0.2, 1.0, 2, 100.0, 0, "%",
					() -> cfg.skyIntensity, v -> cfg.skyIntensity = (float) v));
			items.add(new Slider(cx, py + 166, cw, 24, "Speed", 0.2, 3.0, 1, 1.0, 1, "x",
					() -> cfg.skySpeed, v -> cfg.skySpeed = (float) v));
		}

		private void buildPets() {
			items.add(new Toggle(cx, py + 54, cw, 20, "Pet enabled", () -> cfg.petEnabled, v -> cfg.petEnabled = v));
			String[] names = {"Bat", "Cat", "Chicken", "Dog"};
			int colW = (cw - 8) / 2;
			for (int i = 0; i < names.length; i++) {
				int col = i % 2;
				int row = i / 2;
				final int value = i;
				items.add(new Choice(cx + col * (colW + 8), py + 84 + row * 26, colW, 22, names[i],
						() -> cfg.petType, v -> cfg.petType = v, value));
			}
			items.add(new Slider(cx, py + 144, cw, 24, "Size", 0.6, 2.0, 1, 1.0, 1, "x",
					() -> cfg.petScale, v -> cfg.petScale = (float) v));
		}

		private void buildThemes() {
			int cardW = (cw - 16) / 3;
			int count = Theme.values().length;
			for (int i = 0; i < count; i++) {
				items.add(new ThemeCard(cx + (i % 3) * (cardW + 8), py + 54 + (i / 3) * 40, cardW, 34, i));
			}
			items.add(new Slider(cx, py + 176, cw, 24, "Panel opacity", 0.35, 1.0, 2, 100.0, 0, "%",
					() -> cfg.panelAlpha, v -> cfg.panelAlpha = (float) v));
			items.add(new Toggle(cx, py + 202, cw, 20, "Rounded corners", () -> cfg.rounded, v -> cfg.rounded = v));
			items.add(new Toggle(cx, py + 224, cw, 20, "Animate colors", () -> cfg.animateColors, v -> cfg.animateColors = v));
		}

		private void buildTarget() {
			int colW = (cw - 12) / 2;
			int lx = cx;
			int rx = cx + colW + 12;
			int y = py + 54;
			items.add(new Toggle(lx, y, colW, 20, "Enabled", () -> cfg.targetEnabled, v -> cfg.targetEnabled = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Box", () -> cfg.box, v -> cfg.box = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Ring", () -> cfg.ring, v -> cfg.ring = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Scanner", () -> cfg.scanner, v -> cfg.scanner = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Orbit", () -> cfg.orbit, v -> cfg.orbit = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Ghost (wind)", () -> cfg.ghost, v -> cfg.ghost = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Spin circle", () -> cfg.spinCircle, v -> cfg.spinCircle = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "China hat", () -> cfg.chinaHat, v -> cfg.chinaHat = v));
			items.add(new Toggle(rx, py + 142, colW, 20, "Box glow", () -> cfg.glow, v -> cfg.glow = v));

			items.add(new Slider(rx, py + 54, colW, 24, "Range", 4, 64, 0, 1.0, 0, "",
					() -> cfg.range, v -> cfg.range = (float) v));
			items.add(new Slider(rx, py + 82, colW, 24, "Speed", 0.2, 3.0, 1, 1.0, 1, "x",
					() -> cfg.speed, v -> cfg.speed = (float) v));
			items.add(new Segment(rx, py + 114, colW, 20, "All", "Players",
					() -> cfg.playersOnly, v -> cfg.playersOnly = v));
		}

		private void buildExtras() {
			int colW = (cw - 12) / 2;
			int lx = cx;
			int rx = cx + colW + 12;
			int y = py + 54;
			items.add(new Toggle(lx, y, colW, 20, "Jump circle", () -> cfg.jumpCircle, v -> cfg.jumpCircle = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Trails", () -> cfg.trails, v -> cfg.trails = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Blizzard", () -> cfg.blizzard, v -> cfg.blizzard = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Fireflies", () -> cfg.fireflies, v -> cfg.fireflies = v));
			y += 22;
			items.add(new Toggle(lx, y, colW, 20, "Rain", () -> cfg.rain, v -> cfg.rain = v));

			items.add(new Slider(rx, py + 54, colW, 24, "Trail length", 0.5, 3.0, 1, 1.0, 1, "s",
					() -> cfg.trailSeconds, v -> cfg.trailSeconds = (float) v));
			items.add(new Slider(rx, py + 82, colW, 24, "Snow density", 0.3, 2.0, 1, 1.0, 1, "x",
					() -> cfg.blizzardDensity, v -> cfg.blizzardDensity = (float) v));
			items.add(new Slider(rx, py + 110, colW, 24, "Fireflies", 4, 80, 0, 1.0, 0, "",
					() -> cfg.fireflyCount, v -> cfg.fireflyCount = (float) v));
			items.add(new Slider(rx, py + 138, colW, 24, "Rain density", 0.3, 2.0, 1, 1.0, 1, "x",
					() -> cfg.rainDensity, v -> cfg.rainDensity = (float) v));
		}

		// ------------------------------------------------------------------
		// Rysowanie
		// ------------------------------------------------------------------

		@Override
		public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
			if (pendingTab >= 0) {
				currentTab = pendingTab;
				pendingTab = -1;
				rebuild();
			}

			Theme theme = cfg.theme();
			int accent = accent();

			// Przyciemnienie gry w tle
			ctx.fill(0, 0, width, height, 0x88000000);

			// Ramka i panel
			int alphaByte = Math.max(0, Math.min(255, (int) (cfg.panelAlpha * 255f)));
			rrect(ctx, px - 1, py - 1, W + 2, H + 2, (accent & 0xFFFFFF) | 0x88000000, rad(5));
			rrect(ctx, px, py, W, H, (alphaByte << 24) | theme.panel, rad(4));

			// Pasek boczny
			rrect(ctx, px + 8, py + 8, SIDE_W, H - 16, 0x66000000, rad(4));
			rrect(ctx, px + 16, py + 16, 26, 26, accent, rad(5));
			ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("V"), px + 29, py + 25, 0xFF0B0B10);
			text(ctx, "VISUAL", px + 48, py + 18, COL_TEXT);
			text(ctx, "TARGET v1.1", px + 48, py + 29, COL_DIM);

			String keyName = VisualTargetClient.openMenuKey != null
					? VisualTargetClient.openMenuKey.getBoundKeyLocalizedText().getString()
					: "key";
			text(ctx, "Close: " + keyName, px + 16, py + H - 26, COL_DIM);

			// Naglowek tresci
			text(ctx, TABS[currentTab], cx, py + 16, COL_TEXT);
			ctx.fill(cx, py + 28, cx + 22, py + 30, accent);
			text(ctx, SUBTITLES[currentTab], cx, py + 36, COL_DIM);

			for (Item it : items) {
				it.render(ctx, mouseX, mouseY);
			}

			if (currentTab == 5) {
				text(ctx, "Prev / play / next keys: Controls > Visual Target", cx, py + 198, COL_DIM);
				if (!MediaControl.supported()) {
					text(ctx, "Media control works on Windows only", cx, py + 210, 0xFFE07070);
				}
			}
		}

		// ------------------------------------------------------------------
		// Sterowanie
		// ------------------------------------------------------------------

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button == 0) {
				for (Item it : items) {
					if (it.hit(mouseX, mouseY)) {
						it.click(mouseX, mouseY);
						if (it.draggable()) {
							dragging = it;
						}
						return true;
					}
				}
			}
			return super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
			if (dragging != null) {
				dragging.drag(mouseX, mouseY);
				return true;
			}
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}

		@Override
		public boolean mouseReleased(double mouseX, double mouseY, int button) {
			if (dragging != null) {
				dragging = null;
				cfg.save();
				return true;
			}
			return super.mouseReleased(mouseX, mouseY, button);
		}

		@Override
		public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
			// Ten sam klawisz co otwiera menu, tez je zamyka
			if (VisualTargetClient.openMenuKey != null && VisualTargetClient.openMenuKey.matchesKey(keyCode, scanCode)) {
				close();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}

		@Override
		public boolean shouldPause() {
			return false;
		}

		@Override
		public void close() {
			cfg.save();
			super.close();
		}

		// ------------------------------------------------------------------
		// Pomocnicy rysowania
		// ------------------------------------------------------------------

		/** Kolor akcentu menu (w gradiencie motywu, plynie w czasie). */
		private int accent() {
			double ph = cfg.animateColors ? (System.currentTimeMillis() % 6000L) / 6000.0 : 0.0;
			return 0xFF000000 | cfg.theme().ui(ph);
		}

		/** Promien zaokraglenia (0 gdy wylaczone zaokraglone rogi). */
		private int rad(int r) {
			return cfg.rounded ? r : 0;
		}

		/** Prostokat z zaokraglonymi rogami (promien r). */
		private static void rrect(DrawContext ctx, int x, int y, int w, int h, int color, int r) {
			if (r <= 0 || w < 2 * r + 1 || h < 2 * r + 1) {
				ctx.fill(x, y, x + w, y + h, color);
				return;
			}
			ctx.fill(x, y + r, x + w, y + h - r, color);
			for (int i = 0; i < r; i++) {
				double dy = r - i - 0.5;
				int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
				ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
				ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
			}
		}

		private void text(DrawContext ctx, String s, int x, int y, int color) {
			ctx.drawTextWithShadow(textRenderer, Text.literal(s), x, y, color);
		}

		// ------------------------------------------------------------------
		// Elementy menu
		// ------------------------------------------------------------------

		private abstract class Item {
			final int x, y, w, h;

			Item(int x, int y, int w, int h) {
				this.x = x;
				this.y = y;
				this.w = w;
				this.h = h;
			}

			boolean hit(double mx, double my) {
				return mx >= x && mx < x + w && my >= y && my < y + h;
			}

			abstract void render(DrawContext ctx, int mx, int my);

			void click(double mx, double my) {}

			void drag(double mx, double my) {}

			boolean draggable() {
				return false;
			}
		}

		/** Mini odtwarzacz: klikalne przyciski poprzedni / graj-pauza / nastepny. */
		private class MusicCard extends Item {
			MusicCard(int x, int y, int w, int h) {
				super(x, y, w, h);
			}

			@Override
			void render(DrawContext ctx, int mx, int my) {
				Ui.musicCard(ctx, textRenderer, x, y, w, h, cfg.theme(), 1.0,
						System.currentTimeMillis() / 1000.0, cfg.rounded, cfg.music);
			}

			@Override
			void click(double mx, double my) {
				if (!cfg.music) return;
				switch (Ui.musicHit(x, y, w, h, mx, my)) {
					case 1 -> MediaControl.send("prev");
					case 2 -> MediaControl.send("toggle");
					case 3 -> MediaControl.send("next");
					default -> { }
				}
			}
		}

		/** Zakladka w pasku bocznym. */
		private class TabButton extends Item {
			private final int index;
			private final String label;

			TabButton(int x, int y, int w, int h, int index, String label) {
				super(x, y, w, h);
				this.index = index;
				this.label = label;
			}

			@Override
			void render(DrawContext ctx, int mx, int my) {
				boolean sel = index == currentTab;
				int accent = accent();
				if (sel) {
					rrect(ctx, x, y, w, h, (accent & 0xFFFFFF) | 0x44000000, rad(4));
					rrect(ctx, x - 4, y + 4, 2, h - 8, accent, rad(1));
				} else if (hit(mx, my)) {
					rrect(ctx, x, y, w, h, 0x22FFFFFF, rad(4));
				}
				text(ctx, label, x + 10, y + (h - 8) / 2, sel ? COL_TEXT : COL_OFF);
			}

			@Override
			void click(double mx, double my) {
				if (index != currentTab) pendingTab = index;
			}
		}

		/** Karta motywu z podgladem gradientu. */
		private class ThemeCard extends Item {
			private final int index;

			ThemeCard(int x, int y, int w, int h, int index) {
				super(x, y, w, h);
				this.index = index;
			}

			@Override
			void render(DrawContext ctx, int mx, int my) {
				Theme th = Theme.byIndex(index);
				boolean sel = cfg.theme == index;
				int accent = accent();

				if (sel) {
					rrect(ctx, x - 1, y - 1, w + 2, h + 2, accent, rad(5));
				}
				rrect(ctx, x, y, w, h, hit(mx, my) ? 0xFF22222E : 0xFF171720, rad(4));

				// gradient w pastylce
				int sx = x + 5;
				int sy = y + 5;
				int sw = w - 10;
				int sh = 10;
				for (int i = 0; i < sw; i++) {
					ctx.fill(sx + i, sy, sx + i + 1, sy + sh, 0xFF000000 | th.uiLinear(i / (double) (sw - 1)));
				}
				text(ctx, th.label, x + 5, y + 20, sel ? COL_TEXT : COL_OFF);
			}

			@Override
			void click(double mx, double my) {
				cfg.theme = index;
				cfg.save();
			}
		}

		/** Przelacznik ON/OFF (pastylka z animacja). */
		private class Toggle extends Item {
			private final String label;
			private final BooleanSupplier getter;
			private final Consumer<Boolean> setter;
			private float anim = -1f;

			Toggle(int x, int y, int w, int h, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
				super(x, y, w, h);
				this.label = label;
				this.getter = getter;
				this.setter = setter;
			}

			@Override
			void render(DrawContext ctx, int mx, int my) {
				boolean on = getter.getAsBoolean();
				if (anim < 0f) anim = on ? 1f : 0f;
				anim += ((on ? 1f : 0f) - anim) * 0.35f;

				if (hit(mx, my)) {
					rrect(ctx, x, y, w, h, 0x18FFFFFF, rad(3));
				}
				text(ctx, label, x + 6, y + (h - 8) / 2, on ? COL_TEXT : COL_OFF);

				int pw = 26;
				int ph = 12;
				int pillX = x + w - pw - 4;
				int pillY = y + (h - ph) / 2;
				int accent = accent();
				int track = 0xFF000000 | Theme.mix(COL_TRACK & 0xFFFFFF, accent & 0xFFFFFF, anim);
				rrect(ctx, pillX, pillY, pw, ph, track, rad(5));
				int knobX = pillX + 2 + Math.round(anim * (pw - 4 - 8));
				rrect(ctx, knobX, pillY + 2, 8, 8, 0xFFFFFFFF, rad(3));
			}

			@Override
			void click(double mx, double my) {
				setter.accept(!getter.getAsBoolean());
				cfg.save();
			}
		}

		/** Suwak liczbowy z podpisem i wartoscia. */
		private class Slider extends Item {
			private final String label;
			private final double min, max;
			private final int decimals;
			private final double dispMul;
			private final int dispDec;
			private final String suffix;
			private final DoubleSupplier getter;
			private final DoubleConsumer setter;

			Slider(int x, int y, int w, int h, String label, double min, double max, int decimals,
				   double dispMul, int dispDec, String suffix, DoubleSupplier getter, DoubleConsumer setter) {
				super(x, y, w, h);
				this.label = label;
				this.min = min;
				this.max = max;
				this.decimals = decimals;
				this.dispMul = dispMul;
				this.dispDec = dispDec;
				this.suffix = suffix;
				this.getter = getter;
				this.setter = setter;
			}

			@Override
			void render(DrawContext ctx, int mx, int my) {
				double v = MathHelper.clamp(getter.getAsDouble(), min, max);
				double f = (v - min) / (max - min);
				int accent = accent();

				if (hit(mx, my) || dragging == this) {
					rrect(ctx, x, y, w, h, 0x18FFFFFF, rad(3));
				}
				text(ctx, label, x + 6, y + 3, COL_TEXT);
				String value = String.format(Locale.ROOT, "%." + dispDec + "f", v * dispMul) + suffix;
				text(ctx, value, x + w - 6 - textRenderer.getWidth(value), y + 3, COL_OFF);

				int tx = x + 6;
				int tw = w - 12;
				int ty = y + h - 8;
				rrect(ctx, tx, ty, tw, 4, 0xFF000000 | COL_TRACK, rad(2));
				int filled = (int) Math.round(tw * f);
				if (filled > 0) {
					rrect(ctx, tx, ty, filled, 4, accent, rad(2));
				}
				rrect(ctx, tx + filled - 4, ty - 2, 8, 8, 0xFFFFFFFF, rad(4));
			}

			@Override
			void click(double mx, double my) {
				setFromMouse(mx);
			}

			@Override
			void drag(double mx, double my) {
				setFromMouse(mx);
			}

			@Override
			boolean draggable() {
				return true;
			}

			private void setFromMouse(double mx) {
				double f = MathHelper.clamp((mx - (x + 6)) / (double) (w - 12), 0.0, 1.0);
				double v = min + f * (max - min);
				double scale = Math.pow(10, decimals);
				setter.accept(Math.round(v * scale) / scale);
			}
		}

		/** Wybor jednej z dwoch opcji (np. wszyscy / tylko gracze). */
		private class Segment extends Item {
			private final String first, second;
			private final BooleanSupplier getter;
			private final Consumer<Boolean> setter;

			Segment(int x, int y, int w, int h, String first, String second,
					BooleanSupplier getter, Consumer<Boolean> setter) {
				super(x, y, w, h);
				this.first = first;
				this.second = second;
				this.getter = getter;
				this.setter = setter;
			}

			@Override
			void render(DrawContext ctx, int mx, int my) {
				boolean sec = getter.getAsBoolean();
				int half = w / 2;
				int accent = accent();

				rrect(ctx, x, y, w, h, 0xFF1C1C25, rad(4));
				rrect(ctx, x + (sec ? half : 0) + 1, y + 1, half - 2, h - 2, (accent & 0xFFFFFF) | 0x77000000, rad(3));

				ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(first),
						x + half / 2, y + (h - 8) / 2, sec ? COL_OFF : COL_TEXT);
				ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(second),
						x + half + half / 2, y + (h - 8) / 2, sec ? COL_TEXT : COL_OFF);
			}

			@Override
			void click(double mx, double my) {
				setter.accept(mx >= x + w / 2);
				cfg.save();
			}
		}

		/** Przycisk wyboru jednej wartosci z listy (np. rodzaj nieba albo peta). */
		private class Choice extends Item {
			private final String label;
			private final IntSupplier getter;
			private final IntConsumer setter;
			private final int value;

			Choice(int x, int y, int w, int h, String label, IntSupplier getter, IntConsumer setter, int value) {
				super(x, y, w, h);
				this.label = label;
				this.getter = getter;
				this.setter = setter;
				this.value = value;
			}

			@Override
			void render(DrawContext ctx, int mx, int my) {
				boolean sel = getter.getAsInt() == value;
				int accent = accent();
				if (sel) {
					rrect(ctx, x - 1, y - 1, w + 2, h + 2, accent, rad(5));
				}
				rrect(ctx, x, y, w, h, sel ? 0xFF1B1B26 : (hit(mx, my) ? 0xFF22222E : 0xFF171720), rad(4));
				ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label),
						x + w / 2, y + (h - 8) / 2, sel ? COL_TEXT : COL_OFF);
			}

			@Override
			void click(double mx, double my) {
				setter.accept(value);
				cfg.save();
			}
		}
	}
}
