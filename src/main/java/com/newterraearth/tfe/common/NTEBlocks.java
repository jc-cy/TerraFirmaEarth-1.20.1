package com.newterraearth.tfe.common;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MudBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.blocks.crop.DeadCropBlock;
import net.dries007.tfc.common.blocks.crop.WildCropBlock;
import net.dries007.tfc.common.blocks.soil.ConnectedGrassBlock;
import net.dries007.tfc.common.blocks.soil.DirtBlock;
import net.dries007.tfc.common.blocks.soil.PathBlock;
import net.dries007.tfc.common.blocks.soil.SoilBlockType;
import net.dries007.tfc.common.blocks.soil.TFCRootedDirtBlock;
import net.dries007.tfc.common.items.JarItem;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.Metal;

import com.newterraearth.tfe.NewTerraEarthMod;
import com.newterraearth.tfe.common.block.ConnectedDuffBlock;
import com.newterraearth.tfe.common.block.GoldenBambooBlock;
import com.newterraearth.tfe.common.block.NTEDefaultCropBlock;
import com.newterraearth.tfe.common.block.NTEDryingBricksBlock;
import com.newterraearth.tfe.common.block.NTEFarmlandBlock;
import com.newterraearth.tfe.common.item.FuelBlockItem;
import com.newterraearth.tfe.common.item.NTEProvidedBlockItem;
import com.newterraearth.tfe.common.item.NTEProvidedItem;
import com.newterraearth.tfe.common.block.rope.NTEGroundedRopeBlock;
import com.newterraearth.tfe.common.block.rope.NTEHangingRopeBlock;
import com.newterraearth.tfe.common.block.rope.NTEMetalRopeAnchorBlock;
import com.newterraearth.tfe.common.blockentities.NTEBlockEntities;
import com.newterraearth.tfe.common.blockentities.NTECropBlockEntity;
import com.newterraearth.tfe.world.crop.NTECrop;
import com.newterraearth.tfe.world.plant.NTEPlant;
import com.newterraearth.tfe.world.soil.NTESoil;
import com.newterraearth.tfe.world.soil.NTESoilBlockType;

