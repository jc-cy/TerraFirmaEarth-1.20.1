package com.newterraearth.tfe.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import com.newterraearth.tfe.world.crop.NTECrop;

public final class NTECommonConfig
{
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue WILD_CROP_ALFALFA;
    private static final ForgeConfigSpec.BooleanValue WILD_CROP_CANOLA;
    private static final ForgeConfigSpec.BooleanValue WILD_CROP_CASSAVA;
    private static final ForgeConfigSpec.BooleanValue WILD_CROP_LENTIL;
    private static final ForgeConfigSpec.BooleanValue WILD_CROP_PEANUT;
    private static final ForgeConfigSpec.BooleanValue WILD_CROP_RADISH;

    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_CHERRY;
    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_GREEN_APPLE;
    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_LEMON;
    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_OLIVE;
    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_ORANGE;
    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_PEACH;
    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_PLUM;
    private static final ForgeConfigSpec.BooleanValue FRUIT_TREE_RED_APPLE;
    private static final ForgeConfigSpec.BooleanValue BAMBOO;
    private static final ForgeConfigSpec.BooleanValue GOLDEN_BAMBOO;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_BISON;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_LEOPARD_SEAL;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_LEMMING;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_MONGOOSE;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_JERBOA;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_NEW_FRESHWATER_FISH;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_BACTRIAN_CAMEL;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_DROMEDARY_CAMEL;
    private static final ForgeConfigSpec.BooleanValue ENTITY_SPAWN_ARMADILLO;
    private static final ForgeConfigSpec.IntValue SNOW_MAX_ACCUMULATION_ON_UPDATE;
    private static final ForgeConfigSpec.IntValue TICKS_PER_SNOW_ACCUMULATION;
    private static final ForgeConfigSpec.IntValue SNOW_MELT_MULTIPLIER;
    private static final ForgeConfigSpec.BooleanValue CROP_USE_CURRENT_RAINFALL_HYDRATION;
    private static final ForgeConfigSpec.BooleanValue FRUIT_USE_CURRENT_RAINFALL_HYDRATION;
    private static final ForgeConfigSpec.DoubleValue MOUNTAIN_CLIFF_HEIGHT_MIN;
    private static final ForgeConfigSpec.DoubleValue MOUNTAIN_CLIFF_HEIGHT_MAX;
    private static final ForgeConfigSpec.DoubleValue MOUNTAIN_CLIFF_FADE_RATIO_MIN;
    private static final ForgeConfigSpec.DoubleValue MOUNTAIN_CLIFF_FADE_RATIO_MAX;
    private static final ForgeConfigSpec.BooleanValue TERRAIN_UPLIFT_ENABLED;
    private static final ForgeConfigSpec.DoubleValue TERRAIN_UPLIFT_SOURCE_HEIGHT;
    private static final ForgeConfigSpec.IntValue TERRAIN_UPLIFT_SOURCE_FALLOFF_DISTANCE;
    private static final ForgeConfigSpec.IntValue TERRAIN_UPLIFT_SMALL_PLATFORM_RADIUS;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_TRANSITION_BASE;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_TRANSITION_SLOPE;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_TRANSITION_MAX;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_TIER_LOW;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_TIER_MID;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_TIER_HIGH;
    private static final ForgeConfigSpec.BooleanValue HEADWATER_UNDERGROUND_ENABLED;
    private static final ForgeConfigSpec.IntValue HEADWATER_SINK_MIN_CUT;
    private static final ForgeConfigSpec.IntValue HEADWATER_SINK_MIN_RUN;
    private static final ForgeConfigSpec.IntValue HEADWATER_TUNNEL_ROOF;
    private static final ForgeConfigSpec.IntValue HEADWATER_TUNNEL_AIR_MIN;
    private static final ForgeConfigSpec.IntValue HEADWATER_TUNNEL_AIR_MAX;
    private static final ForgeConfigSpec.IntValue HEADWATER_TUNNEL_WATER_DEPTH;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_TUNNEL_CARVING_NOISE;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_MOUTH_LATERAL_RISE;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_MOUTH_NOISE;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_MOUTH_MAX_SLOPE;
    private static final ForgeConfigSpec.DoubleValue HEADWATER_MOUTH_MAX_LENGTH;

