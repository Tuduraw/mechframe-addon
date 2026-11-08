package com.example.mechframe.garage;

import com.example.mechframe.MechFrame;
import com.example.mechframe.item.MechPartItem;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 54 slots of part storage. Only part items go in (any other item is refused by the slots). */
public class GarageBlockEntity extends BlockEntity implements Container, ExtendedScreenHandlerFactory<BlockPos> {

	public static final int SIZE = 54;
	/** How far a frame may be parked from the garage to be refitted by it (blocks). */
	public static final double RANGE = 16.0;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public GarageBlockEntity(BlockPos pos, BlockState state) {
		super(MechFrame.GARAGE_ENTITY, pos, state);
	}

	/** Index of a slot holding one of the given part, or -1. */
	public int find(Identifier partId) {
		for (int i = 0; i < SIZE; i++) {
			ItemStack stack = this.items.get(i);
			if (!stack.isEmpty() && partId.equals(MechPartItem.partId(stack))) {
				return i;
			}
		}
		return -1;
	}

	/** Puts a whole stack in (merging with matching stacks first). False, and nothing changed, if it doesn't fit. */
	public boolean insert(ItemStack stack) {
		int remaining = stack.getCount();
		for (int i = 0; i < SIZE && remaining > 0; i++) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
				remaining -= Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
			}
		}
		for (int i = 0; i < SIZE && remaining > 0; i++) {
			if (this.items.get(i).isEmpty()) {
				remaining -= Math.min(remaining, stack.getMaxStackSize());
			}
		}
		if (remaining > 0) {
			return false;
		}
		remaining = stack.getCount();
		for (int i = 0; i < SIZE && remaining > 0; i++) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
				int move = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
				slot.grow(move);
				remaining -= move;
			}
		}
		for (int i = 0; i < SIZE && remaining > 0; i++) {
			if (this.items.get(i).isEmpty()) {
				int move = Math.min(remaining, stack.getMaxStackSize());
				this.items.set(i, stack.copyWithCount(move));
				remaining -= move;
			}
		}
		this.setChanged();
		return true;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		this.setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem() instanceof MechPartItem;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.level != null && this.level.getBlockEntity(this.worldPosition) == this
				&& player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	protected void loadAdditional(ValueInput view) {
		super.loadAdditional(view);
		this.items.clear();
		ContainerHelper.loadAllItems(view, this.items);
	}

	@Override
	protected void saveAdditional(ValueOutput view) {
		super.saveAdditional(view);
		ContainerHelper.saveAllItems(view, this.items, true);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.mechframe.garage");
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return this.worldPosition;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new GarageScreenHandler(syncId, playerInventory, this, this.worldPosition);
	}
}
