package com.newterraearth.tfe.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import net.minecraft.network.chat.Component;

/**
 * Bridges the climate temperature display enum across TFC 3.2.21/3.2.22 and 3.2.28+.
 *
 * <p>The enum was renamed from {@code TemperatureDisplayStyle} to
 * {@code ClimateTemperatureDisplayStyle} in 3.2.28, and its full-range formatter changed
 * from {@code format(float, boolean)} to {@code formatRange(float)}. Referencing either
 * concrete enum type from a mixin would make the other TFC line fail during class
 * transformation, so the value intentionally crosses this boundary as {@link Object}.</p>
 */
public final class NTEClimateTemperatureDisplayCompat
{
    private static volatile Method formatter;

    private NTEClimateTemperatureDisplayCompat() {}

    public static Component format(Object style, float temperature)
    {
        Objects.requireNonNull(style, "style");
        final Method method = resolveFormatter(style);
        try
        {
            return method.getParameterCount() == 1
                ? (Component) method.invoke(style, temperature)
                : (Component) method.invoke(style, temperature, true);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            throw new IllegalStateException("Unable to format TFC climate temperature with " + style.getClass().getName(), e);
        }
    }

    private static Method resolveFormatter(Object style)
    {
        final Class<?> styleClass = style.getClass();
        Method method = formatter;
        if (method != null && method.getDeclaringClass().isAssignableFrom(styleClass))
        {
            return method;
        }

        try
        {
            method = styleClass.getMethod("formatRange", float.class);
        }
        catch (NoSuchMethodException ignored)
        {
            try
            {
                method = styleClass.getMethod("format", float.class, boolean.class);
            }
            catch (NoSuchMethodException e)
            {
                throw new IllegalStateException("Unsupported TFC climate temperature display style: " + styleClass.getName(), e);
            }
        }

        formatter = method;
        return method;
    }
}
