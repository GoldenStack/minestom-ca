package net.minestom.ca;

import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;

public final class AutomataEvent {
    public record Tick(Automata.World world, Automata.Metrics metrics, double ms) implements InstanceEvent {
        @Override
        public @NotNull Instance getInstance() {
            return world.instance();
        }
    }

    public record Attach(Automata.World world) implements InstanceEvent {
        @Override
        public @NotNull Instance getInstance() {
            return world.instance();
        }
    }
}
