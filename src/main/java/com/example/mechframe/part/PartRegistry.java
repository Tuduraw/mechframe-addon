package com.example.mechframe.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every loaded part and every mech preset. Filled by MechDataLoader on the server (and therefore
 * also on the integrated server's client in single player) and by MechDataSyncPayload on a client
 * connected to a dedicated server - the raw JSON is kept so it can be sent as-is.
 *
 * <p>generation() changes on every reload so caches built from part data (layouts, definitions, hit
 * meshes) know when to rebuild. */
public final class PartRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger("MechFrame/Parts");

	private static Map<Identifier, MechPart> parts = Map.of();
	private static Map<Identifier, MechPreset> presets = Map.of();
	private static Map<Identifier, String> rawParts = Map.of();
	private static Map<Identifier, String> rawPresets = Map.of();
	private static int generation;

	private PartRegistry() {
	}

	public static Optional<MechPart> get(Identifier id) {
		return id == null ? Optional.empty() : Optional.ofNullable(parts.get(id));
	}

	public static MechPart getOrNull(Identifier id) {
		return id == null ? null : parts.get(id);
	}

	public static Collection<MechPart> all() {
		return parts.values();
	}

	/** Parts that fit the given mount, sorted by tier then name. */
	public static List<MechPart> forMount(MountSlot mount) {
		List<MechPart> list = new ArrayList<>();
		for (MechPart part : parts.values()) {
			if (mount.accepts(part)) {
				list.add(part);
			}
		}
		list.sort(Comparator.comparingInt(MechPart::tier).thenComparing(MechPart::displayName));
		return list;
	}

	public static Optional<MechPreset> preset(Identifier vehicleId) {
		return vehicleId == null ? Optional.empty() : Optional.ofNullable(presets.get(vehicleId));
	}

	public static Map<Identifier, String> rawParts() {
		return rawParts;
	}

	public static Map<Identifier, String> rawPresets() {
		return rawPresets;
	}

	public static int generation() {
		return generation;
	}

	/** Replaces everything from raw JSON text (both the loader and the client sync end up here). */
	public static synchronized void load(Map<Identifier, String> partJson, Map<Identifier, String> presetJson) {
		Map<Identifier, MechPart> parsed = new LinkedHashMap<>();
		for (Map.Entry<Identifier, String> entry : partJson.entrySet()) {
			try {
				JsonObject obj = JsonParser.parseString(entry.getValue()).getAsJsonObject();
				parsed.put(entry.getKey(), PartParser.parse(entry.getKey(), obj));
			} catch (Exception e) {
				LOGGER.error("Failed to parse mech part {}: {}", entry.getKey(), e.getMessage());
			}
		}
		Map<Identifier, MechPreset> parsedPresets = new HashMap<>();
		for (Map.Entry<Identifier, String> entry : presetJson.entrySet()) {
			try {
				JsonElement el = JsonParser.parseString(entry.getValue());
				parsedPresets.put(entry.getKey(), MechPreset.parse(entry.getKey(), el.getAsJsonObject()));
			} catch (Exception e) {
				LOGGER.error("Failed to parse mech preset {}: {}", entry.getKey(), e.getMessage());
			}
		}
		parts = Map.copyOf(parsed);
		presets = Map.copyOf(parsedPresets);
		rawParts = Map.copyOf(partJson);
		rawPresets = Map.copyOf(presetJson);
		generation++;
		LOGGER.info("Loaded {} mech part(s) and {} preset(s)", parts.size(), presets.size());
	}
}
