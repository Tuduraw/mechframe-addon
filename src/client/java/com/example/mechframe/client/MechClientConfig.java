package com.example.mechframe.client;

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

/** config/mechframe-client.json - per-player control preferences. Written with the defaults on
 * first start so the options are discoverable. */
public final class MechClientConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("mechframe");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private MechClientConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("mechframe-client.json");
		JsonObject json = new JsonObject();
		try {
			if (Files.exists(path)) {
				json = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("[mechframe] could not read {}: {}", path, e.toString());
		}
		if (json == null) {
			json = new JsonObject();
		}
		// Double-tapping a direction key also fires a quick boost (the X key always does).
		if (json.has("double_tap_quick_boost")) {
			MechInput.doubleTapQuickBoost = json.get("double_tap_quick_boost").getAsBoolean();
		} else {
			json.addProperty("double_tap_quick_boost", MechInput.doubleTapQuickBoost);
		}
		// Boost key: switch boost on/off (true) or boost only while held (false).
		if (json.has("boost_toggle")) {
			MechInput.boostToggle = json.get("boost_toggle").getAsBoolean();
		} else {
			json.addProperty("boost_toggle", MechInput.boostToggle);
		}
		// Tank legs: A/D turn the hull and W/S drive along it, the upper body aims like a turret.
		if (json.has("tank_controls")) {
			MechInput.tankControls = json.get("tank_controls").getAsBoolean();
		} else {
			json.addProperty("tank_controls", MechInput.tankControls);
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("[mechframe] could not write {}: {}", path, e.toString());
		}
	}
}
