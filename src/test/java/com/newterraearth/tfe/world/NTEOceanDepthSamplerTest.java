package com.newterraearth.tfe.world;

import org.junit.jupiter.api.Test;

import net.dries007.tfc.world.BiomeNoiseSampler;
import net.dries007.tfc.world.noise.Noise2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.dries007.tfc.world.TFCChunkGenerator.SEA_LEVEL_Y;

class NTEOceanDepthSamplerTest
{
    /** 4.2.9 base floors used by the recalibrated curve: ocean, deep ocean and trench. */
    private static final double OCEAN_BASE_FLOOR = SEA_LEVEL_Y - 26d;
    private static final double DEEP_OCEAN_BASE_FLOOR = SEA_LEVEL_Y - 46d;
    private static final double TRENCH_BASE_FLOOR = SEA_LEVEL_Y - 60d;

    @Test
    void noDropBeforeTheOffshoreRampStarts()
    {
        assertEquals(0d, NTEOceanDepthSampler.depthDrop(0d, false, OCEAN_BASE_FLOOR), 0d);
        assertEquals(0d, NTEOceanDepthSampler.depthDrop(0.5d, false, OCEAN_BASE_FLOOR), 0d);
        assertEquals(0d, NTEOceanDepthSampler.depthDrop(0.5d, true, TRENCH_BASE_FLOOR), 0d);
    }

    @Test
    void stageAnchorsKeepThePreviouslyVerifiedFloors()
    {
        // ARCHIVE-20260417-06: ocean y=15, deep ocean y=-10, trench bottom ~y=-30.
        assertEquals(15d, OCEAN_BASE_FLOOR - NTEOceanDepthSampler.depthDrop(3d, false, OCEAN_BASE_FLOOR), 1e-9);
        assertEquals(-10d, DEEP_OCEAN_BASE_FLOOR - NTEOceanDepthSampler.depthDrop(9d, false, DEEP_OCEAN_BASE_FLOOR), 1e-9);
        assertEquals(-30d, TRENCH_BASE_FLOOR - NTEOceanDepthSampler.depthDrop(10.7d, true, TRENCH_BASE_FLOOR), 1e-9);
    }

    @Test
    void dropIsMonotonicAndBounded()
    {
        double previous = -1d;
        for (double depth = 0d; depth <= 15d; depth += 0.05d)
        {
            final double plain = NTEOceanDepthSampler.depthDrop(depth, false, DEEP_OCEAN_BASE_FLOOR);
            final double trench = NTEOceanDepthSampler.depthDrop(depth, true, TRENCH_BASE_FLOOR);
            assertTrue(plain >= previous - 1e-9, "drop must not decrease offshore");
            assertTrue(trench >= plain - 1e-9, "the trench step must only add depth");
            assertTrue(plain >= 0d && plain <= 27d);
            assertTrue(trench <= 47d);
            if (depth < 9.2d)
            {
                assertEquals(plain, trench, 0d);
            }
            previous = plain;
        }

        // Above the ramp the open ocean holds its maximum deepening.
        assertEquals(27d, NTEOceanDepthSampler.depthDrop(15d, false, DEEP_OCEAN_BASE_FLOOR), 1e-9);
    }

    @Test
    void trenchStepNeverCutsBelowItsFloor()
    {
        for (double baseHeight = TRENCH_BASE_FLOOR; baseHeight <= SEA_LEVEL_Y + 10d; baseHeight += 1d)
        {
            final double floor = baseHeight - NTEOceanDepthSampler.depthDrop(12d, true, baseHeight);
            assertTrue(floor >= -30d - 1e-9, "trench floor must stay at or above y=-30: " + floor);
        }
    }

    @Test
    void samplerFallsBackToTheBaseHeightWithoutRegionData()
    {
        final Noise2D base = (x, z) -> 40d + x * 0.001d + z * 0.002d;
        final BiomeNoiseSampler sampler = NTEOceanDepthSampler.ocean(base);

        sampler.setColumn(1234, -5678);

        // The delegate caches the base noise as a float, hence the loose tolerance.
        assertEquals(base.noise(1234, -5678), sampler.height(), 1e-3);
        assertEquals(BiomeNoiseSampler.SOLID, sampler.noise(0));
    }
}
