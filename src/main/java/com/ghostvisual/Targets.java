package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Wykrywa gracza / moba, w ktorego celujesz (w zasiegu z configu). */
public final class Targets {
	private Targets() {
	}

	/** Ostatni cel (zostaje na czas wygaszania). */
	public static LivingEntity shown = null;
	private static LivingEntity current = null;
	private static float fade = 0f;
	private static float fadePrev = 0f;

	public static long startMillis = System.currentTimeMillis();
	public static int targetsSeen = 0;

	public static float fade(float tickDelta) {
		return MathHelper.lerp(tickDelta, fadePrev, fade);
	}

	public static void tick(MinecraftClient mc) {
		Config cfg = Config.get();
		LivingEntity found = null;
		if (cfg.targetEnabled && mc.world != null && mc.player != null) {
			found = find(mc, cfg);
		}
		if (found != null && found != current) {
			targetsSeen++;
		}
		current = found;
		if (found != null) {
			shown = found;
		}
		fadePrev = fade;
		fade += ((found != null ? 1f : 0f) - fade) * 0.25f;
		if (found == null && fade < 0.02f) {
			fade = 0f;
			shown = null;
		}
	}

	private static LivingEntity find(MinecraftClient mc, Config cfg) {
		Entity cam = mc.getCameraEntity();
		if (cam == null) {
			return null;
		}
		Vec3d eye = cam.getCameraPosVec(1f);
		Vec3d look = cam.getRotationVec(1f);
		double range = cfg.range;
		List<LivingEntity> list = mc.world.getEntitiesByClass(LivingEntity.class,
			cam.getBoundingBox().expand(range),
			e -> e != cam && e.isAlive() && !e.isSpectator()
				&& (!cfg.playersOnly || e instanceof PlayerEntity));
		LivingEntity best = null;
		double bestDot = -2;
		for (LivingEntity e : list) {
			Vec3d center = e.getPos().add(0, e.getHeight() * 0.5, 0);
			Vec3d d = center.subtract(eye);
			double dist = d.length();
			if (dist > range || dist < 0.5) {
				continue;
			}
			double dot = d.multiply(1.0 / dist).dotProduct(look);
			double tolerance = Math.cos(Math.atan2(Math.max(e.getWidth(), e.getHeight() * 0.5) * 0.6 + 0.15, dist));
			if (dot >= tolerance && dot > bestDot) {
				bestDot = dot;
				best = e;
			}
		}
		return best;
	}
}
