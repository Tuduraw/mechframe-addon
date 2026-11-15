package com.example.mechframe.entity;

import com.example.mechframe.item.MechPartItem;

import java.util.Objects;

import java.util.List;

import com.example.mechframe.MechFrame;
import com.example.mechframe.asm.DefinitionFactory;
import com.example.mechframe.part.Assembly;
import com.example.mechframe.part.MechForm;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MechPreset;
import com.example.mechframe.part.MechStats;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.asset.WeaponAimRange;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity.WeaponState;
import com.example.tudursvehiclemod.asset.WeaponStats;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.FreeCameraVehicle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One assembled frame. Extends AbstractVehicleEntity directly (as humanoidrobotaddon does - a car
 * tilts its body to slopes and brakes on the jump key, a frame does neither) and overrides
 * getDefinition() so that everything the base mod does - health, weapons, seats, HUD, hit
 * detection, firing - follows the parts that are actually fitted (see DefinitionFactory).
 *
 * <h2>Robot form - high-speed movement</h2>
 * The body turns toward the pilot's camera (at the legs' turn rate); W/A/S/D move in any direction
 * relative to the body. Holding Boost runs at the booster's speed (free on the ground, EN in the
 * air). Quick Boost is an instant burst in the input direction that briefly ignores traction; with
 * no input the frame brakes hard ("急停止"). Space jumps, and holding it afterwards hovers on EN.
 * EN recharges after a short delay; emptying it overheats the generator for longer. Velocity is
 * blended toward the target with a per-leg traction, so direction changes are near-instant without
 * teleporting.
 *
 * <h2>Jet form</h2>
 * Transform-set frames switch to an aircraft flight model (ported from humanoidrobotaddon's port of
 * the base AircraftEntity: quaternion attitude, mouse steering, stall, climb/dive speed) with Boost
 * as afterburner and Quick Boost as a lateral jink.
 *
 * <p>Movement runs on both sides (the pilot's client is authoritative for position, as for every
 * vanilla vehicle); inputs reach the server through MechInputPayload, one-shot actions through
 * MechActionPayload. EN is simulated on both sides and the server's value is synced. */
public class MechEntity extends AbstractVehicleEntity implements FreeCameraVehicle {

	// ------------------------------------------------------------------ synced state

