package com.example.mechframe.part;

import java.util.Locale;

/** The kind of a part - what it is, independent of where on the frame it is mounted. A weapon part
 * can go into several mount slots (see MountSlot); every other kind has exactly one. */
public enum PartSlot {
	HEAD,
	CORE,
	ARMS,
	LEGS,
	BOOSTER,
	WEAPON;

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static PartSlot byId(String id) {
		for (PartSlot slot : values()) {
			if (slot.id().equalsIgnoreCase(id)) {
				return slot;
			}
		}
		return null;
	}
}
