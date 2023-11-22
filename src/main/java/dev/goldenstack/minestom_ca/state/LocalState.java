package dev.goldenstack.minestom_ca.state;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * The state of a world, situated around a given point.
 */
public interface LocalState {

    static @NotNull LocalState relative(@NotNull LocalState state, @NotNull Point offset) {
        return new LocalState() {
            @Override
            public @NotNull State relative(int x, int y, int z) {
                return state.relative(offset.blockX() + x, offset.blockY() + y, offset.blockZ() + z);
            }

            @Override
            public void apply(@NotNull Changes changes) {
                Object2ObjectMap<Point, State> map = new Object2ObjectArrayMap<>();
                for (var entry : changes.changes().entrySet()) {
                    map.put(entry.getKey().add(offset), entry.getValue());
                }
                state.apply(new Changes(map));
            }
        };
    }

    static @NotNull LocalState at(@NotNull Instance instance, @NotNull Point pos) {
        return new LocalState() {
            @Override
            public @NotNull State relative(int x, int y, int z) {
                var block = instance.getBlock(pos.add(x, y, z));
                return new State(block.name());
            }

            @Override
            public void apply(@NotNull Changes changes) {
                for (var change : changes.changes().entrySet()) {
                    instance.setBlock(pos.add(change.getKey()), Block.fromNamespaceId(change.getValue().variant()));
                }
            }
        };
    }

    /**
     * Gets the state at the current origin (not the world's global origin).
     * @return the state at the current origin - equal to {@code #relative(0, 0, 0)}
     */
    default @NotNull State self() {
        return relative(0, 0, 0);
    }

    /**
     * Applies the given changes.
     * @param changes the changes to apply
     */
    void apply(@NotNull Changes changes);

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
    record Changes(@NotNull Object2ObjectMap<Point, State> changes) {}

}
