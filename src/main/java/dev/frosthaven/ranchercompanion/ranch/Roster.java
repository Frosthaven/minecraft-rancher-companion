package dev.frosthaven.ranchercompanion.ranch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every owner's drekki, the source of truth (spec, Checkout): one record per drekki with its whole entity save, where it
 * is and its hatchery. Kept in the overworld's saved data, so it outlives chunks, logouts and broken hatcheries.
 */
public final class Roster extends SavedData {
    /** HOME: lives at its hatchery; OUT: checked out, with its owner; CARRIED: checked out, in a carrier; NONE: no hatchery,
     *  data only; WORK: employed in a Create role (a conductor, a stock keeper), on its seat and no longer with its owner
     *  (spec, Go here: Employed); TOMB: a ghost summoned at its tombstone (home: the tombstone), within 3 blocks of it */
    public enum Where { HOME, OUT, CARRIED, NONE, WORK, TOMB }

    public static final long DAY = 24000, REST = 3 * DAY;

    /** the clock rest is counted on: the overworld's day time, which a night slept through or /time add moves on */
    public static long now(MinecraftServer server) { return server.overworld().getDayTime(); }

    public void rest(Entry e, long now) { e.restUntil = now + REST; setDirty(); }

    /** a pairing starts: a day of breeding, then the 3-day rest, both on the clock so they hold while the hatchery is unloaded */
    public void startBreeding(Entry e, long now) { e.breedUntil = now + DAY; e.restUntil = now + DAY + REST; setDirty(); }

    /** a breeding ends: laid (the rest stays) or cancelled (no rest) */
    public void endBreeding(Entry e, boolean cancelled) { e.breedUntil = 0; if (cancelled) e.restUntil = 0; setDirty(); }

    public static final class Entry {
        public final UUID id, owner;
        public CompoundTag save;
        public Where where;
        public GlobalPos home;
        /** CARRIED: the code the carrier holding it carries (DrekkiData.CARRY_TOKEN); null before 0.3.2's carries */
        public UUID carry;
        /** the hatchery it last lived at, kept while it is out: its kin are the drekki of the same one (spec, Mood: Kinship) */
        public GlobalPos base;
        /** OUT and told to stay: where it stays; null while it follows (spec, Stay and follow). WORK: where it last was */
        public GlobalPos stay;
        /** WORK: its job (Seats.CONDUCTOR, STOCK_KEEPER, or EMPLOYED when not known); 0 otherwise */
        public int role;
        /** WORK on a train: the train (Create's id), so a recall gives its owner the train's schedule back even when the
         *  drekki is not loaded (Seats.returnTools); null otherwise */
        public UUID workplace;
        /** after a pairing: the overworld day time its 3-day rest ends (spec, Pairing); 0 when not resting */
        public long restUntil;
        /** pairing at its hatchery until this overworld day time (a day; spec, Breeding: Pairing): it stays home and cannot
         *  pair again; 0 when not breeding */
        public long breedUntil;

        public boolean breeding(long now) { return breedUntil != 0 && now < breedUntil; }

        /** whole days of rest left, rounded up (0: not resting). Never more than a full rest: a clock set back adds nothing */
        public int restDays(long now) {
            long left = Math.min(restUntil - now, REST);
            return left <= 0 ? 0 : (int) ((left + DAY - 1) / DAY);
        }

        Entry(UUID id, UUID owner, CompoundTag save, Where where, GlobalPos home) {
            this.id = id; this.owner = owner; this.save = save; this.where = where; this.home = home; this.base = home;
        }

        public boolean livesAt(GlobalPos pos) { return where == Where.HOME && pos.equals(home); }
    }

    private final Map<UUID, Entry> byId = new LinkedHashMap<>();
    /** every coat (fur line, a bit per Lineage index) each owner has ever had, kept after a release (spec, Breeding:
     *  the child's preview shows only coats its owner has had, and the muted ones) */
    private final Map<UUID, Long> coats = new java.util.HashMap<>();

    private void coat(Entry e) {
        int line = e.save.getInt("Line");
        if (line < 0 || line >= 64) return;
        long had = coats.getOrDefault(e.owner, 0L), now = had | 1L << line;
        if (now != had) { coats.put(e.owner, now); setDirty(); }
    }

    /** the coats this owner has had: recorded ones and those of the drekki they own now */
    public long coats(UUID owner) {
        long c = coats.getOrDefault(owner, 0L);
        for (Entry e : byId.values()) if (e.owner.equals(owner)) { int l = e.save.getInt("Line"); if (l >= 0 && l < 64) c |= 1L << l; }
        return c;
    }
    /** each owner's checked-out drekki (OUT or CARRIED; one at most), kept in step by add, remove and move */
    private final Map<UUID, Entry> outByOwner = new java.util.HashMap<>();

    /** how many drekki have each hatchery as their base, kept in step with every change of base (kinship) */
    private final Map<GlobalPos, Integer> bases = new java.util.HashMap<>();

    private void base(Entry e, GlobalPos to) {
        if (java.util.Objects.equals(e.base, to)) return;
        if (e.base != null) bases.merge(e.base, -1, (a, b) -> a + b <= 0 ? null : a + b);
        e.base = to;
        if (to != null) bases.merge(to, 1, Integer::sum);
    }

    private void index(Entry e) {
        if (e.where == Where.OUT || e.where == Where.CARRIED) outByOwner.put(e.owner, e);
        else outByOwner.remove(e.owner, e);
    }

