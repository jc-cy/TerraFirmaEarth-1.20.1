package com.newterraearth.tfe.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NTEClimateTemperatureDisplayCompatTest
{
    @Test
    void formatsWithModernRangeMethod()
    {
        final Component result = NTEClimateTemperatureDisplayCompat.format(new ModernStyle(), 12.5f);

        assertEquals("modern:12.5", result.getString());
    }

    @Test
    void formatsWithLegacyFullRangeMethod()
    {
        final Component result = NTEClimateTemperatureDisplayCompat.format(new LegacyStyle(), -4.0f);

        assertEquals("legacy:-4.0:true", result.getString());
    }

    @Test
    void rejectsUnknownStyle()
    {
        final IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> NTEClimateTemperatureDisplayCompat.format(new UnsupportedStyle(), 0f)
        );

        assertEquals("Unsupported TFC climate temperature display style: " + UnsupportedStyle.class.getName(), error.getMessage());
    }

    public static final class ModernStyle
    {
        public Component formatRange(float temperature)
        {
            return Component.literal("modern:" + temperature);
        }
    }

    public static final class LegacyStyle
    {
        public Component format(float temperature, boolean fullRange)
        {
            return Component.literal("legacy:" + temperature + ":" + fullRange);
        }
    }

    public static final class UnsupportedStyle {}
}
