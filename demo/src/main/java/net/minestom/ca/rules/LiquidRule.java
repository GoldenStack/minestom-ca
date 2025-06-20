package net.minestom.ca.rules;

import net.minestom.ca.Automata;
import net.minestom.ca.Automata.Context;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Automata.Rule;
import net.minestom.ca.Neighbors;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LiquidRule implements Rule {
    private static final Automata.Rule.Info INFO = new Automata.Rule.Info(
            "Liquid Rule",
            "Simulates the behavior of liquids (water and lava) in a cellular automaton.",
            List.of(
                    Interest.block(Block.WATER),
                    Interest.block(Block.LAVA)
            ),
            Set.of()
    );

    @Override
    public void init(Map<State, Integer> mapping) {
    }

    private boolean isLiquid(Block block) {
        return block.compare(Block.WATER) || block.compare(Block.LAVA);
    }

    private boolean isSolid(Block block) {
        return !block.isAir() && !isLiquid(block);
    }

    private int getLiquidLevel(Block block) {
        if (!isLiquid(block)) return -1;
        final String levelStr = block.getProperty("level");
        return levelStr == null ? 0 : Integer.parseInt(levelStr); // Default to 0 for base "minecraft:water"
    }

    private Block getLiquidBlock(Block liquidBaseType, int level) {
        level = Math.clamp(level, 0, 7);
        return liquidBaseType.withProperty("level", String.valueOf(level));
    }

    private boolean isReplaceableByLiquid(Block block) {
        return block.isAir(); // Add other replaceable blocks like tall_grass if needed
    }

    private boolean areSameLiquidType(Block block1, Block block2) {
        if (!isLiquid(block1) || !isLiquid(block2)) return false;
        return (block1.compare(Block.WATER) && block2.compare(Block.WATER)) ||
                (block1.compare(Block.LAVA) && block2.compare(Block.LAVA));
    }

    @Override
    public Action process(Context context, Query query) {
        final Block currentBlock = query.block();
        // Rule 1: If I am liquid and can flow downwards, I become air.
        // "Flow downwards" means into air/replaceable OR into a weaker liquid of the same type.
        if (isLiquid(currentBlock)) {
            Block liquidBaseType = currentBlock.compare(Block.WATER) ? Block.WATER : Block.LAVA;
            int currentLvl = getLiquidLevel(currentBlock);

            Point downPos = Neighbors.DOWN;
            Block blockBelow = query.blockAt(downPos);

            boolean canFlowIntoReplaceable = isReplaceableByLiquid(blockBelow);
            boolean canFlowIntoWeakerSameLiquid = false;
            if (!canFlowIntoReplaceable && blockBelow.compare(liquidBaseType)) {
                int belowLvl = getLiquidLevel(blockBelow);
                // Liquid flows from lower level numbers (stronger) to higher level numbers (weaker)
                // e.g. source (0) flows into L1 (1>0). Flowing L3 into L5 (5>3).
                if (belowLvl > currentLvl) {
                    canFlowIntoWeakerSameLiquid = true;
                }
            }

            if (canFlowIntoReplaceable || canFlowIntoWeakerSameLiquid) {
                return Action.action(b -> {
                    b.state(0, Block.AIR.stateId());
                    b.wakeup(Neighbors.NEUMANN_3D_SELF);
                });
            }
        }

        // Rule 2: If I am replaceable, and liquid is above me, I become that liquid.
        if (isReplaceableByLiquid(currentBlock)) {
            Point upPos = Neighbors.UP;
            Block blockAbove = query.blockAt(upPos);

            if (isLiquid(blockAbove)) {
                Block liquidBaseTypeAbove = blockAbove.compare(Block.WATER) ? Block.WATER : Block.LAVA;
                int levelAbove = getLiquidLevel(blockAbove);
                Block newSelf = getLiquidBlock(liquidBaseTypeAbove, levelAbove);

                return Action.action(b -> {
                    b.state(0, newSelf.stateId());
                    b.wakeup(Neighbors.NEUMANN_3D_SELF);
                });
            }
        }

        // New Rule: If I am liquid and there's same type of liquid above me, take the strongest level
        if (isLiquid(currentBlock)) {
            Point upPos = Neighbors.UP;
            Block blockAbove = query.blockAt(upPos);

            if (areSameLiquidType(currentBlock, blockAbove)) {
                int currentLevel = getLiquidLevel(currentBlock);
                int aboveLevel = getLiquidLevel(blockAbove);

                // If the liquid above has a stronger level (lower number), adopt that level
                if (aboveLevel < currentLevel) {
                    Block liquidBaseType = currentBlock.compare(Block.WATER) ? Block.WATER : Block.LAVA;
                    Block newSelf = getLiquidBlock(liquidBaseType, aboveLevel);

                    return Action.action(b -> {
                        b.state(0, newSelf.stateId());
                        b.wakeup(Neighbors.NEUMANN_3D_SELF); // Update surrounding blocks
                    });
                }
            }
        }

        // Rule 3: If I am replaceable/weaker liquid, and can be spread into from sides.
        // (Only if Rule 2 didn't apply)
        boolean canBeSpreadInto = isReplaceableByLiquid(currentBlock);
        int currentBlockLevelIfLiquid = -1;
        Block currentBlockBaseTypeIfLiquid = null;

        if (isLiquid(currentBlock)) {
            canBeSpreadInto = true; // A liquid block can be spread into if the incoming flow is "stronger"
            currentBlockLevelIfLiquid = getLiquidLevel(currentBlock);
            currentBlockBaseTypeIfLiquid = currentBlock.compare(Block.WATER) ? Block.WATER : Block.LAVA;
        }

        if (canBeSpreadInto) {
            // Check if there's a solid block below (necessary for horizontal spread)
            final Point downPos = Neighbors.DOWN;
            final Block blockBelow = query.blockAt(downPos);

            // If block below is not solid and we're trying to spread horizontally, return.
            // This is the key change - only allow horizontal spread if:
            // 1. There's a solid block below OR
            // 2. Current block is already liquid (for updating existing flows)
            final boolean hasSupport = isSolid(blockBelow) || isLiquid(currentBlock);

            // If there's no support and this is empty space, liquid should fall straight down, not spread horizontally
            if (!hasSupport && isReplaceableByLiquid(currentBlock)) {
                return null;
            }

            int bestProspectiveLevel = 8; // Lower is "stronger"
            Block bestLiquidBaseTypeToBecome = null;

            for (Point sidePos : Neighbors.NEUMANN_2D) { // N, S, E, W
                final Block adjBlock = query.blockAt(sidePos);
                if (isLiquid(adjBlock)) {
                    final int adjLevel = getLiquidLevel(adjBlock);
                    Block adjBaseType = adjBlock.compare(Block.WATER) ? Block.WATER : Block.LAVA;

                    int prospectiveLevelFromThisSide;
                    if (adjLevel == 0) { // Source block spreads as level 1
                        prospectiveLevelFromThisSide = 1;
                    } else {
                        prospectiveLevelFromThisSide = adjLevel + 1;
                    }

                    boolean canSpreadAtThisLevel;
                    if (adjBaseType.compare(Block.WATER)) {
                        canSpreadAtThisLevel = prospectiveLevelFromThisSide < 8; // Water spreads up to level 7
                    } else { // LAVA
                        canSpreadAtThisLevel = prospectiveLevelFromThisSide < 4; // Lava spreads up to level 3
                    }

                    if (canSpreadAtThisLevel) {
                        if (prospectiveLevelFromThisSide < bestProspectiveLevel) {
                            bestProspectiveLevel = prospectiveLevelFromThisSide;
                            bestLiquidBaseTypeToBecome = adjBaseType;
                        } else if (prospectiveLevelFromThisSide == bestProspectiveLevel) {
                            // Tie-breaking: prefer water, or prefer same type if current is already liquid
                            if (bestLiquidBaseTypeToBecome == null ||
                                    (adjBaseType.compare(Block.WATER) && !bestLiquidBaseTypeToBecome.compare(Block.LAVA)) ||
                                    (currentBlockBaseTypeIfLiquid != null && adjBaseType.compare(currentBlockBaseTypeIfLiquid) && (bestLiquidBaseTypeToBecome != null && !bestLiquidBaseTypeToBecome.compare(currentBlockBaseTypeIfLiquid)))) {
                                bestLiquidBaseTypeToBecome = adjBaseType;
                            }
                        }
                    }
                }
            }

            if (bestLiquidBaseTypeToBecome != null) {
                boolean shouldBecomeThisLiquid = false;
                if (isReplaceableByLiquid(currentBlock)) {
                    shouldBecomeThisLiquid = true;
                } else if (isLiquid(currentBlock) && currentBlock.compare(bestLiquidBaseTypeToBecome) && bestProspectiveLevel < currentBlockLevelIfLiquid) {
                    // Current is same liquid type, new flow is stronger
                    shouldBecomeThisLiquid = true;
                }
                // Note: This logic prevents different liquid types from flowing into each other unless current is replaceable.

                if (shouldBecomeThisLiquid) {
                    Block newSelf = getLiquidBlock(bestLiquidBaseTypeToBecome, bestProspectiveLevel);
                    return Action.action(b -> {
                        b.state(0, newSelf.stateId());
                        b.wakeup(Neighbors.NEUMANN_3D_SELF);
                    });
                }
            }
        }

        // Rule 4: Decay of unsupported flowing water (not lava)
        if (currentBlock.compare(Block.WATER) && getLiquidLevel(currentBlock) > 0) {
            boolean supported = false;
            int currentL = getLiquidLevel(currentBlock);

            Block blockAbove = query.blockAt(Neighbors.UP);
            if (blockAbove.compare(Block.WATER) && getLiquidLevel(blockAbove) <= currentL) {
                supported = true;
            }

            if (!supported) {
                for (Point sidePos : Neighbors.NEUMANN_2D) {
                    Block adjBlock = query.blockAt(sidePos);
                    if (adjBlock.compare(Block.WATER) && (getLiquidLevel(adjBlock) < currentL || getLiquidLevel(adjBlock) == 0)) {
                        supported = true;
                        break;
                    }
                }
            }

            if (!supported) {
                int nextLevel = currentL + 1;
                Block newSelfState;
                if (nextLevel > 7) {
                    newSelfState = Block.AIR;
                } else {
                    newSelfState = getLiquidBlock(Block.WATER, nextLevel);
                }
                return Action.action(b -> {
                    b.state(0, newSelfState.stateId());
                    b.wakeup(Neighbors.NEUMANN_3D_SELF);
                    // For slower decay, uncomment and adjust schedule ticks (e.g., 5 ticks for 1/4 second)
                    // b.schedule(5);
                });
            }
        }

        return null;
    }

    @Override
    public Info info() {
        return INFO;
    }
}
