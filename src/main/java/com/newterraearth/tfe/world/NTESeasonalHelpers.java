package com.newterraearth.tfe.world;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.util.calendar.Season;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.ClimateModel;
import net.dries007.tfc.util.climate.ClimateRange;
import net.dries007.tfc.util.climate.OverworldClimateModel;
import net.dries007.tfc.world.chunkdata.ChunkData;

import com.newterraearth.tfe.client.NTEClientRainVarianceCache;
import com.newterraearth.tfe.compat.NTEFirmalifeGreenhouseCompat;
import com.newterraearth.tfe.config.NTECommonConfig;
import com.newterraearth.tfe.mixin.SeasonalPlantBlockAccessor;
import com.newterraearth.tfe.world.plant.NTEHarvestDormancyAccess;

public final class NTESeasonalHelpers
{
    private static final float DEFAULT_HEMISPHERE_SCALE = 20_000f;
    private static final int MAX_RAIN_HYDRATION = 60;

    @Nullable private static Field temperatureScaleField;
    private static boolean lookedUpClimateFields;

    @Nullable private static volatile Level cachedGeneratorLevel;
    @Nullable private static volatile ChunkGenerator cachedChunkGenerator;
    @Nullable private static volatile Class<?> cachedChunkSourceClass;
    @Nullable private static volatile Method cachedChunkGeneratorMethod;

    private NTESeasonalHelpers()
    {
    }

    public static boolean canPlantSpread(Level level, RandomSource random, BlockPos pos)
    {
        return random.nextFloat() < TFCConfig.SERVER.plantSpreadChance.get()
            && getHemispheralCalendarMonthOfYear(level, pos).getSeason() != Season.WINTER;
    }

    public static Month getHemispheralCalendarMonthOfYear(Level level, BlockPos pos)
    {
        return getHemispheralCalendarMonthOfYear(level, pos, Calendars.get(level).getCalendarTicks());
    }

    public static Month getHemispheralCalendarMonthOfYear(Level level, BlockPos pos, long calendarTicks)
    {
        final ICalendar calendar = Calendars.get(level);
        long adjustedTicks = calendarTicks;
        if (!isNorthernHemisphere(level, pos))
        {
            adjustedTicks += calendar.getCalendarTicksInYear() / 2L;
        }
        return ICalendar.getMonthOfYear(adjustedTicks, calendar.getCalendarDaysInMonth());
    }

    public static boolean isNorthernHemisphere(Level level, BlockPos pos)
    {
        return NTE121ClimateHelpers.isNorthernHemisphere(pos.getZ(), getHemisphereScale(level));
    }

    public static float getInstantRainfall(Level level, BlockPos pos)
    {
        final ICalendar calendar = Calendars.get(level);
        return getInstantRainfall(level, pos, calendar.getCalendarTicks(), calendar.getCalendarDaysInMonth());
    }

    public static float getInstantRainfall(Level level, BlockPos pos, long calendarTicks, int daysInMonth)
    {
        final float averageRainfall = Climate.getRainfall(level, pos);
        return getInstantRainfall(level, pos, calendarTicks, daysInMonth, averageRainfall);
    }

    public static float getInstantRainfall(Level level, BlockPos pos, long calendarTicks, int daysInMonth, float averageRainfall)
    {
        final float rainVariance = getRainVariance(level, pos);
        if (Float.isNaN(rainVariance) || rainVariance == 0f)
        {
            return averageRainfall;
        }

        return Helpers.triangle(rainVariance * averageRainfall, averageRainfall, 1f, getCalendarFractionOfYear(level, calendarTicks, daysInMonth) + 0.75f);
    }

    public static int getFruitBushHydrationFromRootPos(Level level, BlockPos rootPos)
    {
        if (useAverageHydrationInControlledGreenhouse(level, rootPos))
        {
            return FarmlandBlock.getHydration(level, rootPos);
        }
        return NTECommonConfig.useCurrentRainfallForFruit()
            ? getCurrentHydration(level, rootPos)
            : FarmlandBlock.getHydration(level, rootPos);
    }

