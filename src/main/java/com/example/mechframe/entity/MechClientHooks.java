package com.example.mechframe.entity;

/** Client-only behaviour the common entity code calls into (animation ticking, effects). Installed
 * by the client entry point; a no-op on a dedicated server. */
public interface MechClientHooks {

	int EVENT_QUICK_BOOST = 1;
	int EVENT_JUMP = 2;
	int EVENT_LAND = 3;
	int EVENT_TRANSFORM = 4;
	int EVENT_BRAKE = 5;
	int EVENT_JINK = 6;

	MechClientHooks NONE = new MechClientHooks() {
	};

	/** Once per client tick for every loaded frame, after its own tick. */
	default void tick(MechEntity mech) {
	}

	/** A movement event the LOCAL pilot's own simulation just produced (other players' frames
	 * report the same events through the synced action counters, see MechEntity.ACTION). */
	default void onEvent(MechEntity mech, int event, float a, float b) {
	}
}