public final class NTEBlocks
{
    private static final String TFC_NAMESPACE = "tfc";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, NewTerraEarthMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NewTerraEarthMod.MOD_ID);
    public static final DeferredRegister<Block> TFC_BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TFC_NAMESPACE);
    public static final DeferredRegister<Item> TFC_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TFC_NAMESPACE);

    private static final Map<NTESoilBlockType, Map<NTESoil, RegistryObject<Block>>> SOIL_BLOCKS = new EnumMap<>(NTESoilBlockType.class);
    private static final Map<NTEPlant, RegistryObject<Block>> PLANTS = new EnumMap<>(NTEPlant.class);
    private static final Map<NTECrop, RegistryObject<Block>> CROPS = new EnumMap<>(NTECrop.class);
    private static final Map<NTECrop, RegistryObject<Block>> DEAD_CROPS = new EnumMap<>(NTECrop.class);
    private static final Map<NTECrop, RegistryObject<Block>> WILD_CROPS = new EnumMap<>(NTECrop.class);
    private static final Map<NTECrop, RegistryObject<Item>> CROP_SEEDS = new EnumMap<>(NTECrop.class);
    private static final Map<NTECrop, RegistryObject<Item>> CROP_PRODUCE = new EnumMap<>(NTECrop.class);
    private static final Map<NTECrop, RegistryObject<Item>> COOKED_CROP_PRODUCE = new EnumMap<>(NTECrop.class);
    private static final Map<NTESoil, RegistryObject<Item>> DRIED_MUD_BRICKS = new EnumMap<>(NTESoil.class);
    public static final RegistryObject<Item> CANOLA_OILSEED = ITEMS.register("canola_oilseed", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CANOLA_PASTE = ITEMS.register("canola_paste", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PEANUT_JAM = ITEMS.register("peanut_jam", () -> new Item(jamFoodProperties()));
    public static final RegistryObject<Item> PEANUT_JAR = ITEMS.register("jar/peanut", () -> new JarItem(new Item.Properties(), new ResourceLocation(NewTerraEarthMod.MOD_ID, "block/jar/peanut"), false));
    public static final RegistryObject<Item> PEANUT_JAR_UNSEALED = ITEMS.register("jar/peanut_unsealed", () -> new JarItem(new Item.Properties(), new ResourceLocation(NewTerraEarthMod.MOD_ID, "block/jar/peanut_unsealed"), true));
    public static final RegistryObject<Item> CACTUS_WOOD = TFC_ITEMS.register("cactus_wood", () -> new NTEProvidedItem(new Item.Properties()));
    public static final RegistryObject<Item> DRIED_CACTUS_WOOD = TFC_ITEMS.register("dried_cactus_wood", () -> new NTEProvidedItem(new Item.Properties()));

    public static final RegistryObject<Block> HARDENED_CLAY = register(
        TFC_BLOCKS,
        TFC_ITEMS,
        "hardened_clay",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.TERRACOTTA_ORANGE)
            .strength(7.0F)
            .sound(SoundType.PACKED_MUD)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()),
        block -> new NTEProvidedBlockItem(block, new Item.Properties())
    );
    public static final RegistryObject<Block> HALITE = register(
        TFC_BLOCKS,
        TFC_ITEMS,
        "halite",
        () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(6.0F)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops()),
        block -> new NTEProvidedBlockItem(block, new Item.Properties())
    );
    public static final RegistryObject<Block> GOLDEN_BAMBOO_BLOCK = register(
        TFC_BLOCKS,
        TFC_ITEMS,
        "golden_bamboo_block",
        () -> new GoldenBambooBlock(ExtendedProperties.of(Blocks.BAMBOO_BLOCK)),
        block -> new FuelBlockItem(block, new Item.Properties(), Items.BAMBOO_BLOCK)
    );

    /** 4.2.x climbing rope blocks. Rope segments intentionally have no block item; the rope item places them. */
    public static final RegistryObject<Block> ROPE = TFC_BLOCKS.register("rope", () ->
        new NTEGroundedRopeBlock(ExtendedProperties.of().mapColor(DyeColor.BROWN.getMapColor()).noOcclusion().strength(1f).sound(SoundType.WOOL)));
    public static final RegistryObject<Block> HANGING_ROPE = TFC_BLOCKS.register("hanging_rope", () ->
        new NTEHangingRopeBlock(ExtendedProperties.of().mapColor(DyeColor.BROWN.getMapColor()).noOcclusion().strength(1f).sound(SoundType.WOOL)));
    public static final RegistryObject<Block> STEEL_ROPE_ANCHOR = register(
        TFC_BLOCKS,
        TFC_ITEMS,
        "steel_rope_anchor",
        () -> new NTEMetalRopeAnchorBlock(ExtendedProperties.of(MapColor.METAL).noOcclusion().randomTicks().strength(4f, 10f).requiresCorrectToolForDrops().sound(SoundType.METAL)),
        block -> new NTEProvidedBlockItem(block, new Item.Properties())
    );
    /**
     * TFE 扩展的可制作金属绳锚：覆盖所有拥有金属棒的 TFC 金属，钢锚沿用上游已经存在的 {@code tfc:steel_rope_anchor}，
     * 因此这里排除钢，避免重复注册同名方块。
     */
    public static final Map<Metal.Default, RegistryObject<Block>> METAL_ROPE_ANCHORS = Helpers.mapOfKeys(Metal.Default.class,
        metal -> metal.hasParts() && metal != Metal.Default.STEEL,
        metal -> register(
            BLOCKS,
            ITEMS,
            metal.getSerializedName() + "_rope_anchor",
            () -> new NTEMetalRopeAnchorBlock(ExtendedProperties.of(MapColor.METAL).noOcclusion().randomTicks().strength(4f, 10f).requiresCorrectToolForDrops().sound(SoundType.METAL)),
            block -> new NTEProvidedBlockItem(block, new Item.Properties())
        )
    );

    private static final Set<NTEPlant> TFC_NAMESPACE_PLANTS = EnumSet.of(
        NTEPlant.ANEMONE_GREEN,
        NTEPlant.ANEMONE_LARGE_ORANGE,
        NTEPlant.ANEMONE_LARGE_PURPLE,
        NTEPlant.ANEMONE_PURPLE,
        NTEPlant.BARNACLES,
        NTEPlant.MUSSELS,
        NTEPlant.STARFISH
    );

    static
    {
        for (NTESoilBlockType type : NTESoilBlockType.values())
        {
            SOIL_BLOCKS.put(type, new EnumMap<>(NTESoil.class));
        }

        for (NTESoil soil : NTESoil.values())
        {
            if (soil.isAddonFamily())
            {
                DRIED_MUD_BRICKS.put(soil, ITEMS.register("mud_brick/" + soil.blockPath(), NTEBlocks::driedMudBrickItem));
            }

            for (NTESoilBlockType type : NTESoilBlockType.values())
            {
                if (type.requiresAddonRegistration(soil))
                {
                    SOIL_BLOCKS.get(type).put(soil, register(BLOCKS, ITEMS, type.id(soil), () -> createSoilBlock(type, soil), block -> new BlockItem(block, new Item.Properties())));
                }
            }
        }

        for (NTEPlant plant : NTEPlant.values())
        {
            final boolean usesTFCNamespace = TFC_NAMESPACE_PLANTS.contains(plant);
            final DeferredRegister<Block> blockRegister = usesTFCNamespace ? TFC_BLOCKS : BLOCKS;
            final DeferredRegister<Item> itemRegister = usesTFCNamespace ? TFC_ITEMS : ITEMS;
            final Function<Block, ? extends BlockItem> blockItemFactory = usesTFCNamespace
                ? block -> new NTEProvidedBlockItem(block, new Item.Properties())
                : plant.createBlockItem(new Item.Properties());
            PLANTS.put(plant, register(blockRegister, itemRegister, plant.id(), plant::create, blockItemFactory));
        }

        for (NTECrop crop : NTECrop.values())
        {
            CROPS.put(crop, register(BLOCKS, null, crop.cropPath(), () -> createCropBlock(crop), null));
            DEAD_CROPS.put(crop, register(BLOCKS, null, crop.deadCropPath(), () -> createDeadCropBlock(crop), null));
            WILD_CROPS.put(crop, register(BLOCKS, ITEMS, crop.wildCropPath(), NTEBlocks::createWildCropBlock, block -> new BlockItem(block, new Item.Properties())));

            CROP_SEEDS.put(crop, ITEMS.register(crop.seedPath(), () -> new ItemNameBlockItem(CROPS.get(crop).get(), new Item.Properties())));
            CROP_PRODUCE.put(crop, ITEMS.register(crop.producePath(), () -> createProduceItem(crop)));

            if (crop.hasCookedItem())
            {
                COOKED_CROP_PRODUCE.put(crop, ITEMS.register(crop.cookedProducePath(), () -> createCookedProduceItem(crop)));
            }
        }
    }

    private NTEBlocks()
    {
    }

    public static void register(IEventBus bus)
    {
        NTEDevices.touch();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TFC_BLOCKS.register(bus);
        TFC_ITEMS.register(bus);
    }

    public static Supplier<? extends Block> getBlock(NTESoil soil, NTESoilBlockType type)
    {
        if (type.requiresAddonRegistration(soil))
        {
            return SOIL_BLOCKS.get(type).get(soil);
        }

        return switch (type)
        {
            case DIRT -> soil.nativeVariant().getBlock(SoilBlockType.DIRT);
            case GRASS -> soil.nativeVariant().getBlock(SoilBlockType.GRASS);
            case DUFF -> throw new IllegalStateException("All duff variants require addon registration: " + soil.serializedName());
            case GRASS_PATH -> soil.nativeVariant().getBlock(SoilBlockType.GRASS_PATH);
            case CLAY -> soil.nativeVariant().getBlock(SoilBlockType.CLAY);
            case CLAY_GRASS -> soil.nativeVariant().getBlock(SoilBlockType.CLAY_GRASS);
            case CLAY_DUFF -> throw new IllegalStateException("All clay duff variants require addon registration: " + soil.serializedName());
            case ROOTED_DIRT -> soil.nativeVariant().getBlock(SoilBlockType.ROOTED_DIRT);
            case COARSE_DIRT -> throw new IllegalStateException("Mapped soils still need addon coarse dirt: " + soil.serializedName());
            case FARMLAND -> soil.nativeVariant().getBlock(SoilBlockType.FARMLAND);
            case MUD -> soil.nativeVariant().getBlock(SoilBlockType.MUD);
            case MUD_BRICKS -> soil.nativeVariant().getBlock(SoilBlockType.MUD_BRICKS);
            case DRYING_BRICKS -> soil.nativeVariant().getBlock(SoilBlockType.DRYING_BRICKS);
            case MUDDY_ROOTS -> soil.nativeVariant().getBlock(SoilBlockType.MUDDY_ROOTS);
        };
    }

    public static Supplier<? extends Block> getPlant(NTEPlant plant)
    {
        return PLANTS.get(plant);
    }

    public static Supplier<? extends Block> getCrop(NTECrop crop)
    {
        return CROPS.get(crop);
    }

    public static Supplier<? extends Block> getDeadCrop(NTECrop crop)
    {
        return DEAD_CROPS.get(crop);
    }

    public static Supplier<? extends Block> getWildCrop(NTECrop crop)
    {
        return WILD_CROPS.get(crop);
    }

    public static Supplier<? extends Item> getCropSeed(NTECrop crop)
    {
        return CROP_SEEDS.get(crop);
    }

    public static Supplier<? extends Item> getCropProduce(NTECrop crop)
    {
        return CROP_PRODUCE.get(crop);
    }

    public static Supplier<? extends Item> getCookedCropProduce(NTECrop crop)
    {
        if (!crop.hasCookedItem())
        {
            throw new IllegalStateException("Crop does not have a cooked produce item: " + crop.serializedName());
        }
        return COOKED_CROP_PRODUCE.get(crop);
    }

    public static Supplier<? extends Item> getDriedMudBrickItem(NTESoil soil)
    {
        if (soil.isAddonFamily())
        {
            return DRIED_MUD_BRICKS.get(soil);
        }

        return switch (soil.nativeVariant())
        {
            case SILT -> TFCItems.SILT_MUD_BRICK;
            case LOAM -> TFCItems.LOAM_MUD_BRICK;
            case SANDY_LOAM -> TFCItems.SANDY_LOAM_MUD_BRICK;
            case SILTY_LOAM -> TFCItems.SILTY_LOAM_MUD_BRICK;
        };
    }

    public static Stream<? extends Supplier<? extends Block>> farmlandBlocks()
    {
        return SOIL_BLOCKS.get(NTESoilBlockType.FARMLAND).values().stream();
    }

    public static Stream<? extends Supplier<? extends Block>> cropBlocks()
    {
        return CROPS.values().stream();
    }

    public static Stream<? extends Supplier<? extends Block>> dryingBrickBlocks()
    {
        return SOIL_BLOCKS.get(NTESoilBlockType.DRYING_BRICKS).values().stream();
    }

    private static RegistryObject<Block> register(DeferredRegister<Block> blockRegister, @Nullable DeferredRegister<Item> itemRegister, String name, Supplier<? extends Block> blockFactory, @Nullable Function<Block, ? extends BlockItem> itemFactory)
    {
        final RegistryObject<Block> block = blockRegister.register(name, blockFactory);
        if (itemRegister != null && itemFactory != null)
        {
            itemRegister.register(name, () -> itemFactory.apply(block.get()));
        }
        return block;
    }

    private static Block createSoilBlock(NTESoilBlockType type, NTESoil soil)
    {
        return switch (type)
        {
            case DIRT -> new DirtBlock(
                Block.Properties.of().mapColor(MapColor.DIRT).strength(1.4f).sound(SoundType.GRAVEL),
                getBlock(soil, NTESoilBlockType.GRASS),
                getBlock(soil, NTESoilBlockType.GRASS_PATH),
                getBlock(soil, NTESoilBlockType.FARMLAND),
                getBlock(soil, NTESoilBlockType.ROOTED_DIRT),
                getBlock(soil, NTESoilBlockType.MUD)
            );
            case GRASS -> new ConnectedGrassBlock(
                Block.Properties.of().mapColor(MapColor.GRASS).randomTicks().strength(1.8f).sound(SoundType.GRASS),
                getBlock(soil, NTESoilBlockType.DIRT),
                getBlock(soil, NTESoilBlockType.GRASS_PATH),
                getBlock(soil, NTESoilBlockType.FARMLAND)
            );
            case DUFF -> new ConnectedDuffBlock(
                Block.Properties.of().mapColor(MapColor.DIRT).randomTicks().strength(1.6f).sound(SoundType.GRASS),
                getBlock(soil, NTESoilBlockType.DIRT),
                getBlock(soil, NTESoilBlockType.GRASS_PATH),
                getBlock(soil, NTESoilBlockType.FARMLAND)
            );
            case GRASS_PATH -> new PathBlock(
                Block.Properties.of().mapColor(MapColor.DIRT).strength(1.5f).sound(SoundType.GRASS),
                getBlock(soil, NTESoilBlockType.DIRT)
            );
            case CLAY -> new DirtBlock(
                Block.Properties.of().mapColor(MapColor.DIRT).strength(1.5f).sound(SoundType.GRAVEL),
                getBlock(soil, NTESoilBlockType.CLAY_GRASS),
                getBlock(soil, NTESoilBlockType.GRASS_PATH),
                getBlock(soil, NTESoilBlockType.FARMLAND),
                getBlock(soil, NTESoilBlockType.ROOTED_DIRT),
                getBlock(soil, NTESoilBlockType.MUD)
            );
            case CLAY_GRASS -> new ConnectedGrassBlock(
                Block.Properties.of().mapColor(MapColor.GRASS).randomTicks().strength(1.8f).sound(SoundType.GRASS),
                getBlock(soil, NTESoilBlockType.CLAY),
                getBlock(soil, NTESoilBlockType.GRASS_PATH),
                getBlock(soil, NTESoilBlockType.FARMLAND)
            );
            case CLAY_DUFF -> new ConnectedDuffBlock(
                Block.Properties.of().mapColor(MapColor.DIRT).randomTicks().strength(1.8f).sound(SoundType.GRASS),
                getBlock(soil, NTESoilBlockType.CLAY),
                getBlock(soil, NTESoilBlockType.GRASS_PATH),
                getBlock(soil, NTESoilBlockType.FARMLAND)
            );
            case ROOTED_DIRT -> new TFCRootedDirtBlock(
                Block.Properties.of().mapColor(MapColor.DIRT).strength(2.0f).sound(SoundType.ROOTED_DIRT),
                getBlock(soil, NTESoilBlockType.DIRT),
                getBlock(soil, NTESoilBlockType.MUD)
            );
            case COARSE_DIRT -> new Block(
                Block.Properties.of().mapColor(MapColor.DIRT).sound(SoundType.GRAVEL).strength(1.6f).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
            );
            case FARMLAND -> new NTEFarmlandBlock(
                ExtendedProperties.of(MapColor.DIRT)
                    .strength(1.3f)
                    .sound(SoundType.GRAVEL)
                    .isViewBlocking(TFCBlocks::always)
                    .isSuffocating(TFCBlocks::always)
                    .blockEntity(NTEBlockEntities.FARMLAND),
                getBlock(soil, NTESoilBlockType.DIRT)
            );
            case MUD -> new MudBlock(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .sound(SoundType.MUD)
                    .strength(2f)
                    .speedFactor(0.8f)
                    .isRedstoneConductor(TFCBlocks::always)
                    .isViewBlocking(TFCBlocks::always)
                    .isSuffocating(TFCBlocks::always)
                    .instrument(NoteBlockInstrument.BASEDRUM)
            );
            case MUD_BRICKS -> new Block(
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .sound(SoundType.MUD_BRICKS)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(2.6f)
            );
            case DRYING_BRICKS -> new NTEDryingBricksBlock(
                ExtendedProperties.of(MapColor.DIRT)
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.STEM)
                    .randomTicks()
                    .blockEntity(NTEBlockEntities.TICK_COUNTER),
                getDriedMudBrickItem(soil)
            );
            case MUDDY_ROOTS -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.MUDDY_MANGROVE_ROOTS).strength(4f));
        };
    }

    private static Block createCropBlock(NTECrop crop)
    {
        return NTEDefaultCropBlock.create(cropProperties(), 6, getDeadCrop(crop), getCropSeed(crop), crop);
    }

    private static Block createDeadCropBlock(NTECrop crop)
    {
        return new DeadCropBlock(deadProperties(), crop.climateRange());
    }

    private static Block createWildCropBlock()
    {
        return new WildCropBlock(deadProperties());
    }

    private static Item createProduceItem(NTECrop crop)
    {
        return new Item(crop.isFoodItem() ? rawFoodProperties() : new Item.Properties());
    }

    private static Item createCookedProduceItem(NTECrop crop)
    {
        return new Item(cookedFoodProperties());
    }

    private static Item driedMudBrickItem()
    {
        return new Item(new Item.Properties());
    }

    private static ExtendedProperties cropProperties()
    {
        return deadProperties().blockEntity(NTEBlockEntities.CROP).serverTicks((BlockEntityTicker<NTECropBlockEntity>) NTECropBlockEntity::serverTick);
    }

    private static ExtendedProperties deadProperties()
    {
        return ExtendedProperties.of(MapColor.PLANT)
            .noCollission()
            .randomTicks()
            .strength(0.4F)
            .sound(SoundType.CROP)
            .flammable(60, 30)
            .pushReaction(PushReaction.DESTROY);
    }

    private static Item.Properties rawFoodProperties()
    {
        return new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.3f).build());
    }

    private static Item.Properties cookedFoodProperties()
    {
        return new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.6f).build());
    }

    private static Item.Properties jamFoodProperties()
    {
        return new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(1.0f).build());
    }
}
