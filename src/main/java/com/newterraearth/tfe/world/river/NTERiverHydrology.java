package com.newterraearth.tfe.world.river;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.blocks.rock.RockSpikeBlock;
import net.dries007.tfc.common.fluids.RiverWaterFluid;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.world.region.RegionPartition;
import net.dries007.tfc.world.region.RiverEdge;
import net.dries007.tfc.world.region.Units;
import net.dries007.tfc.world.river.Flow;
import net.dries007.tfc.world.river.RiverInfo;

import com.newterraearth.tfe.config.NTECommonConfig;

/**
 * Runtime bridge between TFC's river graph and the supplemental headwater
 * streams. Main-stem edges stay entirely in TFC's original river pipeline;
 * only a leaf edge whose terrain-aware replacement validates is suppressed.
 */
public final class NTERiverHydrology
{
    public static final double TFC_WATER_CORE_RADIUS_SQ = 0.28d;
    static final double SUPPLEMENTAL_WATER_CORE_RADIUS_SQ = 0.72d;
    /** Native banked terrain finishes blending back to ambient by two river widths. */
    private static final double TFC_TERRAIN_CORRIDOR_RADIUS_SCALE = 2d;
    /** Salt for the deterministic thinning of covered creek cave spikes. */
    private static final long COVERED_SPIKE_SALT = 0x6F1B3C9A47D8E205L;
    /** A covered creek's ceiling is this many blocks lower at the channel edge than at its apex. */
    private static final double TUNNEL_ARCH_RISE = 2d;
    /** Laterally the covered creek is carved out to its own channel; the graded mouth reaches further. */
    private static final double MOUTH_SHOULDER_SQ = 2.25d;
    private static final int MAX_HEIGHT_CACHE_SIZE = 131072;
    private static final ThreadLocal<GenerationContext> ACTIVE_GENERATION = new ThreadLocal<>();
    /** Height probes must not start a terrain-aware creek route search. */
    private static final ThreadLocal<Integer> READ_ONLY_HEIGHT_QUERY_DEPTH = ThreadLocal.withInitial(() -> 0);

    @FunctionalInterface
    public interface TerrainHeightSampler
    {
        double sample(int blockX, int blockZ);
    }

    @FunctionalInterface
    public interface PartitionLookup
    {
        RegionPartition.Point find(int blockX, int blockZ);
    }

    public enum ChannelKind
    {
        STREAM,
        TRIBUTARY,
        RIVER
    }

    public enum ChannelMode
    {
        SURFACE,
        /** A creek section which drops below the terrain and continues as a covered rock tunnel. */
        SUBTERRANEAN
    }

    public record CardinalStep(int x, int z) {}

    public record ColumnProfile(
        double waterSurfaceY,
        double centerBedY,
        double bedY,
        double normalizedDistanceSq,
        double channelRadius,
        double bankRaise,
        double terrainIncision,
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
        ChannelKind kind,
        ChannelMode mode,
        /** Rock ceiling of a subterranean tunnel; ignored for surface columns. */
        double tunnelCeilingY,
        /** Creek seed which drives the covered cavity noise; zero for surface columns. */
        long caveSeed,
        Flow flow
    )
    {
        public boolean inChannel()
        {
            return normalizedDistanceSq <= 1d;
        }

        public boolean inWaterCore()
        {
            return waterAllowed && normalizedDistanceSq <= waterCoreRadiusSq;
        }

        public boolean inSourceWaterCore()
        {
            return sourceWaterAllowed && normalizedDistanceSq <= waterCoreRadiusSq;
        }

        public boolean surfaceVisible()
        {
            return mode != ChannelMode.SUBTERRANEAN;
        }

        public boolean descendingReceiverMouth()
        {
            return !fillAllowed && receiverBlendWeight > 0d && terrainIncision > 0d;
        }

        /** A cut-only fallback feeder which must remain wet until native water contact. */
        public boolean retainedFeederMouth()
        {
            return inChannel() && !fillAllowed && waterAllowed && receiverBlendWeight <= 0d;
        }

        public boolean subterranean()
        {
            return mode == ChannelMode.SUBTERRANEAN;
        }

        /** A covered creek section whose terrain is graded down into the creek: a cave mouth. */
        public boolean gradedMouth()
        {
            return mode == ChannelMode.SUBTERRANEAN && terrainIncision > 0d;
        }

        public int tunnelCeilingBlockY()
        {
            return Mth.floor(tunnelCeilingY);
        }

        public int waterBlockY()
        {
            return Mth.floor(waterSurfaceY);
        }

        public int bedBlockY()
        {
            return Mth.floor(bedY);
        }

        /** Supplemental streams are cut into terrain and may never build it up. */
        public double fillCeiling(double terrainHeight)
        {
            return terrainHeight;
        }

        /**
         * Fade only the part of a BANKED sample which would build above the
         * pre-river terrain. Cutting remains fully effective throughout the
         * transition, and the weight reaches zero before cut-only ownership.
         */
        public double applyBankFillTransition(double terrainHeight, double sampledHeight)
        {
            if (sampledHeight <= terrainHeight)
            {
                return sampledHeight;
            }
            return Mth.lerp(bankFillWeight, terrainHeight, sampledHeight);
        }

        /**
         * A cut-only receiver mouth must realize its planned erosion even when
         * the selected TFC river-shape noise happens to raise an outer bank.
         * The explicit incision is retained even though source water descends
         * with the cut, so lowering the water cannot accidentally cancel the
         * cut-only terrain ceiling.
         */
        public double terrainCutCeiling(double terrainHeight)
        {
            // A covered creek never builds its own surface, so its graded cave mouth is
            // realized as a pure cut even where the shared downstream fields would allow
            // an ordinary profile to fill.
            if (fillAllowed && !subterranean())
            {
                return terrainHeight;
            }
            return terrainHeight - Math.max(0d, terrainIncision);
        }

        /**
         * Whether the height stage must realize this profile's planned erosion. The ordinary
         * creek only cuts where its fill transition may not raise the terrain; a covered creek
         * grades its cave mouth open regardless of the shared fill flag, because a covered
         * section never shapes the surface itself.
         */
        public boolean forcesTerrainCut()
        {
            return subterranean() ? terrainIncision > 0d : !fillAllowed;
        }
    }

