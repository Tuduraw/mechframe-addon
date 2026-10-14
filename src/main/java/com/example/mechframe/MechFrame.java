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
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
		MECH = ModEntityTypes.registerAddonVehicleType(Identifier.of(MOD_ID, "mech"), MechEntity::new, 1.8f, 5.0f);
		com.example.mechframe.ai.MechServerConfig.load();
		MechSounds.init();
		MechItems.register();
		GARAGE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "garage"),
				FabricBlockEntityTypeBuilder.create(GarageBlockEntity::new, MechItems.GARAGE).build());
		COMMAND_POST_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(MOD_ID, "command_post"),
				FabricBlockEntityTypeBuilder.create(com.example.mechframe.ai.CommandPostBlockEntity::new, MechItems.COMMAND_POST).build());
		GARAGE_SCREEN = Registry.register(Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "garage"),
				new ExtendedScreenHandlerType<>((syncId, inventory, pos) -> new GarageScreenHandler(syncId, inventory, pos),
						BlockPos.PACKET_CODEC));
		VehicleConverterTargets.register(MechItems.TARGET);
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new MechDataLoader());
		MechNetworking.register();
		ServerLifecycleEvents.SERVER_STARTED.register(MechNetworking::setServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MechNetworking.setServer(null));
	}
}
