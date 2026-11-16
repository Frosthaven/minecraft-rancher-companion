package dev.frosthaven.ranchercompanion;

import com.mojang.brigadier.CommandDispatcher;
import dev.frosthaven.ranchercompanion.showcase.Showcase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /drekki showcase}: spawns the lineage and the animation groups in front of the player. */
final class DrekkiCommands {
    private DrekkiCommands() {}

    static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("drekki").requires(s -> s.hasPermission(2))
            .then(Commands.literal("showcase").executes(ctx -> {
                CommandSourceStack s = ctx.getSource();
                int n = Showcase.spawn(s.getLevel(), s.getPosition());
                s.sendSuccess(() -> Component.literal("Spawned " + n + " drekki"), true);
                return n;
            })));
    }
}
