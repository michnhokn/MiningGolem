package com.michnhokn.mininggolem.entity;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.entity.goal.StripMineGoal;
import com.michnhokn.mininggolem.inventory.MiningGolemMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MiningGolemEntity extends PathfinderMob {
	public static final int TOOL_START = 0;
	public static final int TORCH_START = 4;
	public static final int LOOT_START = 8;
	public static final int LOOT_ROWS = 4;
	public static final int LOOT_COLUMNS = 9;
	public static final int LOOT_SLOTS = LOOT_ROWS * LOOT_COLUMNS;
	public static final int GOLEM_SLOTS = LOOT_START + LOOT_SLOTS;

	public static final int TUNNEL_LENGTH = 100;
	public static final int CONNECTOR_LENGTH = 3;
	public static final int RUNG_LENGTH = 2;
	public static final int TORCH_INTERVAL = 10;
	public static final int VEIN_CAP = 48;

	private static final EntityDataAccessor<Integer> DATA_STATUS =
			SynchedEntityData.defineId(MiningGolemEntity.class, EntityDataSerializers.INT);

	private static final Map<Item, ToolMaterial> TOOL_TIERS = Map.ofEntries(
			Map.entry(Items.WOODEN_PICKAXE, ToolMaterial.WOOD),
			Map.entry(Items.STONE_PICKAXE, ToolMaterial.STONE),
			Map.entry(Items.COPPER_PICKAXE, ToolMaterial.COPPER),
			Map.entry(Items.IRON_PICKAXE, ToolMaterial.IRON),
			Map.entry(Items.DIAMOND_PICKAXE, ToolMaterial.DIAMOND),
			Map.entry(Items.NETHERITE_PICKAXE, ToolMaterial.NETHERITE),
			Map.entry(Items.GOLDEN_PICKAXE, ToolMaterial.GOLD),
			Map.entry(Items.WOODEN_SHOVEL, ToolMaterial.WOOD),
			Map.entry(Items.STONE_SHOVEL, ToolMaterial.STONE),
			Map.entry(Items.COPPER_SHOVEL, ToolMaterial.COPPER),
			Map.entry(Items.IRON_SHOVEL, ToolMaterial.IRON),
			Map.entry(Items.DIAMOND_SHOVEL, ToolMaterial.DIAMOND),
			Map.entry(Items.NETHERITE_SHOVEL, ToolMaterial.NETHERITE),
			Map.entry(Items.GOLDEN_SHOVEL, ToolMaterial.GOLD));

	private final SimpleContainer inventory = new SimpleContainer(GOLEM_SLOTS);

	private Direction workDirection = Direction.NORTH;
	private boolean jobActive;
	private boolean jobFinished;
	private GolemStatus status = GolemStatus.IDLE;
	private Optional<UUID> owner = Optional.empty();
	private final List<BlockPos> waypoints = new ArrayList<>();
	private int waypointIndex;
	private int torchCounter;
	private int ticketTimer;
	private net.minecraft.world.level.ChunkPos lastTicketChunk;
	/** Start block of the next ring unit (row A start); null until the first extension. */
	private BlockPos nextUnitStart;

	public MiningGolemEntity(EntityType<? extends MiningGolemEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
		setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
		// flooded or burning tunnels are fine: the golem walks straight through
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.LAVA, 0.0F);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.0F);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 0.0F);
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.FIRE, 0.0F);
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean isPushedByFluid() {
		// water and lava currents must not shove the golem off his path
		return false;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 0.6);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATUS, GolemStatus.IDLE.ordinal());
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new StripMineGoal(this));
		// no wandering: the golem stays put when not mining; head tracking only
		goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	// --- Status ---

	public enum GolemStatus {
		IDLE, MINING, PAUSED_TORCHES, PAUSED_PICKAXES, PAUSED_LIQUID, INVENTORY_FULL, DONE;

		public static GolemStatus byId(int id) {
			return values()[Math.clamp(id, 0, values().length - 1)];
		}

		public String translationKey() {
			return "mininggolem.status." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public GolemStatus getStatus() {
		return status;
	}

	public void setStatus(GolemStatus status) {
		this.status = status;
		entityData.set(DATA_STATUS, status.ordinal());
	}

	public boolean isMiningAnim() {
		return status == GolemStatus.MINING;
	}

	// --- Job control ---

	public Direction getWorkDirection() {
		return workDirection;
	}

	public void setWorkDirection(Direction direction) {
		this.workDirection = direction.getAxis().isVertical() ? Direction.NORTH : direction;
		this.nextUnitStart = null;
	}

	public BlockPos getNextUnitStart() {
		return nextUnitStart;
	}

	public void setNextUnitStart(BlockPos pos) {
		this.nextUnitStart = pos;
	}

	public boolean isJobActive() {
		return jobActive;
	}

	public void setJobActive(boolean active) {
		this.jobActive = active;
	}

	public boolean isJobFinished() {
		return jobFinished;
	}

	public void setJobFinished(boolean finished) {
		this.jobFinished = finished;
	}

	public int getTorchCounter() {
		return torchCounter;
	}

	public void setTorchCounter(int count) {
		this.torchCounter = count;
	}

	public List<BlockPos> getWaypoints() {
		return waypoints;
	}

	public int getWaypointIndex() {
		return waypointIndex;
	}

	public void setWaypointIndex(int index) {
		this.waypointIndex = index;
	}

	public void resetJob() {
		waypoints.clear();
		waypointIndex = 0;
		torchCounter = 0;
		jobFinished = false;
		nextUnitStart = null;
	}

	public void toggleMining(ServerPlayer player) {
		if (jobActive) {
			jobActive = false;
			setStatus(GolemStatus.IDLE);
		} else {
			// continue the existing serpentine so old and new tunnels stay connected;
			// a fresh plan is only created via direction change or pick-up placement
			owner = Optional.of(player.getUUID());
			jobActive = true;
			jobFinished = false;
			setStatus(GolemStatus.MINING);
			sendChatKey("mininggolem.chat.started");
		}
	}

	public void setOwner(UUID uuid) {
		this.owner = Optional.ofNullable(uuid);
	}

	public void sendChat(Component message) {
		if (level() instanceof ServerLevel serverLevel) {
			if (owner.isPresent()) {
				ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(owner.get());
				if (player != null) {
					player.sendSystemMessage(message);
					return;
				}
			}
			serverLevel.getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(32.0))
					.forEach(p -> p.sendSystemMessage(message));
		}
	}

	/** Sends a chat message prefixed with the golem's (possibly custom) name. */
	public void sendChatKey(String key, Object... args) {
		Object[] withName = new Object[args.length + 1];
		withName[0] = getDisplayName();
		System.arraycopy(args, 0, withName, 1, args.length);
		sendChat(Component.translatable(key, withName));
	}

	// --- Inventory helpers ---

	public SimpleContainer getInventory() {
		return inventory;
	}

	public ItemStack getActivePickaxe() {
		for (int i = TOOL_START; i < TORCH_START; i++) {
			ItemStack stack = inventory.getItem(i);
			if (isTool(stack) && stack.getCount() > 0) return stack;
		}
		return ItemStack.EMPTY;
	}

	/** Picks the best-matching tool in the tool slots for the given block state. */
	public ItemStack getToolFor(BlockState state) {
		ItemStack any = ItemStack.EMPTY;
		for (int i = TOOL_START; i < TORCH_START; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) continue;
			if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && isShovel(stack)) return stack;
			if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && isPickaxe(stack)) return stack;
			if (any.isEmpty() && isTool(stack)) any = stack;
		}
		return any;
	}

	/** Finds the fastest tool in the tool slots that can actually harvest the given block. */
	public ItemStack getHarvestToolFor(BlockState state) {
		ItemStack best = ItemStack.EMPTY;
		float bestSpeed = 0.0F;
		for (int i = TOOL_START; i < TORCH_START; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !isTool(stack)) continue;
			ToolMaterial tier = tierOf(stack);
			if (state.is(tier.incorrectBlocksForDrops())) continue; // tier cannot harvest this
			if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !isPickaxe(stack)
					&& !state.is(BlockTags.MINEABLE_WITH_SHOVEL)) continue;
			float speed = tier.speed();
			if (speed > bestSpeed) {
				bestSpeed = speed;
				best = stack;
			}
		}
		return best;
	}

	public void damageTool(ItemStack tool) {
		for (int i = TOOL_START; i < TORCH_START; i++) {
			if (inventory.getItem(i) == tool) {
				if (tool.isDamageableItem()) {
					int damage = tool.getDamageValue() + 1;
					if (damage >= tool.getMaxDamage()) {
						inventory.setItem(i, ItemStack.EMPTY);
						playBreakSound();
					} else {
						tool.setDamageValue(damage);
						inventory.setChanged();
					}
				}
				return;
			}
		}
	}

	/** Mirrors the active tool into the main hand slot so the renderer can show it. */
	public void updateDisplayedTool(ItemStack tool) {
		ItemStack current = getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
		ItemStack wanted = tool.isEmpty() ? ItemStack.EMPTY : tool.copyWithCount(1);
		if (!ItemStack.isSameItemSameComponents(current, wanted)) {
			setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, wanted);
		}
	}

	public void consumeTorch() {
		for (int i = TORCH_START; i < LOOT_START; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				stack.shrink(1);
				inventory.setChanged();
				return;
			}
		}
	}

	public int getTorchCount() {
		int count = 0;
		for (int i = TORCH_START; i < LOOT_START; i++) {
			count += inventory.getItem(i).getCount();
		}
		return count;
	}

	/** Total blocks the golem can still break with the remaining durability in its tool slots. */
	public int getMineableBlockCount() {
		int total = 0;
		for (int i = TOOL_START; i < TORCH_START; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.isDamageableItem()) {
				total += Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
			}
		}
		return total;
	}

	/** Tunnel length in blocks the golem can still light with its torches (one per 10 columns). */
	public int getTunnelLength() {
		return getTorchCount() * TORCH_INTERVAL;
	}

	public boolean insertLoot(ItemStack stack) {
		if (stack.isEmpty()) return true;
		stack = stack.copy();
		for (int i = LOOT_START; i < GOLEM_SLOTS; i++) {
			ItemStack existing = inventory.getItem(i);
			if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
				int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
				if (moved > 0) {
					existing.grow(moved);
					stack.shrink(moved);
					inventory.setChanged();
				}
			}
			if (stack.isEmpty()) return true;
		}
		for (int i = LOOT_START; i < GOLEM_SLOTS; i++) {
			if (inventory.getItem(i).isEmpty()) {
				inventory.setItem(i, stack.copy());
				inventory.setChanged();
				return true;
			}
		}
		return false;
	}

	public boolean isLootFull() {
		for (int i = LOOT_START; i < GOLEM_SLOTS; i++) {
			if (inventory.getItem(i).isEmpty()) return false;
		}
		return true;
	}

	public static boolean isTool(ItemStack stack) {
		return isPickaxe(stack) || isShovel(stack);
	}

	public static boolean isPickaxe(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES);
	}

	public static boolean isShovel(ItemStack stack) {
		return stack.is(ItemTags.SHOVELS);
	}

	public static ToolMaterial tierOf(ItemStack stack) {
		ToolMaterial material = TOOL_TIERS.get(stack.getItem());
		return material != null ? material : ToolMaterial.IRON;
	}

	// --- Mining helpers ---

	public void damageActivePickaxe() {
		ItemStack tool = getActivePickaxe();
		if (!tool.isEmpty()) damageTool(tool);
	}

	private void playBreakSound() {
		level().playSound(null, blockPosition(), SoundEvents.COPPER_GOLEM_STATUE_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	public void playMiningEffects(BlockPos pos, BlockState state) {
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(
					new BlockParticleOption(ParticleTypes.BLOCK, state),
					pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
					2, 0.3, 0.2, 0.3, 0.0);
		}
	}

	public boolean tryPlaceTorch(BlockPos digPos, Direction tunnelDir) {
		if (!(level() instanceof ServerLevel serverLevel) || getTorchCount() == 0) return false;

		// preferred: standing torch on the ground, a couple of columns behind the dig position
		BlockPos behind = digPos.relative(tunnelDir.getOpposite(), 2);
		if (serverLevel.getBlockState(behind).isAir()
				&& serverLevel.getBlockState(behind.below()).isFaceSturdy(serverLevel, behind.below(), Direction.UP)) {
			serverLevel.setBlock(behind, Blocks.TORCH.defaultBlockState(), 3);
			consumeTorch();
			return true;
		}

		// fallback: wall-mounted torch one column behind, at head height
		BlockPos torchPos = digPos.relative(tunnelDir.getOpposite()).above();
		if (!serverLevel.getBlockState(torchPos).isAir()) return false;
		for (Direction side : new Direction[] {
				tunnelDir.getClockWise(), tunnelDir.getCounterClockWise(), tunnelDir.getOpposite()}) {
			Direction facing = side.getOpposite();
			BlockState wall = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, facing);
			if (WallTorchBlock.canSurvive(serverLevel, torchPos, facing)) {
				serverLevel.setBlock(torchPos, wall, 3);
				consumeTorch();
				return true;
			}
		}
		return false;
	}

	/** Fills the hole directly below the given position with a solid block from the loot inventory. */
	public boolean bridgeBelow(ServerLevel level, BlockPos pos) {
		return placeFillerAt(level, pos.below());
	}

	/** Places a solid block from the loot inventory at the given position if it is empty. */
	public boolean placeFillerAt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isSolid()) return false;
		if (!state.isAir() && !state.getFluidState().isEmpty() && !state.canBeReplaced()) return false;
		ItemStack filler = takeFillerBlock();
		if (filler.isEmpty()) return false;
		BlockState fill = ((net.minecraft.world.item.BlockItem) filler.getItem()).getBlock().defaultBlockState();
		level.setBlock(pos, fill, 3);
		return true;
	}

	private ItemStack takeFillerBlock() {
		for (int i = LOOT_START; i < GOLEM_SLOTS; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
					&& blockItem.getBlock().defaultBlockState().isSolid()) {
				ItemStack single = stack.copyWithCount(1);
				stack.shrink(1);
				if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
				inventory.setChanged();
				return single;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Mines the whole vein of the ore block that was just broken at pos. */
	public void mineVein(ServerLevel level, BlockPos pos, BlockState brokenState) {
		if (!brokenState.is(BlockTags.ORES)) return;
		var queue = new java.util.ArrayDeque<BlockPos>();
		var visited = new java.util.HashSet<BlockPos>();
		visited.add(pos);
		for (Direction dir : Direction.values()) queue.add(pos.relative(dir));
		int mined = 0;
		while (!queue.isEmpty() && mined < VEIN_CAP) {
			BlockPos current = queue.poll();
			if (!visited.add(current)) continue;
			BlockState state = level.getBlockState(current);
			if (!state.is(brokenState.getBlock())) continue;
			ItemStack tool = getHarvestToolFor(state);
			if (tool.isEmpty()) continue; // no tool can harvest this ore: leave it
			collectDrops(level, current, state);
			level.destroyBlock(current, false, this);
			damageTool(tool);
			mined++;
			for (Direction dir : Direction.values()) queue.add(current.relative(dir));
		}
		if (mined > 0) {
			playMiningEffects(pos, brokenState);
		}
	}

	public void dropInventory() {
		if (!(level() instanceof ServerLevel serverLevel)) return;
		for (int i = 0; i < GOLEM_SLOTS; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				spawnAtLocation(serverLevel, stack);
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	// --- Entity base ---

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (level().isClientSide()) {
			// sneaking = pick up; handled server-side
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (player.isShiftKeyDown()) {
				pickUp(serverPlayer);
				return InteractionResult.SUCCESS_SERVER;
			}
			serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
					(id, inv, p) -> new MiningGolemMenu(id, inv, this),
					getDisplayName()));
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	/** Puts the golem, including inventory, job and name, into the player's hands as an item. */
	private void pickUp(ServerPlayer player) {
		// stop the job and settle animation state before saving
		setJobActive(false);
		setStatus(GolemStatus.IDLE);

		net.minecraft.world.level.storage.TagValueOutput out =
				net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING);
		if (!save(out)) {
			return;
		}
		ItemStack stack = new ItemStack(com.michnhokn.mininggolem.item.ModItems.MINING_GOLEM_SPAWN_EGG);
		net.minecraft.world.item.component.CustomData.set(
				net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, out.buildResult());
		if (!player.getInventory().add(stack)) {
			player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
					player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack));
		}
		discard();
	}

	@Override
	public boolean isInvulnerableTo(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
		// gravel falling on the golem should never suffocate him
		if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) return true;
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && level() instanceof ServerLevel serverLevel && isAlive()) {
			extricateFromBlocks(serverLevel);
			refreshChunkTicket(serverLevel);
		}
	}

	/**
	 * While a job is running, hold a loading+simulation ticket around the golem so his
	 * chunks stay ticking even when no player is nearby. The ticket expires after
	 * 40 ticks, so it is refreshed periodically and on chunk change.
	 */
	private void refreshChunkTicket(ServerLevel level) {
		if (!jobActive) return;
		var chunkPos = chunkPosition();
		if (--ticketTimer > 0 && chunkPos.equals(lastTicketChunk)) return;
		ticketTimer = 20;
		lastTicketChunk = chunkPos;
		level.getChunkSource().addTicketWithRadius(MiningGolem.MINING_TICKET_TYPE, chunkPos, 2);
	}

	/**
	 * Digs out every block that ended up inside the golem's bounding box (falling
	 * gravel/sand or similar) immediately, until none is left.
	 */
	private void extricateFromBlocks(ServerLevel level) {
		AABB box = getBoundingBox();
		BlockPos min = BlockPos.containing(box.minX + 0.001, box.minY + 0.001, box.minZ + 0.001);
		BlockPos max = BlockPos.containing(box.maxX - 0.001, box.maxY - 0.001, box.maxZ - 0.001);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			BlockState state = level.getBlockState(pos);
			if (state.getCollisionShape(level, pos).isEmpty()) continue;
			BlockPos solid = pos.immutable();
			playMiningEffects(solid, state);
			level.playSound(null, solid, state.getSoundType().getBreakSound(), SoundSource.NEUTRAL, 0.6F, 1.0F);
			collectDrops(level, solid, state);
			level.destroyBlock(solid, false, this);
		}
	}

	/** Collects a block's drops into the golem's inventory; excess is dropped on the ground. */
	private void collectDrops(ServerLevel level, BlockPos pos, BlockState state) {
		for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos))) {
			if (!insertLoot(drop)) {
				level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
						level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
			}
		}
	}

	@Override
	protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
		return SoundEvents.COPPER_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.COPPER_GOLEM_DEATH;
	}

	@Override
	public void die(net.minecraft.world.damagesource.DamageSource source) {
		super.die(source);
		if (!level().isClientSide()) dropInventory();
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	// --- Persistence ---

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("WorkDirection", workDirection.getName());
		if (nextUnitStart != null) {
			ValueOutput unitStart = output.child("NextUnitStart");
			unitStart.putInt("X", nextUnitStart.getX());
			unitStart.putInt("Y", nextUnitStart.getY());
			unitStart.putInt("Z", nextUnitStart.getZ());
		}
		output.putBoolean("JobActive", jobActive);
		output.putBoolean("JobFinished", jobFinished);
		output.putInt("TorchCounter", torchCounter);
		owner.ifPresent(uuid -> output.store("Owner", UUIDUtil.CODEC, uuid));

		// only persist waypoints that are still ahead (the list grows forever)
		ValueOutput.ValueOutputList waypointList = output.childrenList("Waypoints");
		for (int i = waypointIndex; i < waypoints.size(); i++) {
			BlockPos pos = waypoints.get(i);
			ValueOutput child = waypointList.addChild();
			child.putInt("X", pos.getX());
			child.putInt("Y", pos.getY());
			child.putInt("Z", pos.getZ());
		}

		ValueOutput.ValueOutputList itemList = output.childrenList("Inventory");
		for (int i = 0; i < GOLEM_SLOTS; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				ValueOutput child = itemList.addChild();
				child.putInt("Slot", i);
				child.store("Item", ItemStack.CODEC, stack);
			}
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		workDirection = Direction.byName(input.getStringOr("WorkDirection", "north"));
		nextUnitStart = input.child("NextUnitStart")
				.map(start -> (BlockPos) new BlockPos(
						start.getIntOr("X", 0),
						start.getIntOr("Y", 0),
						start.getIntOr("Z", 0)))
				.orElse(null);
		jobActive = input.getBooleanOr("JobActive", false);
		jobFinished = input.getBooleanOr("JobFinished", false);
		torchCounter = input.getIntOr("TorchCounter", 0);
		owner = input.read("Owner", UUIDUtil.CODEC).map(Optional::of).orElse(Optional.empty());

		waypoints.clear();
		for (ValueInput child : input.childrenListOrEmpty("Waypoints")) {
			waypoints.add(new BlockPos(
					child.getIntOr("X", 0),
					child.getIntOr("Y", 0),
					child.getIntOr("Z", 0)));
		}
		waypointIndex = 0; // saved list already starts at the current position

		for (int i = 0; i < GOLEM_SLOTS; i++) inventory.setItem(i, ItemStack.EMPTY);
		for (ValueInput child : input.childrenListOrEmpty("Inventory")) {
			int slot = child.getIntOr("Slot", -1);
			Optional<ItemStack> stack = child.read("Item", ItemStack.CODEC);
			if (slot >= 0 && slot < GOLEM_SLOTS && stack.isPresent()) {
				inventory.setItem(slot, stack.get());
			}
		}
		setStatus(jobActive ? GolemStatus.MINING : GolemStatus.IDLE);
	}
}
