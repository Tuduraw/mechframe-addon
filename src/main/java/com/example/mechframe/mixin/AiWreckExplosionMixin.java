package com.example.mechframe.mixin;

import com.example.mechframe.ai.MechServerConfig;
import com.example.mechframe.entity.MechEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The explosion of a destroyed AI frame (the base mod makes it a MOB explosion, which breaks
 * blocks) is re-issued without block damage or fire unless MechServerConfig#aiBlockDamage. */
@Mixin(Level.class)
public abstract class AiWreckExplosionMixin {

	@Inject(method = "explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)V",
			at = @At("HEAD"), cancellable = true)
	private void mechframe$noAiWreckBlockDamage(Entity entity, double x, double y, double z, float power, boolean createFire,
			Level.ExplosionInteraction sourceType, CallbackInfo ci) {
		if (sourceType == Level.ExplosionInteraction.NONE || MechServerConfig.aiBlockDamage
				|| !(entity instanceof MechEntity mech) || mech.getBrain() == null) {
			return;
		}
		ci.cancel();
		((Level) (Object) this).explode(entity, x, y, z, power, false, Level.ExplosionInteraction.NONE);
	}
}
