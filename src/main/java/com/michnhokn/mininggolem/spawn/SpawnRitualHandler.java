package com.michnhokn.mininggolem.spawn;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.entity.MiningGolemEntity;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;

public class SpawnRitualHandler {
	private static final String CARRIED_TAG = "MiningGolemData";

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide()) return InteractionResult.PASS;
			ItemStack held = player.getItemInHand(hand);

			// placing a picked-up golem back into the world
			CustomData carried = held.get(DataComponents.CUSTOM_DATA);
			if (held.is(com.michnhokn.mininggolem.item.ModItems.MINING_GOLEM_SPAWN_EGG) && carried != null) {
				return placeCarriedGolem((net.minecraft.server.level.ServerLevel) level, held,
						carried.copyTag(), hitResult.getBlockPos().relative(hitResult.getDirection()));
			}

			// the diamond block + jack o'lantern ritual
			if (!held.is(Blocks.JACK_O_LANTERN.asItem())) return InteractionResult.PASS;
			if (player.isShiftKeyDown()) return InteractionResult.PASS; // sneak to place a real jack o'lantern
			if (hitResult.getDirection() != Direction.UP) return InteractionResult.PASS;

			BlockPos base = hitResult.getBlockPos();
			if (!level.getBlockState(base).is(Blocks.DIAMOND_BLOCK)) return InteractionResult.PASS;

			// the carved face points toward the placer; work direction is the placer's look direction
			Direction workDir = player.getDirection();
			if (workDir.getAxis().isVertical()) workDir = Direction.NORTH;

			if (!player.hasInfiniteMaterials()) held.shrink(1);
			level.removeBlock(base, false);

			MiningGolemEntity golem = MiningGolem.MINING_GOLEM.create(level, EntitySpawnReason.EVENT);
			if (golem == null) return InteractionResult.FAIL;
			golem.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
			golem.setYRot(workDir.toYRot());
			golem.setWorkDirection(workDir);
			golem.setOwner(player.getUUID());
			level.addFreshEntity(golem);
			level.playSound(null, base, net.minecraft.sounds.SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.NEUTRAL, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		});
	}

	private static InteractionResult placeCarriedGolem(net.minecraft.server.level.ServerLevel level, ItemStack stack, CompoundTag tag, BlockPos pos) {
		var input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
		MiningGolemEntity golem = MiningGolem.MINING_GOLEM.create(level, EntitySpawnReason.EVENT);
		if (golem == null) return InteractionResult.FAIL;
		golem.load(input);
		// re-deploy at the new position: forget the old dig site and its plan
		golem.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		golem.resetJob();
		golem.setJobActive(false);
		golem.setStatus(MiningGolemEntity.GolemStatus.IDLE);
		golem.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		if (!level.addWithUUID(golem)) return InteractionResult.FAIL;
		stack.shrink(1);
		return InteractionResult.SUCCESS;
	}
}
