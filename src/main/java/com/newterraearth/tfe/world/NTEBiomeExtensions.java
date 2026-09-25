package com.newterraearth.tfe.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.world.biome.BiomeBlendType;
import net.dries007.tfc.world.biome.BiomeBuilder;
import net.dries007.tfc.world.biome.BiomeExtension;
import net.dries007.tfc.world.biome.BiomeNoise;
import net.dries007.tfc.world.noise.Noise2D;
import net.dries007.tfc.world.river.RiverBlendType;
import net.dries007.tfc.world.surface.builder.LowlandsSurfaceBuilder;
import net.dries007.tfc.world.surface.builder.NormalSurfaceBuilder;
import net.dries007.tfc.world.surface.builder.SurfaceBuilderFactory;

import com.newterraearth.tfe.world.surface.BurrenSurfaceBuilder;
import com.newterraearth.tfe.world.surface.DuneSurfaceBuilder;
import com.newterraearth.tfe.world.surface.GrassyDunesSurfaceBuilder;
import com.newterraearth.tfe.world.surface.IceSheetSurfaceBuilder;
import com.newterraearth.tfe.world.surface.IceSheetShieldVolcanoSurfaceBuilder;
import com.newterraearth.tfe.world.surface.MudFlatsSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NTEBadlandsSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NTEAtollSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NTECinderConeSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NormalAlternateSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NTERiverSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NTEStratovolcanoSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NTETuffRingsSurfaceBuilder;
import com.newterraearth.tfe.world.surface.NTETuyaSurfaceBuilder;
import com.newterraearth.tfe.world.surface.PatternedGroundSurfaceBuilder;
import com.newterraearth.tfe.world.surface.RockyPlateauSurfaceBuilder;
import com.newterraearth.tfe.world.surface.SaltFlatsSurfaceBuilder;
import com.newterraearth.tfe.world.surface.ShieldVolcanoSurfaceBuilder;
import com.newterraearth.tfe.world.surface.ShilinSurfaceBuilder;
import com.newterraearth.tfe.world.surface.ShorelineSurfaceBuilder;
import com.newterraearth.tfe.world.surface.SimpleSurfaceBuilder;
import com.newterraearth.tfe.world.surface.StoneCirclesSurfaceBuilder;
import com.newterraearth.tfe.world.river.NTERiverBlendType;
import com.newterraearth.tfe.world.shore.NTEShoreBlendType;
import com.newterraearth.tfe.world.volcano.NTECenteredFeatureBlendType;

import static net.dries007.tfc.world.TFCChunkGenerator.SEA_LEVEL_Y;

public final class NTEBiomeExtensions
{
    private NTEBiomeExtensions()
    {
    }

