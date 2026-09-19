package com.newterraearth.tfe.common.block.rope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NTERopeBlockTagResourceTest
{
    private static final String[] ROCK_ANCHORS = {
        "tfc:rock/rope_anchor/andesite",
        "tfc:rock/rope_anchor/basalt",
        "tfc:rock/rope_anchor/chalk",
        "tfc:rock/rope_anchor/chert",
        "tfc:rock/rope_anchor/claystone",
        "tfc:rock/rope_anchor/conglomerate",
        "tfc:rock/rope_anchor/dacite",
        "tfc:rock/rope_anchor/diorite",
        "tfc:rock/rope_anchor/dolomite",
        "tfc:rock/rope_anchor/gabbro",
        "tfc:rock/rope_anchor/gneiss",
        "tfc:rock/rope_anchor/granite",
        "tfc:rock/rope_anchor/limestone",
        "tfc:rock/rope_anchor/marble",
        "tfc:rock/rope_anchor/phyllite",
        "tfc:rock/rope_anchor/quartzite",
        "tfc:rock/rope_anchor/rhyolite",
        "tfc:rock/rope_anchor/schist",
        "tfc:rock/rope_anchor/shale",
        "tfc:rock/rope_anchor/slate",
        "tfe:rock/rope_anchor/tuff"
    };

    private static final String[] METAL_ANCHORS = {
        "tfe:bismuth_rope_anchor",
        "tfe:bismuth_bronze_rope_anchor",
        "tfe:black_bronze_rope_anchor",
        "tfe:bronze_rope_anchor",
        "tfe:brass_rope_anchor",
        "tfe:copper_rope_anchor",
        "tfe:gold_rope_anchor",
        "tfe:nickel_rope_anchor",
        "tfe:rose_gold_rope_anchor",
        "tfe:silver_rope_anchor",
        "tfe:tin_rope_anchor",
        "tfe:zinc_rope_anchor",
        "tfe:sterling_silver_rope_anchor",
        "tfe:wrought_iron_rope_anchor",
        "tfe:cast_iron_rope_anchor",
        "tfe:black_steel_rope_anchor",
        "tfe:blue_steel_rope_anchor",
        "tfe:red_steel_rope_anchor"
    };

    @Test
    void ropeBlocksAreRegisteredInTheExpectedMinecraft120Tags() throws IOException
    {
        final Path resources = Path.of("src/main/resources");
        assertTagContains(resources.resolve("data/tfc/tags/blocks/mineable_with_sharp_tool.json"), "tfc:rope", "tfc:hanging_rope");
        assertTagContains(resources.resolve("data/minecraft/tags/blocks/climbable.json"), "tfc:rope", "tfc:hanging_rope");
        assertTagContains(resources.resolve("data/minecraft/tags/blocks/mineable/pickaxe.json"), "tfc:steel_rope_anchor");
        assertTagContains(resources.resolve("data/minecraft/tags/blocks/mineable/pickaxe.json"), ROCK_ANCHORS);
        assertTagContains(resources.resolve("data/minecraft/tags/blocks/mineable/pickaxe.json"), METAL_ANCHORS);
    }

    private static void assertTagContains(Path path, String... ids) throws IOException
    {
        assertTrue(Files.isRegularFile(path), path.toString());
        final String json = Files.readString(path);
        for (String id : ids)
        {
            assertTrue(json.contains("\"" + id + "\""), path + " missing " + id);
        }
    }
}
