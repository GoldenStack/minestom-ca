package net.minestom.ca.rules;

import net.minestom.ca.Automata;
import net.minestom.ca.Automata.Context;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Automata.Rule;
import net.minestom.ca.Neighbors;
import net.minestom.ca.States;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class RedstonePowerRule implements Rule {
    private static final Automata.Rule.Info INFO = new Automata.Rule.Info(
            "Redstone Power Rule",
            "Simulates the behavior of redstone wires and blocks in a Wireworld-like automaton.",
            List.of(
                    Interest.block(Block.REDSTONE_WIRE),
                    Interest.block(Block.REDSTONE_BLOCK)
            ),
            Set.of(States.DATA)
    );

    // Wireworld states represented by redstone power levels
    private static final int HEAD_STATE = 15;       // power=15 (max power - blue)
    private static final int TAIL_STATE = 10;       // power=10 (intermediate - red)
    private static final int CONDUCTOR_STATE = 0;   // power=0 (low power)

    private static final Action POWER_WAKEUP_ACTION = Action.action(a -> a.wakeup(Neighbors.REDSTONE_CONNECT_SELF));

    private static final List<Vec> POWER_WAKEUP_NEIGHBORS = Neighbors.mooreRadius(2, false);

    private int dataIndex;

    @Override
    public void init(Map<State, Integer> mapping) {
        this.dataIndex = mapping.get(States.DATA);
    }

    @Override
    public Action process(Context context, Query query) {
        final Block currentBlock = query.block();
        if (currentBlock.compare(Block.REDSTONE_WIRE)) return processWire(query, currentBlock);
        if (currentBlock.compare(Block.REDSTONE_BLOCK)) return POWER_WAKEUP_ACTION;
        return null;
    }

    int blockPower(Block block) {
        final String powerStr = block.getProperty("power");
        if (powerStr == null) return 0;
        return Integer.parseInt(powerStr);
    }

    private Action processWire(Query query, Block currentBlock) {
        final int currentProperty = blockPower(currentBlock);
        final long currentValue = query.state(dataIndex);

        final long powerReceived = receivedPower(query);
        int nextProperty;
        long nextValue;
        // Check if a redstone block is adjacent, which would make this a HEAD
        if (powerReceived != 0) {
            nextProperty = HEAD_STATE;
            nextValue = powerReceived;
        } else if (currentProperty == HEAD_STATE) {
            // Electron head -> Electron tail
            nextProperty = TAIL_STATE;
            nextValue = 0;
        } else if (currentProperty == TAIL_STATE) {
            // Electron tail -> Conductor
            nextProperty = CONDUCTOR_STATE;
            nextValue = 0;
        } else {
            // All other redstone wire states are treated as conductors
            // Conductor -> Electron head if exactly 1 or 2 neighboring electron heads
            final long headsValue = computeHeads(query);
            if (headsValue != 0) {
                nextProperty = HEAD_STATE;
                nextValue = headsValue;
            } else {
                nextProperty = CONDUCTOR_STATE;
                nextValue = 0;
            }
        }
        if (currentProperty == nextProperty && currentValue == nextValue) return null;
        return Action.action(action -> {
            action.property("power", String.valueOf(nextProperty));
            action.state(dataIndex, nextValue);
            action.wakeup(POWER_WAKEUP_NEIGHBORS);
        });
    }

    private long receivedPower(Query query) {
        for (Point point : Neighbors.REDSTONE_CONNECT) {
            final Block neighborBlock = query.blockAt(point);
            if (neighborBlock.compare(Block.REDSTONE_BLOCK)) return 1;
        }
        return 0;
    }

    private long computeHeads(Query query) {
        AtomicLong result = new AtomicLong();
        query.count(dataIndex, Neighbors.REDSTONE_CONNECT, value -> {
            final long current = result.getPlain();
            result.setPlain(current | value);
            return true;
        });
        return result.getPlain();
    }

    @Override
    public Info info() {
        return INFO;
    }
}
