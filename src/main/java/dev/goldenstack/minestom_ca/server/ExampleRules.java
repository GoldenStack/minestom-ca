package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.Neighbors;
import dev.goldenstack.minestom_ca.rule.Condition;
import dev.goldenstack.minestom_ca.rule.Result;
import dev.goldenstack.minestom_ca.rule.Rule;
import net.minestom.server.instance.block.Block;

import java.util.List;

/**
 * Example rules for simple behaviour.
 */
public final class ExampleRules {

    public static final Rule GROW_DIRT = new Rule(
            new Condition.SelfState(Block.DIRT),
            new Result.Set(Block.GRASS_BLOCK)
    );

    public static final Rule MOVING_OAK = new Rule(
            new Condition.Joined(
                    new Condition.SelfState(Block.AIR),
                    new Condition.RelativeState(-1, 0, 0, Block.OAK_LOG)
            ),
            new Result.Set(Block.OAK_LOG)
    );

    public static final List<Rule> HAY_RAINBOW = List.of(
            new Rule(
                    new Condition.Joined(
                            new Condition.SelfState(Block.RED_WOOL),
                            new Condition.RelativeState(0, 1, 0, Block.HAY_BLOCK)
                    ),
                    new Result.Set(Block.ORANGE_WOOL)
            ),
            new Rule(
                    new Condition.SelfState(Block.ORANGE_WOOL),
                    new Result.Set(Block.YELLOW_WOOL)
            ),
            new Rule(
                    new Condition.SelfState(Block.YELLOW_WOOL),
                    new Result.Set(Block.LIME_WOOL)
            ),
            new Rule(
                    new Condition.SelfState(Block.LIME_WOOL),
                    new Result.Set(Block.GREEN_WOOL)
            ),
            new Rule(
                    new Condition.SelfState(Block.GREEN_WOOL),
                    new Result.Set(Block.CYAN_WOOL)
            ),
            new Rule(
                    new Condition.SelfState(Block.CYAN_WOOL),
                    new Result.Set(Block.LIGHT_BLUE_WOOL)
            ),
            new Rule(
                    new Condition.SelfState(Block.LIGHT_BLUE_WOOL),
                    new Result.Set(Block.BLUE_WOOL)
            ),
            new Rule(
                    new Condition.SelfState(Block.BLUE_WOOL),
                    new Result.Set(Block.PURPLE_WOOL)
            )
    );

    public static final List<Rule> GAME_OF_LIFE = List.of(
            new Rule(
                    new Condition.Joined(
                            new Condition.SelfState(Block.WHITE_WOOL),
                            new Condition.NeighborCondition(
                                    count -> count != 2 && count != 3,
                                    Neighbors.NEIGHBORS_2D_NOT_SELF,
                                    new Condition.SelfState(Block.WHITE_WOOL)
                            )
                    ),
                    new Result.Set(Block.AIR)
            ),
            new Rule(
                    new Condition.Joined(
                            new Condition.SelfState(Block.AIR),
                            new Condition.NeighborCondition(
                                    count -> count == 3,
                                    Neighbors.NEIGHBORS_2D_NOT_SELF,
                                    new Condition.SelfState(Block.WHITE_WOOL)
                            )
                    ),
                    new Result.Set(Block.WHITE_WOOL)
            )
    );
}
