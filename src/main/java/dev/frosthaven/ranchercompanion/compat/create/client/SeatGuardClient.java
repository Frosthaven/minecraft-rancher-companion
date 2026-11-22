package dev.frosthaven.ranchercompanion.compat.create.client;

import dev.frosthaven.ranchercompanion.compat.create.SeatGuard;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;

/** The use key on a contraption seat a drekki sits on does nothing: Create takes that click on the client
 *  (ContraptionHandlerClient) and would sit the player there, throwing the drekki off. This listener runs first */
public final class SeatGuardClient {
    private SeatGuardClient() {}

    public static void onInteract(InputEvent.InteractionKeyMappingTriggered ev) {
        var p = Minecraft.getInstance().player;
        if (!ev.isUseItem() || p == null || p.isShiftKeyDown()) return;
        if (SeatGuard.drekkiOnLookedSeat(p, p.blockInteractionRange())) {
            ev.setCanceled(true);
            ev.setSwingHand(false);
        }
    }
}
