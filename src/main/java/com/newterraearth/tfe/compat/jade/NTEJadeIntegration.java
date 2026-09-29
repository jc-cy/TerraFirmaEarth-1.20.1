package com.newterraearth.tfe.compat.jade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.dries007.tfc.common.blockentities.CropBlockEntity;
import net.dries007.tfc.common.blockentities.FarmlandBlockEntity;
import net.dries007.tfc.common.blockentities.IFarmland;
import net.dries007.tfc.common.blockentities.TickCounterBlockEntity;
import net.dries007.tfc.common.blocks.crop.CropBlock;
import net.dries007.tfc.common.blocks.crop.CropHelpers;
import net.dries007.tfc.common.blocks.plant.fruit.BananaPlantBlock;
import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeBranchBlock;
import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeLeavesBlock;
import net.dries007.tfc.common.blocks.plant.fruit.GrowingFruitTreeBranchBlock;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.blocks.plant.fruit.SeasonalPlantBlock;
import net.dries007.tfc.common.blocks.plant.fruit.WaterloggedBerryBushBlock;
import net.dries007.tfc.common.blocks.soil.HoeOverlayBlock;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;

import com.newterraearth.tfe.NewTerraEarthMod;
import com.newterraearth.tfe.common.block.rope.NTEMetalRopeAnchorBlock;
import com.newterraearth.tfe.mixin.GrowingFruitTreeBranchBlockAccessor;
import com.newterraearth.tfe.mixin.SeasonalPlantBlockAccessor;
import com.newterraearth.tfe.world.NTESeasonalHelpers;
import com.newterraearth.tfe.world.NTESoilFertility;
import com.newterraearth.tfe.world.crop.NTECropTemperatureAccess;
import com.newterraearth.tfe.world.crop.NTECropTemperatureModel;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class NTEJadeIntegration implements snownee.jade.api.IWailaPlugin
{
    private static final String PRESSURE_TAG = "tfeTempStress";
    private static final ResourceLocation PRESSURE_UID = new ResourceLocation(NewTerraEarthMod.MOD_ID, "crop_temperature_pressure");
    private static final ResourceLocation CROP_GROWTH_UID = new ResourceLocation(NewTerraEarthMod.MOD_ID, "crop_growth");
    private static final ResourceLocation FRUIT_TREE_GROWTH_UID = new ResourceLocation(NewTerraEarthMod.MOD_ID, "fruit_tree_growth");
    private static final ResourceLocation FRUIT_TREE_BRANCH_UID = new ResourceLocation(NewTerraEarthMod.MOD_ID, "fruit_tree_branch");
    private static final ResourceLocation SEASONAL_PLANT_UID = new ResourceLocation(NewTerraEarthMod.MOD_ID, "seasonal_plant");
    private static final ResourceLocation ROPE_ANCHOR_UID = new ResourceLocation(NewTerraEarthMod.MOD_ID, "metal_rope_anchor");

    @Override
    public void register(IWailaCommonRegistration registration)
    {
        registration.registerBlockDataProvider(new TemperaturePressureDataProvider(), CropBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration)
    {
        registration.registerBlockComponent(new TemperaturePressureProvider(), CropBlock.class);
        registration.registerBlockComponent(new CropGrowthProvider(), CropBlock.class);
        registration.registerBlockComponent(new FruitTreeGrowthProvider(), GrowingFruitTreeBranchBlock.class);
        registration.registerBlockComponent(new FruitTreeBranchProvider(), FruitTreeBranchBlock.class);
        registration.registerBlockComponent(new SeasonalPlantProvider(), SeasonalPlantBlock.class);
        registration.registerBlockComponent(new MetalRopeAnchorProvider(), NTEMetalRopeAnchorBlock.class);
    }

    private static final class FruitTreeGrowthProvider implements IBlockComponentProvider
    {
        @Override
        public ResourceLocation getUid()
        {
            return FRUIT_TREE_GROWTH_UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            if (!(accessor.getBlockEntity() instanceof TickCounterBlockEntity counter))
            {
                appendHoeOverlayInfo(tooltip, accessor);
                return;
            }

            final BlockState state = accessor.getBlockState();
            if (state.getValue(FruitTreeBranchBlock.STAGE) >= 3)
            {
                return;
            }
            if (!fruitTreeBranchClimateValid(accessor))
            {
                return;
            }

            final long ticksLeft = 5L * ICalendar.TICKS_IN_DAY - counter.getTicksSinceUpdate();
            if (ticksLeft > 0L)
            {
                tooltip.add(Component.translatable("tfc.jade.time_left", Calendars.get(accessor.getLevel()).getTimeDelta(ticksLeft)));
            }
            else
            {
                tooltip.add(Component.translatable("tfc.jade.ready_to_grow"));
            }
        }
    }

    private static boolean fruitTreeBranchClimateValid(BlockAccessor accessor)
    {
        final BlockState state = accessor.getBlockState();
        if (state.getValue(GrowingFruitTreeBranchBlock.NATURAL))
        {
            return true;
        }

        final BlockPos stemPos = NTESeasonalHelpers.getFruitTreeStemPos(accessor.getLevel(), accessor.getPosition());
        final BlockPos rootPos = stemPos.below();
        final var range = ((GrowingFruitTreeBranchBlockAccessor) accessor.getBlock()).tfe$getClimateRange().get();
        return range.checkBoth(
            NTESeasonalHelpers.getFruitBushHydrationFromRootPos(accessor.getLevel(), rootPos),
            NTESeasonalHelpers.getPlantTemperature(accessor.getLevel(), stemPos),
            false
        );
    }

    /**
     * The normal TFC Jade hoe provider requires a block entity. Static fruit
     * tree branch blocks have none, so expose their climate and completion
     * information directly and avoid an empty Jade panel.
     */
    private static final class FruitTreeBranchProvider implements IBlockComponentProvider
    {
        @Override
        public ResourceLocation getUid()
        {
            return FRUIT_TREE_BRANCH_UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            if (!(accessor.getBlock() instanceof GrowingFruitTreeBranchBlock))
            {
                appendHoeOverlayInfo(tooltip, accessor);
            }
        }
    }

    private static void appendHoeOverlayInfo(ITooltip tooltip, BlockAccessor accessor)
    {
        if (accessor.getBlock() instanceof HoeOverlayBlock overlay)
        {
            final List<Component> text = new ArrayList<>();
            overlay.addHoeOverlayInfo(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState(), text, false);
            tooltip.addAll(text);
        }
    }

    private static final class SeasonalPlantProvider implements IBlockComponentProvider
    {
        @Override
        public ResourceLocation getUid()
        {
            return SEASONAL_PLANT_UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            final BlockState state = accessor.getBlockState();
            if (!state.hasProperty(SeasonalPlantBlock.LIFECYCLE))
            {
                return;
            }
            if (accessor.getBlock() instanceof FruitTreeLeavesBlock && state.getValue(FruitTreeLeavesBlock.PERSISTENT))
            {
                tooltip.add(plantStatus("tfe.jade.plant.decorative_leaves"));
                return;
            }

            // 采摘休眠期内只显示休眠倒计时，不再显示"展叶期"等生命周期状态（与 TFC 1.21 的语义一致）。
            if (NTESeasonalHelpers.isHarvestDormant(accessor.getLevel(), accessor.getPosition()))
            {
                tooltip.add(Component.translatable("tfe.jade.harvest_dormancy",
                    Calendars.get(accessor.getLevel()).getTimeDelta(NTESeasonalHelpers.getHarvestDormancyRemaining(accessor.getLevel(), accessor.getPosition()))));
                return;
            }

            final Lifecycle lifecycle = state.getValue(SeasonalPlantBlock.LIFECYCLE);
            final String statusKey;
            if (lifecycle == Lifecycle.DORMANT)
            {
                final Lifecycle expected = ((SeasonalPlantBlockAccessor) accessor.getBlock()).tfe$invokeGetLifecycleForMonth(
                    NTESeasonalHelpers.getHemispheralCalendarMonthOfYear(accessor.getLevel(), accessor.getPosition())
                );
                if (expected == Lifecycle.DORMANT)
                {
                    statusKey = "tfe.jade.plant.waiting_season";
                }
                else
                {
                    statusKey = seasonalPlantClimateValid(accessor)
                        ? "tfe.jade.plant.waiting_update"
                        : "tfe.jade.plant.waiting_climate";
                }
            }
            else
            {
                statusKey = switch (lifecycle)
                {
                    case HEALTHY -> "tfe.jade.plant.leafing";
                    case FLOWERING -> "tfe.jade.plant.flowering";
                    case FRUITING -> "tfe.jade.plant.fruiting";
                    case DORMANT -> throw new IllegalStateException("Dormant lifecycle handled above");
                };
            }
            tooltip.add(plantStatus(statusKey));
        }
    }

    private static boolean seasonalPlantClimateValid(BlockAccessor accessor)
    {
        final BlockPos pos = accessor.getPosition();
        final BlockPos temperaturePos;
        final BlockPos hydrationPos;
        if (accessor.getBlock() instanceof FruitTreeLeavesBlock)
        {
            temperaturePos = NTESeasonalHelpers.getFruitTreeStemPos(accessor.getLevel(), pos);
            hydrationPos = temperaturePos.below();
        }
        else if (accessor.getBlock() instanceof BananaPlantBlock)
        {
            temperaturePos = NTESeasonalHelpers.getBananaRootPos(accessor.getLevel(), pos);
            hydrationPos = temperaturePos;
        }
        else
        {
            temperaturePos = pos;
            hydrationPos = pos.below();
        }

        final var range = ((SeasonalPlantBlockAccessor) accessor.getBlock()).tfe$getClimateRange().get();
        final int hydration = accessor.getBlock() instanceof WaterloggedBerryBushBlock
            ? stateFluidHydration(accessor.getBlockState())
            : NTESeasonalHelpers.getFruitBushHydrationFromRootPos(accessor.getLevel(), hydrationPos);
        return range.checkBoth(
            hydration,
            NTESeasonalHelpers.getPlantTemperature(accessor.getLevel(), temperaturePos),
            false
        );
    }

    private static int stateFluidHydration(BlockState state)
    {
        return state.getValue(WaterloggedBerryBushBlock.FLUID).getFluid() == Fluids.EMPTY ? 0 : 100;
    }

    private static final class CropGrowthProvider implements IBlockComponentProvider
    {
        @Override
        public ResourceLocation getUid()
        {
            return CROP_GROWTH_UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            if (!(accessor.getBlockEntity() instanceof CropBlockEntity crop) || !(accessor.getBlock() instanceof CropBlock cropBlock))
            {
                return;
            }

            final float growth = Mth.clamp(crop.getGrowth(), 0f, CropHelpers.GROWTH_LIMIT);
            if (growth >= CropHelpers.GROWTH_LIMIT)
            {
                final int expiryPercent = percent(crop.getExpiry(), CropHelpers.EXPIRY_LIMIT);
                tooltip.add(plantStatus("tfe.jade.plant.growth_complete"));
                tooltip.add(Component.translatable("tfe.jade.crop_expiry", expiryPercent));
                return;
            }

            final BlockPos pos = accessor.getPosition();
            final BlockPos sourcePos = pos.below();
            final int growthPercent = percent(growth, CropHelpers.GROWTH_LIMIT);
            tooltip.add(Component.translatable("tfe.jade.crop_growth", growthPercent));

            final boolean climateValid = cropBlock.getClimateRange().checkBoth(
                NTESeasonalHelpers.getConfiguredCropHydration(accessor.getLevel(), sourcePos),
                NTESeasonalHelpers.getPlantTemperature(accessor.getLevel(), pos),
                false
            );
            if (!climateValid)
            {
                tooltip.add(plantStatus("tfe.jade.plant.waiting_climate"));
                return;
            }
            final float growthLimit = cropBlock.getGrowthLimit(accessor.getLevel(), pos, accessor.getBlockState());
            if (growthLimit <= growth + 0.0001f)
            {
                tooltip.add(plantStatus("tfe.jade.crop.growth_blocked"));
                return;
            }
            if (growthLimit < CropHelpers.GROWTH_LIMIT - 0.0001f)
            {
                tooltip.add(plantStatus("tfe.jade.crop.growth_limited"));
                return;
            }

            tooltip.add(plantStatus("tfe.jade.plant.growing"));
            final long estimatedTicks = estimatedGrowthTicks(accessor, sourcePos, cropBlock, growth);
            tooltip.add(Component.translatable(
                "tfe.jade.crop_estimated_time",
                Calendars.get(accessor.getLevel()).getTimeDelta(estimatedTicks)
            ));
            appendFertilizerStatus(tooltip, accessor, sourcePos, cropBlock, growth);
        }
    }

    /**
     * 肥料一行：剩余生长期需要的肥料够不够。需求按真实速率折算（剩余生长量 × 2 / (1 + 土壤肥力)），
     * 缺口再除以"这份耕地上一份肥料实际补多少"，得到玩家需要施加的氮 / 磷 / 钾肥量。
     */
    private static void appendFertilizerStatus(ITooltip tooltip, BlockAccessor accessor, BlockPos farmlandPos, CropBlock cropBlock, float growth)
    {
        if (!(accessor.getLevel().getBlockEntity(farmlandPos) instanceof IFarmland farmland))
        {
            return;
        }
        final FarmlandBlockEntity.NutrientType type = cropBlock.getPrimaryNutrient();
        final BlockState farmlandState = accessor.getLevel().getBlockState(farmlandPos);
        final float soilFertility = NTESoilFertility.getModifier(farmlandState);
        final float growthModifier = TFCConfig.SERVER.cropGrowthModifier.get().floatValue();
        final float missing = (CropHelpers.GROWTH_LIMIT - growth) * 2f / (1f / growthModifier + soilFertility) - farmland.getNutrient(type);
        if (missing <= 0f)
        {
            tooltip.add(Component.translatable("tfe.jade.crop.fertilizer_enough").withStyle(ChatFormatting.GREEN));
            return;
        }
        final float perItem = soilFertility * externalFertilizerMultiplier(farmlandState);
        tooltip.add(Component.translatable(
            "tfe.jade.crop.fertilizer_short",
            Mth.ceil(missing / perItem * 100f),
            Component.translatable(nutrientNameKey(type))
        ).withStyle(ChatFormatting.YELLOW));
    }

    /**
     * 第三方模组在同一处 {@code addNutrients} 上额外叠加的施肥倍率。这类倍率是注入在调用点上的常量，
     * 没有标签、配置或数据出口可供读取，所以只能按方块 id 认出来后写死一份；它与
     * {@link NTESoilFertility} 的土壤肥力档是两套不同数值，前者是别人的施肥加成，后者是本模组的肥力。
     */
    private static float externalFertilizerMultiplier(BlockState state)
    {
        return switch (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath())
        {
            case "rich_soil_farmland/lush" -> 1.2f;
            case "rich_soil_farmland/enriched" -> 1.1f;
            case "rich_soil_farmland/normal" -> 1.0f;
            case "rich_soil_farmland/barren" -> 0.9f;
            case "rich_soil_farmland/reclaimed" -> 0.8f;
            default -> 1.0f;
        };
    }

    private static String nutrientNameKey(FarmlandBlockEntity.NutrientType type)
    {
        return switch (type)
        {
            case NITROGEN -> "tfe.nutrient.nitrogen";
            case PHOSPHOROUS -> "tfe.nutrient.phosphorus";
            case POTASSIUM -> "tfe.nutrient.potassium";
        };
    }

    /**
     * 预计成熟刻数：按 {@code CropHelpers.growthTickStep} 的真实速率估算——基础项（受 cropGrowthModifier 影响）
     * 加养分项（受耕地土壤肥力与当前养分影响）；耕地养分只够支撑一部分生长期时，按两段分别累加。
     */
    private static long estimatedGrowthTicks(BlockAccessor accessor, BlockPos farmlandPos, CropBlock cropBlock, float growth)
    {
        final float growthModifier = TFCConfig.SERVER.cropGrowthModifier.get().floatValue();
        final float baseRate = CropHelpers.GROWTH_FACTOR / growthModifier;
        float soilFertility = 1f;
        float nutrient = 0f;
        if (accessor.getLevel().getBlockEntity(farmlandPos) instanceof IFarmland farmland)
        {
            soilFertility = NTESoilFertility.getModifier(accessor.getLevel().getBlockState(farmlandPos));
            nutrient = farmland.getNutrient(cropBlock.getPrimaryNutrient());
        }
        final float remaining = CropHelpers.GROWTH_LIMIT - growth;
        final float saturatedRate = CropHelpers.GROWTH_FACTOR * (1f / growthModifier + soilFertility);
        final long saturatedTicks = (long) Math.ceil(remaining / saturatedRate);
        final long nutrientTicks = (long) (nutrient / CropHelpers.NUTRIENT_CONSUMPTION);
        if (saturatedTicks <= nutrientTicks)
        {
            return saturatedTicks;
        }
        return nutrientTicks + (long) Math.ceil((remaining - nutrientTicks * saturatedRate) / baseRate);
    }

    private static int percent(float value, float limit)
    {
        return Mth.clamp(Math.round(value / limit * 100f), 0, 100);
    }

    private static Component plantStatus(String translationKey)
    {
        return Component.translatable("tfe.jade.plant_status", Component.translatable(translationKey));
    }

    private static final class TemperaturePressureProvider implements IBlockComponentProvider
    {
        @Override
        public ResourceLocation getUid()
        {
            return PRESSURE_UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            final CompoundTag serverData = accessor.getServerData();
            if (serverData == null || !serverData.contains(PRESSURE_TAG))
            {
                return;
            }

            final int pressure = Mth.clamp(serverData.getByte(PRESSURE_TAG), 0, NTECropTemperatureModel.STRESS_LIMIT);
            tooltip.add(Component.translatable("tfe.jade.crop_health", NTECropTemperatureModel.healthPercent(pressure)));
        }
    }

    private static final class TemperaturePressureDataProvider implements IServerDataProvider<BlockAccessor>
    {
        @Override
        public ResourceLocation getUid()
        {
            return PRESSURE_UID;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor)
        {
            final BlockEntity blockEntity = accessor.getBlockEntity();
            if (blockEntity instanceof NTECropTemperatureAccess stress)
            {
                data.putByte(PRESSURE_TAG, (byte) Mth.clamp(stress.tfe$getTemperatureStress(), 0, NTECropTemperatureModel.STRESS_LIMIT));
            }
        }
    }

    /** 金属绳锚的加固状态；敲满之前不能绑绳。 */
    private static final class MetalRopeAnchorProvider implements IBlockComponentProvider
    {
        @Override
        public ResourceLocation getUid()
        {
            return ROPE_ANCHOR_UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config)
        {
            final int stage = accessor.getBlockState().getValue(NTEMetalRopeAnchorBlock.STAGE);
            if (stage >= NTEMetalRopeAnchorBlock.MAX_STAGE)
            {
                tooltip.add(Component.translatable("tfe.jade.rope_anchor.reinforced"));
            }
            else
            {
                tooltip.add(Component.translatable("tfe.jade.rope_anchor.needs_reinforcement", stage, NTEMetalRopeAnchorBlock.MAX_STAGE));
            }
        }
    }
}
