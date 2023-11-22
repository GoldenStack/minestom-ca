package dev.goldenstack.minestom_ca.server;

import dev.goldenstack.minestom_ca.server.commands.StartCommand;
import dev.goldenstack.minestom_ca.server.commands.StopCommand;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.utils.time.Tick;

import java.util.concurrent.atomic.AtomicBoolean;

import static dev.goldenstack.minestom_ca.server.ExampleRules.GAME_OF_LIFE;

public class Main {

    public static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        // Register commands
        MinecraftServer.getCommandManager().register(new StartCommand());
        MinecraftServer.getCommandManager().register(new StopCommand());

        // Create an instance
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.STONE));

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(PlayerLoginEvent.class, event -> {
            final Player player = event.getPlayer();
            player.setPermissionLevel(2);
            player.setGameMode(GameMode.CREATIVE);

            player.getInventory().addItemStack(ItemStack.of(Material.WHITE_WOOL));

            event.setSpawningInstance(instance);
            player.setRespawnPoint(new Pos(0, 42, 0));
        });

        AutomataTicker ticker = new AutomataTicker(instance, GAME_OF_LIFE);

        globalEventHandler.addListener(PlayerBlockPlaceEvent.class, event -> ticker.handleBlockChange(event.getBlockPosition()));

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            if (!RUNNING.get()) return;

            var start = System.nanoTime();

            ticker.tick();

            var duration = System.nanoTime() - start;
            System.out.printf("Took %.3fms\n", duration / 1.0e6);
        }).repeat(1, Tick.SERVER_TICKS).executionType(ExecutionType.SYNC).schedule();

        minecraftServer.start("0.0.0.0", 25565);
    }
}
