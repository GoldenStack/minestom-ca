package net.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An AutomataWorld is a world capable of ticking cellular automata rules.
 */
public interface AutomataWorld {

    Map<UUID, AutomataWorld> WORLDS = new HashMap<>();

    static void register(AutomataWorld world) {
        final AutomataWorld prev = WORLDS.put(world.instance().getUuid(), world);
        if (prev != null) {
            throw new IllegalStateException("An AutomataWorld is already registered for the instance " + world.instance());
        }
    }

    static AutomataWorld get(Instance instance) {
        return WORLDS.get(instance.getUuid());
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
                Map.of(0, block.stateId()));
    }

    void handleChunkLoad(int chunkX, int chunkZ);

    void handleChunkUnload(int chunkX, int chunkZ);

    /**
     * Gets the instance that is being ticked
     *
     * @return the ticked instance
     */
    Instance instance();

    CellRule rules();

    AutomataQuery query();
}
