package com.michnhokn.mininggolem.entity.goal;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.entity.MiningGolemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

import java.util.EnumSet;

public class StripMineGoal extends Goal {
	private static final double ARRIVE_DISTANCE_SQ = 1.3 * 1.3;
	private static final double ORE_REACH_SQ = 3.2 * 3.2;
	/** Blocks within this distance of the golem are dug in place, no pathing. */
	private static final double DIG_REACH_SQ = 3.0 * 3.0;

	private final MiningGolemEntity golem;

	private int breakTicks;
	private int breakDuration;
	private BlockPos breakPos = BlockPos.ZERO;
	private boolean wasBreaking;
	private boolean torchPending;

	/** Ores exposed by digging; mined (with vein mining) before the tunnel continues. */
	private final java.util.Deque<BlockPos> exposedOres = new java.util.ArrayDeque<>();
	/** The exposed ore currently being dug; kept until fully mined. */
	private BlockPos activeOre;

	public StripMineGoal(MiningGolemEntity golem) {
		this.golem = golem;
		setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return golem.isJobActive() && !golem.isJobFinished();
	}

	@Override
	public boolean canContinueToUse() {
		return golem.isJobActive() && !golem.isJobFinished();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		golem.setStatus(MiningGolemEntity.GolemStatus.MINING);
	}

	@Override
	public void stop() {
		if (golem.level() instanceof ServerLevel serverLevel) {
			serverLevel.destroyBlockProgress(golem.getId(), breakPos, -1);
		}
		golem.getNavigation().stop();
		golem.updateDisplayedTool(ItemStack.EMPTY);
		wasBreaking = false;
		breakTicks = 0;
		torchPending = false;
		exposedOres.clear();
		activeOre = null;
	}

	@Override
	public void tick() {
		if (!(golem.level() instanceof ServerLevel level)) return;

		if (golem.getWaypoints().isEmpty()) {
			// fresh job: no fixed pattern length — extend the serpentine forever
			if (golem.isJobFinished()) {
				finishJob();
				return;
			}
			extendWaypoints();
		} else if (golem.getWaypoints().size() - golem.getWaypointIndex() < WAYPOINT_MARGIN) {
			// running low on waypoints: keep extending the pattern lazily
			extendWaypoints();
		}

		if (golem.getWaypointIndex() >= golem.getWaypoints().size()) {
			finishJob();
			return;
		}

		if (!checkTools()) return;
		if (!checkTorches()) return;

		// after a mined column, ores in the now-visible faces get mined first
		if (mineExposedOre(level)) return;

		BlockPos target = golem.getWaypoints().get(golem.getWaypointIndex());

		stayOnLevel(level, target);

		// keep the tunnel floor gap-free: bridge holes right below the golem
		if (golem.onGround()) {
			golem.bridgeBelow(level, golem.blockPosition());
		}

		if (golem.blockPosition().distSqr(target) <= ARRIVE_DISTANCE_SQ && isTunnelClear(level, target)) {
			golem.setWaypointIndex(golem.getWaypointIndex() + 1);
			resetBreaking(level);
			return;
		}

		BlockPos nextBlock = findNextBlock(level, target);
		if (nextBlock == null) {
			if (columnBusy(level, target)) {
				// falling gravel/sand is still airborne in the bore: wait for it to land
				resetBreaking(level);
				golem.getNavigation().stop();
				return;
			}
			resetBreaking(level);
			golem.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, walkSpeed());
			if (MiningGolem.DEBUG) highlightBlock(level, target.below(), 0x40FF60); // green: bridging spot
			return;
		}

