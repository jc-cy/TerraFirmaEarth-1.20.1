package com.newterraearth.tfe.client;

import net.minecraft.resources.ResourceLocation;

import com.newterraearth.tfe.NewTerraEarthMod;

/**
 * Holds the 1.21 green season foliage colormap for the ported seasonal leaf colour.
 * <p>
 * TFC 1.21 replaced {@code tfc:textures/colormap/foliage.png} with a new image and renamed the old one to
 * {@code foliage_evergreen.png}: the 1.20 image is a temperature x rainfall map, while the 1.21 image is a
 * time of year x groundwater map. The ported 1.21 algorithm indexes it as the latter, so it cannot use whatever
 * TFC 1.20 loaded into its own cache - that would be the wrong picture, and a resource pack that redraws the 1.20
 * colormap (for example a TFC visual pack) would make it worse. Loading our own copy keeps the ported algorithm
 * reading the map it was written for, independently of the pack stack.
 */
public final class NTEFoliageColors
{
    private static final int COLORMAP_SIZE = 256 * 256;

    public static final ResourceLocation FOLIAGE_121_LOCATION = new ResourceLocation(NewTerraEarthMod.MOD_ID, "textures/colormap/foliage_121.png");

    private static int[] foliage121 = new int[COLORMAP_SIZE];

    private NTEFoliageColors()
    {
    }

    public static void setFoliage121(int[] foliageColors)
    {
        foliage121 = foliageColors;
    }

    public static int[] getFoliage121()
    {
        return foliage121;
    }
}
