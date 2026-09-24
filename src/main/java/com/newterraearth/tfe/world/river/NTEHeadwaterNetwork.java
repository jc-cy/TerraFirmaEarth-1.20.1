package com.newterraearth.tfe.world.river;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.river.Flow;

import com.newterraearth.tfe.config.NTECommonConfig;

/**
 * Lazily plans a fine, terrain-sampled creek for each replaceable TFC leaf edge.
 * The TFC graph remains authoritative: the receiving edge is the outlet, while
 * only the route from the source area to its first physical contact is re-planned.
 */
final class NTEHeadwaterNetwork
{
    static final int SAMPLE_STEP = 8;
    private static final int PREFERRED_CORNER_PRECISION = 1;

    private static final int ROUTE_PADDING = 160;
    private static final int SOURCE_EXTENSION_MIN = 48;
    private static final int SOURCE_EXTENSION_MAX = 120;
    private static final int SOURCE_EXTENSION_LATERAL = 80;
    private static final int MAX_DRAINAGE_SOURCE_CANDIDATES = 12;
    private static final double DRAINAGE_SOURCE_SEPARATION = 24d;
    private static final double DRAINAGE_SPILL_EPSILON = 1.0e-6d;
    private static final double FAILED_ROUTE_OVERLAP_PENALTY = 6d;
    private static final double FAILED_ROUTE_LOCAL_PENALTY = 160d;
    private static final int FAILED_ROUTE_LOCAL_RADIUS = 3;
    private static final double PLANNED_INCISION_EXCESS_COST = 24d;
    private static final double JUNCTION_DETECTION_RADIUS = 4d;
    private static final double JUNCTION_MAX_WET_CONTACT_RADIUS = 10d;
    private static final double JUNCTION_BLOCK_CONTACT_MARGIN = 2d;
    private static final double JUNCTION_MIN_BRANCH_LENGTH = 32d;
    private static final double SHARED_CORRIDOR_DETECTION_RADIUS = 1.5d;
    private static final double SHARED_CORRIDOR_MIN_LENGTH = 12d;
    private static final double SHARED_CORRIDOR_TRANSITION_LENGTH = 16d;
    private static final double JUNCTION_TANGENT_REWRITE_LENGTH = 16d;
    private static final double JUNCTION_PROFILE_TRANSITION_LENGTH = 18d;
    private static final double JUNCTION_FLOW_TRANSITION_RADIUS = 6d;
    /** Normalized-radius fillet used only where two coordinated dry banks meet. */
    private static final double CONFLUENCE_BANK_FILLET_RADIUS = 0.55d;
    private static final double VALLEY_SNAP_RADIUS = 8d;
    private static final double VALLEY_SNAP_STEP = 2d;
    private static final double TFC_RIVER_ROUTE_CLEARANCE = 6d;
    private static final double TFC_RIVER_GRID_CLEARANCE = TFC_RIVER_ROUTE_CLEARANCE
        + SAMPLE_STEP * Math.sqrt(2d) * 0.5d;
    private static final double SOURCE_CHANNEL_RADIUS = 0.65d;
    private static final double CENTER_SURFACE_INSET = 1.25d;
    private static final double BANK_FREEBOARD = 0.10d;
    private static final double MAX_CASCADE_SLOPE = 1.0d;
    private static final double MAX_NORMAL_INCISION = 5.0d;
    private static final double FEEDER_RECEIVER_WIDTH_SCALE = 0.36d;
    private static final double TFC_LEAF_TAPER_LENGTH = 64d;
    private static final double SOURCE_ALIGNMENT_LENGTH = 48d;
    private static final double SOURCE_ALIGNMENT_REWRITE_LENGTH = 32d;
    private static final double SOURCE_ALIGNMENT_SAMPLE_SPACING = 1.5d;
    private static final double SOURCE_ALIGNMENT_FLOW_LOOKBACK = 24d;
    private static final double SOURCE_ALIGNMENT_MAX_EXTENSION = 96d;
    private static final double SOURCE_ALIGNMENT_RETRY_EXTENSION = 24d;
    private static final int SOURCE_ALIGNMENT_MAX_ATTEMPTS = 4;
    private static final double SOURCE_ALIGNMENT_TARGET_CONTACT_DOT = 0.90d;
    private static final double SOURCE_ALIGNMENT_SUPPRESSION_WIDTH_SCALE = 1.35d;
    private static final double FLOW_DIRECTION_SAMPLE_RADIUS = 4d;
    /** Reference offset used to measure how far the mouth must descend before the transition is sized. */
    private static final double MOUTH_DROP_REFERENCE_LENGTH = 24d;
    private static final double MOUTH_FAN_LENGTH = 8d;
    /** Salt for the per-creek transition tier, so the tier does not reuse the route seed itself. */
    private static final long TRANSITION_TIER_SALT = 0x51A7C3D9B2E64F1DL;
    /** Salt which separates the mouth floor noise from the mouth rim noise. */
    private static final long MOUTH_FLOOR_SALT = 0x13C6B0A5D9E7428FL;
    /** Allow a global leaf to descend through the final coastal water shelf. */
    private static final double SEA_MOUTH_WATER_TRANSITION_LENGTH = 16d;
    private static final double OUTLET_ADAPTER_LENGTH = SOURCE_ALIGNMENT_LENGTH + 40d;
    private static final double MAX_OUTLET_ADAPTER_INCISION = 8d;
    private static final int MAX_HEADWATER_CACHE_SIZE = 512;
    private static final double MAX_INFLUENCE_SQ = 2.25d;
    private static final double SPATIAL_INDEX_MARGIN = 12d;
    private static final int[] DRAINAGE_DIRECTIONS = {
        -1, -1, 0, -1, 1, -1,
        -1, 0,          1, 0,
        -1, 1,  0, 1,  1, 1
    };
    private static final boolean TRACE = Boolean.getBoolean("tfe.debug.runtimeTrace");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<Headwater> HEADWATER_ORDER = Comparator
        .comparingDouble((Headwater value) -> value.sourceX)
        .thenComparingDouble(value -> value.sourceZ)
        .thenComparingDouble(value -> value.drainX)
        .thenComparingDouble(value -> value.drainZ)
        .thenComparingLong(value -> value.seed);

    @FunctionalInterface
    interface HeightSampler
    {
        double sample(int blockX, int blockZ);
    }

    @FunctionalInterface
    interface RouteObstacleSampler
    {
        boolean blocks(@Nullable RiverEdge owner, double blockX, double blockZ, double clearance);
    }

    private static final RouteObstacleSampler NO_ROUTE_OBSTACLES = (owner, x, z, clearance) -> false;

    record Sample(
        double waterSurfaceY,
        double normalizedDistanceSq,
        double channelRadius,
        double extraIncision,
        double mouthWaterDrop,
        double bankFillWeight,
        double waterCoreRadiusSq,
        boolean fillAllowed,
        boolean waterAllowed,
        boolean sourceWaterAllowed,
        double receiverBlendWeight,
        double receiverBedBlendWeight,
        boolean waterfallLanding,
        boolean headwater,
        boolean subterranean,
        double tunnelCeilingY,
        /** Seed of this creek; drives the deterministic cavity and mouth noise. */
        long caveSeed,
        /** Terrain cut that grades a covered section open at its ends (0 = none). */
        double entranceCut,
        Flow flow
    ) {}

    static boolean samplePreferred(@Nullable Sample candidate, @Nullable Sample current)
    {
        if (candidate == null)
        {
            return false;
        }
        if (current == null)
        {
            return true;
        }
        final double distanceDelta = candidate.normalizedDistanceSq() - current.normalizedDistanceSq();
        if (Math.abs(distanceDelta) > 1.0e-9d)
        {
            return distanceDelta < 0d;
        }
        final double waterDelta = candidate.waterSurfaceY() - current.waterSurfaceY();
        return waterDelta < -1.0e-9d
            || Math.abs(waterDelta) <= 1.0e-9d && candidate.flow().ordinal() < current.flow().ordinal();
    }

    record DiagnosticPoint(
        double x,
        double z,
        double terrainY,
        double waterY,
        double radius,
        boolean subterranean,
        double tunnelCeilingY
    ) {}

    record Vec(double x, double z) {}

    /** Along-route extent of one covered section, used to grade its ends open. */
    record SubterraneanRun(double startAlong, double endAlong) {}

    /**
     * Terrain cut which turns the end of a covered section into a cave mouth.
     * The shape follows the vanilla cave river's mouth language, scaled to this
     * creek: a lateral funnel which reaches the bed at the channel centre and
     * climbs with the squared normalised radius, a longitudinal ramp whose
     * length grows with the drop so a deep mouth becomes a gorge instead of a
     * vertical slot, and noise which breaks the otherwise machine cut rim.
     * The cut is one sided: it may lower terrain but never raise it.
     *
     * @param waterY the water level the mouth is graded toward. A covered creek
     *               passes its already descended water, so the funnel floor is the
     *               bed the tunnel really carries instead of a level above it.
     */
    static double mouthCutAt(
        long caveSeed,
        List<SubterraneanRun> runs,
        double along,
        double channelRadius,
        double terrainY,
        double waterY,
        double normalizedDistanceSq,
        int blockX,
        int blockZ
    )
    {
        if (runs.isEmpty())
        {
            return 0d;
        }
        final double bedY = waterY - NTERiverHydrology.baseCenterDepth(channelRadius);
        final double radiusRamp = Mth.clamp(2d * channelRadius, 4d, 12d);
        final double noise = NTECommonConfig.getHeadwaterMouthNoise();
        final double rimNoise = noise <= 0d
            ? 0d
            : noise * NTERiverCaveNoise.noise(caveSeed, NTERiverCaveNoise.SALT_MOUTH, blockX, blockZ, 0.045d);
        for (SubterraneanRun run : runs)
        {
            final double inward = Math.min(along - run.startAlong(), run.endAlong() - along);
            if (inward < 0d)
            {
                continue;
            }
            final double drop = Math.max(0d, terrainY - bedY);
            final double slopeRamp = drop / NTECommonConfig.getHeadwaterMouthMaxSlope();
            final double rampLimit = Math.max(radiusRamp, NTECommonConfig.getHeadwaterMouthMaxLength());
            final double ramp = Mth.clamp(
                Math.max(radiusRamp, slopeRamp) * (1d + 0.3d * rimNoise),
                radiusRamp * 0.5d,
                rampLimit
            );
            if (inward > ramp)
            {
                continue;
            }
            final double weight = smootherStep(1d - inward / ramp);
            final double floorNoise = noise <= 0d
                ? 0d
                : noise * NTERiverCaveNoise.noise(
                    caveSeed,
                    NTERiverCaveNoise.SALT_MOUTH ^ MOUTH_FLOOR_SALT,
                    blockX,
                    blockZ,
                    0.09d
                );
            final double funnelFloor = bedY
                // Inside the channel's own core the floor stays exactly the creek bed, so the
                // channel and its water are untouched. Beyond it the floor climbs at the creek's
                // bank slope (the same slope limit the longitudinal ramp uses) instead of the
                // steep lateral rise, which used to leave the graded mouth's rim standing as a
                // wall beside the water. The mouth is therefore the bank profile turned across
                // the flow, and it blends outward into a basin.
                + lateralMouthRise(normalizedDistanceSq, channelRadius)
                + floorNoise;
            return weight * Math.max(0d, terrainY - Math.min(funnelFloor, terrainY));
        }
        return 0d;
    }

    private record SearchNode(int index, double score) {}

    private record DrainageField(
        int[] downstream,
        double[] spill,
        double[] accumulation
    ) {}

    private record SourceCandidate(int index, double score) {}

    /** Primitive min-heap matching the former score/index PriorityQueue order. */
    private static final class SearchHeap
    {
        private int[] indices;
        private double[] scores;
        private int size;

        private SearchHeap(int expectedSize)
        {
            final int capacity = Math.max(16, Math.min(expectedSize, 1024));
            indices = new int[capacity];
            scores = new double[capacity];
        }

        private boolean isEmpty()
        {
            return size == 0;
        }

        private void clear()
        {
            size = 0;
        }

        private void add(int index, double score)
        {
            if (size >= indices.length)
            {
                grow();
            }
            int child = size++;
            while (child > 0)
            {
                final int parent = (child - 1) >>> 1;
                if (compare(score, index, scores[parent], indices[parent]) >= 0)
                {
                    break;
                }
                indices[child] = indices[parent];
                scores[child] = scores[parent];
                child = parent;
            }
            indices[child] = index;
            scores[child] = score;
        }

        private int removeFirst()
        {
            final int first = indices[0];
            final int lastSlot = --size;
            if (lastSlot > 0)
            {
                final int lastIndex = indices[lastSlot];
                final double lastScore = scores[lastSlot];
                int parent = 0;
                final int half = lastSlot >>> 1;
                while (parent < half)
                {
                    int child = (parent << 1) + 1;
                    int childIndex = indices[child];
                    double childScore = scores[child];
                    final int right = child + 1;
                    if (right < lastSlot
                        && compare(childScore, childIndex, scores[right], indices[right]) > 0)
                    {
                        child = right;
                        childIndex = indices[right];
                        childScore = scores[right];
                    }
                    if (compare(lastScore, lastIndex, childScore, childIndex) <= 0)
                    {
                        break;
                    }
                    indices[parent] = childIndex;
                    scores[parent] = childScore;
                    parent = child;
                }
                indices[parent] = lastIndex;
                scores[parent] = lastScore;
            }
            return first;
        }

        private void grow()
        {
            final int oldCapacity = indices.length;
            final int newCapacity = oldCapacity < 64
                ? oldCapacity + oldCapacity + 2
                : oldCapacity + (oldCapacity >>> 1);
            indices = Arrays.copyOf(indices, newCapacity);
            scores = Arrays.copyOf(scores, newCapacity);
        }

        private static int compare(double leftScore, int leftIndex, double rightScore, int rightIndex)
        {
            final int scoreOrder = Double.compare(leftScore, rightScore);
            return scoreOrder != 0 ? scoreOrder : Integer.compare(leftIndex, rightIndex);
        }
    }

    /** Primitive min-heap matching the former spill/travel/index PriorityQueue order. */
    private static final class DrainageHeap
    {
        private int[] indices;
        private double[] spills;
        private double[] travels;
        private int size;
        private int removedIndex;
        private double removedSpill;
        private double removedTravel;

        private DrainageHeap(int expectedSize)
        {
            final int capacity = Math.max(16, Math.min(expectedSize, 1024));
            indices = new int[capacity];
            spills = new double[capacity];
            travels = new double[capacity];
        }

        private boolean isEmpty()
        {
            return size == 0;
        }

        private void add(int index, double spill, double travel)
        {
            if (size >= indices.length)
            {
                grow();
            }
            int child = size++;
            while (child > 0)
            {
                final int parent = (child - 1) >>> 1;
                if (compare(spill, travel, index, spills[parent], travels[parent], indices[parent]) >= 0)
                {
                    break;
                }
                indices[child] = indices[parent];
                spills[child] = spills[parent];
                travels[child] = travels[parent];
                child = parent;
            }
            indices[child] = index;
            spills[child] = spill;
            travels[child] = travel;
        }

        private void removeFirst()
        {
            removedIndex = indices[0];
            removedSpill = spills[0];
            removedTravel = travels[0];
            final int lastSlot = --size;
            if (lastSlot > 0)
            {
                final int lastIndex = indices[lastSlot];
                final double lastSpill = spills[lastSlot];
                final double lastTravel = travels[lastSlot];
                int parent = 0;
                final int half = lastSlot >>> 1;
                while (parent < half)
                {
                    int child = (parent << 1) + 1;
                    int childIndex = indices[child];
                    double childSpill = spills[child];
                    double childTravel = travels[child];
                    final int right = child + 1;
                    if (right < lastSlot && compare(
                        childSpill,
                        childTravel,
                        childIndex,
                        spills[right],
                        travels[right],
                        indices[right]
                    ) > 0)
                    {
                        child = right;
                        childIndex = indices[right];
                        childSpill = spills[right];
                        childTravel = travels[right];
                    }
                    if (compare(
                        lastSpill,
                        lastTravel,
                        lastIndex,
                        childSpill,
                        childTravel,
                        childIndex
                    ) <= 0)
                    {
                        break;
                    }
                    indices[parent] = childIndex;
                    spills[parent] = childSpill;
                    travels[parent] = childTravel;
                    parent = child;
                }
                indices[parent] = lastIndex;
                spills[parent] = lastSpill;
                travels[parent] = lastTravel;
            }
        }

        private void grow()
        {
            final int oldCapacity = indices.length;
            final int newCapacity = oldCapacity < 64
                ? oldCapacity + oldCapacity + 2
                : oldCapacity + (oldCapacity >>> 1);
            indices = Arrays.copyOf(indices, newCapacity);
            spills = Arrays.copyOf(spills, newCapacity);
            travels = Arrays.copyOf(travels, newCapacity);
        }

        private static int compare(
            double leftSpill,
            double leftTravel,
            int leftIndex,
            double rightSpill,
            double rightTravel,
            int rightIndex
        )
        {
            final int spillOrder = Double.compare(leftSpill, rightSpill);
            if (spillOrder != 0)
            {
                return spillOrder;
            }
            final int travelOrder = Double.compare(leftTravel, rightTravel);
            return travelOrder != 0 ? travelOrder : Integer.compare(leftIndex, rightIndex);
        }
    }

    /** Reused fixed and mutable state for all source candidates of one route plan. */
    private static final class RouteSearchWorkspace
    {
        private final double[] costs;
        private final int[] parents;
        private final int[] reachedGenerations;
        private final int[] closedGenerations;
        private final double[] heuristics;
        private final double[] traversalBaseCosts;
        private final int[] neighborOffsets;
        private final double[] steps;
        private final SearchHeap open;
        private final boolean allowSeaMouthTransition;
        private int generation;

        private RouteSearchWorkspace(
            int goal,
            int width,
            int depth,
            int seaLevel,
            double[] terrain,
            DrainageField drainage,
            boolean[] blocked,
            boolean allowSeaMouthTransition
        )
        {
            final int size = width * depth;
            final int directionCount = DRAINAGE_DIRECTIONS.length / 2;
            costs = new double[size];
            parents = new int[size];
            reachedGenerations = new int[size];
            closedGenerations = new int[size];
            heuristics = new double[size];
            traversalBaseCosts = new double[size * directionCount];
            Arrays.fill(traversalBaseCosts, Double.NaN);
            neighborOffsets = new int[directionCount];
            steps = new double[directionCount];
            open = new SearchHeap(size);
            this.allowSeaMouthTransition = allowSeaMouthTransition;

            final int goalX = goal % width;
            final int goalZ = goal / width;
            for (int direction = 0; direction < directionCount; direction++)
            {
                final int directionOffset = direction * 2;
                final int dx = DRAINAGE_DIRECTIONS[directionOffset];
                final int dz = DRAINAGE_DIRECTIONS[directionOffset + 1];
                neighborOffsets[direction] = dx + dz * width;
                steps[direction] = dx == 0 || dz == 0 ? 1d : Math.sqrt(2d);
            }

            for (int z = 0; z < depth; z++)
            {
                for (int x = 0; x < width; x++)
                {
                    final int current = index(x, z, width);
                    heuristics[current] = heuristic(x, z, goalX, goalZ);
                    final int traversalOffset = current * directionCount;
                    for (int direction = 0; direction < directionCount; direction++)
                    {
                        final int directionOffset = direction * 2;
                        final int nextX = x + DRAINAGE_DIRECTIONS[directionOffset];
                        final int nextZ = z + DRAINAGE_DIRECTIONS[directionOffset + 1];
                        if (nextX < 0 || nextZ < 0 || nextX >= width || nextZ >= depth)
                        {
                            continue;
                        }
                        final int next = index(nextX, nextZ, width);
                        if (blocked[next] && next != goal
                            || drainage.downstream()[next] < 0 && next != goal)
                        {
                            continue;
                        }
                        if (next != goal && terrain[next] < seaLevel - 1d + CENTER_SURFACE_INSET
                            && !(allowSeaMouthTransition && nearGoal(nextX, nextZ, goalX, goalZ)))
                        {
                            continue;
                        }

                        final double uphill = Math.max(0d, terrain[next] - terrain[current]);
                        final double roughness = Math.abs(terrain[next] - terrain[current]);
                        final double saddle = Math.max(0d, drainage.spill()[next] - terrain[next]);
                        final double treeBias = drainage.downstream()[current] == next ? 0.72d : 1d;
                        traversalBaseCosts[traversalOffset + direction] = treeBias
                            + uphill * 14d
                            + roughness * 0.35d
                            + saddle * 4d;
                    }
                }
            }
        }

        private void begin(int start)
        {
            if (generation == Integer.MAX_VALUE)
            {
                Arrays.fill(reachedGenerations, 0);
                Arrays.fill(closedGenerations, 0);
                generation = 1;
            }
            else
            {
                generation++;
            }
            open.clear();
            reachedGenerations[start] = generation;
            costs[start] = 0d;
            parents[start] = -1;
        }

        private boolean isClosed(int cell)
        {
            return closedGenerations[cell] == generation;
        }

        private void close(int cell)
        {
            closedGenerations[cell] = generation;
        }

        private double cost(int cell)
        {
            return reachedGenerations[cell] == generation ? costs[cell] : Double.POSITIVE_INFINITY;
        }

        private boolean relax(int cell, double cost, int parent)
        {
            final double previous = reachedGenerations[cell] == generation
                ? costs[cell]
                : Double.POSITIVE_INFINITY;
            if (!(cost < previous))
            {
                return false;
            }
            reachedGenerations[cell] = generation;
            costs[cell] = cost;
            parents[cell] = parent;
            return true;
        }

        private int parent(int cell)
        {
            return reachedGenerations[cell] == generation ? parents[cell] : -1;
        }

        private static boolean nearGoal(int x, int z, int goalX, int goalZ)
        {
            return Math.hypot(x - goalX, z - goalZ) * SAMPLE_STEP <= SEA_MOUTH_WATER_TRANSITION_LENGTH;
        }
    }

    private record SegmentApproach(
        double leftDelta,
        double rightDelta,
        double distanceSq,
        Vec leftPoint,
        Vec rightPoint
    ) {}

    private record RouteIntersection(
        int leftSegment,
        double leftDelta,
        double leftAlong,
        int rightSegment,
        double rightDelta,
        double rightAlong,
        Vec point
    ) {}

    private record RouteProjection(int segment, double delta, double along, double distanceSq) {}

    private record RouteOverlap(
        double leftStartAlong,
        double leftEndAlong,
        double rightStartAlong,
        double rightEndAlong,
        double length
    ) {}

    private record JunctionFlowAnchor(Vec point, Flow flow) {}

    private record JunctionFlowTransition(double along, Flow flow) {}

    private record CorridorPoint(Vec point, double waterY, double radius) {}

    private record IncisionRisk(double score, int maximumExcess, int exceedingPoints, @Nullable Vec worstPoint)
    {
        private boolean preferredTo(@Nullable IncisionRisk other)
        {
            return other == null
                || score < other.score - 1.0e-9d
                || Math.abs(score - other.score) <= 1.0e-9d && maximumExcess < other.maximumExcess
                || Math.abs(score - other.score) <= 1.0e-9d && maximumExcess == other.maximumExcess
                    && exceedingPoints < other.exceedingPoints;
        }

        private boolean withinPreferredRange()
        {
            return maximumExcess == 0;
        }
    }

    private record Projection(double distanceSq, double delta) {}

    private record OutletRoute(
        List<Vec> points,
        boolean receiverMouth,
        double receiverWidth,
        @Nullable ReceiverAlignment alignment
    ) {}

    private record ReceiverPath(List<Vec> points, double width) {}

    private record AlignmentCandidate(
        List<Vec> points,
        double contactDot
    ) {}

    private record GridCell(int x, int z) {}

    private final long seed;
    private final int seaLevel;
    private final HeightSampler heights;
    private final RouteObstacleSampler routeObstacles;
    private final Map<RiverEdge, Headwater> headwaters = new IdentityHashMap<>();
    /** Immutable list snapshots allow the per-column read path to stay lock-free. */
    private volatile Map<Long, List<Headwater>> plannedByChunk = new ConcurrentHashMap<>();
    /** Even when stable, odd while one atomic multi-key index publication is in progress. */
    private volatile long plannedIndexVersion;
    private int plannedIndexWriteDepth;

    NTEHeadwaterNetwork(long seed, int seaLevel, HeightSampler heights)
    {
        this(seed, seaLevel, heights, NO_ROUTE_OBSTACLES);
    }

    NTEHeadwaterNetwork(
        long seed,
        int seaLevel,
        HeightSampler heights,
        RouteObstacleSampler routeObstacles
    )
    {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.heights = heights;
        this.routeObstacles = routeObstacles;
    }

    boolean replaces(RiverEdge edge)
    {
        if (edge.sourceEdge())
        {
            return false;
        }
        final Headwater headwater = headwater(edge);
        final boolean replacement = headwater.replacesTfc();
        coordinatePlannedIntersections(headwater);
        indexPlanned(headwater);
        return replacement;
    }

    boolean hasFeeder(RiverEdge edge)
    {
        if (edge.sourceEdge())
        {
            return false;
        }
        final Headwater headwater = headwater(edge);
        final boolean feeder = headwater.hasFeeder();
        coordinatePlannedIntersections(headwater);
        indexPlanned(headwater);
        return feeder;
    }

