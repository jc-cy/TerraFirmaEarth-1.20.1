package com.newterraearth.tfe.mixin;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.world.BiomeNoiseSampler;
import net.dries007.tfc.world.ChunkHeightFiller;
import net.dries007.tfc.world.biome.BiomeBlendType;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.BiomeSourceExtension;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.river.RiverBlendType;
import net.dries007.tfc.world.river.RiverInfo;
import net.dries007.tfc.world.river.RiverNoiseSampler;

import com.newterraearth.tfe.world.NTEBiomeExtensionAccess;
import com.newterraearth.tfe.world.NTEChunkHeightFillerAccess;
import com.newterraearth.tfe.world.NTEChunkShoreContext;
import com.newterraearth.tfe.debug.NTERuntimeTrace;
import com.newterraearth.tfe.world.river.NTERiverBlendType;
import com.newterraearth.tfe.world.river.NTERiverHydrology;
import com.newterraearth.tfe.world.river.NTERiverNoiseSampler;
import com.newterraearth.tfe.world.shore.NTEShoreBlendType;
import com.newterraearth.tfe.world.shore.NTEShoreNoiseSampler;
import com.newterraearth.tfe.world.terrain.NTETerrainUpliftSampler;
import com.newterraearth.tfe.world.volcano.NTECenteredFeatureBlendType;
import com.newterraearth.tfe.world.volcano.NTECenteredFeatureNoiseSampler;

@Mixin(value = ChunkHeightFiller.class, remap = false)
public abstract class ChunkHeightFillerMixin implements NTEChunkHeightFillerAccess
{
    @Shadow protected Map<BiomeExtension, BiomeNoiseSampler> biomeNoiseSamplers;
    @Shadow protected Object2DoubleMap<BiomeNoiseSampler> columnBiomeNoiseSamplers;
    @Shadow protected BiomeSourceExtension biomeSource;
    @Shadow protected Map<RiverBlendType, RiverNoiseSampler> riverNoiseSamplers;
    @Shadow protected double[] riverBlendWeights;
    @Shadow protected int seaLevel;
    @Shadow protected int blockX;
    @Shadow protected int blockZ;
    @Shadow protected int localX;
    @Shadow protected int localZ;

    @Shadow protected abstract @Nullable RiverInfo sampleRiverInfo(boolean useCache);

    @Shadow protected abstract void updateLocalCaches(Object2DoubleMap<BiomeExtension> biomeWeights, BiomeExtension biomeAt, @Nullable RiverInfo info, double height);

    @Unique private boolean tfe$hasShoreRuntime;
    @Unique private Map<NTERiverBlendType, NTERiverNoiseSampler> tfe$exactRiverNoiseSamplers = Collections.emptyMap();
    @Unique private volatile Map<NTERiverBlendType, NTERiverNoiseSampler> tfe$retainedRiverNoiseSamplers = Collections.emptyMap();
    @Unique private volatile Supplier<Map<NTERiverBlendType, NTERiverNoiseSampler>> tfe$retainedRiverNoiseSamplerFactory;
    @Unique private double[] tfe$exactRiverBlendWeights = new double[NTERiverBlendType.SIZE];
    @Unique private double[] tfe$nativeRiverBlendWeights = new double[NTERiverBlendType.SIZE];
    @Unique private double[] tfe$supplementalRiverBlendWeights = new double[NTERiverBlendType.SIZE];
    @Unique private double[] tfe$retainedRiverDensityBlendWeights = new double[NTERiverBlendType.SIZE];
    /** Scratch weights for re-cutting the supplemental creek after the centered-feature stage. */
    @Unique private double[] tfe$scratchRiverBlendWeights = new double[NTERiverBlendType.SIZE];
    @Unique private boolean tfe$usesConfluenceCarvingUnion;
    @Unique private double tfe$retainedReceiverBedHeight = Double.POSITIVE_INFINITY;
    @Unique private double tfe$retainedReceiverHeight = Double.POSITIVE_INFINITY;
    @Unique private double tfe$retainedReceiverNormDistSq = Double.POSITIVE_INFINITY;
    @Unique private Map<NTEShoreBlendType, NTEShoreNoiseSampler> tfe$shoreNoiseSamplers = Collections.emptyMap();
    @Unique private double[] tfe$shoreBlendWeights = new double[NTEShoreBlendType.SIZE];
    @Unique private Noise2D tfe$tideHeightNoise;
    @Unique private Map<NTECenteredFeatureBlendType, NTECenteredFeatureNoiseSampler> tfe$centeredFeatureNoiseSamplers = Collections.emptyMap();
    @Unique private NTETerrainUpliftSampler tfe$terrainUpliftSampler;
    @Unique private double tfe$terrainUpliftBaseHeight;
    @Unique private double tfe$terrainUpliftTopHeight;
    @Unique private double tfe$terrainUpliftAmount;
    @Unique private boolean tfe$forceSubterraneanCaveRiver;
    @Unique private boolean tfe$couldBeSalty;
    @Unique private NTERiverHydrology tfe$riverHydrology;
    @Unique private boolean tfe$suppressRiver;
    @Unique private NTERiverHydrology.ColumnProfile tfe$currentRiverHydrologyProfile;
    @Unique private NTERiverHydrology.ColumnProfile[] tfe$riverHydrologyProfiles = new NTERiverHydrology.ColumnProfile[16 * 16];
    @Unique private boolean[] tfe$nativeDryRiverBanks = new boolean[16 * 16];
    @Unique private double tfe$currentRiverTerrainHeight;
    @Unique private double[] tfe$riverTerrainHeights = new double[16 * 16];
    @Unique private int[] tfe$preVolcanicHeights = new int[16 * 16];
    @Unique private int[] tfe$surfaceIntegrityDepth = new int[16 * 16];

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tfe$init(Object2DoubleMap<BiomeExtension>[] sampledBiomeWeights, BiomeSourceExtension biomeSource, Map<BiomeExtension, BiomeNoiseSampler> biomeNoiseSamplers, Map<RiverBlendType, RiverNoiseSampler> riverNoiseSamplers, Noise2D shoreSampler, int seaLevel, CallbackInfo ci)
    {
        final NTEChunkShoreContext.Context context = NTEChunkShoreContext.current();
        if (context != null)
        {
            tfe$hasShoreRuntime = true;
            tfe$exactRiverNoiseSamplers = context.riverNoiseSamplers();
            tfe$retainedRiverNoiseSamplers = Collections.emptyMap();
            tfe$retainedRiverNoiseSamplerFactory = context.retainedRiverNoiseSamplerFactory();
            tfe$shoreNoiseSamplers = context.shoreNoiseSamplers();
            tfe$tideHeightNoise = context.tideHeightNoise();
            tfe$centeredFeatureNoiseSamplers = context.centeredFeatureNoiseSamplers();
            tfe$terrainUpliftSampler = context.terrainUpliftSampler();
            tfe$riverHydrology = context.riverHydrology();
            tfe$suppressRiver = context.suppressRiver();
        }
        else
        {
            tfe$hasShoreRuntime = false;
            tfe$exactRiverNoiseSamplers = Collections.emptyMap();
            tfe$retainedRiverNoiseSamplers = Collections.emptyMap();
            tfe$retainedRiverNoiseSamplerFactory = null;
            tfe$shoreNoiseSamplers = Collections.emptyMap();
            tfe$tideHeightNoise = null;
            tfe$centeredFeatureNoiseSamplers = Collections.emptyMap();
            tfe$terrainUpliftSampler = null;
            tfe$riverHydrology = null;
            tfe$suppressRiver = false;
        }
        tfe$exactRiverBlendWeights = new double[NTERiverBlendType.SIZE];
        tfe$shoreBlendWeights = new double[NTEShoreBlendType.SIZE];
        tfe$couldBeSalty = false;
    }

    @Override
    public boolean tfe$hasShoreRuntime()
    {
        return tfe$hasShoreRuntime;
    }

    @Override
    public Map<NTEShoreBlendType, NTEShoreNoiseSampler> tfe$getShoreNoiseSamplers()
    {
        return tfe$shoreNoiseSamplers;
    }

