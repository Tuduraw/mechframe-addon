package com.example.mechframe.ai;

import com.example.mechframe.entity.MechEntity;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.Difficulty;

import java.util.Locale;

/** Which side an AI-driven frame is on, and who that side shoots at.
 *
 * <ul>
 *   <li>ENEMY: players, any vehicle with a player aboard (frames included), FRIENDLY frames.</li>
 *   <li>FRIENDLY: hostile mobs, ENEMY frames, and the enemy vehicles of Enemy Vehicle Addon
 *       (recognised by their pilot - no dependency on that mod).</li>
 * </ul>
 * NONE is an ordinary frame (no AI): never a target of FRIENDLY frames, a target of ENEMY frames
 * only while a player rides it. */
public enum Faction {
	NONE, ENEMY, FRIENDLY;

	/** The pilot entity Enemy Vehicle Addon seats in its enemy vehicles. */
	private static final String EVA_PILOT = "com.example.enemyvehicleaddon.entity.EnemyPilotEntity";

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static Faction byId(String id) {
		for (Faction f : values()) {
			if (f.id().equalsIgnoreCase(id)) {
				return f;
			}
		}
		return NONE;
	}

	/** Whether a frame of this faction should attack the entity. */
	public boolean isHostileTo(Entity e, MechEntity self) {
		if (e == null || e == self || !e.isAlive() || e.isRemoved() || self.hasPassenger(e)) {
			return false;
		}
		if (e instanceof AbstractVehicleEntity vehicle && vehicle.tudursvehiclemod$isDestroyed()) {
			return false;
		}
		return switch (this) {
			case ENEMY -> {
				if (self.getEntityWorld().getDifficulty() == Difficulty.PEACEFUL) {
					yield false;
				}
				if (e instanceof PlayerEntity player) {
					yield targetablePlayer(player) && !player.hasVehicle();
				}
				if (e instanceof MechEntity mech && mech.getFaction() == FRIENDLY) {
					yield true;
				}
				if (e instanceof AbstractVehicleEntity vehicle) {
					yield hasTargetablePlayer(vehicle);
				}
				yield false;
			}
			case FRIENDLY -> {
				if (e instanceof MechEntity mech) {
					yield mech.getFaction() == ENEMY;
				}
				if (e instanceof AbstractVehicleEntity vehicle) {
					yield isEnemyVehicleAddonEnemy(vehicle);
				}
				yield e instanceof Monster && e instanceof LivingEntity;
			}
			case NONE -> false;
		};
	}

	static boolean targetablePlayer(PlayerEntity player) {
		return !player.isSpectator() && (!player.isCreative() || MechServerConfig.targetCreativePlayers);
	}

	private static boolean hasTargetablePlayer(AbstractVehicleEntity vehicle) {
		for (Entity p : vehicle.getPassengerList()) {
			if (p instanceof PlayerEntity player && targetablePlayer(player)) {
				return true;
			}
		}
		return false;
	}

	/** An Enemy Vehicle Addon enemy: a vehicle flown / driven by its enemy pilot. */
	public static boolean isEnemyVehicleAddonEnemy(AbstractVehicleEntity vehicle) {
		for (Entity p : vehicle.getPassengerList()) {
			if (p.getClass().getName().equals(EVA_PILOT)) {
				return true;
			}
		}
		return false;
	}
}
