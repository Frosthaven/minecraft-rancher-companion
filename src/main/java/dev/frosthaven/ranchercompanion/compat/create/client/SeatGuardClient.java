package dev.frosthaven.ranchercompanion.compat.create.client;

import dev.frosthaven.ranchercompanion.compat.create.SeatGuard;
import dev.frosthaven.ranchercompanion.item.WhistleItem;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;

/** Create takes every use click on a contraption on the client (ContraptionHandlerClient, sneaking too) and sits the
 *  player on a seat there. This listener runs first: on a seat a drekki sits on, the click does nothing; a sneak-use of
 *  the Drekki Whistle on a contraption seat goes to the whistle (Go here onto that seat) instead of seating the player */
public final class SeatGuardClient {
    private SeatGuardClient() {}

    public static void onInteract(InputEvent.InteractionKeyMappingTriggered ev) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (!ev.isUseItem() || p == null || mc.gameMode == null) return;
        /* on an entity (the drekki on the seat): Create's own ray stops there and seats nobody; the click goes to that
           entity, so a train schedule reaches a seated drekki (it becomes the conductor) */
        if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult) return;
        if (p.isShiftKeyDown()) {
            if (p.getItemInHand(ev.getHand()).getItem() instanceof WhistleItem && SeatGuard.onLookedSeat(p, p.blockInteractionRange())) {
                ev.setCanceled(true);
                ev.setSwingHand(false);
                mc.gameMode.useItem(p, ev.getHand());
            }
            return;
        }
        if (SeatGuard.drekkiOnLookedSeat(p, p.blockInteractionRange())) {
            ev.setCanceled(true);
            ev.setSwingHand(false);
        }
    }
}
