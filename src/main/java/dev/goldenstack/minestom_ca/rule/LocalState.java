package dev.goldenstack.minestom_ca.rule;

public interface LocalState {
    int relativeTest(int x, int y, int z, Rule.Condition condition);

    int selfStateValue(int state);

    int relativeStateValue(int state, int x, int y, int z);
}
