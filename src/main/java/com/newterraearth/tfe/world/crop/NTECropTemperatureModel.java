package com.newterraearth.tfe.world.crop;

/**
 * 作物健康模型：健康以百分比计量，内部记录的是「健康损失」——0 表示满血，{@link #STRESS_LIMIT}
 * 表示枯死。只有 TFC {@code CropBlockEntity} 作物使用这套模型，香蕉等浆果灌木不走这里。
 */
public final class NTECropTemperatureModel
{
    /** 温度超出作物上下限后仍算「能活」的缓冲带（°C）。 */
    public static final float DEATH_MARGIN = 10f;

    /** 健康损失上限；达到即枯死。 */
    public static final int STRESS_LIMIT = 100;

    /** 连续多少次气候不适才会枯死。 */
    public static final int TICKS_TO_DEATH = 25;

    /** 每次气候判定的健康损失 / 恢复量：{@link #STRESS_LIMIT} / {@link #TICKS_TO_DEATH} = 4。 */
    public static final int TICK_DAMAGE = STRESS_LIMIT / TICKS_TO_DEATH;

    /** 超出上下限 {@link #DEATH_MARGIN}°C 的极端温度每次造成的健康损失，避免一击枯死。 */
    public static final int HARD_DAMAGE = 45;

    /** 耕地仍有该作物需要的养分时，健康恢复速度的倍数。 */
    public static final int FERTILIZED_RECOVERY_MULTIPLIER = 2;

    /** 成熟作物的健康损失：固定满血，只保留 TFC 自身的过熟死亡。 */
    public static final int MATURE_STRESS = 0;

    public static int updateStress(int stress, boolean conditionsValid, boolean fertilized)
    {
        if (hasFatalStress(stress))
        {
            return STRESS_LIMIT;
        }
        if (conditionsValid)
        {
            final int recovery = TICK_DAMAGE * (fertilized ? FERTILIZED_RECOVERY_MULTIPLIER : 1);
            return Math.max(0, stress - recovery);
        }
        return Math.min(STRESS_LIMIT, stress + TICK_DAMAGE);
    }

    /** 极端温度打击：每次扣 {@link #HARD_DAMAGE}，连续数次才会枯死。 */
    public static int applyHardDamage(int stress)
    {
        return Math.min(STRESS_LIMIT, Math.max(0, stress) + HARD_DAMAGE);
    }

    public static boolean hasFatalStress(int stress)
    {
        return stress >= STRESS_LIMIT;
    }

    public static int healthPercent(int stress)
    {
        final int clampedStress = Math.max(0, Math.min(STRESS_LIMIT, stress));
        return 100 - clampedStress * 100 / STRESS_LIMIT;
    }

    public static boolean withinDeathRange(float minTemperature, float maxTemperature, float temperature)
    {
        return temperature >= minTemperature - DEATH_MARGIN && temperature <= maxTemperature + DEATH_MARGIN;
    }

    private NTECropTemperatureModel()
    {
    }
}
