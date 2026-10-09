package com.example.mechframe.client.mixin;

import com.example.mechframe.client.MechInput;
import com.example.mechframe.entity.MechEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Jet form: the mouse steers the frame (yaw/pitch), exactly like the base mod's aircraft - unless
 * free-look is held. The vanilla look change still happens (the camera is locked to the nose
 * anyway), so the pilot's own facing stays meaningful for free-look and for the switch back. */
@Mixin(Entity.class)
public abstract class EntityLookMixin {

	private static final float DEGREES_PER_UNIT = 0.08f;

	@Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"))
	private void mechframe$steerJet(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof PlayerEntity player) || !player.isMainPlayer()) {
			return;
		}
		if (player.getVehicle() instanceof MechEntity mech && mech.getControllingPassenger() == player
				&& mech.isJet() && !mech.tudursvehiclemod$isEffectiveFreeLook(player)) {
			MechInput.flightYaw += (float) (cursorDeltaX * DEGREES_PER_UNIT);
			MechInput.flightPitch += (float) (cursorDeltaY * DEGREES_PER_UNIT);
		}
	}
}
