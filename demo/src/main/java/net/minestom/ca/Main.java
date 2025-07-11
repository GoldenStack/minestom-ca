package net.minestom.ca;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.minestom.ca.Automata.RuleConfig;
import net.minestom.ca.backends.lazy.LazyWorld;
import net.minestom.ca.rules.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.ping.Status;
import org.everbuild.blocksandstuff.blocks.BlockBehaviorRuleRegistrations;
import org.everbuild.blocksandstuff.blocks.BlockPlacementRuleRegistrations;

import java.util.ArrayList;
import java.util.List;

import static net.kyori.adventure.text.format.TextColor.color;
import static net.minestom.ca.Automata.Rule;
import static net.minestom.ca.Automata.World;

public final class Main {

    // Performance tracking class for MSPT statistics
    private static final class PerformanceTracker {
        private final List<Double> msptHistory = new ArrayList<>();
        private final int maxHistorySize;

        public PerformanceTracker(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
        }

        public void addMspt(double mspt) {
            msptHistory.add(mspt);
            if (msptHistory.size() > maxHistorySize) {
                msptHistory.removeFirst();
            }
        }

        public double getAverage() {
            if (msptHistory.isEmpty()) return 0.0;
            return msptHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        public double getPercentile(int percentile) {
            if (msptHistory.isEmpty()) return 0.0;
            double[] sorted = msptHistory.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            int index = (int) Math.ceil((percentile / 100.0) * sorted.length) - 1;
            index = Math.max(0, Math.min(index, sorted.length - 1));
            return sorted[index];
        }

        public int getSampleCount() {
            return msptHistory.size();
        }
    }

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        //final TerraMinestomPlatform platform = new TerraMinestomPlatform();

        BlockPlacementRuleRegistrations.registerDefault();
        BlockBehaviorRuleRegistrations.registerDefault();

        // Register commands
        MinecraftServer.getCommandManager().register(
                new CACommands.Start(),
                new CACommands.Stop(),
                new CACommands.State(),
                new CACommands.Action(),
                new CACommands.ListStates(),
                new CACommands.SetBlock(),
                new CACommands.SetLightSpeed(),
                new CACommands.SetTickInterval(),
                new CACommands.ListRules()
        );

        // Create an instance
        InstanceContainer spawnInstance = MinecraftServer.getInstanceManager().createInstanceContainer();
        spawnInstance.setChunkSupplier(LightingChunk::new);
        spawnInstance.setGenerator(unit -> unit.modifier().fillHeight(0, 85, Block.STONE));
        //TerraMinestomWorld terraWorld = platform.worldBuilder(instance).defaultPack().attach();

        final List<RuleConfig> rules = List.of(
                new RuleConfig(new TreeGrowthRule()),
                new RuleConfig(new LiquidRule()),
                new RuleConfig(new RedstoneWireRule()),
                new RuleConfig(new RedstonePowerRule()),
                new RuleConfig(new PistonRule()),
                new RuleConfig(new BlockPusher()),
                new RuleConfig(new GrassSpreadingRule())
        );
        World world = new LazyWorld(spawnInstance, rules);
        AutomataImpl.register(world);
        System.out.println("Variables: " + world.states().stream().map(Rule.State::name).toList());

        // Performance tracker for this instance (keep last 200 ticks)
        PerformanceTracker performanceTracker = new PerformanceTracker(200);
        // First boss bar - Real-time metrics (existing)
        BossBar realtimeBossBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        // Second boss bar - Performance statistics
        BossBar statsBossBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10);

