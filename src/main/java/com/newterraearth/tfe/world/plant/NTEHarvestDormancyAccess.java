package com.newterraearth.tfe.world.plant;

/**
 * 采摘休眠期的存储访问口。由 {@code BerryBushBlockEntityMixin} 混入 TFC 的浆果方块实体（果树树叶、灌木、香蕉共用）
 * 后实现；TFC 1.20 本体没有这套数据，属于 TFE 为 TFC 补的差异。
 */
public interface NTEHarvestDormancyAccess
{
    long tfe$getHarvestedTick();

    void tfe$setHarvestedTick(long tick);
}
