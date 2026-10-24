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
	public boolean scanner = true;
	public boolean orbit = false;

	// --- Cele ---
	public boolean playersOnly = false;
	public float range = 24f;

	// --- Wyglad ---
	public boolean rainbow = false;
	public int red = 255;
	public int green = 70;
	public int blue = 70;
	public float speed = 1f;

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
		red = MathHelper.clamp(red, 0, 255);
		green = MathHelper.clamp(green, 0, 255);
		blue = MathHelper.clamp(blue, 0, 255);
		range = MathHelper.clamp(range, 4f, 64f);
		speed = MathHelper.clamp(speed, 0.2f, 3f);
	}
}
