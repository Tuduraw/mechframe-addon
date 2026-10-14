package com.example.mechframe.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/mechframe-server.json - the defence AI's server-side rules. Written with the defaults on
 * first start so every option is discoverable; read once at startup. */
public final class MechServerConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("MechFrame/Config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Weapons of AI frames (and the wreck of one) break blocks / start fires. */
	public static boolean aiBlockDamage = false;
	/** Chance per part (head, core, arms, legs, booster, each weapon) that a destroyed enemy-mode
	 * frame drops that part. Each part is rolled on its own. */
	public static double partDropChance = 0.08;
	/** Minutes after an enemy unit is destroyed (or lost) before its command post deploys a new one. */
	public static double respawnMinutes = 10.0;
	/** Enemy-mode frames also attack players in creative mode (normally only for testing). */
	public static boolean targetCreativePlayers = false;

	private MechServerConfig() {
	}

	public static int respawnTicks() {
		return (int) Math.max(20, Math.round(respawnMinutes * 1200.0));
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("mechframe-server.json");
		JsonObject json = null;
		try {
			if (Files.exists(path)) {
				json = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read {}: {}", path, e.toString());
		}
		if (json == null) {
			json = new JsonObject();
		}
		aiBlockDamage = bool(json, "ai_block_damage", aiBlockDamage);
		partDropChance = Math.max(0.0, Math.min(1.0, num(json, "part_drop_chance", partDropChance)));
		respawnMinutes = Math.max(0.0, num(json, "respawn_minutes", respawnMinutes));
		targetCreativePlayers = bool(json, "target_creative_players", targetCreativePlayers);
		// Test hooks (dev client): -Dmechframe.respawnMinutes=0.1 -Dmechframe.dropChance=1
		String rm = System.getProperty("mechframe.respawnMinutes");
		if (rm != null) {
			respawnMinutes = Double.parseDouble(rm);
		}
		String dc = System.getProperty("mechframe.dropChance");
		if (dc != null) {
			partDropChance = Double.parseDouble(dc);
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}: {}", path, e.toString());
		}
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		if (json.has(key) && json.get(key).isJsonPrimitive()) {
			return json.get(key).getAsBoolean();
		}
		json.addProperty(key, fallback);
		return fallback;
	}

	private static double num(JsonObject json, String key, double fallback) {
		if (json.has(key) && json.get(key).isJsonPrimitive()) {
			return json.get(key).getAsDouble();
		}
		json.addProperty(key, fallback);
		return fallback;
	}
}
