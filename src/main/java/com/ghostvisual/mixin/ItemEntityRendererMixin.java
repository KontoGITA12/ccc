package com.ghostvisual.mixin;

import com.ghostvisual.Glass;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Glass dla itemow lezacych na ziemi. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

	@ModifyVariable(
		method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"),
		argsOnly = true,
		require = 0)
	private VertexConsumerProvider ghostvisual$glass(VertexConsumerProvider original) {
		return Glass.wrapItems(original);
	}
}
