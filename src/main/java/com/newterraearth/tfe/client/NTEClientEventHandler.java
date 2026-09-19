package com.newterraearth.tfe.client;

import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LeashKnotRenderer;
import net.minecraft.client.model.CamelModel;
import com.newterraearth.tfe.client.model.entity.NTEArmadilloModel;
import com.newterraearth.tfe.client.model.entity.NTEArcticCharModel;
import com.newterraearth.tfe.client.model.entity.NTEBurbotModel;
import com.newterraearth.tfe.client.model.entity.NTEMuksunModel;
import com.newterraearth.tfe.client.model.entity.NTENorthernPikeModel;
import com.newterraearth.tfe.client.model.entity.NTEPacuModel;
import com.newterraearth.tfe.client.model.entity.NTEPeacockBassModel;
import com.newterraearth.tfe.client.model.entity.NTERedPiranhaModel;
import com.newterraearth.tfe.client.model.entity.NTESpottedGudgeonModel;
import com.newterraearth.tfe.client.model.entity.NTETilapiaModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.client.TFCColors;
import net.dries007.tfc.client.render.entity.SimpleMobRenderer;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.common.blocks.rock.Rock;
import net.dries007.tfc.common.blocks.soil.ConnectedGrassBlock;
import net.dries007.tfc.common.entities.prey.Pest;

import com.newterraearth.tfe.NewTerraEarthMod;
import com.newterraearth.tfe.client.model.NTEPlantBlockModel;
import com.newterraearth.tfe.client.model.entity.NTEBisonModel;
import com.newterraearth.tfe.client.model.entity.NTEBactrianCamelModel;
import com.newterraearth.tfe.client.render.entity.NTEBactrianCamelRenderer;
import com.newterraearth.tfe.common.NTEDevices;
import com.newterraearth.tfe.common.entity.NTEFish;
import com.newterraearth.tfe.client.model.entity.NTEJerboaModel;
import com.newterraearth.tfe.client.model.entity.NTELeopardSealModel;
import com.newterraearth.tfe.client.model.entity.NTELemmingModel;
import com.newterraearth.tfe.client.model.entity.NTEMongooseModel;
import com.newterraearth.tfe.common.NTEBlocks;
import com.newterraearth.tfe.common.NTEFluid;
import com.newterraearth.tfe.common.NTEFluids;
import com.newterraearth.tfe.common.NTERock;
import com.newterraearth.tfe.common.NTERockBlocks;
import com.newterraearth.tfe.common.entity.NTEEntities;
import com.newterraearth.tfe.world.crop.NTECrop;
import com.newterraearth.tfe.world.plant.NTEPlant;
import com.newterraearth.tfe.world.soil.NTESoil;
import com.newterraearth.tfe.world.soil.NTESoilBlockType;

public final class NTEClientEventHandler
{
    private static final String[] TUFF_PAN_METALS = {"native_copper", "native_silver", "native_gold", "cassiterite"};

    private NTEClientEventHandler()
    {
    }

    public static void init(IEventBus bus)
    {
        NTEClientRainVarianceCache.init();
        NTEProspectingHud.init();
        bus.addListener(NTEClientEventHandler::clientSetup);
        bus.addListener(NTEClientEventHandler::registerSpecialModels);
        bus.addListener(NTEClientEventHandler::registerModelLoaders);
        bus.addListener(NTEClientEventHandler::registerColorHandlerBlocks);
        bus.addListener(NTEClientEventHandler::registerColorHandlerItems);
        bus.addListener(NTEClientEventHandler::registerEntityRenderers);
        bus.addListener(NTEClientEventHandler::registerLayerDefinitions);
    }

