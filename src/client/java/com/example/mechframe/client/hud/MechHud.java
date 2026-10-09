package com.example.mechframe.client.hud;

import com.example.mechframe.client.MechInput;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MechStats;
import com.example.mechframe.part.MountSlot;
import com.example.tudursvehiclemod.asset.WeaponStatsLoader;
import com.example.tudursvehiclemod.client.hud.HudVariableProvider;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.Map;

/** HUD variables for the frame HUD script (assets/mechframe/hud/mech_hud.txt), all prefixed mf_,
 * and the in-world lock markers (orange = soft lock / FCS candidate, red = hard lock). */
public final class MechHud implements HudVariableProvider {

	private static final String[] MOUNT_KEYS = {"ra", "la", "rb", "lb"};
	private static final MountSlot[] MOUNTS = {MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK};

	@Override
	public void provideNumeric(MinecraftClient client, PlayerEntity player, AbstractVehicleEntity vehicle, Map<String, Double> vars) {
		if (!(vehicle instanceof MechEntity mech)) {
			return;
		}
		MechLayout layout = mech.getLayout();
		MechStats s = mech.getStats();
		vars.put("mf_en", (double) mech.getEn());
		vars.put("mf_en_max", (double) s.enCapacity());
		vars.put("mf_en_rto", (double) (mech.getEn() / Math.max(1f, s.enCapacity())));
		vars.put("mf_overheat", mech.isOverheated() ? 1.0 : 0.0);
		vars.put("mf_boost", mech.isBoostingVisual() ? 1.0 : 0.0);
		vars.put("mf_hover", mech.isHoveringVisual() ? 1.0 : 0.0);
		vars.put("mf_qb_ready", mech.isQuickBoostReady() ? 1.0 : 0.0);
		vars.put("mf_jet", mech.isJet() ? 1.0 : 0.0);
		vars.put("mf_can_transform", layout != null && layout.canTransform ? 1.0 : 0.0);
		vars.put("mf_show_mode", (layout != null && layout.canTransform) || mech.isTurbo()
				|| (MechInput.boostToggle && MechInput.boostOn) ? 1.0 : 0.0);
		vars.put("mf_transform", (double) mech.getTransformProgress(1f));
		vars.put("mf_transforming", mech.isTransforming() ? 1.0 : 0.0);
		vars.put("mf_weight", (double) s.totalWeight());
		vars.put("mf_load_rto", (double) (s.legLoad() / Math.max(1f, s.legLoadLimit())));
		vars.put("mf_overload", s.overloadRatio() > 0f ? 1.0 : 0.0);
		vars.put("mf_arm_overload", s.armOverloadRatio() > 0f ? 1.0 : 0.0);
		vars.put("mf_vs", mech.getVelocity().y * 20.0);
		if (!mech.isJet()) {
			// The base "speed" is the aircraft-style cruise speed, which the robot form never uses:
			// show the real ground speed (any direction - strafing and quick boosts count too).
			double hs = mech.getVelocity().horizontalLength();
			vars.put("speed", hs);
			vars.put("speed_kbh", hs * 72.0);
		}
		Entity lock = mech.getLockTarget();
		Entity cand = mech.getCandidate();
		Entity target = lock != null ? lock : cand;
		vars.put("mf_lock", lock != null ? 1.0 : 0.0);
		vars.put("mf_soft", lock == null && cand != null ? 1.0 : 0.0);
		vars.put("mf_tgt", target != null ? 1.0 : 0.0);
		vars.put("mf_tgt_dist", target != null ? (double) target.distanceTo(mech) : 0.0);
		double hpRatio = 0.0;
		if (target instanceof LivingEntity living) {
			hpRatio = living.getHealth() / Math.max(1f, living.getMaxHealth());
		} else if (target instanceof AbstractVehicleEntity other) {
			hpRatio = other.getHealth() / Math.max(1f, other.getMaxHealth());
		}
		vars.put("mf_tgt_hp_rto", hpRatio);
		for (int i = 0; i < MOUNTS.length; i++) {
			int index = MechInput.weaponIndex(mech, MOUNTS[i]);
			String k = "mf_" + MOUNT_KEYS[i];
			boolean melee = isMelee(mech, MOUNTS[i]);
			vars.put(k + "_has", index >= 0 || melee ? 1.0 : 0.0);
			if (index >= 0) {
				int[] state = mech.getWeaponAmmoState(index);
				vars.put(k + "_ammo", (double) Math.max(0, state[0]));
				vars.put(k + "_reload", state[1] > 0 ? 1.0 : 0.0);
				vars.put(k + "_sel", isActive(mech, MOUNTS[i]) ? 1.0 : 0.0);
			} else {
				vars.put(k + "_ammo", 0.0);
				vars.put(k + "_reload", 0.0);
				vars.put(k + "_sel", 0.0);
			}
			// Which key fires it: its side's trigger, while it is that side's active weapon.
			vars.put(k + "_keyed", !triggerName(mech, MOUNTS[i]).isEmpty() ? 1.0 : 0.0);
		}
	}

