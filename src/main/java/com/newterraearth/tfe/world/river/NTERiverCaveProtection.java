package com.newterraearth.tfe.world.river;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import com.newterraearth.tfe.config.NTECommonConfig;

/**
 * Preserves the shallow terrain shell around supplemental creeks while cave
 * noise and carvers run. The protected band is deliberately bounded above and
 * below the creek bed so deep caves remain untouched.
 */
public final class NTERiverCaveProtection
{
    private static final int BANK_BUFFER = 10;
    private static final int SEARCH_BUFFER = 16;
    private static final int MIN_SUPPORT_DEPTH = 1;
    private static final int MAX_SUPPORT_DEPTH = 3;
    private static final int ABOVE_BED_MARGIN = 2;

    private static final ThreadLocal<Geometry> ACTIVE_DENSITY = new ThreadLocal<>();
    private static final ThreadLocal<Protection> ACTIVE_CARVER = new ThreadLocal<>();

    private record CoreColumn(int x, int z, double centerBedY, double radius, int topY) {}

    private record Protection(ChunkPos chunkPos, int[] minimumProtectedY, int[] maximumProtectedY)
    {
        boolean protects(int blockX, int y, int blockZ)
        {
            if (blockX < chunkPos.getMinBlockX() || blockX > chunkPos.getMaxBlockX()
                || blockZ < chunkPos.getMinBlockZ() || blockZ > chunkPos.getMaxBlockZ())
            {
                return false;
            }
            final int localX = blockX - chunkPos.getMinBlockX();
            final int localZ = blockZ - chunkPos.getMinBlockZ();
            final int index = localX + 16 * localZ;
            return y >= minimumProtectedY[index] && y <= maximumProtectedY[index];
        }
    }

    public static final class DensityScope implements AutoCloseable
    {
        private boolean closed;

        private DensityScope() {}

        @Override
        public void close()
        {
            if (!closed)
            {
                ACTIVE_DENSITY.remove();
                closed = true;
            }
        }
    }

    public static final class Scope implements AutoCloseable
    {
        private boolean closed;

        private Scope() {}

        @Override
        public void close()
        {
            if (!closed)
            {
                ACTIVE_CARVER.remove();
                closed = true;
            }
        }
    }

    public static final class Geometry
    {
        private final ChunkPos chunkPos;
        private final int[] minimumProtectedY = new int[16 * 16];
        private final int[] maximumBedY = new int[16 * 16];
        private final NTERiverHydrology.ColumnProfile[] localProfiles;

        private Geometry(
            ChunkPos chunkPos,
            List<CoreColumn> coreColumns,
            NTERiverHydrology.ColumnProfile[] localProfiles
        )
        {
            this.chunkPos = chunkPos;
            this.localProfiles = localProfiles;
            Arrays.fill(minimumProtectedY, Integer.MAX_VALUE);
            Arrays.fill(maximumBedY, Integer.MIN_VALUE);

            for (int localZ = 0; localZ < 16; localZ++)
            {
                final int blockZ = chunkPos.getBlockZ(localZ);
                for (int localX = 0; localX < 16; localX++)
                {
                    final int blockX = chunkPos.getBlockX(localX);
                    CoreColumn nearest = null;
                    double nearestDistanceSq = Double.POSITIVE_INFINITY;
                    for (CoreColumn core : coreColumns)
                    {
                        final double deltaX = blockX - core.x();
                        final double deltaZ = blockZ - core.z();
                        final double distanceSq = deltaX * deltaX + deltaZ * deltaZ;
                        final double reach = core.radius() + BANK_BUFFER;
                        if (distanceSq <= reach * reach && distanceSq < nearestDistanceSq)
                        {
                            nearestDistanceSq = distanceSq;
                            nearest = core;
                        }
                    }
                    if (nearest != null)
                    {
                        final int index = localX + 16 * localZ;
                        final double reach = nearest.radius() + BANK_BUFFER;
                        final double influence = 1d - Mth.clamp(Math.sqrt(nearestDistanceSq) / reach, 0d, 1d);
                        minimumProtectedY[index] = NTERiverCaveProtection.minimumProtectedY(nearest.centerBedY(), influence);
                        maximumBedY[index] = nearest.topY();
                    }
                }
            }
        }

