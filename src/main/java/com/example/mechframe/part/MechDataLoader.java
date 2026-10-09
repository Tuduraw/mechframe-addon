package com.example.mechframe.part;

import com.example.mechframe.MechFrame;
import com.example.mechframe.asm.DefinitionFactory;
import com.example.mechframe.asm.ObjSource;
import com.example.mechframe.recipe.MechRecipes;
import com.example.tudursvehiclemod.asset.AddonPaths;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Server data reload: every data/<ns>/mech_parts/*.json, the "mechframe" object of every
 * data/<ns>/vehicles/*.json (the presets) and every data/<ns>/mech_recipe/*.json (Mech Fabricator
 * recipes; a part JSON may also carry its own under "recipe"), from data packs / mod jars and from the
 * tudursvehiclemod-addons/ folder (same layout the base mod reads). A part id is
 * {@code <ns>:<file name>}. After loading, every connected client is re-synced (MechNetworking). */
public final class MechDataLoader implements SimpleSynchronousResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("MechFrame/Data");
	private static final String PARTS = "mech_parts";
	private static final String VEHICLES = "vehicles";
	private static final String RECIPES = "mech_recipe";
	private static final String SUFFIX = ".json";
	private static final String PRESET_KEY = "mechframe";

	/** Called after every successful reload - set by MechNetworking to push the new data. */
	public static Runnable afterReload = () -> {
	};

	@Override
	public Identifier getFabricId() {
		return Identifier.of(MechFrame.MOD_ID, "mech_data");
	}

	@Override
	public void reload(ResourceManager manager) {
		Map<Identifier, String> parts = new HashMap<>();
		Map<Identifier, String> presets = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry : manager.findResources(PARTS, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			Identifier id = strip(entry.getKey(), PARTS);
			try (Reader reader = entry.getValue().getReader()) {
				parts.put(id, JsonParser.parseReader(reader).toString());
			} catch (Exception e) {
				LOGGER.error("Failed to read mech part {}", entry.getKey(), e);
			}
		}
		for (Map.Entry<Identifier, Resource> entry : manager.findResources(VEHICLES, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			try (Reader reader = entry.getValue().getReader()) {
				readPreset(strip(entry.getKey(), VEHICLES), JsonParser.parseReader(reader), presets);
			} catch (Exception e) {
				LOGGER.error("Failed to read vehicle {}", entry.getKey(), e);
			}
		}
		Map<Identifier, String[]> recipes = new HashMap<>();
		for (Map.Entry<Identifier, Resource> entry : manager.findResources(RECIPES, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {
			try (Reader reader = entry.getValue().getReader()) {
				recipes.put(strip(entry.getKey(), RECIPES), new String[]{"", JsonParser.parseReader(reader).toString()});
			} catch (Exception e) {
				LOGGER.error("Failed to read mech recipe {}", entry.getKey(), e);
			}
		}
		loadAddonsFolder(parts, presets, recipes);
		inlineRecipes(parts, recipes);
		ObjSource.clear();
		DefinitionFactory.clear();
		PartRegistry.load(parts, presets);
		MechRecipes.load(recipes);
		afterReload.run();
	}

	/** A part's own "recipe" object, unless a recipe file with the part's id already exists. */
	private static void inlineRecipes(Map<Identifier, String> parts, Map<Identifier, String[]> recipes) {
		for (Map.Entry<Identifier, String> e : parts.entrySet()) {
			try {
				JsonObject obj = JsonParser.parseString(e.getValue()).getAsJsonObject();
				if (obj.has("recipe") && obj.get("recipe").isJsonObject() && !recipes.containsKey(e.getKey())) {
					recipes.put(e.getKey(), new String[]{e.getKey().toString(), obj.get("recipe").toString()});
				}
			} catch (Exception ex) {
				LOGGER.error("Failed to read the recipe of mech part {}", e.getKey(), ex);
			}
		}
	}

	private static void readPreset(Identifier vehicleId, JsonElement json, Map<Identifier, String> presets) {
		if (json.isJsonObject()) {
			JsonObject obj = json.getAsJsonObject();
			if (obj.has(PRESET_KEY) && obj.get(PRESET_KEY).isJsonObject()) {
				presets.put(vehicleId, obj.get(PRESET_KEY).toString());
			}
		}
	}

	private static Identifier strip(Identifier fileId, String dir) {
		String path = fileId.getPath();
		return Identifier.of(fileId.getNamespace(), path.substring(dir.length() + 1, path.length() - SUFFIX.length()));
	}

	private static void loadAddonsFolder(Map<Identifier, String> parts, Map<Identifier, String> presets,
			Map<Identifier, String[]> recipes) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			for (Path nsDir : AddonPaths.listSubdirectories(addonDir.resolve("data"))) {
				String ns = nsDir.getFileName().toString();
				scan(nsDir.resolve(PARTS), ns, (id, text) -> parts.put(id, JsonParser.parseString(text).toString()));
				scan(nsDir.resolve(VEHICLES), ns, (id, text) -> readPreset(id, JsonParser.parseString(text), presets));
				scan(nsDir.resolve(RECIPES), ns, (id, text) -> recipes.put(id, new String[]{"", JsonParser.parseString(text).toString()}));
			}
		}
	}

	private interface Sink {
		void accept(Identifier id, String text);
	}

	private static void scan(Path dir, String ns, Sink sink) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var files = Files.walk(dir)) {
			for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(SUFFIX))::iterator) {
				String rel = dir.relativize(file).toString().replace('\\', '/');
				try {
					sink.accept(Identifier.of(ns, rel.substring(0, rel.length() - SUFFIX.length())),
							Files.readString(file, StandardCharsets.UTF_8));
				} catch (Exception e) {
					LOGGER.error("Failed to read {}", file, e);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to scan {}", dir, e);
		}
	}
}
