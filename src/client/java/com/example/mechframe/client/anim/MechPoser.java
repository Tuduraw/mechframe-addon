package com.example.mechframe.client.anim;

import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import com.example.tudursvehiclemod.asset.WeaponPart;
import com.example.tudursvehiclemod.client.render.ObjModel;
import com.example.tudursvehiclemod.client.render.ObjModelLoader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns a frame's layout + animation state into draw calls for one render frame: one entry per
 * (part model, OBJ group) with the matrix that places it in the frame's root space (the space the
 * renderer has already rotated into with the body orientation, lean included).
 *
 * <p>Robot form: legs by IK (or the tank hull's own yaw and spinning wheels), the core and everything
 * on it riding the pelvis, arms and back weapons by the base mod's own weapon-part rotation (the same
 * rotation that aims and fires them), the head looking at the aim point. Jet form: every group at its
 * authored jet pose. During a transformation each group moves on its own staggered schedule, jointed
 * pieces relative to their parent about the joint (see Blender), so the frame folds up piece by
 * piece without coming apart. */
public final class MechPoser {

	public record DrawItem(Identifier model, String group, Identifier texture, Matrix4f matrix, boolean glow) {
	}

	private MechPoser() {
	}

	public static List<DrawItem> pose(MechEntity mech, float td) {
		List<DrawItem> out = new ArrayList<>();
		MechLayout layout = mech.getLayout();
		if (layout == null) {
			return out;
		}
		MechAnimator anim = MechAnimator.of(mech);
		float transform = layout.canTransform ? MathHelper.clamp(mech.getTransformProgress(td), 0f, 1f) : 0f;
		boolean fullyJet = transform >= 0.999f;
		boolean robotOnly = transform <= 0.001f || !layout.canTransform;

		Map<String, Matrix4f> robot = fullyJet ? Map.of() : robotPose(mech, layout, anim, td);
		Map<String, Entry> entries = new java.util.LinkedHashMap<>();
		boolean hideHead = hidesHeadFor(mech);
		for (MountSlot slot : MountSlot.values()) {
			MechPart part = layout.assembly.part(slot);
			if (part == null || (hideHead && slot == MountSlot.HEAD)) {
				continue;
			}
			ObjModel model = ObjModelLoader.get(part.model()).orElse(null);
			if (model == null) {
				continue;
			}
			for (String group : model.getGroupNames()) {
				if (slot == MountSlot.ARMS && !group.startsWith("l_") && !group.startsWith("r_")) {
					continue;
				}
				entries.put(slot.id() + ":" + group, new Entry(slot, group, part));
			}
		}
		Blender blender = new Blender(layout, robot, entries, transform);
		for (Map.Entry<String, Entry> e : entries.entrySet()) {
			Entry entry = e.getValue();
			Matrix4f m;
			if (robotOnly) {
				m = robotOf(robot, entry.slot, entry.group);
			} else if (fullyJet) {
				m = jetMatrix(layout, entry.slot, entry.group);
			} else {
				m = blender.blended(e.getKey());
			}
			if (m != null) {
				out.add(new DrawItem(entry.part.model(), entry.group, entry.part.texture(), m, false));
			}
		}
		return out;
	}

	/** The head sits right at (or in front of) the first-person camera, and its sensor would block
	 * the pilot's view: it is left out only for the viewer riding this frame in first person.
	 * Other players and third-person views still draw it. */
	private static boolean hidesHeadFor(MechEntity mech) {
		net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
		net.minecraft.entity.Entity cam = mc.getCameraEntity();
		return cam != null && mc.options.getPerspective().isFirstPerson() && cam.getVehicle() == mech;
	}

	private record Entry(MountSlot slot, String group, MechPart part) {
	}

	private static Matrix4f robotOf(Map<String, Matrix4f> robot, MountSlot slot, String group) {
		Matrix4f m = robot.get(slot.id() + ":" + group);
		return m != null ? m : robot.get(slot.id() + ":*");
	}

