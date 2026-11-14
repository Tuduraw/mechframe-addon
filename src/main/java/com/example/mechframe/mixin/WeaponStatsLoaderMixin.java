package com.example.mechframe.mixin;

import com.example.mechframe.part.MountSlot;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Resolves this mod's per-mount weapon aliases ("mf_burst_rifle#l_arm", see
 * MountSlot#weaponAlias) to the weapon file they stand for. Names without the separator - every
 * other vehicle's weapons - pass through untouched. */
@Mixin(value = WeaponStatsLoader.class, remap = false)
public abstract class WeaponStatsLoaderMixin {

	/** Alias -> plain weapon name. get() is asked several times per weapon per tick; the base mod
	 * caches its answers by the name it is given (after this rewrite), so cutting the alias with a
	 * new substring every call would be the only allocation left on that path. */
	@Unique
	private static final java.util.Map<String, String> mechframe$plainNames = new java.util.concurrent.ConcurrentHashMap<>();

	@ModifyVariable(method = "get", at = @At("HEAD"), argsOnly = true)
	private static String mechframe$stripMountAlias(String name) {
		if (name == null || name.indexOf(MountSlot.ALIAS_SEPARATOR) <= 0) {
			return name;
		}
		return mechframe$plainNames.computeIfAbsent(name, alias -> alias.substring(0, alias.indexOf(MountSlot.ALIAS_SEPARATOR)));
	}
}
