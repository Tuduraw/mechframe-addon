package com.example.mechframe.garage;

import com.example.mechframe.MechFrame;
import com.example.mechframe.item.MechPartItem;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/** 54 slots of part storage. Only part items go in (any other item is refused by the slots). */
public class GarageBlockEntity extends BlockEntity implements Inventory, ExtendedScreenHandlerFactory<BlockPos> {

	public static final int SIZE = 54;
	/** How far a frame may be parked from the garage to be refitted by it (blocks). */
	public static final double RANGE = 16.0;

	private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

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
			if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
				remaining -= Math.min(remaining, slot.getMaxCount() - slot.getCount());
			}
		}
		for (int i = 0; i < SIZE && remaining > 0; i++) {
			if (this.items.get(i).isEmpty()) {
				remaining -= Math.min(remaining, stack.getMaxCount());
			}
		}
		if (remaining > 0) {
			return false;
		}
		remaining = stack.getCount();
		for (int i = 0; i < SIZE && remaining > 0; i++) {
			ItemStack slot = this.items.get(i);
			if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
				int move = Math.min(remaining, slot.getMaxCount() - slot.getCount());
				slot.increment(move);
				remaining -= move;
			}
		}
		for (int i = 0; i < SIZE && remaining > 0; i++) {
			if (this.items.get(i).isEmpty()) {
				int move = Math.min(remaining, stack.getMaxCount());
				this.items.set(i, stack.copyWithCount(move));
				remaining -= move;
			}
		}
		this.markDirty();
		return true;
	}

	@Override
	public int size() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(this.items, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		this.markDirty();
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return stack.getItem() instanceof MechPartItem;
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return this.world != null && this.world.getBlockEntity(this.pos) == this
				&& player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clear() {
		this.items.clear();
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.items.clear();
		Inventories.readData(view, this.items);
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, this.items, true);
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.mechframe.garage");
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return this.pos;
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new GarageScreenHandler(syncId, playerInventory, this, this.pos);
	}
}
