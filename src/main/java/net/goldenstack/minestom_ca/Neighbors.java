package net.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores lists of neighbors.
 */
public final class Neighbors {
    public static final Point SELF = Vec.ZERO;

    public static final Point UP = new Vec(0, 1, 0);

    public static final Point DOWN = new Vec(0, -1, 0);

    public static final Point NORTH = new Vec(0, 0, -1);
    public static final Point EAST = new Vec(1, 0, 0);
    public static final Point SOUTH = new Vec(0, 0, 1);
    public static final Point WEST = new Vec(-1, 0, 0);

    public static final Point NORTHEAST = NORTH.add(EAST);
    public static final Point SOUTHEAST = SOUTH.add(EAST);
    public static final Point NORTHWEST = NORTH.add(WEST);
    public static final Point SOUTHWEST = SOUTH.add(WEST);

    public static final List<Point> MOORE_2D_SELF = List.of(SELF, NORTH, EAST, SOUTH, WEST, NORTHEAST, SOUTHEAST, NORTHWEST, SOUTHWEST);
    public static final List<Point> MOORE_2D = List.of(NORTH, EAST, SOUTH, WEST, NORTHEAST, SOUTHEAST, NORTHWEST, SOUTHWEST);

    public static final List<Point> MOORE_3D_SELF;
    public static final List<Point> MOORE_3D;

    public static final List<Point> NEUMANN_2D_SELF = List.of(SELF, NORTH, SOUTH, EAST, WEST);
    public static final List<Point> NEUMANN_2D = List.of(NORTH, SOUTH, EAST, WEST);

    public static final List<Point> NEUMANN_3D_SELF = List.of(SELF, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    public static final List<Point> NEUMANN_3D = List.of(NORTH, SOUTH, EAST, WEST, UP, DOWN);

    static {
        List<Point> points3d = new ArrayList<>();
        for (int x : new int[]{-1, 0, 1}) {
            for (int y : new int[]{-1, 0, 1}) {
                for (int z : new int[]{-1, 0, 1}) {
                    points3d.add(new Vec(x, y, z));
                }
            }
        }

        MOORE_3D_SELF = List.copyOf(points3d);

        points3d.removeIf(Vec.ZERO::equals);
        MOORE_3D = List.copyOf(points3d);
    }

    public static final Map<String, List<Point>> NAMED = Map.ofEntries(
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
            Map.entry("neumann3d", NEUMANN_3D)
    );
}
