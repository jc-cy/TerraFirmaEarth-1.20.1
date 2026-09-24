package com.newterraearth.tfe.common.block.rope;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金属绳锚是 TFE 自己扩展的内容（上游只有钢绳锚），资源全部手写，因此需要显式核对闭包：
 * 每种金属都要有模型、物品模型、方块状态、配方、加热、物品热量和中英文名称。
 */
class NTEMetalRopeAnchorResourceTest
{
    private static final String[] METALS = {
        "bismuth", "bismuth_bronze", "black_bronze", "bronze", "brass", "copper", "gold", "nickel",
        "rose_gold", "silver", "tin", "zinc", "sterling_silver", "wrought_iron", "cast_iron",
        "black_steel", "blue_steel", "red_steel"
    };

    private static final String[] MODEL_SUFFIXES = { "", "_stage1", "_stage2", "_stage3", "_rope" };

    @Test
    void everyMetalWithRodsHasACompleteRopeAnchorResourceSet() throws IOException
    {
        final Path resources = Path.of("src/main/resources");
        final String zhLang = Files.readString(resources.resolve("assets/tfe/lang/zh_cn.json"));
        final String enLang = Files.readString(resources.resolve("assets/tfe/lang/en_us.json"));
        for (String metal : METALS)
        {
            final String name = metal + "_rope_anchor";
            assertFile(resources, "assets/tfe/blockstates/" + name + ".json");
            assertFile(resources, "assets/tfe/models/item/" + name + ".json");
            for (String suffix : MODEL_SUFFIXES)
            {
                assertFile(resources, "assets/tfe/models/block/" + name + suffix + ".json");
            }
            assertFile(resources, "data/tfe/recipes/crafting/" + name + ".json");
            assertFile(resources, "data/tfe/recipes/heating/" + name + ".json");
            assertFile(resources, "data/tfe/tfc/item_heats/" + name + ".json");
            assertTrue(zhLang.contains("\"block.tfe." + name + "\""), name);
            assertTrue(enLang.contains("\"block.tfe." + name + "\""), name);
        }
    }

    @Test
    void metalRopeAnchorBlockStatesDeclareEveryReinforcementStage() throws IOException
    {
        final Path resources = Path.of("src/main/resources");
        // 钢锚在 tfc 命名空间、其余金属在 tfe 命名空间，但共用同一套 stage 变体。
        final String[] statePaths = {
            "assets/tfc/blockstates/steel_rope_anchor.json",
            "assets/tfe/blockstates/copper_rope_anchor.json",
            "assets/tfe/blockstates/red_steel_rope_anchor.json"
        };
        for (String path : statePaths)
        {
            final String json = Files.readString(resources.resolve(path));
            for (int stage = 0; stage <= 3; stage++)
            {
                assertTrue(json.contains("\"stage=" + stage + ",has_rope=false,facing=north\""), path + " stage " + stage);
            }
            for (String facing : new String[] { "north", "east", "south", "west" })
            {
                assertTrue(json.contains("\"stage=3,has_rope=true,facing=" + facing + "\""), path + " roped " + facing);
            }
        }
    }

    @Test
    void reinforcedAnchorsReuseTheUpstreamGeometry() throws IOException
    {
        final Path resources = Path.of("src/main/resources");
        // 敲满后必须和上游绳锚完全同高同形，否则带绳模型和绳索位置都会跟着偏。
        assertContains(resources, "assets/tfe/models/block/copper_rope_anchor_stage3.json", "\"parent\": \"tfc:block/rope_anchor\"");
        assertContains(resources, "assets/tfe/models/block/copper_rope_anchor_rope.json", "\"parent\": \"tfc:block/horizontal_rope_anchored\"");
        assertContains(resources, "assets/tfc/models/block/steel_rope_anchor.json", "\"parent\": \"tfc:block/rope_anchor\"");
        assertContains(resources, "assets/tfc/models/block/steel_rope_anchor_rope.json", "\"parent\": \"tfc:block/horizontal_rope_anchored\"");
        // 物品图标使用敲满后的原版造型，避免加长模型溢出背包格子。
        assertContains(resources, "assets/tfe/models/item/copper_rope_anchor.json", "\"parent\": \"tfe:block/copper_rope_anchor_stage3\"");
    }

