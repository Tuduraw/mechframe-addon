package com.example.mechframe.ai;

import com.example.mechframe.MechFrame;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The outposts' command post (see CommandPostBlockEntity). Right-click: status (units out, time to
 * the next deployment). Creative, sneaking: switch the post between enemy and friendly. It drops
 * nothing when broken, so posts cannot be carried off and farmed elsewhere. */
public class CommandPostBlock extends BlockWithEntity {

	public static final MapCodec<CommandPostBlock> CODEC = createCodec(CommandPostBlock::new);

	public CommandPostBlock(Settings settings) {
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
		return new CommandPostBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() ? null : validateTicker(type, MechFrame.COMMAND_POST_ENTITY, CommandPostBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!(world instanceof ServerWorld) || !(world.getBlockEntity(pos) instanceof CommandPostBlockEntity post)) {
			return ActionResult.SUCCESS;
		}
		if (player.isCreative() && player.isSneaking()) {
			post.faction = post.faction == Faction.ENEMY ? Faction.FRIENDLY : Faction.ENEMY;
			post.markDirty();
			player.sendMessage(Text.translatable("message.mechframe.post.switched",
					Text.translatable("message.mechframe.faction." + post.faction.id())), true);
			return ActionResult.SUCCESS;
		}
		player.sendMessage(post.status(world), true);
		return ActionResult.SUCCESS;
	}
}
