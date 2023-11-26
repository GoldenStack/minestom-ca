package dev.goldenstack.minestom_ca.backends.opencl;

import dev.goldenstack.minestom_ca.Rule;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import org.jocl.cl_kernel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class CLRuleCompiler {
    private static final String KERNEL_SOURCE = """
            #define NEIGHBOR_COUNT %s
            #define AIR %s

            int iterate(int neighbors[NEIGHBOR_COUNT]) {
                int x = 0;
                int y = 0;
                int z = 0;
                int globalWidth = get_global_size(0);
                int globalHeight = get_global_size(1);
                
                int indices[NEIGHBOR_COUNT][3] = {
                    {-1,-1,-1}, { 0,-1,-1}, { 1,-1,-1}, {-1, 0,-1}, { 0, 0,-1}, { 1, 0,-1}, {-1, 1,-1}, { 0, 1,-1}, {-1, 1,-1},
                    {-1,-1, 0}, { 0,-1, 0}, { 1,-1, 0}, {-1, 0, 0}, { 0, 0, 0}, { 1, 0, 0}, {-1, 1, 0}, { 0, 1, 0}, { 1, 1, 0},
                    {-1,-1, 1}, { 0,-1, 1}, { 1,-1, 1}, {-1, 0, 1}, { 0, 0, 1}, { 1, 0, 1}, {-1, 1, 1}, { 0, 1, 1}, { 1, 1, 1}
                };
                int inverted_indices[3][3][3] = {
                    {{0,  1,  2},  {3,  4,  5},  {6,  7,  8}},
                    {{9,  10, 11}, {12, 13, 14}, {15, 16, 17}},
                    {{18, 19, 20}, {21, 22, 23}, {24, 25, 26}}
                };
                
                %s
            }
                        
            __kernel void sampleKernel(
                __global const uint *input,
                __global uint *output
            ) {
                        
                int globalX = get_global_id(0);
                int globalY = get_global_id(1);
                int globalZ = get_global_id(2);
               
                int localX = get_local_id(0);
                int localY = get_local_id(1);
                int localZ = get_local_id(2);
               
                int globalWidth = get_global_size(0);
                int globalHeight = get_global_size(1);
                int globalDepth = get_global_size(2);
               
                int localWidth = get_local_size(0);
                int localHeight = get_local_size(1);
                int localDepth = get_local_size(2);
               
                // Calculate the offset of the current work-group within the global space
                int offsetX = get_group_id(0) * get_local_size(0);
                int offsetY = get_group_id(1) * get_local_size(1);
                int offsetZ = get_group_id(2) * get_local_size(2);
               
                int neighbors[NEIGHBOR_COUNT];
                int indices[NEIGHBOR_COUNT][3] = {
                    {-1,-1,-1}, { 0,-1,-1}, { 1,-1,-1}, {-1, 0,-1}, { 0, 0,-1}, { 1, 0,-1}, {-1, 1,-1}, { 0, 1,-1}, {-1, 1,-1},
                    {-1,-1, 0}, { 0,-1, 0}, { 1,-1, 0}, {-1, 0, 0}, { 0, 0, 0}, { 1, 0, 0}, {-1, 1, 0}, { 0, 1, 0}, { 1, 1, 0},
                    {-1,-1, 1}, { 0,-1, 1}, { 1,-1, 1}, {-1, 0, 1}, { 0, 0, 1}, { 1, 0, 1}, {-1, 1, 1}, { 0, 1, 1}, { 1, 1, 1}
                };
               
                for (int i = 0; i < NEIGHBOR_COUNT; i++) {
                    int nX = localX + indices[i][0];
                    int nY = localY + indices[i][1];
                    int nZ = localZ + indices[i][2];
                                                   
                    int nI = (offsetZ + nZ) * globalHeight * globalWidth + (offsetY + nY) * globalWidth + (offsetX + nX);
                    neighbors[i] = input[nI];
                }
               
                int currentIndex = (globalZ * globalHeight * globalWidth) + (globalY * globalWidth) + globalX;
                output[currentIndex] = iterate(neighbors);
            }
            """;

    private static final List<Integer[]> indices = List.of(
            new Integer[]{-1, -1, -1}, new Integer[]{0, -1, -1}, new Integer[]{1, -1, -1}, new Integer[]{-1, 0, -1}, new Integer[]{0, 0, -1}, new Integer[]{1, 0, -1}, new Integer[]{-1, 1, -1}, new Integer[]{0, 1, -1}, new Integer[]{-1, 1, -1},
            new Integer[]{-1, -1, 0}, new Integer[]{0, -1, 0}, new Integer[]{1, -1, 0}, new Integer[]{-1, 0, 0}, new Integer[]{0, 0, 0}, new Integer[]{1, 0, 0}, new Integer[]{-1, 1, 0}, new Integer[]{0, 1, 0}, new Integer[]{1, 1, 0},
            new Integer[]{-1, -1, 1}, new Integer[]{0, -1, 1}, new Integer[]{1, -1, 1}, new Integer[]{-1, 0, 1}, new Integer[]{0, 0, 1}, new Integer[]{1, 0, 1}, new Integer[]{-1, 1, 1}, new Integer[]{0, 1, 1}, new Integer[]{1, 1, 1}
    );

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static String compileCondition(Rule.Condition condition, Consumer<String> prepend) {
        return switch (condition) {
            case Rule.Condition.Equal equal -> {
                final String first = compileExpression(equal.first(), prepend);
                final String second = compileExpression(equal.second(), prepend);
                yield "(" + first + " == " + second + ")";
            }
            case Rule.Condition.And and -> {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < and.conditions().size(); i++) {
                    builder.append("(");
                    builder.append(compileCondition(and.conditions().get(i), prepend));
                    builder.append(")");
                    if (i != and.conditions().size() - 1)
                        builder.append("&&");
                }
                yield builder.toString();
            }
            case Rule.Condition.Not not -> {
                final String c = compileCondition(not.condition(), prepend);
                yield "!(" + c + ")";
            }
            case Rule.Condition.Or or -> {
                StringBuilder builder = new StringBuilder();
                builder.append("(");
                for (int i = 0; i < or.conditions().size(); i++) {
                    builder.append("(");
                    builder.append(compileCondition(or.conditions().get(i), prepend));
                    builder.append(")");
                    if (i != or.conditions().size() - 1)
                        builder.append("||");
                }
                builder.append(")");
                yield builder.toString();
            }
        };
    }

    private static String compileExpression(Rule.Expression expression, Consumer<String> prepend) {
        return switch (expression) {
            case Rule.Expression.Literal literal -> literal.value() + "";
            case Rule.Expression.NeighborsCount neighborsCount -> {
                final String result = "offsets" + COUNTER.incrementAndGet() + "_result";
                final String idxsName = "idx" + COUNTER.incrementAndGet();

                ArrayList<Integer> idxs = new ArrayList<>();
                for (Point point : neighborsCount.offsets()) {
                    int idx = 0;
                    for (int i = 0; i < indices.size(); i++) {
                        Integer[] x = indices.get(i);
                        if (point.sameBlock(x[0], x[1], x[2])) {
                            idx = i;
                            break;
                        }
                    }
                    idxs.add(idx);
                }
                prepend.accept("int " + idxsName + "[] = {");
                idxs.forEach(i -> prepend.accept(i + ","));
                prepend.accept("};");
                prepend.accept(String.format("""
                        int %s = 0;
                        for (int i = 0; i < %d; i++) {
                            x += indices[%s[i]][0];
                            y += indices[%s[i]][1];
                            z += indices[%s[i]][2];
                            if (x + 1 >= 0 && x + 1 < 3 && y + 1 >= 0 && y + 1 < 3 && z + 1 >= 0 && z + 1 < 3) {
                                if (%s) {
                                    %s++;
                                }
                            }
                            x -= indices[%s[i]][0];
                            y -= indices[%s[i]][1];
                            z -= indices[%s[i]][2];
                        }
                        """, result, idxs.size(), idxsName, idxsName, idxsName, compileCondition(neighborsCount.condition(), prepend), result, idxsName, idxsName, idxsName));

                yield result;
            }
            case Rule.Expression.Compare cmp -> {
                final String first = compileExpression(cmp.first(), prepend);
                final String second = compileExpression(cmp.second(), prepend);
                yield "(int)sign((double)" + first + "-" + second + ")";
            }
            case Rule.Expression.Index index -> "neighbors[inverted_indices[x+1][y+1][z+1]]"; // TODO states
            case Rule.Expression.NeighborIndex index ->
                    String.format("neighbors[inverted_indices[x+1+%s][y+1+%s][z+1+%s]]", index.x(), index.y(), index.z());
            case Rule.Expression.Operation operation -> {
                final String first = compileExpression(operation.first(), prepend);
                final String second = compileExpression(operation.second(), prepend);
                final String operator = switch (operation.type()) {
                    case ADD -> "+";
                    case SUBTRACT -> "-";
                    case MULTIPLY -> "*";
                    case DIVIDE -> "/";
                    case MODULO -> "%";
                };
                yield "(" + first + operator + second + ")";
            }
        };
    }

    public static cl_kernel compile(List<Rule> rules) {
        StringBuilder compiled = new StringBuilder();
        StringBuilder prep = new StringBuilder();
        for (Rule rule : rules) {
            StringBuilder res = new StringBuilder();
            switch (rule.result()) {
                case Rule.Result.Set set -> {
                    final Map<Integer, Rule.Expression> map = set.expressions();
                    // TODO state
                    final String value = compileExpression(map.get(0), prep::append);
                    res.append(String.format("return %s;", value));
                }
            }

            String cond = compileCondition(rule.condition(), prep::append);

            compiled.append(String.format("""
                    if (%s) {
                        %s
                    }
                    """, cond, res));
        }
        compiled.insert(0, prep);

        compiled.append("return neighbors[inverted_indices[x+1][y+1][z+1]];");

        String assembledKernel = String.format(KERNEL_SOURCE, 27, Block.AIR.stateId(), compiled);
        if (CLManager.DEBUG) {
            File debugOut = new File("kernel.cl");
            try (FileOutputStream fos = new FileOutputStream(debugOut)) {
                fos.write(assembledKernel.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // TODO: cross chunk magic
        return CLManager.INSTANCE.compileKernel(assembledKernel, "sampleKernel");
    }
}
