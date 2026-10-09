package com.example.mechframe.asm;

import com.example.mechframe.MechFrame;
import com.example.mechframe.mixin.ServerObjModelHitboxesAccessor;
import com.example.mechframe.part.MechForm;
import com.example.mechframe.part.MechLayout;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Builds the hit-detection geometry of an assembled frame and hands it to the base mod.
 *
 * <p>Every part's OBJ is placed with the same MechLayout matrices the renderer starts from, at rest
 * (legs standing, arms forward). Groups:
 * <ul>
 *   <li>"body" - legs, core, head, booster: tested at the body's own transform;</li>
 *   <li>"$w_r_arm" / "$w_l_arm" - one arm plus the weapon in its hand;</li>
 *   <li>"$w_r_back" / "$w_l_back" - a back weapon.</li>
 * </ul>
 * The "$w_..." groups are the frame's weapon parts, so the base mod tests them at their CURRENT aim
 * rotation (AbstractVehicleEntity.tudursvehiclemod$isNearAnimatedParts()). The jet form has no
 * weapon parts and puts everything into "body". */
public final class HitMeshBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger("MechFrame/HitMesh");
	private static final Set<Identifier> INJECTED = ConcurrentHashMap.newKeySet();

	private HitMeshBuilder() {
	}

	/** The model id the definition of this layout/form refers to. Includes the registry generation,
	 * so a /reload that changes part geometry gets a fresh mesh instead of the cached one. */
	public static Identifier modelId(MechLayout layout, MechForm form) {
		// The parts only - which of them are stock doesn't change the geometry.
		String key = layout.assembly.parts() + "#" + form.name() + "#" + PartRegistry.generation();
		String hash = Integer.toHexString(key.hashCode()) + Integer.toHexString(key.length());
		return Identifier.of(MechFrame.MOD_ID, "models/obj/asm/" + hash + ".obj");
	}

	/** Makes sure the base mod's cache holds this frame's mesh (idempotent, cheap after the first call). */
	public static Identifier ensure(MechLayout layout, MechForm form) {
		Identifier id = modelId(layout, form);
		if (INJECTED.contains(id)) {
			return id;
		}
		Map<String, List<float[]>> groups;
		try {
			groups = form == MechForm.JET && layout.canTransform ? buildJet(layout) : buildRobot(layout);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to build hit mesh for {}", layout.assembly.encode(), e);
			groups = Map.of();
		}
		if (!groups.isEmpty()) {
			ServerObjModelHitboxesAccessor.mechframe$groupedCache().put(id, Optional.of(groups));
		}
		INJECTED.add(id);
		return id;
	}

	private static Map<String, List<float[]>> buildRobot(MechLayout layout) {
		Map<String, List<float[]>> out = new LinkedHashMap<>();
		List<float[]> body = new ArrayList<>();
		addAll(body, layout.assembly.part(MountSlot.LEGS), layout.robot.get(MechLayout.Place.LEGS), null);
		addAll(body, layout.assembly.part(MountSlot.CORE), layout.robot.get(MechLayout.Place.CORE), null);
		addAll(body, layout.assembly.part(MountSlot.HEAD), layout.robot.get(MechLayout.Place.HEAD), null);
		addAll(body, layout.assembly.part(MountSlot.BOOSTER), layout.robot.get(MechLayout.Place.BOOSTER), null);
		out.put("body", body);

		MechPart arms = layout.assembly.part(MountSlot.ARMS);
		List<float[]> left = new ArrayList<>();
		List<float[]> right = new ArrayList<>();
		addAll(left, arms, layout.robot.get(MechLayout.Place.ARM_L), "l_");
		addAll(right, arms, layout.robot.get(MechLayout.Place.ARM_R), "r_");
		addAll(left, layout.assembly.part(MountSlot.L_ARM), layout.robotWeapon.get(MountSlot.L_ARM), null);
		addAll(right, layout.assembly.part(MountSlot.R_ARM), layout.robotWeapon.get(MountSlot.R_ARM), null);
		out.put(MountSlot.L_ARM.weaponPartName(), left);
		out.put(MountSlot.R_ARM.weaponPartName(), right);
		for (MountSlot mount : new MountSlot[]{MountSlot.R_BACK, MountSlot.L_BACK}) {
			MechPart weapon = layout.assembly.part(mount);
			if (weapon != null) {
				List<float[]> list = new ArrayList<>();
				addAll(list, weapon, layout.robotWeapon.get(mount), null);
				out.put(mount.weaponPartName(), list);
			}
		}
		out.values().removeIf(List::isEmpty);
		return out;
	}

	private static Map<String, List<float[]>> buildJet(MechLayout layout) {
		List<float[]> body = new ArrayList<>();
		for (MountSlot slot : new MountSlot[]{MountSlot.HEAD, MountSlot.CORE, MountSlot.ARMS, MountSlot.LEGS, MountSlot.BOOSTER}) {
			MechPart part = layout.assembly.part(slot);
			if (part == null) {
				continue;
			}
			for (Map.Entry<String, List<float[]>> group : ObjSource.groups(part.model()).entrySet()) {
				transform(body, group.getValue(), layout.jetMatrix(slot, group.getKey()));
			}
		}
		for (MountSlot mount : new MountSlot[]{MountSlot.R_ARM, MountSlot.L_ARM, MountSlot.R_BACK, MountSlot.L_BACK}) {
			addAll(body, layout.assembly.part(mount), layout.jetWeapon.get(mount), null);
		}
		Map<String, List<float[]>> out = new LinkedHashMap<>();
		if (!body.isEmpty()) {
			out.put("body", body);
		}
		return out;
	}

	/** Adds every group of part (only those starting with prefix, if given) placed by matrix. */
	private static void addAll(List<float[]> out, MechPart part, Matrix4f matrix, String prefix) {
		if (part == null || matrix == null) {
			return;
		}
		for (Map.Entry<String, List<float[]>> group : ObjSource.groups(part.model()).entrySet()) {
			if (prefix != null && !group.getKey().startsWith(prefix)) {
				continue;
			}
			transform(out, group.getValue(), matrix);
		}
	}

	private static void transform(List<float[]> out, List<float[]> tris, Matrix4f m) {
		Vector3f v = new Vector3f();
		for (float[] t : tris) {
			float[] r = new float[9];
			for (int i = 0; i < 3; i++) {
				m.transformPosition(v.set(t[i * 3], t[i * 3 + 1], t[i * 3 + 2]));
				r[i * 3] = v.x;
				r[i * 3 + 1] = v.y;
				r[i * 3 + 2] = v.z;
			}
			out.add(r);
		}
	}
}
