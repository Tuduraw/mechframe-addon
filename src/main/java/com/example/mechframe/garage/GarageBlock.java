package com.example.mechframe.garage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The garage: stores crafted parts and refits any frame parked within GarageBlockEntity.RANGE. */
public class GarageBlock extends BaseEntityBlock {

	public static final MapCodec<GarageBlock> CODEC = simpleCodec(GarageBlock::new);

	public GarageBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
		return net.minecraft.world.level.block.RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GarageBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(world instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof GarageBlockEntity garage && player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(garage);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof GarageBlockEntity garage) {
			Containers.dropContents(world, pos, garage);
		}
		super.affectNeighborsAfterRemoval(state, world, pos, moved);
	}
}
