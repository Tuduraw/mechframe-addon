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
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import java.util.HashMap;
import java.util.Map;

/** Every payload this mod sends, and their server-side handling. */
public final class MechNetworking {

	private MechNetworking() {
	}

	/** Pilot's movement keys, sent every client tick while piloting (state, not events).
	 * flags: bit0 jump held, bit1 boost held. */
	public record InputPayload(float forward, float strafe, byte flags) implements CustomPacketPayload {
		public static final Type<InputPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, InputPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, InputPayload::forward,
				ByteBufCodecs.FLOAT, InputPayload::strafe,
				ByteBufCodecs.BYTE, InputPayload::flags,
				InputPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** One-shot pilot action. action: 0 quick boost (a = left, b = forward), 1 transform. */
	public record ActionPayload(int action, float a, float b) implements CustomPacketPayload {
		public static final int QUICK_BOOST = 0;
		public static final int TRANSFORM = 1;
		/** a: 0 = left arm, 1 = right arm. */
		public static final int MELEE = 2;
		public static final Type<ActionPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, ActionPayload::action,
				ByteBufCodecs.FLOAT, ActionPayload::a,
				ByteBufCodecs.FLOAT, ActionPayload::b,
				ActionPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** Jet form mouse steering accumulated this tick (degrees). */
	public record FlightInputPayload(float yaw, float pitch) implements CustomPacketPayload {
		public static final Type<FlightInputPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "flight_input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FlightInputPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, FlightInputPayload::yaw,
				ByteBufCodecs.FLOAT, FlightInputPayload::pitch,
				FlightInputPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** Garage screen: fit a part (empty string = empty the mount, "*stock*" = reset all to stock). */
	public record AssemblePayload(BlockPos garage, int mechId, String mount, String part) implements CustomPacketPayload {
		public static final String RESET = "*stock*";
		public static final Type<AssemblePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "assemble"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AssemblePayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, AssemblePayload::garage,
				ByteBufCodecs.VAR_INT, AssemblePayload::mechId,
				ByteBufCodecs.STRING_UTF8, AssemblePayload::mount,
				ByteBufCodecs.STRING_UTF8, AssemblePayload::part,
				AssemblePayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** Fabricator screen: make one of a recipe at the fabricator at pos. */
	public record FabricatePayload(BlockPos pos, String recipe) implements CustomPacketPayload {
		public static final Type<FabricatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "fabricate"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FabricatePayload> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, FabricatePayload::pos,
				ByteBufCodecs.STRING_UTF8, FabricatePayload::recipe,
				FabricatePayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return ID;
		}
	}

	/** Server -> client: all part, preset and fabricator recipe JSON (on join and after every reload).
	 * A recipe value is "owner part id" + NUL + JSON (owner empty for recipe files). */
	public record DataSyncPayload(Map<String, String> parts, Map<String, String> presets, Map<String, String> recipes) implements CustomPacketPayload {
		public static final Type<DataSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(MechFrame.MOD_ID, "data_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, DataSyncPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), DataSyncPayload::parts,
				ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), DataSyncPayload::presets,
				ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), DataSyncPayload::recipes,
				DataSyncPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
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
			this.parts.forEach((id, json) -> parts.put(Identifier.parse(id), json));
			Map<Identifier, String> presets = new HashMap<>();
			this.presets.forEach((id, json) -> presets.put(Identifier.parse(id), json));
			PartRegistry.load(parts, presets);
			Map<Identifier, String[]> recipes = new HashMap<>();
			this.recipes.forEach((id, v) -> {
				int cut = v.indexOf('\u0000');
				recipes.put(Identifier.parse(id), new String[]{cut < 0 ? "" : v.substring(0, cut), cut < 0 ? v : v.substring(cut + 1)});
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
				mech.inForward = Mth.clamp(payload.forward(), -1f, 1f);
				mech.inStrafe = Mth.clamp(payload.strafe(), -1f, 1f);
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
				case ActionPayload.QUICK_BOOST -> mech.tryQuickBoost(Mth.clamp(payload.a(), -1f, 1f), Mth.clamp(payload.b(), -1f, 1f));
				case ActionPayload.TRANSFORM -> mech.tryTransform();
				case ActionPayload.MELEE -> mech.tryMelee(payload.a() > 0.5f ? MountSlot.R_ARM : MountSlot.L_ARM);
				default -> {
				}
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(FlightInputPayload.ID, (payload, context) -> context.server().execute(() -> {
			MechEntity mech = pilotedMech(context.player());
			if (mech != null && Float.isFinite(payload.yaw()) && Float.isFinite(payload.pitch())) {
				mech.pendingYawInput += Mth.clamp(payload.yaw(), -90f, 90f);
				mech.pendingPitchInput += Mth.clamp(payload.pitch(), -90f, 90f);
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(AssemblePayload.ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			// Only from the garage screen this player has open: never look up (and so load) a chunk
			// at a position the client made up.
			if (!(player.containerMenu instanceof GarageScreenHandler handler) || !handler.getPos().equals(payload.garage())
					|| !player.level().hasChunk(SectionPos.blockToSectionCoord(payload.garage().getX()),
					SectionPos.blockToSectionCoord(payload.garage().getZ()))) {
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
			ServerPlayer player = context.player();
			BlockPos pos = payload.pos();
			// Only next to a loaded fabricator (never load a chunk at a position the client made up).
			if (player.distanceToSqr(pos.getCenter()) > FabricatorBlock.RANGE * FabricatorBlock.RANGE
					|| !player.level().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))
					|| !player.level().getBlockState(pos).is(MechItems.FABRICATOR)) {
				return;
			}
			Identifier id = Identifier.tryParse(payload.recipe());
			MechRecipe recipe = id == null ? null : MechRecipes.get(id);
			if (recipe != null && MechRecipes.craft(player, recipe)) {
				player.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE,
						net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.0f);
			}
		}));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
			server = srv;
			sender.sendPacket(DataSyncPayload.current());
		});
		MechDataLoader.afterReload = () -> {
			if (server != null) {
				DataSyncPayload payload = DataSyncPayload.current();
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		};
	}

	public static void setServer(MinecraftServer srv) {
		server = srv;
	}

	private static MechEntity pilotedMech(ServerPlayer player) {
		AbstractVehicleEntity vehicle = AbstractVehicleEntity.tudursvehiclemod$getEffectiveVehicle(player);
		return vehicle instanceof MechEntity mech && mech.getControllingPassenger() == player ? mech : null;
	}
}
