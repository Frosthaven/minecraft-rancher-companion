package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.block.HatcheryMenu;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.life.Breeding;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.LifeTag;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.ranch.Roster.Entry;
import dev.frosthaven.ranchercompanion.trait.Trait;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Pairing two drekki at their hatchery (spec, Breeding: Pairing; first pass decided with Shane 2026-11-26): both Mature,
 * both living here, neither resting, breeding nor a ghost; the nest empty and the hatchery under its cap. Breeding takes
 * one Minecraft day (overworld day time, as the rest), which the owner can cancel; meanwhile both are "Breeding", stay
 * home and the nest is kept for their egg. At the end the egg goes into the nest and incubates as usual, and both rest 3
 * days, in which they can neither pair nor be checked out. Hunger does not matter. The child's rules are {@link Breeding};
 * its eyes are lit from the hatch. A tonic in the screen's slot goes with the breeding (the Starlight Tonic: one eye of
 * each parent's astral) and comes back on a cancel.
 */
public final class Pairing {
    private Pairing() {}

    static { Breeding.bakeneko = dev.frosthaven.ranchercompanion.look.Lineage.BAKENEKO; }

    public static boolean ghost(Entry e) { return life(e).ghost; }

    public static int stage(Entry e) {
        DrekkiEntity d = Ranch.liveOf(e.id);
        return d != null ? d.stage() : Math.max(1, e.save.getInt("Stage"));
    }

    private static Life life(Entry e) {
        DrekkiEntity d = Ranch.liveOf(e.id);
        if (d != null) return d.life;
        Life l = new Life();
        if (e.save.contains("Life")) LifeTag.load(l, e.save.getCompound("Life"));
        return l;
    }

    /** every trait it holds: what it can pass on */
    public static List<String> traits(Entry e) { return new ArrayList<>(life(e).traits); }

    /** a drekki that may be picked to pair at this hatchery now */
    public static boolean fit(Entry e, GlobalPos home, long now) {
        return here(e, home) && stage(e) >= 3 && e.restDays(now) == 0 && !e.breeding(now);
    }

    /** ghosts at their tombstones count within this many blocks of a hatchery (Shane, 2026-11-26) */
    public static final int GHOST_REACH = 24;

    /** it lives at this hatchery, or it is a ghost summoned at a tombstone within 24 blocks of it */
    public static boolean here(Entry e, GlobalPos home) {
        if (e.livesAt(home)) return true;
        return e.where == Roster.Where.TOMB && e.home != null && e.home.dimension().equals(home.dimension())
            && e.home.pos().distSqr(home.pos()) <= GHOST_REACH * GHOST_REACH;
    }

    /** a breeding takes one Minecraft day */
    public static final long DAY = Roster.DAY;

    /** what stops any pairing at this hatchery, as a message key, or null: the nest must be empty and the paddock not full */
    public static String blocked(Roster r, GlobalPos home, HatcheryBlockEntity be) {
        if (be.breeding()) return "pair.busy";
        if (be.hasEgg()) return "pair.nest";
        if (r.at(home).size() >= HatcheryBlockEntity.CAP) return "pair.full";
        return null;
    }

    public static void pair(ServerPlayer p, BlockPos pos, HatcheryBlockEntity be, UUID a, UUID b) { pair(p, pos, be, a, b, ""); }

    /** keep: the trait the Heirloom Tonic in the slot keeps (one of either parent's); Pair is refused without one */
    public static void pair(ServerPlayer p, BlockPos pos, HatcheryBlockEntity be, UUID a, UUID b, String keep) {
        Roster r = Ranch.roster(p.server);
        GlobalPos home = Roster.at(p.level(), pos);
        Entry ea = a == null ? null : r.get(a), eb = b == null ? null : r.get(b);
        long now = Roster.now(p.server);
        if (ea == null || eb == null || ea == eb || !ea.owner.equals(p.getUUID()) || !eb.owner.equals(p.getUUID())) return;
        String why = blocked(r, home, be);
        if (why == null && !(fit(ea, home, now) && fit(eb, home, now))) why = "pair.unfit";
        if (why != null) { Ranch.say(p, why); return; }
        HatcheryMenu menu = p.containerMenu instanceof HatcheryMenu m && m.pos.equals(pos) ? m : null;
        boolean heirloom = menu != null && heirloom(menu.tonic());
        if (heirloom && (keep == null || !(traits(ea).contains(keep) || traits(eb).contains(keep)))) { Ranch.say(p, "pair.keep"); return; }
        ItemStack tonic = menu != null ? menu.takeTonic() : ItemStack.EMPTY;
        be.startBreeding(ea.id, eb.id, now, tonic, heirloom ? keep : "");
        r.startBreeding(ea, now);
        r.startBreeding(eb, now);
        p.level().playSound(null, pos, SoundEvents.CHICKEN_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.8f);
        Ranch.say(p, "breed.started", Ranch.label(ea.save), Ranch.label(eb.save));
    }

    /** the owner ends a breeding before its day is out: no egg, no rest */
    public static void cancel(ServerPlayer p, BlockPos pos, HatcheryBlockEntity be) {
        if (!be.breeding()) return;
        ItemStack tonic = stop(Ranch.roster(p.server), be, true);
        if (!tonic.isEmpty()) p.getInventory().placeItemBackInInventory(tonic);  /* a cancel gives the tonic back */
        Ranch.say(p, "breed.cancelled");
    }

