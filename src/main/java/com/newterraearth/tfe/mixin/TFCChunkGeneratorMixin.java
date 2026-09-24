package com.newterraearth.tfe.mixin;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.ToIntFunction;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.mixin.accessor.ChunkAccessAccessor;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.world.BiomeNoiseSampler;
import net.dries007.tfc.world.ChunkBaseBlockSource;
import net.dries007.tfc.world.ChunkBiomeSampler;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import net.dries007.tfc.world.ChunkHeightFiller;
import net.dries007.tfc.world.ChunkNoiseFiller;
import net.dries007.tfc.world.FastConcurrentCache;
import net.dries007.tfc.world.TFCAquifer;
import net.dries007.tfc.world.TFCChunkGenerator;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.dries007.tfc.world.biome.TFCBiomes;
import net.dries007.tfc.world.chunkdata.ChunkData;
import net.dries007.tfc.world.chunkdata.ChunkDataProvider;
import net.dries007.tfc.world.layer.framework.ConcurrentArea;
import net.dries007.tfc.world.noise.ChunkNoiseSamplingSettings;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.noise.NoiseSampler;
import net.dries007.tfc.world.noise.OpenSimplex2D;
import net.dries007.tfc.world.region.RegionGenerator;
import net.dries007.tfc.world.river.Flow;
import net.dries007.tfc.world.river.RiverBlendType;
import net.dries007.tfc.world.river.RiverNoiseSampler;
import net.dries007.tfc.world.surface.SurfaceManager;

import com.newterraearth.tfe.debug.NTERuntimeTrace;
import com.newterraearth.tfe.world.NTEChunkShoreContext;
import com.newterraearth.tfe.world.NTEChunkHeightFillerAccess;
import com.newterraearth.tfe.world.NTESeed;
import com.newterraearth.tfe.world.NTESurfaceContext;
import com.newterraearth.tfe.world.forest.NTE121ForestHelpers;
import com.newterraearth.tfe.world.region.NTERegionGeneratorAccess;
import com.newterraearth.tfe.world.biome.NTERiverBiomeResolver;
import com.newterraearth.tfe.world.river.NTERiverBlendType;
import com.newterraearth.tfe.world.river.NTERiverCaveProtection;
import com.newterraearth.tfe.world.river.NTERiverHydrology;
import com.newterraearth.tfe.world.river.NTERiverNoiseSampler;
import com.newterraearth.tfe.world.shore.NTEShoreBlendType;
import com.newterraearth.tfe.world.shore.NTEShoreNoiseHelpers;
import com.newterraearth.tfe.world.shore.NTEShoreNoiseSampler;
import com.newterraearth.tfe.world.terrain.NTETerrainUpliftSampler;
import com.newterraearth.tfe.world.volcano.NTECenteredFeatureBlendType;
import com.newterraearth.tfe.world.volcano.NTECenteredFeatureNoiseSampler;

import static net.dries007.tfc.world.TFCChunkGenerator.SEA_LEVEL_Y;

@Mixin(TFCChunkGenerator.class)
public abstract class TFCChunkGeneratorMixin
{
    @Unique private static final int TFE_WATER_HORIZONTAL_RANGE = 7;
    @Unique private static final int TFE_MAX_AMBIENT_HEIGHT_FILLERS = 2048;
    @Shadow(remap = false) @Final private Holder<NoiseGeneratorSettings> noiseSettings;
    @Shadow(remap = false) @Final private FastConcurrentCache<TFCAquifer> aquiferCache;
    @Shadow(remap = false) private BiomeSourceExtension customBiomeSource;
    @Shadow(remap = false) private long noiseSamplerSeed;
    @Shadow(remap = false) private SurfaceManager surfaceManager;
    @Shadow(remap = false) private NoiseSampler noiseSampler;
    @Shadow(remap = false) private ChunkDataProvider chunkDataProvider;
    @Unique private Noise2D tfe$tideHeightNoise;
    @Unique private Noise2D tfe$legacyShoreNoise;
    @Unique private volatile NTERiverHydrology tfe$riverHydrology;
    @Unique private volatile NTETerrainUpliftSampler tfe$ambientTerrainUpliftSampler;
    @Unique private final ConcurrentHashMap<Long, CompletableFuture<ChunkHeightFiller>> tfe$ambientHeightFillers = new ConcurrentHashMap<>();
    @Unique private final ArrayDeque<Long> tfe$ambientHeightFillerOrder = new ArrayDeque<>();
    @Unique private final Map<Long, NTERiverHydrology.ColumnProfile[]> tfe$riverProfilesByChunk = new ConcurrentHashMap<>();
    @Unique private final Map<Long, NTERiverCaveProtection.Geometry> tfe$riverCaveProtectionByChunk = new ConcurrentHashMap<>();
    @Unique private final ThreadLocal<NTERiverCaveProtection.Scope> tfe$riverCarverProtection = new ThreadLocal<>();

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void tfe$activateHydrologyForLateDecoration(
        WorldGenLevel level,
        ChunkAccess chunk,
        StructureManager structureManager,
        CallbackInfo ci
    )
    {
        NTERiverHydrology.activateGeneration(
            tfe$getOrCreateRiverHydrology(),
            tfe$riverProfilesByChunk.get(chunk.getPos().toLong()),
            chunk.getPos().getMinBlockX(),
            chunk.getPos().getMinBlockZ()
        );
    }

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void tfe$releaseHydrologyAfterLateDecoration(
        WorldGenLevel level,
        ChunkAccess chunk,
        StructureManager structureManager,
        CallbackInfo ci
    )
    {
        tfe$riverProfilesByChunk.remove(chunk.getPos().toLong());
        tfe$riverCaveProtectionByChunk.remove(chunk.getPos().toLong());
        NTERiverHydrology.clearActiveGeneration();
    }

