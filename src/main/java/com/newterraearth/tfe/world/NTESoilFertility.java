package com.newterraearth.tfe.world;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.dries007.tfc.util.Helpers;

public final class NTESoilFertility
{
    public static final TagKey<Block> FERTILE_FARMLAND = create("fertile_farmland");
    public static final TagKey<Block> VERY_RICH_FARMLAND = create("very_rich_farmland");
    public static final TagKey<Block> RICH_FARMLAND = create("rich_farmland");
    public static final TagKey<Block> NORMAL_FARMLAND = create("normal_farmland");
    public static final TagKey<Block> POOR_FARMLAND = create("poor_farmland");
    public static final TagKey<Block> VERY_POOR_FARMLAND = create("very_poor_farmland");

    private NTESoilFertility()
    {
    }

    public static float getModifier(BlockState state)
    {
        if (Helpers.isBlock(state, FERTILE_FARMLAND))
        {
            return 1.3f;
        }
        if (Helpers.isBlock(state, VERY_RICH_FARMLAND))
        {
            return 1.2f;
        }
        if (Helpers.isBlock(state, RICH_FARMLAND))
        {
            return 1.1f;
        }
        if (Helpers.isBlock(state, POOR_FARMLAND))
        {
            return 0.9f;
        }
        if (Helpers.isBlock(state, VERY_POOR_FARMLAND))
        {
            return 0.8f;
        }
        return richSoilFarmlandModifier(state);
    }

    /**
     * Fertility lookup for rich soil farmland, recognized by block id in any namespace: each variant
     * works one tier above the soil it is crafted from. This is one of the two lookups this class
     * performs — the tag lookup above and the id lookup here — not a degraded path for either.
     */
    private static float richSoilFarmlandModifier(BlockState state)
    {
        return switch (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath())
        {
            case "rich_soil_farmland", "rich_soil_farmland/lush" -> 1.3f;
            case "rich_soil_farmland/enriched" -> 1.2f;
            case "rich_soil_farmland/normal" -> 1.1f;
            case "rich_soil_farmland/barren" -> 1.0f;
            case "rich_soil_farmland/reclaimed" -> 0.9f;
            default -> 1.0f;
        };
    }

    private static TagKey<Block> create(String name)
    {
        return TagKey.create(Registries.BLOCK, Helpers.identifier(name));
    }
}
