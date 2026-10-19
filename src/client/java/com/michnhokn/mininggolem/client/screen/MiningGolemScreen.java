package com.michnhokn.mininggolem.client.screen;

import com.michnhokn.mininggolem.MiningGolem;
import com.michnhokn.mininggolem.client.network.ClientNetworking;
import com.michnhokn.mininggolem.entity.MiningGolemEntity;
import com.michnhokn.mininggolem.inventory.MiningGolemMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class MiningGolemScreen extends AbstractContainerScreen<MiningGolemMenu> {
	private static final Identifier TEXTURE = MiningGolem.id("textures/gui/mining_golem.png");
	private static final int IMAGE_WIDTH = 256;
	private static final int IMAGE_HEIGHT = 246;

	private static final Direction[] CARDINALS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	private static final String[] DIRECTION_KEYS = {"north", "east", "south", "west"};

	private Button toggleButton;
	private EditBox nameBox;
	private final Button[] directionButtons = new Button[4];

	public MiningGolemScreen(MiningGolemMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected void init() {
		super.init();
		var layout = new MiningGolemMenu.MenuLayout();

		nameBox = new EditBox(this.font, this.leftPos + layout.nameX(), this.topPos + layout.nameY(),
				layout.nameWidth(), 16, Component.translatable("mininggolem.gui.name_hint"));
		nameBox.setMaxLength(32);
		nameBox.setValue(this.title.getString());
		nameBox.setHint(Component.translatable("mininggolem.gui.name_hint"));
		nameBox.setTooltip(Tooltip.create(Component.translatable("mininggolem.gui.rename_hint")));
		addRenderableWidget(nameBox);

		for (int i = 0; i < 4; i++) {
			Direction direction = CARDINALS[i];
			final int ordinal = direction.ordinal();
			Button button = addRenderableWidget(Button.builder(
					Component.literal(direction.name().substring(0, 1)),
					b -> ClientNetworking.sendSetDirection(ordinal))
					.bounds(this.leftPos + layout.dirX() + i * layout.dirStep(), this.topPos + layout.dirY(),
							layout.dirButtonWidth(), layout.dirButtonHeight())
					.build());
			button.setTooltip(Tooltip.create(Component.translatable("mininggolem.gui.direction." + DIRECTION_KEYS[i])));
			directionButtons[i] = button;
		}

		toggleButton = addRenderableWidget(Button.builder(
				Component.translatable("mininggolem.gui.start"),
				button -> ClientNetworking.sendToggle())
				.bounds(this.leftPos + layout.buttonX(), this.topPos + layout.buttonY(),
						layout.buttonWidth(), layout.buttonHeight())
				.build());
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (nameBox != null && nameBox.isFocused()) {
			if (event.isConfirmation()) {
				ClientNetworking.sendRename(nameBox.getValue());
				return true;
			}
			if (event.isEscape()) {
				// first Escape stops typing instead of closing the screen
				nameBox.setFocused(false);
				return true;
			}
			nameBox.keyPressed(event);
			// swallow every other key (inventory, drop, ...) while typing
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	protected void containerTick() {
		boolean mining = this.menu.getStatus() == MiningGolemEntity.GolemStatus.MINING;
		toggleButton.setMessage(Component.translatable(mining ? "mininggolem.gui.stop" : "mininggolem.gui.start"));
		// gray out the direction the golem is currently facing
		int current = this.menu.getDirectionOrdinal();
		for (int i = 0; i < 4; i++) {
			directionButtons[i].active = CARDINALS[i].ordinal() != current;
		}
	}

	private static int statusColor(MiningGolemEntity.GolemStatus status) {
		return switch (status) {
			case MINING -> 0x55FF55;
			case PAUSED_TORCHES, PAUSED_PICKAXES, PAUSED_LIQUID -> 0xFFAA00;
			case INVENTORY_FULL -> 0xFF5555;
			case DONE -> 0x55FFFF;
			case IDLE -> 0xA0A0A0;
		};
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos,
				0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, 256, 256);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		var layout = new MiningGolemMenu.MenuLayout();
		graphics.text(this.font, this.title, this.leftPos + layout.titleX(), this.topPos + layout.titleY(), 0x404040);

		// status line lives in the header, right-aligned, color-coded with a state dot
		var status = this.menu.getStatus();
		var statusText = Component.translatable(status.translationKey());
		int color = statusColor(status);
		int statusX = this.leftPos + layout.statusRight() - this.font.width(statusText);
		graphics.text(this.font, statusText, statusX, this.topPos + layout.statusY(), color);
		int dotX = statusX - 6;
		int dotY = this.topPos + layout.statusY() + 3;
		graphics.fill(dotX, dotY, dotX + 3, dotY + 3, color);

		graphics.text(this.font, Component.translatable("mininggolem.gui.direction"),
				this.leftPos + layout.dirX(), this.topPos + layout.dirLabelY(), 0x404040);
		graphics.text(this.font, Component.translatable("container.inventory"),
				this.leftPos + layout.invLabelX(), this.topPos + layout.invLabelY(), 0x404040);
	}
}
