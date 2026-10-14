package com.example.mechframe.mixin;

import com.example.mechframe.ai.MechServerConfig;
import com.example.mechframe.entity.MechEntity;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weapons of AI-driven frames do not break blocks (unless MechServerConfig#aiBlockDamage): their
 * explosions lose the block-breaking and fire, piercing shots stop at the block. Damage, blast and
 * effects are unchanged, and a player's own shots are never affected. Same approach as Enemy Vehicle
 * Addon, so the two can be installed together. */
@Mixin(value = VehicleProjectileEntity.class, remap = false)
public abstract class AiProjectileBlockDamageMixin {

	@Shadow
	protected boolean explosionDestroysBlocks;

	@Shadow
	protected boolean flaming;

	private boolean mechframe$aiShotWithoutBlockDamage() {
		if (MechServerConfig.aiBlockDamage) {
			return false;
		}
		AbstractVehicleEntity v = ((VehicleProjectileEntity) (Object) this).tudursvehiclemod$getFiringVehicle();
		return v instanceof MechEntity mech && mech.getBrain() != null && mech.getControllingPassenger() == null;
	}

	@Inject(method = "tudursvehiclemod$explodeIfConfigured", at = @At("HEAD"))
	private void mechframe$noAiBlockDamage(double x, double y, double z, CallbackInfo ci) {
		if (this.mechframe$aiShotWithoutBlockDamage()) {
			this.explosionDestroysBlocks = false;
			this.flaming = false;
		}
	}

	@Inject(method = "tudursvehiclemod$tryPierceBlock", at = @At("HEAD"), cancellable = true)
	private void mechframe$noAiPiercing(BlockHitResult hit, CallbackInfoReturnable<Boolean> cir) {
		if (this.mechframe$aiShotWithoutBlockDamage()) {
			cir.setReturnValue(false);
		}
	}
}
