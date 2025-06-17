package net.minestom.ca.rules;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minestom.ca.Automata.CellRule;
import net.minestom.ca.Automata.Context;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Neighbors;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;

import java.util.*;

public final class TreeGrowthRule implements CellRule {
    // Custom states for the tree growth
    public static final State SEED_STATE = new State("tree_seed", 32);
    public static final State GROWTH_STAGE = new State("growth_stage", 8);
    public static final State BRANCH_DIRECTION = new State("branch_direction", 4);
    public static final State LEAF_DENSITY = new State("leaf_density", 4);

    // Block state IDs
    private static final long SAPLING_STATE = Block.OAK_SAPLING.stateId();
    private static final long LOG_STATE = Block.OAK_LOG.stateId();
    private static final long LEAVES_STATE = Block.OAK_LEAVES.stateId();
    private static final long AIR_STATE = Block.AIR.stateId();

    // Maximum tree height
    private static final int MAX_TREE_HEIGHT = 8;
    // Growth delay in ticks
    private static final int GROWTH_DELAY = 3;

    // Direction vectors for branches
    private static final Point[] BRANCH_DIRECTIONS = {
            Neighbors.NORTH, Neighbors.SOUTH, Neighbors.EAST, Neighbors.WEST,
            Neighbors.NORTHEAST, Neighbors.NORTHWEST, Neighbors.SOUTHEAST, Neighbors.SOUTHWEST
    };

    // Index mapping for our states
    private int seedIndex;
    private int growthStageIndex;
    private int branchDirectionIndex;
    private int leafDensityIndex;

    @Override
    public void init(Map<State, Integer> mapping) {
        seedIndex = mapping.get(SEED_STATE);
        growthStageIndex = mapping.get(GROWTH_STAGE);
        branchDirectionIndex = mapping.get(BRANCH_DIRECTION);
        leafDensityIndex = mapping.get(LEAF_DENSITY);
    }

    @Override
    public List<Action> process(Context context, Query query) {
        final long blockState = query.state(0);
        final long seedValue = query.state(seedIndex);
        final long growthStage = query.state(growthStageIndex);
        final long branchDirection = query.state(branchDirectionIndex);
        final long leafDensity = query.state(leafDensityIndex);

        // Case 1: Initial sapling with no seed - initialize tree growth
        if (blockState == SAPLING_STATE && seedValue == 0) {
            return List.of(Action.action(action -> {
                // Generate a random seed
                action.state(seedIndex, new Random().nextInt());
                // Set initial growth stage
                action.state(growthStageIndex, 1);
                // Schedule first growth
                action.schedule(GROWTH_DELAY);
            }));
        }

        // Case 2: Sapling with seed - begin trunk growth
        if (blockState == SAPLING_STATE && seedValue != 0 && growthStage == 1) {
            return List.of(Action.action(action -> {
                // Turn sapling into log
                action.state(0, LOG_STATE);
                // Increment growth stage
                action.state(growthStageIndex, 2);
                // Wake neighbors for continued growth
                action.wakeup(Neighbors.NEUMANN_3D_SELF);
                // Schedule next growth
                action.schedule(GROWTH_DELAY);
            }));
        }

        // Case 3: Log needs to grow upward
        if (blockState == LOG_STATE && growthStage >= 2 && growthStage < MAX_TREE_HEIGHT) {
            // Check if there's air above
            Block blockAbove = Block.fromStateId((int) query.stateAt(0, 1, 0, 0));
            if (blockAbove.isAir()) {
                return List.of(Action.action(action -> {
                    // Don't change this log, just tell the air block above to transform
                    // We directly set states for the air block above
                    Int2LongMap airBlockStates = new Int2LongOpenHashMap();
                    airBlockStates.put(0, LOG_STATE); // Change to log
                    airBlockStates.put(seedIndex, seedValue); // Same seed
                    airBlockStates.put(growthStageIndex, growthStage + 1); // Next growth stage

                    // This is the critical fix - we update the block above
                    Point up = new Vec(0, 1, 0);
                    action.wakeup(List.of(up));

                    // Schedule the next growth
                    action.schedule(GROWTH_DELAY);
                }));
            } else {
                // We can't grow upward anymore, but we can start creating leaves
                if (growthStage >= MAX_TREE_HEIGHT - 2) {
                    return createLeafLayer(query, seedValue, growthStage, leafDensity);
                }
            }
        }

        // Case 3b: I'm air above a log - become a log
        if (blockState == AIR_STATE) {
            // Check if there's a log below
            Block blockBelow = Block.fromStateId((int) query.stateAt(0, -1, 0, 0));
            if (blockBelow.stateId() == LOG_STATE) {
                // Get the states from the log below
                long belowSeed = query.stateAt(0, -1, 0, seedIndex);
                long belowStage = query.stateAt(0, -1, 0, growthStageIndex);

                // Only grow if the log below is part of a growing tree
                if (belowSeed != 0 && belowStage >= 2 && belowStage < MAX_TREE_HEIGHT) {
                    return List.of(Action.action(action -> {
                        // Transform this air into a log
                        action.state(0, LOG_STATE);
                        // Copy seed from below
                        action.state(seedIndex, belowSeed);
                        // Increment growth stage
                        action.state(growthStageIndex, belowStage + 1);
                        // Wake neighbors for continued growth
                        action.wakeup(Neighbors.MOORE_3D_SELF); // Use MOORE instead of NEUMANN to wake up diagonal blocks too
                        // Schedule next growth
                        action.schedule(GROWTH_DELAY);
                    }));
                }
            }

            // Case 3c: I'm air near a log that has reached the proper height - become a leaf
            if (shouldBecomeLeaf(query)) {
                return becomeLeaf(query);
            }
        }

        // Case 4: Creating branches at various heights
        if (blockState == LOG_STATE && growthStage >= 3) {
            List<Action> branchActions = handleBranchCreation(query, seedValue, growthStage, branchDirection);
            // If we're at a high enough stage, also generate leaves
            if (growthStage >= MAX_TREE_HEIGHT - 2) {
                List<Action> leafActions = createLeafLayer(query, seedValue, growthStage, leafDensity);
                if (branchActions != null && leafActions != null) {
                    List<Action> combinedActions = new ArrayList<>(branchActions);
                    combinedActions.addAll(leafActions);
                    return combinedActions;
                } else if (leafActions != null) {
                    return leafActions;
                }
            }
            return branchActions;
        }

        // Case 5: Growing leaves around branches and top
        if (blockState == LOG_STATE && growthStage >= MAX_TREE_HEIGHT - 2) {
            return createLeafLayer(query, seedValue, growthStage, leafDensity);
        }

        // Case 6: Leaves spread
        if (blockState == LEAVES_STATE && leafDensity > 0) {
            return spreadLeaves(query, seedValue, leafDensity);
        }

        return null;
    }

