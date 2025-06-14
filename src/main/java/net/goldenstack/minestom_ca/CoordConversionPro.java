package net.goldenstack.minestom_ca;

import static net.minestom.server.coordinate.CoordConversion.globalToChunk;
import static net.minestom.server.coordinate.CoordConversion.globalToSectionRelative;

public final class CoordConversionPro {
    public static long sectionIndex(int sectionX, int sectionY, int sectionZ) {
        // Use 21 bits for each, with sign extension
        long x = sectionX & 0x1FFFFF;
        long y = sectionY & 0x1FFFFF;
        long z = sectionZ & 0x1FFFFF;
        return (x << 42) | (y << 21) | z;
    }

    public static int sectionIndexGetX(long index) {
        int x = (int) (index >> 42) & 0x1FFFFF;
        // Sign extension for 21 bits
        if ((x & 0x100000) != 0) x |= ~0x1FFFFF;
        return x;
    }

    public static int sectionIndexGetY(long index) {
        int y = (int) (index >> 21) & 0x1FFFFF;
        if ((y & 0x100000) != 0) y |= ~0x1FFFFF;
        return y;
    }

    public static int sectionIndexGetZ(long index) {
        int z = (int) index & 0x1FFFFF;
        if ((z & 0x100000) != 0) z |= ~0x1FFFFF;
        return z;
    }

    public static long sectionIndexGlobal(int x, int y, int z) {
        final int sectionX = globalToChunk(x);
        final int sectionY = globalToChunk(y);
        final int sectionZ = globalToChunk(z);
        return sectionIndex(sectionX, sectionY, sectionZ);
    }

    public static int sectionBlockIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    public static int sectionBlockIndexGetX(int index) {
        return index & 0xF;
    }

    public static int sectionBlockIndexGetY(int index) {
        return (index >> 8) & 0xF;
    }

    public static int sectionBlockIndexGetZ(int index) {
        return (index >> 4) & 0xF;
    }

    public static boolean globalSameChunk(int x1, int y1, int z1, int x2, int y2, int z2) {
        return (globalToChunk(x1) == globalToChunk(x2) &&
                globalToChunk(y1) == globalToChunk(y2) &&
                globalToChunk(z1) == globalToChunk(z2));
    }

    public static boolean globalSectionBoundary(int x, int y, int z) {
        final int relX = globalToSectionRelative(x);
        final int relY = globalToSectionRelative(y);
        final int relZ = globalToSectionRelative(z);
        // Check if any coordinate is at the edge of a section (0 or 15)
        return relX == 0 || relX == 15 ||
                relY == 0 || relY == 15 ||
                relZ == 0 || relZ == 15;
    }

    public static long encodeSectionBlockChange(int localX, int localY, int localZ, long value) {
        // To use with `MultiBlockChangePacket`
        final long blockState = value << 12;
        final long pos = ((long) localX << 8 | (long) localZ << 4 | localY);
        return blockState | pos;
    }
}
