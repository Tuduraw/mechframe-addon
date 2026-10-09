package com.example.mechframe.client.mixin;

import com.example.mechframe.client.MechInput;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While piloting a frame the attack button is the left-arm trigger (MechInput), so the vanilla
 * attack / block breaking it would also do is suppressed. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientAttackMixin {

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void mechframe$noAttack(CallbackInfoReturnable<Boolean> cir) {
		if (MechInput.pilotedMech((MinecraftClient) (Object) this) != null) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void mechframe$noBreaking(boolean breaking, CallbackInfo ci) {
		if (MechInput.pilotedMech((MinecraftClient) (Object) this) != null) {
			ci.cancel();
		}
	}
}