    public static boolean shouldUseSupplemental(@Nullable ColumnProfile profile, @Nullable RiverInfo retainedTfcRiver)
    {
        if (profile == null)
        {
            return false;
        }
        if (retainedTfcRiver == null)
        {
            return true;
        }
        if (profile.receiverBlendWeight() > 0d)
        {
            // Keep one owner for every confluence column. The supplemental
            // profile owns its channel and its dry shoulder only until that
            // shoulder reaches the retained river's actual channel. Once the
            // supplemental profile is outside its own channel while the native
            // river is inside its physical cross-section, retaining the almost
            // zero-weight supplemental bank would punch an ambient-height rock
            // fin through the already excavated receiver bank.
            return profile.inChannel() || retainedTfcRiver.normDistSq() > 1d;
        }
        if (!profile.inChannel())
        {
            // A supplemental route keeps a 1.0 -> 1.5-radius dry-bank feather
            // outside its physical channel. RiverInfo can still point at the
            // designated TFC receiver many widths away; rejecting the feather
            // merely because that distant info exists collapses a deep cut
            // from the creek floor straight back to ambient terrain in one
            // column. Preserve the feather until it reaches the receiver's
            // actual channel, where the native cross-section remains owner.
            return retainedTfcRiver.normDistSq() > 1d;
        }
        if (profile.retainedFeederMouth())
        {
            return true;
        }
        return profile.fillAllowed() && retainedTfcRiver.normDistSq() > TFC_WATER_CORE_RADIUS_SQ;
    }

    /**
     * A confluence is the union of two cuts, not an ownership boundary. Keep
     * both complete cross-sections alive anywhere the supplemental mouth and
     * its retained receiver overlap, including the dry shoulder. The height
     * and density stages can then take the more-carved result instead of
     * averaging two incompatible banks or switching between them per column.
     */
    public static boolean usesConfluenceCarvingUnion(
        @Nullable ColumnProfile profile,
        @Nullable RiverInfo retainedTfcRiver
    )
    {
        return profile != null
            && retainedTfcRiver != null
            && profile.receiverBlendWeight() > 0d;
    }

    /**
     * Transfer the receiver's complete bank shape only through the center of
     * the shared wet corridor. Water ownership still uses the full receiver
     * blend, but allowing a tall-canyon sampler to take over the creek's dry
     * shoulder creates a third, overhanging cross-section between both banks.
     */
    public static double receiverBankShapeBlendWeight(@Nullable ColumnProfile profile)
    {
        if (profile == null || profile.receiverBlendWeight() <= 0d)
        {
            return 0d;
        }
        final double coreRadiusSq = profile.waterCoreRadiusSq();
        final double fullReceiverRadiusSq = coreRadiusSq * 0.25d;
        final double t = Mth.clamp(
            (profile.normalizedDistanceSq() - fullReceiverRadiusSq)
                / (coreRadiusSq - fullReceiverRadiusSq),
            0d,
            1d
        );
        final double creekBankWeight = t * t * (3d - 2d * t);
        return profile.receiverBlendWeight() * (1d - creekBankWeight);
    }

    public static double clampToRetainedReceiverBed(
        @Nullable ColumnProfile profile,
        @Nullable RiverInfo retainedTfcRiver,
        double supplementalHeight,
        double retainedReceiverHeight
    )
    {
        return usesConfluenceCarvingUnion(profile, retainedTfcRiver)
            ? Math.min(supplementalHeight, retainedReceiverHeight)
            : supplementalHeight;
    }

