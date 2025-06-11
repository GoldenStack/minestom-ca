package net.goldenstack.minestom_ca.server;

import net.goldenstack.minestom_ca.AutomataWorld;
import net.goldenstack.minestom_ca.Program;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.command.builder.suggestion.SuggestionCallback;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;

import java.util.Map;

import static net.minestom.server.command.builder.arguments.ArgumentType.*;
import static net.minestom.server.command.builder.arguments.ArgumentType.Integer;

public final class CACommands {

    /**
     * A command that starts running CA rules.
     */
    public static final class Start extends Command {
        public Start() {
            super("start");
            setDefaultExecutor(this::execute);
        }

        private void execute(CommandSender sender, CommandContext context) {
            sender.sendMessage(Component.text("Starting CA ticker"));
            Main.RUNNING.set(true);
        }
    }

    /**
     * A command that stops running CA rules.
     */
    public static final class Stop extends Command {
        public Stop() {
            super("stop");
            setDefaultExecutor(this::execute);
        }

        private void execute(CommandSender sender, CommandContext context) {
            sender.sendMessage(Component.text("Stopping CA ticker"));
            Main.RUNNING.set(false);
        }
    }

    public static final class State extends Command {
        public State() {
            super("state");
            setCondition(Conditions::playerOnly);
            var loop = Loop("states", Group("state",
                    Word("state_name").setSuggestionCallback(itemStates()),
                    Integer("state_value")));

            addSyntax((sender, context) -> {
                Player player = ((Player) sender);
                ItemStack itemStack = player.getItemInMainHand();
                for (CommandContext states : context.get(loop)) {
                    final String stateName = states.get("state_name");
                    final int stateValue = states.get("state_value");
                    itemStack = itemStack.withTag(Tag.Integer(stateName), stateValue);
                }
                player.setItemInMainHand(itemStack);
                player.sendMessage("States applied to item! " + itemStack.toItemNBT());
            }, loop);
        }

        private SuggestionCallback itemStates() {
            // FIXME: seem to be a problem inside minestom
            return (sender, context, suggestion) -> {
                if (!(sender instanceof Player player)) return;
                final AutomataWorld world = AutomataWorld.get(player.getInstance());
                final Program program = world.program();
                final Map<String, Integer> variables = program.variables();
                for (String name : variables.keySet()) {
                    suggestion.addEntry(new SuggestionEntry(name, Component.empty()));
                }
            };
        }
    }
}