    @Test
    void unreinforcedShaftsAreTallerAndShrinkByTwoPixels() throws IOException
    {
        final Path resources = Path.of("src/main/resources");
        // 主段固定 16 像素、原样取整张贴图，敲满后就是原版模型；多出来的像素放在下面，用贴图最上面
        // 几行 1:1 补齐。每敲一下整体下沉 2 像素，主段起点随之降低。必须与 NTEMetalRopeAnchorBlock.STAGE_SHAPES 一致。
        assertShaft(resources, "metal_rope_anchor_stage0.json", 22, 6);
        assertShaft(resources, "metal_rope_anchor_stage1.json", 20, 4);
        assertShaft(resources, "metal_rope_anchor_stage2.json", 18, 2);
        // 钢锚的未加固模型在 tfc 命名空间，且不能覆盖上游原版 steel_rope_anchor.json。
        assertContains(resources, "assets/tfc/models/block/steel_rope_anchor_stage0.json", "\"parent\": \"tfe:block/metal_rope_anchor_stage0\"");
        final String steelState = Files.readString(resources.resolve("assets/tfc/blockstates/steel_rope_anchor.json"));
        assertTrue(steelState.contains("\"stage=0,has_rope=false,facing=north\": { \"model\": \"tfc:block/steel_rope_anchor_stage0\" }"), "steel stage 0");
        assertTrue(steelState.contains("\"stage=3,has_rope=false,facing=north\": { \"model\": \"tfc:block/steel_rope_anchor\" }"), "steel stage 3");
    }

    @Test
    void ropeAnchorGuideEntryCyclesEveryAnchorAndLinksThemToTheRopePage() throws IOException
    {
        final Path resources = Path.of("src/main/resources");
        // 书里用一个 spotlight 页列出全部绳锚（Patchouli 每 20 tick 轮换一次图标），
        // extra_recipe_mappings 则把这些绳锚的配方链接统一指回绳索这一章的首屏。
        for (String lang : new String[] { "zh_cn", "en_us" })
        {
            final String json = Files.readString(resources.resolve(
                "assets/tfc/patchouli_books/field_guide/" + lang + "/entries/mechanics/ropes.json"));
            assertTrue(json.contains("\"type\": \"patchouli:spotlight\""), lang + " 缺少轮切页");
            final String spotlightItems = spotlightItemLine(json);
            assertAnchorMapping(json, lang, spotlightItems, "tfc:steel_rope_anchor");
            for (String metal : METALS)
            {
                assertAnchorMapping(json, lang, spotlightItems, "tfe:" + metal + "_rope_anchor");
            }
        }
    }

    private static String spotlightItemLine(String json)
    {
        for (String line : json.split("\n"))
        {
            final String trimmed = line.trim();
            if (trimmed.startsWith("\"item\": \"")) return trimmed;
        }
        return "";
    }

    private static void assertAnchorMapping(String json, String lang, String spotlightItems, String anchor)
    {
        assertTrue(spotlightItems.contains(anchor), lang + " 轮切页缺少 " + anchor);
        assertTrue(json.contains("\"" + anchor + "\": 0"), lang + " 未把 " + anchor + " 指向绳索页");
    }

    private static void assertShaft(Path resources, String fileName, int height, int extra) throws IOException
    {
        final String json = Files.readString(resources.resolve("assets/tfe/models/block/" + fileName));
        // 主段：完整贴图 1:1，结构和上游 rope_anchor 完全一致，只是整体抬高了 extra 像素。
        assertTrue(json.contains("\"from\": [6, " + extra + ", 6]"), fileName + " 主段起点应为 " + extra);
        assertTrue(json.contains("\"from\": [6, 0, 6]"), fileName + " 缺少下段");
        assertTrue(json.contains("\"to\": [10, " + height + ", 10]"), fileName + " 主段顶部应为 " + height);
        assertTrue(json.contains("\"uv\": [0, 0, 4, 16]"), fileName + " 主段必须用整张贴图");
        // 下段：多出来的像素用贴图最上面几行按 1:1 补齐，不能拉伸。
        assertTrue(json.contains("\"to\": [10, " + extra + ", 10]"), fileName + " 下段顶部应为 " + extra);
        assertTrue(json.contains("\"uv\": [0, 0, 4, " + extra + "]"), fileName + " 下段 uv 必须 1:1");
    }

    private static void assertContains(Path resources, String path, String snippet) throws IOException
    {
        assertTrue(Files.readString(resources.resolve(path)).contains(snippet), path + " 缺少 " + snippet);
    }

    private static void assertFile(Path resources, String path)
    {
        assertTrue(Files.isRegularFile(resources.resolve(path)), path);
    }
}
