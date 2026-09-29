package com.newterraearth.tfe.compat;

import java.lang.reflect.Method;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

public final class NTEFirmalifeGreenhouseCompat
{
    public static final String MOD_ID = "firmalife_greenhouse_patch";
    public static final String MODERN_LIFE_MOD_ID = "tfc_modern_life";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String LEGACY_GREENHOUSE_HELPER_CLASS = "com.g1739.firmalifegreenhousepatch.common.temperature.GreenhouseTemperatureHelper";
    private static final String MODERN_LIFE_GREENHOUSE_HELPER_CLASS = "com.jccy.tfcmodernlife.common.climate.GreenhouseTemperatureHelper";

    @Nullable private static volatile Method isControlledGreenhouseMethod;
    @Nullable private static volatile Method getControlledTemperatureMethod;
    private static volatile boolean resolvedMethods;
    private static volatile boolean loggedMissingMethods;
    private static volatile boolean loggedGreenhouseQueryFailure;
    private static volatile boolean loggedTemperatureQueryFailure;

    private NTEFirmalifeGreenhouseCompat()
    {
    }

    public static boolean isControlledGreenhouse(Level level, BlockPos pos)
    {
        if (!isCompatModLoaded())
        {
            return false;
        }

        resolveMethods();
        final Method method = isControlledGreenhouseMethod;
        if (method == null)
        {
            return false;
        }

        try
        {
            return Boolean.TRUE.equals(method.invoke(null, level, pos));
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            isControlledGreenhouseMethod = null;
            warnGreenhouseQueryFailure(e);
            return false;
        }
    }

    public static float getControlledTemperature(Level level, BlockPos pos, float fallbackTemperature)
    {
        if (!isCompatModLoaded())
        {
            return fallbackTemperature;
        }

        resolveMethods();
        final Method method = getControlledTemperatureMethod;
        if (method == null)
        {
            return fallbackTemperature;
        }

        try
        {
            final Object result = method.invoke(null, level, pos, fallbackTemperature);
            return result instanceof Number number ? number.floatValue() : fallbackTemperature;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e)
        {
            getControlledTemperatureMethod = null;
            warnTemperatureQueryFailure(e);
            return fallbackTemperature;
        }
    }

    private static boolean isCompatModLoaded()
    {
        return ModList.get().isLoaded(MODERN_LIFE_MOD_ID) || ModList.get().isLoaded(MOD_ID);
    }

    private static void resolveMethods()
    {
        if (resolvedMethods)
        {
            return;
        }

        synchronized (NTEFirmalifeGreenhouseCompat.class)
        {
            if (resolvedMethods)
            {
                return;
            }

            resolvedMethods = true;
            if (!resolveModernLifeMethods() && !resolveLegacyMethods() && !loggedMissingMethods)
            {
                loggedMissingMethods = true;
                LOGGER.warn("Failed to initialize Firmalife greenhouse compatibility hooks");
            }
        }
    }

    private static boolean resolveModernLifeMethods()
    {
        try
        {
            final Class<?> helperClass = Class.forName(MODERN_LIFE_GREENHOUSE_HELPER_CLASS);
            final Method controlledMethod = helperClass.getMethod("getControlledTemperature", Level.class, BlockPos.class, float.class);
            final Method greenhouseMethod = helperClass.getMethod("isControlledGreenhouse", Level.class, BlockPos.class);
            controlledMethod.setAccessible(true);
            greenhouseMethod.setAccessible(true);
            getControlledTemperatureMethod = controlledMethod;
            isControlledGreenhouseMethod = greenhouseMethod;
            return true;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
        {
            return false;
        }
    }

    private static boolean resolveLegacyMethods()
    {
        try
        {
            final Class<?> helperClass = Class.forName(LEGACY_GREENHOUSE_HELPER_CLASS);
            final Method greenhouseMethod = helperClass.getMethod("isControlledGreenhouse", Level.class, BlockPos.class);
            greenhouseMethod.setAccessible(true);
            isControlledGreenhouseMethod = greenhouseMethod;

            // 一代 `温室地窖重置`（firmalife_greenhouse_patch）同样提供
            // getControlledTemperature(Level, BlockPos, float)。早期这里只解析了识别方法，
            // 导致一代环境下树叶 / 灌木 / 香蕉拿到的是自然环境温度（温室控温看起来失效）。
            try
            {
                final Method controlledMethod = helperClass.getMethod("getControlledTemperature", Level.class, BlockPos.class, float.class);
                controlledMethod.setAccessible(true);
                getControlledTemperatureMethod = controlledMethod;
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
            {
                // 更老的一代版本没有该方法时保持原行为（仅温室识别）。
            }

            return true;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
        {
            return false;
        }
    }

    private static void warnGreenhouseQueryFailure(Throwable e)
    {
        if (!loggedGreenhouseQueryFailure)
        {
            loggedGreenhouseQueryFailure = true;
            LOGGER.warn("Failed to query Firmalife greenhouse control state; disabling this compatibility hook", e);
        }
    }

    private static void warnTemperatureQueryFailure(Throwable e)
    {
        if (!loggedTemperatureQueryFailure)
        {
            loggedTemperatureQueryFailure = true;
            LOGGER.warn("Failed to query Firmalife greenhouse controlled temperature; falling back to TFC temperature", e);
        }
    }
}
