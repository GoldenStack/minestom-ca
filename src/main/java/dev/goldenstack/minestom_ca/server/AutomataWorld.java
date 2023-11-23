package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.rule.Condition;
import dev.goldenstack.minestom_ca.rule.LocalState;
import dev.goldenstack.minestom_ca.rule.Result;
import dev.goldenstack.minestom_ca.rule.Rule;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.palette.Palette;
import net.minestom.server.network.packet.server.CachedPacket;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AutomataWorld {
    private static final Map<Instance, AutomataWorld> instances = new HashMap<>();
    private final Instance instance;
    private final List<Rule> rules;
    private final int minSection;
    private final Map<Chunk, AChunk> chunks = new HashMap<>();
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
    }

    public void tick() {
        this.instance.getChunks().forEach(chunk -> this.chunks.computeIfAbsent(chunk, AChunk::new));
        for (Chunk chunk : instance.getChunks()) {
            final int sectionStart = chunk.getMinSection() * 16;
            // Loop through each block position
            final int chunkX = chunk.getChunkX();
            final int chunkZ = chunk.getChunkZ();
            for (int i = 0; i < chunk.getSections().size(); i++) {
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            final Point point = new Vec(
                                    chunkX * 16 + x,
                                    i * 16 + y + sectionStart,
                                    chunkZ * 16 + z
                            );
                            LocalState localState = at(point);
                            for (Rule rule : rules) {
                                final Condition condition = rule.condition();
                                final Result result = rule.result();
                                if (condition.test(localState)) {
                                    final Map<Point, Map<Integer, Integer>> localChanges = result.apply(localState);
                                    for (var entry : localChanges.entrySet()) {
                                        final Point changePointOffset = entry.getKey();
                                        final Map<Integer, Integer> changeValues = entry.getValue();

                                        final Point changePoint = point.add(changePointOffset);
                                        setState(changePoint, changeValues);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Apply changes
        for (var entry : this.chunks.entrySet()) {
            var chunk = entry.getKey();
            var aChunk = entry.getValue();
            for (int i = 0; i < aChunk.sections.length; i++) {
                Section section = chunk.getSections().get(i);
                if (section == null) continue;
                final Palette[] palettes = aChunk.sections[i].writePalettes();
                final Palette visualPalette = palettes[0];
                section.blockPalette().setAll(visualPalette::get);
            }
            // Invalidate packet cache
            try {
                var field = DynamicChunk.class.getDeclaredField("chunkCache");
                field.setAccessible(true);
                var chunkCache = (CachedPacket) field.get(chunk);
                chunkCache.invalidate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            // Send updated chunk to all viewers
            chunk.sendChunk();
        }

        // Copy write buffer to read buffer
        for (var achunk : this.chunks.values()) {
            for (var section : achunk.sections) {
                final Palette[] palettes = section.writePalettes();
                final Palette[] palettes2 = section.readPalettes();
                for (int i = 0; i < palettes.length; i++) {
                    palettes2[i] = palettes[i].clone();
                }
            }
        }
        this.buffer = !buffer; // Flip buffer
    }

    @NotNull LocalState at(@NotNull Point pos) {
        return new LocalState() {
            @Override
            public LocalState relative(Point point) {
                return at(pos.add(point));
            }

            @Override
            public int selfStateValue(int state) {
                return getState(pos, state);
            }

            @Override
            public int relativeStateValue(int state, int x, int y, int z) {
                return getState(pos.add(x, y, z), state);
            }
        };
    }

    public int getState(Point point, int state) {
        AChunk chunk = chunks.entrySet().stream()
                .filter(entry -> {
                    var c = entry.getKey();
                    return c.getChunkX() == point.chunkX() && c.getChunkZ() == point.chunkZ();
                })
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (chunk == null) return 0;

        final int sectionY = point.section();
        final ASection section = chunk.sections[sectionY - minSection];
        final Palette palette = section.readPalettes()[state];
        return palette.get(
                ChunkUtils.toSectionRelativeCoordinate(point.blockX()),
                ChunkUtils.toSectionRelativeCoordinate(point.blockY()),
                ChunkUtils.toSectionRelativeCoordinate(point.blockZ())
        );
    }

    public void setState(Point point, Map<Integer, Integer> changes) {
        AChunk chunk = chunks.entrySet().stream()
                .filter(entry -> {
                    var c = entry.getKey();
                    return c.getChunkX() == point.chunkX() && c.getChunkZ() == point.chunkZ();
                })
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
        if (chunk == null) return;

        final int sectionY = point.section();
        final ASection section = chunk.sections[sectionY - minSection];
        final Palette[] palettes = section.writePalettes();
        for (var entry : changes.entrySet()) {
            final int state = entry.getKey();
            final int newState = entry.getValue();
            final Palette palette = palettes[state];
            palette.set(
                    ChunkUtils.toSectionRelativeCoordinate(point.blockX()),
                    ChunkUtils.toSectionRelativeCoordinate(point.blockY()),
                    ChunkUtils.toSectionRelativeCoordinate(point.blockZ()),
                    newState
            );
        }
    }

    public final class AChunk {
        private final ASection[] sections;

        public AChunk(Chunk chunk) {
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
