package dev.goldenstack.minestom_ca.test.parser;

import dev.goldenstack.minestom_ca.Rule;
import dev.goldenstack.minestom_ca.Rule.Expression;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static dev.goldenstack.minestom_ca.Neighbors.*;
import static dev.goldenstack.minestom_ca.Rule.Condition.*;
import static dev.goldenstack.minestom_ca.Rule.Result.Set;
import static dev.goldenstack.minestom_ca.test.parser.TestUtils.assertRule;

public class RuleParsingTest {

    @Test
    public void testRules() {

        assertRule("state=#dirt -> state=#grass_block",
                new Rule(
                        new Equal(Block.DIRT),
                        new Set(Block.GRASS_BLOCK)
                ));

        assertRule("""
                        state=#air & west@state=#oak_log -> state=#oak_log
                        state=#oak_log & east@state=#air -> state=#oak_planks
                        """,
                new Rule(
                        new And(
                                new Equal(Block.AIR),
                                new Equal(new Expression.NeighborsCount(WEST, new Equal(Block.OAK_LOG)), new Expression.Literal(1))
                        ),
                        new Set(Block.OAK_LOG)
                ), new Rule(
                        new And(
                                new Equal(Block.OAK_LOG),
                                new Equal(new Expression.NeighborsCount(EAST, new Equal(Block.AIR)), new Expression.Literal(1))
                        ),
                        new Set(Block.OAK_PLANKS)
                ));

        assertRule("""
                        state=#dirt & up@state=#air -> state=#grass_block
                        state=#grass_block & up@state!=#air -> state=#dirt
                        """,
                new Rule(
                        new And(
                                new Equal(Block.DIRT),
                                new Equal(new Expression.NeighborsCount(UP, new Equal(Block.AIR)), new Expression.Literal(1))
                        ),
                        new Set(Block.GRASS_BLOCK)
                ), new Rule(
                        new And(
                                new Equal(Block.GRASS_BLOCK),
                                new Equal(new Expression.NeighborsCount(UP, new Not(new Equal(Block.AIR))), new Expression.Literal(1))
                        ),
                        new Set(Block.DIRT)
                ));

        assertRule("""
                        state=#white_wool & (moore2d@state=#white_wool)<2 -> state=#black_wool
                        state=#white_wool & (moore2d@state=#white_wool)>3 -> state=#black_wool
                        state=#black_wool & (moore2d@state=#white_wool)=3 -> state=#white_wool
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

        assertRule("state=#white_wool & points=0 -> state=#black_wool",
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(new Expression.Index(1), new Expression.Literal(0))
                        ),
                        new Set(Block.BLACK_WOOL)
                ));

        assertRule("""
                        state=#red_wool & up@state=#hay_block -> state=#orange_wool
                        state=#orange_wool -> state=#yellow_wool
                        state=#yellow_wool -> state=#lime_wool
                        state=#lime_wool -> state=#green_wool
                        state=#green_wool -> state=#cyan_wool
                        state=#cyan_wool -> state=#light_blue_wool
                        state=#light_blue_wool -> state=#blue_wool
                        state=#blue_wool -> state=#purple_wool
                        """,
                new Rule(
                        new And(
                                new Equal(Block.RED_WOOL),
                                new Equal(
                                        new Expression.NeighborsCount(UP, new Equal(Block.HAY_BLOCK)),
                                        new Expression.Literal(1)
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

