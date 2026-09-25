package com.newterraearth.tfe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;

import net.dries007.tfc.common.fluids.TFCFluids;

/**
 * Reports TFC's salt water and spring water through vanilla water height queries.
 *
 * <p>TFC tags both fluids as {@code #minecraft:water} and already teaches the vanilla in-water and
 * eye-in-fluid booleans about them, but Forge's {@code Entity#getFluidHeight(TagKey)} answers the
 * {@code FluidTags.WATER} tag by looking up {@code ForgeMod.WATER_TYPE} alone, so anything that
 * measures how deep the water is reads zero in both fluids. Immersive Aircraft's Bamboo Hopper
 * measures exactly that for its pontoon buoyancy, so it settles on the sea floor instead of
 * floating on TFC oceans and rivers.</p>
 *
 * <p>The added scope mirrors TFC's own water-like fluid set (salt water and spring water; TFC river
 * water already uses the vanilla water type). Fluid-type height queries stay untouched so unrelated
 * systems, including TFC's per-type fluid checks, keep their current semantics.</p>
 */
@Mixin(Entity.class)
public abstract class EntityWaterHeightMixin
{
    @Inject(method = "getFluidHeight", at = @At("RETURN"), cancellable = true)
    private void tfe$includeTfcWaterLikeFluidsInWaterHeight(TagKey<Fluid> fluidTag, CallbackInfoReturnable<Double> cir)
    {
        if (fluidTag != FluidTags.WATER)
        {
            return;
        }

        final Entity entity = (Entity) (Object) this;
        final double tfcWaterHeight = Math.max(
            entity.getFluidTypeHeight(TFCFluids.SALT_WATER.type().get()),
            entity.getFluidTypeHeight(TFCFluids.SPRING_WATER.type().get())
        );

        if (tfcWaterHeight > cir.getReturnValue())
        {
            cir.setReturnValue(tfcWaterHeight);
        }
    }
}
