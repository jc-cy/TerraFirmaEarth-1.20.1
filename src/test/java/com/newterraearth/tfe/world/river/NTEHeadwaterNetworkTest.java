package com.newterraearth.tfe.world.river;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.dries007.tfc.world.river.Flow;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NTEHeadwaterNetworkTest
{
    private static final int SEA_LEVEL = 63;
    /**
     * The historical fixed mouth window. The dynamic window must reproduce every
     * curve below this value exactly, so these reference assertions still pin the
     * baseline shape rather than the configured maximum.
     */
    private static final double BASELINE_MOUTH_WINDOW = 24d;

    @Test
    void validatedHeadwaterKeepsTheTfcDrainAndWidensDownstream()
    {
        final NTEHeadwaterNetwork.TestStream stream = slopedValley(918273645L);

        assertTrue(stream.valid());
        final List<NTEHeadwaterNetwork.DiagnosticPoint> points = stream.points();
        assertTrue(points.size() > 8);
        assertEquals(0d, points.get(points.size() - 1).x(), 1.0e-9d);
        assertEquals(0d, points.get(points.size() - 1).z(), 1.0e-9d);
        assertEquals(SEA_LEVEL - 1d, points.get(points.size() - 1).waterY(), 1.0e-9d);

        for (int i = 1; i < points.size(); i++)
        {
            assertTrue(points.get(i - 1).waterY() >= points.get(i).waterY(), "water may not climb downstream");
            assertTrue(points.get(i - 1).radius() <= points.get(i).radius(), "a headwater may not narrow downstream");
        }
        assertTrue(points.get(0).waterY() > points.get(points.size() - 1).waterY());
        assertTrue(points.get(0).radius() < points.get(points.size() - 1).radius());
        assertTrue(points.get(0).x() >= 368d, "the spring must extend at least 48 blocks beyond the old source");
        assertTrue(points.get(0).radius() <= 0.7d, "the extended spring must begin about one block wide");
    }

    @Test
    void globalLeafMayDescendThroughTheFinalSeaMouth()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.planTestStream(
            13572468L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 62.5d + Math.max(0d, x) * 0.08d + Math.abs(z) * 0.12d
        );

        assertTrue(stream.valid(), "a global leaf must be allowed to reach a submerged coastal mouth: " + stream.failureReason());
        final List<NTEHeadwaterNetwork.DiagnosticPoint> points = stream.points();
        assertEquals(SEA_LEVEL - 1d, points.get(points.size() - 1).waterY(), 1.0e-9d);
        assertTrue(points.get(points.size() - 1).terrainY() < SEA_LEVEL - 1d + 1.25d,
            "the regression fixture must exercise a below-sea-level shore endpoint");
    }

    @Test
    void highlandHeadwaterFollowsTerrainAndLimitsCascadeSlope()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.planTestStream(
            246813579L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 65d + Math.max(0, x) * 0.18d + Math.abs(z) * 0.12d
        );

        assertTrue(stream.valid());
        final List<NTEHeadwaterNetwork.DiagnosticPoint> points = stream.points();
        assertTrue(points.get(0).waterY() > 120d, "a mountain creek may not be pinned near sea level");
        for (int i = 1; i < points.size(); i++)
        {
            final NTEHeadwaterNetwork.DiagnosticPoint previous = points.get(i - 1);
            final NTEHeadwaterNetwork.DiagnosticPoint current = points.get(i);
            final double segmentLength = Math.hypot(current.x() - previous.x(), current.z() - previous.z());
            assertTrue(previous.waterY() >= current.waterY(), "water may not climb downstream");
            assertTrue(previous.waterY() - current.waterY() <= segmentLength + 1.0e-6d,
                "a cascade may drop at most one block per horizontal block");
            assertTrue(previous.terrainY() - previous.waterY() <= 6.25d,
                "normal creek sections may not become deep artificial trenches");
        }
    }

    @Test
    void selectedStreamRemainsContinuousWhenAnotherChannelAlreadyLoweredAmbientTerrain()
    {
        final NTEHeadwaterNetwork.TestStream stream = slopedValley(11223344L);
        assertTrue(stream.valid());

        final List<NTEHeadwaterNetwork.DiagnosticPoint> points = stream.points();
        final NTEHeadwaterNetwork.DiagnosticPoint middle = points.get(points.size() / 2);
        final int x = (int) Math.floor(middle.x());
        final int z = (int) Math.floor(middle.z());

        assertNotNull(stream.sample(x, z, middle.terrainY()));
        assertNotNull(stream.sample(x, z, middle.waterY() + 0.25d),
            "a selected route may not disappear because another planned creek cut the shared column first");
        assertNull(stream.sample(x, z + 96, middle.terrainY()));
    }

    @Test
    void lowestRiskCandidateStillGeneratesWhenEveryRouteExceedsThePreferredIncisionRange()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.planTestStream(
            66778899L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 95d + Math.max(0, x) * 0.02d + Math.abs(z) * 0.04d
        );

        assertTrue(stream.valid(),
            "incision is a candidate-ranking risk and may not veto the selected route after planning");
        assertTrue(stream.points().stream().anyMatch(point ->
                NTEHeadwaterNetwork.visibleIncisionDepth(point.terrainY(), point.waterY()) > 8),
            "the fixture must exercise a route beyond the former hard outlet limit");
    }

    @Test
    void retainedTfcLeafCanReceiveANarrowSafeSourceExtension()
    {
        final NTEHeadwaterNetwork.TestStream feeder = NTEHeadwaterNetwork.planTestSourceExtension(
            31415926L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 65d + Math.max(0, x - 320) * 0.04d + Math.abs(z) * 0.08d
        );

        assertTrue(feeder.valid());
        final List<NTEHeadwaterNetwork.DiagnosticPoint> points = feeder.points();
        assertTrue(points.get(0).x() >= 368d);
        assertTrue(points.get(0).radius() <= 0.7d);
        assertEquals(320d, points.get(points.size() - 1).x(), 1.0e-9d);
        assertEquals(0d, points.get(points.size() - 1).z(), 1.0e-9d);
        assertTrue(points.get(points.size() - 1).radius() <= 2d);
        assertEquals(SEA_LEVEL - 1d, points.get(points.size() - 1).waterY(), 1.0e-9d,
            "a retained-leaf feeder must reach the native receiver water level at contact");
    }

    @Test
    void uncontainableOutletInvalidatesTheReplacementSoTfcCanFallback()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.planTestStream(
            42L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> SEA_LEVEL - 1d
        );

        assertFalse(stream.valid());
        assertTrue(stream.points().isEmpty());
    }

    @Test
    void routePlanningIsDeterministicForTheWorldSeed()
    {
        final NTEHeadwaterNetwork.TestStream first = slopedValley(55667788L);
        final NTEHeadwaterNetwork.TestStream second = slopedValley(55667788L);

        assertTrue(first.valid());
        assertEquals(first.points(), second.points());
    }

    @Test
    void representativeRouteGeometryMatchesTheStableBaseline()
    {
        final List<NTEHeadwaterNetwork.TestStream> streams = List.of(
            slopedValley(55667788L),
            highlandDrainageStream(),
            NTEHeadwaterNetwork.planTestStream(
                77889911L,
                SEA_LEVEL,
                320d,
                0d,
                0d,
                0d,
                16,
                (x, z) -> {
                    final double bend = Math.max(0d, Math.min(1d, (x - 64d) / 192d));
                    final double valleyZ = 48d * bend;
                    return 68d + Math.max(0, x) * 0.035d + Math.abs(z - valleyZ) * 0.22d;
                }
            ),
            NTEHeadwaterNetwork.planTestStream(
                1357911L,
                SEA_LEVEL,
                320d,
                0d,
                0d,
                0d,
                16,
                (x, z) -> 70d + Math.max(0, x) * 0.035d + Math.abs(z) * 0.05d,
                (owner, x, z, clearance) -> x > 136d && x < 184d && Math.abs(z) < 22d
            )
        );
        final List<String> expectedHashes = List.of(
            "1a7879345b3efe75",
            "8f76927620b1c749",
            "97fe7c87a3d6bd42",
            "8346b033026a282c"
        );

        // The underground decision and the cave mouth only change how a
        // qualifying section is carved, never its planned water grade, so every
        // pre-existing baseline hash - including the highland trench fixture -
        // must stay bit identical. Covered section coverage is asserted by
        // deepSurfaceCutTurnsIntoACoveredTunnelAndStaysTunable instead, whose
        // fixture is deep enough for the configured threshold.
        for (int index = 0; index < streams.size(); index++)
        {
            final NTEHeadwaterNetwork.TestStream stream = streams.get(index);
            assertTrue(stream.valid());
            assertEquals(expectedHashes.get(index), routeGeometryHash(stream));
        }
    }

    @Test
    void drainagePlannerFollowsACurvedCatchmentInsteadOfTheOldStraightCorridor()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.planTestStream(
            77889911L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> {
                final double bend = Math.max(0d, Math.min(1d, (x - 64d) / 192d));
                final double valleyZ = 48d * bend;
                return 68d + Math.max(0, x) * 0.035d + Math.abs(z - valleyZ) * 0.22d;
            }
        );

        assertTrue(stream.valid());
        final List<NTEHeadwaterNetwork.DiagnosticPoint> points = stream.points();
        final NTEHeadwaterNetwork.DiagnosticPoint spring = points.get(0);
        assertTrue(Math.abs(spring.z() - 48d) <= 12d,
            "the spring must be selected from the upstream catchment instead of the old center line");

        NTEHeadwaterNetwork.DiagnosticPoint bend = points.get(0);
        for (NTEHeadwaterNetwork.DiagnosticPoint point : points)
        {
            if (Math.abs(point.x() - 224d) < Math.abs(bend.x() - 224d))
            {
                bend = point;
            }
        }
        assertTrue(bend.z() > 24d, "the planned channel must stay in the curved valley through the middle reach");
    }

    @Test
    void completeReplacementRoutesAroundAnUnrelatedRetainedRiver()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.planTestStream(
            1357911L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 70d + Math.max(0, x) * 0.035d + Math.abs(z) * 0.05d,
            (owner, x, z, clearance) -> x > 136d && x < 184d && Math.abs(z) < 22d
        );

        assertTrue(stream.valid(), "a protected native channel must reroute the candidate, not be crossed");
        for (NTEHeadwaterNetwork.DiagnosticPoint point : stream.points())
        {
            assertFalse(point.x() > 136d && point.x() < 184d && Math.abs(point.z()) < 22d,
                "the accepted replacement may not enter the unrelated retained river corridor");
        }
    }

    @Test
    void feederSourceIsNotSelectedInsideAnUnrelatedRetainedRiver()
    {
        final NTEHeadwaterNetwork.TestStream feeder = NTEHeadwaterNetwork.planTestSourceExtension(
            2468024L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 65d + Math.max(0, x - 320) * 0.04d + Math.abs(z) * 0.08d,
            (owner, x, z, clearance) -> x >= 368d && x <= 440d && Math.abs(z) < 20d
        );

        assertTrue(feeder.valid(), "the feeder must search the unobstructed side of its upstream catchment");
        final NTEHeadwaterNetwork.DiagnosticPoint spring = feeder.points().get(0);
        assertFalse(spring.x() >= 368d && spring.x() <= 440d && Math.abs(spring.z()) < 20d,
            "the spring may not be placed in the protected retained-river water core");
    }

    @Test
    void failedPrimaryDrainageCandidateFallsBackToAnotherValidValley()
    {
        final NTEHeadwaterNetwork.HeightSampler catchment = (x, z) -> {
            final double base = 70d + Math.max(0, x) * 0.04d;
            final double branch = Math.max(0d, Math.min(1d, (x - 64d) / 128d));
            final double secondValleyZ = 64d * branch;
            final double primaryValley = base + Math.abs(z) * 0.24d;
            final double secondaryValley = base + 0.8d + Math.abs(z - secondValleyZ) * 0.24d;
            return Math.min(primaryValley, secondaryValley);
        };
        final NTEHeadwaterNetwork.TestStream baseline = NTEHeadwaterNetwork.planTestStream(
            99112233L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            catchment
        );
        assertTrue(baseline.valid());
        assertTrue(baseline.availableDrainageCandidates() > 1);

        NTEHeadwaterNetwork.DiagnosticPoint poison = null;
        final List<NTEHeadwaterNetwork.DiagnosticPoint> baselinePoints = baseline.points();
        for (int i = 1; i < baselinePoints.size() / 3; i++)
        {
            final NTEHeadwaterNetwork.DiagnosticPoint candidate = baselinePoints.get(i);
            final int x = (int) Math.floor(candidate.x());
            final int z = (int) Math.floor(candidate.z());
            if (Math.floorMod(x, NTEHeadwaterNetwork.SAMPLE_STEP) != 0
                || Math.floorMod(z, NTEHeadwaterNetwork.SAMPLE_STEP) != 0)
            {
                poison = candidate;
                break;
            }
        }
        assertNotNull(poison);
        final int poisonX = (int) Math.floor(poison.x());
        final int poisonZ = (int) Math.floor(poison.z());

        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.planTestStream(
            99112233L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> x == poisonX && z == poisonZ ? 61.5d : catchment.sample(x, z)
        );

        assertTrue(stream.valid(), "a failed first candidate must not immediately remove the complete creek");
        assertTrue(stream.attemptedDrainageCandidates() > 1,
            "fine validation must reject the poisoned first route and advance to another drainage source");
    }

    @Test
    void failedSharedDrainageTrunkIsPenalizedAndRoutedAround()
    {
        final NTEHeadwaterNetwork.HeightSampler valley = (x, z) ->
            70d + Math.max(0, x) * 0.04d + Math.abs(z) * 0.08d;
        final NTEHeadwaterNetwork.TestStream baseline = NTEHeadwaterNetwork.planTestStream(
            44556677L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            valley
        );
        assertTrue(baseline.valid());
        assertTrue(baseline.availableDrainageCandidates() > 1);

        NTEHeadwaterNetwork.DiagnosticPoint poison = null;
        final List<NTEHeadwaterNetwork.DiagnosticPoint> baselinePoints = baseline.points();
        for (int i = baselinePoints.size() / 2; i < baselinePoints.size() * 3 / 4; i++)
        {
            final NTEHeadwaterNetwork.DiagnosticPoint candidate = baselinePoints.get(i);
            final int x = (int) Math.floor(candidate.x());
            final int z = (int) Math.floor(candidate.z());
            if (Math.floorMod(x, NTEHeadwaterNetwork.SAMPLE_STEP) != 0
                || Math.floorMod(z, NTEHeadwaterNetwork.SAMPLE_STEP) != 0)
            {
                poison = candidate;
                break;
            }
        }
        assertNotNull(poison);
        final int poisonX = (int) Math.floor(poison.x());
        final int poisonZ = (int) Math.floor(poison.z());

        final NTEHeadwaterNetwork.TestStream rerouted = NTEHeadwaterNetwork.planTestStream(
            44556677L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> x == poisonX && z == poisonZ ? 61.5d : valley.sample(x, z)
        );

        assertTrue(rerouted.valid(),
            "a failure on the shared drainage trunk must trigger a genuinely different corridor");
        assertTrue(rerouted.attemptedDrainageCandidates() > 1,
            "the rejected trunk must advance to a penalized replanning pass");
        for (NTEHeadwaterNetwork.DiagnosticPoint point : rerouted.points())
        {
            assertFalse((int) Math.floor(point.x()) == poisonX && (int) Math.floor(point.z()) == poisonZ,
                "the accepted route must not reuse the failed fine-terrain column");
        }
    }

    @Test
    void crossingHeadwatersBecomeOneFourBranchJunctionWithoutDeletingEitherSource()
    {
        final NTEHeadwaterNetwork.HeightSampler crossingValleys = (x, z) -> {
            final double horizontal = 75d + Math.max(0d, x + 160d) * 0.05d + Math.abs(z) * 0.10d;
            final double vertical = 75d + Math.max(0d, z + 160d) * 0.05d + Math.abs(x) * 0.10d;
            return Math.min(horizontal, vertical);
        };
        final NTEHeadwaterNetwork.TestStream horizontal = NTEHeadwaterNetwork.planTestStream(
            11224488L,
            SEA_LEVEL,
            160d,
            0d,
            -160d,
            0d,
            16,
            crossingValleys
        );
        final NTEHeadwaterNetwork.TestStream vertical = NTEHeadwaterNetwork.planTestStream(
            22448811L,
            SEA_LEVEL,
            0d,
            160d,
            0d,
            -160d,
            16,
            crossingValleys
        );
        assertTrue(horizontal.valid());
        assertTrue(vertical.valid());

        final NTEHeadwaterNetwork.DiagnosticPoint horizontalSource = horizontal.points().get(0);
        final NTEHeadwaterNetwork.DiagnosticPoint verticalSource = vertical.points().get(0);
        assertTrue(NTEHeadwaterNetwork.coordinateTestStreams(horizontal, vertical),
            "two independently planned routes must be promoted to one coordinated junction");

        final List<NTEHeadwaterNetwork.DiagnosticPoint> horizontalPoints = horizontal.points();
        final List<NTEHeadwaterNetwork.DiagnosticPoint> verticalPoints = vertical.points();
        assertEquals(horizontalSource.x(), horizontalPoints.get(0).x(), 1.0e-9d);
        assertEquals(horizontalSource.z(), horizontalPoints.get(0).z(), 1.0e-9d);
        assertEquals(verticalSource.x(), verticalPoints.get(0).x(), 1.0e-9d);
        assertEquals(verticalSource.z(), verticalPoints.get(0).z(), 1.0e-9d);
        assertEquals(-160d, horizontalPoints.get(horizontalPoints.size() - 1).x(), 1.0e-9d);
        assertEquals(0d, horizontalPoints.get(horizontalPoints.size() - 1).z(), 1.0e-9d);
        assertEquals(0d, verticalPoints.get(verticalPoints.size() - 1).x(), 1.0e-9d);
        assertEquals(-160d, verticalPoints.get(verticalPoints.size() - 1).z(), 1.0e-9d);

        NTEHeadwaterNetwork.DiagnosticPoint sharedHorizontal = null;
        NTEHeadwaterNetwork.DiagnosticPoint sharedVertical = null;
        for (NTEHeadwaterNetwork.DiagnosticPoint left : horizontalPoints)
        {
            for (NTEHeadwaterNetwork.DiagnosticPoint right : verticalPoints)
            {
                if (Math.hypot(left.x() - right.x(), left.z() - right.z()) <= 1.0e-9d)
                {
                    sharedHorizontal = left;
                    sharedVertical = right;
                    break;
                }
            }
            if (sharedHorizontal != null)
            {
                break;
            }
        }
        assertNotNull(sharedHorizontal, "both routes must contain the same explicit junction point");
        assertNotNull(sharedVertical);
        assertEquals(sharedHorizontal.waterY(), sharedVertical.waterY(), 1.0e-9d,
            "the shared node must have one coordinated water level");
        assertTrue(NTEHeadwaterNetwork.routeDerivedStateMatchesGeometry(horizontal),
            "junction reconstruction must rebuild bounds and segment indexes from its new geometry");
        assertTrue(NTEHeadwaterNetwork.routeDerivedStateMatchesGeometry(vertical));
    }

    @Test
    void junctionRebuildRemovesOldOuterChunkMembershipBeforePublishingNewGeometry()
    {
        final NTEHeadwaterNetwork.HeightSampler valley = (x, z) -> 82d;
        final NTEHeadwaterNetwork.TestStream horizontal = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(-96d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(96d, 0d)
            ),
            78d,
            68d,
            valley
        );
        final NTEHeadwaterNetwork.TestStream vertical = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 96d),
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, -96d)
            ),
            72d,
            64d,
            valley
        );

        assertTrue(NTEHeadwaterNetwork.routeIsPublishedOnlyInCurrentChunksAfterCoordination(horizontal, vertical),
            "the outer chunk index must contain only the rebuilt immutable Route geometry");
    }

    @Test
    void sustainedHighLowOverlapBecomesOneSharedLongitudinalProfile()
    {
        final NTEHeadwaterNetwork.HeightSampler sharedValley = (x, z) -> 80d;
        final List<NTEHeadwaterNetwork.Vec> higherPath = new java.util.ArrayList<>();
        final List<NTEHeadwaterNetwork.Vec> lowerPath = new java.util.ArrayList<>();
        for (int x = 80; x >= -80; x -= 4)
        {
            higherPath.add(new NTEHeadwaterNetwork.Vec(x, 0d));
            lowerPath.add(new NTEHeadwaterNetwork.Vec(x, 1d));
        }
        final NTEHeadwaterNetwork.TestStream higher = NTEHeadwaterNetwork.testStreamFromRoute(
            higherPath, 72d, 66d, sharedValley
        );
        final NTEHeadwaterNetwork.TestStream lower = NTEHeadwaterNetwork.testStreamFromRoute(
            lowerPath, 71d, 65d, sharedValley
        );
        final double[] overlap = NTEHeadwaterNetwork.overlapTestStreams(higher, lower);
        assertNotNull(overlap, "a co-directed near-overlap must be represented as a shared corridor");
        assertTrue(overlap[1] - overlap[0] >= 12d);

        assertTrue(NTEHeadwaterNetwork.coordinateTestStreams(higher, lower));
        final int sampleX = 0;
        final NTEHeadwaterNetwork.Sample higherSample = NTEHeadwaterNetwork.sampleTestStream(higher, sampleX, 1);
        final NTEHeadwaterNetwork.Sample lowerSample = NTEHeadwaterNetwork.sampleTestStream(lower, sampleX, 1);
        assertNotNull(higherSample);
        assertNotNull(lowerSample);
        assertEquals(higherSample.waterSurfaceY(), lowerSample.waterSurfaceY(), 0.08d,
            "the shared trunk may not retain a one-block high/low double water surface");
        assertEquals(higherSample.flow(), lowerSample.flow(),
            "both owners of a shared trunk must bake water in the same downstream direction");
        assertTrue(NTEHeadwaterNetwork.routeDerivedStateMatchesGeometry(higher),
            "shared-profile reconstruction must rebuild all geometry-derived state");
        assertTrue(NTEHeadwaterNetwork.routeDerivedStateMatchesGeometry(lower));
    }

    @Test
    void physicallyTouchingWetCoresCoordinateBeyondTheOldCenterlineCutoff()
    {
        final NTEHeadwaterNetwork.HeightSampler valley = (x, z) -> 82d;
        final NTEHeadwaterNetwork.TestStream higher = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(80d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(-80d, 0d)
            ),
            76d,
            72d,
            valley
        );
        final NTEHeadwaterNetwork.TestStream lower = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(80d, 6.1d),
                new NTEHeadwaterNetwork.Vec(0d, 6.1d),
                new NTEHeadwaterNetwork.Vec(-80d, 6.1d)
            ),
            74d,
            70d,
            valley
        );

        assertTrue(NTEHeadwaterNetwork.coordinateTestStreams(higher, lower),
            "water cores which already touch may not remain independent high/low channels");
        final List<NTEHeadwaterNetwork.DiagnosticPoint> higherPoints = higher.points();
        final List<NTEHeadwaterNetwork.DiagnosticPoint> lowerPoints = lower.points();
        assertTrue(higherPoints.stream().anyMatch(left -> lowerPoints.stream().anyMatch(right ->
                Math.hypot(left.x() - right.x(), left.z() - right.z()) <= 1.0e-9d
                    && Math.abs(left.waterY() - right.waterY()) <= 1.0e-9d)),
            "physical contact must insert one explicit shared topology node");
        assertWaterNeverClimbs(higherPoints);
        assertWaterNeverClimbs(lowerPoints);
    }

    @Test
    void curvedRouteOnlyRejectsColumnsPastItsActualFinalSegment()
    {
        final List<NTEHeadwaterNetwork.Vec> route = List.of(
            new NTEHeadwaterNetwork.Vec(-10d, 20d),
            new NTEHeadwaterNetwork.Vec(20d, 20d),
            new NTEHeadwaterNetwork.Vec(20d, 0d),
            new NTEHeadwaterNetwork.Vec(0d, 0d)
        );

        assertFalse(NTEHeadwaterNetwork.rejectsPastOutlet(route, -10, 20),
            "an upstream meander may lie ahead of the outlet tangent without extending past the route endpoint");
        assertTrue(NTEHeadwaterNetwork.rejectsPastOutlet(route, -2, 0),
            "a column beyond the clamped end of the final segment must still be rejected");
    }

    @Test
    void columnProfileCanOnlyCutAndNeverRaiseTerrain()
    {
        final NTERiverHydrology.ColumnProfile profile = new NTERiverHydrology.ColumnProfile(
            78d,
            76d,
            76.5d,
            0d,
            2.5d,
            0d,
            0d,
            0d,
            1d,
            NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ,
            true,
            true,
            true,
            0d,
            0d,
            false,
            true,
            NTERiverHydrology.ChannelKind.STREAM,
            NTERiverHydrology.ChannelMode.SURFACE,
            0d,
            0L,
            Flow.NONE
        );

        assertEquals(60d, profile.fillCeiling(60d), 0d);
        assertEquals(0d, profile.bankRaise(), 0d);
        assertFalse(profile.subterranean());
    }

    @Test
    void singlePassWholeRouteSmoothingSoftensGridRightAnglesWithoutMovingEndpoints()
    {
        final List<NTEHeadwaterNetwork.Vec> points = NTEHeadwaterNetwork.smoothRoute(List.of(
            new NTEHeadwaterNetwork.Vec(0d, 0d),
            new NTEHeadwaterNetwork.Vec(8d, 0d),
            new NTEHeadwaterNetwork.Vec(8d, 8d)
        ));

        double maximumTurn = 0d;
        for (int i = 1; i < points.size() - 1; i++)
        {
            final NTEHeadwaterNetwork.Vec before = points.get(i - 1);
            final NTEHeadwaterNetwork.Vec at = points.get(i);
            final NTEHeadwaterNetwork.Vec after = points.get(i + 1);
            final double incoming = Math.atan2(at.z() - before.z(), at.x() - before.x());
            final double outgoing = Math.atan2(after.z() - at.z(), after.x() - at.x());
            maximumTurn = Math.max(maximumTurn, Math.abs(Math.atan2(
                Math.sin(outgoing - incoming),
                Math.cos(outgoing - incoming)
            )));
        }

        assertTrue(maximumTurn < Math.toRadians(50d), "one stable smoothing pass must soften an 8-block grid corner");
        assertFalse(points.contains(new NTEHeadwaterNetwork.Vec(8d, 0d)), "the sharp grid corner must be replaced by an arc");
        assertEquals(new NTEHeadwaterNetwork.Vec(0d, 0d), points.get(0));
        assertEquals(new NTEHeadwaterNetwork.Vec(8d, 8d), points.get(points.size() - 1));
    }

    @Test
    void fallbackFeederParticipatesInIntersectionCoordination()
    {
        final NTEHeadwaterNetwork.HeightSampler valley = (x, z) -> 82d;
        final NTEHeadwaterNetwork.TestStream feeder = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(80d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(-80d, 0d)
            ),
            78d,
            70d,
            valley,
            false
        );
        final NTEHeadwaterNetwork.TestStream replacement = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 80d),
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, -80d)
            ),
            70d,
            64d,
            valley
        );
        assertTrue(NTEHeadwaterNetwork.coordinateTestStreams(feeder, replacement),
            "the production coordinator and outer index must include a retained-leaf feeder");
        final List<NTEHeadwaterNetwork.DiagnosticPoint> feederPoints = feeder.points();
        final List<NTEHeadwaterNetwork.DiagnosticPoint> replacementPoints = replacement.points();
        assertTrue(feederPoints.stream().anyMatch(left -> replacementPoints.stream().anyMatch(right ->
                Math.hypot(left.x() - right.x(), left.z() - right.z()) <= 1.0e-9d
                    && Math.abs(left.waterY() - right.waterY()) <= 1.0e-9d)),
            "feeder and replacement must share one water level at their physical contact");
    }

    @Test
    void routeSegmentIndexMatchesTheLinearProjectionReference()
    {
        final NTEHeadwaterNetwork.TestStream stream = slopedValley(55667788L);
        assertTrue(stream.valid());
        for (int z = -64; z <= 64; z += 4)
        {
            for (int x = -32; x <= 352; x += 4)
            {
                assertTrue(NTEHeadwaterNetwork.routeSampleMatchesLinearReference(stream, x, z),
                    "the route segment index must preserve the exact nearest projection");
            }
        }
    }

    @Test
    void plannedRouteIsNotClippedByItsOriginalSearchBounds()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(176d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 32d)
            ),
            78d,
            72d,
            (x, z) -> 82d
        );

        // The synthetic bend lies beyond the endpoints' original +160-block
        // planning padding, just like a real route moved by smoothing or
        // junction coordination. Production reads through sampleIfPlanned,
        // so it must still see the final published geometry there.
        final NTEHeadwaterNetwork.Sample direct = NTEHeadwaterNetwork.sampleTestStream(stream, 176, 0);
        final NTEHeadwaterNetwork.Sample planned = NTEHeadwaterNetwork.samplePlannedTestStream(stream, 176, 0);
        assertNotNull(direct);
        assertNotNull(planned, "the final route geometry must supersede its coarse planning bounds");
        assertEquals(direct.normalizedDistanceSq(), planned.normalizedDistanceSq(), 1.0e-9d);
        assertEquals(direct.waterSurfaceY(), planned.waterSurfaceY(), 1.0e-9d);
    }

    @Test
    void binaryWaterLookupIsBitIdenticalToTheFormerLinearWalk() throws Exception
    {
        final List<NTEHeadwaterNetwork.Vec> points = new ArrayList<>();
        for (int index = 0; index <= 256; index++)
        {
            points.add(new NTEHeadwaterNetwork.Vec(index * 1.25d, Math.sin(index * 0.17d) * 3d));
        }
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.testStreamFromRoute(
            points,
            91.75d,
            62d,
            (x, z) -> 96d
        );
        final Object route = routeObject(stream);
        final double[] distance = doubleArrayField(route, "distance");
        final double[] waterY = doubleArrayField(route, "waterY");
        final Method sample = route.getClass().getDeclaredMethod("sampleWaterYAtAlong", double.class);
        sample.setAccessible(true);

        final List<Double> targets = new ArrayList<>();
        targets.add(-1d);
        targets.add(0d);
        for (int index : new int[] { 1, 2, 31, 128, 255 })
        {
            targets.add(Math.nextDown(distance[index]));
            targets.add(distance[index]);
            targets.add(Math.nextUp(distance[index]));
            targets.add((distance[index - 1] + distance[index]) * 0.5d);
        }
        targets.add(distance[distance.length - 1]);
        targets.add(distance[distance.length - 1] + 1d);

        for (double target : targets)
        {
            final double expected = formerLinearWaterSample(distance, waterY, target);
            final double actual = (double) sample.invoke(route, target);
            assertEquals(
                Double.doubleToRawLongBits(expected),
                Double.doubleToRawLongBits(actual),
                "binary lookup changed the exact interpolation result at along=" + target
            );
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void cacheResetRetiresWholeImmutableIndexWithoutChangingResetSemantics() throws Exception
    {
        final NTEHeadwaterNetwork.TestStream oldest = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(new NTEHeadwaterNetwork.Vec(0d, 0d), new NTEHeadwaterNetwork.Vec(48d, 0d)),
            78d,
            72d,
            (x, z) -> 82d
        );
        final NTEHeadwaterNetwork.TestStream peer = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(new NTEHeadwaterNetwork.Vec(24d, -24d), new NTEHeadwaterNetwork.Vec(24d, 24d)),
            76d,
            70d,
            (x, z) -> 82d
        );
        final NTEHeadwaterNetwork.TestStream survivor = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(new NTEHeadwaterNetwork.Vec(512d, 0d), new NTEHeadwaterNetwork.Vec(560d, 0d)),
            78d,
            72d,
            (x, z) -> 82d
        );
        final Object oldestHeadwater = headwaterObject(oldest);
        final Object peerHeadwater = headwaterObject(peer);
        final Object survivorHeadwater = headwaterObject(survivor);
        final Field junctionPeers = oldestHeadwater.getClass().getDeclaredField("junctionPeers");
        junctionPeers.setAccessible(true);
        ((Set<Object>) junctionPeers.get(oldestHeadwater)).add(peerHeadwater);
        ((Set<Object>) junctionPeers.get(peerHeadwater)).add(oldestHeadwater);

        final NTEHeadwaterNetwork network = new NTEHeadwaterNetwork(1L, SEA_LEVEL, (x, z) -> 82d);
        final Field headwatersField = NTEHeadwaterNetwork.class.getDeclaredField("headwaters");
        headwatersField.setAccessible(true);
        final Map<Object, Object> headwaters = (Map<Object, Object>) headwatersField.get(network);
        final Field plannedIndexVersion = NTEHeadwaterNetwork.class.getDeclaredField("plannedIndexVersion");
        plannedIndexVersion.setAccessible(true);
        headwaters.put(new Object(), oldestHeadwater);
        headwaters.put(new Object(), peerHeadwater);
        headwaters.put(new Object(), survivorHeadwater);

        final Method indexPlanned = NTEHeadwaterNetwork.class.getDeclaredMethod(
            "indexPlanned",
            oldestHeadwater.getClass()
        );
        indexPlanned.setAccessible(true);
        indexPlanned.invoke(network, oldestHeadwater);
        indexPlanned.invoke(network, peerHeadwater);
        indexPlanned.invoke(network, survivorHeadwater);
        assertEquals(0L, plannedIndexVersion.getLong(network) & 1L);

        final Field plannedByChunkField = NTEHeadwaterNetwork.class.getDeclaredField("plannedByChunk");
        plannedByChunkField.setAccessible(true);
        final Map<Long, List<Object>> retiredIndex =
            (Map<Long, List<Object>>) plannedByChunkField.get(network);
        final List<Object> publishedSnapshot = retiredIndex.values().iterator().next();
        assertThrows(UnsupportedOperationException.class, () -> publishedSnapshot.add(survivorHeadwater));

        final Method reset = NTEHeadwaterNetwork.class.getDeclaredMethod("resetHeadwaterCache");
        reset.setAccessible(true);
        reset.invoke(network);

        assertEquals(0L, plannedIndexVersion.getLong(network) & 1L);
        final Map<Long, List<Object>> freshIndex =
            (Map<Long, List<Object>>) plannedByChunkField.get(network);
        assertNotSame(retiredIndex, freshIndex);
        assertTrue(headwaters.isEmpty());
        assertTrue(freshIndex.isEmpty());
        assertTrue(retiredIndex.values().stream().flatMap(List::stream)
            .anyMatch(candidate -> candidate == oldestHeadwater));
        assertTrue(retiredIndex.values().stream().flatMap(List::stream)
            .anyMatch(candidate -> candidate == peerHeadwater));
        assertTrue(retiredIndex.values().stream().flatMap(List::stream)
            .anyMatch(candidate -> candidate == survivorHeadwater));
        assertTrue(((Set<Object>) junctionPeers.get(oldestHeadwater)).contains(peerHeadwater));
        assertTrue(((Set<Object>) junctionPeers.get(peerHeadwater)).contains(oldestHeadwater));
    }

    @Test
    void routeSegmentIndexKeepsTheNearestNarrowSegmentWhenAWideSegmentReturnsNearby()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(48d, 0d),
                new NTEHeadwaterNetwork.Vec(48d, 12d),
                new NTEHeadwaterNetwork.Vec(0d, 12d)
            ),
            78d,
            72d,
            new double[] { 0.65d, 0.65d, 5.5d, 5.5d },
            (x, z) -> 82d,
            true
        );

        // The route returns close to itself with a much wider downstream
        // segment. The legacy rule still owns the column by the absolute
        // nearest centerline segment, so the narrow upstream segment must not
        // disappear from the broad-phase candidate set.
        for (int x = 0; x <= 48; x++)
        {
            for (int z = -2; z <= 14; z++)
            {
                assertTrue(NTEHeadwaterNetwork.routeSampleMatchesLinearReference(stream, x, z));
            }
        }
    }

    @Test
    void staticWaterFlowUsesIntermediateSixteenthTurnsAlongBends()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(12d, 0d),
                new NTEHeadwaterNetwork.Vec(12d, 12d)
            ),
            74d,
            72d,
            (x, z) -> 80d
        );

        assertEquals(Flow.EEE, NTEHeadwaterNetwork.sampleTestStream(stream, 0, 0).flow());
        assertEquals(Flow.EEE, NTEHeadwaterNetwork.sampleTestStream(stream, 4, 0).flow());
        assertEquals(Flow.SEE, NTEHeadwaterNetwork.sampleTestStream(stream, 10, 0).flow());
        assertEquals(Flow.S_E, NTEHeadwaterNetwork.sampleTestStream(stream, 12, 0).flow());
        assertEquals(Flow.SSE, NTEHeadwaterNetwork.sampleTestStream(stream, 12, 2).flow());
        assertEquals(Flow.SSS, NTEHeadwaterNetwork.sampleTestStream(stream, 12, 8).flow());
        assertEquals(Flow.SSS, NTEHeadwaterNetwork.sampleTestStream(stream, 12, 12).flow());
    }

    @Test
    void onlyActualTurnsReceiveLocalDiagonalConnectorCells()
    {
        final List<NTEHeadwaterNetwork.Vec> roundedTurn = NTEHeadwaterNetwork.smoothRoute(List.of(
            new NTEHeadwaterNetwork.Vec(0d, 0d),
            new NTEHeadwaterNetwork.Vec(8d, 0d),
            new NTEHeadwaterNetwork.Vec(8d, 8d)
        ));
        final List<NTEHeadwaterNetwork.Vec> straightDiagonal = List.of(
            new NTEHeadwaterNetwork.Vec(0d, 0d),
            new NTEHeadwaterNetwork.Vec(8d, 8d)
        );

        assertTrue(NTEHeadwaterNetwork.turnConnectorCount(roundedTurn) > 0,
            "the short diagonal transition inside a real bend must not leave a one-column hole");
        assertTrue(NTEHeadwaterNetwork.isTurnConnector(roundedTurn, 7, 2),
            "the exit half of the rounded bend must open the inside corner instead of extending the incoming ledge");
        assertEquals(0, NTEHeadwaterNetwork.turnConnectorCount(straightDiagonal),
            "a diagonal creek run must not be expanded into a global four-connected strip");
    }

    @Test
    void everyRetainedTfcLeafWidensGraduallyFromTheNarrowSourceScale()
    {
        assertEquals(0.36d, NTEHeadwaterNetwork.sourceWidthScale(-10d), 1.0e-9d);
        assertEquals(0.36d, NTEHeadwaterNetwork.sourceWidthScale(0d), 1.0e-9d);
        assertTrue(NTEHeadwaterNetwork.sourceWidthScale(32d) > 0.36d);
        assertTrue(NTEHeadwaterNetwork.sourceWidthScale(32d) < 1d);
        assertEquals(1d, NTEHeadwaterNetwork.sourceWidthScale(64d), 1.0e-9d);
        assertEquals(1d, NTEHeadwaterNetwork.sourceWidthScale(96d), 1.0e-9d);
    }

    @Test
    void headwaterContinuationTurnsTheReceiverInsteadOfCreatingAYJunction()
    {
        final List<NTEHeadwaterNetwork.Vec> aligned = NTEHeadwaterNetwork.alignTestRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(-96d, 0d),
                new NTEHeadwaterNetwork.Vec(-80d, 0d),
                new NTEHeadwaterNetwork.Vec(-64d, 0d),
                new NTEHeadwaterNetwork.Vec(-48d, 0d),
                new NTEHeadwaterNetwork.Vec(-32d, 0d),
                new NTEHeadwaterNetwork.Vec(-16d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 0d)
            ),
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 96d)
            ),
            8d
        );

        assertTrue(aligned.size() > 10);
        assertEquals(new NTEHeadwaterNetwork.Vec(-96d, 0d), aligned.get(0));
        final NTEHeadwaterNetwork.Vec end = aligned.get(aligned.size() - 1);
        assertEquals(0d, end.x(), 1.0e-6d);
        assertEquals(0d, end.z(), 1.0e-6d,
            "the aligned creek must end at the shared topology node instead of following beside the receiver");

        int firstTurn = 1;
        while (firstTurn < aligned.size() && aligned.get(firstTurn).z() == 0d)
        {
            firstTurn++;
        }
        final NTEHeadwaterNetwork.Vec transitionStart = aligned.get(Math.max(1, firstTurn - 1));
        final NTEHeadwaterNetwork.Vec transitionBefore = aligned.get(Math.max(0, firstTurn - 2));
        final NTEHeadwaterNetwork.Vec beforeEnd = aligned.get(aligned.size() - 2);
        final double earlyDirection = Math.atan2(
            transitionStart.z() - transitionBefore.z(),
            transitionStart.x() - transitionBefore.x()
        );
        final double outletDirection = Math.atan2(end.z() - beforeEnd.z(), end.x() - beforeEnd.x());
        assertTrue(Math.cos(earlyDirection) > 0.35d, "the source side must still inherit the east-flowing creek");
        assertTrue(Math.sin(outletDirection) > 0.92d, "the downstream end must align with the south-running receiver");
        final double contactDot = NTEHeadwaterNetwork.firstVisibleContactFlowDot(
            aligned,
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 96d)
            ),
            8d
        );
        assertTrue(contactDot >= 0.90d,
            "the visible merge must be within about 26 degrees of the receiver flow; actual dot=" + contactDot);
    }

    @Test
    void obtuseFlowConfluenceUsesEnoughRunwayToMeetTheReceiverSmoothly()
    {
        final List<NTEHeadwaterNetwork.Vec> receiver = List.of(
            new NTEHeadwaterNetwork.Vec(0d, 0d),
            new NTEHeadwaterNetwork.Vec(-113.137d, 113.137d)
        );
        final List<NTEHeadwaterNetwork.Vec> aligned = NTEHeadwaterNetwork.alignTestRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(-176d, 0d),
                new NTEHeadwaterNetwork.Vec(-160d, 0d),
                new NTEHeadwaterNetwork.Vec(-144d, 0d),
                new NTEHeadwaterNetwork.Vec(-128d, 0d),
                new NTEHeadwaterNetwork.Vec(-112d, 0d),
                new NTEHeadwaterNetwork.Vec(-96d, 0d),
                new NTEHeadwaterNetwork.Vec(-80d, 0d),
                new NTEHeadwaterNetwork.Vec(-64d, 0d),
                new NTEHeadwaterNetwork.Vec(-48d, 0d),
                new NTEHeadwaterNetwork.Vec(-32d, 0d),
                new NTEHeadwaterNetwork.Vec(-16d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 0d)
            ),
            receiver,
            8d
        );

        final NTEHeadwaterNetwork.Vec end = aligned.get(aligned.size() - 1);
        assertEquals(0d, end.x(), 1.0e-6d);
        assertEquals(0d, end.z(), 1.0e-6d,
            "even an obtuse approach must join at the shared topology node");
        final double contactDot = NTEHeadwaterNetwork.firstVisibleContactFlowDot(aligned, receiver, 8d);
        assertTrue(contactDot >= 0.90d,
            "the creek must be nearly parallel when its water first visibly meets the receiver; actual dot=" + contactDot);
    }

    @Test
    void receiverAlignmentAnchorsAtTheSharedTopologyNodeInsteadOfANearbyBend()
    {
        final List<NTEHeadwaterNetwork.Vec> aligned = NTEHeadwaterNetwork.alignTestRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(-96d, 0d),
                new NTEHeadwaterNetwork.Vec(-48d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 0d)
            ),
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(0d, 96d),
                new NTEHeadwaterNetwork.Vec(-40d, 96d),
                new NTEHeadwaterNetwork.Vec(-40d, 0d)
            ),
            8d
        );

        final NTEHeadwaterNetwork.Vec end = aligned.get(aligned.size() - 1);
        assertEquals(0d, end.x(), 1.0e-6d);
        assertEquals(0d, end.z(), 1.0e-6d,
            "a nearby receiver return bend must not move the shared topology endpoint");
    }

    @Test
    void receiverWidthGuidesOnlyReceiverGeometryWithoutInflatingTheCreek()
    {
        final double receiverWidth = 8d;
        final double receiverGeometryRadius = NTEHeadwaterNetwork.matchedReceiverRadius(receiverWidth);

        assertEquals(
            receiverWidth * Math.sqrt(NTERiverHydrology.TFC_WATER_CORE_RADIUS_SQ),
            receiverGeometryRadius * Math.sqrt(NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ),
            1.0e-9d,
            "receiver geometry still measures the native physical water-core radius"
        );
    }

    @Test
    void alignedMouthFanCutsMonotonicallyTowardTheReceiver()
    {
        assertEquals(8d, NTEHeadwaterNetwork.alignedMouthCutLength(2.5d), 1.0e-9d,
            "the complete fan stays cut-only and cannot build a mouth platform");
        assertEquals(1.5d, NTEHeadwaterNetwork.alignedMouthWaterCutLength(2.5d), 1.0e-9d,
            "water must continue through the cut-only fan until the restored TFC core takes ownership");
    }

    @Test
    void alignedMouthUsesOneContinuousBankAndWaterTransition()
    {
        assertEquals(1d, NTEHeadwaterNetwork.mouthBankFillWeight(24d, BASELINE_MOUTH_WINDOW), 1.0e-9d);
        assertEquals(0.5d, NTEHeadwaterNetwork.mouthBankFillWeight(16d, BASELINE_MOUTH_WINDOW), 1.0e-9d);
        assertEquals(0d, NTEHeadwaterNetwork.mouthBankFillWeight(8d, BASELINE_MOUTH_WINDOW), 1.0e-9d);
        assertEquals(0d, NTEHeadwaterNetwork.mouthBankFillWeight(0d, BASELINE_MOUTH_WINDOW), 1.0e-9d);
        assertEquals(0d, NTEHeadwaterNetwork.mouthReceiverBlendWeight(24d), 1.0e-9d);
        assertEquals(0d, NTEHeadwaterNetwork.mouthReceiverBlendWeight(8d), 1.0e-9d);
        assertEquals(0.5d, NTEHeadwaterNetwork.mouthReceiverBlendWeight(4d), 1.0e-9d);
        assertEquals(1d, NTEHeadwaterNetwork.mouthReceiverBlendWeight(0d), 1.0e-9d);
        assertEquals(0d, NTEHeadwaterNetwork.mouthOuterBankReceiverBlendWeight(24d, BASELINE_MOUTH_WINDOW), 1.0e-9d);
        assertEquals(0.5d, NTEHeadwaterNetwork.mouthOuterBankReceiverBlendWeight(16d, BASELINE_MOUTH_WINDOW), 1.0e-9d);
        assertEquals(1d, NTEHeadwaterNetwork.mouthOuterBankReceiverBlendWeight(8d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "only the dry outer bank completes its handoff before the cut-only fan begins");

        double previous = 1d;
        for (int distanceToOutlet = 23; distanceToOutlet >= 0; distanceToOutlet--)
        {
            final double current = NTEHeadwaterNetwork.mouthBankFillWeight(distanceToOutlet, BASELINE_MOUTH_WINDOW);
            assertTrue(current <= previous, "bank construction must fade monotonically toward cut-only ownership");
            previous = current;
        }

        assertEquals(0.4d, NTEHeadwaterNetwork.mouthWaterDrop(4d, 65.4d, 65d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "a flat confluence may not lower supplemental water below the receiver surface");
        assertEquals(5.9d, NTEHeadwaterNetwork.mouthWaterDrop(4d, 67.9d, 62d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "the final fan must finish water-level handoff instead of retaining a raised shelf");
        assertEquals(2.95d, NTEHeadwaterNetwork.mouthWaterDrop(16d, 67.9d, 62d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "water descends smoothly while the dry banks transfer to the receiver");
        assertEquals(0d, NTEHeadwaterNetwork.mouthWaterDrop(0d, 65d, 65d, BASELINE_MOUTH_WINDOW), 1.0e-9d);
        assertEquals(0d, NTERiverHydrology.wideShapeWeight(3d), 1.0e-9d);
        assertEquals(0.5d, NTERiverHydrology.wideShapeWeight(3.5d), 1.0e-9d);
        assertEquals(1d, NTERiverHydrology.wideShapeWeight(4d), 1.0e-9d);
    }

    @Test
    void mouthWindowOnlyGrowsAboveTheBaselineAndStopsAtTheConfiguredCap()
    {
        assertEquals(BASELINE_MOUTH_WINDOW, NTEHeadwaterNetwork.mouthTransitionLength(0d, 1d, 400d), 1.0e-9d,
            "a flat mouth keeps the historical window");
        assertEquals(BASELINE_MOUTH_WINDOW, NTEHeadwaterNetwork.mouthTransitionLength(24d, 1.5d, 400d), 1.0e-9d,
            "a drop at the baseline is still the historical window for every tier");
        assertEquals(40d, NTEHeadwaterNetwork.mouthTransitionLength(40d, 1d, 400d), 1.0e-9d,
            "each extra block of drop adds one block of window at the middle tier");
        assertEquals(32d, NTEHeadwaterNetwork.mouthTransitionLength(40d, 0.5d, 400d), 1.0e-9d,
            "the steep tier adds half of the extra drop");
        assertEquals(48d, NTEHeadwaterNetwork.mouthTransitionLength(40d, 1.5d, 400d), 1.0e-9d,
            "the gentle tier adds one and a half times the extra drop");
        assertEquals(64d, NTEHeadwaterNetwork.mouthTransitionLength(400d, 1.5d, 800d), 1.0e-9d,
            "a very deep drop stops at the configured maximum window");
        assertEquals(30d, NTEHeadwaterNetwork.mouthTransitionLength(40d, 1d, 60d), 1.0e-9d,
            "a short route may never turn more than half of itself into one mouth ramp");
        assertEquals(BASELINE_MOUTH_WINDOW, NTEHeadwaterNetwork.mouthTransitionLength(40d, 1d, 20d), 1.0e-9d,
            "the half-route limit may not shrink the window below the historical baseline");

        double previous = 0d;
        for (double drop = 0d; drop <= 120d; drop += 4d)
        {
            final double window = NTEHeadwaterNetwork.mouthTransitionLength(drop, 1d, 900d);
            assertTrue(window >= previous, "the window must never shrink as the drop grows");
            assertTrue(window >= BASELINE_MOUTH_WINDOW && window <= 64d,
                "the window must stay inside the configured baseline and cap");
            previous = window;
        }
    }

    @Test
    void transitionTierIsStablePerCreekAndCoversEveryTier()
    {
        for (long seed = -50L; seed <= 50L; seed++)
        {
            assertEquals(
                NTEHeadwaterNetwork.transitionTierMultiplier(seed),
                NTEHeadwaterNetwork.transitionTierMultiplier(seed),
                1.0e-9d,
                "the same creek seed must always resolve to the same tier"
            );
        }

        final Set<Double> tiers = new java.util.HashSet<>();
        for (long seed = 0L; seed < 400L; seed++)
        {
            final double tier = NTEHeadwaterNetwork.transitionTierMultiplier(seed);
            assertTrue(tier == 0.5d || tier == 1.0d || tier == 1.5d,
                "only the three configured tiers may appear, found " + tier);
            tiers.add(tier);
        }
        assertEquals(3, tiers.size(), "all three tiers must occur across a population of creeks");
    }

    @Test
    void gentleMouthKeepsTheHistoricalWindowWhileASteepMouthWidensIt()
    {
        final NTEHeadwaterNetwork.TestStream gentle = NTEHeadwaterNetwork.planTestStream(
            918273645L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 64d + Math.max(0d, x) * 0.18d + Math.abs(z) * 0.04d
        );
        assertTrue(gentle.valid(), "the gentle fixture must still plan a stream");
        assertEquals(BASELINE_MOUTH_WINDOW, gentle.mouthTransitionLength(), 1.0e-9d,
            "a creek that already reaches the receiver at the baseline keeps its historical window");

        final NTEHeadwaterNetwork.TestStream steep = NTEHeadwaterNetwork.planTestStream(
            918273645L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 100d + Math.max(0d, x) * 0.02d + Math.abs(z) * 0.04d
        );
        assertTrue(steep.valid(), "the steep fixture must still plan a stream");
        assertTrue(steep.mouthTransitionLength() > BASELINE_MOUTH_WINDOW,
            "a creek that must still lose dozens of blocks at the mouth has to widen its transition");
        assertTrue(steep.mouthTransitionLength() <= 64d,
           "the widened transition still respects the configured cap");
    }

    @Test
    void deepSurfaceCutTurnsIntoACoveredTunnelAndStaysTunable()
    {
        final NTEHeadwaterNetwork.TestStream shallow = NTEHeadwaterNetwork.planTestStream(
            24681357L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 64d + Math.max(0d, x) * 0.18d + Math.abs(z) * 0.04d
        );
        assertTrue(shallow.valid());
        assertTrue(
            shallow.points().stream().noneMatch(NTEHeadwaterNetwork.DiagnosticPoint::subterranean),
            "a creek whose cut stays shallow must keep flowing on the surface"
        );

        final NTEHeadwaterNetwork.TestStream deep = NTEHeadwaterNetwork.planTestStream(
            24681357L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            // Deep enough that the sustained run clears the configured
            // sink_min_cut / sink_min_run defaults with margin.
            (x, z) -> 112d + Math.max(0d, x) * 0.02d + Math.abs(z) * 0.04d
        );
        assertTrue(deep.valid());
        final List<NTEHeadwaterNetwork.DiagnosticPoint> points = deep.points();
        final long tunnelPoints = points.stream()
            .filter(NTEHeadwaterNetwork.DiagnosticPoint::subterranean)
            .count();
        assertTrue(tunnelPoints > 0,
            "a creek cut far deeper than the threshold must be marked as an underground section");

        double previousWater = Double.POSITIVE_INFINITY;
        for (final NTEHeadwaterNetwork.DiagnosticPoint point : points)
        {
            assertTrue(point.waterY() <= previousWater + 1.0e-6d, "water may never climb downstream");
            previousWater = point.waterY();
            if (point.subterranean())
            {
                assertTrue(point.tunnelCeilingY() <= point.terrainY() - 4d + 1.0e-6d,
                    "a covered section must keep its rock cover");
                assertTrue(point.waterY() <= point.tunnelCeilingY() - 2d + 1.0e-6d,
                    "a covered section must keep headroom above its own water surface");
            }
        }
    }

    @Test
    void incisionValidationUsesVisibleBlockDepthInsteadOfFractionalNoise()
    {
        assertEquals(5, NTEHeadwaterNetwork.visibleIncisionDepth(92.60d, 86.25d),
            "a fractional 5.10-height difference still exposes only five vertical blocks");
        assertEquals(7, NTEHeadwaterNetwork.visibleIncisionDepth(91.43d, 83.95d),
            "a genuinely deep fallback trench must remain rejected");
    }

    @Test
    void caveMouthCutsAFunnelInsteadOfOneFlatSlab()
    {
        final long caveSeed = 42424242L;
        final List<NTEHeadwaterNetwork.SubterraneanRun> runs =
            List.of(new NTEHeadwaterNetwork.SubterraneanRun(200d, 400d));
        final double radius = 4.5d;
        final double terrainY = 101d;
        final double waterY = 62d;
        final double bedY = waterY - NTERiverHydrology.baseCenterDepth(radius);
        final double atRunEnd = 399d;

        final double centre = NTEHeadwaterNetwork.mouthCutAt(
            caveSeed, runs, atRunEnd, radius, terrainY, waterY, 0d, -626, -2199);
        final double channelEdge = NTEHeadwaterNetwork.mouthCutAt(
            caveSeed, runs, atRunEnd, radius, terrainY, waterY, 1d, -626, -2199);
        final double shoulder = NTEHeadwaterNetwork.mouthCutAt(
            caveSeed, runs, atRunEnd, radius, terrainY, waterY, 2.25d, -626, -2199);

        assertTrue(centre > channelEdge && channelEdge > shoulder,
            "the mouth must be a funnel: deepest at the channel centre and rising outward");
        // The lateral rise now obeys mouth_max_slope, so a narrow creek no longer walls itself
        // in with a steep rim. The mouth must still fall away outward, just more gently.
        assertTrue(shoulder < centre * 0.9d,
            "the mouth must fall away laterally instead of cutting one flat slab at full depth");
        assertTrue(centre > shoulder * 1.1d, "the lateral rise must stay a funnel");
        assertTrue(centre <= terrainY - bedY + 4d,
            "the mouth may never dig deeper than the drop to its own bed plus rim noise");
        assertEquals(centre, NTEHeadwaterNetwork.mouthCutAt(
                caveSeed, runs, atRunEnd, radius, terrainY, waterY, 0d, -626, -2199),
            0d, "the same creek and column must always produce the same mouth");

        assertEquals(0d, NTEHeadwaterNetwork.mouthCutAt(
                caveSeed, runs, 100d, radius, terrainY, waterY, 0d, -626, -2199),
            0d, "far outside the entrance band the mouth must not cut anything");

        // A deeper mouth has to be graded over a longer run instead of dropping
        // into the receiver as one near vertical slot.
        final double shallowExtent = mouthRampExtent(caveSeed, runs, radius, terrainY, waterY);
        final double deepExtent = mouthRampExtent(caveSeed, runs, radius, terrainY + 40d, waterY);
        assertTrue(shallowExtent >= 2d * radius - 1.0e-9d,
            "even a shallow mouth keeps the radius scaled ramp");
        assertTrue(deepExtent > shallowExtent,
            "a deeper mouth must grade open over a longer distance");
    }

    /** Distance from the run end over which the mouth still lowers terrain. */
    private static double mouthRampExtent(
        long caveSeed,
        List<NTEHeadwaterNetwork.SubterraneanRun> runs,
        double radius,
        double terrainY,
        double waterY
    )
    {
        double extent = 0d;
        for (double inward = 0d; inward <= 128d; inward += 0.5d)
        {
            final double cut = NTEHeadwaterNetwork.mouthCutAt(
                caveSeed, runs, 400d - inward, radius, terrainY, waterY, 0d, -626, -2199);
            if (cut > 0d)
            {
                extent = inward;
            }
        }
        return extent;
    }

    @Test
    void mouthIncisionIsNotAttenuatedTwiceByTheChannelCrossSection()
    {
        assertEquals(1d, NTERiverHydrology.supplementalLocalDepth(2.5d, 0d, 0.8d), 1.0e-9d,
            "ordinary outer-bank depth stays unchanged when no mouth incision exists");
        assertEquals(1.75d, NTERiverHydrology.supplementalLocalDepth(2.5d, 0.75d, 0.8d), 1.0e-9d,
            "the already feathered fan incision must be applied once after the base cross-section");
        assertEquals(3.25d, NTERiverHydrology.supplementalLocalDepth(2.5d, 0.75d, 0d), 1.0e-9d);

    }

    @Test
    void receiverBedHandoffCompletesBeforeTheSupplementalWaterCoreCloses()
    {
        assertEquals(0d, NTEHeadwaterNetwork.mouthReceiverBedBlendWeight(8d, 3d), 1.0e-9d);
        assertEquals(0.5d, NTEHeadwaterNetwork.mouthReceiverBedBlendWeight(5.5d, 3d), 1.0e-9d,
            "the terminal bed must use one smooth curve rather than a late fixed-depth shelf");
        assertEquals(1d, NTEHeadwaterNetwork.mouthReceiverBedBlendWeight(3d, 3d), 1.0e-9d,
            "bed ownership must be complete when supplemental water ownership closes");

        final NTERiverHydrology.ColumnProfile halfway = NTERiverHydrology.adaptToRetainedReceiverBed(
            withReceiverBedBlend(profileAt(0.6d), 0.5d),
            70d
        );
        assertNotNull(halfway);
        assertEquals(73d, halfway.centerBedY(), 1.0e-9d);
        assertEquals(73.25d, halfway.bedY(), 1.0e-9d);
        assertEquals(74.25d, NTERiverHydrology.clampToAdaptedReceiverBed(halfway, 90d), 1.0e-9d,
            "height and density stages must consume the same smoothly adapted bed");

        final NTERiverHydrology.ColumnProfile noFill = NTERiverHydrology.adaptToRetainedReceiverBed(
            withReceiverBedBlend(profileAt(0.6d), 1d),
            90d
        );
        assertNotNull(noFill);
        assertEquals(76.5d, noFill.bedY(), 1.0e-9d,
            "a higher receiver sample may not fill or raise the creek bed");

        assertEquals(0.2d, NTERiverHydrology.retainedReceiverCrossSectionSampleInfo(riverAt(0.5d), 0.2d).normDistSq(), 1.0e-9d,
            "receiver floor probes must preserve the selected cross-section radius");
        assertEquals(0.2d, NTERiverHydrology.retainedReceiverCrossSectionSampleInfo(riverAt(0.2d), 0.3d).normDistSq(), 1.0e-9d,
            "receiver floor probes may not move outward past the real column");
        assertTrue(NTERiverHydrology.crossesReceiverInnerBankCliff(80d, 58d),
            "the tall-canyon inner-bank drop should be detected");
        assertFalse(NTERiverHydrology.crossesReceiverInnerBankCliff(61d, 58d),
            "a normal three-block descent must remain a slope rather than a cliff trigger");
        assertEquals(56d, NTERiverHydrology.receiverDensityBedY(
            62,
            30,
            y -> y >= 57
        ), 1.0e-9d, "a density-carved receiver must hand off to its first solid bed layer");
        assertEquals(Double.POSITIVE_INFINITY, NTERiverHydrology.receiverDensityBedY(
            62,
            30,
            y -> false
        ), "solid terrain without an opened receiver column is not a density bed target");
        assertEquals(Double.POSITIVE_INFINITY, NTERiverHydrology.receiverDensityBedY(
            62,
            30,
            y -> true
        ), "an unbounded open cave must not invent a fixed floor");
    }

    @Test
    void descendingMouthIncisionFeathersAcrossTheCompleteDryBank()
    {
        assertEquals(1d, NTEHeadwaterNetwork.mouthBankIncisionLateralWeight(
            NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ
        ), 1.0e-9d,
            "the generated water core must receive the complete mouth descent");
        assertEquals(0.5d, NTEHeadwaterNetwork.mouthBankIncisionLateralWeight(
            (2.25d + NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ) * 0.5d
        ), 1.0e-9d,
            "the incision must remain continuous through the middle of the visible dry shoulder");
        assertTrue(NTEHeadwaterNetwork.mouthBankIncisionLateralWeight(1.209d) > 0.5d,
            "the reported sharp-bank column must descend with the receiver instead of retaining its upstream bed");
        assertTrue(NTEHeadwaterNetwork.mouthBankIncision(6.8d, 1.209d)
                > NTEHeadwaterNetwork.mouthBankIncision(6.8d, 1.53d),
            "moving a junction sample inward during filleting must also deepen its matching bank incision");
        assertEquals(0d, NTEHeadwaterNetwork.mouthBankIncisionLateralWeight(2.25d), 1.0e-9d,
            "ordinary terrain outside the supplemental bank may not be excavated");
    }

    @Test
    void mouthDistanceFieldSeparatesTheWetConnectorFromTheOuterBankHandoff()
    {
        assertEquals(427.6186951179694d, NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(
            427.6186951179694d,
            0.8434586539486739d,
            200d,
            BASELINE_MOUTH_WINDOW
        ), 1.0e-9d,
            "a distant receiver column must not import an upstream high-water profile into the main river");
        assertEquals(0.2d, NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(0.2d, 4d, 24d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "the ordinary creek cross-section remains authoritative before bank handoff starts");
        assertEquals(0.2d, NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(0.2d, 4d, 10d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "a wet creek-center column may not be reclassified outside the receiver before the final fan");
        final double halfway = NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(0.2d, 4d, 4d, BASELINE_MOUTH_WINDOW);
        assertEquals(0.2d, halfway, 1.0e-9d,
            "terrain SDF remains a union while water and flow perform their longitudinal handoff");
        assertEquals(0.2d, NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(0.2d, 4d, 0d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "terrain carving is the union of both channels even after water ownership reaches the receiver");
        assertEquals(0.2d, NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(4d, 0.2d, 0d, BASELINE_MOUTH_WINDOW), 1.0e-9d,
            "the receiver-aligned side of a high-angle mouth must be opened instead");
        assertTrue(NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(0.924d, 1.011d, 4d, BASELINE_MOUTH_WINDOW) < 1d,
            "the first reported pillar must remain connected to the creek side of the rotating mouth");
        assertTrue(NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(0.835d, 1.327d, 6d, BASELINE_MOUTH_WINDOW) < 1d,
            "the second reported pillar must not become a closed dry island between both channels");
        assertTrue(NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(
            1.2752723888173887d,
            3.034d,
            12d,
            BASELINE_MOUTH_WINDOW
        ) <= 1.2752723888173887d,
            "a creek dry shoulder must remain in the union and may only be carved farther by the confluence fillet");
        final double transitioningBank = NTEHeadwaterNetwork.mouthGeometryNormalizedDistanceSq(1d, 1d, 16d, BASELINE_MOUTH_WINDOW);
        assertTrue(transitioningBank < 1d
                && transitioningBank > NTEHeadwaterNetwork.smoothConfluenceNormalizedDistanceSq(1d, 1d),
            "the fillet must fade in longitudinally instead of appearing along the receiver's entire course");
    }

    @Test
    void confluenceFilletRoundsOnlyTheDryBankInsteadOfWideningTheWetCore()
    {
        assertEquals(0.4d, NTEHeadwaterNetwork.smoothConfluenceNormalizedDistanceSq(0.4d, 0.4d), 1.0e-9d,
            "the shared wet core must remain the exact union so water width and source ownership do not change");
        final double roundedBank = NTEHeadwaterNetwork.smoothConfluenceNormalizedDistanceSq(1d, 1d);
        assertTrue(roundedBank < 0.75d,
            "two equal bank edges need a real fillet instead of retaining the hard-min medial ridge");
        assertTrue(roundedBank > NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ,
            "rounding a dry bank must not create a wider generated-water core");
        assertEquals(1d, NTEHeadwaterNetwork.smoothConfluenceNormalizedDistanceSq(1d, 4d), 1.0e-9d,
            "a distant second channel must not flare an ordinary single bank");
        final double leftRight = NTEHeadwaterNetwork.smoothConfluenceNormalizedDistanceSq(0.92d, 1.08d);
        final double rightLeft = NTEHeadwaterNetwork.smoothConfluenceNormalizedDistanceSq(1.08d, 0.92d);
        assertEquals(leftRight, rightLeft, 1.0e-9d,
            "the rounded confluence must not depend on route publication order");
    }

    @Test
    void cutOnlyMouthStillCarvesAndCarriesWaterWithoutTerrainFill()
    {
        final NTERiverHydrology.ColumnProfile mouth = new NTERiverHydrology.ColumnProfile(
            78d,
            76d,
            76.5d,
            0.508d,
            2.5d,
            0d,
            0.35d,
            3d,
            0d,
            NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ,
            false,
            true,
            false,
            0.5d,
            0d,
            true,
            false,
            NTERiverHydrology.ChannelKind.STREAM,
            NTERiverHydrology.ChannelMode.SURFACE,
            0d,
            0L,
            Flow.EEE
        );

        assertTrue(mouth.inChannel());
        assertTrue(mouth.inWaterCore(),
            "the outer half of the carved receiver blend must carry generated flowing water");
        assertFalse(mouth.inSourceWaterCore(),
            "the generated flowing fringe remains water without becoming a static source column");
        assertTrue(mouth.descendingReceiverMouth(),
            "only a cut-only receiver blend with real descent needs a stable rock lip");
        assertTrue(NTERiverHydrology.shouldUseSupplemental(mouth, null));
        assertTrue(NTERiverHydrology.shouldUseSupplemental(mouth, riverAt(0.5d)),
            "the receiver-aligned fan must keep ownership long enough to blend its banks and bed");
        assertTrue(NTERiverHydrology.protectsBedAt(mouth, mouth.bedBlockY()));
        assertTrue(NTERiverHydrology.protectsBedAt(mouth, mouth.bedBlockY() - 4),
            "the density-stage roof must cover a shallow cave beneath the creek");
        assertFalse(NTERiverHydrology.protectsBedAt(mouth, mouth.bedBlockY() - 5));
        assertEquals(59, NTERiverHydrology.effectiveBedBlockY(mouth, 59.18d));
        assertTrue(NTERiverHydrology.protectsBedAt(mouth, 59, 59.18d));
        assertFalse(NTERiverHydrology.protectsBedAt(mouth, mouth.bedBlockY(), 59.18d),
            "bed protection must follow a deeper retained receiver instead of rebuilding the creek lip");
        assertEquals(59.18d, NTERiverHydrology.clampToRetainedReceiverBed(
            mouth,
            riverAt(0.5d),
            64.95d,
            59.18d
        ), 1.0e-9d);
        assertTrue(NTERiverHydrology.usesConfluenceCarvingUnion(mouth, riverAt(0.5d)),
            "a real receiver overlap must combine both complete excavations instead of switching bank ownership");
        assertFalse(NTERiverHydrology.usesConfluenceCarvingUnion(mouth, null),
            "a supplemental-only mouth has no second excavation to union");
        assertEquals(64.95d, NTERiverHydrology.clampToRetainedReceiverBed(
            mouth,
            null,
            64.95d,
            59.18d
        ), 1.0e-9d,
            "a supplemental-only column must not borrow an unrelated receiver-bed depth");
        assertEquals(78d, mouth.applyBankFillTransition(78d, 82d), 1.0e-9d,
            "cut-only ownership may not retain any creek-built bank above the original terrain");
        assertEquals(76d, mouth.applyBankFillTransition(78d, 76d), 1.0e-9d,
            "bank fading must never attenuate an actual terrain cut");
        assertEquals(77.65d, mouth.terrainCutCeiling(78d), 1.0e-9d,
            "cut-only terrain must realize the planned mouth incision despite river-shape bank noise");
        assertTrue(NTERiverHydrology.clearsWetMouthHeadroom(mouth, mouth.waterBlockY() + 1));
        assertTrue(NTERiverHydrology.clearsWetMouthHeadroom(mouth, mouth.waterBlockY() + 2));
        assertTrue(NTERiverHydrology.clearsWetMouthHeadroom(mouth, mouth.waterBlockY() + 3));
        assertFalse(NTERiverHydrology.clearsWetMouthHeadroom(mouth, mouth.waterBlockY() + 4));
    }

    @Test
    void minimumRiverLayerSurvivesWaterfallMouthShelfCleanup()
    {
        final NTERiverHydrology.ColumnProfile minimumLanding = profileAtWater(62d, true);
        final NTERiverHydrology.ColumnProfile raisedLanding = profileAtWater(67d, true);
        final NTERiverHydrology.ColumnProfile ordinaryMouth = profileAtWater(67d, false);

        assertEquals(63, NTERiverHydrology.retainedMouthWaterClearFromY(minimumLanding, 62),
            "the fixed y=62 river-water layer may not be cleared at an underground-river join");
        assertEquals(67, NTERiverHydrology.retainedMouthWaterClearFromY(raisedLanding, 62),
            "a raised waterfall shelf is still removed so the generated fall can own it");
        assertEquals(68, NTERiverHydrology.retainedMouthWaterClearFromY(ordinaryMouth, 62));
        assertTrue(NTERiverHydrology.usesDirectionalReceiverSurfaceWater(minimumLanding, 62, 62),
            "a waterfall becomes directional TFC river water when it reaches the fixed receiver layer");
        assertFalse(NTERiverHydrology.usesDirectionalReceiverSurfaceWater(raisedLanding, 67, 62),
            "raised waterfall steps remain vanilla flowing water until receiver contact");
        assertTrue(NTERiverHydrology.blocksCaveDecoration(minimumLanding, 63),
            "late cave spikes may not rebuild a hardened shelf inside the wet corridor");
        assertTrue(NTERiverHydrology.blocksCaveColumn(minimumLanding, 48),
            "a hardened cave column growing from below may not cross the wet corridor");
        assertFalse(NTERiverHydrology.blocksCaveColumn(minimumLanding, 66),
            "an unrelated cave column beginning above the protected corridor remains available");
        assertTrue(NTERiverHydrology.blocksErosionSupport(minimumLanding, 63),
            "late erosion may not rebuild a hardened support block over receiver water");
        assertFalse(NTERiverHydrology.blocksErosionSupport(minimumLanding, minimumLanding.bedBlockY()),
            "the actual creek bed remains available to erosion stability handling");
    }

    @Test
    void supplementalStaticWaterKeepsItsOwnFlowAcrossNoRiverBiomes()
    {
        final NTERiverHydrology.ColumnProfile ordinarySource = withSourceWaterAllowed(
            profileAtWater(67d, false),
            true
        );
        final NTERiverHydrology.ColumnProfile dynamicWater = withSourceWaterAllowed(
            profileAtWater(67d, false),
            false
        );

        assertTrue(NTERiverHydrology.usesDirectionalSupplementalWater(ordinarySource, 67, 62),
            "a supplemental source owns its direction even when the base biome has no native rivers");
        assertFalse(NTERiverHydrology.usesDirectionalSupplementalWater(dynamicWater, 67, 62),
            "ordinary dynamic waterfall water must stay vanilla flowing water");
        assertTrue(NTERiverHydrology.usesDirectionalSupplementalWater(profileAtWater(62d, true), 62, 62),
            "receiver contact remains directional even when it is not a source-water column");
        assertEquals(ordinarySource.flow(), NTERiverHydrology.effectiveColumnFlow(ordinarySource, Flow.NONE),
            "a no-river biome must not erase the rebuilt route's final geometric flow");
        assertEquals(Flow.NONE, NTERiverHydrology.effectiveColumnFlow(null, Flow.NONE),
            "columns outside the supplemental water core must preserve TFC's native flow");
    }

    @Test
    void sourceWaterCoreRetractsWithoutShorteningTheGeneratedWaterfall()
    {
        assertEquals(1d, NTEHeadwaterNetwork.mouthSourceWaterInset(0.2d), 0d);
        assertEquals(2d, NTEHeadwaterNetwork.mouthSourceWaterInset(1d), 0d);
        assertFalse(NTEHeadwaterNetwork.mouthSourceWaterAllowed(4.5d, 3d, 1d),
            "the two compensated cells are generated as flowing water, not source water");
        assertTrue(NTEHeadwaterNetwork.mouthSourceWaterAllowed(5.1d, 3d, 1d));
        assertFalse(NTEHeadwaterNetwork.plannedSourceWaterAllowed(0.75d),
            "a planned downstream step must start as generated flowing water even before the mouth taper");
        assertTrue(NTEHeadwaterNetwork.plannedSourceWaterAllowed(0.25d));
        assertFalse(NTEHeadwaterNetwork.plannedSourceWaterAllowed(66.1d, 66.05d, 65.95d),
            "source water must retract two cells before a fractional profile crosses a block-water level");
        assertTrue(NTEHeadwaterNetwork.plannedSourceWaterAllowed(66.9d, 66.4d, 66.05d),
            "sub-block descent which stays on one water layer may remain static");
        assertFalse(NTEHeadwaterNetwork.naturalWaterfallLanding(66.9d, 66.1d),
            "a fractional fan descent inside the same route block is not a terrain waterfall");
        assertTrue(NTEHeadwaterNetwork.naturalWaterfallLanding(67d, 66.9d),
            "a real integer route drop still starts generation-time waterfall baking");

        // The full waterfall range remains governed by the separate 7-cell
        // generation-time spill bake; this helper only changes source shape.
        for (int step = 1; step <= 7; step++)
        {
            assertEquals(new NTERiverHydrology.CardinalStep(1, 0),
                NTERiverHydrology.cardinalFlowStep(Flow.EEE, step));
        }
    }

    @Test
    void steepHeadwaterOnlyLetsTheRouteOriginBypassSourceWaterSlopeRules()
    {
        final NTEHeadwaterNetwork.TestStream stream = NTEHeadwaterNetwork.testStreamFromRoute(
            List.of(
                new NTEHeadwaterNetwork.Vec(0d, 0d),
                new NTEHeadwaterNetwork.Vec(4d, 0d),
                new NTEHeadwaterNetwork.Vec(8d, 0d)
            ),
            80d,
            70d,
            (x, z) -> 82d
        );

        final NTEHeadwaterNetwork.Sample origin = NTEHeadwaterNetwork.sampleTestStream(stream, 0, 0);
        final NTEHeadwaterNetwork.Sample besideOrigin = NTEHeadwaterNetwork.sampleTestStream(stream, 0, 1);
        final NTEHeadwaterNetwork.Sample downstream = NTEHeadwaterNetwork.sampleTestStream(stream, 1, 0);
        assertNotNull(origin);
        assertNotNull(besideOrigin);
        assertNotNull(downstream);
        assertTrue(origin.sourceWaterAllowed(),
            "the first water column must remain a source even before an immediate steep descent");
        assertFalse(besideOrigin.sourceWaterAllowed(),
            "forcing the spring source may not widen it into neighboring columns");
        assertFalse(downstream.sourceWaterAllowed(),
            "all downstream columns must retain the existing steep-flow source rules");
    }

    @Test
    void diagonalWaterfallRasterUsesOneDownstreamCardinalStepAtATime()
    {
        final NTERiverHydrology.CardinalStep first = NTERiverHydrology.cardinalFlowStep(Flow.N_E, 1);
        final NTERiverHydrology.CardinalStep second = NTERiverHydrology.cardinalFlowStep(Flow.N_E, 2);

        assertEquals(1, Math.abs(first.x()) + Math.abs(first.z()));
        assertEquals(1, Math.abs(second.x()) + Math.abs(second.z()));
        assertTrue(Flow.N_E.getVector().x * first.x() + Flow.N_E.getVector().z * first.z() > 0d);
        assertTrue(Flow.N_E.getVector().x * second.x() + Flow.N_E.getVector().z * second.z() > 0d);
        assertFalse(first.equals(second), "a diagonal flow must alternate axes instead of branching into both at once");

        for (int step = 1; step <= 7; step++)
        {
            assertEquals(new NTERiverHydrology.CardinalStep(1, 0), NTERiverHydrology.cardinalFlowStep(Flow.EEE, step));
        }
    }

    @Test
    void supplementalChannelOnlyOverridesARetainedTfcRiverInsideItsActualBed()
    {
        final NTERiverHydrology.ColumnProfile channel = profileAt(0.5d);
        final NTERiverHydrology.ColumnProfile outerBank = profileAt(1.4d);
        final NTERiverHydrology.ColumnProfile receiverTransition = withReceiverBlend(outerBank, 0.5d);

        assertFalse(NTERiverHydrology.shouldUseSupplemental(channel, riverAt(0.20d)),
            "the retained main river owns its physical water core");
        assertTrue(NTERiverHydrology.shouldUseSupplemental(channel, riverAt(0.50d)),
            "the feeder may approach through the retained river's outer influence");
        assertFalse(NTERiverHydrology.shouldUseSupplemental(outerBank, riverAt(0.50d)));
        assertTrue(NTERiverHydrology.shouldUseSupplemental(outerBank, riverAt(1.01d)),
            "a distant receiver must not collapse the creek's dry-bank feather into a one-column cliff");
        assertFalse(NTERiverHydrology.shouldUseSupplemental(receiverTransition, riverAt(0.50d)),
            "a confluence dry shoulder must yield once it reaches the retained river's actual cross-section");
        assertTrue(NTERiverHydrology.shouldUseSupplemental(receiverTransition, riverAt(1.01d)),
            "the same dry shoulder remains supplemental before it reaches the retained river's physical bank");
        assertTrue(NTERiverHydrology.shouldUseSupplemental(outerBank, null));
    }

    @Test
    void retainedRiverObstacleCoversTheNativeTerrainCorridorBeyondItsWetCore()
    {
        final double widthSq = 22d * 22d;
        final double reportedSourceDistance = Math.sqrt(2.868d * widthSq);

        assertTrue(NTERiverHydrology.withinRetainedRiverTerrainCorridor(
            reportedSourceDistance,
            widthSq,
            6d
        ), "a spring may not start on a retained TALUS bank which is still deeply carved");
        assertFalse(NTERiverHydrology.withinRetainedRiverTerrainCorridor(
            50.01d,
            widthSq,
            6d
        ), "the obstacle must remain bounded once the native bank has returned to ambient terrain");
    }

    @Test
    void receiverShapeTransferKeepsTheCreekBankUntilTheSharedWetCenter()
    {
        final net.dries007.tfc.world.river.RiverInfo receiver = riverAt(0.25d);
        final NTERiverHydrology.ColumnProfile creek = profileAt(0.75d);
        final NTERiverHydrology.ColumnProfile halfway = withReceiverBlend(creek, 0.5d);
        final NTERiverHydrology.ColumnProfile receiverOwned = withReceiverBlend(creek, 1d);
        final NTERiverHydrology.ColumnProfile receiverCenter = withReceiverBlend(profileAt(0d), 1d);

        assertEquals(0.75d, NTERiverNoise.radialDistanceSq(receiver, creek), 1.0e-9d);
        assertEquals(0.75d, NTERiverNoise.radialDistanceSq(receiver, halfway), 1.0e-9d);
        assertEquals(0.75d, NTERiverNoise.radialDistanceSq(receiver, receiverOwned), 1.0e-9d,
            "water ownership alone must not replace the creek's dry shoulder with a canyon wall");
        assertEquals(0.25d, NTERiverNoise.radialDistanceSq(receiver, receiverCenter), 1.0e-9d,
            "the receiver's complete cross-section still owns the shared wet center");
        assertEquals(0d, NTERiverHydrology.receiverBankShapeBlendWeight(receiverOwned), 1.0e-9d);
        assertEquals(1d, NTERiverHydrology.receiverBankShapeBlendWeight(receiverCenter), 1.0e-9d);
    }

    @Test
    void densityCavesAndCarversShareOneTaperedShallowCorridor()
    {
        final int outerBankMinimum = NTERiverCaveProtection.minimumProtectedY(84d, 0d);
        final int waterCoreMinimum = NTERiverCaveProtection.minimumProtectedY(84d, 1d);
        final NTERiverCaveProtection.Geometry geometry = NTERiverCaveProtection.geometryForTest(
            new ChunkPos(0, 0),
            0,
            0,
            84d,
            2d
        );

        assertEquals(83, outerBankMinimum);
        assertEquals(81, waterCoreMinimum);
        assertTrue(waterCoreMinimum < outerBankMinimum, "the roof should thicken smoothly toward the water core");
        assertEquals(86, NTERiverCaveProtection.maximumProtectedY(90, 84d));
        assertTrue(waterCoreMinimum > 60, "deep caves must remain outside the protected bed shell");
        assertEquals(81, geometry.minimumProtectedY(0, 0, 90));
        assertEquals(86, geometry.maximumProtectedY(0, 0, 90));
        assertEquals(83, geometry.minimumProtectedY(12, 0, 90),
            "the outer bank receives only a one-block support below the creek bed");
        assertEquals(Integer.MAX_VALUE, geometry.minimumProtectedY(13, 0, 90),
            "caves beyond the dynamic river corridor remain available");

        final NTERiverCaveProtection.Geometry crossChunkGeometry = NTERiverCaveProtection.geometryForTest(
            new ChunkPos(0, 0),
            -1,
            0,
            84d,
            2d
        );
        assertTrue(crossChunkGeometry.minimumProtectedY(0, 0, 90) < Integer.MAX_VALUE,
            "a water core just across the chunk border must protect this chunk's shallow bank");

        try (NTERiverCaveProtection.DensityScope ignored = NTERiverCaveProtection.openDensity(geometry))
        {
            assertTrue(NTERiverCaveProtection.protectsDensity(0, 81, 0, 90));
            assertTrue(NTERiverCaveProtection.protectsDensity(0, 86, 0, 90));
            assertFalse(NTERiverCaveProtection.protectsDensity(0, 80, 0, 90),
                "the shared density mask stops below the support shell");
            assertFalse(NTERiverCaveProtection.protectsDensity(0, 87, 0, 90),
                "the mask must not refill a shallow cave room all the way to the surface");
        }
        assertFalse(NTERiverCaveProtection.protectsDensity(0, 81, 0, 90),
            "density protection must not leak between asynchronous chunk tasks");

        assertEquals(0.4d, NTERiverCaveProtection.preserveTerrainDensity(0.4d, -0.2d, true), 1.0e-9d,
            "density caves are ignored only where the original terrain was solid");
        assertEquals(-0.2d, NTERiverCaveProtection.preserveTerrainDensity(0.4d, -0.2d, false), 1.0e-9d);
        assertEquals(-0.5d, NTERiverCaveProtection.preserveTerrainDensity(-0.1d, -0.5d, true), 1.0e-9d,
            "the corridor must not fill positions which the original terrain already intended as air");
    }

    private static NTEHeadwaterNetwork.TestStream slopedValley(long seed)
    {
        return NTEHeadwaterNetwork.planTestStream(
            seed,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 65d + Math.max(0, x) * 0.04d + Math.abs(z) * 0.08d
        );
    }

    private static NTEHeadwaterNetwork.TestStream highlandDrainageStream()
    {
        return NTEHeadwaterNetwork.planTestStream(
            66778899L,
            SEA_LEVEL,
            320d,
            0d,
            0d,
            0d,
            16,
            (x, z) -> 95d + Math.max(0, x) * 0.02d + Math.abs(z) * 0.04d
        );
    }

    private static String routeGeometryHash(NTEHeadwaterNetwork.TestStream stream)
    {
        long hash = 0xcbf29ce484222325L;
        hash = mixRouteHash(hash, stream.attemptedDrainageCandidates());
        hash = mixRouteHash(hash, stream.availableDrainageCandidates());
        hash = mixRouteHash(hash, stream.points().size());
        for (NTEHeadwaterNetwork.DiagnosticPoint point : stream.points())
        {
            hash = mixRouteHash(hash, Double.doubleToLongBits(point.x()));
            hash = mixRouteHash(hash, Double.doubleToLongBits(point.z()));
            hash = mixRouteHash(hash, Double.doubleToLongBits(point.terrainY()));
            hash = mixRouteHash(hash, Double.doubleToLongBits(point.waterY()));
            hash = mixRouteHash(hash, Double.doubleToLongBits(point.radius()));
        }
        return Long.toUnsignedString(hash, 16);
    }

    private static long mixRouteHash(long hash, long value)
    {
        return (hash ^ value) * 0x100000001b3L;
    }

    private static void assertWaterNeverClimbs(List<NTEHeadwaterNetwork.DiagnosticPoint> points)
    {
        for (int index = 1; index < points.size(); index++)
        {
            assertTrue(points.get(index - 1).waterY() >= points.get(index).waterY() - 1.0e-9d,
                "coordinating a junction may not create an uphill outgoing branch");
        }
    }

    private static NTERiverHydrology.ColumnProfile profileAt(double normalizedDistanceSq)
    {
        return new NTERiverHydrology.ColumnProfile(
            78d,
            76d,
            76.5d,
            normalizedDistanceSq,
            2.5d,
            0d,
            0d,
            0d,
            1d,
            NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ,
            true,
            true,
            true,
            0d,
            0d,
            false,
            true,
            NTERiverHydrology.ChannelKind.STREAM,
            NTERiverHydrology.ChannelMode.SURFACE,
            0d,
            0L,
            Flow.NONE
        );
    }

    private static NTERiverHydrology.ColumnProfile profileAtWater(double waterY, boolean waterfallLanding)
    {
        return new NTERiverHydrology.ColumnProfile(
            waterY,
            waterY - 2d,
            waterY - 2d,
            0d,
            3d,
            0d,
            1d,
            0d,
            0d,
            NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ,
            false,
            true,
            false,
            0.5d,
            0d,
            waterfallLanding,
            false,
            NTERiverHydrology.ChannelKind.STREAM,
            NTERiverHydrology.ChannelMode.SURFACE,
            0d,
            0L,
            Flow.EEE
        );
    }

    private static net.dries007.tfc.world.river.RiverInfo riverAt(double normalizedDistanceSq)
    {
        return new net.dries007.tfc.world.river.RiverInfo(null, Flow.NONE, normalizedDistanceSq * 100d, 100d);
    }

    private static NTERiverHydrology.ColumnProfile withReceiverBlend(
        NTERiverHydrology.ColumnProfile profile,
        double receiverBlendWeight
    )
    {
        return new NTERiverHydrology.ColumnProfile(
            profile.waterSurfaceY(),
            profile.centerBedY(),
            profile.bedY(),
            profile.normalizedDistanceSq(),
            profile.channelRadius(),
            profile.bankRaise(),
            profile.terrainIncision(),
            profile.mouthWaterDrop(),
            profile.bankFillWeight(),
            profile.waterCoreRadiusSq(),
            profile.fillAllowed(),
            profile.waterAllowed(),
            profile.sourceWaterAllowed(),
            receiverBlendWeight,
            profile.receiverBedBlendWeight(),
            profile.waterfallLanding(),
            profile.headwater(),
            profile.kind(),
            profile.mode(),
            profile.tunnelCeilingY(),
            profile.caveSeed(),
            profile.flow()
        );
    }

    private static NTERiverHydrology.ColumnProfile withReceiverBedBlend(
        NTERiverHydrology.ColumnProfile profile,
        double receiverBedBlendWeight
    )
    {
        return new NTERiverHydrology.ColumnProfile(
            profile.waterSurfaceY(),
            profile.centerBedY(),
            profile.bedY(),
            profile.normalizedDistanceSq(),
            profile.channelRadius(),
            profile.bankRaise(),
            profile.terrainIncision(),
            profile.mouthWaterDrop(),
            profile.bankFillWeight(),
            profile.waterCoreRadiusSq(),
            profile.fillAllowed(),
            profile.waterAllowed(),
            profile.sourceWaterAllowed(),
            profile.receiverBlendWeight(),
            receiverBedBlendWeight,
            profile.waterfallLanding(),
            profile.headwater(),
            profile.kind(),
            profile.mode(),
            profile.tunnelCeilingY(),
            profile.caveSeed(),
            profile.flow()
        );
    }

    private static NTERiverHydrology.ColumnProfile withSourceWaterAllowed(
        NTERiverHydrology.ColumnProfile profile,
        boolean sourceWaterAllowed
    )
    {
        return new NTERiverHydrology.ColumnProfile(
            profile.waterSurfaceY(),
            profile.centerBedY(),
            profile.bedY(),
            profile.normalizedDistanceSq(),
            profile.channelRadius(),
            profile.bankRaise(),
            profile.terrainIncision(),
            profile.mouthWaterDrop(),
            profile.bankFillWeight(),
            profile.waterCoreRadiusSq(),
            profile.fillAllowed(),
            profile.waterAllowed(),
            sourceWaterAllowed,
            profile.receiverBlendWeight(),
            profile.receiverBedBlendWeight(),
            profile.waterfallLanding(),
            profile.headwater(),
            profile.kind(),
            profile.mode(),
            profile.tunnelCeilingY(),
            profile.caveSeed(),
            profile.flow()
        );
    }

    private static Object routeObject(NTEHeadwaterNetwork.TestStream stream) throws Exception
    {
        final Object headwater = headwaterObject(stream);
        final Method route = headwater.getClass().getDeclaredMethod("route");
        route.setAccessible(true);
        return route.invoke(headwater);
    }

    private static Object headwaterObject(NTEHeadwaterNetwork.TestStream stream) throws Exception
    {
        final Field headwaterField = NTEHeadwaterNetwork.TestStream.class.getDeclaredField("headwater");
        headwaterField.setAccessible(true);
        return headwaterField.get(stream);
    }

    private static double[] doubleArrayField(Object owner, String name) throws Exception
    {
        final Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (double[]) field.get(owner);
    }

    private static double formerLinearWaterSample(double[] distance, double[] waterY, double targetAlong)
    {
        if (targetAlong <= 0d)
        {
            return waterY[0];
        }
        if (targetAlong >= distance[distance.length - 1])
        {
            return waterY[waterY.length - 1];
        }
        int upper = 1;
        while (upper < distance.length && distance[upper] < targetAlong)
        {
            upper++;
        }
        final int lower = upper - 1;
        final double segmentLength = distance[upper] - distance[lower];
        final double delta = segmentLength <= 1.0e-9d
            ? 0d
            : (targetAlong - distance[lower]) / segmentLength;
        return Mth.lerp(delta, waterY[lower], waterY[upper]);
    }

}
