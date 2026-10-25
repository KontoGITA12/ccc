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