    /**
     * Returns the replacement decision only after normal chunk generation has
     * planned this leaf. Biome and structure lookups must never turn a cheap
     * visibility query into a complete terrain search.
     */
    @Nullable
    Boolean replacementIfPlanned(RiverEdge edge)
    {
        if (edge.sourceEdge())
        {
            return Boolean.FALSE;
        }
        final Headwater headwater = existingHeadwater(edge);
        return headwater == null ? null : headwater.replacementIfPlanned();
    }

    double retainedLeafWidthScale(RiverEdge edge, int blockX, int blockZ)
    {
        final Headwater headwater = headwater(edge);
        if (headwater.replacesTfc())
        {
            coordinatePlannedIntersections(headwater);
            indexPlanned(headwater);
            return 1d;
        }
        coordinatePlannedIntersections(headwater);
        indexPlanned(headwater);
        return retainedLeafWidthScaleForFeeder(edge, blockX, blockZ);
    }

    double retainedLeafWidthScaleIfPlanned(RiverEdge edge, int blockX, int blockZ)
    {
        final Headwater headwater = existingHeadwater(edge);
        if (headwater == null || !headwater.retainsTfcIfPlanned())
        {
            return 1d;
        }

        return retainedLeafWidthScaleForFeeder(edge, blockX, blockZ);
    }

    private static double retainedLeafWidthScaleForFeeder(RiverEdge edge, int blockX, int blockZ)
    {

        final double sourceX = edge.source().x() * Units.GRID_WIDTH_IN_BLOCK;
        final double sourceZ = edge.source().y() * Units.GRID_WIDTH_IN_BLOCK;
        final double drainX = edge.drain().x() * Units.GRID_WIDTH_IN_BLOCK;
        final double drainZ = edge.drain().y() * Units.GRID_WIDTH_IN_BLOCK;
        final double dx = drainX - sourceX;
        final double dz = drainZ - sourceZ;
        final double length = Math.hypot(dx, dz);
        if (length < 1.0e-6d)
        {
            return 1d;
        }
        final double along = ((blockX - sourceX) * dx + (blockZ - sourceZ) * dz) / length;
        return sourceWidthScale(along);
    }

    static double sourceWidthScale(double distanceDownstream)
    {
        final double progress = Mth.clamp(distanceDownstream / TFC_LEAF_TAPER_LENGTH, 0d, 1d);
        final double smooth = progress * progress * (3d - 2d * progress);
        return Mth.lerp(smooth, FEEDER_RECEIVER_WIDTH_SCALE, 1d);
    }

    static double matchedReceiverRadius(double receiverWidth)
    {
        return Math.max(
            SOURCE_CHANNEL_RADIUS,
            receiverWidth * Math.sqrt(
                NTERiverHydrology.TFC_WATER_CORE_RADIUS_SQ
                    / NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ
            )
        );
    }

    /**
     * Deterministic per-creek transition tier: 0.5 / 1.0 / 1.5 by default. The
     * tier comes from the creek's own planned seed, so the same world seed always
     * yields the same tier for the same creek without any sequential randomness.
     */
    static double transitionTierMultiplier(long creekSeed)
    {
        final int tier = (int) Math.floorMod(mix64(creekSeed ^ TRANSITION_TIER_SALT), 3L);
        return NTECommonConfig.getHeadwaterTransitionTier(tier);
    }

    /**
     * A fixed transition window compressed every mouth drop into the same 24
     * blocks. From the configured baseline on, each extra block of drop adds one
     * block of window, scaled by the creek's own tier and capped. The window may
     * never exceed half of the route, so a short route cannot become one long
     * mouth ramp.
     */
    static double mouthTransitionLength(double envelopeDrop, double tierMultiplier, double totalLength)
    {
        final double base = NTECommonConfig.getHeadwaterTransitionBase();
        final double limit = Math.max(base, NTECommonConfig.getHeadwaterTransitionMax());
        final double extended = base
            + Math.max(0d, envelopeDrop - base)
                * NTECommonConfig.getHeadwaterTransitionSlope()
                * Math.max(0d, tierMultiplier);
        final double bounded = Mth.clamp(extended, base, limit);
        return Math.max(
            Math.min(bounded, Math.max(base, totalLength * 0.5d)),
            MOUTH_FAN_LENGTH + 1d
        );
    }

    /** Interpolates a per-node profile value at an along-route distance. */
    static double sampleProfileAtAlong(double[] values, double[] distance, double targetAlong)
    {
        final int last = values.length - 1;
        if (targetAlong <= 0d)
        {
            return values[0];
        }
        if (targetAlong >= distance[last])
        {
            return values[last];
        }
        int lowerBound = 1;
        int upperBound = last;
        while (lowerBound < upperBound)
        {
            final int middle = (lowerBound + upperBound) >>> 1;
            if (distance[middle] < targetAlong)
            {
                lowerBound = middle + 1;
            }
            else
            {
                upperBound = middle;
            }
        }
        final int upper = lowerBound;
        final int lower = upper - 1;
        final double segmentLength = distance[upper] - distance[lower];
        final double delta = segmentLength <= 1.0e-9d
            ? 0d
            : (targetAlong - distance[lower]) / segmentLength;
        return Mth.lerp(delta, values[lower], values[upper]);
    }

    static double mouthBankFillWeight(double distanceToOutlet, double transitionLength)
    {
        final double transition = Math.max(MOUTH_FAN_LENGTH + 1d, transitionLength);
        if (distanceToOutlet >= transition)
        {
            return 1d;
        }
        if (distanceToOutlet <= MOUTH_FAN_LENGTH)
        {
            return 0d;
        }
        return smootherStep(Mth.clamp(
            (distanceToOutlet - MOUTH_FAN_LENGTH)
                / (transition - MOUTH_FAN_LENGTH),
            0d,
            1d
        ));
    }

    static double mouthWaterDrop(double distanceToOutlet, double localWaterY, double receiverWaterY, double transitionLength)
    {
        // Water ownership starts transferring with the dry banks and is
        // complete before the final cut-only fan. Capping this at the fan's
        // two-block erosion allowance left a higher replacement-water shelf
        // after the bed had already yielded to a lower retained receiver.
        return Math.max(0d, localWaterY - receiverWaterY)
            * (1d - mouthBankFillWeight(distanceToOutlet, transitionLength));
    }

    static double mouthBankIncisionLateralWeight(double normalizedDistanceSq)
    {
        // The mouth water surface may descend several blocks to meet a deep
        // receiver. Its matching incision must cross the complete visible
        // bank, not stop at normalized radius 1. Stopping at the channel edge
        // gives the wet core the low receiver bed while the very next dry
        // column keeps the high upstream bed, which is the thin rock pinnacle
        // seen at sharp/deep joins. Feather the descent all the way through
        // the same 1.0 -> 1.5-radius shoulder used by the river shape.
        return smootherStep(Mth.clamp(
            (MAX_INFLUENCE_SQ - normalizedDistanceSq)
                / (MAX_INFLUENCE_SQ - NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ),
            0d,
            1d
        ));
    }

    static double mouthGeometryNormalizedDistanceSq(
        double streamNormalizedDistanceSq,
        double receiverNormalizedDistanceSq,
        double distanceToOutlet,
        double transitionLength
    )
    {
        final double receiverInfluence = mouthOuterBankReceiverBlendWeight(distanceToOutlet, transitionLength);
        if (receiverInfluence <= 0d)
        {
            return streamNormalizedDistanceSq;
        }

        // The receiver is part of the mouth only inside the longitudinal bank
        // transition. Applying its SDF along the route's full length can make
        // an upstream high-water profile reappear as a wall in a distant part
        // of the receiver river. Within the actual mouth, blend toward a
        // rounded union which can only carve farther than the stream itself.
        final double roundedUnion = smoothConfluenceNormalizedDistanceSq(
            streamNormalizedDistanceSq,
            receiverNormalizedDistanceSq
        );
        return Mth.lerp(receiverInfluence, streamNormalizedDistanceSq, roundedUnion);
    }

    static double smoothConfluenceNormalizedDistanceSq(double leftDistanceSq, double rightDistanceSq)
    {
        final double minimumDistanceSq = Math.min(leftDistanceSq, rightDistanceSq);
        if (!Double.isFinite(minimumDistanceSq)
            || minimumDistanceSq <= NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ)
        {
            return minimumDistanceSq;
        }

        final double dryBankWeight = smootherStep(Mth.clamp(
            (minimumDistanceSq - NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ)
                / (1d - NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ),
            0d,
            1d
        ));
        final double filletRadius = CONFLUENCE_BANK_FILLET_RADIUS * dryBankWeight;
        if (filletRadius <= 1.0e-9d)
        {
            return minimumDistanceSq;
        }

        // Smooth-min actual normalized radii, not squared radii. This makes the
        // rounding width proportional to the local channel radius and avoids
        // over-carving narrow creeks or under-carving a deep/wide confluence.
        final double leftRadius = Math.sqrt(Math.max(0d, leftDistanceSq));
        final double rightRadius = Math.sqrt(Math.max(0d, rightDistanceSq));
        final double difference = Math.abs(leftRadius - rightRadius);
        if (difference >= filletRadius)
        {
            return minimumDistanceSq;
        }
        final double h = (filletRadius - difference) / filletRadius;
        final double smoothRadius = Math.max(
            0d,
            Math.min(leftRadius, rightRadius) - filletRadius * h * h * 0.25d
        );
        return Math.max(
            NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ + 1.0e-6d,
            smoothRadius * smoothRadius
        );
    }

    /**
     * Wet-corridor ownership inside the final fan. Deliberately independent of the bank
     * transition length: the creek core keeps its own distance field until the fan
     * actually reaches the receiver, so this is not a function of the mouth window.
     */
    static double mouthReceiverBlendWeight(double distanceToOutlet)
    {
        return smootherStep(Mth.clamp(
            (MOUTH_FAN_LENGTH - distanceToOutlet) / MOUTH_FAN_LENGTH,
            0d,
            1d
        ));
    }

    static double mouthOuterBankReceiverBlendWeight(double distanceToOutlet, double transitionLength)
    {
        return 1d - mouthBankFillWeight(distanceToOutlet, transitionLength);
    }

    static double mouthReceiverBlendWeight(
        double streamNormalizedDistanceSq,
        double distanceToOutlet,
        double transitionLength
    )
    {
        // Bank ownership and wet-corridor ownership cannot use one scalar.
        // The creek core must keep its own distance field until the final fan
        // actually reaches the receiver, otherwise a center column can be
        // reclassified outside both channels and the replacement stream has
        // no retained TFC leaf to fall back to. Only the dry outer cross-
        // section transfers early; the transition is smooth between the
        // flowing-water edge and the physical bank edge.
        final double outerBankWeight = mouthOuterBankLateralWeight(streamNormalizedDistanceSq);
        return Mth.lerp(
            outerBankWeight,
            mouthReceiverBlendWeight(distanceToOutlet),
            mouthOuterBankReceiverBlendWeight(distanceToOutlet, transitionLength)
        );
    }

    private static double mouthOuterBankLateralWeight(double streamNormalizedDistanceSq)
    {
        return smootherStep(Mth.clamp(
            (streamNormalizedDistanceSq - NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ)
                / (1d - NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ),
            0d,
            1d
        ));
    }

    /**
     * The wet supplemental corridor closes before the complete mouth fan ends.
     * Its bed therefore needs a dedicated handoff which reaches the receiver
     * at that exact closure point; reusing the slower geometry blend leaves a
     * short level shelf followed by a sudden drop when waterAllowed flips.
     */
    static double mouthReceiverBedBlendWeight(double distanceToOutlet, double waterCutLength)
    {
        final double transitionLength = Math.max(1.0e-6d, MOUTH_FAN_LENGTH - waterCutLength);
        return smootherStep(Mth.clamp(
            (MOUTH_FAN_LENGTH - distanceToOutlet) / transitionLength,
            0d,
            1d
        ));
    }

    static double mouthBankIncision(double mouthWaterDrop, double normalizedDistanceSq)
    {
        return mouthWaterDrop * mouthBankIncisionLateralWeight(normalizedDistanceSq);
    }

    /**
     * Lateral rise of a graded cave mouth's floor. The channel's own core keeps the creek bed
     * exactly where it was, so no channel or water geometry changes; outside the core the floor
     * climbs at the creek's bank slope instead of the steep configured rise, which is what used
     * to leave the mouth's rim standing as a wall next to the water.
     */
    static double lateralMouthRise(double normalizedDistanceSq, double channelRadius)
    {
        final double distanceSq = Math.max(0d, normalizedDistanceSq);
        final double coreRadiusSq = NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ;
        final double configuredRise = NTECommonConfig.getHeadwaterMouthLateralRise();
        if (distanceSq <= coreRadiusSq)
        {
            return distanceSq * configuredRise;
        }
        final double bankRise = channelRadius * NTECommonConfig.getHeadwaterMouthMaxSlope();
        return coreRadiusSq * configuredRise + (distanceSq - coreRadiusSq) * bankRise;
    }

    /**
     * Reserve one ordinary flow cell, or two cells for a descending mouth, in
     * front of the static source-water core. Those cells are still generated
     * as flowing water and participate in the full generation-time waterfall
     * bake; this is source-shape compensation, not a limit on fall length.
     */
    static double mouthSourceWaterInset(double mouthWaterDrop)
    {
        return mouthWaterDrop >= 0.75d ? 2d : 1d;
    }

    static boolean mouthSourceWaterAllowed(
        double distanceToOutlet,
        double waterCutLength,
        double mouthWaterDrop
    )
    {
        return distanceToOutlet > waterCutLength + mouthSourceWaterInset(mouthWaterDrop);
    }

    static boolean plannedSourceWaterAllowed(double downstreamWaterDrop)
    {
        return downstreamWaterDrop < 0.5d;
    }

    /**
     * Retract the static source core before a quantized water-surface step.
     * Looking two blocks ahead models the short fringe which an ordinary
     * source stair creates after fluid settling, without simulating an
     * unbounded water update during route planning.
     */
    static boolean plannedSourceWaterAllowed(
        double currentPlannedWaterY,
        double downstreamPlannedWaterY,
        double secondDownstreamPlannedWaterY
    )
    {
        final int currentBlockY = Mth.floor(currentPlannedWaterY);
        return currentBlockY <= Mth.floor(downstreamPlannedWaterY)
            && currentBlockY <= Mth.floor(secondDownstreamPlannedWaterY);
    }

    static boolean naturalWaterfallLanding(double upstreamRouteWaterY, double localRouteWaterY)
    {
        return Mth.floor(upstreamRouteWaterY) > Mth.floor(localRouteWaterY);
    }

    static double alignedMouthCutLength(double localRadius)
    {
        return Math.max(MOUTH_FAN_LENGTH, Math.max(1.25d, Math.min(3d, localRadius * 0.6d)));
    }

    static double alignedMouthWaterCutLength(double localRadius)
    {
        return Math.max(1.25d, Math.min(3d, localRadius * 0.6d));
    }

    static int visibleIncisionDepth(double terrainY, double waterY)
    {
        return Math.max(
            0,
            Mth.floor(terrainY - CENTER_SURFACE_INSET) - Mth.floor(waterY)
        );
    }

    private static double smootherStep(double value)
    {
        final double t = Mth.clamp(value, 0d, 1d);
        return t * t * t * (t * (t * 6d - 15d) + 10d);
    }

    void ensurePlanned(RiverEdge edge)
    {
        if (!edge.sourceEdge())
        {
            final Headwater headwater = headwater(edge);
            headwater.route();
            coordinatePlannedIntersections(headwater);
            indexPlanned(headwater);
        }
    }

    boolean suppressesRetainedAt(RiverEdge edge, int blockX, int blockZ)
    {
        final long targetChunkKey = chunkKey(blockX >> 4, blockZ >> 4);
        final List<Headwater> candidates = plannedCandidates(targetChunkKey);
        for (Headwater headwater : candidates)
        {
            final Route route = headwater.plannedRoute();
            if (route != null && route.suppresses(edge, blockX, blockZ))
            {
                return true;
            }
        }
        return false;
    }

    @Nullable
    Sample sample(RiverEdge edge, int blockX, int blockZ, double ambientHeight)
    {
        if (edge.sourceEdge())
        {
            return null;
        }
        final Headwater headwater = headwater(edge);
        headwater.route();
        coordinatePlannedIntersections(headwater);
        final Sample sample = headwater.sample(blockX, blockZ, ambientHeight);
        indexPlanned(headwater);
        return sample;
    }

    @Nullable
    Sample sampleUnindexedIfPlanned(RiverEdge edge, int blockX, int blockZ, double ambientHeight)
    {
        if (edge.sourceEdge())
        {
            return null;
        }
        final Route planned;
        synchronized (headwaters)
        {
            final Headwater headwater = headwaters.get(edge);
            if (headwater == null || !headwater.attempted)
            {
                return null;
            }
            planned = headwater.route;
            if (planned == null || headwater.indexedRoute == planned)
            {
                return null;
            }
        }
        // Initial route planning publishes Headwater.route before it can take
        // the network lock and publish plannedByChunk. Keep this bounded
        // direct fallback only for that short window. Coordinated route
        // replacement and index rebuilding both hold the same network lock.
        return planned.sample(blockX, blockZ, ambientHeight);
    }

    @Nullable
    Sample samplePlannedAt(int blockX, int blockZ, double ambientHeight)
    {
        final long targetChunkKey = chunkKey(blockX >> 4, blockZ >> 4);
        final List<Headwater> candidates = plannedCandidates(targetChunkKey);

        Sample nearest = null;
        Headwater nearestOwner = null;
        Sample roundedNearest = null;
        for (Headwater headwater : candidates)
        {
            final Sample sample = headwater.sampleIfPlanned(blockX, blockZ, ambientHeight);
            if (sample == null)
            {
                continue;
            }
            // Routes registered as parts of the same junction must share one
            // rounded dry-bank field. Keep the wet core on the ordinary nearest
            // sample so channel width and source/flow ownership stay unchanged.
            if (nearest != null
                && nearestOwner != null
                && Math.min(sample.normalizedDistanceSq(), nearest.normalizedDistanceSq())
                    > NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ
                && sample.receiverBlendWeight() > 0d
                && nearest.receiverBlendWeight() > 0d
                && areJunctionPeers(headwater, nearestOwner))
            {
                final Sample preferred = samplePreferred(sample, nearest) ? sample : nearest;
                final double roundedDistanceSq = smoothConfluenceNormalizedDistanceSq(
                    sample.normalizedDistanceSq(),
                    nearest.normalizedDistanceSq()
                );
                if (roundedDistanceSq + 1.0e-9d < preferred.normalizedDistanceSq())
                {
                    final Sample rounded = withNormalizedDistanceSq(preferred, roundedDistanceSq);
                    if (samplePreferred(rounded, roundedNearest))
                    {
                        roundedNearest = rounded;
                    }
                }
            }
            if (samplePreferred(sample, nearest))
            {
                nearest = sample;
                nearestOwner = headwater;
            }
        }
        return samplePreferred(roundedNearest, nearest) ? roundedNearest : nearest;
    }

    private boolean areJunctionPeers(Headwater left, Headwater right)
    {
        synchronized (headwaters)
        {
            return left.junctionPeers.contains(right) || right.junctionPeers.contains(left);
        }
    }

    private List<Headwater> plannedCandidates(long chunkKey)
    {
        final long observedVersion = plannedIndexVersion;
        if ((observedVersion & 1L) == 0L)
        {
            final List<Headwater> observed = plannedByChunk.get(chunkKey);
            if (observedVersion == plannedIndexVersion)
            {
                return observed == null ? List.of() : observed;
            }
        }
        // Index rebuilds are cold and bounded. A reader which overlaps one
        // waits for the same publication lock rather than observing some old
        // chunk keys and some new keys from one coordinated route update.
        synchronized (headwaters)
        {
            return plannedByChunk.getOrDefault(chunkKey, List.of());
        }
    }

    /** Must be called while holding the headwater publication lock. */
    private void beginPlannedIndexWrite()
    {
        if (plannedIndexWriteDepth++ == 0)
        {
            plannedIndexVersion++;
        }
    }

    /** Must be called while holding the headwater publication lock. */
    private void endPlannedIndexWrite()
    {
        if (--plannedIndexWriteDepth == 0)
        {
            plannedIndexVersion++;
        }
    }

    private static Sample withNormalizedDistanceSq(Sample sample, double normalizedDistanceSq)
    {
        // Junction filleting can move a dry-bank sample inward after the
        // route computed its original mouth incision. Re-evaluate the descent
        // at the rounded radius; otherwise geometry says "inner slope" while
        // its bed still keeps the shallower outer-slope elevation, leaving a
        // pointed cap exactly on the fillet.
        final double roundedExtraIncision = Math.max(
            sample.extraIncision(),
            mouthBankIncision(sample.mouthWaterDrop(), normalizedDistanceSq)
        );
        return new Sample(
            sample.waterSurfaceY(),
            normalizedDistanceSq,
            sample.channelRadius(),
            roundedExtraIncision,
            sample.mouthWaterDrop(),
            sample.bankFillWeight(),
            sample.waterCoreRadiusSq(),
            sample.fillAllowed(),
            sample.waterAllowed(),
            sample.sourceWaterAllowed(),
            sample.receiverBlendWeight(),
            sample.receiverBedBlendWeight(),
            sample.waterfallLanding(),
            sample.headwater(),
            sample.subterranean(),
            sample.tunnelCeilingY(),
            sample.caveSeed(),
            sample.entranceCut(),
            sample.flow()
        );
    }

    boolean mayInfluence(RiverEdge edge, int blockX, int blockZ)
    {
        return mayInfluence(edge, blockX, blockZ, 0d);
    }

    boolean mayInfluence(RiverEdge edge, int blockX, int blockZ, double extraBlocks)
    {
        final double distanceSqGrid = edge.fractal().intersectDistance(
            Units.blockToGridExact(blockX),
            Units.blockToGridExact(blockZ)
        );
        final double influenceGrid = (ROUTE_PADDING + 12d + Math.max(0d, extraBlocks))
            / Units.GRID_WIDTH_IN_BLOCK;
        return distanceSqGrid <= influenceGrid * influenceGrid;
    }

    private Headwater headwater(RiverEdge edge)
    {
        synchronized (headwaters)
        {
            Headwater headwater = headwaters.get(edge);
            if (headwater == null)
            {
                if (headwaters.size() >= MAX_HEADWATER_CACHE_SIZE)
                {
                    resetHeadwaterCache();
                }
                final int downstreamWidth = edge.drainEdge() == null ? edge.width : edge.drainEdge().width;
                headwater = new Headwater(
                    seedFor(edge),
                    seaLevel,
                    downstreamWidth,
                    heights,
                    routeObstacles,
                    edge
                );
                headwaters.put(edge, headwater);
            }
            return headwater;
        }
    }

    @Nullable
    private Headwater existingHeadwater(RiverEdge edge)
    {
        synchronized (headwaters)
        {
            return headwaters.get(edge);
        }
    }

    /** Must be called while holding the headwater publication lock. */
    private void resetHeadwaterCache()
    {
        beginPlannedIndexWrite();
        try
        {
            // Preserve the original all-at-once cache reset semantics. The
            // large spatial index is retired by reference swap instead of an
            // O(number of covered chunks) clear; readers which already hold
            // an immutable old list can still finish against that old graph.
            headwaters.clear();
            plannedByChunk = new ConcurrentHashMap<>();
        }
        finally
        {
            endPlannedIndexWrite();
        }
    }

    private void indexPlanned(Headwater headwater)
    {
        final Route observedRoute = headwater.plannedRoute();
        if (observedRoute == null || headwater.indexedRoute == observedRoute)
        {
            return;
        }
        synchronized (headwaters)
        {
            if (!isCurrent(headwater))
            {
                return;
            }
            // Re-read after taking the publication lock. Coordination can
            // replace Route between the optimistic check above and this lock;
            // publishing the captured older instance would roll the spatial
            // index back to stale geometry.
            final Route route = headwater.plannedRoute();
            if (route == null)
            {
                return;
            }
            if (headwater.indexedRoute == route)
            {
                return;
            }

            beginPlannedIndexWrite();
            try
            {
                unindexPlanned(headwater);

                for (long key : route.spatialChunks)
                {
                    final List<Headwater> current = plannedByChunk.getOrDefault(key, List.of());
                    final List<Headwater> indexed = new ArrayList<>(current);
                    if (!indexed.contains(headwater))
                    {
                        indexed.add(headwater);
                    }
                    indexed.sort(HEADWATER_ORDER);
                    plannedByChunk.put(key, List.copyOf(indexed));
                    headwater.indexedChunks.add(key);
                }
                headwater.indexedRoute = route;
            }
            finally
            {
                endPlannedIndexWrite();
            }
        }
    }

    /** Must be called while holding the headwater publication lock. */
    private void unindexPlanned(Headwater headwater)
    {
        for (long key : headwater.indexedChunks)
        {
            final List<Headwater> current = plannedByChunk.get(key);
            if (current == null || !current.contains(headwater))
            {
                continue;
            }
            if (current.size() == 1)
            {
                plannedByChunk.remove(key);
                continue;
            }
            final List<Headwater> remaining = new ArrayList<>(current);
            remaining.remove(headwater);
            plannedByChunk.put(key, List.copyOf(remaining));
        }
        headwater.indexedChunks.clear();
        headwater.indexedRoute = null;
    }

