package com.example.mechframe.mixin;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The base mod's synced health key, and its per-weapon state arrays. A frame's maximum health is the sum of its parts' armour, so
 * it changes when the frame is re-assembled; MechEntity keeps the current health at the same
 * fraction of the new maximum, which needs to write the value itself (the base mod only exposes
 * damage and percentage repair). */
@Mixin(value = AbstractVehicleEntity.class, remap = false)
public interface AbstractVehicleEntityAccessor {

	@Accessor("HEALTH")
	static TrackedData<Float> mechframe$healthKey() {
		throw new AssertionError();
	}

	/** The synced fuel key: turbo mode burns fuel faster than the base mod's throttle-based use. */
	@Accessor("FUEL")
	static TrackedData<Float> mechframe$fuelKey() {
		throw new AssertionError();
	}

	// Per-weapon state, indexed by position in the definition's weapon list. A refit changes which
	// weapon sits at which index, so MechEntity re-keys these by mount (see remapWeaponState).

	@Accessor("weaponAmmo")
	int[] mechframe$getWeaponAmmo();

	@Accessor("weaponAmmo")
	void mechframe$setWeaponAmmo(int[] value);

	@Accessor("weaponReloadTicksRemaining")
	int[] mechframe$getWeaponReload();

	@Accessor("weaponReloadTicksRemaining")
	void mechframe$setWeaponReload(int[] value);

	@Accessor("weaponReserveAmmo")
	int[] mechframe$getWeaponReserve();

	@Accessor("weaponReserveAmmo")
	void mechframe$setWeaponReserve(int[] value);

	@Accessor("weaponFiringPositionIndex")
	int[] mechframe$getWeaponFiringPosition();

	@Accessor("weaponFiringPositionIndex")
	void mechframe$setWeaponFiringPosition(int[] value);

	@Accessor("weaponHeat")
	float[] mechframe$getWeaponHeat();

	@Accessor("weaponHeat")
	void mechframe$setWeaponHeat(float[] value);

	@Accessor("weaponMode")
	int[] mechframe$getWeaponMode();

	@Accessor("weaponMode")
	void mechframe$setWeaponMode(int[] value);
}
