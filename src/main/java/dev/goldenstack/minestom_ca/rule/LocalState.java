package dev.goldenstack.minestom_ca.rule;

import net.minestom.server.coordinate.Point;

public interface LocalState {
    LocalState relative(Point point);

    int selfStateValue(int state);

    int relativeStateValue(int state, int x, int y, int z);
}
