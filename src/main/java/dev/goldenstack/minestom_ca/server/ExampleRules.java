package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.Rule;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;

import java.util.List;

import static dev.goldenstack.minestom_ca.Rule.Condition;
import static dev.goldenstack.minestom_ca.Rule.Result;

/**
 * Example rules for simple behaviour.
 */
public final class ExampleRules {

    public static final Rule GROW_DIRT = new Rule(
            new Condition.Equal(Block.DIRT),
            new Result.Set(Block.GRASS_BLOCK)
    );

    public static final Rule MOVING_OAK = new Rule(
            new Condition.And(
                    new Condition.Equal(Block.AIR),
                    new Condition.Neighbors(-1, 0, 0, new Condition.Equal(Block.OAK_LOG))
            ),
            new Result.And(List.of(
                    new Result.Set(Block.OAK_LOG),
                    new Result.Set(new Vec(-1, 0, 0), Block.OAK_PLANKS)
            ))
    );

    public static final List<Rule> HAY_RAINBOW = List.of(
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.RED_WOOL),
                            new Condition.Neighbors(0, 1, 0, new Condition.Equal(Block.HAY_BLOCK))
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

    private static final Condition NUM_ALIVE_NEIGHBORS =
            new Condition.Neighbors(
                    dev.goldenstack.minestom_ca.Neighbors.NEIGHBORS_2D_NOT_SELF,
                    new Condition.Equal(Block.WHITE_WOOL)
            );

    public static final List<Rule> GAME_OF_LIFE = List.of(
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.WHITE_WOOL),
                            new Condition.Not(new Condition.Equal(NUM_ALIVE_NEIGHBORS, 2)),
                            new Condition.Not(new Condition.Equal(NUM_ALIVE_NEIGHBORS, 3))
                    ),
                    new Result.Set(Block.AIR)
            ),
            new Rule(
                    new Condition.And(
                            new Condition.Equal(Block.AIR),
                            new Condition.Equal(NUM_ALIVE_NEIGHBORS, 3)
                    ),
                    new Result.Set(Block.WHITE_WOOL)
            )
    );
}
