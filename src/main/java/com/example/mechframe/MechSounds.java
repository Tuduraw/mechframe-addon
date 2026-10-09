package com.example.mechframe;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Frame sound events (assets/mechframe/sounds.json). Weapon and engine sounds are not here - those
 * are played by the base mod from assets/mechframe/sounds/<name>.ogg directly. */
public final class MechSounds {

	public static final SoundEvent QUICK_BOOST = register("quick_boost");
	public static final SoundEvent LAND = register("land");
	public static final SoundEvent STEP = register("step");
	public static final SoundEvent TRANSFORM = register("transform");
	public static final SoundEvent BRAKE = register("brake");
	public static final SoundEvent JUMP = register("jump");

	private MechSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = Identifier.of(MechFrame.MOD_ID, name);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
	}
}
