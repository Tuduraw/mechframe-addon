package com.example.mechframe.client.mixin;

import com.example.mechframe.client.CameraFx;
import com.example.mechframe.client.MechInput;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Speed / quick-boost field-of-view kick while piloting a frame (CameraFx). */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void mechframe$speedFov(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		if (MechInput.pilotedMech(Minecraft.getInstance()) != null) {
			cir.setReturnValue(cir.getReturnValueF() * CameraFx.fovMultiplier());
		}
	}
}
