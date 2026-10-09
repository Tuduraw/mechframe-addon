package com.example.mechframe.asm;

import com.example.tudursvehiclemod.asset.AddonPaths;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Raw OBJ geometry (positions only, per "o"/"g" group, fan-triangulated) read straight from the
 * mod jars and the tudursvehiclemod-addons/ folder - the same lookup order the base mod's own
 * ServerObjModelHitboxes uses - so both the dedicated server (which has no client resources) and
 * the client can build assembled hit meshes from part models.
 *
 * <p>Triangles are float[9] (a, b, c). Cached per model id; cleared on data reload. */
public final class ObjSource {

	private static final Logger LOGGER = LoggerFactory.getLogger("MechFrame/ObjSource");
	private static final Map<Identifier, Optional<Map<String, List<float[]>>>> CACHE = new ConcurrentHashMap<>();

	private ObjSource() {
	}

	public static void clear() {
		CACHE.clear();
	}

	/** Grouped triangles of one model, or empty if it can't be found or read. */
	public static Map<String, List<float[]>> groups(Identifier modelId) {
		return CACHE.computeIfAbsent(modelId, ObjSource::load).orElse(Map.of());
	}

	private static Optional<Map<String, List<float[]>>> load(Identifier modelId) {
		Path file = find("assets/" + modelId.getNamespace() + "/" + modelId.getPath());
		if (file == null) {
			LOGGER.warn("Part model {} not found - it will have no hit geometry", modelId);
			return Optional.empty();
		}
		try {
			return Optional.of(parse(Files.readAllLines(file)));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Failed to read part model {}: {}", modelId, e.getMessage());
			return Optional.empty();
		}
	}

	private static Path find(String relative) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path candidate = addonDir.resolve(relative);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		for (var mod : FabricLoader.getInstance().getAllMods()) {
			Optional<Path> found = mod.findPath(relative);
			if (found.isPresent() && Files.isRegularFile(found.get())) {
				return found.get();
			}
		}
		return null;
	}

	static Map<String, List<float[]>> parse(List<String> lines) {
		List<float[]> vertices = new ArrayList<>();
		Map<String, List<float[]>> groups = new LinkedHashMap<>();
		String current = "";
		for (String raw : lines) {
			String line = raw.strip();
			if (line.startsWith("v ")) {
				String[] p = line.split("\\s+");
				vertices.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
			} else if (line.startsWith("o ") || line.startsWith("g ")) {
				String[] p = line.split("\\s+", 2);
				current = p.length > 1 ? p[1].strip() : "";
			} else if (line.startsWith("f ")) {
				String[] p = line.split("\\s+");
				List<float[]> face = new ArrayList<>();
				for (int i = 1; i < p.length; i++) {
					String idx = p[i].split("/")[0];
					if (idx.isEmpty()) {
						continue;
					}
					int index = Integer.parseInt(idx);
					int resolved = index > 0 ? index - 1 : vertices.size() + index;
					if (resolved >= 0 && resolved < vertices.size()) {
						face.add(vertices.get(resolved));
					}
				}
				List<float[]> tris = groups.computeIfAbsent(current, k -> new ArrayList<>());
				for (int i = 1; i + 1 < face.size(); i++) {
					float[] a = face.get(0);
					float[] b = face.get(i);
					float[] c = face.get(i + 1);
					tris.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]});
				}
			}
		}
		return groups;
	}
}
