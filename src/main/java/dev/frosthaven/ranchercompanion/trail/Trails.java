package dev.frosthaven.ranchercompanion.trail;

import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * One level's trail graph (spec, Trails): every Drekki Footprint with its facing, and every Straw Bedding (a trail's rest
 * stops), kept as data so a trail is known where no chunk is loaded, the way Create keeps its track graph. The blocks
 * tell it when they are placed or removed; a chunk that loads is checked against it ({@link #reconcile}), for blocks
 * changed while it was not loaded (explosions, editors, older bedding).
 * <p>
 * A trail's links are not stored: the next print from one is any print 1 or 2 blocks ahead of it ({@link #next}).
 */
public final class Trails extends SavedData {
    /** how far a print reaches for the next one, and how far a rest stop's bedding may lie from the trail */
    public static final int STEP = 2, REST = 3;

    private final Long2ByteOpenHashMap prints = new Long2ByteOpenHashMap();
    private final LongOpenHashSet beds = new LongOpenHashSet();
    /** chunk -> its prints and beds, for reconcile */
    private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();

    public static Trails get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(Trails::new, Trails::load, null), "rancher_companion_trails");
    }

    private static long chunkOf(long p) { return ChunkPos.asLong(BlockPos.getX(p) >> 4, BlockPos.getZ(p) >> 4); }
    private void index(long p) { byChunk.computeIfAbsent(chunkOf(p), k -> new LongOpenHashSet()).add(p); }
    private void unindex(long p) {
        LongOpenHashSet s = byChunk.get(chunkOf(p));
        if (s != null && s.remove(p) && s.isEmpty()) byChunk.remove(chunkOf(p));
    }

    public void putPrint(BlockPos pos, Direction facing) {
        long p = pos.asLong();
        byte f = (byte) facing.get2DDataValue();
        if (prints.containsKey(p) && prints.get(p) == f) return;
        prints.put(p, f); index(p); setDirty();
    }

    public void removePrint(BlockPos pos) {
        long p = pos.asLong();
        if (!prints.containsKey(p)) return;
        prints.remove(p);
        if (!beds.contains(p)) unindex(p);
        setDirty();
    }

    public void putBed(BlockPos pos) { long p = pos.asLong(); if (beds.add(p)) { index(p); setDirty(); } }

    public void removeBed(BlockPos pos) {
        long p = pos.asLong();
        if (!beds.remove(p)) return;
        if (!prints.containsKey(p)) unindex(p);
        setDirty();
    }

    public boolean isPrint(long p) { return prints.containsKey(p); }
    public Direction facing(long p) { return Direction.from2DDataValue(prints.get(p)); }
    public int printCount() { return prints.size(); }

    /**
     * The print a walk goes to from {@code node}, or null at the trail's end. {@code dir} 1 walks the way the print faces,
     * -1 against it. Ahead first: of the prints 1 or 2 blocks ahead (a step up or down too), the nearest ahead, and one
     * of them at random at a fork. With none ahead, a print beside it (a trail that turns: its corner print still faces
     * the old way), never one nearer the print before. Never back to {@code prev} or {@code node}.
     */
    public BlockPos next(long node, long prev, int dir, RandomSource rnd) {
        if (!prints.containsKey(node)) return null;
        Direction f = facing(node);
        int vx = f.getStepX() * dir, vz = f.getStepZ() * dir;
        int x = BlockPos.getX(node), y = BlockPos.getY(node), z = BlockPos.getZ(node);
        List<Long> ahead = new ArrayList<>(), beside = new ArrayList<>();
        int best = Integer.MAX_VALUE;
        for (int dx = -STEP; dx <= STEP; dx++) for (int dz = -STEP; dz <= STEP; dz++) for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dz == 0) continue;
            long q = BlockPos.asLong(x + dx, y + dy, z + dz);
            if (q == prev || !prints.containsKey(q)) continue;
            int dot = dx * vx + dz * vz;
            if (dot >= 1) {
                if (dot < best) { best = dot; ahead.clear(); }
                if (dot == best) ahead.add(q);
            } else if (dot == 0 && Math.max(Math.abs(dx), Math.abs(dz)) == 1 && !backward(node, q, prev)) beside.add(q);
        }
        List<Long> from = !ahead.isEmpty() ? ahead : beside;
        return from.isEmpty() ? null : BlockPos.of(from.get(rnd.nextInt(from.size())));
    }

    /** a side step that comes nearer the print before: that is back the way it came, not a turn */
    private static boolean backward(long node, long q, long prev) {
        if (prev == Long.MIN_VALUE) return false;
        return BlockPos.of(q).distSqr(BlockPos.of(prev)) <= BlockPos.of(node).distSqr(BlockPos.of(prev));
    }

    /** the nearest Straw Bedding within 3 blocks of this print (a rest stop for the night), or null */
    public BlockPos restNear(long node) {
        int x = BlockPos.getX(node), y = BlockPos.getY(node), z = BlockPos.getZ(node);
        BlockPos best = null; int bd = Integer.MAX_VALUE;
        for (int dx = -REST; dx <= REST; dx++) for (int dz = -REST; dz <= REST; dz++) for (int dy = -1; dy <= 1; dy++) {
            long q = BlockPos.asLong(x + dx, y + dy, z + dz);
            int d = dx * dx + dz * dz + dy * dy;
            if (d < bd && beds.contains(q)) { bd = d; best = BlockPos.of(q); }
        }
        return best;
    }

    /** every Straw Bedding within 3 blocks of this print (the night's rest stop) */
    public java.util.List<BlockPos> restsNear(long node) {
        int x = BlockPos.getX(node), y = BlockPos.getY(node), z = BlockPos.getZ(node);
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int dx = -REST; dx <= REST; dx++) for (int dz = -REST; dz <= REST; dz++) for (int dy = -1; dy <= 1; dy++) {
            long q = BlockPos.asLong(x + dx, y + dy, z + dz);
            if (beds.contains(q)) out.add(BlockPos.of(q));
        }
        return out;
    }

    /** the print nearest to a spot within 2 blocks (a trail picked up again after a fight or a broken print), or null */
    public BlockPos nearestPrint(BlockPos at, int r) {
        BlockPos best = null; double bd = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -1; dy <= 1; dy++) {
            long q = BlockPos.asLong(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
            double d = dx * dx + dz * dz + dy * dy;
            if (d < bd && prints.containsKey(q)) { bd = d; best = BlockPos.of(q); }
        }
        return best;
    }

    public static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load ev) {
        if (!(ev.getLevel() instanceof ServerLevel level) || !(ev.getChunk() instanceof LevelChunk chunk)) return;
        if (level.getServer().isSameThread()) get(level).reconcile(chunk);
        else level.getServer().execute(() -> get(level).reconcile(chunk));
    }

    /** a chunk loaded: its prints and bedding as the blocks are now. A section whose palette has neither is skipped */
    public void reconcile(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        LongOpenHashSet had = byChunk.get(key);
        LongSet seen = new LongOpenHashSet();
        LevelChunkSection[] sections = chunk.getSections();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        boolean changed = false;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(Trails::trailBlock)) continue;
            int by = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int lx = 0; lx < 16; lx++) for (int ly = 0; ly < 16; ly++) for (int lz = 0; lz < 16; lz++) {
                BlockState st = s.getBlockState(lx, ly, lz);
                if (!trailBlock(st)) continue;
                long p = BlockPos.asLong(bx + lx, by + ly, bz + lz);
                seen.add(p);
                if (st.is(DrekkiBlocks.FOOTPRINT.get())) {
                    byte f = (byte) st.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).get2DDataValue();
                    if (!prints.containsKey(p) || prints.get(p) != f) { prints.put(p, f); index(p); changed = true; }
                } else if (beds.add(p)) { index(p); changed = true; }
            }
        }
        if (had != null) for (long p : had.toLongArray()) {
            if (seen.contains(p)) continue;
            if (prints.containsKey(p)) prints.remove(p);
            beds.remove(p);
            unindex(p); changed = true;
        }
        if (changed) setDirty();
    }

    private static boolean trailBlock(BlockState s) { return s.is(DrekkiBlocks.FOOTPRINT.get()) || s.is(DrekkiBlocks.STRAW_BEDDING.get()); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        long[] ps = prints.keySet().toLongArray();
        byte[] fs = new byte[ps.length];
        for (int i = 0; i < ps.length; i++) fs[i] = prints.get(ps[i]);
        tag.putLongArray("Prints", ps);
        tag.putByteArray("Facing", fs);
        tag.putLongArray("Beds", beds.toLongArray());
        return tag;
    }

    static Trails load(CompoundTag tag, HolderLookup.Provider reg) {
        Trails t = new Trails();
        long[] ps = tag.getLongArray("Prints");
        byte[] fs = tag.getByteArray("Facing");
        for (int i = 0; i < ps.length && i < fs.length; i++) { t.prints.put(ps[i], fs[i]); t.index(ps[i]); }
        for (long p : tag.getLongArray("Beds")) { t.beds.add(p); t.index(p); }
        return t;
    }
}
