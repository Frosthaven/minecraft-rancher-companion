package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import java.util.HashSet;
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
    public static final int PLACE = 3, ANCHOR = 3, PEN_UP = 6, LEASH = 50;

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
     * The pen: every spot a drekki leashed to this hatchery may use, packed (see Flood.pack): a spot it can walk to from
     * the hatchery, within the leash, within 3 blocks of an anchor it can walk to (a dirt path under the feet, Straw
     * Bedding at them, or the hatchery itself). Anchors chain along the walk, so a fence or wall bounds the pen; an open
     * door or gate lets the walk through (Shane, 2026-11-27: the area went through his pen's fence, and walkers pressed
     * against it for a trail start outside). A pup (stage 1) anchors only to the hatchery and Straw Bedding, never a dirt
     * path (Shane, 2026-11-27).
     */
    public Set<Long> pen(BlockPos h, int stage) {
        boolean paths = stage > 1;
        Flood.Grid walk = new Flood.Grid() {
            @Override public boolean stand(int x, int y, int z) { return Ground.this.stand(x, y, z) || doorway(x, y, z); }
            @Override public boolean headroom(int x, int y, int z) { return Ground.this.headroom(x, y, z) || door(x, y, z); }
        };
        return Flood.anchored(walk, h.getX(), h.getY(), h.getZ(), LEASH, PEN_UP + ANCHOR, ANCHOR, p -> {
            int x = Flood.x(p), y = Flood.y(p), z = Flood.z(p);
            if (Math.abs(y - h.getY()) > PEN_UP) return false;
            if (level.getBlockState(m.set(x, y, z)).is(DrekkiBlocks.STRAW_BEDDING.get())) return true;
            return paths && level.getBlockState(m.set(x, y - 1, z)).is(Blocks.DIRT_PATH);
        });
    }

    /** an open door at these feet, its upper half above, ground under it */
    private boolean doorway(int x, int y, int z) {
        if (!door(x, y, z) || !level.isLoaded(m.set(x, y, z))) return false;
        double below = top(x, y - 1, z);
        return below >= 0.4 && below <= 1.0;
    }

    private boolean door(int x, int y, int z) {
        BlockState s = level.getBlockState(m.set(x, y, z));
        return s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && s.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
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
