package dev.goldenstack.minestom_ca.backends.opencl;

import dev.goldenstack.minestom_ca.Rule;
import dev.goldenstack.minestom_ca.RuleAnalysis;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jocl.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static org.jocl.CL.*;

@SuppressWarnings("UnstableApiUsage")
public final class AutomataWorldCL {
    private static final Map<Instance, AutomataWorldCL> INSTANCES = new HashMap<>();
    private final Instance instance;
    private final List<Rule> rules;
    private final int minSection, maxSection;
    private final Long2ObjectOpenHashMap<AChunk> chunks = new Long2ObjectOpenHashMap<>();

    private final ComputeCL computeCL;
    private final cl_program program;

    // Analysis information
    private final int analysisStateCount;

    public static void create(Instance instance, List<Rule> rule) {
        INSTANCES.computeIfAbsent(instance, i -> new AutomataWorldCL(i, rule));
    }

    public static AutomataWorldCL get(Instance instance) {
        final AutomataWorldCL world = INSTANCES.get(instance);
        Objects.requireNonNull(world, "Unregistered instance!");
        return world;
    }

    public AutomataWorldCL(Instance instance, List<Rule> rules) {
        this.instance = instance;
        this.rules = rules;
        this.minSection = instance.getDimensionType().getMinY() / 16;
        this.maxSection = instance.getDimensionType().getMaxY() / 16;

        this.computeCL = new ComputeCL();
        final String source = RuleCL.compileRules(rules);
        this.program = computeCL.createProgram(source);
        System.out.println(source);

        // Analysis information
        this.analysisStateCount = rules.stream().mapToInt(RuleAnalysis::stateCount).max().orElseThrow();
    }

    public void tick() {
        loadChunks(); // Ensure that all chunks are loaded
        applyRules();
        updateChunks();
    }

    private void loadChunks() {
        this.instance.getChunks().forEach(chunk -> {
            final long chunkIndex = ChunkUtils.getChunkIndex(chunk.getChunkX(), chunk.getChunkZ());
            final AChunk achunk = this.chunks.computeIfAbsent(chunkIndex, i -> new AChunk(chunk));
            if (achunk.chunk != chunk) { // Chunk may have been unloaded and loaded back
                this.chunks.put(chunkIndex, new AChunk(chunk));
            }
        });
    }

    private void applyRules() {
        // Execute
        for (AChunk achunk : chunks.values()) {
            for (ASection section : achunk.sections) {
                if (section.requireRefresh) {
                    section.requireRefresh = false;
                    clEnqueueCopyBuffer(computeCL.commandQueue, section.mem_in, section.mem_out,
                            0, 0, section.byteSize(), 0, null, null);
                }
                clEnqueueNDRangeKernel(computeCL.commandQueue, section.kernel, 1, null, new long[]{1}, null, 0, null, null);
                clEnqueueCopyBuffer(computeCL.commandQueue, section.mem_out, section.mem_in, 0, 0, section.byteSize(), 0, null, null);
            }
        }
        clFinish(computeCL.commandQueue);
    }

