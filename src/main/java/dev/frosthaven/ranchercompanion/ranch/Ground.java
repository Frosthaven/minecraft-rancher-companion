package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The world as a drekki stands in it, and the hatchery's rules on it: where a drekki can stand ({@link Flood.Grid}),
 * where one appears (Placement), the pen it may wander in (Wandering) and the stable checks a hatch needs (Hatchery).
 */
public final class Ground implements Flood.Grid {
    /** how far a placement may reach from the hatchery, and how far up or down a pen may run */
    public static final int PLACE = 3, ANCHOR = 3, PEN_UP = 6, LEASH = 25;

    private final Level level;
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    public Ground(Level level) { this.level = level; }

    private double top(int x, int y, int z) {
        m.set(x, y, z);
        BlockState s = level.getBlockState(m);
        VoxelShape v = s.getCollisionShape(level, m);
        return v.isEmpty() ? 0 : v.max(net.minecraft.core.Direction.Axis.Y);
    }

    /** solid ground at most a block high under the feet (or a carpet-like layer at them), no liquid, a clear block at the head */
    @Override
    public boolean stand(int x, int y, int z) {
        if (!level.isLoaded(m.set(x, y, z))) return false;
        if (!level.getFluidState(m).isEmpty()) return false;
        double feet = top(x, y, z);
        if (feet > 0.2) return false;
        if (feet == 0) {
            double below = top(x, y - 1, z);
            if (below < 0.4 || below > 1.0) return false;
        }
        return top(x, y + 1, z) == 0 || feet + 0.6 <= 1;
    }

    @Override
    public boolean headroom(int x, int y, int z) { return top(x, y, z) == 0; }

    /** the spot right for a drekki of this stage: stage 1 fits in one block; bigger ones need their hitbox clear */
    public boolean fits(long p, int stage) {
        int x = Flood.x(p), y = Flood.y(p), z = Flood.z(p);
        var d = dev.frosthaven.ranchercompanion.look.Dims.of(stage);
        int h = (int) Math.ceil(d.hitH), w = (int) Math.ceil(d.hitW);
        for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < w; dz++) {
            if (!stand(x + dx, y, z + dz)) return false;
            for (int dy = 1; dy < h; dy++) if (top(x + dx, y + dy, z + dz) != 0) return false;
        }
        return true;
    }

    /** where a drekki of this stage appears at the hatchery, or null: no room within 3 blocks */
    public BlockPos place(BlockPos hatchery, int stage) {
        long p = Flood.place(this, hatchery.getX(), hatchery.getY(), hatchery.getZ(), PLACE, s -> fits(s, stage));
        return p == -1 ? null : new BlockPos(Flood.x(p), Flood.y(p), Flood.z(p));
    }

    /**
     * The pen: every spot a drekki leashed to this hatchery may use, packed (see Flood.pack): a spot it can stand on,
     * within the leash, within 3 blocks of an anchor (a dirt path under the feet, Straw Bedding at them, or the hatchery
     * itself). Anchors chain: one counts when it is within 3 blocks of one that counts, starting from the hatchery.
     */
    public Set<Long> pen(BlockPos h, int stage) {
        /* every stage: within 3 blocks of any anchor the walk reaches, anchor to anchor, inside the leash */
        int reach = LEASH, leash = LEASH;
        List<long[]> anchors = new ArrayList<>();
        anchors.add(new long[]{h.getX(), h.getY(), h.getZ()});
        int r = reach;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -PEN_UP; dy <= PEN_UP; dy++) {
            int x = h.getX() + dx, y = h.getY() + dy, z = h.getZ() + dz;
            BlockState s = level.getBlockState(m.set(x, y, z));
            if (s.is(DrekkiBlocks.STRAW_BEDDING.get()) || s.is(Blocks.DIRT_PATH)) anchors.add(new long[]{x, s.is(Blocks.DIRT_PATH) ? y + 1 : y, z});
        }
        /* anchors chain: one counts when it lies within 3 blocks of an anchor that already counts, starting at the hatchery */
        List<long[]> joined = new ArrayList<>(List.of(anchors.get(0)));
        List<long[]> rest = new ArrayList<>(anchors.subList(1, anchors.size()));
        for (int i = 0; i < joined.size(); i++) {
            long[] j = joined.get(i);
            for (var it = rest.iterator(); it.hasNext(); ) {
                long[] c = it.next();
                if (Math.abs(c[0] - j[0]) <= ANCHOR && Math.abs(c[1] - j[1]) <= ANCHOR && Math.abs(c[2] - j[2]) <= ANCHOR) { joined.add(c); it.remove(); }
            }
        }
        /* the area: every spot a drekki can stand on within 3 blocks of a counted anchor, inside the leash */
        Set<Long> out = new HashSet<>();
        for (long[] c : joined) for (int dx = -ANCHOR; dx <= ANCHOR; dx++) for (int dz = -ANCHOR; dz <= ANCHOR; dz++) for (int dy = -ANCHOR; dy <= ANCHOR; dy++) {
            int x = (int) c[0] + dx, y = (int) c[1] + dy, z = (int) c[2] + dz;
            if (Math.max(Math.abs(x - h.getX()), Math.abs(z - h.getZ())) > leash || (x == h.getX() && z == h.getZ())) continue;
            long p = Flood.pack(x, y, z);
            if (!out.contains(p) && stand(x, y, z)) out.add(p);
        }
        return out;
    }

    /** the pen's spots on Straw Bedding (spec, Wandering: a hatchling's strolls and sleep go there) */
    public Set<Long> bedding(Set<Long> pen) {
        Set<Long> out = new HashSet<>();
        for (long p : pen) if (level.getBlockState(m.set(Flood.x(p), Flood.y(p), Flood.z(p))).is(DrekkiBlocks.STRAW_BEDDING.get())) out.add(p);
        return out;
    }

    /** the stable's ceiling (spec, Hatchery): a covered 3x3 of columns over the hatchery, and the two blocks above it clear */
    public boolean ceiling(BlockPos h) {
        if (top(h.getX(), h.getY() + 1, h.getZ()) != 0 || top(h.getX(), h.getY() + 2, h.getZ()) != 0) return false;
        for (int ox = -2; ox <= 0; ox++) for (int oz = -2; oz <= 0; oz++) {
            boolean all = true;
            for (int dx = 0; dx <= 2 && all; dx++) for (int dz = 0; dz <= 2 && all; dz++) {
                boolean cov = false;
                for (int y = h.getY() + 3; y <= h.getY() + 10 && !cov; y++) cov = top(h.getX() + ox + dx, y, h.getZ() + oz + dz) > 0;
                all = cov;
            }
            if (all) return true;
        }
        return false;
    }

    /** light above 0 on every open side of the hatchery (a side against a solid block has no light to read) */
    public boolean lit(BlockPos h) {
        for (var d : net.minecraft.core.Direction.values()) {
            if (d == net.minecraft.core.Direction.DOWN) continue;
            BlockPos p = h.relative(d);
            if (level.getBlockState(p).isSolidRender(level, p)) continue;
            if (level.getMaxLocalRawBrightness(p) == 0) return false;
        }
        return true;
    }
}
