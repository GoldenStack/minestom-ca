package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Program;
import dev.goldenstack.minestom_ca.backends.lazy.LazyWorld;
import dev.goldenstack.minestom_ca.server.commands.StartCommand;
import dev.goldenstack.minestom_ca.server.commands.StateCommand;
import dev.goldenstack.minestom_ca.server.commands.StopCommand;
import net.kyori.adventure.nbt.NumberBinaryTag;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.adventure.audience.Audiences;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.instance.InstanceChunkLoadEvent;
import net.minestom.server.event.instance.InstanceChunkUnloadEvent;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Main {
    public static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    private static final Program FILE_PROGRAM = Program.fromFile(Path.of("rules/piston"));

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        // Register commands
        MinecraftServer.getCommandManager().register(new StartCommand());
        MinecraftServer.getCommandManager().register(new StopCommand());
        MinecraftServer.getCommandManager().register(new StateCommand());

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

        AutomataWorld.register(new LazyWorld(instance, FILE_PROGRAM));
        //AutomataWorld.register(new CLCellularInstance(instance, MOVING_OAK));

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

            event.setSpawningInstance(instance);
            player.setRespawnPoint(new Pos(0, 13, 0));
        });

        globalEventHandler
                .addListener(PlayerBlockPlaceEvent.class, event -> {
                    final Point point = event.getBlockPosition();
                    final Block block = event.getBlock();
                    AutomataWorld world = AutomataWorld.get(event.getPlayer().getInstance());

                    Map<Integer, Integer> properties = new HashMap<>();
                    properties.put(0, block.stateId());
                    final ItemStack item = event.getPlayer().getItemInHand(event.getHand());
                    for (var entry : item.get(ItemComponent.CUSTOM_DATA).nbt()) {
                        if (entry.getValue() instanceof NumberBinaryTag number) {
                            final String name = entry.getKey();
                            final Integer index = world.program().variables().get(name);
                            if (index != null) properties.put(index, number.intValue());
                        }
                    }
                    world.handlePlacement(point.blockX(), point.blockY(), point.blockZ(), properties);
                })
                .addListener(PlayerBlockBreakEvent.class, event -> {
                    final Point point = event.getBlockPosition();
                    AutomataWorld world = AutomataWorld.get(event.getPlayer().getInstance());
                    world.handlePlacement(point, Block.AIR);
                })
                .addListener(PlayerBlockInteractEvent.class, event -> {
                    final Player player = event.getPlayer();
                    final Point point = event.getBlockPosition();
                    AutomataWorld world = AutomataWorld.get(player.getInstance());
                    final Map<String, Integer> indexes = world.queryNames(point.blockX(), point.blockY(), point.blockZ());
                    player.sendMessage(Component.text("States: " + indexes));
                })
                .addListener(InstanceChunkLoadEvent.class, event -> {
                    AutomataWorld world = AutomataWorld.get(event.getInstance());
                    world.handleChunkLoad(event.getChunkX(), event.getChunkZ());
                })
                .addListener(InstanceChunkUnloadEvent.class, event -> {
                    AutomataWorld world = AutomataWorld.get(event.getInstance());
                    world.handleChunkUnload(event.getChunkX(), event.getChunkZ());
                });

        globalEventHandler.addListener(InstanceTickEvent.class, event -> {
            if (!RUNNING.get()) return;
            final long start = System.nanoTime();

            AutomataWorld world = AutomataWorld.get(event.getInstance());
            world.tick();

            final long duration = System.nanoTime() - start;
            Audiences.all().sendPlayerListHeader(Component.text("Took " + duration / 1.0e6 + "ms"));
        });

        minecraftServer.start("0.0.0.0", 25565);
    }
}
