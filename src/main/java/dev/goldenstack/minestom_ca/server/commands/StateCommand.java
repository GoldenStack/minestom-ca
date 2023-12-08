package dev.goldenstack.minestom_ca.server.commands;

import dev.goldenstack.minestom_ca.AutomataWorld;
import dev.goldenstack.minestom_ca.Program;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.CommandContext;
import net.minestom.server.command.builder.condition.Conditions;
import net.minestom.server.command.builder.suggestion.SuggestionCallback;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;

import java.util.Map;

import static net.minestom.server.command.builder.arguments.ArgumentType.*;

public final class StateCommand extends Command {
    public StateCommand() {
        super("state");
        setCondition(Conditions::playerOnly);
        var loop = Loop("states", Group("state",
                Word("state_name").setSuggestionCallback(itemStates()),
                Integer("state_value")));

        addSyntax((sender, context) -> {
            Player player = ((Player) sender);
            PlayerInventory inventory = player.getInventory();
            ItemStack itemStack = inventory.getItemInMainHand();
            for (CommandContext states : context.get(loop)) {
                final String stateName = states.get("state_name");
                final int stateValue = states.get("state_value");
                itemStack = itemStack.withTag(Tag.Integer(stateName), stateValue);
            }
            inventory.setItemInMainHand(itemStack);
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