    /**
     * Cut the terminal supplemental bed into the receiving river's own wet-core
     * profile. The target is sampled from the receiver's active river shape and
     * noise at this column, so this never introduces a fixed Y or a synthetic
     * stair. Both center and local bed use the same longitudinal handoff and the
     * operation is cut-only.
     */
    @Nullable
    public static ColumnProfile adaptToRetainedReceiverBed(
        @Nullable ColumnProfile profile,
        double retainedReceiverBedHeight
    )
    {
        if (profile == null
            || !profile.inWaterCore()
            || profile.receiverBedBlendWeight() <= 0d
            || !Double.isFinite(retainedReceiverBedHeight))
        {
            return profile;
        }
        final double blend = Mth.clamp(profile.receiverBedBlendWeight(), 0d, 1d);
        final double targetCenterBedY = Math.min(profile.centerBedY(), retainedReceiverBedHeight);
        final double targetBedY = Math.min(profile.bedY(), retainedReceiverBedHeight);
        return new ColumnProfile(
            profile.waterSurfaceY(),
            Mth.lerp(blend, profile.centerBedY(), targetCenterBedY),
            Mth.lerp(blend, profile.bedY(), targetBedY),
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

    /** Keep the height-stage ceiling consistent with the density-stage bed. */
    public static double clampToAdaptedReceiverBed(@Nullable ColumnProfile profile, double terrainHeight)
    {
        return profile != null && profile.inWaterCore() && profile.receiverBedBlendWeight() > 0d
            ? Math.min(terrainHeight, profile.bedY() + 1d)
            : terrainHeight;
    }

    /** Probe the same receiving cross-section at a caller-selected radius. */
    public static RiverInfo retainedReceiverCrossSectionSampleInfo(
        RiverInfo info,
        double normalizedDistanceSq
    )
    {
        final double clampedDistanceSq = Mth.clamp(normalizedDistanceSq, 0d, info.normDistSq());
        return new RiverInfo(
            info.edge(),
            info.flow(),
            clampedDistanceSq * info.widthSq(),
            info.widthSq()
        );
    }

    /** Detect the receiver's intentional inner-bank cliff without treating a normal slope as a step. */
    public static boolean crossesReceiverInnerBankCliff(double outerHeight, double innerHeight)
    {
        return Double.isFinite(outerHeight)
            && Double.isFinite(innerHeight)
            && outerHeight - innerHeight >= 4d;
    }

    /**
     * Find the first solid receiver layer below an already-open river column.
     * Cave rivers carve their real bed in the density pass, so their sampled
     * surface height is the cave roof / canyon wall and cannot be used as a
     * confluence bed target.
     */
    public static double receiverDensityBedY(int waterBlockY, int minimumY, IntPredicate isReceiverAir)
    {
        boolean foundOpenRiver = false;
        for (int y = waterBlockY; y >= minimumY; y--)
        {
            if (isReceiverAir.test(y))
            {
                foundOpenRiver = true;
            }
            else if (foundOpenRiver)
            {
                return y;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    /** The density-stage bed follows any lower receiver-bed ceiling. */
    public static int effectiveBedBlockY(ColumnProfile profile, double terrainHeight)
    {
        return Math.min(profile.bedBlockY(), Mth.floor(terrainHeight));
    }

    /**
     * A waterfall landing above the native river may remove its old raised
     * source shelf, but the fixed minimum river layer itself must survive.
     */
    public static int retainedMouthWaterClearFromY(
        ColumnProfile profile,
        int minimumRiverWaterY
    )
    {
        final int plannedWaterY = profile.waterBlockY();
        return profile.waterfallLanding() && plannedWaterY > minimumRiverWaterY
            ? plannedWaterY
            : plannedWaterY + 1;
    }

    /** Clear the planned vertical descent above a wet connector, including cave rivers. */
    public static boolean clearsWetMouthHeadroom(ColumnProfile profile, int y)
    {
        final int clearanceCeilingY = Mth.ceil(
            profile.waterSurfaceY() + profile.mouthWaterDrop()
        );
        return profile.descendingReceiverMouth()
            && profile.inWaterCore()
            && y > profile.waterBlockY()
            && y <= clearanceCeilingY;
    }

    /**
     * Initial terrain fill needs a stable five-block shallow roof before
     * carvers run. Three blocks still left density-noise caves visibly open
     * directly beneath narrow upland streams, producing a thin floating slab.
     */
    public static boolean protectsBedAt(ColumnProfile profile, int y)
    {
        return protectsBedAt(profile, y, profile.bedY());
    }

    public static boolean protectsBedAt(ColumnProfile profile, int y, double terrainHeight)
    {
        final int bedY = effectiveBedBlockY(profile, terrainHeight);
        return profile.inWaterCore() && y <= bedY && y >= bedY - 4;
    }

    /**
     * Arch apex of a covered creek at this column: the planned ceiling lowered by a
     * slow carving noise, so the roof is neither a machine cut tube nor able to reach
     * into the protected rock above the tunnel.
     */
    static double tunnelArchApexY(ColumnProfile profile, int blockX, int blockZ)
    {
        final double waterY = profile.waterSurfaceY();
        final double plannedCeilingY = profile.tunnelCeilingY();
        final double amplitude = NTECommonConfig.getHeadwaterTunnelCarvingNoise();
        if (amplitude <= 0d || plannedCeilingY <= waterY + 1d)
        {
            return plannedCeilingY;
        }
        final double wobble = amplitude * NTERiverCaveNoise.noise(
            profile.caveSeed(),
            NTERiverCaveNoise.SALT_ARCH,
            blockX,
            blockZ,
            0.09d
        );
        return Mth.clamp(plannedCeilingY + wobble, waterY + 1d, plannedCeilingY);
    }

    /**
     * Rock cavity of a covered creek: straight walls from the water up to the arch
     * springing line, then an arch whose ceiling falls by {@link #TUNNEL_ARCH_RISE}
     * from the channel centre to the channel edge. This is the scaled down form of
     * the vanilla cave river's vertical lens: the roof is shaped from the water
     * surface outward instead of the tunnel staying a rectangle with a flat top.
     *
     * @param terrainHeight the column's realized surface height. A graded cave mouth
     *                      lowers the surface to its funnel floor, which can come within
     *                      a few blocks of the arch above the water. Rock that thin is
     *                      not a roof: keeping it leaves a floating shell over the
     *                      creek, so a graded mouth is opened through to its realized
     *                      surface instead. The covered run keeps the arch and its roof
     *                      untouched.
     */
    public static boolean carvesTunnelCavity(
        ColumnProfile profile,
        int y,
        int blockX,
        int blockZ,
        double terrainHeight
    )
    {
        if (!profile.subterranean() || y <= profile.waterBlockY())
        {
            return false;
        }
        final double lateralDistanceSq = profile.normalizedDistanceSq();
        // A covered run is carved out to its own channel; a graded cave mouth reaches its shoulder.
        final double carvedLateralDistanceSq = profile.gradedMouth() ? MOUTH_SHOULDER_SQ : 1d;
        if (lateralDistanceSq > carvedLateralDistanceSq)
        {
            return false;
        }
        final double archTopY = tunnelArchApexY(profile, blockX, blockZ)
            - TUNNEL_ARCH_RISE * Mth.clamp(lateralDistanceSq, 0d, 1d);
        if (profile.gradedMouth()
            && terrainHeight - archTopY <= NTECommonConfig.getHeadwaterTunnelRoof())
        {
            return y <= Mth.floor(terrainHeight);
        }
        return lateralDistanceSq <= 1d && y <= Mth.floor(archTopY);
    }

    /**
     * The fixed river layer is source-like TFC river water. A descending
     * connector may use vanilla flowing water above it, but its contact layer
     * must rejoin the receiver's directional-water semantics.
     */
    public static boolean usesDirectionalReceiverSurfaceWater(
        ColumnProfile profile,
        int y,
        int minimumRiverWaterY
    )
    {
        return profile.inWaterCore()
            && y == profile.waterBlockY()
            && y <= minimumRiverWaterY;
    }

    /**
     * Static supplemental water always owns its route flow. This decision may
     * not depend on the base biome's {@code hasRivers()} flag: a planned creek
     * can legitimately cross a lake or another no-river biome.
     */
    public static boolean usesDirectionalSupplementalWater(
        ColumnProfile profile,
        int y,
        int minimumRiverWaterY
    )
    {
        return profile.inSourceWaterCore()
            || usesDirectionalReceiverSurfaceWater(profile, y, minimumRiverWaterY);
    }

    /**
     * A supplemental route owns the column flow used by TFC's final source-
     * water conversion, including inside lake and other no-river biomes. The
     * profile flow is already derived from the rebuilt post-junction geometry,
     * so legitimate inlet, outlet and turn adjustments remain intact.
     */
    public static Flow effectiveColumnFlow(@Nullable ColumnProfile profile, Flow nativeFlow)
    {
        return profile != null && profile.inWaterCore() ? profile.flow() : nativeFlow;
    }

    /** One canonical block-state conversion for every generated directional-water handoff. */
    public static BlockState directionalRiverWaterState(Flow flow)
    {
        return TFCFluids.RIVER_WATER.get().defaultFluidState()
            .setValue(RiverWaterFluid.FLOW, flow)
            .createLegacyBlock();
    }

    /** Protect the realized wet corridor from late cave-spike decoration. */
    public static boolean blocksCaveDecoration(ColumnProfile profile, int featureY)
    {
        if (profile.subterranean())
        {
            // A covered creek section is a real cave: its own cavity must be
            // decorated like any other cave instead of being cleared out.
            return false;
        }
        final int clearanceCeilingY = Mth.ceil(
            profile.waterSurfaceY() + profile.mouthWaterDrop()
        );
        return profile.inWaterCore()
            && featureY >= profile.bedBlockY() - 4
            && featureY <= clearanceCeilingY + 3;
    }

    /** A cave column grows upward from its origin until it reaches the roof. */
    public static boolean blocksCaveColumn(ColumnProfile profile, int featureY)
    {
        if (profile.subterranean())
        {
            // A covered creek section is a real cave: its own cavity must be
            // decorated like any other cave instead of being cleared out.
            return false;
        }
        final int clearanceCeilingY = Mth.ceil(
            profile.waterSurfaceY() + profile.mouthWaterDrop()
        );
        return profile.inWaterCore() && featureY <= clearanceCeilingY + 1;
    }

    /**
     * Cave spikes inside a covered creek tunnel are thinned so the creek keeps the
     * scenery to itself: two of three stacks remain along the covered run, only one
     * of three near a graded cave mouth.
     * The answer is taken once per formation from the block it grows out of, so a
     * formation is always kept or dropped as a whole.
     */
    public static boolean blocksCaveSpike(ColumnProfile profile, int blockX, int blockY, int blockZ)
    {
        if (profile == null || !profile.subterranean() || !profile.inChannel())
        {
            return false;
        }
        if (blockY < profile.bedBlockY() - 2 || blockY > profile.tunnelCeilingBlockY() + 1)
        {
            return false;
        }
        final int bucket = NTERiverCaveNoise.select(
            profile.caveSeed(),
            COVERED_SPIKE_SALT,
            blockX,
            blockY,
            blockZ,
            3
        );
        // Near a cave mouth the creek is the visible feature, so only one in three
        // spikes remains; along the covered run two of three are kept.
        return profile.terrainIncision() > 0d ? bucket != 0 : bucket == 0;
    }

    /** Blocks one spike formation writes along its own direction, per TFC's three size classes. */
    static int caveSpikeLength(float sizeWeight)
    {
        return sizeWeight < 0.2f ? 2 : sizeWeight < 0.7f ? 3 : 4;
    }

    /**
     * A cave spike formation is placed whole or not at all. TFC grows one from its
     * root block outward and writes a hardened cap above the root, so a formation
     * whose own blocks already hold another spike would grow through it, and its cap
     * would bury one. Rejecting such a formation before it is written is what keeps
     * covered creek spikes free of stacked duplicates.
     */
    public static boolean blocksStackedCaveSpike(
        BlockGetter level,
        @Nullable ColumnProfile profile,
        BlockPos root,
        Direction direction,
        float sizeWeight
    )
    {
        if (profile == null || !profile.subterranean() || !profile.inChannel())
        {
            return false;
        }
        final int length = caveSpikeLength(sizeWeight);
        for (int step = 0; step < length; step++)
        {
            if (level.getBlockState(root.relative(direction, step)).getBlock() instanceof RockSpikeBlock)
            {
                return true;
            }
        }
        return level.getBlockState(root.above()).getBlock() instanceof RockSpikeBlock;
    }

    /**
     * Erosion's late stability repair may not bridge the planned wet cavity
     * with hardened support stone after the density pass has opened it.
     */
    public static boolean blocksErosionSupport(ColumnProfile profile, int y)
    {
        final int clearanceCeilingY = Mth.ceil(
            profile.waterSurfaceY() + profile.mouthWaterDrop()
        );
        return profile.inWaterCore()
            && y > profile.bedBlockY()
            && y <= clearanceCeilingY + 1;
    }

    public static void activateGeneration(
        NTERiverHydrology hydrology,
        @Nullable ColumnProfile[] localProfiles,
        int chunkMinX,
        int chunkMinZ
    )
    {
        ACTIVE_GENERATION.set(new GenerationContext(hydrology, localProfiles, chunkMinX, chunkMinZ));
    }

    public static void clearActiveGeneration()
    {
        ACTIVE_GENERATION.remove();
    }

    /** Enter a locate/pre-generation height query which may only read published routes. */
    public static void beginReadOnlyHeightQuery()
    {
        READ_ONLY_HEIGHT_QUERY_DEPTH.set(READ_ONLY_HEIGHT_QUERY_DEPTH.get() + 1);
    }

    /** Leave a locate/pre-generation height query, restoring any outer scope. */
    public static void endReadOnlyHeightQuery()
    {
        final int depth = READ_ONLY_HEIGHT_QUERY_DEPTH.get() - 1;
        if (depth <= 0)
        {
            READ_ONLY_HEIGHT_QUERY_DEPTH.remove();
        }
        else
        {
            READ_ONLY_HEIGHT_QUERY_DEPTH.set(depth);
        }
    }

    static boolean readOnlyHeightQueryActive()
    {
        return READ_ONLY_HEIGHT_QUERY_DEPTH.get() > 0;
    }

    @Nullable
    public static ColumnProfile activeGenerationProfile(int blockX, int blockZ)
    {
        final GenerationContext context = ACTIVE_GENERATION.get();
        if (context == null)
        {
            return null;
        }
        if (context.localProfiles() != null
            && blockX >= context.chunkMinX() && blockX < context.chunkMinX() + 16
            && blockZ >= context.chunkMinZ() && blockZ < context.chunkMinZ() + 16)
        {
            return context.localProfiles()[
                blockX - context.chunkMinX() + 16 * (blockZ - context.chunkMinZ())
            ];
        }
        return context.hydrology().findGraphProfileIfPlanned(blockX, blockZ);
    }

    public static boolean hasActiveGenerationColumn(NTERiverHydrology hydrology, int blockX, int blockZ)
    {
        final GenerationContext context = ACTIVE_GENERATION.get();
        return context != null
            && context.hydrology() == hydrology
            && context.localProfiles() != null
            && blockX >= context.chunkMinX() && blockX < context.chunkMinX() + 16
            && blockZ >= context.chunkMinZ() && blockZ < context.chunkMinZ() + 16;
    }

    private record GenerationContext(
        NTERiverHydrology hydrology,
        @Nullable ColumnProfile[] localProfiles,
        int chunkMinX,
        int chunkMinZ
    ) {}

    /** One non-branching cardinal step that rasterizes the supplied flow direction. */
    public static CardinalStep cardinalFlowStep(Flow flow, int horizontalStep)
    {
        final double vectorX = flow.getVector().x;
        final double vectorZ = flow.getVector().z;
        final double absX = Math.abs(vectorX);
        final double absZ = Math.abs(vectorZ);
        final double total = absX + absZ;
        if (total <= 1.0e-9d)
        {
            return new CardinalStep(0, 0);
        }

        final int step = Math.max(1, horizontalStep);
        final double xShare = absX / total;
        final boolean advancesX = Math.round(step * xShare) > Math.round((step - 1d) * xShare);
        if (advancesX && absX > 1.0e-9d)
        {
            return new CardinalStep(vectorX > 0d ? 1 : -1, 0);
        }
        if (absZ > 1.0e-9d)
        {
            return new CardinalStep(0, vectorZ > 0d ? 1 : -1);
        }
        return new CardinalStep(vectorX > 0d ? 1 : -1, 0);
    }

    private final TerrainHeightSampler terrainHeightSampler;
    private final PartitionLookup partitionLookup;
    private final NTEHeadwaterNetwork headwaters;
    private final Map<Long, Double> heightCache = new LinkedHashMap<>(256, 0.75f, true);

    public NTERiverHydrology(
        long seed,
        int seaLevel,
        TerrainHeightSampler terrainHeightSampler,
        PartitionLookup partitionLookup
    )
    {
        this.terrainHeightSampler = terrainHeightSampler;
        this.partitionLookup = partitionLookup;
        this.headwaters = new NTEHeadwaterNetwork(
            seed,
            seaLevel,
            this::sampleTerrainHeight,
            this::blocksUnrelatedRetainedRiver
        );
    }

    /**
     * Planning-only mask for native downstream river edges. TFC marks an edge
     * as sourceEdge once another edge feeds it, so these are the main-stem
     * corridors that this addon always retains. The leaf being replaced and
     * its designated receiver remain legal; every other retained terrain
     * corridor is a structural obstacle, not a low valley for a new creek to
     * use. Protecting only the wet core lets a spring start on a TALUS or
     * canyon outer bank which is much lower after native river shaping.
     * This query intentionally never asks headwaters for replacement state.
     */
    private boolean blocksUnrelatedRetainedRiver(
        @Nullable RiverEdge owner,
        double blockX,
        double blockZ,
        double clearance
    )
    {
        final RiverEdge receiver = owner == null ? null : owner.drainEdge();
        final RegionPartition.Point point = partitionLookup.find(Mth.floor(blockX), Mth.floor(blockZ));
        final double exactGridX = Units.blockToGridExact(blockX);
        final double exactGridZ = Units.blockToGridExact(blockZ);
        for (RiverEdge edge : point.rivers())
        {
            if (!edge.sourceEdge() || edge == owner || edge == receiver)
            {
                continue;
            }
            final double distanceBlocks = Math.sqrt(edge.fractal().intersectDistance(exactGridX, exactGridZ))
                * Units.GRID_WIDTH_IN_BLOCK;
            final double widthSq = edge.widthSq(exactGridX, exactGridZ);
            if (withinRetainedRiverTerrainCorridor(distanceBlocks, widthSq, clearance))
            {
                return true;
            }
        }
        return false;
    }

    static boolean withinRetainedRiverTerrainCorridor(
        double distanceBlocks,
        double widthSq,
        double clearance
    )
    {
        return distanceBlocks <= Math.sqrt(widthSq) * TFC_TERRAIN_CORRIDOR_RADIUS_SCALE + clearance;
    }

    /**
     * Resolve the nearest retained TFC edge. The height filler's raw nearest
     * edge can be a suppressed leaf even when a retained main stem is the
     * physical receiving channel at this column.
     */
    @Nullable
    public RiverInfo retainedRiverInfo(@Nullable RiverInfo nearest, int blockX, int blockZ)
    {
        if (readOnlyHeightQueryActive())
        {
            return retainedRiverInfoPrepared(nearest, blockX, blockZ);
        }
        planCandidateLeaves(blockX, blockZ);
        return retainedRiverInfoFromPlannedCandidates(nearest, blockX, blockZ, true);
    }

    @Nullable
    public RiverInfo retainedRiverInfoPrepared(@Nullable RiverInfo nearest, int blockX, int blockZ)
    {
        return retainedRiverInfoFromPlannedCandidates(nearest, blockX, blockZ, false);
    }

    @Nullable
    private RiverInfo retainedRiverInfoFromPlannedCandidates(
        @Nullable RiverInfo nearest,
        int blockX,
        int blockZ,
        boolean allowPlanning
    )
    {
        if (nearest != null && (allowPlanning ? retainsTfcEdge(nearest.edge()) : retainsTfcEdgeIfPlanned(nearest.edge())))
        {
            return headwaters.suppressesRetainedAt(nearest.edge(), blockX, blockZ)
                ? null
                : adaptRetainedLeafWidth(nearest, blockX, blockZ, allowPlanning);
        }

        final RegionPartition.Point point = partitionLookup.find(blockX, blockZ);
        final double exactGridX = Units.blockToGridExact(blockX);
        final double exactGridZ = Units.blockToGridExact(blockZ);
        final double limitDistGridSq = 50d * 50d / (Units.GRID_WIDTH_IN_BLOCK * Units.GRID_WIDTH_IN_BLOCK);
        double minimumAdjusted = Double.POSITIVE_INFINITY;
        double minimumDistanceGridSq = Double.POSITIVE_INFINITY;
        RiverEdge minimumEdge = null;
        for (RiverEdge edge : point.rivers())
        {
            if (!(allowPlanning ? retainsTfcEdge(edge) : retainsTfcEdgeIfPlanned(edge))
                || headwaters.suppressesRetainedAt(edge, blockX, blockZ))
            {
                continue;
            }
            final double distanceGridSq = edge.fractal().intersectDistance(exactGridX, exactGridZ);
            if (distanceGridSq >= limitDistGridSq)
            {
                continue;
            }
            final double adjusted = distanceGridSq / edge.widthSq();
            if (adjusted < minimumAdjusted)
            {
                minimumAdjusted = adjusted;
                minimumDistanceGridSq = distanceGridSq;
                minimumEdge = edge;
            }
        }
        if (minimumEdge == null)
        {
            return null;
        }

        final double widthSq = minimumEdge.widthSq(exactGridX, exactGridZ);
        final double distanceBlocksSq = minimumDistanceGridSq
            * Units.GRID_WIDTH_IN_BLOCK * Units.GRID_WIDTH_IN_BLOCK;
        return adaptRetainedLeafWidth(new RiverInfo(
            minimumEdge,
            minimumEdge.fractal().calculateFlow(exactGridX, exactGridZ),
            distanceBlocksSq,
            widthSq
        ), blockX, blockZ, allowPlanning);
    }

    /** Compatibility entry used by the height filler. RiverInfo is not needed for a new stream sample. */
    @Nullable
    public ColumnProfile sample(@Nullable RiverInfo ignored, int blockX, int blockZ, double ambientHeight)
    {
        return findProfile(blockX, blockZ, ambientHeight);
    }

    @Nullable
    public ColumnProfile samplePreparedColumn(int blockX, int blockZ, double ambientHeight)
    {
        return findProfileFromPlannedCandidates(blockX, blockZ, ambientHeight);
    }

    /**
     * Keeps biome queries aligned with both generators: retained TFC edges use
     * TFC's original 0.08 intersection, while replacement streams use their
     * fill-safe water core.
     */
    public boolean isVisibleRiver(int blockX, int blockZ)
    {
        final RegionPartition.Point point = partitionLookup.find(blockX, blockZ);
        final double exactGridX = Units.blockToGridExact(blockX);
        final double exactGridZ = Units.blockToGridExact(blockZ);
        for (RiverEdge edge : point.rivers())
        {
            // Most biome queries are unrelated to a river. Reject those before
            // consulting any headwater state; planning a whole drainage route
            // here made initial structure biome searches take several minutes.
            if (!edge.fractal().intersect(exactGridX, exactGridZ, 0.08d))
            {
                continue;
            }

            final Boolean replacement = headwaters.replacementIfPlanned(edge);
            if (replacement == null)
            {
                // Until normal chunk generation reaches this leaf, retain TFC's
                // biome result. A visibility lookup is deliberately read-only.
                return true;
            }
            if (replacement)
            {
                continue;
            }

            final double widthScale = headwaters.retainedLeafWidthScaleIfPlanned(edge, blockX, blockZ);
            if (edge.fractal().intersect(exactGridX, exactGridZ, 0.08d * widthScale))
            {
                return true;
            }
        }

        final ColumnProfile profile = findPlannedGraphProfile(blockX, blockZ);
        return profile != null && profile.inWaterCore();
    }

    @Nullable
    private ColumnProfile findPlannedGraphProfile(int blockX, int blockZ)
    {
        return findProfileFromPlannedCandidates(blockX, blockZ, Double.POSITIVE_INFINITY);
    }

    /** Read-only late-decoration query; never initiates a new drainage search. */
    @Nullable
    public ColumnProfile findGraphProfileIfPlanned(int blockX, int blockZ)
    {
        return findPlannedGraphProfile(blockX, blockZ);
    }

    /** Query stream geometry for groundwater influence without a per-column terrain rejection. */
    @Nullable
    public ColumnProfile findGraphProfile(int blockX, int blockZ)
    {
        if (readOnlyHeightQueryActive())
        {
            return findPlannedGraphProfile(blockX, blockZ);
        }
        return findProfileFromCandidates(blockX, blockZ, Double.POSITIVE_INFINITY);
    }

    @Nullable
    public ColumnProfile findProfile(int blockX, int blockZ, double ambientHeight)
    {
        if (readOnlyHeightQueryActive())
        {
            return findProfileFromPlannedCandidates(blockX, blockZ, ambientHeight);
        }
        return findProfileFromCandidates(blockX, blockZ, ambientHeight);
    }

    @Nullable
    private ColumnProfile findProfileFromCandidates(
        int blockX,
        int blockZ,
        double ambientHeight
    )
    {
        planCandidateLeaves(blockX, blockZ);
        return findProfileFromPlannedCandidates(blockX, blockZ, ambientHeight);
    }

    @Nullable
    private ColumnProfile findProfileFromPlannedCandidates(
        int blockX,
        int blockZ,
        double ambientHeight
    )
    {
        NTEHeadwaterNetwork.Sample nearest = null;
        for (RiverEdge edge : candidateEdges(blockX, blockZ))
        {
            if (edge.sourceEdge() || !headwaters.mayInfluence(edge, blockX, blockZ))
            {
                continue;
            }
            final NTEHeadwaterNetwork.Sample sample = headwaters.sampleUnindexedIfPlanned(
                edge,
                blockX,
                blockZ,
                ambientHeight
            );
            if (NTEHeadwaterNetwork.samplePreferred(sample, nearest))
            {
                nearest = sample;
            }
        }
        final NTEHeadwaterNetwork.Sample spatial = headwaters.samplePlannedAt(blockX, blockZ, ambientHeight);
        if (NTEHeadwaterNetwork.samplePreferred(spatial, nearest))
        {
            nearest = spatial;
        }
        return nearest == null ? null : createProfile(nearest);
    }

    /**
     * Only the local TFC partition is allowed to initiate route planning.
     * Already-planned terrain-aware routes are found through the headwater
     * spatial index, including portions that have left their original
     * partition. Broad neighboring-partition scans multiply route planning and
     * made otherwise unrelated chunk generation contend on the same cache.
     */
    private List<RiverEdge> candidateEdges(int blockX, int blockZ)
    {
        return partitionLookup.find(blockX, blockZ).rivers();
    }

    private void planCandidateLeaves(int blockX, int blockZ)
    {
        for (RiverEdge edge : candidateEdges(blockX, blockZ))
        {
            if (!edge.sourceEdge() && headwaters.mayInfluence(edge, blockX, blockZ))
            {
                headwaters.ensurePlanned(edge);
            }
        }
    }

    private ColumnProfile createProfile(NTEHeadwaterNetwork.Sample sample)
    {
        if (sample.subterranean())
        {
            // A covered section is the open-air creek with a roof, not a second kind of creek:
            // the bed cross-section, the receiver transition and its weights are the very same
            // fields, computed by the very same shape function. The roof only keeps the covered
            // descent supplied by source-like water and adds its ceiling for the cavity carve.
            final double roofedWaterDepth = supplementalLocalDepth(
                baseCenterDepth(sample.channelRadius()),
                sample.extraIncision(),
                sample.normalizedDistanceSq()
            );
            final double roofedWaterSurfaceY = sample.waterSurfaceY() - sample.mouthWaterDrop();
            return new ColumnProfile(
                roofedWaterSurfaceY,
                roofedWaterSurfaceY - roofedWaterDepth,
                roofedWaterSurfaceY - roofedWaterDepth,
                sample.normalizedDistanceSq(),
                sample.channelRadius(),
                0d,
                Math.max(0d, sample.entranceCut()),
                sample.mouthWaterDrop(),
                sample.bankFillWeight(),
                sample.waterCoreRadiusSq(),
                sample.fillAllowed(),
                sample.waterAllowed(),
                true,
                sample.receiverBlendWeight(),
                sample.receiverBedBlendWeight(),
                sample.waterfallLanding(),
                sample.headwater(),
                sample.channelRadius() < 3.5d ? ChannelKind.STREAM : ChannelKind.TRIBUTARY,
                ChannelMode.SUBTERRANEAN,
                sample.tunnelCeilingY(),
                sample.caveSeed(),
                sample.flow()
            );
        }
        final double baseCenterDepth = baseCenterDepth(sample.channelRadius());
        final double centerDepth = baseCenterDepth + sample.extraIncision();
        final double centerBedY = sample.waterSurfaceY() - centerDepth;
        final double localDepth = supplementalLocalDepth(
            baseCenterDepth,
            sample.extraIncision(),
            sample.normalizedDistanceSq()
        );
        final double bedY = sample.waterSurfaceY() - localDepth;
        // The fan erosion and the source-water surface descend together in the
        // ordinary water core. This preserves the already validated absolute
        // bed cut while preventing the old 2 -> 3+ block depth jump and raised
        // water shelf at the ownership boundary.
        final double waterSurfaceY = sample.waterSurfaceY() - sample.mouthWaterDrop();
        final ChannelKind kind = sample.channelRadius() < 3.5d ? ChannelKind.STREAM : ChannelKind.TRIBUTARY;

        return new ColumnProfile(
            waterSurfaceY,
            centerBedY,
            bedY,
            sample.normalizedDistanceSq(),
            sample.channelRadius(),
            0d,
            sample.extraIncision(),
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
            kind,
            ChannelMode.SURFACE,
            0d,
            0L,
            sample.flow()
        );
    }

    static double baseCenterDepth(double channelRadius)
    {
        return Mth.clamp(1.1d + channelRadius * 0.22d, 1.25d, 2.75d);
    }

    /**
     * Shape the ordinary channel first, then apply the already laterally
     * feathered mouth incision once. Applying the fan incision before the
     * cross-section taper attenuated it twice and preserved a raised rim at
     * cut-only receiver mouths.
     */
    static double supplementalLocalDepth(
        double baseCenterDepth,
        double extraIncision,
        double normalizedDistanceSq
    )
    {
        final double crossSection = Mth.clamp(
            normalizedDistanceSq / SUPPLEMENTAL_WATER_CORE_RADIUS_SQ,
            0d,
            1d
        );
        final double baseLocalDepth = Math.max(1d, baseCenterDepth * (1d - crossSection * crossSection));
        return baseLocalDepth + extraIncision;
    }

    public static double wideShapeWeight(double channelRadius)
    {
        final double progress = Mth.clamp((channelRadius - 3d) / 1d, 0d, 1d);
        return progress * progress * (3d - 2d * progress);
    }

    public boolean retainsTfcEdge(RiverEdge edge)
    {
        if (readOnlyHeightQueryActive())
        {
            return retainsTfcEdgeIfPlanned(edge);
        }
        return edge.sourceEdge() || !headwaters.replaces(edge);
    }

    private boolean retainsTfcEdgeIfPlanned(RiverEdge edge)
    {
        if (edge.sourceEdge())
        {
            return true;
        }
        final Boolean replacement = headwaters.replacementIfPlanned(edge);
        return replacement == null || !replacement;
    }

    private RiverInfo adaptRetainedLeafWidth(
        RiverInfo info,
        int blockX,
        int blockZ,
        boolean allowPlanning
    )
    {
        final RiverEdge edge = info.edge();
        if (edge == null || edge.sourceEdge())
        {
            return info;
        }
        final double widthScale = allowPlanning
            ? headwaters.retainedLeafWidthScale(edge, blockX, blockZ)
            : headwaters.retainedLeafWidthScaleIfPlanned(edge, blockX, blockZ);
        if (widthScale >= 0.999999d)
        {
            return info;
        }
        return new RiverInfo(info.edge(), info.flow(), info.distSq(), info.widthSq() * widthScale * widthScale);
    }

    private double sampleTerrainHeight(int blockX, int blockZ)
    {
        final long key = ((long) blockX << 32) ^ (blockZ & 0xffffffffL);
        synchronized (heightCache)
        {
            final Double cached = heightCache.get(key);
            if (cached != null)
            {
                return cached;
            }
        }

        // Do not hold the cache lock while the height filler performs nested biome queries.
        final double height = terrainHeightSampler.sample(blockX, blockZ);
        synchronized (heightCache)
        {
            final Double raced = heightCache.get(key);
            if (raced != null)
            {
                return raced;
            }
            if (heightCache.size() >= MAX_HEIGHT_CACHE_SIZE)
            {
                final Iterator<Long> oldest = heightCache.keySet().iterator();
                if (oldest.hasNext())
                {
                    oldest.next();
                    oldest.remove();
                }
            }
            heightCache.put(key, height);
        }
        return height;
    }
}