    public static int getFruitTreeSaplingHydration(Level level, BlockPos rootPos)
    {
        if (useAverageHydrationInControlledGreenhouse(level, rootPos))
        {
            return FarmlandBlock.getHydration(level, rootPos);
        }
        if (!NTECommonConfig.useCurrentRainfallForFruit())
        {
            return FarmlandBlock.getHydration(level, rootPos);
        }
        return getCurrentHydration(level, rootPos);
    }

    public static int getConfiguredCropHydration(Level level, BlockPos rootPos)
    {
        if (useAverageHydrationInControlledGreenhouse(level, rootPos))
        {
            return FarmlandBlock.getHydration(level, rootPos);
        }
        if (!NTECommonConfig.useCurrentRainfallForCrops())
        {
            return FarmlandBlock.getHydration(level, rootPos);
        }
        return getCurrentHydration(level, rootPos);
    }

    public static int getConfiguredCropHydration(Level level, BlockPos rootPos, long calendarTicks, int daysInMonth)
    {
        if (useAverageHydrationInControlledGreenhouse(level, rootPos))
        {
            return FarmlandBlock.getHydration(level, rootPos);
        }
        if (!NTECommonConfig.useCurrentRainfallForCrops())
        {
            return FarmlandBlock.getHydration(level, rootPos);
        }
        return getCurrentHydration(level, rootPos, calendarTicks, daysInMonth);
    }

    public static int getInstantRainHydration(float rainfall)
    {
        return (int) Mth.clampedMap(rainfall, ClimateModel.MINIMUM_RAINFALL, ClimateModel.MAXIMUM_RAINFALL, 0f, MAX_RAIN_HYDRATION);
    }

    public static int getAverageRainHydration(LevelAccessor level, BlockPos pos)
    {
        return getInstantRainHydration(ChunkData.get(level, pos).getRainfall(pos));
    }

    public static int getInstantHydrationFromRainHydration(Level level, BlockPos pos, int rainHydration)
    {
        if (Helpers.isFluid(level.getFluidState(pos.above()), TFCTags.Fluids.HYDRATING))
        {
            return 100;
        }
        final int waterBoost = 20 * (5 - findMinCostHydratingSource(level, pos));
        return Mth.clamp(waterBoost + rainHydration, 0, 100);
    }

    public static int getCurrentHydration(Level level, BlockPos pos)
    {
        final ICalendar calendar = Calendars.get(level);
        return getCurrentHydration(level, pos, calendar.getCalendarTicks(), calendar.getCalendarDaysInMonth());
    }

    public static int getCurrentHydration(Level level, BlockPos pos, long calendarTicks, int daysInMonth)
    {
        final float rainfall = getInstantRainfall(level, pos, calendarTicks, daysInMonth);
        return getInstantHydrationFromRainHydration(level, pos, getInstantRainHydration(rainfall));
    }

    public static float getPlantTemperature(Level level, BlockPos pos)
    {
        final ICalendar calendar = Calendars.get(level);
        return getPlantTemperature(level, pos, calendar.getCalendarTicks(), calendar.getCalendarDaysInMonth());
    }

    public static float getPlantTemperature(Level level, BlockPos pos, long calendarTicks, int daysInMonth)
    {
        final float temperature = Climate.getTemperature(level, pos, calendarTicks, daysInMonth);
        return NTEFirmalifeGreenhouseCompat.getControlledTemperature(level, pos, temperature);
    }

    public static Component getPlantTemperatureTooltip(Level level, BlockPos pos, ClimateRange validRange, boolean allowWiggle)
    {
        return FarmlandBlock.getTemperatureTooltip(level, pos, validRange, getPlantTemperature(level, pos), allowWiggle, "tfc.tooltip.farmland.temperature");
    }

    public static Component getAveragePlantTemperatureTooltip(Level level, BlockPos pos)
    {
        return Component.translatable("tfe.tooltip.climate_average_temperature", String.format(Locale.ROOT, "%.1f", Climate.getAverageTemperature(level, pos)));
    }