    public static Roster get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Roster::new, Roster::load, null), "rancher_companion_roster");
    }

    public Entry get(UUID id) { return byId.get(id); }

    public List<Entry> of(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byId.values()) if (e.owner.equals(owner)) out.add(e);
        return out;
    }

    public List<Entry> at(GlobalPos home) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : byId.values()) if (e.livesAt(home)) out.add(e);
        return out;
    }

    /** the owner's checked-out drekki (with them or in a carrier), or null */
    public Entry out(UUID owner) { return outByOwner.get(owner); }

    public Entry add(UUID id, UUID owner, CompoundTag save, Where where, GlobalPos home) {
        Entry e = new Entry(id, owner, save, where, null);
        e.home = home;
        base(e, home);
        byId.put(id, e);
        index(e);
        coat(e);
        setDirty();
        return e;
    }

    public void remove(UUID id) {
        Entry e = byId.remove(id);
        if (e != null) { outByOwner.remove(e.owner, e); base(e, null); setDirty(); }
    }

    public void move(Entry e, Where where, GlobalPos home) {
        e.where = where; e.home = home;
        if (home != null) base(e, home);
        if (where != Where.CARRIED) e.carry = null;
        e.stay = null;  /* checked in, carried or checked out again: it follows */
        if (where != Where.WORK) { e.role = 0; e.workplace = null; }
        index(e); setDirty();
    }

    /** the other drekki whose base is this one's hatchery, checked out or not */
    public int kin(Entry e) { return e.base == null ? 0 : Math.max(0, bases.getOrDefault(e.base, 0) - 1); }

    /** sets a drekki's base hatchery (tests, and moves that keep the record) */
    public void setBase(Entry e, GlobalPos to) { base(e, to); setDirty(); }

    public void snapshot(Entry e, CompoundTag save) { e.save = save; coat(e); setDirty(); }

    public void stay(Entry e, GlobalPos at) { e.stay = at; setDirty(); }

    /** a broken hatchery: its drekki go to No hatchery, kept as data */
    public void orphan(GlobalPos home) {
        for (Entry e : byId.values()) { if (e.livesAt(home)) { e.where = Where.NONE; e.home = null; setDirty(); } if (home.equals(e.base)) base(e, null); }
    }

    public static Roster load(CompoundTag tag, HolderLookup.Provider reg) {
        Roster r = new Roster();
        for (Tag t : tag.getList("drekki", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            GlobalPos home = c.contains("home") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("home")).result().orElse(null) : null;
            Where where = Where.valueOf(c.getString("where"));
            Entry e = new Entry(c.getUUID("id"), c.getUUID("owner"), c.getCompound("save"), where, home);
            if (c.hasUUID("carry")) e.carry = c.getUUID("carry");
            /* "phoenix" (a Phoenix egg owed or out) went with Phoenix Heart in 0.7: that drekki is back, as data at No hatchery */
            e.role = where == Where.WORK ? Math.max(1, c.getInt("role")) : 0;  /* employed before 0.4.13 kept no role */
            if (where == Where.WORK && c.hasUUID("workplace")) e.workplace = c.getUUID("workplace");
            e.restUntil = c.getLong("restUntil");
            e.breedUntil = c.getLong("breedUntil");
            if (c.contains("stay")) e.stay = GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("stay")).result().orElse(null);
            GlobalPos base = c.contains("base") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("base")).result().orElse(home) : home;
            e.base = null;
            r.base(e, base);
            r.byId.put(e.id, e);
            r.index(e);
        }
        /* a drekki employed or checked out before kinship (0.7.0) kept no base: its owner's hatchery, the one most of
           their checked-in drekki live at (a train driver had no kin at all, Shane, 2026-11-27) */
        for (Entry e : r.byId.values()) {
            if (e.base != null || e.where == Where.HOME || e.where == Where.TOMB) continue;
            Map<GlobalPos, Integer> homes = new java.util.HashMap<>();
            for (Entry o : r.byId.values()) if (o.owner.equals(e.owner) && o.where == Where.HOME && o.home != null) homes.merge(o.home, 1, Integer::sum);
            homes.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(h -> r.base(e, h.getKey()));
            if (e.base != null) r.setDirty();
        }
        CompoundTag c = tag.getCompound("coats");
        for (String k : c.getAllKeys()) try { r.coats.put(UUID.fromString(k), c.getLong(k)); } catch (IllegalArgumentException ignored) {}
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (Entry e : byId.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.id); c.putUUID("owner", e.owner); c.put("save", e.save); c.putString("where", e.where.name());
            if (e.carry != null) c.putUUID("carry", e.carry);
            if (e.role != 0) c.putInt("role", e.role);
            if (e.workplace != null) c.putUUID("workplace", e.workplace);
            if (e.restUntil != 0) c.putLong("restUntil", e.restUntil);
            if (e.breedUntil != 0) c.putLong("breedUntil", e.breedUntil);
            if (e.home != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.home).result().ifPresent(h -> c.put("home", h));
            if (e.stay != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.stay).result().ifPresent(h -> c.put("stay", h));
            if (e.base != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.base).result().ifPresent(h -> c.put("base", h));
            list.add(c);
        }
        tag.put("drekki", list);
        CompoundTag c = new CompoundTag();
        coats.forEach((owner, bits) -> c.putLong(owner.toString(), bits));
        tag.put("coats", c);
        return tag;
    }

    public static GlobalPos at(net.minecraft.world.level.Level level, BlockPos pos) { return GlobalPos.of(level.dimension(), pos.immutable()); }
}
