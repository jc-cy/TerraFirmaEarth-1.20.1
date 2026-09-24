package com.newterraearth.tfe.mixin;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.levelgen.Beardifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.dries007.tfc.world.ChunkBaseBlockSource;
import net.dries007.tfc.world.ChunkNoiseFiller;
import net.dries007.tfc.world.MutableDensityFunctionContext;
import net.dries007.tfc.world.TFCAquifer;
import net.dries007.tfc.world.biome.BiomeBlendType;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.TFCBiomes;
import net.dries007.tfc.world.noise.ChunkNoiseSamplingSettings;
import net.dries007.tfc.world.noise.TrilinearInterpolator;
import net.dries007.tfc.world.river.Flow;
import net.dries007.tfc.world.river.RiverInfo;

import com.newterraearth.tfe.world.NTEChunkBaseBlockSourceAccess;
import com.newterraearth.tfe.world.NTEChunkHeightFillerAccess;
import com.newterraearth.tfe.world.river.NTERiverBlendType;
import com.newterraearth.tfe.world.river.NTERiverCaveProtection;
import com.newterraearth.tfe.world.river.NTERiverHydrology;
import com.newterraearth.tfe.world.river.NTERiverNoiseSampler;
import com.newterraearth.tfe.world.shore.NTEShoreBlendType;
import com.newterraearth.tfe.world.shore.NTEShoreNoiseSampler;

import static net.dries007.tfc.world.TFCChunkGenerator.SEA_LEVEL_Y;

@Mixin(value = ChunkNoiseFiller.class, remap = false)
public abstract class ChunkNoiseFillerMixin
{
    @Unique private static final double TFE_NATIVE_RIVER_BANK_RADIUS_SQ = 4d;

    @Shadow private int[] surfaceHeight;
    @Shadow private BiomeExtension[] localBiomes;
    @Shadow private BiomeExtension[] localBiomesNoRivers;
    @Shadow private double[] localBiomeWeights;
    @Shadow private ChunkBaseBlockSource baseBlockSource;
    @Shadow private TrilinearInterpolator noiseCaves;
    @Shadow private TrilinearInterpolator noodleToggle;
    @Shadow private TrilinearInterpolator noodleThickness;
    @Shadow private TrilinearInterpolator noodleRidgeA;
    @Shadow private TrilinearInterpolator noodleRidgeB;
    @Shadow private Beardifier beardifier;
    @Shadow private MutableDensityFunctionContext mutableDensityFunctionContext;
    @Shadow private TFCAquifer aquifer;
    @Shadow private ChunkNoiseSamplingSettings settings;

    @ModifyVariable(
        method = "fillColumn",
        at = @At("STORE"),
        ordinal = 0
    )
    private Flow tfe$applyHydrologyColumnFlow(Flow nativeFlow)
    {
        final NTEChunkHeightFillerAccess access = (NTEChunkHeightFillerAccess) this;
        final NTERiverHydrology.ColumnProfile profile = access.tfe$getRiverHydrologyProfile(access.tfe$getLocalX(), access.tfe$getLocalZ());
        return NTERiverHydrology.effectiveColumnFlow(profile, nativeFlow);
    }

