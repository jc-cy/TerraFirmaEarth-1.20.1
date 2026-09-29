package com.newterraearth.tfe;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;

import com.newterraearth.tfe.client.NTEClientEventHandler;
import com.newterraearth.tfe.api.climate.NTEDailyTemperatureLifecycle;
import com.newterraearth.tfe.common.NTEBlocks;
import com.newterraearth.tfe.common.NTEFluids;
import com.newterraearth.tfe.common.NTERockBlocks;
import com.newterraearth.tfe.common.blockentities.NTEBlockEntities;
import com.newterraearth.tfe.config.NTECommonConfig;
import com.newterraearth.tfe.common.entity.NTEEntities;
import com.newterraearth.tfe.common.entity.NTEEntitySounds;
import com.newterraearth.tfe.common.entity.NTEFaunas;
import com.newterraearth.tfe.common.entity.NTEItems;
import com.newterraearth.tfe.common.entity.NTEVanillaFaunas;
import com.newterraearth.tfe.event.NTEBuiltinPackEvents;
import com.newterraearth.tfe.event.NTECactusEvents;
import com.newterraearth.tfe.event.NTEFarmlandEvents;
import com.newterraearth.tfe.event.NTEDeviceEvents;
import com.newterraearth.tfe.debug.NTERuntimeTrace;
import com.newterraearth.tfe.network.NTEPacketHandler;
import com.newterraearth.tfe.world.feature.NTEFeatures;
import com.newterraearth.tfe.world.NTEClimateDisplaySync;
import com.newterraearth.tfe.world.placement.NTEPlacements;

@Mod(NewTerraEarthMod.MOD_ID)
public final class NewTerraEarthMod
{
    public static final String MOD_ID = "tfe";

    public NewTerraEarthMod()
    {
        final var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, NTECommonConfig.SPEC);
        modBus.addListener(this::setup);
        modBus.addListener(this::loadComplete);
        NTEBuiltinPackEvents.init(modBus);
        NTERuntimeTrace.init();
        NTECactusEvents.init();
        NTEFarmlandEvents.init();
        NTEDeviceEvents.init();
        NTEDeviceEvents.initModBus(modBus);
        modBus.addListener(NTEEntities::onEntityAttributeCreation);
        modBus.addListener(NTEVanillaFaunas::registerSpawnPlacements);
        modBus.addListener(NTEFaunas::registerSpawnPlacements);
        NTEPacketHandler.init();
        NTEClimateDisplaySync.init();
        NTERockBlocks.init();
        NTEFluids.register(modBus);
        NTEBlocks.register(modBus);
        NTEBlockEntities.register(modBus);
        NTEEntitySounds.register(modBus);
        NTEEntities.register(modBus);
        NTEItems.register(modBus);
        NTEFeatures.register(modBus);
        NTEPlacements.register(modBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NTEClientEventHandler.init(modBus));
    }

    private void setup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> {
            NTERockBlocks.registerRockSettings();
            NTERockBlocks.registerProspectingRepresentatives();
            NTEFluids.registerCauldronInteractions();
        });
    }

    private void loadComplete(FMLLoadCompleteEvent event)
    {
        NTEDailyTemperatureLifecycle.freezeRegistration();
    }
}
