package com.example.mechframe.ai;

import com.example.mechframe.MechFrame;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.Assembly;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A command post: keeps {@code count} AI frames deployed around itself (GUARD mode, territory =
 * {@code radius} around the post). Placed by the outpost structures, with its settings in the
 * structure template's block-entity data.
 *
 * <p>Every unit is a fresh random frame (RandomAssembly, tiers tierMin..tierMax) rolled when it is
 * deployed and kept for its whole life. When a unit is destroyed - or has been missing for a while
 * - the post deploys a new one after MechServerConfig#respawnMinutes (world time, so time passes
 * while the area is unloaded; the redeploy happens when it is loaded again). Breaking the post
 * stops the redeploys; the units already out keep guarding.
 */
public class CommandPostBlockEntity extends BlockEntity {

	/** Default preset id the units are created from (only its vehicle-level settings are used). */
	private static final Identifier UNIT_DEFINITION = Identifier.of(MechFrame.MOD_ID, "striker");
	/** A unit that cannot be found for this long (ticks, while the post is loaded) is taken for lost. */
	private static final int MISSING_LIMIT = 600;

	public Faction faction = Faction.ENEMY;
	public int radius = 40;
	public int tierMin = 1;
	public int tierMax = 3;
	public int count = 1;
	public float skill = 0.5f;

	private final List<UUID> units = new ArrayList<>();
	/** Per slot: world time of the next deployment (0: as soon as possible, -1: nothing pending). */
	private final List<Long> respawnAt = new ArrayList<>();
	private final List<Integer> missing = new ArrayList<>();

	public CommandPostBlockEntity(BlockPos pos, BlockState state) {
		super(MechFrame.COMMAND_POST_ENTITY, pos, state);
	}

	private void ensureSlots() {
		int n = MathHelper.clamp(this.count, 1, 8);
		while (this.units.size() < n) {
			this.units.add(null);
			this.respawnAt.add(0L);
			this.missing.add(0);
		}
		while (this.units.size() > n) {
			this.units.removeLast();
			this.respawnAt.removeLast();
			this.missing.removeLast();
		}
	}

	public static void tick(World world, BlockPos pos, BlockState state, CommandPostBlockEntity post) {
		if (!(world instanceof ServerWorld server) || world.getTime() % 20 != Math.floorMod(pos.asLong(), 20)) {
			return;
		}
		post.serverTick(server);
	}

	private void serverTick(ServerWorld world) {
		if (this.faction == Faction.ENEMY && world.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		this.ensureSlots();
		long now = world.getTime();
		boolean dirty = false;
		for (int i = 0; i < this.units.size(); i++) {
			UUID id = this.units.get(i);
			if (id != null) {
				Entity e = world.getEntity(id);
				if (e instanceof MechEntity mech && !mech.isRemoved() && !mech.tudursvehiclemod$isDestroyed()) {
					this.missing.set(i, 0);
					continue;
				}
				boolean lost = e instanceof MechEntity mech && mech.tudursvehiclemod$isDestroyed();
				if (!lost) {
					int m = this.missing.get(i) + 20;
					this.missing.set(i, m);
					lost = m >= MISSING_LIMIT;
				}
				if (!lost) {
					continue;
				}
				this.units.set(i, null);
				this.missing.set(i, 0);
				this.respawnAt.set(i, now + MechServerConfig.respawnTicks());
				dirty = true;
			}
			long at = this.respawnAt.get(i);
			if (at >= 0 && now >= at) {
				MechEntity unit = this.deploy(world, i);
				if (unit != null) {
					this.units.set(i, unit.getUuid());
					this.respawnAt.set(i, -1L);
					dirty = true;
				}
			}
		}
		if (dirty) {
			this.markDirty();
		}
	}

	/** Whether the post at pos (if loaded) still counts this unit as its slot's unit. True when the
	 * post is not loaded or gone - only a post that has positively replaced the unit says no. */
	public static boolean stillOwns(ServerWorld world, BlockPos pos, int slot, UUID unit) {
		if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
			return true;
		}
		if (!(world.getBlockEntity(pos) instanceof CommandPostBlockEntity post)) {
			return true;
		}
		if (slot < 0 || slot >= post.units.size()) {
			return false;
		}
		UUID owned = post.units.get(slot);
		return owned == null || owned.equals(unit);
	}

