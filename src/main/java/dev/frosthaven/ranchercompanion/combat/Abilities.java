package dev.frosthaven.ranchercompanion.combat;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.BardingEnchants;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.look.LeapMath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A Mature's cooldown ability, one by diet (spec, Combat): Raw the Pounce, Cooked the Elemental breath, Seed the Steel
 * edge, Root Rejuvenate. One 15 s cooldown, half in its astral home terrain. A ghost has none. Pounce and breath are
 * attacks (CombatGoals.Attack starts them); Steel edge and Rejuvenate are support and work in every stance.
 */
public final class Abilities {
    private Abilities() {}

    public static final int COOLDOWN = 300, EDGE_TICKS = 100, BREATH_AT = 14;
    /** the Pounce by the tick, as its loop on the page: a crouch, the leap, the landing with the bite, the jaws shut */
    public static final int POUNCE_CROUCH = 2, POUNCE_LANDS = 9, POUNCE_ENDS = 15;
    public static final double POUNCE_RANGE = 5, BREATH_RANGE = 4, SUPPORT_RANGE = 10;
    public static final float POUNCE = CombatGoals.BITE * 2, BREATH = 4, EDGE = 2, HEAL = 4;
    private static final ResourceLocation EDGE_ID = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "steel_edge");
    /** who has the Steel edge, and the game time it ends */
    private static final Map<UUID, Long> EDGED = new HashMap<>();

    public static boolean ready(DrekkiEntity d) {
        return d.stage() >= 3 && d.owned() && d.isAlive() && !d.life.ghost && !d.isSleepingLoop() && Vitals.now(d) >= d.abilityAt;
    }

    private static void spend(DrekkiEntity d) { d.abilityAt = Vitals.now(d) + (Vitals.atHome(d) ? COOLDOWN / 2 : COOLDOWN); }

    /** the Pounce begins: the crouch. The leap follows POUNCE_CROUCH ticks later (leap), the bite at POUNCE_LANDS for
     *  twice a bite; the loop is over at POUNCE_ENDS, and a bite can follow at once */
    static void pounce(DrekkiEntity d, LivingEntity t) {
        spend(d);
        d.act(Anim.POUNCE, POUNCE_ENDS);
        d.getNavigation().stop();
        d.playSound(dev.frosthaven.ranchercompanion.DrekkiSounds.BITE2.get(), 0.8f, 0.8f);
    }

    /** the leap: launched to land at biting reach of the target, where it is now, when the bite lands. The drekki
     *  itself flies there (its loop draws no leap of its own on a live one), so what is seen is where it is */
    static void leap(DrekkiEntity d, LivingEntity t) {
        Vec3 to = t.position().subtract(d.position());
        double h = Math.hypot(to.x, to.z), dist = Math.max(0, h - (d.getBbWidth() / 2 + t.getBbWidth() / 2 + 0.3));
        var under = d.getBlockPosBelowThatAffectsMyMovement();
        double ground = d.onGround() ? d.level().getBlockState(under).getFriction(d.level(), under, d) * LeapMath.AIR : LeapMath.AIR;
        double[] v = LeapMath.launch(dist, Math.max(-3, Math.min(2, to.y)), POUNCE_LANDS - POUNCE_CROUCH, ground);
        Vec3 dir = h > 1e-4 ? new Vec3(to.x / h, 0, to.z / h) : Vec3.ZERO;
        d.getNavigation().stop();
        d.setDeltaMovement(dir.scale(v[0]).add(0, Math.min(v[1], 0.8), 0));
        d.hasImpulse = true;
    }

    /** the Elemental breath: a cone ahead, 4 blocks; it hits BREATH_AT ticks into the loop */
    static void breath(DrekkiEntity d) {
        spend(d);
        d.act(Anim.BREATH, 60);
        d.breathIn = BREATH_AT;
    }

    private static void breathHits(DrekkiEntity d) {
        Vec3 ahead = Vec3.directionFromRotation(0, d.yBodyRot);
        for (LivingEntity t : d.level().getEntitiesOfClass(LivingEntity.class, d.getBoundingBox().inflate(BREATH_RANGE), e -> CombatGoals.fair(d, e))) {
            Vec3 to = t.position().subtract(d.position());
            double dist = to.multiply(1, 0, 1).length();
            if (dist > BREATH_RANGE + t.getBbWidth() / 2 || Math.abs(to.y) > 2.5) continue;
            if (dist > 0.8 && to.multiply(1, 0, 1).normalize().dot(ahead) < 0.7) continue;  /* a cone of about 45 degrees a side */
            if (!(t instanceof Player) && !(t instanceof net.minecraft.world.entity.monster.Enemy)) continue;
            BardingEnchants.strike(d, t, d.damageSources().indirectMagic(d, d), BREATH, false);
        }
    }

    /** each server tick of an owned Mature: the breath's hit, and the support abilities when they are of use */
    public static void tick(DrekkiEntity d) {
        if (d.breathIn > 0 && --d.breathIn == 0 && d.isAlive()) breathHits(d);
        if (d.tickCount % 10 != 0 || !ready(d)) return;
        Diet diet = Diet.of(d.diet());
        if (diet != Diet.SEED && diet != Diet.ROOT) return;
        var players = d.level().getEntitiesOfClass(Player.class, d.getBoundingBox().inflate(SUPPORT_RANGE), p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(d) <= SUPPORT_RANGE * SUPPORT_RANGE);
        if (diet == Diet.SEED) {
            /* Steel edge: +2 attack damage for every player within 10 blocks, 5 s; when one of them is in a fight */
            boolean fight = false;
            for (Player p : players) fight |= p.getLastHurtMob() != null && p.tickCount - p.getLastHurtMobTimestamp() < 100 || p.getLastHurtByMob() != null && p.tickCount - p.getLastHurtByMobTimestamp() < 100;
            if (!fight || players.isEmpty()) return;
            spend(d);
            d.act(Anim.GUARD, 60);
            long until = Vitals.now(d) + EDGE_TICKS;
            for (Player p : players) {
                var a = p.getAttribute(Attributes.ATTACK_DAMAGE);
                if (a == null) continue;
                if (!a.hasModifier(EDGE_ID)) a.addTransientModifier(new AttributeModifier(EDGE_ID, EDGE, AttributeModifier.Operation.ADD_VALUE));
                EDGED.put(p.getUUID(), until);
            }
        } else {
            /* Rejuvenate: heals itself and every player within 10 blocks, then very slow regeneration for 5 s; when one is hurt */
            boolean hurt = d.getHealth() <= d.getMaxHealth() - HEAL;
            for (Player p : players) hurt |= p.getHealth() <= p.getMaxHealth() - HEAL;
            if (!hurt) return;
            spend(d);
            d.act(Anim.REJUV, 60);
            Vitals.ownHeal(d, HEAL);
            d.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EDGE_TICKS, 0, true, true));
            for (Player p : players) { p.heal(HEAL); p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, EDGE_TICKS, 0, true, true)); }
        }
    }

    /** the Steel edge wears off */
    public static void serverTick(MinecraftServer server) {
        if (EDGED.isEmpty() || server.getTickCount() % 5 != 0) return;
        long now = server.overworld().getGameTime();
        EDGED.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) { var a = p.getAttribute(Attributes.ATTACK_DAMAGE); if (a != null) a.removeModifier(EDGE_ID); }
            return true;
        });
    }

    public static boolean edged(Player p) { var a = p.getAttribute(Attributes.ATTACK_DAMAGE); return a != null && a.hasModifier(EDGE_ID); }
}
