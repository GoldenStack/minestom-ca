package net.minestom.ca.rules;

import net.minestom.ca.Automata;
import net.minestom.ca.Neighbors;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameOfLife implements Automata.Rule {
    private static final Automata.Rule.Info INFO = new Info(
            "Game of Life",
            "A classic cellular automaton simulating life and death based on neighbor counts.",
            List.of(Interest.block(Block.WHITE_WOOL)),
            Set.of()
    );

    private static final long VOID_STATE = Block.AIR.stateId();
    private static final long ALIVE_STATE = Block.WHITE_WOOL.stateId();

    private static final Action KILL_ACTION = Action.action(action -> {
        action.state(0, VOID_STATE);
        action.wakeup(Neighbors.MOORE_2D_SELF);
    });
    private static final Action REPRODUCE_ACTION = Action.action(action -> {
        action.state(0, ALIVE_STATE);
        action.wakeup(Neighbors.MOORE_2D_SELF);
    });

    @Override
    public void init(Map<State, Integer> mapping) {
    }

    @Override
    public Action process(Automata.Context context, Automata.Query query) {
        final int neighbors = query.countLimit(0, 4, Neighbors.MOORE_2D,
                state -> state == ALIVE_STATE);
        final long currentState = query.state(0);
        if (currentState == ALIVE_STATE && (neighbors < 2 || neighbors > 3)) {
            // Underpopulation or overpopulation
            return KILL_ACTION;
        } else if (currentState == VOID_STATE && neighbors == 3) {
            // Reproduction
            return REPRODUCE_ACTION;
        }
        return null;
    }

    @Override
    public Info info() {
        return INFO;
    }
}
