package com.newterraearth.tfe.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import net.dries007.tfc.client.ClimateRenderCache;
import net.dries007.tfc.client.screen.ClimateScreen;
import net.dries007.tfc.client.screen.TFCContainerScreen;
import net.dries007.tfc.common.container.Container;
import net.dries007.tfc.config.TFCConfig;

import com.newterraearth.tfe.client.NTEClimateRenderCacheBridge;
import com.newterraearth.tfe.client.NTEClimateRenderHelpers;
import com.newterraearth.tfe.client.NTEClimateTemperatureDisplayCompat;
import com.newterraearth.tfe.client.NTEKoppenClimateClassification;

@Mixin(value = ClimateScreen.class, remap = false)
public abstract class ClimateScreenMixin extends TFCContainerScreen<Container>
{
    @Unique private static final int NTE_ALIGN_LEFT = 0;
    @Unique private static final int NTE_ALIGN_CENTER = 1;
    @Unique private static final int NTE_ALIGN_RIGHT = 2;

    protected ClimateScreenMixin(Container container, Inventory playerInv, Component name, ResourceLocation texture)
    {
        super(container, playerInv, name, texture);
    }

    /**
     * @author Codex
     * @reason Render the climate screen using the 1.21 grouped layout while keeping the backported rainfall and Koppen data.
     */
    @Overwrite(remap = false)
    protected void renderLabels(GuiGraphics stack, int mouseX, int mouseY)
    {
        super.renderLabels(stack, mouseX, mouseY);

        final NTEClimateRenderCacheBridge cache = (NTEClimateRenderCacheBridge) (Object) ClimateRenderCache.INSTANCE;
        final float averageTemp = cache.getAverageSeaLevelTemperature();
        final float averageRainfall = cache.getAverageRainfall();
        final float currentTemp = cache.getInstantTemperature();
        final float currentRainfall = cache.getInstantRainfall();
        final float rainVariance = cache.getRainVariance();

        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;
        final Player player = minecraft.player;
        final boolean northernHemisphere = level == null || player == null || NTEClimateRenderHelpers.isNorthernHemisphere(level, player.blockPosition());
        final float displayRainVariance = northernHemisphere ? rainVariance : -rainVariance;
        final float peakRainfall = averageRainfall * (1f + Math.abs(rainVariance));
        final String peakRainfallKey = displayRainVariance > 0f
            ? "tfc.tooltip.climate_peak_rainfall_july"
            : "tfc.tooltip.climate_peak_rainfall_january";
        final NTEKoppenClimateClassification classification = NTEKoppenClimateClassification.classify(averageTemp, averageRainfall, rainVariance, northernHemisphere);

        final Object style = TFCConfig.CLIENT.climateTooltipStyle.get();

        nte$drawLine(stack, Component.translatable(classification.translationKey()), NTE_ALIGN_CENTER, 18);

        nte$drawLine(stack, Component.translatable("tfc.tooltip.climate_temperature_name"), NTE_ALIGN_LEFT, 32);
        nte$drawLine(stack, Component.translatable("tfc.tooltip.climate_temperature_average", NTEClimateTemperatureDisplayCompat.format(style, averageTemp)), NTE_ALIGN_LEFT, -1, 36, 32);
        nte$drawLine(stack, Component.translatable("tfc.tooltip.climate_temperature_now", NTEClimateTemperatureDisplayCompat.format(style, currentTemp)), NTE_ALIGN_LEFT, -1, 96, 32);

        nte$drawLine(stack, Component.translatable("tfc.tooltip.climate_rainfall_name"), NTE_ALIGN_LEFT, 0x202080, 46);
        nte$drawLine(stack, Component.translatable("tfc.tooltip.climate_rainfall_average", String.format("%.0f", averageRainfall)), NTE_ALIGN_LEFT, 0x202080, 36, 46);
        nte$drawLine(stack, Component.translatable("tfc.tooltip.climate_rainfall_now", String.format("%.0f", currentRainfall)), NTE_ALIGN_LEFT, 0x202080, 96, 46);

        nte$drawLine(stack, Component.translatable("tfc.tooltip.climate_peak_rainfall"), NTE_ALIGN_LEFT, 0x202080, 57);
        nte$drawLine(stack, Component.translatable(peakRainfallKey, String.format("%.0f", peakRainfall)), NTE_ALIGN_LEFT, 0x202080, 36, 57);
    }

    @Unique
    private void nte$drawLine(GuiGraphics graphics, Component text, int alignment, int y)
    {
        nte$drawLine(graphics, text, alignment, 0x404040, 0, y);
    }

    @Unique
    private void nte$drawLine(GuiGraphics graphics, Component text, int alignment, int color, int y)
    {
        nte$drawLine(graphics, text, alignment, color, 0, y);
    }

    @Unique
    private void nte$drawLine(GuiGraphics graphics, Component text, int alignment, int color, int x, int y)
    {
        int drawX = x;
        int drawColor = color == -1 ? 0x404040 : color;
        if (alignment == NTE_ALIGN_RIGHT)
        {
            drawX *= -1;
        }
        switch (alignment)
        {
            case NTE_ALIGN_LEFT -> drawX += 8;
            case NTE_ALIGN_CENTER -> drawX += (imageWidth - font.width(text)) / 2;
            case NTE_ALIGN_RIGHT -> drawX += imageWidth - font.width(text) - 8;
            default -> drawX += 8;
        }
        graphics.drawString(font, text, drawX, y, drawColor, false);
    }
}
