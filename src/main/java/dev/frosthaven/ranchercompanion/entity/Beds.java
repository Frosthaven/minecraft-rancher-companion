package dev.frosthaven.ranchercompanion.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Straw Bedding for the night (spec, Wandering): each drekki claims a free bedding block and lies down on that block,
 * not beside it. A drekki going home and a drekki on a trail both slept next to the bedding, a block short where their
 * path ended, and two picked the same block (Shane, 2026-11-27). A claim holds while its drekki is live near it; a
 * block another drekki stands or sleeps on is taken too. They spread out: a block with no taken neighbour first.
 */
public final class Beds {
    private Beds() {}

    /** a claim: the bedding block (level and position) a drekki is going to or lying on */
    private record Spot(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, long pos) {}
    private static final Map<Spot, UUID> CLAIMS = new HashMap<>();
    private static final Map<UUID, Spot> BY = new HashMap<>();

    /** ticks a drekki may spend stepping onto its bedding before it tries another block */
    static final int STEP_TICKS = 60;

    /** a free bedding block of these, not one in {@code not} (given up tonight), at random, claimed for this drekki;
     *  null when none is free */
    public static BlockPos pick(DrekkiEntity d, Collection<BlockPos> beds, Collection<BlockPos> not) {
        release(d);
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos b : beds) if (!not.contains(b) && !taken(d, b)) free.add(b);
        if (free.isEmpty()) return null;
        /* spread out (Shane, 2026-11-27): of the free blocks, those with the fewest taken neighbours, one at random */
        int fewest = Integer.MAX_VALUE;
        List<BlockPos> best = new ArrayList<>();
        for (BlockPos f : free) {
            int n = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if ((dx != 0 || dz != 0) && taken(d, f.offset(dx, 0, dz))) n++;
            if (n < fewest) { fewest = n; best.clear(); }
            if (n == fewest) best.add(f);
        }
        BlockPos b = best.get(d.getRandom().nextInt(best.size()));
        Spot s = new Spot(d.level().dimension(), b.asLong());
        CLAIMS.put(s, d.drekkiId());
        BY.put(d.drekkiId(), s);
        return b;
    }

    /** another drekki's: claimed by one live near it, or one stands on it */
    static boolean taken(DrekkiEntity d, BlockPos b) {
        UUID by = CLAIMS.get(new Spot(d.level().dimension(), b.asLong()));
        if (by != null && !by.equals(d.drekkiId())) {
            DrekkiEntity o = dev.frosthaven.ranchercompanion.ranch.Ranch.liveOf(by);
            if (o != null && !o.isRemoved() && o.level() == d.level() && o.blockPosition().distSqr(b) <= 32 * 32) return true;
        }
        for (DrekkiEntity o : d.level().getEntitiesOfClass(DrekkiEntity.class, new AABB(b), o -> o != d && o.isAlive()))
            if (o.blockPosition().equals(b)) return true;
        return false;
    }

    public static void release(DrekkiEntity d) {
        if (d.drekkiId() == null) return;
        Spot s = BY.remove(d.drekkiId());
        if (s != null) CLAIMS.remove(s, d.drekkiId());
    }

    public enum Step { ON, GOING, STUCK }

    /**
     * One tick toward lying on this bedding block: a path to it, and from close by the last step straight onto the block
     * (a path stops a block short of it). STUCK after {@link #STEP_TICKS} of trying from close by, or of paths that do
     * not reach it. {@code tries} is the caller's counter, one per call.
     */
    public static Step approach(DrekkiEntity d, BlockPos bed, int[] tries) {
        if (d.blockPosition().equals(bed)) return Step.ON;
        double dx = bed.getX() + 0.5 - d.getX(), dz = bed.getZ() + 0.5 - d.getZ();
        boolean close = dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(bed.getY() - d.getY()) < 1.5;
        if (close) {
            d.getNavigation().stop();
            d.getMoveControl().setWantedPosition(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0.6);
            tries[0]++;
        } else if (d.getNavigation().isDone()) {   /* on its way it only counts paths that end short */
            var path = d.getNavigation().createPath(bed, 0);
            if (path != null) d.getNavigation().moveTo(path, 0.8);
            tries[0] += 6;
        }
        return tries[0] > STEP_TICKS ? Step.STUCK : Step.GOING;
    }

    public static void clear() { CLAIMS.clear(); BY.clear(); }
}