        public NTERiverHydrology.ColumnProfile[] localProfiles()
        {
            return localProfiles;
        }

        int minimumProtectedY(int blockX, int blockZ, int surfaceY)
        {
            if (!contains(blockX, blockZ))
            {
                return Integer.MAX_VALUE;
            }
            final int localX = blockX - chunkPos.getMinBlockX();
            final int localZ = blockZ - chunkPos.getMinBlockZ();
            return minimumProtectedY[localX + 16 * localZ];
        }

        int maximumProtectedY(int blockX, int blockZ, int surfaceY)
        {
            if (!contains(blockX, blockZ))
            {
                return Integer.MIN_VALUE;
            }
            final int localX = blockX - chunkPos.getMinBlockX();
            final int localZ = blockZ - chunkPos.getMinBlockZ();
            final int index = localX + 16 * localZ;
            return minimumProtectedY[index] == Integer.MAX_VALUE
                ? Integer.MIN_VALUE
                : Math.min(surfaceY, maximumBedY[index]);
        }

        private boolean protects(int blockX, int y, int blockZ, int surfaceY)
        {
            if (!contains(blockX, blockZ))
            {
                return false;
            }
            final int localX = blockX - chunkPos.getMinBlockX();
            final int localZ = blockZ - chunkPos.getMinBlockZ();
            final int index = localX + 16 * localZ;
            final int minimumY = minimumProtectedY[index];
            if (minimumY == Integer.MAX_VALUE)
            {
                return false;
            }
            final int maximumY = Math.min(surfaceY, maximumBedY[index]);
            return y >= minimumY && y <= maximumY;
        }

        private boolean contains(int blockX, int blockZ)
        {
            return blockX >= chunkPos.getMinBlockX() && blockX <= chunkPos.getMaxBlockX()
                && blockZ >= chunkPos.getMinBlockZ() && blockZ <= chunkPos.getMaxBlockZ();
        }
    }

    private NTERiverCaveProtection() {}

    /**
     * Builds the protection geometry for one generated chunk. Route planning
     * stays demand-driven: the current chunk is queried first, and the
     * one-chunk protection ring is queried only after a local river profile is
     * actually present. This keeps cave protection local instead of turning
     * every noise-fill task into a neighboring-partition route batch.
     */
    public static Geometry plan(NTERiverHydrology hydrology, ChunkPos chunkPos)
    {
        boolean localInfluence = false;
        final List<CoreColumn> coreColumns = new ArrayList<>();
        final NTERiverHydrology.ColumnProfile[] localProfiles = new NTERiverHydrology.ColumnProfile[16 * 16];
        for (int blockZ = chunkPos.getMinBlockZ(); blockZ <= chunkPos.getMaxBlockZ(); blockZ++)
        {
            for (int blockX = chunkPos.getMinBlockX(); blockX <= chunkPos.getMaxBlockX(); blockX++)
            {
                final NTERiverHydrology.ColumnProfile profile = hydrology.findGraphProfile(blockX, blockZ);
                localProfiles[blockX - chunkPos.getMinBlockX() + 16 * (blockZ - chunkPos.getMinBlockZ())] = profile;
                if (profile != null)
                {
                    localInfluence = true;
                    addCoreColumn(coreColumns, profile, blockX, blockZ);
                }
            }
        }

        if (localInfluence)
        {
            for (int blockZ = chunkPos.getMinBlockZ() - SEARCH_BUFFER; blockZ <= chunkPos.getMaxBlockZ() + SEARCH_BUFFER; blockZ++)
            {
                for (int blockX = chunkPos.getMinBlockX() - SEARCH_BUFFER; blockX <= chunkPos.getMaxBlockX() + SEARCH_BUFFER; blockX++)
                {
                    if (blockX < chunkPos.getMinBlockX() || blockX > chunkPos.getMaxBlockX()
                        || blockZ < chunkPos.getMinBlockZ() || blockZ > chunkPos.getMaxBlockZ())
                    {
                        addCoreColumn(coreColumns, hydrology.findGraphProfile(blockX, blockZ), blockX, blockZ);
                    }
                }
            }
        }
        return new Geometry(chunkPos, coreColumns, localProfiles);
    }

