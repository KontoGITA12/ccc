package com.visualtarget;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ustawienia moda. Zapisywane w config/visualtarget.json.
 */
public class VisualConfig {
	// --- Efekty ---
	public boolean enabled = true;
	public boolean box = true;
	public boolean ring = true;
	public boolean scanner = false;
	public boolean orbit = false;
	public boolean ghost = true;   // wiatr krazacy wokol celu

	// --- Opcje ---
	public boolean glow = true;    // miekka poswiata na liniach
	public boolean playersOnly = false;
	public float range = 24f;

	// --- Wyglad ---
	public boolean rainbow = false;
	public float colorPos = 0.62f; // pozycja na suwaku koloru (0 = biel, potem cala teczka)
	public float speed = 1f;

	// -------------------------------------------------------------

	/** Zamienia pozycje na suwaku (0..1) na kolor RGB. Poczatek suwaka to biel. */
	public static int colorFromPos(float v) {
		if (v < 0.06f) return 0xFFFFFF;
		float hue = (v - 0.06f) / 0.94f * 0.95f;
		return MathHelper.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
	}

	/** Kolor do pokazania w menu (w trybie rainbow zmienia sie w czasie). */
	public int displayRgb() {
		if (rainbow) {
			float hue = (System.currentTimeMillis() % 5000L) / 5000f;
			return MathHelper.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
		}
		return colorFromPos(colorPos);
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
		colorPos = MathHelper.clamp(colorPos, 0f, 1f);
		range = MathHelper.clamp(range, 4f, 64f);
		speed = MathHelper.clamp(speed, 0.2f, 3f);
	}
}
