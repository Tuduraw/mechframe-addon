package com.example.mechframe.client.mixin;

import com.example.mechframe.client.CameraFx;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechLayout;
import com.example.tudursvehiclemod.client.AircraftCameraZoomState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Third-person camera for a frame in robot form: orbits a pivot high above the frame's centre at a
 * distance scaled to its size (the base mod's zoom keys still add to it), clipped against blocks,
 * plus CameraFx shake in every view. Runs after the base mod's own CameraMixin (higher priority, same
 * TAIL), which keeps handling the first-person eye and the jet form's nose-locked rotation. */
@Mixin(value = Camera.class, priority = 2100)
public abstract class MechCameraMixin {

	private static final double JET_CENTRE_Y = 0.95;
	private static final double JET_CHASE_DISTANCE = 10.0;

	@Invoker("setPos")
	public abstract void mechframe$setPos(Vec3d pos);

	@Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
	private void mechframe$applyMechCamera(World area, Entity focused, boolean thirdPerson, boolean inverseView,
			float tickProgress, CallbackInfo ci) {
		if (!(focused instanceof PlayerEntity player) || !(player.getVehicle() instanceof MechEntity mech)) {
			return;
		}
		Camera self = (Camera) (Object) this;
		if (mech.getControllingPassenger() == player) {
			CameraFx.applyShake(self.getRotation());
		}
		MechLayout layout = mech.getLayout();
		if (!thirdPerson || layout == null) {
			return;
		}
		double zoom = AircraftCameraZoomState.currentDistance - AircraftCameraZoomState.MIN_DISTANCE;
		Vector3f pull = new Vector3f(0, 0, 1);
		self.getRotation().transform(pull);
		Vec3d pivot;
		double distance;
		if (mech.getTransformProgress(tickProgress) > 0.5f) {
			// Jet form: keep the base mod's nose-locked rotation, but its fixed 4 block pull-back puts
			// the camera between the wings. Orbit the airframe's centre from a chase distance instead,
			// lifted along the camera's own up axis so the jet sits just under the reticle.
			Vector3f up = new Vector3f(0, 1, 0);
			self.getRotation().transform(up);
			pivot = mech.getLerpedPos(tickProgress).add(0, JET_CENTRE_Y, 0).add(up.x * 1.6, up.y * 1.6, up.z * 1.6);
			distance = JET_CHASE_DISTANCE + zoom;
		} else {
			pivot = CameraFx.pivot(mech, layout, tickProgress);
			distance = layout.height * 1.1 + 2.5 + zoom;
		}
		Vec3d desired = pivot.add(pull.x * distance, pull.y * distance, pull.z * distance);
		BlockHitResult hit = area.raycast(new RaycastContext(pivot, desired, RaycastContext.ShapeType.VISUAL,
				RaycastContext.FluidHandling.NONE, player));
		double actual = distance;
		if (hit.getType() == HitResult.Type.BLOCK) {
			actual = Math.max(0.5, Math.min(distance, pivot.distanceTo(hit.getPos()) - 0.3));
		}
		this.mechframe$setPos(pivot.add(pull.x * actual, pull.y * actual, pull.z * actual));
	}
}
