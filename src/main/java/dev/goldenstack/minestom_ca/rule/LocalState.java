package dev.goldenstack.minestom_ca.rule;

public interface LocalState {
    boolean relativeTest(int x, int y, int z, Condition condition);

    int selfStateValue(int state);

    int relativeStateValue(int state, int x, int y, int z);
}
