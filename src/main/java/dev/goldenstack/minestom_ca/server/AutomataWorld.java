package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.rule.Condition;
import dev.goldenstack.minestom_ca.rule.LocalState;
import dev.goldenstack.minestom_ca.rule.Result;
import dev.goldenstack.minestom_ca.rule.Rule;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("UnstableApiUsage")
public final class AutomataWorld {
    private static final Map<Instance, AutomataWorld> instances = new HashMap<>();
    private final Instance instance;
    private final List<Rule> rules;
    private final int minSection, maxSection;
    private final Long2ObjectOpenHashMap<AChunk> chunks = new Long2ObjectOpenHashMap<>();
    private boolean buffer = false;

    public static AutomataWorld create(Instance instance, List<Rule> rule) {
        return instances.computeIfAbsent(instance, i -> new AutomataWorld(i, rule));
    }

    public static AutomataWorld get(Instance instance) {
        final AutomataWorld world = instances.get(instance);
        Objects.requireNonNull(world, "Unregistered instance!");
        return world;
    }

    public AutomataWorld(Instance instance, List<Rule> rules) {
        this.instance = instance;
        this.rules = rules;
        this.minSection = instance.getDimensionType().getMinY() / 16;
        this.maxSection = instance.getDimensionType().getMaxY() / 16;
    }

    public void tick() {
        loadChunks(); // Ensure that all chunks are loaded
        applyRules();
        updateChunks();
        // Copy write buffer to read buffer
        for (AChunk achunk : this.chunks.values()) {
            for (ASection section : achunk.sections) {
                final Palette[] palettes = section.writePalettes();
                final Palette[] palettes2 = section.readPalettes();
                for (int i = 0; i < palettes.length; i++) {
                    palettes2[i] = palettes[i].clone();
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
        var localState = new LocalState() {
            int x, y, z;

            @Override
            public int relativeTest(int x2, int y2, int z2, Condition condition) {
                x += x2;
                y += y2;
                z += z2;

                final int result = condition.applyAsInt(this);

                x -= x2;
                y -= y2;
                z -= z2;

                return result;
            }

            @Override
            public int selfStateValue(int state) {
                return getState(x, y, z, state);
            }

            @Override
            public int relativeStateValue(int state, int x2, int y2, int z2) {
                return getState(x + x2, y + y2, z + z2, state);
            }
        };

        final int sectionCount = maxSection - minSection;
        for (AChunk achunk : chunks.values()) {
            final Chunk chunk = achunk.chunk;
            final int sectionStart = minSection * 16;
            final int chunkX = chunk.getChunkX();
            final int chunkZ = chunk.getChunkZ();
            for (int i = 0; i < sectionCount; i++) {
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            localState.x = chunkX * 16 + x;
                            localState.y = i * 16 + y + sectionStart;
                            localState.z = chunkZ * 16 + z;
                            for (Rule rule : rules) {
                                final Condition condition = rule.condition();
                                final Result result = rule.result();
                                if (condition.applyAsInt(localState) == 0) continue;
                                final Map<Point, Map<Integer, Integer>> localChanges = result.apply(localState);
                                for (var entry : localChanges.entrySet()) {
                                    final Point changePoint = entry.getKey().add(localState.x, localState.y, localState.z);

                                    var ac = chunks.get(ChunkUtils.getChunkIndex(
                                            ChunkUtils.getChunkCoordinate(changePoint.blockX()),
                                            ChunkUtils.getChunkCoordinate(changePoint.blockZ())));
                                    final Map<Integer, Integer> changeValues = entry.getValue();
                                    setState(ac, changePoint.blockX(), changePoint.blockY(), changePoint.blockZ(),
                                            changeValues);
                                    ac.updated = true;
                                }
                            }
                        }
                    }
                }
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
                final Palette[] palettes = aChunk.sections[i].writePalettes();
                final Palette visualPalette = palettes[0];
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
        final Palette palette = section.readPalettes()[state];
        return palette.get(
                ChunkUtils.toSectionRelativeCoordinate(x),
                ChunkUtils.toSectionRelativeCoordinate(y),
                ChunkUtils.toSectionRelativeCoordinate(z)
        );
    }

    private void setState(AChunk chunk, int x, int y, int z, Map<Integer, Integer> changes) {
        final int sectionY = y / 16;
        final ASection section = chunk.sections[sectionY - minSection];
        final Palette[] palettes = section.writePalettes();
        for (var entry : changes.entrySet()) {
            final int state = entry.getKey();
            final int newState = entry.getValue();
            final Palette palette = palettes[state];
            palette.set(
                    ChunkUtils.toSectionRelativeCoordinate(x),
                    ChunkUtils.toSectionRelativeCoordinate(y),
                    ChunkUtils.toSectionRelativeCoordinate(z),
                    newState
            );
        }
    }

    public void handlePlacement(Point point, Block block) {
        final int x = point.blockX();
        final int y = point.blockY();
        final int z = point.blockZ();
        final int chunkX = ChunkUtils.getChunkCoordinate(x);
        final int chunkZ = ChunkUtils.getChunkCoordinate(z);
        final long chunkIndex = ChunkUtils.getChunkIndex(chunkX, chunkZ);
        AChunk chunk = this.chunks.get(chunkIndex);
        if (chunk == null) return;
        setState(chunk, x, y, z, Map.of(0, (int) block.stateId()));
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
                final Palette palette = section.blockPalette();

                // Only one palette for block state id, TODO: internal states
                this.sections[i++] = new ASection(
                        new Palette[]{palette.clone()},
                        new Palette[]{palette.clone()}
                );
            }
        }
    }

    public final class ASection {
        private final Palette[] palettes;
        private final Palette[] palettes2;

        public ASection(Palette[] palettes, Palette[] palettes2) {
            this.palettes = palettes;
            this.palettes2 = palettes2;
        }

        Palette[] readPalettes() {
            return buffer ? palettes2 : palettes;
        }

        Palette[] writePalettes() {
            return buffer ? palettes : palettes2;
        }
    }
}
