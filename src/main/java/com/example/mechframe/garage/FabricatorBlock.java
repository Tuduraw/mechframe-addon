package com.example.mechframe.garage;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * The Mech Fabricator: every frame part (and frame kit) is made here, from the recipes in
 * data/&lt;ns&gt;/mech_recipe (see MechRecipes). Right-click opens the fabricator screen on the client;
 * crafting is requested with MechNetworking.FabricatePayload and checked on the server (the player
 * must be within RANGE of a fabricator).
 */
public class FabricatorBlock extends Block {

	public static final MapCodec<FabricatorBlock> CODEC = createCodec(FabricatorBlock::new);
	/** How far (blocks) the player may be from the fabricator while using it. */
	public static final double RANGE = 8.0;
	/** Set by the client entry point: opens the screen for the fabricator at that position. */
	public static Consumer<BlockPos> openScreen = pos -> {
	};

	public FabricatorBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient()) {
			openScreen.accept(pos);
		}
		return ActionResult.SUCCESS;
	}
}
