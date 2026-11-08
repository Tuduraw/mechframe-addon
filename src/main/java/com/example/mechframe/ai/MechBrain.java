package com.example.mechframe.ai;

import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MechStats;
import com.example.mechframe.part.MountSlot;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The defence AI of one frame (server only). It drives the frame through the same inputs a pilot
 * gives - move, boost, quick boost, body yaw, the FCS lock, the arm triggers - so every bit of the
 * frame's physics, animation and weapon handling is the ordinary one.
 *
 * <p>Modes:
 * <ul>
 *   <li>GUARD: holds a territory (home, radius) - patrols inside it, engages what comes in, never
 *       chases past its edge, walks back when the fight is over. Command-post units and friendly
 *       base defence.</li>
 *   <li>ESCORT (friendly only): a wingman - keeps a slot beside its owner, engages what threatens
 *       around the owner, catches up (teleports when left far behind on foot).</li>
 * </ul>
 * The frame only acts while nobody pilots it; a player climbing in takes over, and the AI resumes
 * when they leave.
 */
public final class MechBrain {

	public enum Mode {
		GUARD, ESCORT;

		static Mode byId(String id) {
			return "escort".equalsIgnoreCase(id) ? ESCORT : GUARD;
		}
	}

	public Faction faction;
	public Mode mode;
	public Vec3 home;
	public double radius;
	/** ESCORT: the player followed; GUARD (friendly): who set it up. */
	public UUID owner;
	/** Command post that deployed this unit and its slot there (null: not a post unit). */
	public BlockPos post;
	public int postSlot;
	/** 0..1: how often it dodges, how disciplined its bursts are, how soon it reacts. */
	public float skill = 0.5f;
	/** Enemy units: whether the part drops have been rolled (only once per wreck). */
	public boolean dropsRolled;

	// ---- runtime (not saved)
	private Entity target;
	private Entity lastAttacker;
	private int lastAttackerTick = -1000;
	private int retarget;
	private int idleTicks;
	private Vec3 patrolPoint;
	private int patrolWait;
	private int strafeSign = 1;
	private int strafeTimer;
	private int dodgeTimer = 40;
	private int burstTimer;
	private boolean burstOn = true;
	private int jumpTicks;
	private int stuckTicks;
	private Vec3 lastPos = Vec3.ZERO;
	private int postCheck;
	/** Body yaw the frame turns toward (NaN: keep). Read by MechEntity's movement. */
	public float desiredYaw = Float.NaN;
	/** Turbo mode: nose pitch to hold (degrees, negative = up). */
	public float desiredPitch;
	// escort: where the owner was last seen heading (changes only when the owner actually moves)
	private Vec3 ownerAnchor;
	private float ownerHeading = Float.NaN;
	private boolean escortMoving;
	private boolean wantTurbo;

	public MechBrain(Faction faction, Mode mode, Vec3 home, double radius) {
		this.faction = faction;
		this.mode = mode;
		this.home = home;
		this.radius = radius;
	}

	public Entity getTarget() {
		return this.target;
	}

	/** Something hit this frame: remembered for a while as a preferred target if it is a foe. */
	public void onAttacked(MechEntity self, Entity attacker) {
		if (attacker != null && this.faction.isHostileTo(attacker, self)) {
			this.lastAttacker = attacker;
			this.lastAttackerTick = self.tickCount;
		}
	}

	// ================================================================================== tick

