package com.example.mechframe.part;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** The static geometry of one assembly: where each part sits in the frame's root space (entity
 * origin on the ground, +Z forward) at rest, in both forms, plus the points other systems need
 * (seat, eye, weapon pivots and muzzles). Shared by the definition factory (seats, weapon offsets),
 * the hit mesh builder and the client renderer, so all three agree by construction.
 *
 * <p>Built once per (assembly, registry generation) and cached; immutable afterwards - callers must
 * copy the matrices they intend to modify. */
public final class MechLayout {

	public enum Place { LEGS, CORE, HEAD, ARM_L, ARM_R, BOOSTER }

	public final Assembly assembly;
	public final MechStats stats;
	public final boolean valid;

	public final Map<Place, Matrix4f> robot = new EnumMap<>(Place.class);
	public final Map<MountSlot, Matrix4f> robotWeapon = new EnumMap<>(MountSlot.class);
	/** Root-space pivot of each weapon mount's base weapon part (shoulder for arm mounts). */
	public final Map<MountSlot, Vector3f> weaponPivot = new EnumMap<>(MountSlot.class);
	public final Map<MountSlot, Vector3f> robotMuzzle = new EnumMap<>(MountSlot.class);

	public final Vector3f seat = new Vector3f();
	public final Vector3f eye = new Vector3f();
	public final Vector3f hip = new Vector3f();
	/** Forward lean of the core about the hip (radians, legs' core_tilt). Head, arms and back weapons
	 * hang from the tilted core's points but stay level, so aiming is unaffected. */
	public final float coreTilt;
	/** Root-space shoulder points (left, right) and arm-local elbow/grip (left arm; mirror for right). */
	public final Vector3f shoulderL = new Vector3f();
	public final Vector3f shoulderR = new Vector3f();
	public final Vector3f elbow = new Vector3f();
	public final Vector3f grip = new Vector3f();
	/** Overall height to the top of the head, and the camera pivot / aim origin height. */
	public float height;
	public float cameraPivotY;
	public float collisionWidth;
	public float collisionHeight;

	// ---- jet form (transformable assemblies only) ----
	public final boolean canTransform;
	/** Per part slot, per OBJ group ("*" = default for unlisted groups): jet placement and delay. */
	public final Map<MountSlot, Map<String, MechPart.JetPose>> jetPoses = new EnumMap<>(MountSlot.class);
	public final Map<MountSlot, Matrix4f> jetWeapon = new EnumMap<>(MountSlot.class);
	public final Map<MountSlot, Vector3f> jetMuzzle = new EnumMap<>(MountSlot.class);
	public final Vector3f jetSeat = new Vector3f();
	public final Vector3f jetEye = new Vector3f();
	public float jetHeight = 1.6f;

