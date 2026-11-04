package com.ghostvisual;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Tytul i wykonawca aktualnej muzyki jako duzy napis na niebie. */
public final class MusicSky {
	private MusicSky() {
	}

	private static String lastTitle = "";
	private static double yaw = 0;
	private static float fade = 0f;

	public static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		Config cfg = Config.get();
		if (mc.player == null || mc.world == null) {
			return;
		}
		if (!cfg.musicSky || !MediaControl.available || MediaControl.title.isEmpty()) {
			fade = Math.max(0f, fade - 0.04f);
			if (fade <= 0f) {
				return;
			}
		} else {
			String title = MediaControl.title;
			if (!title.equals(lastTitle)) {
				lastTitle = title;
				yaw = mc.player.getYaw();
				fade = 0f;
			}
			fade = Math.min(1f, fade + 0.02f);
		}
		MatrixStack ms = ctx.matrixStack();
		VertexConsumerProvider vcp = ctx.consumers();
		if (ms == null || vcp == null) {
			return;
		}
		Vec3d cam = ctx.camera().getPos();
		double t = System.nanoTime() / 1.0e9;
		double yawR = Math.toRadians(yaw + Math.sin(t * 0.15) * 4.0);
		double elev = Math.toRadians(30);
		double dist = 55;
		double fx = -Math.sin(yawR), fz = Math.cos(yawR);
		double cx = cam.x + fx * Math.cos(elev) * dist;
		double cy = cam.y + Math.sin(elev) * dist;
		double cz = cam.z + fz * Math.cos(elev) * dist;
		double ux = -Math.cos(yawR), uz = -Math.sin(yawR);

		Theme th = cfg.theme();
		int accent = th.accent(cfg.animateColors);
		int a = Math.max(5, Math.round(fade * 255f));
		int cTitle = (a << 24) | 0xFFFFFF;
		int cArtist = (a << 24) | (accent & 0xFFFFFF);
		int cTime = (Math.round(a * 0.7f) << 24) | 0xFFFFFF;

		TextRenderer tr = mc.textRenderer;
		String title = tr.trimToWidth(MediaControl.title.isEmpty() ? lastTitle : MediaControl.title, 220);
		String artist = tr.trimToWidth(MediaControl.artist, 260);
		String time = Gfx.time(MediaControl.livePosition()) + " / " + Gfx.time(MediaControl.duration);

		line(tr, vcp, ms, cam, cx, cy, cz, ux, uz, fx, fz, Text.literal(title).formatted(Formatting.BOLD),
			tr.getWidth(title), 0.42, 0, cTitle);
		line(tr, vcp, ms, cam, cx, cy, cz, ux, uz, fx, fz, Text.literal(artist),
			tr.getWidth(artist), 0.26, 13.5 * 0.42 + 2, cArtist);
		line(tr, vcp, ms, cam, cx, cy, cz, ux, uz, fx, fz, Text.literal(time),
			tr.getWidth(time), 0.2, 13.5 * 0.42 + 2 + 9 * 0.26 + 3, cTime);
		if (vcp instanceof VertexConsumerProvider.Immediate imm) {
			imm.draw();
		}
	}

	/** Jedna linia tekstu wysrodkowana w punkcie c, przesunieta w dol o dropBlocks blokow. */
	private static void line(TextRenderer tr, VertexConsumerProvider vcp, MatrixStack ms, Vec3d cam,
							 double cx, double cy, double cz, double ux, double uz, double fx, double fz,
							 Text text, int widthPx, double s, double dropBlocks, int color) {
		if (widthPx <= 0) {
			return;
		}
		double w = widthPx * s;
		double ox = cx - ux * (w / 2) - cam.x;
		double oy = cy - dropBlocks - cam.y;
		double oz = cz - uz * (w / 2) - cam.z;
		Matrix4f m = new Matrix4f(ms.peek().getPositionMatrix());
		Matrix4f local = new Matrix4f().set(
			(float) (ux * s), 0f, (float) (uz * s), 0f,
			0f, (float) -s, 0f, 0f,
			(float) (fx * s), 0f, (float) (fz * s), 0f,
			(float) ox, (float) oy, (float) oz, 1f);
		m.mul(local);
		tr.draw(text, 0f, 0f, color, true, m, vcp, TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}
}
