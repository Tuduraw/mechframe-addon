package com.example.mechframe.garage;

import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.item.MechPartItem;
import com.example.mechframe.part.Assembly;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Server-side refitting. The rules that keep it from duplicating parts:
 * <ul>
 *   <li>A part comes either from the frame's own stock (its preset - free, any number of times for
 *       that frame) or from the garage's storage (one item consumed).</li>
 *   <li>A part leaving the frame goes back into the garage only if it came from the garage; a stock
 *       part simply returns to "available as stock".</li>
 *   <li>Nothing changes unless every step fits (returned part has room, the new part exists).</li>
 * </ul> */
public final class AssemblyService {

	private AssemblyService() {
	}

	/** partId null = empty the mount (only for mounts that may be empty). */
	public static void apply(ServerPlayerEntity player, BlockPos garagePos, int mechId, MountSlot mount, Identifier partId) {
		if (!(player.getEntityWorld().getBlockEntity(garagePos) instanceof GarageBlockEntity garage) || !garage.canPlayerUse(player)) {
			return;
		}
		Entity entity = player.getEntityWorld().getEntityById(mechId);
		if (!(entity instanceof MechEntity mech) || !inRange(mech, garagePos)) {
			fail(player, "message.mechframe.garage.no_frame");
			return;
		}
		if (mech.tudursvehiclemod$isDestroyed()) {
			fail(player, "message.mechframe.garage.destroyed");
			return;
		}
		if (mech.getControllingPassenger() != null) {
			fail(player, "message.mechframe.garage.occupied");
			return;
		}
		Assembly current = mech.getAssembly();
		Assembly stock = mech.getStockAssembly();
		if (current.isLocked(mount)) {
			fail(player, "message.mechframe.garage.integrated");
			return;
		}
		Identifier installed = current.get(mount);
		if (partId == null ? installed == null : partId.equals(installed)) {
			return;
		}
		if (partId == null && mount.required()) {
			fail(player, "message.mechframe.garage.required");
			return;
		}
		boolean fromStock = false;
		int garageSlot = -1;
		if (partId != null) {
			MechPart part = PartRegistry.getOrNull(partId);
			if (part == null || !mount.accepts(part)) {
				fail(player, "message.mechframe.garage.incompatible");
				return;
			}
			fromStock = partId.equals(stock.get(mount));
			if (!fromStock) {
				garageSlot = garage.find(partId);
				if (garageSlot < 0) {
					fail(player, "message.mechframe.garage.not_stored");
					return;
				}
			}
		}
		// Take the new part out first (that may free the very slot the old one needs), then return
		// the old one; if it doesn't fit, put the new one back and change nothing.
		if (installed != null && !current.isStock(mount) && PartRegistry.getOrNull(installed) == null) {
			// Its JSON was removed by a data reload: swapping it out would delete the item for good.
			fail(player, "message.mechframe.garage.unknown_part");
			return;
		}
		ItemStack taken = garageSlot >= 0 ? garage.removeStack(garageSlot, 1) : ItemStack.EMPTY;
		if (installed != null && !current.isStock(mount)) {
			MechPart old = PartRegistry.getOrNull(installed);
			if (old != null && !garage.insert(MechPartItem.stackFor(old))) {
				if (!taken.isEmpty()) {
					garage.insert(taken);
				}
				fail(player, "message.mechframe.garage.full");
				return;
			}
		}
		mech.setAssembly(current.with(mount, partId, fromStock));
		garage.markDirty();
	}

	/** Puts every mount back to the frame's stock part, returning garage parts to the garage. */
	public static void resetToStock(ServerPlayerEntity player, BlockPos garagePos, int mechId) {
		Entity entity = player.getEntityWorld().getEntityById(mechId);
		if (!(entity instanceof MechEntity mech)) {
			return;
		}
		Assembly stock = mech.getStockAssembly();
		for (MountSlot mount : MountSlot.values()) {
			apply(player, garagePos, mechId, mount, stock.get(mount));
		}
	}

	public static boolean inRange(MechEntity mech, BlockPos garagePos) {
		return mech.squaredDistanceTo(Vec3d.ofCenter(garagePos)) <= GarageBlockEntity.RANGE * GarageBlockEntity.RANGE;
	}

	private static void fail(ServerPlayerEntity player, String key) {
		player.sendMessage(Text.translatable(key), true);
	}
}