    private void coordinatePlannedIntersections(Headwater headwater)
    {
        if (headwater.intersectionsCoordinated || headwater.plannedRoute() == null)
        {
            return;
        }
        synchronized (headwaters)
        {
            if (!isCurrent(headwater)
                || headwater.intersectionsCoordinated
                || headwater.plannedRoute() == null)
            {
                return;
            }
            beginPlannedIndexWrite();
            try
            {
                final List<Headwater> planned = nearbyPlannedHeadwaters(headwater);
                planned.sort(HEADWATER_ORDER);
                coordinateNetwork(planned, heights);
                // Coordination replaces immutable Route instances. Rebuild every
                // affected outer index now; otherwise a peer indexed before this
                // junction can keep publishing coverage for its old geometry.
                for (Headwater plannedHeadwater : planned)
                {
                    indexPlanned(plannedHeadwater);
                }
            }
            finally
            {
                endPlannedIndexWrite();
            }
        }
    }

    private List<Headwater> nearbyPlannedHeadwaters(Headwater target)
    {
        final Route targetRoute = target.plannedRoute();
        if (targetRoute == null)
        {
            return List.of();
        }
        final Set<Headwater> nearby = Collections.newSetFromMap(new IdentityHashMap<>());
        nearby.add(target);
        if (target.indexedRoute == targetRoute)
        {
            for (long key : targetRoute.spatialChunks)
            {
                final List<Headwater> indexed = plannedByChunk.get(key);
                if (indexed != null)
                {
                    nearby.addAll(indexed);
                }
            }
        }
        else
        {
            for (Headwater peer : headwaters.values())
            {
                final Route peerRoute = peer.plannedRoute();
                if (peer.attempted && peerRoute != null
                    && targetRoute.boundsOverlap(peerRoute, JUNCTION_MAX_WET_CONTACT_RADIUS))
                {
                    nearby.add(peer);
                }
            }
        }
        nearby.removeIf(peer -> !peer.attempted || peer.plannedRoute() == null);
        return new ArrayList<>(nearby);
    }

    /** Must be called while holding the headwater publication lock. */
    private boolean isCurrent(Headwater headwater)
    {
        return headwater.edge == null || headwaters.get(headwater.edge) == headwater;
    }