	private MechLayout(Assembly assembly) {
		this.assembly = assembly;
		this.stats = MechStats.compute(assembly);
		MechPart legs = assembly.part(MountSlot.LEGS);
		MechPart core = assembly.part(MountSlot.CORE);
		MechPart head = assembly.part(MountSlot.HEAD);
		MechPart arms = assembly.part(MountSlot.ARMS);
		MechPart booster = assembly.part(MountSlot.BOOSTER);
		this.valid = legs != null && core != null && arms != null && legs.legs() != null;

		Vector3f hipPoint = legs != null ? legs.point("hip") : new Vector3f(0, 3, 0);
		this.hip.set(hipPoint);
		Matrix4f legsM = new Matrix4f();
		this.coreTilt = legs != null && legs.legs() != null ? (float) Math.toRadians(legs.legs().coreTilt()) : 0f;
		Matrix4f coreM = new Matrix4f().translation(hipPoint).rotateX(this.coreTilt);
		this.robot.put(Place.LEGS, legsM);
		this.robot.put(Place.CORE, coreM);
		Vector3f neck = core != null ? core.point("neck") : new Vector3f(0, 1.8f, 0);
		this.robot.put(Place.HEAD, level(coreM, neck));
		Vector3f shL = core != null ? core.point("shoulder_l") : new Vector3f(1.1f, 1.5f, 0);
		Vector3f shR = core != null && core.hasPoint("shoulder_r") ? core.point("shoulder_r") : new Vector3f(-shL.x, shL.y, shL.z);
		Matrix4f armL = level(coreM, shL);
		Matrix4f armR = level(coreM, shR);
		this.robot.put(Place.ARM_L, armL);
		this.robot.put(Place.ARM_R, armR);
		this.robot.put(Place.BOOSTER, new Matrix4f(coreM).translate(core != null ? core.point("back") : new Vector3f(0, 1.2f, -0.8f)));
		coreM.transformPosition(shL, this.shoulderL);
		coreM.transformPosition(shR, this.shoulderR);

		if (arms != null) {
			this.elbow.set(arms.point("elbow"));
			this.grip.set(arms.point("grip"));
		}
		Vector3f gripL = new Vector3f(this.grip);
		Vector3f gripR = new Vector3f(-this.grip.x, this.grip.y, this.grip.z);
		Vector3f backL = core != null ? core.point("back_l") : new Vector3f(0.7f, 1.9f, -0.5f);
		Vector3f backR = core != null && core.hasPoint("back_r") ? core.point("back_r") : new Vector3f(-backL.x, backL.y, backL.z);

		this.robotWeapon.put(MountSlot.L_ARM, new Matrix4f(armL).translate(gripL));
		this.robotWeapon.put(MountSlot.R_ARM, new Matrix4f(armR).translate(gripR));
		this.robotWeapon.put(MountSlot.L_BACK, level(coreM, backL));
		this.robotWeapon.put(MountSlot.R_BACK, level(coreM, backR));
		this.weaponPivot.put(MountSlot.L_ARM, new Vector3f(this.shoulderL));
		this.weaponPivot.put(MountSlot.R_ARM, new Vector3f(this.shoulderR));
		this.weaponPivot.put(MountSlot.L_BACK, coreM.transformPosition(backL, new Vector3f()));
		this.weaponPivot.put(MountSlot.R_BACK, coreM.transformPosition(backR, new Vector3f()));
		for (MountSlot mount : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK}) {
			MechPart weapon = assembly.part(mount);
			Vector3f muzzle = weapon != null ? weapon.point("muzzle") : new Vector3f(0, 0, 1);
			this.robotMuzzle.put(mount, this.robotWeapon.get(mount).transformPosition(muzzle, new Vector3f()));
		}

		if (core != null) {
			coreM.transformPosition(core.point("seat"), this.seat);
			coreM.transformPosition(core.hasPoint("eye") ? core.point("eye") : core.point("seat").add(0, 0.6f, 0), this.eye);
		}
		float headTop = head != null && head.hasPoint("top") ? head.point("top").y : 0.9f;
		this.height = coreM.transformPosition(neck, new Vector3f()).y + headTop;
		this.cameraPivotY = this.height * 0.82f;
		this.collisionWidth = legs != null && legs.legs() != null ? legs.legs().collisionWidth() : 1.8f;
		this.collisionHeight = Math.max(2.0f, this.height * 0.8f);

