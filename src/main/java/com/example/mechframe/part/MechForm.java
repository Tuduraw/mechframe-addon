package com.example.mechframe.part;

/** The two shapes a frame can take. Only an assembly whose head, core, arms, legs and booster all
 * belong to the same transform set (and whose core carries jet stats) can take the JET form. */
public enum MechForm {
	ROBOT,
	JET;

	public static MechForm byOrdinal(int ordinal) {
		return ordinal == 1 ? JET : ROBOT;
	}
}
