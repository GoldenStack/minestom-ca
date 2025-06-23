package net.minestom.ca;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minestom.ca.Automata.*;

public final class AutomataImpl {
    public static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    public static final Tag<Boolean> AUTOMATA_DEBUG = Tag.Boolean("automata_debug").defaultValue(false);
    public static final Tag<Boolean> AUTOMATA_STOPWATCH = Tag.Boolean("automata_stopwatch").defaultValue(false);
    public static final EventNode<InstanceEvent> AUTOMATA_EVENT_NODE = EventNode.type("automata", EventFilter.INSTANCE)
            .addListener(PlayerBlockPlaceEvent.class, event -> {
                final Point point = event.getBlockPosition();
                final Block block = event.getBlock();
                World world = World.get(event.getPlayer().getInstance());
                final ItemStack item = event.getPlayer().getItemInHand(event.getHand());
                final Map<Rule.State, Long> properties = itemStates(world, item, block);
                world.handlePlacement(point.blockX(), point.blockY(), point.blockZ(), properties);
            })
            .addListener(PlayerBlockBreakEvent.class, event -> {
                final Point point = event.getBlockPosition();
                World world = World.get(event.getPlayer().getInstance());
                world.handlePlacement(point, Block.AIR);
            })
            .addListener(PlayerBlockInteractEvent.class, event -> {
                final Player player = event.getPlayer();
                final Point point = event.getBlockPosition();
                final ItemStack item = event.getPlayer().getItemInHand(event.getHand());
                if (item.getTag(AUTOMATA_DEBUG)) showBlockInfo(player, point);
                final ItemStack update = applyAction(player, point, item, CellEvent.INTERACT);
                if (update != null) player.setItemInHand(event.getHand(), update);
            })
            .addListener(PlayerUseItemEvent.class, event -> {
                final Player player = event.getPlayer();
                final ItemStack item = event.getItemStack();
                if (item.getTag(AUTOMATA_STOPWATCH)) useStopwatch(player);
            })
            .addListener(InstanceChunkLoadEvent.class, event -> {
                World world = World.get(event.getInstance());
                world.handleChunkLoad(event.getChunkX(), event.getChunkZ());
            })
            .addListener(InstanceChunkUnloadEvent.class, event -> {
                World world = World.get(event.getInstance());
                world.handleChunkUnload(event.getChunkX(), event.getChunkZ());
            })
            .addListener(InstanceTickEvent.class, event -> {
                if (!RUNNING.get()) return;
                final Instance eventInstance = event.getInstance();
                worldTick(eventInstance);
            });

    private static void worldTick(Instance instance) {
        World world = World.get(instance);
        if (world == null) return;

        final long start = System.nanoTime();
        final Metrics metrics = world.tick();

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

        instance.sendPlayerListHeader(header);
    }

    private static Map<Rule.State, Long> itemStates(World world, ItemStack item, Block block) {
        Map<Rule.State, Long> properties = new HashMap<>();
        properties.put(Rule.BLOCK_STATE, (long) block.stateId());
        if (item.has(DataComponents.CUSTOM_DATA)) {
            for (Rule.State state : world.states()) {
                final Tag<Long> tag = AutomataImpl.CellTag(state);
                final Long value = item.getTag(tag);
                if (value != null) properties.put(state, value);
            }
        }
        return properties;
    }

