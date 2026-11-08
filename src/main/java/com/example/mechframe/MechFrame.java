package com.example.mechframe;

import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.garage.GarageBlockEntity;
import com.example.mechframe.garage.GarageScreenHandler;
import com.example.mechframe.item.MechItems;
import com.example.mechframe.net.MechNetworking;
import com.example.mechframe.part.MechDataLoader;
import com.example.tudursvehiclemod.item.VehicleConverterTargets;
import com.example.tudursvehiclemod.registry.ModEntityTypes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Mech Frame Addon - assembled, high-speed combat frames for Tudur's Vehicle Mod.
 *
 * <p>One entity type (mechframe:mech) for every frame: what a frame is, and how it moves, comes
 * from the parts in its assembly (data/<ns>/mech_parts/*.json), its stock assembly from a vehicle
 * JSON's "mechframe" object. See README.md / DEVELOPMENT_NOTES.md. */
public class MechFrame implements ModInitializer {

	public static final String MOD_ID = "mechframe";

	public static EntityType<MechEntity> MECH;
	public static BlockEntityType<GarageBlockEntity> GARAGE_ENTITY;
	public static ExtendedScreenHandlerType<GarageScreenHandler, BlockPos> GARAGE_SCREEN;
	public static BlockEntityType<com.example.mechframe.ai.CommandPostBlockEntity> COMMAND_POST_ENTITY;

	@Override
	public void onInitialize() {
		MECH = ModEntityTypes.registerAddonVehicleType(Identifier.fromNamespaceAndPath(MOD_ID, "mech"), MechEntity::new, 1.8f, 5.0f);
		com.example.mechframe.ai.MechServerConfig.load();
		MechSounds.init();
		MechItems.register();
		GARAGE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "garage"),
				FabricBlockEntityTypeBuilder.create(GarageBlockEntity::new, MechItems.GARAGE).build());
		COMMAND_POST_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "command_post"),
				FabricBlockEntityTypeBuilder.create(com.example.mechframe.ai.CommandPostBlockEntity::new, MechItems.COMMAND_POST).build());
		GARAGE_SCREEN = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MOD_ID, "garage"),
				new ExtendedScreenHandlerType<>((syncId, inventory, pos) -> new GarageScreenHandler(syncId, inventory, pos),
						BlockPos.STREAM_CODEC));
		VehicleConverterTargets.register(MechItems.TARGET);
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new MechDataLoader());
		MechNetworking.register();
		ServerLifecycleEvents.SERVER_STARTED.register(MechNetworking::setServer);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(com.example.mechframe.ai.MechBrain::serverTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MechNetworking.setServer(null));
	}
}
