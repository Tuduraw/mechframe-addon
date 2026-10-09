package com.example.mechframe.client.anim;

import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Per-frame client animation state of one frame, advanced once per client tick.
 *
 * <h2>Feet</h2>
 * Walking legs plant their feet in the world. Each tick a foot's "home" is projected ahead along the
 * velocity and dropped onto the terrain (a raycast); when the planted foot falls too far behind
 * home, it steps - lifted in an arc over step_ticks (faster at speed) - provided no foot of another
 * gait group is mid-step, which gives alternating bipeds, trotting quads and tripod-gaited spiders
 * from one rule. Boosting on the ground switches skating legs to body-relative "skate" feet; in the
 * air the feet tuck up under the body. A planted foot never slides: the IK (LegIk) bends the leg to
 * reach it, so feet stay put on slopes and steps.
 *
 * <h2>Body</h2>
 * The pelvis rides lower on uneven ground (the average foot height), bobs with the stride, crouches
 * while skating and dips on landing (a damped spring). Tank legs turn their lower hull toward the
 * direction of travel independently of the upper body. */
public final class MechAnimator {

	public enum Mode { WALK, SKATE, AIR, FOLDED }

	/** The shortest leg swing, ticks: long enough to read as a big, deliberate step. */
	static final float SWING_TICKS = 10f;
	/** How far the pelvis dips (metres) at the extremes of a walking stride. */
	static final float WALK_DIP = 0.14f;
	/** Many legs: the share of the angular gap to a neighbouring leg a foot may swing into, and the cap. */
	static final float LEG_SHARE = 0.36f;
	static final float MAX_LEG_SWING = 45f;
	/** Many legs: how fast (degrees per tick) the legs' base turns after the upper body. */
	static final float LEG_TURN_RATE = 6f;
	/** Front-to-back lane gap kept between neighbouring feet on one side (m). */
	static final float LANE_MARGIN = 0.6f;

	/**
	 * Clamps a foot position (world) into its leg's lane: along the body, between the midpoints to the
	 * neighbouring legs on the same side (less a margin), so that no two legs on a side can cross.
	 */
	private Vec3d keepInLane(Vec3d w, Vec3d pos, Quaternionf yawQ, MechPart.LegSpec spec, int i) {
		List<MechPart.LegChain> chains = spec.chains();
		Vector3f me = chains.get(i).ankle();
		float lo = -Float.MAX_VALUE, hi = Float.MAX_VALUE;
		for (int j = 0; j < chains.size(); j++) {
			Vector3f o = chains.get(j).ankle();
			if (j == i || Math.signum(o.x) != Math.signum(me.x)) {
				continue;
			}
			float mid = (me.z + o.z) * 0.5f;
			if (o.z > me.z) {
				hi = Math.min(hi, mid - LANE_MARGIN * 0.5f);
			} else if (o.z < me.z) {
				lo = Math.max(lo, mid + LANE_MARGIN * 0.5f);
			}
		}
		if (lo == -Float.MAX_VALUE && hi == Float.MAX_VALUE) {
			return w;
		}
		Vector3f l = toLocal(pos, yawQ, w);
		float z = MathHelper.clamp(l.z, lo, hi);
		if (z == l.z) {
			return w;
		}
		l.z = z;
		return toWorld(pos, yawQ, l);
	}

	/** Many legs: the farthest a foot's landing spot leads its rest spot, as a share of the legs' radius. */
	static final float MULTI_LEAD_SHARE = 0.28f;
	/** Many legs, walking: middle legs step wider out and the front/back legs a little in, so that
	 * neighbouring feet pass beside each other instead of over each other. */
	static final float MULTI_SPREAD = 0.15f;

	public static final class Foot {
		public Vec3d planted = Vec3d.ZERO;
		public Vec3d stepFrom = Vec3d.ZERO;
		public Vec3d pos = Vec3d.ZERO;
		public Vec3d prevPos = Vec3d.ZERO;
		public float progress;
		public float duration = 6f;
		public boolean stepping;
		public boolean touchedDown;
		/** Body-relative (yaw frame) point a swing started from. */
		public Vector3f relFrom;
		/** Why the last step started (diagnostics): 'p' on the beat, 'o' overstretched, 'r' out of reach, 's' settling. */
		public char trigger;
		/** Body-relative (yaw frame) foot position while it is not planted - skating, airborne, folded. */
		public Vector3f local;
	}

	private String layoutKey;
	public final List<Foot> feet = new ArrayList<>();
	public Mode mode = Mode.WALK;
	private Mode prevMode = Mode.WALK;
	public int stepTurn;

