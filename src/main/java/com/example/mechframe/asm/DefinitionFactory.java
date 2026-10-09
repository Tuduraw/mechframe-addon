package com.example.mechframe.asm;

import com.example.mechframe.MechFrame;
import com.example.mechframe.part.MechForm;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MechStats;
import com.example.mechframe.part.MountSlot;
import com.example.tudursvehiclemod.asset.CameraPositionEntry;
import com.example.tudursvehiclemod.asset.SeatDefinition;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.WeaponAimRange;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.asset.WeaponOffset;
import com.example.tudursvehiclemod.asset.WeaponPart;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Synthesizes the base mod's VehicleDefinition for an assembled frame - one per (assembly, form,
 * preset definition), cached, so the base mod's identity-keyed caches (static body mesh, animated
 * part names) see one stable instance per distinct frame.
 *
 * <p>What comes from where:
 * <ul>
 *   <li>the preset's vehicle JSON (or a built-in default where the client has none): HUD, sounds,
 *       armour thresholds, spawn item, inventory, everything a part has no opinion about;</li>
 *   <li>the parts: health (sum of armour), speeds, fuel, seat and camera from the core, one weapon
 *       per filled weapon mount (on its own seat index, pilot_usable) and one weapon part per arm and
 *       back weapon, all positioned by MechLayout;</li>
 *   <li>HitMeshBuilder: the model id (a generated, in-memory hit mesh).</li>
 * </ul> */
public final class DefinitionFactory {

	public static final Identifier ENTITY_TYPE = Identifier.of(MechFrame.MOD_ID, "mech");

	private record Key(String assembly, MechForm form, int generation) {
	}

	private record Entry(VehicleDefinition base, VehicleDefinition built) {
	}

	private static final Map<Key, Entry> CACHE = new HashMap<>();
	private static VehicleDefinition fallbackBase;

	private DefinitionFactory() {
	}

	public static synchronized VehicleDefinition get(MechLayout layout, MechForm form, VehicleDefinition presetDef) {
		VehicleDefinition base = presetDef != null && ENTITY_TYPE.equals(presetDef.entityType()) ? presetDef : fallbackBase();
		Key key = new Key(layout.assembly.encode(), form, com.example.mechframe.part.PartRegistry.generation());
		Entry entry = CACHE.get(key);
		if (entry != null && entry.base == base) {
			return entry.built;
		}
		VehicleDefinition built = build(layout, form == MechForm.JET && layout.canTransform ? MechForm.JET : MechForm.ROBOT, base);
		CACHE.put(key, new Entry(base, built));
		return built;
	}

	public static synchronized void clear() {
		CACHE.clear();
	}

	/** A definition with every base-mod default, for a client that never received the preset JSON. */
	public static VehicleDefinition fallbackBase() {
		if (fallbackBase == null) {
			String json = "{\"entity_type\":\"" + ENTITY_TYPE + "\",\"model\":\"mechframe:models/obj/frame_icon.obj\","
					+ "\"texture\":\"mechframe:textures/part/frame_icon.png\",\"width\":1.8,\"height\":5.0,"
					+ "\"hud\":\"mech_hud\",\"weight_type\":\"car\",\"max_health\":1000,\"armor_damage_factor\":0.8,"
					+ "\"armor_min_damage\":1.0,\"inventory_size\":27,\"fuel_consumption\":0.3,"
					+ "\"submerged_damage_height\":3.0,\"flare_type\":0}";
			fallbackBase = VehicleDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
		}
		return fallbackBase;
	}

