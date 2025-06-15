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

    private static final Direction[] DIRECTIONS = Direction.values();

    private int directionIndex;
    private int strengthIndex;

    static Direction direction(long value) {
        if (value == 0) return null;
        return DIRECTIONS[(int) (value - 1)];
    }

    @Override
    public void init(Map<State, Integer> mapping) {
        directionIndex = mapping.get(PUSH_DIRECTION);
        strengthIndex = mapping.get(PUSH_STRENGTH);
    }

    boolean replaceable(long state) {
        return state == AIR_STATE;
    }

    private static final List<Point>[] WAKEUP_POINTS_CACHE = new List[Direction.values().length];

    static {
        for (Direction dir : DIRECTIONS) {
            WAKEUP_POINTS_CACHE[dir.ordinal()] = List.of(Neighbors.SELF, dir.vec(), dir.opposite().vec());
        }
    }

    List<Point> wakeupPoints(Direction direction) {
        return WAKEUP_POINTS_CACHE[direction.ordinal()];
    }

    @Override
    public List<Action> process(Query query) {
        final long dirValue = query.state(directionIndex);
        if (dirValue != 0) {
            // We're a block being pushed
            final Direction pushDir = direction((int) dirValue);
            assert pushDir != null;
            final Point dirPoint = pushDir.vec();
            final int dx = dirPoint.blockX(), dy = dirPoint.blockY(), dz = dirPoint.blockZ();
            // Check if we can move forward
            if (replaceable(query.stateAt(dx, dy, dz, 0))) {
                // We can move - clear and wake up the target position
                return List.of(new Action(
                        null, true,
                        wakeupPoints(pushDir),
                        null,
                        0
                ));
            } else {
                // We hit a block - wait for propagation and empty space
                return List.of(new Action(
                        null, false,
                        wakeupPoints(pushDir),
                        null,
                        0
                ));
            }
        } else {
            // Check if we should receive a push from any neighbor
            final long blockState = query.state(0);
            for (Direction dir : DIRECTIONS) {
                final Point dirPoint = dir.vec();
                final int nx = dirPoint.blockX(), ny = dirPoint.blockY(), nz = dirPoint.blockZ();
                // Check if neighbor is pushing toward us
                final long neighborDirValue = query.stateAt(nx, ny, nz, directionIndex);
                if (neighborDirValue == 0) continue;
                if (direction(neighborDirValue) == dir.opposite()) {
                    final long neighborStrength = query.stateAt(nx, ny, nz, strengthIndex);
                    if (replaceable(blockState) && neighborStrength > 0) {
                        // Become the neighbor's block
                        long[] neighborStates = query.queryIndexes(nx, ny, nz);
                        final long newStrength = neighborStrength - 1;
                        if (newStrength > 0) {
                            neighborStates[directionIndex] = neighborDirValue;
                            neighborStates[strengthIndex] = newStrength;
                        } else {
                            neighborStates[directionIndex] = 0;
                            neighborStates[strengthIndex] = 0;
                        }
                        Int2LongMap updatedState = new Int2LongOpenHashMap(neighborStates.length);
                        for (int i = 0; i < neighborStates.length; i++) {
                            updatedState.put(i, neighborStates[i]);
                        }
                        return List.of(new Action(
                                updatedState, true,
                                wakeupPoints(dir),
                                null,
                                0
                        ));
                    } else {
                        // Block should store push direction and propagate it
                        Int2LongMap updatedState = new Int2LongOpenHashMap();
                        updatedState.put(directionIndex, neighborDirValue);
                        updatedState.put(strengthIndex, neighborStrength);
                        return List.of(new Action(
                                updatedState, false,
                                wakeupPoints(dir),
                                null,
                                0
                        ));
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean tracked(int state) {
        return false; // Tracking doesn't depend on block state, but on push properties
    }

    @Override
    public List<State> states() {
        return List.of(PUSH_DIRECTION, PUSH_STRENGTH);
    }
}
