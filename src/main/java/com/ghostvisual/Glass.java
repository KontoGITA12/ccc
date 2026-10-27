package com.ghostvisual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** Glass: przezroczystosc graczy i itemow lezacych na ziemi (uzywane przez mixiny). */
public final class Glass {
	private Glass() {
	}

	/** Czy encja rysowana wlasnie teraz ma byc przezroczysta (ustawiane przez mixin). */
	public static volatile boolean active = false;

	public static boolean appliesTo(Entity e) {
		Config cfg = Config.get();
		if (!(e instanceof PlayerEntity)) {
			return false;
		}
		boolean self = e == MinecraftClient.getInstance().player;
		return self ? cfg.glassSelf : cfg.glassPlayers;
	}

	/** Kolor ARGB uzywany przez model gracza (alfa z configu). */
	public static int argb() {
		int a = Math.max(8, Math.min(255, Math.round(Config.get().glassAlpha * 255f)));
		return (a << 24) | 0xFFFFFF;
	}

	public static VertexConsumerProvider wrapItems(VertexConsumerProvider original) {
		Config cfg = Config.get();
		if (!cfg.glassItems || original == null) {
			return original;
		}
		return new Provider(original, cfg.glassAlpha);
	}

	private static final class Provider implements VertexConsumerProvider {
		private final VertexConsumerProvider delegate;
		private final float alpha;

		Provider(VertexConsumerProvider delegate, float alpha) {
			this.delegate = delegate;
			this.alpha = alpha;
		}

		@Override
		public VertexConsumer getBuffer(RenderLayer layer) {
			if (layer.toString().contains("glint")) {
				return delegate.getBuffer(layer);
			}
			return new AlphaConsumer(delegate.getBuffer(TexturedRenderLayers.getItemEntityTranslucentCull()), alpha);
		}
	}

	private static final class AlphaConsumer implements VertexConsumer {
		private final VertexConsumer d;
		private final float alpha;

		AlphaConsumer(VertexConsumer d, float alpha) {
			this.d = d;
			this.alpha = alpha;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			d.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int a) {
			d.color(red, green, blue, Math.round(a * alpha));
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			d.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			d.overlay(u, v);
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			d.light(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			d.normal(x, y, z);
			return this;
		}
	}
}
