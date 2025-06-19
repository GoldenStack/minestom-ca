package net.minestom.ca.rules;

import net.minestom.ca.Automata.CellRule;
import net.minestom.ca.Automata.Context;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Neighbors;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RedstoneWireRule implements CellRule {

    // Direction pairs for opposite sides
    private static final Map<String, String> OPPOSITE_DIRECTIONS = Map.of(
            "north", "south",
            "south", "north",
            "east", "west",
            "west", "east"
    );

    @Override
    public void init(Map<State, Integer> mapping) {
    }

    @Override
    public Action process(Context context, Query query) {
        final Block currentBlock = query.block();
        if (!currentBlock.compare(Block.REDSTONE_WIRE)) {
            return null;
        }

        // Calculate basic connections
        Map<String, String> initialConnections = Map.of(
                "east", connectionType(query, Neighbors.EAST),
                "north", connectionType(query, Neighbors.NORTH),
                "south", connectionType(query, Neighbors.SOUTH),
                "west", connectionType(query, Neighbors.WEST)
        );

        // Apply special rules for isolated wires and single-connection wires
        Map<String, String> finalConnections = applySpecialRules(initialConnections);

        // Only update if any property changed
        if (!hasChanged(currentBlock, finalConnections)) {
            return null;
        }

        // Create updated block with new properties
        return Action.action(action -> {
            action.properties(finalConnections);
            action.wakeup(Neighbors.NEUMANN_2D_SELF);
        });
    }

    private Map<String, String> applySpecialRules(Map<String, String> connections) {
        // Create a mutable copy of the connections
        Map<String, String> result = new HashMap<>(connections);

        // Count non-"none" connections
        int connectionCount = 0;
        String connectedDirection = null;

        for (Map.Entry<String, String> entry : connections.entrySet()) {
            if (!entry.getValue().equals("none")) {
                connectionCount++;
                connectedDirection = entry.getKey();
            }
        }

        // Case 1: No connections - set all to "side"
        if (connectionCount == 0) {
            for (String direction : connections.keySet()) {
                result.put(direction, "side");
            }
            return result;
        }

        // Case 2: Single connection - set opposite to "side"
        if (connectionCount == 1 && connectedDirection != null) {
            String oppositeDirection = OPPOSITE_DIRECTIONS.get(connectedDirection);
            result.put(oppositeDirection, "side");
        }

        return result;
    }

    private String connectionType(Query query, Point direction) {
        final int dx = direction.blockX();
        final int dy = direction.blockY();
        final int dz = direction.blockZ();
        final Block neighborBlock = query.blockAt(dx, dy, dz);
        if (connectable(neighborBlock)) {
            return "side";
        }
        final Block blockUp = query.blockAt(dx, dy + 1, dz);
        if (connectable(blockUp) && neighborBlock.isSolid()) {
            return "up";
        }
        final Block blockDown = query.blockAt(dx, dy - 1, dz);
        if (connectable(blockDown) && !neighborBlock.isSolid()) {
            return "side";
        }

        // No connection
        return "none";
    }

    private boolean connectable(Block block) {
        return block.compare(Block.REDSTONE_WIRE) || block.compare(Block.REDSTONE_BLOCK);
    }

    private boolean hasChanged(Block currentBlock, Map<String, String> newConnections) {
        for (Map.Entry<String, String> entry : newConnections.entrySet()) {
            String currentValue = currentBlock.getProperty(entry.getKey());
            if (currentValue == null || !currentValue.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Interest> interests() {
        return List.of(Interest.block(Block.REDSTONE_WIRE));
    }

    @Override
    public Set<State> states() {
        return Set.of();
    }
}
