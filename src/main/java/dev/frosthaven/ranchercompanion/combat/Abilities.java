package dev.frosthaven.ranchercompanion.combat;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.BardingEnchants;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Diet;
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

    public static final int COOLDOWN = 300, EDGE_TICKS = 100, BREATH_AT = 14, POUNCE_LANDS = 9;
    public static final double POUNCE_RANGE = 5, BREATH_RANGE = 4, SUPPORT_RANGE = 10;
    public static final float POUNCE = CombatGoals.BITE * 2, BREATH = 4, EDGE = 2, HEAL = 4;
    private static final ResourceLocation EDGE_ID = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "steel_edge");
    /** who has the Steel edge, and the game time it ends */
    private static final Map<UUID, Long> EDGED = new HashMap<>();

    public static boolean ready(DrekkiEntity d) {
        return d.stage() >= 3 && d.owned() && d.isAlive() && !d.life.ghost && !d.isSleepingLoop() && Vitals.now(d) >= d.abilityAt;
    }

    private static void spend(DrekkiEntity d) { d.abilityAt = Vitals.now(d) + (Vitals.atHome(d) ? COOLDOWN / 2 : COOLDOWN); }

    /** the Pounce: a leap at a target up to 5 blocks away; the bite lands POUNCE_LANDS ticks later for twice a bite */
    static void pounce(DrekkiEntity d, LivingEntity t) {
        spend(d);
        d.act(Anim.POUNCE, 48);
        Vec3 to = t.position().subtract(d.position()).multiply(1, 0, 1);
        double h = to.length();
        if (h > 1.5) { d.setDeltaMovement(to.scale(Math.min(0.9, (h - 1) / POUNCE_LANDS * 1.6) / h).add(0, 0.42, 0)); d.hasImpulse = true; }
        d.playSound(dev.frosthaven.ranchercompanion.DrekkiSounds.BITE2.get(), 0.8f, 0.8f);
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
