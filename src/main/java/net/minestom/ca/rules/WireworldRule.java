package net.minestom.ca.rules;

import net.minestom.ca.Automata.CellRule;
import net.minestom.ca.Automata.Context;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Neighbors;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WireworldRule implements CellRule {
    // Wireworld states represented by redstone power levels
    private static final int HEAD_STATE = 15;       // power=15 (max power - blue)
    private static final int TAIL_STATE = 10;       // power=10 (intermediate - red)
    private static final int CONDUCTOR_STATE = 0;   // power=0 (low power)

    private static final List<Action> POWER_WAKEUP_ACTION =
            List.of(Action.action(action -> action.wakeup(Neighbors.MOORE_3D)));

    @Override
    public void init(Map<State, Integer> mapping) {
        // No custom states needed, we use redstone power levels
    }

    @Override
    public List<Action> process(Context context, Query query) {
        final Block currentBlock = query.block();
        // Check if this is a redstone wire
        if (currentBlock.compare(Block.REDSTONE_WIRE)) {
            return processWire(query, currentBlock);
        }
        // Check if this is a redstone block (power source)
        if (currentBlock.compare(Block.REDSTONE_BLOCK)) {
            // Wake up surrounding blocks to detect the power source
            return POWER_WAKEUP_ACTION;
        }
        return null;
    }

    int blockPower(Block block) {
        final String powerStr = block.getProperty("power");
        if (powerStr == null) return 0;
        return Integer.parseInt(powerStr);
    }

    private List<Action> processWire(Query query, Block currentBlock) {
        final int currentPower = blockPower(currentBlock);

        // Determine the next state based on Wireworld rules
        int nextPower;
        // Check if a redstone block is adjacent, which would make this a HEAD
        if (isAdjacentToRedstoneBlock(query)) {
            nextPower = HEAD_STATE;
        } else if (currentPower == HEAD_STATE) {
            // Electron head -> Electron tail
            nextPower = TAIL_STATE;
        } else if (currentPower == TAIL_STATE) {
            // Electron tail -> Conductor
            nextPower = CONDUCTOR_STATE;
        } else {
            // All other redstone wire states are treated as conductors
            // Conductor -> Electron head if exactly 1 or 2 neighboring electron heads
            final int headCount = countHeads(query);
            nextPower = (headCount == 1 || headCount == 2) ? HEAD_STATE : CONDUCTOR_STATE;
        }

        // Only create an action if the state changes
        if (nextPower == currentPower) return null;

        // Create a new block with updated power level and wire connections
        Block newBlock = currentBlock.withProperty("power", String.valueOf(nextPower));
        return List.of(Action.action(action -> {
            action.state(0, newBlock.stateId());
            action.wakeup(Neighbors.MOORE_3D);
        }));
    }

    private boolean isAdjacentToRedstoneBlock(Query query) {
        for (Point point : Neighbors.NEUMANN_2D) {
            final Block neighborBlock = query.blockAt(point.blockX(), point.blockY(), point.blockZ());
            if (neighborBlock.compare(Block.REDSTONE_BLOCK)) return true;
        }
        return false;
    }

    private int countHeads(Query query) {
        int count = 0;
        for (Point point : Neighbors.MOORE_3D) {
            Block neighborBlock = query.blockAt(point.blockX(), point.blockY(), point.blockZ());
            if (neighborBlock.compare(Block.REDSTONE_WIRE)) {
                final int power = blockPower(neighborBlock);
                if (power == HEAD_STATE) count++;
            }
        }
        return count;
    }

    @Override
    public boolean tracked(Block block) {
        // Track both redstone wire blocks and redstone blocks
        return block.compare(Block.REDSTONE_WIRE) || block.compare(Block.REDSTONE_BLOCK);
    }

    @Override
    public Set<State> states() {
        // We don't use custom states, we use the power property of redstone wire
        return Set.of();
    }
}
