package net.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * An AutomataWorld is a world capable of ticking cellular automata rules.
 */
public interface AutomataWorld {

    Map<Instance, AutomataWorld> WORLDS = new HashMap<>();

    static void register(AutomataWorld world) {
        final AutomataWorld prev = WORLDS.put(world.instance(), world);
        if (prev != null) {
            throw new IllegalStateException("An AutomataWorld is already registered for the instance " + world.instance());
        }
    }

    static AutomataWorld get(Instance instance) {
        return WORLDS.get(instance);
    }

    /**
     * Simulates one tick of progress for the world.
     */
    void tick();

    /**
     * Handles an external block change (e.g. block place or break)
     */
    void handlePlacement(int x, int y, int z, Map<Integer, Integer> properties);

    default void handlePlacement(Point point, Block block) {
        handlePlacement(point.blockX(), point.blockY(), point.blockZ(),
                Map.of(0, (int) block.stateId()));
    }

    void handleChunkLoad(int chunkX, int chunkZ);

    void handleChunkUnload(int chunkX, int chunkZ);

    Map<Integer, Integer> queryIndexes(int x, int y, int z);

    default Map<String, Integer> queryNames(int x, int y, int z) {
        Map<Integer, String> variables = new HashMap<>();
        for (var entry : program().variables().entrySet()) {
            variables.put(entry.getValue(), entry.getKey());
        }
        final Map<Integer, Integer> indexes = queryIndexes(x, y, z);
        Map<String, Integer> names = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : indexes.entrySet()) {
            final String name = variables.get(entry.getKey());
            if (name != null) names.put(name, entry.getValue());
            else names.put("var" + entry.getKey(), entry.getValue());
        }
        return Map.copyOf(names);
    }

    /**
     * Gets the instance that is being ticked
     *
     * @return the ticked instance
     */
    Instance instance();

    Program program();
}