    private static void addCoreColumn(
        List<CoreColumn> coreColumns,
        NTERiverHydrology.ColumnProfile profile,
        int blockX,
        int blockZ
    )
    {
        if (profile != null && profile.inWaterCore())
        {
            // A surface creek only protects its own shallow bed shell. A covered
            // section must protect the complete roof above its cavity instead, so
            // carvers cannot punch the tunnel open.
            final int topY = profile.subterranean()
                ? profile.tunnelCeilingBlockY() + NTECommonConfig.getHeadwaterTunnelRoof()
                : Mth.floor(profile.centerBedY()) + ABOVE_BED_MARGIN;
            coreColumns.add(new CoreColumn(blockX, blockZ, profile.centerBedY(), profile.channelRadius(), topY));
        }
    }

    public static DensityScope openDensity(Geometry geometry)
    {
        ACTIVE_DENSITY.set(geometry);
        return new DensityScope();
    }

    public static Scope openCarvers(Geometry geometry, ChunkAccess chunk)
    {
        final ChunkPos chunkPos = chunk.getPos();
        final int[] minimumProtectedY = new int[16 * 16];
        final int[] maximumProtectedY = new int[16 * 16];
        Arrays.fill(minimumProtectedY, Integer.MAX_VALUE);
        Arrays.fill(maximumProtectedY, Integer.MIN_VALUE);

        for (int localZ = 0; localZ < 16; localZ++)
        {
            final int blockZ = chunkPos.getBlockZ(localZ);
            for (int localX = 0; localX < 16; localX++)
            {
                final int blockX = chunkPos.getBlockX(localX);
                final int index = localX + 16 * localZ;
                final int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ);
                minimumProtectedY[index] = geometry.minimumProtectedY(
                    blockX,
                    blockZ,
                    surfaceY
                );
                maximumProtectedY[index] = geometry.maximumProtectedY(blockX, blockZ, surfaceY);
            }
        }
        ACTIVE_CARVER.set(new Protection(chunkPos, minimumProtectedY, maximumProtectedY));
        return new Scope();
    }

    public static boolean protectsDensity(int blockX, int y, int blockZ, int surfaceY)
    {
        final Geometry geometry = ACTIVE_DENSITY.get();
        if (geometry == null)
        {
            return false;
        }
        return geometry.protects(blockX, y, blockZ, surfaceY);
    }

    public static boolean protectsCarver(int blockX, int y, int blockZ)
    {
        final Protection protection = ACTIVE_CARVER.get();
        return protection != null && protection.protects(blockX, y, blockZ);
    }

    public static double preserveTerrainDensity(double terrainWithoutCaves, double terrainAndCaves, boolean protectedByRiver)
    {
        return protectedByRiver && terrainWithoutCaves > 0d && terrainAndCaves <= 0d ? terrainWithoutCaves : terrainAndCaves;
    }

    static int minimumProtectedY(double centerBedY, double influence)
    {
        final double smoothInfluence = influence * influence * (3d - 2d * influence);
        final int supportDepth = Mth.floor(Mth.lerp(smoothInfluence, MIN_SUPPORT_DEPTH, MAX_SUPPORT_DEPTH));
        return Mth.floor(centerBedY) - supportDepth;
    }

    static int maximumProtectedY(int surfaceY, double centerBedY)
    {
        return Math.min(surfaceY, Mth.floor(centerBedY) + ABOVE_BED_MARGIN);
    }

    static Geometry geometryForTest(ChunkPos chunkPos, int coreX, int coreZ, double centerBedY, double radius)
    {
        return new Geometry(
            chunkPos,
            List.of(new CoreColumn(coreX, coreZ, centerBedY, radius, Mth.floor(centerBedY) + ABOVE_BED_MARGIN)),
            new NTERiverHydrology.ColumnProfile[16 * 16]
        );
    }
}
