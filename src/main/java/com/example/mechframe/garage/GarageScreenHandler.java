package com.example.mechframe.garage;

import com.example.mechframe.MechFrame;
import com.example.mechframe.item.MechPartItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Garage storage (9x6, part items only) plus the player's inventory. The assembly side of the
 * screen is not slot-based: it sends AssemblePayload and the server moves parts between the garage
 * and the frame (see AssemblyService), and the slot contents follow through normal syncing. */
public class GarageScreenHandler extends AbstractContainerMenu {

	public static final int STORAGE_X = 8;
	public static final int STORAGE_Y = 18;
	public static final int PLAYER_Y = 140;

	private final Container inventory;
	private final BlockPos pos;

	public GarageScreenHandler(int syncId, Inventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, new SimpleContainer(GarageBlockEntity.SIZE), pos);
	}

	public GarageScreenHandler(int syncId, Inventory playerInventory, Container inventory, BlockPos pos) {
		super(MechFrame.GARAGE_SCREEN, syncId);
		this.inventory = inventory;
		this.pos = pos;
		inventory.startOpen(playerInventory.player);
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9, STORAGE_X + col * 18, STORAGE_Y + row * 18) {
					@Override
					public boolean mayPlace(ItemStack stack) {
						return stack.getItem() instanceof MechPartItem;
					}
				});
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, PLAYER_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_Y + 58));
		}
	}

	public BlockPos getPos() {
		return this.pos;
	}

	public Container getInventory() {
		return this.inventory;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.inventory.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack original = slot.getItem();
			result = original.copy();
			if (index < GarageBlockEntity.SIZE) {
				if (!this.moveItemStackTo(original, GarageBlockEntity.SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (original.getItem() instanceof MechPartItem) {
				if (!this.moveItemStackTo(original, 0, GarageBlockEntity.SIZE, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				return ItemStack.EMPTY;
			}
			if (original.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}
}
