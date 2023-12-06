package dev.goldenstack.minestom_ca.test.parser;

import dev.goldenstack.minestom_ca.Rule;
import dev.goldenstack.minestom_ca.Rule.Expression;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static dev.goldenstack.minestom_ca.Neighbors.*;
import static dev.goldenstack.minestom_ca.Rule.Condition.*;
import static dev.goldenstack.minestom_ca.Rule.Result.Set;
import static dev.goldenstack.minestom_ca.test.parser.TestUtils.assertRule;

public final class RuleParsingTest {

    @Test
    public void testRules() {

        assertRule("#dirt -> #grass_block",
                new Rule(
                        new Equal(Block.DIRT),
                        new Set(Block.GRASS_BLOCK)
                ));

        assertRule("""
                        #air & west@#oak_log -> #oak_log
                        #oak_log & east@#air -> #oak_planks
                        """,
                new Rule(
                        new And(
                                new Equal(Block.AIR),
                                new Equal(new Expression.Index(WEST), new Expression.Literal(Block.OAK_LOG))
                        ),
                        new Set(Block.OAK_LOG)
                ), new Rule(
                        new And(
                                new Equal(Block.OAK_LOG),
                                new Equal(new Expression.Index(EAST), new Expression.Literal(Block.AIR))
                        ),
                        new Set(Block.OAK_PLANKS)
                ));

        assertRule("""
                        #dirt & up@#air -> #grass_block
                        #grass_block & up@!#air -> #dirt
                        """,
                new Rule(
                        new And(
                                new Equal(Block.DIRT),
                                new Equal(new Expression.Index(UP), new Expression.Literal(Block.AIR))
                        ),
                        new Set(Block.GRASS_BLOCK)
                ), new Rule(
                        new And(
                                new Equal(Block.GRASS_BLOCK),
                                new Not(new Equal(new Expression.Index(UP), new Expression.Literal(Block.AIR)))
                        ),
                        new Set(Block.DIRT)
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
                                        new Expression.Literal(1)
                                )
                        ),
                        new Set(Block.BLACK_WOOL)
                ), new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(
                                        new Expression.Compare(new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Expression.Literal(3)),
                                        new Expression.Literal(-1)
                                )
                        ),
                        new Set(Block.BLACK_WOOL)
                ), new Rule(
                        new And(
                                new Equal(Block.BLACK_WOOL),
                                new Equal(new Expression.NeighborsCount(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Expression.Literal(3))
                        ),
                        new Set(Block.WHITE_WOOL)
                )
        );

        assertRule("#white_wool & points=0 -> #black_wool",
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(new Expression.Index(1), new Expression.Literal(0))
                        ),
                        new Set(Block.BLACK_WOOL)
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
                                new Equal(
                                        new Expression.Index(UP),
                                        new Expression.Literal(Block.HAY_BLOCK)
                                )
                        ),
                        new Set(Block.ORANGE_WOOL)
                ),
                new Rule(
                        new Equal(Block.ORANGE_WOOL),
                        new Set(Block.YELLOW_WOOL)
                ), new Rule(
                        new Equal(Block.YELLOW_WOOL),
                        new Set(Block.LIME_WOOL)
                ), new Rule(
                        new Equal(Block.LIME_WOOL),
                        new Set(Block.GREEN_WOOL)
                ), new Rule(
                        new Equal(Block.GREEN_WOOL),
                        new Set(Block.CYAN_WOOL)
                ), new Rule(
                        new Equal(Block.CYAN_WOOL),
                        new Set(Block.LIGHT_BLUE_WOOL)
                ), new Rule(
                        new Equal(Block.LIGHT_BLUE_WOOL),
                        new Set(Block.BLUE_WOOL)
                ), new Rule(
                        new Equal(Block.BLUE_WOOL),
                        new Set(Block.PURPLE_WOOL)
                ));
    }
}
