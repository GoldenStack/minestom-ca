package dev.goldenstack.minestom_ca.state;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minestom.server.coordinate.Vec;
import org.jetbrains.annotations.NotNull;

/**
 * The state of a world, situated around a given point.
 */
public interface LocalState {

    /**
     * Gets the state at the current origin (not the world's global origin).
     * @return the state at the current origin - equal to {@code #relative(0, 0, 0)}
     */
    default @NotNull State self() {
        return relative(0, 0, 0);
    }

    /**
     * Gets the state relative to the current origin, at the given offset.
     * @param x the x offset
     * @param y the y offset
     * @param z the z offset
     * @return the state at the given offset to the current origin
     */
    @NotNull State relative(int x, int y, int z);

    /**
     * Changes that are applied to a local state after a rule application.
     * @param changes the map of changes
     */
    record Changes(@NotNull Object2ObjectMap<Vec, State> changes) {}

}
