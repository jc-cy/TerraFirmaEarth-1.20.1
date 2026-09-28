package com.newterraearth.tfe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.dries007.tfc.common.blockentities.BerryBushBlockEntity;

import com.newterraearth.tfe.world.plant.NTEHarvestDormancyAccess;

/**
 * 采摘休眠期的时间戳存储。TFC 1.21 本体在 {@code TickingPlantBlockEntity} 里有等价字段（{@code lastPickedTick}），
 * 1.20 完全没有，因此由 TFE 补上，供果树树叶 / 灌木 / 香蕉共用。
 */
@Mixin(value = BerryBushBlockEntity.class, remap = false)
public abstract class BerryBushBlockEntityMixin implements NTEHarvestDormancyAccess
{
    @Unique private static final String TFE_HARVESTED_TICK = "tfeHarvestedTick";
    @Unique private static final long TFE_NEVER_HARVESTED = -1L;

    @Unique private long tfe$harvestedTick = TFE_NEVER_HARVESTED;

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void tfe$loadHarvestedTick(CompoundTag tag, CallbackInfo ci)
    {
        tfe$harvestedTick = tag.contains(TFE_HARVESTED_TICK) ? tag.getLong(TFE_HARVESTED_TICK) : TFE_NEVER_HARVESTED;
    }

    @Inject(method = {"saveAdditional", "m_183515_"}, at = @At("TAIL"), remap = false, require = 1)
    private void tfe$saveHarvestedTick(CompoundTag tag, CallbackInfo ci)
    {
        if (tfe$harvestedTick > 0L)
        {
            tag.putLong(TFE_HARVESTED_TICK, tfe$harvestedTick);
        }
    }

    @Override
    public long tfe$getHarvestedTick()
    {
        return tfe$harvestedTick;
    }

    @Override
    public void tfe$setHarvestedTick(long tick)
    {
        if (tfe$harvestedTick != tick)
        {
            tfe$harvestedTick = tick;
            final BlockEntity blockEntity = (BlockEntity) (Object) this;
            blockEntity.setChanged();
        }
    }
}
