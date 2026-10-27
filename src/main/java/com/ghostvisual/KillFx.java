package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Wykrywa Twoje trafienia i zabicia (po stronie klienta) i odpala efekty:
 * shockwave, palka i piorun. Efekty sa tylko wizualne, nie istnieja na serwerze.
 */
public final class KillFx {
	private KillFx() {
	}

	private static final class Hit {
		LivingEntity e;
		long time;
		double x, y, z, dx, dz;
	}

	private static final List<Hit> hits = new ArrayList<>();
	private static long lastShock = 0;

	/** Wywolywane, gdy Ty uderzasz encje. */
	public static void onAttack(LivingEntity e) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Config cfg = Config.get();
		if (mc.player == null || e == mc.player) {
			return;
		}
		long now = System.currentTimeMillis();
		double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
		double l = Math.sqrt(dx * dx + dz * dz);
		if (l < 1e-6) {
			dx = 0;
			dz = 1;
		} else {
			dx /= l;
			dz /= l;
		}
		Hit h = null;
		for (Hit o : hits) {
			if (o.e == e) {
				h = o;
				break;
			}
		}
		if (h == null) {
			if (hits.size() >= 12) {
				hits.remove(0);
			}
			h = new Hit();
			h.e = e;
			hits.add(h);
		}
		h.time = now;
		h.x = e.getX();
		h.y = e.getY();
		h.z = e.getZ();
		h.dx = dx;
		h.dz = dz;

		if ((cfg.shockwave == 1 || cfg.shockwave == 3) && now - lastShock > 180) {
			lastShock = now;
			WorldFx.addShock(e.getX(), e.getY(), e.getZ(), 2.4);
		}
	}

	public static void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) {
			hits.clear();
			return;
		}
		Config cfg = Config.get();
		long now = System.currentTimeMillis();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			LivingEntity e = h.e;
			boolean dead = e.getHealth() <= 0f || e.deathTime > 0;
			if (dead) {
				hits.remove(i);
				trigger(mc, h, cfg);
			} else if (e.isRemoved() || now - h.time > 8000) {
				hits.remove(i);
			} else {
				h.x = e.getX();
				h.y = e.getY();
				h.z = e.getZ();
			}
		}
	}

	private static void trigger(MinecraftClient mc, Hit h, Config cfg) {
		if (cfg.killPlayersOnly && !(h.e instanceof PlayerEntity)) {
			return;
		}
		if (cfg.shockwave == 2 || cfg.shockwave == 3) {
			WorldFx.addShock(h.x, h.y, h.z, 6.5);
		}
		if (cfg.killClub || cfg.killLightning) {
			WorldFx.addKill(h.x, h.y, h.z, h.dx, h.dz, cfg.killClub, cfg.killLightning);
		}
		if (cfg.killLightning && mc.world != null) {
			mc.world.playSound(h.x, h.y, h.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 3.0f, 1.0f, false);
			mc.world.playSound(h.x, h.y, h.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 2.0f, 1.0f, false);
		}
	}
}
