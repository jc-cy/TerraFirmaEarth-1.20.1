package com.newterraearth.tfe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.dries007.tfc.common.blockentities.CropBlockEntity;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity;
import net.dries007.tfc.common.blockentities.IFarmland;
import net.dries007.tfc.common.blocks.crop.CropHelpers;
import net.dries007.tfc.common.blocks.crop.ICropBlock;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.Fertilizer;
import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.util.climate.ClimateRange.Result;

import com.newterraearth.tfe.world.NTESoilFertility;
import com.newterraearth.tfe.world.NTESeasonalHelpers;
import com.newterraearth.tfe.world.crop.NTECropTemperatureAccess;
import com.newterraearth.tfe.world.crop.NTECropTemperatureModel;

@Mixin(value = CropHelpers.class, remap = false, priority = 900)
public abstract class CropHelpersMixin
{
    @Redirect(
        method = "growthTickStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/common/blocks/crop/ICropBlock;die(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"
        ),
        remap = false,
        require = 1
    )
    private static void tfe$setHealthToZeroOnDeath(
        ICropBlock cropBlock,
        Level level,
        BlockPos pos,
        BlockState state,
        boolean mature
    )
    {
        if (level.getBlockEntity(pos) instanceof NTECropTemperatureAccess stress)
        {
            stress.tfe$setTemperatureStress(NTECropTemperatureModel.STRESS_LIMIT);
        }
        cropBlock.die(level, pos, state, mature);
    }

    @Redirect(
        method = "growthTickStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/common/blocks/crop/CropHelpers;checkClimate(Lnet/dries007/tfc/util/climate/ClimateRange;IFFZ)Z"
        ),
        remap = false,
        require = 2
    )
    private static boolean tfe$trackCropHealth(
        ClimateRange range,
        int hydration,
        float fromTemperature,
        float toTemperature,
        boolean allowWiggle,
        Level level,
        BlockPos pos,
        BlockState state,
        RandomSource random,
        long fromTick,
        long toTick,
        CropBlockEntity crop
    )
    {
        final NTECropTemperatureAccess stress = (NTECropTemperatureAccess) (Object) crop;
        final int currentStress = stress.tfe$getTemperatureStress();

        // 成熟后健康固定为满值：不再累计损失，也不会因健康枯死，只保留 TFC 自身的过熟（expiry）死亡。
        if (crop.getGrowth() >= CropHelpers.GROWTH_LIMIT)
        {
            stress.tfe$setTemperatureStress(NTECropTemperatureModel.MATURE_STRESS);
            return true;
        }

        if (!allowWiggle)
        {
            final boolean climateValid = range.checkBoth(hydration, fromTemperature, false)
                && range.checkTemperature(toTemperature, false) == Result.VALID;
            final int updatedStress = NTECropTemperatureModel.updateStress(
                currentStress,
                climateValid,
                tfe$hasFertilizer(level, pos, state)
            );
            stress.tfe$setTemperatureStress(updatedStress);
            return climateValid && !NTECropTemperatureModel.hasFatalStress(updatedStress);
        }

        if (withinDeathRange(range, fromTemperature) && withinDeathRange(range, toTemperature))
        {
            return !NTECropTemperatureModel.hasFatalStress(currentStress);
        }

        // 极端温度不再一击枯死：每次扣 45% 健康，连续三次才会枯死。
        final int updatedStress = NTECropTemperatureModel.applyHardDamage(currentStress);
        stress.tfe$setTemperatureStress(updatedStress);
        return !NTECropTemperatureModel.hasFatalStress(updatedStress);
    }

    /**
     * 耕地仍有该作物需要的养分（施过肥或尚未耗尽）时，健康恢复速度翻倍。判定取作物的主养分槽，
     * 避免被上茬作物留下的其他养分误判成「有肥料」。
     */
    private static boolean tfe$hasFertilizer(Level level, BlockPos pos, BlockState state)
    {
        if (!(state.getBlock() instanceof ICropBlock cropBlock)
            || !(level.getBlockEntity(pos.below()) instanceof IFarmland farmland))
        {
            return false;
        }
        final FarmlandBlockEntity.NutrientType type = cropBlock.getPrimaryNutrient();
        return type != null && farmland.getNutrient(type) > 0;
    }

    private static boolean withinDeathRange(ClimateRange range, float temperature)
    {
        return NTECropTemperatureModel.withinDeathRange(
            range.getMinTemperature(false),
            range.getMaxTemperature(false),
            temperature
        );
    }

    @Redirect(
        method = "growthTickStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/common/blocks/soil/FarmlandBlock;getHydration(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)I"
        ),
        remap = false,
        require = 0
    )
    private static int tfe$useConfiguredCropHydration(
        LevelAccessor levelAccessor,
        BlockPos sourcePos,
        Level level,
        BlockPos pos,
        BlockState state,
        RandomSource random,
        long fromTick,
        long toTick,
        CropBlockEntity crop
    )
    {
        final var calendar = Calendars.get(level);
        return NTESeasonalHelpers.getConfiguredCropHydration(level, sourcePos, calendar.ticksToCalendarTicks(toTick), calendar.getCalendarDaysInMonth());
    }

    @Redirect(
        method = "growthTickStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/util/climate/Climate;getTemperature(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/dries007/tfc/util/calendar/ICalendar;J)F"
        ),
        remap = false,
        require = 0
    )
    private static float tfe$usePlantTemperatureForCropGrowth(Level level, BlockPos pos, ICalendar calendar, long calendarTick)
    {
        return NTESeasonalHelpers.getPlantTemperature(level, pos, calendarTick, calendar.getCalendarDaysInMonth());
    }

    @Redirect(
        method = "growthTickStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/common/blockentities/IFarmland;getNutrient(Lnet/dries007/tfc/common/blockentities/FarmlandBlockEntity$NutrientType;)F"
        ),
        remap = false,
        require = 0
    )
    private static float tfe$scaleAvailableNutrients(
        IFarmland farmland,
        FarmlandBlockEntity.NutrientType type,
        Level level,
        BlockPos pos,
        BlockState state,
        RandomSource random,
        long fromTick,
        long toTick,
        CropBlockEntity crop
    )
    {
        return farmland.getNutrient(type) * modifierAt(level, pos.below());
    }

    @Redirect(
        method = "growthTickStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/common/blockentities/IFarmland;consumeNutrientAndResupplyOthers(Lnet/dries007/tfc/common/blockentities/FarmlandBlockEntity$NutrientType;F)F"
        ),
        remap = false,
        require = 0
    )
    private static float tfe$scaleConsumedNutrients(
        IFarmland farmland,
        FarmlandBlockEntity.NutrientType type,
        float amount,
        Level level,
        BlockPos pos,
        BlockState state,
        RandomSource random,
        long fromTick,
        long toTick,
        CropBlockEntity crop
    )
    {
        return farmland.consumeNutrientAndResupplyOthers(type, amount) * modifierAt(level, pos.below());
    }

    @Redirect(
        method = "useFertilizer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/common/blocks/crop/CropHelpers;minAmountRequiredToNextFillBar(Lnet/dries007/tfc/common/blockentities/IFarmland;Lnet/dries007/tfc/util/Fertilizer;Lnet/dries007/tfc/common/blockentities/FarmlandBlockEntity$NutrientType;I)I"
        ),
        remap = false,
        require = 0
    )
    private static int tfe$scaleShiftFertilizerFill(
        IFarmland farmland,
        Fertilizer fertilizer,
        FarmlandBlockEntity.NutrientType type,
        int prevValue,
        Level level,
        Player player,
        InteractionHand hand,
        BlockPos farmlandPos
    )
    {
        final float amount = fertilizer.getNutrient(type) * modifierAt(level, farmlandPos);
        if (amount > 0 && farmland.getNutrient(type) < 1)
        {
            final int requiredValue = Mth.ceil((1 - farmland.getNutrient(type)) / amount);
            if (prevValue == -1 || requiredValue < prevValue)
            {
                return requiredValue;
            }
        }
        return prevValue;
    }

    @Redirect(
        method = "useFertilizer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/common/blockentities/IFarmland;addNutrients(Lnet/dries007/tfc/util/Fertilizer;F)V"
        ),
        remap = false,
        require = 0
    )
    private static void tfe$scaleAppliedFertilizer(
        IFarmland farmland,
        Fertilizer fertilizer,
        float multiplier,
        Level level,
        Player player,
        InteractionHand hand,
        BlockPos farmlandPos
    )
    {
        farmland.addNutrients(fertilizer, multiplier * modifierAt(level, farmlandPos));
    }

    private static float modifierAt(Level level, BlockPos pos)
    {
        return NTESoilFertility.getModifier(level.getBlockState(pos));
    }
}
