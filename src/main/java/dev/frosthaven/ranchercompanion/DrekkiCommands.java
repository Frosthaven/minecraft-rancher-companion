package dev.frosthaven.ranchercompanion;

import com.mojang.brigadier.CommandDispatcher;
import dev.frosthaven.ranchercompanion.showcase.Showcase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /drekki offer}: the chat offer's link (any player). {@code /drekki showcase} (operators): spawns the lineage
 *  and the animation groups in front of the player. */
final class DrekkiCommands {
    private DrekkiCommands() {}

    static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("drekki")
            /* the chat offer's link: the checked-out drekki's screen on its Traits tab */
            .then(Commands.literal("offer").executes(ctx -> dev.frosthaven.ranchercompanion.trait.Traits.showOffer(ctx.getSource().getPlayerOrException())))
            .then(Commands.literal("showcase").requires(s -> s.hasPermission(2)).executes(ctx -> {
                CommandSourceStack s = ctx.getSource();
                int n = Showcase.spawn(s.getLevel(), s.getPosition());
                s.sendSuccess(() -> Component.literal("Spawned " + n + " drekki"), true);
                return n;
            })));
    }
}
