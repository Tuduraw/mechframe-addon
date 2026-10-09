package com.example.mechframe.part;

import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One assembly part, as loaded from {@code data/<namespace>/mech_parts/<id>.json} (see PartParser).
 *
 * <p>Coordinates are in the part's own model space - the space its OBJ was authored in: +X left,
 * +Y up, +Z forward, 1 unit = 1 block. Where a part is placed on the frame is decided by MechLayout
 * from the attach points of the parts it hangs off:
 * <ul>
 *   <li>legs: origin on the ground under the frame; {@code hip} is where the core's origin goes.</li>
 *   <li>core: origin at its hip joint; {@code neck}, {@code shoulder_l}, {@code shoulder_r},
 *       {@code back}, {@code back_l}, {@code back_r}, {@code seat}, {@code eye}.</li>
 *   <li>head: origin at the neck joint.</li>
 *   <li>arms: both arms in one OBJ, each authored with its own shoulder joint at the origin (the right
 *       arm already mirrored); {@code elbow} and {@code grip} are given for the left arm and mirrored.</li>
 *   <li>booster: origin at the core's {@code back} point.</li>
 *   <li>weapon: origin at the grip (arm) or mount (back); {@code muzzle} in the same space.</li>
 * </ul>
 * The {@code jet} poses of a transform-set part replace that placement entirely in the jet form. */
