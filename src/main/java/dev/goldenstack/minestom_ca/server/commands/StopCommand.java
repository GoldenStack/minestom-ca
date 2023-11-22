package dev.goldenstack.minestom_ca.server.commands;

import dev.goldenstack.minestom_ca.server.Main;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;

/**
 * A command that stops running CA rules.
 */
public class StopCommand extends Command {
    public StopCommand() {
        super("stop");
        setDefaultExecutor(this::execute);
    }

    private void execute(CommandSender sender, CommandContext context) {
        sender.sendMessage(Component.text("Stopping CA ticker"));
        Main.RUNNING.set(false);
    }
}