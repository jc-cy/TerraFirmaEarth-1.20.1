package com.newterraearth.tfe.event;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.dries007.tfc.common.TFCCreativeTabs;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.dries007.tfc.util.advancements.TFCAdvancements;
import net.dries007.tfc.util.events.StartFireEvent;

import com.newterraearth.tfe.common.NTEBlocks;
import com.newterraearth.tfe.common.NTEDevices;
import com.newterraearth.tfe.common.block.rope.NTEMetalRopeAnchorBlock;
import com.newterraearth.tfe.common.entity.NTEItems;

/**
 * Forge-bus event glue for the stove / stove pot devices.
 *
 * <ul>
 *   <li>Adds the stove and stove pot to TFC's misc creative tab so they appear alongside the
 *       firepit and pot in creative search results.</li>
 *   <li>Mirrors 1.20 TFC's {@code ForgeEventHandler#onFireStart} branch so flint and steel,
 *       firestarter and similar trigger the firepit lighting routine on stove blocks.</li>
 * </ul>
 */
public final class NTEDeviceEvents
{
    private NTEDeviceEvents() {}

    public static void init()
    {
        MinecraftForge.EVENT_BUS.register(NTEDeviceEvents.class);
    }

    public static void initModBus(IEventBus modBus)
    {
        modBus.addListener(NTEDeviceEvents::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTab() == TFCCreativeTabs.FOOD.tab().get())
        {
            // Items registered by the compatibility layer are not part of
            // TFCItems.FOOD, so add their food entries explicitly. Their
            // TFC food_items definitions still provide decay, nutrition and
            // recipe behaviour at runtime.
            event.accept(NTEItems.BISON.get());
            event.accept(NTEItems.COOKED_BISON.get());
            event.accept(NTEItems.ARMADILLO.get());
            event.accept(NTEItems.COOKED_ARMADILLO.get());
            NTEItems.NEW_FRESHWATER_FISH.values().forEach(reg -> event.accept(reg.get()));
            NTEItems.NEW_COOKED_FRESHWATER_FISH.values().forEach(reg -> event.accept(reg.get()));
        }
        else if (event.getTab() == TFCCreativeTabs.MISC.tab().get())
        {
            event.accept(NTEBlocks.CACTUS_WOOD.get());
            event.accept(NTEBlocks.DRIED_CACTUS_WOOD.get());
            event.accept(NTEDevices.STOVE_ITEM.get());
            event.accept(NTEDevices.STOVE_POT_ITEM.get());
            event.accept(NTEItems.ROPE.get());
            event.accept(NTEBlocks.STEEL_ROPE_ANCHOR.get().asItem());
            NTEBlocks.METAL_ROPE_ANCHORS.values().forEach(anchor -> event.accept(anchor.get().asItem()));
            event.accept(NTEItems.ARMADILLO_SCUTE.get());
            NTEItems.NEW_FRESHWATER_FISH_BUCKETS.values().forEach(reg -> event.accept(reg.get()));
        }
    }

    @SubscribeEvent
    public static void onBlockPlacedAboveUnreinforcedAnchor(BlockEvent.EntityPlaceEvent event)
    {
        // 未加固的绳锚柱体向上伸进上方一格，所以那一格不放允许放方块；加固完成后高度回到原版，放置恢复正常。
        final BlockState below = event.getLevel().getBlockState(event.getPos().below());
        if (below.getBlock() instanceof NTEMetalRopeAnchorBlock anchor && !anchor.isReinforced(below))
        {
            // Forge 取消该事件时会回滚已经写入的方块快照，不需要额外清理。
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player)
            {
                player.displayClientMessage(Component.translatable("tfe.tooltip.rope_anchor.occupied"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onFireStart(StartFireEvent event)
    {
        if (!event.isStrong()) return;
        final Level level = event.getLevel();
        final BlockState state = event.getState();
        final Block block = state.getBlock();
        if (block == NTEDevices.STOVE.get() || block == NTEDevices.STOVE_POT.get())
        {
            final BlockEntity entity = level.getBlockEntity(event.getPos());
            if (entity instanceof AbstractFirepitBlockEntity<?> firepit && firepit.light(state))
            {
                event.setCanceled(true);
                if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                {
                    TFCAdvancements.LIT.trigger(serverPlayer, state);
                }
            }
        }
    }
}
