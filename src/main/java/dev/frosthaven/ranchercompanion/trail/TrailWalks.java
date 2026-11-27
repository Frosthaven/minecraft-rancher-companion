package dev.frosthaven.ranchercompanion.trail;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Drekki out on a footprint trail (spec, Trails). A walk is a record: the print it is at, the one before, and its way
 * (1 out along the prints' facing, -1 back). While the drekki is an entity in a ticking chunk, its goal ({@link TrailWalk})
 * walks it and keeps the record; where the next print is in ground that does not tick, the entity is saved to the
 * roster and removed, and the record walks on by itself here, print to print at a drekki's pace, owner online or not
 * (Create's trains do the same). When the print it is at ticks again, the drekki appears there and walks on.
 * <p>
 * A walk ends at the trail's start (no print further back), or when the drekki leaves its hatchery's roster (checked
 * out, released, died); then the hatchery puts it back as any missing drekki. After a restart every walk is a record:
 * a copy that a chunk loads is refused, and the drekki appears at its record's print.
 */
public final class TrailWalks extends SavedData {
    /** a record's pace: blocks a second (a strolling drekki with its pauses) */
    static final double PACE = 2.0;

    public static final class Walk {
        public final UUID id;
        public ResourceKey<Level> dim;
        public long node, prev = Long.MIN_VALUE;
        public int dir = 1;
        public double progress;
        public boolean asleep;
        /** an entity walks it now (never saved: after a restart every walk is a record) */
        public transient boolean live;

        public Walk(UUID id, ResourceKey<Level> dim, long node) { this.id = id; this.dim = dim; this.node = node; }

        public BlockPos at() { return BlockPos.of(node); }
        public void stepTo(long next) { prev = node; node = next; }
        public void turn() { dir = -dir; prev = Long.MIN_VALUE; }
    }

    private final Map<UUID, Walk> walks = new HashMap<>();

    public static TrailWalks get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(TrailWalks::new, TrailWalks::load, null), "rancher_companion_trail_walks");
    }

    /** out on a trail now (server): the pen's goals (stroll, sleep, eat) leave it to its walk */
    public static boolean walking(DrekkiEntity d) {
        return !d.level().isClientSide && d.drekkiId() != null && get(d.level().getServer()).of(d.drekkiId()) != null;
    }

    public Walk of(UUID id) { return id == null ? null : walks.get(id); }
    public java.util.Collection<Walk> all() { return java.util.Collections.unmodifiableCollection(walks.values()); }

    public Walk start(DrekkiEntity d, BlockPos print) {
        Walk w = new Walk(d.drekkiId(), d.level().dimension(), print.asLong());
        w.live = true;
        walks.put(w.id, w); setDirty();
        return w;
    }

    public void end(UUID id) { if (walks.remove(id) != null) setDirty(); }
    public void changed() { setDirty(); }

    /** a walking drekki's entity goes: the record walks on */
    public void toRecord(DrekkiEntity d) {
        Walk w = of(d.drekkiId());
        if (w == null || !w.live) return;
        w.live = false; w.progress = 0; setDirty();
        if (!d.isRemoved()) { Ranch.touch(d); d.discard(); }
        dev.frosthaven.ranchercompanion.Debug.log("server: {} walks on as a record from {}", dev.frosthaven.ranchercompanion.Debug.of(d), w.at().toShortString());
    }

    // ---- the records' walk, once a second ----

    public static void serverTick(MinecraftServer s) {
        if (s.getTickCount() % 20 != 0) return;
        TrailWalks t = get(s);
        if (t.walks.isEmpty()) return;
        Roster r = Ranch.roster(s);
        for (Walk w : java.util.List.copyOf(t.walks.values())) {
            Roster.Entry e = r.get(w.id);
            /* checked out, released, dead or moved home: the walk is over (a live one's goal sees the same) */
            if (e == null || e.where != Roster.Where.HOME || e.home == null || !e.home.dimension().equals(w.dim)) { t.end(w.id); continue; }
            if (w.live) {
                DrekkiEntity d = Ranch.liveOf(w.id);
                if (d == null) w.live = false;  /* gone without a leave we saw */
                else continue;
            }
            ServerLevel level = s.getLevel(w.dim);
            if (level == null) { t.end(w.id); continue; }
            if (!t.walk(level, w)) { t.end(w.id); continue; }
            t.setDirty();
            if (level.isPositionEntityTicking(w.at()) && Ranch.liveOf(w.id) == null) {
                w.live = true;  /* before it joins: the join check lets this copy in */
                if (Ranch.placeWalker(level, e, w.at()) == null) w.live = false;
            }
        }
    }

    /** one second of a record's walk; false: the walk is over */
    public boolean walk(ServerLevel level, Walk w) {
        Trails tr = Trails.get(level);
        RandomSource rnd = level.random;
        if (!tr.isPrint(w.node)) {  /* its print was broken: the nearest one, or home */
            BlockPos p = tr.nearestPrint(w.at(), 2);
            if (p == null) return false;
            w.node = p.asLong(); w.prev = Long.MIN_VALUE;
        }
        boolean night = level.isNight();
        if (w.asleep) { if (night) return true; w.asleep = false; }
        if (night && w.dir == 1) w.turn();  /* night: homeward, to the first rest stop */
        if (night && tr.restNear(w.node) != null) { w.asleep = true; return true; }
        w.progress += PACE;
        for (int i = 0; i < 8; i++) {
            BlockPos next = tr.next(w.node, w.prev, w.dir, rnd);
            if (next == null && w.dir == 1) { w.turn(); next = tr.next(w.node, w.prev, w.dir, rnd); }  /* the end: back the whole way */
            if (next == null) return false;  /* back at the start */
            double dist = Math.sqrt(next.distSqr(w.at()));
            if (w.progress < dist) break;
            w.progress -= dist;
            w.stepTo(next.asLong());
            if (level.isPositionEntityTicking(next)) break;
            if (night && tr.restNear(w.node) != null) { w.asleep = true; break; }
        }
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (Walk w : walks.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", w.id);
            c.putString("Dim", w.dim.location().toString());
            c.putLong("Node", w.node);
            c.putLong("Prev", w.prev);
            c.putInt("Dir", w.dir);
            c.putBoolean("Asleep", w.asleep);
            list.add(c);
        }
        tag.put("Walks", list);
        return tag;
    }

    static TrailWalks load(CompoundTag tag, HolderLookup.Provider reg) {
        TrailWalks t = new TrailWalks();
        for (Tag x : tag.getList("Walks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            Walk w = new Walk(c.getUUID("Id"), ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("Dim"))), c.getLong("Node"));
            w.prev = c.getLong("Prev"); w.dir = c.getInt("Dir") == -1 ? -1 : 1; w.asleep = c.getBoolean("Asleep");
            t.walks.put(w.id, w);
        }
        return t;
    }
}
