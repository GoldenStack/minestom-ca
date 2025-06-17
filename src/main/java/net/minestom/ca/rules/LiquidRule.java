package net.minestom.ca.rules;

import net.minestom.ca.Automata.CellRule;
import net.minestom.ca.Automata.Context;
import net.minestom.ca.Automata.Query;
import net.minestom.ca.Neighbors;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LiquidRule implements CellRule {
    private int blockStateIndex;

    @Override
    public void init(Map<State, Integer> mapping) {
        blockStateIndex = mapping.get(BLOCK_STATE);
    }

    private boolean isLiquid(Block block) {
        return block.compare(Block.WATER) || block.compare(Block.LAVA);
    }

    private boolean isSolid(Block block) {
        return !block.isAir() && !isLiquid(block);
    }

    private int getLiquidLevel(Block block) {
        if (!isLiquid(block)) return -1;
        String levelStr = block.getProperty("level");
        return levelStr == null ? 0 : Integer.parseInt(levelStr); // Default to 0 for base "minecraft:water"
    }

    private Block getLiquidBlock(Block liquidBaseType, int level) {
        level = Math.min(Math.max(level, 0), 7); // Clamp to 0-7 for block property
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
    public List<Action> process(Context context, Query query) {
        long currentBlockStateId = query.state(blockStateIndex);
        Block currentBlock = Block.fromStateId((int) currentBlockStateId);
        List<Action> resultingActions = new ArrayList<>();

        // Rule 1: If I am liquid and can flow downwards, I become air.
        // "Flow downwards" means into air/replaceable OR into a weaker liquid of the same type.
        if (isLiquid(currentBlock)) {
            Block liquidBaseType = currentBlock.compare(Block.WATER) ? Block.WATER : Block.LAVA;
            int currentLvl = getLiquidLevel(currentBlock);

            Point downPos = Neighbors.DOWN;
            Block blockBelow = Block.fromStateId((int) query.stateAt(downPos.blockX(), downPos.blockY(), downPos.blockZ(), blockStateIndex));

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
                Action becomeAir = Action.action(b -> {
                    b.state(blockStateIndex, Block.AIR.stateId());
                    b.wakeup(Neighbors.NEUMANN_3D_SELF);
                });
                resultingActions.add(becomeAir);
                return resultingActions; // This liquid block has done its action for falling.
            }
        }

        // Rule 2: If I am replaceable, and liquid is above me, I become that liquid.
        if (isReplaceableByLiquid(currentBlock)) {
            Point upPos = Neighbors.UP;
            Block blockAbove = Block.fromStateId((int) query.stateAt(upPos.blockX(), upPos.blockY(), upPos.blockZ(), blockStateIndex));

            if (isLiquid(blockAbove)) {
                Block liquidBaseTypeAbove = blockAbove.compare(Block.WATER) ? Block.WATER : Block.LAVA;
                int levelAbove = getLiquidLevel(blockAbove);
                Block newSelf = getLiquidBlock(liquidBaseTypeAbove, levelAbove);

                Action becomeLiquidFromAbove = Action.action(b -> {
                    b.state(blockStateIndex, newSelf.stateId());
                    b.wakeup(Neighbors.NEUMANN_3D_SELF);
                });
                resultingActions.add(becomeLiquidFromAbove);
                return resultingActions;
            }
        }

        // New Rule: If I am liquid and there's same type of liquid above me, take the strongest level
        if (isLiquid(currentBlock)) {
            Point upPos = Neighbors.UP;
            Block blockAbove = Block.fromStateId((int) query.stateAt(upPos.blockX(), upPos.blockY(), upPos.blockZ(), blockStateIndex));

            if (areSameLiquidType(currentBlock, blockAbove)) {
                int currentLevel = getLiquidLevel(currentBlock);
                int aboveLevel = getLiquidLevel(blockAbove);

                // If the liquid above has a stronger level (lower number), adopt that level
                if (aboveLevel < currentLevel) {
                    Block liquidBaseType = currentBlock.compare(Block.WATER) ? Block.WATER : Block.LAVA;
                    Block newSelf = getLiquidBlock(liquidBaseType, aboveLevel);

                    Action becomeStrongerLiquid = Action.action(b -> {
                        b.state(blockStateIndex, newSelf.stateId());
                        b.wakeup(Neighbors.NEUMANN_3D_SELF); // Update surrounding blocks
                    });
                    resultingActions.add(becomeStrongerLiquid);
                    return resultingActions;
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
            Point downPos = Neighbors.DOWN;
            Block blockBelow = Block.fromStateId((int) query.stateAt(downPos.blockX(), downPos.blockY(), downPos.blockZ(), blockStateIndex));

            // If block below is not solid and we're trying to spread horizontally, return.
            // This is the key change - only allow horizontal spread if:
            // 1. There's a solid block below OR
            // 2. Current block is already liquid (for updating existing flows)
            boolean hasSupport = isSolid(blockBelow) || isLiquid(currentBlock);

            // If there's no support and this is empty space, liquid should fall straight down, not spread horizontally
            if (!hasSupport && isReplaceableByLiquid(currentBlock)) {
                return resultingActions;
            }

            int bestProspectiveLevel = 8; // Lower is "stronger"
            Block bestLiquidBaseTypeToBecome = null;

            for (Point sidePos : Neighbors.NEUMANN_2D) { // N, S, E, W
                Block adjBlock = Block.fromStateId((int) query.stateAt(sidePos.blockX(), sidePos.blockY(), sidePos.blockZ(), blockStateIndex));
                if (isLiquid(adjBlock)) {
                    int adjLevel = getLiquidLevel(adjBlock);
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
                    Action spreadToMe = Action.action(b -> {
                        b.state(blockStateIndex, newSelf.stateId());
                        b.wakeup(Neighbors.NEUMANN_3D_SELF);
                    });
                    resultingActions.add(spreadToMe);
                    return resultingActions;
                }
            }
        }

        // Rule 4: Decay of unsupported flowing water (not lava)
        if (currentBlock.compare(Block.WATER) && getLiquidLevel(currentBlock) > 0 && resultingActions.isEmpty()) {
            boolean supported = false;
            int currentL = getLiquidLevel(currentBlock);

            Block blockAbove = Block.fromStateId((int) query.stateAt(Neighbors.UP.blockX(), Neighbors.UP.blockY(), Neighbors.UP.blockZ(), blockStateIndex));
            if (blockAbove.compare(Block.WATER) && getLiquidLevel(blockAbove) <= currentL) {
                supported = true;
            }

            if (!supported) {
                for (Point sidePos : Neighbors.NEUMANN_2D) {
                    Block adjBlock = Block.fromStateId((int) query.stateAt(sidePos.blockX(), sidePos.blockY(), sidePos.blockZ(), blockStateIndex));
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
                Action decayAction = Action.action(b -> {
                    b.state(blockStateIndex, newSelfState.stateId());
                    b.wakeup(Neighbors.NEUMANN_3D_SELF);
                    // For slower decay, uncomment and adjust schedule ticks (e.g., 5 ticks for 1/4 second)
                    // b.schedule(5);
                });
                resultingActions.add(decayAction);
                return resultingActions;
            }
        }

        return resultingActions.isEmpty() ? null : resultingActions;
    }

    @Override
    public boolean tracked(Block block) {
        // Track liquids, as they can initiate changes (flow, decay).
        // Air blocks will be woken up by changing liquids, so they don't need to be tracked initially.
        return isLiquid(block);
    }

    @Override
    public Set<State> states() {
        return Set.of(); // Only uses BLOCK_STATE implicitly
    }
}
