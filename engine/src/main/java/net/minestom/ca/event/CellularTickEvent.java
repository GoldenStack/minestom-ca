package net.minestom.ca.event;

import net.minestom.ca.Automata;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

public record CellularTickEvent(Automata.World world, Automata.Metrics metrics, double ms) implements InstanceEvent {
    @Override
    public @NotNull Instance getInstance() {
        return world.instance();
    }
}