    /** ends a breeding; returns the tonic it held */
    private static ItemStack stop(Roster r, HatcheryBlockEntity be, boolean cancelled) {
        for (UUID id : new UUID[]{be.breedA(), be.breedB()}) { Entry e = r.get(id); if (e != null) r.endBreeding(e, cancelled); }
        return be.stopBreeding();
    }

    public static boolean starlight(ItemStack tonic) { return tonic.is(DrekkiItems.TONICS.get(3).get()); }

    public static boolean heirloom(ItemStack tonic) { return tonic.is(DrekkiItems.TONICS.get(1).get()); }

    /** its mood band (Life.Band ordinal), from the live drekki, else from its record as the drekki would work it out */
    public static int band(Entry e) {
        if (life(e).ghost) return -1;  /* a ghost has no mood: none counts toward the pairing's score */
        DrekkiEntity d = Ranch.liveOf(e.id);
        if (d != null) return d.band().ordinal();
        Life l = life(e);
        return Life.band(Life.effective(l.mood, 0), l.petted(System.currentTimeMillis()), false, l.soulflame(Roster.now(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer())), l.food).ordinal();
    }

    private static Breeding.Parent parent(Entry e) {
        return new Breeding.Parent(e.save.getInt("Line"), e.save.getInt("Horn"), traits(e), eyes(e), band(e), life(e).ghost);
    }

    /** a drekki's astral set from its record */
    public static int[] eyes(Entry e) { return Breeding.set(e.save.getInt("Astral"), e.save.getInt("Astral2")); }

    /** every second while a hatchery breeds: a parent gone (released, moved away) ends it; a full day lays the egg */
    public static void tick(ServerLevel level, BlockPos pos, HatcheryBlockEntity be) {
        Roster r = Ranch.roster(level.getServer());
        GlobalPos home = Roster.at(level, pos);
        Entry ea = r.get(be.breedA()), eb = r.get(be.breedB());
        if (ea == null || eb == null || !here(ea, home) || !here(eb, home)) {  /* a parent left (a ghost laid to rest, a mined tombstone): no egg */
            ItemStack tonic = stop(r, be, true);
            if (!tonic.isEmpty()) net.minecraft.world.level.block.Block.popResource(level, pos.above(), tonic);  /* its tonic comes back */
            return;
        }
        long now = Roster.now(level.getServer()), end = be.breedStart() + DAY;
        if (now < end || be.hasEgg()) return;
        lay(level, pos, be, r, ea, eb, now - end);
    }

    /** the day is out (since this many ticks: a hatchery unloaded at the end lays when it loads, the egg already that far
     *  along): the child's rolls go on an egg in the nest; the parents' rest was set on the clock at the start */
    static void lay(ServerLevel level, BlockPos pos, HatcheryBlockEntity be, Roster r, Entry ea, Entry eb, long since) {
        RandomSource rnd = level.getRandom();
        boolean twin = starlight(be.breedTonic());
        String keep = be.breedKeep().isEmpty() ? null : be.breedKeep();
        Breeding.Child c = Breeding.child(parent(ea), parent(eb), twin, keep,
            new Breeding.Rng() {
                public int below(int n) { return rnd.nextInt(n); }
                public double unit() { return rnd.nextDouble(); }
            });
        CompoundTag bred = new CompoundTag();
        bred.putInt("Line", c.line());
        bred.putInt("Horn", c.horn());
        bred.putInt("Astral", c.astral());
        if (c.astral2() != 0) bred.putInt("Astral2", c.astral2());
        ListTag t = new ListTag();
        for (String id : c.traits()) t.add(StringTag.valueOf(id));
        bred.put("Traits", t);
        bred.putString("A", Ranch.label(ea.save).getString());
        bred.putString("B", Ranch.label(eb.save).getString());
        ItemStack egg = new ItemStack(DrekkiItems.INTRIGUING_EGG.get());
        egg.set(DrekkiData.BRED.get(), bred);
        egg.set(DrekkiData.INCUBATION.get(), Math.max(0, since));
        stop(r, be, false);
        be.putEgg(egg);
        level.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.8f, 0.9f);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ea.owner);
        if (owner != null) {
            owner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.rancher_companion.breed.laid", Ranch.label(ea.save), Ranch.label(eb.save)));
            Ranch.refreshFor(owner, pos);
        }
    }

    /** a bred egg's child at its hatch: its line, horns and eyes (lit, one astral or two), and every inherited trait (up to
     *  5), even past its stage's cap (Shane, 2026-11-26): it learns more only while it holds fewer than its cap. An egg laid before the eye rules has no astral: it hatches Dormant, as those did */
    public static void hatchBred(DrekkiEntity d, CompoundTag bred, RandomSource rnd) {
        d.setLook(1, bred.getInt("Line"), 0, bred.contains("Astral") ? bred.getInt("Astral") : Astral.DORMANT, bred.getInt("Horn"));
        d.setAstral2(bred.getInt("Astral2"));
        for (Tag s : bred.getList("Traits", Tag.TAG_STRING)) d.life.traits.add(s.getAsString());
    }
}
