package com.example.mechframe.item;

import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import com.example.mechframe.part.PartSlot;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.function.Consumer;

/** A crafted frame part, kept in a Garage and fitted from there. One item per part KIND (so each kind
 * has its own icon); which part it is lives in the mechframe:part_id component. */
public class MechPartItem extends Item {

	private final PartSlot slot;

	public MechPartItem(Settings settings, PartSlot slot) {
		super(settings);
		this.slot = slot;
	}

	public PartSlot slot() {
		return this.slot;
	}

	public static Identifier partId(ItemStack stack) {
		String id = stack.get(MechItems.PART_ID);
		return id == null ? null : Identifier.tryParse(id);
	}

	public static MechPart part(ItemStack stack) {
		return stack.getItem() instanceof MechPartItem ? PartRegistry.getOrNull(partId(stack)) : null;
	}

	public static ItemStack stackFor(MechPart part) {
		Item item = MechItems.PART_ITEMS.get(part.slot());
		ItemStack stack = new ItemStack(item);
		stack.set(MechItems.PART_ID, part.id().toString());
		return stack;
	}

	@Override
	public Text getName(ItemStack stack) {
		MechPart part = part(stack);
		if (part != null) {
			return Text.literal(part.displayName());
		}
		return super.getName(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> tooltip, TooltipType type) {
		MechPart part = part(stack);
		if (part == null) {
			Identifier id = partId(stack);
			tooltip.accept(Text.translatable("tooltip.mechframe.unknown_part", id == null ? "-" : id.toString()).formatted(Formatting.RED));
			return;
		}
		tooltip.accept(Text.translatable("tooltip.mechframe.slot." + part.slot().id()).formatted(Formatting.GOLD));
		if (!part.maker().isEmpty()) {
			tooltip.accept(Text.literal(part.maker()).formatted(Formatting.DARK_GRAY));
		}
		tooltip.accept(line("weight", String.format(Locale.ROOT, "%.1f t", part.weight())));
		tooltip.accept(line("armor", String.format(Locale.ROOT, "%.0f", part.armor())));
		switch (part.slot()) {
			case HEAD -> tooltip.accept(line("lock", String.format(Locale.ROOT, "%.0f m / %.0f°", part.head().lockRange(), part.head().lockCone())));
			case CORE -> {
				tooltip.accept(line("en", String.format(Locale.ROOT, "%.0f (+%.0f/t)", part.core().enCapacity(), part.core().enRecharge())));
				part.core().jet().ifPresent(jet -> tooltip.accept(Text.translatable("tooltip.mechframe.transformable").formatted(Formatting.AQUA)));
			}
			case ARMS -> {
				tooltip.accept(line("arm_load", String.format(Locale.ROOT, "%.1f t", part.arms().loadLimit())));
				if (!part.arms().integrated().isEmpty()) {
					java.util.List<String> names = new java.util.ArrayList<>();
					for (Identifier w : new java.util.TreeSet<>(part.arms().integrated().values())) {
						MechPart wp = PartRegistry.getOrNull(w);
						names.add(wp != null ? wp.displayName() : w.toString());
					}
					tooltip.accept(Text.translatable("tooltip.mechframe.integrated", String.join(", ", names)).formatted(Formatting.GOLD));
				}
			}
			case LEGS -> {
				tooltip.accept(Text.translatable("tooltip.mechframe.legtype." + part.legs().type().name().toLowerCase(Locale.ROOT)).formatted(Formatting.GRAY));
				tooltip.accept(line("leg_load", String.format(Locale.ROOT, "%.1f t", part.legs().loadLimit())));
				tooltip.accept(line("speed", String.format(Locale.ROOT, "%.0f km/h", part.legs().walkSpeed() * 72f)));
			}
			case BOOSTER -> {
				tooltip.accept(line("boost", String.format(Locale.ROOT, "%.0f km/h", part.booster().boostSpeed() * 72f)));
				tooltip.accept(line("qb", String.format(Locale.ROOT, "%.1f / EN %.0f", part.booster().qbSpeed(), part.booster().qbCost())));
			}
			case WEAPON -> {
				MountSlot.WeaponMount mount = part.weapon().mount();
				tooltip.accept(Text.translatable("tooltip.mechframe.mount." + mount.name().toLowerCase(Locale.ROOT)).formatted(Formatting.GRAY));
				if (part.weapon().isMelee()) {
					MechPart.MeleeSpec m = part.weapon().melee();
					if (m.thrust()) {
						tooltip.accept(Text.translatable("tooltip.mechframe.melee_thrust").formatted(Formatting.GRAY));
					}
					tooltip.accept(Text.translatable("tooltip.mechframe.melee", String.format(Locale.ROOT, "%.0f", m.damage()),
							String.format(Locale.ROOT, "%.1f", m.reach())).formatted(Formatting.GRAY));
				}
			}
		}
		part.transformSet().ifPresent(set -> tooltip.accept(Text.translatable("tooltip.mechframe.transform_set", set).formatted(Formatting.AQUA)));
	}

	private static Text line(String key, String value) {
		return Text.translatable("tooltip.mechframe." + key).append(": " + value).formatted(Formatting.GRAY);
	}
}
