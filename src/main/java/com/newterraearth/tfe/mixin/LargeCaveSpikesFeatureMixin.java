package com.newterraearth.tfe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dries007.tfc.world.feature.cave.LargeCaveSpikesFeature;
import com.newterraearth.tfe.world.river.NTERiverHydrology;

/**
 * The large spike builds its whole hardened mass in one call, so the covered creek
 * thinning has to be taken here, once per formation, instead of per written block.
 */
@Mixin(LargeCaveSpikesFeature.class)
public abstract class LargeCaveSpikesFeatureMixin
{
    @Inject(
        method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void tfe$filterWholeLargeSpike(
        WorldGenLevel level,
        BlockPos pos,
        BlockState spike,
        BlockState raw,
        Direction direction,
        RandomSource random,
        CallbackInfo ci
    )
    {
        final NTERiverHydrology.ColumnProfile profile = NTERiverHydrology.activeGenerationProfile(
            pos.getX(),
            pos.getZ()
        );
        if (NTERiverHydrology.blocksCaveSpike(profile, pos.getX(), pos.getY(), pos.getZ()))
        {
            ci.cancel();
        }
    }
}
