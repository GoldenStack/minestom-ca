package net.minestom.ca;

import static net.minestom.server.coordinate.CoordConversion.globalToChunk;
import static net.minestom.server.coordinate.CoordConversion.globalToSectionRelative;

public final class CoordConversionPro {
    public static long sectionIndex(int sectionX, int sectionY, int sectionZ) {
        // Use 21 bits for each, with sign extension
        final long x = sectionX & 0x1FFFFF;
        final long y = sectionY & 0x1FFFFF;
        final long z = sectionZ & 0x1FFFFF;
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

    public static String sectionIndexGetString(long sectionIndex) {
        final int x = sectionIndexGetX(sectionIndex);
        final int y = sectionIndexGetY(sectionIndex);
        final int z = sectionIndexGetZ(sectionIndex);
        return String.format("[%d, %d, %d]", x, y, z);
    }

    public static long sectionIndexGlobal(int x, int y, int z) {
        final int sectionX = globalToChunk(x);
        final int sectionY = globalToChunk(y);
        final int sectionZ = globalToChunk(z);
        return sectionIndex(sectionX, sectionY, sectionZ);
    }

    public static boolean globalSectionBoundary(int x, int y, int z) {
        final int relX = globalToSectionRelative(x);
        final int relY = globalToSectionRelative(y);
        final int relZ = globalToSectionRelative(z);
        return relX == 0 || relX == 15 || relY == 0 || relY == 15 || relZ == 0 || relZ == 15;
    }
}