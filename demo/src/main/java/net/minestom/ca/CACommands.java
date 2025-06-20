package net.minestom.ca;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.command.builder.suggestion.SuggestionCallback;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;

import java.util.List;
import java.util.Map;

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
                final Automata.World world = Automata.World.get(player.getInstance());
                if (world == null) {
                    player.sendMessage(Component.text("No automata world found in this instance.").color(NamedTextColor.RED));
                    return;
                }
                ItemStack.Builder itemStack = player.getItemInMainHand().builder();
                for (CommandContext states : context.get(loop)) {
                    final String stateName = states.get("state_name");
                    final Automata.Rule.State state = world.state(stateName);
                    if (state == null) continue;
                    final long stateValue = states.get("state_value");
                    player.sendMessage(Component.text(" * Applying: " + stateName + " = " + stateValue));
                    final Tag<Long> tag = AutomataImpl.CellTag(state);
                    itemStack.set(tag, stateValue);
                }
                player.setItemInMainHand(itemStack.build());
                player.sendMessage("States applied to item!");
            }, loop);
        }

        private SuggestionCallback itemStates() {
            // FIXME: seem to be a problem inside minestom
            return (sender, context, suggestion) -> {
                if (!(sender instanceof Player player)) return;
                final Automata.World world = Automata.World.get(player.getInstance());
                for (Automata.Rule.State state : world.states()) {
                    suggestion.addEntry(new SuggestionEntry(state.name(), Component.empty()));
                }
            };
        }
    }

    /**
     * Add cell action states to the item in hand.
     */
    public static final class Action extends Command {
        public Action() {
            super("action");
            setCondition(Conditions::playerOnly);
            var loop = Loop("actions", Group("action",
                    Word("state_name"),
                    Enum("state_event", AutomataImpl.CellEvent.class).setFormat(ArgumentEnum.Format.LOWER_CASED),
                    String("state_expression")));

            addSyntax((sender, context) -> {
                Player player = ((Player) sender);
                final Automata.World world = Automata.World.get(player.getInstance());
                if (world == null) {
                    player.sendMessage(Component.text("No automata world found in this instance.").color(NamedTextColor.RED));
                    return;
                }
                ItemStack.Builder itemStack = player.getItemInMainHand().builder();
                for (CommandContext states : context.get(loop)) {
                    final String stateName = states.get("state_name");
                    final Automata.Rule.State state = world.state(stateName);
                    if (state == null) continue;
                    final AutomataImpl.CellEvent event = states.get("state_event");
                    final String stateExpression = states.get("state_expression");
                    player.sendMessage(Component.text(" * Applying: " + stateName + " = '" + stateExpression + "' on " + event));
                    final Tag<AutomataImpl.CellAction> tag = AutomataImpl.CellActionTag(state);
                    final AutomataImpl.CellAction action = new AutomataImpl.CellAction(event, stateExpression, false, 0);
                    itemStack.set(tag, action);
                }
                player.setItemInMainHand(itemStack.build());
                player.sendMessage("States applied to item!");
            }, loop);
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

            final List<Automata.Rule.State> states = world.states();
            if (states.isEmpty()) {
                player.sendMessage(Component.text("No states defined in this automata world.").color(NamedTextColor.YELLOW));
                return;
            }
            player.sendMessage(Component.text("Available states:").color(NamedTextColor.GOLD));
            for (Automata.Rule.State state : states) {
                player.sendMessage(Component.text(" • " + state.name() + " (" + state.bitSize() + " bits)")
                        .color(NamedTextColor.WHITE));
            }
        }
    }

    public static final class SetBlock extends Command {
        public SetBlock() {
            super("setblock");
            final var position = RelativeBlockPosition("position");
            final var block = BlockState("block");

            addSyntax((sender, context) -> {
                final Player player = (Player) sender;
                final Block blockToPlace = context.get(block);
                final Point point = context.get(position).from(player);
                Instance instance = player.getInstance();
                instance.setBlock(point, blockToPlace);

                final Automata.World world = Automata.World.get(instance);
                if (world != null) {
                    final Map<Automata.Rule.State, Long> properties = Map.of(Automata.Rule.BLOCK_STATE, (long) blockToPlace.stateId());
                    world.handlePlacement(point.blockX(), point.blockY(), point.blockZ(), properties);
                }
            }, position, block);
        }
    }

    public static final class SetLightSpeed extends Command {
        public SetLightSpeed() {
            super("speed");
            setCondition(Conditions::playerOnly);
            final var speed = Integer("speed");

            addSyntax((sender, context) -> {
                Player player = (Player) sender;
                final Automata.World world = Automata.World.get(player.getInstance());
                if (world == null) {
                    player.sendMessage(Component.text("No automata world found in this instance.").color(NamedTextColor.RED));
                    return;
                }

                final int lightSpeed = context.get(speed);
                world.updateLightSpeed(lightSpeed);
                player.sendMessage(Component.text("Light speed updated to: " + lightSpeed).color(NamedTextColor.GREEN));
            }, speed);
        }
    }

    public static final class SetTickInterval extends Command {
        public SetTickInterval() {
            super("tickinterval");
            setCondition(Conditions::playerOnly);
            final var interval = Integer("interval");

            addSyntax((sender, context) -> {
                Player player = (Player) sender;
                final Automata.World world = Automata.World.get(player.getInstance());
                if (world == null) {
                    player.sendMessage(Component.text("No automata world found in this instance.").color(NamedTextColor.RED));
                    return;
                }

                final int tickInterval = context.get(interval);
                world.updateTickInterval(tickInterval);
                player.sendMessage(Component.text("Tick interval updated to: " + tickInterval).color(NamedTextColor.GREEN));
            }, interval);
        }
    }

    public static final class ListRules extends Command {
        public ListRules() {
            super("listrules");
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

            final List<Automata.Rule> rules = world.rules();
            if (rules.isEmpty()) {
                player.sendMessage(Component.text("No rules defined in this automata world.").color(NamedTextColor.YELLOW));
                return;
            }

            player.sendMessage(Component.text("Registered Rules:").color(NamedTextColor.GOLD));

            for (Automata.Rule rule : rules) {
                Automata.Rule.Info info = rule.info();

                // Create hoverable component with description
                Component ruleComponent = Component.text(" • " + info.name())
                        .color(NamedTextColor.WHITE)
                        .hoverEvent(Component.text(info.description()).color(NamedTextColor.GRAY));

                // Add interests information if available
                if (!info.interests().isEmpty()) {
                    Component interestsComponent = Component.text(" [");

                    for (int i = 0; i < info.interests().size(); i++) {
                        Automata.Rule.Interest interest = info.interests().get(i);
                        if (i > 0) interestsComponent = interestsComponent.append(Component.text(", "));

                        Component interestText = Component.text(interest.state().name())
                                .color(NamedTextColor.AQUA);
                        interestsComponent = interestsComponent.append(interestText);
                    }

                    interestsComponent = interestsComponent.append(Component.text("]"))
                            .color(NamedTextColor.GRAY);

                    ruleComponent = ruleComponent.append(interestsComponent);
                }

                player.sendMessage(ruleComponent);
            }
        }
    }
}
