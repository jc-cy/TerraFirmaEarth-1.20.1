package com.newterraearth.tfe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blockentities.LogPileBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.devices.LogPileBlock;
import net.dries007.tfc.util.Helpers;

import com.newterraearth.tfe.common.NTELogPileHelpers;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;

@Mixin(value = LogPileBlock.class, remap = false)
public abstract class LogPileBlockMixin
{
    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"), remap = true)
    private void tfe$addCountProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci)
    {
        builder.add(NTELogPileHelpers.LOG_PILE_COUNT);
    }

    /**
     * @author Codex
     * @reason 1.21 log piles store one visible log count in block state.
     */
    @Overwrite(remap = true)
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return ((LogPileBlock) (Object) this).defaultBlockState()
            .setValue(HORIZONTAL_AXIS, context.getHorizontalDirection().getAxis())
            .setValue(NTELogPileHelpers.LOG_PILE_COUNT, 1);
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true, remap = true)
    private void tfe$collapseUnsupportedPile(BlockState state, Direction facing, BlockState facingState, LevelAccessor levelAccess, BlockPos currentPos, BlockPos facingPos, CallbackInfoReturnable<BlockState> cir)
    {
        if (!levelAccess.isClientSide() && levelAccess instanceof Level level)
        {
            if (facing == Direction.DOWN && !facingState.isFaceSturdy(levelAccess, facingPos, Direction.UP) && !(facingState.getBlock() instanceof LogPileBlock))
            {
                cir.setReturnValue(Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * @author Codex
     * @reason Port 1.21 click behavior: normal click inserts/extracts one log and no longer opens the 1.20 GUI.
     */
    @Overwrite(remap = true)
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult result)
    {
        if (!(level.getBlockEntity(pos) instanceof LogPileBlockEntity logPile))
        {
            return InteractionResult.PASS;
        }

        final ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown())
        {
            // Shift + log inserts the whole held stack and is handled earlier by InteractionManagerMixin. Shift + an
            // empty hand is its counterpart: take one full pile (a group) from the top of the stack. Anything else (a
            // torch to light the pile, a hammer, ...) is left untouched so the regular TFC interactions still run.
            if (stack.isEmpty())
            {
                if (!level.isClientSide())
                {
                    NTELogPileHelpers.extractFromTop(level, pos, player, true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }

        if (!level.isClientSide())
        {
            if (Helpers.isItem(stack.getItem(), TFCTags.Items.LOG_PILE_LOGS))
            {
                NTELogPileHelpers.insertAndPushUp(stack, state, level, pos, logPile, false);
            }
            else if (stack.isEmpty())
            {
                NTELogPileHelpers.extractFromTop(level, pos, player, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // These inherited Block methods are not TFC targets, so they need production runtime names in the reobf jar.
    public boolean m_7898_(BlockState state, LevelReader level, BlockPos pos)
    {
        final BlockState below = level.getBlockState(pos.below());
        return Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP) || below.getBlock() instanceof LogPileBlock;
    }

    public VoxelShape m_5940_(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return NTELogPileHelpers.getShape(state.getValue(HORIZONTAL_AXIS), NTELogPileHelpers.getVisibleLogCount(state));
    }

    public VoxelShape m_5939_(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return NTELogPileHelpers.getShape(state.getValue(HORIZONTAL_AXIS), NTELogPileHelpers.getVisibleLogCount(state));
    }

    public VoxelShape m_5909_(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return NTELogPileHelpers.getShape(state.getValue(HORIZONTAL_AXIS), NTELogPileHelpers.getVisibleLogCount(state));
    }

    @SuppressWarnings("deprecation")
    public VoxelShape m_7952_(BlockState state, BlockGetter level, BlockPos pos)
    {
        return Shapes.empty();
    }

    @SuppressWarnings("deprecation")
    public boolean m_7923_(BlockState state)
    {
        return true;
    }

    /**
     * @author Codex
     * @reason Match 1.21 pick-block behavior by returning a stored log instead of the empty 1.20 fallback.
     */
    @Overwrite(remap = false)
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player)
    {
        if (level.getBlockEntity(pos) instanceof LogPileBlockEntity pile)
        {
            final var inventory = NTELogPileHelpers.getInventory(pile);
            for (int i = 0; i < inventory.getSlots(); i++)
            {
                final ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty())
                {
                    return stack.copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