	/**
	 * Mid-transformation placement. A group whose jet pose names a parent moves RELATIVE to that
	 * parent about its joint: its placement relative to the parent is interpolated from the robot one
	 * (live - IK legs, aimed arms) to the jet one, rotating about the joint while the joint itself
	 * travels from where it sits on the parent in robot form to where it sits in jet form (the same
	 * point for a plain hinge). Composed down the chain from the core, every jointed piece therefore
	 * stays attached to what it hangs from at every moment of the transformation. Weapons hang from
	 * the hand (arm mounts) or the core (back mounts) at their grip. Groups without a parent blend on
	 * their own (rigid lerp/slerp of the whole placement).
	 */
	private static final class Blender {
		private final MechLayout layout;
		private final Map<String, Matrix4f> robot;
		private final Map<String, Entry> entries;
		private final float transform;
		private final Map<String, Matrix4f> done = new HashMap<>();
		private final java.util.Set<String> visiting = new java.util.HashSet<>();

		Blender(MechLayout layout, Map<String, Matrix4f> robot, Map<String, Entry> entries, float transform) {
			this.layout = layout;
			this.robot = robot;
			this.entries = entries;
			this.transform = transform;
		}

		Matrix4f blended(String key) {
			Matrix4f cached = this.done.get(key);
			if (cached != null) {
				return cached;
			}
			Entry entry = this.entries.get(key);
			if (entry == null || !this.visiting.add(key)) {
				return null;
			}
			Matrix4f robotM = robotOf(this.robot, entry.slot, entry.group);
			Matrix4f jetM = jetMatrix(this.layout, entry.slot, entry.group);
			float delay = jetDelayFor(this.layout, entry.slot, entry.group);
			float s = MathHelper.clamp((this.transform - delay) / Math.max(0.05f, 1f - delay), 0f, 1f);
			s = s * s * (3f - 2f * s);
			Matrix4f result = null;
			String parentKey = this.parentOf(entry);
			Entry parent = parentKey != null ? this.entries.get(parentKey) : null;
			if (robotM != null && parent != null) {
				Matrix4f parentNow = this.blended(parentKey);
				Matrix4f parentRobot = robotOf(this.robot, parent.slot, parent.group);
				if (parentNow != null && parentRobot != null) {
					Matrix4f parentJet = jetMatrix(this.layout, parent.slot, parent.group);
					Matrix4f relRobot = new Matrix4f(parentRobot).invert().mul(robotM);
					Matrix4f relJet = new Matrix4f(parentJet).invert().mul(jetM);
					Vector3f joint = this.jointOf(entry);
					Vector3f pr = relRobot.transformPosition(joint, new Vector3f());
					Vector3f pj = relJet.transformPosition(joint, new Vector3f());
					Quaternionf qr = relRobot.getNormalizedRotation(new Quaternionf());
					Quaternionf qj = relJet.getNormalizedRotation(new Quaternionf());
					Matrix4f rel = new Matrix4f().translation(pr.lerp(pj, s)).rotate(qr.slerp(qj, s))
							.translate(-joint.x, -joint.y, -joint.z);
					result = new Matrix4f(parentNow).mul(rel);
				}
			}
			if (result == null) {
				result = robotM == null ? jetM : blend(robotM, jetM, s);
			}
			this.visiting.remove(key);
			this.done.put(key, result);
			return result;
		}

		private String parentOf(Entry entry) {
			MechPart.JetPose pose = entry.slot.isWeapon() ? null : this.layout.jetPose(entry.slot, entry.group);
			if (pose != null) {
				return pose.parent().orElse(null);
			}
			return switch (entry.slot) {
				case L_ARM -> this.entries.containsKey("arms:l_fore") ? "arms:l_fore" : this.coreRoot();
				case R_ARM -> this.entries.containsKey("arms:r_fore") ? "arms:r_fore" : this.coreRoot();
				case L_BACK, R_BACK -> this.coreRoot();
				default -> null;
			};
		}

		/** The core's root group: "core:core", else the first core group. */
		private String coreRoot() {
			if (this.entries.containsKey("core:core")) {
				return "core:core";
			}
			for (String key : this.entries.keySet()) {
				if (key.startsWith("core:")) {
					return key;
				}
			}
			return null;
		}

		private Vector3f jointOf(Entry entry) {
			MechPart.JetPose pose = entry.slot.isWeapon() ? null : this.layout.jetPose(entry.slot, entry.group);
			return pose != null ? new Vector3f(pose.joint()) : new Vector3f();
		}
	}

	private static Matrix4f jetMatrix(MechLayout layout, MountSlot slot, String group) {
		if (slot.isWeapon()) {
			return new Matrix4f(layout.jetWeapon.get(slot));
		}
		return layout.jetMatrix(slot, group);
	}

