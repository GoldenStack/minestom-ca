package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.Neighbors;
import dev.goldenstack.minestom_ca.rule.Condition;
import dev.goldenstack.minestom_ca.rule.Result;
import dev.goldenstack.minestom_ca.rule.Rule;
import dev.goldenstack.minestom_ca.state.State;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Example rules for simple behaviour.
 */
public class ExampleRules {

    public static final @NotNull Rule GROW_DIRT = new Rule(
            new Condition.SelfState("minecraft:dirt"),
            new Result.Set(new State("minecraft:grass_block"))
    );

    public static final @NotNull Rule MOVING_OAK = new Rule(
            new Condition.Joined(
                    new Condition.SelfState("minecraft:air"),
                    new Condition.RelativeState(-1, 0, 0, "minecraft:oak_log")
            ),
            new Result.Set(new State("minecraft:oak_log"))
    );
    
    public static final @NotNull List<Rule> HAY_RAINBOW = List.of(
            new Rule(
                    new Condition.Joined(
                            new Condition.SelfState("minecraft:red_wool"),
                            new Condition.RelativeState(0, 1, 0, "minecraft:hay_block")
                    ),
                    new Result.Set(new State("minecraft:orange_wool"))
            ),
            new Rule(
                    new Condition.SelfState("minecraft:orange_wool"),
                    new Result.Set(new State("minecraft:yellow_wool"))
            ),
            new Rule(
                    new Condition.SelfState("minecraft:yellow_wool"),
                    new Result.Set(new State("minecraft:lime_wool"))
            ),
            new Rule(
                    new Condition.SelfState("minecraft:lime_wool"),
                    new Result.Set(new State("minecraft:green_wool"))
            ),
            new Rule(
                    new Condition.SelfState("minecraft:green_wool"),
                    new Result.Set(new State("minecraft:cyan_wool"))
            ),
            new Rule(
                    new Condition.SelfState("minecraft:cyan_wool"),
                    new Result.Set(new State("minecraft:light_blue_wool"))
            ),
            new Rule(
                    new Condition.SelfState("minecraft:light_blue_wool"),
                    new Result.Set(new State("minecraft:blue_wool"))
            ),
            new Rule(
                    new Condition.SelfState("minecraft:blue_wool"),
                    new Result.Set(new State("minecraft:purple_wool"))
            )      
    );

    public static final @NotNull List<Rule> GAME_OF_LIFE = List.of(
            new Rule(
                    new Condition.Joined(
                            new Condition.SelfState("minecraft:white_wool"),
                            new Condition.NeighborCondition(
                                    count -> count != 2 && count != 3,
                                    Neighbors.NEIGHBORS_2D_NOT_SELF,
                                    new Condition.SelfState("minecraft:white_wool")
                            )
                    ),
                    new Result.Set(new State("minecraft:air"))
            ),
            new Rule(
                    new Condition.Joined(
                            new Condition.SelfState("minecraft:air"),
                            new Condition.NeighborCondition(
                                    count -> count == 3,
                                    Neighbors.NEIGHBORS_2D_NOT_SELF,
                                    new Condition.SelfState("minecraft:white_wool")
                            )
                    ),
                    new Result.Set(new State("minecraft:white_wool"))
            )
    );


}
