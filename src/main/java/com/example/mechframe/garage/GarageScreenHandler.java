package com.example.mechframe.garage;

import com.example.mechframe.MechFrame;
import com.example.mechframe.item.MechPartItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** Garage storage (9x6, part items only) plus the player's inventory. The assembly side of the
 * screen is not slot-based: it sends AssemblePayload and the server moves parts between the garage
 * and the frame (see AssemblyService), and the slot contents follow through normal syncing. */
public class GarageScreenHandler extends ScreenHandler {

	public static final int STORAGE_X = 8;
	public static final int STORAGE_Y = 18;
	public static final int PLAYER_Y = 140;

	private final Inventory inventory;
	private final BlockPos pos;

	public GarageScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, new SimpleInventory(GarageBlockEntity.SIZE), pos);
	}

	public GarageScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos pos) {
		super(MechFrame.GARAGE_SCREEN, syncId);
		this.inventory = inventory;
		this.pos = pos;
		inventory.onOpen(playerInventory.player);
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(inventory, col + row * 9, STORAGE_X + col * 18, STORAGE_Y + row * 18) {
					@Override
					public boolean canInsert(ItemStack stack) {
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

	public Inventory getInventory() {
		return this.inventory;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasStack()) {
			ItemStack original = slot.getStack();
			result = original.copy();
			if (index < GarageBlockEntity.SIZE) {
				if (!this.insertItem(original, GarageBlockEntity.SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (original.getItem() instanceof MechPartItem) {
				if (!this.insertItem(original, 0, GarageBlockEntity.SIZE, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				return ItemStack.EMPTY;
			}
			if (original.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return result;
	}
}
