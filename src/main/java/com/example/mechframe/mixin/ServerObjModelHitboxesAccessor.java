package com.example.mechframe.mixin;

import com.example.tudursvehiclemod.asset.ServerObjModelHitboxes;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The base mod's per-model grouped-triangle cache, which every hit-detection mesh it builds is
 * derived from. An assembled frame has no OBJ file of its own (it is put together from part models
 * at run time), so asm.HitMeshBuilder puts its triangles in here under a generated model id before
 * the base mod first asks for them. Read-only access to an existing map - no base-mod behaviour is
 * changed. */
@Mixin(value = ServerObjModelHitboxes.class, remap = false)
public interface ServerObjModelHitboxesAccessor {

	@Accessor("GROUPED_CACHE")
	static Map<Identifier, Optional<Map<String, List<float[]>>>> mechframe$groupedCache() {
		throw new AssertionError();
	}
}
