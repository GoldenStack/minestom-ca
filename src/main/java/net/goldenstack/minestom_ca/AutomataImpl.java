package net.goldenstack.minestom_ca;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.kyori.adventure.nbt.NumberBinaryTag;
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

import java.util.List;
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

                Int2LongMap properties = new Int2LongOpenHashMap();
                properties.put(0, block.stateId());
                final ItemStack item = event.getPlayer().getItemInHand(event.getHand());
                for (var entry : item.get(DataComponents.CUSTOM_DATA, CustomData.EMPTY).nbt()) {
                    if (entry.getValue() instanceof NumberBinaryTag number) {
                        final String name = entry.getKey();
                        final List<Automata.CellRule.State> states = world.rules().states();
                        for (int i = 0; i < states.size(); i++) {
                            final Automata.CellRule.State state = states.get(i);
                            if (state.name().equals(name)) {
                                properties.put(i + 1, number.intValue());
                                break;
                            }
                        }
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
                if (!player.getItemInMainHand().getTag(AUTOMATA_DEBUG)) return;

                Automata.World world = Automata.World.get(player.getInstance());
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
                    indexes.forEach((name, value) -> {
                        player.sendMessage(Component.text()
                                .append(Component.text(name).color(NamedTextColor.GRAY))
                                .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                                .append(Component.text(value).color(NamedTextColor.WHITE))
                                .build());
                    });
                }
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
                        .append(Component.text("§b■ §fProcessed Sections: §a" + metrics.processedSections()))
                        .append(Component.newline())
                        .append(Component.text("§b■ §fProcessed Blocks: §a" + metrics.processedBlocks()))
                        .append(Component.newline())
                        .append(Component.text("§b■ §fModified Blocks: §a" + metrics.modifiedBlocks() + " §f(§a" + ratio + "§f)"))
                        .build();

                eventInstance.sendPlayerListHeader(header);
            });
}
