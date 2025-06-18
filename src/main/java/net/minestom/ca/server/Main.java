package net.minestom.ca.server;

import net.kyori.adventure.text.Component;
import net.minestom.ca.Automata;
import net.minestom.ca.AutomataImpl;
import net.minestom.ca.backends.lazy.LazyWorld;
import net.minestom.ca.rules.LiquidRule;
import net.minestom.ca.rules.RedstoneWireRule;
import net.minestom.ca.rules.TreeGrowthRule;
import net.minestom.ca.rules.WireworldRule;
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

public final class Main {
    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        // Register commands
        MinecraftServer.getCommandManager().register(
                new CACommands.Start(),
                new CACommands.Stop(),
                new CACommands.State(),
                new CACommands.Action(),
                new CACommands.ListStates(),
                new CACommands.SetBlock()
        );

        // Create an instance
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 10, Block.STONE));
        instance.enableAutoChunkLoad(false);
        int range = 5;
        for (int x = -range; x < range; x++) {
            for (int z = -range; z < range; z++) {
                instance.loadChunk(x, z).join();
            }
        }
        System.out.println("Chunks loaded: " + instance.getChunks().size());

        final Automata.CellRule rules = Automata.CellRule.rules(
                new TreeGrowthRule(),
                new LiquidRule(),
                new RedstoneWireRule(),
                new WireworldRule()
        );

        // Print variables
        System.out.println("Variables: " + rules.states().stream()
                .map(Automata.CellRule.State::name)
                .toList());

        Automata.World.register(new LazyWorld(instance, rules));

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
            inventory.addItemStack(ItemStack.of(Material.STICK)
                    .withTag(AutomataImpl.AUTOMATA_DEBUG, true)
                    .withCustomName(Component.text("Automata Debug")));

            event.setSpawningInstance(instance);
            player.setRespawnPoint(new Pos(0, 13, 0));
        });

        minecraftServer.start("0.0.0.0", 25565);
    }
}
