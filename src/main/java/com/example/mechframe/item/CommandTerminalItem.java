package com.example.mechframe.item;

import net.minecraft.item.Item;

/** The command terminal: right-click a frame with it to give that frame orders (handled in
 * MechEntity#interact): off -> base defence (guards where it stands) -> wingman (follows you) -> off.
 * Enemy frames do not take orders. */
public class CommandTerminalItem extends Item {

	public CommandTerminalItem(Settings settings) {
		super(settings);
	}
}