    static
    {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("只控制本模组管理的可种植内容是否自然生成；不会禁用方块、物品或手动种植逻辑。");
        builder.push("wild_crops");
        WILD_CROP_ALFALFA = builder.define("alfalfa", true);
        WILD_CROP_CANOLA = builder.define("canola", true);
        WILD_CROP_CASSAVA = builder.define("cassava", true);
        WILD_CROP_LENTIL = builder.define("lentil", true);
        WILD_CROP_PEANUT = builder.define("peanut", true);
        WILD_CROP_RADISH = builder.define("radish", true);
        builder.pop();

        builder.push("fruit_trees");
        FRUIT_TREE_CHERRY = builder.define("cherry", true);
        FRUIT_TREE_GREEN_APPLE = builder.define("green_apple", true);
        FRUIT_TREE_LEMON = builder.define("lemon", true);
        FRUIT_TREE_OLIVE = builder.define("olive", true);
        FRUIT_TREE_ORANGE = builder.define("orange", true);
        FRUIT_TREE_PEACH = builder.define("peach", true);
        FRUIT_TREE_PLUM = builder.define("plum", true);
        FRUIT_TREE_RED_APPLE = builder.define("red_apple", true);
        builder.pop();

        builder.push("bamboo");
        BAMBOO = builder.define("bamboo", true);
        GOLDEN_BAMBOO = builder.define("golden_bamboo", true);
        builder.pop();

        builder.comment("只控制自然刷新选择，包括群系刷新和虫害系统选择的害虫；关闭后实体仍会注册，仍可通过刷怪蛋、命令或自定义脚本使用。");
        builder.push("entity_spawns");
        ENTITY_SPAWN_BISON = builder.define("bison", true);
        ENTITY_SPAWN_LEOPARD_SEAL = builder.define("leopard_seal", true);
        ENTITY_SPAWN_LEMMING = builder.define("lemming", true);
        ENTITY_SPAWN_MONGOOSE = builder.define("mongoose", true);
        ENTITY_SPAWN_JERBOA = builder.define("jerboa", true);
        ENTITY_SPAWN_NEW_FRESHWATER_FISH = builder.define("new_freshwater_fish", true);
        ENTITY_SPAWN_BACTRIAN_CAMEL = builder.define("bactrian_camel", true);
        ENTITY_SPAWN_DROMEDARY_CAMEL = builder.define("dromedary_camel", true);
        ENTITY_SPAWN_ARMADILLO = builder.define("armadillo", true);
        builder.pop();

        builder.comment("控制 1.21 风格的运行时积雪追赶后端；原版 1.20 的降雨和雷暴调度不会改变。");
        builder.push("weather_runtime");
        SNOW_MAX_ACCUMULATION_ON_UPDATE = builder
            .comment("重新进入未加载区块时最多执行的雪、冰、冰锥更新次数。数值越低，越接近周围已加载区块的变化速度。")
            .defineInRange("snow_max_accumulation_on_update", 64, 0, 256);
        TICKS_PER_SNOW_ACCUMULATION = builder
            .comment("两次积雪尝试之间的游戏刻间隔。数值越低积雪越快，但运行时开销越高。")
            .defineInRange("ticks_per_snow_accumulation", 80, 1, Integer.MAX_VALUE);
        SNOW_MELT_MULTIPLIER = builder
            .comment("安排融雪尝试时套用在积雪间隔上的倍率。默认值 3 与 1.21 TFC 一致。")
            .defineInRange("snow_melt_multiplier", 3, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("控制作物和结果植物使用当前季节降雨湿度，还是使用旧版年均湿度。");
        builder.push("plant_hydration");
        CROP_USE_CURRENT_RAINFALL_HYDRATION = builder
            .comment("设为 true 时，作物生长和作物提示使用当前季节降雨换算出的湿度。")
            .define("crop_use_current_rainfall_hydration", true);
        FRUIT_USE_CURRENT_RAINFALL_HYDRATION = builder
            .comment("设为 true 时，果树、浆果灌木和香蕉植株使用当前季节降雨换算出的湿度。")
            .define("fruit_use_current_rainfall_hydration", true);
        builder.pop();

        builder.comment("山脉 heightmap 的峰顶 cliff 噪声。改动只影响新生成区块。");
        builder.push("mountain_heightmap");
        MOUNTAIN_CLIFF_HEIGHT_MIN = builder
            .comment("山脉在 y > 120 后额外叠加的峰顶 cliff 随机高度下限。默认 0 表示仍允许部分山没有明显 cliff 抬高。")
            .defineInRange("cliff_height_min", 0.0d, 0.0d, 64.0d);
        MOUNTAIN_CLIFF_HEIGHT_MAX = builder
            .comment("山脉在 y > 120 后额外叠加的峰顶 cliff 随机高度上限。TFC 原生正向上限为 25 格；默认值为 30 格。")
            .defineInRange("cliff_height_max", 30.0d, 0.0d, 64.0d);
        MOUNTAIN_CLIFF_FADE_RATIO_MIN = builder
            .comment("峰顶 cliff 从 0 渐变到实际抬高量所需的基础高度差比例下限。实际渐变高度 = 当前 cliff 抬高量 * ratio；0.25 约等于最陡每 1 格基础高度增加 4 格 cliff 抬高。")
            .defineInRange("cliff_fade_ratio_min", 0.25d, 0.01d, 4.0d);
        MOUNTAIN_CLIFF_FADE_RATIO_MAX = builder
            .comment("峰顶 cliff 从 0 渐变到实际抬高量所需的基础高度差比例上限。0.5 约等于每 1 格基础高度增加 2 格 cliff 抬高。")
            .defineInRange("cliff_fade_ratio_max", 0.5d, 0.01d, 4.0d);
        builder.pop();

        builder.comment("控制本模组的山区地势抬高场。改动只影响新生成区块；在已有世界中途修改可能造成新旧区块地形边界。");
        builder.push("terrain_uplift");
        TERRAIN_UPLIFT_ENABLED = builder
            .comment("设为 false 时关闭本模组的山区地势抬高场；不会改变群系选择或普通 TFC 地形地物。")
            .define("enabled", true);
        TERRAIN_UPLIFT_SOURCE_HEIGHT = builder
            .comment("单个地势抬高源的强度参数，之后还会经过河流、湖泊、海岸和多源融合折算；默认 150.0 实际约按 15.8% 增加高山最高高度")
            .defineInRange("source_height", 150.0d, 0.0d, 4096.0d);
        TERRAIN_UPLIFT_SOURCE_FALLOFF_DISTANCE = builder
            .comment("普通点源从平台边缘衰减到 0 的距离，单位为方块。默认 590 加上 10 格平台半径，约等于从中心到外缘 600 格。")
            .defineInRange("source_falloff_distance", 590, 1, 2048);
        TERRAIN_UPLIFT_SMALL_PLATFORM_RADIUS = builder
            .comment("普通山地抬高源中心平缓平台半径，单位为方块。巨型火山仍使用自己的动态平台大小。")
            .defineInRange("small_platform_radius", 10, 0, 256);
        builder.pop();

        builder.comment("补充溪流的河口过渡参数。改动只影响新生成区块；已有区块不会回刷，请在新地图使用。");
        builder.push("headwater_streams");
        HEADWATER_TRANSITION_BASE = builder
            .comment("河口过渡的基础长度，单位为方块。落差不超过该值时，溪流剖面与旧版完全一致。")
            .defineInRange("transition_base", 24.0d, 8.0d, 64.0d);
        HEADWATER_TRANSITION_SLOPE = builder
            .comment("落差超过基础长度后，每多 1 格落差额外增加的过渡长度倍率。默认 1.0 表示一比一增加。")
            .defineInRange("transition_slope", 1.0d, 0.0d, 4.0d);
        HEADWATER_TRANSITION_MAX = builder
            .comment("过渡长度的硬上限，单位为方块。超过上限的落差不会继续拉长地表过渡。")
            .defineInRange("transition_max", 64.0d, 24.0d, 256.0d);
        HEADWATER_TIER_LOW = builder
            .comment("每条溪流自己的随机倍率档位之一，数值越小河口过渡越陡。")
            .defineInRange("random_tier_low", 0.5d, 0.0d, 4.0d);
        HEADWATER_TIER_MID = builder
            .comment("每条溪流自己的随机倍率档位之一，默认 1.0 表示按原样增长。")
            .defineInRange("random_tier_mid", 1.0d, 0.0d, 4.0d);
        HEADWATER_TIER_HIGH = builder
            .comment("每条溪流自己的随机倍率档位之一，数值越大河口过渡越平缓。")
            .defineInRange("random_tier_high", 1.5d, 0.0d, 4.0d);
        HEADWATER_UNDERGROUND_ENABLED = builder
            .comment("切割过深时是否让溪流转入地下。设为 false 时只保留动态过渡，用于对照排查。")
            .define("underground_enabled", true);
        HEADWATER_SINK_MIN_CUT = builder
            .comment("触发转入地下的可见切割深度阈值，单位为方块。低于该深度的切割继续按地表溪流开挖；"
                + "该值必须明显高于洞口开挖带能吸收的深度，否则临界处会留下薄壁。")
            .defineInRange("sink_min_cut", 15, 1, 64);
        HEADWATER_SINK_MIN_RUN = builder
            .comment("触发转入地下所需的连续超标长度，单位为方块；用于排除单点噪声。")
            .defineInRange("sink_min_run", 15, 1, 256);
        HEADWATER_TUNNEL_ROOF = builder
            .comment("地下溪流洞顶到地表的最小岩层厚度，单位为方块。")
            .defineInRange("tunnel_roof", 4, 1, 32);
        HEADWATER_TUNNEL_AIR_MIN = builder
            .comment("地下溪流水面以上的洞腔高度下限，单位为方块。")
            .defineInRange("tunnel_air_min", 2, 1, 16);
        HEADWATER_TUNNEL_AIR_MAX = builder
            .comment("地下溪流水面以上的洞腔高度上限，单位为方块；洞口越宽越接近该值。")
            .defineInRange("tunnel_air_max", 5, 1, 24);
        HEADWATER_TUNNEL_WATER_DEPTH = builder
            .comment("地下溪流的水道深度，单位为方块。")
            .defineInRange("tunnel_water_depth", 1, 1, 8);
        HEADWATER_TUNNEL_CARVING_NOISE = builder
            .comment("地下洞腔雕琢噪声幅度，单位为方块。数值越大洞顶与洞壁起伏越明显；0 表示光滑管道。")
            .defineInRange("tunnel_carving_noise", 1.5d, 0.0d, 8.0d);
        HEADWATER_MOUTH_LATERAL_RISE = builder
            .comment("洞口漏斗每单位归一化半径²抬升的高度，单位为方块；决定洞口是窄槽还是开阔漏斗。")
            .defineInRange("mouth_lateral_rise", 12.0d, 0.0d, 64.0d);
        HEADWATER_MOUTH_NOISE = builder
            .comment("洞口开挖面的噪声幅度，单位为方块。用于打散人工直线边坡。")
            .defineInRange("mouth_noise", 2.0d, 0.0d, 16.0d);
        HEADWATER_MOUTH_MAX_SLOPE = builder
            .comment("洞口纵向斜坡的最大坡度，单位为方块/方块。默认 1.0 表示落差大的洞口会被拉成缓坡峡谷而不是竖井。")
            .defineInRange("mouth_max_slope", 1.0d, 0.05d, 4.0d);
        HEADWATER_MOUTH_MAX_LENGTH = builder
            .comment("洞口纵向斜坡的长度上限，单位为方块。超过上限的落差只能靠更陡的坡度吸收。")
            .defineInRange("mouth_max_length", 48.0d, 4.0d, 256.0d);
        builder.pop();

        SPEC = builder.build();
    }

    private NTECommonConfig()
    {
    }

    public static boolean isWildCropEnabled(NTECrop crop)
    {
        return switch (crop)
        {
            case ALFALFA -> WILD_CROP_ALFALFA.get();
            case CANOLA -> WILD_CROP_CANOLA.get();
            case CASSAVA -> WILD_CROP_CASSAVA.get();
            case LENTIL -> WILD_CROP_LENTIL.get();
            case PEANUT -> WILD_CROP_PEANUT.get();
            case RADISH -> WILD_CROP_RADISH.get();
        };
    }

    public static boolean isWildCropEnabled(ResourceLocation blockId)
    {
        if (blockId == null || !"tfe".equals(blockId.getNamespace()))
        {
            return true;
        }

        final String path = blockId.getPath();
        if (!path.startsWith("wild_crop/"))
        {
            return true;
        }

        final NTECrop crop = NTECrop.byName(path.substring("wild_crop/".length()));
        return crop == null || isWildCropEnabled(crop);
    }

    public static boolean isFruitTreeEnabled(String treeName)
    {
        return switch (treeName)
        {
            case "cherry" -> FRUIT_TREE_CHERRY.get();
            case "green_apple" -> FRUIT_TREE_GREEN_APPLE.get();
            case "lemon" -> FRUIT_TREE_LEMON.get();
            case "olive" -> FRUIT_TREE_OLIVE.get();
            case "orange" -> FRUIT_TREE_ORANGE.get();
            case "peach" -> FRUIT_TREE_PEACH.get();
            case "plum" -> FRUIT_TREE_PLUM.get();
            case "red_apple" -> FRUIT_TREE_RED_APPLE.get();
            default -> true;
        };
    }

    public static boolean isFruitTreeEnabled(ResourceLocation blockId)
    {
        if (blockId == null || !"tfc".equals(blockId.getNamespace()))
        {
            return true;
        }

        final String path = blockId.getPath();
        if (!path.startsWith("plant/") || !path.endsWith("_growing_branch"))
        {
            return true;
        }

        return isFruitTreeEnabled(path.substring("plant/".length(), path.length() - "_growing_branch".length()));
    }

    public static boolean isBambooEnabled(String bambooName)
    {
        return switch (bambooName)
        {
            case "bamboo" -> BAMBOO.get();
            case "golden_bamboo" -> GOLDEN_BAMBOO.get();
            default -> true;
        };
    }

    public static boolean isBambooEnabled(ResourceLocation blockId)
    {
        if (blockId == null)
        {
            return true;
        }

        if ("minecraft".equals(blockId.getNamespace()) && "bamboo".equals(blockId.getPath()))
        {
            return BAMBOO.get();
        }

        if ("tfe".equals(blockId.getNamespace()) && "plant/golden_bamboo".equals(blockId.getPath()))
        {
            return GOLDEN_BAMBOO.get();
        }

        return true;
    }

    public static boolean isBambooEnabledByBlock(net.minecraft.world.level.block.Block block)
    {
        return isBambooEnabled(ForgeRegistries.BLOCKS.getKey(block));
    }

    public static boolean isPlacedPlantFeatureEnabled(ResourceLocation featureId)
    {
        if (featureId == null || !"tfc".equals(featureId.getNamespace()))
        {
            return true;
        }

        final String path = featureId.getPath();
        if ("bamboo".equals(path))
        {
            return BAMBOO.get();
        }
        if ("bamboo_golden".equals(path))
        {
            return GOLDEN_BAMBOO.get();
        }
        if (!path.startsWith("plant/"))
        {
            return true;
        }

        final String plantPath = path.substring("plant/".length());
        if (plantPath.startsWith("wild_crop/"))
        {
            String cropName = plantPath.substring("wild_crop/".length());
            if (cropName.endsWith("_patch"))
            {
                cropName = cropName.substring(0, cropName.length() - "_patch".length());
            }
            final NTECrop crop = NTECrop.byName(cropName);
            return crop == null || isWildCropEnabled(crop);
        }
        return isFruitTreeEnabled(plantPath);
    }

    public static boolean hasDisabledConfiguredPlantFeatures()
    {
        return !WILD_CROP_ALFALFA.get()
            || !WILD_CROP_CANOLA.get()
            || !WILD_CROP_CASSAVA.get()
            || !WILD_CROP_LENTIL.get()
            || !WILD_CROP_PEANUT.get()
            || !WILD_CROP_RADISH.get()
            || !FRUIT_TREE_CHERRY.get()
            || !FRUIT_TREE_GREEN_APPLE.get()
            || !FRUIT_TREE_LEMON.get()
            || !FRUIT_TREE_OLIVE.get()
            || !FRUIT_TREE_ORANGE.get()
            || !FRUIT_TREE_PEACH.get()
            || !FRUIT_TREE_PLUM.get()
            || !FRUIT_TREE_RED_APPLE.get()
            || !BAMBOO.get()
            || !GOLDEN_BAMBOO.get();
    }

    public static boolean isEntitySpawnEnabled(ResourceLocation entityId)
    {
        if (entityId == null || !"tfc".equals(entityId.getNamespace()))
        {
            return true;
        }

        return switch (entityId.getPath())
        {
            case "bison" -> ENTITY_SPAWN_BISON.get();
            case "leopard_seal" -> ENTITY_SPAWN_LEOPARD_SEAL.get();
            case "lemming" -> ENTITY_SPAWN_LEMMING.get();
            case "mongoose" -> ENTITY_SPAWN_MONGOOSE.get();
            case "jerboa" -> ENTITY_SPAWN_JERBOA.get();
            case "arctic_char", "burbot", "muksun", "northern_pike", "pacu", "peacock_bass", "red_piranha", "spotted_gudgeon", "tilapia" -> ENTITY_SPAWN_NEW_FRESHWATER_FISH.get();
            case "bactrian_camel" -> ENTITY_SPAWN_BACTRIAN_CAMEL.get();
            case "dromedary_camel" -> ENTITY_SPAWN_DROMEDARY_CAMEL.get();
            case "armadillo" -> ENTITY_SPAWN_ARMADILLO.get();
            default -> true;
        };
    }

    public static boolean hasDisabledConfiguredEntitySpawns()
    {
        return !ENTITY_SPAWN_BISON.get()
            || !ENTITY_SPAWN_LEOPARD_SEAL.get()
            || !ENTITY_SPAWN_LEMMING.get()
            || !ENTITY_SPAWN_MONGOOSE.get()
            || !ENTITY_SPAWN_JERBOA.get()
            || !ENTITY_SPAWN_NEW_FRESHWATER_FISH.get()
            || !ENTITY_SPAWN_BACTRIAN_CAMEL.get()
            || !ENTITY_SPAWN_DROMEDARY_CAMEL.get()
            || !ENTITY_SPAWN_ARMADILLO.get();
    }

    public static int getConfiguredEntitySpawnMask()
    {
        int mask = 0;
        if (ENTITY_SPAWN_BISON.get())
        {
            mask |= 1;
        }
        if (ENTITY_SPAWN_LEOPARD_SEAL.get())
        {
            mask |= 1 << 1;
        }
        if (ENTITY_SPAWN_LEMMING.get())
        {
            mask |= 1 << 2;
        }
        if (ENTITY_SPAWN_MONGOOSE.get())
        {
            mask |= 1 << 3;
        }
        if (ENTITY_SPAWN_JERBOA.get())
        {
            mask |= 1 << 4;
        }
        if (ENTITY_SPAWN_NEW_FRESHWATER_FISH.get())
        {
            mask |= 1 << 5;
        }
        if (ENTITY_SPAWN_BACTRIAN_CAMEL.get())
        {
            mask |= 1 << 6;
        }
        if (ENTITY_SPAWN_DROMEDARY_CAMEL.get())
        {
            mask |= 1 << 7;
        }
        if (ENTITY_SPAWN_ARMADILLO.get())
        {
            mask |= 1 << 8;
        }
        return mask;
    }

    public static int getSnowMaxAccumulationOnUpdate()
    {
        return SNOW_MAX_ACCUMULATION_ON_UPDATE.get();
    }

    public static int getTicksPerSnowAccumulation()
    {
        return TICKS_PER_SNOW_ACCUMULATION.get();
    }

    public static int getSnowMeltMultiplier()
    {
        return SNOW_MELT_MULTIPLIER.get();
    }

    public static boolean useCurrentRainfallForCrops()
    {
        return CROP_USE_CURRENT_RAINFALL_HYDRATION.get();
    }

    public static boolean useCurrentRainfallForFruit()
    {
        return FRUIT_USE_CURRENT_RAINFALL_HYDRATION.get();
    }

    public static double getMountainCliffHeightMin()
    {
        return MOUNTAIN_CLIFF_HEIGHT_MIN.get();
    }

    public static double getMountainCliffHeightMax()
    {
        return MOUNTAIN_CLIFF_HEIGHT_MAX.get();
    }

    public static double getMountainCliffFadeRatioMin()
    {
        return MOUNTAIN_CLIFF_FADE_RATIO_MIN.get();
    }

    public static double getMountainCliffFadeRatioMax()
    {
        return MOUNTAIN_CLIFF_FADE_RATIO_MAX.get();
    }

    public static boolean isTerrainUpliftEnabled()
    {
        return TERRAIN_UPLIFT_ENABLED.get();
    }

    public static double getTerrainUpliftSourceHeight()
    {
        return TERRAIN_UPLIFT_SOURCE_HEIGHT.get();
    }

    public static int getTerrainUpliftSourceFalloffDistance()
    {
        return TERRAIN_UPLIFT_SOURCE_FALLOFF_DISTANCE.get();
    }

    public static int getTerrainUpliftSmallPlatformRadius()
    {
        return TERRAIN_UPLIFT_SMALL_PLATFORM_RADIUS.get();
    }

    public static double getHeadwaterTransitionBase()
    {
        return worldGenValue(HEADWATER_TRANSITION_BASE);
    }

    public static double getHeadwaterTransitionSlope()
    {
        return worldGenValue(HEADWATER_TRANSITION_SLOPE);
    }

    public static double getHeadwaterTransitionMax()
    {
        return worldGenValue(HEADWATER_TRANSITION_MAX);
    }

    /** Tier index 0 / 1 / 2 maps to the low / mid / high multiplier. */
    public static double getHeadwaterTransitionTier(int tier)
    {
        return switch (tier)
        {
            case 0 -> worldGenValue(HEADWATER_TIER_LOW);
            case 1 -> worldGenValue(HEADWATER_TIER_MID);
            default -> worldGenValue(HEADWATER_TIER_HIGH);
        };
    }

    /**
     * World-generation values are read through the spec; when the spec is not
     * loaded (dedicated unit tests run without a config) the spec's own default is
     * used, so the exercised behaviour is exactly the documented default.
     */
    private static double worldGenValue(ForgeConfigSpec.DoubleValue value)
    {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static int worldGenValue(ForgeConfigSpec.IntValue value)
    {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static boolean worldGenValue(ForgeConfigSpec.BooleanValue value)
    {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static boolean isHeadwaterUndergroundEnabled()
    {
        return worldGenValue(HEADWATER_UNDERGROUND_ENABLED);
    }

    public static int getHeadwaterSinkMinCut()
    {
        return worldGenValue(HEADWATER_SINK_MIN_CUT);
    }

    public static int getHeadwaterSinkMinRun()
    {
        return worldGenValue(HEADWATER_SINK_MIN_RUN);
    }

    public static int getHeadwaterTunnelRoof()
    {
        return worldGenValue(HEADWATER_TUNNEL_ROOF);
    }

    public static int getHeadwaterTunnelAirMin()
    {
        return worldGenValue(HEADWATER_TUNNEL_AIR_MIN);
    }

    public static int getHeadwaterTunnelAirMax()
    {
        return Math.max(getHeadwaterTunnelAirMin(), worldGenValue(HEADWATER_TUNNEL_AIR_MAX));
    }

    public static int getHeadwaterTunnelWaterDepth()
    {
        return worldGenValue(HEADWATER_TUNNEL_WATER_DEPTH);
    }

    public static double getHeadwaterTunnelCarvingNoise()
    {
        return Math.max(0d, worldGenValue(HEADWATER_TUNNEL_CARVING_NOISE));
    }

    public static double getHeadwaterMouthLateralRise()
    {
        return Math.max(0d, worldGenValue(HEADWATER_MOUTH_LATERAL_RISE));
    }

    public static double getHeadwaterMouthNoise()
    {
        return Math.max(0d, worldGenValue(HEADWATER_MOUTH_NOISE));
    }

    public static double getHeadwaterMouthMaxSlope()
    {
        return Math.max(0.05d, worldGenValue(HEADWATER_MOUTH_MAX_SLOPE));
    }

    public static double getHeadwaterMouthMaxLength()
    {
        return Math.max(4d, worldGenValue(HEADWATER_MOUTH_MAX_LENGTH));
    }

}