    private void updateChunks() {
        for (AChunk aChunk : this.chunks.values()) {
            Chunk chunk = aChunk.chunk;
            for (int i = 0; i < aChunk.sections.length; i++) {
                Section section = chunk.getSections().get(i);
                if (section == null) continue;
                aChunk.sections[i].usePalette(aPalette -> section.blockPalette().setAll(aPalette::get), CL_MAP_READ);
            }
            // Invalidate packet cache
            try {
                var blockCacheField = DynamicChunk.class.getDeclaredField("chunkCache");
                blockCacheField.setAccessible(true);

                var lightCacheField = LightingChunk.class.getDeclaredField("lightCache");
                lightCacheField.setAccessible(true);

                ((CachedPacket) lightCacheField.get(chunk)).invalidate();
                ((CachedPacket) blockCacheField.get(chunk)).invalidate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // Send updated chunk to all viewers
            chunk.sendChunk();
        }
    }

    public void handlePlacement(Point point, Block block) {
        final long chunkIndex = ChunkUtils.getChunkIndex(point.chunkX(), point.chunkZ());
        AChunk chunk = this.chunks.get(chunkIndex);
        if (chunk == null) return;

        final int x = point.blockX();
        final int y = point.blockY();
        final int z = point.blockZ();
        final ASection section = chunk.sections[y / 16 - minSection];

        final int localX = ChunkUtils.toSectionRelativeCoordinate(x);
        final int localY = ChunkUtils.toSectionRelativeCoordinate(y);
        final int localZ = ChunkUtils.toSectionRelativeCoordinate(z);

        // Set to block state for visual palette
        section.usePalette(aPalette -> aPalette.set(localX, localY, localZ, block.stateId()), CL_MAP_WRITE);
        section.requireRefresh = true;
    }

    public final class AChunk {
        private final Chunk chunk;
        private final ASection[] sections;

        public AChunk(Chunk chunk) {
            this.chunk = chunk;
            final List<Section> sections = chunk.getSections();
            final int sectionCount = sections.size();

            this.sections = new ASection[sectionCount];

            // Init palette
            int i = 0;
            for (Section section : chunk.getSections()) {
                if (section == null) continue;
                // One palette for each state.
                // 0 is the visual palette which is first copied from the section's palette.
                // (To retrieve generation blocks)
                APalette tmp = new APalette();
                section.blockPalette().getAll(tmp::set);
                this.sections[i++] = new ASection(tmp);
            }
        }
    }

    public final class ASection {
        private final APalette tmpPalette;
        private final int byteSize;
        private final cl_kernel kernel;
        private final cl_mem mem_in;
        private final cl_mem mem_out;
        private boolean requireRefresh = true;

        public ASection(APalette palette) {
            this.tmpPalette = palette;
            this.byteSize = palette.values.limit();
            final var blocks = palette.values;
            this.kernel = computeCL.createKernel(program);
            this.mem_in = clCreateBuffer(computeCL.context,
                    CL_MEM_READ_WRITE | CL_MEM_ALLOC_HOST_PTR | CL_MEM_COPY_HOST_PTR,
                    byteSize(), Pointer.to(blocks), null);
            this.mem_out = clCreateBuffer(computeCL.context,
                    CL_MEM_READ_WRITE | CL_MEM_ALLOC_HOST_PTR,
                    byteSize(), null, null);

            clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(mem_in));
            clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(mem_out));
        }

        void usePalette(Consumer<APalette> consumer, long flags) {
            var mappedBuffer = clEnqueueMapBuffer(computeCL.commandQueue, mem_in,
                    CL_TRUE, flags, 0, byteSize(), 0, null, null, null)
                    .order(ByteOrder.nativeOrder());
            tmpPalette.values = mappedBuffer;
            consumer.accept(tmpPalette);
            clEnqueueUnmapMemObject(computeCL.commandQueue, mem_in, mappedBuffer, 0, null, null);
            tmpPalette.values = null;
        }

        long byteSize() {
            return byteSize;
        }
    }

    public final class APalette {
        private ByteBuffer values;
        private final byte bitsPerEntry = 16;

        public APalette() {
            final int valuesPerLong = 64 / bitsPerEntry;
            final int maxSize = 16 * 16 * 16;
            this.values = ByteBuffer.allocate((maxSize + valuesPerLong - 1) / valuesPerLong * 8).order(ByteOrder.nativeOrder());
        }

        private long getArray(int index) {
            return values.getLong(index * 8);
        }

        private void setArray(int index, long value) {
            this.values.putLong(index * 8, value);
        }

        public int get(int x, int y, int z) {
            final int bitsPerEntry = this.bitsPerEntry;
            final int sectionIndex = getSectionIndex(x, y, z);
            final int valuesPerLong = 64 / bitsPerEntry;
            final int index = sectionIndex / valuesPerLong;
            final int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;
            return (int) (getArray(index) >> bitIndex) & ((1 << bitsPerEntry) - 1);
        }

        public void set(int x, int y, int z, int value) {
            final int bitsPerEntry = this.bitsPerEntry;
            // Change to palette value
            final int valuesPerLong = 64 / bitsPerEntry;
            final int sectionIndex = getSectionIndex(x, y, z);
            final int index = sectionIndex / valuesPerLong;
            final int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;

            final long block = getArray(index);
            final long clear = (1L << bitsPerEntry) - 1L;
            final long result = block & ~(clear << bitIndex) | ((long) value << bitIndex);
            setArray(index, result);
        }

        static int getSectionIndex(int x, int y, int z) {
            final int dimensionMask = 16 - 1;
            final int dimensionBitCount = Integer.SIZE - Integer.numberOfLeadingZeros(dimensionMask);
            return (y & dimensionMask) << (dimensionBitCount << 1) |
                    (z & dimensionMask) << dimensionBitCount |
                    (x & dimensionMask);
        }
    }
}
