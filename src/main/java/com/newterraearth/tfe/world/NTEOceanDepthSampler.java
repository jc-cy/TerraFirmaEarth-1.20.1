package com.newterraearth.tfe.world;

import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.world.BiomeNoiseSampler;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.chunkdata.ChunkDataProvider;
import net.dries007.tfc.world.chunkdata.RegionChunkDataGenerator;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.region.Region;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.region.Units;

/**
 * Applies a smooth region-scale depth curve to the open-ocean height samplers so the seafloor
 * keeps deepening as the player moves offshore instead of quickly flattening into one shelf.
 * <p>
 * The curve is the pre-4.2.9 TFE modulation that was dropped when the 4.2.9 ocean heightmaps were
 * ported (see {@code ARCHIVE-20260417-06} for the verified floors and {@code ARCHIVE-20260831-06}
 * for the removal). The stage ramp positions are unchanged; only the stage drops were recalibrated,
 * because the 4.2.9 base floors differ from the ones the original constants were derived from
 * (sea level is {@code 63}):
 * <ul>
 *     <li>{@code ocean} base floor {@code 37} → {@code 15} at offshore depth {@code 3} (drop 22)</li>
 *     <li>{@code deep_ocean} base floor {@code 17} → {@code -10} at offshore depth {@code 9} (drop 27)</li>
 *     <li>{@code deep_ocean_trench} base floor {@code 3} → capped at {@code -30} by the trench step</li>
 * </ul>
 * The original constants (20 / 9 / 14, cumulative 43) were derived from floors of {@code 35 / 39 / 33}
 * and would have pushed the 4.2.9 abyssal plain to {@code y=-26} and the trench axis to roughly
 * {@code y=-56}, i.e. within a few blocks of bedrock, so they are not reused verbatim.
 * <p>
 * Every open-ocean biome ({@code BiomeBlendType.OCEAN} terrain, not the shore-type ones) shares the
 * same drop function, so the relative relief between shelf, reef, abyssal plain, spreading ridge,
 * atolls, volcanic arc and trench is preserved and no biome-boundary steps appear.
 */
public final class NTEOceanDepthSampler implements BiomeNoiseSampler
{
    /** Offshore distance is a region-cell count; one cell is {@code 128} blocks. */
    private static final double OCEAN_STAGE_START_DEPTH = 0.5d;
    private static final double OCEAN_STAGE_FULL_DEPTH = 3d;
    private static final double REEF_STAGE_FULL_DEPTH = 5d;
    private static final double DEEP_OCEAN_STAGE_START_DEPTH = 5d;
    private static final double DEEP_OCEAN_STAGE_FULL_DEPTH = 9d;
    private static final double TRENCH_STAGE_START_DEPTH = 9.2d;
    private static final double TRENCH_STAGE_FULL_DEPTH = 10.7d;

    /** Cumulative drops: {@code 22} at depth 3, {@code 24} at depth 5, {@code 27} at depth 9. */
    private static final double OCEAN_STAGE_MAX_DROP = 22d;
    private static final double REEF_STAGE_MAX_DROP = 2d;
    private static final double DEEP_OCEAN_STAGE_MAX_DROP = 3d;

    /** Extra drop reserved for the trench core, and the floor it may never cut below. */
    private static final double TRENCH_STEP_DROP = 20d;
    private static final double TRENCH_TARGET_MIN_Y = -30d;

    private final BiomeNoiseSampler delegate;
    private final boolean trench;
    private double height;
    @Nullable private RegionGenerator regionGenerator;

    private NTEOceanDepthSampler(Noise2D baseHeight, boolean trench)
    {
        this.delegate = BiomeNoiseSampler.fromHeightNoise(baseHeight);
        this.trench = trench;
    }

    public static BiomeNoiseSampler ocean(Noise2D baseHeight)
    {
        return new NTEOceanDepthSampler(baseHeight, false);
    }

    public static BiomeNoiseSampler trench(Noise2D baseHeight)
    {
        return new NTEOceanDepthSampler(baseHeight, true);
    }

