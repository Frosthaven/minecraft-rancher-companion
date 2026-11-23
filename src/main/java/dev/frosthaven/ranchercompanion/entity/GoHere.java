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
 * block, or sits on the seat looked at (Create's: a stock keeper's seat, a train's). It walks when a path reaches the
 * spot; with none, or stuck on the way, it is summoned there. Once there it holds the spot for {@link #HOLD} ticks and
 * then follows again, unless its owner told it to stay by then (the whistle's plain use); on a job seat it is employed.
 */
public final class GoHere {
    /** how far the whistle points */
    public static final double RANGE = 64;
    /** walking there: no nearer by half a block in this long, it is summoned. And never longer than the whole */
    private static final int STUCK = 60, LONGEST = 30 * 20;

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
     *  path for a mob in the air, mid-jump), else it is summoned there */
    static void send(DrekkiEntity d, Target t) {
        if (d.isPassenger()) d.stopRiding();
        Ranch.leaveStay(d);
        d.goHere = t;
        if (t.at().distanceTo(d.position()) > RANGE) arrive(d, true);
    }

    /** a path that reaches the spot, or null */
    private static net.minecraft.world.level.pathfinder.Path path(DrekkiEntity d, Target t) {
        var path = t.seat != null ? d.getNavigation().createPath(BlockPos.containing(t.at()), 1) : d.getNavigation().createPath(t.stand, 0);
        return path != null && path.canReach() && (t.seat != null || path.getTarget().equals(t.stand)) ? path : null;
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
        private double best;
        private int bestAt, recalc;
        /** the first path is found (on the ground): from then on stuck means summoned */
        private boolean checked;

        Walk(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() { return d.goHere != null && d.isOut(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void start() { best = Double.MAX_VALUE; bestAt = d.tickCount; recalc = 0; checked = false; }

        @Override
        public void tick() {
            Target t = d.goHere;
            if (t == null) return;  /* arrived, or called off, since canContinueToUse */
            Vec3 at = t.at();
            double dist = d.position().distanceTo(at);
            /* a seat: next to it (no path leads onto one); a block: within a block of its middle on its level, the last step is a snap */
            boolean close = t.seat != null ? dist < 1.6 : Math.hypot(d.getX() - at.x, d.getZ() - at.z) < 1 && Math.abs(d.getY() - at.y) < 0.6;
            if (close) { arrive(d, false); return; }
            if (!checked) {
                if (!d.onGround() && !d.isInWater() && d.tickCount - t.since() < 40) return;  /* landing first */
                var first = path(d, t);
                if (first == null) { arrive(d, true); return; }  /* no way to walk there */
                checked = true;
                d.getNavigation().moveTo(first, 1.3);
                bestAt = d.tickCount;
                recalc = 10;
                return;
            }
            if (dist < best - 0.5) { best = dist; bestAt = d.tickCount; }
            if (d.tickCount - bestAt > STUCK || d.tickCount - t.since > LONGEST) { arrive(d, true); return; }
            d.getLookControl().setLookAt(at.x, at.y + 0.5, at.z);
            if (--recalc > 0 && !d.getNavigation().isDone()) return;
            recalc = 10;
            if (t.seat != null) d.getNavigation().moveTo(at.x, at.y, at.z, 1, 1.3);
            else d.getNavigation().moveTo(d.getNavigation().createPath(t.stand, 0), 1.3);
        }
    }
}
