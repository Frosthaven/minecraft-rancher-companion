package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Roster.Entry;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * An owner riding a Create contraption (a train carriage, a contraption cart; anything that is an
 * {@code AbstractContraptionEntity} up their vehicle chain): the following drekki waits as data until the ride is over
 * (Shane, 2026-11-25: a following drekki kept getting hit by the train). It is put away with a poof and a message, is
 * not brought back meanwhile (keepUp, bringBack, a level change through a portal, an arrival), and comes back next to its
 * owner by bringBack within a second of the ride's end. Not put away: told to stay, seated or riding itself (a train
 * seat by Go here), sent somewhere by Go here, sent at a target, held. Without Create nothing changes.
 */
public final class Aboard {
    private static final Class<?> CONTRAPTION = find("com.simibubi.create.content.contraptions.AbstractContraptionEntity");
    /** owners whose drekki was put away for their ride */
    private static final Set<UUID> AWAY = new HashSet<>();

    private Aboard() {}

    private static Class<?> find(String name) {
        try { return Class.forName(name); } catch (ClassNotFoundException | LinkageError e) { return null; }
    }

    /** the player rides a Create contraption */
    static boolean on(ServerPlayer p) {
        if (CONTRAPTION == null) return false;
        for (Entity v = p.getVehicle(); v != null; v = v.getVehicle()) if (CONTRAPTION.isInstance(v)) return true;
        return false;
    }

    /** from keepUp, each second and on a mount: true while the owner rides (keepUp stops there). Their following drekki
     *  is put away; after the ride, the owner is forgotten here and keepUp brings the missing drekki back */
    static boolean hold(ServerPlayer p, Roster r, Entry out) {
        if (!on(p)) { AWAY.remove(p.getUUID()); return false; }
        DrekkiEntity d = Ranch.liveOf(out.id);
        if (d == null || d.isPassenger() || d.goHere != null || d.ordered != null || d.held()) return true;
        r.snapshot(out, Ranch.save(d));
        Ranch.poofAt(d);
        d.discard();
        if (AWAY.add(p.getUUID())) Ranch.say(p, "aboard", Ranch.label(out.save));
        return true;
    }

    /** a player gets on something: a contraption puts their drekki away now, not up to a second later */
    public static void onMount(net.neoforged.neoforge.event.entity.EntityMountEvent ev) {
        if (ev.isMounting() && ev.getEntityMounting() instanceof ServerPlayer p && CONTRAPTION != null && CONTRAPTION.isInstance(ev.getEntityBeingMounted()))
            Ranch.later(p.server, () -> Ranch.keepUp(p));
    }

    static void forget(UUID owner) { AWAY.remove(owner); }
}