	private MechEntity deploy(ServerWorld world, int slot) {
		Assembly assembly = RandomAssembly.roll(world.getRandom(), this.tierMin, this.tierMax);
		if (assembly == null) {
			return null;
		}
		BlockPos p = this.getPos();
		Vec3d spot = null;
		for (int attempt = 0; attempt < 12 && spot == null; attempt++) {
			double a = world.getRandom().nextDouble() * Math.PI * 2.0;
			double r = 6.0 + world.getRandom().nextDouble() * Math.min(10.0, this.radius * 0.4);
			int x = MathHelper.floor(p.getX() + 0.5 + Math.cos(a) * r);
			int z = MathHelper.floor(p.getZ() + 0.5 + Math.sin(a) * r);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			Box box = new Box(x - 1.5, y, z - 1.5, x + 2.5, y + 5.0, z + 2.5);
			if (world.isSpaceEmpty(box) && !world.getFluidState(new BlockPos(x, y, z).down()).isStill()) {
				spot = new Vec3d(x + 0.5, y, z + 0.5);
			}
		}
		if (spot == null) {
			return null;
		}
		MechEntity mech = MechFrame.MECH.create(world, SpawnReason.EVENT);
		if (mech == null) {
			return null;
		}
		mech.setVehicleDefinitionId(UNIT_DEFINITION);
		mech.setAssembly(assembly);
		if (mech.getLayout() == null) {
			mech.discard();
			return null;
		}
		mech.refreshPositionAndAngles(spot.x, spot.y, spot.z, world.getRandom().nextFloat() * 360f, 0f);
		MechBrain brain = new MechBrain(this.faction, MechBrain.Mode.GUARD, Vec3d.ofBottomCenter(p), this.radius);
		brain.post = p.toImmutable();
		brain.postSlot = slot;
		brain.skill = this.skill;
		mech.setBrain(brain);
		mech.setCustomName(unitName(this.faction, assembly));
		mech.tudursvehiclemod$fillAllWeaponAmmoAndFuel();
		mech.tudursvehiclemod$syncWeaponAmmo();
		world.spawnEntity(mech);
		return mech;
	}

	/** "Hostile Unit: <core maker>" / "Allied Unit: ...", coloured by side. */
	public static Text unitName(Faction faction, Assembly assembly) {
		MechPart core = assembly.part(MountSlot.CORE);
		String maker = core != null && !core.maker().isEmpty() ? core.maker() : "";
		return Text.translatable(faction == Faction.ENEMY ? "entity.mechframe.unit.enemy" : "entity.mechframe.unit.friendly", maker)
				.formatted(faction == Faction.ENEMY ? Formatting.RED : Formatting.GREEN);
	}

	/** Status line for a player inspecting the post. */
	public Text status(World world) {
		this.ensureSlots();
		int alive = 0;
		long next = Long.MAX_VALUE;
		for (int i = 0; i < this.units.size(); i++) {
			if (this.units.get(i) != null) {
				alive++;
			} else if (this.respawnAt.get(i) > 0) {
				next = Math.min(next, this.respawnAt.get(i));
			}
		}
		long secs = next == Long.MAX_VALUE ? -1 : Math.max(0, (next - world.getTime()) / 20);
		return Text.translatable("message.mechframe.post.status", Text.translatable("message.mechframe.faction." + this.faction.id()),
				alive, this.units.size(), secs < 0 ? "-" : (secs / 60) + ":" + String.format("%02d", secs % 60));
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.faction = Faction.byId(view.getString("Faction", "enemy"));
		if (this.faction == Faction.NONE) {
			this.faction = Faction.ENEMY;
		}
		this.radius = MathHelper.clamp(view.getInt("Radius", 40), 8, 256);
		this.tierMin = MathHelper.clamp(view.getInt("TierMin", 1), 1, 5);
		this.tierMax = MathHelper.clamp(view.getInt("TierMax", 3), this.tierMin, 5);
		this.count = MathHelper.clamp(view.getInt("Count", 1), 1, 8);
		this.skill = MathHelper.clamp(view.getFloat("Skill", 0.5f), 0f, 1f);
		this.units.clear();
		this.respawnAt.clear();
		this.missing.clear();
		String[] u = view.getString("Units", "").split(",", -1);
		String[] r = view.getString("RespawnAt", "").split(",", -1);
		for (int i = 0; i < this.count; i++) {
			UUID id = null;
			if (i < u.length && !u[i].isEmpty()) {
				try {
					id = UUID.fromString(u[i]);
				} catch (IllegalArgumentException ignored) {
					id = null;
				}
			}
			long at = 0L;
			if (i < r.length && !r[i].isEmpty()) {
				try {
					at = Long.parseLong(r[i]);
				} catch (NumberFormatException ignored) {
					at = 0L;
				}
			}
			this.units.add(id);
			this.respawnAt.add(id != null ? -1L : at);
			this.missing.add(0);
		}
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		this.ensureSlots();
		view.putString("Faction", this.faction.id());
		view.putInt("Radius", this.radius);
		view.putInt("TierMin", this.tierMin);
		view.putInt("TierMax", this.tierMax);
		view.putInt("Count", this.count);
		view.putFloat("Skill", this.skill);
		StringBuilder u = new StringBuilder();
		StringBuilder r = new StringBuilder();
		for (int i = 0; i < this.units.size(); i++) {
			if (i > 0) {
				u.append(',');
				r.append(',');
			}
			UUID id = this.units.get(i);
			u.append(id == null ? "" : id.toString());
			r.append(this.respawnAt.get(i));
		}
		view.putString("Units", u.toString());
		view.putString("RespawnAt", r.toString());
	}
}
