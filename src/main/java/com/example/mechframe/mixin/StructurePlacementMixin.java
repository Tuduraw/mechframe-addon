package com.example.mechframe.mixin;

import com.example.mechframe.ai.OutpostGeneration;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Turns generation of Mech Frame's bases off when the server config says so (see OutpostGeneration),
 * and exposes the placement's salt that recognises their structure set. */
@Mixin(StructurePlacement.class)
public abstract class StructurePlacementMixin implements OutpostGeneration.Salted {
	@Shadow
	@Final
	private int salt;

	@Override
	public int mechframe$salt() {
		return this.salt;
	}

	@Inject(method = "isStructureChunk", at = @At("HEAD"), cancellable = true)
	private void mechframe$basesDisabled(ChunkGeneratorStructureState state, int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
		if (this.salt == OutpostGeneration.SALT && !OutpostGeneration.enabled()) {
			cir.setReturnValue(false);
		}
	}
}
