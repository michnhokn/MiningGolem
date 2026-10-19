package com.michnhokn.mininggolem.inventory;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.entity.MiningGolemEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MiningGolemMenu extends AbstractContainerMenu {
	private final MiningGolemEntity golem;
	private final SimpleContainer clientContainer;
	private int clientStatus;
	private int clientDirection;

	public MiningGolemMenu(int containerId, Inventory playerInventory, MiningGolemEntity golem) {
		super(MiningGolem.MINING_GOLEM_MENU, containerId);
		this.golem = golem;
		this.clientContainer = golem != null ? null : new SimpleContainer(MiningGolemEntity.GOLEM_SLOTS);

		var container = getGolemContainer();
		var layout = new MenuLayout();

		for (int row = 0; row < 4; row++) {
			int index = MiningGolemEntity.TOOL_START + row;
			addSlot(new FilterSlot(container, index, layout.toolX(), layout.toolY(row), MiningGolemEntity::isTool));
		}
		for (int row = 0; row < 4; row++) {
			int index = MiningGolemEntity.TORCH_START + row;
			addSlot(new FilterSlot(container, index, layout.torchX(), layout.torchY(row), MiningGolemMenu::isTorch));
		}
		for (int row = 0; row < MiningGolemEntity.LOOT_ROWS; row++) {
			for (int col = 0; col < MiningGolemEntity.LOOT_COLUMNS; col++) {
				int index = MiningGolemEntity.LOOT_START + row * MiningGolemEntity.LOOT_COLUMNS + col;
				addSlot(new Slot(container, index, layout.lootX(col), layout.lootY(row)));
			}
		}
		addStandardInventorySlots(playerInventory, layout.invX(), layout.invY());

		addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return golem != null ? golem.getStatus().ordinal() : clientStatus;
			}

			@Override
			public void set(int value) {
				clientStatus = value;
			}
		});
		addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return golem != null ? golem.getWorkDirection().ordinal() : clientDirection;
			}

			@Override
			public void set(int value) {
				clientDirection = value;
			}
		});
	}

	public static MiningGolemMenu createClient(int containerId, Inventory playerInventory) {
		return new MiningGolemMenu(containerId, playerInventory, null);
	}

	private net.minecraft.world.Container getGolemContainer() {
		return golem != null ? golem.getInventory() : clientContainer;
	}

	public MiningGolemEntity getGolem() {
		return golem;
	}

	public MiningGolemEntity.GolemStatus getStatus() {
		return MiningGolemEntity.GolemStatus.byId(clientStatus);
	}

	/** Ordinal of the golem's work direction (synced to the client). */
	public int getDirectionOrdinal() {
		return clientDirection;
	}

	public static boolean isTorch(ItemStack stack) {
		return stack.is(net.minecraft.world.item.Items.TORCH) || stack.is(net.minecraft.world.item.Items.SOUL_TORCH);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();

		if (index < MiningGolemEntity.GOLEM_SLOTS) {
			if (!moveItemStackTo(original, MiningGolemEntity.GOLEM_SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (MiningGolemEntity.isTool(original)) {
			if (!moveItemStackTo(original, MiningGolemEntity.TOOL_START, MiningGolemEntity.TORCH_START, false)) {
				return ItemStack.EMPTY;
			}
		} else if (isTorch(original)) {
			if (!moveItemStackTo(original, MiningGolemEntity.TORCH_START, MiningGolemEntity.LOOT_START, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(original, MiningGolemEntity.LOOT_START, MiningGolemEntity.GOLEM_SLOTS, false)) {
			return ItemStack.EMPTY;
		}

		if (original.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return golem != null && !golem.isRemoved() && player.distanceToSqr(golem) <= 64.0 * 64.0;
	}

	// --- Layout constants shared with the screen ---

	public static final class MenuLayout {
		public int titleX() { return 8; }
		public int titleY() { return 6; }
		public int statusRight() { return 248; }
		public int statusY() { return 6; }
		public int toolX() { return 8; }
		public int toolY(int row) { return 20 + row * 18; }
		public int torchX() { return 230; }
		public int torchY(int row) { return 20 + row * 18; }
		public int lootX(int col) { return 44 + col * 18; }
		public int lootY(int row) { return 20 + row * 18; }
		public int nameX() { return 8; }
		public int nameY() { return 106; }
		public int nameWidth() { return 120; }
		public int dirX() { return 134; }
		public int dirLabelY() { return 96; }
		public int dirY() { return 106; }
		public int dirButtonWidth() { return 28; }
		public int dirButtonHeight() { return 16; }
		public int dirStep() { return 29; }
		public int invX() { return 9; }
		public int invY() { return 166; }
		public int invLabelX() { return 8; }
		public int invLabelY() { return 155; }
		public int buttonX() { return 8; }
		public int buttonY() { return 126; }
		public int buttonWidth() { return 240; }
		public int buttonHeight() { return 20; }
	}

	private static class FilterSlot extends Slot {
		private final java.util.function.Predicate<ItemStack> filter;

		FilterSlot(net.minecraft.world.Container container, int index, int x, int y, java.util.function.Predicate<ItemStack> filter) {
			super(container, index, x, y);
			this.filter = filter;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return filter.test(stack);
		}
	}
}
