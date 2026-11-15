package com.example.mechframe.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Whether, and how densely, the world generates Mech Frame's bases (outposts and forts: the structure set
 * data/mechframe/worldgen/structure_set/outposts.json). Set from the server config; read by
 * StructurePlacementMixin / OutpostPlacementMixin, which recognise the set's placement by its salt.
 * Frequency is a density multiplier (2 = about twice as many bases, 0.5 = about half): the set's spacing and
 * separation (in chunks) are divided by its square root. It only affects chunks generated from then on, so
 * set it before creating a world (changing it later moves the grid, and bases may be cut at the border with
 * already generated terrain). */
public final class OutpostGeneration {
	private static final Logger LOGGER = LoggerFactory.getLogger("MechFrame/Structures");

	/** salt of structure_set/outposts.json. */
	public static final int SALT = 73190526;

	private static boolean enabled = true;
	private static double scale = 1.0;

	private OutpostGeneration() {
	}

	/** The placement's salt (added to StructurePlacement by StructurePlacementMixin). */
	public interface Salted {
		int mechframe$salt();
	}

	public static void configure(boolean on, double frequency, String source) {
		enabled = on && frequency > 0.0;
		scale = enabled ? 1.0 / Math.sqrt(frequency) : 1.0;
		if (!enabled) {
			LOGGER.info("Mech Frame bases: not generated ({})", source);
		} else {
			LOGGER.info("Mech Frame bases: frequency x{} - spacing {} chunks, separation {} ({})", frequency,
					spacing(34), separation(34, 12), source);
		}
	}

	public static boolean enabled() {
		return enabled;
	}

	public static int spacing(int base) {
		return Math.max(2, (int) Math.round(base * scale));
	}

	/** The separation must stay below the spacing. */
	public static int separation(int baseSpacing, int baseSeparation) {
		return Math.max(0, Math.min(spacing(baseSpacing) - 1, (int) Math.round(baseSeparation * scale)));
	}
}
