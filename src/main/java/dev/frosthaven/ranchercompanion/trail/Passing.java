package dev.frosthaven.ranchercompanion.trail;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Two trail walkers that meet on a path one block wide pushed against each other and stood there (Shane, 2026-11-28).
 * A walker counts as blocked when it is on its way (a path under way), moved less than {@link #MOVED} in
 * {@link #CHECK} ticks, and another walker is within {@link #NEAR}; only then do those two stop pushing each other, for
 * {@link #FOR} ticks, so they slip past. Never otherwise: drekki do not walk through each other just because they can
 * (Shane). Server side; DrekkiEntity's push and doPush ask {@link #through}.
 */
public final class Passing {
    static final int CHECK = 30, FOR = 60;
    static final double MOVED = 0.3, NEAR = 1.3;

    private Passing() {}

    /** a pass under way: the other walker, and the game time it ends */
    private record Pass(Entity with, long until) {}
    private static final Map<Entity, Pass> PASSES = new WeakHashMap<>();
    private static final Map<Entity, Vec3> SAMPLE = new WeakHashMap<>();

    /** these two pass through each other now */
    public static boolean through(Entity a, Entity b) {
        if (!(a instanceof DrekkiEntity) || !(b instanceof DrekkiEntity)) return false;
        long now = a.level().getGameTime();
        Pass p = PASSES.get(a), q = PASSES.get(b);
        return p != null && p.with() == b && now < p.until() || q != null && q.with() == a && now < q.until();
    }

    /** this walker passes through another one now */
    static boolean passing(Entity a) {
        Pass p = PASSES.get(a);
        return p != null && a.level().getGameTime() < p.until();
    }

    /** every tick of a trail walk: is this walker blocked by another one? */
    static void check(DrekkiEntity d) {
        if (d.tickCount % CHECK != 0) return;
        Vec3 was = SAMPLE.put(d, d.position());
        if (was == null || !d.getNavigation().isInProgress() || was.distanceTo(d.position()) >= MOVED) return;
        blockedBy(d);
    }

    /** another walker within reach blocks this one: they pass through each other now. False when none is near */
    static boolean blockedBy(DrekkiEntity d) {
        for (DrekkiEntity o : d.level().getEntitiesOfClass(DrekkiEntity.class, new AABB(d.blockPosition()).inflate(NEAR),
                o -> o != d && o.isAlive() && o.distanceTo(d) <= NEAR && TrailWalks.walking(o))) {
            long until = d.level().getGameTime() + FOR;
            PASSES.put(d, new Pass(o, until));
            PASSES.put(o, new Pass(d, until));
            dev.frosthaven.ranchercompanion.RancherCompanion.LOG.info("[trails] {} and {} blocked each other on a trail: they pass through each other for {} s",
                d.getName().getString(), o.getName().getString(), FOR / 20);
            return true;
        }
        return false;
    }
}
