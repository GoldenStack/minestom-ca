package dev.goldenstack.minestom_ca;

import net.minestom.server.coordinate.Point;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RuleCL {
    private static final String TEMPLATE = """
            int getSectionIndex(int x, int y, int z) {
                int dimensionMask = 16 - 1;
                int dimensionBitCount = 32 - clz(dimensionMask);
                return (y & dimensionMask) << (dimensionBitCount << 1) |
                        (z & dimensionMask) << dimensionBitCount |
                        (x & dimensionMask);
            }
            int get(__global long* data, int x, int y, int z) {
                int bitsPerEntry = 16;
                int sectionIndex = getSectionIndex(x, y, z);
                int valuesPerLong = 64 / bitsPerEntry;
                int index = sectionIndex / valuesPerLong;
                int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;
                return (int) (data[index] >> bitIndex) & ((1 << bitsPerEntry) - 1);
            }
            void set(__global long* data,int x, int y, int z, int value) {
                int bitsPerEntry = 16;
                int valuesPerLong = 64 / bitsPerEntry;
                int sectionIndex = getSectionIndex(x, y, z);
                int index = sectionIndex / valuesPerLong;
                int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;
                long block = data[index];
                long clear = (1L << bitsPerEntry) - 1L;
                data[index] = block & ~(clear << bitIndex) | ((long) value << bitIndex);
            }
            __kernel void automata(__global long* input, __global long* output) {
                for (int x = 1; x < 15; x++) {
                    for (int y = 1; y < 15; y++) {
                        for (int z = 1; z < 15; z++) {
            %s
                        }
                    }
                }
            }
            """;
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static String compileRules(List<Rule> rules) {
        StringBuilder builder = new StringBuilder();
        for (Rule rule : rules) {
            builder.append(compileRule(rule));
            builder.append('\n');
        }
        return String.format(TEMPLATE, builder);
    }

    private static String compileRule(Rule rule) {
        StringBuilder initializer = new StringBuilder();
        final String condition = compileCondition(rule.condition(), initializer::append);
        final String result = compileResult(rule.result());
        return String.format(
                """
                        {
                        %s
                        if (%s) {
                            %s
                        }
                        }
                        """, initializer, condition, result);
    }


    private static String compileCondition(Rule.Condition condition, Consumer<String> initializer) {
        return switch (condition) {
            case Rule.Condition.Equal equal -> {
                final String first = compileCondition(equal.first(), initializer);
                final String second = compileCondition(equal.second(), initializer);
                yield first + " == " + second;
            }
            case Rule.Condition.Index index -> {
                // TODO support other indexes
                yield "get(input, x, y, z)";
            }
            case Rule.Condition.Literal literal -> literal.value() + "";
            case Rule.Condition.Neighbors neighbors -> {
                final String varName = "offsets" + COUNTER.incrementAndGet();
                final String resName = varName + "_result";
                final List<Point> points = neighbors.offsets();
                // Encode point literal {x,y,z} as {1, 3, 5}
                {
                    StringBuilder literalBuilder = new StringBuilder();
                    for (Point point : neighbors.offsets()) {
                        literalBuilder.append("{");
                        literalBuilder.append(point.blockX());
                        literalBuilder.append(", ");
                        literalBuilder.append(point.blockY());
                        literalBuilder.append(", ");
                        literalBuilder.append(point.blockZ());
                        literalBuilder.append("};\n");
                    }

                    initializer.accept("int " + varName + "[" + points.size() * 3 + "] = " + literalBuilder);
                }

                initializer.accept(String.format("""
                        int %s = 0;
                        for (int i = 0; i < %d; i++) {
                            int x2 = %s[i * 3];
                            int y2 = %s[i * 3 + 1];
                            int z2 = %s[i * 3 + 2];
                            x += x2;
                            y += y2;
                            z += z2;
                            if (%s) {
                                %s++;
                            }
                            x -= x2;
                            y -= y2;
                            z -= z2;
                        }
                        """, resName, points.size(), varName, varName, varName, compileCondition(neighbors.condition(), initializer), resName));

                yield resName;
            }
            case Rule.Condition.And and -> {
                final StringBuilder builder = new StringBuilder();
                for (Rule.Condition other : and.conditions()) {
                    builder.append(compileCondition(other, initializer));
                    builder.append(" && ");
                }
                yield builder.substring(0, builder.length() - 4);
            }
            case Rule.Condition.Or or -> {
                final StringBuilder builder = new StringBuilder();
                for (Rule.Condition other : or.conditions()) {
                    builder.append(compileCondition(other, initializer));
                    builder.append(" || ");
                }
                yield builder.substring(0, builder.length() - 4);
            }
            case Rule.Condition.Not not -> {
                final String other = compileCondition(not.condition(), initializer);
                yield "!(" + other + ")";
            }
        };
    }

    private static String compileResult(Rule.Result result) {
        return switch (result) {
            case Rule.Result.And and -> {
                final StringBuilder builder = new StringBuilder();
                for (Rule.Result other : and.others()) {
                    builder.append(compileResult(other));
                    builder.append('\n');
                }
                yield builder.toString();
            }
            case Rule.Result.Set set -> {
                final Point offset = set.offset();
                yield "  set(output, x + " + offset.blockX() + ", y + " + offset.blockY() + ", z + " + offset.blockZ() + ", " + set.value() + ");";
            }
        };
    }

}