public record MechPart(
		Identifier id,
		PartSlot slot,
		String displayName,
		String maker,
		int tier,
		Identifier model,
		Identifier texture,
		float weight,
		float armor,
		Optional<String> transformSet,
		Map<String, Vector3f> points,
		HeadSpec head,
		CoreSpec core,
		ArmSpec arms,
		LegSpec legs,
		BoosterSpec booster,
		WeaponSpec weapon,
		Map<String, JetPose> jetPoses
) {

	public Vector3f point(String name) {
		Vector3f p = this.points.get(name);
		return p == null ? new Vector3f() : new Vector3f(p);
	}

	public boolean hasPoint(String name) {
		return this.points.containsKey(name);
	}

	// ------------------------------------------------------------------------------------------

	public record HeadSpec(float lockRange, float lockCone, float stability) {
		public static final HeadSpec DEFAULT = new HeadSpec(110f, 14f, 1f);
	}

	/** jet: present only on a transformable core - the jet form's flight envelope. */
	public record CoreSpec(float enCapacity, float enRecharge, int rechargeDelay, float defense,
						   float fuel, float stability, Optional<JetSpec> jet) {
		public static final CoreSpec DEFAULT = new CoreSpec(1500f, 30f, 12, 1f, 800f, 1f, Optional.empty());
	}

	public record JetSpec(float maxSpeed, float acceleration, float turnSpeed, float stallFraction,
						  float afterburner, float afterburnerCost, float collisionHeight) {
	}

	/** integrated: weapons built into the arms (special standard) - mount -> weapon part id. Those arm
	 * mounts then always carry that weapon and take nothing else. */
	public record ArmSpec(float loadLimit, float aimSpeed, float recoilControl, java.util.Map<MountSlot, Identifier> integrated) {
		public static final ArmSpec DEFAULT = new ArmSpec(20f, 360f, 1f, java.util.Map.of());
	}

	public enum LegType {
		BIPED, REVERSE_JOINT, TANK, QUAD, HEXAPOD,
		/** No legs: a skirt riding on its lift fans a little above the ground (no steps, no tracks). */
		HOVER;

		public static LegType byId(String id) {
			for (LegType type : values()) {
				if (type.name().equalsIgnoreCase(id)) {
					return type;
				}
			}
			return BIPED;
		}

		/** Legs that walk with planted feet (everything but treads). */
		public boolean walks() {
			return this != TANK && this != HOVER;
		}

		/** The lower unit turns as one piece under the body (tracks, a hover skirt). */
		public boolean hull() {
			return this == TANK || this == HOVER;
		}

		/** Skates on its feet while boosting instead of stepping. */
		public boolean skates() {
			return this == BIPED || this == REVERSE_JOINT || this == QUAD;
		}
	}

	/** One IK leg: hip -> knee -> ankle at rest (leg-part space), the two segment groups and an
	 * optional coxa (a yaw segment between body and hip - spider legs) and foot. pole: the direction
	 * the knee should bend toward (leg-part space). */
	/** One leg. Two segments (upper: hip -> knee, lower: knee -> ankle), or three when {@code mid} is
	 * given: upper hip -> knee, mid knee -> knee2, lower knee2 -> ankle (a double-jointed leg; pole = the knee's side). */
	public record LegChain(String name, Optional<String> coxa, Vector3f coxaPivot, String upper, String lower,
						   Optional<String> foot, Vector3f hip, Vector3f knee, Vector3f ankle, Vector3f pole,
						   int group, float phase, Optional<String> mid, Vector3f knee2) {

		/** Hip-to-ankle length with the knee straight (knee2 held at its rest fold). */
		public float reach() {
			return this.knee.distance(this.hip) + this.ankle.distance(this.knee);
		}
	}

	/** A rotating tread wheel group (tank legs). */
	public record Wheel(String group, Vector3f pivot, float radius) {
	}

	public record LegSpec(
			LegType type,
			float loadLimit,
			float walkSpeed,
			float boostMultiplier,
			float jump,
			float turnRate,
			float stepHeight,
			float traction,
			float qbMultiplier,
			float hoverMultiplier,
			float collisionWidth,
			float stepLength,
			float stepLift,
			int stepTicks,
			/** Forward lean of the core on the hip, degrees (the torso tilts; arms, head and back weapons stay level). */
			float coreTilt,
			List<String> pelvisGroups,
			List<LegChain> chains,
			List<Wheel> wheels
	) {
	}

	public record Nozzle(Vector3f pos, Vector3f dir, float size) {
	}

	public record BoosterSpec(
			float boostSpeed,
			float qbSpeed,
			float qbCost,
			int qbCooldown,
			float hoverThrust,
			float hoverCost,
			float airBoostCost,
			List<Nozzle> nozzles,
			/** Jet-form exhaust points in jet root space (transform sets: usually the leg engines). */
			List<Nozzle> jetNozzles
	) {
		public static final BoosterSpec DEFAULT = new BoosterSpec(0.9f, 2.4f, 320f, 10, 0.16f, 14f, 6f, List.of(), List.of());
	}

	public record WeaponSpec(
			String weaponName,
			MountSlot.WeaponMount mount,
			Identifier projectileItem,
			float recoilDistance,
			float minYaw,
			float maxYaw,
			float minPitch,
			float maxPitch,
			boolean fcs,
			MeleeSpec melee
	) {
		public boolean isMelee() {
			return this.melee != null;
		}
	}

	/**
	 * A close-combat arm weapon (stats.melee): fired, the frame lunges at the locked target (or straight
	 * ahead) and swings once, hitting everything within reach in front of it.
	 *
	 * @param damage     damage per hit
	 * @param reach      metres from the frame's front within which the swing connects
	 * @param arc        half-angle (degrees) of the swing in front of the frame
	 * @param lungeSpeed blocks/tick of the dash before the swing
	 * @param lungeRange the farthest (metres) a locked target is dashed at; beyond it the dash is straight ahead
	 * @param enCost     EN per swing
	 * @param cooldown   ticks between swings
	 * @param thrust     a straight thrust (pile bunker, lance) instead of a sideways slash
	 */
	public record MeleeSpec(float damage, float reach, float arc, float lungeSpeed, float lungeRange, float enCost, int cooldown,
							boolean thrust) {
	}

	/** Jet-form placement of one OBJ group of this part: model-local geometry is placed by
	 * T(pos)·R in the frame's root space, R = quat if given, else Ry·Rx·Rz of rot (degrees). delay
	 * staggers this group within the transformation (0 = moves from the start, 0.5 = waits for half
	 * of it).
	 *
	 * <p>parent ("slot:group") and joint (a point in this group's own model space) make the group a
	 * jointed child: during the transformation it moves relative to its parent about that joint, so
	 * the two stay attached at it the whole way instead of each flying to its jet place on its own. */
	public record JetPose(Vector3f pos, Vector3f rot, Optional<org.joml.Quaternionf> quat, float delay,
						  Optional<String> parent, Vector3f joint) {
	}
}
