package com.example.mechframe.item;

import com.example.mechframe.MechFrame;
import com.example.tudursvehiclemod.item.VehicleConverterTarget;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/** The "Mech Frame" page of the base mod's vehicle converter and the selection screen of the tiered
 * spawners: every vehicle JSON whose entity_type is mechframe:mech. */
public class MechConverterTarget implements VehicleConverterTarget {

	private final Item[] spawners;

	public MechConverterTarget(Item[] spawners) {
		this.spawners = spawners;
	}

	@Override
	public Identifier entityTypeId() {
		return Identifier.of(MechFrame.MOD_ID, "mech");
	}

	@Override
	public String translationKey() {
		return "item.mechframe.category.mech";
	}

	@Override
	public Identifier id() {
		return Identifier.of(MechFrame.MOD_ID, "mech");
	}

	@Override
	public Item[] tieredSpawnerItems() {
		return this.spawners;
	}
}