	private static VehicleDefinition build(MechLayout layout, MechForm form, VehicleDefinition base) {
		MechStats stats = layout.stats;
		boolean jet = form == MechForm.JET;
		MechPart.JetSpec jetSpec = stats.jet().orElse(null);
		Identifier model = HitMeshBuilder.ensure(layout, form);
		MechPart core = layout.assembly.part(MountSlot.CORE);
		Identifier texture = core != null ? core.texture() : base.texture();

		Vector3f seat = jet ? layout.jetSeat : layout.seat;
		Vector3f eye = jet ? layout.jetEye : layout.eye;
		List<SeatDefinition> seats = List.of(new SeatDefinition("pilot", seat.x, seat.y, seat.z, true, false,
				List.of(new CameraPositionEntry(eye.x, eye.y, eye.z, false, Optional.empty(), Optional.empty())),
				Optional.of((double) (layout.collisionWidth * 0.5f + 1.2f)), Optional.empty(), Optional.of(0.0)));

		List<WeaponDefinition> weapons = new ArrayList<>();
		List<WeaponPart> weaponParts = new ArrayList<>();
		if (!jet) {
			// Both arms always aim, armed or not - an empty hand still points where the pilot looks.
			for (MountSlot arm : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM}) {
				MechPart weapon = layout.assembly.part(arm);
				Vector3f pivot = layout.weaponPivot.get(arm);
				WeaponAimRange range = aimRange(weapon, -100, 100, -85, 70);
				// A melee weapon has no base-mod weapon behind it: the arm just aims (the swing is ours).
				boolean gun = weapon != null && weapon.weapon() != null && !weapon.weapon().isMelee();
				weaponParts.add(new WeaponPart(arm.weaponPartName(), arm.seatIndex(), false, true, true,
						pivot.x, pivot.y, pivot.z,
						gun ? weapon.weapon().recoilDistance() / Math.max(0.3f, armRecoilControl(layout)) : 0f,
						true, Optional.empty(), Optional.of(range),
						gun ? Optional.of(arm.weaponAlias(weapon.weapon().weaponName())) : Optional.empty(), false));
			}
			for (MountSlot back : new MountSlot[]{MountSlot.R_BACK, MountSlot.L_BACK}) {
				MechPart weapon = layout.assembly.part(back);
				if (weapon == null) {
					continue;
				}
				Vector3f pivot = layout.weaponPivot.get(back);
				weaponParts.add(new WeaponPart(back.weaponPartName(), back.seatIndex(), false, true, true,
						pivot.x, pivot.y, pivot.z, weapon.weapon().recoilDistance(), true, Optional.empty(),
						Optional.of(aimRange(weapon, -60, 60, -70, 35)), Optional.of(back.weaponAlias(weapon.weapon().weaponName())), false));
			}
		}
		for (MountSlot mount : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK}) {
			MechPart weapon = layout.assembly.part(mount);
			if (weapon == null || weapon.weapon() == null || weapon.weapon().isMelee()) {
				continue;
			}
			MechPart.WeaponSpec spec = weapon.weapon();
			if (jet) {
				Vector3f muzzle = layout.jetMuzzle.get(mount);
				weapons.add(new WeaponDefinition(mount.seatIndex(),
						List.of(new WeaponOffset(muzzle.x, muzzle.y, muzzle.z, 0.0, 0.0, Optional.empty())),
						Optional.empty(), spec.projectileItem(), mount.weaponAlias(spec.weaponName()), true, Optional.empty()));
			} else {
				Vector3f muzzle = layout.robotMuzzle.get(mount);
				float aimSpeed = mount.isArmWeapon() ? stats.aimSpeed() : stats.aimSpeed() * 0.7f;
				weapons.add(new WeaponDefinition(mount.seatIndex(),
						List.of(new WeaponOffset(muzzle.x, muzzle.y, muzzle.z, 0.0, 0.0, Optional.of(mount.weaponPartName()))),
						Optional.of(mount.isArmWeapon() ? aimRange(weapon, -100, 100, -85, 70) : aimRange(weapon, -60, 60, -70, 35)),
						spec.projectileItem(), mount.weaponAlias(spec.weaponName()), true, Optional.of(aimSpeed)));
			}
		}

		float width = layout.collisionWidth;
		float height = jet ? layout.jetHeight : layout.collisionHeight;
		float maxSpeed = jet && jetSpec != null ? jetSpec.maxSpeed() : Math.max(0.05f, stats.boostSpeed());
		float accel = jet && jetSpec != null ? jetSpec.acceleration() : 0.3f;
		float turn = jet && jetSpec != null ? jetSpec.turnSpeed() : stats.turnRate();
		float stall = jet && jetSpec != null ? jetSpec.stallFraction() : base.stallSpeedFraction();

		return new VehicleDefinition(
				ENTITY_TYPE, model, texture, 1.0f, width, height,
				maxSpeed, accel, turn, stats.stepHeight(), -0.11f, 0f,
				jet ? 0f : -1f, Optional.empty(), base.engineSoundVolume(), 0f, base.wheelRotationSpeed(),
				Optional.of(jet ? 1.0f : 2.0f), base.weightType(), Optional.of(1),
				seats, weapons, List.of(), List.of(), base.spawnItem(), base.hud(), base.engineSound(), weaponParts,
				stats.maxAp(), base.armorDamageFactor() / Math.max(0.2f, stats.defense()), base.armorMinDamage(),
				base.armorMaxDamage(), base.damageFactor(), stats.fuel(), base.fuelConsumptionPerSecond(),
				base.inventorySize(), List.of(), Math.max(base.submergedDamageHeight(), jet ? 0.8f : layout.hip.y),
				true, stall, true, base.entityWidth(), base.entityHeight(), false,
				List.of(), List.of(), List.of(), List.of(), List.of(), false, false, base.vtolHoverSpeedFraction(),
				List.of(), Optional.empty(), base.wakeTrailDurationTicks(), base.flareType(),
				List.of(), List.of(), 0f, 0f, 0f, false, false, false, Optional.empty());
	}

	private static float armRecoilControl(MechLayout layout) {
		MechPart arms = layout.assembly.part(MountSlot.ARMS);
		return arms != null ? arms.arms().recoilControl() : 1f;
	}

	private static WeaponAimRange aimRange(MechPart weapon, float minYaw, float maxYaw, float minPitch, float maxPitch) {
		if (weapon != null && weapon.weapon() != null) {
			MechPart.WeaponSpec s = weapon.weapon();
			minYaw = Math.max(minYaw, s.minYaw());
			maxYaw = Math.min(maxYaw, s.maxYaw());
			minPitch = Math.max(minPitch, s.minPitch());
			maxPitch = Math.min(maxPitch, s.maxPitch());
		}
		return new WeaponAimRange(0.0, minYaw, maxYaw, minPitch, maxPitch, List.of(), 0.0, 0.0);
	}
}
