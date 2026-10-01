package com.newterraearth.tfe.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import net.dries007.tfc.client.ClientHelpers;
import net.dries007.tfc.client.TFCColors;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.climate.Climate;

import com.newterraearth.tfe.client.NTEClimateRenderHelpers;
import com.newterraearth.tfe.client.NTEFoliageColors;

@Mixin(value = TFCColors.class, remap = false)
public abstract class TFCColorsMixin
{
    @Shadow @Final private static int[] FOLIAGE_FALL_COLORS_CACHE;

    /**
     * @author Codex
     * @reason Seasonal foliage colour ported from TFC 4.2.9 (1.21) {@code TFCColors#getSeasonalFoliageColor}, with two
     * TFE changes: the green season colour is read from a bundled 1.21 colormap, and the season window keeps the 1.20
     * elevation adjusted temperature at the block instead of the 1.21 sea level sample (so high ground turns earlier,
     * see the note in the body). 1.21 adds the evergreen climate and dry season branches, samples the spring / summer
     * colour by time of year and groundwater rather than by temperature, and uses one uniform winter brown. The two
     * values 1.20 does not expose (rainfall variance and instant groundwater) come from the TFE climate helpers, which
     * read the same synced client data the existing grass / foliage sampling uses.
     */
    @Overwrite(remap = false)
    private static int getSeasonalFoliageColor(BlockPos pos, LevelAccessor level, int autumnIndex)
    {
        final int[] foliageColors = NTEFoliageColors.getFoliage121();
        if (!(level instanceof Level actualLevel))
        {
            return getClimateColor(foliageColors, pos);
        }

        // Deliberate deviation from TFC 1.21, per user decision: keep the elevation adjusted average temperature at the
        // block, like TFC 1.20 does, so higher ground turns earlier. Real autumn colour does move down the slopes, which
        // the upstream sea level sample loses. Rainfall is a 2D field (ChunkData#getRainfall ignores y), so the dry
        // season branch below behaves the same either way.
        float temp = NTEClimateRenderHelpers.getAdjustedAverageTemperature(actualLevel, pos);
        // 1.20 has no Climate#getRainfallVariance; the synced client cache carries the same value. Before that sync
        // arrives the variance is unknown, and "no variance" is the neutral choice (no dry season).
        float rainVar = NTEClimateRenderHelpers.getRainVarianceOrNaN(actualLevel, pos);
        if (Float.isNaN(rainVar))
        {
            rainVar = 0f;
        }

        // Fuzz the two transition boundaries so block noise, not the biome edge, decides the last few blocks.
        if ((temp > 11.7f && temp < 12.8f) || (rainVar > 0.38f && rainVar < 0.42f))
        {
            final int positionClimateHash = (Helpers.hash(912381187503828153L, pos) & 127);
            temp += (float) (positionClimateHash - 63) / 4000f;
            rainVar += (float) (positionClimateHash - 63) / 60000f;
        }
        final float rainVarAbs = Math.abs(rainVar);

        // Evergreen climate: warm and wet enough to keep its leaves all year.
        if (temp > 12f && rainVarAbs < 0.4f)
        {
            return nte$getGreenSeasonFoliageColor(actualLevel, pos);
        }

        final float x;
        float seasonOffset = 0f;
        if (temp <= 12f)
        {
            // Chosen so -20C gets a 2.5 month summer and 15C gets a year round one.
            x = 1.25f * Math.max(temp, -20f) + 7.6f;
            if (!NTEClimateRenderHelpers.isNorthernHemisphere(actualLevel, pos))
            {
                seasonOffset = 0.5f;
            }
        }
        else
        {
            final float averageRain = Climate.getRainfall(actualLevel, pos);
            final float minimumRain = averageRain * (1f - rainVarAbs);
            if (rainVarAbs > 0.4f && temp > 12.5f && minimumRain <= 120f)
            {
                // Dry season controlled climate: the wet season takes the place of summer.
                if (rainVar < 0f)
                {
                    seasonOffset = 0.5f;
                }
                x = -0.2604f * (0.4f - rainVarAbs) * (120f - minimumRain) + 18.75f + 5.3f;
            }
            else
            {
                // Evergreen border belt between the winter controlled and the dry season controlled climates.
                return nte$getGreenSeasonFoliageColor(actualLevel, pos);
            }
        }

        final float cubedTerm = x * x * x / 4096f; // 1 / 16^3
        final float squaredTerm = x * x / 256f; // 1 / 16^2

        // Positional hashing fuzzes the time of year per block, then the hemisphere / dry season offset shifts it.
        final int positionDeltaHash = (Helpers.hash(836494187578334123L, pos) & 127);
        final float timeOfYear = (1f + Calendars.CLIENT.getCalendarFractionOfYear() + seasonOffset + (positionDeltaHash - 63) / 4096f) % 1f;

        final float autumnStart = (cubedTerm - squaredTerm + 8.5f) / 12f;
        final float autumnEnd = (cubedTerm - squaredTerm + 10.5f) / 12f;

        if (timeOfYear > autumnEnd)
        {
            return 0x7c592b;
        }
        else if (timeOfYear > autumnStart)
        {
            return nte$getAutumnColor(FOLIAGE_FALL_COLORS_CACHE, timeOfYear, autumnStart, autumnEnd, pos, autumnIndex);
        }
        final float springStart = 1f - autumnEnd;
        if (timeOfYear > springStart)
        {
            return nte$getSpringSummerColor(foliageColors, timeOfYear, springStart, autumnStart, actualLevel, pos);
        }
        return 0x7c592b;
    }