	private static float jetDelayFor(MechLayout layout, MountSlot slot, String group) {
		return switch (slot) {
			case L_ARM -> layout.jetDelay(MountSlot.ARMS, "l_fore");
			case R_ARM -> layout.jetDelay(MountSlot.ARMS, "r_fore");
			case L_BACK, R_BACK -> layout.jetDelay(MountSlot.CORE, "core");
			default -> layout.jetDelay(slot, group);
		};
	}

	/** Rigid blend of two rigid placements. */
	static Matrix4f blend(Matrix4f a, Matrix4f b, float t) {
		Vector3f ta = a.getTranslation(new Vector3f());
		Vector3f tb = b.getTranslation(new Vector3f());
		Quaternionf qa = a.getNormalizedRotation(new Quaternionf());
		Quaternionf qb = b.getNormalizedRotation(new Quaternionf());
		return new Matrix4f().translation(ta.lerp(tb, t)).rotate(qa.slerp(qb, t));
	}

	/** Robot-form matrices keyed "slot:group" ("slot:*" = every group of that part). */
	private static Map<String, Matrix4f> robotPose(MechEntity mech, MechLayout layout, MechAnimator anim, float td) {
		Map<String, Matrix4f> out = new HashMap<>();
		MechPart legs = layout.assembly.part(MountSlot.LEGS);
		MechPart.LegSpec spec = legs.legs();
		float bodyYaw = mech.getYaw(td);
		float pelvisDy = MathHelper.lerp(td, anim.prevPelvisDy, anim.pelvisDy) + mech.getStepUpVisualOffset(td);
		Matrix4f pelvis = new Matrix4f().translation(0, pelvisDy, 0);

		// Root space <- world, for planted feet.
		Quaternionf body = mech.tudursvehiclemod$getBodyOrientation(td);
		Quaternionf bodyInv = new Quaternionf(body).conjugate();
		Vec3d origin = mech.getLerpedPos(td).add(mech.tudursvehiclemod$getBodyFrameOffset(td));
		Quaternionf yawQ = new Quaternionf().rotationY((float) Math.toRadians(-bodyYaw));
		Quaternionf leanInv = new Quaternionf(bodyInv).mul(yawQ); // cancels the lean: feet stay level

		if (spec.type() == MechPart.LegType.HOVER) {
			// Hover skirt: one piece, riding the bob, turned after the body and banked against drift.
			float lower = MathHelper.lerp(td, anim.prevLowerYaw, anim.lowerYaw);
			float rel = MathHelper.wrapDegrees(lower - bodyYaw);
			float bank = MathHelper.lerp(td, anim.prevHoverBank, anim.hoverBank);
			float pitch = MathHelper.lerp(td, anim.prevHoverPitch, anim.hoverPitch);
			Matrix4f hull = new Matrix4f(pelvis).rotateY((float) Math.toRadians(-rel))
					.translate(0f, 0.6f, 0f).rotateZ((float) Math.toRadians(bank)).rotateX((float) Math.toRadians(pitch)).translate(0f, -0.6f, 0f);
			out.put("legs:*", hull);
			for (String g : spec.pelvisGroups()) {
				out.put("legs:" + g, new Matrix4f(hull));
			}
		} else if (spec.type() == MechPart.LegType.TANK) {
			float lower = MathHelper.lerp(td, anim.prevLowerYaw, anim.lowerYaw);
			float rel = MathHelper.wrapDegrees(lower - bodyYaw);
			Matrix4f hull = new Matrix4f().rotationY((float) Math.toRadians(-rel));
			out.put("legs:*", hull);
			for (String g : spec.pelvisGroups()) {
				out.put("legs:" + g, new Matrix4f(pelvis));
			}
			// wheelSpin is the angle of the first (road) wheel; smaller rollers turn faster for the same belt speed.
			float spin = MathHelper.lerp(td, anim.prevWheelSpin, anim.wheelSpin);
			float r0 = spec.wheels().isEmpty() ? 1f : spec.wheels().get(0).radius();
			for (MechPart.Wheel wheel : spec.wheels()) {
				Vector3f p = wheel.pivot();
				float a = wheel.radius() > 0.01f ? spin * r0 / wheel.radius() : spin;
				out.put("legs:" + wheel.group(), new Matrix4f(hull).translate(p).rotateX(a).translate(-p.x, -p.y, -p.z));
			}
		} else {
			Matrix4f legBase = new Matrix4f(pelvis);
			if (spec.chains().size() > 2) {
				// Many legs: their base lags behind the upper body's turn (MechAnimator.LEG_TURN_RATE).
				float lower = MathHelper.lerp(td, anim.prevLowerYaw, anim.lowerYaw);
				float rel = MathHelper.wrapDegrees(lower - bodyYaw);
				legBase.rotateY((float) Math.toRadians(-rel));
			}
			Matrix4f pelvisLegs = legBase;
			out.put("legs:*", new Matrix4f(pelvisLegs));
			for (String g : spec.pelvisGroups()) {
				out.put("legs:" + g, new Matrix4f(pelvisLegs));
			}
			for (int i = 0; i < spec.chains().size() && i < anim.feet.size(); i++) {
				MechPart.LegChain chain = spec.chains().get(i);
				MechAnimator.Foot foot = anim.feet.get(i);
				Vector3f target;
				if (anim.mode == MechAnimator.Mode.FOLDED) {
					// Transforming: the legs stay at rest on the pelvis whatever the airframe's attitude.
					target = pelvisLegs.transformPosition(new Vector3f(chain.ankle()));
				} else {
					Vec3d world = foot.prevPos.lerp(foot.pos, td);
					target = bodyInv.transform(new Vector3f(
							(float) (world.x - origin.x), (float) (world.y - origin.y), (float) (world.z - origin.z)));
				}
				Map<String, Matrix4f> chainOut = new HashMap<>();
				LegIk.solve(chain, pelvisLegs, target, leanInv, chainOut, maxBend(spec.type()), maxSwing(spec.type()));
				chainOut.forEach((g, m) -> out.put("legs:" + g, m));
			}
		}

		Matrix4f core = new Matrix4f(pelvis).mul(layout.robot.get(MechLayout.Place.CORE));
		out.put("core:*", core);
		MechPart corePart = layout.assembly.part(MountSlot.CORE);
		Vector3f neck = corePart.point("neck");
		float headYaw = MathHelper.lerp(td, anim.prevHeadYaw, anim.headYaw);
		float headPitch = MathHelper.lerp(td, anim.prevHeadPitch, anim.headPitch);
		out.put("head:*", new Matrix4f(pelvis).mul(MechLayout.level(layout.robot.get(MechLayout.Place.CORE), neck))
				.rotateY((float) Math.toRadians(-headYaw)).rotateX((float) Math.toRadians(headPitch)));
		out.put("booster:*", new Matrix4f(core).translate(corePart.point("back")));

		Map<String, WeaponPart> parts = new HashMap<>();
		for (WeaponPart wp : mech.getDefinition().weaponParts()) {
			parts.put(wp.part(), wp);
		}
		Matrix4f armL = aimed(mech, parts.get(MountSlot.L_ARM.weaponPartName()), new Matrix4f(pelvis).mul(layout.robot.get(MechLayout.Place.ARM_L)), td);
		Matrix4f armR = aimed(mech, parts.get(MountSlot.R_ARM.weaponPartName()), new Matrix4f(pelvis).mul(layout.robot.get(MechLayout.Place.ARM_R)), td);
		// Melee: the swinging arm winds up out to its side and high, then sweeps across and down.
		MountSlot swingArm = mech.getMeleeArm();
		if (swingArm != null) {
			Matrix4f swing = swingArm == MountSlot.L_ARM ? armL : armR;
			MechPart w = layout.assembly.part(swingArm);
			if (w != null && w.weapon() != null && w.weapon().isMelee() && w.weapon().melee().thrust()) {
				thrust(swing, mech.getMeleeTime(td), swingArm == MountSlot.L_ARM ? 1f : -1f);
			} else {
				swing.rotate(slash(mech.getMeleeTime(td), swingArm == MountSlot.L_ARM ? 1f : -1f));
			}
		}
		MechPart armsPart = layout.assembly.part(MountSlot.ARMS);
		for (String g : groupsOf(armsPart)) {
			if (g.startsWith("l_")) {
				out.put("arms:" + g, armL);
			} else if (g.startsWith("r_")) {
				out.put("arms:" + g, armR);
			}
		}
		Vector3f grip = new Vector3f(layout.grip);
		out.put("l_arm:*", new Matrix4f(armL).translate(grip));
		out.put("r_arm:*", new Matrix4f(armR).translate(-grip.x, grip.y, grip.z));
		out.put("l_back:*", aimed(mech, parts.get(MountSlot.L_BACK.weaponPartName()), new Matrix4f(pelvis).mul(layout.robotWeapon.get(MountSlot.L_BACK)), td));
		out.put("r_back:*", aimed(mech, parts.get(MountSlot.R_BACK.weaponPartName()), new Matrix4f(pelvis).mul(layout.robotWeapon.get(MountSlot.R_BACK)), td));
		return out;
	}

