package com.example.mechframe.part;

import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/** Which part sits in each mount of one frame.
 *
 * <p>stockMask: bit {@code mount.ordinal()} set means the part in that mount came with the frame
 * (its preset) rather than out of a garage. Swapping a stock part out does not produce an item -
 * the frame's stock parts are always available to it again for free instead (see
 * garage.AssemblyService) - while a part installed from a garage goes back into the garage when it
 * is replaced. That is what keeps spawning a frame from becoming a way to duplicate parts.
 *
 * <p>Immutable. Encoded as "mount=ns:path;...|stock" for the DataTracker and NBT. */
public record Assembly(Map<MountSlot, Identifier> parts, int stockMask) {

	public static final Assembly EMPTY = new Assembly(new EnumMap<>(MountSlot.class), 0);

	public Assembly {
		EnumMap<MountSlot, Identifier> copy = new EnumMap<>(MountSlot.class);
		copy.putAll(parts);
		parts = java.util.Collections.unmodifiableMap(copy);
	}

	public Identifier get(MountSlot mount) {
		return this.parts.get(mount);
	}

	/** The installed part, or null if the mount is empty - or holds something it can't take (a
	 * preset or save naming the wrong kind of part, or a part whose slot changed in a data reload),
	 * which is then simply treated as empty instead of breaking every consumer. */
	public MechPart part(MountSlot mount) {
		MechPart builtIn = this.integrated(mount);
		if (builtIn != null) {
			return builtIn;
		}
		MechPart part = PartRegistry.getOrNull(this.parts.get(mount));
		return part != null && mount.accepts(part) ? part : null;
	}

	/** The weapon the fitted arms carry built in at this arm mount (special-standard arms), or null. */
	public MechPart integrated(MountSlot mount) {
		if (!mount.isArmWeapon()) {
			return null;
		}
		MechPart arms = PartRegistry.getOrNull(this.parts.get(MountSlot.ARMS));
		if (arms == null || arms.slot() != PartSlot.ARMS) {
			return null;
		}
		Identifier id = arms.arms().integrated().get(mount);
		MechPart weapon = id == null ? null : PartRegistry.getOrNull(id);
		return weapon != null && weapon.slot() == PartSlot.WEAPON ? weapon : null;
	}

	/** Whether this mount is taken by a weapon built into the arms (nothing can be fitted there). */
	public boolean isLocked(MountSlot mount) {
		if (!mount.isArmWeapon()) {
			return false;
		}
		MechPart arms = PartRegistry.getOrNull(this.parts.get(MountSlot.ARMS));
		return arms != null && arms.slot() == PartSlot.ARMS && arms.arms().integrated().containsKey(mount);
	}

	public boolean isStock(MountSlot mount) {
		return (this.stockMask & (1 << mount.ordinal())) != 0;
	}

	public boolean isEmpty() {
		return this.parts.isEmpty();
	}

	/** True once every required mount holds a part that is actually loaded. */
	public boolean isComplete() {
		for (MountSlot mount : MountSlot.values()) {
			if (mount.required() && this.part(mount) == null) {
				return false;
			}
		}
		return true;
	}

	public Assembly with(MountSlot mount, Identifier partId, boolean stock) {
		EnumMap<MountSlot, Identifier> copy = new EnumMap<>(MountSlot.class);
		copy.putAll(this.parts);
		int mask = this.stockMask & ~(1 << mount.ordinal());
		if (partId == null) {
			copy.remove(mount);
		} else {
			copy.put(mount, partId);
			if (stock) {
				mask |= 1 << mount.ordinal();
			}
		}
		return new Assembly(copy, mask);
	}

	/** The transform set every transform-relevant part shares, or null if they don't all share one. */
	public String transformSet() {
		String set = null;
		for (MountSlot mount : new MountSlot[]{MountSlot.HEAD, MountSlot.CORE, MountSlot.ARMS, MountSlot.LEGS, MountSlot.BOOSTER}) {
			MechPart part = this.part(mount);
			if (part == null || part.transformSet().isEmpty()) {
				return null;
			}
			if (set == null) {
				set = part.transformSet().get();
			} else if (!set.equals(part.transformSet().get())) {
				return null;
			}
		}
		MechPart core = this.part(MountSlot.CORE);
		return core != null && core.core().jet().isPresent() ? set : null;
	}

	public boolean canTransform() {
		return this.transformSet() != null;
	}

	public String encode() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<MountSlot, Identifier> e : this.parts.entrySet()) {
			if (!sb.isEmpty()) {
				sb.append(';');
			}
			sb.append(e.getKey().id()).append('=').append(e.getValue());
		}
		sb.append('|').append(Integer.toHexString(this.stockMask));
		return sb.toString();
	}

	public static Assembly decode(String text) {
		if (text == null || text.isEmpty()) {
			return EMPTY;
		}
		int bar = text.lastIndexOf('|');
		String body = bar >= 0 ? text.substring(0, bar) : text;
		int mask = 0;
		if (bar >= 0) {
			try {
				mask = Integer.parseUnsignedInt(text.substring(bar + 1), 16);
			} catch (NumberFormatException ignored) {
				mask = 0;
			}
		}
		EnumMap<MountSlot, Identifier> map = new EnumMap<>(MountSlot.class);
		for (String entry : body.split(";")) {
			int eq = entry.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			MountSlot mount = MountSlot.byId(entry.substring(0, eq));
			Identifier id = Identifier.tryParse(entry.substring(eq + 1));
			if (mount != null && id != null) {
				map.put(mount, id);
			}
		}
		return new Assembly(map, mask);
	}
}