    /**
     * @author Codex
     * @reason Port the 1.21 shore density pass after river carving noise.
     */
    @Overwrite
    private double calculateNoiseAtHeight(int y, double heightNoiseValue)
    {
        final NTEChunkHeightFillerAccess access = (NTEChunkHeightFillerAccess) this;
        final Object2DoubleMap<net.dries007.tfc.world.BiomeNoiseSampler> columnBiomeNoiseSamplers = access.tfe$getColumnBiomeNoiseSamplers();
        final double[] riverBlendWeights = access.tfe$getExactRiverBlendWeights();
        final NTERiverHydrology.ColumnProfile riverProfile = access.tfe$getRiverHydrologyProfile(access.tfe$getLocalX(), access.tfe$getLocalZ());
        final double riverTerrainHeight = access.tfe$getRiverTerrainHeight(access.tfe$getLocalX(), access.tfe$getLocalZ());
        final double terrainHeightNoiseValue = riverProfile == null
            ? heightNoiseValue
            : riverTerrainHeight;

        double noise = 0;
        for (Object2DoubleMap.Entry<net.dries007.tfc.world.BiomeNoiseSampler> entry : columnBiomeNoiseSamplers.object2DoubleEntrySet())
        {
            final net.dries007.tfc.world.BiomeNoiseSampler sampler = entry.getKey();
            noise += sampler.noise(y) * entry.getDoubleValue();
        }

        final double initialNoise = tfe$applyTerrainUpliftLayerNoise(y, noise, access);
        if (access.tfe$usesConfluenceCarvingUnion())
        {
            final double supplementalNoise = tfe$sampleExactRiverDensity(
                y,
                initialNoise,
                access.tfe$getSupplementalRiverBlendWeights(),
                access.tfe$getExactRiverNoiseSamplers()
            );
            final double retainedNoise = tfe$sampleExactRiverDensity(
                y,
                initialNoise,
                access.tfe$getRetainedRiverBlendWeights(),
                access.tfe$getRetainedRiverNoiseSamplers()
            );
            // Biome/rivers use larger pre-inversion values for air. Taking
            // max therefore produces the Boolean union of both excavations:
            // either the creek or receiver may remove terrain, neither may
            // rebuild a rock/gravel fin left by the other.
            noise = Math.max(supplementalNoise, retainedNoise);
        }
        else
        {
            noise = tfe$sampleExactRiverDensity(
                y,
                initialNoise,
                riverBlendWeights,
                access.tfe$getExactRiverNoiseSamplers()
            );
        }
        final double riverAdjustedNoise = noise;

        if (access.tfe$hasShoreRuntime())
        {
            final double[] shoreBlendWeights = access.tfe$getShoreBlendWeights();
            noise = 0;
            for (NTEShoreBlendType type : NTEShoreBlendType.ALL)
            {
                final double weight = shoreBlendWeights[type.ordinal()];
                if (type == NTEShoreBlendType.NONE)
                {
                    noise += weight * riverAdjustedNoise;
                }
                else if (weight > 0)
                {
                    final NTEShoreNoiseSampler sampler = access.tfe$getShoreNoiseSamplers().get(type);
                    if (sampler != null)
                    {
                        noise += weight * sampler.noise(tfe$shoreNoiseY(type, y, access), riverAdjustedNoise);
                    }
                }
            }
        }
        // TFE river density intentionally runs through the raised terrain; keep its
        // cut / bed-shell result authoritative while protecting non-river volcano columns.
        final int surfaceIntegrityDepth = access.tfe$getSurfaceIntegrityDepth()[access.tfe$getLocalX() + 16 * access.tfe$getLocalZ()];
        if (surfaceIntegrityDepth > 0 && !tfe$hasExactRiverDensity(access))
        {
            final int carveBelowHeight = (int) heightNoiseValue - surfaceIntegrityDepth;
            noise = Mth.clampedMap(y, carveBelowHeight - 10, carveBelowHeight, noise, 0d);
        }
        noise = tfe$protectTerrainUpliftLayerAfterShore(y, noise, access);

        if (riverProfile != null
            && riverProfile.receiverBlendWeight() > 0d
            && y > Math.floor(riverTerrainHeight))
        {
            // Height already stores min(creek cut, receiver cut). Force the
            // density pass to honor that same Boolean union exactly. Without
            // this hard ceiling, vegetation/shore density can re-solidify a
            // column above the lower envelope after both river samplers have
            // correctly excavated it, recreating the apparent detached pillar.
            noise = Math.max(noise, net.dries007.tfc.world.BiomeNoiseSampler.AIR_THRESHOLD + 1d);
        }

        noise = net.dries007.tfc.world.BiomeNoiseSampler.AIR_THRESHOLD - noise;
        if (y > terrainHeightNoiseValue)
        {
            noise -= (y - terrainHeightNoiseValue) * 0.2f;
        }

        return Mth.clamp(noise, -1, 1);
    }

    @Unique
    private static double tfe$sampleExactRiverDensity(
        int y,
        double initialNoise,
        double[] weights,
        java.util.Map<NTERiverBlendType, NTERiverNoiseSampler> samplers
    )
    {
        double noise = 0d;
        for (NTERiverBlendType type : NTERiverBlendType.ALL)
        {
            final double weight = weights[type.ordinal()];
            if (type == NTERiverBlendType.NONE)
            {
                noise += weight * initialNoise;
            }
            else if (weight > 0d)
            {
                final NTERiverNoiseSampler sampler = samplers.get(type);
                if (sampler != null)
                {
                    noise += weight * sampler.noise(y, initialNoise);
                }
            }
        }
        return noise;
    }