	/** Slash keys (degrees, relative to the aimed arm): rest, wound up (outward + raised), swept
	 * through (across + down), back to rest. side: +1 left arm, -1 right arm (outward = toward it). */
	static Quaternionf slash(float t, float side) {
		float strike = MechEntity.MELEE_STRIKE;
		float yaw, pitch, roll;
		if (t < strike) {
			float e = ease(t / strike);
			yaw = 75f * e;
			pitch = -40f * e;
			roll = 25f * e;
		} else if (t < strike + 2.5f) {
			float e = ease((t - strike) / 2.5f);
			yaw = MathHelper.lerp(e, 75f, -55f);
			pitch = MathHelper.lerp(e, -40f, 20f);
			roll = MathHelper.lerp(e, 25f, -20f);
		} else {
			float e = ease(MathHelper.clamp((t - strike - 2.5f) / (MechEntity.MELEE_END - strike - 2.5f), 0f, 1f));
			yaw = MathHelper.lerp(e, -55f, 0f);
			pitch = MathHelper.lerp(e, 20f, 0f);
			roll = MathHelper.lerp(e, -20f, 0f);
		}
		return new Quaternionf().rotateY((float) Math.toRadians(yaw * side)).rotateX((float) Math.toRadians(pitch))
				.rotateZ((float) Math.toRadians(roll * side));
	}

