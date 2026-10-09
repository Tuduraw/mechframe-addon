package com.example.mechframe.net;

import com.example.mechframe.MechFrame;
import com.example.mechframe.entity.MechEntity;
import com.example.mechframe.garage.AssemblyService;
import com.example.mechframe.garage.GarageScreenHandler;
import com.example.mechframe.part.MechDataLoader;
import com.example.mechframe.part.MountSlot;
import com.example.mechframe.part.PartRegistry;
import com.example.mechframe.recipe.MechRecipe;
import com.example.mechframe.recipe.MechRecipes;
import com.example.mechframe.item.MechItems;
import com.example.mechframe.garage.FabricatorBlock;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

/** Every payload this mod sends, and their server-side handling. */
public final class MechNetworking {

	private MechNetworking() {
	}

	/** Pilot's movement keys, sent every client tick while piloting (state, not events).
	 * flags: bit0 jump held, bit1 boost held. */
	public record InputPayload(float forward, float strafe, byte flags) implements CustomPayload {
		public static final Id<InputPayload> ID = new Id<>(Identifier.of(MechFrame.MOD_ID, "input"));
		public static final PacketCodec<RegistryByteBuf, InputPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.FLOAT, InputPayload::forward,
				PacketCodecs.FLOAT, InputPayload::strafe,
				PacketCodecs.BYTE, InputPayload::flags,
				InputPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** One-shot pilot action. action: 0 quick boost (a = left, b = forward), 1 transform. */
	public record ActionPayload(int action, float a, float b) implements CustomPayload {
		public static final int QUICK_BOOST = 0;
		public static final int TRANSFORM = 1;
		/** a: 0 = left arm, 1 = right arm. */
		public static final int MELEE = 2;
		public static final Id<ActionPayload> ID = new Id<>(Identifier.of(MechFrame.MOD_ID, "action"));
		public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.VAR_INT, ActionPayload::action,
				PacketCodecs.FLOAT, ActionPayload::a,
				PacketCodecs.FLOAT, ActionPayload::b,
				ActionPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Jet form mouse steering accumulated this tick (degrees). */
	public record FlightInputPayload(float yaw, float pitch) implements CustomPayload {
		public static final Id<FlightInputPayload> ID = new Id<>(Identifier.of(MechFrame.MOD_ID, "flight_input"));
		public static final PacketCodec<RegistryByteBuf, FlightInputPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.FLOAT, FlightInputPayload::yaw,
				PacketCodecs.FLOAT, FlightInputPayload::pitch,
				FlightInputPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Garage screen: fit a part (empty string = empty the mount, "*stock*" = reset all to stock). */
	public record AssemblePayload(BlockPos garage, int mechId, String mount, String part) implements CustomPayload {
		public static final String RESET = "*stock*";
		public static final Id<AssemblePayload> ID = new Id<>(Identifier.of(MechFrame.MOD_ID, "assemble"));
		public static final PacketCodec<RegistryByteBuf, AssemblePayload> CODEC = PacketCodec.tuple(
				BlockPos.PACKET_CODEC, AssemblePayload::garage,
				PacketCodecs.VAR_INT, AssemblePayload::mechId,
				PacketCodecs.STRING, AssemblePayload::mount,
				PacketCodecs.STRING, AssemblePayload::part,
				AssemblePayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Fabricator screen: make one of a recipe at the fabricator at pos. */
	public record FabricatePayload(BlockPos pos, String recipe) implements CustomPayload {
		public static final Id<FabricatePayload> ID = new Id<>(Identifier.of(MechFrame.MOD_ID, "fabricate"));
		public static final PacketCodec<RegistryByteBuf, FabricatePayload> CODEC = PacketCodec.tuple(
				BlockPos.PACKET_CODEC, FabricatePayload::pos,
				PacketCodecs.STRING, FabricatePayload::recipe,
				FabricatePayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Server -> client: all part, preset and fabricator recipe JSON (on join and after every reload).
	 * A recipe value is "owner part id" + NUL + JSON (owner empty for recipe files). */
	public record DataSyncPayload(Map<String, String> parts, Map<String, String> presets, Map<String, String> recipes) implements CustomPayload {
		public static final Id<DataSyncPayload> ID = new Id<>(Identifier.of(MechFrame.MOD_ID, "data_sync"));
		public static final PacketCodec<RegistryByteBuf, DataSyncPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING), DataSyncPayload::parts,
				PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING), DataSyncPayload::presets,
				PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING), DataSyncPayload::recipes,
				DataSyncPayload::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}

		public static DataSyncPayload current() {
			Map<String, String> parts = new HashMap<>();
			PartRegistry.rawParts().forEach((id, json) -> parts.put(id.toString(), json));
			Map<String, String> presets = new HashMap<>();
			PartRegistry.rawPresets().forEach((id, json) -> presets.put(id.toString(), json));
			Map<String, String> recipes = new HashMap<>();
			MechRecipes.raw().forEach((id, v) -> recipes.put(id.toString(), v[0] + '\u0000' + v[1]));
			return new DataSyncPayload(parts, presets, recipes);
		}

