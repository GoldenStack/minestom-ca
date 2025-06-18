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
import java.util.concurrent.ThreadLocalRandom;

public final class GrassSpreadingRule implements CellRule {
    // Configurable parameters
    private static final int SPREAD_CHANCE = 20;  // 1 in X chance per attempt (higher = slower)
    private static final int SPREAD_DELAY = 40;  // Ticks between spread attempts

    private static final long GRASS_BLOCK_STATE = Block.GRASS_BLOCK.stateId();
    private static final long DIRT_STATE = Block.DIRT.stateId();

    public static final List<Action> GRASS_TO_DIRT = List.of(Action.action(action -> {
        action.state(0, DIRT_STATE);
        action.condition(0, GRASS_BLOCK_STATE);
        action.wakeup(Neighbors.MOORE_3D_SELF);
    }));
    public static final List<Action> DIRT_TO_GRASS = List.of(Action.action(action -> {
        action.state(0, GRASS_BLOCK_STATE);
        action.condition(0, DIRT_STATE);
        action.wakeup(Neighbors.MOORE_3D_SELF);
    }));
    public static final List<Action> DIRT_TRY_LATER = List.of(Action.action(action -> {
        action.wakeup(Neighbors.SELF);
        action.schedule(SPREAD_DELAY);
    }));

    @Override
    public void init(Map<State, Integer> mapping) {
    }

    @Override
    public List<Action> process(Context context, Query query) {
        final Block currentBlock = query.block();
        // Rule 1: If I'm a grass block, try to spread to adjacent dirt blocks
        if (currentBlock.compare(Block.GRASS_BLOCK)) {
            return processGrassBlock(query);
        }
        // Rule 2: If I'm dirt and adjacent to grass, potentially receive grass
        if (currentBlock.compare(Block.DIRT)) {
            return processDirtBlock(query);
        }
        return null;
    }

    private List<Action> processGrassBlock(Query query) {
        // Check if there's a block above that would prevent grass from surviving/spreading
        final Block blockAbove = query.blockAt(0, 1, 0);
        return blockAbove.isSolid() ? GRASS_TO_DIRT : null;
    }

    private List<Action> processDirtBlock(Query query) {
        // Check if there's a block above that would prevent grass growth
        Block blockAbove = query.blockAt(0, 1, 0);
        if (blockAbove.isSolid()) return null;

        // Check if there's at least one adjacent grass block
        boolean hasAdjacentGrass = false;
        for (Point point : Neighbors.MOORE_2D) {
            final Block neighborBlock = query.blockAt(point.blockX(), point.blockY(), point.blockZ());
            if (neighborBlock.compare(Block.GRASS_BLOCK)) {
                hasAdjacentGrass = true;
                break;
            }
        }

        if (!hasAdjacentGrass) return null;
        if (ThreadLocalRandom.current().nextInt(SPREAD_CHANCE) == 0) {
            return DIRT_TO_GRASS;
        } else {
            return DIRT_TRY_LATER;
        }
    }

    @Override
    public List<Interest> interests() {
        return List.of(
                Interest.block(Block.GRASS_BLOCK),
                Interest.block(Block.DIRT)
        );
    }

    @Override
    public Set<State> states() {
        return Set.of();
    }
}