    private static long chunkKey(int chunkX, int chunkZ)
    {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    private long seedFor(RiverEdge edge)
    {
        long value = seed;
        value ^= Double.doubleToLongBits(edge.source().x()) * 0x9E3779B97F4A7C15L;
        value ^= Double.doubleToLongBits(edge.source().y()) * 0xC2B2AE3D27D4EB4FL;
        value ^= Double.doubleToLongBits(edge.drain().x()) * 0x165667B19E3779F9L;
        value ^= Double.doubleToLongBits(edge.drain().y()) * 0x85EBCA77C2B2AE63L;
        return mix64(value);
    }

    static TestStream planTestStream(
        long seed,
        int seaLevel,
        double sourceX,
        double sourceZ,
        double drainX,
        double drainZ,
        int downstreamWidth,
        HeightSampler heights
    )
    {
        return new TestStream(new Headwater(
            seed,
            seaLevel,
            downstreamWidth,
            heights,
            sourceX,
            sourceZ,
            drainX,
            drainZ
        ));
    }

    static TestStream planTestStream(
        long seed,
        int seaLevel,
        double sourceX,
        double sourceZ,
        double drainX,
        double drainZ,
        int downstreamWidth,
        HeightSampler heights,
        RouteObstacleSampler routeObstacles
    )
    {
        return new TestStream(new Headwater(
            seed,
            seaLevel,
            downstreamWidth,
            heights,
            routeObstacles,
            null,
            sourceX,
            sourceZ,
            drainX,
            drainZ
        ));
    }

    static TestStream planTestSourceExtension(
        long seed,
        int seaLevel,
        double sourceX,
        double sourceZ,
        double drainX,
        double drainZ,
        int downstreamWidth,
        HeightSampler heights
    )
    {
        final Headwater headwater = new Headwater(
            seed,
            seaLevel,
            downstreamWidth,
            heights,
            sourceX,
            sourceZ,
            drainX,
            drainZ
        );
        headwater.route = headwater.planSourceExtension();
        headwater.attempted = true;
        return new TestStream(headwater);
    }

    static TestStream planTestSourceExtension(
        long seed,
        int seaLevel,
        double sourceX,
        double sourceZ,
        double drainX,
        double drainZ,
        int downstreamWidth,
        HeightSampler heights,
        RouteObstacleSampler routeObstacles
    )
    {
        final Headwater headwater = new Headwater(
            seed,
            seaLevel,
            downstreamWidth,
            heights,
            routeObstacles,
            null,
            sourceX,
            sourceZ,
            drainX,
            drainZ
        );
        headwater.route = headwater.planSourceExtension();
        headwater.attempted = true;
        return new TestStream(headwater);
    }

    static List<Vec> alignTestRoute(List<Vec> route, List<Vec> receiver, double receiverWidth)
    {
        final ReceiverPath receiverPath = new ReceiverPath(List.copyOf(receiver), receiverWidth);
        return Headwater.alignRoute(route, receiverPath).points();
    }

    static double firstVisibleContactFlowDot(
        List<Vec> route,
        List<Vec> receiver,
        double receiverWidth
    )
    {
        final ReceiverPath receiverPath = new ReceiverPath(List.copyOf(receiver), receiverWidth);
        final double streamRadius = Mth.clamp(receiverWidth * 0.45d, 3.2d, 5.5d);
        final double contactRadius = receiverWidth * Math.sqrt(NTERiverHydrology.TFC_WATER_CORE_RADIUS_SQ)
            + streamRadius * Math.sqrt(NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ);
        return Headwater.firstContactFlowDot(route, 0, receiverPath, contactRadius);
    }

    static boolean coordinateTestStreams(TestStream left, TestStream right)
    {
        final Route leftBefore = left.headwater.route();
        final Route rightBefore = right.headwater.route();
        if (leftBefore == null || rightBefore == null)
        {
            return false;
        }
        final NTEHeadwaterNetwork network = new NTEHeadwaterNetwork(
            0L,
            left.headwater.seaLevel,
            left.headwater.heights
        );
        network.indexPlanned(left.headwater);
        network.indexPlanned(right.headwater);
        network.coordinatePlannedIntersections(left.headwater);
        return (left.headwater.plannedRoute() != leftBefore || right.headwater.plannedRoute() != rightBefore)
            && left.headwater.indexedRoute == left.headwater.plannedRoute()
            && right.headwater.indexedRoute == right.headwater.plannedRoute()
            && (network.plannedIndexVersion & 1L) == 0L;
    }

    @Nullable
    static double[] overlapTestStreams(TestStream left, TestStream right)
    {
        left.headwater.route();
        right.headwater.route();
        final Route leftRoute = left.headwater.plannedRoute();
        final Route rightRoute = right.headwater.plannedRoute();
        if (leftRoute == null || rightRoute == null)
        {
            return null;
        }
        final RouteOverlap overlap = findRouteOverlap(leftRoute, rightRoute, SHARED_CORRIDOR_MIN_LENGTH);
        return overlap == null ? null : new double[] {
            overlap.leftStartAlong(),
            overlap.leftEndAlong(),
            overlap.rightStartAlong(),
            overlap.rightEndAlong()
        };
    }

    @Nullable
    static Sample sampleTestStream(TestStream stream, int blockX, int blockZ)
    {
        final Route route = stream.headwater.route();
        return route == null ? null : route.sample(blockX, blockZ, Double.POSITIVE_INFINITY);
    }

    @Nullable
    static Sample samplePlannedTestStream(TestStream stream, int blockX, int blockZ)
    {
        return stream.headwater.sampleIfPlanned(blockX, blockZ, Double.POSITIVE_INFINITY);
    }

    static TestStream testStreamFromRoute(List<Vec> points, double startWaterY, double endWaterY, HeightSampler heights)
    {
        return testStreamFromRoute(points, startWaterY, endWaterY, heights, true);
    }

    static TestStream testStreamFromRoute(
        List<Vec> points,
        double startWaterY,
        double endWaterY,
        HeightSampler heights,
        boolean replacement
    )
    {
        final double[] radii = new double[points.size()];
        Arrays.fill(radii, 2.5d);
        return testStreamFromRoute(points, startWaterY, endWaterY, radii, heights, replacement);
    }

    static TestStream testStreamFromRoute(
        List<Vec> points,
        double startWaterY,
        double endWaterY,
        double[] radii,
        HeightSampler heights,
        boolean replacement
    )
    {
        if (radii.length != points.size())
        {
            throw new IllegalArgumentException("one radius is required for each route point");
        }
        final Headwater headwater = new Headwater(
            1L,
            (int) Math.floor(endWaterY + 1d),
            16,
            heights,
            points.get(0).x(),
            points.get(0).z(),
            points.get(points.size() - 1).x(),
            points.get(points.size() - 1).z()
        );
        final int size = points.size();
        final double[] x = new double[size];
        final double[] z = new double[size];
        final double[] terrain = new double[size];
        final double[] water = new double[size];
        final double[] radius = new double[size];
        final double[] distance = new double[size];
        for (int i = 0; i < size; i++)
        {
            final Vec point = points.get(i);
            x[i] = point.x();
            z[i] = point.z();
            terrain[i] = heights.sample(Mth.floor(x[i]), Mth.floor(z[i]));
            if (i > 0)
            {
                distance[i] = distance[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            }
        }
        for (int i = 0; i < size; i++)
        {
            water[i] = Mth.lerp(distance[i] / distance[size - 1], startWaterY, endWaterY);
            radius[i] = radii[i];
        }
        headwater.route = new Route(
            x,
            z,
            terrain,
            water,
            radius,
            distance,
            distance[size - 1],
            false,
            null,
            endWaterY,
            mouthTransitionLength(0d, transitionTierMultiplier(1L), distance[size - 1]),
            new boolean[size],
            new double[size],
            List.of(),
            1L
        );
        headwater.attempted = true;
        headwater.replacement = replacement;
        return new TestStream(headwater);
    }

    static boolean routeSampleMatchesLinearReference(TestStream stream, int blockX, int blockZ)
    {
        final Route route = stream.headwater.route();
        if (route == null)
        {
            return false;
        }
        final RouteProjection indexed = route.nearestIndexedProjection(new Vec(blockX, blockZ));
        final RouteProjection linear = route.nearestProjectionLinear(new Vec(blockX, blockZ));
        return indexed.segment() == linear.segment()
            && Math.abs(indexed.delta() - linear.delta()) <= 1.0e-9d
            && Math.abs(indexed.along() - linear.along()) <= 1.0e-9d
            && Math.abs(indexed.distanceSq() - linear.distanceSq()) <= 1.0e-9d;
    }

    static boolean routeDerivedStateMatchesGeometry(TestStream stream)
    {
        final Route route = stream.headwater.route();
        if (route == null)
        {
            return false;
        }
        if (Math.abs(route.minX - minimum(route.x)) > 1.0e-9d
            || Math.abs(route.minZ - minimum(route.z)) > 1.0e-9d
            || Math.abs(route.maxX - maximum(route.x)) > 1.0e-9d
            || Math.abs(route.maxZ - maximum(route.z)) > 1.0e-9d)
        {
            return false;
        }
        for (double blockZ = route.minZ - 4d; blockZ <= route.maxZ + 4d; blockZ += 4d)
        {
            for (double blockX = route.minX - 4d; blockX <= route.maxX + 4d; blockX += 4d)
            {
                final RouteProjection indexed = route.nearestIndexedProjection(new Vec(blockX, blockZ));
                final RouteProjection linear = route.nearestProjectionLinear(new Vec(blockX, blockZ));
                if (indexed.segment() != linear.segment()
                    || Math.abs(indexed.delta() - linear.delta()) > 1.0e-9d
                    || Math.abs(indexed.along() - linear.along()) > 1.0e-9d
                    || Math.abs(indexed.distanceSq() - linear.distanceSq()) > 1.0e-9d)
                {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean routeIsPublishedOnlyInCurrentChunksAfterCoordination(TestStream left, TestStream right)
    {
        final Route leftBefore = left.headwater.route();
        final Route rightBefore = right.headwater.route();
        if (leftBefore == null || rightBefore == null)
        {
            return false;
        }
        final Set<Long> previousChunks = new HashSet<>(leftBefore.spatialChunks);
        previousChunks.addAll(rightBefore.spatialChunks);
        final NTEHeadwaterNetwork network = new NTEHeadwaterNetwork(
            0L,
            left.headwater.seaLevel,
            left.headwater.heights
        );
        network.indexPlanned(left.headwater);
        network.indexPlanned(right.headwater);
        network.coordinatePlannedIntersections(left.headwater);

        final Route leftAfter = left.headwater.plannedRoute();
        final Route rightAfter = right.headwater.plannedRoute();
        if (leftAfter == null || rightAfter == null || leftAfter == leftBefore && rightAfter == rightBefore)
        {
            return false;
        }
        final Set<Long> currentChunks = new HashSet<>(leftAfter.spatialChunks);
        currentChunks.addAll(rightAfter.spatialChunks);
        previousChunks.addAll(currentChunks);
        synchronized (network.headwaters)
        {
            for (long key : previousChunks)
            {
                final List<Headwater> indexed = network.plannedByChunk.get(key);
                final boolean containsLeft = indexed != null && indexed.contains(left.headwater);
                final boolean containsRight = indexed != null && indexed.contains(right.headwater);
                if (containsLeft != leftAfter.spatialChunks.contains(key))
                {
                    return false;
                }
                if (containsRight != rightAfter.spatialChunks.contains(key))
                {
                    return false;
                }
            }
        }
        return left.headwater.indexedRoute == leftAfter && right.headwater.indexedRoute == rightAfter;
    }

    static boolean rejectsPastOutlet(List<Vec> points, int blockX, int blockZ)
    {
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        int bestIndex = -1;
        double bestDelta = 0d;
        for (int i = 0; i < points.size() - 1; i++)
        {
            final Vec from = points.get(i);
            final Vec to = points.get(i + 1);
            final Projection projection = project(from.x(), from.z(), to.x(), to.z(), blockX, blockZ);
            if (projection.distanceSq() < bestDistanceSq)
            {
                bestDistanceSq = projection.distanceSq();
                bestIndex = i;
                bestDelta = projection.delta();
            }
        }
        final int last = points.size() - 1;
        if (bestIndex != last - 1 || bestDelta < 1d - 1.0e-9d)
        {
            return false;
        }
        final Vec previous = points.get(last - 1);
        final Vec outlet = points.get(last);
        return extendsPastOutlet(previous.x(), previous.z(), outlet.x(), outlet.z(), blockX, blockZ);
    }

    static final class TestStream
    {
        private final Headwater headwater;

        private TestStream(Headwater headwater)
        {
            this.headwater = headwater;
        }

        boolean valid()
        {
            return headwater.route() != null;
        }

        double mouthTransitionLength()
        {
            final Route route = headwater.route();
            return route == null ? 0d : route.mouthTransitionLength;
        }

        int attemptedDrainageCandidates()
        {
            headwater.route();
            return headwater.attemptedDrainageCandidates;
        }

        int availableDrainageCandidates()
        {
            headwater.route();
            return headwater.availableDrainageCandidates;
        }

        String failureReason()
        {
            headwater.route();
            return headwater.failureReason == null ? "none" : headwater.failureReason;
        }

        @Nullable
        Sample sample(int blockX, int blockZ, double ambientHeight)
        {
            return headwater.sample(blockX, blockZ, ambientHeight);
        }

        List<DiagnosticPoint> points()
        {
            final Route route = headwater.route();
            return route == null ? List.of() : route.diagnostics();
        }

    }

    private static boolean coordinatePair(Headwater left, Headwater right, HeightSampler heights)
    {
        final Route leftRoute = left.plannedRoute();
        final Route rightRoute = right.plannedRoute();
        if (leftRoute == null || rightRoute == null || left.junctionPeers.contains(right))
        {
            return false;
        }
        final Vec leftOutlet = new Vec(
            leftRoute.x[leftRoute.x.length - 1],
            leftRoute.z[leftRoute.z.length - 1]
        );
        final Vec rightOutlet = new Vec(
            rightRoute.x[rightRoute.x.length - 1],
            rightRoute.z[rightRoute.z.length - 1]
        );
        final boolean sharedOutlet = distance(leftOutlet, rightOutlet) <= JUNCTION_DETECTION_RADIUS;
        final RouteOverlap overlap = findRouteOverlap(
            leftRoute,
            rightRoute,
            sharedOutlet ? 4d : SHARED_CORRIDOR_MIN_LENGTH
        );
        if (overlap != null)
        {
            left.route = leftRoute.withSharedProfile(
                overlap.leftStartAlong(),
                overlap.leftEndAlong(),
                rightRoute,
                overlap.rightStartAlong(),
                overlap.rightEndAlong(),
                heights
            );
            right.route = rightRoute.withSharedProfile(
                overlap.rightStartAlong(),
                overlap.rightEndAlong(),
                leftRoute,
                overlap.leftStartAlong(),
                overlap.leftEndAlong(),
                heights
            );
            left.junctionPeers.add(right);
            right.junctionPeers.add(left);
            return true;
        }
        if (sharedOutlet)
        {
            return false;
        }

        final RouteIntersection intersection = findRouteIntersection(leftRoute, rightRoute);
        if (intersection == null)
        {
            return false;
        }
        final double junctionWater = Math.min(
            leftRoute.sampleWaterYAtAlong(intersection.leftAlong()),
            rightRoute.sampleWaterYAtAlong(intersection.rightAlong())
        );
        final double leftRadius = leftRoute.sampleRadiusAtAlong(intersection.leftAlong());
        final double rightRadius = rightRoute.sampleRadiusAtAlong(intersection.rightAlong());
        final double junctionRadius = Math.min(
            Math.max(leftRadius, rightRadius),
            Math.min(leftRadius, rightRadius) + 0.5d
        );
        final Vec junctionDirection = leftRoute.sampleWaterYAtAlong(intersection.leftAlong())
            <= rightRoute.sampleWaterYAtAlong(intersection.rightAlong())
                ? leftRoute.directionAtAlong(intersection.leftAlong() + 2d)
                : rightRoute.directionAtAlong(intersection.rightAlong() + 2d);
        Flow junctionFlow = leftRoute.junctionAnchorFlowAt(intersection.point());
        if (junctionFlow == null)
        {
            junctionFlow = rightRoute.junctionAnchorFlowAt(intersection.point());
        }
        if (junctionFlow == null)
        {
            junctionFlow = Flow.fromAngle(Mth.atan2(-junctionDirection.z(), junctionDirection.x()));
        }
        left.route = leftRoute.withJunction(
            intersection.leftSegment(),
            intersection.leftDelta(),
            intersection.point(),
            junctionWater,
            junctionRadius,
            junctionDirection,
            junctionFlow,
            heights
        );
        right.route = rightRoute.withJunction(
            intersection.rightSegment(),
            intersection.rightDelta(),
            intersection.point(),
            junctionWater,
            junctionRadius,
            junctionDirection,
            junctionFlow,
            heights
        );
        left.junctionPeers.add(right);
        right.junctionPeers.add(left);
        return true;
    }

    /**
     * Detect a sustained, co-directed overlap rather than treating it as many
     * unrelated point intersections. The shared interval is allowed to begin
     * at a source: a spring entering an established creek should become a
     * tributary there instead of laying a second water surface over the trunk.
     */
    @Nullable
    private static RouteOverlap findRouteOverlap(Route left, Route right, double minimumLength)
    {
        if (!left.boundsOverlap(right, SHARED_CORRIDOR_DETECTION_RADIUS))
        {
            return null;
        }
        final double spacing = 2d;
        double runLeftStart = Double.NaN;
        double runRightStart = Double.NaN;
        double previousRightAlong = Double.NaN;
        RouteOverlap best = null;
        for (double leftAlong = 0d; leftAlong <= left.totalLength + 1.0e-6d; leftAlong += spacing)
        {
            final Vec point = left.pointAtAlong(leftAlong);
            final RouteProjection projection = right.nearestProjection(point);
            final boolean close = projection.distanceSq()
                <= SHARED_CORRIDOR_DETECTION_RADIUS * SHARED_CORRIDOR_DETECTION_RADIUS;
            final boolean coDirected = close && directionDot(
                left.directionAtAlong(leftAlong),
                right.directionAtAlong(projection.along())
            ) >= 0.72d;
            final boolean progressesTogether = Double.isNaN(previousRightAlong)
                || projection.along() + spacing * 0.5d >= previousRightAlong;
            if (coDirected && progressesTogether)
            {
                if (Double.isNaN(runLeftStart))
                {
                    runLeftStart = leftAlong;
                    runRightStart = projection.along();
                }
                previousRightAlong = projection.along();
                final double runLength = leftAlong - runLeftStart;
                if (runLength >= minimumLength
                    && (best == null || runLength > best.length()))
                {
                    best = new RouteOverlap(
                        runLeftStart,
                        leftAlong,
                        runRightStart,
                        projection.along(),
                        runLength
                    );
                }
            }
            else
            {
                runLeftStart = Double.NaN;
                runRightStart = Double.NaN;
                previousRightAlong = Double.NaN;
            }
        }
        return best;
    }

    /**
     * Revisit every uncoordinated pair until inserting junction nodes no
     * longer exposes another route intersection. This is deliberately a
     * network operation rather than a one-off two-stream special case: a
     * later third or fourth route may join an already coordinated trunk
     * without deleting any existing source or outlet branch.
     */
    private static boolean coordinateNetwork(List<Headwater> headwaters, HeightSampler heights)
    {
        headwaters.sort(HEADWATER_ORDER);
        boolean coordinatedAny = false;
        boolean changed;
        do
        {
            changed = false;
            for (int left = 0; left < headwaters.size(); left++)
            {
                for (int right = left + 1; right < headwaters.size(); right++)
                {
                    if (coordinatePair(headwaters.get(left), headwaters.get(right), heights))
                    {
                        coordinatedAny = true;
                        changed = true;
                    }
                }
            }
        }
        while (changed);
        for (Headwater headwater : headwaters)
        {
            headwater.intersectionsCoordinated = true;
        }
        return coordinatedAny;
    }

    @Nullable
    private static RouteIntersection findRouteIntersection(Route left, Route right)
    {
        if (!left.boundsOverlap(right, JUNCTION_MAX_WET_CONTACT_RADIUS))
        {
            return null;
        }
        RouteIntersection best = null;
        double bestProgress = Double.POSITIVE_INFINITY;
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int leftSegment = 0; leftSegment < left.x.length - 1; leftSegment++)
        {
            final Vec leftStart = new Vec(left.x[leftSegment], left.z[leftSegment]);
            final Vec leftEnd = new Vec(left.x[leftSegment + 1], left.z[leftSegment + 1]);
            for (int rightSegment = 0; rightSegment < right.x.length - 1; rightSegment++)
            {
                final Vec rightStart = new Vec(right.x[rightSegment], right.z[rightSegment]);
                final Vec rightEnd = new Vec(right.x[rightSegment + 1], right.z[rightSegment + 1]);
                final SegmentApproach approach = segmentApproach(leftStart, leftEnd, rightStart, rightEnd);
                final double leftAlong = Mth.lerp(
                    approach.leftDelta(),
                    left.distance[leftSegment],
                    left.distance[leftSegment + 1]
                );
                final double rightAlong = Mth.lerp(
                    approach.rightDelta(),
                    right.distance[rightSegment],
                    right.distance[rightSegment + 1]
                );
                if (leftAlong < JUNCTION_MIN_BRANCH_LENGTH
                    || left.totalLength - leftAlong < JUNCTION_MIN_BRANCH_LENGTH
                    || rightAlong < JUNCTION_MIN_BRANCH_LENGTH
                    || right.totalLength - rightAlong < JUNCTION_MIN_BRANCH_LENGTH)
                {
                    continue;
                }

                // Topology must merge when the physical wet cores touch, not
                // only when their mathematical centerlines enter a fixed
                // four-block radius. Otherwise a high and a low stream can
                // occupy the same visible water cells without sharing a node.
                final double wetContactRadius = Math.min(
                    JUNCTION_MAX_WET_CONTACT_RADIUS,
                    Math.max(
                        JUNCTION_DETECTION_RADIUS,
                        left.sampleRadiusAtAlong(leftAlong) * Math.sqrt(NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ)
                            + right.sampleRadiusAtAlong(rightAlong) * Math.sqrt(NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ)
                            + JUNCTION_BLOCK_CONTACT_MARGIN
                    )
                );
                if (approach.distanceSq() > wetContactRadius * wetContactRadius + 1.0e-9d)
                {
                    continue;
                }

                // A pair can touch once, diverge, and cross again. The old
                // minimum-distance rule always chose the later exact crossing
                // and left the earlier visible high/low contact uncoordinated.
                // Choose the first contact reached by both downstream paths;
                // distance only breaks ties at the same network progress.
                final double progress = Math.max(
                    leftAlong / left.totalLength,
                    rightAlong / right.totalLength
                );
                if (progress > bestProgress + 1.0e-9d
                    || Math.abs(progress - bestProgress) <= 1.0e-9d
                        && approach.distanceSq() > bestDistanceSq + 1.0e-9d)
                {
                    continue;
                }

                bestProgress = progress;
                bestDistanceSq = approach.distanceSq();
                best = new RouteIntersection(
                    leftSegment,
                    approach.leftDelta(),
                    leftAlong,
                    rightSegment,
                    approach.rightDelta(),
                    rightAlong,
                    lerp(approach.leftPoint(), approach.rightPoint(), 0.5d)
                );
            }
        }
        return best;
    }

    private static SegmentApproach segmentApproach(Vec leftStart, Vec leftEnd, Vec rightStart, Vec rightEnd)
    {
        final double ux = leftEnd.x() - leftStart.x();
        final double uz = leftEnd.z() - leftStart.z();
        final double vx = rightEnd.x() - rightStart.x();
        final double vz = rightEnd.z() - rightStart.z();
        final double wx = leftStart.x() - rightStart.x();
        final double wz = leftStart.z() - rightStart.z();
        final double a = ux * ux + uz * uz;
        final double b = ux * vx + uz * vz;
        final double c = vx * vx + vz * vz;
        final double d = ux * wx + uz * wz;
        final double e = vx * wx + vz * wz;
        final double denominator = a * c - b * b;

        double leftDelta = denominator <= 1.0e-9d ? 0d : Mth.clamp((b * e - c * d) / denominator, 0d, 1d);
        double rightDelta = c <= 1.0e-9d ? 0d : Mth.clamp((b * leftDelta + e) / c, 0d, 1d);
        leftDelta = a <= 1.0e-9d ? 0d : Mth.clamp((b * rightDelta - d) / a, 0d, 1d);
        rightDelta = c <= 1.0e-9d ? 0d : Mth.clamp((b * leftDelta + e) / c, 0d, 1d);

        final Vec leftPoint = lerp(leftStart, leftEnd, leftDelta);
        final Vec rightPoint = lerp(rightStart, rightEnd, rightDelta);
        return new SegmentApproach(
            leftDelta,
            rightDelta,
            distanceSq(leftPoint, rightPoint),
            leftPoint,
            rightPoint
        );
    }

    private static final class Headwater
    {
        private final long seed;
        private final int seaLevel;
        private final int downstreamWidth;
        private final HeightSampler heights;
        private final RouteObstacleSampler routeObstacles;
        @Nullable private final RiverEdge edge;
        private final double sourceX;
        private final double sourceZ;
        private final double drainX;
        private final double drainZ;
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;

        private volatile boolean attempted;
        @Nullable private volatile Route route;
        private volatile boolean replacement;
        @Nullable private volatile Route indexedRoute;
        private final Set<Long> indexedChunks = new HashSet<>();
        private volatile boolean intersectionsCoordinated;
        @Nullable private String failureReason;
        @Nullable private Vec failurePoint;
        private int attemptedDrainageCandidates;
        private int availableDrainageCandidates;
        private final Set<Headwater> junctionPeers = Collections.newSetFromMap(new IdentityHashMap<>());

        private Headwater(
            long seed,
            int seaLevel,
            int downstreamWidth,
            HeightSampler heights,
            RouteObstacleSampler routeObstacles,
            RiverEdge edge
        )
        {
            this(
                seed,
                seaLevel,
                downstreamWidth,
                heights,
                routeObstacles,
                edge,
                edge.source().x() * Units.GRID_WIDTH_IN_BLOCK,
                edge.source().y() * Units.GRID_WIDTH_IN_BLOCK,
                edge.drain().x() * Units.GRID_WIDTH_IN_BLOCK,
                edge.drain().y() * Units.GRID_WIDTH_IN_BLOCK
            );
        }

        private Headwater(
            long seed,
            int seaLevel,
            int downstreamWidth,
            HeightSampler heights,
            double sourceX,
            double sourceZ,
            double drainX,
            double drainZ
        )
        {
            this(
                seed,
                seaLevel,
                downstreamWidth,
                heights,
                NO_ROUTE_OBSTACLES,
                null,
                sourceX,
                sourceZ,
                drainX,
                drainZ
            );
        }

        private Headwater(
            long seed,
            int seaLevel,
            int downstreamWidth,
            HeightSampler heights,
            RouteObstacleSampler routeObstacles,
            @Nullable RiverEdge edge,
            double sourceX,
            double sourceZ,
            double drainX,
            double drainZ
        )
        {
            this.seed = seed;
            this.seaLevel = seaLevel;
            this.downstreamWidth = downstreamWidth;
            this.heights = heights;
            this.routeObstacles = routeObstacles;
            this.edge = edge;
            this.sourceX = sourceX;
            this.sourceZ = sourceZ;
            this.drainX = drainX;
            this.drainZ = drainZ;

            double minX = Math.min(sourceX, drainX);
            double minZ = Math.min(sourceZ, drainZ);
            double maxX = Math.max(sourceX, drainX);
            double maxZ = Math.max(sourceZ, drainZ);
            if (edge != null)
            {
                final double[] segments = edge.fractal().segments;
                for (int i = 0; i < segments.length; i += 2)
                {
                    final double x = segments[i] * Units.GRID_WIDTH_IN_BLOCK;
                    final double z = segments[i + 1] * Units.GRID_WIDTH_IN_BLOCK;
                    minX = Math.min(minX, x);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxZ = Math.max(maxZ, z);
                }
            }
            this.minX = Mth.floor(minX) - ROUTE_PADDING;
            this.minZ = Mth.floor(minZ) - ROUTE_PADDING;
            this.maxX = Mth.ceil(maxX) + ROUTE_PADDING;
            this.maxZ = Mth.ceil(maxZ) + ROUTE_PADDING;
        }

        @Nullable
        private Sample sample(int blockX, int blockZ, double ambientHeight)
        {
            // These bounds describe the initial TFC edge search area, not the
            // immutable route eventually published after smoothing and
            // junction coordination. They may prevent an unrelated query from
            // triggering planning, but must not clip an already planned route.
            if (!attempted && outsideInitialPlanningBounds(blockX, blockZ))
            {
                return null;
            }
            final Route route = route();
            return route == null ? null : route.sample(blockX, blockZ, ambientHeight);
        }

        @Nullable
        private Sample sampleIfPlanned(int blockX, int blockZ, double ambientHeight)
        {
            if (!attempted)
            {
                return null;
            }
            final Route planned = route;
            return planned == null ? null : planned.sample(blockX, blockZ, ambientHeight);
        }

        private boolean outsideInitialPlanningBounds(int blockX, int blockZ)
        {
            return blockX < minX || blockX > maxX || blockZ < minZ || blockZ > maxZ;
        }

        @Nullable
        private Boolean replacementIfPlanned()
        {
            return attempted ? replacement : null;
        }

        private boolean retainsTfcIfPlanned()
        {
            return attempted && !replacement;
        }

        @Nullable
        private Route route()
        {
            if (!attempted)
            {
                synchronized (this)
                {
                    if (!attempted)
                    {
                        route = plan();
                        replacement = route != null;
                        if (route == null && edge != null)
                        {
                            route = planSourceExtension();
                        }
                        attempted = true;
                        if (TRACE)
                        {
                            LOGGER.info(
                                "[TFE][HeadwaterTrace] source=({}, {}) drain=({}, {}) width={} result={} points={} drainageTried={}/{} path={} reason={}",
                                sourceX,
                                sourceZ,
                                drainX,
                                drainZ,
                                downstreamWidth,
                                route == null ? "TFC_FALLBACK" : replacement ? "STREAM" : "TFC_WITH_FEEDER",
                                route == null ? 0 : route.x.length,
                                attemptedDrainageCandidates,
                                availableDrainageCandidates,
                                route == null ? "none" : route.summary(),
                                failureReason == null ? "none" : failureReason
                            );
                        }
                    }
                }
            }
            return route;
        }

        @Nullable
        private Route plannedRoute()
        {
            return attempted ? route : null;
        }

        private boolean replacesTfc()
        {
            route();
            return replacement;
        }

        private boolean hasFeeder()
        {
            final Route planned = route();
            return planned != null && !replacement;
        }

        @Nullable
        private Route plan()
        {
            return planWithSmoothing(PREFERRED_CORNER_PRECISION);
        }

        @Nullable
        private Route planWithSmoothing(int smoothingPasses)
        {
            final int minCellX = Math.floorDiv(minX, SAMPLE_STEP);
            final int minCellZ = Math.floorDiv(minZ, SAMPLE_STEP);
            final int maxCellX = Math.floorDiv(maxX, SAMPLE_STEP);
            final int maxCellZ = Math.floorDiv(maxZ, SAMPLE_STEP);
            final int width = maxCellX - minCellX + 1;
            final int depth = maxCellZ - minCellZ + 1;
            final int size = width * depth;
            if (width < 2 || depth < 2 || size > 24000)
            {
                return fail("search_bounds");
            }

            final double[] terrain = new double[size];
            final boolean[] blocked = new boolean[size];
            for (int z = 0; z < depth; z++)
            {
                for (int x = 0; x < width; x++)
                {
                    final int cell = index(x, z, width);
                    final double blockX = (minCellX + x) * (double) SAMPLE_STEP;
                    final double blockZ = (minCellZ + z) * (double) SAMPLE_STEP;
                    terrain[cell] = heights.sample(Mth.floor(blockX), Mth.floor(blockZ));
                    blocked[cell] = routeObstacles.blocks(edge, blockX, blockZ, TFC_RIVER_GRID_CLEARANCE);
                }
            }

            final int nominalSourceX = Mth.clamp((int) Math.round(sourceX / SAMPLE_STEP) - minCellX, 0, width - 1);
            final int nominalSourceZ = Mth.clamp((int) Math.round(sourceZ / SAMPLE_STEP) - minCellZ, 0, depth - 1);
            final int goalX = Mth.clamp((int) Math.round(drainX / SAMPLE_STEP) - minCellX, 0, width - 1);
            final int goalZ = Mth.clamp((int) Math.round(drainZ / SAMPLE_STEP) - minCellZ, 0, depth - 1);
            final int goal = index(goalX, goalZ, width);

            final DrainageField drainage = buildDrainageField(
                goal,
                minCellX,
                minCellZ,
                width,
                depth,
                terrain,
                blocked
            );
            final List<SourceCandidate> sources = chooseDrainageSources(
                nominalSourceX,
                nominalSourceZ,
                width,
                depth,
                terrain,
                drainage,
                blocked
            );
            if (sources.isEmpty())
            {
                return fail("no_drainage_source");
            }
            availableDrainageCandidates = sources.size();

            String lastFailure = "none";
            int attemptedCandidates = 0;
            final double[] routePenalty = new double[size];
            final RouteSearchWorkspace routeSearch = new RouteSearchWorkspace(
                goal,
                width,
                depth,
                seaLevel,
                terrain,
                drainage,
                blocked,
                edge == null
            );
            Route bestRoute = null;
            IncisionRisk bestRisk = null;
            for (SourceCandidate source : sources)
            {
                final List<Vec> drainageRoute = searchDrainageAlternative(
                    source.index(),
                    goal,
                    minCellX,
                    minCellZ,
                    width,
                    routePenalty,
                    routeSearch
                );
                if (drainageRoute == null || drainageRoute.size() < 2)
                {
                    lastFailure = "broken_drainage_route";
                    continue;
                }

                final List<Vec> raw = snapRouteToValleys(drainageRoute);
                raw.set(raw.size() - 1, new Vec(drainX, drainZ));
                final Vec rawObstacle = firstRouteObstacle(raw);
                if (rawObstacle != null)
                {
                    lastFailure = String.format(
                        "unrelated_tfc_river at=(%.1f,%.1f)",
                        rawObstacle.x(),
                        rawObstacle.z()
                    );
                    applyFailedRoutePenalty(
                        drainageRoute,
                        rawObstacle,
                        goal,
                        minCellX,
                        minCellZ,
                        width,
                        depth,
                        routePenalty
                    );
                    continue;
                }
                attemptedCandidates++;
                attemptedDrainageCandidates = attemptedCandidates;
                failureReason = null;
                failurePoint = null;
                final Route candidate = buildFullRoute(raw, smoothingPasses);
                if (candidate != null)
                {
                    final IncisionRisk risk = evaluateIncisionRisk(
                        candidate,
                        MAX_NORMAL_INCISION,
                        MAX_OUTLET_ADAPTER_INCISION
                    );
                    if (risk.preferredTo(bestRisk))
                    {
                        bestRoute = candidate;
                        bestRisk = risk;
                    }
                    if (risk.withinPreferredRange())
                    {
                        return candidate;
                    }
                    lastFailure = String.format(
                        "incision_risk score=%.2f maxExcess=%d points=%d",
                        risk.score(),
                        risk.maximumExcess(),
                        risk.exceedingPoints()
                    );
                    applyFailedRoutePenalty(
                        drainageRoute,
                        risk.worstPoint(),
                        goal,
                        minCellX,
                        minCellZ,
                        width,
                        depth,
                        routePenalty
                    );
                    continue;
                }
                lastFailure = failureReason == null ? "unknown" : failureReason;
                applyFailedRoutePenalty(
                    drainageRoute,
                    failurePoint,
                    goal,
                    minCellX,
                    minCellZ,
                    width,
                    depth,
                    routePenalty
                );
            }

            if (bestRoute != null)
            {
                failureReason = null;
                failurePoint = null;
                return bestRoute;
            }

            return fail("drainage_candidates_exhausted count=" + attemptedCandidates + " last=" + lastFailure);
        }

        @Nullable
        private Route buildFullRoute(List<Vec> raw, int smoothingPasses)
        {

            final RiverEdge receiver = edge == null ? null : edge.drainEdge();
            final OutletRoute outlet = alignWithReceiver(
                smoothRoute(raw, smoothingPasses),
                receiver,
                downstreamWidth
            );
            if (outlet == null)
            {
                return failAt("receiver_alignment", raw.get(raw.size() - 1));
            }
            final List<Vec> smooth = outlet.points();
            if (smooth.size() < 2)
            {
                return failAt("route_entirely_inside_receiver", raw.get(raw.size() / 2));
            }
            final Vec obstacle = firstRouteObstacle(smooth);
            if (obstacle != null)
            {
                return failAt("unrelated_tfc_river", obstacle);
            }
            final int pointCount = smooth.size();
            final double[] x = new double[pointCount];
            final double[] z = new double[pointCount];
            final double[] terrainY = new double[pointCount];
            final double[] distance = new double[pointCount];
            for (int i = 0; i < pointCount; i++)
            {
                final Vec point = smooth.get(i);
                x[i] = point.x();
                z[i] = point.z();
                terrainY[i] = heights.sample(Mth.floor(x[i]), Mth.floor(z[i]));
                if (i > 0)
                {
                    distance[i] = distance[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
                }
            }
            final double totalLength = distance[pointCount - 1];
            if (totalLength < 96d)
            {
                return failAt("route_under_96_blocks", smooth.get(smooth.size() / 2));
            }

            final double[] radius = new double[pointCount];
            final double drainRadius = Mth.clamp(outlet.receiverWidth() * 0.45d, 3.2d, 5.5d);
            for (int i = 0; i < pointCount; i++)
            {
                final double progress = distance[i] / totalLength;
                final double baseRadius = Mth.lerp(Math.pow(progress, 0.72d), SOURCE_CHANNEL_RADIUS, drainRadius);
                // Keep the terrain-planned stream width through the aligned
                // mouth. Receiver width only defines the receiver distance
                // field below and does not inflate the supplemental creek.
                radius[i] = baseRadius;
            }

            final double outletWater = seaLevel - 1d;
            final double[] capacity = new double[pointCount];
            for (int i = 0; i < pointCount; i++)
            {
                capacity[i] = Math.min(
                    terrainY[i] - CENTER_SURFACE_INSET,
                    bankCapacity(i, x, z, radius)
                );
                if (capacity[i] + 1.0e-6d < outletWater)
                {
                    // A leaf with no TFC receiver terminates at the shore. The
                    // final sampled cells may already be below the global sea
                    // level, where the ocean is the receiving basin rather
                    // than an excavated river bank. Keep that short water
                    // transition wet; all inland cells remain subject to the
                    // normal bank-capacity invariant.
                    final boolean submergedSeaMouth = receiver == null
                        && totalLength - distance[i] <= SEA_MOUTH_WATER_TRANSITION_LENGTH;
                    if (submergedSeaMouth)
                    {
                        capacity[i] = outletWater;
                        continue;
                    }
                    return failAt(String.format(
                        "route_below_outlet_water index=%d capacity=%.2f outletWater=%.2f",
                        i,
                        capacity[i],
                        outletWater
                    ), smooth.get(i));
                }
            }

            // Streams-style grade: follow the real terrain envelope while
            // never climbing downstream. A second pass caps only genuinely
            // vertical drops at one block per horizontal block, allowing
            // terraces and cascades instead of forcing a sea-level trench.
            // The mouth transition is sized from the terrain envelope at the
            // reference offset: a drop at or below the configured baseline keeps
            // the historical 24-block window unchanged, while a deeper drop
            // spreads the same descent over a longer, gentler window. The
            // per-creek tier comes from this creek's own seed, so it is stable
            // for a given world seed and never depends on generation order.
            final double mouthEnvelopeWaterY = sampleProfileAtAlong(
                capacity,
                distance,
                Math.max(0d, totalLength - MOUTH_DROP_REFERENCE_LENGTH)
            );
            final double mouthTransition = mouthTransitionLength(
                Math.max(0d, mouthEnvelopeWaterY - outletWater),
                transitionTierMultiplier(seed),
                totalLength
            );
            final boolean[] subterranean = new boolean[pointCount];
            final double[] tunnelCeilingY = new double[pointCount];
            final double[] waterY = new double[pointCount];
            waterY[0] = capacity[0];
            for (int i = 1; i < pointCount; i++)
            {
                waterY[i] = Math.max(outletWater, Math.min(waterY[i - 1], capacity[i]));
            }
            waterY[pointCount - 1] = outletWater;
            for (int i = pointCount - 2; i >= 0; i--)
            {
                final double segmentLength = Math.max(1.0e-6d, distance[i + 1] - distance[i]);
                waterY[i] = Math.min(waterY[i], waterY[i + 1] + segmentLength * MAX_CASCADE_SLOPE);
            }
            // Strict decision only: the planned water grade is never touched. Nodes
            // inside a sustained run whose exposed cut exceeds the threshold are
            // marked as an underground section, and that section is then carved as
            // a covered cavity instead of an open cut.
            final List<SubterraneanRun> subterraneanRuns = NTECommonConfig.isHeadwaterUndergroundEnabled()
                ? markSubterranean(terrainY, waterY, radius, distance, pointCount, drainRadius, subterranean, tunnelCeilingY)
                : List.of();

            return new Route(
                x,
                z,
                terrainY,
                waterY,
                radius,
                distance,
                totalLength,
                outlet.receiverMouth(),
                outlet.alignment(),
                outletWater,
                mouthTransition,
                subterranean,
                tunnelCeilingY,
                subterraneanRuns,
                seed
            );
        }

        /**
         * Compare completed candidates by the excavation they would expose.
         * Exceeding the old preferred range is a planning cost, not a later
         * veto: isolated one-block excesses are cheap, while deep or sustained
         * artificial trenches grow quadratically and are avoided whenever a
         * gentler candidate exists.
         */
        /**
         * Strict underground decision. Only the exposed cut depth and its
         * sustained length are considered: the planned water grade, bed, widths and
         * mouth handling are never touched, so a creek which is not converted keeps
         * exactly its previous generation. Nodes inside a qualifying run are carved
         * as a covered cavity instead of an open cut.
         */
        private List<SubterraneanRun> markSubterranean(
            double[] terrainY,
            double[] waterY,
            double[] radius,
            double[] distance,
            int pointCount,
            double drainRadius,
            boolean[] subterranean,
            double[] tunnelCeilingY
        )
        {
            final List<SubterraneanRun> runs = new ArrayList<>();
            final int minimumCut = NTECommonConfig.getHeadwaterSinkMinCut();
            final int minimumRun = NTECommonConfig.getHeadwaterSinkMinRun();
            final int roof = NTECommonConfig.getHeadwaterTunnelRoof();
            final int airMin = NTECommonConfig.getHeadwaterTunnelAirMin();
            final int airMax = NTECommonConfig.getHeadwaterTunnelAirMax();
            // The density stage shapes each column's arch with its own noise, so
            // the planned ceiling keeps a noise sized budget on top of the base
            // air height. The planned ceiling stays the upper bound: cave
            // protection and the carved arch can never exceed it.
            final double noiseBudget = NTECommonConfig.getHeadwaterTunnelCarvingNoise();
            final double radiusRange = Math.max(1.0e-6d, drainRadius - SOURCE_CHANNEL_RADIUS);
            int runStart = -1;
            double runLength = 0d;
            for (int i = 0; i <= pointCount; i++)
            {
                final boolean deep = i < pointCount
                    && visibleIncisionDepth(terrainY[i], waterY[i]) >= minimumCut;
                if (deep)
                {
                    if (runStart < 0)
                    {
                        runStart = i;
                        runLength = 0d;
                    }
                    else if (i > 0)
                    {
                        runLength += Math.max(1.0e-6d, distance[i] - distance[i - 1]);
                    }
                    continue;
                }
                if (runStart >= 0 && runLength >= minimumRun)
                {
                    for (int node = runStart; node < i; node++)
                    {
                        final double widthProgress = Mth.clamp(
                            (radius[node] - SOURCE_CHANNEL_RADIUS) / radiusRange,
                            0d,
                            1d
                        );
                        final double air = Mth.clamp(
                            airMin + (airMax - airMin) * widthProgress + noiseBudget,
                            airMin,
                            airMax + noiseBudget
                        );
                        subterranean[node] = true;
                        tunnelCeilingY[node] = Math.min(waterY[node] + air, terrainY[node] - roof);
                    }
                    runs.add(new SubterraneanRun(distance[runStart], distance[i - 1]));
                }
                runStart = -1;
                runLength = 0d;
            }
            return List.copyOf(runs);
        }
        private static IncisionRisk evaluateIncisionRisk(
            Route route,
            double normalPreferredIncision,
            double outletPreferredIncision
        )
        {
            double score = 0d;
            double continuousLength = 0d;
            int maximumExcess = 0;
            int exceedingPoints = 0;
            Vec worstPoint = null;
            for (int i = 0; i < route.x.length; i++)
            {
                final double distanceToOutlet = route.totalLength - route.distance[i];
                final int preferredVisible = Mth.ceil(distanceToOutlet <= OUTLET_ADAPTER_LENGTH
                    ? outletPreferredIncision
                    : normalPreferredIncision);
                final int excess = Math.max(
                    0,
                    visibleIncisionDepth(route.terrainY[i], route.waterY[i]) - preferredVisible
                );
                if (excess == 0)
                {
                    continuousLength = 0d;
                    continue;
                }

                exceedingPoints++;
                continuousLength += i == 0
                    ? 1d
                    : Math.max(1d, route.distance[i] - route.distance[i - 1]);
                score += excess * excess * PLANNED_INCISION_EXCESS_COST
                    + excess * continuousLength * 0.35d;
                if (excess > maximumExcess)
                {
                    maximumExcess = excess;
                    worstPoint = new Vec(route.x[i], route.z[i]);
                }
            }
            return new IncisionRisk(score, maximumExcess, exceedingPoints, worstPoint);
        }

        /**
         * If a complete leaf replacement is unsafe, retain TFC's leaf and add
         * only a narrow upstream feeder. The feeder disappears under the
         * retained TFC river influence at the join, so it cannot override the
         * main channel or recreate a suspended crossing over the unsafe area.
         */
        @Nullable
        private Route planSourceExtension()
        {
            final int minCellX = Math.floorDiv(minX, SAMPLE_STEP);
            final int minCellZ = Math.floorDiv(minZ, SAMPLE_STEP);
            final int maxCellX = Math.floorDiv(maxX, SAMPLE_STEP);
            final int maxCellZ = Math.floorDiv(maxZ, SAMPLE_STEP);
            final int width = maxCellX - minCellX + 1;
            final int depth = maxCellZ - minCellZ + 1;
            final int size = width * depth;
            if (width < 2 || depth < 2 || size > 24000)
            {
                return failFeeder("search_bounds");
            }

            final double[] terrain = new double[size];
            final boolean[] blocked = new boolean[size];
            for (int z = 0; z < depth; z++)
            {
                for (int x = 0; x < width; x++)
                {
                    final int cell = index(x, z, width);
                    final double blockX = (minCellX + x) * (double) SAMPLE_STEP;
                    final double blockZ = (minCellZ + z) * (double) SAMPLE_STEP;
                    terrain[cell] = heights.sample(Mth.floor(blockX), Mth.floor(blockZ));
                    blocked[cell] = routeObstacles.blocks(edge, blockX, blockZ, TFC_RIVER_GRID_CLEARANCE);
                }
            }

            final int nominalSourceX = Mth.clamp((int) Math.round(sourceX / SAMPLE_STEP) - minCellX, 0, width - 1);
            final int nominalSourceZ = Mth.clamp((int) Math.round(sourceZ / SAMPLE_STEP) - minCellZ, 0, depth - 1);
            final int start = chooseSource(nominalSourceX, nominalSourceZ, width, depth, terrain, blocked);
            final int goal = index(nominalSourceX, nominalSourceZ, width);
            if (start < 0)
            {
                return failFeeder("no_unobstructed_source");
            }
            final int[] parent = search(start, goal, minCellX, minCellZ, width, depth, terrain, blocked);
            if (parent == null || parent[goal] < 0)
            {
                return failFeeder("no_route");
            }

            final List<Vec> raw = new ArrayList<>();
            int cursor = goal;
            while (cursor != -1)
            {
                final int cellX = cursor % width;
                final int cellZ = cursor / width;
                raw.add(new Vec(
                    (minCellX + cellX) * (double) SAMPLE_STEP,
                    (minCellZ + cellZ) * (double) SAMPLE_STEP
                ));
                if (cursor == start)
                {
                    break;
                }
                cursor = parent[cursor];
            }
            Collections.reverse(raw);
            if (raw.size() < 2)
            {
                return failFeeder("short_raw_route");
            }
            raw.set(raw.size() - 1, new Vec(sourceX, sourceZ));

            // A feeder is a conservative fallback for a retained TFC leaf. If
            // its terrain-planned approach falls outside the near-parallel
            // target cone, turn it on the creek side of the shared node before
            // yielding ownership at the first TFC water-core hit.
            final List<Vec> rounded = smoothRoute(raw, 1);
            final double joinRadius = Mth.clamp(downstreamWidth * 0.18d, 1.4d, 2.0d);
            OutletRoute outlet = clipAtReceiver(
                rounded,
                edge,
                FEEDER_RECEIVER_WIDTH_SCALE,
                downstreamWidth
            );
            if (edge != null)
            {
                final ReceiverPath retainedPath = receiverPath(edge);
                if (approachFlowDot(rounded, retainedPath) < SOURCE_ALIGNMENT_TARGET_CONTACT_DOT)
                {
                    final OutletRoute aligned = alignRoute(
                        rounded,
                        retainedPath,
                        FEEDER_RECEIVER_WIDTH_SCALE,
                        joinRadius
                    );
                    if (aligned.alignment() != null)
                    {
                        final OutletRoute clipped = clipAtReceiver(
                            aligned.points(),
                            edge,
                            FEEDER_RECEIVER_WIDTH_SCALE,
                            downstreamWidth
                        );
                        if (clipped.points().size() >= 2
                            && polylineLength(clipped.points()) >= SOURCE_EXTENSION_MIN - SAMPLE_STEP)
                        {
                            outlet = clipped;
                        }
                    }
                }
            }
            final List<Vec> smooth = outlet.points();
            if (smooth.size() < 2)
            {
                return failFeeder("route_entirely_inside_receiver");
            }
            final Vec obstacle = firstRouteObstacle(smooth);
            if (obstacle != null)
            {
                return failFeeder(String.format(
                    "unrelated_tfc_river at=(%.1f,%.1f)",
                    obstacle.x(),
                    obstacle.z()
                ));
            }
            final int pointCount = smooth.size();
            final double[] x = new double[pointCount];
            final double[] z = new double[pointCount];
            final double[] terrainY = new double[pointCount];
            final double[] distance = new double[pointCount];
            for (int i = 0; i < pointCount; i++)
            {
                final Vec point = smooth.get(i);
                x[i] = point.x();
                z[i] = point.z();
                terrainY[i] = heights.sample(Mth.floor(x[i]), Mth.floor(z[i]));
                if (i > 0)
                {
                    distance[i] = distance[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
                }
            }
            final double totalLength = distance[pointCount - 1];
            if (totalLength < SOURCE_EXTENSION_MIN - SAMPLE_STEP)
            {
                return failFeeder("route_under_source_extension_minimum");
            }

            final double[] radius = new double[pointCount];
            for (int i = 0; i < pointCount; i++)
            {
                final double progress = distance[i] / totalLength;
                radius[i] = Mth.lerp(Math.pow(progress, 0.72d), SOURCE_CHANNEL_RADIUS, joinRadius);
            }

            final double minimumWater = seaLevel - 1d;
            final double[] capacity = new double[pointCount];
            for (int i = 0; i < pointCount; i++)
            {
                capacity[i] = Math.min(terrainY[i] - CENTER_SURFACE_INSET, bankCapacity(i, x, z, radius));
                if (capacity[i] + 1.0e-6d < minimumWater)
                {
                    return failFeeder(String.format(
                        "below_minimum_water index=%d capacity=%.2f minimum=%.2f",
                        i,
                        capacity[i],
                        minimumWater
                    ));
                }
            }

            final double[] waterY = new double[pointCount];
            waterY[0] = capacity[0];
            for (int i = 1; i < pointCount; i++)
            {
                waterY[i] = Math.max(minimumWater, Math.min(waterY[i - 1], capacity[i]));
            }
            // A fallback feeder still has to reach the retained river's real
            // water layer. Previously its last sample stayed at local terrain
            // height and the later mouth adapter could lower it by only two
            // blocks, leaving a dry vertical gap above the native river.
            waterY[pointCount - 1] = minimumWater;
            for (int i = pointCount - 2; i >= 0; i--)
            {
                final double segmentLength = Math.max(1.0e-6d, distance[i + 1] - distance[i]);
                waterY[i] = Math.min(waterY[i], waterY[i + 1] + segmentLength * MAX_CASCADE_SLOPE);
            }
            final double feederEnvelopeWaterY = sampleProfileAtAlong(
                capacity,
                distance,
                Math.max(0d, totalLength - MOUTH_DROP_REFERENCE_LENGTH)
            );
            final boolean[] subterranean = new boolean[pointCount];
            final double[] tunnelCeilingY = new double[pointCount];
            // Feeder sections follow exactly the same strict decision as a full
            // replacement; the planned grade itself stays untouched.
            final List<SubterraneanRun> subterraneanRuns = NTECommonConfig.isHeadwaterUndergroundEnabled()
                ? markSubterranean(terrainY, waterY, radius, distance, pointCount, joinRadius, subterranean, tunnelCeilingY)
                : List.of();
            return new Route(
                x,
                z,
                terrainY,
                waterY,
                radius,
                distance,
                totalLength,
                outlet.receiverMouth(),
                outlet.alignment(),
                minimumWater,
                mouthTransitionLength(
                    Math.max(0d, feederEnvelopeWaterY - minimumWater),
                    transitionTierMultiplier(seed),
                    totalLength
                ),
                subterranean,
                tunnelCeilingY,
                subterraneanRuns,
                seed
            );
        }

        @Nullable
        private Route fail(String reason)
        {
            failureReason = reason;
            return null;
        }

        @Nullable
        private Route failAt(String reason, Vec point)
        {
            failurePoint = point;
            return fail(String.format("%s at=(%.1f,%.1f)", reason, point.x(), point.z()));
        }

        @Nullable
        private Route failFeeder(String reason)
        {
            failureReason = (failureReason == null ? "primary=unknown" : "primary=" + failureReason)
                + "; feeder=" + reason;
            return null;
        }

        @Nullable
        private static OutletRoute alignWithReceiver(
            List<Vec> route,
            @Nullable RiverEdge receiver,
            double fallbackWidth
        )
        {
            if (receiver == null)
            {
                return new OutletRoute(route, false, fallbackWidth, null);
            }
            if (route.size() < 3)
            {
                return null;
            }

            final ReceiverPath receiverPath = receiverPath(receiver);
            final OutletRoute aligned = alignRoute(route, receiverPath);
            if (aligned.alignment() == null)
            {
                return null;
            }
            final ReceiverAlignment geometry = aligned.alignment();
            final Vec anchor = aligned.points().get(aligned.points().size() - 1);
            final double receiverWidth = receiverWidthAt(receiver, anchor);
            return new OutletRoute(
                aligned.points(),
                true,
                receiverWidth,
                new ReceiverAlignment(
                    receiver,
                    geometry.receiverPath,
                    geometry.sourceAlong,
                    geometry.anchorAlong,
                    receiverWidth
                )
            );
        }

        private static OutletRoute alignRoute(List<Vec> route, ReceiverPath receiverPath)
        {
            final double streamRadius = Mth.clamp(receiverPath.width() * 0.45d, 3.2d, 5.5d);
            return alignRoute(route, receiverPath, 1d, streamRadius);
        }

        private static OutletRoute alignRoute(
            List<Vec> route,
            ReceiverPath receiverPath,
            double receiverWidthScale,
            double supplementalRadius
        )
        {
            if (receiverPath.points().size() < 2 || route.size() < 3)
            {
                return new OutletRoute(route, false, receiverPath.width(), null);
            }

            // The graph node shared by route.last() and the receiver source is
            // the authoritative join. Looking up the receiver from the rewrite
            // start can snap to an unrelated nearby bend and create a false,
            // visibly reversed confluence.
            final double connectionAlong = nearestAlong(
                receiverPath.points(),
                route.get(route.size() - 1)
            );
            final double flowDot = approachFlowDot(route, receiverPath, connectionAlong);
            final double turnSeverity = Mth.clamp(
                (SOURCE_ALIGNMENT_TARGET_CONTACT_DOT - flowDot)
                    / (SOURCE_ALIGNMENT_TARGET_CONTACT_DOT + 1d),
                0d,
                1d
            );
            final int attempts = turnSeverity > 0d ? SOURCE_ALIGNMENT_MAX_ATTEMPTS : 1;
            final double contactRadius = receiverPath.width()
                * receiverWidthScale
                * Math.sqrt(NTERiverHydrology.TFC_WATER_CORE_RADIUS_SQ)
                + supplementalRadius * Math.sqrt(NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ);

            AlignmentCandidate best = null;
            for (int attempt = 0; attempt < attempts; attempt++)
            {
                final double retryExtension = attempt * SOURCE_ALIGNMENT_RETRY_EXTENSION;
                final double rewriteLength = SOURCE_ALIGNMENT_REWRITE_LENGTH
                    + turnSeverity * SOURCE_ALIGNMENT_MAX_EXTENSION
                    + retryExtension;
                final AlignmentCandidate candidate = alignmentCandidate(
                    route,
                    receiverPath,
                    connectionAlong,
                    rewriteLength,
                    turnSeverity,
                    attempt,
                    contactRadius
                );
                if (candidate != null && (best == null || candidate.contactDot() > best.contactDot()))
                {
                    best = candidate;
                }
                if (candidate != null && candidate.contactDot() >= SOURCE_ALIGNMENT_TARGET_CONTACT_DOT)
                {
                    break;
                }
            }

            if (best == null)
            {
                return new OutletRoute(route, false, receiverPath.width(), null);
            }

            final ReceiverAlignment alignment = new ReceiverAlignment(
                null,
                List.copyOf(receiverPath.points()),
                connectionAlong,
                connectionAlong,
                receiverPath.width()
            );
            return new OutletRoute(best.points(), true, receiverPath.width(), alignment);
        }

        @Nullable
        private static AlignmentCandidate alignmentCandidate(
            List<Vec> route,
            ReceiverPath receiverPath,
            double connectionAlong,
            double rewriteLength,
            double turnSeverity,
            int attempt,
            double contactRadius
        )
        {
            final int rewriteStart = findRewriteStart(route, rewriteLength);
            final List<Vec> prefix = new ArrayList<>(route.subList(0, rewriteStart + 1));
            final int connectorStart = prefix.size() - 1;
            final Vec start = prefix.get(connectorStart);
            final Vec incoming = normalizedDirection(route.get(Math.max(0, rewriteStart - 2)), route.get(rewriteStart));
            if (vectorLength(incoming) < 1.0e-6d)
            {
                return null;
            }

            // Keep the graph join as the actual geometric endpoint. Following
            // the receiver downstream made the replacement creek run beside
            // the retained river and subjected that artificial tail to terrain
            // validation, which caused both parallel misses and mass fallback.
            final Vec anchor = route.get(route.size() - 1);
            final Vec receiverDirection = directionAlong(
                receiverPath.points(),
                Math.min(polylineLength(receiverPath.points()), connectionAlong + 1.0e-3d)
            );
            final double directLength = distance(start, anchor);
            if (directLength < SOURCE_ALIGNMENT_LENGTH * 0.45d || vectorLength(receiverDirection) < 1.0e-6d)
            {
                return null;
            }

            // The visible merge should already be nearly parallel to the receiver's flow,
            // not merely non-obtuse. Scale every approach outside the target
            // cone and retry with more room; a nearly parallel merge retains
            // the established compact geometry.
            final double tangentLimit = SOURCE_ALIGNMENT_LENGTH * 0.72d
                + turnSeverity * SOURCE_ALIGNMENT_MAX_EXTENSION * 0.75d
                + attempt * SOURCE_ALIGNMENT_RETRY_EXTENSION * 0.25d;
            final double startTangentLength = Math.min(tangentLimit, directLength * 0.62d);
            final double receiverTangentLength = Math.min(tangentLimit * 1.35d, directLength * 0.88d);
            final Vec startControl = add(start, scale(incoming, startTangentLength));
            final Vec endControl = add(anchor, scale(receiverDirection, -receiverTangentLength));
            final int steps = Math.max(8, Mth.ceil(directLength / SOURCE_ALIGNMENT_SAMPLE_SPACING));
            for (int step = 1; step <= steps; step++)
            {
                addIfDifferent(prefix, cubicBezier(
                    start,
                    startControl,
                    endControl,
                    anchor,
                    step / (double) steps
                ));
            }

            return new AlignmentCandidate(
                List.copyOf(prefix),
                firstContactFlowDot(prefix, connectorStart, receiverPath, contactRadius)
            );
        }

        private static double approachFlowDot(List<Vec> route, ReceiverPath receiverPath)
        {
            final double connectionAlong = nearestAlong(
                receiverPath.points(),
                route.get(route.size() - 1)
            );
            return approachFlowDot(route, receiverPath, connectionAlong);
        }

        private static double approachFlowDot(
            List<Vec> route,
            ReceiverPath receiverPath,
            double connectionAlong
        )
        {
            final double routeLength = polylineLength(route);
            final Vec approachStart = pointAlong(
                route,
                Math.max(0d, routeLength - SOURCE_ALIGNMENT_FLOW_LOOKBACK)
            );
            final Vec incoming = normalizedDirection(approachStart, route.get(route.size() - 1));
            final double receiverLength = polylineLength(receiverPath.points());
            final Vec receiverDirection = directionAlong(
                receiverPath.points(),
                Math.min(receiverLength, connectionAlong + 1.0e-3d)
            );
            return directionDot(incoming, receiverDirection);
        }

        private static double firstContactFlowDot(
            List<Vec> route,
            int connectorStart,
            ReceiverPath receiverPath,
            double contactRadius
        )
        {
            final double contactRadiusSq = contactRadius * contactRadius;
            for (int i = Math.max(1, connectorStart + 1); i < route.size(); i++)
            {
                final Vec current = route.get(i);
                if (distanceSqToPolyline(receiverPath.points(), current) <= contactRadiusSq)
                {
                    final Vec currentDirection = normalizedDirection(route.get(i - 1), current);
                    final double receiverAlong = nearestAlong(receiverPath.points(), current);
                    final Vec receiverDirection = directionAlong(
                        receiverPath.points(),
                        Math.min(polylineLength(receiverPath.points()), receiverAlong + 1.0e-3d)
                    );
                    return directionDot(currentDirection, receiverDirection);
                }
            }
            return -1d;
        }

        private static OutletRoute clipAtReceiver(
            List<Vec> route,
            @Nullable RiverEdge receiver,
            double receiverWidthScale,
            double fallbackWidth
        )
        {
            if (receiver == null || route.size() < 2)
            {
                return new OutletRoute(route, false, fallbackWidth, null);
            }

            final double receiverCoreRadiusSq = NTERiverHydrology.TFC_WATER_CORE_RADIUS_SQ
                * receiverWidthScale * receiverWidthScale;
            final List<Vec> clipped = new ArrayList<>(route.size());
            Vec previous = route.get(0);
            double previousDistance = receiverDistanceSq(receiver, previous);
            clipped.add(previous);
            for (int i = 1; i < route.size(); i++)
            {
                final Vec current = route.get(i);
                final double currentDistance = receiverDistanceSq(receiver, current);
                if (currentDistance <= receiverCoreRadiusSq)
                {
                    if (previousDistance > receiverCoreRadiusSq)
                    {
                        double outside = 0d;
                        double inside = 1d;
                        for (int iteration = 0; iteration < 12; iteration++)
                        {
                            final double delta = (outside + inside) * 0.5d;
                            final Vec candidate = lerp(previous, current, delta);
                            if (receiverDistanceSq(receiver, candidate) <= receiverCoreRadiusSq)
                            {
                                inside = delta;
                            }
                            else
                            {
                                outside = delta;
                            }
                        }
                        clipped.add(lerp(previous, current, inside));
                    }
                    final List<Vec> clippedRoute = List.copyOf(clipped);
                    final Vec anchor = clippedRoute.get(clippedRoute.size() - 1);
                    final double receiverWidth = receiverWidthAt(receiver, anchor);
                    final ReceiverPath retainedPath = receiverPath(receiver);
                    final double anchorAlong = nearestAlong(retainedPath.points(), anchor);
                    return new OutletRoute(
                        clippedRoute,
                        true,
                        receiverWidth,
                        new ReceiverAlignment(
                            null,
                            retainedPath.points(),
                            anchorAlong,
                            anchorAlong,
                            receiverWidth
                        )
                    );
                }
                clipped.add(current);
                previous = current;
                previousDistance = currentDistance;
            }
            return new OutletRoute(route, false, fallbackWidth, null);
        }

        private static ReceiverPath receiverPath(RiverEdge receiver)
        {
            final double[] segments = receiver.fractal().segments;
            final List<Vec> points = new ArrayList<>(segments.length / 2);
            for (int i = 0; i < segments.length; i += 2)
            {
                points.add(new Vec(
                    segments[i] * Units.GRID_WIDTH_IN_BLOCK,
                    segments[i + 1] * Units.GRID_WIDTH_IN_BLOCK
                ));
            }
            if (points.size() >= 2)
            {
                final Vec topologySource = new Vec(
                    receiver.source().x() * Units.GRID_WIDTH_IN_BLOCK,
                    receiver.source().y() * Units.GRID_WIDTH_IN_BLOCK
                );
                if (distance(points.get(points.size() - 1), topologySource)
                    < distance(points.get(0), topologySource))
                {
                    Collections.reverse(points);
                }
            }
            final double width = points.isEmpty() ? receiver.width : receiverWidthAt(receiver, points.get(0));
            return new ReceiverPath(List.copyOf(points), width);
        }

        private static double receiverWidthAt(RiverEdge receiver, Vec point)
        {
            return Math.sqrt(receiver.widthSq(
                Units.blockToGridExact(point.x()),
                Units.blockToGridExact(point.z())
            ));
        }

        private static double receiverDistanceSq(RiverEdge receiver, Vec point)
        {
            final double gridX = Units.blockToGridExact(point.x());
            final double gridZ = Units.blockToGridExact(point.z());
            final double distanceSqBlocks = receiver.fractal().intersectDistance(gridX, gridZ)
                * Units.GRID_WIDTH_IN_BLOCK * Units.GRID_WIDTH_IN_BLOCK;
            return distanceSqBlocks / receiver.widthSq(gridX, gridZ);
        }

        /**
         * Build one deterministic, receiver-rooted drainage tree. The spill
         * elevation is a local priority-flood analogue: every cell is linked
         * to the neighbour that reaches the retained TFC outlet over the
         * lowest possible saddle, with travel distance and cell id providing
         * stable tie-breakers. Unlike the former point-to-point A*, this field
         * describes all viable upstream approaches before a spring is chosen.
         */
        private DrainageField buildDrainageField(
            int goal,
            int minCellX,
            int minCellZ,
            int width,
            int depth,
            double[] terrain,
            boolean[] blocked
        )
        {
            final int size = width * depth;
            final int[] downstream = new int[size];
            final double[] spill = new double[size];
            final double[] travel = new double[size];
            Arrays.fill(downstream, -1);
            Arrays.fill(spill, Double.POSITIVE_INFINITY);
            Arrays.fill(travel, Double.POSITIVE_INFINITY);

            final DrainageHeap open = new DrainageHeap(size);
            spill[goal] = terrain[goal];
            travel[goal] = 0d;
            open.add(goal, spill[goal], 0d);
            final int goalX = goal % width;
            final int goalZ = goal / width;

            while (!open.isEmpty())
            {
                open.removeFirst();
                final int nodeIndex = open.removedIndex;
                final double nodeSpill = open.removedSpill;
                final double nodeTravel = open.removedTravel;
                if (nodeSpill > spill[nodeIndex] + DRAINAGE_SPILL_EPSILON
                    || nodeTravel > travel[nodeIndex] + DRAINAGE_SPILL_EPSILON)
                {
                    continue;
                }

                final int currentX = nodeIndex % width;
                final int currentZ = nodeIndex / width;
                for (int direction = 0; direction < DRAINAGE_DIRECTIONS.length; direction += 2)
                {
                    final int nextX = currentX + DRAINAGE_DIRECTIONS[direction];
                    final int nextZ = currentZ + DRAINAGE_DIRECTIONS[direction + 1];
                    if (nextX < 0 || nextZ < 0 || nextX >= width || nextZ >= depth)
                    {
                        continue;
                    }

                    final int next = index(nextX, nextZ, width);
                    if (blocked[next] && next != goal)
                    {
                        continue;
                    }
                    if (next != goal && terrain[next] < seaLevel - 1d + CENTER_SURFACE_INSET
                        && !(edge == null && RouteSearchWorkspace.nearGoal(nextX, nextZ, goalX, goalZ)))
                    {
                        continue;
                    }
                    final double step = DRAINAGE_DIRECTIONS[direction] == 0
                        || DRAINAGE_DIRECTIONS[direction + 1] == 0 ? 1d : Math.sqrt(2d);
                    final double nextSpill = Math.max(nodeSpill, terrain[next]);
                    final double nextTravel = nodeTravel + step;
                    final boolean lowerSpill = nextSpill < spill[next] - DRAINAGE_SPILL_EPSILON;
                    final boolean equalSpill = Math.abs(nextSpill - spill[next]) <= DRAINAGE_SPILL_EPSILON;
                    final boolean shorter = nextTravel < travel[next] - DRAINAGE_SPILL_EPSILON;
                    final boolean stableTie = Math.abs(nextTravel - travel[next]) <= DRAINAGE_SPILL_EPSILON
                        && (downstream[next] < 0 || nodeIndex < downstream[next]);
                    if (lowerSpill || equalSpill && (shorter || stableTie))
                    {
                        spill[next] = nextSpill;
                        travel[next] = nextTravel;
                        downstream[next] = nodeIndex;
                        open.add(next, nextSpill, nextTravel);
                    }
                }
            }

            final double[] accumulation = new double[size];
            final List<Integer> upstreamFirst = new ArrayList<>(size);
            for (int cell = 0; cell < size; cell++)
            {
                if (cell == goal || downstream[cell] >= 0)
                {
                    final int x = cell % width;
                    final int z = cell / width;
                    accumulation[cell] = 1d + Math.min(4d, valleyBonus(x, z, width, depth, terrain) * 0.35d);
                    upstreamFirst.add(cell);
                }
            }
            upstreamFirst.sort((left, right) -> {
                final int distanceOrder = Double.compare(travel[right], travel[left]);
                return distanceOrder != 0 ? distanceOrder : Integer.compare(left, right);
            });
            for (int cell : upstreamFirst)
            {
                final int next = downstream[cell];
                if (next >= 0)
                {
                    accumulation[next] += accumulation[cell];
                }
            }

            return new DrainageField(downstream, spill, accumulation);
        }

        private List<SourceCandidate> chooseDrainageSources(
            int nominalX,
            int nominalZ,
            int width,
            int depth,
            double[] terrain,
            DrainageField drainage,
            boolean[] blocked
        )
        {
            final double downstreamX = drainX - sourceX;
            final double downstreamZ = drainZ - sourceZ;
            final double downstreamLength = Math.hypot(downstreamX, downstreamZ);
            if (downstreamLength < 1.0e-6d)
            {
                final int nearby = chooseNearbySource(nominalX, nominalZ, width, depth, terrain, blocked);
                return nearby >= 0 && drainage.downstream()[nearby] >= 0
                    ? List.of(new SourceCandidate(nearby, 0d))
                    : List.of();
            }

            final double upstreamX = -downstreamX / downstreamLength;
            final double upstreamZ = -downstreamZ / downstreamLength;
            final List<SourceCandidate> ranked = new ArrayList<>();
            for (int z = 1; z < depth - 1; z++)
            {
                for (int x = 1; x < width - 1; x++)
                {
                    final int candidate = index(x, z, width);
                    if (blocked[candidate]
                        || drainage.downstream()[candidate] < 0
                        || terrain[candidate] < seaLevel - 1d + CENTER_SURFACE_INSET)
                    {
                        continue;
                    }

                    final double deltaX = (x - nominalX) * (double) SAMPLE_STEP;
                    final double deltaZ = (z - nominalZ) * (double) SAMPLE_STEP;
                    final double along = deltaX * upstreamX + deltaZ * upstreamZ;
                    if (along < SOURCE_EXTENSION_MIN || along > SOURCE_EXTENSION_MAX)
                    {
                        continue;
                    }
                    final double lateral = Math.abs(deltaX * upstreamZ - deltaZ * upstreamX);
                    if (lateral > SOURCE_EXTENSION_LATERAL)
                    {
                        continue;
                    }

                    final double barrier = Math.max(0d, drainage.spill()[candidate] - terrain[candidate]);
                    final double valley = valleyBonus(x, z, width, depth, terrain);
                    final double score = Math.log1p(drainage.accumulation()[candidate]) * 10d
                        + valley * 3.5d
                        + Math.max(0d, terrain[candidate] - terrain[index(nominalX, nominalZ, width)]) * 0.12d
                        + along * 0.012d
                        - lateral * 0.025d
                        - barrier * 18d;
                    ranked.add(new SourceCandidate(candidate, score));
                }
            }

            ranked.sort(Comparator.comparingDouble(SourceCandidate::score).reversed()
                .thenComparingInt(SourceCandidate::index));
            final List<SourceCandidate> selected = new ArrayList<>(MAX_DRAINAGE_SOURCE_CANDIDATES);
            for (SourceCandidate candidate : ranked)
            {
                final int candidateX = candidate.index() % width;
                final int candidateZ = candidate.index() / width;
                boolean separated = true;
                for (SourceCandidate existing : selected)
                {
                    final double dx = (candidateX - existing.index() % width) * (double) SAMPLE_STEP;
                    final double dz = (candidateZ - existing.index() / width) * (double) SAMPLE_STEP;
                    if (Math.hypot(dx, dz) < DRAINAGE_SOURCE_SEPARATION)
                    {
                        separated = false;
                        break;
                    }
                }
                if (separated)
                {
                    selected.add(candidate);
                    if (selected.size() >= MAX_DRAINAGE_SOURCE_CANDIDATES)
                    {
                        break;
                    }
                }
            }
            return selected;
        }

        /**
         * Search one route through the drainage field. The first pass strongly
         * prefers the receiver-rooted drainage tree; later passes receive a
         * penalty field from failed candidates, so a different spring can
         * approach the same retained outlet through a genuinely different
         * corridor instead of immediately merging onto the failed trunk.
         */
        @Nullable
        private List<Vec> searchDrainageAlternative(
            int start,
            int goal,
            int minCellX,
            int minCellZ,
            int width,
            double[] routePenalty,
            RouteSearchWorkspace workspace
        )
        {
            workspace.begin(start);
            workspace.open.add(start, workspace.heuristics[start]);
            final int directionCount = workspace.neighborOffsets.length;
            while (!workspace.open.isEmpty())
            {
                final int current = workspace.open.removeFirst();
                if (workspace.isClosed(current))
                {
                    continue;
                }
                workspace.close(current);
                if (current == goal)
                {
                    break;
                }

                final int traversalOffset = current * directionCount;
                final double currentCost = workspace.cost(current);
                for (int direction = 0; direction < directionCount; direction++)
                {
                    final double traversalBaseCost = workspace.traversalBaseCosts[traversalOffset + direction];
                    if (Double.isNaN(traversalBaseCost))
                    {
                        continue;
                    }
                    final int next = current + workspace.neighborOffsets[direction];
                    if (workspace.isClosed(next))
                    {
                        continue;
                    }
                    final double nextCost = currentCost + workspace.steps[direction]
                        * (traversalBaseCost + routePenalty[next]);
                    if (workspace.relax(next, nextCost, current))
                    {
                        workspace.open.add(next, nextCost + workspace.heuristics[next] * 0.55d);
                    }
                }
            }

            if (start != goal && workspace.parent(goal) < 0)
            {
                return null;
            }
            final List<Vec> route = new ArrayList<>();
            int cursor = goal;
            while (cursor >= 0)
            {
                route.add(new Vec(
                    (minCellX + cursor % width) * (double) SAMPLE_STEP,
                    (minCellZ + cursor / width) * (double) SAMPLE_STEP
                ));
                if (cursor == start)
                {
                    Collections.reverse(route);
                    return route;
                }
                cursor = workspace.parent(cursor);
            }
            return null;
        }

        private static void applyFailedRoutePenalty(
            List<Vec> route,
            @Nullable Vec failure,
            int goal,
            int minCellX,
            int minCellZ,
            int width,
            int depth,
            double[] routePenalty
        )
        {
            // Penalize overlap along the complete rejected line, but never
            // penalize the mandatory shared outlet cell itself.
            for (int i = 1; i < route.size() - 1; i++)
            {
                final Vec point = route.get(i);
                final int cellX = Mth.clamp((int) Math.round(point.x() / SAMPLE_STEP) - minCellX, 0, width - 1);
                final int cellZ = Mth.clamp((int) Math.round(point.z() / SAMPLE_STEP) - minCellZ, 0, depth - 1);
                final int cell = index(cellX, cellZ, width);
                if (cell != goal)
                {
                    routePenalty[cell] += FAILED_ROUTE_OVERLAP_PENALTY;
                }
            }
            if (failure == null)
            {
                return;
            }

            final int failureX = Mth.clamp((int) Math.round(failure.x() / SAMPLE_STEP) - minCellX, 0, width - 1);
            final int failureZ = Mth.clamp((int) Math.round(failure.z() / SAMPLE_STEP) - minCellZ, 0, depth - 1);
            for (int dz = -FAILED_ROUTE_LOCAL_RADIUS; dz <= FAILED_ROUTE_LOCAL_RADIUS; dz++)
            {
                for (int dx = -FAILED_ROUTE_LOCAL_RADIUS; dx <= FAILED_ROUTE_LOCAL_RADIUS; dx++)
                {
                    final int x = failureX + dx;
                    final int z = failureZ + dz;
                    if (x < 0 || z < 0 || x >= width || z >= depth)
                    {
                        continue;
                    }
                    final int cell = index(x, z, width);
                    if (cell == goal)
                    {
                        continue;
                    }
                    final double distance = Math.hypot(dx, dz);
                    if (distance <= FAILED_ROUTE_LOCAL_RADIUS)
                    {
                        routePenalty[cell] += FAILED_ROUTE_LOCAL_PENALTY
                            * (1d - distance / (FAILED_ROUTE_LOCAL_RADIUS + 1d));
                    }
                }
            }
        }

        private List<Vec> snapRouteToValleys(List<Vec> route)
        {
            if (route.size() < 3)
            {
                return new ArrayList<>(route);
            }
            final List<Vec> snapped = new ArrayList<>(route.size());
            snapped.add(route.get(0));
            for (int i = 1; i < route.size() - 1; i++)
            {
                final Vec previous = route.get(i - 1);
                final Vec current = route.get(i);
                final Vec next = route.get(i + 1);
                final Vec direction = normalizedDirection(previous, next);
                if (vectorLength(direction) < 1.0e-6d)
                {
                    snapped.add(current);
                    continue;
                }

                final Vec perpendicular = new Vec(-direction.z(), direction.x());
                Vec best = current;
                double bestHeight = heights.sample(Mth.floor(current.x()), Mth.floor(current.z()));
                for (double offset = -VALLEY_SNAP_RADIUS; offset <= VALLEY_SNAP_RADIUS + 1.0e-9d; offset += VALLEY_SNAP_STEP)
                {
                    final Vec candidate = add(current, scale(perpendicular, offset));
                    if (routeObstacles.blocks(
                        edge,
                        candidate.x(),
                        candidate.z(),
                        TFC_RIVER_ROUTE_CLEARANCE
                    ))
                    {
                        continue;
                    }
                    final double height = heights.sample(Mth.floor(candidate.x()), Mth.floor(candidate.z()));
                    if (height < bestHeight - 1.0e-6d
                        || Math.abs(height - bestHeight) <= 1.0e-6d && Math.abs(offset) < distance(best, current))
                    {
                        best = candidate;
                        bestHeight = height;
                    }
                }
                addIfDifferent(snapped, best);
            }
            addIfDifferent(snapped, route.get(route.size() - 1));
            return snapped;
        }

        /**
         * Fine validation after valley snapping and corner smoothing. The
         * eight-block search mask prevents broad crossings; this segment walk
         * catches a curve that clips a protected native wet channel between
         * two grid centers.
         */
        @Nullable
        private Vec firstRouteObstacle(List<Vec> route)
        {
            if (route.size() < 2)
            {
                return null;
            }
            for (int segment = 0; segment < route.size() - 1; segment++)
            {
                final Vec from = route.get(segment);
                final Vec to = route.get(segment + 1);
                final double length = distance(from, to);
                final int samples = Math.max(1, Mth.ceil(length / 1.5d));
                for (int sample = segment == 0 ? 0 : 1; sample <= samples; sample++)
                {
                    final double delta = sample / (double) samples;
                    final Vec point = new Vec(
                        Mth.lerp(delta, from.x(), to.x()),
                        Mth.lerp(delta, from.z(), to.z())
                    );
                    if (routeObstacles.blocks(
                        edge,
                        point.x(),
                        point.z(),
                        TFC_RIVER_ROUTE_CLEARANCE
                    ))
                    {
                        return point;
                    }
                }
            }
            return null;
        }

        private int chooseSource(
            int nominalX,
            int nominalZ,
            int width,
            int depth,
            double[] terrain,
            boolean[] blocked
        )
        {
            final double downstreamX = drainX - sourceX;
            final double downstreamZ = drainZ - sourceZ;
            final double downstreamLength = Math.hypot(downstreamX, downstreamZ);
            if (downstreamLength < 1.0e-6d)
            {
                return chooseNearbySource(nominalX, nominalZ, width, depth, terrain, blocked);
            }

            // Extend beyond TFC's nominal leaf source. This turns the old
            // several-block-wide endpoint into a confluence and gives the new
            // terrain-following creek room to taper to a one-block spring.
            final double upstreamX = -downstreamX / downstreamLength;
            final double upstreamZ = -downstreamZ / downstreamLength;
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int z = 1; z < depth - 1; z++)
            {
                for (int x = 1; x < width - 1; x++)
                {
                    final double deltaX = (x - nominalX) * (double) SAMPLE_STEP;
                    final double deltaZ = (z - nominalZ) * (double) SAMPLE_STEP;
                    final double along = deltaX * upstreamX + deltaZ * upstreamZ;
                    if (along < SOURCE_EXTENSION_MIN || along > SOURCE_EXTENSION_MAX)
                    {
                        continue;
                    }
                    final double lateral = Math.abs(deltaX * upstreamZ - deltaZ * upstreamX);
                    if (lateral > SOURCE_EXTENSION_LATERAL)
                    {
                        continue;
                    }

                    final int candidate = index(x, z, width);
                    if (blocked[candidate]
                        || terrain[candidate] < seaLevel - 1d + CENTER_SURFACE_INSET)
                    {
                        continue;
                    }
                    final double valleyBonus = valleyBonus(x, z, width, depth, terrain);
                    final double score = terrain[candidate]
                        + valleyBonus * 2.2d
                        + along * 0.012d
                        - lateral * 0.035d;
                    if (score > bestScore)
                    {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
            return best >= 0 ? best : chooseNearbySource(nominalX, nominalZ, width, depth, terrain, blocked);
        }

        private int chooseNearbySource(
            int nominalX,
            int nominalZ,
            int width,
            int depth,
            double[] terrain,
            boolean[] blocked
        )
        {
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int dz = -4; dz <= 4; dz++)
            {
                for (int dx = -4; dx <= 4; dx++)
                {
                    final int x = nominalX + dx;
                    final int z = nominalZ + dz;
                    if (x <= 0 || z <= 0 || x >= width - 1 || z >= depth - 1)
                    {
                        continue;
                    }
                    final int candidate = index(x, z, width);
                    if (blocked[candidate])
                    {
                        continue;
                    }
                    final double score = terrain[candidate]
                        + valleyBonus(x, z, width, depth, terrain) * 2.2d
                        - Math.hypot(dx, dz) * 0.35d;
                    if (score > bestScore)
                    {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
            return best;
        }

        private static double valleyBonus(int x, int z, int width, int depth, double[] terrain)
        {
            double neighborAverage = 0d;
            int count = 0;
            for (int nz = -1; nz <= 1; nz++)
            {
                for (int nx = -1; nx <= 1; nx++)
                {
                    final int neighborX = x + nx;
                    final int neighborZ = z + nz;
                    if ((nx != 0 || nz != 0)
                        && neighborX >= 0 && neighborZ >= 0
                        && neighborX < width && neighborZ < depth)
                    {
                        neighborAverage += terrain[index(neighborX, neighborZ, width)];
                        count++;
                    }
                }
            }
            return count == 0 ? 0d : Math.max(0d, neighborAverage / count - terrain[index(x, z, width)]);
        }

        @Nullable
        private int[] search(
            int start,
            int goal,
            int minCellX,
            int minCellZ,
            int width,
            int depth,
            double[] terrain,
            boolean[] blocked
        )
        {
            final int size = width * depth;
            final double[] cost = new double[size];
            final int[] parent = new int[size];
            final boolean[] closed = new boolean[size];
            Arrays.fill(cost, Double.POSITIVE_INFINITY);
            Arrays.fill(parent, -1);

            final int startX = start % width;
            final int startZ = start / width;
            final int goalX = goal % width;
            final int goalZ = goal / width;
            final double directDistance = Math.max(1d, Math.hypot(goalX - startX, goalZ - startZ));
            final double sourceHeight = terrain[start];
            final double goalHeight = terrain[goal];
            final PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::score));
            cost[start] = 0d;
            open.add(new SearchNode(start, heuristic(startX, startZ, goalX, goalZ)));

            final int[] directions = {-1, -1, 0, -1, 1, -1, -1, 0, 1, 0, -1, 1, 0, 1, 1, 1};
            while (!open.isEmpty())
            {
                final SearchNode node = open.poll();
                final int current = node.index();
                if (closed[current])
                {
                    continue;
                }
                closed[current] = true;
                if (current == goal)
                {
                    return parent;
                }

                final int currentX = current % width;
                final int currentZ = current / width;
                for (int i = 0; i < directions.length; i += 2)
                {
                    final int nextX = currentX + directions[i];
                    final int nextZ = currentZ + directions[i + 1];
                    if (nextX < 0 || nextZ < 0 || nextX >= width || nextZ >= depth)
                    {
                        continue;
                    }
                    final int next = index(nextX, nextZ, width);
                    if (closed[next] || blocked[next] && next != goal)
                    {
                        continue;
                    }
                    if (next != goal && terrain[next] < seaLevel - 1d + CENTER_SURFACE_INSET)
                    {
                        continue;
                    }

                    final double step = directions[i] == 0 || directions[i + 1] == 0 ? 1d : Math.sqrt(2d);
                    final double deltaHeight = terrain[next] - terrain[current];
                    final double uphillDownstream = Math.max(0d, deltaHeight);
                    final double progress = Mth.clamp(
                        1d - Math.hypot(goalX - nextX, goalZ - nextZ) / directDistance,
                        0d,
                        1d
                    );
                    final double expectedHeight = Mth.lerp(progress, sourceHeight, goalHeight);
                    final double highGround = Math.max(0d, terrain[next] - expectedHeight);
                    final double blockX = (minCellX + nextX) * (double) SAMPLE_STEP;
                    final double blockZ = (minCellZ + nextZ) * (double) SAMPLE_STEP;
                    final double corridor = Math.max(0d, corridorDistance(blockX, blockZ) - 24d) / 64d;
                    final double meander = meanderCost(blockX, blockZ);
                    final double nextCost = cost[current] + step * (
                        1d
                            + uphillDownstream * 26d
                            + Math.abs(deltaHeight) * 0.35d
                            + highGround * 0.65d
                            + corridor * corridor * 0.9d
                            + meander
                    );
                    if (nextCost < cost[next])
                    {
                        cost[next] = nextCost;
                        parent[next] = current;
                        open.add(new SearchNode(next, nextCost + heuristic(nextX, nextZ, goalX, goalZ)));
                    }
                }
            }
            return null;
        }

        private double corridorDistance(double x, double z)
        {
            if (edge != null)
            {
                return Math.sqrt(edge.fractal().intersectDistance(
                    x / Units.GRID_WIDTH_IN_BLOCK,
                    z / Units.GRID_WIDTH_IN_BLOCK
                )) * Units.GRID_WIDTH_IN_BLOCK;
            }
            return Math.sqrt(project(sourceX, sourceZ, drainX, drainZ, x, z).distanceSq());
        }

        private double meanderCost(double x, double z)
        {
            final double phaseX = (seed & 0xffffL) * 0.0017d;
            final double phaseZ = ((seed >>> 16) & 0xffffL) * 0.0013d;
            final double wave = Math.sin(x * 0.024d + phaseX) * Math.sin(z * 0.021d - phaseZ);
            return 0.32d * (1d + wave);
        }

        private double bankCapacity(int index, double[] x, double[] z, double[] radius)
        {
            final int before = Math.max(0, index - 1);
            final int after = Math.min(x.length - 1, index + 1);
            final double dx = x[after] - x[before];
            final double dz = z[after] - z[before];
            final double length = Math.hypot(dx, dz);
            if (length < 1.0e-6d)
            {
                return Double.NEGATIVE_INFINITY;
            }
            final double offset = radius[index] * 0.9d + 0.75d;
            final double nx = -dz / length * offset;
            final double nz = dx / length * offset;
            final double left = heights.sample(Mth.floor(x[index] + nx), Mth.floor(z[index] + nz));
            final double right = heights.sample(Mth.floor(x[index] - nx), Mth.floor(z[index] - nz));
            return Math.min(left, right) - BANK_FREEBOARD;
        }
    }

    private static final class Route
    {
        private final double[] x;
        private final double[] z;
        private final double[] terrainY;
        private final double[] waterY;
        private final double[] radius;
        private final double[] distance;
        private final double totalLength;
        private final double minX;
        private final double minZ;
        private final double maxX;
        private final double maxZ;
        private final boolean receiverMouth;
        @Nullable private final ReceiverAlignment receiverAlignment;
        private final double receiverWaterY;
        private final double mouthTransitionLength;
        /** Per-node flag: this node's water runs inside a covered rock tunnel. */
        private final boolean[] subterranean;
        /** Per-node rock ceiling of a subterranean node; zero for surface nodes. */
        private final double[] tunnelCeilingY;
        private final List<SubterraneanRun> subterraneanRuns;
        /** This creek's own seed; shapes the covered cavity and the cave mouth. */
        private final long caveSeed;
        private final Set<Long> turnConnectorColumns;
        private final Map<Long, int[]> segmentsByChunk;
        private final Set<Long> spatialChunks;
        private final List<JunctionFlowAnchor> junctionFlowAnchors;
        @Nullable private final JunctionFlowTransition[] junctionFlowTransitions;

        private Route(
            double[] x,
            double[] z,
            double[] terrainY,
            double[] waterY,
            double[] radius,
            double[] distance,
            double totalLength,
            boolean receiverMouth,
            @Nullable ReceiverAlignment receiverAlignment,
            double receiverWaterY,
            double mouthTransitionLength,
            boolean[] subterranean,
            double[] tunnelCeilingY,
            List<SubterraneanRun> subterraneanRuns,
            long caveSeed
        )
        {
            this(
                x,
                z,
                terrainY,
                waterY,
                radius,
                distance,
                totalLength,
                receiverMouth,
                receiverAlignment,
                receiverWaterY,
                mouthTransitionLength,
                subterranean,
                tunnelCeilingY,
                subterraneanRuns,
                caveSeed,
                List.of()
            );
        }

        private Route(
            double[] x,
            double[] z,
            double[] terrainY,
            double[] waterY,
            double[] radius,
            double[] distance,
            double totalLength,
            boolean receiverMouth,
            @Nullable ReceiverAlignment receiverAlignment,
            double receiverWaterY,
            double mouthTransitionLength,
            boolean[] subterranean,
            double[] tunnelCeilingY,
            List<SubterraneanRun> subterraneanRuns,
            long caveSeed,
            List<JunctionFlowAnchor> junctionFlowAnchors
        )
        {
            this.x = x;
            this.z = z;
            this.terrainY = terrainY;
            this.waterY = waterY;
            this.radius = radius;
            this.distance = distance;
            this.totalLength = totalLength;
            this.minX = minimum(x);
            this.minZ = minimum(z);
            this.maxX = maximum(x);
            this.maxZ = maximum(z);
            this.receiverMouth = receiverMouth;
            this.receiverAlignment = receiverAlignment;
            this.receiverWaterY = receiverWaterY;
            this.mouthTransitionLength = mouthTransitionLength;
            this.subterranean = subterranean;
            this.tunnelCeilingY = tunnelCeilingY;
            this.subterraneanRuns = subterraneanRuns;
            this.caveSeed = caveSeed;
            this.turnConnectorColumns = rasterizeTurnConnectors(x, z);
            this.segmentsByChunk = indexSegmentsByChunk(x, z, radius);
            this.spatialChunks = indexRouteChunks(x, z, SPATIAL_INDEX_MARGIN);
            this.junctionFlowAnchors = List.copyOf(junctionFlowAnchors);
            if (junctionFlowAnchors.isEmpty())
            {
                this.junctionFlowTransitions = null;
            }
            else
            {
                this.junctionFlowTransitions = new JunctionFlowTransition[junctionFlowAnchors.size()];
                for (int index = 0; index < junctionFlowAnchors.size(); index++)
                {
                    final JunctionFlowAnchor anchor = junctionFlowAnchors.get(index);
                    junctionFlowTransitions[index] = new JunctionFlowTransition(
                        nearestProjectionLinear(anchor.point()).along(),
                        anchor.flow()
                    );
                }
                Arrays.sort(junctionFlowTransitions, Comparator.comparingDouble(JunctionFlowTransition::along));
            }
        }

        private boolean suppresses(RiverEdge edge, int blockX, int blockZ)
        {
            return receiverAlignment != null && receiverAlignment.suppresses(edge, blockX, blockZ);
        }

        /**
         * Junction and shared-drainage rebuilds create a new node list for the
         * same route. The subterranean section is a step function along the
         * route, so it is re-sampled by along-distance instead of being dropped.
         */
        private static boolean[] resampleSubterranean(
            boolean[] source,
            double[] sourceDistance,
            double[] targetDistance,
            int size
        )
        {
            final boolean[] result = new boolean[size];
            for (int i = 0; i < size; i++)
            {
                result[i] = source[nearestNodeIndex(sourceDistance, targetDistance[i])];
            }
            return result;
        }

        private static double[] resampleCeiling(
            double[] source,
            double[] sourceDistance,
            double[] targetDistance,
            int size
        )
        {
            final double[] result = new double[size];
            for (int i = 0; i < size; i++)
            {
                result[i] = source[nearestNodeIndex(sourceDistance, targetDistance[i])];
            }
            return result;
        }

        private static int nearestNodeIndex(double[] distance, double target)
        {
            final int last = distance.length - 1;
            if (target <= distance[0])
            {
                return 0;
            }
            if (target >= distance[last])
            {
                return last;
            }
            int lowerBound = 1;
            int upperBound = last;
            while (lowerBound < upperBound)
            {
                final int middle = (lowerBound + upperBound) >>> 1;
                if (distance[middle] < target)
                {
                    lowerBound = middle + 1;
                }
                else
                {
                    upperBound = middle;
                }
            }
            final int upper = lowerBound;
            return target - distance[upper - 1] <= distance[upper] - target
                ? upper - 1
                : upper;
        }

        @Nullable
        private Sample sample(int blockX, int blockZ, double ambientHeight)
        {
            // Once planning has completed, the route's own bounds replace the
            // Headwater's coarse TFC-edge bounds. Keep this cheap rejection so
            // direct edge lookups outside the route do not fall back to a
            // full linear segment scan.
            if (blockX < minX - SPATIAL_INDEX_MARGIN || blockX > maxX + SPATIAL_INDEX_MARGIN
                || blockZ < minZ - SPATIAL_INDEX_MARGIN || blockZ > maxZ + SPATIAL_INDEX_MARGIN)
            {
                return null;
            }
            final RouteProjection nearest = nearestIndexedProjection(new Vec(blockX, blockZ));
            if (nearest.segment() < 0)
            {
                return null;
            }
            final double bestDistanceSq = nearest.distanceSq();
            final int bestIndex = nearest.segment();
            final double bestDelta = nearest.delta();

            final double localRadius = Mth.lerp(bestDelta, radius[bestIndex], radius[bestIndex + 1]);
            final double rawNormalizedDistanceSq = bestDistanceSq / (localRadius * localRadius);
            final double localWaterY = Mth.lerp(bestDelta, waterY[bestIndex], waterY[bestIndex + 1]);
            final double along = Mth.lerp(bestDelta, distance[bestIndex], distance[bestIndex + 1]);
            final double downstreamWaterY = sampleWaterYAtAlong(along + 1d);
            final double downstreamWaterDrop = Math.max(0d, localWaterY - downstreamWaterY);
            final double distanceToOutlet = totalLength - along;
            final boolean retainedReceiverJoin = receiverAlignment != null
                && receiverAlignment.retainsNativeReceiver();
            // The final tangent only describes space beyond the actual route
            // endpoint. A long meander can put a perfectly valid upstream
            // segment in that tangent's forward half-plane, so apply the
            // rejection only when this column's closest projection really is
            // the clamped end of the final segment.
            if (receiverAlignment != null
                && bestIndex == x.length - 2
                && bestDelta >= 1d - 1.0e-9d
                && extendsPastOutlet(blockX, blockZ))
            {
                traceTargetSample(blockX, blockZ, ambientHeight, localWaterY, rawNormalizedDistanceSq, Double.POSITIVE_INFINITY, false, "past_outlet");
                return null;
            }
            final double mouthNormalizedDistanceSq = receiverAlignment == null || retainedReceiverJoin
                ? rawNormalizedDistanceSq
                : receiverAlignment.mouthNormalizedDistanceSq(
                    rawNormalizedDistanceSq,
                    blockX,
                    blockZ,
                    distanceToOutlet,
                    mouthTransitionLength
                );
            // Do not four-connect every diagonal step. Only a short diagonal
            // transition between perpendicular cardinal runs receives its
            // inside-corner cells, so a real bend stays connected without
            // turning an entire one-block diagonal creek into a two-block cut.
            // The aligned mouth uses its receiver-aware distance field, so
            // connector cells must not override that shape.
            final boolean turnConnector = (receiverAlignment == null
                || retainedReceiverJoin
                || distanceToOutlet >= MOUTH_FAN_LENGTH)
                && turnConnectorColumns.contains(columnKey(blockX, blockZ));
            final double normalizedDistanceSq = turnConnector
                ? Math.min(mouthNormalizedDistanceSq, NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ * 0.95d)
                : mouthNormalizedDistanceSq;
            if (normalizedDistanceSq > MAX_INFLUENCE_SQ)
            {
                traceTargetSample(blockX, blockZ, ambientHeight, Double.NaN, rawNormalizedDistanceSq, normalizedDistanceSq, turnConnector, "outside");
                return null;
            }
            final double terrainCutLength = receiverAlignment == null
                ? alignedMouthWaterCutLength(localRadius)
                : receiverAlignment.terrainCutLength(localRadius);
            final double waterCutLength = receiverAlignment == null
                ? terrainCutLength
                : receiverAlignment.waterCutLength(localRadius);
            final boolean fillAllowed = !receiverMouth || distanceToOutlet > terrainCutLength;
            // The retained river has no replacement tail which can take over a
            // fallback feeder's last wet cells. Keep that feeder wet through
            // the contact point; only complete replacement routes may hand
            // their water corridor off before the topology endpoint.
            final boolean waterAllowed = retainedReceiverJoin
                || !receiverMouth
                || distanceToOutlet > waterCutLength;
            // Keep the generated flowing-water corridor as wide as the creek
            // cut. Static source water is retracted independently by
            // mouthSourceWaterAllowed(); shrinking the dynamic core would
            // leave a carved but dry annulus at real joins.
            final double waterCoreRadiusSq = NTERiverHydrology.SUPPLEMENTAL_WATER_CORE_RADIUS_SQ;
            final double mouthWaterDrop = receiverAlignment == null || retainedReceiverJoin
                ? 0d
                : mouthWaterDrop(distanceToOutlet, localWaterY, receiverWaterY, mouthTransitionLength);
            final double downstreamDistanceToOutlet = Math.max(0d, totalLength - (along + 1d));
            final double downstreamMouthWaterDrop = receiverAlignment == null || retainedReceiverJoin
                ? 0d
                : mouthWaterDrop(
                    downstreamDistanceToOutlet,
                    downstreamWaterY,
                    receiverWaterY,
                    mouthTransitionLength
                );
            final double secondDownstreamWaterY = sampleWaterYAtAlong(along + 2d);
            final double secondDownstreamDistanceToOutlet = Math.max(0d, totalLength - (along + 2d));
            final double secondDownstreamMouthWaterDrop = receiverAlignment == null || retainedReceiverJoin
                ? 0d
                : mouthWaterDrop(
                    secondDownstreamDistanceToOutlet,
                    secondDownstreamWaterY,
                    receiverWaterY,
                    mouthTransitionLength
                );
            final double plannedWaterY = localWaterY - mouthWaterDrop;
            final double downstreamPlannedWaterY = downstreamWaterY - downstreamMouthWaterDrop;
            final double secondDownstreamPlannedWaterY = secondDownstreamWaterY - secondDownstreamMouthWaterDrop;
            final double effectiveDownstreamWaterDrop = Math.max(
                0d,
                plannedWaterY - downstreamPlannedWaterY
            );
            final double upstreamAlong = Math.max(0d, along - 1d);
            final double upstreamWaterY = sampleWaterYAtAlong(upstreamAlong);
            // The small fan descent is an artificial profile adaptation, not
            // a terrain waterfall. Treating every quantized fan step as a
            // landing made generation-time spill baking restore the upper
            // water layer which the profile had deliberately lowered. Real
            // route drops still pre-bake their complete waterfalls.
            final boolean waterfallLanding = naturalWaterfallLanding(upstreamWaterY, localWaterY);
            final double extraIncision = receiverAlignment == null || retainedReceiverJoin
                ? 0d
                : mouthBankIncision(mouthWaterDrop, normalizedDistanceSq);
            final double bankFillWeight = receiverAlignment == null
                ? 1d
                : mouthBankFillWeight(distanceToOutlet, mouthTransitionLength);
            final double receiverBlendWeight = receiverAlignment == null || retainedReceiverJoin
                ? 0d
                : mouthReceiverBlendWeight(rawNormalizedDistanceSq, distanceToOutlet, mouthTransitionLength);
            final double receiverBedBlendWeight = receiverAlignment == null || retainedReceiverJoin
                ? 0d
                : mouthReceiverBedBlendWeight(distanceToOutlet, waterCutLength);
            final Vec smoothedStreamDirection = flowDirectionAtAlong(along);
            final Vec streamDirection = vectorLength(smoothedStreamDirection) < 1.0e-6d
                ? normalizedDirection(
                    new Vec(x[bestIndex], z[bestIndex]),
                    new Vec(x[bestIndex + 1], z[bestIndex + 1])
                )
                : smoothedStreamDirection;
            final Vec flowDirection = receiverAlignment == null || retainedReceiverJoin
                ? streamDirection
                : receiverAlignment.blendedFlowDirection(
                    streamDirection,
                    blockX,
                    blockZ,
                    distanceToOutlet,
                    mouthTransitionLength
                );
            final double angle = Mth.atan2(-flowDirection.z(), flowDirection.x());
            final Flow flow = junctionFlowAtAlong(along, Flow.fromAngle(angle));
            // The route origin is the spring itself. Always keep exactly that
            // center column as a source so a steep first step cannot leave the
            // complete headwater supplied only by transient flowing water.
            // Every neighboring and downstream column retains the ordinary
            // slope, waterfall and receiver-mouth source rules below.
            final boolean sourceAnchor = blockX == Mth.floor(x[0]) && blockZ == Mth.floor(z[0]);
            final boolean sourceWaterAllowed = sourceAnchor || waterAllowed
                && plannedSourceWaterAllowed(effectiveDownstreamWaterDrop)
                && plannedSourceWaterAllowed(plannedWaterY, downstreamPlannedWaterY, secondDownstreamPlannedWaterY)
                && !waterfallLanding
                && (receiverAlignment == null
                    || retainedReceiverJoin
                    || mouthSourceWaterAllowed(distanceToOutlet, waterCutLength, mouthWaterDrop));
            final int nearestNode = bestDelta < 0.5d ? bestIndex : bestIndex + 1;
            if (subterranean[nearestNode])
            {
                // A covered section is the open-air creek plus a roof, not a second kind of
                // creek: the cross-section, the receiver transition and its weights are the
                // very same values, and the tunnel only adds its ceiling, its noise and the
                // graded mouth cut. Anything else (bed, water depth, hand-over) is adapted
                // downstream from these fields instead of being computed twice.
                traceTargetSample(blockX, blockZ, ambientHeight, localWaterY, rawNormalizedDistanceSq, normalizedDistanceSq, turnConnector, "subterranean");
                return new Sample(
                    localWaterY,
                    normalizedDistanceSq,
                    localRadius,
                    extraIncision,
                    mouthWaterDrop,
                    bankFillWeight,
                    waterCoreRadiusSq,
                    fillAllowed,
                    waterAllowed,
                    true,
                    receiverBlendWeight,
                    receiverBedBlendWeight,
                    waterfallLanding,
                    along / totalLength < 0.15d,
                    true,
                    tunnelCeilingY[nearestNode],
                    caveSeed,
                    mouthCutAt(
                        caveSeed,
                        subterraneanRuns,
                        along,
                        localRadius,
                        terrainY[nearestNode],
                        // The covered profile's water is the already descended one, so the
                        // graded mouth must take its floor from that same level: anchoring it
                        // to the pre-descent water left the fresh cut sitting the whole
                        // receiver drop above the water actually flowing through the mouth.
                        plannedWaterY,
                        normalizedDistanceSq,
                        blockX,
                        blockZ
                    ),
                    flow
                );
            }
            traceTargetSample(blockX, blockZ, ambientHeight, localWaterY, rawNormalizedDistanceSq, normalizedDistanceSq, turnConnector, "accepted");
            return new Sample(
                localWaterY,
                normalizedDistanceSq,
                localRadius,
                extraIncision,
                mouthWaterDrop,
                bankFillWeight,
                waterCoreRadiusSq,
                fillAllowed,
                waterAllowed,
                sourceWaterAllowed,
                receiverBlendWeight,
                receiverBedBlendWeight,
                waterfallLanding,
                along / totalLength < 0.15d,
                false,
                0d,
                0L,
                0d,
                flow
            );
        }

        private double sampleWaterYAtAlong(double targetAlong)
        {
            if (targetAlong <= 0d)
            {
                return waterY[0];
            }
            if (targetAlong >= totalLength)
            {
                return waterY[waterY.length - 1];
            }
            // Lower-bound search selects the same first distance >= target as
            // the former linear walk. Keep the old interpolation expression
            // below so exact route nodes retain bit-identical double results.
            int lowerBound = 1;
            int upperBound = distance.length - 1;
            while (lowerBound < upperBound)
            {
                final int middle = (lowerBound + upperBound) >>> 1;
                if (distance[middle] < targetAlong)
                {
                    lowerBound = middle + 1;
                }
                else
                {
                    upperBound = middle;
                }
            }
            final int upper = lowerBound;
            final int lower = upper - 1;
            final double segmentLength = distance[upper] - distance[lower];
            final double delta = segmentLength <= 1.0e-9d
                ? 0d
                : (targetAlong - distance[lower]) / segmentLength;
            return Mth.lerp(delta, waterY[lower], waterY[upper]);
        }

        private boolean extendsPastOutlet(int blockX, int blockZ)
        {
            final int last = x.length - 1;
            return NTEHeadwaterNetwork.extendsPastOutlet(
                x[last - 1], z[last - 1], x[last], z[last], blockX, blockZ
            );
        }

        private static void traceTargetSample(
            int blockX,
            int blockZ,
            double ambientHeight,
            double waterY,
            double rawNormalizedDistanceSq,
            double normalizedDistanceSq,
            boolean turnConnector,
            String result
        )
        {
            if (TRACE
                && blockX == Integer.getInteger("tfe.debug.traceX", Integer.MIN_VALUE)
                && blockZ == Integer.getInteger("tfe.debug.traceZ", Integer.MIN_VALUE))
            {
                LOGGER.info(
                    "[TFE][HeadwaterTargetSample] x={} z={} ambient={} water={} rawNorm={} norm={} connector={} result={}",
                    blockX,
                    blockZ,
                    ambientHeight,
                    waterY,
                    rawNormalizedDistanceSq,
                    normalizedDistanceSq,
                    turnConnector,
                    result
                );
            }
        }

        private List<DiagnosticPoint> diagnostics()
        {
            final List<DiagnosticPoint> points = new ArrayList<>(x.length);
            for (int i = 0; i < x.length; i++)
            {
                points.add(new DiagnosticPoint(
                    x[i],
                    z[i],
                    terrainY[i],
                    waterY[i],
                    radius[i],
                    subterranean[i],
                    tunnelCeilingY[i]
                ));
            }
            return List.copyOf(points);
        }

        private String summary()
        {
            final int middle = x.length / 2;
            int tunnelPoints = 0;
            for (boolean value : subterranean)
            {
                if (value)
                {
                    tunnelPoints++;
                }
            }
            return String.format(
                "start=(%.1f,%.1f,water=%.1f) middle=(%.1f,%.1f,water=%.1f) end=(%.1f,%.1f,water=%.1f) turnConnectors=%d tunnel=%d%s",
                x[0], z[0], waterY[0],
                x[middle], z[middle], waterY[middle],
                x[x.length - 1], z[z.length - 1], waterY[waterY.length - 1],
                turnConnectorColumns.size(),
                tunnelPoints,
                tracedGeometry()
            );
        }

        private boolean boundsOverlap(Route other, double margin)
        {
            return minX - margin <= other.maxX
                && maxX + margin >= other.minX
                && minZ - margin <= other.maxZ
                && maxZ + margin >= other.minZ;
        }

        private double sampleRadiusAtAlong(double targetAlong)
        {
            return sampleAtAlong(radius, targetAlong);
        }

        private Vec pointAtAlong(double targetAlong)
        {
            if (targetAlong <= 0d)
            {
                return new Vec(x[0], z[0]);
            }
            if (targetAlong >= totalLength)
            {
                return new Vec(x[x.length - 1], z[z.length - 1]);
            }
            final int found = Arrays.binarySearch(distance, targetAlong);
            if (found >= 0)
            {
                return new Vec(x[found], z[found]);
            }
            final int upper = -found - 1;
            final int lower = upper - 1;
            final double segmentLength = distance[upper] - distance[lower];
            final double delta = segmentLength <= 1.0e-9d
                ? 0d
                : (targetAlong - distance[lower]) / segmentLength;
            return new Vec(
                Mth.lerp(delta, x[lower], x[upper]),
                Mth.lerp(delta, z[lower], z[upper])
            );
        }

        /**
         * Samples a centered secant around the projected route position. The
         * route itself is already rounded, but assigning one flow to each raw
         * segment made a whole water strip snap direction at segment borders.
         * A short physical window exposes the intermediate 16-way TFC flow
         * states without changing the channel geometry or its dynamic water.
         */
        private Vec flowDirectionAtAlong(double targetAlong)
        {
            final double before = Math.max(0d, targetAlong - FLOW_DIRECTION_SAMPLE_RADIUS);
            final double after = Math.min(totalLength, targetAlong + FLOW_DIRECTION_SAMPLE_RADIUS);
            return normalizedDirection(pointAtAlong(before), pointAtAlong(after));
        }

        private Vec directionAtAlong(double targetAlong)
        {
            final double before = Math.max(0d, targetAlong - 1.5d);
            final double after = Math.min(totalLength, targetAlong + 1.5d);
            return normalizedDirection(pointAtAlong(before), pointAtAlong(after));
        }

        /**
         * TFC river flow interpolates between its sixteen discrete directions.
         * A rebuilt junction needs the same local transition, but only around
         * its explicit shared node; ordinary bends already use the centered
         * secant above and must remain unchanged.
         */
        private Flow junctionFlowAtAlong(double targetAlong, Flow routeFlow)
        {
            if (junctionFlowTransitions == null)
            {
                return routeFlow;
            }
            JunctionFlowTransition nearest = null;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (JunctionFlowTransition transition : junctionFlowTransitions)
            {
                final double distance = Math.abs(targetAlong - transition.along());
                if (distance < nearestDistance && distance < JUNCTION_FLOW_TRANSITION_RADIUS)
                {
                    nearest = transition;
                    nearestDistance = distance;
                }
            }
            if (nearest == null)
            {
                return routeFlow;
            }

            final float weight = (float) smootherStep(
                1d - nearestDistance / JUNCTION_FLOW_TRANSITION_RADIUS
            );
            final Flow blended = Flow.lerp(routeFlow, nearest.flow(), weight);
            // Opposite directions have no unique midpoint and TFC represents
            // that exact tie as NONE. A junction is still directional water,
            // so keep the nearer endpoint instead of erasing its Flow.
            return blended == Flow.NONE ? weight < 0.5f ? routeFlow : nearest.flow() : blended;
        }

        @Nullable
        private Flow junctionAnchorFlowAt(Vec junction)
        {
            for (JunctionFlowAnchor anchor : junctionFlowAnchors)
            {
                if (distanceSq(anchor.point(), junction) <= 1.0e-6d)
                {
                    return anchor.flow();
                }
            }
            return null;
        }

        private RouteProjection nearestProjection(Vec point)
        {
            return nearestProjectionLinear(point);
        }

        private RouteProjection nearestIndexedProjection(Vec point)
        {
            final int[] candidates = segmentsByChunk.get(chunkKey(Mth.floor(point.x()) >> 4, Mth.floor(point.z()) >> 4));
            return candidates == null ? nearestProjectionLinear(point) : nearestProjection(point, candidates);
        }

        private RouteProjection nearestProjectionLinear(Vec point)
        {
            double bestDistanceSq = Double.POSITIVE_INFINITY;
            int bestSegment = -1;
            double bestDelta = 0d;
            double bestAlong = 0d;
            for (int segment = 0; segment < x.length - 1; segment++)
            {
                final Projection projection = project(
                    x[segment], z[segment], x[segment + 1], z[segment + 1], point.x(), point.z()
                );
                if (projection.distanceSq() < bestDistanceSq)
                {
                    bestDistanceSq = projection.distanceSq();
                    bestSegment = segment;
                    bestDelta = projection.delta();
                    bestAlong = Mth.lerp(projection.delta(), distance[segment], distance[segment + 1]);
                }
            }
            return new RouteProjection(bestSegment, bestDelta, bestAlong, bestDistanceSq);
        }

        private RouteProjection nearestProjection(Vec point, int[] candidates)
        {
            double bestDistanceSq = Double.POSITIVE_INFINITY;
            int bestSegment = -1;
            double bestDelta = 0d;
            double bestAlong = 0d;
            for (int segment : candidates)
            {
                final Projection projection = project(
                    x[segment], z[segment], x[segment + 1], z[segment + 1], point.x(), point.z()
                );
                if (projection.distanceSq() < bestDistanceSq)
                {
                    bestDistanceSq = projection.distanceSq();
                    bestSegment = segment;
                    bestDelta = projection.delta();
                    bestAlong = Mth.lerp(projection.delta(), distance[segment], distance[segment + 1]);
                }
            }
            return new RouteProjection(bestSegment, bestDelta, bestAlong, bestDistanceSq);
        }

        private static Map<Long, int[]> indexSegmentsByChunk(double[] x, double[] z, double[] radius)
        {
            final Map<Long, List<Integer>> mutable = new HashMap<>();
            final double margin = maximum(radius) * Math.sqrt(MAX_INFLUENCE_SQ) + 1d;
            for (int segment = 0; segment < x.length - 1; segment++)
            {
                final int minChunkX = Mth.floor(Math.min(x[segment], x[segment + 1]) - margin) >> 4;
                final int maxChunkX = Mth.floor(Math.max(x[segment], x[segment + 1]) + margin) >> 4;
                final int minChunkZ = Mth.floor(Math.min(z[segment], z[segment + 1]) - margin) >> 4;
                final int maxChunkZ = Mth.floor(Math.max(z[segment], z[segment + 1]) + margin) >> 4;
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
                {
                    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
                    {
                        mutable.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(segment);
                    }
                }
            }
            final Map<Long, int[]> indexed = new HashMap<>(mutable.size());
            for (Map.Entry<Long, List<Integer>> entry : mutable.entrySet())
            {
                final int[] segments = new int[entry.getValue().size()];
                for (int index = 0; index < segments.length; index++)
                {
                    segments[index] = entry.getValue().get(index);
                }
                indexed.put(entry.getKey(), segments);
            }
            return indexed;
        }

        private static Set<Long> indexRouteChunks(double[] x, double[] z, double margin)
        {
            final Set<Long> chunks = new HashSet<>();
            for (int segment = 0; segment < x.length - 1; segment++)
            {
                final int minChunkX = Mth.floor(Math.min(x[segment], x[segment + 1]) - margin) >> 4;
                final int maxChunkX = Mth.floor(Math.max(x[segment], x[segment + 1]) + margin) >> 4;
                final int minChunkZ = Mth.floor(Math.min(z[segment], z[segment + 1]) - margin) >> 4;
                final int maxChunkZ = Mth.floor(Math.max(z[segment], z[segment + 1]) + margin) >> 4;
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
                {
                    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
                    {
                        chunks.add(chunkKey(chunkX, chunkZ));
                    }
                }
            }
            return Set.copyOf(chunks);
        }

        private double sampleAtAlong(double[] values, double targetAlong)
        {
            if (targetAlong <= 0d)
            {
                return values[0];
            }
            if (targetAlong >= totalLength)
            {
                return values[values.length - 1];
            }
            final int found = Arrays.binarySearch(distance, targetAlong);
            if (found >= 0)
            {
                return values[found];
            }
            final int upper = -found - 1;
            final int lower = upper - 1;
            final double segmentLength = distance[upper] - distance[lower];
            final double delta = segmentLength <= 1.0e-9d
                ? 0d
                : (targetAlong - distance[lower]) / segmentLength;
            return Mth.lerp(delta, values[lower], values[upper]);
        }

        /**
         * Collapse a sustained high/low double route into one physical
         * corridor. Geometry, water and width are blended into the same
         * shared profile, with short transitions at both ends so neither
         * branch acquires a one-block shelf or width spike.
         */
        private Route withSharedProfile(
            double startAlong,
            double endAlong,
            Route peer,
            double peerStartAlong,
            double peerEndAlong,
            HeightSampler heights
        )
        {
            final List<CorridorPoint> corridor = new ArrayList<>();
            final double spacing = 1.5d;
            for (double along = 0d; along < totalLength; along += spacing)
            {
                corridor.add(sharedCorridorPoint(
                    along,
                    startAlong,
                    endAlong,
                    peer,
                    peerStartAlong,
                    peerEndAlong
                ));
            }
            corridor.add(sharedCorridorPoint(
                totalLength,
                startAlong,
                endAlong,
                peer,
                peerStartAlong,
                peerEndAlong
            ));
            return routeFromCorridor(corridor, heights);
        }

        private CorridorPoint sharedCorridorPoint(
            double along,
            double startAlong,
            double endAlong,
            Route peer,
            double peerStartAlong,
            double peerEndAlong
        )
        {
            final double beforeWeight = smootherStep(
                (along - (startAlong - SHARED_CORRIDOR_TRANSITION_LENGTH))
                    / SHARED_CORRIDOR_TRANSITION_LENGTH
            );
            final double afterWeight = 1d - smootherStep(
                (along - endAlong) / SHARED_CORRIDOR_TRANSITION_LENGTH
            );
            final double sharedWeight = Math.min(beforeWeight, afterWeight);
            if (sharedWeight <= 1.0e-9d)
            {
                return new CorridorPoint(
                    pointAtAlong(along),
                    sampleWaterYAtAlong(along),
                    sampleRadiusAtAlong(along)
                );
            }

            final double interval = Math.max(1.0e-6d, endAlong - startAlong);
            final double progress = Mth.clamp((along - startAlong) / interval, 0d, 1d);
            final double peerAlong = Mth.lerp(progress, peerStartAlong, peerEndAlong);
            final Vec ownPoint = pointAtAlong(along);
            final Vec peerPoint = peer.pointAtAlong(peerAlong);
            final Vec sharedPoint = lerp(ownPoint, peerPoint, 0.5d);
            final double sharedWater = Math.min(
                sampleWaterYAtAlong(along),
                peer.sampleWaterYAtAlong(peerAlong)
            );
            // A sustained shared trunk has one physical cross-section, so
            // both logical owners must use the same larger carrying radius.
            // The half-block cap belongs only to a one-point confluence.
            final double sharedRadius = Math.max(
                sampleRadiusAtAlong(along),
                peer.sampleRadiusAtAlong(peerAlong)
            );
            return new CorridorPoint(
                lerp(ownPoint, sharedPoint, sharedWeight),
                Math.min(sampleWaterYAtAlong(along), Mth.lerp(sharedWeight, sampleWaterYAtAlong(along), sharedWater)),
                Mth.lerp(sharedWeight, sampleRadiusAtAlong(along), sharedRadius)
            );
        }

        private Route routeFromCorridor(List<CorridorPoint> corridor, HeightSampler heights)
        {
            final int size = corridor.size();
            final double[] coordinatedX = new double[size];
            final double[] coordinatedZ = new double[size];
            final double[] coordinatedTerrain = new double[size];
            final double[] coordinatedWater = new double[size];
            final double[] coordinatedRadius = new double[size];
            final double[] coordinatedDistance = new double[size];
            for (int i = 0; i < size; i++)
            {
                final CorridorPoint point = corridor.get(i);
                coordinatedX[i] = point.point().x();
                coordinatedZ[i] = point.point().z();
                coordinatedTerrain[i] = heights.sample(Mth.floor(coordinatedX[i]), Mth.floor(coordinatedZ[i]));
                coordinatedWater[i] = point.waterY();
                coordinatedRadius[i] = point.radius();
                if (i > 0)
                {
                    coordinatedDistance[i] = coordinatedDistance[i - 1]
                        + Math.hypot(coordinatedX[i] - coordinatedX[i - 1], coordinatedZ[i] - coordinatedZ[i - 1]);
                    coordinatedWater[i] = Math.min(coordinatedWater[i - 1], coordinatedWater[i]);
                }
            }
            for (int i = size - 2; i >= 0; i--)
            {
                final double segmentLength = Math.max(1.0e-6d, coordinatedDistance[i + 1] - coordinatedDistance[i]);
                coordinatedWater[i] = Math.min(
                    coordinatedWater[i],
                    coordinatedWater[i + 1] + segmentLength * MAX_CASCADE_SLOPE
                );
            }
            coordinatedWater[size - 1] = waterY[waterY.length - 1];
            return new Route(
                coordinatedX,
                coordinatedZ,
                coordinatedTerrain,
                coordinatedWater,
                coordinatedRadius,
                coordinatedDistance,
                coordinatedDistance[size - 1],
                receiverMouth,
                receiverAlignment,
                receiverWaterY,
                mouthTransitionLength,
                resampleSubterranean(subterranean, distance, coordinatedDistance, size),
                resampleCeiling(tunnelCeilingY, distance, coordinatedDistance, size),
                subterraneanRuns,
                caveSeed,
                junctionFlowAnchors
            );
        }

        private Route withJunction(
            int segment,
            double delta,
            Vec junction,
            double junctionWater,
            double junctionRadius,
            Vec junctionDirection,
            Flow junctionFlow,
            HeightSampler heights
        )
        {
            final double junctionAlong = Mth.lerp(delta, distance[segment], distance[segment + 1]);
            final double rewriteStartAlong = Math.max(0d, junctionAlong - JUNCTION_TANGENT_REWRITE_LENGTH);
            final List<Vec> points = new ArrayList<>(x.length + 16);
            int rewriteStartIndex = 0;
            while (rewriteStartIndex + 1 < distance.length && distance[rewriteStartIndex + 1] < rewriteStartAlong)
            {
                rewriteStartIndex++;
            }
            for (int i = 0; i <= rewriteStartIndex; i++)
            {
                points.add(new Vec(x[i], z[i]));
            }
            final Vec rewriteStart = pointAtAlong(rewriteStartAlong);
            addIfDifferent(points, rewriteStart);
            final Vec incoming = directionAtAlong(rewriteStartAlong);
            final double connectorLength = distance(rewriteStart, junction);
            if (connectorLength > 1.0e-6d)
            {
                final Vec startControl = add(rewriteStart, scale(incoming, connectorLength * 0.38d));
                final Vec endControl = add(junction, scale(junctionDirection, -connectorLength * 0.38d));
                final int steps = Math.max(3, Mth.ceil(connectorLength / 1.5d));
                for (int step = 1; step <= steps; step++)
                {
                    addIfDifferent(points, cubicBezier(
                        rewriteStart,
                        startControl,
                        endControl,
                        junction,
                        step / (double) steps
                    ));
                }
            }
            addIfDifferent(points, junction);
            for (int i = segment + 1; i < x.length; i++)
            {
                addIfDifferent(points, new Vec(x[i], z[i]));
            }

            final int size = points.size();
            final double[] coordinatedX = new double[size];
            final double[] coordinatedZ = new double[size];
            final double[] coordinatedTerrain = new double[size];
            final double[] coordinatedWater = new double[size];
            final double[] coordinatedRadius = new double[size];
            final double[] coordinatedDistance = new double[size];
            for (int i = 0; i < size; i++)
            {
                final Vec point = points.get(i);
                coordinatedX[i] = point.x();
                coordinatedZ[i] = point.z();
                coordinatedTerrain[i] = heights.sample(Mth.floor(point.x()), Mth.floor(point.z()));
                if (i > 0)
                {
                    coordinatedDistance[i] = coordinatedDistance[i - 1]
                        + Math.hypot(coordinatedX[i] - coordinatedX[i - 1], coordinatedZ[i] - coordinatedZ[i - 1]);
                }
                final double originalAlong = nearestProjection(point).along();
                coordinatedWater[i] = sampleWaterYAtAlong(originalAlong);
                coordinatedRadius[i] = sampleRadiusAtAlong(originalAlong);
            }

            int junctionIndex = 0;
            double junctionDistanceSq = Double.POSITIVE_INFINITY;
            for (int i = 0; i < size; i++)
            {
                final double candidateDistanceSq = distanceSq(points.get(i), junction);
                if (candidateDistanceSq < junctionDistanceSq)
                {
                    junctionDistanceSq = candidateDistanceSq;
                    junctionIndex = i;
                }
            }
            coordinatedWater[junctionIndex] = Math.min(coordinatedWater[junctionIndex], junctionWater);
            coordinatedRadius[junctionIndex] = Math.min(
                Math.max(coordinatedRadius[junctionIndex], junctionRadius),
                coordinatedRadius[junctionIndex] + 0.5d
            );
            for (int i = junctionIndex - 1; i >= 0; i--)
            {
                final double segmentLength = Math.max(1.0e-6d, coordinatedDistance[i + 1] - coordinatedDistance[i]);
                final double distanceToJunction = coordinatedDistance[junctionIndex] - coordinatedDistance[i];
                final double transition = smootherStep(
                    1d - distanceToJunction / JUNCTION_PROFILE_TRANSITION_LENGTH
                );
                final double target = junctionWater
                    + distanceToJunction * Math.min(0.35d, MAX_CASCADE_SLOPE);
                coordinatedWater[i] = Math.min(coordinatedWater[i], Mth.lerp(transition, coordinatedWater[i], target));
                coordinatedWater[i] = Math.min(coordinatedWater[i], coordinatedWater[i + 1] + segmentLength * MAX_CASCADE_SLOPE);
            }
            for (int i = junctionIndex + 1; i < size; i++)
            {
                coordinatedWater[i] = Math.min(coordinatedWater[i - 1], coordinatedWater[i]);
            }
            coordinatedWater[size - 1] = waterY[waterY.length - 1];

            final List<JunctionFlowAnchor> coordinatedFlowAnchors = new ArrayList<>(junctionFlowAnchors);
            if (junctionAnchorFlowAt(junction) == null)
            {
                coordinatedFlowAnchors.add(new JunctionFlowAnchor(junction, junctionFlow));
            }
            return new Route(
                coordinatedX,
                coordinatedZ,
                coordinatedTerrain,
                coordinatedWater,
                coordinatedRadius,
                coordinatedDistance,
                coordinatedDistance[size - 1],
                receiverMouth,
                receiverAlignment,
                receiverWaterY,
                mouthTransitionLength,
                resampleSubterranean(subterranean, distance, coordinatedDistance, size),
                resampleCeiling(tunnelCeilingY, distance, coordinatedDistance, size),
                subterraneanRuns,
                caveSeed,
                coordinatedFlowAnchors
            );
        }

        private String tracedGeometry()
        {
            if (!TRACE)
            {
                return "";
            }
            final int targetX = Integer.getInteger("tfe.debug.traceX", Integer.MIN_VALUE);
            final int targetZ = Integer.getInteger("tfe.debug.traceZ", Integer.MIN_VALUE);
            final int traceRadius = Math.max(6, Integer.getInteger("tfe.debug.traceRadius", 8));
            final List<String> nearby = new ArrayList<>();
            for (int i = 0; i < x.length; i++)
            {
                if (Math.abs(x[i] - targetX) <= traceRadius && Math.abs(z[i] - targetZ) <= traceRadius)
                {
                    nearby.add(String.format("%.1f/%.1f", x[i], z[i]));
                }
            }
            if (nearby.isEmpty())
            {
                return "";
            }
            final List<String> connectors = new ArrayList<>();
            for (long key : turnConnectorColumns)
            {
                final int connectorX = (int) key;
                final int connectorZ = (int) (key >>> 32);
                if (Math.abs(connectorX - targetX) <= traceRadius && Math.abs(connectorZ - targetZ) <= traceRadius)
                {
                    connectors.add(connectorX + "/" + connectorZ);
                }
            }
            final Vec target = new Vec(targetX, targetZ);
            final RouteProjection projection = nearestProjection(target);
            final Vec streamDirection = directionAtAlong(projection.along());
            final double distanceToOutlet = totalLength - projection.along();
            if (receiverAlignment == null)
            {
                return String.format(
                    " nearby=%s nearbyConnectors=%s targetAlong=%.2f outletDistance=%.2f streamDir=%.3f/%.3f alignment=none",
                    nearby,
                    connectors,
                    projection.along(),
                    distanceToOutlet,
                    streamDirection.x(),
                    streamDirection.z()
                );
            }
            final double receiverAlong = nearestAlong(receiverAlignment.receiverPath, target);
            final Vec receiverDirection = directionAlong(
                receiverAlignment.receiverPath,
                Math.min(polylineLength(receiverAlignment.receiverPath), receiverAlong + 1.0e-3d)
            );
            return String.format(
                " nearby=%s nearbyConnectors=%s targetAlong=%.2f outletDistance=%.2f streamDir=%.3f/%.3f receiverDir=%.3f/%.3f directionDot=%.3f alignment=%s",
                nearby,
                connectors,
                projection.along(),
                distanceToOutlet,
                streamDirection.x(),
                streamDirection.z(),
                receiverDirection.x(),
                receiverDirection.z(),
                directionDot(streamDirection, receiverDirection),
                receiverAlignment.retainsNativeReceiver() ? "retained" : "replacement"
            );
        }
    }

    private static final class ReceiverAlignment
    {
        @Nullable private final RiverEdge receiver;
        private final List<Vec> receiverPath;
        private final double sourceAlong;
        private final double anchorAlong;
        private final double receiverWidth;

        private ReceiverAlignment(
            @Nullable RiverEdge receiver,
            List<Vec> receiverPath,
            double sourceAlong,
            double anchorAlong,
            double receiverWidth
        )
        {
            this.receiver = receiver;
            this.receiverPath = receiverPath;
            this.sourceAlong = sourceAlong;
            this.anchorAlong = anchorAlong;
            this.receiverWidth = receiverWidth;
        }

        private double terrainCutLength(double localRadius)
        {
            return alignedMouthCutLength(localRadius);
        }

        private double waterCutLength(double localRadius)
        {
            return alignedMouthWaterCutLength(localRadius);
        }

        private double mouthNormalizedDistanceSq(
            double streamNormalizedDistanceSq,
            int blockX,
            int blockZ,
            double distanceToOutlet,
            double transitionLength
        )
        {
            final double receiverRadius = matchedReceiverRadius(receiverWidth);
            final double receiverNormalizedDistanceSq = distanceSqToPolyline(
                receiverPath,
                new Vec(blockX, blockZ)
            ) / (receiverRadius * receiverRadius);
            return mouthGeometryNormalizedDistanceSq(
                streamNormalizedDistanceSq,
                receiverNormalizedDistanceSq,
                distanceToOutlet,
                transitionLength
            );
        }

        private Vec blendedFlowDirection(
            Vec streamDirection,
            int blockX,
            int blockZ,
            double distanceToOutlet,
            double transitionLength
        )
        {
            final Vec point = new Vec(blockX, blockZ);
            final double receiverAlong = nearestAlong(receiverPath, point);
            final Vec receiverDirection = directionAlong(
                receiverPath,
                Math.min(polylineLength(receiverPath), receiverAlong + 1.0e-3d)
            );
            final double weight = smootherStep(
                1d - distanceToOutlet / Math.max(1.0e-6d, transitionLength)
            );
            final Vec blended = new Vec(
                Mth.lerp(weight, streamDirection.x(), receiverDirection.x()),
                Mth.lerp(weight, streamDirection.z(), receiverDirection.z())
            );
            return vectorLength(blended) <= 1.0e-6d
                ? receiverDirection
                : normalizedDirection(new Vec(0d, 0d), blended);
        }

        private boolean suppresses(RiverEdge edge, int blockX, int blockZ)
        {
            if (receiver == null || edge != receiver)
            {
                return false;
            }
            final Vec point = new Vec(blockX, blockZ);
            final double along = nearestAlong(receiverPath, point);
            if (along < sourceAlong - receiverWidth || along > anchorAlong - receiverWidth * 0.35d)
            {
                return false;
            }
            final double distanceSq = distanceSqToPolyline(receiverPath, point);
            final double suppressionRadius = Math.max(
                4d,
                receiverWidth * SOURCE_ALIGNMENT_SUPPRESSION_WIDTH_SCALE
            );
            return distanceSq <= suppressionRadius * suppressionRadius;
        }

        private boolean retainsNativeReceiver()
        {
            return receiver == null;
        }
    }

    private static Set<Long> rasterizeTurnConnectors(double[] x, double[] z)
    {
        final List<GridCell> cells = new ArrayList<>();
        for (int segment = 0; segment < x.length - 1; segment++)
        {
            final double dx = x[segment + 1] - x[segment];
            final double dz = z[segment + 1] - z[segment];
            final int steps = Math.max(1, Mth.ceil(Math.max(Math.abs(dx), Math.abs(dz)) * 2d));
            for (int step = segment == 0 ? 0 : 1; step <= steps; step++)
            {
                final double delta = step / (double) steps;
                final GridCell cell = new GridCell(
                    Mth.floor(Mth.lerp(delta, x[segment], x[segment + 1]) + 0.5d),
                    Mth.floor(Mth.lerp(delta, z[segment], z[segment + 1]) + 0.5d)
                );
                if (cells.isEmpty() || !cells.get(cells.size() - 1).equals(cell))
                {
                    cells.add(cell);
                }
            }
        }

        final Set<Long> connectors = new HashSet<>();
        int edge = 0;
        while (edge < cells.size() - 1)
        {
            final int dx = cells.get(edge + 1).x() - cells.get(edge).x();
            final int dz = cells.get(edge + 1).z() - cells.get(edge).z();
            if (!isDiagonalUnitStep(dx, dz))
            {
                edge++;
                continue;
            }

            final int diagonalStart = edge;
            while (edge < cells.size() - 1)
            {
                final int runDx = cells.get(edge + 1).x() - cells.get(edge).x();
                final int runDz = cells.get(edge + 1).z() - cells.get(edge).z();
                if (!isDiagonalUnitStep(runDx, runDz))
                {
                    break;
                }
                edge++;
            }

            final int diagonalCount = edge - diagonalStart;
            if (diagonalStart == 0 || edge >= cells.size() - 1 || diagonalCount > 4)
            {
                continue;
            }

            final int beforeDx = cells.get(diagonalStart).x() - cells.get(diagonalStart - 1).x();
            final int beforeDz = cells.get(diagonalStart).z() - cells.get(diagonalStart - 1).z();
            final int afterDx = cells.get(edge + 1).x() - cells.get(edge).x();
            final int afterDz = cells.get(edge + 1).z() - cells.get(edge).z();
            if (!isCardinalUnitStep(beforeDx, beforeDz)
                || !isCardinalUnitStep(afterDx, afterDz)
                || beforeDx * afterDx + beforeDz * afterDz != 0)
            {
                continue;
            }

            for (int diagonalEdge = diagonalStart; diagonalEdge < edge; diagonalEdge++)
            {
                final GridCell from = cells.get(diagonalEdge);
                final GridCell to = cells.get(diagonalEdge + 1);
                final boolean followsIncomingAxis = (diagonalEdge - diagonalStart) * 2 < diagonalCount;
                final boolean connectorUsesXAxis = followsIncomingAxis ? beforeDx != 0 : afterDx != 0;
                final int connectorX = connectorUsesXAxis ? to.x() : from.x();
                final int connectorZ = connectorUsesXAxis ? from.z() : to.z();
                connectors.add(columnKey(connectorX, connectorZ));
            }
        }
        return Set.copyOf(connectors);
    }

    static int turnConnectorCount(List<Vec> points)
    {
        final double[] x = new double[points.size()];
        final double[] z = new double[points.size()];
        for (int i = 0; i < points.size(); i++)
        {
            x[i] = points.get(i).x();
            z[i] = points.get(i).z();
        }
        return rasterizeTurnConnectors(x, z).size();
    }

    static boolean isTurnConnector(List<Vec> points, int blockX, int blockZ)
    {
        final double[] x = new double[points.size()];
        final double[] z = new double[points.size()];
        for (int i = 0; i < points.size(); i++)
        {
            x[i] = points.get(i).x();
            z[i] = points.get(i).z();
        }
        return rasterizeTurnConnectors(x, z).contains(columnKey(blockX, blockZ));
    }

    private static boolean isDiagonalUnitStep(int dx, int dz)
    {
        return Math.abs(dx) == 1 && Math.abs(dz) == 1;
    }

    private static boolean isCardinalUnitStep(int dx, int dz)
    {
        return Math.abs(dx) + Math.abs(dz) == 1;
    }

    private static long columnKey(int x, int z)
    {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    static List<Vec> smoothRoute(List<Vec> input)
    {
        return smoothRoute(input, PREFERRED_CORNER_PRECISION);
    }

    private static int findRewriteStart(List<Vec> route, double rewriteLength)
    {
        double remaining = rewriteLength;
        for (int i = route.size() - 2; i > 0; i--)
        {
            remaining -= distance(route.get(i), route.get(i + 1));
            if (remaining <= 0d)
            {
                return i;
            }
        }
        return 1;
    }

    private static Vec cubicBezier(Vec start, Vec startControl, Vec endControl, Vec end, double delta)
    {
        final double inverse = 1d - delta;
        final double startWeight = inverse * inverse * inverse;
        final double startControlWeight = 3d * inverse * inverse * delta;
        final double endControlWeight = 3d * inverse * delta * delta;
        final double endWeight = delta * delta * delta;
        return new Vec(
            start.x() * startWeight
                + startControl.x() * startControlWeight
                + endControl.x() * endControlWeight
                + end.x() * endWeight,
            start.z() * startWeight
                + startControl.z() * startControlWeight
                + endControl.z() * endControlWeight
                + end.z() * endWeight
        );
    }

    private static Vec normalizedDirection(Vec from, Vec to)
    {
        final double dx = to.x() - from.x();
        final double dz = to.z() - from.z();
        final double length = Math.hypot(dx, dz);
        return length < 1.0e-6d ? new Vec(0d, 0d) : new Vec(dx / length, dz / length);
    }

    private static Vec add(Vec left, Vec right)
    {
        return new Vec(left.x() + right.x(), left.z() + right.z());
    }

    private static Vec scale(Vec vector, double factor)
    {
        return new Vec(vector.x() * factor, vector.z() * factor);
    }

    private static double vectorLength(Vec vector)
    {
        return Math.hypot(vector.x(), vector.z());
    }

    private static double directionDot(Vec left, Vec right)
    {
        if (vectorLength(left) < 1.0e-6d || vectorLength(right) < 1.0e-6d)
        {
            return -1d;
        }
        return Mth.clamp(left.x() * right.x() + left.z() * right.z(), -1d, 1d);
    }

    private static double distance(Vec left, Vec right)
    {
        return Math.hypot(right.x() - left.x(), right.z() - left.z());
    }

    private static double distanceSq(Vec left, Vec right)
    {
        final double dx = right.x() - left.x();
        final double dz = right.z() - left.z();
        return dx * dx + dz * dz;
    }

    private static double minimum(double[] values)
    {
        double result = Double.POSITIVE_INFINITY;
        for (double value : values)
        {
            result = Math.min(result, value);
        }
        return result;
    }

    private static double maximum(double[] values)
    {
        double result = Double.NEGATIVE_INFINITY;
        for (double value : values)
        {
            result = Math.max(result, value);
        }
        return result;
    }

    private static double polylineLength(List<Vec> points)
    {
        double length = 0d;
        for (int i = 0; i < points.size() - 1; i++)
        {
            length += distance(points.get(i), points.get(i + 1));
        }
        return length;
    }

    private static double nearestAlong(List<Vec> points, Vec point)
    {
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        double bestAlong = 0d;
        double traversed = 0d;
        for (int i = 0; i < points.size() - 1; i++)
        {
            final Vec from = points.get(i);
            final Vec to = points.get(i + 1);
            final Projection projection = project(from.x(), from.z(), to.x(), to.z(), point.x(), point.z());
            final double segmentLength = distance(from, to);
            if (projection.distanceSq() < bestDistanceSq)
            {
                bestDistanceSq = projection.distanceSq();
                bestAlong = traversed + projection.delta() * segmentLength;
            }
            traversed += segmentLength;
        }
        return bestAlong;
    }

    private static double distanceSqToPolyline(List<Vec> points, Vec point)
    {
        double bestDistanceSq = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size() - 1; i++)
        {
            final Vec from = points.get(i);
            final Vec to = points.get(i + 1);
            bestDistanceSq = Math.min(bestDistanceSq, project(
                from.x(),
                from.z(),
                to.x(),
                to.z(),
                point.x(),
                point.z()
            ).distanceSq());
        }
        return bestDistanceSq;
    }

    private static Vec pointAlong(List<Vec> points, double targetAlong)
    {
        double traversed = 0d;
        for (int i = 0; i < points.size() - 1; i++)
        {
            final Vec from = points.get(i);
            final Vec to = points.get(i + 1);
            final double segmentLength = distance(from, to);
            if (traversed + segmentLength >= targetAlong)
            {
                final double delta = segmentLength < 1.0e-6d ? 0d : (targetAlong - traversed) / segmentLength;
                return lerp(from, to, delta);
            }
            traversed += segmentLength;
        }
        return points.get(points.size() - 1);
    }

    private static Vec directionAlong(List<Vec> points, double targetAlong)
    {
        double traversed = 0d;
        for (int i = 0; i < points.size() - 1; i++)
        {
            final Vec from = points.get(i);
            final Vec to = points.get(i + 1);
            final double segmentLength = distance(from, to);
            if (traversed + segmentLength >= targetAlong || i == points.size() - 2)
            {
                return normalizedDirection(from, to);
            }
            traversed += segmentLength;
        }
        return new Vec(0d, 0d);
    }

    private static List<Vec> smoothRoute(List<Vec> input, int passes)
    {
        if (input.size() < 3)
        {
            return input;
        }

        // Smooth the complete polyline without independently moving every grid
        // corner. The old per-corner fillets produced a mathematically round
        // curve whose sub-block raster was discontinuous at narrow sources.
        List<Vec> current = List.copyOf(input);
        for (int pass = 0; pass < Math.max(1, passes); pass++)
        {
            final List<Vec> rounded = new ArrayList<>(current.size() * 2);
            rounded.add(current.get(0));
            for (int i = 0; i < current.size() - 1; i++)
            {
                final Vec from = current.get(i);
                final Vec to = current.get(i + 1);
                addIfDifferent(rounded, lerp(from, to, 0.25d));
                addIfDifferent(rounded, lerp(from, to, 0.75d));
            }
            addIfDifferent(rounded, current.get(current.size() - 1));
            current = List.copyOf(rounded);
        }
        return resampleRoute(current, passes >= PREFERRED_CORNER_PRECISION ? 1.5d : 2.25d);
    }

    private static List<Vec> resampleRoute(List<Vec> input, double spacing)
    {
        final List<Vec> output = new ArrayList<>(input.size() * 2);
        output.add(input.get(0));
        for (int i = 0; i < input.size() - 1; i++)
        {
            final Vec from = input.get(i);
            final Vec to = input.get(i + 1);
            final double length = Math.hypot(to.x() - from.x(), to.z() - from.z());
            final int steps = Math.max(1, Mth.ceil(length / spacing));
            for (int step = 1; step <= steps; step++)
            {
                addIfDifferent(output, lerp(from, to, step / (double) steps));
            }
        }
        return List.copyOf(output);
    }

    private static void addIfDifferent(List<Vec> output, Vec point)
    {
        final Vec last = output.get(output.size() - 1);
        if (Math.hypot(last.x() - point.x(), last.z() - point.z()) > 1.0e-6d)
        {
            output.add(point);
        }
    }

    private static Vec lerp(Vec from, Vec to, double delta)
    {
        return new Vec(Mth.lerp(delta, from.x(), to.x()), Mth.lerp(delta, from.z(), to.z()));
    }

    private static int index(int x, int z, int width)
    {
        return x + z * width;
    }

    private static double heuristic(int x, int z, int goalX, int goalZ)
    {
        return Math.hypot(goalX - x, goalZ - z) * 0.8d;
    }

    private static Projection project(double sourceX, double sourceZ, double drainX, double drainZ, double x, double z)
    {
        final double dx = drainX - sourceX;
        final double dz = drainZ - sourceZ;
        final double lengthSq = dx * dx + dz * dz;
        final double delta = lengthSq <= 1.0e-9d
            ? 0d
            : Mth.clamp(((x - sourceX) * dx + (z - sourceZ) * dz) / lengthSq, 0d, 1d);
        final double nearestX = sourceX + dx * delta;
        final double nearestZ = sourceZ + dz * delta;
        final double offsetX = x - nearestX;
        final double offsetZ = z - nearestZ;
        return new Projection(offsetX * offsetX + offsetZ * offsetZ, delta);
    }

    private static boolean extendsPastOutlet(
        double previousX,
        double previousZ,
        double outletX,
        double outletZ,
        int blockX,
        int blockZ
    )
    {
        final double dx = outletX - previousX;
        final double dz = outletZ - previousZ;
        final double length = Math.hypot(dx, dz);
        if (length <= 1.0e-9d)
        {
            return false;
        }
        final double forward = ((blockX - outletX) * dx + (blockZ - outletZ) * dz) / length;
        return forward > 0.5d;
    }

    private static long mix64(long value)
    {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
