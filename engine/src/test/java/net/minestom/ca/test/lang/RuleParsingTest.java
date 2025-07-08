package net.minestom.ca.test.lang;

import net.minestom.ca.Automata;
import net.minestom.ca.lang.Rule;
import net.minestom.ca.lang.Rule.Expression;
import net.minestom.ca.lang.Rule.Result.SetState;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static net.minestom.ca.Neighbors.*;
import static net.minestom.ca.lang.Rule.Condition.*;
import static net.minestom.ca.test.lang.TestUtils.assertRule;

@SuppressWarnings("DuplicateExpressions")
public final class RuleParsingTest {

    @Test
    public void testRules() {
        assertRule("#dirt -> #grass_block",
                new Rule(
                        new Equal(Block.DIRT),
                        new SetState(Block.GRASS_BLOCK)
                ));

        assertRule("""
                        #air & west@#oak_log -> #oak_log
                        #oak_log & east@#air -> #oak_planks
                        """,
                new Rule(
                        new And(
                                new Equal(Block.AIR),
                                new Equal(new Expression.NeighborsCount(WEST, new Equal(Block.OAK_LOG)), new Expression.Literal(1))
                        ),
                        new SetState(Block.OAK_LOG)
                ), new Rule(
                        new And(
                                new Equal(Block.OAK_LOG),
                                new Equal(new Expression.NeighborsCount(EAST, new Equal(Block.AIR)), new Expression.Literal(1))
                        ),
                        new SetState(Block.OAK_PLANKS)
                ));

        assertRule("""
                        #dirt & up@#air -> #grass_block
                        #grass_block & up@!#air -> #dirt
                        """,
                new Rule(
                        new And(
                                new Equal(Block.DIRT),
                                new Equal(new Expression.NeighborsCount(UP, new Equal(Block.AIR)), new Expression.Literal(1))
                        ),
                        new SetState(Block.GRASS_BLOCK)
                ), new Rule(
                        new And(
                                new Equal(Block.GRASS_BLOCK),
                                new Equal(new Expression.NeighborsCount(UP, new Not(new Equal(Block.AIR))), new Expression.Literal(1))
                        ),
                        new SetState(Block.DIRT)
                ));

        assertRule("""
                        #white_wool & [<2]moore2d@#white_wool -> #black_wool
                        #white_wool & [>3]moore2d@#white_wool -> #black_wool
                        #black_wool & [3]moore2d@#white_wool -> #white_wool
                        """,
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(
                                        new Expression.Compare(new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Expression.Literal(2)),
                                        new Expression.Literal(-1)
                                )
                        ),
                        new SetState(Block.BLACK_WOOL)
                ), new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(
                                        new Expression.Compare(new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Expression.Literal(3)),
                                        new Expression.Literal(1)
                                )
                        ),
                        new SetState(Block.BLACK_WOOL)
                ), new Rule(
                        new And(
                                new Equal(Block.BLACK_WOOL),
                                new Equal(new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Expression.Literal(3))
                        ),
                        new SetState(Block.WHITE_WOOL)
                )
        );

        assertRule("#white_wool & points=0 -> #black_wool",
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(new Expression.State("points"), new Expression.Literal(0))
                        ),
                        new SetState(Block.BLACK_WOOL)
                ));

        assertRule("""
                        #red_wool & up@#hay_block -> #orange_wool
                        #orange_wool -> #yellow_wool
                        #yellow_wool -> #lime_wool
                        #lime_wool -> #green_wool
                        #green_wool -> #cyan_wool
                        #cyan_wool -> #light_blue_wool
                        #light_blue_wool -> #blue_wool
                        #blue_wool -> #purple_wool
                        """,
                new Rule(
                        new And(
                                new Equal(Block.RED_WOOL),
                                new Equal(new Expression.NeighborsCount(UP, new Equal(Block.HAY_BLOCK)), new Expression.Literal(1))
                        ),
                        new SetState(Block.ORANGE_WOOL)
                ),
                new Rule(
                        new Equal(Block.ORANGE_WOOL),
                        new SetState(Block.YELLOW_WOOL)
                ), new Rule(
                        new Equal(Block.YELLOW_WOOL),
                        new SetState(Block.LIME_WOOL)
                ), new Rule(
                        new Equal(Block.LIME_WOOL),
                        new SetState(Block.GREEN_WOOL)
                ), new Rule(
                        new Equal(Block.GREEN_WOOL),
                        new SetState(Block.CYAN_WOOL)
                ), new Rule(
                        new Equal(Block.CYAN_WOOL),
                        new SetState(Block.LIGHT_BLUE_WOOL)
                ), new Rule(
                        new Equal(Block.LIGHT_BLUE_WOOL),
                        new SetState(Block.BLUE_WOOL)
                ), new Rule(
                        new Equal(Block.BLUE_WOOL),
                        new SetState(Block.PURPLE_WOOL)
                ));
    }

    @Test
    public void notPredicate() {
        assertRule("""
                        #white_wool & [!0]moore2d@#white_wool -> #black_wool
                        """,
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Not(new Equal(
                                        new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)),
                                        new Expression.Literal(0))
                                )
                        ),
                        new SetState(Block.BLACK_WOOL)
                )
        );
    }

    @Test
    public void stateTests() {
        assertRule("points=1 -> #dirt",
                new Rule(
                        new Equal(new Expression.State("points"), new Expression.Literal(1)),
                        new SetState(Block.DIRT)
                ));
        assertRule("points=0 -> points=1",
                new Rule(
                        new Equal(new Expression.State("points"), new Expression.Literal(0)),
                        new SetState("points", new Expression.Literal(1))
                ));
        assertRule("points=1 -> #dirt points=2",
                new Rule(
                        new Equal(new Expression.State("points"), new Expression.Literal(1)),
                        new SetState(Automata.Rule.BLOCK_STATE.name(), new Expression.Literal(Block.DIRT)),
                        new SetState("points", new Expression.Literal(2))
                ));
        assertRule("#dirt -> points=up@points",
                new Rule(
                        new Equal(Block.DIRT),
                        new SetState("points", new Expression.NeighborState(0, 1, 0, "points"))
                ));
    }

    @Test
    public void blockStateTests() {
        assertRule("#redstone_wire[power=15] -> #dirt",
                new Rule(
                        new Equal(new Expression.State(Automata.Rule.BLOCK_STATE.name()), new Expression.Literal(Block.REDSTONE_WIRE.withProperty("power", "15"))),
                        new SetState(Block.DIRT)
                ));
        assertRule("#redstone_wire[power=15, south=side] -> #dirt",
                new Rule(
                        new Equal(new Expression.State(Automata.Rule.BLOCK_STATE.name()), new Expression.Literal(Block.REDSTONE_WIRE
                                .withProperty("power", "15")
                                .withProperty("south", "side"))),
                        new SetState(Block.DIRT)
                ));
        assertRule("#redstone_wire[power=15] -> #redstone_wire[power=7]",
                new Rule(
                        new Equal(new Expression.State(Automata.Rule.BLOCK_STATE.name()), new Expression.Literal(Block.REDSTONE_WIRE.withProperty("power", "15"))),
                        new SetState(Block.REDSTONE_WIRE.withProperty("power", "7"))
                ));
    }

    @Test
    public void eventTriggerTest() {
        assertRule("#dirt -> $dirt_exist=1",
                new Rule(
                        new Equal(Block.DIRT),
                        new Rule.Result.TriggerEvent("dirt_exist", new Expression.Literal(1))
                ));
        assertRule("#dirt -> $dirt_exist",
                new Rule(
                        new Equal(Block.DIRT),
                        new Rule.Result.TriggerEvent("dirt_exist", null)
                ));
    }

    @Test
    public void math() {
        assertRule("points!0 -> points=points+1",
                new Rule(
                        new Not(new Equal(new Expression.State("points"), new Expression.Literal(0))),
                        new SetState("points", new Expression.Operation(
                                new Expression.State("points"), new Expression.Literal(1),
                                Expression.Operation.Type.ADD))
                ));
        assertRule("points!0 -> points=points-1",
                new Rule(
                        new Not(new Equal(new Expression.State("points"), new Expression.Literal(0))),
                        new SetState("points", new Expression.Operation(
                                new Expression.State("points"), new Expression.Literal(1),
                                Expression.Operation.Type.SUBTRACT))
                ));
    }

    @Test
    public void comments() {
        assertRule("""
                        // Test comment
                        #dirt -> #grass_block
                        """,
                new Rule(
                        new Equal(Block.DIRT),
                        new SetState(Block.GRASS_BLOCK)
                ));
        assertRule("""
                        #dirt -> #grass_block
                        // Test comment
                        """,
                new Rule(
                        new Equal(Block.DIRT),
                        new SetState(Block.GRASS_BLOCK)
                ));
        assertRule("""
                        // Test comment
                        #dirt -> #grass_block
                        // Test comment
                        """,
                new Rule(
                        new Equal(Block.DIRT),
                        new SetState(Block.GRASS_BLOCK)
                ));
    }
}
