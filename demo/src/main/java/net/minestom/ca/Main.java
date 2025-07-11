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

import java.util.List;

import static net.minestom.ca.Automata.Rule;
import static net.minestom.ca.Automata.World;

public final class Main {
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

        BossBar bossBar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        spawnInstance.eventNode()
                .addListener(AutomataEvent.Attach.class, event -> event.world().instance().showBossBar(bossBar))
                .addListener(AddEntityToInstanceEvent.class, event -> {
                    if (event.getEntity() instanceof Player player) player.showBossBar(bossBar);
                })
                .addListener(RemoveEntityFromInstanceEvent.class, event -> {
                    if (event.getEntity() instanceof Player player) player.hideBossBar(bossBar);
                })
                .addListener(AutomataEvent.Tick.class, event -> {
                    final Automata.Metrics metrics = event.metrics();
                    final double ms = event.ms();
                    // Calculate modification ratio (percentage of processed blocks that were modified)
                    String ratio = "0%";
                    if (metrics.processedBlocks() > 0) {
                        final int modRatio = (metrics.modifiedBlocks() * 100) / metrics.processedBlocks();
                        ratio = modRatio + "%";
                    }

                    bossBar.name(Component.text()
                                    .append(Component.text("§b■ §fMSPT: §a" + String.format("%.2f", ms) + "ms"))
                                    .append(Component.text(" §b■ §fSections: §a" + String.format("%,d", metrics.processedSections())))
                                    .append(Component.text(" §b■ §fBlocks: §a" + String.format("%,d", metrics.processedBlocks())))
                                    .append(Component.text(" §b■ §fUpdated: §a" + String.format("%,d", metrics.modifiedBlocks()) + " §f(§a" + ratio + "§f)"))
                                    .build()
                            )
                            .progress(Math.min(1.0f, (float) (ms / 50.0)))
                            .color(ms >= 50 ? BossBar.Color.RED : (ms >= 25 ? BossBar.Color.YELLOW : BossBar.Color.GREEN));
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
