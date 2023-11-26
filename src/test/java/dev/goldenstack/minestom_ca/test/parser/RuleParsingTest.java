package dev.goldenstack.minestom_ca.test.parser;

import dev.goldenstack.minestom_ca.Rule;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static dev.goldenstack.minestom_ca.Neighbors.MOORE_2D;
import static dev.goldenstack.minestom_ca.Neighbors.UP;
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

        assertRule("state=#dirt & up@state=#air -> state=#grass_block",
                new Rule(
                        new And(
                                new Equal(Block.DIRT),
                                new Neighbors(UP, new Equal(Block.AIR))
                        ),
                        new Set(Block.GRASS_BLOCK)
                ));

        assertRule("state=#grass_block & up@state!=#air -> state=#dirt",
                new Rule(
                        new And(
                                new Equal(Block.GRASS_BLOCK),
                                new Neighbors(UP, new Not(new Equal(Block.AIR)))
                        ),
                        new Set(Block.DIRT)
                ));

        assertRule("state=#white_wool & (moore2d@state=#white_wool)>2 -> state=#black_wool",
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(
                                        new Compare(new Neighbors(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Literal(2)),
                                        new Literal(1)
                                )
                        ),
                        new Set(Block.BLACK_WOOL)
                ));

        assertRule("state=#white_wool & (moore2d@state=#white_wool)<3 -> state=#black_wool",
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(
                                        new Compare(new Neighbors(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Literal(3)),
                                        new Literal(-1)
                                )
                        ),
                        new Set(Block.BLACK_WOOL)
                ));

        assertRule("state=#black_wool & (moore2d@state=#white_wool) -> state=#white_wool",
                new Rule(
                        new And(
                                new Equal(Block.BLACK_WOOL),
                                new Equal(new Neighbors(MOORE_2D, new Equal(Block.WHITE_WOOL)), new Literal(3))
                        ),
                        new Set(Block.WHITE_WOOL)
                ));

        assertRule("state=#white_wool & points=0 -> state=#black_wool",
                new Rule(
                        new And(
                                new Equal(Block.WHITE_WOOL),
                                new Equal(new Index(1), new Literal(0))
                        ),
                        new Set(Block.BLACK_WOOL)
                ));
    }

}

