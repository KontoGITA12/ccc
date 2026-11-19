package com.webswap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * WebSwap (made by shvga). Dziala tylko po Twoim kliknieciu:
 * A) dzwignia/przycisk w rece + prawy klik w pajeczyne: wiaderko z woda, woda niszczy pajeczyne,
 *    zbiera wode i od razu stawia dzwignie/przycisk z powrotem.
 * B) pajeczyna w rece + prawy klik w dzwignie/przycisk: pajeczyna stawiana jak przy kucaniu.
 */
public class WebSwapClient implements ClientModInitializer {
	private static int stage = 0;
	private static int origSlot, waited;
	private static BlockPos web;

	@Override
	public void onInitializeClient() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
			world.isClient && player instanceof ClientPlayerEntity cp ? onUse(cp, hand, hit) : ActionResult.PASS);
		ClientTickEvents.END_CLIENT_TICK.register(WebSwapClient::tick);
	}

	private static boolean isLeverItem(ItemStack s) {
		return s.isOf(Items.LEVER) || s.isIn(ItemTags.BUTTONS);
	}

	private static ActionResult onUse(ClientPlayerEntity p, Hand hand, BlockHitResult hit) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (stage != 0 || hand != Hand.MAIN_HAND || mc.interactionManager == null || mc.world == null) {
			return ActionResult.PASS;
		}
		ItemStack held = p.getMainHandStack();
		var state = mc.world.getBlockState(hit.getBlockPos());
		if (isLeverItem(held) && state.isOf(Blocks.COBWEB)) {
			int w = -1;
			for (int i = 0; i < 9; i++) {
				if (p.getInventory().getStack(i).isOf(Items.WATER_BUCKET)) {
					w = i;
					break;
				}
			}
			if (w < 0) {
				p.sendMessage(Text.literal("WebSwap: brak wiaderka z wodą na pasku."), true);
				return ActionResult.FAIL;
			}
			origSlot = p.getInventory().selectedSlot;
			web = hit.getBlockPos();
			p.getInventory().selectedSlot = w;
			mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
			p.swingHand(Hand.MAIN_HAND);
			stage = 1;
			waited = 0;
			return ActionResult.FAIL;
		}
		if (held.isOf(Items.COBWEB) && !p.isSneaking() && (state.isOf(Blocks.LEVER) || state.isIn(BlockTags.BUTTONS))) {
			var nh = mc.getNetworkHandler();
			if (nh != null) {
				nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
				p.setSneaking(true);
				mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
				p.setSneaking(false);
				nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
				p.swingHand(Hand.MAIN_HAND);
			}
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static void tick(MinecraftClient mc) {
		if (stage == 0) {
			return;
		}
		ClientPlayerEntity p = mc.player;
		if (p == null || mc.world == null || mc.interactionManager == null) {
			stage = 0;
			return;
		}
		waited++;
		boolean gone = !mc.world.getBlockState(web).isOf(Blocks.COBWEB);
		if (!gone && waited < 16) {
			return;
		}
		stage = 0;
		if (p.getMainHandStack().isOf(Items.BUCKET)) {
			mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
		}
		p.getInventory().selectedSlot = origSlot;
		HitResult t = mc.crosshairTarget;
		if (t instanceof BlockHitResult b && t.getType() == HitResult.Type.BLOCK) {
			mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, b);
			p.swingHand(Hand.MAIN_HAND);
		}
	}
}
