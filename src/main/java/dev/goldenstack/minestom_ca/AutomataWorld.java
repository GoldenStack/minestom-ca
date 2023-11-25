package dev.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.HashMap;
import java.util.Map;

public interface AutomataWorld {

    Map<Instance, AutomataWorld> WORLDS = new HashMap<>();

    static void register(AutomataWorld world) {
        WORLDS.put(world.instance(), world);
    }

    static AutomataWorld get(Instance instance) {
        return WORLDS.get(instance);
    }

    Instance instance();

    void tick();

    void handlePlacement(Point point, Block block);
}