	public float pelvisDy;
	public float prevPelvisDy;
	private float crouch;
	private float crouchVel;
	public float lowerYaw = Float.NaN;
	public float prevLowerYaw;
	public float wheelSpin;
	public float prevWheelSpin;
	public float headYaw;
	public float headPitch;
	public float prevHeadYaw;
	public float prevHeadPitch;
	private Vec3d lastPos;
	private Quaternionf lastYawQ = new Quaternionf();
	public float speed;
	/** Gait clock: one cycle per stride; each gait group steps when the clock passes its offset. */
	private float gaitPhase;
	private float lastYaw = Float.NaN;
	private boolean prevSkate;
	/** Slow-averaged body-local velocity, the spring-driven leg drag and its rate (see DRAG_*). */
	private Vector3f velSlow;
	private final Vector3f drag = new Vector3f();
	private final Vector3f dragVel = new Vector3f();
	/** How fast the slow velocity average follows (per tick): lower = longer drag after a quick boost. */
	static final float DRAG_SLOW_RATE = 0.16f;
	/** Leg drag: metres of trailing foot offset per block/tick of velocity change, and the cap. */
	static final float DRAG_GAIN = 1.5f;
	static final float DRAG_MAX = 1.1f;
	private int glideTicks;
	/** Hover skirt: roll (bank) and pitch, degrees. */
	public float hoverBank, prevHoverBank, hoverPitch, prevHoverPitch;
	static final int GLIDE_MIN_TICKS = 6;
	static final int GLIDE_MAX_TICKS = 30;
	/** Body-relative lead of the feet's targets ahead of their rest spots, eased toward the travel. */
	private final Vector3f leadLocal = new Vector3f();
	/** Footfalls this tick (world positions), for dust and sound. */
	public final List<Vec3d> footfalls = new ArrayList<>();

	public static MechAnimator of(MechEntity mech) {
		if (mech.clientAnimation instanceof MechAnimator existing) {
			return existing;
		}
		MechAnimator created = new MechAnimator();
		mech.clientAnimation = created;
		return created;
	}

	public void kick(float impact) {
		this.crouchVel -= Math.min(impact, 2.5f) * 0.22f * this.landingGive;
	}

	/** How much a landing sinks the frame (reverse joints soak it up deeper), and how far it may. */
	private float landingGive = 1f;
	private float landingLimit = 0.5f;

