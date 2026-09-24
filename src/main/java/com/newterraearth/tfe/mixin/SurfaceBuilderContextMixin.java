package com.newterraearth.tfe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

import net.dries007.tfc.world.surface.SurfaceBuilderContext;

import com.newterraearth.tfe.world.NTESurfaceContext;
import com.newterraearth.tfe.world.river.NTERiverHydrology;

@Mixin(value = SurfaceBuilderContext.class, remap = false)
public abstract class SurfaceBuilderContextMixin
{
    /**
     * A covered creek carves its rock cavity before the surface pass runs, so the
     * column already contains air and water between the terrain surface and the
     * tunnel bed. Every stock surface builder reads that gap as a fresh air column
     * and starts a second soil profile on the tunnel floor, which is exactly how
     * grass ended up inside the flowing underground water. Stop the scan at the
     * planned rock ceiling instead; the terrain above keeps its own surface.
     */
    @ModifyArg(
        method = "buildSurface",
        at = @At(
            value = "INVOKE",
            target = "Lnet/dries007/tfc/world/surface/builder/SurfaceBuilder;buildSurface(Lnet/dries007/tfc/world/surface/SurfaceBuilderContext;II)V"
        ),
        index = 2
    )
    private int tfe$stopSurfaceScanAboveCoveredCreek(
        int endY
    )
    {
        final NTESurfaceContext.Context surfaceContext = NTESurfaceContext.current();
        if (surfaceContext == null)
        {
            return endY;
        }
        final SurfaceBuilderContext context = (SurfaceBuilderContext) (Object) this;
        final BlockPos pos = context.pos();
        final NTERiverHydrology.ColumnProfile profile = surfaceContext.riverProfile(pos.getX(), pos.getZ());
        if (profile == null || !profile.subterranean())
        {
            return endY;
        }
        final int roofStop = profile.tunnelCeilingBlockY() + 1;
        final int surfaceY = context.chunk().getHeight(
            Heightmap.Types.WORLD_SURFACE_WG,
            pos.getX() & 15,
            pos.getZ() & 15
        ) + 1;
        if (surfaceY <= roofStop)
        {
            // The creek graded this column open: its own cut floor is the creek bed
            // and must keep the normal bed handling below the tunnel ceiling.
            return endY;
        }
        return Math.min(Math.max(endY, roofStop), surfaceY - 1);
    }
}
