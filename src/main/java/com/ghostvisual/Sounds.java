package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Wlasne dzwieki trafienia i zabicia (grane tylko u Ciebie). */
public final class Sounds {
	private Sounds() {
	}

	public static final String[] HIT = {"Wyłączony", "Pop", "Cięcie", "Dzwonek"};
	public static final String[] KILL = {"Wyłączony", "Katana (anime)", "Iskry (anime)", "Bas", "Digi digi hey", "Skibidi", "Fart"};

	public static void play(String kind, int idx, float pitch) {
		Config cfg = Config.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (idx <= 0 || mc.getSoundManager() == null) {
			return;
		}
		try {
			SoundEvent ev = SoundEvent.of(Identifier.of("ghostvisual", kind + "_" + idx));
			mc.getSoundManager().play(PositionedSoundInstance.master(ev, pitch, cfg.soundVolume));
		} catch (Exception ignored) {
		}
	}
}
