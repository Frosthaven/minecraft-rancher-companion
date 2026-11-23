package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Who learns and who benefits (spec, Traits). Only the owner's checked-out drekki learns, within 10 blocks of them, from
 * their own activities, at every stage up to its stage cap (spec, Traits: caps). Every player within
 * 10 blocks of a drekki gets the player half of its traits, the strongest copy once; a ghost's traits do not work.
 */
public final class Traits {
    private Traits() {}

    public static final double RANGE = 10, TELL_RANGE = 30;

    /** the drekki watching this player and able to learn now, or null: the roster's checked-out one, by id */
    public static DrekkiEntity learner(Player p) {
        if (!(p instanceof ServerPlayer sp) || sp.server == null) return null;
        Roster.Entry out = Ranch.roster(sp.server).out(p.getUUID());
        if (out == null || out.where != Roster.Where.OUT) return null;
        DrekkiEntity d = Ranch.liveOf(out.id);
        return d != null && d.level() == p.level() && d.distanceToSqr(p) <= RANGE * RANGE && !d.life.ghost && d.isAlive() ? d : null;
    }

    /* The live drekki whose traits work, by trait: game hooks that run often (piglin AI asks about gold many times a
       tick, every fishing hook ticks) look only at the few holders of their trait, not at every drekki. Rebuilt lazily
       after a change: a drekki joins or leaves the world, or its traits or ghost state change (syncLife). The way area
       effects in established mods keep a registry of active sources instead of scanning for them. */
    private static int version, built = -1;
    private static final java.util.Map<String, java.util.List<DrekkiEntity>> HOLDERS = new java.util.HashMap<>();

    public static void changed() { version++; }

    public static java.util.List<DrekkiEntity> holders(Trait t) {
        if (built != version) {
            HOLDERS.clear();
            for (DrekkiEntity d : Ranch.lives())
                if (!d.life.ghost) for (String id : d.life.traits) HOLDERS.computeIfAbsent(id, k -> new java.util.ArrayList<>()).add(d);
            built = version;
        }
        return HOLDERS.getOrDefault(t.id, java.util.List.of());
    }

    /** the player did the activity: counted ones count (twice when Thriving), one-off ones roll the mood's chance, death
     *  ones always; a pass makes it an offer. At its cap a drekki learns nothing and counts nothing. */
    public static void activity(Player p, Trait t) {
        DrekkiEntity d = learner(p);
        if (d == null || d.life.traits.contains(t.id) || t.id.equals(d.life.offer) || full(d)) return;
        if (!t.learnedBy(d.astral())) return;  /* not its affinity's to learn: no count, no roll, no message */
        Life.Band band = d.band();
        switch (t.learn) {
            case COUNTED -> {
                int now = d.life.progress.merge(t.id, Life.countedStep(band), Integer::sum);
                d.syncLife();
                if (now >= t.count) offer(d, t);
            }
            case ONE_OFF -> { if (d.getRandom().nextDouble() < Life.oneOffChance(band)) offer(d, t); }
            case DEATH -> offer(d, t);
        }
    }

    private static boolean full(DrekkiEntity d) { return d.life.traits.size() >= Trait.cap(d.stage(), d.life.horizons); }

    /** it wants to learn the trait: the owner answers on its screen (spec, Traits: offers). A new offer replaces the
     *  old one, whose count stays full, so it comes back on its next count. The owner only is told. */
    public static void offer(DrekkiEntity d, Trait t) {
        if (d.life.traits.contains(t.id) || full(d)) return;
        d.life.offer = t.id;
        d.syncLife();
        Ranch.touch(d);
        if (d.ownerPlayer() instanceof ServerPlayer owner)
            owner.sendSystemMessage(Component.translatable("message.rancher_companion.trait_offer", Vitals.who(d), offerLink(t)));
    }

    /** the owner's yes (learn) or no (pass: a counted trait's count goes back to 0) to its offer */
    public static boolean answer(DrekkiEntity d, boolean learn) {
        Trait t = Trait.byId(d.life.offer);
        if (t == null || d.life.ghost) return false;
        d.life.offer = null;
        if (learn && !full(d)) teach(d, t);
        else { d.life.progress.remove(t.id); d.syncLife(); Ranch.touch(d); }
        return true;
    }

    /** the chat link: the owner's checked-out drekki, out and near them, opens on its Traits tab with the offer */
    public static int showOffer(ServerPlayer p) {
        DrekkiEntity d = learner(p);
        if (d == null) { p.displayClientMessage(Component.translatable("message.rancher_companion.offer_away"), true); return 0; }
        if (d.life.offer == null) { p.displayClientMessage(Component.translatable("message.rancher_companion.offer_gone"), true); return 0; }
        dev.frosthaven.ranchercompanion.entity.DrekkiInteract.openScreen(p, d, true);
        return 1;
    }