	/** Thrust: the arm draws back and in (aimed straight at the target), drives forward past rest on
	 * the strike - with a hard recoil kick - and settles back. */
	static void thrust(Matrix4f arm, float t, float side) {
		float strike = MechEntity.MELEE_STRIKE;
		float back, yaw;
		if (t < strike) {
			float e = ease(t / strike);
			back = -0.55f * e;
			yaw = -12f * e;
		} else if (t < strike + 1.2f) {
			float e = (t - strike) / 1.2f;
			back = MathHelper.lerp(e, -0.55f, 0.6f);
			yaw = MathHelper.lerp(e, -12f, 0f);
		} else if (t < strike + 2.5f) {
			float e = ease((t - strike - 1.2f) / 1.3f);
			back = MathHelper.lerp(e, 0.6f, 0.25f);
			yaw = 0f;
		} else {
			float e = ease(MathHelper.clamp((t - strike - 2.5f) / (MechEntity.MELEE_END - strike - 2.5f), 0f, 1f));
			back = MathHelper.lerp(e, 0.25f, 0f);
			yaw = 0f;
		}
		arm.rotate(new Quaternionf().rotateY((float) Math.toRadians(yaw * side))).translate(0f, 0f, back);
	}

	private static float ease(float x) {
		return x * x * (3f - 2f * x);
	}

	/** Knee fold limit (degrees from straight) per leg kind. */
	static float maxBend(MechPart.LegType type) {
		return switch (type) {
			case REVERSE_JOINT -> 120f;
			case QUAD, HEXAPOD -> 130f;
			default -> 125f;
		};
	}

	/** Hip swing limit (degrees from the rest direction) per leg kind; spider legs also yaw at the coxa. */
	static float maxSwing(MechPart.LegType type) {
		return switch (type) {
			case QUAD, HEXAPOD -> 60f;
			default -> 55f;
		};
	}

	/** mount · (the base mod's aim rotation of that weapon part) · recoil, exactly as the base renderer
	 * composes a weapon part (rotation about the pivot, then recoil along the barrel). */
	private static Matrix4f aimed(MechEntity mech, WeaponPart part, Matrix4f mount, float td) {
		if (part == null) {
			return mount;
		}
		Quaternionf own = mech.tudursvehiclemod$getWeaponPartOwnRotation(part, td);
		float recoil = mech.tudursvehiclemod$getWeaponPartRecoilOffset(part.part(), td);
		return mount.rotate(own).translate(0f, 0f, -recoil);
	}

	private static Iterable<String> groupsOf(MechPart part) {
		if (part == null) {
			return List.of();
		}
		return ObjModelLoader.get(part.model()).map(ObjModel::getGroupNames).orElse(java.util.Set.of());
	}
}