	public void tick(MechEntity mech) {
		this.footfalls.clear();
		MechLayout layout = mech.getLayout();
		if (layout == null) {
			return;
		}
		MechPart legs = layout.assembly.part(MountSlot.LEGS);
		MechPart.LegSpec spec = legs.legs();
		boolean reverseJoint = spec.type() == MechPart.LegType.REVERSE_JOINT;
		this.landingGive = reverseJoint ? 1.7f : 1f;
		this.landingLimit = reverseJoint ? 0.6f : 0.5f;
		String key = layout.assembly.parts().toString();
		Vec3d pos = mech.getEntityPos();
		float yaw = mech.getYaw();
		boolean reset = !key.equals(this.layoutKey) || this.lastPos == null || this.lastPos.squaredDistanceTo(pos) > 400;
		Vec3d vel = this.lastPos == null ? Vec3d.ZERO : pos.subtract(this.lastPos);
		if (vel.lengthSquared() > 100) {
			vel = Vec3d.ZERO;
		}
		this.lastPos = pos;
		this.speed = (float) vel.horizontalLength();
		// Many-legged frames: the legs' base turns after the upper body at the pace the legs can step
		// (each foot stays within its share of the circle), the upper body turning on it like a turret.
		boolean spider = spec.chains().size() > 2 && spec.type() != MechPart.LegType.TANK;
		float legYaw = yaw;
		if (spider) {
			if (reset || Float.isNaN(this.lowerYaw)) {
				this.lowerYaw = this.prevLowerYaw = yaw;
			}
			this.prevLowerYaw = this.lowerYaw;
			this.lowerYaw = approachAngle(this.lowerYaw, yaw, LEG_TURN_RATE);
			legYaw = this.lowerYaw;
		}
		Quaternionf yawQ = new Quaternionf().rotationY((float) Math.toRadians(-legYaw));
		this.lastYawQ = yawQ;
		// Turning rate of the legs (degrees per tick): turning in place also drives the gait.
		float yawRate = Float.isNaN(this.lastYaw) || reset ? 0f : MathHelper.wrapDegrees(legYaw - this.lastYaw);
		this.lastYaw = legYaw;

		if (reset) {
			this.layoutKey = key;
			this.feet.clear();
			for (MechPart.LegChain chain : spec.chains()) {
				Foot foot = new Foot();
				foot.planted = toWorld(pos, yawQ, chain.ankle());
				foot.pos = foot.prevPos = foot.planted;
				this.feet.add(foot);
			}
			this.lowerYaw = this.prevLowerYaw = legYaw;
		}

		boolean grounded = mech.isGroundedVisual();
		float t = mech.getTransformProgress(1f);
		int glideBefore = this.glideTicks;
		if (t > 0.02f) {
			this.mode = Mode.FOLDED;
		} else if (mech.isTurbo()) {
			this.mode = Mode.AIR;
		} else if (!grounded) {
			this.mode = Mode.AIR;
		} else if (spec.type().skates() && (mech.isBoostingVisual() || this.speed > 0.75f)) {
			this.mode = Mode.SKATE;
		} else if (spec.type() == MechPart.LegType.HEXAPOD && this.speed > 0.9f) {
			this.mode = Mode.SKATE;
		} else if (spec.chains().size() > 2 && this.prevMode == Mode.SKATE
				&& (this.glideTicks < GLIDE_MIN_TICKS || this.speed > spec.walkSpeed() * 1.3f)
				&& this.glideTicks < GLIDE_MAX_TICKS) {
			// Many legs after a boost: the feet keep the braced stance and slide along with the body
			// (inertia) until the mech has slowed to a walk - then they are re-placed one group at a time.
			this.mode = Mode.SKATE;
			this.glideTicks++;
		} else {
			this.mode = Mode.WALK;
		}
		if (this.mode != Mode.SKATE || this.glideTicks == glideBefore) {
			this.glideTicks = 0; // not gliding this tick
		}

		// Coming down from the air or out of the jet form (not a skate flicker): settle the feet.
		// ...or, for many legs, out of a skate (the boosting stance): the feet are wherever the skate left them.
		boolean landing = this.mode == Mode.WALK && (this.prevMode == Mode.AIR || this.prevMode == Mode.FOLDED
				|| (this.prevMode == Mode.SKATE && spec.chains().size() > 2));
		this.prevSkate = this.prevMode == Mode.SKATE;
		if (landing && this.prevSkate) {
			// Restart the gait clock on group 0's beat, so the groups take turns from the first step.
			this.gaitPhase = (float) Math.floor(this.gaitPhase) + 1.0e-3f;
		}
		this.prevMode = this.mode;
		double vy = vel.y;
		World world = mech.getEntityWorld();
		float stepLength = spec.stepLength();
		// Stride per gait cycle: long walking forward, short side-steps (the legs open and close
		// rather than both reaching toward the travel direction).
		Vector3f localVel = toLocal(Vec3d.ZERO, yawQ, new Vec3d(vel.x, 0, vel.z));
		// Inertia of the legs: the part of the velocity that is newer than the slow average (a quick boost,
		// the kick of a boost) drives a damped spring - the legs are left behind for a moment, then catch up.
		if (this.velSlow == null) {
			this.velSlow = new Vector3f(localVel);
		}
		Vector3f impulse = new Vector3f(localVel).sub(this.velSlow);
		this.velSlow.lerp(localVel, DRAG_SLOW_RATE);
		boolean legsDrag = spec.chains().size() <= 2 && spec.type().walks() && !mech.isTurbo();
		Vector3f dragTarget = legsDrag && (this.mode == Mode.SKATE || this.mode == Mode.AIR) ? impulse : new Vector3f();
		this.dragVel.add(new Vector3f(dragTarget).sub(this.drag).mul(0.35f)).mul(0.62f);
		this.drag.add(this.dragVel);
		float fwdFrac = this.speed > 1.0e-4f ? Math.abs(localVel.z) / this.speed : 1f;
		int groups = 1;
		for (MechPart.LegChain c : spec.chains()) {
			groups = Math.max(groups, c.group() + 1);
		}
		// Gait timing: every foot swings for at least SWING_TICKS (a big, unhurried leg swing). A gait cycle
		// is one swing per group. Each foot stays planted only while the body passes over it within the leg's
		// reach (hip height unchanged); at speed that is shorter than the cycle, so the gait becomes a
		// bounding run - both feet off the ground between strides - instead of short, quick steps.
		float legH = 0f;
		float legReach = 0f;
		for (MechPart.LegChain c : spec.chains()) {
			legH += c.hip().y - c.ankle().y;
			legReach += c.reach();
		}
		legH /= Math.max(1, spec.chains().size());
		legReach = legReach / Math.max(1, spec.chains().size()) * 0.95f;
		float hipH = legH - 0.14f;
		float reachFwd = (float) Math.sqrt(Math.max(0.16f, legReach * legReach - hipH * hipH));
		// Sideways the legs may only open so far (they must not cross).
		float reachHere = MathHelper.lerp(fwdFrac, Math.min(0.55f, reachFwd), reachFwd);
		float swingMin = Math.max(SWING_TICKS, spec.stepTicks() * 1.7f);
		float cycleTicks = groups * swingMin;
		// Feet also have to travel when the body turns: their speed round the turn counts as walking.
		float turnRadius = 0f;
		for (MechPart.LegChain c : spec.chains()) {
			turnRadius += (float) Math.sqrt(c.ankle().x * c.ankle().x + c.ankle().z * c.ankle().z);
		}
		turnRadius /= Math.max(1, spec.chains().size());
		float turnSpeed = Math.abs((float) Math.toRadians(yawRate)) * turnRadius;
		float rawGait = Math.max(this.speed, turnSpeed);
		// Many legs: above their own walking pace (still sliding out of a boost) the stride doesn't grow any
		// further, so no foot is flung far ahead of its place while the frame slows down.
		float gaitSpeed = spec.chains().size() > 2 ? Math.min(rawGait, Math.max(turnSpeed, spec.walkSpeed() * 1.15f)) : rawGait;
		float sp = Math.max(gaitSpeed, 0.015f);
		// (with a margin: a foot that lands a tick late must still be within reach when it lifts)
		float stanceTicks = Math.min(cycleTicks - swingMin, 2f * reachHere * 0.8f / sp);
		float swingTicks = cycleTicks - stanceTicks;
		if (spec.chains().size() > 2) {
			// Many legs never run: one group always stands. Quick, short steps when the body moves or turns
			// fast (each within reach), the full slow swing when it doesn't.
			swingTicks = Math.min(swingMin, Math.max(4f, stanceTicks / Math.max(1, groups - 1)));
			stanceTicks = Math.max(stanceTicks, swingTicks * (groups - 1));
			cycleTicks = stanceTicks + swingTicks;
		}
		float stride = sp * cycleTicks;
		float leadLen = sp * stanceTicks * 0.5f;
		if (spec.chains().size() > 2) {
			// Many legs: a foot sweeps at most MULTI_LEAD_SHARE of the legs' radius ahead of and behind its
			// place (about a metre for the spider) - long, but short of the neighbouring leg's sweep.
			leadLen = Math.min(leadLen, turnRadius * MULTI_LEAD_SHARE);
		}
		boolean running = spec.chains().size() <= 2 && stanceTicks < cycleTicks - swingMin - 0.01f;
		// While running, the swinging leg kicks back and reaches forward past its landing spot.
		float overshoot = running ? Math.min(0.55f, reachFwd * 0.6f) * fwdFrac : 0f;
		float liftScale = MathHelper.clamp(stride / (stepLength * 1.3f), 1f, 1.6f);
		// Feet land ahead of their rest spots (so they sweep around them); the lead is eased so a change
		// of direction swings the targets over a few ticks instead of at once.
		Vector3f wantLead = this.speed > 0.015f
				? new Vector3f(localVel).normalize().mul(leadLen * MathHelper.clamp(this.speed / 0.12f, 0f, 1f))
				: new Vector3f();
		this.leadLocal.lerp(wantLead, 0.22f);
		Vector3f leadDir = this.leadLocal.lengthSquared() > 1.0e-6f ? new Vector3f(this.leadLocal).normalize() : new Vector3f();
		Vector3f leadW = yawQ.transform(new Vector3f(this.leadLocal));
		Vec3d lead = new Vec3d(leadW.x, 0, leadW.z);
		float prevPhase = this.gaitPhase;
		// Many legs: each foot may only swing so far round the body before it would run into its
		// neighbours - a share of the angular gap to the next leg on each side (small for a spider's
		// middle legs, wider out toward the front and back).
		boolean multi = spec.chains().size() > 2;
		int nLegs = spec.chains().size();
		float[] homeAng = new float[nLegs];
		float[] rangeNeg = new float[nLegs];
		float[] rangePos = new float[nLegs];
		float minRange = 180f;
		if (multi) {
			for (int k = 0; k < nLegs; k++) {
				Vector3f a = spec.chains().get(k).ankle();
				homeAng[k] = (float) Math.toDegrees(Math.atan2(a.x, a.z));
			}
			for (int k = 0; k < nLegs; k++) {
				float gapPos = 360f;
				float gapNeg = 360f;
				for (int o = 0; o < nLegs; o++) {
					if (o == k) {
						continue;
					}
					float d = MathHelper.wrapDegrees(homeAng[o] - homeAng[k]);
					if (d > 0f) {
						gapPos = Math.min(gapPos, d);
					} else {
						gapNeg = Math.min(gapNeg, -d);
					}
				}
				rangePos[k] = Math.min(LEG_SHARE * gapPos, MAX_LEG_SWING);
				rangeNeg[k] = Math.min(LEG_SHARE * gapNeg, MAX_LEG_SWING);
				minRange = Math.min(minRange, Math.min(rangePos[k], rangeNeg[k]));
			}
			// A stance may only last while the body moves (and turns) through what a foot's share allows.
			{
				float byMove = 2f * turnRadius * MULTI_LEAD_SHARE / sp;
				float byTurn = Math.abs(yawRate) > 0.01f ? 1.6f * minRange / Math.abs(yawRate) : Float.MAX_VALUE;
				float maxStance = Math.min(byMove, byTurn);
				if (stanceTicks > maxStance) {
					stanceTicks = Math.max(2f, maxStance);
					swingTicks = Math.min(swingMin, Math.max(4f, stanceTicks / Math.max(1, groups - 1)));
					stanceTicks = Math.max(stanceTicks, swingTicks * (groups - 1));
					cycleTicks = stanceTicks + swingTicks;
				}
			}
		}
		boolean moving = this.mode == Mode.WALK && gaitSpeed > 0.015f;
		// Turning: the feet land ahead in the turn too (as far as the body turns in half a stance).
		float turnLimit = multi ? minRange * 0.7f : 25f;
		float turnLead = MathHelper.clamp(yawRate * stanceTicks * 0.5f, -turnLimit, turnLimit);
		Quaternionf turnQ = new Quaternionf().rotationY((float) Math.toRadians(-turnLead));
		float walkDrop = 0f;
		float walkDip = 0f;
		if (moving) {
			this.gaitPhase += 1f / cycleTicks;
			if (this.gaitPhase > 64f) {
				this.gaitPhase -= 64f;
				prevPhase -= 64f;
			}
		}
		float duration = swingTicks;
		// Feet never move faster than this per tick (no snapping when the targets jump).
		float maxFootStep = this.speed + (2f * leadLen + 2f * overshoot + 0.6f) / Math.max(4f, duration) * 2.5f + 0.15f;
		float groundSum = 0f;
		int groundCount = 0;
		for (int i = 0; i < this.feet.size() && i < spec.chains().size(); i++) {
			MechPart.LegChain chain = spec.chains().get(i);
			Foot foot = this.feet.get(i);
			foot.prevPos = foot.pos;
			Vector3f home = new Vector3f(chain.ankle());
			switch (this.mode) {
				case WALK -> {
					Vec3d h = toWorld(pos, yawQ, turnQ.transform(new Vector3f(home))).add(lead);
					if (multi) {
						// Keep the target inside this leg's share of the circle round the body.
						Vector3f tl = toLocal(pos, yawQ, h);
						if (moving) {
							float side = Math.abs((float) Math.cos(Math.toRadians(homeAng[i])));
							float k = 1f + MULTI_SPREAD * (1f - 2f * side) * MathHelper.clamp(gaitSpeed / 0.15f, 0f, 1f);
							tl.x *= k;
							tl.z = tl.z + (k - 1f) * chain.ankle().z;
						}
						float dev = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(tl.x, tl.z)) - homeAng[i]);
						float lim = MathHelper.clamp(dev, -rangeNeg[i] * 0.85f, rangePos[i] * 0.85f);
						if (lim != dev) {
							new Quaternionf().rotationY((float) Math.toRadians(lim - dev)).transform(tl);
							h = toWorld(pos, yawQ, tl);
						}
					}
					// Never aim a step beyond what the leg can reach from its hip: a target out of range (a
					// sharp turn, a sudden dash) is approached in several steps, each within reach.
					Vec3d hipW = toWorld(pos, yawQ, chain.hip());
					float legHi = chain.hip().y - chain.ankle().y;
					Vector3f homeW = yawQ.transform(new Vector3f(chain.ankle()));
					double hx = h.x - hipW.x, hz = h.z - hipW.z;
					double hd = Math.sqrt(hx * hx + hz * hz);
					double homeD = Math.sqrt(Math.pow(pos.x + homeW.x - hipW.x, 2) + Math.pow(pos.z + homeW.z - hipW.z, 2));
					double rr = chain.reach() * 0.97;
					double maxD = Math.max(homeD + 0.1, Math.sqrt(Math.max(0.09, rr * rr - (legHi - 0.5) * (legHi - 0.5))));
					if (hd > maxD) {
						h = new Vec3d(hipW.x + hx / hd * maxD, h.y, hipW.z + hz / hd * maxD);
					}
					double gy = groundHeight(world, h.x, h.z, pos.y + spec.stepHeight() + 1.0, pos.y - 4.0, pos.y);
					Vec3d target = new Vec3d(h.x, gy + chain.ankle().y, h.z);
					if (multi) {
						target = this.keepInLane(target, pos, yawQ, spec, i);
					}
					boolean fromSkate = landing && this.prevSkate && multi;
					if (landing) {
						// Coming down from the air / a skate / the jet: put the feet down from where they are.
						// Many legs out of a skate re-place one group at a time (the other stands), with a full,
						// unhurried step - the gait then takes over.
						foot.planted = foot.pos;
						foot.stepping = !fromSkate || chain.group() == 0;
						foot.progress = 0f;
						foot.duration = fromSkate ? Math.max(6f, spec.stepTicks() * 1.2f) : 3f;
						foot.stepFrom = foot.pos;
						foot.relFrom = null;
					}
					foot.local = null;
					// Walking: the gait clock alone decides when each foot swings (and how far through the
					// swing it is), so the legs always alternate and keep their rhythm.
					boolean beat = false;
					if (moving && !landing) {
						float offset = ((float) chain.group() / groups + chain.phase()) % 1f;
						float ph = this.gaitPhase - offset;
						ph -= (float) Math.floor(ph);
						float swingFrac = swingTicks / cycleTicks;
						if (ph < swingFrac) {
							if (!foot.stepping || foot.trigger != 'p') {
								foot.stepFrom = foot.stepping ? foot.pos : foot.planted;
								foot.stepping = true;
								foot.trigger = 'p';
								foot.progress = 0f;
								foot.relFrom = null;
								foot.duration = duration;
								this.stepTurn = chain.group();
							}
							foot.progress = Math.max(foot.progress, ph / swingFrac);
							beat = true;
						} else if (foot.stepping && foot.trigger == 'p') {
							foot.progress = 1f; // the beat says land
							beat = true;
						}
					}
					if (!foot.stepping && !moving) {
						double dx = foot.planted.x - target.x;
						double dz = foot.planted.z - target.z;
						double dist = Math.sqrt(dx * dx + dz * dz) + Math.abs(foot.planted.y - target.y) * 0.5;
						// A foot left far behind (a sudden dash or turn) catches up at once, gait or not,
						// before the leg runs out of reach.
						// ...or one the leg could no longer reach from the hip (the IK would leave it hanging).
						Vec3d hipWorld = toWorld(pos, yawQ, chain.hip()).add(0, this.pelvisDy, 0);
						float reach = chain.reach();
						boolean outOfReach = foot.planted.distanceTo(hipWorld) > reach * (moving ? 0.995f : 0.97f);
						boolean overstretched = dist > 2f * reachHere + 1.0f;
						if (multi && !overstretched) {
							Vector3f pl = toLocal(pos, yawQ, foot.planted);
							float pdev = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(pl.x, pl.z)) - homeAng[i]);
							overstretched = (pdev > rangePos[i] || pdev < -rangeNeg[i]) && this.canStep(chain.group(), spec);
						}
						if (overstretched) {
							// Left far behind (a sudden dash or turn): catch up now.
							foot.stepping = true;
							foot.progress = 0f;
							foot.duration = Math.max(4f, spec.stepTicks());
							foot.trigger = 'o';
							foot.stepFrom = foot.planted;
							foot.relFrom = null;
							this.stepTurn = chain.group();
						} else if ((dist > stepLength * 0.3f || outOfReach) && this.canStep(chain.group(), spec)) {
							// Standing or turning in place: settle the feet one group at a time.
							foot.stepping = true;
							foot.progress = 0f;
							foot.duration = Math.max(4f, spec.stepTicks());
							foot.trigger = 's';
							foot.stepFrom = foot.planted;
							foot.relFrom = null;
							this.stepTurn = chain.group();
						}
					}
					if (foot.stepping) {
						if (foot.relFrom == null) {
							foot.relFrom = toLocal(pos, yawQ, foot.stepFrom);
						}
						if (!beat) {
							foot.progress = Math.min(1f, foot.progress + 1f / foot.duration);
						}
						float e = ease(foot.progress);
						// The swing is body-relative (the body may travel metres while a foot is in the air):
						// from where it lifted off to its landing spot, kicking back then reaching forward.
						Vector3f relTo = toLocal(pos, yawQ, target);
						Vector3f rel = new Vector3f(foot.relFrom).lerp(relTo, e);
						float sw = (float) Math.sin(2.0 * Math.PI * foot.progress);
						rel.fma(-overshoot * sw, leadDir);
						float lift = spec.stepLift() * liftScale * MathHelper.sin((float) Math.PI * foot.progress) * MathHelper.clamp(this.speed * 4f + 0.35f, 0.35f, 1f);
						rel.y += lift;
						Vec3d p = toWorld(pos, yawQ, rel);
						Vec3d move = p.subtract(foot.prevPos);
						boolean late = false;
						if (move.length() > maxFootStep) {
							p = foot.prevPos.add(move.normalize().multiply(maxFootStep));
							late = foot.progress >= 1f;
							if (late) {
								foot.relFrom = toLocal(pos, yawQ, p);
								foot.progress = 0.85f;
							}
						}
						foot.pos = p;
						if (!late && foot.progress >= 1f) {
							foot.stepping = false;
							foot.relFrom = null;
							foot.planted = target;
							foot.pos = target;
							this.footfalls.add(target);
						}
					} else {
						foot.pos = foot.planted;
					}
					if (multi) {
						// Each foot keeps to its own lane front-to-back, so neighbouring legs never cross: a
						// planted foot that would be dragged past the lane's end slides along with the body
						// instead (inertia), and the gait re-places it on its beat.
						Vec3d kept = this.keepInLane(foot.pos, pos, yawQ, spec, i);
						if (kept != foot.pos) {
							Vec3d d = kept.subtract(foot.pos);
							foot.pos = kept;
							if (!foot.stepping) {
								foot.planted = foot.planted.add(d);
							}
						}
					}
					// How far the pelvis must sink this tick for this planted foot to stay within the leg's reach
					// (none when the foot is under the hip: the stride rises and falls through the cycle).
					if (!foot.stepping) {
						double fx = foot.planted.x - hipW.x, fz = foot.planted.z - hipW.z;
						double r = chain.reach() * 0.97;
						double avail = Math.sqrt(Math.max(0.0, r * r - fx * fx - fz * fz));
						walkDrop = Math.max(walkDrop, (float) (legHi - avail));
						// The stride's own dip: down by WALK_DIP with the feet spread front and back, back up
						// to standing height as a foot passes under the hip.
						Vector3f off = toLocal(pos, yawQ, foot.planted).sub(chain.ankle());
						float spread = MathHelper.clamp((float) Math.sqrt(off.x * off.x + off.z * off.z) / Math.max(0.3f, leadLen), 0f, 1f);
						walkDip = Math.max(walkDip, spread * spread);
					}
					// Terrain under the feet: where they are (or will be) planted, not how high a swinging foot is lifted.
					groundSum += (float) ((foot.stepping ? target.y : foot.planted.y) - chain.ankle().y - pos.y);
					groundCount++;
				}
				case SKATE -> {
					// Feet slide under the body in a braced stance; height follows the ground.
					Vector3f skate = new Vector3f(home.x * 1.12f, home.y, home.z - 0.15f);
					Vec3d h = toWorld(pos, yawQ, skate);
					double gy = groundHeight(world, h.x, h.z, pos.y + 1.5, pos.y - 3.0, pos.y);
					skate.y = (float) (gy - pos.y) + chain.ankle().y;
					this.applyDrag(skate, home, chain, true);
					this.follow(foot, skate, 0.55f);
					if (legsDrag) {
						// Two legs: the lagging foot never swings across under the body into the other leg.
						float side = Math.signum(home.x);
						foot.local.x = side * Math.max(side * foot.local.x, Math.abs(home.x) * 0.6f);
					}
					foot.pos = toWorld(pos, yawQ, foot.local);
					foot.planted = foot.pos;
					foot.stepping = false;
				}
				case AIR -> {
					// Body-relative, so a fast fall or an air dash never leaves the feet behind: tucked
					// while rising, reaching down a little for the landing while falling.
					float lift = vy > 0.05 ? 0.5f : (vy < -0.4 ? 0.12f : 0.3f);
					Vector3f tuck = spec.type().walks() && chain.coxa().isPresent()
							? new Vector3f(home.x * 0.85f, home.y + lift + 0.25f, home.z * 0.85f)
							: new Vector3f(home.x * 0.95f, home.y + lift, home.z + (vy < -0.4 ? 0.12f : -0.18f));
					if (reverseJoint && !mech.isTurbo()) {
						// Reverse joints: legs stretched out in the air.
						// Up: stretched straight out (push-off). Down: reaching for the ground, still long, so
						// the landing itself folds the legs and soaks up the impact.
						tuck = vy > 0.05 ? new Vector3f(home).add(0f, -0.8f, 0.05f)
								: vy < -0.15 ? new Vector3f(home).add(0f, -0.55f, 0.1f) : new Vector3f(home).add(0f, -0.3f, 0.05f);
					}
					if (mech.isTurbo()) {
						// Turbo: the legs lie back in line with the forward-leaning body - the body's own lean
						// about the hip pivot, with the legs stretched a little straighter and trailing.
						float lean = (float) Math.toRadians(mech.getLeanPitch(1f));
						Vector3f pivot = new Vector3f(0f, layout.hip.y, 0f);
						tuck = new Vector3f(home).add(0f, -0.12f, -0.22f).sub(pivot).rotateX(lean).add(pivot);
					}
					this.applyDrag(tuck, home, chain, false);
					this.follow(foot, tuck, 0.35f);
					if (legsDrag) {
						// Two legs: the lagging foot never swings across under the body into the other leg.
						float side = Math.signum(home.x);
						foot.local.x = side * Math.max(side * foot.local.x, Math.abs(home.x) * 0.6f);
					}
					foot.pos = toWorld(pos, yawQ, foot.local);
					foot.planted = foot.pos;
					foot.stepping = false;
				}
				case FOLDED -> {
					this.follow(foot, home, 0.5f);
					foot.pos = toWorld(pos, yawQ, foot.local);
					foot.planted = foot.pos;
					foot.stepping = false;
				}
			}
		}

		// Pelvis height: terrain, stride bob, skate crouch, landing spring.
		this.prevPelvisDy = this.pelvisDy;
		float terrain = groundCount > 0 && spec.type().walks() ? MathHelper.clamp(groundSum / groundCount * 0.7f, -0.9f, 0.35f) : 0f;
		float bob = 0f;
		if (this.mode == Mode.WALK) {
			float maxLift = 0f;
			for (Foot foot : this.feet) {
				if (foot.stepping) {
					maxLift = Math.max(maxLift, MathHelper.sin((float) Math.PI * foot.progress));
				}
			}
			bob = -0.03f * maxLift * MathHelper.clamp(this.speed * 3f, 0f, 1f);
		}
		float skateCrouch = this.mode == Mode.SKATE ? -0.28f : 0f;
		// Walking: the pelvis sinks only as far as the planted feet need to stay within reach, so it is back at
		// its standing height whenever a foot is under the hip.
		float walkCrouch = this.mode == Mode.WALK
				? -MathHelper.clamp(Math.max(walkDrop, WALK_DIP * walkDip * MathHelper.clamp(this.speed * 5f, 0f, 1f)), 0f, 0.6f) : 0f;
		this.crouchVel += (0f - this.crouch) * 0.3f - this.crouchVel * 0.38f;
		this.crouch += this.crouchVel;
		// A hard landing compresses the legs, but only as far as the knees can fold.
		if (this.crouch < -this.landingLimit) {
			this.crouch = -this.landingLimit;
			this.crouchVel = Math.max(this.crouchVel, 0f);
		}
		if (spec.type() == MechPart.LegType.HOVER) {
			// Hover skirt: rides on its fans - a slow bob, lower while boosting (the fans bite harder).
			bob = 0.06f * MathHelper.sin(mech.age * 0.13f) + (mech.isBoostingVisual() ? -0.08f : 0f);
			skateCrouch = 0f;
		}
		float target = terrain + bob + skateCrouch + walkCrouch + this.crouch;
		this.pelvisDy += (target - this.pelvisDy) * 0.45f;

		// Tank: the lower hull turns toward travel (or reverses), the upper body follows the aim.
		if (spider) {
			this.prevWheelSpin = this.wheelSpin;
		} else if (spec.type() == MechPart.LegType.TANK) {
			this.prevLowerYaw = this.lowerYaw;
			float hull = mech.getHullYaw();
			if (!Float.isNaN(hull)) {
				// Tank controls: the hull points where the driver steers it; the turret turns freely.
				this.lowerYaw = this.prevLowerYaw + MathHelper.wrapDegrees(hull - this.prevLowerYaw);
			} else if (this.speed > 0.04f) {
				float travel = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
				float reverse = travel + 180f;
				float pick = Math.abs(MathHelper.wrapDegrees(travel - this.lowerYaw)) <= Math.abs(MathHelper.wrapDegrees(reverse - this.lowerYaw)) ? travel : reverse;
				this.lowerYaw = approachAngle(this.lowerYaw, pick, 9f);
			}
			float relative = MathHelper.wrapDegrees(this.lowerYaw - yaw);
			if (Float.isNaN(hull) && Math.abs(relative) > 120f) {
				this.lowerYaw = yaw + Math.signum(relative) * 120f;
			}
			double hullRad = Math.toRadians(this.lowerYaw);
			double forward = -vel.x * Math.sin(hullRad) + vel.z * Math.cos(hullRad);
			this.prevWheelSpin = this.wheelSpin;
			float radius = spec.wheels().isEmpty() ? 0.4f : spec.wheels().get(0).radius();
			this.wheelSpin += (float) (forward / radius);
		} else if (spec.type() == MechPart.LegType.HOVER) {
			// Hover skirt: swings round after the body, a little late; banks against sideways drift.
			this.prevLowerYaw = this.lowerYaw;
			this.lowerYaw = approachAngle(this.lowerYaw, yaw, 7f);
			this.prevWheelSpin = this.wheelSpin;
			this.prevHoverBank = this.hoverBank;
			this.prevHoverPitch = this.hoverPitch;
			Vector3f lv = toLocal(Vec3d.ZERO, new Quaternionf().rotationY((float) Math.toRadians(-this.lowerYaw)), new Vec3d(vel.x, 0, vel.z));
			this.hoverBank += (MathHelper.clamp(-lv.x * 14f, -9f, 9f) - this.hoverBank) * 0.2f;
			this.hoverPitch += (MathHelper.clamp(lv.z * 8f, -6f, 6f) - this.hoverPitch) * 0.2f;
		} else {
			this.prevLowerYaw = this.lowerYaw;
			this.lowerYaw = yaw;
			this.prevWheelSpin = this.wheelSpin;
		}

		// Head: look toward the aim point, smoothed.
		this.prevHeadYaw = this.headYaw;
		this.prevHeadPitch = this.headPitch;
		float targetYaw = 0f;
		float targetPitch = 0f;
		if (mech.getControllingPassenger() != null && !mech.isJet()) {
			Vec3d aim = mech.getAimPoint(1f);
			Vec3d eye = mech.getAimOrigin(1f);
			Vec3d d = aim.subtract(eye);
			float aimYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			targetYaw = MathHelper.clamp(MathHelper.wrapDegrees(aimYaw - yaw), -55f, 55f);
			targetPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalLength())), -40f, 40f);
		}
		this.headYaw += (targetYaw - this.headYaw) * 0.35f;
		this.headPitch += (targetPitch - this.headPitch) * 0.35f;
	}

	/** Moves a foot's body-relative position toward a target (starting from where it is now). */
	/**
	 * Two legs under a sudden push (a quick boost): the leg on the far side from the push is left behind,
	 * stretched out away from the body, while the near leg is pulled in and lifts a little, being dragged
	 * along. Pushes forward or back leave both feet trailing. Offsets are body-local (+X left, +Z ahead).
	 */
	private void applyDrag(Vector3f target, Vector3f home, MechPart.LegChain chain, boolean onGround) {
		float len = this.drag.length();
		if (len < 1.0e-3f) {
			return;
		}
		float amount = Math.min(DRAG_MAX, len * DRAG_GAIN);
		float dx = this.drag.x / len, dz = this.drag.z / len;
		float side = Math.signum(home.x);
		float trailing = MathHelper.clamp(-dx * side, 0f, 1f); // this foot is on the side away from the push
		float leading = MathHelper.clamp(dx * side, 0f, 1f);
		// Left behind: against the push, so the far leg reaches out long; the near leg less so, drawn in.
		float back = amount * (0.35f + 0.65f * trailing - 0.25f * leading);
		target.x -= dx * back;
		target.z -= dz * amount * 0.7f;
		// The dragged near leg lifts a little (on the ground the far foot keeps skidding along the ground).
		target.y += amount * (0.3f * leading + (onGround ? 0f : 0.1f));
		// Never further out than the leg can reach from its hip.
		Vector3f hip = chain.hip();
		Vector3f d = new Vector3f(target).sub(hip);
		float r = chain.reach() * 0.98f;
		if (d.length() > r) {
			d.normalize(r);
			target.set(hip).add(d);
		}
	}

	private void follow(Foot foot, Vector3f target, float rate) {
		if (foot.local == null) {
			foot.local = this.lastPos == null ? new Vector3f(target) : toLocal(this.lastPos, this.lastYawQ, foot.pos);
		}
		foot.local.lerp(target, rate);
	}

	static Vector3f toLocal(Vec3d pos, Quaternionf yawQ, Vec3d world) {
		Vector3f v = new Vector3f((float) (world.x - pos.x), (float) (world.y - pos.y), (float) (world.z - pos.z));
		return new Quaternionf(yawQ).conjugate().transform(v);
	}

	/** Whether a cycle clock moving from a to b passed the fractional offset. */
	static boolean crossed(float a, float b, float offset) {
		if (b <= a) {
			return false;
		}
		float k = (float) Math.ceil(a - offset);
		return offset + k > a && offset + k <= b;
	}

	private boolean canStep(int group, MechPart.LegSpec spec) {
		for (int i = 0; i < this.feet.size() && i < spec.chains().size(); i++) {
			Foot other = this.feet.get(i);
			if (other.stepping && spec.chains().get(i).group() != group) {
				return false;
			}
		}
		// Prefer alternating: the group that stepped last waits while any foot of the other is overdue.
		return true;
	}

	static Vec3d toWorld(Vec3d pos, Quaternionf yawQ, Vector3f local) {
		Vector3f v = yawQ.transform(new Vector3f(local));
		return pos.add(v.x, v.y, v.z);
	}

	static double groundHeight(World world, double x, double z, double fromY, double toY, double fallback) {
		BlockHitResult hit = world.raycast(new RaycastContext(new Vec3d(x, fromY, z), new Vec3d(x, toY, z),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getPos().y : fallback;
	}

	static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	static float approachAngle(float current, float target, float step) {
		float delta = MathHelper.clamp(MathHelper.wrapDegrees(target - current), -step, step);
		return current + delta;
	}
}
