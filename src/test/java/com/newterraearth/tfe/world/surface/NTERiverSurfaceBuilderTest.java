package com.newterraearth.tfe.world.surface;

import org.junit.jupiter.api.Test;

import net.dries007.tfc.world.river.Flow;

import com.newterraearth.tfe.world.river.NTERiverHydrology;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NTERiverSurfaceBuilderTest
{
    @Test
    void onlyAGradedOpenCoveredChannelOwnsTheCreekBedSurface()
    {
        assertTrue(NTERiverSurfaceBuilder.exposesSubterraneanCreekBed(covered(0.5d), 62),
            "a covered channel excavated into its own cavity is an open creek bed");
        assertTrue(NTERiverSurfaceBuilder.exposesSubterraneanCreekBed(covered(1d), 68),
            "the whole open channel, not only its water core, must drop living soil");
        assertFalse(NTERiverSurfaceBuilder.exposesSubterraneanCreekBed(covered(0.5d), 90),
            "a still covered section keeps the ambient biome surface far above its cavity");
        assertFalse(NTERiverSurfaceBuilder.exposesSubterraneanCreekBed(covered(1.5d), 61),
            "the creek shoulder outside its channel is not the channel floor");
        assertFalse(NTERiverSurfaceBuilder.exposesSubterraneanCreekBed(surfaceCreek(), 61),
            "an ordinary surface creek keeps its already validated bed handling");
        assertFalse(NTERiverSurfaceBuilder.exposesSubterraneanCreekBed(null, 61),
            "columns without a creek profile are untouched");
    }

    private static NTERiverHydrology.ColumnProfile covered(double normalizedDistanceSq)
    {
        return profile(normalizedDistanceSq, NTERiverHydrology.ChannelMode.SUBTERRANEAN);
    }

    private static NTERiverHydrology.ColumnProfile surfaceCreek()
    {
        return profile(0.5d, NTERiverHydrology.ChannelMode.SURFACE);
    }

    private static NTERiverHydrology.ColumnProfile profile(
        double normalizedDistanceSq,
        NTERiverHydrology.ChannelMode mode
    )
    {
        return new NTERiverHydrology.ColumnProfile(
            62d,
            61d,
            61d,
            normalizedDistanceSq,
            4.5d,
            0d,
            0d,
            0d,
            1d,
            0.72d,
            false,
            true,
            false,
            0d,
            0d,
            false,
            false,
            NTERiverHydrology.ChannelKind.STREAM,
            mode,
            mode == NTERiverHydrology.ChannelMode.SUBTERRANEAN ? 80d : 0d,
            mode == NTERiverHydrology.ChannelMode.SUBTERRANEAN ? 4242L : 0L,
            Flow.EEE
        );
    }
}
