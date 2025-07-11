package net.minestom.ca;

import jdk.jfr.*;
import org.jetbrains.annotations.ApiStatus;

/**
 * JFR events for monitoring Minestom server activities.
 */
@ApiStatus.Internal
@SuppressWarnings("ALL")
public final class AutomataJFR {
    public static final String AUTOMATA_TICK = "minestom.ca.AutomataTick";

    @Name(AUTOMATA_TICK)
    @Label("Automata Tick")
    @Category({"Minestom", "CA"})
    @Description("Time spent ticking a single automata instance")
    public static final class Tick extends Event {
        @Label("Instance UUID")
        String uuid;
        @Label("Value")
        @Unsigned
        int processedSections;
        @Label("Processed Blocks")
        @Unsigned
        int processedBlocks;
        @Label("Updated Blocks")
        @Unsigned
        int updatedBlocks;
    }
}
