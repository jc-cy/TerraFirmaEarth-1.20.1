package com.newterraearth.tfe.common.block.rope;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NTERopeLootTableResourceTest
{
    private static final String[] ROPE_BLOCK_LOOT_TABLES = {
        "data/tfc/loot_tables/blocks/rope.json",
        "data/tfc/loot_tables/blocks/hanging_rope.json",
        "data/tfc/loot_tables/blocks/steel_rope_anchor.json",
        "data/tfe/loot_tables/blocks/rock/rope_anchor/tuff.json",
        "data/tfe/loot_tables/blocks/bismuth_rope_anchor.json",
        "data/tfe/loot_tables/blocks/bismuth_bronze_rope_anchor.json",
        "data/tfe/loot_tables/blocks/black_bronze_rope_anchor.json",
        "data/tfe/loot_tables/blocks/bronze_rope_anchor.json",
        "data/tfe/loot_tables/blocks/brass_rope_anchor.json",
        "data/tfe/loot_tables/blocks/copper_rope_anchor.json",
        "data/tfe/loot_tables/blocks/gold_rope_anchor.json",
        "data/tfe/loot_tables/blocks/nickel_rope_anchor.json",
        "data/tfe/loot_tables/blocks/rose_gold_rope_anchor.json",
        "data/tfe/loot_tables/blocks/silver_rope_anchor.json",
        "data/tfe/loot_tables/blocks/tin_rope_anchor.json",
        "data/tfe/loot_tables/blocks/zinc_rope_anchor.json",
        "data/tfe/loot_tables/blocks/sterling_silver_rope_anchor.json",
        "data/tfe/loot_tables/blocks/wrought_iron_rope_anchor.json",
        "data/tfe/loot_tables/blocks/cast_iron_rope_anchor.json",
        "data/tfe/loot_tables/blocks/black_steel_rope_anchor.json",
        "data/tfe/loot_tables/blocks/blue_steel_rope_anchor.json",
        "data/tfe/loot_tables/blocks/red_steel_rope_anchor.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/andesite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/basalt.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/chalk.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/chert.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/claystone.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/conglomerate.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/dacite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/diorite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/dolomite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/gabbro.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/gneiss.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/granite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/limestone.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/marble.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/phyllite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/quartzite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/rhyolite.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/schist.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/shale.json",
        "data/tfc/loot_tables/blocks/rock/rope_anchor/slate.json"
    };

    @Test
    void ropeBlockLootTablesUseTheMinecraft120PluralPath()
    {
        final Path resources = Path.of("src/main/resources");
        for (String path : ROPE_BLOCK_LOOT_TABLES)
        {
            assertTrue(Files.isRegularFile(resources.resolve(path)), path);
            assertFalse(Files.exists(resources.resolve(path.replace("/loot_tables/", "/loot_table/"))), path);
        }
    }
}
