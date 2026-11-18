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
    /** HOME: lives at its hatchery; OUT: checked out, with its owner; CARRIED: checked out, in a carrier; NONE: no hatchery, data only */
    public enum Where { HOME, OUT, CARRIED, NONE }

    public static final class Entry {
        public final UUID id, owner;
        public CompoundTag save;
        public Where where;
        public GlobalPos home;
        /** CARRIED: the code the carrier holding it carries (DrekkiData.CARRY_TOKEN); null before 0.3.2's carries */
        public UUID carry;

        Entry(UUID id, UUID owner, CompoundTag save, Where where, GlobalPos home) {
            this.id = id; this.owner = owner; this.save = save; this.where = where; this.home = home;
        }

        public boolean livesAt(GlobalPos pos) { return where == Where.HOME && pos.equals(home); }
    }

    private final Map<UUID, Entry> byId = new LinkedHashMap<>();
    /** each owner's checked-out drekki (OUT or CARRIED; one at most), kept in step by add, remove and move */
    private final Map<UUID, Entry> outByOwner = new java.util.HashMap<>();

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
        Entry e = new Entry(id, owner, save, where, home);
        byId.put(id, e);
        index(e);
        setDirty();
        return e;
    }

    public void remove(UUID id) {
        Entry e = byId.remove(id);
        if (e != null) { outByOwner.remove(e.owner, e); setDirty(); }
    }

    public void move(Entry e, Where where, GlobalPos home) { e.where = where; e.home = home; if (where != Where.CARRIED) e.carry = null; index(e); setDirty(); }

    public void snapshot(Entry e, CompoundTag save) { e.save = save; setDirty(); }

    /** a broken hatchery: its drekki go to No hatchery, kept as data */
    public void orphan(GlobalPos home) {
        for (Entry e : byId.values()) if (e.livesAt(home)) { e.where = Where.NONE; e.home = null; setDirty(); }
    }

    private static Roster load(CompoundTag tag, HolderLookup.Provider reg) {
        Roster r = new Roster();
        for (Tag t : tag.getList("drekki", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            GlobalPos home = c.contains("home") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, c.get("home")).result().orElse(null) : null;
            Where where = Where.valueOf(c.getString("where"));
            Entry e = new Entry(c.getUUID("id"), c.getUUID("owner"), c.getCompound("save"), where, home);
            if (c.hasUUID("carry")) e.carry = c.getUUID("carry");
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
            if (e.home != null) GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, e.home).result().ifPresent(h -> c.put("home", h));
            list.add(c);
        }
        tag.put("drekki", list);
        return tag;
    }

    public static GlobalPos at(net.minecraft.world.level.Level level, BlockPos pos) { return GlobalPos.of(level.dimension(), pos.immutable()); }
}
