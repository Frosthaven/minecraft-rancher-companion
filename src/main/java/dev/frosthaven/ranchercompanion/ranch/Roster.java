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
     *  (spec, Go here: Employed) */
    public enum Where { HOME, OUT, CARRIED, NONE, WORK }

    public static final class Entry {
        public final UUID id, owner;
        public CompoundTag save;
        public Where where;
        public GlobalPos home;
        /** CARRIED: the code the carrier holding it carries (DrekkiData.CARRY_TOKEN); null before 0.3.2's carries */
        public UUID carry;
        /** the hatchery it last lived at, kept while it is out: its kin are the drekki of the same one (spec, Mood: Kinship) */
        public GlobalPos base;
        /** Phoenix Heart: 0 none, EGG_OWED (its egg waits for the owner), EGG_GIVEN (the egg is out; a hatchery brings it back) */
        public int phoenix;
        public static final int EGG_OWED = 1, EGG_GIVEN = 2;
        /** OUT and told to stay: where it stays; null while it follows (spec, Stay and follow). WORK: where it last was */
        public GlobalPos stay;
        /** WORK: its job (Seats.CONDUCTOR, STOCK_KEEPER, or EMPLOYED when not known); 0 otherwise */
        public int role;

        Entry(UUID id, UUID owner, CompoundTag save, Where where, GlobalPos home) {
            this.id = id; this.owner = owner; this.save = save; this.where = where; this.home = home; this.base = home;
        }

        public boolean livesAt(GlobalPos pos) { return where == Where.HOME && pos.equals(home); }
    }

    private final Map<UUID, Entry> byId = new LinkedHashMap<>();
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
        if (where != Where.WORK) e.role = 0;
        index(e); setDirty();
    }

    /** the other drekki whose base is this one's hatchery, checked out or not */
    public int kin(Entry e) { return e.base == null ? 0 : Math.max(0, bases.getOrDefault(e.base, 0) - 1); }

    /** sets a drekki's base hatchery (tests, and moves that keep the record) */
    public void setBase(Entry e, GlobalPos to) { base(e, to); setDirty(); }

    public void snapshot(Entry e, CompoundTag save) { e.save = save; setDirty(); }

    public void stay(Entry e, GlobalPos at) { e.stay = at; setDirty(); }

    /** a broken hatchery: its drekki go to No hatchery, kept as data */
    public void orphan(GlobalPos home) {
        for (Entry e : byId.values()) { if (e.livesAt(home)) { e.where = Where.NONE; e.home = null; setDirty(); } if (home.equals(e.base)) base(e, null); }
    }

    private static Roster load(CompoundTag tag, HolderLookup.Provider reg) {
        Roster r = new Roster();
        for (Tag t : tag.getList("drekki", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            GlobalPos home = c.contains("home") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("home")).result().orElse(null) : null;
            Where where = Where.valueOf(c.getString("where"));
            Entry e = new Entry(c.getUUID("id"), c.getUUID("owner"), c.getCompound("save"), where, home);
            if (c.hasUUID("carry")) e.carry = c.getUUID("carry");
            e.phoenix = c.getInt("phoenix");
            e.role = where == Where.WORK ? Math.max(1, c.getInt("role")) : 0;  /* employed before 0.4.13 kept no role */
            if (c.contains("stay")) e.stay = GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("stay")).result().orElse(null);
            GlobalPos base = c.contains("base") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("base")).result().orElse(home) : home;
            e.base = null;
            r.base(e, base);
            r.byId.put(e.id, e);
            r.index(e);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (Entry e : byId.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.id); c.putUUID("owner", e.owner); c.put("save", e.save); c.putString("where", e.where.name());
            if (e.carry != null) c.putUUID("carry", e.carry);
            if (e.phoenix != 0) c.putInt("phoenix", e.phoenix);
            if (e.role != 0) c.putInt("role", e.role);
            if (e.home != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.home).result().ifPresent(h -> c.put("home", h));
            if (e.stay != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.stay).result().ifPresent(h -> c.put("stay", h));
            if (e.base != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.base).result().ifPresent(h -> c.put("base", h));
            list.add(c);
        }
        tag.put("drekki", list);
        return tag;
    }

    public static GlobalPos at(net.minecraft.world.level.Level level, BlockPos pos) { return GlobalPos.of(level.dimension(), pos.immutable()); }
}
