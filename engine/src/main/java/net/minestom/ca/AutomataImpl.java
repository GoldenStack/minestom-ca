package net.minestom.ca;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
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
import net.minestom.server.instance.palette.Palette;
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
    public static final Tag<Boolean> AUTOMATA_DEBUG = Tag.Boolean("ca:debug").defaultValue(false);
    public static final Tag<Boolean> AUTOMATA_STOPWATCH = Tag.Boolean("ca:stopwatch").defaultValue(false);
    public static final EventNode<InstanceEvent> AUTOMATA_EVENT_NODE = EventNode.type("ca:node", EventFilter.INSTANCE)
            .addListener(PlayerBlockPlaceEvent.class, event -> {
                final Point point = event.getBlockPosition();
                final Block block = event.getBlock();
                World world = World.get(event.getPlayer().getInstance());
                final ItemStack item = event.getPlayer().getItemInHand(event.getHand());
                final Map<Rule.State, Long> properties = itemStates(world, item, block);
                world.handlePlacement(point, properties);
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
        EventDispatcher.call(new AutomataEvent.Tick(world, metrics, mspt));
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
        final Map<Rule.State, Long> indexes = world.query().queryNames(point);
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
        for (String key : actions.keySet()) {
            final Rule.State state = world.state(key);
            if (state == null) continue;
            final Tag<CellAction> tag = AutomataImpl.CellActionTag(state);
            final CellAction action = item.getTag(tag);
            if (action == null || !action.event().equals(event)) continue;
            // Run action
            final long value = actionValue(action.expression());
            // Update world
            final int range = action.range();
            if (range > 0) {
                // Loop through the range
                for (int dx = -range; dx <= range; dx++) {
                    for (int dy = -range; dy <= range; dy++) {
                        for (int dz = -range; dz <= range; dz++) {
                            final Point offsetPoint = point.add(dx, dy, dz);
                            world.updateAt(offsetPoint, Map.of(state, value));
                        }
                    }
                }
            } else if (action.section()) {
                Palette palette = world.statePalette(point, state);
                palette.fill((int) value);
                world.invalidateStatePalette(point, state);
            } else {
                // Single point
                world.updateAt(point, Map.of(state, value));
            }
            // Item update
            if (action.consume()) updatedItem = Objects.requireNonNullElse(updatedItem, item).consume(1);
            final int damage = action.damage();
            if (damage > 0) updatedItem = Objects.requireNonNullElse(updatedItem, item).damage(damage);
        }
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
            boolean consume, int damage,
            // Range
            int range, boolean section
    ) {
        public CellAction(CellEvent event, String expression, boolean consume, int damage) {
            this(event, expression, consume, damage, 0, false);
        }
    }

    private static final TagSerializer<CellAction> CELL_ACTION_SERIALIZER = new CellActionSerializer();

    private static final class CellActionSerializer implements TagSerializer<CellAction> {
        private static final Tag<String> EVENT_TAG = Tag.String("event");
        private static final Tag<String> EXPRESSION_TAG = Tag.String("expression");
        private static final Tag<Boolean> CONSUME_TAG = Tag.Boolean("consume").defaultValue(false);
        private static final Tag<Integer> DAMAGE_TAG = Tag.Integer("damage").defaultValue(0);

        private static final Tag<Integer> RANGE_TAG = Tag.Integer("range").defaultValue(0);
        private static final Tag<Boolean> SECTION_TAG = Tag.Boolean("section").defaultValue(false);

        @Override
        public @Nullable CellAction read(@NotNull TagReadable reader) {
            final String eventStr = reader.getTag(EVENT_TAG);
            final String expression = reader.getTag(EXPRESSION_TAG);
            final boolean consume = reader.getTag(CONSUME_TAG);
            final int damage = reader.getTag(DAMAGE_TAG);
            final int range = reader.getTag(RANGE_TAG);
            final boolean section = reader.getTag(SECTION_TAG);
            if (eventStr == null || expression == null) return null;
            final CellEvent event = CellEvent.valueOf(eventStr);
            return new CellAction(event, expression, consume, damage, range, section);
        }

        @Override
        public void write(@NotNull TagWritable writer, @NotNull CellAction value) {
            writer.setTag(EVENT_TAG, value.event().name());
            writer.setTag(EXPRESSION_TAG, value.expression());
            if (value.consume()) writer.setTag(CONSUME_TAG, true);
            if (value.damage() > 0) writer.setTag(DAMAGE_TAG, value.damage());
            if (value.range() > 0) writer.setTag(RANGE_TAG, value.range());
            if (value.section()) writer.setTag(SECTION_TAG, true);
        }
    }

    public static Tag<Long> CellTag(Rule.State state) {
        return Tag.Long(state.name()).path("cell_states");
    }

    public static Tag<CellAction> CellActionTag(Rule.State state) {
        return Tag.Structure(state.name(), CELL_ACTION_SERIALIZER).path("cell_actions");
    }
}
