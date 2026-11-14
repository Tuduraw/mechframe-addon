package com.example.mechframe.asm;

import com.example.mechframe.part.MechLayout;

/** Releases the per-frame data (layouts, definitions, hit meshes) of frames that are no longer
 * loaded. Every defence-AI unit is a freshly rolled random frame, so without this the shared caches
 * - and the base mod's per-definition caches - would keep one entry per unit ever spawned until the
 * next /reload. Called every tick from the server and the client tick; does its work at most every
 * half minute. */
public final class FrameCaches {
	private static final long INTERVAL_NANOS = 30L * 1_000_000_000L;
	private static volatile long nextSweep = System.nanoTime() + INTERVAL_NANOS;

	private FrameCaches() {
	}

	public static void tick() {
		long now = System.nanoTime();
		if (now - nextSweep < 0) {
			return;
		}
		synchronized (FrameCaches.class) {
			if (now - nextSweep < 0) {
				return;
			}
			nextSweep = now + INTERVAL_NANOS;
		}
		MechLayout.sweep();
		DefinitionFactory.sweep();
	}
}
