package com.example.mechframe.ai;

import com.example.mechframe.part.Assembly;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MechStats;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Rolls a random frame from every loaded part (base mod and all packs) within a tier range: one
 * part per body mount, a weapon in each hand most of the time and on each shoulder some of the
 * time. A roll that overloads the legs drops shoulder weapons (then rerolls) so the unit can still
 * move properly. Nothing here is stock, so every part of it can drop. */
public final class RandomAssembly {

	private static final MountSlot[] BODY = {MountSlot.LEGS, MountSlot.CORE, MountSlot.HEAD, MountSlot.ARMS, MountSlot.BOOSTER};
	private static final int TRIES = 8;

	private RandomAssembly() {
	}

	/** Null if the registry cannot make a complete frame in that tier range (even widened to 1..5). */
	public static Assembly roll(Random random, int tierMin, int tierMax) {
		Assembly best = null;
		for (int i = 0; i < TRIES; i++) {
			Assembly a = rollOnce(random, tierMin, tierMax);
			if (a == null) {
				a = rollOnce(random, 1, 5);
			}
			if (a == null) {
				return null;
			}
			MechStats s = MechStats.compute(a);
			if (!s.valid()) {
				continue;
			}
			if (s.legLoad() > s.legLoadLimit()) {
				a = a.with(MountSlot.L_BACK, null, false).with(MountSlot.R_BACK, null, false);
				s = MechStats.compute(a);
			}
			best = a;
			if (s.legLoad() <= s.legLoadLimit() && s.armLoad() <= s.armLoadLimit() * 1.2f) {
				return a;
			}
		}
		return best;
	}

	private static Assembly rollOnce(Random random, int tierMin, int tierMax) {
		Map<MountSlot, Identifier> map = new EnumMap<>(MountSlot.class);
		for (MountSlot mount : BODY) {
			MechPart p = pick(random, mount, tierMin, tierMax);
			if (p == null && mount != MountSlot.HEAD && mount != MountSlot.BOOSTER) {
				return null;
			}
			if (p != null) {
				map.put(mount, p.id());
			}
		}
		Assembly a = new Assembly(map, 0);
		weapon(random, map, a, MountSlot.R_ARM, 0.95f, tierMin, tierMax);
		weapon(random, map, a, MountSlot.L_ARM, 0.85f, tierMin, tierMax);
		weapon(random, map, a, MountSlot.R_BACK, 0.5f, tierMin, tierMax);
		weapon(random, map, a, MountSlot.L_BACK, 0.4f, tierMin, tierMax);
		return new Assembly(map, 0);
	}

	private static void weapon(Random random, Map<MountSlot, Identifier> map, Assembly body, MountSlot mount, float chance,
			int tierMin, int tierMax) {
		if (body.isLocked(mount) || random.nextFloat() >= chance) {
			return;
		}
		MechPart p = pick(random, mount, tierMin, tierMax);
		if (p != null) {
			map.put(mount, p.id());
		}
	}

	private static MechPart pick(Random random, MountSlot mount, int tierMin, int tierMax) {
		List<MechPart> list = new ArrayList<>();
		for (MechPart p : PartRegistry.forMount(mount)) {
			if (p.tier() >= tierMin && p.tier() <= tierMax) {
				list.add(p);
			}
		}
		return list.isEmpty() ? null : list.get(random.nextInt(list.size()));
	}
}