		// ---- jet form ----
		boolean transformable = this.valid && assembly.canTransform();
		if (transformable) {
			for (MountSlot slot : new MountSlot[]{MountSlot.HEAD, MountSlot.CORE, MountSlot.ARMS, MountSlot.LEGS, MountSlot.BOOSTER}) {
				MechPart part = assembly.part(slot);
				if (part == null || part.jetPoses().isEmpty()) {
					transformable = false;
					break;
				}
				this.jetPoses.put(slot, part.jetPoses());
			}
		}
		this.canTransform = transformable;
		if (transformable) {
			Matrix4f coreJ = jetMatrix(MountSlot.CORE, "core");
			Matrix4f foreL = jetMatrix(MountSlot.ARMS, "l_fore");
			Matrix4f foreR = jetMatrix(MountSlot.ARMS, "r_fore");
			// Weapons stay on their hand / back mount and swivel there to point along the nose (the
			// jet fires straight ahead), so they stay attached through the transformation.
			this.jetWeapon.put(MountSlot.L_ARM, new Matrix4f().translation(foreL.transformPosition(gripL, new Vector3f())));
			this.jetWeapon.put(MountSlot.R_ARM, new Matrix4f().translation(foreR.transformPosition(gripR, new Vector3f())));
			this.jetWeapon.put(MountSlot.L_BACK, new Matrix4f().translation(coreJ.transformPosition(backL, new Vector3f())));
			this.jetWeapon.put(MountSlot.R_BACK, new Matrix4f().translation(coreJ.transformPosition(backR, new Vector3f())));
			for (MountSlot mount : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK}) {
				MechPart weapon = assembly.part(mount);
				Vector3f muzzle = weapon != null ? weapon.point("muzzle") : new Vector3f(0, 0, 1);
				this.jetMuzzle.put(mount, this.jetWeapon.get(mount).transformPosition(muzzle, new Vector3f()));
			}
			coreJ.transformPosition(core.point("seat"), this.jetSeat);
			// jet_eye: where the pilot looks from in jet form, when the core's eye isn't at the front then.
			coreJ.transformPosition(core.hasPoint("jet_eye") ? core.point("jet_eye")
					: core.hasPoint("eye") ? core.point("eye") : core.point("seat").add(0, 0.6f, 0), this.jetEye);
			this.jetHeight = this.stats.jet().map(MechPart.JetSpec::collisionHeight).orElse(1.6f);
		}
	}

	/** A level (untilted) frame at a point of the core. */
	public static Matrix4f level(Matrix4f coreM, Vector3f local) {
		return new Matrix4f().translation(coreM.transformPosition(local, new Vector3f()));
	}

	/** Jet placement of one group of the part in a slot ("*" fallback); identity if none. */
	public Matrix4f jetMatrix(MountSlot slot, String group) {
		Map<String, MechPart.JetPose> poses = this.jetPoses.get(slot);
		if (poses == null) {
			return new Matrix4f();
		}
		MechPart.JetPose pose = poses.get(group);
		if (pose == null) {
			pose = poses.get("*");
		}
		return pose == null ? new Matrix4f() : poseMatrix(pose);
	}

	/** The jet pose of one group exactly as listed ("*" fallback), or null. */
	public MechPart.JetPose jetPose(MountSlot slot, String group) {
		Map<String, MechPart.JetPose> poses = this.jetPoses.get(slot);
		if (poses == null) {
			return null;
		}
		MechPart.JetPose pose = poses.get(group);
		return pose != null ? pose : poses.get("*");
	}

	public float jetDelay(MountSlot slot, String group) {
		Map<String, MechPart.JetPose> poses = this.jetPoses.get(slot);
		if (poses == null) {
			return 0f;
		}
		MechPart.JetPose pose = poses.getOrDefault(group, poses.get("*"));
		return pose == null ? 0f : pose.delay();
	}

	/** T(pos)·Ry·Rx·Rz, degrees - the convention the model generator writes jet poses in. */
	public static Matrix4f poseMatrix(MechPart.JetPose pose) {
		if (pose.quat().isPresent()) {
			return new Matrix4f().translation(pose.pos()).rotate(pose.quat().get());
		}
		return new Matrix4f().translation(pose.pos())
				.rotateY((float) Math.toRadians(pose.rot().y))
				.rotateX((float) Math.toRadians(pose.rot().x))
				.rotateZ((float) Math.toRadians(pose.rot().z));
	}

	// ------------------------------------------------------------------------------------------

	private static final Map<String, MechLayout> CACHE = new HashMap<>();
	private static int cacheGeneration = -1;

	public static synchronized MechLayout of(Assembly assembly) {
		if (cacheGeneration != PartRegistry.generation()) {
			CACHE.clear();
			cacheGeneration = PartRegistry.generation();
		}
		return CACHE.computeIfAbsent(assembly.encode(), key -> new MechLayout(assembly));
	}
}