        spawnInstance.eventNode()
                .addListener(AutomataEvent.Attach.class, event -> {
                    event.world().instance().showBossBar(realtimeBossBar);
                    event.world().instance().showBossBar(statsBossBar);
                })
                .addListener(AddEntityToInstanceEvent.class, event -> {
                    // Only show boss bars to players in this specific instance
                    if (event.getEntity() instanceof Player player) {
                        player.showBossBar(realtimeBossBar);
                        player.showBossBar(statsBossBar);
                    }
                })
                .addListener(RemoveEntityFromInstanceEvent.class, event -> {
                    // Only hide boss bars from players leaving this specific instance
                    if (event.getEntity() instanceof Player player) {
                        player.hideBossBar(realtimeBossBar);
                        player.hideBossBar(statsBossBar);
                    }
                })
                .addListener(AutomataEvent.Tick.class, event -> {
                    final Automata.Metrics metrics = event.metrics();
                    final double ms = event.ms();

                    // Add to performance tracker
                    performanceTracker.addMspt(ms);

                    // Calculate modification ratio (percentage of processed blocks that were modified)
                    final int modRatio = metrics.processedBlocks() > 0 ? (metrics.modifiedBlocks() * 100) / metrics.processedBlocks() : 100;
                    final float ratioValue = modRatio / 100.0f;
                    final int red = (int) (255 * (1 - ratioValue));
                    final int green = (int) (255 * ratioValue);
                    final int color = (red << 16) | (green << 8);
                    final Component ratioComponent = Component.text(modRatio + "%").color(color(color));

                    realtimeBossBar.name(Component.text()
                                    .append(Component.text("§6⚡ §a" + String.format("%.2f", ms) + "ms"))
                                    .append(Component.text(" §6◆ §f" + String.format("%,d", metrics.processedSections()) + " §7section"))
                                    .append(Component.text(" §6◆ §f" + String.format("%,d", metrics.processedBlocks()) + " §7block"))
                                    .append(Component.text(" §6◆ §a" + String.format("%,d", metrics.modifiedBlocks()) + " §f(§a"))
                                    .append(ratioComponent)
                                    .append(Component.text("§f)"))
                                    .build()
                            )
                            .progress(Math.min(1.0f, (float) (ms / 50.0)))
                            .color(ms >= 50 ? BossBar.Color.RED : (ms >= 25 ? BossBar.Color.YELLOW : BossBar.Color.GREEN));

                    // Update statistics boss bar
                    final double avgMspt = performanceTracker.getAverage();
                    final double p90 = performanceTracker.getPercentile(90);
                    final double p95 = performanceTracker.getPercentile(95);
                    final double p99 = performanceTracker.getPercentile(99);
                    final int samples = performanceTracker.getSampleCount();

                    statsBossBar.name(Component.text()
                                    .append(Component.text("§3📊 §fAvg: §b" + String.format("%.2f", avgMspt) + "ms"))
                                    .append(Component.text(" §3◈ §f90th: §b" + String.format("%.1f", p90)))
                                    .append(Component.text(" §3◈ §f95th: §b" + String.format("%.1f", p95)))
                                    .append(Component.text(" §3◈ §f99th: §b" + String.format("%.1f", p99)))
                                    .append(Component.text(" §7(§b" + samples + "§7)"))
                                    .build()
                            )
                            .progress(Math.min(1.0f, (float) (avgMspt / 50.0)))
                            .color(avgMspt >= 50 ? BossBar.Color.RED : (avgMspt >= 25 ? BossBar.Color.YELLOW : BossBar.Color.BLUE));
                });

        MinecraftServer.getGlobalEventHandler()
                .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                    Player player = event.getPlayer();
                    PlayerInventory inventory = player.getInventory();
                    player.setPermissionLevel(2);
                    player.setGameMode(GameMode.CREATIVE);

                    inventory.addItemStack(ItemStack.of(Material.WHITE_WOOL));
                    inventory.addItemStack(ItemStack.of(Material.DIRT));
                    inventory.addItemStack(ItemStack.of(Material.REDSTONE_BLOCK));
                    inventory.addItemStack(ItemStack.of(Material.REDSTONE));
                    inventory.addItemStack(ItemStack.of(Material.OAK_LOG));
                    inventory.addItemStack(ItemStack.of(Material.OAK_SAPLING));
                    inventory.addItemStack(ItemStack.of(Material.PISTON));
                    inventory.addItemStack(ItemStack.builder(Material.STICK)
                            .set(AutomataImpl.AUTOMATA_DEBUG, true)
                            .customName(Component.text("Automata Debug"))
                            .build());
                    inventory.addItemStack(ItemStack.builder(Material.COMPASS)
                            .set(AutomataImpl.AUTOMATA_STOPWATCH, true)
                            .customName(Component.text("Stopwatch"))
                            .build());

                    event.setSpawningInstance(spawnInstance);
                    player.setRespawnPoint(new Pos(0, 100, 0));
                })
                .addListener(ServerListPingEvent.class, event -> event.setStatus(Status.builder()
                        .description(Component.text("Minestom CA Demo"))
                        .build()));

        minecraftServer.start("0.0.0.0", 25565);
    }
}