		digBlock(level, nextBlock);
	}

	// --- Level keeping ---

	/**
	 * The golem should never drift off the Y level the job was started on: the floor of
	 * the next column is bridged before he walks in, and if he did fall he stacks a lift
	 * block and jumps back up.
	 */
	private void stayOnLevel(ServerLevel level, BlockPos target) {
		int tunnelY = target.getY();
		if (golem.blockPosition().distSqr(target) <= 9.0) {
			golem.bridgeBelow(level, target);
		}
		if (!golem.onGround()) return;
		int dy = golem.getBlockY() - tunnelY;
		if (dy == -1) {
			golem.setJumping(true);
		} else if (dy < -1) {
			if (golem.placeFillerAt(level, golem.blockPosition().above())) {
				golem.setJumping(true);
			}
		}
	}

	// --- Exposed ore mining ---

	/**
	 * After the golem mined the 1x2 column, all six now-visible faces of the broken
	 * blocks are checked for ores he can harvest with a tool in his inventory. Those
	 * are mined (with vein mining) before the tunnel continues. The same ore is kept
	 * until its break completes so switching between ores never resets progress.
	 */
	private boolean mineExposedOre(ServerLevel level) {
		while (true) {
			if (activeOre != null) {
				if (!isMinableOre(level, activeOre)) {
					activeOre = null; // mined this tick / taken by vein mining / unharvestable now
					continue;
				}
				digBlock(level, activeOre);
				if (!level.getBlockState(activeOre).is(BlockTags.ORES)) {
					activeOre = null; // break completed
				}
				return true;
			}
			activeOre = pollMinableOre(level);
			if (activeOre == null) return false;
		}
	}

	private boolean isMinableOre(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(BlockTags.ORES)
				&& !golem.getHarvestToolFor(level.getBlockState(pos)).isEmpty()
				&& golem.blockPosition().distSqr(pos) <= ORE_REACH_SQ;
	}

	private BlockPos pollMinableOre(ServerLevel level) {
		while (!exposedOres.isEmpty()) {
			BlockPos ore = exposedOres.poll();
			if (isMinableOre(level, ore)) return ore;
		}
		return null;
	}

	/** Checks the six now-visible faces of a freshly mined block for harvestable ores. */
	private void queueExposedOres(ServerLevel level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			BlockPos neighbor = pos.relative(dir);
			if (level.getBlockState(neighbor).is(BlockTags.ORES)
					&& !golem.getHarvestToolFor(level.getBlockState(neighbor)).isEmpty()) {
				exposedOres.add(neighbor.immutable());
			}
		}
	}

	/** True while a falling block (gravel/sand) is still airborne inside the tunnel bore. */
	private boolean columnBusy(ServerLevel level, BlockPos target) {
		var falling = level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
				new net.minecraft.world.phys.AABB(target.getX(), target.getY(), target.getZ(),
						target.getX() + 1.0, target.getY() + 2.0, target.getZ() + 1.0));
		return !falling.isEmpty();
	}

	// --- Pause conditions ---

	private boolean checkTools() {
		if (!golem.getActivePickaxe().isEmpty()) {
			resumeIfPaused();
			return true;
		}
		golem.getNavigation().stop();
		resetBreaking((ServerLevel) golem.level());
		pause(MiningGolemEntity.GolemStatus.PAUSED_PICKAXES, "mininggolem.chat.paused_tools");
		return false;
	}

	private boolean checkTorches() {
		if (golem.getTorchCount() > 0) {
			resumeIfPaused();
			return true;
		}
		golem.getNavigation().stop();
		resetBreaking((ServerLevel) golem.level());
		pause(MiningGolemEntity.GolemStatus.PAUSED_TORCHES, "mininggolem.chat.paused_torches");
		return false;
	}

	private void pause(MiningGolemEntity.GolemStatus status, String messageKey) {
		if (golem.getStatus() != status) {
			golem.setStatus(status);
			golem.updateDisplayedTool(ItemStack.EMPTY);
			golem.sendChatKey(messageKey);
		}
	}

	private void resumeIfPaused() {
		if (golem.getStatus() != MiningGolemEntity.GolemStatus.MINING) {
			golem.setStatus(MiningGolemEntity.GolemStatus.MINING);
			golem.sendChatKey("mininggolem.chat.resumed");
		}
	}

	// --- Digging ---

	private BlockPos findNextBlock(ServerLevel level, BlockPos target) {
		BlockPos head = target.above();
		if (needsMining(level, target)) return target;
		if (needsMining(level, head)) return head;
		return null;
	}

	private boolean needsMining(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.getFluidState().isEmpty() && !state.liquid();
	}

	private boolean isTunnelClear(ServerLevel level, BlockPos target) {
		return !needsMining(level, target) && !needsMining(level, target.above());
	}

	private void resetBreaking(ServerLevel level) {
		if (wasBreaking) {
			level.destroyBlockProgress(golem.getId(), breakPos, -1);
			wasBreaking = false;
		}
		breakTicks = 0;
	}

	private void digBlock(ServerLevel level, BlockPos pos) {
		if (!pos.equals(breakPos)) {
			resetBreaking(level);
			breakPos = pos;
			BlockState state = level.getBlockState(pos);
			// prefer a tool that can actually harvest the block; fall back to any tool
			ItemStack tool = golem.getHarvestToolFor(state);
			if (tool.isEmpty()) tool = golem.getToolFor(state);
			golem.updateDisplayedTool(tool);
			if (tool.isEmpty()) return;
			float hardness = state.getDestroySpeed(level, pos);
			if (hardness < 0.0F) {
				onUnbreakable();
				return;
			}
			float speed = MiningGolemEntity.tierOf(tool).speed();
			breakDuration = Mth.clamp((int) (hardness * 1.5F * 20.0F / speed), 5, 1200);
			if (MiningGolem.DEBUG) breakDuration *= 10; // debug: watch the dig progress
		}

		// in reach of the target block: hold position and dig (navigating into a solid
		// block only produces weird detour paths)
		if (golem.blockPosition().distSqr(pos) > DIG_REACH_SQ) {
			golem.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, walkSpeed());
			return;
		}
		golem.getNavigation().stop();

		wasBreaking = true;
		breakTicks++;
		golem.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

		int stage = (int) ((float) breakTicks / breakDuration * 10.0F);
		level.destroyBlockProgress(golem.getId(), breakPos, Math.clamp(stage, 0, 9));
		if (breakTicks % 10 == 0) {
			golem.playMiningEffects(breakPos, level.getBlockState(breakPos));
			playDigSound(level, breakPos);
		}
		if (MiningGolem.DEBUG) {
			highlightBlock(level, breakPos, 0xFF4040); // red: about to break
			highlightBlock(level, breakPos.relative(currentForward(level, breakPos).getOpposite(), 2), 0x4090FF); // blue: torch spot
		}

		if (breakTicks >= breakDuration) {
			breakBlock(level, breakPos);
			breakTicks = 0;
		}
	}

	private double walkSpeed() {
		return MiningGolem.DEBUG ? 0.25 : 1.0;
	}

	/** Debug: outlines a block with floating dust particles. */
	private void highlightBlock(ServerLevel level, BlockPos pos, int color) {
		level.sendParticles(new DustParticleOptions(color, 1.0F),
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.45, 0.45, 0.45, 0.0);
	}

	private void breakBlock(ServerLevel level, BlockPos pos) {
		level.destroyBlockProgress(golem.getId(), pos, -1);
		wasBreaking = false;
		BlockState state = level.getBlockState(pos);
		ItemStack tool = golem.getToolFor(state);

		if (!tool.isEmpty() && !state.is(MiningGolemEntity.tierOf(tool).incorrectBlocksForDrops())) {
			for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos))) {
				if (!golem.insertLoot(drop)) {
					level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
					golem.setJobActive(false);
					golem.setJobFinished(true);
					golem.setStatus(MiningGolemEntity.GolemStatus.INVENTORY_FULL);
					golem.sendChatKey("mininggolem.chat.inventory_full");
					return;
				}
			}
		}

		level.destroyBlock(pos, false, golem);
		golem.damageTool(tool);

		// torch rhythm counts tunnel columns (foot-level blocks only, any tool)
		Direction forward = currentForward(level, pos);
		if (torchPending) {
			torchPending = !golem.tryPlaceTorch(pos, forward);
		}
		if (pos.getY() == golem.blockPosition().getY()) {
			golem.setTorchCounter(golem.getTorchCounter() + 1);
			if (golem.getTorchCounter() >= MiningGolemEntity.TORCH_INTERVAL) {
				golem.setTorchCounter(0);
				torchPending = !golem.tryPlaceTorch(pos, forward);
			}
		}

		// ores: take the whole vein while we're here, then check the now-visible faces
		golem.mineVein(level, pos, state);
		queueExposedOres(level, pos);
	}

	/** The horizontal direction the golem is currently digging towards. */
	private Direction currentForward(ServerLevel level, BlockPos digPos) {
		var waypoints = golem.getWaypoints();
		int index = golem.getWaypointIndex();
		if (index > 0 && index - 1 < waypoints.size()) {
			BlockPos prev = waypoints.get(index - 1);
			int dx = digPos.getX() - prev.getX();
			int dz = digPos.getZ() - prev.getZ();
			if (dx > 0) return Direction.EAST;
			if (dx < 0) return Direction.WEST;
			if (dz > 0) return Direction.SOUTH;
			if (dz < 0) return Direction.NORTH;
		}
		return golem.getWorkDirection();
	}

	private void playDigSound(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.NEUTRAL, 0.5F, 0.8F);
	}

	// --- Termination ---

	private void onUnbreakable() {
		golem.setJobActive(false);
		golem.setJobFinished(true);
		golem.setStatus(MiningGolemEntity.GolemStatus.IDLE);
		golem.sendChatKey("mininggolem.chat.bedrock");
	}

	private void finishJob() {
		golem.setJobActive(false);
		golem.setJobFinished(true);
		golem.setStatus(MiningGolemEntity.GolemStatus.DONE);
		golem.sendChatKey("mininggolem.chat.done");
	}

	// --- Waypoints ---

	private static final int WAYPOINT_MARGIN = 10;

	/**
	 * The plan is endless: whenever the golem gets close to the end of the waypoint list,
	 * the next ring unit is appended. One unit = [leading connector] + tunnel row
	 * (work direction) + connector (3 to the side) + return row (opposite direction) +
	 * a short rung (2) that closes the ring — every pair of rows is connected at BOTH
	 * ends and the network is a chain of loops. The job only ends via stop conditions
	 * (inventory full, no tools, …).
	 */
	private void extendWaypoints() {
		var waypoints = golem.getWaypoints();
		BlockPos cursor = golem.getNextUnitStart();
		if (cursor == null) {
			cursor = golem.blockPosition();
		} else {
			// continuation: connect the previous return row to the next ring
			Direction advance = golem.getWorkDirection().getCounterClockWise();
			for (int i = 0; i < MiningGolemEntity.CONNECTOR_LENGTH; i++) {
				cursor = cursor.relative(advance);
				waypoints.add(cursor.immutable());
			}
		}
		Direction forward = golem.getWorkDirection();
		Direction advance = forward.getCounterClockWise();
		Direction back = forward.getOpposite();

		// row A
		for (int i = 0; i < MiningGolemEntity.TUNNEL_LENGTH; i++) {
			cursor = cursor.relative(forward);
			waypoints.add(cursor.immutable());
		}
		// connector to row B
		for (int i = 0; i < MiningGolemEntity.CONNECTOR_LENGTH; i++) {
			cursor = cursor.relative(advance);
			waypoints.add(cursor.immutable());
		}
		// row B
		for (int i = 0; i < MiningGolemEntity.TUNNEL_LENGTH; i++) {
			cursor = cursor.relative(back);
			waypoints.add(cursor.immutable());
		}

		// the next ring starts at row B's end
		golem.setNextUnitStart(cursor.immutable());

		// closing rung: branch off row B's end, reconnecting to the previous row
		for (int i = 1; i <= MiningGolemEntity.RUNG_LENGTH; i++) {
			waypoints.add(cursor.relative(advance, i).immutable());
		}
		MiningGolem.LOGGER.debug("Extended mining plan to {} waypoints", waypoints.size());
	}
}
