package dev.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores lists of neighbors.
 */
public class Neighbors {

    /**
     * All 2d neighbors (so y = 0). 9 total.
     */
    public static final @NotNull List<Point> NEIGHBORS_2D;

    /**
     * All 2d neighbors (so y = 0) excluding {@code 0, 0}. 8 total.
     */
    public static final @NotNull List<Point> NEIGHBORS_2D_NOT_SELF;

    /**
     * All 3d neighbors. 27 total.
     */
    public static final @NotNull List<Point> NEIGHBORS_3D;

    /**
     * All 3d neighbors excluding {@code 0, 0, 0}. 26 total.
     */
    public static final @NotNull List<Point> NEIGHBORS_3D_NOT_SELF;

    static {
        List<Point> points2d = new ArrayList<>();
        for (int x : new int[]{-1, 0, 1}) {
            for (int z : new int[]{-1, 0, 1}) {
                points2d.add(new Vec(x, 0, z));
            }
        }

        NEIGHBORS_2D = List.copyOf(points2d);

        points2d.removeIf(Vec.ZERO::equals);
        NEIGHBORS_2D_NOT_SELF = List.copyOf(points2d);


        List<Point> points3d = new ArrayList<>();
        for (int x : new int[]{-1, 0, 1}) {
            for (int y : new int[]{-1, 0, 1}) {
                for (int z : new int[]{-1, 0, 1}) {
                    points3d.add(new Vec(x, y, z));
                }
            }
        }

        NEIGHBORS_3D = List.copyOf(points3d);

        points3d.removeIf(Vec.ZERO::equals);
        NEIGHBORS_3D_NOT_SELF = List.copyOf(points3d);
    }

}
