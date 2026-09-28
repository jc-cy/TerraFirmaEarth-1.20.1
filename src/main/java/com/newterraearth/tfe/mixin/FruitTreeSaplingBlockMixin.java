package com.newterraearth.tfe.mixin;

import java.util.List;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeSaplingBlock;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.climate.ClimateRange;

import com.newterraearth.tfe.world.NTESeasonalHelpers;

@Mixin(value = FruitTreeSaplingBlock.class, remap = false)
public abstract class FruitTreeSaplingBlockMixin
{
    @Shadow @Final private Supplier<ClimateRange> climateRange;
    @Shadow @Final private Lifecycle[] stages;

    @Shadow public abstract void createTree(Level level, BlockPos pos, BlockState state, RandomSource random);

    @Shadow public abstract int getTreeGrowthDays();

    /**
     * @author Codex
     * @reason Fruit tree sapling overlays should respect hemisphere months and base-elevation current climate.
     */
    @Overwrite(remap = false)
    public void addHoeOverlayInfo(Level level, BlockPos pos, BlockState state, List<Component> text, boolean isDebug)
    {
        final ClimateRange range = climateRange.get();
        final BlockPos stemPos = NTESeasonalHelpers.getFruitTreeSaplingStemPos(level, pos);
        final BlockPos rootPos = stemPos.below();
        final int hydration = NTESeasonalHelpers.getFruitTreeSaplingHydration(level, rootPos);

        NTESeasonalHelpers.addPlantClimateTooltips(text, level, stemPos, rootPos, range, hydration);

        if (!NTESeasonalHelpers.isSaplingGrowing(level, pos, stages))
        {
            text.add(tfe$plantStatus("tfe.jade.plant.waiting_season"));
        }
        else if (!range.checkBoth(hydration, NTESeasonalHelpers.getPlantTemperature(level, stemPos), false))
        {
            text.add(tfe$plantStatus("tfe.jade.plant.waiting_climate"));
        }
        else
        {
            text.add(tfe$plantStatus("tfe.jade.plant.growing"));
        }
        if (FruitTreeSaplingBlock.maySplice(level, pos, state))
        {
            text.add(Component.translatable("tfc.tooltip.fruit_tree.sapling_splice"));
        }
    }

    private static Component tfe$plantStatus(String translationKey)
    {
        return Component.translatable("tfe.jade.plant_status", Component.translatable(translationKey));
    }

    /**
     * @author Codex
     * @reason Fruit tree sapling growth should use hemisphere months and base-elevation current climate without climate death.
     */
    @Overwrite(remap = true)
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (NTESeasonalHelpers.isSaplingGrowing(level, pos, stages))
        {
            if (level.getBlockEntity(pos) instanceof TickCounterBlockEntity counter)
            {
                final long ticksToGrow = (long) (ICalendar.TICKS_IN_DAY * getTreeGrowthDays() * TFCConfig.SERVER.globalFruitSaplingGrowthModifier.get());
                final long elapsedTicks = counter.getTicksSinceUpdate();
                if (elapsedTicks > ticksToGrow)
                {
                    final BlockPos stemPos = NTESeasonalHelpers.getFruitTreeSaplingStemPos(level, pos);
                    final int hydration = NTESeasonalHelpers.getFruitTreeSaplingHydration(level, stemPos.below());
                    final float temp = NTESeasonalHelpers.getPlantTemperature(level, stemPos);
                    if (climateRange.get().checkBoth(hydration, temp, false))
                    {
                        createTree(level, pos, state, random);
                        final long carriedTicks = elapsedTicks - ticksToGrow;
                        if (carriedTicks > 0L && level.getBlockEntity(pos) instanceof TickCounterBlockEntity grownCounter)
                        {
                            grownCounter.reduceCounter(carriedTicks);
                        }
                    }
                }
            }
        }
    }
}
