package com.newterraearth.tfe.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.dries007.tfc.common.blockentities.BerryBushBlockEntity;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.plant.fruit.BananaPlantBlock;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.blocks.plant.fruit.SeasonalPlantBlock;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.climate.ClimateRange;

import com.newterraearth.tfe.world.NTESeasonalHelpers;

@Mixin(value = BananaPlantBlock.class, remap = false)
public abstract class BananaPlantBlockMixin
{
    @Inject(method = "getProductItem", at = @At("RETURN"), remap = false)
    private void tfe$increaseBananaYield(RandomSource random, CallbackInfoReturnable<ItemStack> callback)
    {
        callback.getReturnValue().setCount(Mth.nextInt(random, 8, 16));
    }

    /**
     * @author Codex
     * @reason Banana plant overlays should read hydration and temperature from the stalk base.
     */
    @Overwrite(remap = false)
    public void addHoeOverlayInfo(Level level, BlockPos pos, BlockState state, List<Component> text, boolean isDebug)
    {
        final ClimateRange range = ((SeasonalPlantBlockAccessor) this).tfe$getClimateRange().get();
        final BlockPos rootPos = NTESeasonalHelpers.getBananaRootPos(level, pos);

        NTESeasonalHelpers.addPlantClimateTooltips(text, level, rootPos, rootPos, range, NTESeasonalHelpers.getFruitBushHydrationFromRootPos(level, rootPos));
    }

    /**
     * @author Codex
     * @reason Banana lifecycle replay should use hemisphere months and root-position climate while preserving the 1.20 vertical growth chain.
     */
    @Overwrite(remap = false)
    public void onUpdate(Level level, BlockPos pos, BlockState state)
    {
        if (level.getBlockEntity(pos) instanceof BerryBushBlockEntity bush)
        {
            final SeasonalPlantBlockAccessor accessor = (SeasonalPlantBlockAccessor) this;
            final BlockPos rootPos = NTESeasonalHelpers.getBananaRootPos(level, pos);

            Lifecycle currentLifecycle = state.getValue(BananaPlantBlock.LIFECYCLE);
            final Month month = NTESeasonalHelpers.getHemispheralCalendarMonthOfYear(level, pos);
            Lifecycle expectedLifecycle = NTESeasonalHelpers.getExpectedLifecycle(accessor, level, pos, month);
            if (!SeasonalPlantBlock.checkAndSetDormant(level, pos, state, currentLifecycle, expectedLifecycle))
            {
                long deltaTicks = Math.min(bush.getTicksSinceBushUpdate(), Calendars.SERVER.getCalendarTicksInYear());
                long currentCalendarTick = Calendars.SERVER.getCalendarTicks();
                long nextCalendarTick = currentCalendarTick - deltaTicks;

                final ClimateRange range = accessor.tfe$getClimateRange().get();
                final int hydration = NTESeasonalHelpers.getFruitBushHydrationFromRootPos(level, rootPos);

                int stage = state.getValue(BananaPlantBlock.STAGE);
                final BlockPos abovePos = pos.above();
                BlockState newState = state;
                do
                {
                    nextCalendarTick = Math.min(nextCalendarTick + Calendars.SERVER.getCalendarTicksInMonth(), currentCalendarTick);
                    if (currentLifecycle.active() && stage < 2)
                    {
                        final BlockPos downPos = pos.below(3);
                        if (!Helpers.isBlock(level.getBlockState(abovePos), (BananaPlantBlock) (Object) this) && (level.random.nextInt(4) == 0 || Helpers.isBlock(level.getBlockState(downPos), (BananaPlantBlock) (Object) this)))
                        {
                            stage++;
                        }
                    }

                    final float temperatureAtNextTick = NTESeasonalHelpers.getPlantTemperature(level, rootPos, nextCalendarTick, Calendars.SERVER.getCalendarDaysInMonth());
                    final Lifecycle lifecycleAtNextTick = NTESeasonalHelpers.getExpectedLifecycle(accessor, level, pos,
                        NTESeasonalHelpers.getHemispheralCalendarMonthOfYear(level, pos, nextCalendarTick));
                    if (range.checkBoth(hydration, temperatureAtNextTick, false))
                    {
                        currentLifecycle = NTESeasonalHelpers.advanceLifecycle(level, pos, currentLifecycle, lifecycleAtNextTick);
                    }
                    else
                    {
                        currentLifecycle = Lifecycle.DORMANT;
                    }
                    if (stage < 2 && currentLifecycle.active())
                    {
                        currentLifecycle = Lifecycle.HEALTHY;
                    }

                    newState = state.setValue(BananaPlantBlock.STAGE, stage).setValue(BananaPlantBlock.LIFECYCLE, currentLifecycle);
                    if (stage < 2 && currentLifecycle.active() && level.isEmptyBlock(abovePos) && level.canSeeSky(abovePos))
                    {
                        final long propagatedTick = nextCalendarTick;
                        level.setBlockAndUpdate(abovePos, newState);
                        level.getBlockEntity(abovePos, TFCBlockEntities.BERRY_BUSH.get()).ifPresent(newBush -> newBush.setLastBushTick(propagatedTick));
                    }
                }
                while (nextCalendarTick < currentCalendarTick);

                if (state != newState)
                {
                    level.setBlockAndUpdate(pos, newState);
                }
            }
        }
    }
}