    @Override
    public void prepare(ChunkGeneratorExtension generator, @Nullable ChunkAccess chunk)
    {
        delegate.prepare(generator, chunk);

        final ChunkDataProvider provider = generator.chunkDataProvider();
        if (provider.generator() instanceof RegionChunkDataGenerator regionChunkDataGenerator)
        {
            regionGenerator = regionChunkDataGenerator.regionGenerator();
        }
        else
        {
            regionGenerator = null;
        }
    }

    @Override
    public void setColumn(int x, int z)
    {
        delegate.setColumn(x, z);
        height = delegate.height() - sampleDepthDrop(x, z);
    }

    @Override
    public double height()
    {
        return height;
    }

    @Override
    public double noise(int y)
    {
        return delegate.noise(y);
    }

    private double sampleDepthDrop(int blockX, int blockZ)
    {
        if (regionGenerator == null)
        {
            return 0d;
        }
        return depthDrop(sampleBaseOceanDepth(blockX, blockZ), trench, delegate.height());
    }

    /**
     * The shared offshore drop curve, in blocks. {@code oceanDepth} is the region-scale distance
     * from land (1.20 {@code Region.Point.baseOceanDepth}, in region cells); {@code baseHeight} is
     * the biome's undropped height, only used to keep the trench step above its floor.
     */
    static double depthDrop(double oceanDepth, boolean trench, double baseHeight)
    {
        double drop = OCEAN_STAGE_MAX_DROP * smoothstep(OCEAN_STAGE_START_DEPTH, OCEAN_STAGE_FULL_DEPTH, oceanDepth);
        drop += REEF_STAGE_MAX_DROP * smoothstep(OCEAN_STAGE_FULL_DEPTH, REEF_STAGE_FULL_DEPTH, oceanDepth);
        drop += DEEP_OCEAN_STAGE_MAX_DROP * smoothstep(DEEP_OCEAN_STAGE_START_DEPTH, DEEP_OCEAN_STAGE_FULL_DEPTH, oceanDepth);

        if (trench)
        {
            // Only apply the trench-specific step to the trench core, so the surrounding deep ocean
            // keeps its own decorations instead of being pulled into one huge flat basin.
            final double trenchFactor = smoothstep(TRENCH_STAGE_START_DEPTH, TRENCH_STAGE_FULL_DEPTH, oceanDepth);
            final double maxExtraDrop = Math.max(0d, baseHeight - drop - TRENCH_TARGET_MIN_Y);
            drop += Math.min(TRENCH_STEP_DROP * trenchFactor, maxExtraDrop);
        }
        return drop;
    }

    private double sampleBaseOceanDepth(int blockX, int blockZ)
    {
        final int gridX = Units.blockToGrid(blockX);
        final int gridZ = Units.blockToGrid(blockZ);

        final Region.Point point00 = regionGenerator.getOrCreateRegionPoint(gridX, gridZ);
        final Region.Point point01 = regionGenerator.getOrCreateRegionPoint(gridX, gridZ + 1);
        final Region.Point point10 = regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ);
        final Region.Point point11 = regionGenerator.getOrCreateRegionPoint(gridX + 1, gridZ + 1);

        final double deltaX = Units.blockToGridExact(blockX) - gridX;
        final double deltaZ = Units.blockToGridExact(blockZ) - gridZ;
        final double lower = Mth.lerp(deltaX, point00.baseOceanDepth, point10.baseOceanDepth);
        final double upper = Mth.lerp(deltaX, point01.baseOceanDepth, point11.baseOceanDepth);
        return Mth.lerp(deltaZ, lower, upper);
    }

    private static double smoothstep(double edge0, double edge1, double value)
    {
        if (edge0 == edge1)
        {
            return value < edge0 ? 0d : 1d;
        }

        final double normalized = Mth.clamp((value - edge0) / (edge1 - edge0), 0d, 1d);
        return normalized * normalized * (3d - 2d * normalized);
    }
}
