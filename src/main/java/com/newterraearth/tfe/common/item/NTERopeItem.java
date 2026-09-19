package com.newterraearth.tfe.common.item;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.blocks.rock.RockSpikeBlock;
import net.dries007.tfc.util.Helpers;

import com.newterraearth.tfe.common.NTEBlocks;
import com.newterraearth.tfe.common.NTERockBlocks;
import com.newterraearth.tfe.common.block.rope.NTEAbstractRopeBlock;
import com.newterraearth.tfe.common.block.rope.NTEGroundedRopeBlock;
import com.newterraearth.tfe.common.block.rope.NTEHangingRopeBlock;
import com.newterraearth.tfe.common.block.rope.NTERopeAnchorBlock;
import com.newterraearth.tfe.common.block.rope.NTEMetalRopeAnchorBlock;
import com.newterraearth.tfe.common.entity.NTEEntities;
import com.newterraearth.tfe.common.entity.misc.NTERopeKnot;

public class NTERopeItem extends Item
{
    public NTERopeItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final BlockState state = level.getBlockState(pos);
        final Player player = context.getPlayer();
        if (player != null && getKnotAt(level, player.blockPosition(), player) != null) return InteractionResult.PASS;

        if (state.getBlock() instanceof RockSpikeBlock && canPlaceRopeOn(level, pos, state))
        {
            if (!level.isClientSide && player != null)
            {
                final net.minecraft.world.level.block.Block anchor = NTERockBlocks.getRopeAnchor(state.getBlock());
                if (anchor == null) return InteractionResult.PASS;
                level.setBlockAndUpdate(pos, anchor.defaultBlockState().setValue(NTEAbstractRopeBlock.FACING, player.getDirection()));
                bindToAnchor(player, level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (state.getBlock() instanceof NTEMetalRopeAnchorBlock anchor && anchor.isReinforced(state) && !state.getValue(NTEMetalRopeAnchorBlock.HAS_ROPE) && state.getFluidState().isEmpty())
        {
            if (!level.isClientSide && player != null) bindToAnchor(player, level, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        final ItemStack stack = player.getItemInHand(hand);
        final NTERopeKnot knot = getKnotAt(level, player.blockPosition(), player);
        if (knot == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide)
        {
            final BlockPos anchorPos = knot.blockPosition();
            final BlockState anchorState = level.getBlockState(anchorPos);
            if (player.isShiftKeyDown())
            {
                if (anchorState.getBlock() instanceof NTERopeAnchorBlock anchor) anchor.removeRope(level, anchorPos, anchorState, player);
            }
            else placeRopes(level, player, stack, anchorPos);
            knot.discard();
        }
        return InteractionResultHolder.consume(stack);
    }

    public static void bindToAnchor(Player player, Level level, BlockPos pos)
    {
        final NTERopeKnot knot = NTERopeKnot.getNewKnotAtLocation(level, pos);
        if (knot != null)
        {
            knot.playPlacementSound();
            knot.setOwner(player);
            level.gameEvent(GameEvent.BLOCK_ATTACH, pos, GameEvent.Context.of(player));
            player.displayClientMessage(Component.translatable("tfc.tooltip.rope.throw_me"), true);
        }
    }

    @Nullable
    public static NTERopeKnot getKnotAt(Level level, BlockPos pos, Player player)
    {
        if (!level.isLoaded(pos)) return null;
        final List<NTERopeKnot> knots = level.getEntitiesOfClass(NTERopeKnot.class, new AABB(player.blockPosition()).inflate(7d), knot -> knot.isOwnedBy(player));
        return knots.isEmpty() ? null : knots.get(0);
    }

    public static boolean canPlaceRopeOn(Level level, BlockPos pos, BlockState state)
    {
        return state.getBlock() instanceof RockSpikeBlock && state.getValue(RockSpikeBlock.PART) == RockSpikeBlock.Part.TIP &&
            level.getFluidState(pos).isEmpty() && level.getBlockState(pos.below()).isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER);
    }

    public static void placeRopes(Level level, Player player, ItemStack stack, BlockPos origin)
    {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos().set(origin);
        final int count = stack.getCount();
        final Direction dir = player.getDirection();
        final DirectionProperty facing = NTEAbstractRopeBlock.FACING;
        final BlockState hangingRope = NTEBlocks.HANGING_ROPE.get().defaultBlockState().setValue(facing, dir.getOpposite());
        final BlockState horizontalRope = NTEBlocks.ROPE.get().defaultBlockState().setValue(facing, dir.getOpposite()).setValue(NTEGroundedRopeBlock.ASCENDING, false);
        final BlockState slopeRope = horizontalRope.setValue(NTEGroundedRopeBlock.ASCENDING, true);
        Helpers.playSound(level, origin, SoundEvents.FISHING_BOBBER_THROW);

        BlockState state = level.getBlockState(cursor);
        RopeState previous = RopeState.HORIZONTAL;
        BlockPos descentRampPos = null;
        if (state.getBlock() instanceof NTERopeAnchorBlock)
        {
            BlockState anchorState = state.setValue(facing, dir);
            if (anchorState.hasProperty(NTEMetalRopeAnchorBlock.HAS_ROPE))
            {
                anchorState = anchorState.setValue(NTEMetalRopeAnchorBlock.HAS_ROPE, true);
                level.scheduleTick(origin, anchorState.getBlock(), 1);
            }
            level.setBlockAndUpdate(cursor, anchorState);
        }

        cursor.move(dir);
        for (int i = 0; i < count; i++)
        {
            state = level.getBlockState(cursor);
            if (!canRopeReplace(state)) continue;

            if (previous == RopeState.VERTICAL)
            {
                cursor.move(0, -1, 0);
                state = level.getBlockState(cursor);
                cursor.move(0, 1, 0);
                if (canRopeReplace(state))
                {
                    if (!hangingRope.canSurvive(level, cursor)) break;
                    level.setBlockAndUpdate(cursor, hangingRope);
                    if (!player.isCreative()) stack.shrink(1);
                    cursor.move(0, -1, 0);
                }
                else if (slopeRope.canSurvive(level, cursor))
                {
                    level.setBlockAndUpdate(cursor, slopeRope);
                    if (!player.isCreative()) stack.shrink(1);
                    descentRampPos = cursor.immutable();
                    previous = RopeState.SLOPE;
                    cursor.move(dir);
                }
                else
                {
                    endWithHangingSegment(level, cursor, player, stack, hangingRope);
                    break;
                }
            }
            else
            {
                cursor.move(0, -1, 0);
                state = level.getBlockState(cursor);
                if (canRopeReplace(state))
                {
                    cursor.move(0, -1, 0);
                    state = level.getBlockState(cursor);
                    cursor.move(0, 1, 0);
                    if (canRopeReplace(state))
                    {
                        if (!hangingRope.canSurvive(level, cursor)) break;
                        level.setBlockAndUpdate(cursor, hangingRope);
                        if (!player.isCreative()) stack.shrink(1);
                        cursor.move(0, -1, 0);
                        descentRampPos = null;
                        previous = RopeState.VERTICAL;
                    }
                    else if (slopeRope.canSurvive(level, cursor))
                    {
                        level.setBlockAndUpdate(cursor, slopeRope);
                        if (!player.isCreative()) stack.shrink(1);
                        descentRampPos = null;
                        previous = RopeState.SLOPE;
                        cursor.move(dir);
                    }
                    else
                    {
                        if (endWithHangingSegment(level, cursor, player, stack, hangingRope)) descentRampPos = null;
                        break;
                    }
                }
                else
                {
                    cursor.move(0, 1, 0);
                    state = level.getBlockState(cursor);
                    if (canRopeReplace(state))
                    {
                        if (!horizontalRope.canSurvive(level, cursor)) break;
                        level.setBlockAndUpdate(cursor, horizontalRope);
                        if (!player.isCreative()) stack.shrink(1);
                        descentRampPos = null;
                        previous = RopeState.HORIZONTAL;
                        cursor.move(dir);
                    }
                }
            }
        }
        if (descentRampPos != null && hangingRope.canSurvive(level, descentRampPos))
        {
            // The descent stopped on a ramp, so the throw ended diagonally. The block below the ramp is solid, so
            // the rope can end straight in that cell instead of trailing off in a slope.
            level.setBlockAndUpdate(descentRampPos, hangingRope);
        }
    }

    private static boolean endWithHangingSegment(Level level, BlockPos pos, Player player, ItemStack stack, BlockState hangingRope)
    {
        // No support for a ramp (flowing water, or any surface the rope cannot rest on). End the throw with a
        // straight segment flush above the surface rather than stalling a block short of it.
        if (!hangingRope.canSurvive(level, pos)) return false;
        level.setBlockAndUpdate(pos, hangingRope);
        if (!player.isCreative()) stack.shrink(1);
        return true;
    }

    private static boolean canRopeReplace(BlockState state)
    {
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private enum RopeState { HORIZONTAL, VERTICAL, SLOPE }
}
