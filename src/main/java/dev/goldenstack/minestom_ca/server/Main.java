package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.backends.opencl.CLCellularInstance;
import dev.goldenstack.minestom_ca.server.commands.StartCommand;
import dev.goldenstack.minestom_ca.server.commands.StopCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.concurrent.atomic.AtomicBoolean;

import static dev.goldenstack.minestom_ca.server.ExampleRules.GAME_OF_LIFE;
import static dev.goldenstack.minestom_ca.server.ExampleRules.MOVING_OAK;

public class Main {

    public static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        // Register commands
        MinecraftServer.getCommandManager().register(new StartCommand());
        MinecraftServer.getCommandManager().register(new StopCommand());

        // Create an instance
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 10, Block.STONE));
        instance.enableAutoChunkLoad(false);
        int range = 10;
        for (int x = -range; x < range; x++) {
            for (int z = -range; z < range; z++) {
                instance.loadChunk(x, z).join();
            }
        }
        System.out.println("Chunks loaded: " + instance.getChunks().size());

        AutomataWorld.register(new CLCellularInstance(instance, GAME_OF_LIFE));

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(PlayerLoginEvent.class, event -> {
            Player player = event.getPlayer();
            PlayerInventory inventory = player.getInventory();
            player.setPermissionLevel(2);
            player.setGameMode(GameMode.CREATIVE);

            inventory.addItemStack(ItemStack.of(Material.WHITE_WOOL));
            inventory.addItemStack(ItemStack.of(Material.RED_WOOL));
            inventory.addItemStack(ItemStack.of(Material.HAY_BLOCK));
            inventory.addItemStack(ItemStack.of(Material.OAK_LOG));

            event.setSpawningInstance(instance);
            player.setRespawnPoint(new Pos(0, 13, 0));
        });

        globalEventHandler.addListener(PlayerBlockPlaceEvent.class, event -> {
            final Point point = event.getBlockPosition();
            final Block block = event.getBlock();
            AutomataWorld world = AutomataWorld.get(event.getPlayer().getInstance());
            world.handlePlacement(point, block);
        });

        globalEventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            final Point point = event.getBlockPosition();
            AutomataWorld world = AutomataWorld.get(event.getPlayer().getInstance());
            world.handlePlacement(point, Block.AIR);
        });

        globalEventHandler.addListener(InstanceTickEvent.class, event -> {
            if (!RUNNING.get()) return;
            final long start = System.nanoTime();

            AutomataWorld world = AutomataWorld.get(event.getInstance());
            world.tick();

            final long duration = System.nanoTime() - start;
            System.out.printf("Took %.3fms\n", duration / 1.0e6);
        });

        minecraftServer.start("0.0.0.0", 25565);
    }
}
