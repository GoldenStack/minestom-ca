package dev.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.util.*;

import static dev.goldenstack.minestom_ca.Rule.Condition;
import static dev.goldenstack.minestom_ca.Rule.Result;

@SuppressWarnings("UnstableApiUsage")
public final class AutomataWorld {
    private static final Map<Instance, AutomataWorld> INSTANCES = new HashMap<>();
    private final Instance instance;
    private final List<Rule> rules;
    private final int minSection, maxSection;
    private final Long2ObjectOpenHashMap<AChunk> chunks = new Long2ObjectOpenHashMap<>();
    private boolean buffer = false;

    // Analysis information
    private final int analysisStateCount;

    public static void create(Instance instance, List<Rule> rule) {
        INSTANCES.computeIfAbsent(instance, i -> new AutomataWorld(i, rule));
    }

    public static AutomataWorld get(Instance instance) {
        final AutomataWorld world = INSTANCES.get(instance);
        Objects.requireNonNull(world, "Unregistered instance!");
        return world;
    }

    public AutomataWorld(Instance instance, List<Rule> rules) {
        this.instance = instance;
        this.rules = rules;
        this.minSection = instance.getDimensionType().getMinY() / 16;
        this.maxSection = instance.getDimensionType().getMaxY() / 16;

        // Analysis information
        this.analysisStateCount = rules.stream().mapToInt(RuleAnalysis::stateCount).max().orElseThrow();
    }

