package com.newterraearth.tfe.world;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraftforge.common.ForgeConfigSpec;

import com.newterraearth.tfe.config.NTECommonConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 沃土耕地兼容倍率开放为配置项后，默认值必须等于来源模组当前版本的实际数值，
 * 否则本模组的施肥量、作物生长估算和 Jade 的肥料提示都会与对方不一致。
 */
class NTESoilFertilityConfigTest
{
    @Test
    void richSoilFertilityDefaultsMatchTheDocumentedTiers()
    {
        assertEquals(1.3d, defaultOf("fertility", "lush"), 1e-6);
        assertEquals(1.2d, defaultOf("fertility", "enriched"), 1e-6);
        assertEquals(1.1d, defaultOf("fertility", "normal"), 1e-6);
        assertEquals(1.0d, defaultOf("fertility", "barren"), 1e-6);
        assertEquals(0.9d, defaultOf("fertility", "reclaimed"), 1e-6);
    }

    @Test
    void extraFertilizerDefaultsMatchSurvivorsRichSoils()
    {
        // 生存者的沃土扩展 0.3.2 的实际公式：倍率 = 1 + 0.1 × (2 − 枚举序号)，序号按 lush 到 reclaimed 递增
        final ForgeConfigSpec.ConfigValue<?> value = NTECommonConfig.SPEC
            .getValues()
            .get(List.of("rich_soil_farmland", "extra_fertilizer"));
        assertNotNull(value, "缺少配置项 rich_soil_farmland.extra_fertilizer");
        assertEquals(List.of(1.2d, 1.1d, 1.0d, 0.9d, 0.8d), value.getDefault());
    }

    @Test
    void unknownTierFallsBackToPlainSoil()
    {
        assertEquals(1.0f, NTECommonConfig.getRichSoilFertility("unknown"));
        assertEquals(1.0f, NTECommonConfig.getRichSoilExtraFertilizer("unknown"));
    }

    private static double defaultOf(String group, String tier)
    {
        final ForgeConfigSpec.ConfigValue<?> value = NTECommonConfig.SPEC
            .getValues()
            .get(List.of("rich_soil_farmland", group, tier));
        assertNotNull(value, "缺少配置项 rich_soil_farmland." + group + "." + tier);
        return ((Number) value.getDefault()).doubleValue();
    }
}