    /** learns it and tells its owner and the players nearby */
    public static void teach(DrekkiEntity d, Trait t) {
        Life l = d.life;
        if (l.traits.contains(t.id)) return;
        if (t.id.equals(l.offer)) l.offer = null;
        l.traits.add(t.id);
        l.progress.remove(t.id);
        d.applyStageHealth(false);
        d.syncLife();
        Ranch.touch(d);
        tell(d, Component.translatable("message.rancher_companion.trait_learned", Vitals.who(d), link(t)));
        Vitals.burst(d, 0x9FE3A6);
    }

    /** [Trait], its name and effect on hover, as an advancement message shows one */
    public static Component link(Trait t) {
        Component name = Component.translatable("trait.rancher_companion." + t.id);
        return Component.literal("[").append(name).append("]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Tips.hover(Tips.trait(t.id, 0, false)))));
    }

    /** [Trait] for the offer: the same hover, and a click opens the drekki screen */
    private static Component offerLink(Trait t) {
        return link(t).copy().withStyle(s -> s.withColor(ChatFormatting.GOLD)
            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/drekki offer")));
    }

    /** its owner and every other player within 30 blocks; others read "<owner>'s drekki" for an unnamed one */
    /** to its owner and every player within 30 blocks of it; an unnamed drekki is "<owner>'s drekki" to the others */
    public static void tell(DrekkiEntity d, Component msgForOwner) {
        for (Player p : audience(d)) {
            boolean owner = p.getUUID().equals(d.owner());
            if (owner || d.hasCustomName()) p.sendSystemMessage(msgForOwner);
            else p.sendSystemMessage(rename(msgForOwner, Component.translatable("drekki.rancher_companion.of", Ranch.ownerName(d))));
        }
    }

    /** who hears its news: its owner (in its level) and every other player within 30 blocks */
    public static java.util.List<Player> audience(DrekkiEntity d) {
        java.util.List<Player> l = new java.util.ArrayList<>();
        for (Player p : d.level().players())
            if (p.getUUID().equals(d.owner()) || p.distanceToSqr(d) <= TELL_RANGE * TELL_RANGE) l.add(p);
        return l;
    }

    private static Component rename(Component msg, Component who) {
        if (!(msg.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)) return msg;
        Object[] args = tc.getArgs().clone();
        if (args.length > 0) args[0] = who;
        return Component.translatable(tc.getKey(), args);
    }

    /** a drekki with this trait within 10 blocks of the entity, its traits working (not a ghost), or null */
    public static DrekkiEntity near(Entity e, Trait t) { return near(e, t, RANGE); }

    public static DrekkiEntity near(Entity e, Trait t, double range) {
        for (DrekkiEntity d : holders(t))
            if (d.level() == e.level() && d.isAlive() && helps(d, e) && d.distanceToSqr(e) <= range * range) return d;
        return null;
    }

    /** a drekki's traits help its owner only; traits on other entities (villagers near it) work for them all */
    private static boolean helps(DrekkiEntity d, Entity e) { return !(e instanceof Player p) || p.getUUID().equals(d.owner()); }

    /** a drekki with the trait near the entity whose cooldown is over; null when none */
    public static DrekkiEntity ready(Entity e, Trait t) { return ready(e, t, false); }

    /** flat: 10 blocks across, at any depth (Undertow: a drekki floats at the surface while its owner dives) */
    public static DrekkiEntity ready(Entity e, Trait t, boolean flat) {
        for (DrekkiEntity d : holders(t)) {
            double dx = d.getX() - e.getX(), dz = d.getZ() - e.getZ();
            boolean near = flat ? dx * dx + dz * dz <= RANGE * RANGE : d.distanceToSqr(e) <= RANGE * RANGE;
            if (d.level() == e.level() && d.isAlive() && near && helps(d, e) && d.life.cooldowns.getOrDefault(t.id, Long.MIN_VALUE) <= Vitals.now(d)) return d;
        }
        return null;
    }

    /** starts the trait's cooldown: half in its astral home terrain (spec, Astral); the screen shows it */
    public static void cool(DrekkiEntity d, Trait t) {
        d.life.cooldowns.put(t.id, Vitals.now(d) + (Vitals.atHome(d) ? t.cooldown / 2 : t.cooldown));
        d.syncLife();
    }
}
