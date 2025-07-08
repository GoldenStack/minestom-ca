package net.minestom.ca;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.utils.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores lists of neighbors.
 */
public final class Neighbors {
    public static final Vec SELF = Vec.ZERO;

    public static final Vec DOWN = Direction.DOWN.vec();
    public static final Vec UP = Direction.UP.vec();
    public static final Vec NORTH = Direction.NORTH.vec();
    public static final Vec SOUTH = Direction.SOUTH.vec();
    public static final Vec WEST = Direction.WEST.vec();
    public static final Vec EAST = Direction.EAST.vec();

    public static final Vec NORTHEAST = NORTH.add(EAST);
    public static final Vec SOUTHEAST = SOUTH.add(EAST);
    public static final Vec NORTHWEST = NORTH.add(WEST);
    public static final Vec SOUTHWEST = SOUTH.add(WEST);

    public static final List<Vec> MOORE_2D_SELF = List.of(SELF, NORTH, EAST, SOUTH, WEST, NORTHEAST, SOUTHEAST, NORTHWEST, SOUTHWEST);
    public static final List<Vec> MOORE_2D = List.of(NORTH, EAST, SOUTH, WEST, NORTHEAST, SOUTHEAST, NORTHWEST, SOUTHWEST);

    public static final List<Vec> MOORE_3D_SELF;
    public static final List<Vec> MOORE_3D;

    public static final List<Vec> NEUMANN_2D_SELF = List.of(SELF, NORTH, SOUTH, EAST, WEST);
    public static final List<Vec> NEUMANN_2D = List.of(NORTH, SOUTH, EAST, WEST);

    public static final List<Vec> NEUMANN_3D_SELF = List.of(SELF, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    public static final List<Vec> NEUMANN_3D = List.of(NORTH, SOUTH, EAST, WEST, UP, DOWN);

    public static final List<Vec> REDSTONE_CONNECT_SELF  = List.of(
            SELF,
            NORTH, EAST, SOUTH, WEST, UP, DOWN,
            NORTH.add(UP), EAST.add(UP), SOUTH.add(UP), WEST.add(UP),
            NORTH.add(DOWN), EAST.add(DOWN), SOUTH.add(DOWN), WEST.add(DOWN)
    );
    public static final List<Vec> REDSTONE_CONNECT = List.of(
            NORTH, EAST, SOUTH, WEST, UP, DOWN,
            NORTH.add(UP), EAST.add(UP), SOUTH.add(UP), WEST.add(UP),
            NORTH.add(DOWN), EAST.add(DOWN), SOUTH.add(DOWN), WEST.add(DOWN)
    );
    public static final List<Vec> RADIAL_3D_SELF;
    public static final List<Vec> RADIAL_3D;

    static {
        List<Vec> points3d = new ArrayList<>();
        final int[] offsets = {-1, 0, 1};
        for (int x : offsets) {
            for (int y : offsets) {
                for (int z : offsets) {
                    points3d.add(new Vec(x, y, z));
                }
            }
        }

        MOORE_3D_SELF = List.copyOf(points3d);

        points3d.removeIf(Vec.ZERO::equals);
        MOORE_3D = List.copyOf(points3d);

        // Generate radial 3D neighbors
        List<Vec> radialPoints = new ArrayList<>(NEUMANN_3D_SELF);
        // Add diagonal points at distance √2
        radialPoints.add(new Vec(1, 1, 0));
        radialPoints.add(new Vec(1, -1, 0));
        radialPoints.add(new Vec(-1, 1, 0));
        radialPoints.add(new Vec(-1, -1, 0));
        radialPoints.add(new Vec(1, 0, 1));
        radialPoints.add(new Vec(1, 0, -1));
        radialPoints.add(new Vec(-1, 0, 1));
        radialPoints.add(new Vec(-1, 0, -1));
        radialPoints.add(new Vec(0, 1, 1));
        radialPoints.add(new Vec(0, 1, -1));
        radialPoints.add(new Vec(0, -1, 1));
        radialPoints.add(new Vec(0, -1, -1));

        RADIAL_3D_SELF = List.copyOf(radialPoints);

        List<Vec> radialPointsNoSelf = new ArrayList<>(radialPoints);
        radialPointsNoSelf.removeIf(Vec.ZERO::equals);
        RADIAL_3D = List.copyOf(radialPointsNoSelf);
    }

    public static final Map<String, List<Vec>> NAMED = Map.ofEntries(
            Map.entry("up", List.of(UP)),
            Map.entry("down", List.of(DOWN)),
            Map.entry("north", List.of(NORTH)),
            Map.entry("east", List.of(EAST)),
            Map.entry("south", List.of(SOUTH)),
            Map.entry("west", List.of(WEST)),
            Map.entry("northeast", List.of(NORTHEAST)),
            Map.entry("southeast", List.of(SOUTHEAST)),
            Map.entry("northwest", List.of(NORTHWEST)),
            Map.entry("southwest", List.of(SOUTHWEST)),
            Map.entry("moore2dself", MOORE_2D_SELF),
            Map.entry("moore2d", MOORE_2D),
            Map.entry("moore3dself", MOORE_3D_SELF),
            Map.entry("moore3d", MOORE_3D),
            Map.entry("neumann2dself", NEUMANN_2D_SELF),
            Map.entry("neumann2d", NEUMANN_2D),
            Map.entry("neumann3dself", NEUMANN_3D_SELF),
            Map.entry("neumann3d", NEUMANN_3D),
            Map.entry("radial3dself", RADIAL_3D_SELF),
            Map.entry("radial3d", RADIAL_3D)
    );

    public static List<Vec> mooreRadius(int radius, boolean includeSelf) {
        if (radius < 0) throw new IllegalArgumentException("Radius cannot be negative");
        if (radius == 0) return includeSelf ? List.of(SELF) : List.of();
        if (radius == 1) return includeSelf ? MOORE_3D_SELF : MOORE_3D;
        List<Vec> result = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (includeSelf || x != 0 || y != 0 || z != 0) {
                        result.add(new Vec(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    public static List<Vec> neumannRadius(int radius, boolean includeSelf) {
        if (radius < 0) throw new IllegalArgumentException("Radius cannot be negative");
        if (radius == 0) return includeSelf ? List.of(SELF) : List.of();
        if (radius == 1) return includeSelf ? NEUMANN_3D_SELF : NEUMANN_3D;
        List<Vec> result = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    final int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (distance <= radius && (includeSelf || distance > 0)) {
                        result.add(new Vec(x, y, z));
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
