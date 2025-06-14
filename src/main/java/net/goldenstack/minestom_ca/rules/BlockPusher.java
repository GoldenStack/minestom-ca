package net.goldenstack.minestom_ca.rules;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.goldenstack.minestom_ca.Automata.CellRule;
import net.goldenstack.minestom_ca.Automata.Query;
import net.goldenstack.minestom_ca.Neighbors;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;

import java.util.List;
import java.util.Map;

public final class BlockPusher implements CellRule {
    private static final long AIR_STATE = Block.AIR.stateId();

    // Direction state - stores which direction to push (0=none, Direction ordinal + 1)
    private static final State PUSH_DIRECTION = new State("push_direction");
    // Strength state - stores how far the push should propagate
    private static final State PUSH_STRENGTH = new State("push_strength");

    private int directionIndex;
    private int strengthIndex;

    static Direction direction(int value) {
        if (value == 0) return null;
        return Direction.values()[value - 1];
    }

    @Override
    public void init(Map<State, Integer> mapping) {
        directionIndex = mapping.get(PUSH_DIRECTION);
        strengthIndex = mapping.get(PUSH_STRENGTH);
    }

    @Override
    public List<Action> process(Query query) {
        final long blockState = query.state(0);
        final boolean isAir = blockState == AIR_STATE;

        // Get current push properties
        final long dirValue = query.state(directionIndex);
        final long strengthValue = query.state(strengthIndex);
        final Direction pushDir = direction((int) dirValue);

        if (isAir) {
            // Check if a neighbor is pushing into this air block
            for (Direction dir : Direction.values()) {
                final Point dirPoint = dir.vec();
                final int nx = dirPoint.blockX(), ny = dirPoint.blockY(), nz = dirPoint.blockZ();
                // Check if neighbor is pushing toward us
                final long neighborDirValue = query.stateAt(nx, ny, nz, directionIndex);
                final Direction neighborDir = direction((int) neighborDirValue);
                if (neighborDir == dir.opposite()) {
                    final long neighborStrength = query.stateAt(nx, ny, nz, strengthIndex);
                    final long neighborBlock = query.stateAt(nx, ny, nz, 0);
                    if (neighborStrength > 0 && neighborBlock != AIR_STATE) {
                        // Air becomes the neighbor's block
                        Int2LongMap updatedState = new Int2LongOpenHashMap();
                        updatedState.put(0, neighborBlock);
                        final long newStrength = neighborStrength - 1;
                        if (newStrength > 0) {
                            updatedState.put(directionIndex, neighborDirValue);
                            updatedState.put(strengthIndex, newStrength);
                        } else {
                            updatedState.put(directionIndex, 0);
                            updatedState.put(strengthIndex, 0);
                        }
                        return List.of(Action.UpdateState(updatedState));
                    }
                }
            }
        } else if (pushDir != null && strengthValue > 0) {
            // We're a block being pushed
            final Point dirPoint = pushDir.vec();
            final int dx = dirPoint.blockX(), dy = dirPoint.blockY(), dz = dirPoint.blockZ();

            // Check if we can move forward (into air)
            if (query.stateAt(dx, dy, dz, 0) == AIR_STATE) {
                // We can move - turn into air and wake up the target position
                Int2LongMap clearState = new Int2LongOpenHashMap();
                clearState.put(0, AIR_STATE);
                clearState.put(directionIndex, 0);
                clearState.put(strengthIndex, 0);
                return List.of(new Action(
                        clearState,
                        List.of(Neighbors.SELF, dirPoint),
                        null,
                        0
                ));
            } else {
                // We hit a block - try to propagate the push to it
                return List.of(new Action(
                        null,
                        List.of(Neighbors.SELF, dirPoint),
                        null,
                        0
                ));
            }
        } else {
            // Check if we should receive a push from any neighbor
            for (Direction dir : Direction.values()) {
                final Point dirPoint = dir.vec();
                final int nx = dirPoint.blockX(), ny = dirPoint.blockY(), nz = dirPoint.blockZ();
                // Check if neighbor is pushing toward us
                final long neighborDirValue = query.stateAt(nx, ny, nz, directionIndex);
                final Direction neighborDir = direction((int) neighborDirValue);
                // If neighbor is pushing in the opposite direction (toward us)
                if (neighborDir == dir.opposite()) {
                    final long neighborStrength = query.stateAt(nx, ny, nz, strengthIndex);
                    if (neighborStrength > 0) {
                        // Block should store push direction and propagate it
                        Int2LongMap updatedState = new Int2LongOpenHashMap();
                        updatedState.put(directionIndex, neighborDirValue);
                        updatedState.put(strengthIndex, neighborStrength);
                        return List.of(Action.UpdateState(updatedState));
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean tracked(int state) {
        return true; // Track all states for push propagation
    }

    @Override
    public List<State> states() {
        return List.of(PUSH_DIRECTION, PUSH_STRENGTH);
    }
}