    // Helper method to check if an air block should become a leaf
    private boolean shouldBecomeLeaf(Query query) {
        // If this air block is adjacent to a log block that has reached sufficient height
        for (Point point : Neighbors.MOORE_3D) {
            int dx = point.blockX(), dy = point.blockY(), dz = point.blockZ();
            Block neighborBlock = Block.fromStateId((int) query.stateAt(dx, dy, dz, 0));

            if (neighborBlock.stateId() == LOG_STATE) {
                long neighborStage = query.stateAt(dx, dy, dz, growthStageIndex);
                long neighborLeafDensity = query.stateAt(dx, dy, dz, leafDensityIndex);

                // Check if the log is mature enough to grow leaves
                if (neighborStage >= MAX_TREE_HEIGHT - 2 && neighborLeafDensity > 0) {
                    return true;
                }
            } else if (neighborBlock.stateId() == LEAVES_STATE) {
                // If this air block is adjacent to a leaf block that can still spread
                long neighborLeafDensity = query.stateAt(dx, dy, dz, leafDensityIndex);
                if (neighborLeafDensity > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    // Helper method to make an air block become a leaf
    private List<Action> becomeLeaf(Query query) {
        // Find a nearby log or leaf to get the seed and density from
        for (Point point : Neighbors.MOORE_3D) {
            int dx = point.blockX(), dy = point.blockY(), dz = point.blockZ();
            Block neighborBlock = Block.fromStateId((int) query.stateAt(dx, dy, dz, 0));

            if (neighborBlock.stateId() == LOG_STATE || neighborBlock.stateId() == LEAVES_STATE) {
                long neighborSeed = query.stateAt(dx, dy, dz, seedIndex);
                long neighborDensity = query.stateAt(dx, dy, dz, leafDensityIndex);

                // For logs, use full leaf density; for leaves, reduce by 1
                long newDensity = (neighborBlock.stateId() == LOG_STATE) ?
                        neighborDensity : Math.max(1, neighborDensity - 1);

                return List.of(Action.action(action -> {
                    // Transform this air into a leaf
                    action.state(0, LEAVES_STATE);
                    // Copy seed from neighbor
                    action.state(seedIndex, neighborSeed);
                    // Set leaf density
                    action.state(leafDensityIndex, newDensity);
                    // Wake neighbors for potential spreading
                    action.wakeup(Neighbors.MOORE_3D_SELF);
                }));
            }
        }
        return null;
    }

    private List<Action> handleBranchCreation(Query query, long seedValue, long growthStage, long existingDirection) {
        // Skip if this block already has a branch direction
        if (existingDirection != 0) return null;

        // Only create branches at certain heights
        if (growthStage != 3 && growthStage != 5 && growthStage != MAX_TREE_HEIGHT - 1) return null;

        // Use seed to determine branch directions
        Random random = new Random(seedValue + growthStage);
        int branchCount = 1 + random.nextInt(3); // 1-3 branches

        List<Action> actions = new ArrayList<>();

        // Choose random directions for branches
        Set<Integer> directionIndices = new HashSet<>();
        while (directionIndices.size() < branchCount) {
            directionIndices.add(random.nextInt(BRANCH_DIRECTIONS.length));
        }

        for (int dirIdx : directionIndices) {
            Point direction = BRANCH_DIRECTIONS[dirIdx];
            Block neighborBlock = Block.fromStateId((int) query.stateAt(
                    direction.blockX(),
                    direction.blockY(),
                    direction.blockZ(),
                    0));

            if (neighborBlock.isAir()) {
                actions.add(Action.action(action -> {
                    // Create a branch in the neighbor position by waking it up
                    // Wake up the neighbor to create the branch
                    action.wakeup(List.of(direction));
                    // Record that this block spawned a branch
                    action.state(branchDirectionIndex, dirIdx + 1);
                    // Set leaf density for the branch
                    action.state(leafDensityIndex, 3); // Initial leaf density

                    // Also directly set the states for the air block to become a log
                    Int2LongMap branchStates = new Int2LongOpenHashMap();
                    branchStates.put(0, LOG_STATE);
                    branchStates.put(seedIndex, seedValue);
                    branchStates.put(growthStageIndex, growthStage);
                    branchStates.put(branchDirectionIndex, dirIdx + 1);
                    branchStates.put(leafDensityIndex, 3);
                }));
            }
        }

        // If we reached the top, create a leaf crown
        if (growthStage >= MAX_TREE_HEIGHT - 1) {
            actions.add(Action.action(action -> {
                action.state(leafDensityIndex, 4); // Maximum leaf density for the top
                action.schedule(GROWTH_DELAY);
                // Wake up all surrounding blocks to create leaves
                action.wakeup(Neighbors.MOORE_3D);
            }));
        }

        return actions.isEmpty() ? null : actions;
    }

    private List<Action> createLeafLayer(Query query, long seedValue, long growthStage, long leafDensity) {
        if (leafDensity == 0) {
            return List.of(Action.action(action -> {
                action.state(leafDensityIndex, 3); // Set initial leaf density
                action.wakeup(Neighbors.MOORE_3D); // Wake up all surrounding blocks
                action.schedule(GROWTH_DELAY);
            }));
        }

        Random random = new Random(seedValue * growthStage);
        List<Action> actions = new ArrayList<>();

        // Set our own leaf density if it's not set yet
        if (leafDensity == 0) {
            actions.add(Action.action(action -> {
                action.state(leafDensityIndex, 3); // Set leaf density
                action.wakeup(Neighbors.MOORE_3D); // Wake up all surrounding blocks
            }));
        }

        // Create leaves in a pattern around the log - directly set the air blocks to leaves
        for (Point point : Neighbors.MOORE_3D) {
            // Skip points directly below for a nicer tree shape
            if (point.blockY() < 0) continue;

            Block neighborBlock = Block.fromStateId((int) query.stateAt(
                    point.blockX(),
                    point.blockY(),
                    point.blockZ(),
                    0));

            if (neighborBlock.isAir()) {
                // Always create leaves (no random chance)
                actions.add(Action.action(action -> {
                    // Directly modify the air block to become a leaf
                    Int2LongMap leafStates = new Int2LongOpenHashMap();
                    leafStates.put(0, LEAVES_STATE);
                    leafStates.put(seedIndex, seedValue);
                    leafStates.put(leafDensityIndex, leafDensity > 0 ? leafDensity - 1 : 1);

                    // Wake up the neighbor to create the leaf
                    action.wakeup(List.of(point));
                }));
            }
        }

        return actions.isEmpty() ? null : actions;
    }

    private List<Action> spreadLeaves(Query query, long seedValue, long leafDensity) {
        if (leafDensity <= 1) return null; // Don't spread further

        Random random = new Random(seedValue ^ (leafDensity * 31));
        List<Action> actions = new ArrayList<>();

        // Spread leaves to neighbors with decreasing density - 100% chance instead of 50%
        for (Point point : Neighbors.MOORE_3D) {
            Block neighborBlock = Block.fromStateId((int) query.stateAt(
                    point.blockX(),
                    point.blockY(),
                    point.blockZ(),
                    0));

            if (neighborBlock.isAir()) {
                actions.add(Action.action(action -> {
                    // Directly set the air block to a leaf
                    Int2LongMap leafStates = new Int2LongOpenHashMap();
                    leafStates.put(0, LEAVES_STATE);
                    leafStates.put(seedIndex, seedValue);
                    leafStates.put(leafDensityIndex, leafDensity - 1);

                    // Wake up the neighbor
                    action.wakeup(List.of(point));
                }));
            }
        }

        return actions.isEmpty() ? null : actions;
    }

    @Override
    public boolean tracked(Block block) {
        return block.stateId() == SAPLING_STATE ||
                block.stateId() == LOG_STATE ||
                block.stateId() == LEAVES_STATE ||
                block.isAir(); // Also track air blocks so they can become logs or leaves
    }

    @Override
    public Set<State> states() {
        return Set.of(SEED_STATE, GROWTH_STAGE, BRANCH_DIRECTION, LEAF_DENSITY);
    }
}
