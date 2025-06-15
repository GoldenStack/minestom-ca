package net.goldenstack.minestom_ca.server;

import net.goldenstack.minestom_ca.Automata;
import net.goldenstack.minestom_ca.AutomataImpl;
import net.goldenstack.minestom_ca.backends.lazy.LazyWorld;
import net.goldenstack.minestom_ca.rules.BlockPusher;
import net.kyori.adventure.text.Component;
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
        MinecraftServer.getCommandManager().register(new CACommands.Start());
        MinecraftServer.getCommandManager().register(new CACommands.Stop());
        MinecraftServer.getCommandManager().register(new CACommands.State());

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
                new BlockPusher()
        );
        //final CellRule rules = Program.fromFile(Path.of("rules/piston")).makeCellRule();

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
            inventory.addItemStack(ItemStack.of(Material.RED_WOOL));
            inventory.addItemStack(ItemStack.of(Material.REDSTONE_BLOCK));
            inventory.addItemStack(ItemStack.of(Material.OAK_LOG));

            inventory.addItemStack(ItemStack.of(Material.STICK)
                    .withTag(AutomataImpl.AUTOMATA_DEBUG, true)
                    .withCustomName(Component.text("Automata Debug")));


            event.setSpawningInstance(instance);
            player.setRespawnPoint(new Pos(0, 13, 0));
        });

        minecraftServer.start("0.0.0.0", 25565);
    }
}
