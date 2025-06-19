package net.minestom.ca.rules;

import net.minestom.ca.Neighbors;
import net.minestom.ca.States;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.minestom.ca.Automata.*;

public final class PistonRule implements CellRule {
    private static final Action SET_AIR_ACTION = Action.action(action -> action.state(0, 0));
    private int pushDirectionIndex, pushStrengthIndex;

    @Override
    public void init(Map<State, Integer> mapping) {
        this.pushDirectionIndex = mapping.get(States.PUSH_DIRECTION);
        this.pushStrengthIndex = mapping.get(States.PUSH_STRENGTH);
    }

    Action processPiston(Query query) {
        final boolean isPowered = isRedstonePowered(query);
        final boolean extended = query.block().getProperty("extended").equals("true");
        if (isPowered != extended) {
            return Action.action(action -> action.property("extended", String.valueOf(isPowered)));
        }
        return null;
    }

    private boolean compareVectors(Vec v1, Vec v2) {
        final double epsilon = Vec.EPSILON;
        return Math.abs(v1.x() - v2.x()) < epsilon &&
                Math.abs(v1.y() - v2.y()) < epsilon &&
                Math.abs(v1.z() - v2.z()) < epsilon;
    }

    Action processBlock(Block block, Query query) {
        // Find if there is any near piston block waiting to extend
        for (Vec neighbor : Neighbors.NEUMANN_3D) {
            final int nx = neighbor.blockX();
            final int ny = neighbor.blockY();
            final int nz = neighbor.blockZ();

            final Block neighborBlock = query.blockAt(nx, ny, nz);
            if (isPiston(neighborBlock)) {
                final boolean extended = neighborBlock.getProperty("extended").equals("true");
                final String facing = neighborBlock.getProperty("facing");
                final Direction direction = facingDirection(facing);
                // Check if the piston is extending towards this block
                final boolean towardsBlock = extended && compareVectors(direction.vec(), neighbor.neg());
                if (towardsBlock) {
                    if (block.isAir()) {
                        // Become piston head
                        final Block pistonHead = Block.PISTON_HEAD.withProperty("facing", facing);
                        return Action.action(action -> {
                            action.state(0, pistonHead.stateId());
                            action.condition(0, 0);
                        });
                    } else {
                        // Apply direction/strength states if not already set
                        return Action.action(action -> {
                            action.state(pushDirectionIndex, direction.ordinal() + 1);
                            action.state(pushStrengthIndex, 1);

                            action.condition(pushDirectionIndex, 0);
                            action.condition(pushStrengthIndex, 0);
                        });
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Action process(Context context, Query query) {
        final Block block = query.block();
        if (block.compare(Block.PISTON_HEAD)) {
            // Change to air if there is no piston block behind
            final String facing = block.getProperty("facing");
            if (facing == null) return null;
            final Direction direction = facingDirection(facing);
            final Vec behind = direction.opposite().vec();
            final Block pistonBlock = query.blockAt(behind.blockX(), behind.blockY(), behind.blockZ());
            if (!isPiston(pistonBlock) || pistonBlock.getProperty("extended").equals("false")) {
                return SET_AIR_ACTION;
            } else {
                return null;
            }
        }
        if (isPiston(block)) return processPiston(query);
        return processBlock(block, query);
    }

    private boolean isPiston(Block block) {
        return block.compare(Block.PISTON) || block.compare(Block.STICKY_PISTON);
    }

    private boolean isRedstonePowered(Query query) {
        for (Point neighbor : Neighbors.NEUMANN_2D) {
            final int nx = neighbor.blockX();
            final int ny = neighbor.blockY();
            final int nz = neighbor.blockZ();
            final Block neighborBlock = query.blockAt(nx, ny, nz);
            if (neighborBlock.compare(Block.REDSTONE_WIRE)) {
                // Check if the redstone wire has power
                final String powerProp = neighborBlock.getProperty("power");
                if (powerProp != null && Integer.parseInt(powerProp) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private Direction facingDirection(String facing) {
        return Direction.valueOf(facing.toUpperCase(Locale.ROOT));
    }

    @Override
    public List<Interest> interests() {
        return List.of(
                Interest.block(Block.PISTON),
                Interest.block(Block.PISTON_HEAD),
                Interest.block(Block.STICKY_PISTON),
                Interest.block(Block.REDSTONE_WIRE)
        );
    }

    @Override
    public Set<State> states() {
        return Set.of();
    }
}
