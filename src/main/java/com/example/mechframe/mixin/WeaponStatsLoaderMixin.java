package com.example.mechframe.mixin;

import com.example.mechframe.part.MountSlot;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Resolves this mod's per-mount weapon aliases ("mf_burst_rifle#l_arm", see
 * MountSlot#weaponAlias) to the weapon file they stand for. Names without the separator - every
 * other vehicle's weapons - pass through untouched. */
@Mixin(value = WeaponStatsLoader.class, remap = false)
public abstract class WeaponStatsLoaderMixin {

	@ModifyVariable(method = "get", at = @At("HEAD"), argsOnly = true)
	private static String mechframe$stripMountAlias(String name) {
		if (name == null) {
			return null;
		}
		int i = name.indexOf(MountSlot.ALIAS_SEPARATOR);
		return i > 0 ? name.substring(0, i) : name;
	}
}
