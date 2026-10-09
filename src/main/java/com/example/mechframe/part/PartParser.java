package com.example.mechframe.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JSON -> MechPart. Hand-written rather than a Codec: parts have many optional, slot-specific
 * blocks, and a missing or misspelled key should fall back to a sensible default with a clear log
 * line rather than rejecting the whole part. Unknown keys are ignored. */
public final class PartParser {

	private PartParser() {
	}

	public static MechPart parse(Identifier id, JsonObject json) {
		PartSlot slot = PartSlot.byId(str(json, "slot", ""));
		if (slot == null) {
			throw new IllegalArgumentException("unknown or missing \"slot\"");
		}
		String displayName = str(json, "display_name", id.getPath());
		String maker = str(json, "maker", "");
		int tier = (int) num(json, "tier", 1);
		Identifier model = Identifier.of(str(json, "model", id.getNamespace() + ":models/obj/parts/" + id.getPath() + ".obj"));
		Identifier texture = Identifier.of(str(json, "texture", id.getNamespace() + ":textures/part/" + id.getPath() + ".png"));
		float weight = (float) num(json, "weight", 5);
		float armor = (float) num(json, "armor", 100);
		Optional<String> transformSet = json.has("transform_set") ? Optional.of(json.get("transform_set").getAsString()) : Optional.empty();

		Map<String, Vector3f> points = new LinkedHashMap<>();
		if (json.has("points")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("points").entrySet()) {
				points.put(e.getKey(), vec(e.getValue()));
			}
		}

		MechPart.HeadSpec head = MechPart.HeadSpec.DEFAULT;
		MechPart.CoreSpec core = MechPart.CoreSpec.DEFAULT;
		MechPart.ArmSpec arms = MechPart.ArmSpec.DEFAULT;
		MechPart.LegSpec legs = null;
		MechPart.BoosterSpec booster = MechPart.BoosterSpec.DEFAULT;
		MechPart.WeaponSpec weapon = null;

		JsonObject stats = json.has("stats") ? json.getAsJsonObject("stats") : new JsonObject();
		switch (slot) {
			case HEAD -> head = new MechPart.HeadSpec(
					(float) num(stats, "lock_range", 110), (float) num(stats, "lock_cone", 14),
					(float) num(stats, "stability", 1));
			case CORE -> {
				Optional<MechPart.JetSpec> jet = Optional.empty();
				if (stats.has("jet")) {
					JsonObject j = stats.getAsJsonObject("jet");
					jet = Optional.of(new MechPart.JetSpec(
							(float) num(j, "max_speed", 3.0), (float) num(j, "acceleration", 0.05),
							(float) num(j, "turn_speed", 2.2), (float) num(j, "stall_fraction", 0.25),
							(float) num(j, "afterburner", 1.4), (float) num(j, "afterburner_cost", 10),
							(float) num(j, "collision_height", 1.6)));
				}
				core = new MechPart.CoreSpec(
						(float) num(stats, "en_capacity", 1500), (float) num(stats, "en_recharge", 30),
						(int) num(stats, "recharge_delay", 12), (float) num(stats, "defense", 1),
						(float) num(stats, "fuel", 800), (float) num(stats, "stability", 1), jet);
			}
			case ARMS -> {
				java.util.Map<MountSlot, Identifier> integrated = new java.util.EnumMap<>(MountSlot.class);
				if (stats.has("integrated")) {
					for (Map.Entry<String, JsonElement> e : stats.getAsJsonObject("integrated").entrySet()) {
						MountSlot m = MountSlot.byId(e.getKey());
						if (m != null && m.isArmWeapon()) {
							integrated.put(m, Identifier.of(e.getValue().getAsString()));
						}
					}
				}
				arms = new MechPart.ArmSpec(
						(float) num(stats, "load_limit", 20), (float) num(stats, "aim_speed", 360),
						(float) num(stats, "recoil_control", 1), java.util.Map.copyOf(integrated));
			}
			case LEGS -> legs = parseLegs(stats);
			case BOOSTER -> {
				List<MechPart.Nozzle> nozzles = nozzles(stats, "nozzles");
				List<MechPart.Nozzle> jetNozzles = nozzles(stats, "jet_nozzles");
				booster = new MechPart.BoosterSpec(
						(float) num(stats, "boost_speed", 0.9), (float) num(stats, "qb_speed", 2.4),
						(float) num(stats, "qb_cost", 320), (int) num(stats, "qb_cooldown", 10),
						(float) num(stats, "hover_thrust", 0.16), (float) num(stats, "hover_cost", 14),
						(float) num(stats, "air_boost_cost", 6), List.copyOf(nozzles), List.copyOf(jetNozzles));
			}
			case WEAPON -> weapon = new MechPart.WeaponSpec(
					str(stats, "weapon", id.getPath()),
					MountSlot.WeaponMount.byId(str(stats, "mount", "any")),
					Identifier.of(str(stats, "projectile_item", "minecraft:iron_nugget")),
					(float) num(stats, "recoil_distance", 0.1),
					(float) num(stats, "min_yaw", -75), (float) num(stats, "max_yaw", 75),
					(float) num(stats, "min_pitch", -80), (float) num(stats, "max_pitch", 60),
					bool(stats, "fcs", true),
					stats.has("melee") ? melee(stats.getAsJsonObject("melee")) : null);
		}
		if (slot == PartSlot.LEGS && legs == null) {
			throw new IllegalArgumentException("legs part without stats");
		}

