package net.goldenstack.minestom_ca.test.lang;

import net.goldenstack.minestom_ca.lang.Rule;
import net.goldenstack.minestom_ca.lang.Rule.Expression;
import net.goldenstack.minestom_ca.lang.Rule.Result.SetIndex;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static net.goldenstack.minestom_ca.Neighbors.*;
import static net.goldenstack.minestom_ca.lang.Rule.Condition.*;
import static net.goldenstack.minestom_ca.test.lang.TestUtils.assertRule;

@SuppressWarnings("DuplicateExpressions")
public final class RuleParsingTest {

    @Test
    public void testRules() {
        assertRule("#dirt -> #grass_block",
                new Rule(
                        new Equal(Block.DIRT),
                        new SetIndex(Block.GRASS_BLOCK)
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
                        new SetIndex(Block.OAK_LOG)
                ), new Rule(
                        new And(
                                new Equal(Block.OAK_LOG),
                                new Equal(new Expression.NeighborsCount(EAST, new Equal(Block.AIR)), new Expression.Literal(1))
                        ),
                        new SetIndex(Block.OAK_PLANKS)
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
                        new SetIndex(Block.GRASS_BLOCK)
                ), new Rule(
                        new And(
                                new Equal(Block.GRASS_BLOCK),
                                new Equal(new Expression.NeighborsCount(UP, new Not(new Equal(Block.AIR))), new Expression.Literal(1))
                        ),
                        new SetIndex(Block.DIRT)
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
                        new SetIndex(Block.BLACK_WOOL)
                ), new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(
                                        new Expression.Compare(new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Expression.Literal(3)),
                                        new Expression.Literal(1)
                                )
                        ),
                        new SetIndex(Block.BLACK_WOOL)
                ), new Rule(
                        new And(
                                new Equal(Block.BLACK_WOOL),
                                new Equal(new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Expression.Literal(3))
                        ),
                        new SetIndex(Block.WHITE_WOOL)
                )
        );

        assertRule("#white_wool & points=0 -> #black_wool",
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(new Expression.Index(1), new Expression.Literal(0))
                        ),
                        new SetIndex(Block.BLACK_WOOL)
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
                        new SetIndex(Block.ORANGE_WOOL)
                ),
                new Rule(
                        new Equal(Block.ORANGE_WOOL),
                        new SetIndex(Block.YELLOW_WOOL)
                ), new Rule(
                        new Equal(Block.YELLOW_WOOL),
                        new SetIndex(Block.LIME_WOOL)
                ), new Rule(
                        new Equal(Block.LIME_WOOL),
                        new SetIndex(Block.GREEN_WOOL)
                ), new Rule(
                        new Equal(Block.GREEN_WOOL),
                        new SetIndex(Block.CYAN_WOOL)
                ), new Rule(
                        new Equal(Block.CYAN_WOOL),
                        new SetIndex(Block.LIGHT_BLUE_WOOL)
                ), new Rule(
                        new Equal(Block.LIGHT_BLUE_WOOL),
                        new SetIndex(Block.BLUE_WOOL)
                ), new Rule(
                        new Equal(Block.BLUE_WOOL),
                        new SetIndex(Block.PURPLE_WOOL)
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
                        new SetIndex(Block.BLACK_WOOL)
                )
        );
    }

    @Test
    public void stateTests() {
        assertRule("points=1 -> #dirt",
                new Rule(
                        new Equal(new Expression.Index(1), new Expression.Literal(1)),
                        new SetIndex(Block.DIRT)
                ));
        assertRule("points=0 -> points=1",
                new Rule(
                        new Equal(new Expression.Index(1), new Expression.Literal(0)),
                        new SetIndex(1, new Expression.Literal(1))
                ));
        assertRule("points=1 -> #dirt points=2",
                new Rule(
                        new Equal(new Expression.Index(1), new Expression.Literal(1)),
                        new SetIndex(0, new Expression.Literal(Block.DIRT)),
                        new SetIndex(1, new Expression.Literal(2))
                ));
        assertRule("#dirt -> points=up@points",
                new Rule(
                        new Equal(Block.DIRT),
                        new SetIndex(1, new Expression.NeighborIndex(0, 1, 0, 1))
                ));
    }

    @Test
    public void blockStateTests() {
        assertRule("#redstone_wire[power=15] -> #dirt",
                new Rule(
                        new Equal(new Expression.Index(0), new Expression.Literal(Block.REDSTONE_WIRE.withProperty("power", "15"))),
                        new SetIndex(Block.DIRT)
                ));
        assertRule("#redstone_wire[power=15, south=side] -> #dirt",
                new Rule(
                        new Equal(new Expression.Index(0), new Expression.Literal(Block.REDSTONE_WIRE
                                .withProperty("power", "15")
                                .withProperty("south", "side"))),
                        new SetIndex(Block.DIRT)
                ));
        assertRule("#redstone_wire[power=15] -> #redstone_wire[power=7]",
                new Rule(
                        new Equal(new Expression.Index(0), new Expression.Literal(Block.REDSTONE_WIRE.withProperty("power", "15"))),
                        new SetIndex(Block.REDSTONE_WIRE.withProperty("power", "7"))
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
                        new Not(new Equal(new Expression.Index(1), new Expression.Literal(0))),
                        new SetIndex(1, new Expression.Operation(
                                new Expression.Index(1), new Expression.Literal(1),
                                Expression.Operation.Type.ADD))
                ));
        assertRule("points!0 -> points=points-1",
                new Rule(
                        new Not(new Equal(new Expression.Index(1), new Expression.Literal(0))),
                        new SetIndex(1, new Expression.Operation(
                                new Expression.Index(1), new Expression.Literal(1),
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
                        new SetIndex(Block.GRASS_BLOCK)
                ));
        assertRule("""
                        #dirt -> #grass_block
                        // Test comment
                        """,
                new Rule(
                        new Equal(Block.DIRT),
                        new SetIndex(Block.GRASS_BLOCK)
                ));
        assertRule("""
                        // Test comment
                        #dirt -> #grass_block
                        // Test comment
                        """,
                new Rule(
                        new Equal(Block.DIRT),
                        new SetIndex(Block.GRASS_BLOCK)
                ));
    }
}
