package com.example.mechframe.garage;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The garage: stores crafted parts and refits any frame parked within GarageBlockEntity.RANGE. */
public class GarageBlock extends BlockWithEntity {

	public static final MapCodec<GarageBlock> CODEC = createCodec(GarageBlock::new);

	public GarageBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected net.minecraft.block.BlockRenderType getRenderType(BlockState state) {
		return net.minecraft.block.BlockRenderType.MODEL;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new GarageBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world instanceof ServerWorld)) {
			return ActionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof GarageBlockEntity garage && player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.openHandledScreen(garage);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof GarageBlockEntity garage) {
			ItemScatterer.spawn(world, pos, garage);
		}
		super.onStateReplaced(state, world, pos, moved);
	}
}
