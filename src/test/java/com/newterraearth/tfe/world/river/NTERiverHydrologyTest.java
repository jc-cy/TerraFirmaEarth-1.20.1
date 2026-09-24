package com.newterraearth.tfe.world.river;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

import net.dries007.tfc.world.region.RegionPartition;
import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.river.River;
import net.dries007.tfc.world.river.Flow;

import com.newterraearth.tfe.config.NTECommonConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NTERiverHydrologyTest
{
    @Test
    void readOnlyHeightQueryDoesNotPlanAnUnseenLeaf()
    {
        final AtomicInteger heightSamples = new AtomicInteger();
        final RiverEdge leaf = testLeaf();
        final RegionPartition.Point partition = new RegionPartition.Point(List.of(leaf));
        final NTERiverHydrology hydrology = new NTERiverHydrology(
            918273645L,
            63,
            (x, z) -> {
                heightSamples.incrementAndGet();
                return 80d;
            },
            (x, z) -> partition
        );

        NTERiverHydrology.beginReadOnlyHeightQuery();
        try
        {
            assertTrue(hydrology.retainsTfcEdge(leaf));
            assertNull(hydrology.findGraphProfile(0, 0));
        }
        finally
        {
            NTERiverHydrology.endReadOnlyHeightQuery();
        }

        assertEquals(0, heightSamples.get(),
            "locate and height-preload queries must not sample terrain for a new creek route");
        assertFalse(NTERiverHydrology.readOnlyHeightQueryActive());
    }

    @Test
    void nestedReadOnlyHeightQueriesRestoreTheOuterScope()
    {
        assertFalse(NTERiverHydrology.readOnlyHeightQueryActive());
        NTERiverHydrology.beginReadOnlyHeightQuery();
        NTERiverHydrology.beginReadOnlyHeightQuery();
        try
        {
            assertTrue(NTERiverHydrology.readOnlyHeightQueryActive());
            NTERiverHydrology.endReadOnlyHeightQuery();
            assertTrue(NTERiverHydrology.readOnlyHeightQueryActive());
        }
        finally
        {
            if (NTERiverHydrology.readOnlyHeightQueryActive())
            {
                NTERiverHydrology.endReadOnlyHeightQuery();
            }
        }
        assertFalse(NTERiverHydrology.readOnlyHeightQueryActive());
    }

    @Test
    void coveredCreekGrowsARoundedNoiseShapedCavity()
    {
        final int blockX = -626;
        final int blockZ = -2199;
        final NTERiverHydrology.ColumnProfile tunnel = coveredCreek(0d, 4242L);
        final int waterY = tunnel.waterBlockY();

        assertFalse(NTERiverHydrology.carvesTunnelCavity(tunnel, waterY, blockX, blockZ, 96d),
            "the water surface itself must stay water instead of being carved out");
        assertFalse(NTERiverHydrology.carvesTunnelCavity(tunnel, waterY - 1, blockX, blockZ, 96d),
            "the bed under the covered creek must stay solid");
        assertTrue(NTERiverHydrology.carvesTunnelCavity(tunnel, waterY + 1, blockX, blockZ, 96d),
            "a covered creek must open its cavity directly above the water");

        final int centreTop = carvedTop(tunnel, blockX, blockZ);
        assertTrue(centreTop >= waterY + 1, "the covered creek must keep at least one block of headroom");
        assertTrue(centreTop <= 80, "the carved arch may never reach above the planned rock ceiling");
        assertEquals(centreTop, carvedTop(tunnel, blockX, blockZ),
            "the same creek and column must always carve the same cavity");

        // The arch springs from the apex: the ceiling holds over the channel centre and
        // falls by exactly the arch rise at the channel edge, so the roof is a dome
        // instead of a flat slab over straight walls.
        final int bankTop = carvedTop(coveredCreek(1d, 4242L), blockX, blockZ);
        assertEquals(2, centreTop - bankTop,
            "the ceiling must drop by the full arch rise at the channel edge");
        assertTrue(carvedTop(coveredCreek(0.25d, 4242L), blockX, blockZ) >= centreTop - 1,
            "the arch must keep a nearly full ceiling over the middle of the channel");

        boolean varied = false;
        for (int offset = 1; offset <= 24 && !varied; offset++)
        {
            varied = carvedTop(tunnel, blockX + offset, blockZ) != centreTop
                || carvedTop(tunnel, blockX, blockZ + offset) != centreTop;
        }
        assertTrue(varied, "covered creek carving must vary along the creek instead of a constant tube");
    }

    private static int carvedTop(NTERiverHydrology.ColumnProfile profile, int blockX, int blockZ)
    {
        return carvedTop(profile, blockX, blockZ, profile.tunnelCeilingY() + 32d);
    }

    private static int carvedTop(
        NTERiverHydrology.ColumnProfile profile,
        int blockX,
        int blockZ,
        double terrainHeight
    )
    {
        int top = Integer.MIN_VALUE;
        for (int y = profile.bedBlockY() - 1; y <= profile.tunnelCeilingBlockY() + 2; y++)
        {
            if (NTERiverHydrology.carvesTunnelCavity(profile, y, blockX, blockZ, terrainHeight))
            {
                top = y;
            }
        }
        return top;
    }

    @Test
    void coveredCreekCavityOpensThroughAGradedMouthFloor()
    {
        final int blockX = -934;
        final int blockZ = -1775;
        final NTERiverHydrology.ColumnProfile mouth = coveredCreekIncision(0d, 62d, 80d, 4242L, 12d);
        final int roof = NTECommonConfig.getHeadwaterTunnelRoof();
        // A graded cave mouth lowers this column from its ambient terrain (about 88) to a
        // funnel floor at 73, which is only about one block above the arch: rock that thin
        // is not a roof, it is a floating shell over the creek.
        final double mouthFloor = 73.3d;

        assertEquals((int) mouthFloor, carvedTop(mouth, blockX, blockZ, mouthFloor),
            "a graded mouth floor thinner than the tunnel roof must be carved through, not preserved");
        assertFalse(
            NTERiverHydrology.carvesTunnelCavity(mouth, (int) mouthFloor + 1, blockX, blockZ, mouthFloor),
            "the opened mouth must stop at its own surface"
        );
        assertTrue(
            NTERiverHydrology.carvesTunnelCavity(mouth, (int) mouthFloor - 1, blockX, blockZ, mouthFloor),
            "the whole floor below the surface is opened"
        );
        // The graded mouth also reaches its shoulder, so a lip left outside the channel is
        // opened as well instead of hanging over the creek.
        assertEquals((int) mouthFloor, carvedTop(coveredCreekIncision(1.2d, 62d, 80d, 4242L, 12d), blockX, blockZ, mouthFloor),
            "the graded mouth opens through its shoulder too");
        // A column with a full roof above its arch keeps that roof.
        final NTERiverHydrology.ColumnProfile tunnel = coveredCreek(0d, 62d, 80d, 4242L);
        final double roofedFloor = tunnel.tunnelCeilingY() + roof + 1d;
        assertEquals(carvedTop(tunnel, blockX, blockZ), carvedTop(tunnel, blockX, blockZ, roofedFloor),
            "a thick roof must be left alone");
        assertTrue(carvedTop(tunnel, blockX, blockZ, roofedFloor) < (int) roofedFloor - roof,
            "the arch must stay below a roofed column's own surface");
    }

    @Test
    void coveredCreekRealizesItsGradedMouthEvenWhenTheSharedFieldsAllowFilling()
    {
        // A covered section reuses the ordinary creek's fields, fill flag included, but it never
        // shapes its own surface. Skipping the mouth cut there left the cave mouth ungraded, the
        // covered water capped by the planned bed (a flat one block channel) while the adjacent
        // open-air row filled the whole V, and the rock above the tunnel standing as a wall.
        final NTERiverHydrology.ColumnProfile roofed = withFillAllowed(coveredCreek(0d, 4242L), true);
        final NTERiverHydrology.ColumnProfile mouth = withFillAllowed(
            coveredCreekIncision(0d, 70.996d, 77d, 4242L, 18.714d),
            true
        );

        assertTrue(roofed.fillAllowed(), "the shared fill flag reaches the covered profile");
        assertFalse(roofed.forcesTerrainCut(),
            "a covered column without a graded mouth must leave the terrain alone");
        assertTrue(mouth.forcesTerrainCut(),
            "a graded cave mouth must be excavated no matter what the shared fill flag says");
        assertEquals(86.645d - 18.714d, mouth.terrainCutCeiling(86.645d), 1.0e-9d,
            "a covered creek must realize its planned mouth incision instead of keeping its surface");
        assertEquals(67, NTERiverHydrology.effectiveBedBlockY(mouth, mouth.terrainCutCeiling(86.645d)),
            "the excavated mouth floor must own the water column depth");
        assertEquals(3, mouth.waterBlockY() - NTERiverHydrology.effectiveBedBlockY(
            mouth,
            mouth.terrainCutCeiling(86.645d)
        ), "the graded mouth must carry the same three block V as the adjacent open-air row");

        final NTERiverHydrology.ColumnProfile openAirCut = withFillAllowed(surfaceCreekProfile(), false);
        final NTERiverHydrology.ColumnProfile openAirFill = withFillAllowed(surfaceCreekProfile(), true);
        assertTrue(openAirCut.forcesTerrainCut(),
            "cut-only ownership keeps realizing an ordinary creek's planned incision");
        assertFalse(openAirFill.forcesTerrainCut(),
            "an ordinary creek still only cuts where its fill transition may not raise terrain");
        assertEquals(80d, openAirFill.terrainCutCeiling(80d), 1.0e-9d,
            "an ordinary filling profile must not cut its own surface");
    }

    @Test
    void coveredCreekThinsItsCaveSpikes()
    {
        final NTERiverHydrology.ColumnProfile tunnel = coveredCreek(0d, 62d, 71d, 4242L);
        final NTERiverHydrology.ColumnProfile mouth = coveredCreekIncision(0d, 62d, 71d, 4242L, 12d);
        final int blockX = -847;
        final int blockZ = -1571;

        assertFalse(NTERiverHydrology.blocksCaveColumn(surfaceCreekProfile(), 64),
            "surface creeks keep their already validated column handling");
        assertFalse(NTERiverHydrology.blocksCaveColumn(tunnel, 64),
            "covered creek sections keep their cave columns: that is not a spike rule");

        int stacks = 0;
        int keptSpikes = 0;
        for (int offset = 0; offset < 480; offset++)
        {
            final int x = blockX + offset;
            stacks++;
            if (!NTERiverHydrology.blocksCaveSpike(tunnel, x, 68, blockZ))
            {
                keptSpikes++;
            }
        }
        assertTrue(keptSpikes >= stacks * 0.55d && keptSpikes <= stacks * 0.78d,
            "the covered run must keep roughly two thirds of its spike stacks, kept=" + keptSpikes);
        assertTrue(keptSpikes > 0, "the covered creek must still grow cave spikes");

        // Near a graded cave mouth the creek is the visible feature: two thirds of
        // the spike stacks are dropped instead of one third.
        int mouthStacks = 0;
        int mouthKept = 0;
        for (int offset = 0; offset < 480; offset++)
        {
            final int x = blockX + offset;
            mouthStacks++;
            if (!NTERiverHydrology.blocksCaveSpike(mouth, x, 68, blockZ))
            {
                mouthKept++;
            }
        }
        assertTrue(mouthKept >= mouthStacks * 0.22d && mouthKept <= mouthStacks * 0.45d,
            "a cave mouth keeps only one spike stack in three, kept=" + mouthKept);
        assertFalse(NTERiverHydrology.blocksCaveSpike(surfaceCreekProfile(), blockX, 63, blockZ),
            "an ordinary surface creek keeps vanilla cave spikes");
        assertFalse(NTERiverHydrology.blocksCaveSpike(null, blockX, 63, blockZ),
            "columns without a creek profile are untouched");
    }

    @Test
    void coveredCreekSpikeFormationsAreJudgedWhole()
    {
        // TFC writes a spike formation from its root block outward: two blocks for a
        // small stalk, three for a base stalk and four for a rooted one. The filter
        // tests that exact footprint, so a formation which would grow into another
        // spike is rejected before any of its own blocks exist.
        assertEquals(2, NTERiverHydrology.caveSpikeLength(0.19f));
        assertEquals(3, NTERiverHydrology.caveSpikeLength(0.5f));
        assertEquals(4, NTERiverHydrology.caveSpikeLength(0.7f));
        assertEquals(4, NTERiverHydrology.caveSpikeLength(1f));
    }

    private static NTERiverHydrology.ColumnProfile coveredCreek(double normalizedDistanceSq, long caveSeed)
    {
        return coveredCreek(normalizedDistanceSq, 62d, 80d, caveSeed);
    }

    private static NTERiverHydrology.ColumnProfile coveredCreekIncision(
        double normalizedDistanceSq,
        double waterY,
        double ceilingY,
        long caveSeed,
        double incision
    )
    {
        final NTERiverHydrology.ColumnProfile base = coveredCreek(normalizedDistanceSq, waterY, ceilingY, caveSeed);
        return new NTERiverHydrology.ColumnProfile(
            base.waterSurfaceY(),
            base.centerBedY(),
            base.bedY(),
            base.normalizedDistanceSq(),
            base.channelRadius(),
            base.bankRaise(),
            incision,
            base.mouthWaterDrop(),
            base.bankFillWeight(),
            base.waterCoreRadiusSq(),
            base.fillAllowed(),
            base.waterAllowed(),
            base.sourceWaterAllowed(),
            base.receiverBlendWeight(),
            base.receiverBedBlendWeight(),
            base.waterfallLanding(),
            base.headwater(),
            base.kind(),
            base.mode(),
            base.tunnelCeilingY(),
            base.caveSeed(),
            base.flow()
        );
    }

    private static NTERiverHydrology.ColumnProfile coveredCreek(
        double normalizedDistanceSq,
        double waterY,
        double ceilingY,
        long caveSeed
    )
    {
        return new NTERiverHydrology.ColumnProfile(
            waterY,
            waterY - 1d,
            waterY - 1d,
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
            NTERiverHydrology.ChannelMode.SUBTERRANEAN,
            ceilingY,
            caveSeed,
            Flow.EEE
        );
    }

    private static NTERiverHydrology.ColumnProfile surfaceCreekProfile()
    {
        return new NTERiverHydrology.ColumnProfile(
            62d,
            61d,
            61d,
            0d,
            4.5d,
            0d,
            0d,
            0d,
            1d,
            0.72d,
            false,
            true,
            true,
            0d,
            0d,
            false,
            false,
            NTERiverHydrology.ChannelKind.STREAM,
            NTERiverHydrology.ChannelMode.SURFACE,
            0d,
            0L,
            Flow.EEE
        );
    }

    private static NTERiverHydrology.ColumnProfile withFillAllowed(
        NTERiverHydrology.ColumnProfile base,
        boolean fillAllowed
    )
    {
        return new NTERiverHydrology.ColumnProfile(
            base.waterSurfaceY(),
            base.centerBedY(),
            base.bedY(),
            base.normalizedDistanceSq(),
            base.channelRadius(),
            base.bankRaise(),
            base.terrainIncision(),
            base.mouthWaterDrop(),
            base.bankFillWeight(),
            base.waterCoreRadiusSq(),
            fillAllowed,
            base.waterAllowed(),
            base.sourceWaterAllowed(),
            base.receiverBlendWeight(),
            base.receiverBedBlendWeight(),
            base.waterfallLanding(),
            base.headwater(),
            base.kind(),
            base.mode(),
            base.tunnelCeilingY(),
            base.caveSeed(),
            base.flow()
        );
    }

    private static RiverEdge testLeaf()
    {
        final River.Vertex source = new River.Vertex(-1d, 0d, 0d, 1d, 1);
        final River.Vertex drain = new River.Vertex(1d, 0d, 0d, 1d, 0);
        final RiverEdge edge = new RiverEdge(new River.Edge(source, drain), RandomSource.create(55667788L));
        edge.width = 10;
        return edge;
    }
}
