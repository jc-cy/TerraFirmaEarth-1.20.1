package com.newterraearth.tfe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.blocks.plant.fruit.SeasonalPlantBlock;

import com.newterraearth.tfe.world.NTESeasonalHelpers;

/**
 * 玩家采集成熟果实时记录时间戳，作为"采摘休眠期"的起点。
 * <p>
 * 挂在玩家交互（{@code use}）而不是 {@code stateAfterPicking} 上：与 TFC 1.21 一致——狐狸等动物取果不会触发休眠期。
 */
@Mixin(value = SeasonalPlantBlock.class, remap = false)
public abstract class SeasonalPlantBlockMixin
{
    @Inject(method = {"use", "m_6227_"}, at = @At("HEAD"), remap = false)
    private void tfe$markHarvested(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir)
    {
        if (!level.isClientSide
            && state.hasProperty(SeasonalPlantBlock.LIFECYCLE)
            && state.getValue(SeasonalPlantBlock.LIFECYCLE) == Lifecycle.FRUITING)
        {
            NTESeasonalHelpers.markHarvested(level, pos);
        }
    }
}