	private static boolean isMelee(MechEntity mech, MountSlot mount) {
		MechPart p = mech.getAssembly().part(mount);
		return p != null && p.weapon() != null && p.weapon().isMelee() && !mech.isJet();
	}

	private static boolean isRight(MountSlot mount) {
		return mount == MountSlot.R_ARM || mount == MountSlot.R_BACK;
	}

	/** Whether this mount is its side's active weapon (the one the side's trigger fires). */
	private static boolean isActive(MechEntity mech, MountSlot mount) {
		return MechInput.activeMount(mech, isRight(mount)) == mount;
	}

	/** The name of the trigger key that fires this mount's weapon now ("" if it is not active). */
	private static String triggerName(MechEntity mech, MountSlot mount) {
		if (!isActive(mech, mount)) {
			return "";
		}
		net.minecraft.client.option.KeyBinding key = isRight(mount) ? MechInput.fireRightKey : MechInput.fireLeftKey;
		return key.isUnbound() ? "" : key.getBoundKeyLocalizedText().getString().toUpperCase(java.util.Locale.ROOT);
	}

	@Override
	public void provideString(MinecraftClient client, PlayerEntity player, AbstractVehicleEntity vehicle, Map<String, String> vars) {
		if (!(vehicle instanceof MechEntity mech)) {
			return;
		}
		for (int i = 0; i < MOUNTS.length; i++) {
			MechPart weapon = mech.getAssembly().part(MOUNTS[i]);
			String name = weapon != null && weapon.weapon() != null
					? WeaponStatsLoader.get(weapon.weapon().weaponName()).displayName() : "-";
			if (weapon != null && weapon.weapon() != null && weapon.weapon().isMelee()) {
				name = weapon.displayName();
			}
			vars.put("mf_" + MOUNT_KEYS[i] + "_name", name);
			String label = new String[]{"R-ARM ", "L-ARM ", "R-BACK", "L-BACK"}[i];
			String state;
			if (isMelee(mech, MOUNTS[i])) {
				state = mech.isMeleeReady() ? "READY" : "----";
			} else {
				int index = MechInput.weaponIndex(mech, MOUNTS[i]);
				state = index >= 0 ? Integer.toString(Math.max(0, mech.getWeaponAmmoState(index)[0])) : "";
			}
			vars.put("mf_" + MOUNT_KEYS[i] + "_slot", label + "  " + state);
			vars.put("mf_" + MOUNT_KEYS[i] + "_key", triggerName(mech, MOUNTS[i]));
		}
		vars.put("mf_mode", mech.isTransforming() ? "TRANSFORM" : mech.isJet() ? "JET" : mech.isTurbo() ? "TURBO"
				: MechInput.boostToggle && MechInput.boostOn && mech == MechInput.pilotedMech(MinecraftClient.getInstance()) ? "BOOST" : "ROBOT");
		Entity target = mech.getFcsTarget();
		vars.put("mf_tgt_name", target != null ? target.getName().getString() : "");
		MechPart core = mech.getAssembly().part(MountSlot.CORE);
		vars.put("mf_frame", core != null ? mech.getDefinition().displayName(mech.getVehicleDefinitionId()) : "");
	}

	/** Once per client tick: lock markers for the frame the local player pilots. */
	public static void tickMarkers(MinecraftClient client) {
		MechEntity mech = MechInput.pilotedMech(client);
		if (mech == null || client.world == null) {
			return;
		}
		Entity lock = mech.getLockTarget();
		Entity cand = mech.getCandidate();
		try (var scope = client.newGizmoScope()) {
			if (lock != null) {
				GizmoDrawing.box(lock.getBoundingBox().expand(0.4), DrawStyle.stroked(0xFFFF3030, 2.5f));
				GizmoDrawing.entityLabel(lock, 0, "LOCK", 0xFFFF3030, 1.0f);
			}
			if (cand != null && cand != lock) {
				GizmoDrawing.box(cand.getBoundingBox().expand(0.3), DrawStyle.stroked(0xFFFF9A20, 1.5f));
			}
		}
	}
}