    public static void addPlantClimateTooltips(List<Component> text, Level level, BlockPos temperaturePos, BlockPos hydrationPos, ClimateRange validRange, int hydration)
    {
        final boolean hideAverages = isInControlledGreenhouse(level, temperaturePos) || isInControlledGreenhouse(level, hydrationPos);
        text.add(getPlantTemperatureTooltip(level, temperaturePos, validRange, false));
        if (!hideAverages)
        {
            text.add(getAveragePlantTemperatureTooltip(level, temperaturePos));
        }
        text.add(FarmlandBlock.getHydrationTooltip(level, hydrationPos, validRange, false, hydration));
        if (!hideAverages)
        {
            text.add(getAverageHydrationTooltip(level, hydrationPos));
        }
    }

    public static void addAverageHydrationTooltipIfNeeded(List<Component> text, LevelAccessor level, BlockPos pos, ClimateRange validRange, boolean allowWiggle, boolean showAverage)
    {
        if (showAverage && !useAverageHydrationInControlledGreenhouse(level, pos))
        {
            text.add(getAverageHydrationTooltip(level, pos));
        }
    }

    // ===== 采摘休眠期（TFC 1.20 缺失、1.21 本体自带 fruitPickBloomDelayTicks 的机制） =====

    /**
     * 采摘休眠期长度（游戏 tick）。配置按"游戏日"给出，0 表示关闭。
     */
    public static long getHarvestDormancyDelay()
    {
        return Math.max(0, NTECommonConfig.getFruitPickDormancyDays()) * (long) ICalendar.TICKS_IN_DAY;
    }

    public static long getHarvestDormancyRemaining(Level level, BlockPos pos)
    {
        if (!(level.getBlockEntity(pos) instanceof NTEHarvestDormancyAccess access))
        {
            return 0L;
        }
        final long delay = getHarvestDormancyDelay();
        final long harvested = access.tfe$getHarvestedTick();
        if (delay <= 0L || harvested <= -1L)
        {
            return 0L;
        }
        return Math.max(0L, delay - (Calendars.get(level).getTicks() - harvested));
    }

    public static boolean isHarvestDormant(Level level, BlockPos pos)
    {
        return getHarvestDormancyRemaining(level, pos) > 0L;
    }