	public void tick(MechEntity self, ServerLevel world) {
		if (self.getLayout() == null) {
			return;
		}
		// Robot form only: leave the jet form a player may have left it in.
		if (self.isJet() && !self.isTransforming()) {
			self.tryTransform();
		}
		if (this.post != null && --this.postCheck <= 0) {
			this.postCheck = 100;
			if (!CommandPostBlockEntity.stillOwns(world, this.post, this.postSlot, self.getUUID())) {
				// The post has replaced this unit (it was taken for lost while unloaded): stand down.
				self.discard();
				return;
			}
		}

		Vec3 center;
		double leash;
		ServerPlayer ownerPlayer = null;
		if (this.mode == Mode.ESCORT) {
			ownerPlayer = this.owner != null ? world.getServer().getPlayerList().getPlayer(this.owner) : null;
			if (ownerPlayer != null && ownerPlayer.level() == world && !ownerPlayer.isSpectator()) {
				center = ownerPlayer.position();
				leash = 36.0;
			} else {
				ownerPlayer = null;
				center = self.position();
				leash = 24.0;
			}
		} else {
			center = this.home != null ? this.home : self.position();
			leash = this.radius;
		}

		if (--this.retarget <= 0 || this.target == null || !this.faction.isHostileTo(this.target, self)) {
			this.retarget = 10 + (int) ((1f - this.skill) * 10f);
			this.target = this.findTarget(self, world, center, leash);
		}
		self.aiLock(this.target);

		float fwd = 0f;
		float str = 0f;
		boolean boost = false;
		boolean jump = false;
		MechStats s = self.getStats();
		float enFrac = self.getEn() / Math.max(1f, s.enCapacity());

		this.wantTurbo = false;
		this.desiredPitch = 0f;
		if (this.target != null) {
			this.idleTicks = 0;
			Vec3 to = this.target.position().subtract(self.position());
			double dist = Math.sqrt(to.x * to.x + to.z * to.z);
			this.desiredYaw = yawOf(to);
			boolean meleeOnly = this.hasMelee(self) && !this.hasGuns(self);
			boolean meleeNow = this.hasMelee(self) && self.isMeleeReady() && enFrac > 0.3f;
			double near = meleeOnly || meleeNow ? 2.0 : s.legType() == MechPart.LegType.TANK ? 18.0 : 12.0;
			double far = meleeOnly ? 6.0 : s.legType() == MechPart.LegType.TANK ? 46.0 : 34.0;
			if (dist > far) {
				fwd = 1f;
				boost = enFrac > 0.35f && dist > far + 8.0;
			} else if (dist < near) {
				fwd = -0.7f;
			}
			if (--this.strafeTimer <= 0) {
				this.strafeTimer = 30 + self.getRandom().nextInt(50);
				this.strafeSign = self.getRandom().nextBoolean() ? 1 : -1;
			}
			str = this.strafeSign * (meleeOnly ? 0.3f : 0.8f);
			// Dodge with quick boosts, more often the better the pilot.
			if (--this.dodgeTimer <= 0) {
				this.dodgeTimer = (int) (40 + self.getRandom().nextInt(60) * (1.3f - this.skill));
				if (enFrac > 0.5f && self.getRandom().nextFloat() < 0.3f + this.skill * 0.6f) {
					float side = self.getRandom().nextBoolean() ? 1f : -1f;
					self.tryQuickBoost(side, dist < near ? -0.4f : 0.2f);
				}
			}
			this.fire(self, world, dist);
		} else {
			this.idleTicks++;
			if (this.mode == Mode.ESCORT && ownerPlayer != null) {
				// The slot follows where the owner GOES, not where they look: the heading is only
				// updated once the owner has actually moved a couple of blocks.
				Vec3 ownerPos = ownerPlayer.position();
				if (this.ownerAnchor == null || Float.isNaN(this.ownerHeading)) {
					this.ownerAnchor = ownerPos;
					this.ownerHeading = ownerPlayer.getYRot();
				} else if (horizontalDistance(ownerPos, this.ownerAnchor) > 2.0) {
					this.ownerHeading = yawOf(ownerPos.subtract(this.ownerAnchor));
					this.ownerAnchor = ownerPos;
				}
				Vec3 slot = this.escortSlot(ownerPos, this.ownerHeading, self);
				Vec3 d = slot.subtract(self.position());
				double dist = Math.sqrt(d.x * d.x + d.z * d.z);
				ESCORTS.add(self);
				if (horizontalDistance(ownerPos, self.position()) > CATCH_UP_DISTANCE) {
					this.teleportTo(self, world, slot);
					return;
				}
				// Start moving only when clearly off the slot, stop once on it (no shuffling).
				if (!this.escortMoving && dist > 6.0) {
					this.escortMoving = true;
				} else if (this.escortMoving && dist < 2.5) {
					this.escortMoving = false;
				}
				if (this.escortMoving) {
					this.desiredYaw = yawOf(d);
					fwd = 1f;
					boost = dist > 10.0 && enFrac > 0.15f;
					// Turbo (frames that cannot transform) to keep up with a fast-moving owner.
					boolean canTurbo = self.getLayout() != null && !self.getLayout().canTransform && !self.tudursvehiclemod$isOutOfFuel();
					this.wantTurbo = canTurbo && (self.isTurbo() ? dist > 16.0 : dist > 36.0);
					double dy = slot.y - self.getY();
					this.desiredPitch = self.horizontalCollision ? -30f
							: Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.max(dist, 1.0))), -30f, 25f);
				} else {
					this.desiredYaw = this.ownerHeading;
				}
			} else if (this.mode == Mode.GUARD) {
				this.patrol(self, world, center, leash);
				if (this.patrolPoint != null && this.patrolWait <= 0) {
					Vec3 d = this.patrolPoint.subtract(self.position());
					this.desiredYaw = yawOf(d);
					fwd = 0.8f;
					boost = center.distanceTo(self.position()) > leash && enFrac > 0.3f;
				}
			}
			// Enemy units top up between fights (they have no one to resupply them).
			if (this.faction == Faction.ENEMY && this.idleTicks % 200 == 199) {
				self.tudursvehiclemod$fillAllWeaponAmmoAndFuel();
				self.tudursvehiclemod$syncWeaponAmmo();
			}
		}

		// Never leave the territory: anything that would carry the frame out is turned back in.
		// (A wingman with nothing to fight is already heading for its owner - and may boost to.)
		boolean keepBoost = this.mode == Mode.ESCORT;
		if (this.mode == Mode.GUARD || (ownerPlayer != null && this.target != null)) {
			Vec3 move = localToWorld(self.getYRot(), fwd, str);
			Vec3 next = self.position().add(move.scale(4.0));
			double out = horizontalDistance(next, center) - leash;
			if (out > 0 && move.lengthSqr() > 1.0e-4) {
				Vec3 back = center.subtract(self.position());
				back = new Vec3(back.x, 0, back.z).normalize();
				float[] local = worldToLocal(self.getYRot(), back);
				fwd = local[0];
				str = local[1];
				boost = boost && keepBoost;
			}
		}

		// Unstick: jump (or hover) over what blocks the way, give up on a point it cannot reach.
		boolean moving = Math.abs(fwd) + Math.abs(str) > 0.1f;
		Vec3 pos = self.position();
		if (moving && pos.distanceToSqr(this.lastPos) < 0.0025) {
			if (++this.stuckTicks > 30) {
				this.stuckTicks = 0;
				this.patrolPoint = null;
				this.jumpTicks = 12;
			}
		} else {
			this.stuckTicks = 0;
		}
		this.lastPos = pos;
		if (moving && self.horizontalCollision) {
			this.jumpTicks = Math.max(this.jumpTicks, 8);
		}
		if (this.jumpTicks > 0) {
			this.jumpTicks--;
			jump = true;
		}

		if (self.isTurbo() != this.wantTurbo && !self.isTransforming() && self.getLayout() != null && !self.getLayout().canTransform) {
			self.tryTransform(); // turbo on / off
		}
		self.inForward = fwd;
		self.inStrafe = str;
		self.inBoost = boost;
		self.inJump = jump;
		self.inTankControls = false;
	}

	// ================================================================================== targets

	private Entity findTarget(MechEntity self, ServerLevel world, Vec3 center, double leash) {
		MechStats s = self.getStats();
		double sense = Mth.clamp(s.lockRange(), 48.0, 140.0);
		if (this.lastAttacker != null && self.tickCount - this.lastAttackerTick < 200
				&& this.faction.isHostileTo(this.lastAttacker, self)
				&& horizontalDistance(this.lastAttacker.position(), center) < leash + 32.0
				&& this.lastAttacker.distanceTo(self) < sense) {
			return this.lastAttacker;
		}
		Vec3 eye = self.getAimOrigin(1f);
		Entity best = null;
		double bestDist = Double.MAX_VALUE;
		List<Entity> list = world.getEntities(self, self.getBoundingBox().inflate(sense, sense * 0.5, sense),
				e -> this.faction.isHostileTo(e, self));
		for (Entity e : list) {
			if (horizontalDistance(e.position(), center) > leash + 12.0) {
				continue;
			}
			double d = e.distanceToSqr(self);
			if (d >= bestDist || d > sense * sense) {
				continue;
			}
			if (!this.canSee(world, self, eye, e)) {
				continue;
			}
			best = e;
			bestDist = d;
		}
		return best;
	}

	private boolean canSee(ServerLevel world, MechEntity self, Vec3 eye, Entity e) {
		Vec3 c = e.getBoundingBox().getCenter();
		BlockHitResult hit = world.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, self));
		return hit.getType() != HitResult.Type.BLOCK;
	}

	// ================================================================================== weapons

	private boolean hasMelee(MechEntity self) {
		for (MountSlot m : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM}) {
			MechPart p = self.getAssembly().part(m);
			if (p != null && p.weapon() != null && p.weapon().isMelee()) {
				return true;
			}
		}
		return false;
	}

	private boolean hasGuns(MechEntity self) {
		for (MountSlot m : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK}) {
			if (weaponIndex(self, m) >= 0) {
				return true;
			}
		}
		return false;
	}

	/** Index of the mount's weapon in the frame's weapon list, or -1 (no gun there). */
	public static int weaponIndex(MechEntity self, MountSlot mount) {
		List<WeaponDefinition> weapons = self.getDefinition().weapons();
		for (int i = 0; i < weapons.size(); i++) {
			if (weapons.get(i).seatIndex() == mount.seatIndex()) {
				return i;
			}
		}
		return -1;
	}

	private void fire(MechEntity self, ServerLevel world, double dist) {
		// Bursts: fire for a while, then hold; a skilled pilot holds less.
		if (--this.burstTimer <= 0) {
			this.burstOn = !this.burstOn;
			this.burstTimer = this.burstOn ? 20 + self.getRandom().nextInt(30)
					: (int) (6 + self.getRandom().nextInt(20) * (1.2f - this.skill));
		}
		float yawErr = Math.abs(Mth.wrapDegrees(this.desiredYaw - self.getYRot()));
		boolean visible = this.canSee(world, self, self.getAimOrigin(1f), this.target);
		for (MountSlot mount : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK}) {
			MechPart p = self.getAssembly().part(mount);
			if (p == null || p.weapon() == null) {
				continue;
			}
			if (p.weapon().isMelee()) {
				double reachGap = dist - (self.getBbWidth() + this.target.getBbWidth()) * 0.5;
				if (reachGap < p.weapon().melee().lungeRange() * 0.8 && yawErr < 40f && self.isMeleeReady()) {
					self.tryMelee(mount);
				}
				continue;
			}
			if (!this.burstOn || !visible) {
				continue;
			}
			if (mount.isArmWeapon() ? yawErr > 30f : yawErr > 70f) {
				continue;
			}
			if (mount.isBackWeapon() && dist < 8.0) {
				continue;
			}
			int index = weaponIndex(self, mount);
			if (index >= 0) {
				self.tryFireWeapon(index, null, this.target);
			}
		}
	}

	// ================================================================================== movement helpers

	private void patrol(MechEntity self, ServerLevel world, Vec3 center, double leash) {
		if (this.patrolWait > 0) {
			this.patrolWait--;
			return;
		}
		if (this.patrolPoint == null || horizontalDistance(this.patrolPoint, self.position()) < 3.0) {
			if (this.patrolPoint != null) {
				this.patrolWait = 40 + self.getRandom().nextInt(100);
				this.patrolPoint = null;
				return;
			}
			double a = self.getRandom().nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(self.getRandom().nextDouble()) * leash * 0.6;
			int x = Mth.floor(center.x + Math.cos(a) * r);
			int z = Mth.floor(center.z + Math.sin(a) * r);
			int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			this.patrolPoint = new Vec3(x + 0.5, y, z + 0.5);
		}
	}

	/** The wingman's place: beside and behind the owner, on the side its UUID picks. */
	private Vec3 escortSlot(Vec3 ownerPos, float heading, MechEntity self) {
		float side = (self.getUUID().getLeastSignificantBits() & 1) == 0 ? 1f : -1f;
		Vec3 local = localToWorld(heading, -0.6f, side);
		return ownerPos.add(local.scale(7.0));
	}

	/** Wingmen seen ticking (weak: a removed or unloaded frame just drops out). */
	private static final java.util.Set<MechEntity> ESCORTS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
	/** A wingman further than this from its owner is moved up to them. */
	private static final double CATCH_UP_DISTANCE = 64.0;

	/** Once a second (server): wingmen left far behind are moved next to their owner - including
	 * those whose chunk has stopped ticking because the owner outran the simulation distance (their
	 * own AI cannot run there). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || ESCORTS.isEmpty()) {
			return;
		}
		for (MechEntity m : List.copyOf(ESCORTS)) {
			MechBrain b = m.getBrain();
			if (m.isRemoved() || b == null || b.mode != Mode.ESCORT || b.owner == null || m.tudursvehiclemod$isDestroyed()) {
				ESCORTS.remove(m);
				continue;
			}
			if (m.isVehicle() || !(m.level() instanceof ServerLevel world)) {
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
			if (owner == null || owner.level() != world || owner.isSpectator()) {
				continue;
			}
			if (horizontalDistance(owner.position(), m.position()) > CATCH_UP_DISTANCE) {
				float heading = Float.isNaN(b.ownerHeading) ? owner.getYRot() : b.ownerHeading;
				b.teleportTo(m, world, b.escortSlot(owner.position(), heading, m));
			}
		}
	}

	private void teleportTo(MechEntity self, ServerLevel world, Vec3 p) {
		int x = Mth.floor(p.x);
		int z = Mth.floor(p.z);
		int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		AABB box = self.getBoundingBox().move(x + 0.5 - self.getX(), y - self.getY(), z + 0.5 - self.getZ());
		if (!world.noCollision(self, box) || !world.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) {
			return;
		}
		self.snapTo(x + 0.5, y, z + 0.5, self.getYRot(), 0f);
		self.setDeltaMovement(Vec3.ZERO);
	}

	static float yawOf(Vec3 d) {
		return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
	}

	static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Frame-local (forward, left) input -> world horizontal direction (model +X is left). */
	static Vec3 localToWorld(float yaw, float fwd, float str) {
		double r = Math.toRadians(yaw);
		double cos = Math.cos(r);
		double sin = Math.sin(r);
		return new Vec3(str * cos - fwd * sin, 0, str * sin + fwd * cos);
	}

	static float[] worldToLocal(float yaw, Vec3 d) {
		double r = Math.toRadians(yaw);
		double cos = Math.cos(r);
		double sin = Math.sin(r);
		float fwd = (float) (-d.x * sin + d.z * cos);
		float str = (float) (d.x * cos + d.z * sin);
		return new float[]{fwd, str};
	}

	// ================================================================================== persistence

	public void write(ValueOutput view) {
		view.putString("Faction", this.faction.id());
		view.putString("Mode", this.mode.name().toLowerCase());
		if (this.home != null) {
			view.putDouble("HomeX", this.home.x);
			view.putDouble("HomeY", this.home.y);
			view.putDouble("HomeZ", this.home.z);
		}
		view.putDouble("Radius", this.radius);
		if (this.owner != null) {
			view.putString("Owner", this.owner.toString());
		}
		if (this.post != null) {
			view.putIntArray("Post", new int[]{this.post.getX(), this.post.getY(), this.post.getZ(), this.postSlot});
		}
		view.putFloat("Skill", this.skill);
		view.putBoolean("DropsRolled", this.dropsRolled);
	}

	public static MechBrain read(ValueInput view) {
		Faction f = Faction.byId(view.getStringOr("Faction", "none"));
		if (f == Faction.NONE) {
			return null;
		}
		double hx = view.getDoubleOr("HomeX", Double.NaN);
		Vec3 home = Double.isNaN(hx) ? null : new Vec3(hx, view.getDoubleOr("HomeY", 0), view.getDoubleOr("HomeZ", 0));
		MechBrain b = new MechBrain(f, Mode.byId(view.getStringOr("Mode", "guard")), home, view.getDoubleOr("Radius", 32.0));
		String owner = view.getStringOr("Owner", "");
		if (!owner.isEmpty()) {
			try {
				b.owner = UUID.fromString(owner);
			} catch (IllegalArgumentException ignored) {
				b.owner = null;
			}
		}
		view.getIntArray("Post").ifPresent(a -> {
			if (a.length >= 4) {
				b.post = new BlockPos(a[0], a[1], a[2]);
				b.postSlot = a[3];
			}
		});
		b.skill = view.getFloatOr("Skill", 0.5f);
		b.dropsRolled = view.getBooleanOr("DropsRolled", false);
		return b;
	}
}