    @Redirect(
        method = "applyBiomeDecoration",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/FeatureSorter$StepFeatureData;indexMapping()Ljava/util/function/ToIntFunction;"
        )
    )
    private ToIntFunction<PlacedFeature> tfe$skipUnindexedDecorationFeatures(FeatureSorter.StepFeatureData step)
    {
        final ToIntFunction<PlacedFeature> indexMapping = step.indexMapping();
        final List<PlacedFeature> features = step.features();
        return feature -> {
            final int index = indexMapping.applyAsInt(feature);
            return index >= 0 && index < features.size() && features.get(index) == feature ? index : -1;
        };
    }

    @Inject(method = "initRandomState", at = @At("TAIL"), remap = false)
    private void tfe$registerRiverBiomeHydrology(net.minecraft.server.level.ChunkMap chunkMap, net.minecraft.server.level.ServerLevel level, CallbackInfo ci)
    {
        tfe$getOrCreateRiverHydrology();
    }

    /**
     * Structure location, map previews and LOD/pre-generation mods use this
     * method as a read-only terrain probe. Scope only the actual height sample
     * so an exception cannot leak the query mode into the next server task.
     */
    @Redirect(
        method = "getBaseHeight",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/world/ChunkHeightFiller;sampleHeight(II)D",
            remap = false
        )
    )
    private double tfe$sampleReadOnlyBaseHeight(ChunkHeightFiller filler, int x, int z)
    {
        NTERiverHydrology.beginReadOnlyHeightQuery();
        try
        {
            return filler.sampleHeight(x, z);
        }
        finally
        {
            NTERiverHydrology.endReadOnlyHeightQuery();
        }
    }

    @Redirect(
        method = "applyBiomeDecoration",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/ints/IntSet;add(I)Z",
            remap = false
        )
    )
    private boolean tfe$addDecorationFeatureIndexIfValid(IntSet indices, int index)
    {
        return index >= 0 && indices.add(index);
    }

    @Inject(method = "applyCarvers", at = @At("HEAD"))
    private void tfe$protectShallowSupplementalRiverTerrainBeforeCarvers(
        WorldGenRegion level,
        long seed,
        RandomState randomState,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunk,
        GenerationStep.Carving step,
        CallbackInfo ci
    )
    {
        if (step == GenerationStep.Carving.AIR)
        {
            final NTERiverHydrology riverHydrology = tfe$getOrCreateRiverHydrology();
            NTERiverHydrology.activateGeneration(
                riverHydrology,
                tfe$riverProfilesByChunk.get(chunk.getPos().toLong()),
                chunk.getPos().getMinBlockX(),
                chunk.getPos().getMinBlockZ()
            );
            final NTERiverCaveProtection.Scope previous = tfe$riverCarverProtection.get();
            if (previous != null)
            {
                previous.close();
            }
            final NTERiverCaveProtection.Geometry geometry = tfe$riverCaveProtectionByChunk.computeIfAbsent(
                chunk.getPos().toLong(),
                ignored -> NTERiverCaveProtection.plan(riverHydrology, chunk.getPos())
            );
            tfe$riverCarverProtection.set(NTERiverCaveProtection.openCarvers(geometry, chunk));
        }
    }

    /**
     * Bake vertical falling-water columns before the chunk leaves CARVERS.
     * Scheduled ticks remain only for stabilization; they no longer create a
     * waterfall for the first time when a player eventually loads the chunk.
     */
    @Inject(method = "applyCarvers", at = @At("TAIL"))
    private void tfe$finishSupplementalRiverCarving(
        WorldGenRegion level,
        long seed,
        RandomState randomState,
        BiomeManager biomeManager,
        StructureManager structureManager,
        ChunkAccess chunk,
        GenerationStep.Carving step,
        CallbackInfo ci
    )
    {
        if (step != GenerationStep.Carving.AIR)
        {
            return;
        }

        final ChunkPos chunkPos = chunk.getPos();
        final NTERiverHydrology.ColumnProfile[] noiseProfiles = tfe$riverProfilesByChunk.get(chunkPos.toLong());
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        try
        {
            tfe$trimRetainedWaterAbovePlannedMouth(chunk, noiseProfiles, cursor);
            tfe$bakeSupplementalWaterfalls(level, chunk, noiseProfiles, cursor);
            for (int localX = 0; localX < 16; localX++)
            {
                final int blockX = chunkPos.getBlockX(localX);
                for (int localZ = 0; localZ < 16; localZ++)
                {
                    final int blockZ = chunkPos.getBlockZ(localZ);
                    final NTERiverHydrology.ColumnProfile profile = tfe$profileForCarver(
                        chunkPos,
                        noiseProfiles,
                        blockX,
                        blockZ
                    );
                    if (profile == null || !profile.inWaterCore())
                    {
                        continue;
                    }

                    final int waterY = profile.waterBlockY();
                    if (waterY >= chunk.getMinBuildHeight() && waterY < chunk.getMaxBuildHeight())
                    {
                        cursor.set(blockX, waterY, blockZ);
                        final FluidState fluid = chunk.getFluidState(cursor);
                        if (fluid.is(FluidTags.WATER))
                        {
                            level.scheduleTick(cursor.immutable(), fluid.getType(), 5);
                        }
                    }
                }
            }
        }
        finally
        {
            final NTERiverCaveProtection.Scope scope = tfe$riverCarverProtection.get();
            if (scope != null)
            {
                scope.close();
                tfe$riverCarverProtection.remove();
            }
            NTERiverHydrology.clearActiveGeneration();
        }
    }

    @Unique
    private void tfe$trimRetainedWaterAbovePlannedMouth(
        ChunkAccess chunk,
        @Nullable NTERiverHydrology.ColumnProfile[] noiseProfiles,
        BlockPos.MutableBlockPos cursor
    )
    {
        if (noiseProfiles == null)
        {
            return;
        }
        final ChunkPos chunkPos = chunk.getPos();
        for (int localX = 0; localX < 16; localX++)
        {
            final int blockX = chunkPos.getBlockX(localX);
            for (int localZ = 0; localZ < 16; localZ++)
            {
                final NTERiverHydrology.ColumnProfile profile = noiseProfiles[localX + 16 * localZ];
                if (profile == null || profile.receiverBlendWeight() <= 0d || !profile.inChannel())
                {
                    continue;
                }
                final int blockZ = chunkPos.getBlockZ(localZ);
                final int plannedWaterY = profile.waterBlockY();
                // Surface generation can reintroduce the retained TFC source
                // shelf after the density pass. A raised waterfall landing
                // may hand its top layer to the following dynamic fall bake,
                // but y=62 is the receiver's fixed minimum water layer and
                // must never be included in this cleanup.
                final int clearFromY = NTERiverHydrology.retainedMouthWaterClearFromY(
                    profile,
                    SEA_LEVEL_Y - 1
                );
                for (int y = clearFromY; y <= plannedWaterY + 8; y++)
                {
                    cursor.set(blockX, y, blockZ);
                    final BlockState state = chunk.getBlockState(cursor);
                    if (state.getFluidState().is(FluidTags.WATER))
                    {
                        chunk.setBlockState(cursor, Blocks.AIR.defaultBlockState(), false);
                    }
                    else if (!state.isAir())
                    {
                        break;
                    }
                }
            }
        }
    }

    @Unique
    private void tfe$bakeSupplementalWaterfalls(
        WorldGenRegion level,
        ChunkAccess chunk,
        @Nullable NTERiverHydrology.ColumnProfile[] noiseProfiles,
        BlockPos.MutableBlockPos cursor
    )
    {
        if (noiseProfiles == null)
        {
            return;
        }
        final ChunkPos chunkPos = chunk.getPos();
        final ArrayDeque<BlockPos> spillFronts = new ArrayDeque<>();
        final ArrayDeque<Integer> flowLevels = new ArrayDeque<>();
        final ArrayDeque<Flow> spillFlows = new ArrayDeque<>();
        final Set<Long> queuedFronts = new HashSet<>();
        for (int localX = 0; localX < 16; localX++)
        {
            final int blockX = chunkPos.getBlockX(localX);
            for (int localZ = 0; localZ < 16; localZ++)
            {
                final int blockZ = chunkPos.getBlockZ(localZ);
                final NTERiverHydrology.ColumnProfile targetProfile = noiseProfiles[localX + 16 * localZ];
                if (targetProfile == null || !targetProfile.inWaterCore())
                {
                    continue;
                }

                final int waterY = targetProfile.waterBlockY();
                cursor.set(blockX, waterY, blockZ);
                final FluidState sourceFluid = chunk.getFluidState(cursor);
                if (!sourceFluid.is(FluidTags.WATER) || !sourceFluid.isSource())
                {
                    continue;
                }

                // Only the planned downstream face may seed a pre-baked fall.
                // Branching over all four faces turns a widened mouth into a
                // rectangular water platform before normal fluid ticks begin.
                final NTERiverHydrology.CardinalStep offset = tfe$selectFlowSpillDirection(
                    chunk,
                    chunkPos,
                    blockX,
                    waterY,
                    blockZ,
                    targetProfile.flow(),
                    1,
                    cursor
                );
                if (offset != null)
                {
                    final int nextLocalX = localX + offset.x();
                    final int nextLocalZ = localZ + offset.z();
                    if (nextLocalX < 0 || nextLocalX >= 16 || nextLocalZ < 0 || nextLocalZ >= 16)
                    {
                        continue;
                    }

                    final int nextX = blockX + offset.x();
                    final int nextZ = blockZ + offset.z();
                    cursor.set(nextX, waterY, nextZ);
                    final BlockState destination = chunk.getBlockState(cursor);
                    final FluidState destinationFluid = destination.getFluidState();
                    if (!destination.isAir()
                        && (!destinationFluid.is(FluidTags.WATER) || destinationFluid.isSource()))
                    {
                        continue;
                    }

                    final long frontKey = BlockPos.asLong(nextX, waterY, nextZ);
                    if (queuedFronts.add(frontKey))
                    {
                        spillFronts.addLast(new BlockPos(nextX, waterY, nextZ));
                        flowLevels.addLast(1);
                        spillFlows.addLast(targetProfile.flow());
                    }
                }
            }
        }

        tfe$bakeSettledSpills(level, chunk, chunkPos, spillFronts, flowLevels, spillFlows, cursor);
    }

    /**
     * Pre-bake the small part of vanilla water settling that cannot happen
     * until a new chunk starts ticking: fall through open air, spread over a
     * ledge with decaying levels, then continue falling from the ledge edge.
     * The bounded queue prevents a creek waterfall from becoming a cave flood.
     */
    @Unique
    private void tfe$bakeSettledSpills(
        WorldGenRegion level,
        ChunkAccess chunk,
        ChunkPos chunkPos,
        ArrayDeque<BlockPos> fronts,
        ArrayDeque<Integer> flowLevels,
        ArrayDeque<Flow> flows,
        BlockPos.MutableBlockPos cursor
    )
    {
        final Set<Long> visited = new HashSet<>();

        int processed = 0;
        while (!fronts.isEmpty() && processed++ < 512)
        {
            final BlockPos front = fronts.removeFirst();
            final int flowLevel = flowLevels.removeFirst();
            final Flow flow = flows.removeFirst();
            if (flowLevel > TFE_WATER_HORIZONTAL_RANGE
                || front.getX() < chunkPos.getMinBlockX() || front.getX() > chunkPos.getMaxBlockX()
                || front.getZ() < chunkPos.getMinBlockZ() || front.getZ() > chunkPos.getMaxBlockZ()
                || !visited.add(front.asLong()))
            {
                continue;
            }

            final int landingY = tfe$bakeFlowingDrop(
                level,
                chunk,
                front.getX(),
                front.getZ(),
                front.getY(),
                Math.max(chunk.getMinBuildHeight(), front.getY() - 64),
                flowLevel,
                flow,
                cursor
            );
            if (landingY == Integer.MIN_VALUE || flowLevel >= TFE_WATER_HORIZONTAL_RANGE)
            {
                continue;
            }

            final NTERiverHydrology.CardinalStep offset = tfe$selectFlowSpillDirection(
                chunk,
                chunkPos,
                front.getX(),
                landingY,
                front.getZ(),
                flow,
                flowLevel + 1,
                cursor
            );
            if (offset != null)
            {
                fronts.addLast(new BlockPos(front.getX() + offset.x(), landingY, front.getZ() + offset.z()));
                flowLevels.addLast(flowLevel + 1);
                flows.addLast(flow);
            }
        }
    }

    @Unique
    @Nullable
    private NTERiverHydrology.CardinalStep tfe$selectFlowSpillDirection(
        ChunkAccess chunk,
        ChunkPos chunkPos,
        int blockX,
        int blockY,
        int blockZ,
        Flow flow,
        int horizontalStep,
        BlockPos.MutableBlockPos cursor
    )
    {
        final NTERiverHydrology.CardinalStep offset = NTERiverHydrology.cardinalFlowStep(flow, horizontalStep);
        if (offset.x() == 0 && offset.z() == 0)
        {
            return null;
        }
        final int nextX = blockX + offset.x();
        final int nextZ = blockZ + offset.z();
        return tfe$isInsideChunk(chunkPos, nextX, nextZ)
            && tfe$canSpillInto(chunk, nextX, blockY, nextZ, cursor)
            ? offset
            : null;
    }

    @Unique
    private boolean tfe$canSpillInto(
        ChunkAccess chunk,
        int blockX,
        int blockY,
        int blockZ,
        BlockPos.MutableBlockPos cursor
    )
    {
        cursor.set(blockX, blockY, blockZ);
        final BlockState state = chunk.getBlockState(cursor);
        return state.isAir()
            || state.getFluidState().is(FluidTags.WATER) && !state.getFluidState().isSource();
    }

    @Unique
    private static boolean tfe$isInsideChunk(ChunkPos chunkPos, int blockX, int blockZ)
    {
        return blockX >= chunkPos.getMinBlockX() && blockX <= chunkPos.getMaxBlockX()
            && blockZ >= chunkPos.getMinBlockZ() && blockZ <= chunkPos.getMaxBlockZ();
    }

    @Unique
    private int tfe$bakeFlowingDrop(
        WorldGenRegion level,
        ChunkAccess chunk,
        int blockX,
        int blockZ,
        int startY,
        int minimumY,
        int flowLevel,
        Flow flow,
        BlockPos.MutableBlockPos cursor
    )
    {
        final BlockState fallingWater = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        final BlockState spreadingWater = Blocks.WATER.defaultBlockState().setValue(
            LiquidBlock.LEVEL,
            Math.min(7, Math.max(1, flowLevel))
        );
        for (int y = startY; y > minimumY; y--)
        {
            cursor.set(blockX, y, blockZ);
            final BlockState current = chunk.getBlockState(cursor);
            if (current.getFluidState().is(FluidTags.WATER) && current.getFluidState().isSource())
            {
                return Integer.MIN_VALUE;
            }
            if (!current.isAir() && !current.getFluidState().is(FluidTags.WATER))
            {
                return Integer.MIN_VALUE;
            }

            cursor.set(blockX, y - 1, blockZ);
            final BlockState below = chunk.getBlockState(cursor);
            final boolean landsHere = !below.isAir() && below.getFluidState().isEmpty();
            cursor.set(blockX, y, blockZ);
            final boolean joinsDirectionalReceiver = y <= SEA_LEVEL_Y - 1
                || below.getFluidState().getType() == TFCFluids.RIVER_WATER.get();
            final BlockState water = joinsDirectionalReceiver
                ? NTERiverHydrology.directionalRiverWaterState(flow)
                : landsHere ? spreadingWater : fallingWater;
            chunk.setBlockState(cursor, water, false);
            if (!joinsDirectionalReceiver)
            {
                level.scheduleTick(cursor.immutable(), water.getFluidState().getType(), 1);
            }
            if (landsHere)
            {
                return y;
            }
            if (joinsDirectionalReceiver || below.getFluidState().is(FluidTags.WATER))
            {
                return Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    @Unique
    @Nullable
    private NTERiverHydrology.ColumnProfile tfe$profileForCarver(
        ChunkPos currentChunk,
        @Nullable NTERiverHydrology.ColumnProfile[] localProfiles,
        int blockX,
        int blockZ
    )
    {
        if (localProfiles != null && blockX >= currentChunk.getMinBlockX() && blockX <= currentChunk.getMaxBlockX()
            && blockZ >= currentChunk.getMinBlockZ() && blockZ <= currentChunk.getMaxBlockZ())
        {
            return localProfiles[(blockX - currentChunk.getMinBlockX()) + 16 * (blockZ - currentChunk.getMinBlockZ())];
        }
        // Carver completion may inspect one column across the chunk border.
        // Starting a complete headwater search from that bookkeeping lookup is
        // both unnecessary and disastrous under parallel world generation.
        return null;
    }

    @Redirect(
        method = "initRandomState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/world/biome/BiomeSourceExtension;initRandomState(Lnet/dries007/tfc/world/region/RegionGenerator;Lnet/dries007/tfc/world/layer/framework/ConcurrentArea;)V"
        ),
        remap = false,
        require = 0
    )
    private void tfe$alignExtraRegionNoiseToRootSeed(
        BiomeSourceExtension biomeSource,
        RegionGenerator regionGenerator,
        ConcurrentArea<BiomeExtension> biomeLayer,
        net.minecraft.server.level.ChunkMap chunkMap,
        net.minecraft.server.level.ServerLevel level
    )
    {
        ((NTERegionGeneratorAccess) regionGenerator).nte$setRootLevelSeed(level.getSeed());
        biomeSource.initRandomState(regionGenerator, biomeLayer);
    }

    /**
     * @author Codex
     * @reason Inject 1.21-style shoreline samplers and tide noise into height sampling.
     */
    @Overwrite(remap = false)
    public ChunkHeightFiller createHeightFillerForChunk(ChunkPos pos)
    {
        final Object2DoubleMap<BiomeExtension>[] biomeWeights = ChunkBiomeSampler.sampleBiomes(pos, this::tfe$sampleBiomeNoRiver, BiomeExtension::biomeBlendType);
        final NTETerrainUpliftSampler terrainUpliftSampler = tfe$createTerrainUpliftSampler();
        final NTERiverHydrology riverHydrology = tfe$getOrCreateRiverHydrology();
        try (NTEChunkShoreContext.Scope ignored = NTEChunkShoreContext.open(
            tfe$createShoreSamplersForChunk(),
            tfe$createTideHeightNoise(),
            tfe$createExactRiverSamplersForChunk(),
            this::tfe$createExactRiverSamplersForChunk,
            tfe$createCenteredFeatureSamplersForChunk(),
            terrainUpliftSampler,
            riverHydrology,
            false))
        {
            return new ChunkHeightFiller(
                biomeWeights,
                customBiomeSource,
                tfe$createBiomeSamplersForChunk(null),
                tfe$createRiverSamplersForChunk(),
                tfe$createLegacyShoreSamplerForChunk(),
                ((TFCChunkGenerator) (Object) this).getSeaLevel()
            );
        }
    }

    /**
     * @author Codex
     * @reason Inject 1.21 shoreline runtime into the 1.20 terrain fill path without changing TFC constructor signatures.
     */
    @Overwrite
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor mainExecutor, Blender oldTerrainBlender, RandomState rawState, StructureManager structureFeatureManager, ChunkAccess chunk)
    {
        final ChunkNoiseSamplingSettings settings = tfe$createNoiseSamplingSettingsForChunk(chunk);
        final LevelAccessor actualLevel = (LevelAccessor) ((ChunkAccessAccessor) chunk).accessor$getLevelHeightAccessor();
        final ChunkPos chunkPos = chunk.getPos();
        final NTERiverHydrology riverHydrology = tfe$getOrCreateRiverHydrology();
        final RandomSource random = new XoroshiroRandomSource(chunkPos.x * 1842639486192314L, chunkPos.z * 579238196380231L);
        final ChunkData chunkData = chunkDataProvider.get(chunk);
        tfe$syncForestType121(chunkData);
        final Object2DoubleMap<BiomeExtension>[] biomeWeights = ChunkBiomeSampler.sampleBiomes(chunkPos, this::tfe$sampleBiomeNoRiver, BiomeExtension::biomeBlendType);
        final Beardifier beardifier = Beardifier.forStructuresInChunk(structureFeatureManager, chunkPos);

        final Set<LevelChunkSection> sections = new HashSet<>();
        for (LevelChunkSection section : chunk.getSections())
        {
            section.acquire();
            sections.add(section);
        }

        final ChunkNoiseFiller[] fillerHolder = new ChunkNoiseFiller[1];
        final BiomeExtension[] cinderConeBiomeHolder = new BiomeExtension[1];
        final BiomeExtension[] tuffRingBiomeHolder = new BiomeExtension[1];
        final BiomeExtension[] tuyaBiomeHolder = new BiomeExtension[1];
        final BiomeExtension[] atollBiomeHolder = new BiomeExtension[1];
        final BiomeExtension[] stratovolcanoBiomeHolder = new BiomeExtension[1];

        return CompletableFuture.supplyAsync(() -> {
            final ChunkBaseBlockSource baseBlockSource = tfe$createBaseBlockSourceForChunk(chunk, chunkData);
            final Map<NTEShoreBlendType, NTEShoreNoiseSampler> exactShoreSamplers = tfe$createShoreSamplersForChunk();
            final Noise2D tideHeightNoise = tfe$createTideHeightNoise();
            final Map<NTERiverBlendType, NTERiverNoiseSampler> exactRiverSamplers = tfe$createExactRiverSamplersForChunk();
            final Map<NTECenteredFeatureBlendType, NTECenteredFeatureNoiseSampler> centeredFeatureSamplers = tfe$createCenteredFeatureSamplersForChunk();
            final NTETerrainUpliftSampler terrainUpliftSampler = tfe$createTerrainUpliftSampler();

            final ChunkNoiseFiller filler;
            try (NTEChunkShoreContext.Scope ignored = NTEChunkShoreContext.open(exactShoreSamplers, tideHeightNoise, exactRiverSamplers, this::tfe$createExactRiverSamplersForChunk, centeredFeatureSamplers, terrainUpliftSampler, tfe$getOrCreateRiverHydrology(), false))
            {
                filler = new ChunkNoiseFiller(
                    (ProtoChunk) chunk,
                    biomeWeights,
                    customBiomeSource,
                    tfe$createBiomeSamplersForChunk(chunk),
                    tfe$createRiverSamplersForChunk(),
                    tfe$createLegacyShoreSamplerForChunk(),
                    noiseSampler,
                    baseBlockSource,
                    settings,
                    ((TFCChunkGenerator) (Object) this).getSeaLevel(),
                    beardifier
                );
            }

            fillerHolder[0] = filler;
            cinderConeBiomeHolder[0] = tfe$getCenteredFeatureBiome(centeredFeatureSamplers.get(NTECenteredFeatureBlendType.CINDER_CONE), chunkPos);
            tuffRingBiomeHolder[0] = tfe$getCenteredFeatureBiome(centeredFeatureSamplers.get(NTECenteredFeatureBlendType.TUFF_RING), chunkPos);
            tuyaBiomeHolder[0] = tfe$getCenteredFeatureBiome(centeredFeatureSamplers.get(NTECenteredFeatureBlendType.TUYA), chunkPos);
            atollBiomeHolder[0] = tfe$getCenteredFeatureBiome(centeredFeatureSamplers.get(NTECenteredFeatureBlendType.ATOLL), chunkPos);
            stratovolcanoBiomeHolder[0] = tfe$getCenteredFeatureBiome(centeredFeatureSamplers.get(NTECenteredFeatureBlendType.STRATOVOLCANO), chunkPos);

            filler.sampleAquiferSurfaceHeight(this::tfe$sampleBiomeNoRiver);
            chunkData.generateFull(filler.surfaceHeight(), filler.aquifer().surfaceHeights());
            chunkData.getRockData().useCache(chunkPos);
            final NTERiverCaveProtection.Geometry caveProtection = NTERiverCaveProtection.plan(riverHydrology, chunkPos);
            NTERiverHydrology.activateGeneration(
                riverHydrology,
                caveProtection.localProfiles(),
                chunkPos.getMinBlockX(),
                chunkPos.getMinBlockZ()
            );
            try (NTERiverCaveProtection.DensityScope ignored = NTERiverCaveProtection.openDensity(caveProtection))
            {
                filler.fillFromNoise();
            }
            finally
            {
                NTERiverHydrology.clearActiveGeneration();
            }

            if (tfe$riverProfilesByChunk.size() >= 1024)
            {
                tfe$riverProfilesByChunk.clear();
                tfe$riverCaveProtectionByChunk.clear();
            }
            tfe$riverProfilesByChunk.put(chunkPos.toLong(), tfe$copyRiverProfiles(filler));
            tfe$riverCaveProtectionByChunk.put(chunkPos.toLong(), caveProtection);

            aquiferCache.set(chunkPos.x, chunkPos.z, filler.aquifer());
            return chunk;
        }, Util.backgroundExecutor()).whenCompleteAsync((ret, error) -> {
            sections.forEach(LevelChunkSection::release);

            final ChunkNoiseFiller filler = fillerHolder[0];
            if (error != null || filler == null)
            {
                return;
            }

            try (NTESurfaceContext.Scope ignored = NTESurfaceContext.open(
                (TFCChunkGenerator) (Object) this,
                chunkData,
                filler.surfaceHeight(),
                ((NTEChunkHeightFillerAccess) filler).tfe$getPreVolcanicHeights(),
                chunkPos,
                cinderConeBiomeHolder[0],
                tuffRingBiomeHolder[0],
                tuyaBiomeHolder[0],
                atollBiomeHolder[0],
                stratovolcanoBiomeHolder[0],
                tfe$copyRiverProfiles(filler),
                ((NTEChunkHeightFillerAccess) filler).tfe$getNativeDryRiverBanks()
            ))
            {
                surfaceManager.buildSurface(
                    actualLevel,
                    chunk,
                    ((ChunkGeneratorExtension) (Object) this).rockLayerSettings(),
                    chunkData,
                    filler.localBiomes(),
                    filler.localBiomesNoRivers(),
                    filler.localBiomeWeights(),
                    filler.createSlopeMap(),
                    random,
                    ((TFCChunkGenerator) (Object) this).getSeaLevel(),
                    settings.minY()
                );
            }
        }, mainExecutor);
    }

    @Unique
    private BiomeExtension tfe$sampleBiomeNoRiver(int blockX, int blockZ)
    {
        return customBiomeSource.getBiomeExtensionNoRiver(QuartPos.fromBlock(blockX), QuartPos.fromBlock(blockZ));
    }

    @Unique
    private ChunkBaseBlockSource tfe$createBaseBlockSourceForChunk(ChunkAccess chunk, ChunkData chunkData)
    {
        return new ChunkBaseBlockSource(chunkData.getRockData(), this::tfe$sampleBiomeNoRiver);
    }

    @Unique
    private ChunkNoiseSamplingSettings tfe$createNoiseSamplingSettingsForChunk(ChunkAccess chunk)
    {
        return tfe$createNoiseSamplingSettingsForChunk(chunk.getPos(), chunk.getHeightAccessorForGeneration());
    }

    @Unique
    private ChunkNoiseSamplingSettings tfe$createNoiseSamplingSettingsForChunk(ChunkPos pos, LevelHeightAccessor level)
    {
        final NoiseSettings noiseSettings = this.noiseSettings.value().noiseSettings();

        final int cellWidth = noiseSettings.getCellWidth();
        final int cellHeight = noiseSettings.getCellHeight();

        final int minY = Math.max(noiseSettings.minY(), level.getMinBuildHeight());
        final int maxY = Math.min(noiseSettings.minY() + noiseSettings.height(), level.getMaxBuildHeight());

        final int cellCountY = Math.floorDiv(maxY - minY, noiseSettings.getCellHeight());

        final int firstCellX = Math.floorDiv(pos.getMinBlockX(), cellWidth);
        final int firstCellY = Math.floorDiv(minY, cellHeight);
        final int firstCellZ = Math.floorDiv(pos.getMinBlockZ(), cellWidth);

        return new ChunkNoiseSamplingSettings(minY, 16 / cellWidth, cellCountY, cellWidth, cellHeight, firstCellX, firstCellY, firstCellZ);
    }

    @Unique
    private Map<BiomeExtension, BiomeNoiseSampler> tfe$createBiomeSamplersForChunk(@Nullable ChunkAccess chunk)
    {
        final ImmutableMap.Builder<BiomeExtension, BiomeNoiseSampler> builder = ImmutableMap.builder();
        for (BiomeExtension extension : TFCBiomes.getExtensions())
        {
            final BiomeNoiseSampler sampler = extension.createNoiseSampler(noiseSamplerSeed);
            if (sampler != null)
            {
                sampler.prepare((TFCChunkGenerator) (Object) this, chunk);
                builder.put(extension, sampler);
            }
        }
        return builder.build();
    }

    @Unique
    private Map<RiverBlendType, RiverNoiseSampler> tfe$createRiverSamplersForChunk()
    {
        final EnumMap<RiverBlendType, RiverNoiseSampler> builder = new EnumMap<>(RiverBlendType.class);
        for (RiverBlendType blendType : RiverBlendType.ALL)
        {
            builder.put(blendType, blendType.createNoiseSampler(noiseSamplerSeed));
        }
        return builder;
    }

    @Unique
    private Map<NTERiverBlendType, NTERiverNoiseSampler> tfe$createExactRiverSamplersForChunk()
    {
        final NTESeed seed = NTESeed.of(noiseSamplerSeed);
        final EnumMap<NTERiverBlendType, NTERiverNoiseSampler> builder = new EnumMap<>(NTERiverBlendType.class);
        for (NTERiverBlendType blendType : NTERiverBlendType.ALL)
        {
            builder.put(blendType, blendType.createNoiseSampler(seed));
        }
        return builder;
    }

    @Unique
    private Map<NTEShoreBlendType, NTEShoreNoiseSampler> tfe$createShoreSamplersForChunk()
    {
        final NTESeed seed = NTESeed.unsafeOf(noiseSamplerSeed);
        final EnumMap<NTEShoreBlendType, NTEShoreNoiseSampler> builder = new EnumMap<>(NTEShoreBlendType.class);
        for (NTEShoreBlendType blendType : NTEShoreBlendType.ALL)
        {
            builder.put(blendType, blendType.createNoiseSampler(seed));
        }
        return builder;
    }

    @Unique
    private Noise2D tfe$createTideHeightNoise()
    {
        Noise2D tideHeightNoise = tfe$tideHeightNoise;
        if (tideHeightNoise == null)
        {
            tideHeightNoise = NTEShoreNoiseHelpers.shoreTideLevelNoise(NTESeed.unsafeOf(noiseSamplerSeed));
            tfe$tideHeightNoise = tideHeightNoise;
        }
        return tideHeightNoise;
    }

    @Unique
    private Noise2D tfe$createLegacyShoreSamplerForChunk()
    {
        Noise2D legacyShoreNoise = tfe$legacyShoreNoise;
        if (legacyShoreNoise == null)
        {
            legacyShoreNoise = new OpenSimplex2D(noiseSamplerSeed)
                .octaves(2)
                .spread(0.003f)
                .scaled(-0.1, 1.1);
            tfe$legacyShoreNoise = legacyShoreNoise;
        }
        return legacyShoreNoise;
    }

    @Unique
    private Map<NTECenteredFeatureBlendType, NTECenteredFeatureNoiseSampler> tfe$createCenteredFeatureSamplersForChunk()
    {
        final NTESeed seed = NTESeed.of(noiseSamplerSeed);
        final EnumMap<NTECenteredFeatureBlendType, NTECenteredFeatureNoiseSampler> builder = new EnumMap<>(NTECenteredFeatureBlendType.class);
        for (NTECenteredFeatureBlendType blendType : NTECenteredFeatureBlendType.ALL)
        {
            builder.put(blendType, blendType.createNoiseSampler(seed));
        }
        return builder;
    }

    @Unique
    private NTETerrainUpliftSampler tfe$createTerrainUpliftSampler()
    {
        return new NTETerrainUpliftSampler(noiseSamplerSeed, customBiomeSource);
    }

    @Unique
    private NTERiverHydrology tfe$getOrCreateRiverHydrology()
    {
        NTERiverHydrology hydrology = tfe$riverHydrology;
        if (hydrology == null)
        {
            synchronized (this)
            {
                hydrology = tfe$riverHydrology;
                if (hydrology == null)
                {
                    hydrology = new NTERiverHydrology(
                        noiseSamplerSeed,
                        ((TFCChunkGenerator) (Object) this).getSeaLevel(),
                        this::tfe$sampleAmbientTerrainHeight,
                        customBiomeSource::getPartition
                    );
                    NTERiverBiomeResolver.register(customBiomeSource, hydrology);
                    tfe$riverHydrology = hydrology;
                }
            }
        }
        return hydrology;
    }

    @Unique
    private double tfe$sampleAmbientTerrainHeight(int blockX, int blockZ)
    {
        final ChunkPos pos = new ChunkPos(blockX >> 4, blockZ >> 4);
        final long cacheKey = pos.toLong();
        try (NTERiverBiomeResolver.Scope ignoredBiomeRivers = NTERiverBiomeResolver.suppressRivers())
        {
            CompletableFuture<ChunkHeightFiller> future = tfe$ambientHeightFillers.get(cacheKey);
            if (future == null)
            {
                final CompletableFuture<ChunkHeightFiller> created = new CompletableFuture<>();
                future = tfe$ambientHeightFillers.putIfAbsent(cacheKey, created);
                if (future == null)
                {
                    future = created;
                    try
                    {
                        created.complete(tfe$createAmbientHeightFiller(pos));
                        tfe$recordCompletedAmbientHeightFiller(cacheKey, created);
                    }
                    catch (RuntimeException | Error exception)
                    {
                        created.completeExceptionally(exception);
                        tfe$ambientHeightFillers.remove(cacheKey, created);
                        throw exception;
                    }
                }
            }

            final ChunkHeightFiller filler = future.join();
            // ChunkHeightFiller reuses mutable per-column state. Only callers of
            // the same cached chunk serialize; unrelated chunks run in parallel.
            synchronized (filler)
            {
                final double sampled = filler.sampleHeight(blockX, blockZ);
                if (NTERuntimeTrace.isTargetColumn(blockX, blockZ))
                {
                    System.out.printf(
                        "[TFE][RuntimeTrace][planner_height] x=%d z=%d h=%.4f%n",
                        blockX,
                        blockZ,
                        sampled
                    );
                }
                return sampled;
            }
        }
    }

    @Unique
    private void tfe$recordCompletedAmbientHeightFiller(
        long cacheKey,
        CompletableFuture<ChunkHeightFiller> completed
    )
    {
        // Only completed fillers enter the FIFO, so eviction cannot discard a
        // chunk while another worldgen task is still constructing its sampler.
        synchronized (tfe$ambientHeightFillerOrder)
        {
            if (tfe$ambientHeightFillers.get(cacheKey) == completed)
            {
                tfe$ambientHeightFillerOrder.addLast(cacheKey);
            }
            while (tfe$ambientHeightFillers.size() > TFE_MAX_AMBIENT_HEIGHT_FILLERS
                && !tfe$ambientHeightFillerOrder.isEmpty())
            {
                final long oldestKey = tfe$ambientHeightFillerOrder.removeFirst();
                final CompletableFuture<ChunkHeightFiller> oldest = tfe$ambientHeightFillers.get(oldestKey);
                if (oldest != null && oldest.isDone())
                {
                    tfe$ambientHeightFillers.remove(oldestKey, oldest);
                }
            }
        }
    }

    @Unique
    private ChunkHeightFiller tfe$createAmbientHeightFiller(ChunkPos pos)
    {
        final Object2DoubleMap<BiomeExtension>[] biomeWeights = ChunkBiomeSampler.sampleBiomes(
            pos,
            this::tfe$sampleBiomeNoRiver,
            BiomeExtension::biomeBlendType
        );
        NTETerrainUpliftSampler terrainUpliftSampler = tfe$ambientTerrainUpliftSampler;
        if (terrainUpliftSampler == null)
        {
            terrainUpliftSampler = tfe$createTerrainUpliftSampler();
            tfe$ambientTerrainUpliftSampler = terrainUpliftSampler;
        }
        try (NTEChunkShoreContext.Scope ignored = NTEChunkShoreContext.open(
            tfe$createShoreSamplersForChunk(),
            tfe$createTideHeightNoise(),
            Collections.emptyMap(),
            Collections::emptyMap,
            tfe$createCenteredFeatureSamplersForChunk(),
            terrainUpliftSampler,
            tfe$riverHydrology,
            true))
        {
            return new ChunkHeightFiller(
                biomeWeights,
                customBiomeSource,
                tfe$createBiomeSamplersForChunk(null),
                tfe$createRiverSamplersForChunk(),
                tfe$createLegacyShoreSamplerForChunk(),
                ((TFCChunkGenerator) (Object) this).getSeaLevel()
            );
        }
    }

    @Unique
    private BiomeExtension tfe$getCenteredFeatureBiome(@Nullable NTECenteredFeatureNoiseSampler sampler, ChunkPos chunkPos)
    {
        if (sampler == null)
        {
            return null;
        }
        return sampler.getCenterBiome(chunkPos.getBlockX(8), chunkPos.getBlockZ(8), customBiomeSource);
    }

    @Unique
    private NTERiverHydrology.ColumnProfile[] tfe$copyRiverProfiles(ChunkNoiseFiller filler)
    {
        final NTEChunkHeightFillerAccess access = (NTEChunkHeightFillerAccess) filler;
        final NTERiverHydrology.ColumnProfile[] profiles = new NTERiverHydrology.ColumnProfile[16 * 16];
        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
            {
                profiles[x + 16 * z] = access.tfe$getRiverHydrologyProfile(x, z);
            }
        }
        return profiles;
    }

    @Unique
    private void tfe$syncForestType121(ChunkData chunkData)
    {
        final ChunkPos chunkPos = chunkData.getPos();
        ((ChunkDataAccessor) chunkData).tfe$setForestType(
            NTE121ForestHelpers.toLegacyForestType(
                NTE121ForestHelpers.getForestType(noiseSamplerSeed, chunkPos.getMinBlockX(), chunkPos.getMinBlockZ())
            )
        );
    }
}
