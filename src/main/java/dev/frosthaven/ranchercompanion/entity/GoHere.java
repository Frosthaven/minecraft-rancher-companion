package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Seats;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The whistle's Go here (spec, Go here): sneak and blow it at a block and the owner's drekki goes to stand on that very
 * block, or sits on the seat looked at (Create's: a stock keeper's seat, a train's). It walks as far as it can, at most
 * {@link #TRAVEL} blocks (decided 2026-11-23: no drekki sent across the world); stuck, with no way on, or out of travel, it
 * stops where it is, except within {@link #SNAP} blocks of the spot (the step onto a train seat, a ledge), where it is
 * summoned the rest. Where it ends it holds for {@link #HOLD} ticks and then follows again, unless its owner told it to
 * stay by then (the whistle's plain use); on a job seat it is employed.
 */
public final class GoHere {
    /** how far the whistle points */
    public static final double RANGE = 64;
    /** walking there: no nearer by half a block in this long, it stops (stopShort). And never longer than the whole */
    private static final int STUCK = 60, LONGEST = 30 * 20;
    /** blocks it walks at most on one Go here; within SNAP of the spot a stop becomes a summon onto it */
    public static final double TRAVEL = 15, SNAP = 4;

    private GoHere() {}

    /** where a drekki was sent: a block to stand on, or a seat */
    public record Target(BlockPos stand, Seats.Seat seat, int since) {
        Vec3 at() { return seat != null ? seat.at() : Vec3.atBottomCenterOf(stand); }
    }

    /** the whistle, sneaking. Returns false when nothing was sent (a message says why) */
    public static boolean command(ServerPlayer p) { return command(p, true); }

    private static boolean command(ServerPlayer p, boolean mayCall) {
        DrekkiEntity d = Ranch.withOwner(p);
        if (d == null) {
            /* not with them (unloaded, another level): it comes first, then goes */
            if (!mayCall) { Ranch.tell(p, "gohere.lost", null); return false; }
            Ranch.call(p);
            Ranch.after(p.server, 5, () -> { if (p.isAlive()) command(p, false); });
            return true;
        }
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getViewVector(1).scale(RANGE)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        double blockDist = hit.getType() == HitResult.Type.MISS ? RANGE : hit.getLocation().distanceTo(eye);
        /* a hostile mob looked at, before the block behind it: an order to attack it, whatever the stance (decided 2026-11-23) */
        var mobHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, eye.add(p.getViewVector(1).scale(blockDist)),
            p.getBoundingBox().expandTowards(p.getViewVector(1).scale(blockDist)).inflate(1), en -> en instanceof net.minecraft.world.entity.monster.Enemy
                && en instanceof net.minecraft.world.entity.LivingEntity l && dev.frosthaven.ranchercompanion.combat.CombatGoals.fair(d, l), blockDist * blockDist);
        if (mobHit != null && mobHit.getEntity() instanceof net.minecraft.world.entity.LivingEntity mob) {
            if (d.stage() < 2) { Ranch.tell(p, "gohere.too_young", d); return false; }
            attack(d, mob);
            Ranch.tell(p, "gohere.attack", d);
            return true;
        }
        Seats.Seat seat = Seats.get().looked(p, RANGE, blockDist);
        if (seat == null && hit.getType() != HitResult.Type.MISS && p.level() instanceof net.minecraft.server.level.ServerLevel sl)
            seat = Seats.get().at(sl, hit.getBlockPos());
        if (seat != null) {
            if (!seat.free()) { Ranch.tell(p, "gohere.taken", d); return false; }
            send(d, new Target(null, seat, d.tickCount));
            Ranch.tell(p, "gohere.sit", d);
            return true;
        }
        if (hit.getType() == HitResult.Type.MISS) { Ranch.tell(p, "gohere.nothing", d); return false; }
        BlockPos stand = hit.getBlockPos().above();
        if (!d.fitsAt(stand)) { Ranch.tell(p, "gohere.no_room", d); return false; }
        send(d, new Target(stand, null, d.tickCount));
        Ranch.tell(p, "gohere.going", d);
        return true;
    }

    /** on its way: Walk decides on its first tick on the ground whether a path reaches the spot (the game makes no
     *  path for a mob in the air, mid-jump), else it goes as far as it can (stopShort) */
    static void send(DrekkiEntity d, Target t) {
        if (d.isPassenger()) d.stopRiding();
        Ranch.leaveStay(d);
        d.disengage();  /* sent somewhere: its fights end, an attack order too */
        d.goHere = t;
    }

    /** the attack order: off any seat and any other order, old fights dropped, this mob its target in every stance */
    public static void attack(DrekkiEntity d, net.minecraft.world.entity.LivingEntity mob) {
        if (d.isPassenger()) d.stopRiding();
        Ranch.leaveStay(d);
        d.disengage();
        d.goHere = null;
        d.endCalm();  /* an order to fight, whatever truce the whistle made */
        d.ordered = mob;
        d.setTarget(mob);
    }

    /** tests: send it to stand on a block, as the whistle would */
    public static void sendTo(DrekkiEntity d, BlockPos stand) { send(d, new Target(stand, null, d.tickCount)); }

    /** not there and no further: it holds where it stands, or is summoned the last few blocks */
    static void stopShort(DrekkiEntity d) {
        Target t = d.goHere;
        if (t != null && d.position().distanceTo(t.at()) <= SNAP) { arrive(d, true); return; }
        d.goHere = null;
        d.getNavigation().stop();
        dev.frosthaven.ranchercompanion.Debug.log("server: {} sent, stopped short at {}", dev.frosthaven.ranchercompanion.Debug.of(d), d.blockPosition().toShortString());
        d.heldUntil = d.tickCount + HOLD;
    }

    /** a path that reaches the spot, or null */
    private static net.minecraft.world.level.pathfinder.Path path(DrekkiEntity d, Target t) {
        var path = t.seat != null ? d.getNavigation().createPath(BlockPos.containing(t.at()), 1) : d.getNavigation().createPath(t.stand, 0);
        return path != null && path.canReach() && (t.seat != null || path.getTarget().equals(t.stand)) ? path : null;
    }

    /** a path toward the spot that ends short of it, or null when it leads nowhere nearer */
    private static net.minecraft.world.level.pathfinder.Path partial(DrekkiEntity d, Target t) {
        var path = d.getNavigation().createPath(BlockPos.containing(t.at()), 1);
        if (path == null || path.getEndNode() == null) return null;
        return Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()).distanceTo(t.at()) < d.position().distanceTo(t.at()) - 1 ? path : null;
    }

    /** server ticks it holds its spot after Go here before it follows again, unless told to stay by then */
    public static final int HOLD = 40;

    /** there (walked), or summoned: it stands on the very block, or sits; then it holds there (DrekkiEntity.heldUntil) */
    static void arrive(DrekkiEntity d, boolean summoned) {
        Target t = d.goHere;
        d.goHere = null;
        if (t == null) return;
        d.getNavigation().stop();
        if (t.seat != null) {
            if (!t.seat.sit(d)) { d.heldUntil = d.tickCount + HOLD; return; }  /* taken meanwhile, or gone: it holds where it is */
            if (t.seat.role() != Seats.NO_ROLE) { Ranch.employ(d, t.seat.role()); return; }
        } else {
            Vec3 at = Vec3.atBottomCenterOf(t.stand);
            if (summoned) d.jumpTo(at);  /* not into lava or fire */
            else d.moveTo(at.x, at.y, at.z, d.getYRot(), d.getXRot());
            d.setDeltaMovement(Vec3.ZERO);  /* no slide off the spot */
        }
        dev.frosthaven.ranchercompanion.Debug.log("server: {} sent {} {}", dev.frosthaven.ranchercompanion.Debug.of(d), summoned ? "and summoned to" : "and walked to", t.seat != null ? "a seat" : t.stand.toShortString());
        d.heldUntil = d.tickCount + HOLD;  /* not told to stay: that is the whistle's plain use (decided 2026-11-22) */
    }

    /** walking there; it outranks following, eating and fights, and is summoned when stuck */
    static final class Walk extends Goal {
        private final DrekkiEntity d;
        private double best, walked;
        private Vec3 last;
        private int bestAt, recalc;
        /** the first path is found (on the ground): from then on stuck means summoned */
        private boolean checked, partialOnly;

        Walk(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() { return d.goHere != null && d.isOut(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() { best = Double.MAX_VALUE; bestAt = d.tickCount; recalc = 0; checked = false; walked = 0; last = d.position(); }

        @Override
        public void tick() {
            Target t = d.goHere;
            if (t == null) return;  /* arrived, or called off, since canContinueToUse */
            Vec3 at = t.at();
            double dist = d.position().distanceTo(at);
            /* a seat: next to it (no path leads onto one); a block: within a block of its middle on its level, the last step is a snap */
            boolean close = t.seat != null ? dist < 1.6 : Math.hypot(d.getX() - at.x, d.getZ() - at.z) < 1 && Math.abs(d.getY() - at.y) < 0.6;
            if (close) { arrive(d, false); return; }
            walked += Math.hypot(d.getX() - last.x, d.getZ() - last.z);
            last = d.position();
            if (walked > TRAVEL) { stopShort(d); return; }
            if (!checked) {
                if (!d.onGround() && !d.isInWater() && d.tickCount - t.since() < 40) return;  /* landing first */
                var first = path(d, t);
                if (first == null) first = partial(d, t);  /* no way all the way: as far as it can */
                if (first == null) { stopShort(d); return; }
                checked = true;
                partialOnly = !first.canReach();
                d.getNavigation().moveTo(first, 1.3);
                bestAt = d.tickCount;
                recalc = 10;
                return;
            }
            if (dist < best - 0.5) { best = dist; bestAt = d.tickCount; }
            if (d.tickCount - bestAt > STUCK || d.tickCount - t.since > LONGEST) { stopShort(d); return; }
            if (d.getNavigation().isDone() && partialOnly) {  /* the end of the way it had (paths reach only so far): on from here, if nearer */
                var next = path(d, t);
                if (next == null) next = partial(d, t);
                if (next == null) { stopShort(d); return; }
                partialOnly = !next.canReach();
                d.getNavigation().moveTo(next, 1.3);
                return;
            }
            d.getLookControl().setLookAt(at.x, at.y + 0.5, at.z);
            if (--recalc > 0 && !d.getNavigation().isDone()) return;
            recalc = 10;
            if (partialOnly) return;  /* it keeps the way it has to the end */
            if (t.seat != null) d.getNavigation().moveTo(at.x, at.y, at.z, 1, 1.3);
            else d.getNavigation().moveTo(d.getNavigation().createPath(t.stand, 0), 1.3);
        }
    }
}
