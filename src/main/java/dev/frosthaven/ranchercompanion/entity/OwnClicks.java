package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.item.WhistleItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * An owner's click on their own drekki, and their whistle, work on land another player claimed (Shane, 2026-11-23).
 * Claim mods (FTB Chunks and MineColonies in the Haven pack) cancel a visitor's clicks there, on every entity alike.
 * These listeners run last and see cancelled events: they let the owner's click through again. Other players stay
 * stopped, by the claim and by the drekki's own owner checks. An employed drekki is left alone: its clicks are
 * cancelled on purpose (DrekkiInteract, Create).
 */
public final class OwnClicks {
    private OwnClicks() {}

    private static boolean own(Player p, Entity target) {
        return target instanceof DrekkiEntity d && d.job() == 0 && p.getUUID().equals(d.owner());
    }

    public static void onEntity(PlayerInteractEvent.EntityInteract ev) {
        if (ev.isCanceled() && own(ev.getEntity(), ev.getTarget())) ev.setCanceled(false);
    }

    public static void onEntitySpecific(PlayerInteractEvent.EntityInteractSpecific ev) {
        if (ev.isCanceled() && own(ev.getEntity(), ev.getTarget())) ev.setCanceled(false);
    }

    /** the whistle with a block in reach: the click goes on to the whistle, and never to the block */
    public static void onBlock(PlayerInteractEvent.RightClickBlock ev) {
        if (!ev.isCanceled() || !(ev.getItemStack().getItem() instanceof WhistleItem)) return;
        ev.setCanceled(false);
        ev.setUseBlock(TriState.FALSE);
    }

    public static void onItem(PlayerInteractEvent.RightClickItem ev) {
        if (ev.isCanceled() && ev.getItemStack().getItem() instanceof WhistleItem) ev.setCanceled(false);
    }
}