		public void apply() {
			Map<Identifier, String> parts = new HashMap<>();
			this.parts.forEach((id, json) -> parts.put(Identifier.of(id), json));
			Map<Identifier, String> presets = new HashMap<>();
			this.presets.forEach((id, json) -> presets.put(Identifier.of(id), json));
			PartRegistry.load(parts, presets);
			Map<Identifier, String[]> recipes = new HashMap<>();
			this.recipes.forEach((id, v) -> {
				int cut = v.indexOf('\u0000');
				recipes.put(Identifier.of(id), new String[]{cut < 0 ? "" : v.substring(0, cut), cut < 0 ? v : v.substring(cut + 1)});
			});
			MechRecipes.load(recipes);
		}
	}

	private static MinecraftServer server;

	public static void register() {
		PayloadTypeRegistry.playC2S().register(InputPayload.ID, InputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FlightInputPayload.ID, FlightInputPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AssemblePayload.ID, AssemblePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FabricatePayload.ID, FabricatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DataSyncPayload.ID, DataSyncPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(InputPayload.ID, (payload, context) -> context.server().execute(() -> {
			MechEntity mech = pilotedMech(context.player());
			// MathHelper.clamp passes NaN through, and one NaN velocity would stick for good.
			if (mech != null && Float.isFinite(payload.forward()) && Float.isFinite(payload.strafe())) {
				mech.inForward = MathHelper.clamp(payload.forward(), -1f, 1f);
				mech.inStrafe = MathHelper.clamp(payload.strafe(), -1f, 1f);
				mech.inJump = (payload.flags() & 1) != 0;
				mech.inBoost = (payload.flags() & 2) != 0;
				mech.inTankControls = (payload.flags() & 4) != 0;
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) -> context.server().execute(() -> {
			MechEntity mech = pilotedMech(context.player());
			if (mech == null || !Float.isFinite(payload.a()) || !Float.isFinite(payload.b())) {
				return;
			}
			switch (payload.action()) {
				case ActionPayload.QUICK_BOOST -> mech.tryQuickBoost(MathHelper.clamp(payload.a(), -1f, 1f), MathHelper.clamp(payload.b(), -1f, 1f));
				case ActionPayload.TRANSFORM -> mech.tryTransform();
				case ActionPayload.MELEE -> mech.tryMelee(payload.a() > 0.5f ? MountSlot.R_ARM : MountSlot.L_ARM);
				default -> {
				}
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(FlightInputPayload.ID, (payload, context) -> context.server().execute(() -> {
			MechEntity mech = pilotedMech(context.player());
			if (mech != null && Float.isFinite(payload.yaw()) && Float.isFinite(payload.pitch())) {
				mech.pendingYawInput += MathHelper.clamp(payload.yaw(), -90f, 90f);
				mech.pendingPitchInput += MathHelper.clamp(payload.pitch(), -90f, 90f);
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(AssemblePayload.ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayerEntity player = context.player();
			// Only from the garage screen this player has open: never look up (and so load) a chunk
			// at a position the client made up.
			if (!(player.currentScreenHandler instanceof GarageScreenHandler handler) || !handler.getPos().equals(payload.garage())
					|| !player.getEntityWorld().isChunkLoaded(ChunkSectionPos.getSectionCoord(payload.garage().getX()),
					ChunkSectionPos.getSectionCoord(payload.garage().getZ()))) {
				return;
			}
			if (AssemblePayload.RESET.equals(payload.part())) {
				AssemblyService.resetToStock(player, payload.garage(), payload.mechId());
				return;
			}
			MountSlot mount = MountSlot.byId(payload.mount());
			if (mount == null) {
				return;
			}
			Identifier part = payload.part().isEmpty() ? null : Identifier.tryParse(payload.part());
			AssemblyService.apply(player, payload.garage(), payload.mechId(), mount, part);
		}));

		ServerPlayNetworking.registerGlobalReceiver(FabricatePayload.ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayerEntity player = context.player();
			BlockPos pos = payload.pos();
			// Only next to a loaded fabricator (never load a chunk at a position the client made up).
			if (player.squaredDistanceTo(pos.toCenterPos()) > FabricatorBlock.RANGE * FabricatorBlock.RANGE
					|| !player.getEntityWorld().isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()))
					|| !player.getEntityWorld().getBlockState(pos).isOf(MechItems.FABRICATOR)) {
				return;
			}
			Identifier id = Identifier.tryParse(payload.recipe());
			MechRecipe recipe = id == null ? null : MechRecipes.get(id);
			if (recipe != null && MechRecipes.craft(player, recipe)) {
				player.getEntityWorld().playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_SMITHING_TABLE_USE,
						net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 1.0f);
			}
		}));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
			server = srv;
			sender.sendPacket(DataSyncPayload.current());
		});
		MechDataLoader.afterReload = () -> {
			if (server != null) {
				DataSyncPayload payload = DataSyncPayload.current();
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		};
	}

	public static void setServer(MinecraftServer srv) {
		server = srv;
	}

	private static MechEntity pilotedMech(ServerPlayerEntity player) {
		AbstractVehicleEntity vehicle = AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(player);
		return vehicle instanceof MechEntity mech && mech.getControllingPassenger() == player ? mech : null;
	}
}
