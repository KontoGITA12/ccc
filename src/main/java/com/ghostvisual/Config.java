package com.ghostvisual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
	// wyglad menu
	public int theme = 1;
	public float panelAlpha = 0.88f;
	public boolean rounded = true;
	public boolean animateColors = true;
	public boolean targetPanel = true;

	// cel
	public boolean targetEnabled = true;
	public boolean playersOnly = false;
	public float range = 24f;
	public float speed = 1f;
	public boolean box = false;
	public boolean ring = true;
	public boolean scanner = true;
	public boolean orbit = true;
	public boolean glow = true;

	// efekty
	public boolean jumpCircle = true;
	public boolean trails = false;
	public float trailSeconds = 2f;
	public boolean rain = false;
	public float rainDensity = 0.5f;
	public boolean blizzard = false;
	public float blizzardDensity = 0.5f;
	public boolean fireflies = false;
	public float fireflyCount = 0.5f;
	public boolean sky = false;
	public float skyIntensity = 0.5f;
	public float skySpeed = 0.5f;

	// pety
	public boolean petEnabled = false;
	public int petType = 0;
	public float petScale = 1f;

	// china hat: 0 = wylaczony, 1 = tylko ja, 2 = wszyscy
	public int chinaHat = 0;
	public float chinaHatScale = 1f;

	// muzyka
	public boolean musicPanel = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config instance = new Config();

	public static Config get() {
		return instance;
	}

	public Theme theme() {
		return Theme.byIndex(theme);
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("ghostvisual.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (var reader = Files.newBufferedReader(p)) {
				Config c = GSON.fromJson(reader, Config.class);
				if (c != null) {
					instance = c;
				}
			} catch (Exception e) {
				GhostVisualClient.LOGGER.warn("Nie udalo sie wczytac configu", e);
			}
		}
		instance.clamp();
		instance.save();
	}

	public void save() {
		clamp();
		try (var writer = Files.newBufferedWriter(path())) {
			GSON.toJson(this, writer);
		} catch (IOException e) {
			GhostVisualClient.LOGGER.warn("Nie udalo sie zapisac configu", e);
		}
	}

	private static float c(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private void clamp() {
		theme = Math.max(0, Math.min(Theme.values().length - 1, theme));
		panelAlpha = c(panelAlpha, 0.3f, 1f);
		range = c(range, 3f, 64f);
		speed = c(speed, 0.2f, 3f);
		trailSeconds = c(trailSeconds, 0.5f, 6f);
		rainDensity = c(rainDensity, 0f, 1f);
		blizzardDensity = c(blizzardDensity, 0f, 1f);
		fireflyCount = c(fireflyCount, 0f, 1f);
		skyIntensity = c(skyIntensity, 0f, 1f);
		skySpeed = c(skySpeed, 0f, 1f);
		petType = Math.max(0, Math.min(PetManager.NAMES.length - 1, petType));
		petScale = c(petScale, 0.4f, 2f);
		chinaHat = Math.max(0, Math.min(2, chinaHat));
		chinaHatScale = c(chinaHatScale, 0.5f, 2f);
	}

	/** Ile efektow jest teraz wlaczonych (do statystyk w Profilu). */
	public int activeEffects() {
		int n = 0;
		if (targetEnabled) n++;
		if (box) n++;
		if (ring) n++;
		if (scanner) n++;
		if (orbit) n++;
		if (glow) n++;
		if (jumpCircle) n++;
		if (trails) n++;
		if (rain) n++;
		if (blizzard) n++;
		if (fireflies) n++;
		if (sky) n++;
		if (petEnabled) n++;
		if (chinaHat != 0) n++;
		return n;
	}
}
