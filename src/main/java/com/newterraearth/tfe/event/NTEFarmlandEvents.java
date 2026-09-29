package com.newterraearth.tfe.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.crop.CropHelpers;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.util.Fertilizer;
import net.dries007.tfc.util.Helpers;

/**
 * Addon farmland that is built on vanilla's {@code FarmBlock} — Farmer's Delight rich soil farmland and
 * its quality variants — has no {@code use} override, so TFC's fertilizer entry point never runs on an
 * empty field. TFC's own farmland already accepts fertilizer directly through {@link FarmlandBlock#use};
 * this listener only fills that gap for the other blocks in {@link TFCTags.Blocks#FARMLAND}, leaving the
 * native path untouched.
 */
public final class NTEFarmlandEvents
{
    private NTEFarmlandEvents() {}

    public static void init()
    {
        MinecraftForge.EVENT_BUS.register(NTEFarmlandEvents.class);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        final Level level = event.getLevel();
        if (level.isClientSide())
        {
            return;
        }

        final ItemStack stack = event.getItemStack();
        if (Fertilizer.get(stack) == null)
        {
            return;
        }

        final BlockPos pos = event.getPos();
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FarmlandBlock || !Helpers.isBlock(state, TFCTags.Blocks.FARMLAND))
        {
            return;
        }

        if (CropHelpers.useFertilizer(level, event.getEntity(), event.getHand(), pos))
        {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
