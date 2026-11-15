package com.example.mechframe.mixin;

import com.example.mechframe.ai.OutpostGeneration;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The configured density of Mech Frame's bases (see OutpostGeneration): their structure set's spacing and
 * separation, both where the game places them (getPotentialStructureChunk) and where it searches for them
 * (/locate and maps use spacing()). Other structure sets are untouched. */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class OutpostPlacementMixin {
	@Shadow
	@Final
	private int spacing;
	@Shadow
	@Final
	private int separation;

	@Unique
	private boolean mechframe$isBases() {
		return ((OutpostGeneration.Salted) (Object) this).mechframe$salt() == OutpostGeneration.SALT;
	}

	@Unique
	private int mechframe$spacing() {
		return this.mechframe$isBases() ? OutpostGeneration.spacing(this.spacing) : this.spacing;
	}

	@Unique
	private int mechframe$separation() {
		return this.mechframe$isBases() ? OutpostGeneration.separation(this.spacing, this.separation) : this.separation;
	}

	@Inject(method = "spacing", at = @At("HEAD"), cancellable = true)
	private void mechframe$configuredSpacing(CallbackInfoReturnable<Integer> cir) {
		if (this.mechframe$isBases()) {
			cir.setReturnValue(this.mechframe$spacing());
		}
	}

	@Inject(method = "separation", at = @At("HEAD"), cancellable = true)
	private void mechframe$configuredSeparation(CallbackInfoReturnable<Integer> cir) {
		if (this.mechframe$isBases()) {
			cir.setReturnValue(this.mechframe$separation());
		}
	}

	@Redirect(method = "getPotentialStructureChunk", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;spacing:I"))
	private int mechframe$spacingField(RandomSpreadStructurePlacement self) {
		return this.mechframe$spacing();
	}

	@Redirect(method = "getPotentialStructureChunk", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/world/level/levelgen/structure/placement/RandomSpreadStructurePlacement;separation:I"))
	private int mechframe$separationField(RandomSpreadStructurePlacement self) {
		return this.mechframe$separation();
	}
}
