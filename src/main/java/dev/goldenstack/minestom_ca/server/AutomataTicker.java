package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.Neighbors;
import dev.goldenstack.minestom_ca.rule.Rule;
import dev.goldenstack.minestom_ca.state.LocalState;
import dev.goldenstack.minestom_ca.state.State;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Ticks a list of cellular automata rules within a given world.
 */
public class AutomataTicker {

    private final @NotNull Instance instance;
    private final @NotNull List<Rule> rules;

    private final @NotNull Set<Point> tracked = new HashSet<>();
    private final @NotNull List<Point> nextTracked = new ArrayList<>();

    public AutomataTicker(@NotNull Instance instance, @NotNull List<Rule> rules) {
        this.instance = instance;
        this.rules = rules;
    }

    public void handleBlockChange(@NotNull Point point) {
        for (var point3d : Neighbors.NEIGHBORS_3D) {
            nextTracked.add(point.add(point3d));
        }
    }

    public void tick() {
        System.out.println("Tracked " + (tracked.size() + nextTracked.size()) + " (+" + nextTracked.size() + ")");
        tracked.addAll(nextTracked);
        nextTracked.clear();

        Map<Point, State> changes = new Object2ObjectOpenHashMap<>();

        for (var pos : tracked) {
            LocalState state = LocalState.at(instance, pos);

            boolean hasOne = false;

            for (var rule : rules) {
                if (rule.condition().test(state)) {
                    hasOne = true;

                    var localChanges = rule.result().apply(state);

                    for (var entry : localChanges.entrySet()) {
                        changes.put(entry.getKey().add(pos), entry.getValue());
                    }
                }
            }

            if (hasOne) {
                handleBlockChange(pos);
            }
        }

        for (var change : changes.entrySet()) {
            instance.setBlock(change.getKey(), Block.fromNamespaceId(change.getValue().variant()));
        }

    }

}
