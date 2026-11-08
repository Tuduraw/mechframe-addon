package com.example.mechframe.ai;

import com.example.mechframe.MechFrame;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The outposts' command post (see CommandPostBlockEntity). Right-click: status (units out, time to
 * the next deployment). Creative, sneaking: switch the post between enemy and friendly. It drops
 * nothing when broken, so posts cannot be carried off and farmed elsewhere. */
public class CommandPostBlock extends BaseEntityBlock {

	public static final MapCodec<CommandPostBlock> CODEC = simpleCodec(CommandPostBlock::new);

	public CommandPostBlock(Properties settings) {
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
		return new CommandPostBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return world.isClientSide() ? null : createTickerHelper(type, MechFrame.COMMAND_POST_ENTITY, CommandPostBlockEntity::tick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(world instanceof ServerLevel) || !(world.getBlockEntity(pos) instanceof CommandPostBlockEntity post)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isCreative() && player.isShiftKeyDown()) {
			post.faction = post.faction == Faction.ENEMY ? Faction.FRIENDLY : Faction.ENEMY;
			post.setChanged();
			player.displayClientMessage(Component.translatable("message.mechframe.post.switched",
					Component.translatable("message.mechframe.faction." + post.faction.id())), true);
			return InteractionResult.SUCCESS;
		}
		player.displayClientMessage(post.status(world), true);
		return InteractionResult.SUCCESS;
	}
}