    private static void clientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> {
            NTEJourneyMapCompat.install();
            final RenderType cutoutMipped = RenderType.cutoutMipped();
            final RenderType cutout = RenderType.cutout();
            final RenderType translucent = RenderType.translucent();
            forEachTintedSoilBlock(block -> ItemBlockRenderTypes.setRenderLayer(block, cutoutMipped));
            forEachRockCutoutBlock(block -> ItemBlockRenderTypes.setRenderLayer(block, cutout));
            ItemBlockRenderTypes.setRenderLayer(NTEDevices.STOVE.get(), cutout);
            ItemBlockRenderTypes.setRenderLayer(NTEDevices.STOVE_POT.get(), cutout);
            ItemBlockRenderTypes.setRenderLayer(NTEBlocks.ROPE.get(), cutout);
            ItemBlockRenderTypes.setRenderLayer(NTEBlocks.HANGING_ROPE.get(), cutout);
            ItemBlockRenderTypes.setRenderLayer(NTEBlocks.STEEL_ROPE_ANCHOR.get(), cutout);
            NTEBlocks.METAL_ROPE_ANCHORS.values().forEach(anchor -> ItemBlockRenderTypes.setRenderLayer(anchor.get(), cutout));
            NTERockBlocks.TFC_ROPE_ANCHORS.values().forEach(anchor -> ItemBlockRenderTypes.setRenderLayer(anchor.get(), cutout));
            ItemBlockRenderTypes.setRenderLayer(NTERockBlocks.TUFF_ROPE_ANCHOR.get(), cutout);
            for (NTEFluid fluid : NTEFluid.values())
            {
                ItemBlockRenderTypes.setRenderLayer(NTEFluids.getBlock(fluid).get(), translucent);
            }
            for (NTEPlant plant : NTEPlant.values())
            {
                ItemBlockRenderTypes.setRenderLayer(NTEBlocks.getPlant(plant).get(), cutout);
            }
            for (NTECrop crop : NTECrop.values())
            {
                ItemBlockRenderTypes.setRenderLayer(NTEBlocks.getCrop(crop).get(), cutout);
                ItemBlockRenderTypes.setRenderLayer(NTEBlocks.getDeadCrop(crop).get(), cutout);
                ItemBlockRenderTypes.setRenderLayer(NTEBlocks.getWildCrop(crop).get(), cutout);
            }
        });
    }

    private static void registerModelLoaders(ModelEvent.RegisterGeometryLoaders event)
    {
        event.register("plant", NTEPlantBlockModel.Loader.INSTANCE);
    }

    private static void registerSpecialModels(ModelEvent.RegisterAdditional event)
    {
        for (String metal : TUFF_PAN_METALS)
        {
            event.register(model("item/pan/" + metal + "/tuff_half"));
            event.register(model("item/pan/" + metal + "/tuff_full"));
        }
    }

    private static ResourceLocation model(String path)
    {
        return new ResourceLocation(NewTerraEarthMod.MOD_ID, path);
    }

    private static void registerColorHandlerBlocks(RegisterColorHandlersEvent.Block event)
    {
        final BlockColor grassColor = (state, level, pos, tintIndex) -> TFCColors.getGrassColor(pos, tintIndex);
        final BlockColor tallGrassColor = (state, level, pos, tintIndex) -> TFCColors.getTallGrassColor(pos, tintIndex);
        final BlockColor foliageColor = (state, level, pos, tintIndex) -> TFCColors.getFoliageColor(pos, tintIndex);
        final BlockColor waterColor = (state, level, pos, tintIndex) -> TFCColors.getWaterColor(pos);
        final BlockColor grassBlockColor = (state, level, pos, tintIndex) ->
            state.getValue(ConnectedGrassBlock.SNOWY) || tintIndex != 1 ? -1 : grassColor.getColor(state, level, pos, tintIndex);

        forEachTintedSoilBlock(block -> event.register(grassBlockColor, block));

        for (NTEPlant plant : NTEPlant.values())
        {
            if (plant.isBlockTinted())
            {
                event.register(
                    plant.usesWaterTint() ? waterColor :
                        plant.isTallGrass() ? tallGrassColor :
                            plant.isFoliage() ? foliageColor :
                                grassColor,
                    NTEBlocks.getPlant(plant).get()
                );
            }
        }

        for (NTECrop crop : NTECrop.values())
        {
            event.register(grassColor, NTEBlocks.getWildCrop(crop).get());
        }
    }

    private static void registerColorHandlerItems(RegisterColorHandlersEvent.Item event)
    {
        final ItemColor grassColor = (stack, tintIndex) -> TFCColors.getGrassColor(null, tintIndex);
        final ItemColor foliageColor = (stack, tintIndex) -> TFCColors.getFoliageColor(null, tintIndex);

        forEachTintedSoilBlock(block -> event.register(grassColor, block.asItem()));

        for (NTEPlant plant : NTEPlant.values())
        {
            if (plant.isItemTinted())
            {
                event.register(plant.isFoliage() ? foliageColor : grassColor, NTEBlocks.getPlant(plant).get().asItem());
            }
        }

        for (NTECrop crop : NTECrop.values())
        {
            event.register(grassColor, NTEBlocks.getWildCrop(crop).get().asItem());
        }
    }

    private static void forEachTintedSoilBlock(Consumer<Block> consumer)
    {
        for (NTESoil soil : NTESoil.values())
        {
            consumer.accept(NTEBlocks.getBlock(soil, NTESoilBlockType.GRASS).get());
            consumer.accept(NTEBlocks.getBlock(soil, NTESoilBlockType.CLAY_GRASS).get());
            consumer.accept(NTEBlocks.getBlock(soil, NTESoilBlockType.DUFF).get());
            consumer.accept(NTEBlocks.getBlock(soil, NTESoilBlockType.CLAY_DUFF).get());
        }
    }

    private static void forEachRockCutoutBlock(Consumer<Block> consumer)
    {
        for (NTERock rock : NTERock.values())
        {
            consumer.accept(NTERockBlocks.ROCK_BLOCKS.get(rock).get(Rock.BlockType.SPIKE).get());
            consumer.accept(NTERockBlocks.ROCK_BLOCKS.get(rock).get(Rock.BlockType.AQUEDUCT).get());
            NTERockBlocks.ORES.get(rock).values().forEach(reg -> consumer.accept(reg.get()));
            NTERockBlocks.GRADED_ORES.get(rock).values().forEach(map -> map.values().forEach(reg -> consumer.accept(reg.get())));
            NTERockBlocks.ORE_DEPOSITS.get(rock).values().forEach(reg -> consumer.accept(reg.get()));
        }
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(NTEEntities.LEOPARD_SEAL.get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTELeopardSealModel::new, "seal").build());
        event.registerEntityRenderer(NTEEntities.BISON.get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTEBisonModel::new, "bison").build());
        event.registerEntityRenderer(NTEEntities.LEMMING.get(), ctx -> pestRenderer(ctx, NTELemmingModel::new, "lemming"));
        event.registerEntityRenderer(NTEEntities.MONGOOSE.get(), ctx -> pestRenderer(ctx, NTEMongooseModel::new, "mongoose"));
        event.registerEntityRenderer(NTEEntities.JERBOA.get(), ctx -> pestRenderer(ctx, NTEJerboaModel::new, "jerboa"));
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.ARCTIC_CHAR).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTEArcticCharModel::new, "arctic_char").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.BURBOT).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTEBurbotModel::new, "burbot").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.MUKSUN).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTEMuksunModel::new, "muksun").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.NORTHERN_PIKE).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTENorthernPikeModel::new, "northern_pike").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.PACU).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTEPacuModel::new, "pacu").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.PEACOCK_BASS).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTEPeacockBassModel::new, "peacock_bass").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.RED_PIRANHA).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTERedPiranhaModel::new, "red_piranha").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.SPOTTED_GUDGEON).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTESpottedGudgeonModel::new, "spotted_gudgeon").flops().build());
        event.registerEntityRenderer(NTEEntities.NEW_FRESHWATER_FISH.get(NTEFish.TILAPIA).get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTETilapiaModel::new, "tilapia").flops().build());
        event.registerEntityRenderer(NTEEntities.BACTRIAN_CAMEL.get(), ctx -> new NTEBactrianCamelRenderer(
            ctx,
            new NTEBactrianCamelModel(RenderHelpers.bakeSimple(ctx, "bactrian_camel")),
            0.6F
        ));
        event.registerEntityRenderer(NTEEntities.DROMEDARY_CAMEL.get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, CamelModel::new, "dromedary_camel")
            .texture(camel -> new ResourceLocation("minecraft", "textures/entity/camel/camel.png"))
            .hasBabyTexture().shadow(0.8f).scale(1.0f).build());
        event.registerEntityRenderer(NTEEntities.ARMADILLO.get(), ctx -> new SimpleMobRenderer.Builder<>(ctx, NTEArmadilloModel::new, "armadillo")
            .texture(entity -> Helpers.animalTexture("armadillo"))
            .shadow(0.35f).scale(0.9f).build());
        event.registerEntityRenderer(NTEEntities.ROPE_KNOT.get(), LeashKnotRenderer::new);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("seal"), NTELeopardSealModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("bison"), NTEBisonModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("lemming"), NTELemmingModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("mongoose"), NTEMongooseModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("jerboa"), NTEJerboaModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("arctic_char"), NTEArcticCharModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("burbot"), NTEBurbotModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("muksun"), NTEMuksunModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("northern_pike"), NTENorthernPikeModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("pacu"), NTEPacuModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("peacock_bass"), NTEPeacockBassModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("red_piranha"), NTERedPiranhaModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("spotted_gudgeon"), NTESpottedGudgeonModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("tilapia"), NTETilapiaModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("bactrian_camel"), NTEBactrianCamelModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("dromedary_camel"), CamelModel::createBodyLayer);
        event.registerLayerDefinition(RenderHelpers.modelIdentifier("armadillo"), NTEArmadilloModel::createBodyLayer);
    }

    private static <M extends net.minecraft.client.model.EntityModel<Pest>> SimpleMobRenderer<Pest, M> pestRenderer(
        EntityRendererProvider.Context ctx,
        java.util.function.Function<net.minecraft.client.model.geom.ModelPart, M> model,
        String texture
    )
    {
        return new SimpleMobRenderer.Builder<>(ctx, model, texture)
            .shadow(0.2f)
            .build();
    }
}