    /**
     * @author Codex
     * @reason Sample colormaps from the ported 1.21 instant groundwater semantics when available.
     */
    @Overwrite(remap = false)
    private static int getClimateColor(int[] colorCache, BlockPos pos)
    {
        final Level level = ClientHelpers.getLevel();
        if (level != null)
        {
            final float temperature = Climate.getTemperature(level, pos);
            final float groundwater = NTEClimateRenderHelpers.getInstantGroundwater(level, pos);
            return nte$getClimateColor(colorCache, temperature, groundwater);
        }
        return 0;
    }

    @Unique
    private static int nte$getClimateColor(int[] colorCache, float temperature, float groundwater)
    {
        final int temperatureIndex = 255 - Mth.clamp((int) ((temperature + 20f) * 255f / 50f), 0, 255);
        final int groundwaterIndex = 255 - Mth.clamp((int) (groundwater * 255f / 500f), 0, 255);
        return colorCache[temperatureIndex | (groundwaterIndex << 8)];
    }

    /**
     * 1.21 samples the summer colormap (the same {@code foliage.png} as {@code FOLIAGE_COLORS_CACHE}) horizontally by
     * time of year and vertically by groundwater, so a larger daily temperature swing cannot move the green.
     */
    @Unique
    private static int nte$getSpringSummerColor(int[] colorCache, float timeOfYear, float springStart, float autumnStart, Level level, BlockPos pos)
    {
        final float groundwater = NTEClimateRenderHelpers.getInstantGroundwater(level, pos);
        final int summerProgressIndex = Mth.clamp((int) (255f * (timeOfYear - springStart) / (autumnStart - springStart)), 0, 255);
        final int groundwaterIndex = 255 - Mth.clamp((int) (groundwater * 255f / 500f), 0, 255);
        return colorCache[summerProgressIndex | (groundwaterIndex << 8)];
    }

    @Unique
    private static int nte$getGreenSeasonFoliageColor(Level level, BlockPos pos)
    {
        final float groundwater = NTEClimateRenderHelpers.getInstantGroundwater(level, pos);
        final int groundwaterIndex = 255 - Mth.clamp((int) (groundwater * 255f / 500f), 0, 255);
        return NTEFoliageColors.getFoliage121()[127 | (groundwaterIndex << 8)];
    }

    /**
     * @author Codex
     * @reason 1.21 reads the conifer / default foliage colour from the same replaced {@code foliage.png} as the green
     * season. TFC 1.20 loaded the old image there, so the ported algorithm has to supply the 1.21 one, otherwise
     * conifers and deciduous trees in the same forest would be tinted from two different palettes.
     */
    @Overwrite(remap = false)
    public static int getFoliageColor(BlockPos pos, int tintIndex)
    {
        final int[] foliageColors = NTEFoliageColors.getFoliage121();
        if (tintIndex == 0)
        {
            if (pos != null)
            {
                return getClimateColor(foliageColors, pos);
            }
            return nte$getClimateColor(foliageColors, 10f, 250f); // Default values
        }
        return -1;
    }

    @Unique
    private static int nte$getAutumnColor(int[] colorCache, float timeOfYear, float autumnStart, float autumnEnd, BlockPos pos, int autumnIndex)
    {
        final int positionDeltaHash = (Helpers.hash(836494186029734123L, pos) & 127) - 63;
        final int autumnProgressIndex = (int) Mth.clamp(255f * (timeOfYear - autumnStart) / (autumnEnd - autumnStart) + positionDeltaHash, 0, 255);
        return colorCache[autumnProgressIndex | (autumnIndex << 8)];
    }
}
