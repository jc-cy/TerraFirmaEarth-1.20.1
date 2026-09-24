package com.newterraearth.tfe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.rock.RockSpikeBlock;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.world.feature.cave.CaveSpikesFeature;
import com.newterraearth.tfe.world.river.NTERiverHydrology;
import net.dries007.tfc.world.TFCChunkGenerator;

@Mixin(CaveSpikesFeature.class)
public abstract class CaveSpikesFeatureMixin
{
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void tfe$protectSupplementalRiverCorridor(
        FeaturePlaceContext<NoneFeatureConfiguration> context,
        CallbackInfoReturnable<Boolean> cir
    )
    {
        if (!(context.chunkGenerator() instanceof TFCChunkGenerator))
        {
            return;
        }

        final BlockPos origin = context.origin();
        for (int offsetZ = -1; offsetZ <= 1; offsetZ++)
        {
            for (int offsetX = -1; offsetX <= 1; offsetX++)
            {
                final NTERiverHydrology.ColumnProfile profile = NTERiverHydrology.activeGenerationProfile(
                    origin.getX() + offsetX,
                    origin.getZ() + offsetZ
                );
                if (profile != null && NTERiverHydrology.blocksCaveDecoration(profile, origin.getY()))
                {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }

    @Inject(method = "replaceBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfe$replaceSaltWaterSpike(WorldGenLevel level, BlockPos pos, BlockState state, CallbackInfo ci)
    {
        if (tfe$isTerraceCliffBiome(level, pos))
        {
            ci.cancel();
            return;
        }

        final Block block = level.getBlockState(pos).getBlock();
        if (block == TFCBlocks.SALT_WATER.get())
        {
            level.setBlock(pos, state.setValue(RockSpikeBlock.FLUID, RockSpikeBlock.FLUID.keyFor(TFCFluids.SALT_WATER.getSource())), 3);
            ci.cancel();
        }
    }

    @Inject(method = "replaceBlockWithoutFluid", at = @At("HEAD"), cancellable = true, remap = false)
    private void tfe$replaceSaltWaterSpikeBase(WorldGenLevel level, BlockPos pos, BlockState state, CallbackInfo ci)
    {
        if (tfe$isTerraceCliffBiome(level, pos))
        {
            ci.cancel();
            return;
        }

        final Block block = level.getBlockState(pos).getBlock();
        if (block == TFCBlocks.SALT_WATER.get())
        {
            level.setBlock(pos, state, 3);
            ci.cancel();
        }
    }

    /**
     * Every spike formation is judged once, before TFC writes any of its blocks. The
     * thinning and the stacking test both key on the formation's own root block, so a
     * formation is placed whole or not at all: nothing is ever cut out afterwards, and
     * the hardened cap TFC writes above the root is skipped together with the spike.
     */
    @Inject(
        method = "placeSmallSpike(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;F)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void tfe$filterWholeSpike(
        WorldGenLevel level,
        BlockPos pos,
        BlockState spike,
        BlockState raw,
        Direction direction,
        float sizeWeight,
        CallbackInfo ci
    )
    {
        final NTERiverHydrology.ColumnProfile profile = NTERiverHydrology.activeGenerationProfile(
            pos.getX(),
            pos.getZ()
        );
        if (NTERiverHydrology.blocksCaveSpike(profile, pos.getX(), pos.getY(), pos.getZ())
            || NTERiverHydrology.blocksStackedCaveSpike(level, profile, pos, direction, sizeWeight))
        {
            ci.cancel();
        }
    }

    private static boolean tfe$isTerraceCliffBiome(WorldGenLevel level, BlockPos pos)
    {
        if (tfe$isTerraceCliffBiomeAt(level, pos))
        {
            return true;
        }

        for (Direction direction : Direction.values())
        {
            if (tfe$isTerraceCliffBiomeAt(level, pos.relative(direction)))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean tfe$isTerraceCliffBiomeAt(WorldGenLevel level, BlockPos pos)
    {
        return level.getBiome(pos).unwrapKey()
            .map(key -> {
                final String namespace = key.location().getNamespace();
                final String path = key.location().getPath();
                return namespace.equals("tfc") && (path.equals("terrace_upper") || path.equals("terrace_lower"));
            })
            .orElse(false);
    }
}
