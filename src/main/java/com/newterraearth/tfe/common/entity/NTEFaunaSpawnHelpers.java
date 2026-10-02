package com.newterraearth.tfe.common.entity;

import java.util.List;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.FluidState;

import net.dries007.tfc.common.entities.EntityHelpers;
import net.dries007.tfc.common.entities.Fauna;
import net.dries007.tfc.util.RegisteredDataManager;
import net.dries007.tfc.util.calendar.Month;
import net.dries007.tfc.world.chunkdata.ChunkData;

import com.newterraearth.tfe.world.NTEClimatePlacementAccess;
import com.newterraearth.tfe.world.NTESeasonalHelpers;

public final class NTEFaunaSpawnHelpers
{
    /**
     * Fallback used when a fauna entry omits {@code min_water_volume}. {@code 0} means "not checked", so the water
     * volume requirement is opt in: only fauna entries that declare the field are restricted.
     */
    public static final int DEFAULT_MIN_WATER_VOLUME = 0;

    private static final Direction[] NEIGHBORS = Direction.values();

    private NTEFaunaSpawnHelpers()
    {
    }

    @Nullable
    public static Fauna getFauna(EntityType<?> entityType)
    {
        final RegisteredDataManager.Entry<Fauna> entry = Fauna.MANAGER.get(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
        return entry != null ? entry.get() : null;
    }

    public static boolean isClimateValid(Fauna fauna, ServerLevelAccessor level, BlockPos pos, RandomSource random)
    {
        final ChunkData data = EntityHelpers.getChunkDataForSpawning(level, pos);
        final ChunkGenerator generator = level.getLevel().getChunkSource().getGenerator();
        if (fauna.getClimate() instanceof NTEClimatePlacementAccess access)
        {
            return access.nte$isValid(data, pos, random, generator, level.getLevel().getSeed());
        }
        return fauna.getClimate().isValid(data, pos, random);
    }

    public static boolean isMonthValid(Fauna fauna, ServerLevelAccessor level, BlockPos pos)
    {
        if (!(fauna instanceof NTEFaunaAccess access))
        {
            return true;
        }

        final List<Month> months = access.tfe$getMonths();
        return months.isEmpty() || months.contains(NTESeasonalHelpers.getHemispheralCalendarMonthOfYear(level.getLevel(), pos));
    }

    /**
     * Requires a natural spawn position to sit in a connected body of water of at least {@code min_water_volume} blocks.
     * The requirement is opt in: a fauna entry that does not declare the field is never checked. The search is a plain
     * 6-neighbour flood fill and it stops as soon as the threshold is reached, so a one block wide channel that reaches
     * open water counts exactly like a large lake. No shape, area or radius rule is applied.
     * <p>
     * Only source like water counts: a cell must be in {@link FluidTags#WATER} and report a water level
     * ({@link FluidState#getAmount()}) of {@code >= 7}, i.e. a source (8), falling water (8) or the single flowing
     * level world generation uses (7). Levels 1..6 only ever appear after generated water leaks into a gap or a
     * player places water, so ignoring them keeps player made spills from passing the threshold while generated
     * rivers, creeks, lakes and oceans are unaffected.
     * <p>
     * Non water spawns, non natural spawn sources (spawners, chunk generation) and a threshold of {@code <= 0} pass
     * immediately.
     */
    public static boolean isWaterVolumeValid(EntityType<?> entityType, Fauna fauna, ServerLevelAccessor level, BlockPos pos, MobSpawnType spawnType)
    {
        if (spawnType != MobSpawnType.NATURAL || SpawnPlacements.getPlacementType(entityType) != SpawnPlacements.Type.IN_WATER)
        {
            return true;
        }

        final int threshold = fauna instanceof NTEFaunaAccess access ? access.tfe$getMinWaterVolume() : DEFAULT_MIN_WATER_VOLUME;
        if (threshold <= 0)
        {
            return true;
        }

        final LongOpenHashSet seen = new LongOpenHashSet(threshold * 2);
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        seen.add(pos.asLong());
        queue.enqueue(pos.asLong());
        int water = 0;

        while (!queue.isEmpty())
        {
            final long packed = queue.dequeueLong();
            if (++water >= threshold)
            {
                return true;
            }

            final int x = BlockPos.getX(packed);
            final int y = BlockPos.getY(packed);
            final int z = BlockPos.getZ(packed);
            for (Direction direction : NEIGHBORS)
            {
                final int nextY = y + direction.getStepY();
                if (nextY < level.getMinBuildHeight() || nextY >= level.getMaxBuildHeight())
                {
                    continue;
                }

                cursor.set(x + direction.getStepX(), nextY, z + direction.getStepZ());
                final long key = cursor.asLong();
                // hasChunkAt is a non loading lookup: chunks outside the loaded area never force a load and never count as water.
                if (seen.contains(key) || !level.hasChunkAt(cursor))
                {
                    continue;
                }

                final FluidState state = level.getFluidState(cursor);
                // getAmount is the public water level accessor: 8 = source or falling, 1..7 = flowing levels.
                if (!state.is(FluidTags.WATER) || state.getAmount() < 7)
                {
                    continue;
                }

                seen.add(key);
                queue.enqueue(key);
            }
        }
        return false;
    }
}
