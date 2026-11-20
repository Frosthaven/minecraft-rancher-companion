package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.life.Feeding;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A hungry drekki eats at a trough (spec, Feeding at troughs): when not fighting or following its owner on the move, it
 * claims a spot at a random usable trough, walks to it, eats one item, lets the spot go and strolls a few blocks off,
 * then waits a short pause, so a waiting drekki gets the spot. A path that keeps failing marks the trough unreachable.
 */
final class EatGoal extends Goal {
    private static final int LOOK_EVERY = 40, PAUSE = 200, PATIENCE = 100, CHEW = 30;
    private final DrekkiEntity d;
    private Feeding.Spot spot;
    private int nextLook, stuck, chew;
    private long pauseUntil;
    private Player still;
    private int stillTicks;
    private net.minecraft.world.phys.Vec3 stillAt;

    EatGoal(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

    /** a checked-out drekki eats only while its owner stands still */
    private boolean ownerStill() {
        Player p = d.ownerPlayer();
        if (p == null) return false;
        if (p != still) { still = p; stillTicks = 0; stillAt = p.position(); }
        /* a server player's velocity is not kept up to date: compare positions (the goal is asked every other tick) */
        stillTicks = p.position().distanceToSqr(stillAt) < 0.01 ? stillTicks + 1 : 0;
        stillAt = p.position();
        return stillTicks > 20;
    }

    @Override
    public boolean canUse() {
        if (!d.owned() || d.life.ghost || d.getTarget() != null || d.isSleepingLoop() || !d.life.wantsFood()) return false;  /* a ghost does not eat */
        if (d.level().getGameTime() < pauseUntil) return false;
        boolean still = !d.isOut() || ownerStill();
        if (--nextLook > 0) return false;
        nextLook = adjustedTickDelay(LOOK_EVERY);
        if (still && (spot = Feeding.claim(d)) != null) return true;
        packMeal();  /* no usable trough, or on the move with its owner: its pack (spec, Hunger) */
        return false;
    }

    private void packMeal() {
        ItemStack food = Feeding.fromPack(d);
        if (food.isEmpty()) return;
        Vitals.eat(d, food, true);
        d.act(dev.frosthaven.ranchercompanion.look.Anim.FEED, CHEW);
        Ranch.touch(d);
        pauseUntil = d.level().getGameTime() + PAUSE;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && d.getTarget() == null && (!d.isOut() || ownerStill()) && d.life.wantsFood();
    }

    @Override
    public void start() { stuck = 0; chew = 0; d.eating = true; walk(); }

    private void walk() {
        BlockPos at = spot.at();
        var path = d.getNavigation().createPath(at, 0);
        if (path != null && path.canReach()) d.getNavigation().moveTo(path, 1.0);
    }

    private boolean there() {
        BlockPos at = spot.at();
        return d.blockPosition().equals(at) || d.position().distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 0.6;
    }

    @Override
    public void tick() {
        BlockPos t = spot.trough();
        if (!there()) {
            if (d.getNavigation().isDone()) {
                if (++stuck > PATIENCE / 10) { Feeding.unreachable(d, spot); spot = null; return; }
                walk();
            }
            return;
        }
        d.getNavigation().stop();
        d.getLookControl().setLookAt(t.getX() + 0.5, t.getY() + 0.4, t.getZ() + 0.5);
        if (chew == 0) d.act(Anim.FEED, CHEW + 10);
        if (++chew < CHEW) return;
        ItemStack food = Feeding.take(d, t);
        if (!food.isEmpty()) { Vitals.eat(d, food, true); Ranch.touch(d); }
        Feeding.release(d, spot);
        spot = null;
        pauseUntil = d.level().getGameTime() + PAUSE;
        stepAway(t);
    }

    /** a few blocks off the trough, inside its area, so the spot is clear */
    private void stepAway(BlockPos trough) {
        for (int i = 0; i < 8; i++) {
            BlockPos p = d.blockPosition().offset(d.getRandom().nextInt(-4, 5), 0, d.getRandom().nextInt(-4, 5));
            if (p.distSqr(trough) < 9 || d.isOut() || !d.inPen(p)) continue;
            if (d.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.8)) return;
        }
    }

    @Override
    public void stop() {
        if (spot != null) Feeding.release(d, spot);
        spot = null;
        d.eating = false;
    }
}
