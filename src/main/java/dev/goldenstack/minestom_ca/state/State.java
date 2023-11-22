package dev.goldenstack.minestom_ca.state;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import org.jetbrains.annotations.NotNull;

/**
 * A state within a given world. This is different from a Minecraft block state.
 * @param variant the variant of this state
 * @param data the data contained within this state
 */
public record State(@NotNull String variant, @NotNull Object2IntMap<String> data) {

    public State(@NotNull String variant) {
        this(variant, Object2IntMaps.emptyMap());
    }

}