    public static void markHarvested(Level level, BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof NTEHarvestDormancyAccess access)
        {
            access.tfe$setHarvestedTick(Calendars.get(level).getTicks());
        }
    }

    /**
     * 生命周期推进一格，但采摘休眠期内不允许进入开花；与 TFC 1.21 的写回守卫同义。
     */
    public static Lifecycle advanceLifecycle(Level level, BlockPos pos, Lifecycle current, Lifecycle target)
    {
        final Lifecycle next = current.advanceTowards(target);
        return next == Lifecycle.FLOWERING && isHarvestDormant(level, pos) ? Lifecycle.HEALTHY : next;
    }

    /**
     * 期望生命周期：受控温室内无视季节恒为结果期（由 TFCModernLife 提供温室判定与受控温度），否则用半球月份表。
     */
    public static Lifecycle getExpectedLifecycle(SeasonalPlantBlockAccessor accessor, Level level, BlockPos pos, Month month)
    {
        if (NTEFirmalifeGreenhouseCompat.isControlledGreenhouse(level, pos))
        {
            return Lifecycle.FRUITING;
        }
        return accessor.tfe$invokeGetLifecycleForMonth(month);
    }

    /**
     * 果树树苗是否处于可生长期：半球月份表命中，或处于受控温室（温室内跨季节）。
     */
    public static boolean isSaplingGrowing(Level level, BlockPos pos, Lifecycle[] stages)
    {
        return stages[getHemispheralCalendarMonthOfYear(level, pos).ordinal()].active()
            || NTEFirmalifeGreenhouseCompat.isControlledGreenhouse(level, pos);
    }

    public static BlockPos getFruitTreeStemPos(LevelReader level, BlockPos pos)
    {
        if (Helpers.isBlock(level.getBlockState(pos), TFCTags.Blocks.FRUIT_TREE_BRANCH))
        {
            return findFruitTreeBase(level, pos);
        }
        if (Helpers.isBlock(level.getBlockState(pos.below()), TFCTags.Blocks.FRUIT_TREE_BRANCH))
        {
            return findFruitTreeBase(level, pos.below());
        }

        for (Direction direction : Helpers.DIRECTIONS)
        {
            final BlockPos adjacentPos = pos.relative(direction);
            if (Helpers.isBlock(level.getBlockState(adjacentPos), TFCTags.Blocks.FRUIT_TREE_BRANCH))
            {
                return findFruitTreeBase(level, adjacentPos);
            }
        }
        return pos;
    }

    public static BlockPos getFruitTreeRootPos(LevelReader level, BlockPos pos)
    {
        return getFruitTreeStemPos(level, pos).below();
    }

    public static BlockPos getFruitTreeSaplingStemPos(LevelReader level, BlockPos pos)
    {
        final BlockPos belowPos = pos.below();
        return Helpers.isBlock(level.getBlockState(belowPos), TFCTags.Blocks.FRUIT_TREE_BRANCH) ? findFruitTreeBase(level, belowPos) : pos;
    }

    public static BlockPos getBananaRootPos(LevelReader level, BlockPos pos)
    {
        final BlockState state = level.getBlockState(pos);
        if (state.isAir())
        {
            return pos;
        }

        final BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < 16; i++)
        {
            final BlockPos belowPos = cursor.below();
            if (level.getBlockState(belowPos).getBlock() != state.getBlock())
            {
                break;
            }
            cursor.move(Direction.DOWN);
        }
        return cursor.immutable();
    }

    public static float getHemisphereScale(@Nullable Level level)
    {
        if (level == null)
        {
            return DEFAULT_HEMISPHERE_SCALE;
        }

        if (level instanceof ServerLevel serverLevel)
        {
            final ChunkGenerator generator = serverLevel.getChunkSource().getGenerator();
            if (generator instanceof net.dries007.tfc.world.ChunkGeneratorExtension extension)
            {
                return extension.settings().temperatureScale();
            }
        }

        final OverworldClimateModel model = OverworldClimateModel.getIfPresent(level);
        if (model == null)
        {
            return DEFAULT_HEMISPHERE_SCALE;
        }

        ensureClimateFields();
        if (temperatureScaleField != null)
        {
            try
            {
                return temperatureScaleField.getFloat(model);
            }
            catch (IllegalAccessException ignored)
            {
            }
        }
        return DEFAULT_HEMISPHERE_SCALE;
    }

    @Nullable
    public static ChunkGenerator getChunkGenerator(Level level)
    {
        if (cachedGeneratorLevel != level)
        {
            synchronized (NTESeasonalHelpers.class)
            {
                if (cachedGeneratorLevel != level)
                {
                    cachedGeneratorLevel = level;
                    cachedChunkGenerator = resolveChunkGenerator(level);
                }
            }
        }
        return cachedChunkGenerator;
    }

    private static float getCalendarFractionOfYear(Level level, long calendarTicks, int daysInMonth)
    {
        final ICalendar calendar = Calendars.get(level);
        final long ticksInYear = calendar.getCalendarTicksInYear();
        if (ticksInYear <= 0L)
        {
            return 0f;
        }

        final long normalizedTicks = Math.floorMod(calendarTicks, ticksInYear);
        final int resolvedDaysInMonth = daysInMonth > 0 ? daysInMonth : calendar.getCalendarDaysInMonth();
        final long resolvedTicksInYear = (long) ICalendar.TICKS_IN_DAY * resolvedDaysInMonth * 12L;
        return normalizedTicks / (float) (resolvedTicksInYear > 0L ? resolvedTicksInYear : ticksInYear);
    }

    private static Component getAverageHydrationTooltip(LevelAccessor level, BlockPos pos)
    {
        return Component.translatable("tfc.tooltip.farmland.average_hydration", getAverageRainHydration(level, pos));
    }

    public static boolean useAverageHydrationInControlledGreenhouse(LevelAccessor level, BlockPos pos)
    {
        return isInControlledGreenhouse(level, pos);
    }

    public static boolean isInControlledGreenhouse(LevelAccessor level, BlockPos pos)
    {
        return level instanceof Level world && (
            NTEFirmalifeGreenhouseCompat.isControlledGreenhouse(world, pos)
                || NTEFirmalifeGreenhouseCompat.isControlledGreenhouse(world, pos.above())
        );
    }

    private static BlockPos findFruitTreeBase(LevelReader level, BlockPos startPos)
    {
        final BlockPos.MutableBlockPos cursor = startPos.mutable();
        for (int i = 0; i < 32; i++)
        {
            final BlockPos belowPos = cursor.below();
            if (!Helpers.isBlock(level.getBlockState(belowPos), TFCTags.Blocks.FRUIT_TREE_BRANCH))
            {
                break;
            }
            cursor.move(Direction.DOWN);
        }

        final BlockState state = level.getBlockState(cursor);
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            final var property = PipeBlock.PROPERTY_BY_DIRECTION.get(direction);
            if (state.hasProperty(property) && state.getValue(property))
            {
                final BlockPos sidePos = cursor.relative(direction);
                if (Helpers.isBlock(level.getBlockState(sidePos), TFCTags.Blocks.FRUIT_TREE_BRANCH))
                {
                    final BlockPos.MutableBlockPos sideCursor = sidePos.mutable();
                    for (int i = 0; i < 32; i++)
                    {
                        final BlockPos belowPos = sideCursor.below();
                        if (!Helpers.isBlock(level.getBlockState(belowPos), TFCTags.Blocks.FRUIT_TREE_BRANCH))
                        {
                            break;
                        }
                        sideCursor.move(Direction.DOWN);
                    }
                    if (sideCursor.getY() < cursor.getY())
                    {
                        return sideCursor.immutable();
                    }
                }
            }
        }
        return cursor.immutable();
    }

    private static float getRainVariance(Level level, BlockPos pos)
    {
        if (level.isClientSide)
        {
            return NTEClientRainVarianceCache.getRainVariance(pos);
        }

        return level instanceof ServerLevel serverLevel
            ? NTEClimateDisplaySync.getRainVariance(serverLevel, pos)
            : Float.NaN;
    }

    private static void ensureClimateFields()
    {
        if (lookedUpClimateFields)
        {
            return;
        }

        lookedUpClimateFields = true;
        try
        {
            temperatureScaleField = OverworldClimateModel.class.getDeclaredField("temperatureScale");
            temperatureScaleField.setAccessible(true);
        }
        catch (ReflectiveOperationException ignored)
        {
            temperatureScaleField = null;
        }

    }

    private static int findMinCostHydratingSource(LevelAccessor level, BlockPos pos)
    {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int minCostWater = 5;
        for (int dx = -4; dx <= 4; dx++)
        {
            for (int dz = -4; dz <= 4; dz++)
            {
                for (int dy = -1; dy <= 0; dy++)
                {
                    final int cost = Math.max(Math.abs(dx), Math.abs(dz)) + (-2 * dy);
                    if (cost < minCostWater && Helpers.isFluid(level.getFluidState(cursor.setWithOffset(pos, dx, dy, dz)).getType(), TFCTags.Fluids.HYDRATING))
                    {
                        minCostWater = cost;
                        if (minCostWater == 1)
                        {
                            return 1;
                        }
                    }
                }
            }
        }
        return minCostWater;
    }

    @Nullable
    private static ChunkGenerator resolveChunkGenerator(Level level)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            return serverLevel.getChunkSource().getGenerator();
        }

        try
        {
            final Object chunkSource = level.getChunkSource();
            if (chunkSource == null)
            {
                return null;
            }

            final Class<?> chunkSourceClass = chunkSource.getClass();
            Method method = cachedChunkGeneratorMethod;
            if (cachedChunkSourceClass != chunkSourceClass || method == null)
            {
                method = chunkSourceClass.getMethod("getGenerator");
                method.setAccessible(true);
                cachedChunkSourceClass = chunkSourceClass;
                cachedChunkGeneratorMethod = method;
            }

            final Object result = method.invoke(chunkSource);
            return result instanceof ChunkGenerator generator ? generator : null;
        }
        catch (ReflectiveOperationException | RuntimeException ignored)
        {
            return null;
        }
    }
}
