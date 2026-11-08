package com.example.mechframe.client.mixin;

import com.example.mechframe.client.MechInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While piloting a frame the attack button is the left-arm trigger (MechInput), so the vanilla
 * attack / block breaking it would also do is suppressed. */
@Mixin(Minecraft.class)
public abstract class MinecraftClientAttackMixin {

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void mechframe$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (MechInput.pilotedMech((Minecraft) (Object) this) != null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void mechframe$noBreaking(boolean breaking, CallbackInfo ci) {
		if (MechInput.pilotedMech((Minecraft) (Object) this) != null) {
			ci.cancel();
		}
	}
}
