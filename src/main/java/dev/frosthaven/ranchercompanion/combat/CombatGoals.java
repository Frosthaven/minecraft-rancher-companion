package dev.frosthaven.ranchercompanion.combat;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Diet;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

/**
 * Fighting by stance (spec, Combat; Growth release: stage 2's basic attack). Aggressive: any hostile within 10 blocks
 * (neutral mobs only while angry; never creepers), and what Reactive answers. Reactive: whatever hurts it or its owner,
 * and whatever its owner attacks. Passive: never. Stage 1 and ghosts never fight; players only with PvP on. Raw and Seed
 * bite in melee (4); Cooked and Root shoot the Elemental ball (3) from range.
 */
public final class CombatGoals {
    private CombatGoals() {}

    public static final float BITE = 4;
    static final double AGGRO = 10, LEASH = 16, ORDER = 32;

    public static boolean fights(DrekkiEntity d) {
        return d.owned() && d.stage() >= 2 && !d.life.ghost && (d.life.stance != Life.Stance.PASSIVE || order(d) != null) && !d.isSleepingLoop();
    }

    /** an attack order still on (GoHere.attack): the mob alive, fair and within ORDER; else it is dropped */
    public static LivingEntity order(DrekkiEntity d) {
        LivingEntity o = d.ordered;
        if (o != null && (!fair(d, o) || o.level() != d.level() || o.distanceToSqr(d) > ORDER * ORDER)) { d.ordered = null; o = null; }
        return o;
    }

    /** a fair target: alive, not itself, its owner, the owner's pets or drekki; no creepers; players only with PvP on */
    public static boolean fair(DrekkiEntity d, LivingEntity t) {
        if (t == null || !t.isAlive() || t == d || t instanceof Creeper || t.isInvulnerable()) return false;
        var owner = d.owner();
        if (owner != null && owner.equals(t.getUUID())) return false;
        if (t instanceof DrekkiEntity o && owner != null && owner.equals(o.owner())) return false;
        if (t instanceof OwnableEntity o && owner != null && owner.equals(o.getOwnerUUID())) return false;
        if (t instanceof Player p && (p.isCreative() || p.isSpectator() || d.getServer() == null || !d.getServer().isPvpAllowed())) return false;
        /* only a hostile mob, in every stance (decided 2026-11-22): the red health bar's rule (Enemy). A village's guard
           or iron golem that turns on a player who hit a villager, an angry wolf or bee, is not fought back */
        return t instanceof Player || t instanceof Enemy;
    }

    /** Aggressive picks these by itself: a hostile mob, and a neutral one (an enderman, a zombified piglin) only while angry */
    private static boolean hostile(LivingEntity t) {
        return t instanceof Enemy && (!(t instanceof NeutralMob n) || n.isAngry());
    }

    /** picks its target twice a second by the stance's rules, nearest first */
    public static final class PickTarget extends Goal {
        private final DrekkiEntity d;
        private int next;