    private static void showBlockInfo(Player player, Point point) {
        final Instance instance = player.getInstance();
        final World world = World.get(instance);
        if (world == null) return;
        final Map<Rule.State, Long> indexes = world.query().queryNames(point.blockX(), point.blockY(), point.blockZ());
        player.sendMessage(Component.text()
                .append(Component.text("Block States").color(NamedTextColor.GRAY)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text(point.blockX() + "," + point.blockY() + "," + point.blockZ())
                        .color(NamedTextColor.DARK_GRAY))
                .build());
        for (Map.Entry<Rule.State, Long> entry : indexes.entrySet()) {
            final Rule.State state = entry.getKey();
            final Long value = entry.getValue();
            if (value == null || value == 0) continue;
            final String stateString = state.visualizer().apply(value);
            player.sendMessage(Component.text()
                    .append(Component.text(state.name()).color(NamedTextColor.GRAY))
                    .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(stateString).color(NamedTextColor.WHITE))
                    .build());
        }
    }

    private static void useStopwatch(Player player) {
        // Pause if running
        if (RUNNING.compareAndExchange(true, false)) {
            player.sendMessage(Component.text("Automata paused").color(NamedTextColor.YELLOW));
        }
        // Manually tick the world
        final Instance instance = player.getInstance();
        if (instance == null) return;
        worldTick(instance);
        player.sendMessage(Component.text("Automata ticked manually").color(NamedTextColor.GREEN));
    }

    private static ItemStack applyAction(Player player, Point point, ItemStack item, CellEvent event) {
        World world = World.get(player.getInstance());
        if (world == null) return null;
        final CustomData data = item.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundBinaryTag actions = data.nbt().getCompound("cell_actions");
        if (actions.isEmpty()) return null;

        ItemStack updatedItem = null;
        Map<Rule.State, Long> properties = new HashMap<>();
        for (String key : actions.keySet()) {
            final Rule.State state = world.state(key);
            if (state == null) continue;
            final Tag<CellAction> tag = AutomataImpl.CellActionTag(state);
            final CellAction action = item.getTag(tag);
            if (action == null || !action.event().equals(event)) continue;
            // Run action
            final long value = actionValue(action.expression()); // TODO: operation support
            properties.put(state, value);
            // Item update
            if (action.consume()) {
                updatedItem = Objects.requireNonNullElse(updatedItem, item).consume(1);
            }
            if (action.damage() > 0) {
                updatedItem = Objects.requireNonNullElse(updatedItem, item).damage(action.damage());
            }
        }
        if (properties.isEmpty()) return null;
        world.updateAt(point.blockX(), point.blockY(), point.blockZ(), properties);
        player.sendMessage(Component.text("Applied " + event.name().toLowerCase() + " action at " +
                        point.blockX() + "," + point.blockY() + "," + point.blockZ())
                .color(NamedTextColor.GREEN));
        return updatedItem;
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

    public record CellAction(
            CellEvent event, String expression,
            boolean consume, int damage
    ) {
    }

    static final CellActionSerializer CELL_ACTION_SERIALIZER = new CellActionSerializer();

    static final class CellActionSerializer implements TagSerializer<CellAction> {
        private static final Tag<String> EVENT_TAG = Tag.String("event");
        private static final Tag<String> EXPRESSION_TAG = Tag.String("expression");
        private static final Tag<Boolean> CONSUME_TAG = Tag.Boolean("consume").defaultValue(false);
        private static final Tag<Integer> DAMAGE_TAG = Tag.Integer("damage").defaultValue(0);

        @Override
        public @Nullable CellAction read(@NotNull TagReadable reader) {
            final String eventStr = reader.getTag(EVENT_TAG);
            final String expression = reader.getTag(EXPRESSION_TAG);
            final boolean consume = reader.getTag(CONSUME_TAG);
            final int damage = reader.getTag(DAMAGE_TAG);
            if (eventStr == null || expression == null) return null;
            final CellEvent event = CellEvent.valueOf(eventStr);
            return new CellAction(event, expression, consume, damage);
        }

        @Override
        public void write(@NotNull TagWritable writer, @NotNull CellAction value) {
            writer.setTag(EVENT_TAG, value.event().name());
            writer.setTag(EXPRESSION_TAG, value.expression());
            if (value.consume()) writer.setTag(CONSUME_TAG, true);
            if (value.damage() > 0) writer.setTag(DAMAGE_TAG, value.damage());
        }
    }

    public static Tag<Long> CellTag(Rule.State state) {
        return Tag.Long(state.name()).path("cell_states");
    }

    public static Tag<CellAction> CellActionTag(Rule.State state) {
        return Tag.Structure(state.name(), CELL_ACTION_SERIALIZER).path("cell_actions");
    }
}
