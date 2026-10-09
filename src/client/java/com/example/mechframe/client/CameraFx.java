package com.example.mechframe.client;

import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/** Pilot-only camera feel: speed-dependent field of view, punches on quick boosts and landings, and
 * a short decaying shake. Pure presentation - none of it touches aiming (the aim ray is computed from
 * the frame's position and the pilot's view direction, not from the camera). */
public final class CameraFx {

	private static float fovKick;
	private static float speedFov;
	private static float shake;
	private static long lastNanos;
	private static double smoothedPivotY = Double.NaN;
	private static int pivotEntityId = -1;

	private CameraFx() {
	}

	public static void kickFov(float amount) {
		fovKick = Math.min(0.18f, fovKick + amount);
	}

	public static void addShake(float amount) {
		shake = Math.min(2.5f, shake + amount);
	}

	/** Once per client tick. */
	public static void tick(MinecraftClient client) {
		MechEntity mech = MechInput.pilotedMech(client);
		float target = 0f;
		if (mech != null && mech.getLayout() != null) {
			Vec3d v = mech.getVelocity();
			float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
			if (mech.isJet()) {
				target = MathHelper.clamp(speed / 3.5f, 0f, 1f) * 0.06f + (mech.isAfterburnerVisual() ? 0.06f : 0f);
			} else {
				float boost = Math.max(0.1f, mech.getStats().boostSpeed());
				target = MathHelper.clamp(speed / boost, 0f, 1.3f) * (mech.isBoostingVisual() ? 0.09f : 0.03f);
			}
		}
		speedFov += (target - speedFov) * 0.12f;
		fovKick *= 0.82f;
		shake *= 0.78f;
		if (shake < 0.01f) {
			shake = 0f;
		}
	}

	public static float fovMultiplier() {
		return 1f + speedFov + fovKick;
	}

	/** Small rotation noise composed onto the camera (degrees scale with shake). */
	public static void applyShake(Quaternionf rotation) {
		if (shake <= 0f) {
			return;
		}
		double t = System.nanoTime() / 1.0e9;
		float yaw = (float) (Math.sin(t * 47.0) * 0.6 + Math.sin(t * 83.0) * 0.4) * shake * 0.9f;
		float pitch = (float) (Math.sin(t * 59.0 + 1.3) * 0.6 + Math.sin(t * 97.0) * 0.4) * shake * 0.9f;
		rotation.rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch));
	}

	/** Third-person pivot: above the frame's centre, with the vertical part smoothed so step-ups,
	 * jumps and landings glide instead of snapping. Horizontal stays exact (strafing must not slide
	 * the crosshair off the aim line). */
	public static Vec3d pivot(MechEntity mech, MechLayout layout, float tickDelta) {
		Vec3d pos = mech.getLerpedPos(tickDelta);
		double targetY = pos.y + layout.cameraPivotY + mech.getStepUpVisualOffset(tickDelta);
		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0.016 : Math.min(0.1, (now - lastNanos) / 1.0e9);
		lastNanos = now;
		if (pivotEntityId != mech.getId() || Double.isNaN(smoothedPivotY) || Math.abs(smoothedPivotY - targetY) > 12) {
			smoothedPivotY = targetY;
			pivotEntityId = mech.getId();
		}
		double k = 1.0 - Math.exp(-dt * 9.0);
		smoothedPivotY += (targetY - smoothedPivotY) * k;
		// Smooth small bumps only: in a long fall or hover climb the camera must not trail by blocks.
		smoothedPivotY = MathHelper.clamp(smoothedPivotY, targetY - 1.0, targetY + 1.0);
		return new Vec3d(pos.x, smoothedPivotY, pos.z);
	}
}