        public PickTarget(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.TARGET)); }

        @Override
        public boolean canUse() {
            if (--next > 0 || !fights(d)) return false;
            next = 10;
            LivingEntity t = choose();
            if (t == null || t == d.getTarget()) return false;
            d.setTarget(t);
            return false;  /* setting the target is the whole job */
        }

        private LivingEntity choose() {
            LivingEntity cur = d.getTarget();
            LivingEntity o = order(d);
            if (o != null) return o == cur ? null : o;  /* an attack order: that mob, nothing else */
            if (cur != null && fair(d, cur) && cur.distanceToSqr(d) <= LEASH * LEASH) return null;  /* keeps its fight */
            d.setTarget(null);
            Player owner = d.ownerPlayer();
            /* only hits since it last left Passive count: it starts fresh (DrekkiEntity.setStance) */
            if (d.getLastHurtByMob() != null && fair(d, d.getLastHurtByMob()) && d.tickCount - d.getLastHurtByMobTimestamp() < 200
                && d.getLastHurtByMobTimestamp() >= d.fightsFrom) return d.getLastHurtByMob();
            if (owner != null) {
                boolean fresh = owner == d.fightsFromOwner;
                if (owner.getLastHurtByMob() != null && fair(d, owner.getLastHurtByMob()) && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200
                    && (!fresh || owner.getLastHurtByMobTimestamp() >= d.fightsFromOwnerTick)
                    && owner.getLastHurtByMob().distanceToSqr(d) <= LEASH * LEASH) return owner.getLastHurtByMob();
                if (owner.getLastHurtMob() != null && fair(d, owner.getLastHurtMob()) && owner.tickCount - owner.getLastHurtMobTimestamp() < 200
                    && (!fresh || owner.getLastHurtMobTimestamp() >= d.fightsFromOwnerTick)
                    && owner.getLastHurtMob().distanceToSqr(d) <= LEASH * LEASH) return owner.getLastHurtMob();
            }
            if (d.life.stance != Life.Stance.AGGRESSIVE) return null;
            LivingEntity best = null;
            for (LivingEntity t : d.level().getEntitiesOfClass(LivingEntity.class, d.getBoundingBox().inflate(AGGRO), e -> fair(d, e) && hostile(e) && !(e instanceof Player)))
                if (d.hasLineOfSight(t) && (best == null || t.distanceToSqr(d) < best.distanceToSqr(d)) && (d.isOut() || d.inPen(t.blockPosition()))) best = t;
            return best;
        }
    }

    /** closes in and bites, or keeps its range and shoots */
    public static final class Attack extends Goal {
        private final DrekkiEntity d;
        private int cooldown, strikeIn, repath;

        public Attack(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        private boolean ranged() { Diet diet = Diet.of(d.diet()); return diet == Diet.COOKED || diet == Diet.ROOT; }

        @Override
        public boolean canUse() { return fights(d) && fair(d, d.getTarget()); }

        @Override
        public boolean canContinueToUse() { return canUse() && d.getTarget().distanceToSqr(d) <= (d.ordered == d.getTarget() ? ORDER * ORDER : LEASH * LEASH); }

        @Override
        public void start() { repath = 0; strikeIn = 0; }

        @Override
        public void stop() { d.getNavigation().stop(); if (!fair(d, d.getTarget())) d.setTarget(null); }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = d.getTarget();
            if (t == null) return;
            d.getLookControl().setLookAt(t, 30, 30);
            double dist = d.distanceToSqr(t);
            if (cooldown > 0) cooldown--;
            if (strikeIn > 0 && --strikeIn == 0) strike(t);
            boolean sees = d.hasLineOfSight(t);
            if (ranged()) {
                if ((dist > 8 * 8 || !sees) && --repath <= 0) { d.getNavigation().moveTo(t, 1.1); repath = 10; }
                else if (dist <= 8 * 8 && sees) d.getNavigation().stop();
                if (cooldown == 0 && sees && dist <= 10 * 10) { d.act(Anim.BALL, 56); strikeIn = 8; cooldown = d.surged() ? 20 : 40; }
            } else {
                /* it reaches as far as the game's melee mobs do (a zombie's rule: its box grown 0.83 a side meets the
                   target's), and never less than before. A zombie hit it from further off than it could bite */
                double reach = d.getBbWidth() / 2 + t.getBbWidth() / 2 + 1.0;
                if (--repath <= 0) { d.getNavigation().moveTo(t, 1.2); repath = 8; }
                if (cooldown == 0 && (dist <= reach * reach || d.isWithinMeleeAttackRange(t))) { d.act(Anim.BITE, 32); strikeIn = 6; cooldown = d.surged() ? 12 : 24; }
            }
        }

        private void strike(LivingEntity t) {
            if (!t.isAlive()) return;
            if (ranged()) {
                ElementalBall b = RancherCompanion.ELEMENTAL_BALL.get().create(d.level());
                if (b == null) return;
                b.setOwner(d);
                /* point blank: a ball made inside the target's box never meets it, so it hits at once */
                double close = d.getBbWidth() / 2 + t.getBbWidth() / 2 + 1.5;
                if (d.distanceToSqr(t) <= close * close) { b.setPos(t.getX(), t.getY(0.5), t.getZ()); b.strike(t); return; }
                b.setPos(d.getX(), d.getEyeY() - 0.1, d.getZ());
                double dx = t.getX() - d.getX(), dy = t.getY(0.5) - (d.getEyeY() - 0.1), dz = t.getZ() - d.getZ();
                b.shoot(dx, dy, dz, 1.0f, 1.0f);
                d.level().addFreshEntity(b);
                d.playSound(dev.frosthaven.ranchercompanion.DrekkiSounds.FIRE_SHOT.get(), 0.7f, 1.2f);
            } else {
                /* a bite begun in reach lands: a hit that knocks the drekki back in its 6 ticks no longer spoils it
                   (2026-11-21: in water a zombie kept it from ever biting). Not on a target that has gone far */
                if (d.level() != t.level() || d.distanceToSqr(t) > 6 * 6) return;
                if (t.hurt(d.damageSources().mobAttack(d), BITE)) d.playSound(dev.frosthaven.ranchercompanion.DrekkiSounds.BITE2.get(), 0.8f, 1.1f);
            }
        }
    }
}