		Map<String, MechPart.JetPose> jet = new LinkedHashMap<>();
		if (json.has("jet_pose")) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("jet_pose").entrySet()) {
				JsonObject p = e.getValue().getAsJsonObject();
				Optional<org.joml.Quaternionf> quat = Optional.empty();
				if (p.has("quat")) {
					JsonArray q = p.getAsJsonArray("quat");
					quat = Optional.of(new org.joml.Quaternionf(q.get(0).getAsFloat(), q.get(1).getAsFloat(),
							q.get(2).getAsFloat(), q.get(3).getAsFloat()).normalize());
				}
				jet.put(e.getKey(), new MechPart.JetPose(vec(p.get("pos")),
						p.has("rot") ? vec(p.get("rot")) : new Vector3f(), quat, (float) num(p, "delay", 0),
						p.has("parent") ? Optional.of(p.get("parent").getAsString()) : Optional.empty(),
						p.has("joint") ? vec(p.get("joint")) : new Vector3f()));
			}
		}

		return new MechPart(id, slot, displayName, maker, tier, model, texture, weight, armor, transformSet,
				Map.copyOf(points), head, core, arms, legs, booster, weapon, Map.copyOf(jet));
	}

	private static MechPart.MeleeSpec melee(JsonObject m) {
		return new MechPart.MeleeSpec((float) num(m, "damage", 300), (float) num(m, "reach", 4.5),
				(float) num(m, "arc", 70), (float) num(m, "lunge_speed", 1.5), (float) num(m, "lunge_range", 11),
				(float) num(m, "en_cost", 300), (int) num(m, "cooldown", 28),
				"thrust".equalsIgnoreCase(str(m, "style", "slash")));
	}

	private static MechPart.LegSpec parseLegs(JsonObject s) {
		MechPart.LegType type = MechPart.LegType.byId(str(s, "type", "biped"));
		List<String> pelvis = new ArrayList<>();
		if (s.has("pelvis_groups")) {
			for (JsonElement e : s.getAsJsonArray("pelvis_groups")) {
				pelvis.add(e.getAsString());
			}
		}
		List<MechPart.LegChain> chains = new ArrayList<>();
		if (s.has("chains")) {
			for (JsonElement e : s.getAsJsonArray("chains")) {
				JsonObject c = e.getAsJsonObject();
				chains.add(new MechPart.LegChain(
						str(c, "name", "leg" + chains.size()),
						c.has("coxa") ? Optional.of(c.get("coxa").getAsString()) : Optional.empty(),
						c.has("coxa_pivot") ? vec(c.get("coxa_pivot")) : new Vector3f(),
						str(c, "upper", ""), str(c, "lower", ""),
						c.has("foot") ? Optional.of(c.get("foot").getAsString()) : Optional.empty(),
						vec(c.get("hip")), vec(c.get("knee")), vec(c.get("ankle")),
						c.has("pole") ? vec(c.get("pole")) : new Vector3f(0, 0, 1),
						(int) num(c, "group", 0), (float) num(c, "phase", 0),
						c.has("mid") && c.has("knee2") ? Optional.of(c.get("mid").getAsString()) : Optional.empty(),
						c.has("knee2") ? vec(c.get("knee2")) : vec(c.get("knee"))));
			}
		}
		List<MechPart.Wheel> wheels = new ArrayList<>();
		if (s.has("wheels")) {
			for (JsonElement e : s.getAsJsonArray("wheels")) {
				JsonObject w = e.getAsJsonObject();
				wheels.add(new MechPart.Wheel(str(w, "group", ""), vec(w.get("pivot")), (float) num(w, "radius", 0.4)));
			}
		}
		boolean tank = type.hull();
		return new MechPart.LegSpec(type,
				(float) num(s, "load_limit", 60),
				(float) num(s, "walk_speed", tank ? 0.42 : 0.3),
				(float) num(s, "boost_multiplier", 1.0),
				(float) num(s, "jump", tank ? 0 : 0.95),
				(float) num(s, "turn_rate", 14),
				(float) num(s, "step_height", 1.2),
				(float) num(s, "traction", 0.35),
				(float) num(s, "qb_multiplier", 1.0),
				(float) num(s, "hover_multiplier", 1.0),
				(float) num(s, "collision_width", 1.8),
				(float) num(s, "step_length", 1.4),
				(float) num(s, "step_lift", 0.5),
				(int) num(s, "step_ticks", 6),
				(float) num(s, "core_tilt", 0),
				List.copyOf(pelvis), List.copyOf(chains), List.copyOf(wheels));
	}

	private static List<MechPart.Nozzle> nozzles(JsonObject stats, String key) {
		List<MechPart.Nozzle> list = new ArrayList<>();
		if (stats.has(key)) {
			for (JsonElement e : stats.getAsJsonArray(key)) {
				JsonObject n = e.getAsJsonObject();
				list.add(new MechPart.Nozzle(vec(n.get("pos")),
						n.has("dir") ? vec(n.get("dir")) : new Vector3f(0, 0, -1), (float) num(n, "size", 0.3)));
			}
		}
		return list;
	}

	// ------------------------------------------------------------------------------------------

	static Vector3f vec(JsonElement e) {
		if (e == null || !e.isJsonArray()) {
			return new Vector3f();
		}
		JsonArray a = e.getAsJsonArray();
		return new Vector3f(a.size() > 0 ? a.get(0).getAsFloat() : 0f,
				a.size() > 1 ? a.get(1).getAsFloat() : 0f,
				a.size() > 2 ? a.get(2).getAsFloat() : 0f);
	}

	static String str(JsonObject o, String key, String fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}

	static double num(JsonObject o, String key, double fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : fallback;
	}

	static boolean bool(JsonObject o, String key, boolean fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
	}
}
