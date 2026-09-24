package com.newterraearth.tfe.world.surface;

import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.surface.SurfaceBuilderContext;
import net.dries007.tfc.world.surface.SurfaceState;
import net.dries007.tfc.world.surface.SurfaceStates;
import net.dries007.tfc.world.surface.builder.NormalSurfaceBuilder;
import net.dries007.tfc.world.surface.builder.SurfaceBuilder;
import net.dries007.tfc.world.surface.builder.SurfaceBuilderFactory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import com.newterraearth.tfe.common.NTEBlocks;
import com.newterraearth.tfe.world.NTESurfaceContext;
import com.newterraearth.tfe.world.river.NTERiverHydrology;
import com.newterraearth.tfe.world.soil.NTESoil;
import com.newterraearth.tfe.world.soil.NTESoilBlockType;

public class NTERiverSurfaceBuilder implements SurfaceBuilder
{
    public static final SurfaceBuilderFactory INSTANCE = NTERiverSurfaceBuilder::new;

    private static final NTESoilBlockType[] UNSTABLE_MOUTH_SOILS = {
        NTESoilBlockType.DIRT,
        NTESoilBlockType.GRASS,
        NTESoilBlockType.DUFF,
        NTESoilBlockType.CLAY,
        NTESoilBlockType.CLAY_GRASS,
        NTESoilBlockType.CLAY_DUFF,
        NTESoilBlockType.ROOTED_DIRT,
        NTESoilBlockType.COARSE_DIRT,
        NTESoilBlockType.MUD
    };

    private final long seed;

    protected NTERiverSurfaceBuilder(long seed)
    {
        this.seed = seed;
    }

    @Override
    public void buildSurface(SurfaceBuilderContext context, int startY, int endY)
    {
        final NTESurfaceContext.Context surfaceContext = NTESurfaceContext.current();
        final NTERiverHydrology.ColumnProfile riverProfile = surfaceContext == null
            ? null
            : surfaceContext.riverProfile(context.pos());
        if (riverProfile != null
            && riverProfile.surfaceVisible()
            && riverProfile.inWaterCore()
            && riverProfile.bedBlockY() < riverProfile.waterBlockY())
        {
            context.originalBiome().createSurfaceBuilder(seed).buildSurface(context, startY, endY);
            if (riverProfile.descendingReceiverMouth())
            {
                stabilizeDescendingMouthSurface(context, riverProfile, startY, endY);
            }
            else
            {
                demoteSubmergedOrganicSurface(context, riverProfile, startY, endY);
            }
            return;
        }
        if (exposesSubterraneanCreekBed(riverProfile, startY))
        {
            // A covered creek normally keeps the ambient biome surface far above
            // its rock cavity. Where the creek grades its own cave mouth open, the
            // channel floor is exposed and is the creek bed itself: it must follow
            // the creek bed rules instead of growing a lawn, or the flowing water
            // ends up lined with grass at its own water line.
            context.originalBiome().createSurfaceBuilder(seed).buildSurface(context, startY, endY);
            demoteOrganicSoil(
                context,
                Math.min(startY, riverProfile.waterBlockY()),
                Math.max(endY, riverProfile.bedBlockY() - 3)
            );
            return;
        }

        final BiomeExtension biome = context.originalBiome();
        if (biome.isShore())
        {
            biome.createSurfaceBuilder(seed).buildSurface(context, startY, endY);
        }
        else if (!biome.hasSandyRiverShores())
        {
            NormalSurfaceBuilder.INSTANCE.buildSurface(context, startY, endY);
        }
        else
        {
            SurfaceState state = SurfaceStates.GRAVEL;
            if (context.getSlope() < 2)
            {
                state = NTESurfaceStates.TOP_GRASS_TO_GRAVEL;
            }
            else if (context.getSlope() < 5)
            {
                state = SurfaceStates.RIVER_SAND;
            }
            NormalSurfaceBuilder.INSTANCE.buildSurface(context, startY, endY, state, SurfaceStates.GRAVEL, SurfaceStates.GRAVEL);
        }
    }

