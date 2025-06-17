package net.minestom.ca;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.trait.InstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.CustomData;
import net.minestom.server.tag.Tag;
import net.minestom.server.tag.TagReadable;
import net.minestom.server.tag.TagSerializer;
import net.minestom.server.tag.TagWritable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutomataImpl {
    public static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    public static final Tag<Boolean> AUTOMATA_DEBUG = Tag.Boolean("automata_debug").defaultValue(false);
    public static final EventNode<InstanceEvent> AUTOMATA_EVENT_NODE = EventNode.type("automata", EventFilter.INSTANCE)
            .addListener(PlayerBlockPlaceEvent.class, event -> {
                final Point point = event.getBlockPosition();
                final Block block = event.getBlock();
                Automata.World world = Automata.World.get(event.getPlayer().getInstance());

                Map<Automata.CellRule.State, Long> properties = new HashMap<>();
                properties.put(Automata.CellRule.BLOCK_STATE, (long) block.stateId());
                final ItemStack item = event.getPlayer().getItemInHand(event.getHand());
                if (item.has(DataComponents.CUSTOM_DATA)) {
                    for (Automata.CellRule.State state : world.rules().states()) {
                        final Tag<Long> tag = AutomataImpl.CellTag(state);
                        final Long value = item.getTag(tag);
                        if (value != null) properties.put(state, value);
                    }
                }
                world.handlePlacement(point.blockX(), point.blockY(), point.blockZ(), properties);
            })
            .addListener(PlayerBlockBreakEvent.class, event -> {
                final Point point = event.getBlockPosition();
                Automata.World world = Automata.World.get(event.getPlayer().getInstance());
                world.handlePlacement(point, Block.AIR);
            })
            .addListener(PlayerBlockInteractEvent.class, event -> {
                final Player player = event.getPlayer();
                final Point point = event.getBlockPosition();
                if (event.getHand() != PlayerHand.MAIN) return;
                final ItemStack item = event.getPlayer().getItemInHand(event.getHand());
                if (item.getTag(AUTOMATA_DEBUG)) showBlockInfo(player, point);
                applyAction(player, point, item, CellEvent.INTERACT);
            })
            .addListener(InstanceChunkLoadEvent.class, event -> {
                Automata.World world = Automata.World.get(event.getInstance());
                world.handleChunkLoad(event.getChunkX(), event.getChunkZ());
            })
            .addListener(InstanceChunkUnloadEvent.class, event -> {
                Automata.World world = Automata.World.get(event.getInstance());
                world.handleChunkUnload(event.getChunkX(), event.getChunkZ());
            })
            .addListener(InstanceTickEvent.class, event -> {
                if (!RUNNING.get()) return;
                final Instance eventInstance = event.getInstance();
                final long start = System.nanoTime();

                Automata.World world = Automata.World.get(eventInstance);
                final Automata.Metrics metrics = world.tick();

                final long duration = System.nanoTime() - start;
                final double mspt = duration / 1.0e6;

                // Calculate modification ratio (percentage of processed blocks that were modified)
                String ratio = "0%";
                if (metrics.processedBlocks() > 0) {
                    final int modRatio = (metrics.modifiedBlocks() * 100) / metrics.processedBlocks();
                    ratio = modRatio + "%";
                }

                Component header = Component.text()
                        .append(Component.text("§b■ §fMSPT: §a" + String.format("%.2f", mspt) + "ms"))
                        .append(Component.newline())
                        .append(Component.text("§b■ §fProcessed Sections: §a" + String.format("%,d", metrics.processedSections())))
                        .append(Component.newline())
                        .append(Component.text("§b■ §fProcessed Blocks: §a" + String.format("%,d", metrics.processedBlocks())))
                        .append(Component.newline())
                        .append(Component.text("§b■ §fModified Blocks: §a" + String.format("%,d", metrics.modifiedBlocks()) + " §f(§a" + ratio + "§f)"))
                        .build();

                eventInstance.sendPlayerListHeader(header);
            });

    private static void showBlockInfo(Player player, Point point) {
        Automata.World world = Automata.World.get(player.getInstance());
        if (world == null) return;
        final Map<String, Long> indexes = world.query().queryNames(point.blockX(), point.blockY(), point.blockZ());
        player.sendMessage(Component.text()
                .append(Component.text("Block States").color(NamedTextColor.GRAY)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text(point.blockX() + "," + point.blockY() + "," + point.blockZ())
                        .color(NamedTextColor.DARK_GRAY))
                .build());

        if (indexes.isEmpty()) {
            player.sendMessage(Component.text("No states available")
                    .color(NamedTextColor.RED).decorate(TextDecoration.ITALIC));
        } else {
            indexes.forEach((name, value) -> player.sendMessage(Component.text()
                    .append(Component.text(name).color(NamedTextColor.GRAY))
                    .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(value).color(NamedTextColor.WHITE))
                    .build()));
        }
    }

    private static void applyAction(Player player, Point point, ItemStack item, CellEvent event) {
        Automata.World world = Automata.World.get(player.getInstance());
        if (world == null) return;

        final CustomData data = item.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        // FIXME: `Tag#path` is broken on items
        CompoundBinaryTag actions = data.nbt();//data.nbt().getCompound("cell_actions");
        if (actions.isEmpty()) return;
        Map<Automata.CellRule.State, Long> properties = new HashMap<>();
        for (String key : actions.keySet()) {
            final Automata.CellRule.State state = world.rules().state(key);
            if (state == null) continue;
            final Tag<CellAction> tag = AutomataImpl.CellActionTag(state);
            final CellAction action = item.getTag(tag);
            if (action == null || !action.event().equals(event)) continue;
            final long value = actionValue(action.expression()); // TODO: operation support
            properties.put(state, value);
        }
        if (properties.isEmpty()) return;
        world.updateAt(point.blockX(), point.blockY(), point.blockZ(), properties);
        player.sendMessage(Component.text("Applied " + event.name().toLowerCase() + " action at " +
                        point.blockX() + "," + point.blockY() + "," + point.blockZ())
                .color(NamedTextColor.GREEN));
    }

    private static long actionValue(String expression) {
        // TODO: operation support
        return Long.parseLong(expression);
    }

    public enum CellEvent {
        PLACE,
        BREAK,
        INTERACT
    }

    public record CellAction(CellEvent event, String expression) {
    }

    static final CellActionSerializer CELL_ACTION_SERIALIZER = new CellActionSerializer();

    static final class CellActionSerializer implements TagSerializer<CellAction> {
        private static final Tag<String> EVENT_TAG = Tag.String("event");
        private static final Tag<String> EXPRESSION_TAG = Tag.String("expression");

        @Override
        public @Nullable CellAction read(@NotNull TagReadable reader) {
            final String eventStr = reader.getTag(EVENT_TAG);
            final String expression = reader.getTag(EXPRESSION_TAG);
            if (eventStr == null || expression == null) return null;
            final CellEvent event = CellEvent.valueOf(eventStr);
            return new CellAction(event, expression);
        }

        @Override
        public void write(@NotNull TagWritable writer, @NotNull CellAction value) {
            writer.setTag(EVENT_TAG, value.event().name());
            writer.setTag(EXPRESSION_TAG, value.expression());
        }
    }

    public static Tag<Long> CellTag(Automata.CellRule.State state) {
        return Tag.Long(state.name()).path("cell_states");
    }

    public static Tag<CellAction> CellActionTag(Automata.CellRule.State state) {
        return Tag.Structure(state.name(), CELL_ACTION_SERIALIZER).path("cell_actions");
    }
}
