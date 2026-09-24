package com.newterraearth.tfe.world.river;

import net.minecraft.util.Mth;

/**
 * Deterministic, allocation free smooth value noise used to shape covered
 * creeks. Every caller passes the creek's own planned seed, so the same world
 * always produces the same cavity and mouth without sequential randomness.
 */
final class NTERiverCaveNoise
{
    /** Along run wobble of the tunnel arch apex. */
    static final long SALT_ARCH = 0x2D7E41B95C3A806FL;
    /** Mouth rim irregularity. */
    static final long SALT_MOUTH = 0x41A9E5C7D2B3086FL;

    private static final double SECOND_OCTAVE_WEIGHT = 0.35d;

    private NTERiverCaveNoise() {}

    /**
     * Smooth noise in {@code [-1, 1]} whose first octave repeats every
     * {@code 1 / frequency} blocks. Two octaves keep the shape organic without
     * turning it into per block static.
     */
    static double noise(long seed, long salt, int x, int z, double frequency)
    {
        final double primary = octave(seed, salt, x, z, frequency);
        final double secondary = octave(seed, salt ^ 0x9E3779B97F4A7C15L, x, z, frequency * 2.7d);
        final double combined = primary * (1d - SECOND_OCTAVE_WEIGHT) + secondary * SECOND_OCTAVE_WEIGHT;
        return Mth.clamp(combined, -1d, 1d);
    }

    private static double octave(long seed, long salt, int x, int z, double frequency)
    {
        final double scaledX = x * frequency;
        final double scaledZ = z * frequency;
        final int latticeX = Mth.floor(scaledX);
        final int latticeZ = Mth.floor(scaledZ);
        final double blendX = smootherStep(scaledX - latticeX);
        final double blendZ = smootherStep(scaledZ - latticeZ);
        final double near = Mth.lerp(blendX, latticeValue(seed, salt, latticeX, latticeZ), latticeValue(seed, salt, latticeX + 1, latticeZ));
        final double far = Mth.lerp(blendX, latticeValue(seed, salt, latticeX, latticeZ + 1), latticeValue(seed, salt, latticeX + 1, latticeZ + 1));
        return Mth.lerp(blendZ, near, far);
    }

    private static double latticeValue(long seed, long salt, int x, int z)
    {
        final long hashed = mix64(seed ^ salt ^ ((long) x * 0x2545F4914F6CDD1DL) ^ ((long) z * 0x9E3779B97F4A7C15L));
        // 24 significant bits keep the result exactly representable and centred on zero.
        return ((hashed >>> 40) / (double) (1 << 23)) - 1d;
    }

    private static double smootherStep(double value)
    {
        final double t = Mth.clamp(value, 0d, 1d);
        return t * t * t * (t * (t * 6d - 15d) + 10d);
    }

    /**
     * Deterministic selector in {@code [0, modulus)} for one block position. Used
     * where a decoration must be thinned by a fixed fraction without touching the
     * sequential world random source.
     */
    static int select(long seed, long salt, int x, int y, int z, int modulus)
    {
        if (modulus <= 1)
        {
            return 0;
        }
        final long hashed = mix64(
            seed
                ^ salt
                ^ ((long) x * 0x2545F4914F6CDD1DL)
                ^ ((long) y * 0xC2B2AE3D27D4EB4FL)
                ^ ((long) z * 0x9E3779B97F4A7C15L)
        );
        return (int) Math.floorMod(hashed, (long) modulus);
    }

    private static long mix64(long value)
    {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
