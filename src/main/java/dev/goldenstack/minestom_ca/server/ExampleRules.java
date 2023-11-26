package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.Neighbors;
import dev.goldenstack.minestom_ca.Rule;
import dev.goldenstack.minestom_ca.Rule.Expression;
import net.minestom.server.instance.block.Block;

import java.util.List;
import java.util.Map;

import static dev.goldenstack.minestom_ca.Rule.Condition;
import static dev.goldenstack.minestom_ca.Rule.Result;

/**
 * Example rules for simple behaviour.
 */
public final class ExampleRules {

    public static final List<Rule> GROW_DIRT = List.of(new Rule(
            new Condition.Equal(Block.DIRT),
            new Result.Set(Block.GRASS_BLOCK)
    ));

    public static final List<Rule> GROW_DIRT_AGE = List.of(
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.DIRT)
                    ),
                    new Result.Set(Map.of(1, new Expression.Operation(new Expression.Index(1),
                            new Expression.Literal(1), Expression.Operation.Type.ADD)))
            ),
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.DIRT),
                            new Condition.Equal(new Expression.Index(1), new Expression.Literal(60))
                    ),
                    new Result.Set(Map.of(
                            0, new Expression.Literal(Block.GRASS_BLOCK.stateId()),
                            1, new Expression.Literal(0)))
            )
    );

    public static final List<Rule> MOVING_OAK = List.of(
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.AIR),
                            new Condition.Equal(
                                    new Expression.Literal(Block.OAK_LOG),
                                    new Expression.Index(-1, 0, 0)
                            )
                    ),
                    new Result.Set(Block.OAK_LOG)),
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.OAK_LOG),
                            new Condition.Equal(
                                    new Expression.Literal(Block.AIR),
                                    new Expression.Index(1, 0, 0)
                            )
                    ),
                    new Result.Set(Block.OAK_PLANKS))
    );

    public static final List<Rule> HAY_RAINBOW = List.of(
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.RED_WOOL),
                            new Condition.Equal(
                                    new Expression.Literal(Block.HAY_BLOCK),
                                    new Expression.Index(0, 1, 0)
                            )
                    ),
                    new Result.Set(Block.ORANGE_WOOL)
            ),
            new Rule(
                    new Condition.Equal(Block.ORANGE_WOOL),
                    new Result.Set(Block.YELLOW_WOOL)
            ),
            new Rule(
                    new Condition.Equal(Block.YELLOW_WOOL),
                    new Result.Set(Block.LIME_WOOL)
            ),
            new Rule(
                    new Condition.Equal(Block.LIME_WOOL),
                    new Result.Set(Block.GREEN_WOOL)
            ),
            new Rule(
                    new Condition.Equal(Block.GREEN_WOOL),
                    new Result.Set(Block.CYAN_WOOL)
            ),
            new Rule(
                    new Condition.Equal(Block.CYAN_WOOL),
                    new Result.Set(Block.LIGHT_BLUE_WOOL)
            ),
            new Rule(
                    new Condition.Equal(Block.LIGHT_BLUE_WOOL),
                    new Result.Set(Block.BLUE_WOOL)
            ),
            new Rule(
                    new Condition.Equal(Block.BLUE_WOOL),
                    new Result.Set(Block.PURPLE_WOOL)
            )
    );

    private static final Expression NUM_ALIVE_NEIGHBORS =
            new Expression.NeighborsCount(
                    Neighbors.MOORE_2D,
                    new Condition.Equal(Block.WHITE_WOOL)
            );

    public static final List<Rule> GAME_OF_LIFE = List.of(
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.WHITE_WOOL),
                            new Condition.Not(new Condition.Equal(NUM_ALIVE_NEIGHBORS, new Expression.Literal(2))),
                            new Condition.Not(new Condition.Equal(NUM_ALIVE_NEIGHBORS, new Expression.Literal(3)))
                    ),
                    new Result.Set(Block.AIR)
            ),
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.AIR),
                            new Condition.Equal(NUM_ALIVE_NEIGHBORS, new Expression.Literal(3))
                    ),
                    new Result.Set(Block.WHITE_WOOL)
            )
    );
}
