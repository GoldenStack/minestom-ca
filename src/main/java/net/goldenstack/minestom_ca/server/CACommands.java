package net.goldenstack.minestom_ca.server;

import net.goldenstack.minestom_ca.Automata;
import net.goldenstack.minestom_ca.AutomataImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.command.builder.suggestion.SuggestionCallback;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;

import java.util.Set;

import static net.minestom.server.command.builder.arguments.ArgumentType.*;

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
            AutomataImpl.RUNNING.set(true);
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
            AutomataImpl.RUNNING.set(false);
        }
    }

    public static final class State extends Command {
        public State() {
            super("state");
            setCondition(Conditions::playerOnly);
            var loop = Loop("states", Group("state",
                    Word("state_name").setSuggestionCallback(itemStates()),
                    Long("state_value")));

            addSyntax((sender, context) -> {
                Player player = ((Player) sender);
                ItemStack itemStack = player.getItemInMainHand();
                for (CommandContext states : context.get(loop)) {
                    final String stateName = states.get("state_name");
                    final long stateValue = states.get("state_value");
                    player.sendMessage(Component.text(" * Applying: " + stateName + " = " + stateValue));
                    itemStack = itemStack.withTag(Tag.Long(stateName), stateValue);
                }
                player.setItemInMainHand(itemStack);
                player.sendMessage("States applied to item!");
            }, loop);
        }

        private SuggestionCallback itemStates() {
            // FIXME: seem to be a problem inside minestom
            return (sender, context, suggestion) -> {
                if (!(sender instanceof Player player)) return;
                final Automata.World world = Automata.World.get(player.getInstance());
                final Set<Automata.CellRule.State> states = world.rules().states();
                for (Automata.CellRule.State state : states) {
                    suggestion.addEntry(new SuggestionEntry(state.name(), Component.empty()));
                }
            };
        }
    }

    /**
     * A command that lists all available states in the current automata world.
     */
    public static final class ListStates extends Command {
        public ListStates() {
            super("liststates");
            setCondition(Conditions::playerOnly);
            setDefaultExecutor(this::execute);
        }

        private void execute(CommandSender sender, CommandContext context) {
            Player player = (Player) sender;
            final Automata.World world = Automata.World.get(player.getInstance());
            if (world == null) {
                player.sendMessage(Component.text("No automata world found in this instance.").color(NamedTextColor.RED));
                return;
            }

            final Set<Automata.CellRule.State> states = world.rules().states();
            if (states.isEmpty()) {
                player.sendMessage(Component.text("No states defined in this automata world.").color(NamedTextColor.YELLOW));
                return;
            }
            player.sendMessage(Component.text("Available states:").color(NamedTextColor.GOLD));
            for (Automata.CellRule.State state : states) {
                player.sendMessage(Component.text(" • " + state.name() + " (" + state.bitSize() + " bits)")
                        .color(NamedTextColor.WHITE));
            }
        }
    }
}