    public void tick() {
        loadChunks(); // Ensure that all chunks are loaded
        applyRules();
        updateChunks();
        // Copy write buffer to read buffer
        for (AChunk achunk : this.chunks.values()) {
            for (ASection section : achunk.sections) {
                final APalette[] palettes = section.writePalettes();
                final APalette[] palettes2 = section.readPalettes();
                for (int i = 0; i < palettes.length; i++) {
                    palettes2[i].copyFrom(palettes[i]);
                }
            }
        }
        this.buffer = !buffer; // Flip buffer
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
        final int sectionCount = maxSection - minSection;
        for (AChunk achunk : chunks.values()) {
            final Chunk chunk = achunk.chunk;
            final int sectionStart = minSection * 16;
            final int chunkX = chunk.getChunkX();
            final int chunkZ = chunk.getChunkZ();
            for (int i = 0; i < sectionCount; i++) {
                final APalette[] readPalettes = achunk.sections[i].readPalettes();
                APalette[] writePalettes = achunk.sections[i].writePalettes();
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            if (x > 0 && x < 15 && y > 0 && y < 15 && z > 0 && z < 15) {
                                // Center handling
                                for (Rule rule : rules) {
                                    if (handleConditionCenter(rule.condition(), readPalettes, x, y, z) == 0)
                                        continue;
                                    handleResultCenter(x, y, z, writePalettes, rule.result());
                                    achunk.updated = true;
                                }
                            } else {
                                // Border handling
                                final int blockX = chunkX * 16 + x;
                                final int blockY = i * 16 + y + sectionStart;
                                final int blockZ = chunkZ * 16 + z;
                                for (Rule rule : rules) {
                                    if (handleConditionBorder(rule.condition(), blockX, blockY, blockZ) == 0) continue;
                                    handleResultBorder(blockX, blockY, blockZ, rule.result());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private int handleConditionCenter(Condition condition, APalette[] palettes, int x, int y, int z) {
        return switch (condition) {
            case Condition.Literal literal -> literal.value();
            case Condition.Index index -> palettes[index.stateIndex()].get(x, y, z);
            case Condition.Neighbors neighbors -> {
                int count = 0;
                for (var offset : neighbors.offsets()) {
                    if (handleConditionCenter(
                            neighbors.condition(), palettes,
                            x + offset.blockX(),
                            y + offset.blockY(),
                            z + offset.blockZ()) != 0) {
                        count++;
                    }
                }
                yield count;
            }
            case Condition.And and -> {
                for (Condition c : and.conditions()) {
                    if (handleConditionCenter(c, palettes, x, y, z) == 0) yield 0;
                }
                yield 1;
            }
            case Condition.Or or -> {
                for (Condition c : or.conditions()) {
                    if (handleConditionCenter(c, palettes, x, y, z) != 0) yield 1;
                }
                yield 0;
            }
            case Condition.Not not -> handleConditionCenter(not.condition(), palettes, x, y, z) == 0 ? 1 : 0;
            case Condition.Equal equal ->
                    handleConditionCenter(equal.first(), palettes, x, y, z) == handleConditionCenter(equal.second(), palettes, x, y, z)
                            ? 1 : 0;
        };
    }

    private void handleResultCenter(int x, int y, int z, APalette[] palettes, Result result) {
        switch (result) {
            case Result.And and -> {
                for (Result r : and.others()) {
                    handleResultCenter(x, y, z, palettes, r);
                }
            }
            case Result.Set set -> {
                final Point offset = set.offset();
                palettes[set.index()].set(
                        x + offset.blockX(),
                        y + offset.blockY(),
                        z + offset.blockZ(),
                        set.value());
            }
        }
    }

    private int handleConditionBorder(Condition condition, int x, int y, int z) {
        return switch (condition) {
            case Condition.Literal literal -> literal.value();
            case Condition.Index index -> getState(x, y, z, index.stateIndex());
            case Condition.Neighbors neighbors -> {
                int count = 0;
                for (var offset : neighbors.offsets()) {
                    if (handleConditionBorder(
                            neighbors.condition(),
                            x + offset.blockX(),
                            y + offset.blockY(),
                            z + offset.blockZ()) != 0) {
                        count++;
                    }
                }
                yield count;
            }
            case Condition.And and -> {
                for (Condition c : and.conditions()) {
                    if (handleConditionBorder(c, x, y, z) == 0) yield 0;
                }
                yield 1;
            }
            case Condition.Or or -> {
                for (Condition c : or.conditions()) {
                    if (handleConditionBorder(c, x, y, z) != 0) yield 1;
                }
                yield 0;
            }
            case Condition.Not not -> handleConditionBorder(not.condition(), x, y, z) == 0 ? 1 : 0;
            case Condition.Equal equal ->
                    handleConditionBorder(equal.first(), x, y, z) == handleConditionBorder(equal.second(), x, y, z)
                            ? 1 : 0;
        };
    }

    private void handleResultBorder(int x, int y, int z, Result result) {
        switch (result) {
            case Result.And and -> {
                for (Result r : and.others()) {
                    handleResultBorder(x, y, z, r);
                }
            }
            case Result.Set set -> {
                final Point offset = set.offset();
                final Point changePoint = offset.add(x, y, z);

                var ac = chunks.get(ChunkUtils.getChunkIndex(
                        ChunkUtils.getChunkCoordinate(changePoint.blockX()),
                        ChunkUtils.getChunkCoordinate(changePoint.blockZ())));
                setState(ac, changePoint.blockX(), changePoint.blockY(), changePoint.blockZ(),
                        Map.of(set.index(), set.value()));
                ac.updated = true;
            }
        }
    }

    private void updateChunks() {
        for (AChunk aChunk : this.chunks.values()) {
            if (!aChunk.updated) continue;
            aChunk.updated = false;
            Chunk chunk = aChunk.chunk;
            for (int i = 0; i < aChunk.sections.length; i++) {
                Section section = chunk.getSections().get(i);
                if (section == null) continue;
                final APalette[] palettes = aChunk.sections[i].writePalettes();
                final APalette visualPalette = palettes[0];
                section.blockPalette().setAll(visualPalette::get);
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

    public int getState(int x, int y, int z, int state) {
        final int chunkX = ChunkUtils.getChunkCoordinate(x);
        final int chunkZ = ChunkUtils.getChunkCoordinate(z);
        final long chunkIndex = ChunkUtils.getChunkIndex(chunkX, chunkZ);
        AChunk chunk = this.chunks.get(chunkIndex);
        if (chunk == null) return 0;

        final int sectionY = y / 16;
        final ASection section = chunk.sections[sectionY - minSection];
        final APalette palette = section.readPalettes()[state];
        return palette.get(
                ChunkUtils.toSectionRelativeCoordinate(x),
                ChunkUtils.toSectionRelativeCoordinate(y),
                ChunkUtils.toSectionRelativeCoordinate(z)
        );
    }

    private void setState(AChunk chunk, int x, int y, int z, Map<Integer, Integer> changes) {
        final int sectionY = y / 16;
        final ASection section = chunk.sections[sectionY - minSection];
        final APalette[] palettes = section.writePalettes();
        for (var entry : changes.entrySet()) {
            final int state = entry.getKey();
            final int newState = entry.getValue();
            final APalette palette = palettes[state];
            palette.set(
                    ChunkUtils.toSectionRelativeCoordinate(x),
                    ChunkUtils.toSectionRelativeCoordinate(y),
                    ChunkUtils.toSectionRelativeCoordinate(z),
                    newState
            );
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
        final APalette[] palettes = section.writePalettes();

        final int localX = ChunkUtils.toSectionRelativeCoordinate(x);
        final int localY = ChunkUtils.toSectionRelativeCoordinate(y);
        final int localZ = ChunkUtils.toSectionRelativeCoordinate(z);

        // Set to block state for visual palette
        palettes[0].set(localX, localY, localZ, block.stateId());
        // Set to 0 for all other palettes (clear internal states)
        for (int i = 1; i < palettes.length; i++) {
            palettes[i].set(localX, localY, localZ, 0);
        }
    }

    public final class AChunk {
        private final Chunk chunk;
        private final ASection[] sections;
        private boolean updated = false;

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
                APalette[] palettes = new APalette[analysisStateCount];
                APalette[] palettes2 = new APalette[analysisStateCount];
                Arrays.setAll(palettes, j -> new APalette());
                Arrays.setAll(palettes2, j -> new APalette());
                section.blockPalette().getAll((x, y, z, value) -> {
                    palettes[0].set(x, y, z, value);
                    palettes2[0].set(x, y, z, value);
                });
                this.sections[i++] = new ASection(palettes, palettes2);
            }
        }
    }

    public final class ASection {
        private final APalette[] palettes;
        private final APalette[] palettes2;

        public ASection(APalette[] palettes, APalette[] palettes2) {
            this.palettes = palettes;
            this.palettes2 = palettes2;
        }

        APalette[] readPalettes() {
            return buffer ? palettes2 : palettes;
        }

        APalette[] writePalettes() {
            return buffer ? palettes : palettes2;
        }
    }

    public final class APalette {
        private long[] values;
        private byte bitsPerEntry;

        public APalette() {
            this.bitsPerEntry = 16;

            final int valuesPerLong = 64 / bitsPerEntry;
            final int maxSize = 16 * 16 * 16;
            this.values = new long[(maxSize + valuesPerLong - 1) / valuesPerLong];
        }

        public int get(int x, int y, int z) {
            final int bitsPerEntry = this.bitsPerEntry;
            final int sectionIndex = getSectionIndex(x, y, z);
            final int valuesPerLong = 64 / bitsPerEntry;
            final int index = sectionIndex / valuesPerLong;
            final int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;
            return (int) (values[index] >> bitIndex) & ((1 << bitsPerEntry) - 1);
        }

        public void set(int x, int y, int z, int value) {
            final int bitsPerEntry = this.bitsPerEntry;
            final long[] values = this.values;
            // Change to palette value
            final int valuesPerLong = 64 / bitsPerEntry;
            final int sectionIndex = getSectionIndex(x, y, z);
            final int index = sectionIndex / valuesPerLong;
            final int bitIndex = (sectionIndex - index * valuesPerLong) * bitsPerEntry;

            final long block = values[index];
            final long clear = (1L << bitsPerEntry) - 1L;
            values[index] = block & ~(clear << bitIndex) | ((long) value << bitIndex);
        }

        static int getSectionIndex(int x, int y, int z) {
            final int dimensionMask = 16 - 1;
            final int dimensionBitCount = Integer.SIZE - Integer.numberOfLeadingZeros(dimensionMask);
            return (y & dimensionMask) << (dimensionBitCount << 1) |
                    (z & dimensionMask) << dimensionBitCount |
                    (x & dimensionMask);
        }

        private void copyFrom(APalette palette) {
            if (this.values.length == palette.values.length) {
                System.arraycopy(palette.values, 0, this.values, 0, this.values.length);
            } else {
                this.values = palette.values.clone();
            }
            this.bitsPerEntry = palette.bitsPerEntry;
        }
    }
}
