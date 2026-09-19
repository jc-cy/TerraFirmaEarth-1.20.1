package com.newterraearth.tfe.common.block.rope;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.dries007.tfc.client.TFCSounds;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.TFCBlockStateProperties;
import net.dries007.tfc.common.blocks.rock.RockSpikeBlock;
import net.dries007.tfc.util.Helpers;

public class NTEMetalRopeAnchorBlock extends NTERopeAnchorBlock
{
    public static final BooleanProperty HAS_ROPE = BooleanProperty.create("has_rope");
    /** 0 = 刚放下、尚未加固；{@link #MAX_STAGE} = 已敲入地面，可以绑绳使用。 */
    public static final IntegerProperty STAGE = TFCBlockStateProperties.STAGE_3;
    public static final int MAX_STAGE = 3;

    /**
     * 未加固时柱体高出地面，每敲一下降低 2 像素，敲满后与上游 {@code tfc:block/rope_anchor} 完全同高。
     * 高度必须与 {@code scripts/generate_metal_rope_anchor_resources.py} 生成的柱体模型一致。
     */
    private static final VoxelShape[] STAGE_SHAPES = {
        Block.box(6, 0, 6, 10, 22, 10),
        Block.box(6, 0, 6, 10, 20, 10),
        Block.box(6, 0, 6, 10, 18, 10),
        RockSpikeBlock.TIP_SHAPE
    };

    public NTEMetalRopeAnchorBlock(ExtendedProperties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FLUID, FLUID.keyFor(Fluids.EMPTY)).setValue(HAS_ROPE, false).setValue(STAGE, 0));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult)
    {
        final int stage = state.getValue(STAGE);
        final ItemStack stack = player.getItemInHand(hand);
        if (stage < MAX_STAGE && Helpers.isItem(stack, TFCTags.Items.HAMMERS))
        {
            // 未加固的绳锚无法使用：只能拿任意锤子逐次敲入。每敲一下模型整体下移 2 像素，第 3 下完成后才能绑绳。
            if (!level.isClientSide)
            {
                final boolean reinforced = stage + 1 == MAX_STAGE;
                level.setBlockAndUpdate(pos, state.setValue(STAGE, stage + 1));
                level.playSound(null, pos, reinforced ? SoundEvents.ANVIL_USE : TFCSounds.ANVIL_HIT.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
                stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // 未加固且没有残留绳索时不响应；加固后才允许空手回收绳索。
        if (stage < MAX_STAGE && !state.getValue(HAS_ROPE)) return InteractionResult.PASS;
        return super.use(state, level, pos, player, hand, hitResult);
    }

    /** 加固完成后绳锚才能绑绳使用。 */
    public boolean isReinforced(BlockState state)
    {
        return state.getValue(STAGE) >= MAX_STAGE;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        return super.updateShape(state.setValue(HAS_ROPE, isRopeAttached(level, pos, state)), direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(HAS_ROPE) && !isRopeAttached(level, pos, state)) level.setBlockAndUpdate(pos, state.setValue(HAS_ROPE, false));
    }

    @Override
    protected BlockState getStateAfterRemoval(BlockState state)
    {
        return state.setValue(HAS_ROPE, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder.add(HAS_ROPE, STAGE));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return STAGE_SHAPES[state.getValue(STAGE)];
    }
}