	private static final EntityDataAccessor<String> ASSEMBLY = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> EN = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
	/** bit0 boosting, bit1 hovering, bit2 afterburner, bit3 braking, bit5 overheat,
	 * bits 8-11 quick-boost counter, 12-14 quick-boost octant, 16-19 landing counter, 20-23 jump counter. */
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LOCK_ID = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> CANDIDATE_ID = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
	/** Tank controls: the lower hull's yaw (NaN when the hull just follows the travel direction). */
	/** Turbo mode (frames that can't transform): a fast, committed forward flight. */
	private static final EntityDataAccessor<Boolean> TURBO = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> HULL_YAW = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
	/** Faction ordinal of the defence AI (0 = none) - synced so clients can tell allies from foes. */
	private static final EntityDataAccessor<Integer> FACTION = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);

	public static MechClientHooks clientHooks = MechClientHooks.NONE;

	// ------------------------------------------------------------------ tuning

	private static final double GRAVITY = 0.11;
	private static final double MAX_FALL_SPEED = -2.6;
	private static final double HOVER_MAX_RISE = 0.5;
	private static final float BOOST_GLIDE_GRAVITY = 0.45f;
	private static final float BRAKE_TRACTION = 0.42f;
	private static final float AIR_TRACTION = 0.035f;
	private static final float AIR_BOOST_TRACTION = 0.13f;
	private static final int QB_TICKS = 6;
	private static final int QB_BURST_TICKS = 2;
	private static final int TRANSFORM_TICKS = 24;
	private static final int GROUNDED_GRACE_TICKS = 2;
	/** Turbo: cruise speed as a multiple of the booster's boost speed, how fast velocity and heading
	 * follow the pilot, and the extra fuel burned (multiples of the frame's full-throttle use). */
	private static final float TURBO_SPEED = 2.4f;
	private static final float TURBO_TRACTION = 0.045f;
	private static final float TURBO_TURN = 0.3f;
	private static final float TURBO_EXTRA_FUEL = 2.0f;
	/** Tank controls: hull turn rate as a fraction of the legs' turn rate. */
	private static final float HULL_TURN_FACTOR = 0.6f;
	private static final double AIM_RANGE = 256.0;

	// ------------------------------------------------------------------ inputs (set by payload / local client)

	public float inForward;
	public float inStrafe;
	public boolean inJump;
	public boolean inBoost;
	/** The pilot drives tank legs like a tank: W/S along the hull, A/D turn the hull, the upper body is the turret. */
	public boolean inTankControls;
	/** Jet form: accumulated mouse steering, degrees, consumed each tick. */
	public float pendingYawInput;
	public float pendingPitchInput;

	// ------------------------------------------------------------------ simulation state

	private float en = -1f;
	private int enDelay;
	private boolean overheat;
	private boolean drainedThisTick;
	private int qbTimer;
	private int qbCooldown;
	private int ticksSinceGrounded;
	private float hullYaw = Float.NaN;
	private float turboPitch;
	private int airTicks;
	private boolean prevJump;
	private boolean boostingNow;
	private boolean hoveringNow;
	private boolean brakingNow;
	private boolean afterburnerNow;
	private int brakeHold;
	private double lastFallSpeed;
	private float transform;
	private float prevTransform;
	private int transformCooldown;
	private float stepUpVisualOffset;
	private float prevStepUpVisualOffset;
	private float travel;
	private float prevTravel;
	private int qbCounter;
	private int qbOctant;
	private int landCounter;
	private int jumpCounter;
	/** Melee: ticks since the swing started (-1: none), which arm, the dash direction, the synced count. */
	private int meleeTick = -1;
	private MountSlot meleeArm = MountSlot.R_ARM;
	private Vec3 meleeDir = Vec3.ZERO;
	/** What this swing goes for (picked when it starts), or null: straight ahead. */
	private Entity meleeVictim;
	private int meleeCooldown;
	private int meleeCounter;
	private int meleeCounterSeen = -1;
	/** Swing timeline (ticks): dash and wind-up until STRIKE, the blow lands on STRIKE, recovered by END. */
	public static final int MELEE_STRIKE = 5;
	public static final int MELEE_END = 13;
	private int jinkTicks;
	private int jinkSide;

	// lean (spring), both sides from position deltas
	private float leanPitch;
	private float leanRoll;
	private float prevLeanPitch;
	private float prevLeanRoll;
	private float leanPitchVel;
	private float leanRollVel;
	private Vec3 prevMotion = Vec3.ZERO;

	/** Client-side animation state (client.anim.MechAnimator), owned by the client hooks. */
	public Object clientAnimation;

	/** The defence AI (server; null: an ordinary frame). See ai.MechBrain. */
	private com.example.mechframe.ai.MechBrain brain;

	// ------------------------------------------------------------------ caches

	// No field initializers here: getDefinition() (and so getAssembly()) already runs inside the
	// super constructor, and an initializer would wipe what it cached there afterwards.
	private String cachedAssemblyText;
	private Assembly cachedAssembly;
	private int cachedGeneration;
	private MechLayout cachedLayout;
	/** getDefinition()'s last answer and what it was built from: the base mod asks for the definition
	 * many times per tick, and building the shared cache's key means encoding the whole assembly. */
	private DefinitionFactory.Entry defEntry;
	private MechLayout defLayout;
	private MechForm defForm;
	private Identifier defVehicleId;
	private java.util.Map<Identifier, VehicleDefinition> defRegistry;
	private VehicleDefinition defPreset;
	private int defEpoch;

	private Vec3 aimPointCache;
	private float aimCacheDelta = Float.NaN;
	private int aimCacheAge = -1;

	public MechEntity(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ASSEMBLY, "");
		builder.define(FORM, 0);
		builder.define(EN, 0f);
		builder.define(ACTION, 0);
		builder.define(LOCK_ID, -1);
		builder.define(CANDIDATE_ID, -1);
		builder.define(HULL_YAW, Float.NaN);
		builder.define(TURBO, false);
		builder.define(FACTION, 0);
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "striker");
	}

	// ==================================================================================== assembly

	/** The preset's name ("KD) Striker") instead of the generic entity type name, so FCS readouts,
	 * death messages and the like say which frame it is. */
	@Override
	protected net.minecraft.network.chat.Component getTypeName() {
		Identifier id = this.getVehicleDefinitionId();
		if (id != null) {
			try {
				return net.minecraft.network.chat.Component.literal(this.getDefinition().displayName(id));
			} catch (RuntimeException ignored) {
				// Definition not resolvable yet (data still loading): fall back to the type name.
			}
		}
		return super.getTypeName();
	}

	public Assembly getAssembly() {
		if (this.entityData == null) {
			return Assembly.EMPTY;
		}
		String text = this.entityData.get(ASSEMBLY);
		if (this.cachedAssembly == null || !text.equals(this.cachedAssemblyText)) {
			this.cachedAssemblyText = text;
			this.cachedAssembly = Assembly.decode(text);
			this.cachedLayout = null;
		}
		return this.cachedAssembly;
	}

	/** Null until the frame has a complete, loaded assembly. */
	public MechLayout getLayout() {
		Assembly assembly = this.getAssembly();
		if (assembly.isEmpty()) {
			return null;
		}
		if (this.cachedLayout == null || this.cachedGeneration != PartRegistry.generation() + 1) {
			this.cachedLayout = MechLayout.of(assembly);
			this.cachedGeneration = PartRegistry.generation() + 1;
		}
		return this.cachedLayout.valid ? this.cachedLayout : null;
	}

	public MechStats getStats() {
		MechLayout layout = this.getLayout();
		return layout != null ? layout.stats : MechStats.INVALID;
	}

	/** Server: replaces the assembly, keeping health at the same fraction of the new maximum and
	 * dropping out of the jet form if the new parts can't take it. */
	public void setAssembly(Assembly assembly) {
		float ratio = this.getMaxHealth() > 0f ? Mth.clamp(this.getHealth() / this.getMaxHealth(), 0f, 1f) : 1f;
		Assembly before = this.getAssembly();
		VehicleDefinition oldDef = this.getDefinition();
		List<WeaponState> oldStates = new java.util.ArrayList<>();
		for (int i = 0; i < oldDef.weapons().size(); i++) {
			oldStates.add(this.tudursvehiclemod$getWeaponState(i));
		}
		this.entityData.set(ASSEMBLY, assembly.encode());
		this.remapWeaponState(oldDef, oldStates, before, assembly);
		MechLayout layout = this.getLayout();
		if (layout == null || !layout.canTransform) {
			this.entityData.set(FORM, 0);
		}
		if (!this.tudursvehiclemod$isDestroyed()) {
			this.tudursvehiclemod$setHealth(this.getMaxHealth() * ratio);
		}
		this.en = Math.min(this.en, this.getStats().enCapacity());
		this.refreshDimensions();
	}

	/** The base mod keeps magazine, reserve, reload, heat and fire mode per weapon INDEX. After a
	 * refit the same index can be a different weapon (emptying the right arm shifts every other
	 * weapon down one), so carry each mount's state over to its new index, and start a weapon that
	 * was actually swapped out empty - the same state a freshly built frame's weapons start in
	 * (a null entry, see AbstractVehicleEntity.tudursvehiclemod$setWeaponStates()). */
	private void remapWeaponState(VehicleDefinition oldDef, List<WeaponState> oldStates, Assembly before, Assembly after) {
		List<WeaponDefinition> oldWeapons = oldDef.weapons();
		List<WeaponDefinition> newWeapons = this.getDefinition().weapons();
		List<WeaponState> states = new java.util.ArrayList<>(newWeapons.size());
		for (WeaponDefinition weapon : newWeapons) {
			MountSlot mount = MountSlot.bySeat(weapon.seatIndex());
			WeaponState kept = null;
			if (mount != null && Objects.equals(before.get(mount), after.get(mount))) {
				for (int j = 0; j < oldWeapons.size() && j < oldStates.size(); j++) {
					if (oldWeapons.get(j).seatIndex() == weapon.seatIndex()) {
						kept = oldStates.get(j);
						break;
					}
				}
			}
			states.add(kept);
		}
		this.tudursvehiclemod$setWeaponStates(states);
	}

	// ==================================================================================== defence AI

	public com.example.mechframe.ai.MechBrain getBrain() {
		return this.brain;
	}

	/** Server: hands the frame to the defence AI (null: back to an ordinary frame). */
	public void setBrain(com.example.mechframe.ai.MechBrain brain) {
		this.brain = brain;
		this.entityData.set(FACTION, brain == null ? 0 : brain.faction.ordinal());
		if (brain == null) {
			this.entityData.set(LOCK_ID, -1);
			this.inForward = 0f;
			this.inStrafe = 0f;
			this.inJump = false;
			this.inBoost = false;
		}
	}

	public com.example.mechframe.ai.Faction getFaction() {
		int i = this.entityData.get(FACTION);
		com.example.mechframe.ai.Faction[] all = com.example.mechframe.ai.Faction.values();
		return i >= 0 && i < all.length ? all[i] : com.example.mechframe.ai.Faction.NONE;
	}

	/** Server: the AI drives this tick (it has a brain, nobody pilots it, and it can still move). */
	public boolean aiDriven() {
		return this.brain != null && this.getControllingPassenger() == null && !this.tudursvehiclemod$isDestroyed();
	}

	/** Server: the AI's FCS lock (null: none). */
	public void aiLock(Entity target) {
		int id = target == null ? -1 : target.getId();
		if (this.entityData.get(LOCK_ID) != id) {
			this.entityData.set(LOCK_ID, id);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) {
		boolean wasDestroyed = this.tudursvehiclemod$isDestroyed();
		if (this.brain != null) {
			Entity attacker = source.getEntity();
			if (attacker != null && attacker.getVehicle() != null) {
				attacker = attacker.getRootVehicle();
			}
			this.brain.onAttacked(this, attacker);
		}
		boolean result = super.hurtServer(world, source, amount);
		if (!wasDestroyed && this.tudursvehiclemod$isDestroyed()) {
			this.onAiUnitDestroyed(world);
		}
		return result;
	}

	/** An enemy unit going down: each of its parts drops on its own roll (MechServerConfig#partDropChance). */
	private void onAiUnitDestroyed(ServerLevel world) {
		if (this.brain == null || this.brain.faction != com.example.mechframe.ai.Faction.ENEMY || this.brain.dropsRolled) {
			return;
		}
		this.brain.dropsRolled = true;
		Assembly assembly = this.getAssembly();
		double chance = com.example.mechframe.ai.MechServerConfig.partDropChance;
		for (MountSlot mount : MountSlot.values()) {
			if (assembly.isLocked(mount)) {
				continue; // a weapon built into the arms comes with the arms, not on its own
			}
			MechPart part = assembly.part(mount);
			if (part == null || this.getRandom().nextDouble() >= chance) {
				continue;
			}
			net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(world, this.getX(), this.getY() + 1.5, this.getZ(),
					MechPartItem.stackFor(part));
			item.setDeltaMovement((this.getRandom().nextDouble() - 0.5) * 0.3, 0.35, (this.getRandom().nextDouble() - 0.5) * 0.3);
			item.setDefaultPickUpDelay();
			world.addFreshEntity(item);
		}
	}

	/** Command terminal on a frame: off -> base defence -> wingman -> off (friendly side only). */
	private InteractionResult command(Player player) {
		if (!(this.level() instanceof ServerLevel world)) {
			return InteractionResult.SUCCESS;
		}
		com.example.mechframe.ai.MechBrain b = this.brain;
		if (b != null && b.faction == com.example.mechframe.ai.Faction.ENEMY) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.mechframe.command.enemy"), true);
			return InteractionResult.FAIL;
		}
		if (this.tudursvehiclemod$isDestroyed()) {
			return InteractionResult.FAIL;
		}
		if (b != null && b.owner != null && !b.owner.equals(player.getUUID()) && !player.isCreative()) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.mechframe.command.not_yours"), true);
			return InteractionResult.FAIL;
		}
		String key;
		if (b == null) {
			b = new com.example.mechframe.ai.MechBrain(com.example.mechframe.ai.Faction.FRIENDLY,
					com.example.mechframe.ai.MechBrain.Mode.GUARD, this.position(), 32.0);
			b.owner = player.getUUID();
			this.setBrain(b);
			key = "message.mechframe.command.guard";
		} else if (b.mode == com.example.mechframe.ai.MechBrain.Mode.GUARD && b.post == null) {
			b.mode = com.example.mechframe.ai.MechBrain.Mode.ESCORT;
			b.owner = player.getUUID();
			key = "message.mechframe.command.escort";
		} else {
			this.setBrain(null);
			key = "message.mechframe.command.off";
		}
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key, this.getName()), true);
		return InteractionResult.SUCCESS;
	}

	/** Sneak + right-click packs the frame up into a spawner (the base mod), which only rebuilds the
	 * stock frame: hand back every part that came from a garage first so it isn't lost. */
	/** The base mod boards the player from interactAt (per-seat hit boxes), which runs before
	 * interact: a frame given orders with the command terminal - or an enemy unit - must be
	 * handled here already, or the player would climb in. */
	@Override
	public InteractionResult interactAt(Player player, Vec3 hitPos, InteractionHand hand) {
		if (player.getItemInHand(hand).is(com.example.mechframe.item.MechItems.COMMAND_TERMINAL)) {
			return this.command(player);
		}
		if (this.getFaction() == com.example.mechframe.ai.Faction.ENEMY && !player.isCreative()) {
			return InteractionResult.FAIL;
		}
		return super.interactAt(player, hitPos, hand);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (player.getItemInHand(hand).is(com.example.mechframe.item.MechItems.COMMAND_TERMINAL)) {
			return this.command(player);
		}
		if (this.getFaction() == com.example.mechframe.ai.Faction.ENEMY && !player.isCreative()) {
			// Enemy units cannot be boarded or packed up.
			return InteractionResult.FAIL;
		}
		if (player.isShiftKeyDown() && !this.level().isClientSide() && this.getPassengers().isEmpty() && !this.isRemoved()) {
			Assembly assembly = this.getAssembly();
			for (MountSlot mount : MountSlot.values()) {
				Identifier id = assembly.get(mount);
				MechPart part = id != null && !assembly.isStock(mount) ? PartRegistry.getOrNull(id) : null;
				if (part != null) {
					ItemStack stack = MechPartItem.stackFor(part);
					if (!player.getInventory().add(stack)) {
						player.drop(stack, false);
					}
				}
			}
		}
		return super.interact(player, hand);
	}

	@Override
	public void setVehicleDefinitionId(Identifier id) {
		super.setVehicleDefinitionId(id);
		if (!this.level().isClientSide() && this.getAssembly().isEmpty()) {
			Optional<MechPreset> preset = PartRegistry.preset(id);
			if (preset.isPresent()) {
				this.entityData.set(ASSEMBLY, preset.get().assembly().encode());
				this.tudursvehiclemod$setHealth(this.getMaxHealth());
				this.en = this.getStats().enCapacity();
				this.refreshDimensions();
			}
		}
	}

	/** The stock assembly of this frame's preset (what it spawned with). */
	public Assembly getStockAssembly() {
		return PartRegistry.preset(this.getVehicleDefinitionId()).map(MechPreset::assembly).orElse(Assembly.EMPTY);
	}

	@Override
	public VehicleDefinition getDefinition() {
		MechLayout layout = this.getLayout();
		if (layout == null) {
			return super.getDefinition();
		}
		MechForm form = this.getForm();
		Identifier vehicleId = this.getVehicleDefinitionId();
		// The preset definition is looked up again only when the id changed or the vehicle registry
		// was replaced (a reload installs a new map).
		java.util.Map<Identifier, VehicleDefinition> registry = VehicleRegistry.getAll();
		if (vehicleId != this.defVehicleId || registry != this.defRegistry) {
			this.defVehicleId = vehicleId;
			this.defRegistry = registry;
			this.defPreset = vehicleId != null ? registry.get(vehicleId) : null;
			this.defEntry = null;
		}
		if (this.defEntry == null || layout != this.defLayout || form != this.defForm || this.defEpoch != DefinitionFactory.epoch()) {
			this.defEpoch = DefinitionFactory.epoch();
			this.defEntry = DefinitionFactory.get(layout, form, this.defPreset);
			this.defLayout = layout;
			this.defForm = form;
		}
		return this.defEntry.definition();
	}

	/** Once per tick: tells the shared caches this frame's layout and definition are still in use
	 * (anything no loaded frame marks for a while is released - see DefinitionFactory.sweep()). */
	private void markCachesInUse() {
		if (this.cachedLayout != null) {
			this.cachedLayout.touch();
		}
		if (this.defEntry != null) {
			this.defEntry.touch();
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
		super.onSyncedDataUpdated(data);
		if (data == ASSEMBLY || data == FORM) {
			this.refreshDimensions();
		}
		if (data == FORM && this.flightOrientation != null) {
			this.onFormChanged();
		}
	}

	public MechForm getForm() {
		return this.entityData == null ? MechForm.ROBOT : MechForm.byOrdinal(this.entityData.get(FORM));
	}

	public boolean isJet() {
		return this.getForm() == MechForm.JET;
	}

	/** 0 = robot, 1 = jet, eased in between during a transformation (render-interpolated). */
	public float getTransformProgress(float tickDelta) {
		return Mth.lerp(tickDelta, this.prevTransform, this.transform);
	}

	public boolean isTransforming() {
		return this.transform > 0.001f && this.transform < 0.999f;
	}

	// ==================================================================================== tick

	@Override
	public void tick() {
		if (!this.level().isClientSide() && this.entityData.get(FORM) == 1
				&& (this.getLayout() == null || !this.getLayout().canTransform)) {
			// A data reload took the transformation away (or the parts are gone): back to robot form,
			// or the frame would be stuck flying a jet model with no way to transform back.
			this.entityData.set(FORM, 0);
		}
		if (this.lastFormSeen < 0) {
			this.lastFormSeen = this.entityData.get(FORM);
			this.transform = this.prevTransform = this.isJet() ? 1f : 0f;
		}
		this.prevTransform = this.transform;
		float target = this.isJet() ? 1f : 0f;
		this.transform = target > this.transform
				? Math.min(target, this.transform + 1f / TRANSFORM_TICKS)
				: Math.max(target, this.transform - 1f / TRANSFORM_TICKS);
		if (this.transformCooldown > 0) {
			this.transformCooldown--;
		}
		MechStats stats = this.getStats();
		this.markCachesInUse();
		if (this.en < 0f) {
			this.en = this.level().isClientSide() ? this.entityData.get(EN) : stats.enCapacity();
		}

		if (!this.level().isClientSide() && this.aiDriven() && this.level() instanceof ServerLevel aiWorld) {
			this.brain.tick(this, aiWorld);
			if (this.isRemoved()) {
				return;
			}
		}

		super.tick();

		this.tickMelee();
		this.updateLean();
		if (this.level().isClientSide()) {
			// The pilot's own client keeps its predicted EN unless it has drifted far from the server.
			float synced = this.entityData.get(EN);
			if (!this.isLocalPilot() || Math.abs(synced - this.en) > stats.enCapacity() * 0.3f) {
				this.en = synced;
			}
			clientHooks.tick(this);
			return;
		}
		if (this.getControllingPassenger() == null && !this.aiDriven()) {
			this.inForward = 0f;
			this.inStrafe = 0f;
			this.inJump = false;
			this.inBoost = false;
		}
		float syncedHull = this.entityData.get(HULL_YAW);
		if (Float.isNaN(this.hullYaw) != Float.isNaN(syncedHull) || (!Float.isNaN(this.hullYaw) && Math.abs(Mth.wrapDegrees(syncedHull - this.hullYaw)) > 0.5f)) {
			this.entityData.set(HULL_YAW, this.hullYaw);
		}
		if (Math.abs(this.entityData.get(EN) - this.en) > 0.5f) {
			this.entityData.set(EN, this.en);
		}
		this.entityData.set(ACTION, this.packAction());
		if (this.tickCount % 2 == 0) {
			this.updateCandidate();
		}
		this.pruneLock();
	}

	private int packAction() {
		int bits = 0;
		if (this.boostingNow) bits |= 1;
		if (this.hoveringNow) bits |= 2;
		if (this.afterburnerNow) bits |= 4;
		if (this.brakingNow) bits |= 8;
		if (this.overheat) bits |= 32;
		bits |= (this.qbCounter & 15) << 8;
		bits |= (this.qbOctant & 7) << 12;
		bits |= (this.landCounter & 15) << 16;
		bits |= (this.jumpCounter & 15) << 20;
		bits |= (this.meleeCounter & 15) << 24;
		bits |= (this.meleeArm == MountSlot.L_ARM ? 1 : 0) << 28;
		return bits;
	}

	public int getSyncedAction() {
		return this.entityData.get(ACTION);
	}

	/** Tank legs driven with tank controls (robot form, piloted, the pilot asked for them). */
	public boolean isTankControlled() {
		MechStats s = this.getStats();
		return this.inTankControls && s != null && s.legType() == MechPart.LegType.TANK && !this.isJet()
				&& this.getControllingPassenger() instanceof Player;
	}

	/** Hull yaw under tank controls (NaN otherwise): the pilot's own prediction, or the synced value. */
	public float getHullYaw() {
		return this.isLocalPilot() || !this.level().isClientSide() ? this.hullYaw : this.entityData.get(HULL_YAW);
	}

	/** True on the client of the player piloting this frame. */
	public boolean isLocalPilot() {
		return this.level().isClientSide() && this.getControllingPassenger() instanceof Player p && p.isLocalPlayer();
	}

	public boolean isBoostingVisual() {
		return this.isLocalPilot() ? this.boostingNow : (this.getSyncedAction() & 1) != 0;
	}

	public boolean isHoveringVisual() {
		return this.isLocalPilot() ? this.hoveringNow : (this.getSyncedAction() & 2) != 0;
	}

	public boolean isAfterburnerVisual() {
		return this.isLocalPilot() ? this.afterburnerNow : (this.getSyncedAction() & 4) != 0;
	}

	public boolean isBrakingVisual() {
		return this.isLocalPilot() ? this.brakingNow : (this.getSyncedAction() & 8) != 0;
	}

	public float getEn() {
		return Math.max(0f, this.en);
	}

	public boolean isOverheated() {
		return this.isLocalPilot() ? this.overheat : (this.getSyncedAction() & 32) != 0;
	}

	public boolean isQuickBoostReady() {
		MechStats s = this.getStats();
		return this.qbCooldown <= 0 && !this.overheat && this.en >= s.qbCost() * 0.5f && s.qbSpeed() > 0f;
	}

	public boolean isGroundedVisual() {
		return this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS;
	}

	public float getTravel(float tickDelta) {
		return Mth.lerp(tickDelta, this.prevTravel, this.travel);
	}

	public float getStepUpVisualOffset(float tickDelta) {
		return Mth.lerp(tickDelta, this.prevStepUpVisualOffset, this.stepUpVisualOffset);
	}

	public int getJinkTicks() {
		return this.jinkTicks;
	}

	public int getJinkSide() {
		return this.jinkSide;
	}

	// ==================================================================================== movement

	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		this.drainedThisTick = false;
		if (this.qbCooldown > 0) {
			this.qbCooldown--;
		}
		if (this.jinkTicks > 0) {
			this.jinkTicks--;
		}
		if (this.tudursvehiclemod$isSinking()) {
			this.move(MoverType.SELF, this.getDeltaMovement());
			return;
		}
		if (this.isJet() && this.getLayout() != null) {
			this.updateJetFlight(def);
		} else {
			this.jetActive = false;
			this.updateRobotMovement();
		}
		this.updateEn();
	}

	private boolean hasControl() {
		return (this.getControllingPassenger() instanceof Player || this.aiDriven())
				&& !this.tudursvehiclemod$isDestroyed()
				&& this.getLayout() != null
				&& !this.tudursvehiclemod$isOutOfFuel();
	}

	private void updateRobotMovement() {
		MechStats s = this.getStats();
		if (this.isTurbo()) {
			this.updateTurbo(s);
			return;
		}
		this.turboPitch = 0f;
		boolean wasGrounded = this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS;
		if (this.onGround()) {
			this.ticksSinceGrounded = 0;
		} else {
			this.ticksSinceGrounded++;
		}
		boolean grounded = this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS;
		boolean control = this.hasControl();

		// Level out whatever attitude the jet form left behind.
		this.setXRot(stepTowardAngle(this.getXRot(), 0f, 8f));
		this.prevRoll = this.roll;
		this.roll = stepTowardAngle(this.roll, 0f, 8f);

		// Body follows the camera.
		if (this.getControllingPassenger() instanceof Player pilot && !this.tudursvehiclemod$isDestroyed()) {
			float rate = s.turnRate() * (grounded ? 1f : 0.85f);
			this.setYRot(stepTowardAngle(this.getYRot(), pilot.getYRot(), rate));
		} else if (this.aiDriven() && !Float.isNaN(this.brain.desiredYaw)) {
			float rate = s.turnRate() * (grounded ? 1f : 0.85f);
			this.setYRot(stepTowardAngle(this.getYRot(), this.brain.desiredYaw, rate));
		}

		float fwd = control ? Mth.clamp(this.inForward, -1f, 1f) : 0f;
		float str = control ? Mth.clamp(this.inStrafe, -1f, 1f) : 0f;
		// Tank controls: A/D turn the hull, W/S drive along it; the upper body (turret) keeps following the camera.
		boolean tankMode = this.isTankControlled();
		if (tankMode) {
			if (Float.isNaN(this.hullYaw)) {
				this.hullYaw = this.getYRot();
			}
			this.hullYaw = Mth.wrapDegrees(this.hullYaw - str * s.turnRate() * HULL_TURN_FACTOR * (grounded ? 1f : 0.5f));
			str = 0f;
		} else if (s.legType() != MechPart.LegType.TANK || this.getControllingPassenger() != null) {
			this.hullYaw = Float.NaN;
		}
		float len = Mth.sqrt(fwd * fwd + str * str);
		if (len > 1f) {
			fwd /= len;
			str /= len;
			len = 1f;
		}
		boolean hasInput = len > 0.01f;
		boolean boosting = control && this.inBoost && s.boostSpeed() > 0f && (grounded || (this.en > 0f && !this.overheat));
		// In the air the boost only works - and only costs EN - while a direction is held: just falling with the
		// boost switched on (it is a toggle by default) is falling, not boosting.
		boolean airBoost = boosting && !grounded && hasInput;
		this.boostingNow = boosting && hasInput;

		double yawRad = Math.toRadians(tankMode ? this.hullYaw : this.getYRot());
		double cos = Math.cos(yawRad);
		double sin = Math.sin(yawRad);
		// Model +X (left) is world (cos, sin), model +Z (forward) is world (-sin, cos).
		double dirX = str * cos - fwd * sin;
		double dirZ = str * sin + fwd * cos;

		float speed = boosting ? s.boostSpeed() : s.walkSpeed();
		if (!grounded) {
			speed *= boosting ? 0.92f : 0.55f;
		}
		if (fwd < -0.1f && !boosting) {
			speed *= 0.8f;
		}
		Vec3 v = this.getDeltaMovement();
		double tx = dirX * speed;
		double tz = dirZ * speed;
		float k;
		if (grounded) {
			if (hasInput) {
				k = boosting ? s.traction() * (s.legType() == MechPart.LegType.TANK ? 0.55f : 0.8f) : s.traction();
				if (s.legType() == MechPart.LegType.HOVER && !boosting) {
					// A hover skirt has no grip: it drifts into a turn and slides on when the keys are let go.
					k *= 0.75f;
				}
			} else {
				k = BRAKE_TRACTION * Mth.clamp(s.traction() / 0.35f, 0.6f, 1.3f);
				if (s.legType() == MechPart.LegType.HOVER) {
					k *= 0.35f;
				}
			}
		} else {
			k = boosting ? AIR_BOOST_TRACTION : AIR_TRACTION;
		}
		if (this.qbTimer > 0) {
			int elapsed = QB_TICKS - this.qbTimer;
			// Full burst, then a hard bleed-off: a sharp ~10 block side-step rather than a long slide.
			k = elapsed < QB_BURST_TICKS ? 0f : 0.3f + 0.06f * (elapsed - QB_BURST_TICKS);
			this.qbTimer--;
		}
		double horizontalSpeed = Math.sqrt(v.x * v.x + v.z * v.z);
		// Hard stop from speed: kick up a skid (and the lean throws the frame back on its heels).
		boolean braking = grounded && control && !hasInput && horizontalSpeed > 0.45 && this.qbTimer == 0;
		if (braking && !this.brakingNow && this.brakeHold == 0) {
			this.fireEvent(MechClientHooks.EVENT_BRAKE, (float) horizontalSpeed, 0f);
		}
		this.brakingNow = braking;
		this.brakeHold = braking ? 4 : Math.max(0, this.brakeHold - 1);
		double vx = v.x + (tx - v.x) * k;
		double vz = v.z + (tz - v.z) * k;
		Vec3 dash = this.meleeDash(v);
		if (dash != null) {
			vx = dash.x;
			vz = dash.z;
		}

		// Vertical: jump on the press, hover while held afterwards (Space straight to hover for legs that can't jump).
		double vy = v.y;
		boolean jumpHeld = control && this.inJump;
		boolean jumpEdge = jumpHeld && !this.prevJump;
		this.prevJump = jumpHeld;
		this.hoveringNow = false;
		boolean jumped = false;
		if (jumpEdge && grounded && s.jump() > 0f) {
			vy = s.jump();
			jumped = true;
			this.airTicks = 0;
			this.ticksSinceGrounded = GROUNDED_GRACE_TICKS + 1;
			this.jumpCounter++;
			this.fireEvent(MechClientHooks.EVENT_JUMP, (float) vy, 0f);
		}
		if (!grounded || jumped || (jumpHeld && s.jump() <= 0f)) {
			this.airTicks++;
			boolean wantHover = jumpHeld && (this.airTicks > 4 || s.jump() <= 0f) && s.hoverThrust() > 0f;
			if (wantHover && this.en > 0f && !this.overheat) {
				vy = Math.min(vy + s.hoverThrust(), HOVER_MAX_RISE);
				this.hoveringNow = true;
				this.drain(s.hoverCost());
			} else if (!jumped) {
				double g = GRAVITY * (airBoost ? BOOST_GLIDE_GRAVITY : 1.0);
				vy = Math.max(vy - g, MAX_FALL_SPEED);
			}
			if (airBoost) {
				this.drain(s.airBoostCost());
			}
		} else {
			this.airTicks = 0;
			vy = -0.2;
		}
		this.lastFallSpeed = vy;

		this.setDeltaMovement(vx, vy, vz);
		this.prevStepUpVisualOffset = this.stepUpVisualOffset;
		if (grounded) {
			this.applyStepUp(Math.max(s.stepHeight(), MIN_STEP_HEIGHT));
		}
		this.stepUpVisualOffset += (0f - this.stepUpVisualOffset) * 0.3f;
		Vec3 before = this.getDeltaMovement();
		this.move(MoverType.SELF, before);
		if (this.horizontalCollision) {
			// Glance off walls instead of sticking to them.
			Vec3 after = this.getDeltaMovement();
			this.setDeltaMovement(after.x * 0.5, after.y, after.z * 0.5);
		}
		if (!wasGrounded && this.onGround() && before.y < -0.45) {
			this.landCounter++;
			this.fireEvent(MechClientHooks.EVENT_LAND, (float) -before.y, 0f);
		}
		this.prevTravel = this.travel;
		this.travel += (float) Math.sqrt(vx * vx + vz * vz);
		this.setThrottleDirect(Mth.clamp((float) (Math.sqrt(vx * vx + vz * vz) / Math.max(0.05f, s.boostSpeed())), 0f, 1f));
	}

	/** Turbo: flies along the body's heading and the pilot's view pitch at high speed. Heading, pitch
	 * and speed all follow only slowly; no quick boost, jump or hover; extra fuel burned. */
	private void updateTurbo(MechStats s) {
		boolean control = this.hasControl();
		if (!this.level().isClientSide() && (!control || this.tudursvehiclemod$isOutOfFuel())) {
			this.entityData.set(TURBO, false);
		}
		boolean grounded = this.onGround();
		this.ticksSinceGrounded = grounded ? 0 : this.ticksSinceGrounded + 1;
		this.prevRoll = this.roll;
		this.roll = stepTowardAngle(this.roll, 0f, 8f);
		this.setXRot(stepTowardAngle(this.getXRot(), 0f, 8f));
		float wantPitch = 0f;
		if (this.getControllingPassenger() instanceof Player pilot) {
			this.setYRot(stepTowardAngle(this.getYRot(), pilot.getYRot(), s.turnRate() * TURBO_TURN));
			wantPitch = Mth.clamp(pilot.getXRot(), -40f, 40f);
		} else if (this.aiDriven()) {
			if (!Float.isNaN(this.brain.desiredYaw)) {
				this.setYRot(stepTowardAngle(this.getYRot(), this.brain.desiredYaw, s.turnRate() * TURBO_TURN));
			}
			wantPitch = Mth.clamp(this.brain.desiredPitch, -40f, 40f);
		}
		this.turboPitch = stepTowardAngle(this.turboPitch, wantPitch, 1.6f);
		float fwd = control ? Mth.clamp(this.inForward, -1f, 1f) : 0f;
		float str = control ? Mth.clamp(this.inStrafe, -1f, 1f) : 0f;
		float speed = Math.max(0.6f, s.boostSpeed()) * TURBO_SPEED * (fwd < -0.1f ? 0.55f : 1f) * (control ? 1f : 0f);
		double yawRad = Math.toRadians(this.getYRot());
		double pRad = Math.toRadians(this.turboPitch);
		double cp = Math.cos(pRad);
		double tx = -Math.sin(yawRad) * cp * speed + Math.cos(yawRad) * str * speed * 0.12;
		double tz = Math.cos(yawRad) * cp * speed + Math.sin(yawRad) * str * speed * 0.12;
		double ty = -Math.sin(pRad) * speed;
		Vec3 v = this.getDeltaMovement();
		double vx = v.x + (tx - v.x) * TURBO_TRACTION;
		double vy = v.y + (ty - v.y) * TURBO_TRACTION * 1.5;
		double vz = v.z + (tz - v.z) * TURBO_TRACTION;
		if (grounded && vy < 0.1) {
			vy = 0.1; // skim off the ground rather than drag along it
		}
		this.boostingNow = control;
		this.hoveringNow = false;
		this.brakingNow = false;
		this.qbTimer = 0;
		this.setDeltaMovement(vx, vy, vz);
		this.move(MoverType.SELF, this.getDeltaMovement());
		if (this.horizontalCollision) {
			Vec3 after = this.getDeltaMovement();
			this.setDeltaMovement(after.x * 0.3, after.y, after.z * 0.3);
		}
		this.lastFallSpeed = vy;
		this.prevTravel = this.travel;
		this.travel += (float) Math.sqrt(vx * vx + vz * vz);
		this.setThrottleDirect(control ? 1f : 0f);
		if (!this.level().isClientSide() && control && !this.tudursvehiclemod$isFuelless()) {
			float extra = this.getDefinition().fuelConsumptionPerSecond() / 20f * TURBO_EXTRA_FUEL;
			this.tudursvehiclemod$setFuel(this.getFuel() - extra);
		}
	}

	/** Robot form: an instant burst toward (localX = left, localZ = forward). Returns whether it fired. */
	public boolean tryQuickBoost(float localX, float localZ) {
		if (!this.hasControl() || this.isTransforming()) {
			return false;
		}
		if (this.isJet()) {
			return this.tryJink(localX);
		}
		MechStats s = this.getStats();
		if (!this.isQuickBoostReady() || this.isTurbo()) {
			return false;
		}
		float len = Mth.sqrt(localX * localX + localZ * localZ);
		if (len < 0.01f) {
			localX = 0f;
			localZ = 1f;
			len = 1f;
		}
		localX /= len;
		localZ /= len;
		// Tank controls: quick boosts are relative to the hull.
		double yawRad = Math.toRadians(this.isTankControlled() && !Float.isNaN(this.hullYaw) ? this.hullYaw : this.getYRot());
		double cos = Math.cos(yawRad);
		double sin = Math.sin(yawRad);
		double dirX = localX * cos - localZ * sin;
		double dirZ = localX * sin + localZ * cos;
		Vec3 v = this.getDeltaMovement();
		boolean airborne = this.ticksSinceGrounded > GROUNDED_GRACE_TICKS;
		this.setDeltaMovement(dirX * s.qbSpeed(), airborne ? Math.max(v.y, 0.06) : v.y, dirZ * s.qbSpeed());
		this.qbTimer = QB_TICKS;
		this.qbCooldown = s.qbCooldown();
		this.drain(s.qbCost());
		this.qbCounter++;
		this.qbOctant = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(localX, localZ)) / 45f), 8);
		this.fireEvent(MechClientHooks.EVENT_QUICK_BOOST, localX, localZ);
		return true;
	}

	/**
	 * Swing the melee weapon on that arm: dash at the locked target (or straight ahead) and strike.
	 * Called on the pilot's client (prediction: the dash and the animation) and on the server (the hit).
	 */
	public boolean tryMelee(MountSlot arm) {
		if (!this.hasControl() || this.isTransforming() || this.isJet() || this.isTurbo()) {
			return false;
		}
		MechPart weapon = this.getAssembly().part(arm);
		if (weapon == null || weapon.weapon() == null || !weapon.weapon().isMelee()) {
			return false;
		}
		MechPart.MeleeSpec m = weapon.weapon().melee();
		if (this.meleeCooldown > 0 || this.meleeTick >= 0 || this.overheat || this.en < m.enCost() * 0.5f) {
			return false;
		}
		this.drain(m.enCost());
		this.meleeCooldown = m.cooldown();
		this.meleeTick = 0;
		this.meleeArm = arm;
		this.meleeCounter++;
		this.meleeCounterSeen = this.meleeCounter;
		Entity t = this.meleeTarget();
		this.meleeVictim = t != null && t.isAlive() && t.distanceTo(this) <= m.lungeRange() + (this.getBbWidth() + t.getBbWidth()) * 0.5 ? t : null;
		this.meleeDir = this.meleeAim(m);
		return true;
	}

	/** What the dash goes for: the hard lock, else the FCS (soft-lock) target. */
	private Entity meleeTarget() {
		Entity lock = this.getLockTarget();
		return lock != null ? lock : this.getFcsTarget();
	}

	/** Horizontal unit direction of the dash: at the locked target within lunge range, else ahead. */
	private Vec3 meleeAim(MechPart.MeleeSpec m) {
		Entity target = this.meleeVictim;
		if (target != null && target.isAlive()) {
			Vec3 d = target.position().subtract(this.position());
			d = new Vec3(d.x, 0, d.z);
			if (d.length() > 0.1) {
				return d.normalize();
			}
		}
		double yawRad = Math.toRadians(this.getYRot());
		return new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
	}

	/** The swing in progress: which arm and how far through it (0..1 over MELEE_END ticks), or null. */
	public MountSlot getMeleeArm() {
		return this.meleeTick >= 0 ? this.meleeArm : null;
	}

	public float getMeleeTime(float tickDelta) {
		return this.meleeTick < 0 ? -1f : Math.min(MELEE_END, this.meleeTick + tickDelta);
	}

	public boolean isMeleeReady() {
		return this.meleeCooldown == 0 && this.meleeTick < 0;
	}

	/** Each tick: dash, strike, and (other players' frames) start the swing a synced count announces. */
	private void tickMelee() {
		if (this.meleeCooldown > 0) {
			this.meleeCooldown--;
		}
		Level world = this.level();
		if (world.isClientSide() && !this.isLocalPilot()) {
			int synced = (this.getSyncedAction() >> 24) & 15;
			if (this.meleeCounterSeen < 0) {
				this.meleeCounterSeen = synced;
			} else if (synced != (this.meleeCounterSeen & 15)) {
				this.meleeCounterSeen = synced;
				this.meleeTick = 0;
				this.meleeArm = ((this.getSyncedAction() >> 28) & 1) != 0 ? MountSlot.L_ARM : MountSlot.R_ARM;
			}
		}
		if (this.meleeTick < 0) {
			return;
		}
		if (this.meleeTick == MELEE_STRIKE && world instanceof ServerLevel server) {
			this.strike(server);
		}
		if (++this.meleeTick > MELEE_END) {
			this.meleeTick = -1;
		}
	}

	/** Damage of one melee hit. Vehicles cap each hit at their armor_max_damage (frames: 260), which
	 * would flatten every blow to the same number - a melee hit lands as a burst of smaller strikes
	 * instead, so a pile driver still hits far harder than a blade. Living targets take it in one. */
	private boolean meleeDamage(ServerLevel world, Entity target, net.minecraft.world.damagesource.DamageSource source, float damage) {
		if (!(target instanceof AbstractVehicleEntity)) {
			return target.hurtServer(world, source, damage);
		}
		boolean hit = false;
		for (float left = damage; left > 0.5f; left -= MELEE_CHUNK) {
			hit |= target.hurtServer(world, source, Math.min(left, MELEE_CHUNK));
		}
		return hit;
	}

	/** Melee damage per strike against a vehicle (see meleeDamage). */
	private static final float MELEE_CHUNK = 200f;

	/** Dash velocity while winding up (null: no dash this tick). Slows to a stop at the target. */
	private Vec3 meleeDash(Vec3 v) {
		if (this.meleeTick < 0 || this.meleeTick >= MELEE_STRIKE) {
			return null;
		}
		MechPart weapon = this.getAssembly().part(this.meleeArm);
		if (weapon == null || weapon.weapon() == null || !weapon.weapon().isMelee()) {
			return null;
		}
		MechPart.MeleeSpec m = weapon.weapon().melee();
		double speed = m.lungeSpeed();
		Entity target = this.meleeVictim;
		if (target != null && target.isAlive()) {
			Vec3 d = target.position().subtract(this.position());
			double gap = Math.sqrt(d.x * d.x + d.z * d.z) - (this.getBbWidth() + target.getBbWidth()) * 0.5;
			if (gap <= m.lungeRange()) {
				this.meleeDir = new Vec3(d.x, 0, d.z).normalize();
				// Close in to striking distance, no further.
				speed = Mth.clamp(gap - m.reach() * 0.45, 0.0, speed);
			}
		} else if (this.level().getEntities(this, this.getBoundingBox().move(this.meleeDir.scale(m.reach() * 0.5))
				.inflate(m.reach() * 0.3, 0.0, m.reach() * 0.3),
				e -> e.isAlive() && e.isAttackable() && e.getRootVehicle() != this).size() > 0) {
			// Straight-ahead dash: stop where something is already within the blade's reach.
			speed = 0.0;
		}
		return new Vec3(this.meleeDir.x * speed, v.y, this.meleeDir.z * speed);
	}

	/** The blow: everything within reach in front of the frame (in the dash direction) takes the hit once. */
	private void strike(ServerLevel world) {
		MechPart weapon = this.getAssembly().part(this.meleeArm);
		if (weapon == null || weapon.weapon() == null || !weapon.weapon().isMelee()) {
			return;
		}
		MechPart.MeleeSpec m = weapon.weapon().melee();
		Vec3 dir = this.meleeDir.lengthSqr() > 1.0e-4 ? this.meleeDir : this.meleeAim(m);
		double half = this.getBbWidth() * 0.5;
		Vec3 center = this.position().add(0, this.getBbHeight() * 0.45, 0);
		AABB box = this.getBoundingBox().inflate(m.reach() + 1.0, 1.0, m.reach() + 1.0);
		Entity pilot = this.getControllingPassenger();
		var source = pilot instanceof Player player ? this.damageSources().playerAttack(player)
				: pilot instanceof LivingEntity living ? this.damageSources().mobAttack(living)
				: this.damageSources().generic();
		double cosArc = Math.cos(Math.toRadians(m.arc()));
		int hits = 0;
		for (Entity e : world.getEntities(this, box, e -> e.isAlive() && e.isAttackable()
				&& e.getRootVehicle() != this.getRootVehicle() && !(e.getRootVehicle() == this))) {
			Vec3 d = e.position().subtract(this.position());
			Vec3 flat = new Vec3(d.x, 0, d.z);
			double dist = flat.length() - half - e.getBbWidth() * 0.5;
			if (dist > m.reach() || d.y > this.getBbHeight() + 1.0 || d.y < -e.getBbHeight() - 1.0) {
				continue;
			}
			if (flat.length() > 0.5 && flat.normalize().dot(dir) < cosArc) {
				continue;
			}
			if (this.meleeDamage(world, e, source, m.damage())) {
				hits++;
				e.push(dir.x * 0.6, 0.15, dir.z * 0.6);
				world.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
						e.getX(), e.getY(0.5), e.getZ(), 24, 0.4, 0.4, 0.4, 0.4);
				world.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
						e.getX(), e.getY(0.5), e.getZ(), 12, 0.3, 0.3, 0.3, 0.5);
			}
		}
		Vec3 swing = center.add(dir.scale(half + m.reach() * 0.55));
		world.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, swing.x, swing.y, swing.z, 3, 0.6, 0.3, 0.6, 0.0);
		world.playSound(null, swing.x, swing.y, swing.z, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
				this.getSoundSource(), 2.5f, 0.55f);
		if (hits > 0) {
			world.playSound(null, swing.x, swing.y, swing.z, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
					this.getSoundSource(), 2.5f, 0.6f);
		}
	}

	public int getQuickBoostTimer() {
		return this.qbTimer;
	}

	/** Every frame walks up a full block without jumping (and so without EN): part data may give more. */
	private static final float MIN_STEP_HEIGHT = 1.05f;

	/** Walks up a step in the way (up to stepHeight) by lifting the frame onto it. No EN: it is part of walking. */
	private void applyStepUp(float stepHeight) {
		Vec3 velocity = this.getDeltaMovement();
		double horizontal = velocity.horizontalDistance();
		if (horizontal < 1.0e-4) {
			return;
		}
		double scale = horizontal > 0.1 ? 1.0 : 0.1 / horizontal;
		AABB moved = this.getBoundingBox().move(velocity.x * scale, 0, velocity.z * scale);
		Level world = this.level();
		if (world.noCollision(this, moved) || !world.noCollision(this, moved.move(0, stepHeight, 0))) {
			return;
		}
		float lo = 0f;
		float hi = stepHeight;
		for (int i = 0; i < 8; i++) {
			float mid = (lo + hi) * 0.5f;
			if (world.noCollision(this, moved.move(0, mid, 0))) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		this.setPos(this.getX(), this.getY() + hi, this.getZ());
		// No downward speed this tick: the game moves vertically before horizontally, so the frame would drop
		// back below the step's top before reaching it, hit its side and fall back down, again and again.
		this.setDeltaMovement(velocity.x, Math.max(velocity.y, 0.0), velocity.z);
		this.stepUpVisualOffset -= hi;
	}

	// ---------------------------------------------------------------------------------- EN

	private void drain(float amount) {
		if (amount <= 0f) {
			return;
		}
		MechStats s = this.getStats();
		this.en = Math.max(0f, this.en - amount);
		this.enDelay = s.rechargeDelay();
		this.drainedThisTick = true;
		if (this.en <= 0f && !this.overheat) {
			this.overheat = true;
			this.enDelay = s.rechargeDelay() * 4;
		}
	}

	private void updateEn() {
		MechStats s = this.getStats();
		if (this.drainedThisTick) {
			return;
		}
		if (this.enDelay > 0) {
			this.enDelay--;
			return;
		}
		// No recharge in the air (rising or falling); the jet form recharges as before.
		boolean grounded = this.ticksSinceGrounded <= GROUNDED_GRACE_TICKS || this.isJet();
		if (!grounded) {
			return;
		}
		this.en = Math.min(s.enCapacity(), this.en + s.enRecharge() * (this.overheat ? 1.5f : 1f));
		if (this.overheat && this.en >= s.enCapacity() * 0.35f) {
			this.overheat = false;
		}
	}

	private void fireEvent(int event, float a, float b) {
		if (this.isLocalPilot()) {
			clientHooks.onEvent(this, event, a, b);
		}
	}

	// ---------------------------------------------------------------------------------- lean

	/** Body lean from motion: forward speed tips it forward, sideways speed banks it into the turn,
	 * and acceleration (a quick boost, a hard stop) throws it the other way - a damped spring, so it
	 * overshoots a little and settles. Computed from the position change on both sides so other
	 * players see the same thing; the server needs it too, because it rotates weapon mounts. */
	private void updateLean() {
		this.prevLeanPitch = this.leanPitch;
		this.prevLeanRoll = this.leanRoll;
		Vec3 motion = new Vec3(this.getX() - this.xo, 0, this.getZ() - this.zo);
		if (motion.lengthSqr() > 16.0) {
			motion = Vec3.ZERO; // teleport
		}
		Vec3 accel = motion.subtract(this.prevMotion);
		this.prevMotion = motion;
		float targetPitch = 0f;
		float targetRoll = 0f;
		if (this.isTurbo()) {
			// Turbo: the frame lies forward into its flight, less so while climbing.
			targetPitch = 58f + this.turboPitch * 0.5f;
		} else if (!this.isJet() && this.transform < 0.5f) {
			double yawRad = Math.toRadians(this.getYRot());
			double fwdX = -Math.sin(yawRad);
			double fwdZ = Math.cos(yawRad);
			double leftX = Math.cos(yawRad);
			double leftZ = Math.sin(yawRad);
			double vf = motion.x * fwdX + motion.z * fwdZ;
			double vl = motion.x * leftX + motion.z * leftZ;
			double af = accel.x * fwdX + accel.z * fwdZ;
			double al = accel.x * leftX + accel.z * leftZ;
			float steady = this.getStats().stability();
			targetPitch = (float) Mth.clamp((vf * 7.0 - af * 22.0) / steady, -16.0, 16.0);
			targetRoll = (float) Mth.clamp((-vl * 7.0 + al * 22.0) / steady, -14.0, 14.0);
		}
		this.leanPitchVel += (targetPitch - this.leanPitch) * 0.32f - this.leanPitchVel * 0.42f;
		this.leanRollVel += (targetRoll - this.leanRoll) * 0.32f - this.leanRollVel * 0.42f;
		this.leanPitch += this.leanPitchVel;
		this.leanRoll += this.leanRollVel;
	}

	public float getLeanPitch(float tickDelta) {
		return Mth.lerp(tickDelta, this.prevLeanPitch, this.leanPitch);
	}

	public float getLeanRoll(float tickDelta) {
		return Mth.lerp(tickDelta, this.prevLeanRoll, this.leanRoll);
	}

	private float leanPivotY() {
		MechLayout layout = this.getLayout();
		return layout != null ? layout.hip.y : 2f;
	}

	private Quaternionf leanRotation(float tickDelta) {
		float blend = 1f - this.getTransformProgress(tickDelta);
		return new Quaternionf()
				.rotateX((float) Math.toRadians(this.getLeanPitch(tickDelta) * blend))
				.rotateZ((float) Math.toRadians(this.getLeanRoll(tickDelta) * blend));
	}

	// ==================================================================================== orientation

	@Override
	public Quaternionf tudursvehiclemod$getBodyOrientation() {
		return this.unleanedOrientation(1f).mul(this.leanRotation(1f));
	}

	@Override
	public Quaternionf tudursvehiclemod$getBodyOrientation(float tickDelta) {
		return this.unleanedOrientation(tickDelta).mul(this.leanRotation(tickDelta));
	}

	private Quaternionf unleanedOrientation(float tickDelta) {
		if (this.flightOrientation != null && this.isJet()) {
			return new Quaternionf(this.prevFlightOrientation).slerp(this.flightOrientation, tickDelta);
		}
		return super.tudursvehiclemod$getBodyOrientation(tickDelta);
	}

	/** The lean pivots about the hip rather than the feet: the offset that keeps the hip in place. */
	@Override
	public Vec3 tudursvehiclemod$getBodyFrameOffset(float tickDelta) {
		float h = this.leanPivotY();
		Quaternionf unleaned = this.unleanedOrientation(tickDelta);
		Vector3f pivot = new Vector3f(0, h, 0);
		Vector3f leaned = this.leanRotation(tickDelta).transform(new Vector3f(0, -h, 0)).add(pivot);
		Vector3f offset = unleaned.transform(leaned);
		return new Vec3(offset.x, offset.y, offset.z);
	}

	@Override
	protected Quaternionf getSeatRotationInterpolated(float tickDelta) {
		return this.tudursvehiclemod$getBodyOrientation(tickDelta);
	}

	@Override
	protected Quaternionf getSeatRotationCurrent() {
		return this.tudursvehiclemod$getBodyOrientation();
	}

	@Override
	public float getViewYRot(float tickDelta) {
		if (this.flightOrientation != null && this.isJet()) {
			return extractYawPitchRoll(new Quaternionf(this.prevFlightOrientation).slerp(this.flightOrientation, tickDelta))[0];
		}
		return super.getViewYRot(tickDelta);
	}

	@Override
	public float getYRot() {
		if (this.flightOrientation != null && this.isJet()) {
			return extractYawPitchRoll(this.flightOrientation)[0];
		}
		return super.getYRot();
	}

	@Override
	public float getViewXRot(float tickDelta) {
		if (this.flightOrientation != null && this.isJet()) {
			return extractYawPitchRoll(new Quaternionf(this.prevFlightOrientation).slerp(this.flightOrientation, tickDelta))[1];
		}
		return super.getViewXRot(tickDelta);
	}

	@Override
	public float getXRot() {
		if (this.flightOrientation != null && this.isJet()) {
			return extractYawPitchRoll(this.flightOrientation)[1];
		}
		return super.getXRot();
	}

	@Override
	public float getRoll(float tickDelta) {
		if (this.flightOrientation != null && this.isJet()) {
			return extractYawPitchRoll(new Quaternionf(this.prevFlightOrientation).slerp(this.flightOrientation, tickDelta))[2];
		}
		return super.getRoll(tickDelta);
	}

	@Override
	public float getRoll() {
		if (this.flightOrientation != null && this.isJet()) {
			return extractYawPitchRoll(this.flightOrientation)[2];
		}
		return super.getRoll();
	}

	@Override
	public float getCurrentRoll() {
		return this.getRoll();
	}

	/** Robot form: the camera is the pilot's own (free, like a car). Jet form: locked to the nose
	 * like an aircraft, free only with the base mod's free-look key. */
	@Override
	public boolean tudursvehiclemod$isEffectiveFreeLook(Entity viewer) {
		if (this.isJet()) {
			return viewer != this.getControllingPassenger() || this.isFreeLook();
		}
		return true;
	}

	// ==================================================================================== transformation

	/** Server: start a transformation (the form change itself then runs on every side through
	 * onTrackedDataSet -> onFormChanged). */
	public boolean tryTransform() {
		MechLayout layout = this.getLayout();
		if (layout != null && !layout.canTransform) {
			return this.toggleTurbo();
		}
		if (layout == null || !layout.canTransform || this.transformCooldown > 0 || this.tudursvehiclemod$isDestroyed()) {
			return false;
		}
		this.transformCooldown = TRANSFORM_TICKS + 6;
		this.entityData.set(FORM, this.isJet() ? 0 : 1);
		return true;
	}

	/** Server: frames that can't transform use the transform key for turbo mode. */
	private boolean toggleTurbo() {
		if (this.transformCooldown > 0 || this.tudursvehiclemod$isDestroyed()) {
			return false;
		}
		boolean on = !this.isTurbo();
		if (on && (!this.hasControl() || this.tudursvehiclemod$isOutOfFuel())) {
			return false;
		}
		this.transformCooldown = 10;
		this.entityData.set(TURBO, on);
		this.fireEvent(MechClientHooks.EVENT_QUICK_BOOST, 0f, 1f);
		return true;
	}

	public boolean isTurbo() {
		return this.entityData != null && this.entityData.get(TURBO);
	}

	/** Turbo flight's pitch (degrees, negative = climbing), for the pose. */
	public float getTurboPitch() {
		return this.turboPitch;
	}

	private int lastFormSeen = -1;

	/** Both sides, once per actual form change: hands the current motion over to the other form's
	 * physics. Robot -> jet in the air is a launch (the boosters carry the frame up to flight speed);
	 * jet -> robot keeps the momentum and turns the pilot's view to where the nose was. */
	private void onFormChanged() {
		int form = this.entityData.get(FORM);
		if (form == this.lastFormSeen) {
			return;
		}
		boolean first = this.lastFormSeen < 0;
		this.lastFormSeen = form;
		if (first || this.getLayout() == null) {
			return;
		}
		boolean toJet = form == 1;
		this.transformCooldown = TRANSFORM_TICKS + 6;
		if (toJet) {
			this.flightInitialized = false;
			this.jetActive = false;
			MechStats s = this.getStats();
			boolean airborne = this.ticksSinceGrounded > GROUNDED_GRACE_TICKS;
			float jetMax = s.jet().map(MechPart.JetSpec::maxSpeed).orElse(2.5f);
			Vec3 v = this.getDeltaMovement();
			this.jetEntrySpeed = (float) Math.max(v.length(), airborne ? jetMax * 0.6f : 0f);
			this.jetEntryThrottle = airborne ? 0.85f : 0f;
		} else {
			float yaw = extractYawPitchRoll(this.flightOrientation)[0];
			float pitch = extractYawPitchRoll(this.flightOrientation)[1];
			super.setYRot(yaw);
			super.setXRot(0f);
			this.roll = 0f;
			if (this.getControllingPassenger() instanceof Player pilot) {
				if (pilot instanceof ServerPlayer serverPilot) {
					serverPilot.setYRot(yaw);
					serverPilot.setXRot(Mth.clamp(pitch, -60f, 60f));
				} else if (pilot.isLocalPlayer()) {
					pilot.setYRot(yaw);
					pilot.setXRot(Mth.clamp(pitch, -60f, 60f));
				}
			}
		}
		this.fireEvent(MechClientHooks.EVENT_TRANSFORM, toJet ? 1f : 0f, 0f);
	}

	@Override
	public boolean tudursvehiclemod$canFireWeapons(Optional<WeaponDefinition> weapon) {
		if (this.isTransforming() || this.getLayout() == null) {
			return false;
		}
		return super.tudursvehiclemod$canFireWeapons(weapon);
	}

	// ==================================================================================== aiming / FCS

	/** Where the camera pivot is - the origin of the aim ray. Above the frame's centre, so the ray is
	 * the same line the third-person camera looks along regardless of zoom. */
	public Vec3 getAimOrigin(float tickDelta) {
		MechLayout layout = this.getLayout();
		float h = layout != null ? (this.isJet() ? layout.jetEye.y : layout.cameraPivotY) : 3f;
		return this.getPosition(tickDelta).add(0, h, 0);
	}

	/** The point under the crosshair (block, entity or max range), cached per frame. */
	public Vec3 getAimPoint(float tickDelta) {
		if (this.aimPointCache != null && this.aimCacheAge == this.tickCount && this.aimCacheDelta == tickDelta) {
			return this.aimPointCache;
		}
		Vec3 origin = this.getAimOrigin(tickDelta);
		Vec3 dir;
		Entity pilot = this.getControllingPassenger();
		if (pilot != null) {
			dir = pilot.getViewVector(tickDelta);
		} else {
			dir = Vec3.directionFromRotation(0f, this.getViewYRot(tickDelta));
		}
		Vec3 end = origin.add(dir.scale(AIM_RANGE));
		BlockHitResult hit = this.level().clip(new ClipContext(origin, end,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 point = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
		this.aimPointCache = this.nearestEntityAlong(origin, point);
		this.aimCacheAge = this.tickCount;
		this.aimCacheDelta = tickDelta;
		return point;
	}

	/** Length of the stretches nearestEntityAlong() searches one at a time. */
	private static final double AIM_SEGMENT = 16.0;

	/** The first entity hit on the way from origin to end (more than 2 blocks out), else end. The ray
	 * is searched a short stretch at a time, nearest first, and the search stops at the first stretch
	 * that holds a hit: one box round the whole ray (up to AIM_RANGE, diagonally) covered hundreds of
	 * chunks, and this runs every frame on the pilot's client and every tick on the server. */
	private Vec3 nearestEntityAlong(Vec3 origin, Vec3 end) {
		Vec3 ray = end.subtract(origin);
		double length = ray.length();
		if (length < 1.0e-6) {
			return end;
		}
		Vec3 point = end;
		double best = length * length;
		for (double from = 0.0; from < length; from += AIM_SEGMENT) {
			double to = Math.min(length, from + AIM_SEGMENT);
			Vec3 a = origin.add(ray.scale(from / length));
			Vec3 b = origin.add(ray.scale(to / length));
			for (Entity e : this.level().getEntities(this, new AABB(a, b).inflate(2.0), e -> e.isAlive() && !this.hasPassenger(e) && e.isPickable())) {
				Optional<Vec3> h = e.getBoundingBox().inflate(0.3).clip(origin, end);
				if (h.isPresent()) {
					double d = origin.distanceToSqr(h.get());
					if (d < best && d > 4.0) {
						best = d;
						point = h.get();
					}
				}
			}
			// A hit is final once it lies within the stretches searched so far: anything in a later
			// stretch is farther away.
			if (best <= to * to) {
				break;
			}
		}
		return point;
	}

	public Entity getLockTarget() {
		int id = this.entityData.get(LOCK_ID);
		return id < 0 ? null : this.level().getEntity(id);
	}

	public Entity getCandidate() {
		int id = this.entityData.get(CANDIDATE_ID);
		return id < 0 ? null : this.level().getEntity(id);
	}

	/** The FCS target the weapons track: the hard lock, else the soft lock (candidate). */
	public Entity getFcsTarget() {
		Entity lock = this.getLockTarget();
		return lock != null ? lock : this.getCandidate();
	}

	private boolean isValidTarget(Entity e, Entity pilot) {
		if (e == this || e == pilot || this.hasPassenger(e) || !e.isAlive() || e.isRemoved()) {
			return false;
		}
		if (e instanceof MechEntity other && other.getFaction() == com.example.mechframe.ai.Faction.FRIENDLY) {
			return false; // allies are never soft-locked
		}
		if (e instanceof AbstractVehicleEntity vehicle) {
			return !vehicle.tudursvehiclemod$isDestroyed() && !vehicle.hasPassenger(pilot);
		}
		if (e instanceof Player player) {
			return !player.isCreative() && !player.isSpectator() && !player.isPassenger();
		}
		if (e instanceof Enemy) {
			return true;
		}
		return e instanceof Mob mob && pilot instanceof LivingEntity living && mob.getTarget() == living;
	}

	private void updateCandidate() {
		Entity best = null;
		if (this.getControllingPassenger() instanceof Player pilot && !this.isManualMode() && this.getLayout() != null) {
			MechStats s = this.getStats();
			Vec3 origin = this.getAimOrigin(1f);
			Vec3 dir = pilot.getViewVector(1f);
			double range = s.lockRange();
			double cosCone = Math.cos(Math.toRadians(s.lockCone()));
			double bestScore = Double.MAX_VALUE;
			// Only the lock cone can hold a candidate: search the box round it (the cone's axis grown by
			// its widest radius) instead of a cube of the whole lock range round the frame.
			AABB search = s.lockCone() < 80.0
					? new AABB(origin, origin.add(dir.scale(range))).inflate(range * Math.sin(Math.toRadians(s.lockCone())))
					: new AABB(origin, origin).inflate(range);
			for (Entity e : this.level().getEntities(this, search, e -> this.isValidTarget(e, pilot))) {
				Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
				double dist = to.length();
				if (dist < 3.0 || dist > range) {
					continue;
				}
				double cos = to.dot(dir) / dist;
				if (cos < cosCone) {
					continue;
				}
				double score = (1.0 - cos) * 4000.0 + dist;
				if (score >= bestScore) {
					continue;
				}
				BlockHitResult hit = this.level().clip(new ClipContext(origin, e.getBoundingBox().getCenter(),
						ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
				if (hit.getType() == HitResult.Type.BLOCK) {
					continue;
				}
				bestScore = score;
				best = e;
			}
		}
		this.entityData.set(CANDIDATE_ID, best == null ? -1 : best.getId());
	}

	private void pruneLock() {
		Entity lock = this.getLockTarget();
		if (lock == null) {
			if (this.entityData.get(LOCK_ID) >= 0) {
				this.entityData.set(LOCK_ID, -1);
			}
			return;
		}
		Entity pilot = this.getControllingPassenger();
		boolean valid = (pilot != null || this.aiDriven()) && lock.isAlive() && !lock.isRemoved()
				&& !(lock instanceof AbstractVehicleEntity v && v.tudursvehiclemod$isDestroyed())
				&& lock.distanceToSqr(this) < Math.pow(this.getStats().lockRange() * 1.5, 2);
		if (!valid) {
			this.entityData.set(LOCK_ID, -1);
		}
	}

	/** The base mod's target-designation key (B): lock the soft-lock candidate, or release. */
	@Override
	public void tudursvehiclemod$toggleCarrierLockMode() {
		if (this.entityData.get(LOCK_ID) >= 0) {
			this.entityData.set(LOCK_ID, -1);
			return;
		}
		Entity candidate = this.getCandidate();
		if (candidate != null) {
			this.entityData.set(LOCK_ID, candidate.getId());
		}
	}

	@Override
	public void tudursvehiclemod$releaseAllCarrierLocks() {
		this.entityData.set(LOCK_ID, -1);
	}

	/** World point a weapon mount should hit: the FCS target led by the projectile's flight time
	 * (and lifted for its drop), or the crosshair point. */
	private Vec3 targetPointFor(MountSlot mount, float tickDelta) {
		MechPart weapon = this.getAssembly().part(mount);
		Entity target = weapon != null && weapon.weapon() != null && weapon.weapon().fcs() ? this.getFcsTarget() : null;
		if (target == null) {
			return this.getAimPoint(tickDelta);
		}
		Vec3 center = target.getPosition(tickDelta).add(0, target.getBbHeight() * 0.5, 0);
		Vec3 targetVel = new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
		if (targetVel.lengthSqr() > 25.0) {
			targetVel = Vec3.ZERO;
		}
		WeaponStats stats = WeaponStatsLoader.get(weapon.weapon().weaponName());
		double speed = Math.max(0.5, stats.velocity());
		Vec3 origin = this.getAimOrigin(tickDelta);
		Vec3 aim = center;
		double t = 0;
		for (int i = 0; i < 3; i++) {
			double dist = aim.distanceTo(origin);
			double ratio = 0.01 * dist / speed;
			t = ratio < 0.95 ? -Math.log(1.0 - ratio) / 0.01005 : dist / speed * 3.0;
			aim = center.add(targetVel.scale(t));
		}
		double drop = 0.5 * Math.abs(stats.gravity()) * t * t;
		return aim.add(0, Math.min(drop, 40.0), 0);
	}

	/** Vehicle-relative {yaw, pitch} for a weapon mount, in the (leaning) body frame the base mod
	 * mounts and fires it in. Null = defer to the base mod. */
	private float[] aimFor(MountSlot mount, float tickDelta) {
		MechLayout layout = this.getLayout();
		if (layout == null || this.isJet()) {
			return null;
		}
		boolean aiAim = this.level().isClientSide() ? this.getFaction() != com.example.mechframe.ai.Faction.NONE
				&& this.getControllingPassenger() == null && this.getLockTarget() != null : this.aiDriven();
		if ((!(this.getControllingPassenger() instanceof Player) && !aiAim) || this.tudursvehiclemod$isDestroyed()) {
			// Unmanned: arms hang down, back weapons rest level.
			return new float[]{0f, mount.isArmWeapon() ? 40f : 0f};
		}
		Vec3 target = this.targetPointFor(mount, tickDelta);
		Quaternionf body = this.tudursvehiclemod$getBodyOrientation(tickDelta);
		Quaternionf bodyInv = new Quaternionf(body).conjugate();
		Vec3 frame = this.tudursvehiclemod$getBodyFrameOffset(tickDelta);
		Vec3 pos = this.getPosition(tickDelta);
		Vector3f pivotRoot = layout.weaponPivot.get(mount);
		// Target in the body frame, relative to the mount's pivot.
		Vector3f toTarget = bodyInv.transform(new Vector3f(
				(float) (target.x - pos.x - frame.x), (float) (target.y - pos.y - frame.y), (float) (target.z - pos.z - frame.z)))
				.sub(pivotRoot);
		// The muzzle sits away from the pivot (an arm gun hangs a metre below the shoulder), so aim the
		// muzzle - not the pivot - at the target: rotate the muzzle offset by the current guess and
		// re-aim from there. Two passes converge for anything further away than the arm is long.
		Vector3f offset = new Vector3f(layout.robotMuzzle.get(mount)).sub(pivotRoot);
		float yaw = 0f;
		float pitch = 0f;
		Vector3f dir = new Vector3f(toTarget);
		for (int pass = 0; pass < 3; pass++) {
			if (dir.lengthSquared() < 1.0e-6f) {
				return new float[]{yaw, pitch};
			}
			dir.normalize();
			yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			pitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1f, 1f)));
			Vector3f muzzle = new Quaternionf().rotationY((float) Math.toRadians(-yaw))
					.rotateX((float) Math.toRadians(pitch)).transform(new Vector3f(offset));
			// Only the sideways/vertical part of the offset matters for parallax; the along-barrel part doesn't.
			Vector3f along = new Quaternionf().rotationY((float) Math.toRadians(-yaw))
					.rotateX((float) Math.toRadians(pitch)).transform(new Vector3f(0, 0, 1));
			muzzle.fma(-muzzle.dot(along), along);
			dir.set(toTarget).sub(muzzle);
		}
		return new float[]{yaw, pitch};
	}

	@Override
	public float getWeaponAimYaw(int seatIndex, boolean pilotFallback, WeaponAimRange aimRange, float tickProgress) {
		MountSlot mount = MountSlot.bySeat(seatIndex);
		float[] aim = mount != null ? this.aimFor(mount, tickProgress) : null;
		if (aim == null) {
			return super.getWeaponAimYaw(seatIndex, pilotFallback, aimRange, tickProgress);
		}
		if (aimRange == null) {
			return aim[0];
		}
		return (float) Mth.clamp(Mth.wrapDegrees(aim[0] - aimRange.defaultYaw()),
				Math.min(aimRange.minYaw(), aimRange.maxYaw()), Math.max(aimRange.minYaw(), aimRange.maxYaw()));
	}

	@Override
	public float getWeaponAimPitch(int seatIndex, boolean pilotFallback, WeaponAimRange aimRange, float tickProgress) {
		MountSlot mount = MountSlot.bySeat(seatIndex);
		float[] aim = mount != null ? this.aimFor(mount, tickProgress) : null;
		if (aim == null) {
			return super.getWeaponAimPitch(seatIndex, pilotFallback, aimRange, tickProgress);
		}
		if (aimRange == null) {
			return aim[1];
		}
		return (float) Mth.clamp(aim[1], Math.min(aimRange.minPitch(), aimRange.maxPitch()),
				Math.max(aimRange.minPitch(), aimRange.maxPitch()));
	}

	// ==================================================================================== jet flight

	private Quaternionf flightOrientation = new Quaternionf();
	private Quaternionf prevFlightOrientation = new Quaternionf();
	private boolean flightInitialized;
	private boolean jetActive;
	private float jetEntrySpeed;
	private float jetEntryThrottle;
	private float smoothedYawDelta;
	private float smoothedPitchDelta;
	private float smoothedRollDelta;
	private int noInputTicks;
	private boolean jetSurfaced;
	private int jetSurfacedDebounce;
	private boolean jetStalling;
	private boolean jetLifted;
	private boolean jetSustained;
	private float jetSmoothedPathPitch;
	private float jetHoldYaw;
	private boolean jetHoldYawLocked;

	private static final float JET_YAW_RATE_MULTIPLIER = 3.0f;
	private static final float JET_PITCH_RATE_MULTIPLIER = 9.0f;
	private static final float JET_ROLL_RATE = 7f;
	private static final float JET_ROTATION_SMOOTHING = 0.25f;
	private static final int JET_LEVEL_ASSIST_GRACE = 60;
	private static final float JET_LEVEL_ASSIST = 0.03f;
	private static final float JET_MIN_FLIGHT_FRACTION = 0.45f;
	private static final float JET_SUSTAINED_DISENGAGE = 0.35f;
	private static final double JET_LIFTOFF_VELOCITY = 0.35;
	private static final float JET_GROUND_BLEND = 0.05f;
	private static final float JET_GROUND_DECEL = 0.04f;
	private static final float JET_GLIDE_BLEND = 0.004f;
	private static final float JET_DIVE_MULTIPLIER = 1.4f;
	private static final float JET_DIVE_BLEND = 0.05f;
	private static final float JET_CLIMB_DECEL = 0.04f;
	private static final float JET_CLIMB_MIN_FRACTION = 0.45f;
	private static final float JET_NEAR_STALL_GRAVITY = 6f;
	private static final double JET_NEAR_STALL_MAX_FALL = -1.5;
	private static final float JET_STALL_PITCH_SMOOTHING = 0.12f;
	private static final double JET_CRASH_MIN_SPEED = 0.9;
	private static final float JET_CRASH_DAMAGE_PER_SPEED = 120f;

	static float[] extractYawPitchRoll(Quaternionf q) {
		double x = q.x;
		double y = q.y;
		double z = q.z;
		double w = q.w;
		double pitch = Math.asin(Mth.clamp(2.0 * (x * w - y * z), -1.0, 1.0));
		double negYaw = Math.atan2(2.0 * (x * z + y * w), 1.0 - 2.0 * (x * x + y * y));
		double roll = Math.atan2(2.0 * (x * y + z * w), 1.0 - 2.0 * (x * x + z * z));
		return new float[]{(float) -Math.toDegrees(negYaw), (float) Math.toDegrees(pitch), (float) Math.toDegrees(roll)};
	}

	private void seedFlightOrientation(float yaw, float pitch, float roll) {
		this.flightOrientation.rotationY((float) Math.toRadians(-yaw))
				.rotateX((float) Math.toRadians(pitch))
				.rotateZ((float) Math.toRadians(roll));
	}

	/** Composes this tick's rotation input (mouse yaw/pitch, A/D roll), level assist, and an optional
	 * forced target, then mirrors the result to the vanilla angle fields and the passengers' views. */
	private void updateFlightOrientation(boolean pilotControl, Quaternionf forced, float forcedT) {
		this.prevFlightOrientation.set(this.flightOrientation);
		if (pilotControl) {
			float turn = this.getDefinition().turnSpeed() * (this.tudursvehiclemod$isDestroyed() ? 0.1f : 1f);
			if (this.isFreeLook()) {
				this.pendingYawInput = 0f;
				this.pendingPitchInput = 0f;
			}
			float yawRate = turn * JET_YAW_RATE_MULTIPLIER;
			float pitchRate = turn * JET_PITCH_RATE_MULTIPLIER;
			float targetYaw = Mth.clamp(this.pendingYawInput, -yawRate, yawRate);
			float targetPitch = Mth.clamp(this.pendingPitchInput, -pitchRate, pitchRate);
			this.pendingYawInput = 0f;
			this.pendingPitchInput = 0f;
			float targetRoll = this.inStrafe * -JET_ROLL_RATE;
			this.smoothedYawDelta += (targetYaw - this.smoothedYawDelta) * JET_ROTATION_SMOOTHING;
			this.smoothedPitchDelta += (targetPitch - this.smoothedPitchDelta) * JET_ROTATION_SMOOTHING;
			this.smoothedRollDelta += (targetRoll - this.smoothedRollDelta) * JET_ROTATION_SMOOTHING;
			Quaternionf delta = new Quaternionf()
					.rotateY((float) Math.toRadians(-this.smoothedYawDelta))
					.rotateX((float) Math.toRadians(this.smoothedPitchDelta))
					.rotateZ((float) Math.toRadians(this.smoothedRollDelta));
			this.flightOrientation.mul(delta).normalize();
			boolean hasInput = targetYaw != 0f || targetPitch != 0f || targetRoll != 0f;
			this.noInputTicks = hasInput ? 0 : this.noInputTicks + 1;
			if (this.noInputTicks >= JET_LEVEL_ASSIST_GRACE) {
				float[] cur = extractYawPitchRoll(this.flightOrientation);
				Quaternionf level = new Quaternionf().rotationY((float) Math.toRadians(-cur[0])).rotateX((float) Math.toRadians(cur[1] * 0.5f));
				this.flightOrientation.slerp(level, JET_LEVEL_ASSIST).normalize();
			}
		}
		if (forced != null) {
			this.flightOrientation.slerp(forced, forcedT).normalize();
		}
		float[] e = extractYawPitchRoll(this.flightOrientation);
		super.setYRot(e[0]);
		super.setXRot(e[1]);
		this.prevRoll = this.roll;
		this.roll = e[2];
		this.carryRotationOntoPassengers();
	}

	private void carryRotationOntoPassengers() {
		Quaternionf delta = new Quaternionf(this.flightOrientation).mul(new Quaternionf(this.prevFlightOrientation).invert());
		if (Math.abs(delta.w) > 0.999999f) {
			return;
		}
		for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
			Quaternionf view = new Quaternionf().rotationY((float) Math.toRadians(-passenger.getYRot()))
					.rotateX((float) Math.toRadians(passenger.getXRot()));
			float[] e = extractYawPitchRoll(new Quaternionf(delta).mul(view).normalize());
			passenger.setYRot(e[0]);
			passenger.setXRot(Mth.clamp(e[1], -90f, 90f));
		}
	}

	private void updateJetFlight(VehicleDefinition def) {
		MechStats s = this.getStats();
		MechPart.JetSpec jet = s.jet().orElse(null);
		Player pilot = this.hasControl() && this.getControllingPassenger() instanceof Player p ? p : null;
		if (!this.flightInitialized) {
			this.seedFlightOrientation(super.getYRot(), super.getXRot(), this.roll);
			this.prevFlightOrientation.set(this.flightOrientation);
			this.flightInitialized = true;
			this.smoothedYawDelta = 0f;
			this.smoothedPitchDelta = 0f;
			this.smoothedRollDelta = 0f;
			this.noInputTicks = 0;
		}
		if (!this.jetActive) {
			this.jetActive = true;
			this.jetSurfaced = this.onGround();
			this.jetLifted = !this.jetSurfaced;
			this.jetStalling = false;
			this.jetSustained = false;
			this.jetSmoothedPathPitch = 0f;
			this.jetHoldYawLocked = false;
			this.cruiseSpeed = this.jetEntrySpeed;
			if (this.jetEntryThrottle > 0f) {
				this.setThrottleDirect(this.jetEntryThrottle);
			}
		}

		float throttle;
		if (pilot != null) {
			throttle = Mth.clamp(this.getThrottle() + this.inForward * 0.025f, 0f, 1f);
			this.setThrottleDirect(throttle);
		} else {
			throttle = Math.max(0f, this.getThrottle() - 0.01f);
			this.setThrottleDirect(throttle);
		}
		boolean afterburner = pilot != null && this.inBoost && this.en > 0f && !this.overheat && jet != null;
		this.afterburnerNow = afterburner;
		this.boostingNow = afterburner;
		this.hoveringNow = false;
		this.brakingNow = false;
		if (afterburner) {
			this.drain(jet.afterburnerCost());
		}
		float maxSpeed = def.maxSpeed() * (afterburner ? jet.afterburner() : 1f);
		float effThrottle = afterburner ? 1f : throttle;

		boolean raw = this.onGround();
		if (raw == this.jetSurfaced) {
			this.jetSurfacedDebounce = 0;
		} else if (++this.jetSurfacedDebounce >= 3) {
			this.jetSurfaced = raw;
			this.jetSurfacedDebounce = 0;
		}
		boolean surfaced = this.jetSurfaced;
		this.ticksSinceGrounded = surfaced ? 0 : this.ticksSinceGrounded + 1;
		float actual = (float) this.getDeltaMovement().length();
		if (this.jetSustained) {
			if (this.cruiseSpeed < def.maxSpeed() * JET_SUSTAINED_DISENGAGE) {
				this.jetSustained = false;
			}
		} else if (this.cruiseSpeed >= def.maxSpeed() * JET_MIN_FLIGHT_FRACTION) {
			this.jetSustained = true;
		}
		float stallFraction = def.stallSpeedFraction();
		if (surfaced) {
			this.jetStalling = false;
		} else if (!this.jetStalling && this.jetLifted && actual < def.maxSpeed() * stallFraction) {
			this.jetStalling = true;
		} else if (this.jetStalling && actual >= def.maxSpeed() * (stallFraction + 0.1f)) {
			this.jetStalling = false;
		}

		// ---- attitude
		if (surfaced) {
			float steer = pilot != null ? -this.inStrafe * def.turnSpeed() * 1.5f + Mth.clamp(this.pendingYawInput * 0.3f, -6f, 6f) : 0f;
			Quaternionf ground = new Quaternionf().rotationY((float) Math.toRadians(-(this.getYRot() + steer)));
			this.pendingYawInput = 0f;
			this.pendingPitchInput = Math.min(0f, this.pendingPitchInput);
			if (pilot != null && this.cruiseSpeed > def.maxSpeed() * JET_MIN_FLIGHT_FRACTION && this.pendingPitchInput < 0f) {
				this.updateFlightOrientation(true, null, 0f); // rotation for take-off
			} else {
				this.pendingPitchInput = 0f;
				this.updateFlightOrientation(false, ground, 0.3f);
			}
			if (pilot != null) {
				this.jetLifted = false;
			}
		} else if (this.jetStalling) {
			if (!this.jetHoldYawLocked) {
				this.jetHoldYaw = this.getYRot();
				this.jetHoldYawLocked = true;
			}
			Quaternionf noseDown = new Quaternionf().rotationY((float) Math.toRadians(-this.jetHoldYaw)).rotateX((float) Math.toRadians(70f));
			this.pendingYawInput = 0f;
			this.pendingPitchInput = 0f;
			this.updateFlightOrientation(false, noseDown, JET_STALL_PITCH_SMOOTHING);
			this.jetLifted = true;
		} else {
			this.jetHoldYawLocked = false;
			this.updateFlightOrientation(pilot != null, null, 0f);
			this.jetLifted = true;
		}

		// ---- velocity
		Vec3 target;
		if (this.isInWater()) {
			Vec3 cur = this.getDeltaMovement();
			target = new Vec3(cur.x * 0.85, -0.08, cur.z * 0.85);
			this.cruiseSpeed *= 0.85f;
		} else if (surfaced) {
			float ts = effThrottle * maxSpeed;
			float blend = ts < this.cruiseSpeed ? JET_GROUND_DECEL : JET_GROUND_BLEND;
			this.cruiseSpeed += (ts - this.cruiseSpeed) * blend;
			Vec3 heading = this.getViewVector(1f);
			target = new Vec3(heading.x * this.cruiseSpeed, -0.05, heading.z * this.cruiseSpeed);
			if (this.jetSustained && this.getXRot() < -3f) {
				target = new Vec3(target.x, JET_LIFTOFF_VELOCITY, target.z);
			}
		} else {
			Vec3 heading = this.getViewVector(1f);
			Vec3 vel = this.getDeltaMovement();
			double hs = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
			float rawPath = hs < 1e-4 && Math.abs(vel.y) < 1e-4 ? 0f : (float) Math.toDegrees(Math.atan2(vel.y, hs));
			if (Math.abs(rawPath) >= Math.abs(this.jetSmoothedPathPitch)) {
				this.jetSmoothedPathPitch = rawPath;
			} else {
				this.jetSmoothedPathPitch += (rawPath - this.jetSmoothedPathPitch) * 0.03f;
			}
			float path = this.jetSmoothedPathPitch;
			float ts;
			float blend;
			float base = this.jetSustained ? Mth.clamp(def.acceleration(), 0.01f, 1f) : JET_GLIDE_BLEND;
			if (path >= 0f) {
				float climb = Mth.clamp(path / 90f, 0f, 1f);
				ts = effThrottle * maxSpeed * Mth.lerp(climb, 1f, JET_CLIMB_MIN_FRACTION);
				blend = Math.max(base, climb * JET_CLIMB_DECEL);
			} else {
				float dive = Mth.clamp(-path / 90f, 0f, 1f);
				ts = Mth.lerp(dive, effThrottle * maxSpeed, maxSpeed * JET_DIVE_MULTIPLIER);
				blend = Math.max(base, dive * JET_DIVE_BLEND);
			}
			if (ts < this.cruiseSpeed) {
				blend = Math.max(blend * 1.5f, 0.002f);
			}
			this.cruiseSpeed += (ts - this.cruiseSpeed) * blend;
			target = heading.scale(this.cruiseSpeed);
			if (!this.jetSustained && !this.jetStalling && this.cruiseSpeed < def.maxSpeed() * (stallFraction + 0.1f)) {
				float lower = def.maxSpeed() * stallFraction;
				float upper = def.maxSpeed() * (stallFraction + 0.1f);
				float f = upper > lower ? Mth.clamp(1f - (this.cruiseSpeed - lower) / (upper - lower), 0f, 1f) : 1f;
				double gy = Math.max(JET_NEAR_STALL_MAX_FALL, vel.y + def.gravity() * JET_NEAR_STALL_GRAVITY * f);
				target = new Vec3(target.x, Mth.lerp(f, target.y, gy), target.z);
			}
			if (this.jinkTicks > 0) {
				// Lateral jink: a sideways shove that decays over its duration.
				double yawRad = Math.toRadians(this.getYRot());
				double side = this.jinkSide * 1.5 * (this.jinkTicks / 10.0);
				target = target.add(Math.cos(yawRad) * side, 0, Math.sin(yawRad) * side);
			}
		}
		double preSpeed = target.length();
		this.setDeltaMovement(target);
		this.move(MoverType.SELF, this.getDeltaMovement());
		if (this.horizontalCollision && preSpeed > JET_CRASH_MIN_SPEED && this.level() instanceof ServerLevel sw) {
			this.hurtServer(sw, this.damageSources().flyIntoWall(), (float) (preSpeed * JET_CRASH_DAMAGE_PER_SPEED));
			this.cruiseSpeed = 0f;
		}
		this.prevTravel = this.travel;
		this.travel += (float) this.getDeltaMovement().horizontalDistance();
	}

	private boolean tryJink(float localX) {
		if (this.jinkTicks > 0 || this.en < 200f || this.overheat) {
			return false;
		}
		int side = localX >= 0f ? 1 : -1;
		this.jinkSide = side;
		this.jinkTicks = 10;
		this.drain(200f);
		this.qbCounter++;
		this.qbOctant = side > 0 ? 2 : 6;
		this.fireEvent(MechClientHooks.EVENT_JINK, side, 0f);
		return true;
	}

	public float getCruiseSpeed() {
		return this.cruiseSpeed;
	}

	// ==================================================================================== persistence

	@Override
	protected void readAdditionalSaveData(ValueInput view) {
		super.readAdditionalSaveData(view);
		this.entityData.set(ASSEMBLY, view.getStringOr("MechAssembly", ""));
		MechLayout layout = this.getLayout();
		int form = view.getIntOr("MechForm", 0);
		this.entityData.set(FORM, layout != null && layout.canTransform ? form : 0);
		this.transform = this.prevTransform = this.isJet() ? 1f : 0f;
		this.lastFormSeen = this.entityData.get(FORM);
		this.en = view.getFloatOr("MechEn", -1f);
		this.setBrain(view.child("MechAI").map(com.example.mechframe.ai.MechBrain::read).orElse(null));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput view) {
		super.addAdditionalSaveData(view);
		view.putString("MechAssembly", this.entityData.get(ASSEMBLY));
		view.putInt("MechForm", this.entityData.get(FORM));
		view.putFloat("MechEn", this.en);
		if (this.brain != null) {
			this.brain.write(view.child("MechAI"));
		}
	}
}