    @Unique
    private static double tfe$applyTerrainUpliftLayerNoise(int y, double initialNoise, NTEChunkHeightFillerAccess access)
    {
        if (!tfe$isTerrainUpliftLayer(y, access))
        {
            return initialNoise;
        }

        return Math.min(initialNoise, net.dries007.tfc.world.BiomeNoiseSampler.SOLID);
    }

    @Unique
    private static boolean tfe$isTerrainUpliftLayer(int y, NTEChunkHeightFillerAccess access)
    {
        return access.tfe$getTerrainUpliftAmount() > 0d
            && y > access.tfe$getTerrainUpliftBaseHeight()
            && y <= access.tfe$getTerrainUpliftTopHeight();
    }

    @Unique
    private static double tfe$protectTerrainUpliftLayerAfterShore(int y, double noise, NTEChunkHeightFillerAccess access)
    {
        if (!tfe$isTerrainUpliftLayer(y, access))
        {
            return noise;
        }

        if (tfe$hasExactRiverDensity(access))
        {
            return noise;
        }
        if (tfe$hasTerrainCarvingShoreDensity(access))
        {
            return noise;
        }

        return Math.min(noise, net.dries007.tfc.world.BiomeNoiseSampler.SOLID);
    }

    @Unique
    private static int tfe$shoreNoiseY(NTEShoreBlendType type, int y, NTEChunkHeightFillerAccess access)
    {
        if (!tfe$isTerrainCarvingShoreType(type))
        {
            return y;
        }

        if (!tfe$isTerrainUpliftAffectedHeight(y, access))
        {
            return y;
        }

        return Mth.floor(y - access.tfe$getTerrainUpliftAmount());
    }

    @Unique
    private static boolean tfe$isTerrainUpliftAffectedHeight(int y, NTEChunkHeightFillerAccess access)
    {
        return access.tfe$getTerrainUpliftAmount() > 0d
            && y <= access.tfe$getTerrainUpliftTopHeight();
    }

