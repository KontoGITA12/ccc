package com.ghostvisual.mixin;

import com.ghostvisual.Glass;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Glass dla graczy: renderer rysuje gracza jak "niewidzialnego, ale widocznego"
 * (polprzezroczysty), z alfa z configu.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

	@Inject(
		method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"),
		require = 0)
	private void ghostvisual$flag(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
		Glass.active = Glass.appliesTo(entity);
	}

	@Inject(
		method = "isVisible(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)Z",
		at = @At("HEAD"),
		cancellable = true,
		require = 0)
	private void ghostvisual$visible(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (Glass.active) {
			cir.setReturnValue(false);
		}
	}

	@ModifyConstant(
		method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		constant = @Constant(intValue = 654311423),
		require = 0)
	private int ghostvisual$color(int original) {
		return Glass.active ? Glass.argb() : original;
	}
}