    /**
     * A covered creek section whose terrain has already been cut down into its own
     * cavity: that channel column is an open creek bed and owns the creek bed
     * material rather than the ambient biome surface. A column which is still
     * covered keeps its ambient terrain far above the planned ceiling.
     */
    static boolean exposesSubterraneanCreekBed(@Nullable NTERiverHydrology.ColumnProfile profile, int startY)
    {
        return profile != null
            && profile.subterranean()
            && profile.inChannel()
            && startY <= profile.tunnelCeilingBlockY() + 1;
    }

    /**
     * The original biome owns the river-bed material. At raised water levels
     * its surface builder can still regard the bed as dry because TFC compares
     * against the global sea level. Only remove living/organic top blocks here;
     * sand, gravel, mud, rock and the biome's normal soil layering are retained.
     */
    private static void demoteSubmergedOrganicSurface(
        SurfaceBuilderContext context,
        NTERiverHydrology.ColumnProfile profile,
        int startY,
        int endY
    )
    {
        demoteOrganicSoil(
            context,
            Math.min(startY, profile.waterBlockY() - 1),
            Math.max(endY, profile.bedBlockY() - 3)
        );
    }

    private static void demoteOrganicSoil(SurfaceBuilderContext context, int topY, int bottomY)
    {
        for (int y = topY; y >= bottomY; y--)
        {
            final BlockState replacement = nonOrganicSoil(context.getBlockState(y));
            if (replacement != null)
            {
                context.setBlockState(y, replacement);
            }
        }
    }

    @Nullable
    private static BlockState nonOrganicSoil(BlockState state)
    {
        final Block block = state.getBlock();
        for (NTESoil soil : NTESoil.values())
        {
            if (block == NTEBlocks.getBlock(soil, NTESoilBlockType.GRASS).get()
                || block == NTEBlocks.getBlock(soil, NTESoilBlockType.DUFF).get())
            {
                return NTEBlocks.getBlock(soil, NTESoilBlockType.DIRT).get().defaultBlockState();
            }
            if (block == NTEBlocks.getBlock(soil, NTESoilBlockType.CLAY_GRASS).get()
                || block == NTEBlocks.getBlock(soil, NTESoilBlockType.CLAY_DUFF).get())
            {
                return NTEBlocks.getBlock(soil, NTESoilBlockType.CLAY).get().defaultBlockState();
            }
        }
        return null;
    }

    /**
     * A descending receiver mouth exposes the creek lip beside generated
     * falling water. TFC soil is a landslide ingredient, so leaving a dirt or
     * clay skin here makes the bank collapse after the first block update.
     * Stabilize only the already submerged cut-only lip with the local raw
     * rock; ordinary creek beds and flat confluences retain their biome soil.
     */
    private static void stabilizeDescendingMouthSurface(
        SurfaceBuilderContext context,
        NTERiverHydrology.ColumnProfile profile,
        int startY,
        int endY
    )
    {
        final int topY = Math.min(startY, profile.waterBlockY() - 1);
        final int bottomY = Math.max(endY, profile.bedBlockY() - 3);
        final BlockState stableRock = context.getRock().raw().defaultBlockState();
        for (int y = topY; y >= bottomY; y--)
        {
            if (isUnstableMouthSoil(context.getBlockState(y)))
            {
                context.setBlockState(y, stableRock);
            }
        }
    }

    private static boolean isUnstableMouthSoil(BlockState state)
    {
        final Block block = state.getBlock();
        for (NTESoil soil : NTESoil.values())
        {
            for (NTESoilBlockType type : UNSTABLE_MOUTH_SOILS)
            {
                if (block == NTEBlocks.getBlock(soil, type).get())
                {
                    return true;
                }
            }
        }
        return false;
    }
}
