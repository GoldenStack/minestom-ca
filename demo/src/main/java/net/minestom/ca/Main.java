package net.minestom.ca;

import net.kyori.adventure.text.Component;
import net.minestom.ca.Automata.RuleConfig;
import net.minestom.ca.backends.lazy.LazyWorld;
import net.minestom.ca.rules.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.everbuild.blocksandstuff.blocks.BlockPlacementRuleRegistrations;
import org.everbuild.blocksandstuff.blocks.behavior.BlockBehaviorRuleRegistrations;

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
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 85, Block.STONE));
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
        World world = new LazyWorld(instance, rules);
        World.register(world);
        System.out.println("Variables: " + world.states().stream().map(Rule.State::name).toList());

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
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
            inventory.addItemStack(ItemStack.of(Material.STICK)
                    .withTag(AutomataImpl.AUTOMATA_DEBUG, true)
                    .withCustomName(Component.text("Automata Debug")));

            event.setSpawningInstance(instance);
            player.setRespawnPoint(new Pos(0, 100, 0));
        });

        minecraftServer.start("0.0.0.0", 25565);
    }
}