    @Override
    public double[] tfe$getShoreBlendWeights()
    {
        return tfe$shoreBlendWeights;
    }

    @Override
    public Noise2D tfe$getTideHeightNoise()
    {
        return tfe$tideHeightNoise;
    }

    @Override
    public Object2DoubleMap<BiomeNoiseSampler> tfe$getColumnBiomeNoiseSamplers()
    {
        return columnBiomeNoiseSamplers;
    }

    @Override
    public double[] tfe$getExactRiverBlendWeights()
    {
        return tfe$exactRiverBlendWeights;
    }

    @Override
    public Map<NTERiverBlendType, NTERiverNoiseSampler> tfe$getExactRiverNoiseSamplers()
    {
        return tfe$exactRiverNoiseSamplers;
    }

    @Override
    public Map<NTERiverBlendType, NTERiverNoiseSampler> tfe$getRetainedRiverNoiseSamplers()
    {
        return tfe$getOrCreateRetainedRiverNoiseSamplers();
    }

    @Override
    public double[] tfe$getSupplementalRiverBlendWeights()
    {
        return tfe$supplementalRiverBlendWeights;
    }

    @Override
    public double[] tfe$getRetainedRiverBlendWeights()
    {
        return tfe$retainedRiverDensityBlendWeights;
    }

    @Override
    public boolean tfe$usesConfluenceCarvingUnion()
    {
        return tfe$usesConfluenceCarvingUnion;
    }

    @Override
    public double[] tfe$getRiverBlendWeights()
    {
        return riverBlendWeights;
    }

    @Override
    public Map<RiverBlendType, RiverNoiseSampler> tfe$getRiverNoiseSamplers()
    {
        return riverNoiseSamplers;
    }

    @Override
    public Map<NTECenteredFeatureBlendType, NTECenteredFeatureNoiseSampler> tfe$getCenteredFeatureNoiseSamplers()
    {
        return tfe$centeredFeatureNoiseSamplers;
    }

    @Override
    public double tfe$getTerrainUpliftBaseHeight()
    {
        return tfe$terrainUpliftBaseHeight;
    }

    @Override
    public double tfe$getTerrainUpliftTopHeight()
    {
        return tfe$terrainUpliftTopHeight;
    }

    @Override
    public double tfe$getTerrainUpliftAmount()
    {
        return tfe$terrainUpliftAmount;
    }

    @Override
    public boolean tfe$isForceSubterraneanCaveRiver()
    {
        return tfe$forceSubterraneanCaveRiver;
    }

    @Override
    public int tfe$getBlockX()
    {
        return blockX;
    }

    @Override
    public int tfe$getBlockZ()
    {
        return blockZ;
    }

    @Override
    public boolean tfe$couldBeSalty()
    {
        return tfe$couldBeSalty;
    }

    @Override
    public int tfe$getLocalX()
    {
        return localX;
    }

    @Override
    public int tfe$getLocalZ()
    {
        return localZ;
    }

    @Override
    public NTERiverHydrology.ColumnProfile tfe$getRiverHydrologyProfile(int localX, int localZ)
    {
        return tfe$riverHydrologyProfiles[localX + 16 * localZ];
    }

    @Override
    public boolean[] tfe$getNativeDryRiverBanks()
    {
        return tfe$nativeDryRiverBanks;
    }

    @Override
    public double tfe$getRiverTerrainHeight(int localX, int localZ)
    {
        return tfe$riverTerrainHeights[localX + 16 * localZ];
    }

    @Override
    public void tfe$recordRiverHydrologyProfile(int localX, int localZ)
    {
        final int index = localX + 16 * localZ;
        tfe$riverHydrologyProfiles[index] = tfe$currentRiverHydrologyProfile;
        tfe$riverTerrainHeights[index] = tfe$currentRiverTerrainHeight;
    }

    /**
     * @author Codex
     * @reason Port 1.21 shore blend weighting and tide-aware shoreline height adjustment into 1.20.
     */
    @Overwrite
    protected double sampleColumnHeightAndBiome(Object2DoubleMap<BiomeExtension> biomeWeights, boolean useCache)
    {
        if (!tfe$hasShoreRuntime)
        {
            return tfe$sampleColumnHeightAndBiome120(biomeWeights, useCache);
        }

        columnBiomeNoiseSamplers.clear();

        double height = 0;
        double normalHeight = 0;
        double shoreHeight = 0;
        double shoreWeight = 0;
        double oceanWeight = 0;

        BiomeExtension biomeAt = null;
        BiomeExtension normalBiomeAt = null;
        BiomeExtension shoreBiomeAt = null;
        BiomeExtension oceanBiomeAt = null;
        double maxNormalWeight = 0;
        double maxShoreWeight = 0;
        double maxOceanWeight = 0;
        tfe$couldBeSalty = false;
        final boolean trace = NTERuntimeTrace.isTargetColumn(blockX, blockZ);
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            final double biomeWeight = entry.getDoubleValue();
            final BiomeExtension biome = entry.getKey();
            final BiomeNoiseSampler sampler = biomeNoiseSamplers.get(biome);

            if (biome.isSalty())
            {
                tfe$couldBeSalty = true;
            }

            assert sampler != null : "Non-existent sampler for biome: " + biome.key();

            if (columnBiomeNoiseSamplers.containsKey(sampler))
            {
                columnBiomeNoiseSamplers.mergeDouble(sampler, biomeWeight, Double::sum);
            }
            else
            {
                sampler.setColumn(blockX, blockZ);
                columnBiomeNoiseSamplers.put(sampler, biomeWeight);
            }

            final double biomeHeight = biomeWeight * sampler.height();
            height += biomeHeight;

            if (biome.isShore())
            {
                shoreHeight += biomeHeight;
                shoreWeight += biomeWeight;
                if (maxShoreWeight < biomeWeight)
                {
                    shoreBiomeAt = biome;
                    maxShoreWeight = biomeWeight;
                }
            }
            else if (biome.biomeBlendType() == BiomeBlendType.OCEAN)
            {
                oceanWeight += biomeWeight;
                if (maxOceanWeight < biomeWeight)
                {
                    oceanBiomeAt = biome;
                    maxOceanWeight = biomeWeight;
                }
            }
            else
            {
                normalHeight += biomeHeight;
                if (maxNormalWeight < biomeWeight)
                {
                    normalBiomeAt = biome;
                    maxNormalWeight = biomeWeight;
                }
            }
        }

        biomeAt = normalBiomeAt;
        tfe$computeInitialShoreWeights(biomeWeights);
        final double baseHeight = height;
        if (trace)
        {
            tfe$trace("base", biomeWeights, baseHeight, normalHeight, shoreHeight, shoreWeight, oceanWeight, 0d, 0d, null);
        }

        final double landWeight = 1 - oceanWeight - shoreWeight;
        if (shoreWeight > 0 && shoreBiomeAt != null)
        {
            height = tfe$adjustHeightForShoreContributions(height, oceanWeight, landWeight, shoreWeight, maxShoreWeight, shoreBiomeAt, shoreHeight, normalHeight);
            if (shoreWeight > 0.5)
            {
                biomeAt = shoreBiomeAt;
            }
        }

        if (biomeAt == null)
        {
            biomeAt = oceanBiomeAt;
        }

        final double shoreAdjustedHeight = height;
        if (trace)
        {
            tfe$trace("shore", biomeWeights, height, normalHeight, shoreHeight, shoreWeight, oceanWeight, landWeight, maxShoreWeight, shoreBiomeAt);
        }

        if (oceanWeight >= 0.25d && tfe$tideHeightNoise != null)
        {
            final double tideAdjustedSeaEdgeHeight = tfe$tideHeightNoise.noise(blockX, blockZ) - 4d;
            height = Mth.clampedMap(landWeight, 0.32d, 0.36d, Math.min(height, tideAdjustedSeaEdgeHeight), height);
        }

        assert biomeAt != null;

