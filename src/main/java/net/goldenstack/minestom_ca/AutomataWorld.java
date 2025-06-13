package net.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Chunk;
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
        final Instance instance = world.instance();
        final AutomataWorld prev = WORLDS.put(instance.getUuid(), world);
        if (prev != null) {
            throw new IllegalStateException("An AutomataWorld is already registered for the instance " + instance);
        }
        // Register loaded chunks
        System.out.println("Registering loaded chunks...");
        for (Chunk c : instance.getChunks()) {
            world.handleChunkLoad(c.getChunkX(), c.getChunkZ());
        }
        System.out.println("Loaded chunks registered");
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
    void handlePlacement(int x, int y, int z, Int2LongMap properties);

    default void handlePlacement(Point point, Block block) {
        handlePlacement(point.blockX(), point.blockY(), point.blockZ(),
                CellRule.stateMap(0, block.stateId()));
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
