package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.RideMath;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * A ridden Mature (spec, Stage 3; Traits: mount controls). Its rider's game moves it, as a horse: the jump key jumps,
 * jumps again in the air (Cloud Stompers) and, held, flies on wingbeats (Wings of Hope); the sprint key dashes (Four
 * Winds). The server keeps the rules a game cannot be trusted with: off in deep liquid, the fall, Predator's Eye.
 */
public final class Riding {
    private Riding() {}

    /** the rider's keys, from the client (the server has none) */
    public interface Keys { boolean jump(); boolean sprint(); }
    public static Keys keys;

    /** client-side state of the one drekki this game steers */
    static final class State { boolean jumpWas, sprintWas, groundWas; int airJumps, air, dashIn, beat = -1; }

    public static boolean holds(DrekkiEntity d, Trait t) {
        if (d.ghost()) return false;
        for (Tag tag : d.traitView().getList("traits", Tag.TAG_STRING)) if (tag.getAsString().equals(t.id)) return true;
        return false;
    }

    /** the rider's game, each tick it steers */
    static void steer(DrekkiEntity d, Player rider) {
        State s = d.ride;
        boolean jump = keys != null && keys.jump(), sprint = keys != null && keys.sprint();
        boolean grounded = d.onGround();
        Vec3 v = d.getDeltaMovement();
        /* off a ledge of a block or less it steps down at once, as it steps up: no float, no fall loop on a staircase */
        if (d.level().isClientSide && !grounded && s.groundWas && v.y <= 0 && !d.isInLiquid()) {
            double drop = net.minecraft.world.entity.Entity.collideBoundingBox(d, new Vec3(0, -RideMath.STEP_DOWN, 0), d.getBoundingBox(), d.level(), java.util.List.of()).y;
            if (drop < 0 && drop > -RideMath.STEP_DOWN) { d.setPos(d.getX(), d.getY() + drop, d.getZ()); d.setOnGround(true); d.fallDistance = 0; grounded = true; }
        }
        if (grounded) { s.airJumps = 0; s.air = 0; s.beat = -1; } else s.air++;
        if (s.dashIn > 0) s.dashIn--;
        if (jump && !s.jumpWas) {
            if (grounded) { v = new Vec3(v.x, RideMath.JUMP + d.getJumpBoostPower(), v.z); d.hasImpulse = true; }
            else if (s.airJumps < 1 && holds(d, Trait.CLOUD_STOMPERS)) { s.airJumps++; v = new Vec3(v.x, RideMath.JUMP, v.z); d.hasImpulse = true; d.fallDistance = 0; }
        }
        if (sprint && !s.sprintWas && grounded && s.dashIn == 0 && holds(d, Trait.FOUR_WINDS)) {
            Vec3 look = rider.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() > 1e-6) { v = v.add(look.normalize().scale(RideMath.DASH)).add(0, RideMath.DASH_UP, 0); d.hasImpulse = true; s.dashIn = RideMath.DASH_COOLDOWN; }
        }
        /* Wings of Hope: the jump key held in the air. No hover and no glide: let go, and it falls */
        if (jump && !grounded && s.air > 6 && holds(d, Trait.WINGS_OF_HOPE)) {
            s.beat++;
            Vec3 up = rider.getUpVector(1);
            double[] push = RideMath.thrust(s.beat, up.x, up.y, up.z, v.y);
            if (push != null) { v = v.add(push[0], push[1], push[2]); d.fallDistance = 0; }
        } else s.beat = -1;
        /* Leap of Faith: past a plain fall's 3 blocks it falls slowly */
        if (!grounded && s.beat < 0 && v.y < -RideMath.SLOW_FALL && d.fallDistance > RideMath.SAFE_FALL && holds(d, Trait.LEAP_OF_FAITH)) v = new Vec3(v.x, -RideMath.SLOW_FALL, v.z);
        d.setDeltaMovement(v);
        s.jumpWas = jump; s.sprintWas = sprint; s.groundWas = grounded;
    }

    /** Ocean Walker and Lava Walker: ridden, it stays on top when it steps on or lands from 3 blocks or less */
    static boolean walksOn(DrekkiEntity d, FluidState f) {
        if (!d.isVehicle() || !RideMath.staysOnTop(d.fallDistance)) return false;
        return f.is(FluidTags.WATER) && holds(d, Trait.OCEAN_WALKER) || f.is(FluidTags.LAVA) && holds(d, Trait.LAVA_WALKER);
    }

    /** a dash leaves the ground for about this many ticks; in the air longer, its speed is flight */
    static final int DASH_AIR = 14;

    /** in the air on its wings: more than 8 ticks up, with Wings of Hope (rising too; not dropping fast) or Leap of Faith
     *  (any drop: the slow fall catches it). A Leap of Faith drop once read as a fall until the slow fall caught it, so a
     *  slow fall went glide, fall, glide, with the fall's landing thud and yelp in mid-air (2026-11-27) */
    static boolean glides(DrekkiEntity d, double rise) {
        return d.airSeen > 8 && (rise > -0.3 && holds(d, Trait.WINGS_OF_HOPE) || rise < 0.05 && holds(d, Trait.LEAP_OF_FAITH));
    }

    /** a glide's wind and calls: only a long drop sounds (Shane, 2026-11-27: every short hop with Leap of Faith blew the
     *  wind); Wings of Hope's flight keeps them, it is a glide at any height */
    public static final double LONG_DROP = 4;
    public static boolean glideSounds(DrekkiEntity d) { return holds(d, Trait.WINGS_OF_HOPE) || d.drop() >= LONG_DROP; }

    /** the loop of a ridden drekki (its sounds and particles; its pose in the air is DrekkiAnimations.rideAir), from what
     *  every game sees of it: its speed, the ground, its rise or fall (smoothed). In the air it is never a walk or a
     *  run, whatever its rise: level flight between wingbeats once fell through to the run (2026-11-24) */
    static Anim loop(DrekkiEntity d, double speed, double rise) {
        if (!d.onGround() && !d.isInLiquid() && d.airSeen >= 2) {
            if (speed > 12 && d.airSeen < DASH_AIR) return Anim.DASH;  /* the dash's own hop; fast flight is no dash */
            return glides(d, rise) ? Anim.GLIDE : rise >= 0 ? Anim.JUMP : Anim.FALL;
        }
        return speed > 12 ? Anim.DASH : speed < 0.15 ? Anim.IDLE : speed < 4.5 ? Anim.WALK : Anim.RUN;
    }

    /** the server's part, each tick it is ridden */
    static void serverTick(DrekkiEntity d, ServerPlayer rider) {
        /* any liquid takes the rider off (as water does a horse's), unless the drekki walks on it */
        if (d.getFluidTypeHeight(d.getMaxHeightFluidType()) >= RideMath.DEEP) { d.ejectPassengers(); return; }
        /* a slow descent is no fall: the server counts every block it sinks, wings or not */
        /* over 5 ticks: the rider's game sends its moves unevenly, and one late move must not end a real fall */
        if (d.tickCount % 5 == 0) {
            if (d.getY() - d.rideY > -RideMath.FALLING * 5) { d.fallDistance = 0; rider.fallDistance = 0; }
            d.rideY = d.getY();
        }
        if (holds(d, Trait.WINGS_OF_HOPE)) ((dev.frosthaven.ranchercompanion.mixin.VehicleFloatAccess) rider.connection).rancher$setVehicleFloat(0);
        /* Spirit Veil (Bakeneko): its rider is invisible while riding; refreshed each half second, it runs out 2 s after */
        if (d.tickCount % 10 == 0 && holds(d, Trait.SPIRIT_VEIL))
            rider.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, true, true));  /* the full effect: its swirls show too (Shane, 2026-11-26) */
        /* Predator's Eye: hostile mobs within 24 blocks glow through walls */
        if (d.tickCount % 10 == 0 && holds(d, Trait.PREDATORS_EYE))
            for (LivingEntity m : d.level().getEntitiesOfClass(LivingEntity.class, d.getBoundingBox().inflate(24), e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(d) <= 24 * 24))
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, true, false, false));
    }
}
