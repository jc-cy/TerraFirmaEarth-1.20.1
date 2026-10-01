package com.newterraearth.tfe.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import net.dries007.tfc.client.ClimateRenderCache;
import net.dries007.tfc.client.particle.TFCParticles;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.wood.TFCLeavesBlock;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.calendar.Season;

import com.newterraearth.tfe.client.NTEClimateRenderHelpers;

@Mixin(value = TFCLeavesBlock.class, remap = false)
public abstract class TFCLeavesBlockMixin
{
    @Shadow @Final private int maxDecayDistance;

    @Shadow protected abstract IntegerProperty getDistanceProperty();

    @Shadow public abstract void createDestructionEffects(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, boolean replaceOnlyAir);

    @Shadow(remap = true) public abstract void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand);

    /**
     * @author Codex
     * @reason Fall leaf particles need the same hemisphere-aware season split as the 1.21 branch.
     */
    @Overwrite(remap = true)
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (!state.getValue(TFCLeavesBlock.PERSISTENT) && random.nextInt(30) == 0)
        {
            if (NTEClimateRenderHelpers.getHemispheralCalendarMonthOfYear(level, pos).getSeason() == Season.FALL || ClimateRenderCache.INSTANCE.getWind().lengthSquared() > 0.42f * 0.42f)
            {
                final BlockState belowState = level.getBlockState(pos.below());
                if (belowState.isAir())
                {
                    final BlockState aboveState = level.getBlockState(pos.above());
                    final ParticleOptions particle = Helpers.isBlock(aboveState, TFCTags.Blocks.SNOW) && random.nextBoolean()
                        ? TFCParticles.SNOWFLAKE.get()
                        : new BlockParticleOption(TFCParticles.FALLING_LEAF.get(), state);
                    ParticleUtils.spawnParticleBelow(level, pos, random, particle);
                }
            }
        }
        TFCLeavesBlock.dripRainwater(level, pos, random);
    }

    /**
     * @author Codex
     * @reason Autumn destruction effects must respect the ported southern hemisphere season offset.
     */
    @Overwrite(remap = true)
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rand)
    {
        // Upstream TFC starts this method with super.randomTick(state, level, pos, rand) // super calls tick(), which
        // refreshes the decay distance (and drives the slow decay config). The previous port dropped that call. Forge
        // 1.20.1 BlockBehaviour#randomTick is verified to be exactly this.tick(...), so calling tick() here restores the
        // upstream behaviour without needing a super call.
        tick(state, level, pos, rand);
        if (state.getValue(getDistanceProperty()) > maxDecayDistance && !state.getValue(TFCLeavesBlock.PERSISTENT))
        {
            level.removeBlock(pos, false);
            if (rand.nextFloat() < 0.01f)
            {
                createDestructionEffects(state, level, pos, rand, false);
            }
            TFCLeavesBlock.doParticles(level, pos.getX() + rand.nextFloat(), pos.getY() + rand.nextFloat(), pos.getZ() + rand.nextFloat(), 1);
        }
        else if (rand.nextFloat() < 0.0005f && NTEClimateRenderHelpers.getHemispheralCalendarMonthOfYear(level, pos).getSeason() == Season.FALL && !state.getValue(TFCLeavesBlock.PERSISTENT))
        {
            createDestructionEffects(state, level, pos, rand, true);
        }
    }
}
