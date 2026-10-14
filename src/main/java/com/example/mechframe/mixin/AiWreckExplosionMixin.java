package com.example.mechframe.mixin;

import com.example.mechframe.ai.MechServerConfig;
import com.example.mechframe.entity.MechEntity;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The explosion of a destroyed AI frame (the base mod makes it a MOB explosion, which breaks
 * blocks) is re-issued without block damage or fire unless MechServerConfig#aiBlockDamage. */
@Mixin(World.class)
public abstract class AiWreckExplosionMixin {

	@Inject(method = "createExplosion(Lnet/minecraft/entity/Entity;DDDFZLnet/minecraft/world/World$ExplosionSourceType;)V",
			at = @At("HEAD"), cancellable = true)
	private void mechframe$noAiWreckBlockDamage(Entity entity, double x, double y, double z, float power, boolean createFire,
			World.ExplosionSourceType sourceType, CallbackInfo ci) {
		if (sourceType == World.ExplosionSourceType.NONE || MechServerConfig.aiBlockDamage
				|| !(entity instanceof MechEntity mech) || mech.getBrain() == null) {
			return;
		}
		ci.cancel();
		((World) (Object) this).createExplosion(entity, x, y, z, power, false, World.ExplosionSourceType.NONE);
	}
}
