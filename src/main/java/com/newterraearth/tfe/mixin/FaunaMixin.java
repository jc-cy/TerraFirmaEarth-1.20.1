package com.newterraearth.tfe.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.ResourceLocation;

import net.dries007.tfc.common.entities.Fauna;
import net.dries007.tfc.util.JsonHelpers;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.world.placement.ClimatePlacement;

import com.newterraearth.tfe.common.entity.NTEFaunaAccess;
import com.newterraearth.tfe.common.entity.NTEFaunaSpawnHelpers;

@Mixin(value = Fauna.class, remap = false)
public abstract class FaunaMixin implements NTEFaunaAccess
{
    @Mutable
    @Shadow
    @Final
    private ClimatePlacement climate;

    @Unique
    private static final Map<Fauna, List<Month>> TFE_MONTHS = Collections.synchronizedMap(new WeakHashMap<>());

    @Unique
    private int tfe$minWaterVolume = NTEFaunaSpawnHelpers.DEFAULT_MIN_WATER_VOLUME;

    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceLocation;)V", at = @At("TAIL"))
    private void tfe$initDefaultMonths(ResourceLocation id, CallbackInfo ci)
    {
        TFE_MONTHS.put((Fauna) (Object) this, List.of());
    }

    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonObject;)V", at = @At("TAIL"))
    private void tfe$readMonths(ResourceLocation id, JsonObject json, CallbackInfo ci)
    {
        if (!json.has("climate"))
        {
            climate = ClimatePlacement.PLACEMENT_CODEC.decode(JsonOps.INSTANCE, json).getOrThrow(false, error -> {}).getFirst();
        }

        final List<Month> months = new ArrayList<>();
        if (json.has("months"))
        {
            for (JsonElement element : json.getAsJsonArray("months"))
            {
                months.add(Month.valueOf(element.getAsString().toUpperCase(Locale.ROOT)));
            }
        }
        TFE_MONTHS.put((Fauna) (Object) this, List.copyOf(months));

        tfe$minWaterVolume = JsonHelpers.getAsInt(json, "min_water_volume", NTEFaunaSpawnHelpers.DEFAULT_MIN_WATER_VOLUME);
    }

    @Override
    public List<Month> tfe$getMonths()
    {
        return TFE_MONTHS.getOrDefault((Fauna) (Object) this, List.of());
    }

    @Override
    public int tfe$getMinWaterVolume()
    {
        return tfe$minWaterVolume;
    }
}
