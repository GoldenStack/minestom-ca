package net.minestom.ca.rules;

import net.minestom.ca.Automata.Context;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Automata.Rule;
import net.minestom.ca.Neighbors;
import net.minestom.ca.States;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockPusher implements Rule {
    private static final Rule.Info INFO = new Info(
            "Block Pusher",
            "A rule that simulates a block pushing mechanism, allowing blocks to be pushed in a specified direction with a defined strength.",
            List.of(
                    Interest.state(States.PUSH_DIRECTION),
                    Interest.state(States.PUSH_STRENGTH)
            ),
            Set.of(
                    States.PUSH_DIRECTION,
                    States.PUSH_STRENGTH
            )
    );

    private static final long AIR_STATE = Block.AIR.stateId();
    private static final Direction[] DIRECTIONS = Direction.values();

    private int directionIndex;
    private int strengthIndex;

    static Direction direction(long value) {
        if (value == 0) return null;
        return DIRECTIONS[(int) (value - 1)];
    }

    @Override
    public void init(Map<State, Integer> mapping) {
        directionIndex = mapping.get(States.PUSH_DIRECTION);
        strengthIndex = mapping.get(States.PUSH_STRENGTH);
    }

    boolean replaceable(long state) {
        return state == AIR_STATE;
    }

    private static final List<Vec>[] WAKEUP_POINTS_CACHE = new List[Direction.values().length];

    static {
        for (Direction dir : DIRECTIONS) {
            WAKEUP_POINTS_CACHE[dir.ordinal()] = List.of(Neighbors.SELF, dir.vec(), dir.opposite().vec());
        }
    }

    List<Vec> wakeupPoints(Direction direction) {
        return WAKEUP_POINTS_CACHE[direction.ordinal()];
    }

    @Override
    public Action process(Context context, Query query) {
        final long dirValue = query.state(directionIndex);
        if (dirValue != 0) {
            // We're a block being pushed
            final Direction pushDir = direction((int) dirValue);
            assert pushDir != null;
            final Point dirPoint = pushDir.vec();
            // Check if we can move forward
            if (replaceable(query.stateAt(dirPoint, 0))) {
                // We can move - clear and wake up the target position
                return Action.action(action -> {
                    action.clear();
                    action.wakeup(wakeupPoints(pushDir));
                });
            } else {
                // We hit a block - wait for propagation and empty space
                return Action.action(action -> action.wakeup(wakeupPoints(pushDir)));
            }
        } else {
            // Check if we should receive a push from any neighbor
            final Direction neighborDir = query.findFirstPresent(directionIndex, Neighbors.NEUMANN_3D,
                    (x, y, z, value) -> {
                        final Direction dir = direction(value);
                        assert dir != null;
                        final Vec vec = dir.vec();
                        final boolean zeroX = vec.blockX() + x == 0;
                        final boolean zeroY = vec.blockY() + y == 0;
                        final boolean zeroZ = vec.blockZ() + z == 0;
                        return (zeroX && zeroY && zeroZ) ? dir : null;
                    });
            if (neighborDir == null) return null;
            final Direction dir = neighborDir.opposite();
            final Vec dirPoint = dir.vec();
            final long neighborDirValue = neighborDir.ordinal() + 1;
            final long neighborStrength = query.stateAt(dirPoint, strengthIndex);
            final long blockState = query.state(0);
            if (replaceable(blockState) && neighborStrength > 0) {
                // Become the neighbor's block
                final long[] neighborStates = query.queryIndexes(dirPoint);
                final long newStrength = neighborStrength - 1;
                return Action.action(action -> {
                    action.clear();
                    action.copy(neighborStates);
                    if (newStrength > 0) {
                        action.state(directionIndex, neighborDirValue);
                        action.state(strengthIndex, newStrength);
                    } else {
                        action.state(directionIndex, 0);
                        action.state(strengthIndex, 0);
                    }
                    action.wakeup(wakeupPoints(dir));
                });
            } else {
                // Block should store push direction and propagate it
                return Action.action(action -> {
                    action.state(directionIndex, neighborDirValue);
                    action.state(strengthIndex, neighborStrength);
                    action.wakeup(wakeupPoints(dir));
                });
            }
        }
    }

    @Override
    public Info info() {
        return INFO;
    }
}
