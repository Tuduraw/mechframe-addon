package com.example.mechframe.item;

import com.example.mechframe.MechFrame;
import com.example.mechframe.garage.FabricatorBlock;
import com.example.mechframe.garage.GarageBlock;
import com.example.mechframe.part.MechPart;
import com.example.mechframe.part.PartRegistry;
import com.example.mechframe.part.PartSlot;
import com.example.tudursvehiclemod.item.TieredVehicleSpawnerItem;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Items, the garage block and the creative tab. */
public final class MechItems {

	public static final DataComponentType<String> PART_ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "part_id"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

	public static final Map<PartSlot, Item> PART_ITEMS = new EnumMap<>(PartSlot.class);
	public static final Item[] SPAWNERS = new Item[5];
	public static final MechConverterTarget TARGET = new MechConverterTarget(SPAWNERS);
	public static Block GARAGE;
	public static Item GARAGE_ITEM;
	public static Block FABRICATOR;
	public static Item FABRICATOR_ITEM;
	public static Block COMMAND_POST;
	public static Item COMMAND_POST_ITEM;
	public static Item COMMAND_TERMINAL;

	private MechItems() {
	}

	public static void register() {
		for (PartSlot slot : PartSlot.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "part_" + slot.id()));
			PART_ITEMS.put(slot, Registry.register(BuiltInRegistries.ITEM, key,
					new MechPartItem(new Item.Properties().setId(key).stacksTo(16), slot)));
		}
		for (int tier = 1; tier <= 5; tier++) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "mech_spawner_t" + tier));
			SPAWNERS[tier - 1] = Registry.register(BuiltInRegistries.ITEM, key,
					new TieredVehicleSpawnerItem(new Item.Properties().setId(key).stacksTo(1), TARGET, tier));
		}

		ResourceKey<Block> garageKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "garage"));
		GARAGE = Registry.register(BuiltInRegistries.BLOCK, garageKey, new GarageBlock(BlockBehaviour.Properties.of()
				.setId(garageKey).mapColor(MapColor.METAL).strength(4.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
		ResourceKey<Item> garageItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "garage"));
		GARAGE_ITEM = Registry.register(BuiltInRegistries.ITEM, garageItemKey,
				new BlockItem(GARAGE, new Item.Properties().setId(garageItemKey).useBlockDescriptionPrefix()));

		ResourceKey<Block> fabKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "fabricator"));
		FABRICATOR = Registry.register(BuiltInRegistries.BLOCK, fabKey, new FabricatorBlock(BlockBehaviour.Properties.of()
				.setId(fabKey).mapColor(MapColor.METAL).strength(4.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
		ResourceKey<Item> fabItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "fabricator"));
		FABRICATOR_ITEM = Registry.register(BuiltInRegistries.ITEM, fabItemKey,
				new BlockItem(FABRICATOR, new Item.Properties().setId(fabItemKey).useBlockDescriptionPrefix()));

		ResourceKey<Block> postKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "command_post"));
		COMMAND_POST = Registry.register(BuiltInRegistries.BLOCK, postKey, new com.example.mechframe.ai.CommandPostBlock(BlockBehaviour.Properties.of()
				.setId(postKey).mapColor(MapColor.COLOR_RED).strength(30.0f, 1200.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
		ResourceKey<Item> postItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "command_post"));
		COMMAND_POST_ITEM = Registry.register(BuiltInRegistries.ITEM, postItemKey,
				new BlockItem(COMMAND_POST, new Item.Properties().setId(postItemKey).useBlockDescriptionPrefix()));
		ResourceKey<Item> terminalKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "command_terminal"));
		COMMAND_TERMINAL = Registry.register(BuiltInRegistries.ITEM, terminalKey,
				new CommandTerminalItem(new Item.Properties().setId(terminalKey).stacksTo(1)));

		ResourceKey<CreativeModeTab> groupKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "mechs"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, groupKey, FabricItemGroup.builder()
				.icon(() -> new ItemStack(SPAWNERS[2]))
				.title(Component.translatable("itemGroup.mechframe.mechs"))
				.build());
		ItemGroupEvents.modifyEntriesEvent(groupKey).register(entries -> {
			entries.accept(FABRICATOR_ITEM);
			entries.accept(GARAGE_ITEM);
			entries.accept(COMMAND_TERMINAL);
			entries.accept(COMMAND_POST_ITEM);
			for (Item spawner : SPAWNERS) {
				entries.accept(spawner);
			}
			List<MechPart> parts = new ArrayList<>(PartRegistry.all());
			parts.sort(Comparator.comparing((MechPart p) -> p.slot().ordinal()).thenComparingInt(MechPart::tier)
					.thenComparing(MechPart::displayName));
			for (MechPart part : parts) {
				entries.accept(MechPartItem.stackFor(part));
			}
		});
	}
}