    public static BiomeExtension ocean()
    {
        return build("ocean", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.ocean(BiomeNoise.ocean(seed, -26, -12)))
            .surface(ShorelineSurfaceBuilder.OCEAN)
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers());
    }

    public static BiomeExtension oceanReef()
    {
        return build("ocean_reef", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.ocean(BiomeNoise.ocean(seed, -16, -8)))
            .surface(ShorelineSurfaceBuilder.OCEAN)
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers());
    }

    public static BiomeExtension deepOcean()
    {
        return build("deep_ocean", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.ocean(BiomeNoise.ocean(seed, -46, -30)))
            .surface(ShorelineSurfaceBuilder.OCEAN)
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers());
    }

    public static BiomeExtension deepOceanTrench()
    {
        return build("deep_ocean_trench", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.trench(NTEBiomeNoise.oceanTrench(seed, -60, -46)))
            .surface(ShorelineSurfaceBuilder.OCEAN)
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers());
    }

    public static BiomeExtension oceanicVolcanicArc()
    {
        return setCenteredFeatureFrequencyMetadata(build("oceanic_volcanic_arc", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.ocean(BiomeNoise.ocean(seed, -26, -12)))
            .surface(stratovolcanoes(ShorelineSurfaceBuilder.OCEAN))
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.7f, SEA_LEVEL_Y, -12, 200, false);
    }

    public static BiomeExtension oceanAtolls()
    {
        return setCenteredFeatureMetadata(build("ocean_atolls", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.ocean(BiomeNoise.ocean(seed, -26, -12)))
            .surface(atolls(ShorelineSurfaceBuilder.OCEAN))
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers()), NTECenteredFeatureBlendType.ATOLL, 8, SEA_LEVEL_Y - 3, -4, 11, false);
    }

    public static BiomeExtension deepOceanAtolls()
    {
        return setCenteredFeatureMetadata(build("deep_ocean_atolls", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.ocean(BiomeNoise.ocean(seed, -46, -30)))
            .surface(atolls(ShorelineSurfaceBuilder.OCEAN))
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers()), NTECenteredFeatureBlendType.ATOLL, 8, SEA_LEVEL_Y - 3, -4, 11, false);
    }

    public static BiomeExtension oceanRidge()
    {
        return build("ocean_ridge", BiomeBuilder.builder()
            .noise(seed -> NTEOceanDepthSampler.ocean(NTEBiomeNoise.oceanRidge(seed)))
            .surface(ShorelineSurfaceBuilder.OCEAN_RIDGE)
            .aquiferHeightOffset(-24)
            .salty()
            .type(BiomeBlendType.OCEAN)
            .noRivers());
    }

    public static BiomeExtension riftValley()
    {
        return setCenteredFeatureMetadata(build("rift_valley", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.riftValley(seed, 2, 25, false))
            .surface(cinder(SimpleSurfaceBuilder.VOLCANIC_SOIL))
            .spawnable()
            .type(RiverBlendType.CAVE)), NTECenteredFeatureBlendType.CINDER_CONE, 7, SEA_LEVEL_Y + 20, 0, 28, false);
    }

    public static BiomeExtension riftLake()
    {
        return build("rift_lake", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.riftValley(seed, -10, 25, true))
            .surface(NormalSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .type(RiverBlendType.CAVE)
            .type(BiomeBlendType.LAKE)
            .noRivers());
    }

    public static BiomeExtension riverValley()
    {
        return setRiverMetadata(build("river_valley", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -2, 4))
            .surface(NormalAlternateSurfaceBuilder.SANDY)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.WIDE)), NTERiverBlendType.FLOODPLAIN);
    }

    public static BiomeExtension volcanicMountainIslands()
    {
        return setCenteredFeatureMetadata(build("volcanic_mountain_islands", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.mountains(seed, -24, 50))
            .surface(cinder(ShorelineSurfaceBuilder.VOLCANIC_MOUNTAINS))
            .aquiferHeightOffset(-8)
            .salty()
            .type(RiverBlendType.CAVE)), NTECenteredFeatureBlendType.CINDER_CONE, 2, SEA_LEVEL_Y + 15, 7, 35, false);
    }

    public static BiomeExtension volcanicIsland()
    {
        return setCenteredFeatureFrequencyMetadata(build("volcanic_island", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -5, 28))
            .surface(stratovolcanoes(ShorelineSurfaceBuilder.VOLCANIC_MOUNTAINS))
            .aquiferHeightOffset(-8)
            .salty()
            .type(RiverBlendType.CAVE)), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y, 0, 200, false);
    }

    public static BiomeExtension plains()
    {
        return setRiverMetadata(build("plains", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 4, 10))
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE)), NTERiverBlendType.FLOODPLAIN);
    }

    public static BiomeExtension hills()
    {
        return setRiverMetadata(build("hills", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -5, 16))
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE)), NTERiverBlendType.FLOODPLAIN);
    }

    public static BiomeExtension lowlands()
    {
        return setRiverMetadata(build("lowlands", BiomeBuilder.builder()
            .heightmap(BiomeNoise::lowlands)
            .surface(LowlandsSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.WIDE)
            .noSandyRiverShores()), NTERiverBlendType.BANKED);
    }

    public static BiomeExtension saltMarsh()
    {
        return setRiverMetadata(build("salt_marsh", BiomeBuilder.builder()
            .heightmap(BiomeNoise::lowlands)
            .surface(LowlandsSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .spawnable()
            .salty()
            .type(RiverBlendType.WIDE)
            .noSandyRiverShores()), NTERiverBlendType.BANKED);
    }

    public static BiomeExtension lowCanyons()
    {
        return build("low_canyons", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.canyons(seed, -8, 21))
            .surface(NormalSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.WIDE)
            .noSandyRiverShores());
    }

    public static BiomeExtension highlands()
    {
        return build("highlands", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::sharpHills)
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CANYON));
    }

    public static BiomeExtension badlands()
    {
        return build("badlands", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::badlands)
            .surface(NTEBadlandsSurfaceBuilder.NORMAL)
            .spawnable()
            .type(RiverBlendType.CANYON));
    }

    public static BiomeExtension plateau()
    {
        return build("plateau", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 20, 30))
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension plateauWide()
    {
        return setRiverMetadata(build("plateau_wide", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 20, 30))
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores()), NTERiverBlendType.TALUS);
    }

    public static BiomeExtension canyons()
    {
        return setCenteredFeatureMetadata(build("canyons", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.canyons(seed, -2, 40))
            .surface(cinder(SimpleSurfaceBuilder.VOLCANIC_SOIL))
            .spawnable()
            .type(RiverBlendType.CANYON)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.CINDER_CONE, 6, SEA_LEVEL_Y + 28, 14, 30, false);
    }

    public static BiomeExtension mountains()
    {
        return build("mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.ridgeMountains(seed, 10, 90, 0.4f, 140, 40))
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CAVE));
    }

    public static BiomeExtension collisionalMountains()
    {
        return build("collisional_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.ridgeMountains(seed, 18, 130, 0.3f, 170, 35))
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CAVE));
    }

    public static BiomeExtension oldMountains()
    {
        return build("old_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.mountains(seed, 16, 40))
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CAVE));
    }

    public static BiomeExtension oceanicMountains()
    {
        return build("oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.mountains(seed, -16, 60))
            .surface(ShorelineSurfaceBuilder.MOUNTAINS)
            .aquiferHeightOffset(-8)
            .salty()
            .spawnable()
            .type(RiverBlendType.CAVE));
    }

    public static BiomeExtension volcanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("volcanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.ridgeMountains(seed, 10, 80, 0.4f, 130, 40))
            .surface(stratovolcanoes(SimpleSurfaceBuilder.ROCKY_VOLCANIC_SOIL))
            .type(RiverBlendType.CAVE)), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y + 12, 12, 200, false);
    }

    public static BiomeExtension volcanicOceanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("volcanic_oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.mountains(seed, -24, 50))
            .surface(stratovolcanoes(ShorelineSurfaceBuilder.VOLCANIC_MOUNTAINS))
            .aquiferHeightOffset(-8)
            .salty()
            .type(RiverBlendType.CAVE)), NTECenteredFeatureBlendType.STRATOVOLCANO, 1f, SEA_LEVEL_Y, 0, 200, false);
    }

    public static BiomeExtension shore()
    {
        return buildShore("shore", BiomeBuilder.builder()
            .heightmap(BiomeNoise::shore)
            .surface(ShorelineSurfaceBuilder.SANDY)
            .aquiferHeightOffset(-16)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.WIDE)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.SANDY, -4);
    }

    public static BiomeExtension river()
    {
        return build("river", BiomeBuilder.builder()
            .surface(NTERiverSurfaceBuilder.INSTANCE));
    }

    public static BiomeExtension tidalFlats()
    {
        return buildShore("tidal_flats", BiomeBuilder.builder()
            .heightmap(BiomeNoise::shore)
            .surface(ShorelineSurfaceBuilder.SANDY)
            .aquiferHeightOffset(-16)
            .type(BiomeBlendType.OCEAN)
            .salty()
            .shore()
            .type(RiverBlendType.WIDE)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.SANDY, -4);
    }

    public static BiomeExtension guanoIsland()
    {
        return build("guano_island", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::rockyIslands)
            .surface(ShorelineSurfaceBuilder.ROCKY_SHORE)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension seaStacks()
    {
        return buildShore("sea_stacks", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 10, 30))
            .surface(ShorelineSurfaceBuilder.SEA_CLIFFS)
            .aquiferHeightOffset(-40)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.TALL_CANYON)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.SEA_STACKS, -6);
    }

    public static BiomeExtension terraceUpper()
    {
        return buildShore("terrace_upper", BiomeBuilder.builder()
            .heightmap(seed -> constant(0))
            .surface(ShorelineSurfaceBuilder.TERRACE_CLIFFS)
            .aquiferHeightOffset(-40)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.TALL_CANYON)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.UPPER_TERRACE);
    }

    public static BiomeExtension terraceLower()
    {
        return buildShore("terrace_lower", BiomeBuilder.builder()
            .heightmap(seed -> constant(0))
            .surface(ShorelineSurfaceBuilder.TERRACE_CLIFFS)
            .aquiferHeightOffset(-40)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.TALL_CANYON)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.LOWER_TERRACE);
    }

    public static BiomeExtension setbackCliffs()
    {
        return buildShore("setback_cliffs", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 20, 30))
            .surface(ShorelineSurfaceBuilder.SANDY)
            .aquiferHeightOffset(-40)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.CANYON)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.SETBACK_CLIFFS);
    }

    public static BiomeExtension coastalDunes()
    {
        return setRiverMetadata(buildShore("coastal_dunes", BiomeBuilder.builder()
            .heightmap(seed -> constant(0))
            .surface(ShorelineSurfaceBuilder.SANDY)
            .aquiferHeightOffset(-40)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.WIDE)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.DUNES), NTERiverBlendType.WIDE_DEEP);
    }

    public static BiomeExtension rockyShores()
    {
        return buildShore("rocky_shores", BiomeBuilder.builder()
            .heightmap(seed -> constant(-15))
            .surface(ShorelineSurfaceBuilder.ROCKY_SHORE)
            .aquiferHeightOffset(-40)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.CANYON)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.ROCKY_SHORES);
    }

    public static BiomeExtension embayments()
    {
        return buildShore("embayments", BiomeBuilder.builder()
            .heightmap(BiomeNoise::shore)
            .surface(ShorelineSurfaceBuilder.SEA_CLIFFS)
            .aquiferHeightOffset(-40)
            .type(BiomeBlendType.LAND)
            .salty()
            .shore()
            .type(RiverBlendType.CANYON)
            .noRivers()
            .noSandyRiverShores(), NTEShoreBlendType.EMBAYMENTS);
    }

    public static BiomeExtension mountainLake()
    {
        return build("mountain_lake", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.ridgeMountains(seed, 10, 90, 0.4f, 140, 40))
            .surface(NormalSurfaceBuilder.ROCKY)
            .carving(NTEBiomeNoise::undergroundLakes)
            .type(BiomeBlendType.LAKE)
            .noRivers());
    }

    public static BiomeExtension oldMountainLake()
    {
        return build("old_mountain_lake", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.mountains(seed, -16, 60))
            .surface(NormalSurfaceBuilder.ROCKY)
            .carving(NTEBiomeNoise::undergroundLakes)
            .type(BiomeBlendType.LAKE)
            .noRivers());
    }

    public static BiomeExtension oceanicMountainLake()
    {
        return build("oceanic_mountain_lake", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.mountains(seed, -16, 60))
            .surface(ShorelineSurfaceBuilder.MOUNTAINS)
            .carving(NTEBiomeNoise::undergroundLakes)
            .salty()
            .type(BiomeBlendType.LAKE)
            .noRivers());
    }

    public static BiomeExtension volcanicMountainLake()
    {
        return setCenteredFeatureFrequencyMetadata(build("volcanic_mountain_lake", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.ridgeMountains(seed, 10, 80, 0.4f, 130, 40))
            .surface(stratovolcanoes(SimpleSurfaceBuilder.ROCKY_VOLCANIC_SOIL))
            .carving(NTEBiomeNoise::undergroundLakes)
            .type(BiomeBlendType.LAKE)
            .noRivers()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y + 12, 12, 200, false);
    }

    public static BiomeExtension volcanicOceanicMountainLake()
    {
        return setCenteredFeatureFrequencyMetadata(build("volcanic_oceanic_mountain_lake", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.mountains(seed, -24, 50))
            .surface(stratovolcanoes(ShorelineSurfaceBuilder.VOLCANIC_MOUNTAINS))
            .carving(NTEBiomeNoise::undergroundLakes)
            .salty()
            .type(BiomeBlendType.LAKE)
            .noRivers()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y, 0, 200, false);
    }

    public static BiomeExtension plateauLake()
    {
        return build("plateau_lake", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 20, 30))
            .surface(NormalSurfaceBuilder.INSTANCE)
            .carving(NTEBiomeNoise::undergroundLakes)
            .type(BiomeBlendType.LAKE)
            .noRivers());
    }

    public static BiomeExtension mudFlats()
    {
        return setRiverMetadata(build("mud_flats", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::flats)
            .surface(MudFlatsSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.WIDE)
            .noSandyRiverShores()), NTERiverBlendType.TALL_BANKED);
    }

    public static BiomeExtension saltFlats()
    {
        return setRiverMetadata(build("salt_flats", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::saltFlats)
            .surface(SaltFlatsSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .salty()
            .spawnable()
            .type(RiverBlendType.WIDE)
            .noSandyRiverShores()), NTERiverBlendType.TALL_BANKED);
    }

    public static BiomeExtension duneSea()
    {
        return build("dune_sea", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.dunes(seed, 2, 16))
            .surface(DuneSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension grassyDunes()
    {
        return build("grassy_dunes", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.dunes(seed, 2, 16))
            .surface(GrassyDunesSurfaceBuilder.INSTANCE)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension whorledCanyons()
    {
        return build("whorled_canyons", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.canyons(seed, 8, 60))
            .surface(NTEBadlandsSurfaceBuilder.WARPED)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON));
    }

    public static BiomeExtension stairStepCanyons()
    {
        return setRiverMetadata(build("stair_step_canyons", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::stairCanyons)
            .surface(NTEBadlandsSurfaceBuilder.MESAS)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)), NTERiverBlendType.TERRACES);
    }

    public static BiomeExtension mesas()
    {
        return setRiverMetadata(build("mesas", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::mesas)
            .surface(NTEBadlandsSurfaceBuilder.MESAS)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)), NTERiverBlendType.TERRACES);
    }

    public static BiomeExtension buttes()
    {
        return setRiverMetadata(build("buttes", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::buttes)
            .surface(NTEBadlandsSurfaceBuilder.MESAS)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)), NTERiverBlendType.TERRACES);
    }

    public static BiomeExtension hoodoos()
    {
        return setRiverMetadata(build("hoodoos", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::hoodoos)
            .surface(NTEBadlandsSurfaceBuilder.HOODOOS)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)), NTERiverBlendType.TERRACES);
    }

    public static BiomeExtension rockyPlateau()
    {
        return setRiverMetadata(build("rocky_plateau", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::rockyPlateau)
            .surface(RockyPlateauSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores()), NTERiverBlendType.TALUS);
    }

    public static BiomeExtension towerKarstPlains()
    {
        return build("tower_karst_plains", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::towerKarstPlains)
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON));
    }

    public static BiomeExtension towerKarstCanyons()
    {
        return build("tower_karst_canyons", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::towerKarstCanyons)
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension towerKarstHills()
    {
        return build("tower_karst_hills", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::towerKarstHills)
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON));
    }

    public static BiomeExtension towerKarstHighlands()
    {
        return build("tower_karst_highlands", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::towerKarstHighlands)
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension towerKarstLake()
    {
        return build("tower_karst_lake", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::towerKarstLake)
            .surface(NormalSurfaceBuilder.ROCKY)
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .type(BiomeBlendType.LAKE)
            .noSandyRiverShores());
    }

    public static BiomeExtension towerKarstBay()
    {
        return build("tower_karst_bay", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::towerKarstBay)
            .surface(NormalSurfaceBuilder.ROCKY)
            .aquiferHeightOffset(-16)
            .salty()
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension burrenPlateau()
    {
        return build("burren_plateau", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::burrenPlateau)
            .surface(BurrenSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension burrenBadlands()
    {
        return build("burren_badlands", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::burrenBadlands)
            .surface(BurrenSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension burrenBadlandsTall()
    {
        return build("burren_badlands_tall", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::burrenBadlandsTall)
            .surface(BurrenSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension burrenRocheMoutonee()
    {
        return build("burren_roche_moutonee", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::burrenRocheMoutonee)
            .surface(BurrenSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE)
            .noSandyRiverShores());
    }

    public static BiomeExtension burrenPlains()
    {
        return build("burren_plains", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::burrenPlains)
            .surface(BurrenSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE)
            .noSandyRiverShores());
    }

    public static BiomeExtension shilinPlains()
    {
        return build("shilin_plains", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::shilinPlains)
            .surface(ShilinSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension shilinCanyons()
    {
        return build("shilin_canyons", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::shilinCanyons)
            .surface(ShilinSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension shilinHills()
    {
        return build("shilin_hills", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::shilinHills)
            .surface(ShilinSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension shilinHighlands()
    {
        return build("shilin_highlands", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::shilinHighlands)
            .surface(ShilinSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension shilinPlateau()
    {
        return build("shilin_plateau", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::shilinPlateau)
            .surface(ShilinSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension dolinePlains()
    {
        return setRiverMetadata(build("doline_plains", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::dolinePlains)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE)), NTERiverBlendType.FLOODPLAIN);
    }

    public static BiomeExtension dolineHills()
    {
        return build("doline_hills", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::dolineHills)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension dolineRollingHills()
    {
        return build("doline_rolling_hills", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::dolineRollingHills)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.CANYON));
    }

    public static BiomeExtension dolineHighlands()
    {
        return build("doline_highlands", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::dolineHighlands)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.CANYON));
    }

    public static BiomeExtension dolinePlateau()
    {
        return build("doline_plateau", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::dolinePlateau)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension dolineCanyons()
    {
        return setCenteredFeatureMetadata(build("doline_canyons", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::dolineCanyons)
            .surface(cinder(SimpleSurfaceBuilder.VOLCANIC_SOIL))
            .spawnable()
            .type(RiverBlendType.CANYON)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.CINDER_CONE, 6, SEA_LEVEL_Y + 28, 14, 30, false);
    }

    public static BiomeExtension cenotePlains()
    {
        return setRiverMetadata(build("cenote_plains", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 4, 10))
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE)), NTERiverBlendType.FLOODPLAIN);
    }

    public static BiomeExtension cenoteHills()
    {
        return build("cenote_hills", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -5, 16))
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension cenoteRollingHills()
    {
        return build("cenote_rolling_hills", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -5, 28))
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.CANYON));
    }

    public static BiomeExtension cenoteCanyons()
    {
        return build("cenote_canyons", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.canyons(seed, 2, 28))
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension cenoteHighlands()
    {
        return build("cenote_highlands", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.sharpHills(seed, 0, 24))
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension cenotePlateau()
    {
        return build("cenote_plateau", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, 20, 30))
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension extremeDolinePlateau()
    {
        return build("extreme_doline_plateau", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::mogotePlateau)
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON));
    }

    public static BiomeExtension extremeDolineMountains()
    {
        return build("extreme_doline_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.tiankeng(seed, NTEBiomeNoise.mountains(seed, 16, 40, 1.3f)))
            .carving(NTEBiomeNoise::cenotes)
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CAVE));
    }

    public static BiomeExtension activeShieldVolcano()
    {
        return setCenteredFeatureMetadata(build("active_shield_volcano", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::activeShieldVolcano)
            .surface(cinder(ShieldVolcanoSurfaceBuilder.ACTIVE))
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.CINDER_CONE, 4, SEA_LEVEL_Y + 28, 15, 25, false);
    }

    public static BiomeExtension dormantShieldVolcano()
    {
        return setCenteredFeatureMetadata(build("dormant_shield_volcano", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::dormantShieldVolcano)
            .surface(tuffRings(ShieldVolcanoSurfaceBuilder.DORMANT))
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.TUFF_RING, 2, 0, 0, 36, false);
    }

    public static BiomeExtension extinctShieldVolcano()
    {
        return setCenteredFeatureMetadata(build("extinct_shield_volcano", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::extinctShieldVolcano)
            .surface(tuffRings(ShieldVolcanoSurfaceBuilder.DORMANT))
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.TUFF_RING, 2, 0, 0, 26, false);
    }

    public static BiomeExtension ancientShieldVolcano()
    {
        return setCenteredFeatureMetadata(build("ancient_shield_volcano", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::ancientShieldVolcano)
            .surface(tuffRings(ShieldVolcanoSurfaceBuilder.DORMANT))
            .aquiferHeightOffset(-16)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.TUFF_RING, 3, 0, -16, 30, false);
    }

    public static BiomeExtension sunkenShieldVolcano()
    {
        return setCenteredFeatureMetadata(build("sunken_shield_volcano", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::sunkenShieldVolcano)
            .surface(tuffRings(ShieldVolcanoSurfaceBuilder.DORMANT))
            .aquiferHeightOffset(-16)
            .salty()
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.TUFF_RING, 2, 0, -8, 24, false);
    }

    public static BiomeExtension shieldVolcanoShore()
    {
        return buildShore("shield_volcano_shore", BiomeBuilder.builder()
            .heightmap(BiomeNoise::shore)
            .surface(ShorelineSurfaceBuilder.ACTIVE_SHIELD_VOLCANO)
            .spawnable()
            .salty()
            .shore()
            .type(RiverBlendType.TALL_CANYON), NTEShoreBlendType.CLASSIC);
    }

    public static BiomeExtension oldShieldVolcanoShore()
    {
        return setCenteredFeatureMetadata(buildShore("old_shield_volcano_shore", BiomeBuilder.builder()
            .heightmap(BiomeNoise::shore)
            .surface(tuffRings(ShorelineSurfaceBuilder.OLD_SHIELD_VOLCANO))
            .spawnable()
            .salty()
            .shore()
            .type(RiverBlendType.TALL_CANYON), NTEShoreBlendType.SANDY), NTECenteredFeatureBlendType.TUFF_RING, 3, 0, -8, 26, false);
    }

    public static BiomeExtension iceSheet()
    {
        return build("ice_sheet", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::iceSheet)
            .surface(IceSheetSurfaceBuilder.NORMAL)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension iceSheetMountains()
    {
        return build("ice_sheet_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.iceSheetMountains(seed, false))
            .surface(IceSheetSurfaceBuilder.ICE_SHEET_MOUNTAINS)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension iceSheetOceanicMountains()
    {
        return build("ice_sheet_oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.iceSheetMountains(seed, true))
            .surface(IceSheetSurfaceBuilder.ICE_SHEET_OCEANIC_MOUNTAINS)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension iceSheetVolcanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("ice_sheet_volcanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.iceSheetMountains(seed, false))
            .surface(stratovolcanoes(IceSheetSurfaceBuilder.ICE_SHEET_VOLCANIC_MOUNTAINS))
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y + 12, 12, 200, true);
    }

    public static BiomeExtension iceSheetVolcanicOceanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("ice_sheet_volcanic_oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.iceSheetMountains(seed, true))
            .surface(stratovolcanoes(IceSheetSurfaceBuilder.ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS))
            .aquiferHeightOffset(-24)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y, 0, 200, true);
    }

    public static BiomeExtension iceSheetShieldVolcano()
    {
        return build("ice_sheet_shield_volcano", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::iceSheetShieldVolcanoTerrain)
            .surface(IceSheetShieldVolcanoSurfaceBuilder.ICE_SHEET)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension iceSheetTuyas()
    {
        return setCenteredFeatureMetadata(build("ice_sheet_tuyas", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::iceSheet)
            .surface(tuyas(IceSheetSurfaceBuilder.NORMAL))
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.TUYA, 3, SEA_LEVEL_Y - 6, 0, 35, true);
    }

    public static BiomeExtension subglacialLake()
    {
        return build("subglacial_lake", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::iceSheet)
            .surface(IceSheetSurfaceBuilder.HIDDEN_LAKE)
            .carving(NTEBiomeNoise::undergroundLakes)
            .type(BiomeBlendType.LAKE)
            .noRivers());
    }

    public static BiomeExtension iceSheetEdge()
    {
        return build("ice_sheet_edge", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::iceSheetEdge)
            .surface(IceSheetSurfaceBuilder.EDGE)
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores());
    }

    public static BiomeExtension iceSheetTuyasEdge()
    {
        return setCenteredFeatureMetadata(build("ice_sheet_tuyas_edge", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::iceSheetEdge)
            .surface(tuyas(IceSheetSurfaceBuilder.EDGE))
            .spawnable()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.TUYA, 3, SEA_LEVEL_Y - 6, 0, 35, true);
    }

    public static BiomeExtension iceSheetOceanic()
    {
        return build("ice_sheet_oceanic", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::iceSheetOceanic)
            .surface(IceSheetSurfaceBuilder.OCEANIC)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension iceSheetOceanicMountainsEdge()
    {
        return buildShore("ice_sheet_oceanic_mountains_edge", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciatedMountains(seed, true))
            .surface(IceSheetSurfaceBuilder.ICE_SHEET_OCEANIC_MOUNTAINS)
            .aquiferHeightOffset(-24)
            .spawnable()
            .salty()
            .shore()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores(), NTEShoreBlendType.CLASSIC, -16);
    }

    public static BiomeExtension iceSheetMountainsEdge()
    {
        return build("ice_sheet_mountains_edge", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciatedMountains(seed, false))
            .surface(IceSheetSurfaceBuilder.MOUNTAINS)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension glaciatedMountains()
    {
        return build("glaciated_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciatedMountains(seed, false))
            .surface(IceSheetSurfaceBuilder.GLACIATED_MOUNTAINS)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension glaciatedOceanicMountains()
    {
        return build("glaciated_oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciatedMountains(seed, true))
            .surface(IceSheetSurfaceBuilder.GLACIATED_OCEANIC_MOUNTAINS)
            .aquiferHeightOffset(-24)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension glaciatedVolcanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("glaciated_volcanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciatedMountains(seed, false))
            .surface(stratovolcanoes(IceSheetSurfaceBuilder.GLACIATED_VOLCANIC_MOUNTAINS))
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y + 12, 12, 200, true);
    }

    public static BiomeExtension glaciatedVolcanicOceanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("glaciated_volcanic_oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciatedMountains(seed, true))
            .surface(stratovolcanoes(IceSheetSurfaceBuilder.GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS))
            .aquiferHeightOffset(-24)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y, 0, 200, true);
    }

    public static BiomeExtension meltwaterLake()
    {
        return buildShore("meltwater_lake", BiomeBuilder.builder()
            .heightmap(BiomeNoise::lake)
            .surface(IceSheetSurfaceBuilder.EDGE_LAKE)
            .aquiferHeightOffset(-16)
            .type(BiomeBlendType.LAKE)
            .type(RiverBlendType.WIDE)
            .noRivers()
            .shore(), NTEShoreBlendType.CLASSIC, -16);
    }

    public static BiomeExtension glaciatedShieldVolcano()
    {
        return build("glaciated_shield_volcano", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::glaciatedShieldVolcanoTerrain)
            .surface(IceSheetShieldVolcanoSurfaceBuilder.GLACIATED)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension iceSheetShore()
    {
        return buildShore("ice_sheet_shore", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.ocean(seed, -16, -8))
            .surface(IceSheetSurfaceBuilder.OCEANIC)
            .aquiferHeightOffset(-24)
            .spawnable()
            .salty()
            .shore()
            .type(RiverBlendType.TALL_CANYON)
            .noSandyRiverShores(), NTEShoreBlendType.CLASSIC, -12);
    }

    public static BiomeExtension glaciallyCarvedMountains()
    {
        return build("glacially_carved_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciallyCarvedMountains(seed, false))
            .surface(NormalSurfaceBuilder.ROCKY)
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension glaciallyCarvedOceanicMountains()
    {
        return build("glacially_carved_oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciallyCarvedMountains(seed, true))
            .surface(NormalSurfaceBuilder.ROCKY)
            .aquiferHeightOffset(-24)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores());
    }

    public static BiomeExtension glaciallyCarvedVolcanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("glacially_carved_volcanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciallyCarvedMountains(seed, false))
            .surface(stratovolcanoes(SimpleSurfaceBuilder.ROCKY_VOLCANIC_SOIL))
            .spawnable()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y + 12, 12, 200, true);
    }

    public static BiomeExtension glaciallyCarvedVolcanicOceanicMountains()
    {
        return setCenteredFeatureFrequencyMetadata(build("glacially_carved_volcanic_oceanic_mountains", BiomeBuilder.builder()
            .heightmap(seed -> NTEBiomeNoise.glaciallyCarvedMountains(seed, true))
            .surface(stratovolcanoes(SimpleSurfaceBuilder.ROCKY_VOLCANIC_SOIL))
            .aquiferHeightOffset(-24)
            .spawnable()
            .salty()
            .type(RiverBlendType.CAVE)
            .noSandyRiverShores()), NTECenteredFeatureBlendType.STRATOVOLCANO, 0.8f, SEA_LEVEL_Y, 0, 200, true);
    }

    public static BiomeExtension drumlins()
    {
        return build("drumlins", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::drumlins)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension tuyas()
    {
        return setCenteredFeatureMetadata(build("tuyas", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::drumlins)
            .surface(tuyas(NormalSurfaceBuilder.INSTANCE))
            .spawnable()
            .type(RiverBlendType.WIDE)
            .type(RiverBlendType.CANYON)), NTECenteredFeatureBlendType.TUYA, 2, SEA_LEVEL_Y - 6, 0, 35, false);
    }

    public static BiomeExtension knobAndKettle()
    {
        return build("knob_and_kettle", BiomeBuilder.builder()
            .heightmap(NTEBiomeNoise::knobAndKettle)
            .surface(NormalSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension patternedGround()
    {
        return build("patterned_ground", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -4, 3).add(NTEBiomeNoise.patternedGround(seed)))
            .surface(PatternedGroundSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension invertedPatternedGround()
    {
        return build("inverted_patterned_ground", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -4, 3).add(NTEBiomeNoise.invertedPatternedGround(seed)))
            .surface(PatternedGroundSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    public static BiomeExtension stoneCircles()
    {
        return build("stone_circles", BiomeBuilder.builder()
            .heightmap(seed -> BiomeNoise.hills(seed, -2, 4).add(NTEBiomeNoise.stoneCircles(seed)))
            .surface(StoneCirclesSurfaceBuilder.INSTANCE)
            .spawnable()
            .type(RiverBlendType.WIDE));
    }

    private static BiomeExtension build(String name, BiomeBuilder builder)
    {
        final ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Helpers.identifier(name));
        return setRiverMetadata(builder.build(key), null);
    }

    private static BiomeExtension buildShore(String name, BiomeBuilder builder, NTEShoreBlendType blendType)
    {
        return setShoreMetadata(build(name, builder), blendType, SEA_LEVEL_Y);
    }

    private static BiomeExtension buildShore(String name, BiomeBuilder builder, NTEShoreBlendType blendType, int shoreBaseHeightOffset)
    {
        return setShoreMetadata(build(name, builder), blendType, SEA_LEVEL_Y + shoreBaseHeightOffset);
    }

    private static BiomeExtension setShoreMetadata(BiomeExtension extension, NTEShoreBlendType blendType, int shoreBaseHeight)
    {
        final NTEBiomeExtensionAccess access = (NTEBiomeExtensionAccess) (Object) extension;
        access.tfe$setShoreBlendType(blendType);
        access.tfe$setShoreBaseHeight(shoreBaseHeight);
        return extension;
    }

    private static BiomeExtension setRiverMetadata(BiomeExtension extension, NTERiverBlendType blendType)
    {
        final NTEBiomeExtensionAccess access = (NTEBiomeExtensionAccess) (Object) extension;
        access.tfe$setRiverBlendType(blendType != null ? blendType : NTERiverBlendType.fromLegacy(extension.riverBlendType()));
        return extension;
    }

    private static BiomeExtension setCenteredFeatureMetadata(BiomeExtension extension, NTECenteredFeatureBlendType blendType, int rarity, int rockHeight, int baseHeight, int scaleHeight, boolean icy)
    {
        final NTEBiomeExtensionAccess access = (NTEBiomeExtensionAccess) (Object) extension;
        access.tfe$setCenteredFeatureBlendType(blendType);
        access.tfe$setCenteredFeatureRarity(rarity);
        access.tfe$setCenteredFeatureFrequency(rarity > 0 ? 1f / rarity : 0f);
        access.tfe$setCenteredFeatureRockHeight(rockHeight);
        access.tfe$setCenteredFeatureBaseHeight(baseHeight);
        access.tfe$setCenteredFeatureScaleHeight(scaleHeight);
        access.tfe$setCenteredFeatureIce(icy);
        return extension;
    }

    private static BiomeExtension setCenteredFeatureFrequencyMetadata(BiomeExtension extension, NTECenteredFeatureBlendType blendType, float frequency, int rockHeight, int baseHeight, int scaleHeight, boolean icy)
    {
        final BiomeExtension result = setCenteredFeatureMetadata(extension, blendType, Math.max(1, Math.round(1f / Math.max(0.0001f, frequency))), rockHeight, baseHeight, scaleHeight, icy);
        ((NTEBiomeExtensionAccess) (Object) result).tfe$setCenteredFeatureFrequency(frequency);
        return result;
    }

    private static SurfaceBuilderFactory cinder(SurfaceBuilderFactory parent)
    {
        return NTECinderConeSurfaceBuilder.create(parent);
    }

    private static SurfaceBuilderFactory tuffRings(SurfaceBuilderFactory parent)
    {
        return NTETuffRingsSurfaceBuilder.create(parent);
    }

    private static SurfaceBuilderFactory tuyas(SurfaceBuilderFactory parent)
    {
        return NTETuyaSurfaceBuilder.create(parent);
    }

    private static SurfaceBuilderFactory atolls(SurfaceBuilderFactory parent)
    {
        return NTEAtollSurfaceBuilder.create(parent);
    }

    private static SurfaceBuilderFactory stratovolcanoes(SurfaceBuilderFactory parent)
    {
        return NTEStratovolcanoSurfaceBuilder.create(parent);
    }

    private static Noise2D constant(int height)
    {
        return (x, z) -> SEA_LEVEL_Y + height;
    }
}
