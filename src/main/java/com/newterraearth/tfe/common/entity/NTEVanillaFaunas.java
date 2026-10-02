package com.newterraearth.tfe.common.entity;

import com.google.common.collect.BiMap;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.levelgen.Heightmap;

import net.dries007.tfc.common.entities.Fauna;
import net.dries007.tfc.common.entities.aquatic.AquaticMob;
import net.dries007.tfc.common.entities.predator.AmphibiousPredator;
import net.dries007.tfc.util.Helpers;

import com.newterraearth.tfe.mixin.DataManagerAccessor;

public final class NTEVanillaFaunas
{
    private NTEVanillaFaunas()
    {
    }

    @SuppressWarnings("unchecked")
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event)
    {
        final BiMap<ResourceLocation, ?> types = ((DataManagerAccessor<?>) Fauna.MANAGER).tfe$getTypes();
        for (ResourceLocation id : types.keySet())
        {
            if (isCustomEntity(id))
            {
                continue;
            }

            final EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (entityType == null)
            {
                continue;
            }

            registerSpawnPlacement(event, (EntityType<? extends Mob>) entityType);
        }
    }

    private static boolean isCustomEntity(ResourceLocation id)
    {
        if (id.equals(NTEEntities.BISON.getId()) || id.equals(NTEEntities.LEOPARD_SEAL.getId())
            || id.equals(NTEEntities.BACTRIAN_CAMEL.getId()) || id.equals(NTEEntities.DROMEDARY_CAMEL.getId())
            || id.equals(NTEEntities.ARMADILLO.getId()))
        {
            return true;
        }
        return NTEEntities.NEW_FRESHWATER_FISH.values().stream().anyMatch(entity -> id.equals(entity.getId()));
    }

    private static <E extends Mob> void registerSpawnPlacement(SpawnPlacementRegisterEvent event, EntityType<E> type)
    {
        event.register(type, resolvePlacementType(type), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (entityType, level, spawnType, pos, rand) -> {
            final Fauna fauna = NTEFaunaSpawnHelpers.getFauna(entityType);
            if (fauna == null)
            {
                return false;
            }

            if (rand.nextInt(fauna.getChance()) != 0)
            {
                return false;
            }

            if (entityType instanceof AquaticMob aquaticMob && !aquaticMob.canSpawnIn(level.getFluidState(pos).getType()))
            {
                return false;
            }

            final int seaLevel = level.getLevel().getChunkSource().getGenerator().getSeaLevel();
            if (fauna.getDistanceBelowSeaLevel() != -1 && pos.getY() > seaLevel - fauna.getDistanceBelowSeaLevel())
            {
                return false;
            }

            if (!NTEFaunaSpawnHelpers.isClimateValid(fauna, level, pos, rand) || !NTEFaunaSpawnHelpers.isMonthValid(fauna, level, pos))
            {
                return false;
            }

            final BlockPos below = pos.below();
            if (fauna.isSolidGround() && !Helpers.isBlock(level.getBlockState(below), BlockTags.VALID_SPAWN))
            {
                return false;
            }
            return (fauna.getMaxBrightness() == -1 || level.getRawBrightness(pos, 0) <= fauna.getMaxBrightness())
                && NTEFaunaSpawnHelpers.isWaterVolumeValid(entityType, fauna, level, pos, spawnType);
        }, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static SpawnPlacements.Type resolvePlacementType(EntityType<?> entityType)
    {
        final MobCategory category = entityType.getCategory();
        if (category == MobCategory.WATER_AMBIENT
            || category == MobCategory.WATER_CREATURE
            || category == MobCategory.UNDERGROUND_WATER_CREATURE)
        {
            return SpawnPlacements.Type.IN_WATER;
        }

        if (AmphibiousPredator.class.isAssignableFrom(entityType.getBaseClass()))
        {
            return SpawnPlacements.Type.NO_RESTRICTIONS;
        }

        return SpawnPlacements.Type.ON_GROUND;
    }
}
