package net.minestom.ca.rules;

import net.minestom.ca.Automata;
import net.minestom.ca.Neighbors;
import net.minestom.ca.States;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.minestom.ca.Automata.*;

public final class PistonRule implements Rule {
    private static final Automata.Rule.Info INFO = new Info(
            "Piston Mechanism",
            "Simulates the behavior of pistons and sticky pistons in Minecraft, allowing blocks to be pushed or pulled.",
            List.of(
                    Interest.block(Block.PISTON),
                    Interest.block(Block.STICKY_PISTON),
                    Interest.block(Block.PISTON_HEAD),
                    Interest.state(States.DATA)
            ),
            Set.of()
    );

    private static final Action SET_AIR_ACTION = Action.action(action -> action.state(0, 0));
    private int pushDirectionIndex, pushStrengthIndex;
    private int dataIndex;

    @Override
    public void init(Map<State, Integer> mapping) {
        this.pushDirectionIndex = mapping.get(States.PUSH_DIRECTION);
        this.pushStrengthIndex = mapping.get(States.PUSH_STRENGTH);
        this.dataIndex = mapping.get(States.DATA);
    }

    Action processPistonHead(Block block, Query query) {
        // Change to air if there is no piston block behind or if piston is not powered
        final String facing = block.getProperty("facing");
        final Direction direction = facingDirection(facing);
        final Vec behind = direction.opposite().vec();
        final Block pistonBlock = query.blockAt(behind);
        return !isPiston(pistonBlock) || !poweredAt(query.offset(behind)) ? SET_AIR_ACTION : null;
    }

    Action processPiston(Block block, Query query) {
        final boolean isPowered = poweredAt(query);
        final boolean extended = block.getProperty("extended").equals("true");
        if (isPowered != extended) {
            return Action.action(action -> action.property("extended", String.valueOf(isPowered)));
        }
        return null;
    }

    Action processBlock(Block block, Query query) {
        // Find if there is any near piston block waiting to extend
        for (Vec neighbor : Neighbors.NEUMANN_3D) {
            final Block neighborBlock = query.blockAt(neighbor);
            if (!isPiston(neighborBlock)) continue;
            final String facing = neighborBlock.getProperty("facing");
            final Direction direction = facingDirection(facing);
            final boolean towardsBlock = direction.vec().samePoint(neighbor.neg());
            if (!towardsBlock) continue;
            final boolean powered = poweredAt(query.offset(neighbor));
            if (!powered) continue;
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

                    action.condition(0, block.stateId());
                    action.condition(pushDirectionIndex, 0);
                    action.condition(pushStrengthIndex, 0);
                });
            }
        }
        return null;
    }

    @Override
    public Action process(Context context, Query query) {
        final Block block = query.block();
        if (block.compare(Block.PISTON_HEAD)) return processPistonHead(block, query);
        if (isPiston(block)) return processPiston(block, query);
        return processBlock(block, query);
    }

    private boolean isPiston(Block block) {
        return block.compare(Block.PISTON) || block.compare(Block.STICKY_PISTON);
    }

    private boolean poweredAt(Query query) {
        return query.anyPresent(dataIndex, Neighbors.NEUMANN_2D);
    }

    private Direction facingDirection(String facing) {
        return Direction.valueOf(facing.toUpperCase(Locale.ROOT));
    }

    @Override
    public Info info() {
        return INFO;
    }
}
