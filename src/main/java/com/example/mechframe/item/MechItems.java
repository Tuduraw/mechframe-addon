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
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.component.ComponentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Items, the garage block and the creative tab. */
public final class MechItems {

	public static final ComponentType<String> PART_ID = Registry.register(Registries.DATA_COMPONENT_TYPE,
			Identifier.of(MechFrame.MOD_ID, "part_id"),
			ComponentType.<String>builder().codec(Codec.STRING).packetCodec(PacketCodecs.STRING).build());

	public static final Map<PartSlot, Item> PART_ITEMS = new EnumMap<>(PartSlot.class);
	public static final Item[] SPAWNERS = new Item[5];
	public static final MechConverterTarget TARGET = new MechConverterTarget(SPAWNERS);
	public static Block GARAGE;
	public static Item GARAGE_ITEM;
	public static Block FABRICATOR;
	public static Item FABRICATOR_ITEM;

	private MechItems() {
	}

	public static void register() {
		for (PartSlot slot : PartSlot.values()) {
			RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MechFrame.MOD_ID, "part_" + slot.id()));
			PART_ITEMS.put(slot, Registry.register(Registries.ITEM, key,
					new MechPartItem(new Item.Settings().registryKey(key).maxCount(16), slot)));
		}
		for (int tier = 1; tier <= 5; tier++) {
			RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MechFrame.MOD_ID, "mech_spawner_t" + tier));
			SPAWNERS[tier - 1] = Registry.register(Registries.ITEM, key,
					new TieredVehicleSpawnerItem(new Item.Settings().registryKey(key).maxCount(1), TARGET, tier));
		}

		RegistryKey<Block> garageKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MechFrame.MOD_ID, "garage"));
		GARAGE = Registry.register(Registries.BLOCK, garageKey, new GarageBlock(AbstractBlock.Settings.create()
				.registryKey(garageKey).mapColor(MapColor.IRON_GRAY).strength(4.0f).sounds(BlockSoundGroup.METAL).requiresTool()));
		RegistryKey<Item> garageItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MechFrame.MOD_ID, "garage"));
		GARAGE_ITEM = Registry.register(Registries.ITEM, garageItemKey,
				new BlockItem(GARAGE, new Item.Settings().registryKey(garageItemKey).useBlockPrefixedTranslationKey()));

		RegistryKey<Block> fabKey = RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(MechFrame.MOD_ID, "fabricator"));
		FABRICATOR = Registry.register(Registries.BLOCK, fabKey, new FabricatorBlock(AbstractBlock.Settings.create()
				.registryKey(fabKey).mapColor(MapColor.IRON_GRAY).strength(4.0f).sounds(BlockSoundGroup.METAL).requiresTool()));
		RegistryKey<Item> fabItemKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MechFrame.MOD_ID, "fabricator"));
		FABRICATOR_ITEM = Registry.register(Registries.ITEM, fabItemKey,
				new BlockItem(FABRICATOR, new Item.Settings().registryKey(fabItemKey).useBlockPrefixedTranslationKey()));

		RegistryKey<ItemGroup> groupKey = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MechFrame.MOD_ID, "mechs"));
		Registry.register(Registries.ITEM_GROUP, groupKey, FabricItemGroup.builder()
				.icon(() -> new ItemStack(SPAWNERS[2]))
				.displayName(Text.translatable("itemGroup.mechframe.mechs"))
				.build());
		ItemGroupEvents.modifyEntriesEvent(groupKey).register(entries -> {
			entries.add(FABRICATOR_ITEM);
			entries.add(GARAGE_ITEM);
			for (Item spawner : SPAWNERS) {
				entries.add(spawner);
			}
			List<MechPart> parts = new ArrayList<>(PartRegistry.all());
			parts.sort(Comparator.comparing((MechPart p) -> p.slot().ordinal()).thenComparingInt(MechPart::tier)
					.thenComparing(MechPart::displayName));
			for (MechPart part : parts) {
				entries.add(MechPartItem.stackFor(part));
			}
		});
	}
}
