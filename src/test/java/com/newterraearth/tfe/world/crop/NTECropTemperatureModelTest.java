package com.newterraearth.tfe.world.crop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NTECropTemperatureModelTest
{
    @Test
    void alfalfaIsFoodWithoutChangingItsExistingItemId() throws IOException
    {
        final String cropSource = Files.readString(
            Path.of("src/main/java/com/newterraearth/tfe/world/crop/NTECrop.java"),
            StandardCharsets.UTF_8
        );
        assertTrue(cropSource.contains("ALFALFA(FarmlandBlockEntity.NutrientType.NITROGEN, true, false, \"alfalfa\")"));

        final JsonObject food = resource("data/tfe/tfc/food_items/alfalfa.json");
        assertEquals("tfe:alfalfa", food.getAsJsonObject("ingredient").get("item").getAsString());
        assertEquals(4, food.get("hunger").getAsInt());
        assertEquals(0.5F, food.get("saturation").getAsFloat());
        assertEquals(0.2F, food.get("grain").getAsFloat());
        assertEquals(0.3F, food.get("vegetables").getAsFloat());

        final float decayModifier = food.get("decay_modifier").getAsFloat();
        assertEquals(0.275F, decayModifier);
        assertEquals(80F, 22F / decayModifier, 0.001F);

        assertTagContains("data/tfc/tags/items/foods.json", "tfe:alfalfa");
        assertTagContains("data/tfc/tags/items/foods/vegetables.json", "tfe:alfalfa");
    }

    @Test
    void climateStressAccumulatesAndRecovers()
    {
        // 每次气候判定损失 4%：连续 25 次不适才会枯死。
        assertEquals(4, NTECropTemperatureModel.TICK_DAMAGE);
        assertEquals(25, NTECropTemperatureModel.STRESS_LIMIT / NTECropTemperatureModel.TICK_DAMAGE);
        assertEquals(4, NTECropTemperatureModel.updateStress(0, false, false));
        assertEquals(8, NTECropTemperatureModel.updateStress(4, false, true));
        assertEquals(60, NTECropTemperatureModel.updateStress(56, false, false));
        assertEquals(100, NTECropTemperatureModel.updateStress(96, false, false));
        assertEquals(100, NTECropTemperatureModel.updateStress(100, false, false));
        assertEquals(100, NTECropTemperatureModel.updateStress(100, true, false));

        // 条件合适时恢复 4%；耕地有肥料时恢复速度翻倍（8%）。
        assertEquals(52, NTECropTemperatureModel.updateStress(56, true, false));
        assertEquals(48, NTECropTemperatureModel.updateStress(56, true, true));
        assertEquals(0, NTECropTemperatureModel.updateStress(0, true, false));
        assertEquals(0, NTECropTemperatureModel.updateStress(4, true, false));
        assertEquals(0, NTECropTemperatureModel.updateStress(4, true, true));

        assertFalse(NTECropTemperatureModel.hasFatalStress(99));
        assertTrue(NTECropTemperatureModel.hasFatalStress(100));
        assertEquals(100, NTECropTemperatureModel.healthPercent(0));
        assertEquals(96, NTECropTemperatureModel.healthPercent(4));
        assertEquals(1, NTECropTemperatureModel.healthPercent(99));
        assertEquals(0, NTECropTemperatureModel.healthPercent(100));
    }

    @Test
    void extremeTemperatureDrainsHealthInsteadOfKillingInstantly()
    {
        // 极端温度每次扣 45%：第一、二次活下来，第三次才枯死。
        assertEquals(45, NTECropTemperatureModel.HARD_DAMAGE);
        assertEquals(45, NTECropTemperatureModel.applyHardDamage(0));
        assertEquals(90, NTECropTemperatureModel.applyHardDamage(45));
        assertEquals(100, NTECropTemperatureModel.applyHardDamage(90));
        assertFalse(NTECropTemperatureModel.hasFatalStress(NTECropTemperatureModel.applyHardDamage(45)));
        assertTrue(NTECropTemperatureModel.hasFatalStress(NTECropTemperatureModel.applyHardDamage(90)));
    }

    @Test
    void matureCropsStayAtFullHealth()
    {
        assertEquals(0, NTECropTemperatureModel.MATURE_STRESS);
        assertEquals(100, NTECropTemperatureModel.healthPercent(NTECropTemperatureModel.MATURE_STRESS));
        assertFalse(NTECropTemperatureModel.hasFatalStress(NTECropTemperatureModel.MATURE_STRESS));
    }

    @Test
    void deathRangeExtendsSuitableTemperatureByTenDegrees()
    {
        assertTrue(NTECropTemperatureModel.withinDeathRange(-4f, 22f, -14f));
        assertTrue(NTECropTemperatureModel.withinDeathRange(-4f, 22f, 32f));
        assertFalse(NTECropTemperatureModel.withinDeathRange(-4f, 22f, -14.01f));
        assertFalse(NTECropTemperatureModel.withinDeathRange(-4f, 22f, 32.01f));
    }

    private static void assertTagContains(String path, String item) throws IOException
    {
        final JsonArray values = resource(path).getAsJsonArray("values");
        assertTrue(values.asList().stream().anyMatch(value -> item.equals(value.getAsString())));
    }

    private static JsonObject resource(String path) throws IOException
    {
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources").resolve(path), StandardCharsets.UTF_8))
        {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
