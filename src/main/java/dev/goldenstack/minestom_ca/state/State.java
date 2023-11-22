package dev.goldenstack.minestom_ca.state;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;

/**
 * A state within a given world. This is different from a Minecraft block state.
 */
public interface State {

    /**
     * Returns a string representing the variant that this state is.
     * @return the variant of this state
     */
    @NotNull String variant();

    /**
     * Returns the data contained within this state.
     * @return the map of data within the state
     */
    @NotNull Object2IntMap<String> data();

}
