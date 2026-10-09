package com.example.mechframe.part;

import java.util.Locale;

/** A place on the frame that holds exactly one part. The four weapon mounts each map to their own
 * (virtual, never-occupied) seat index so the base mod aims and fires them independently - the
 * same pattern humanoidrobotaddon uses (pilot_usable weapons on empty seats, pilot_fallback parts). */
public enum MountSlot {
	HEAD(PartSlot.HEAD, -1, false),
	CORE(PartSlot.CORE, -1, true),
	ARMS(PartSlot.ARMS, -1, true),
	LEGS(PartSlot.LEGS, -1, true),
	BOOSTER(PartSlot.BOOSTER, -1, false),
	R_ARM(PartSlot.WEAPON, 1, false),
	L_ARM(PartSlot.WEAPON, 2, false),
	R_BACK(PartSlot.WEAPON, 3, false),
	L_BACK(PartSlot.WEAPON, 4, false);

	private final PartSlot partSlot;
	private final int seatIndex;
	private final boolean required;

	MountSlot(PartSlot partSlot, int seatIndex, boolean required) {
		this.partSlot = partSlot;
		this.seatIndex = seatIndex;
		this.required = required;
	}

	public PartSlot partSlot() {
		return this.partSlot;
	}

	/** Seat index the weapon in this mount is bound to, or -1 for a non-weapon mount. */
	public int seatIndex() {
		return this.seatIndex;
	}

	/** A frame cannot be assembled without one of these. */
	public boolean required() {
		return this.required;
	}

	public boolean isWeapon() {
		return this.partSlot == PartSlot.WEAPON;
	}

	public boolean isArmWeapon() {
		return this == R_ARM || this == L_ARM;
	}

	public boolean isBackWeapon() {
		return this == R_BACK || this == L_BACK;
	}

	/** +1 for the left side (model +X), -1 for the right, 0 for centred mounts. */
	public int side() {
		return switch (this) {
			case L_ARM, L_BACK -> 1;
			case R_ARM, R_BACK -> -1;
			default -> 0;
		};
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	/** Name of the base mod weapon part this mount drives ("$w_r_arm" etc.). */
	public String weaponPartName() {
		return "$w_" + this.id();
	}

	/** Separator of a per-mount weapon alias ("mf_burst_rifle#l_arm"); see {@link #weaponAlias}. */
	public static final char ALIAS_SEPARATOR = '#';

	/** The base mod pairs a weapon with its turret part, recoil and fire gating by weapon NAME, so
	 * the same rifle in both hands would make the left gun fire along the right arm's aim. Every
	 * mount therefore registers its weapon under its own alias; WeaponStatsLoaderMixin strips the
	 * suffix again when the stats are looked up, so both still read the one weapon file. */
	public String weaponAlias(String weaponName) {
		return weaponName + ALIAS_SEPARATOR + this.id();
	}

	public static MountSlot byId(String id) {
		for (MountSlot slot : values()) {
			if (slot.id().equalsIgnoreCase(id)) {
				return slot;
			}
		}
		return null;
	}

	public static MountSlot bySeat(int seatIndex) {
		for (MountSlot slot : values()) {
			if (slot.seatIndex == seatIndex && seatIndex >= 0) {
				return slot;
			}
		}
		return null;
	}

	public boolean accepts(MechPart part) {
		if (part == null || part.slot() != this.partSlot) {
			return false;
		}
		if (!this.isWeapon()) {
			return true;
		}
		WeaponMount mount = part.weapon().mount();
		if (mount == WeaponMount.INTEGRATED) {
			return false; // only ever carried as part of arms that integrate it (Assembly.part)
		}
		return this.isArmWeapon() ? mount != WeaponMount.BACK : mount != WeaponMount.ARM;
	}

	/** Which mounts a weapon can be fitted to. INTEGRATED: none - it is built into an arms part. */
	public enum WeaponMount {
		ARM, BACK, ANY, INTEGRATED;

		public static WeaponMount byId(String id) {
			for (WeaponMount mount : values()) {
				if (mount.name().equalsIgnoreCase(id)) {
					return mount;
				}
			}
			return ANY;
		}
	}
}
