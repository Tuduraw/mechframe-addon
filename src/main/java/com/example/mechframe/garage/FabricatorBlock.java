package com.example.mechframe.garage;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Mech Fabricator: every frame part (and frame kit) is made here, from the recipes in
 * data/&lt;ns&gt;/mech_recipe (see MechRecipes). Right-click opens the fabricator screen on the client;
 * crafting is requested with MechNetworking.FabricatePayload and checked on the server (the player
 * must be within RANGE of a fabricator).
 */
public class FabricatorBlock extends Block {

	public static final MapCodec<FabricatorBlock> CODEC = simpleCodec(FabricatorBlock::new);
	/** How far (blocks) the player may be from the fabricator while using it. */
	public static final double RANGE = 8.0;
	/** Set by the client entry point: opens the screen for the fabricator at that position. */
	public static Consumer<BlockPos> openScreen = pos -> {
	};

	public FabricatorBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (world.isClientSide()) {
			openScreen.accept(pos);
		}
		return InteractionResult.SUCCESS;
	}
}
