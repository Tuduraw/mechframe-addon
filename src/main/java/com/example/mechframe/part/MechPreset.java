package com.example.mechframe.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/** The "mechframe" object of a vehicle JSON: the stock assembly a freshly spawned frame comes with.
 *
 * <pre>
 * "mechframe": {
 *   "assembly": { "head": "mechframe:hd_owl", "core": "...", "arms": "...", "legs": "...",
 *                 "booster": "...", "r_arm": "...", "l_arm": "...", "r_back": "...", "l_back": "..." }
 * }
 * </pre> */
public record MechPreset(Identifier vehicleId, Assembly assembly) {

	public static MechPreset parse(Identifier vehicleId, JsonObject json) {
		EnumMap<MountSlot, Identifier> map = new EnumMap<>(MountSlot.class);
		if (json.has("assembly")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("assembly").entrySet()) {
				MountSlot mount = MountSlot.byId(e.getKey());
				if (mount != null && e.getValue().isJsonPrimitive() && !e.getValue().getAsString().isEmpty()) {
					map.put(mount, Identifier.of(e.getValue().getAsString()));
				}
			}
		}
		// Every part of a preset counts as "stock" for that frame - see Assembly's own doc.
		int stock = 0;
		for (MountSlot mount : map.keySet()) {
			stock |= 1 << mount.ordinal();
		}
		return new MechPreset(vehicleId, new Assembly(map, stock));
	}
}