        RiverInfo info = tfe$suppressRiver ? null : sampleRiverInfo(false);
        final boolean preparedHydrologyColumn = tfe$riverHydrology != null
            && NTERiverHydrology.hasActiveGenerationColumn(tfe$riverHydrology, blockX, blockZ);
        if (tfe$riverHydrology != null && !tfe$suppressRiver)
        {
            info = preparedHydrologyColumn
                ? tfe$riverHydrology.retainedRiverInfoPrepared(info, blockX, blockZ)
                : tfe$riverHydrology.retainedRiverInfo(info, blockX, blockZ);
        }
        tfe$computeInitialExactRiverWeights(biomeWeights);
        final double terrainUplift = tfe$sampleTerrainUplift(biomeAt, biomeWeights, info);
        final double terrainUpliftBaseHeight = height;
        height += terrainUplift;
        tfe$recordTerrainUpliftLayer(
            terrainUpliftBaseHeight,
            terrainUpliftBaseHeight + terrainUplift,
            terrainUplift
        );
        final double preSupplementalRiverHeight = height;
        tfe$usesConfluenceCarvingUnion = false;
        Arrays.fill(tfe$supplementalRiverBlendWeights, 0d);
        Arrays.fill(tfe$retainedRiverDensityBlendWeights, 0d);
        if (tfe$suppressRiver)
        {
            tfe$currentRiverHydrologyProfile = null;
            height = tfe$adjustHeightForCenteredFeatures(height);
            tfe$currentRiverTerrainHeight = height;
            return height;
        }
        final NTERiverHydrology.ColumnProfile supplementalRiverProfile = tfe$riverHydrology == null
            ? null
            : preparedHydrologyColumn
                ? NTERiverHydrology.activeGenerationProfile(blockX, blockZ)
                : tfe$riverHydrology.samplePreparedColumn(blockX, blockZ, height);
        final boolean confluenceCarvingUnion = NTERiverHydrology.usesConfluenceCarvingUnion(
            supplementalRiverProfile,
            info
        );
        tfe$currentRiverHydrologyProfile = confluenceCarvingUnion
            || NTERiverHydrology.shouldUseSupplemental(supplementalRiverProfile, info)
            ? supplementalRiverProfile
            : null;
        final RiverInfo retainedReceiverInfo = info;
        tfe$usesConfluenceCarvingUnion = confluenceCarvingUnion;
        final double retainedReceiverHeight = tfe$sampleRetainedReceiverHeight(
            preSupplementalRiverHeight,
            retainedReceiverInfo,
            supplementalRiverProfile,
            terrainUplift
        );
        tfe$currentRiverHydrologyProfile = NTERiverHydrology.adaptToRetainedReceiverBed(
            tfe$currentRiverHydrologyProfile,
            tfe$retainedReceiverBedHeight
        );
        if (tfe$currentRiverHydrologyProfile != null)
        {
            // A retained TFC edge may overlap the last few columns of a replacement
            // headwater. Only the actual supplemental channel wins there; outside it
            // the retained main-stem profile remains authoritative.
            if (tfe$currentRiverHydrologyProfile.inChannel()
                && tfe$currentRiverHydrologyProfile.receiverBlendWeight() <= 0d)
            {
                info = null;
            }
            tfe$selectRiverShapeForHydrology();
            if (tfe$usesConfluenceCarvingUnion)
            {
                // The supplemental side of a confluence is sampled as one
                // complete cut. The retained receiver is sampled separately
                // below and density combines both with an air-union.
                info = null;
                System.arraycopy(
                    tfe$supplementalRiverBlendWeights,
                    0,
                    tfe$exactRiverBlendWeights,
                    0,
                    tfe$exactRiverBlendWeights.length
                );
            }
        }
        final double initialCaveWeight = tfe$adjustExactRiverWeightsForCaves();
        final double caveTransitionTerrainUplift = terrainUplift * tfe$caveTransitionTerrainUpliftProtection(initialCaveWeight);
        tfe$forceSubterraneanCaveRiver = false;
        height = tfe$adjustHeightForExactRiverContributions(height, info, initialCaveWeight, caveTransitionTerrainUplift);
        if (tfe$currentRiverHydrologyProfile != null && !tfe$currentRiverHydrologyProfile.subterranean())
        {
            height = tfe$currentRiverHydrologyProfile.applyBankFillTransition(preSupplementalRiverHeight, height);
        }
        if (tfe$currentRiverHydrologyProfile != null
            && (!tfe$currentRiverHydrologyProfile.subterranean()
                || tfe$currentRiverHydrologyProfile.terrainIncision() > 0d)
            && !tfe$currentRiverHydrologyProfile.fillAllowed())
        {
            height = Math.min(
                height,
                tfe$currentRiverHydrologyProfile.terrainCutCeiling(preSupplementalRiverHeight)
            );
        }
        height = NTERiverHydrology.clampToRetainedReceiverBed(
            supplementalRiverProfile,
            retainedReceiverInfo,
            height,
            retainedReceiverHeight
        );
        height = NTERiverHydrology.clampToAdaptedReceiverBed(
            tfe$currentRiverHydrologyProfile,
            height
        );
        final double terrainHeightBeforeCenteredFeature = height;
        final int preVolcanicHeight = (int) height;
        height = tfe$adjustHeightForCenteredFeatures(height);
        if (tfe$currentRiverHydrologyProfile != null && !tfe$currentRiverHydrologyProfile.subterranean())
        {
            // The supplemental creek is the terrain-aware last step: the centered
            // feature stage may have rewritten the surface after the first river
            // pass, so the creek is cut into that final terrain as well. Cut-only:
            // the result never raises what the rest of the pipeline produced.
            System.arraycopy(
                tfe$exactRiverBlendWeights,
                0,
                tfe$scratchRiverBlendWeights,
                0,
                tfe$exactRiverBlendWeights.length
            );
            System.arraycopy(
                tfe$supplementalRiverBlendWeights,
                0,
                tfe$exactRiverBlendWeights,
                0,
                tfe$exactRiverBlendWeights.length
            );
            final RiverInfo centeredReceiverInfo = tfe$usesConfluenceCarvingUnion ? null : info;
            double creekHeight = tfe$adjustHeightForExactRiverContributions(
                height,
                centeredReceiverInfo,
                initialCaveWeight,
                caveTransitionTerrainUplift
            );
            System.arraycopy(
                tfe$scratchRiverBlendWeights,
                0,
                tfe$exactRiverBlendWeights,
                0,
                tfe$exactRiverBlendWeights.length
            );
            creekHeight = tfe$currentRiverHydrologyProfile.applyBankFillTransition(preSupplementalRiverHeight, creekHeight);
            if (!tfe$currentRiverHydrologyProfile.fillAllowed())
            {
                creekHeight = Math.min(
                    creekHeight,
                    tfe$currentRiverHydrologyProfile.terrainCutCeiling(preSupplementalRiverHeight)
                );
            }
            creekHeight = NTERiverHydrology.clampToRetainedReceiverBed(
                supplementalRiverProfile,
                retainedReceiverInfo,
                creekHeight,
                retainedReceiverHeight
            );
            creekHeight = NTERiverHydrology.clampToAdaptedReceiverBed(
                tfe$currentRiverHydrologyProfile,
                creekHeight
            );
            height = Math.min(height, creekHeight);
        }
        final int surfaceIntegrityDepth = tfe$computeSurfaceIntegrityDepth(
            terrainHeightBeforeCenteredFeature,
            height
        );
        final double centeredFeatureHeight = height;
        if (NTERuntimeTrace.isTargetColumn(blockX, blockZ))
        {
            System.out.printf(
                "[TFE][RuntimeTrace][centered_stage] x=%d z=%d in=%.4f out=%.4f%n",
                blockX,
                blockZ,
                terrainHeightBeforeCenteredFeature,
                centeredFeatureHeight
            );
        }
        tfe$currentRiverTerrainHeight = height;
        if (trace)
        {
            tfe$traceFinal(biomeWeights, baseHeight, shoreAdjustedHeight, centeredFeatureHeight, terrainUpliftBaseHeight, terrainUplift, initialCaveWeight, caveTransitionTerrainUplift, info, height);
        }

