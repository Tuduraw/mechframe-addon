package com.example.mechframe.part;

import net.minecraft.util.math.MathHelper;

import java.util.Optional;

/** Everything about how a frame performs, derived from its assembly. Built once per assembly (see
 * MechLayout.of(), which caches it alongside the geometry) - never per tick.
 *
 * <p>Weight: the legs carry everything else (load vs load_limit); exceeding it scales every speed
 * down (to at most -65%) and drains more EN per quick boost. The arms carry the two arm weapons;
 * exceeding their load slows aiming. Speeds are blocks per tick (1 b/t = 72 km/h). */
public record MechStats(
		boolean valid,
		MechPart.LegType legType,
		float totalWeight,
		float legLoad,
		float legLoadLimit,
		float armLoad,
		float armLoadLimit,
		float maxAp,
		float defense,
		float enCapacity,
		float enRecharge,
		int rechargeDelay,
		float walkSpeed,
		float boostSpeed,
		float qbSpeed,
		float qbCost,
		int qbCooldown,
		float jump,
		float hoverThrust,
		float hoverCost,
		float airBoostCost,
		float turnRate,
		float stepHeight,
		float traction,
		float aimSpeed,
		float lockRange,
		float lockCone,
		float fuel,
		Optional<MechPart.JetSpec> jet,
		/** Head x core stability: divides how far the body leans when accelerating, braking and
		 * strafing, and how hard landings shake the camera. 1 = neutral. */
		float stability
) {

	public static final MechStats INVALID = new MechStats(false, MechPart.LegType.BIPED, 0, 0, 1, 0, 1, 100, 1,
			1000, 20, 20, 0.2f, 0.5f, 1.5f, 400, 12, 0.6f, 0.1f, 15, 6, 10, 1, 0.3f, 180, 80, 12, 500, Optional.empty(), 1f);

	public float overloadRatio() {
		return Math.max(0f, this.legLoad - this.legLoadLimit) / Math.max(1f, this.legLoadLimit);
	}

	public float armOverloadRatio() {
		return Math.max(0f, this.armLoad - this.armLoadLimit) / Math.max(1f, this.armLoadLimit);
	}

	public static MechStats compute(Assembly assembly) {
		MechPart head = assembly.part(MountSlot.HEAD);
		MechPart core = assembly.part(MountSlot.CORE);
		MechPart arms = assembly.part(MountSlot.ARMS);
		MechPart legs = assembly.part(MountSlot.LEGS);
		MechPart booster = assembly.part(MountSlot.BOOSTER);
		if (core == null || arms == null || legs == null || legs.legs() == null) {
			return INVALID;
		}
		float total = 0f;
		float ap = 0f;
		for (MountSlot mount : MountSlot.values()) {
			MechPart part = assembly.part(mount);
			if (part != null) {
				total += part.weight();
				ap += part.armor();
			}
		}
		float armLoad = 0f;
		for (MountSlot mount : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM}) {
			MechPart part = assembly.part(mount);
			if (part != null) {
				armLoad += part.weight();
			}
		}
		MechPart.LegSpec l = legs.legs();
		MechPart.BoosterSpec b = booster != null ? booster.booster() : MechPart.BoosterSpec.DEFAULT;
		MechPart.HeadSpec h = head != null ? head.head() : MechPart.HeadSpec.DEFAULT;
		MechPart.CoreSpec c = core.core();
		MechPart.ArmSpec a = arms.arms();

		float legLoad = total - legs.weight();
		float overload = Math.max(0f, legLoad - l.loadLimit()) / Math.max(1f, l.loadLimit());
		float speedMul = MathHelper.clamp(1f - 0.9f * overload, 0.35f, 1f);
		// Heavier frames feel heavier even within their limit: a gentle curve on quick boost and turning.
		float massFactor = MathHelper.clamp((total - 45f) / 160f, 0f, 0.3f);
		float armOverload = Math.max(0f, armLoad - a.loadLimit()) / Math.max(1f, a.loadLimit());

		boolean noBooster = booster == null;
		float boostSpeed = noBooster ? l.walkSpeed() * 1.4f : b.boostSpeed() * l.boostMultiplier();
		float qbSpeed = noBooster ? 0f : b.qbSpeed() * l.qbMultiplier() * (1f - massFactor);
		return new MechStats(
				assembly.isComplete(),
				l.type(),
				total,
				legLoad,
				l.loadLimit(),
				armLoad,
				a.loadLimit(),
				Math.max(1f, ap),
				c.defense(),
				c.enCapacity(),
				c.enRecharge(),
				c.rechargeDelay(),
				l.walkSpeed() * speedMul,
				boostSpeed * speedMul,
				qbSpeed * speedMul,
				b.qbCost() * (1f + overload),
				b.qbCooldown(),
				l.jump() * MathHelper.clamp(1f - 0.6f * overload, 0.4f, 1f),
				noBooster ? 0f : b.hoverThrust() * l.hoverMultiplier() * speedMul,
				b.hoverCost(),
				b.airBoostCost(),
				l.turnRate() * (1f - massFactor * 0.6f),
				l.stepHeight(),
				l.traction(),
				a.aimSpeed() * MathHelper.clamp(1f - 0.7f * armOverload, 0.3f, 1f),
				h.lockRange(),
				h.lockCone(),
				c.fuel(),
				c.jet(),
				MathHelper.clamp(h.stability() * c.stability(), 0.5f, 2f));
	}
}
