package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.showcase.Layout;
import dev.frosthaven.ranchercompanion.showcase.Showcase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/** The showcase's camera: a screenshot a moment after each teleport, then the game closes itself. */
public final class ShowcaseClient {
    private ShowcaseClient() {}

    private static int tick = -1;

    public static void tick(ClientTickEvent.Post e) {
        if (!Showcase.AUTO) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { tick = -1; return; }
        if (tick < 0) tick = 0;
        tick++;
        List<Layout.Shot> shots = Layout.shots();
        int rel = tick - Layout.FIRST_TICK - Layout.GRAB_DELAY;
        if (rel >= 0 && rel % Layout.SHOT_TICKS == 0) {
            int i = rel / Layout.SHOT_TICKS;
            if (i < shots.size()) Screenshot.grab(mc.gameDirectory, "showcase_" + shots.get(i).name() + ".png", mc.getMainRenderTarget(), c -> {});
            else if (i == shots.size()) mc.stop();
        }
    }
}
