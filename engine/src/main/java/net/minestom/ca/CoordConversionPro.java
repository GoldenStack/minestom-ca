package net.minestom.ca;

import static net.minestom.server.coordinate.CoordConversion.globalToSectionRelative;

public final class CoordConversionPro {
    public static boolean globalSectionBoundary(int x, int y, int z) {
        final int relX = globalToSectionRelative(x);
        final int relY = globalToSectionRelative(y);
        final int relZ = globalToSectionRelative(z);
        return relX == 0 || relX == 15 || relY == 0 || relY == 15 || relZ == 0 || relZ == 15;
    }
}