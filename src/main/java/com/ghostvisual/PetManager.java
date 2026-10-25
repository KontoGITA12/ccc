package com.ghostvisual;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Pety to zwykle moby z Minecrafta, rysowane kliencko (nie istnieja na serwerze).
 * Chodzace ida za Toba, latajace unosza sie przy glowie.
 */
public final class PetManager {
	private PetManager() {
	}

	public static final EntityType<?>[] TYPES = {
		EntityType.CAT, EntityType.WOLF, EntityType.CHICKEN, EntityType.PIG,
		EntityType.RABBIT, EntityType.FOX, EntityType.PARROT, EntityType.BAT,
		EntityType.ALLAY, EntityType.BEE, EntityType.SHEEP, EntityType.COW
	};
	public static final String[] NAMES = {
		"Kot", "Pies", "Kurczak", "Świnia", "Królik", "Lis",
		"Papuga", "Nietoperz", "Allay", "Pszczoła", "Owca", "Krowa"
	};
	private static final boolean[] FLYING = {
		false, false, false, false, false, false,
		true, true, true, true, false, false
	};
	private static final boolean[] BROKEN = new boolean[TYPES.length];

	private static Entity pet;
	private static int petIndex = -1;
	private static ClientWorld petWorld;

	private static double x, y, z, px, py, pz;
	private static float yaw;
	private static double lastGroundY;

	public static void tick(MinecraftClient mc) {
		Config cfg = Config.get();
		if (!cfg.petEnabled || mc.player == null || mc.world == null) {
			pet = null;
			petIndex = -1;
			return;
		}
		try {
			if (pet == null || petIndex != cfg.petType || petWorld != mc.world) {
				create(mc, cfg.petType);
				if (pet == null) {
					return;
				}
			}
			update(mc, cfg);
		} catch (Throwable t) {
			GhostVisualClient.LOGGER.warn("Pet '{}' sprawia problemy, wylaczam go", NAMES[cfg.petType], t);
			BROKEN[cfg.petType] = true;
			pet = null;
			petIndex = cfg.petType;
		}
	}

	private static void create(MinecraftClient mc, int index) {
		petIndex = index;
		petWorld = mc.world;
		pet = null;
		if (BROKEN[index]) {
			return;
		}
		Entity e = TYPES[index].create(mc.world, SpawnReason.COMMAND);
		if (e == null) {
			return;
		}
		if (e instanceof BatEntity bat) {
			bat.setRoosting(false);
		}
		pet = e;
		var p = mc.player;
		x = px = p.getX() - 1;
		y = py = p.getY();
		z = pz = p.getZ() - 1;
		lastGroundY = p.getY();
		yaw = p.getYaw();
	}

	private static void update(MinecraftClient mc, Config cfg) {
		var p = mc.player;
		boolean flying = FLYING[petIndex];
		double s = cfg.petScale;

		if (p.isOnGround()) {
			lastGroundY = p.getY();
		}
		double yawRad = Math.toRadians(p.getYaw());
		double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);   // przod gracza
		double rx = -Math.cos(yawRad), rz = -Math.sin(yawRad);  // prawa strona gracza
		double tx = p.getX() - fx * (1.3 + 0.4 * s) + rx * 0.9;
		double tz = p.getZ() - fz * (1.3 + 0.4 * s) + rz * 0.9;
		double ty = flying
			? p.getY() + 1.9 + Math.sin(pet.age * 0.12) * 0.15
			: lastGroundY;

		px = x;
		py = y;
		pz = z;

		double dx = tx - x, dz = tz - z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist > 12) {
			x = tx;
			z = tz;
			y = ty;
			px = x;
			py = y;
			pz = z;
			dx = dz = 0;
			dist = 0;
		}
		double moveX = 0, moveZ = 0;
		if (dist > 0.12) {
			double step = Math.min(0.42, dist * 0.14);
			moveX = dx / dist * step;
			moveZ = dz / dist * step;
		}
		x += moveX;
		z += moveZ;
		y += (ty - y) * (flying ? 0.2 : 0.35);

		double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);
		float targetYaw;
		if (speed > 0.02) {
			targetYaw = (float) (Math.atan2(-moveX, moveZ) * 180.0 / Math.PI);
		} else {
			targetYaw = (float) (Math.atan2(-(p.getX() - x), p.getZ() - z) * 180.0 / Math.PI);
		}
		float prevYaw = yaw;
		yaw = MathHelper.lerpAngleDegrees(0.3f, yaw, targetYaw);

		Entity e = pet;
		e.prevX = px;
		e.prevY = py;
		e.prevZ = pz;
		e.lastRenderX = px;
		e.lastRenderY = py;
		e.lastRenderZ = pz;
		e.setPosition(x, y, z);
		e.prevYaw = prevYaw;
		e.setYaw(yaw);
		e.prevPitch = 0f;
		e.setPitch(0f);
		e.setOnGround(!flying);
		e.age++;
		if (e instanceof LivingEntity le) {
			le.prevBodyYaw = prevYaw;
			le.bodyYaw = yaw;
			le.prevHeadYaw = prevYaw;
			le.headYaw = yaw;
			float limb = flying ? 0f : (float) Math.min(1.0, speed * 4.0);
			le.limbAnimator.updateLimbs(limb, 0.4f, 1f);
		}
		if (e instanceof BatEntity bat) {
			bat.flyingAnimationState.startIfNotRunning(bat.age);
		}
	}

	public static void render(WorldRenderContext ctx) {
		Config cfg = Config.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (pet == null || !cfg.petEnabled || mc.world == null) {
			return;
		}
		MatrixStack ms = ctx.matrixStack();
		VertexConsumerProvider vcp = ctx.consumers();
		if (ms == null || vcp == null) {
			return;
		}
		try {
			float td = ctx.tickCounter().getTickDelta(false);
			Vec3d cam = ctx.camera().getPos();
			double rx = MathHelper.lerp(td, px, x) - cam.x;
			double ry = MathHelper.lerp(td, py, y) - cam.y;
			double rz = MathHelper.lerp(td, pz, z) - cam.z;
			int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(x, y + 0.5, z));
			float s = cfg.petScale;
			ms.push();
			ms.translate((float) rx, (float) ry, (float) rz);
			ms.scale(s, s, s);
			mc.getEntityRenderDispatcher().render(pet, 0.0, 0.0, 0.0, td, ms, vcp, light);
			ms.pop();
		} catch (Throwable t) {
			GhostVisualClient.LOGGER.warn("Nie udalo sie narysowac peta", t);
			BROKEN[petIndex] = true;
			pet = null;
		}
	}
}