        if (useCache)
        {
            tfe$preVolcanicHeights[localX + 16 * localZ] = preVolcanicHeight;
            tfe$surfaceIntegrityDepth[localX + 16 * localZ] = surfaceIntegrityDepth;
            updateLocalCaches(biomeWeights, biomeAt, info, height);
        }

        return height;
    }

    @Unique
    private double tfe$sampleColumnHeightAndBiome120(Object2DoubleMap<BiomeExtension> biomeWeights, boolean useCache)
    {
        columnBiomeNoiseSamplers.clear();

        double height = 0;
        double normalHeight = 0;
        double shoreHeight = 0;
        double shoreWeight = 0;

        BiomeExtension biomeAt = null;
        BiomeExtension normalBiomeAt = null;
        BiomeExtension shoreBiomeAt = null;
        double maxNormalWeight = 0;
        double maxShoreWeight = 0;
        tfe$couldBeSalty = false;
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            final double biomeWeight = entry.getDoubleValue();
            final BiomeExtension biome = entry.getKey();
            final BiomeNoiseSampler sampler = biomeNoiseSamplers.get(biome);

            if (biome.isSalty())
            {
                tfe$couldBeSalty = true;
            }

            assert sampler != null : "Non-existent sampler for biome: " + biome.key();

            if (columnBiomeNoiseSamplers.containsKey(sampler))
            {
                columnBiomeNoiseSamplers.mergeDouble(sampler, biomeWeight, Double::sum);
            }
            else
            {
                sampler.setColumn(blockX, blockZ);
                columnBiomeNoiseSamplers.put(sampler, biomeWeight);
            }

            final double biomeHeight = biomeWeight * sampler.height();
            height += biomeHeight;

            if (biome.isShore())
            {
                shoreHeight += biomeHeight;
                shoreWeight += biomeWeight;
                if (maxShoreWeight < biomeWeight)
                {
                    shoreBiomeAt = biome;
                    maxShoreWeight = biomeWeight;
                }
            }
            else
            {
                normalHeight += biomeHeight;
                if (maxNormalWeight < biomeWeight)
                {
                    normalBiomeAt = biome;
                    maxNormalWeight = biomeWeight;
                }
            }
        }

        biomeAt = normalBiomeAt;
        if (biomeAt == null)
        {
            biomeAt = shoreBiomeAt;
        }
        final double baseHeight = height;

        if (shoreWeight > 0.5 && shoreBiomeAt != null)
        {
            final double cliffInfluence = Mth.clamp(
                Mth.map(height, seaLevel, seaLevel + 20, 0, 0.6),
                0.0,
                1.0
            );
            final double adjustedCliffInfluence = 1.0 - (1.0 - cliffInfluence) * (1.0 - cliffInfluence);
            final double x2 = Mth.lerp(adjustedCliffInfluence, 0.8, 0.515);
            final double y2 = 1.15 - 0.3 * x2;
            final double adjustedShoreWeight = shoreWeight < x2
                ? Mth.map(shoreWeight, 0.5, x2, 0.5, y2)
                : Mth.map(shoreWeight, x2, 1.0, y2, 1.0);

            final double normalWeight = 1.0 - shoreWeight;
            final double adjustedNormalWeight = 1.0 - adjustedShoreWeight;
            final double adjustedHeight = Math.max(
                (adjustedShoreWeight / shoreWeight) * shoreHeight + (adjustedNormalWeight / normalWeight) * normalHeight,
                seaLevel
            );

            if (adjustedHeight < height)
            {
                height = adjustedHeight;
            }

            biomeAt = shoreBiomeAt;
        }
        final double shoreAdjustedHeight = height;

        assert biomeAt != null;

        final double terrainHeightBeforeCenteredFeature = height;
        height = tfe$adjustHeightForCenteredFeatures(height);
        final int surfaceIntegrityDepth = tfe$computeSurfaceIntegrityDepth(
            terrainHeightBeforeCenteredFeature,
            height
        );
        final double centeredFeatureHeight = height;
        RiverInfo info = tfe$suppressRiver ? null : sampleRiverInfo(false);
        final boolean preparedHydrologyColumn = tfe$riverHydrology != null
            && NTERiverHydrology.hasActiveGenerationColumn(tfe$riverHydrology, blockX, blockZ);
        if (tfe$riverHydrology != null && !tfe$suppressRiver)
        {
            info = preparedHydrologyColumn
                ? tfe$riverHydrology.retainedRiverInfoPrepared(info, blockX, blockZ)
                : tfe$riverHydrology.retainedRiverInfo(info, blockX, blockZ);
        }
        tfe$computeInitialExactRiverWeights(biomeWeights);
        final double terrainUplift = tfe$sampleTerrainUplift(biomeAt, biomeWeights, info);
        final double terrainUpliftBaseHeight = height;
        height += terrainUplift;
        final double preSupplementalRiverHeight = height;
        tfe$usesConfluenceCarvingUnion = false;
        Arrays.fill(tfe$supplementalRiverBlendWeights, 0d);
        Arrays.fill(tfe$retainedRiverDensityBlendWeights, 0d);
        if (tfe$suppressRiver)
        {
            tfe$currentRiverHydrologyProfile = null;
            tfe$currentRiverTerrainHeight = height;
            tfe$recordTerrainUpliftLayer(terrainUpliftBaseHeight, terrainUpliftBaseHeight + terrainUplift, terrainUplift);
            return height;
        }
        final NTERiverHydrology.ColumnProfile supplementalRiverProfile = tfe$riverHydrology == null
            ? null
            : preparedHydrologyColumn
                ? NTERiverHydrology.activeGenerationProfile(blockX, blockZ)
                : tfe$riverHydrology.samplePreparedColumn(blockX, blockZ, height);
        final boolean confluenceCarvingUnion = NTERiverHydrology.usesConfluenceCarvingUnion(
            supplementalRiverProfile,
            info
        );
        tfe$currentRiverHydrologyProfile = confluenceCarvingUnion
            || NTERiverHydrology.shouldUseSupplemental(supplementalRiverProfile, info)
            ? supplementalRiverProfile
            : null;
        final RiverInfo retainedReceiverInfo = info;
        tfe$usesConfluenceCarvingUnion = confluenceCarvingUnion;
        final double retainedReceiverHeight = tfe$sampleRetainedReceiverHeight(
            preSupplementalRiverHeight,
            retainedReceiverInfo,
            supplementalRiverProfile,
            terrainUplift
        );
        tfe$currentRiverHydrologyProfile = NTERiverHydrology.adaptToRetainedReceiverBed(
            tfe$currentRiverHydrologyProfile,
            tfe$retainedReceiverBedHeight
        );
        if (tfe$currentRiverHydrologyProfile != null)
        {
            if (tfe$currentRiverHydrologyProfile.inChannel()
                && tfe$currentRiverHydrologyProfile.receiverBlendWeight() <= 0d)
            {
                info = null;
            }
            tfe$selectRiverShapeForHydrology();
            if (tfe$usesConfluenceCarvingUnion)
            {
                info = null;
                System.arraycopy(
                    tfe$supplementalRiverBlendWeights,
                    0,
                    tfe$exactRiverBlendWeights,
                    0,
                    tfe$exactRiverBlendWeights.length
                );
            }
        }
        final double initialCaveWeight = tfe$adjustExactRiverWeightsForCaves();
        final double caveTransitionTerrainUplift = terrainUplift * tfe$caveTransitionTerrainUpliftProtection(initialCaveWeight);
        tfe$forceSubterraneanCaveRiver = false;
        height = tfe$adjustHeightForExactRiverContributions(height, info, initialCaveWeight, caveTransitionTerrainUplift);
        if (tfe$currentRiverHydrologyProfile != null && !tfe$currentRiverHydrologyProfile.subterranean())
        {
            height = tfe$currentRiverHydrologyProfile.applyBankFillTransition(preSupplementalRiverHeight, height);
        }
        if (tfe$currentRiverHydrologyProfile != null
            && !tfe$currentRiverHydrologyProfile.subterranean()
            && !tfe$currentRiverHydrologyProfile.fillAllowed())
        {
            height = Math.min(
                height,
                tfe$currentRiverHydrologyProfile.terrainCutCeiling(preSupplementalRiverHeight)
            );
        }
        height = NTERiverHydrology.clampToRetainedReceiverBed(
            supplementalRiverProfile,
            retainedReceiverInfo,
            height,
            retainedReceiverHeight
        );
        height = NTERiverHydrology.clampToAdaptedReceiverBed(
            tfe$currentRiverHydrologyProfile,
            height
        );
        tfe$currentRiverTerrainHeight = height;
        tfe$recordTerrainUpliftLayer(terrainUpliftBaseHeight, height, terrainUplift);
        if (useCache)
        {
            tfe$surfaceIntegrityDepth[localX + 16 * localZ] = surfaceIntegrityDepth;
            updateLocalCaches(biomeWeights, biomeAt, info, height);
        }
        return height;
    }

    @Unique
    private void tfe$computeInitialShoreWeights(Object2DoubleMap<BiomeExtension> biomeWeights)
    {
        Arrays.fill(tfe$shoreBlendWeights, 0d);
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            final NTEShoreBlendType blendType = ((NTEBiomeExtensionAccess) (Object) entry.getKey()).tfe$getShoreBlendType();
            tfe$shoreBlendWeights[blendType.ordinal()] += entry.getDoubleValue();
        }
    }

    @Unique
    private double tfe$adjustHeightForShoreContributions(double height, double oceanWeight, double landWeight, double shoreWeight, double thisWeight, BiomeExtension biome, double shoreHeight, double normalHeight)
    {
        double shoreBlendHeight = 0d;
        for (NTEShoreBlendType type : NTEShoreBlendType.ALL)
        {
            final double weight = tfe$shoreBlendWeights[type.ordinal()];
            final NTEShoreNoiseSampler sampler = tfe$shoreNoiseSamplers.get(type);
            if (type == NTEShoreBlendType.NONE)
            {
                shoreBlendHeight += weight * height;
            }
            else if (weight > 0 && sampler != null)
            {
                shoreBlendHeight += weight * sampler.setColumnAndSampleHeight(height, blockX, blockZ, oceanWeight, landWeight, shoreWeight, thisWeight, biome, shoreHeight, normalHeight);
            }
        }
        return shoreBlendHeight;
    }

    @Unique
    private double tfe$adjustHeightForCenteredFeatures(double heightIn)
    {
        double centeredFeatureHeight = NTECenteredFeatureNoiseSampler.NOT_PRESENT_RETURN;
        final boolean traceColumn = NTERuntimeTrace.isTargetColumn(blockX, blockZ);
        for (NTECenteredFeatureBlendType type : NTECenteredFeatureBlendType.ALL)
        {
            if (type == NTECenteredFeatureBlendType.NONE)
            {
                continue;
            }
            final NTECenteredFeatureNoiseSampler sampler = tfe$centeredFeatureNoiseSamplers.get(type);
            if (sampler != null)
            {
                final double sampledHeight = sampler.setColumnAndSampleHeight(heightIn, blockX, blockZ, biomeSource);
                if (traceColumn)
                {
                    System.out.printf(
                        "[TFE][RuntimeTrace][centered_sample] x=%d z=%d type=%s in=%.4f sampled=%.4f%n",
                        blockX,
                        blockZ,
                        type,
                        heightIn,
                        sampledHeight
                    );
                }
                if (sampledHeight > centeredFeatureHeight)
                {
                    centeredFeatureHeight = sampledHeight;
                }
            }
        }
        return centeredFeatureHeight == NTECenteredFeatureNoiseSampler.NOT_PRESENT_RETURN ? heightIn : centeredFeatureHeight;
    }

    @Unique
    private double tfe$sampleTerrainUplift(
        BiomeExtension biomeAt,
        Object2DoubleMap<BiomeExtension> biomeWeights,
        @Nullable RiverInfo info
    )
    {
        if (tfe$terrainUpliftSampler == null)
        {
            return 0d;
        }

        final double rawUplift = tfe$terrainUpliftSampler.sample(blockX, blockZ);
        if (rawUplift <= 0d)
        {
            return 0d;
        }

        double uplift = tfe$terrainUpliftSampler.sampleWithOceanExtension(blockX, blockZ, rawUplift);
        if (uplift <= 0d)
        {
            return 0d;
        }

        final double protectedWaterWeight = Math.max(
            Math.max(
                Math.max(tfe$lakeUpliftSuppression(biomeWeights), tfe$terraceCliffUpliftSuppression(biomeAt, biomeWeights)),
                tfe$fixedShoreUpliftSuppression()
            ),
            tfe$estuaryRiverUpliftSuppression(info, biomeWeights)
        );
        return uplift * (1d - Mth.clamp(protectedWaterWeight, 0d, 1d));
    }

    @Unique
    private double tfe$estuaryRiverUpliftSuppression(@Nullable RiverInfo info, Object2DoubleMap<BiomeExtension> biomeWeights)
    {
        if (info == null || info.normDistSq() >= 1.10d)
        {
            return 0d;
        }

        double oceanOrShoreWeight = 0d;
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            final BiomeExtension biome = entry.getKey();
            if (biome.isShore() || biome.biomeBlendType() == BiomeBlendType.OCEAN)
            {
                oceanOrShoreWeight += entry.getDoubleValue();
            }
        }
        if (oceanOrShoreWeight <= 0d)
        {
            return 0d;
        }

        double exactRiverWeight = 0d;
        for (NTERiverBlendType type : NTERiverBlendType.ALL)
        {
            if (type != NTERiverBlendType.NONE)
            {
                exactRiverWeight += tfe$exactRiverBlendWeights[type.ordinal()];
            }
        }
        if (exactRiverWeight <= 1.0e-4d)
        {
            return 0d;
        }

        final double channelCore = tfe$smoothStep(Mth.clampedMap(info.normDistSq(), 1.10d, 0.25d, 0d, 1d));
        final double waterBlend = tfe$smoothStep(Mth.clampedMap(oceanOrShoreWeight, 0.20d, 0.50d, 0d, 1d));
        final double riverBlend = tfe$smoothStep(Mth.clampedMap(exactRiverWeight, 0.05d, 0.30d, 0d, 1d));
        return channelCore * waterBlend * riverBlend;
    }

    @Unique
    private double tfe$fixedShoreUpliftSuppression()
    {
        if (!tfe$hasShoreRuntime)
        {
            return 0d;
        }

        double fixedShoreWeight = 0d;
        for (NTEShoreBlendType type : NTEShoreBlendType.ALL)
        {
            if (tfe$isFixedHeightShoreType(type))
            {
                fixedShoreWeight += tfe$shoreBlendWeights[type.ordinal()];
            }
        }
        return tfe$smoothStep(Mth.clampedMap(fixedShoreWeight, 0.06d, 0.35d, 0d, 1d));
    }

    @Unique
    private static boolean tfe$isFixedHeightShoreType(NTEShoreBlendType type)
    {
        return type == NTEShoreBlendType.SANDY
            || type == NTEShoreBlendType.DUNES
            || type == NTEShoreBlendType.EMBAYMENTS
            || type == NTEShoreBlendType.ROCKY_SHORES;
    }

    @Unique
    private static double tfe$terraceCliffUpliftSuppression(BiomeExtension biomeAt, Object2DoubleMap<BiomeExtension> biomeWeights)
    {
        double terraceWeight = 0d;
        double oceanOrShoreWeight = 0d;
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            final BiomeExtension biome = entry.getKey();
            final double weight = entry.getDoubleValue();
            if (tfe$isTerraceCliffBiome(biome))
            {
                terraceWeight += weight;
            }
            if (biome.isShore() || biome.biomeBlendType() == BiomeBlendType.OCEAN)
            {
                oceanOrShoreWeight += weight;
            }
        }

        final boolean terraceAtColumn = tfe$isTerraceCliffBiome(biomeAt);
        if (terraceWeight <= 0d && !terraceAtColumn)
        {
            return 0d;
        }

        final double cliffBlend = terraceAtColumn ? 1d : tfe$smoothStep(Mth.clampedMap(terraceWeight, 0.04d, 0.28d, 0d, 1d));
        final double waterBlend = tfe$smoothStep(Mth.clampedMap(oceanOrShoreWeight, 0.18d, 0.45d, 0d, 1d));
        return cliffBlend * waterBlend;
    }

    @Unique
    private static boolean tfe$isTerraceCliffBiome(BiomeExtension biome)
    {
        final String path = biome.key().location().getPath();
        return path.equals("terrace_upper") || path.equals("terrace_lower");
    }

    @Unique
    private void tfe$recordTerrainUpliftLayer(double baseHeight, double topHeight, double terrainUplift)
    {
        if (terrainUplift <= 0d || topHeight <= baseHeight)
        {
            tfe$terrainUpliftBaseHeight = 0d;
            tfe$terrainUpliftTopHeight = 0d;
            tfe$terrainUpliftAmount = 0d;
            return;
        }
        tfe$terrainUpliftBaseHeight = baseHeight;
        tfe$terrainUpliftTopHeight = topHeight;
        tfe$terrainUpliftAmount = topHeight - baseHeight;
    }

    @Unique
    private static double tfe$lakeUpliftSuppression(Object2DoubleMap<BiomeExtension> biomeWeights)
    {
        double weight = 0d;
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            if (entry.getKey().biomeBlendType() == BiomeBlendType.LAKE)
            {
                weight += entry.getDoubleValue();
            }
        }
        return tfe$smoothStep(Mth.clampedMap(weight, 0.08d, 0.86d, 0d, 1d));
    }

    @Unique
    private static double tfe$smoothStep(double value)
    {
        final double t = Mth.clamp(value, 0d, 1d);
        return t * t * (3d - 2d * t);
    }

    @Unique
    private static double tfe$caveTransitionTerrainUpliftProtection(double initialCaveWeight)
    {
        return tfe$smoothStep(Mth.clampedMap(initialCaveWeight, 0.40d, 0.75d, 0d, 1d));
    }

    @Unique
    private void tfe$computeInitialExactRiverWeights(Object2DoubleMap<BiomeExtension> biomeWeights)
    {
        Arrays.fill(tfe$exactRiverBlendWeights, 0d);
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            final NTERiverBlendType blendType = ((NTEBiomeExtensionAccess) (Object) entry.getKey()).tfe$getRiverBlendType();
            tfe$exactRiverBlendWeights[blendType.ordinal()] += entry.getDoubleValue();
        }
        System.arraycopy(
            tfe$exactRiverBlendWeights,
            0,
            tfe$nativeRiverBlendWeights,
            0,
            tfe$exactRiverBlendWeights.length
        );
    }

    @Unique
    private double tfe$adjustExactRiverWeightsForCaves()
    {
        final double initialCaveWeight = tfe$exactRiverBlendWeights[NTERiverBlendType.CAVE.ordinal()];
        if (initialCaveWeight > 0)
        {
            final double totalWeight = 1.0 - tfe$exactRiverBlendWeights[NTERiverBlendType.NONE.ordinal()];
            final double adjustedCaveWeight = initialCaveWeight < 0.25
                ? Mth.map(initialCaveWeight, 0.0, 0.25, 0, 0.1 * totalWeight)
                : totalWeight;

            for (NTERiverBlendType type : NTERiverBlendType.ALL)
            {
                final double weight = tfe$exactRiverBlendWeights[type.ordinal()];
                tfe$exactRiverBlendWeights[type.ordinal()] = weight * (1.0 - adjustedCaveWeight) / (1.0 - initialCaveWeight);
            }
            tfe$exactRiverBlendWeights[NTERiverBlendType.CAVE.ordinal()] = adjustedCaveWeight;
        }
        return initialCaveWeight;
    }

    @Unique
    private void tfe$selectRiverShapeForHydrology()
    {
        assert tfe$currentRiverHydrologyProfile != null;
        Arrays.fill(tfe$supplementalRiverBlendWeights, 0d);

        if (tfe$currentRiverHydrologyProfile.subterranean())
        {
            // A covered section must not shape the surface at all: the density
            // stage owns its rock cavity and the height stage stays ambient.
            tfe$supplementalRiverBlendWeights[NTERiverBlendType.NONE.ordinal()] = 1d;
            Arrays.fill(tfe$exactRiverBlendWeights, 0d);
            tfe$exactRiverBlendWeights[NTERiverBlendType.NONE.ordinal()] = 1d;
            return;
        }

        final double riverWeight = tfe$smoothStep(Mth.clampedMap(
            tfe$currentRiverHydrologyProfile.normalizedDistanceSq(),
            2.25d,
            0.72d,
            0d,
            1d
        ));
        tfe$supplementalRiverBlendWeights[NTERiverBlendType.NONE.ordinal()] = 1d - riverWeight;
        if (tfe$currentRiverHydrologyProfile.kind() == NTERiverHydrology.ChannelKind.RIVER)
        {
            tfe$supplementalRiverBlendWeights[NTERiverBlendType.WIDE_DEEP.ordinal()] = riverWeight;
        }
        else
        {
            // Keep the ordinary radius-based BANKED -> WIDE blend. Removing
            // terrain fill must not also replace the creek's sloped bank with
            // a third WIDE mouth profile; receiver shape ownership is handed
            // off separately below, just as cave mouths delegate one height
            // shape instead of deriving a new one from material ownership.
            final double radiusWideWeight = NTERiverHydrology.wideShapeWeight(
                tfe$currentRiverHydrologyProfile.channelRadius()
            );
            final double wideWeight = radiusWideWeight;
            tfe$supplementalRiverBlendWeights[NTERiverBlendType.BANKED.ordinal()] = riverWeight * (1d - wideWeight);
            tfe$supplementalRiverBlendWeights[NTERiverBlendType.WIDE.ordinal()] = riverWeight * wideWeight;
        }

        // Water may align with the receiver before the dry bank does. Reuse
        // the same center-weighted scalar as distance, bed and water-height
        // sampling so the bank type is handed off once instead of producing a
        // third shape by stacking several independent interpolation fields.
        final double receiverBlend = NTERiverHydrology.receiverBankShapeBlendWeight(
            tfe$currentRiverHydrologyProfile
        );
        for (NTERiverBlendType type : NTERiverBlendType.ALL)
        {
            final int index = type.ordinal();
            tfe$exactRiverBlendWeights[index] = Mth.lerp(
                receiverBlend,
                tfe$supplementalRiverBlendWeights[index],
                tfe$nativeRiverBlendWeights[index]
            );
        }
    }

    @Unique
    private double tfe$sampleRetainedReceiverHeight(
        double terrainHeight,
        @Nullable RiverInfo retainedReceiverInfo,
        @Nullable NTERiverHydrology.ColumnProfile supplementalProfile,
        double terrainUplift
    )
    {
        tfe$retainedReceiverBedHeight = Double.POSITIVE_INFINITY;
        tfe$retainedReceiverHeight = Double.POSITIVE_INFINITY;
        tfe$retainedReceiverNormDistSq = retainedReceiverInfo == null
            ? Double.POSITIVE_INFINITY
            : retainedReceiverInfo.normDistSq();
        if (!NTERiverHydrology.usesConfluenceCarvingUnion(supplementalProfile, retainedReceiverInfo))
        {
            return Double.POSITIVE_INFINITY;
        }
        final Map<NTERiverBlendType, NTERiverNoiseSampler> retainedRiverNoiseSamplers =
            tfe$getOrCreateRetainedRiverNoiseSamplers();

        final NTERiverHydrology.ColumnProfile previousProfile = tfe$currentRiverHydrologyProfile;
        final boolean previousForceCaveRiver = tfe$forceSubterraneanCaveRiver;
        tfe$currentRiverHydrologyProfile = null;
        System.arraycopy(
            tfe$nativeRiverBlendWeights,
            0,
            tfe$exactRiverBlendWeights,
            0,
            tfe$exactRiverBlendWeights.length
        );
        final double nativeCaveWeight = tfe$adjustExactRiverWeightsForCaves();
        System.arraycopy(
            tfe$exactRiverBlendWeights,
            0,
            tfe$retainedRiverDensityBlendWeights,
            0,
            tfe$exactRiverBlendWeights.length
        );
        final double caveTransitionTerrainUplift = terrainUplift
            * tfe$caveTransitionTerrainUpliftProtection(nativeCaveWeight);
        tfe$forceSubterraneanCaveRiver = false;
        final double retainedReceiverHeight = tfe$adjustHeightForExactRiverContributions(
            retainedRiverNoiseSamplers,
            terrainHeight,
            retainedReceiverInfo,
            nativeCaveWeight,
            caveTransitionTerrainUplift
        );
        tfe$retainedReceiverHeight = retainedReceiverHeight;
        final boolean needsReceiverBed = supplementalProfile != null
            && supplementalProfile.inWaterCore()
            && supplementalProfile.receiverBedBlendWeight() > 0d;
        if (needsReceiverBed)
        {
            final double receiverShapeBedHeight = tfe$sampleReceiverFloorBelowInnerBank(
                retainedRiverNoiseSamplers,
                terrainHeight,
                retainedReceiverInfo,
                retainedReceiverHeight,
                nativeCaveWeight,
                caveTransitionTerrainUplift
            );
            // Restore every stateful receiver sampler to this column's actual
            // distance after probing inward; density generation consumes the
            // state left by this final call.
            tfe$adjustHeightForExactRiverContributions(
                retainedRiverNoiseSamplers,
                terrainHeight,
                retainedReceiverInfo,
                nativeCaveWeight,
                caveTransitionTerrainUplift
            );
            final double receiverDensityBedHeight = nativeCaveWeight >= 0.25d
                ? tfe$sampleReceiverDensityBed(
                    retainedRiverNoiseSamplers,
                    supplementalProfile,
                    terrainHeight - terrainUplift,
                    terrainHeight,
                    terrainUplift
                )
                : Double.POSITIVE_INFINITY;
            tfe$retainedReceiverBedHeight = Math.min(
                receiverShapeBedHeight,
                receiverDensityBedHeight
            );
        }

        tfe$currentRiverHydrologyProfile = previousProfile;
        tfe$forceSubterraneanCaveRiver = previousForceCaveRiver;
        System.arraycopy(
            tfe$nativeRiverBlendWeights,
            0,
            tfe$exactRiverBlendWeights,
            0,
            tfe$exactRiverBlendWeights.length
        );
        return retainedReceiverHeight;
    }

    @Override
    public int[] tfe$getPreVolcanicHeights()
    {
        return tfe$preVolcanicHeights;
    }

    @Override
    public int[] tfe$getSurfaceIntegrityDepth()
    {
        return tfe$surfaceIntegrityDepth;
    }

    @Unique
    private static int tfe$computeSurfaceIntegrityDepth(double preCenteredFeatureHeight, double centeredFeatureHeight)
    {
        final double heightDelta = centeredFeatureHeight - preCenteredFeatureHeight;
        return heightDelta > 0d ? (int) heightDelta * 3 : 0;
    }

    @Unique
    private double tfe$sampleReceiverFloorBelowInnerBank(
        Map<NTERiverBlendType, NTERiverNoiseSampler> retainedRiverNoiseSamplers,
        double terrainHeight,
        RiverInfo retainedReceiverInfo,
        double outerHeight,
        double nativeCaveWeight,
        double caveTransitionTerrainUplift
    )
    {
        final double outerNormDistSq = Math.min(
            retainedReceiverInfo.normDistSq(),
            NTERiverHydrology.TFC_WATER_CORE_RADIUS_SQ
        );
        final int steps = Math.max(1, Mth.ceil(outerNormDistSq / 0.015d));
        double previousHeight = retainedReceiverInfo.normDistSq() > outerNormDistSq
            ? tfe$adjustHeightForExactRiverContributions(
                retainedRiverNoiseSamplers,
                terrainHeight,
                NTERiverHydrology.retainedReceiverCrossSectionSampleInfo(retainedReceiverInfo, outerNormDistSq),
                nativeCaveWeight,
                caveTransitionTerrainUplift
            )
            : outerHeight;
        for (int step = 1; step <= steps; step++)
        {
            final double normDistSq = outerNormDistSq * (1d - step / (double) steps);
            final double sampleHeight = tfe$adjustHeightForExactRiverContributions(
                retainedRiverNoiseSamplers,
                terrainHeight,
                NTERiverHydrology.retainedReceiverCrossSectionSampleInfo(retainedReceiverInfo, normDistSq),
                nativeCaveWeight,
                caveTransitionTerrainUplift
            );
            if (NTERiverHydrology.crossesReceiverInnerBankCliff(previousHeight, sampleHeight))
            {
                return sampleHeight;
            }
            previousHeight = sampleHeight;
        }
        return outerHeight;
    }

    @Unique
    private double tfe$sampleReceiverDensityBed(
        Map<NTERiverBlendType, NTERiverNoiseSampler> retainedRiverNoiseSamplers,
        NTERiverHydrology.ColumnProfile supplementalProfile,
        double terrainUpliftBaseHeight,
        double terrainUpliftTopHeight,
        double terrainUplift
    )
    {
        final int waterBlockY = supplementalProfile.waterBlockY();
        return NTERiverHydrology.receiverDensityBedY(
            waterBlockY,
            waterBlockY - 32,
            y -> tfe$sampleRetainedReceiverDensity(
                retainedRiverNoiseSamplers,
                y,
                terrainUpliftBaseHeight,
                terrainUpliftTopHeight,
                terrainUplift
            ) > BiomeNoiseSampler.AIR_THRESHOLD
        );
    }

    @Unique
    private double tfe$sampleRetainedReceiverDensity(
        Map<NTERiverBlendType, NTERiverNoiseSampler> retainedRiverNoiseSamplers,
        int y,
        double terrainUpliftBaseHeight,
        double terrainUpliftTopHeight,
        double terrainUplift
    )
    {
        double initialNoise = 0d;
        for (Object2DoubleMap.Entry<BiomeNoiseSampler> entry : columnBiomeNoiseSamplers.object2DoubleEntrySet())
        {
            initialNoise += entry.getKey().noise(y) * entry.getDoubleValue();
        }
        if (terrainUplift > 0d && y > terrainUpliftBaseHeight && y <= terrainUpliftTopHeight)
        {
            initialNoise = Math.min(initialNoise, BiomeNoiseSampler.SOLID);
        }

        double receiverNoise = 0d;
        for (NTERiverBlendType type : NTERiverBlendType.ALL)
        {
            final double weight = tfe$retainedRiverDensityBlendWeights[type.ordinal()];
            if (type == NTERiverBlendType.NONE)
            {
                receiverNoise += weight * initialNoise;
            }
            else if (weight > 0d)
            {
                final NTERiverNoiseSampler sampler = retainedRiverNoiseSamplers.get(type);
                if (sampler != null)
                {
                    receiverNoise += weight * sampler.noise(y, initialNoise);
                }
            }
        }
        return receiverNoise;
    }

    @Unique
    private Map<NTERiverBlendType, NTERiverNoiseSampler> tfe$getOrCreateRetainedRiverNoiseSamplers()
    {
        Map<NTERiverBlendType, NTERiverNoiseSampler> samplers = tfe$retainedRiverNoiseSamplers;
        if (samplers.isEmpty() && tfe$retainedRiverNoiseSamplerFactory != null)
        {
            synchronized (this)
            {
                samplers = tfe$retainedRiverNoiseSamplers;
                final Supplier<Map<NTERiverBlendType, NTERiverNoiseSampler>> factory =
                    tfe$retainedRiverNoiseSamplerFactory;
                if (samplers.isEmpty() && factory != null)
                {
                    samplers = factory.get();
                    tfe$retainedRiverNoiseSamplers = samplers;
                    tfe$retainedRiverNoiseSamplerFactory = null;
                }
            }
        }
        return samplers;
    }

    @Unique
    private double tfe$adjustHeightForExactRiverContributions(double height, @Nullable RiverInfo info, double initialCaveWeight, double caveTransitionTerrainUplift)
    {
        return tfe$adjustHeightForExactRiverContributions(
            tfe$exactRiverNoiseSamplers,
            height,
            info,
            initialCaveWeight,
            caveTransitionTerrainUplift
        );
    }

    @Unique
    private double tfe$adjustHeightForExactRiverContributions(
        Map<NTERiverBlendType, NTERiverNoiseSampler> samplers,
        double height,
        @Nullable RiverInfo info,
        double initialCaveWeight,
        double caveTransitionTerrainUplift
    )
    {
        if (info != null || tfe$currentRiverHydrologyProfile != null)
        {
            double riverBlendHeight = 0d;
            for (NTERiverBlendType type : NTERiverBlendType.ALL)
            {
                final double weight = tfe$exactRiverBlendWeights[type.ordinal()];
                final NTERiverNoiseSampler sampler = samplers.get(type);
                if (type == NTERiverBlendType.NONE)
                {
                    riverBlendHeight += weight * height;
                }
                else if (weight > 0 && sampler != null)
                {
                    final boolean caveTransition = type == NTERiverBlendType.CAVE && caveTransitionTerrainUplift > 0d;
                    final double sampleHeight = caveTransition ? height - caveTransitionTerrainUplift : height;
                    final double sampledHeight = sampler.setColumnAndSampleHeight(info, tfe$currentRiverHydrologyProfile, blockX, blockZ, sampleHeight, initialCaveWeight, weight);
                    riverBlendHeight += weight * (caveTransition ? sampledHeight + caveTransitionTerrainUplift : sampledHeight);
                }
            }
            return riverBlendHeight;
        }

        Arrays.fill(tfe$exactRiverBlendWeights, 0);
        tfe$exactRiverBlendWeights[NTERiverBlendType.NONE.ordinal()] = 1.0;
        return height;
    }

    @Unique
    private void tfe$trace(String stage, Object2DoubleMap<BiomeExtension> biomeWeights, double height, double normalHeight, double shoreHeight, double shoreWeight, double oceanWeight, double landWeight, double maxShoreWeight, @Nullable BiomeExtension shoreBiomeAt)
    {
        System.out.printf(
            "[TFE][RuntimeTrace][terrain_cut][%s] x=%d z=%d h=%.3f normalH=%.3f shoreH=%.3f landW=%.3f shoreW=%.3f oceanW=%.3f maxShoreW=%.3f shoreBiome=%s biomeWeights=%s shoreWeights=%s%n",
            stage,
            blockX,
            blockZ,
            height,
            normalHeight,
            shoreHeight,
            landWeight,
            shoreWeight,
            oceanWeight,
            maxShoreWeight,
            tfe$biomeName(shoreBiomeAt),
            tfe$formatBiomeWeights(biomeWeights),
            tfe$formatShoreWeights()
        );
    }

    @Unique
    private void tfe$traceFinal(Object2DoubleMap<BiomeExtension> biomeWeights, double baseHeight, double shoreAdjustedHeight, double centeredFeatureHeight, double terrainUpliftBaseHeight, double terrainUplift, double initialCaveWeight, double caveTransitionTerrainUplift, @Nullable RiverInfo info, double finalHeight)
    {
        System.out.printf(
            "[TFE][RuntimeTrace][terrain_cut][final] x=%d z=%d base=%.3f shore=%.3f centered=%.3f upliftBase=%.3f uplift=%.3f caveInitial=%.3f caveTransitionUplift=%.3f terrain=%.3f final=%.3f river=%s hydrology=%s exactWeights=%s nativeWeights=%s biomeWeights=%s noRiverBiome=%s upliftSources=%s%n",
            blockX,
            blockZ,
            baseHeight,
            shoreAdjustedHeight,
            centeredFeatureHeight,
            terrainUpliftBaseHeight,
            terrainUplift,
            initialCaveWeight,
            caveTransitionTerrainUplift,
            tfe$currentRiverTerrainHeight,
            finalHeight,
            tfe$formatRiverInfo(info),
            tfe$formatRiverHydrologyProfile(),
            tfe$formatExactRiverWeights(),
            tfe$formatRiverWeights(tfe$nativeRiverBlendWeights),
            tfe$formatBiomeWeights(biomeWeights),
            tfe$biomeName(biomeSource.getBiomeExtensionNoRiver(net.minecraft.core.QuartPos.fromBlock(blockX), net.minecraft.core.QuartPos.fromBlock(blockZ))),
            tfe$formatTerrainUpliftSources()
        );
    }

    @Unique
    private String tfe$formatRiverHydrologyProfile()
    {
        final NTERiverHydrology.ColumnProfile profile = tfe$currentRiverHydrologyProfile;
        if (profile == null)
        {
            return "null";
        }
        return String.format(
            "water=%.3f centerBed=%.3f bed=%.3f radialSq=%.3f radius=%.3f bankRaise=%.3f incision=%.3f bankFill=%.3f receiverBlend=%.3f receiverBedBlend=%.3f receiverNorm=%.3f receiverHeight=%.3f receiverBedTarget=%.3f fill=%s waterAllowed=%s sourceWaterAllowed=%s waterfallLanding=%s headwater=%s kind=%s mode=%s tunnelCeil=%d flow=%s",
            profile.waterSurfaceY(),
            profile.centerBedY(),
            profile.bedY(),
            profile.normalizedDistanceSq(),
            profile.channelRadius(),
            profile.bankRaise(),
            profile.terrainIncision(),
            profile.bankFillWeight(),
            profile.receiverBlendWeight(),
            profile.receiverBedBlendWeight(),
            tfe$retainedReceiverNormDistSq,
            tfe$retainedReceiverHeight,
            tfe$retainedReceiverBedHeight,
            profile.fillAllowed(),
            profile.waterAllowed(),
            profile.sourceWaterAllowed(),
            profile.waterfallLanding(),
            profile.headwater(),
            profile.kind(),
            profile.mode(),
            profile.tunnelCeilingBlockY(),
            profile.flow()
        );
    }

    @Unique
    private String tfe$formatTerrainUpliftSources()
    {
        if (tfe$terrainUpliftSampler == null)
        {
            return "none";
        }
        return tfe$terrainUpliftSampler.debugDescribeContributors(blockX, blockZ);
    }

    @Unique
    private static String tfe$biomeName(@Nullable BiomeExtension biome)
    {
        return biome == null ? "null" : biome.key().location().getPath();
    }

    @Unique
    private static String tfe$formatRiverInfo(@Nullable RiverInfo info)
    {
        if (info == null)
        {
            return "null";
        }
        return String.format("normDistSq=%.3f widthSq=%.3f", info.normDistSq(), info.widthSq());
    }

    @Unique
    private static String tfe$formatBiomeWeights(Object2DoubleMap<BiomeExtension> biomeWeights)
    {
        return biomeWeights.object2DoubleEntrySet().stream()
            .sorted((a, b) -> Double.compare(b.getDoubleValue(), a.getDoubleValue()))
            .map(entry -> tfe$biomeName(entry.getKey()) + "=" + String.format("%.3f", entry.getDoubleValue()))
            .collect(Collectors.joining(","));
    }

    @Unique
    private String tfe$formatExactRiverWeights()
    {
        return tfe$formatRiverWeights(tfe$exactRiverBlendWeights);
    }

    @Unique
    private static String tfe$formatRiverWeights(double[] weights)
    {
        return Arrays.stream(NTERiverBlendType.ALL)
            .filter(type -> weights[type.ordinal()] > 1.0e-6d)
            .map(type -> type.name() + "=" + String.format("%.3f", weights[type.ordinal()]))
            .collect(Collectors.joining(","));
    }

    @Unique
    private String tfe$formatShoreWeights()
    {
        return Arrays.stream(NTEShoreBlendType.ALL)
            .filter(type -> tfe$shoreBlendWeights[type.ordinal()] > 1.0e-6d)
            .map(type -> type.name() + "=" + String.format("%.3f", tfe$shoreBlendWeights[type.ordinal()]))
            .collect(Collectors.joining(","));
    }
}