    @Unique
    private static boolean tfe$hasTerrainCarvingShoreDensity(NTEChunkHeightFillerAccess access)
    {
        if (!access.tfe$hasShoreRuntime())
        {
            return false;
        }

        final double[] weights = access.tfe$getShoreBlendWeights();
        for (NTEShoreBlendType type : NTEShoreBlendType.ALL)
        {
            if (tfe$isTerrainCarvingShoreType(type) && weights[type.ordinal()] > 1.0e-4d)
            {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean tfe$isTerrainCarvingShoreType(NTEShoreBlendType type)
    {
        return type == NTEShoreBlendType.SANDY
            || type == NTEShoreBlendType.DUNES
            || type == NTEShoreBlendType.SEA_STACKS
            || type == NTEShoreBlendType.ROCKY_SHORES
            || type == NTEShoreBlendType.EMBAYMENTS
            || type == NTEShoreBlendType.UPPER_TERRACE
            || type == NTEShoreBlendType.LOWER_TERRACE
            || type == NTEShoreBlendType.SETBACK_CLIFFS;
    }

    @Unique
    private static boolean tfe$hasExactRiverDensity(NTEChunkHeightFillerAccess access)
    {
        if (access.tfe$isForceSubterraneanCaveRiver())
        {
            return true;
        }

        final double[] weights = access.tfe$getExactRiverBlendWeights();
        for (NTERiverBlendType type : NTERiverBlendType.ALL)
        {
            if (type != NTERiverBlendType.NONE && weights[type.ordinal()] > 1.0e-4d)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @author Codex
     * @reason Keep the shore-ported density pass aligned with native cave noise behavior.
     */
    @Overwrite
    private BlockState calculateBlockStateAtNoise(int y, double terrainNoise)
    {
        double terrainAndCaveNoise = terrainNoise;
        final NTEChunkHeightFillerAccess access = (NTEChunkHeightFillerAccess) this;
        final int blockX = access.tfe$getBlockX();
        final int blockZ = access.tfe$getBlockZ();

        if (noodleToggle.sample() >= 0)
        {
            final double thickness = Mth.clampedMap(noodleThickness.sample(), -1, 1, 0.05, 0.1);
            final double ridgeA = Math.abs(1.5 * noodleRidgeA.sample()) - thickness;
            final double ridgeB = Math.abs(1.5 * noodleRidgeB.sample()) - thickness;
            final double ridge = Math.max(ridgeA, ridgeB);

            terrainAndCaveNoise = Math.min(terrainAndCaveNoise, ridge);
        }

        terrainAndCaveNoise = Math.min(terrainAndCaveNoise, noiseCaves.sample());

        mutableDensityFunctionContext.cursor().set(blockX, y, blockZ);
        final double structureDensity = beardifier.compute(mutableDensityFunctionContext);
        final double terrainWithoutCaves = terrainNoise + structureDensity;
        terrainAndCaveNoise += structureDensity;

        final int localX = access.tfe$getLocalX();
        final int localZ = access.tfe$getLocalZ();
        terrainAndCaveNoise = NTERiverCaveProtection.preserveTerrainDensity(
            terrainWithoutCaves,
            terrainAndCaveNoise,
            NTERiverCaveProtection.protectsDensity(blockX, y, blockZ, surfaceHeight[localX + 16 * localZ])
        );

        final BlockState aquiferState = aquifer.sampleState(blockX, y, blockZ, terrainAndCaveNoise);
        final NTERiverHydrology.ColumnProfile riverProfile = access.tfe$getRiverHydrologyProfile(localX, localZ);
        if (riverProfile != null)
        {
            final double riverTerrainHeight = access.tfe$getRiverTerrainHeight(localX, localZ);
            final int effectiveBedY = NTERiverHydrology.effectiveBedBlockY(riverProfile, riverTerrainHeight);
            if (riverProfile.subterranean() && riverProfile.inChannel())
            {
                // The covered creek section owns a noise shaped arched rock cavity. Its
                // ceiling is bounded both by the planned rock ceiling and by this
                // column's realized surface, so a graded cave mouth keeps its floor
                // intact instead of being carved into from below.
                final int waterY = riverProfile.waterBlockY();
                if (NTERiverHydrology.carvesTunnelCavity(riverProfile, y, blockX, blockZ, riverTerrainHeight))
                {
                    return Blocks.AIR.defaultBlockState();
                }
                if (riverProfile.inWaterCore() && y <= waterY && y > effectiveBedY)
                {
                    return Blocks.WATER.defaultBlockState();
                }
            }
            if (NTERiverHydrology.clearsWetMouthHeadroom(riverProfile, y))
            {
                // Cave-river noise may otherwise leave a suspended shelf
                // through the descending connector. Clear the complete
                // planned vertical descent, but only in the wet core; natural
                // cave walls outside the flowing corridor remain untouched.
                return Blocks.AIR.defaultBlockState();
            }
            if (riverProfile.receiverBlendWeight() > 0d
                && riverProfile.inChannel()
                && y > riverProfile.waterBlockY()
                && aquiferState != null
                && aquiferState.getFluidState().is(net.minecraft.tags.FluidTags.WATER))
            {
                // The receiver-aligned profile owns the descending mouth water
                // surface as well as its terrain. Do not retain the old TFC
                // source-water shelf above that planned surface: it would flow
                // back over the generated dynamic fringe and recreate the
                // raised four-block column after ordinary fluid ticks.
                return Blocks.AIR.defaultBlockState();
            }
            if (riverProfile.inWaterCore()
                && y <= riverProfile.waterBlockY()
                && y > effectiveBedY
                && (aquiferState == null || aquiferState.getFluidState().isEmpty() || aquiferState.getFluidState().is(net.minecraft.tags.FluidTags.WATER)))
            {
                if (NTERiverHydrology.usesDirectionalSupplementalWater(
                    riverProfile,
                    y,
                    SEA_LEVEL_Y - 1
                ))
                {
                    // fillColumn now carries the supplemental route's final
                    // geometric flow independently of the biome river flag;
                    // keep returning vanilla source water here so TFC performs
                    // its standard directional-water conversion in one place.
                    return Blocks.WATER.defaultBlockState();
                }
                // The mouth fringe is deliberately generated as flowing water
                // rather than deferred to player-proximity fluid ticks. The
                // top layer flows forward; lower layers are falling water, and
                // the carver-tail bake continues the complete fall and landing.
                return Blocks.WATER.defaultBlockState().setValue(
                    LiquidBlock.LEVEL,
                    y == riverProfile.waterBlockY() ? 1 : 8
                );
            }
            if (terrainAndCaveNoise <= 0d && NTERiverHydrology.protectsBedAt(riverProfile, y, riverTerrainHeight))
            {
                return baseBlockSource.getBaseBlock(blockX, y, blockZ);
            }
        }
        if (aquiferState != null)
        {
            return aquiferState;
        }
        return baseBlockSource.getBaseBlock(blockX, y, blockZ);
    }

    /**
     * @author Codex
     * @reason Port 1.21 shore saltwater edge handling into local biome caching.
     */
    @Overwrite
    protected void updateLocalCaches(Object2DoubleMap<BiomeExtension> biomeWeights, BiomeExtension biomeAt, @Nullable RiverInfo info, double height)
    {
        final NTEChunkHeightFillerAccess access = (NTEChunkHeightFillerAccess) this;
        final int localX = access.tfe$getLocalX();
        final int localZ = access.tfe$getLocalZ();
        final int localIndex = localX + 16 * localZ;
        final boolean couldBeSalty = access.tfe$couldBeSalty();
        access.tfe$recordRiverHydrologyProfile(localX, localZ);
        final NTERiverHydrology.ColumnProfile riverProfile = access.tfe$getRiverHydrologyProfile(localX, localZ);
        access.tfe$getNativeDryRiverBanks()[localIndex] = info != null
            && info.normDistSq() > 1d
            && info.normDistSq() <= TFE_NATIVE_RIVER_BANK_RADIUS_SQ
            && tfe$hasSurfaceRiverWeight(access.tfe$getExactRiverBlendWeights());

        localBiomesNoRivers[localIndex] = biomeAt;
        // A covered creek normally keeps its original biome: the surface far above
        // the cavity is untouched terrain. Where the creek grades its own cave mouth
        // open, the channel floor is the creek bed itself and must be owned by the
        // creek surface builder, otherwise the reachable water line grows grass.
        final boolean exposedCoveredBed = riverProfile != null
            && riverProfile.gradedMouth()
            && riverProfile.inChannel();
        if (biomeAt.hasRivers() && (
            (height <= SEA_LEVEL_Y + 1 && info != null && info.normDistSq() < 1.1d)
                || (riverProfile != null && riverProfile.surfaceVisible() && riverProfile.inWaterCore())
                || exposedCoveredBed
        ))
        {
            biomeAt = TFCBiomes.RIVER;
        }

        localBiomes[localIndex] = biomeAt;
        final double biomeWeightAt = biomeWeights.getOrDefault(biomeAt, 0.5);
        localBiomeWeights[localIndex] = biomeWeightAt;
        surfaceHeight[localIndex] = riverProfile != null && riverProfile.inWaterCore()
            ? Math.max((int) height, riverProfile.waterBlockY())
            : (int) height;

        double oceanWeight = 0d;
        for (Object2DoubleMap.Entry<BiomeExtension> entry : biomeWeights.object2DoubleEntrySet())
        {
            if (entry.getKey().biomeBlendType() == BiomeBlendType.OCEAN)
            {
                oceanWeight += entry.getDoubleValue();
            }
        }
        final boolean forceCoastalSaltWater = biomeAt != TFCBiomes.RIVER
            && height <= SEA_LEVEL_Y
            && oceanWeight >= 0.05d;

        ((NTEChunkBaseBlockSourceAccess) baseBlockSource).tfe$useAccurateBiome(
            localX,
            localZ,
            biomeAt,
            biomeWeightAt,
            couldBeSalty,
            forceCoastalSaltWater
        );
    }

    @Unique
    private static boolean tfe$hasSurfaceRiverWeight(double[] weights)
    {
        for (NTERiverBlendType type : NTERiverBlendType.ALL)
        {
            if (type != NTERiverBlendType.NONE
                && type != NTERiverBlendType.CAVE
                && weights[type.ordinal()] > 0d)
            {
                return true;
            }
        }
        return false;
    }

}
