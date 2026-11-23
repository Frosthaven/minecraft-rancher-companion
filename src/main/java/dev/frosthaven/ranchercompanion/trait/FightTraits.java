package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.combat.CombatGoals;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Vitals;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The combat traits of 0.5.0 (spec, Traits; the sheet's Activity Traits tab): Provoke, Fade, Barbed Coat, Point Break and
 * Marked. How each is learned and what it does, side by side. Provoke, Fade and Point Break are a drekki's own doing; the
 * drekki still learns only within 10 blocks of its owner, as with every trait.
 */
public final class FightTraits {
    private FightTraits() {}

    /** Provoke and Fade last 8 s; their 16 s cooldown runs from the start */
    static final int HOLD = 160;
    public static final float BARBED = 0.25f, MARKED = 1.15f, BLAST = 3;

    /** Provoke: the mobs a drekki has hit under an attack order while it counts toward the trait, by drekki */
    private static final Map<UUID, Set<UUID>> PROVOKED = new HashMap<>();
    /** Provoke: the mob each drekki holds, and until which game time */
    private record Taunt(Mob mob, long until) {}
    private static final Map<UUID, Taunt> TAUNTS = new HashMap<>();
    /** Fade: the game time until which no mob targets each drekki */
    private static final Map<UUID, Long> FADED = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(LivingDamageEvent.Post.class, FightTraits::hit);
        bus.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, FightTraits::incoming);
        bus.addListener(LivingChangeTargetEvent.class, FightTraits::retarget);
        bus.addListener(ServerTickEvent.Post.class, e -> { if (e.getServer().getTickCount() % 10 == 0) tick(); });
    }

    public static void clear() { PROVOKED.clear(); TAUNTS.clear(); FADED.clear(); }

    /** the live drekki with this entity id, or null */
    private static DrekkiEntity live(UUID entity) {
        for (DrekkiEntity d : dev.frosthaven.ranchercompanion.ranch.Ranch.lives()) if (d.getUUID().equals(entity) && !d.isRemoved()) return d;
        return null;
    }

    private static boolean has(DrekkiEntity d, Trait t) { return d.owned() && !d.life.ghost && d.life.traits.contains(t.id); }

    private static boolean ready(DrekkiEntity d, Trait t) { return d.life.cooldowns.getOrDefault(t.id, Long.MIN_VALUE) <= Vitals.now(d); }

    // ---- damage that landed ----

    private static void hit(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || e.getNewDamage() <= 0) return;
        DamageSource s = e.getSource();
        /* Provoke: the drekki's hit on the mob its owner's whistle sent it at */
        if (s.getEntity() instanceof DrekkiEntity d && d.ordered == victim && victim instanceof Mob mob) {
            if (has(d, Trait.PROVOKE)) taunt(d, mob);
            else if (d.ownerPlayer() != null && PROVOKED.computeIfAbsent(d.getUUID(), k -> new HashSet<>()).add(mob.getUUID()))
                Traits.activity(d.ownerPlayer(), Trait.PROVOKE);  /* a different mob each time */
        }
        /* Marked: the owner's arrow from 30 blocks or more */
        if (s.getDirectEntity() instanceof AbstractArrow && s.getEntity() instanceof ServerPlayer p && p.distanceToSqr(victim) >= 30 * 30)
            Traits.activity(p, Trait.MARKED);
        /* Barbed Coat: the owner hurt by spikes */
        if (victim instanceof ServerPlayer p && spikes(s)) Traits.activity(p, Trait.BARBED_COAT);
    }

    /** damage by spikes: the damage type tag rancher_companion:spikes (the game's cactus, berry bush, thorns and
     *  dripstone types; Biomes O' Plenty's bramble, which has a type of its own), and a pufferfish's sting */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> SPIKES = net.minecraft.tags.TagKey.create(
        net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.frosthaven.ranchercompanion.RancherCompanion.ID, "spikes"));

    static boolean spikes(DamageSource s) { return s.is(SPIKES) || s.getEntity() instanceof net.minecraft.world.entity.animal.Pufferfish; }

    // ---- damage on its way ----

    private static void incoming(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || e.getAmount() <= 0) return;
        DamageSource s = e.getSource();
        /* Marked: the mob a holder was ordered to attack takes 15% more from every source */
        for (DrekkiEntity d : Traits.holders(Trait.MARKED))
            if (d.ordered == victim && d.isAlive()) { e.setAmount(e.getAmount() * MARKED); break; }
        if (!(victim instanceof DrekkiEntity d) || !d.owned() || d.life.ghost) return;
        Entity attacker = s.getEntity();
        boolean mobMelee = attacker instanceof LivingEntity && !(attacker instanceof Player) && attacker == s.getDirectEntity()
            && !s.is(DamageTypes.THORNS) && !s.is(DamageTypeTags.IS_EXPLOSION) && !s.is(DamageTypeTags.IS_PROJECTILE);
        if (!mobMelee) return;
        /* Fade is learned from a hit taken at or under half health */
        if (d.getHealth() <= d.getMaxHealth() / 2 && d.ownerPlayer() != null) Traits.activity(d.ownerPlayer(), Trait.FADE);
        /* Barbed Coat: a quarter of the hit goes back, at least half a heart, also when its barding takes the hit */
        if (has(d, Trait.BARBED_COAT)) attacker.hurt(d.damageSources().thorns(d), Math.max(1, e.getAmount() * BARBED));
    }

    // ---- Provoke ----

    /** the ordered mob can only target the drekki for 8 s; one at a time; any mob, a boss too */
    private static void taunt(DrekkiEntity d, Mob mob) {
        if (TAUNTS.containsKey(d.getUUID()) || !ready(d, Trait.PROVOKE)) return;
        TAUNTS.put(d.getUUID(), new Taunt(mob, Vitals.now(d) + HOLD));
        Traits.cool(d, Trait.PROVOKE);
        hold(d, mob);
        Vitals.burst(d, 0xE05A3A);
    }

    /** puts the mob on the drekki, for mobs that keep a target and for those that keep one in their brain */
    private static void hold(DrekkiEntity d, Mob mob) {
        if (mob.getTarget() != d) mob.setTarget(d);
        if (mob instanceof net.minecraft.world.entity.monster.warden.Warden w) {
            w.increaseAngerAt(d, 150, false);
            w.setAttackTarget(d);
        } else if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, d);
        }
    }

    private static DrekkiEntity taunter(Mob mob) {
        for (var en : TAUNTS.entrySet())
            if (en.getValue().mob == mob) return live(en.getKey());
        return null;
    }

    // ---- Fade ----

    public static boolean faded(DrekkiEntity d) { return FADED.getOrDefault(d.getUUID(), Long.MIN_VALUE) > Vitals.now(d) && has(d, Trait.FADE); }

    private static void retarget(LivingChangeTargetEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        LivingEntity to = e.getNewAboutToBeSetTarget();
        /* Provoke: a held mob takes no other target, nor none */
        DrekkiEntity holder = taunter(mob);
        if (holder != null && to != holder) { e.setNewAboutToBeSetTarget(holder); return; }
        if (!(to instanceof DrekkiEntity d) || holder == d) return;
        if (faded(d)) { e.setNewAboutToBeSetTarget(other(d, mob)); return; }
        if (!has(d, Trait.FADE) || !ready(d, Trait.FADE)) return;
        LivingEntity alt = other(d, mob);
        if (alt == null) return;  /* only the drekki is in this fight: not spent */
        FADED.put(d.getUUID(), Vitals.now(d) + HOLD);
        Traits.cool(d, Trait.FADE);
        e.setNewAboutToBeSetTarget(alt);
        Taunt held = TAUNTS.get(d.getUUID());
        if (mob.level() instanceof ServerLevel sl)
            for (Mob m : sl.getEntitiesOfClass(Mob.class, d.getBoundingBox().inflate(48), m -> m != mob && m.getTarget() == d && (held == null || held.mob != m)))
                m.setTarget(other(d, m));
        Vitals.burst(d, 0x8FA0C8);
    }

    /** who else this mob could fight: whoever hit it in the last 5 s, else the drekki's owner or another of the owner's
     *  fighting animals within the mob's sight range; null when only the drekki is there */
    public static LivingEntity other(DrekkiEntity d, Mob mob) {
        LivingEntity by = mob.getLastHurtByMob();
        if (by != null && by != d && by.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100 && !(by instanceof Player p && (p.isCreative() || p.isSpectator()))) return by;
        var range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        double r = range == null ? 16 : range.getValue();
        Player owner = d.ownerPlayer();
        if (owner != null && owner.isAlive() && !owner.isCreative() && !owner.isSpectator() && owner.level() == mob.level() && owner.distanceToSqr(mob) <= r * r) return owner;
        if (d.owner() == null) return null;
        for (LivingEntity pet : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(r), x -> x != d && x.isAlive()
            && (x instanceof DrekkiEntity o ? d.owner().equals(o.owner()) && CombatGoals.fights(o) : x instanceof OwnableEntity own && d.owner().equals(own.getOwnerUUID()) && x instanceof Mob pm && pm.getTarget() == mob)))
            return pet;
        return null;
    }

    // ---- twice a second ----

    private static void tick() {
        /* Provoke ends when its time is up, the mob or the drekki is gone, or the order is off (the whistle sent the
           drekki elsewhere, Stay, Passive: all drop the order) */
        TAUNTS.entrySet().removeIf(en -> {
            DrekkiEntity d = live(en.getKey());
            Taunt t = en.getValue();
            boolean on = d != null && d.isAlive() && !d.life.ghost && t.mob.isAlive() && !t.mob.isRemoved() && d.ordered == t.mob && Vitals.now(d) < t.until && d.level() == t.mob.level();
            if (on) hold(d, t.mob);
            else if (t.mob.isAlive() && t.mob.getTarget() == d && d != null && d.ordered != t.mob) t.mob.setTarget(null);
            return !on;
        });
        FADED.entrySet().removeIf(en -> {
            DrekkiEntity d = live(en.getKey());
            return d == null || !faded(d);
        });
        /* Marked: the ordered mob glows while the order holds */
        for (DrekkiEntity d : Traits.holders(Trait.MARKED))
            if (d.ordered != null && d.ordered.isAlive() && d.isAlive()) d.ordered.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
        PROVOKED.keySet().removeIf(id -> live(id) == null);
    }

    // ---- Point Break ----

    /** from DrekkiEntity, as a hit breaks its barding */
    public static void bardingBroke(DrekkiEntity d, DamageSource s) {
        if (!d.owned() || d.life.ghost) return;
        if (has(d, Trait.POINT_BREAK)) {
            if (ready(d, Trait.POINT_BREAK)) blast(d);
        } else if (s.is(DamageTypeTags.IS_EXPLOSION) && (creeper(s.getEntity()) || creeper(s.getDirectEntity()))) {
            if (d.ownerPlayer() != null) Traits.activity(d.ownerPlayer(), Trait.POINT_BREAK);
        }
    }

    /** a creeper of the game's, or of a mod that lists its own in the entity tag rancher_companion:creepers */
    static boolean creeper(Entity e) {
        return e instanceof Creeper || e != null && e.getType().is(CREEPERS);
    }

    public static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> CREEPERS = net.minecraft.tags.TagKey.create(
        net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.frosthaven.ranchercompanion.RancherCompanion.ID, "creepers"));

    /** a creeper's blast around the drekki that breaks no block and hurts and throws only what the drekki would fight */
    private static void blast(DrekkiEntity d) {
        Traits.cool(d, Trait.POINT_BREAK);
        ExplosionDamageCalculator only = new ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(net.minecraft.world.level.Explosion ex, Entity e) { return foe(d, e); }
            @Override
            public float getKnockbackMultiplier(Entity e) { return foe(d, e) ? 1 : 0; }
        };
        d.level().explode(d, net.minecraft.world.level.Explosion.getDefaultDamageSource(d.level(), d), only, d.getX(), d.getY(0.5), d.getZ(), BLAST, false, Level.ExplosionInteraction.NONE);
    }

    private static boolean foe(DrekkiEntity d, Entity e) {
        if (!(e instanceof LivingEntity l) || !(e instanceof Enemy) || !l.isAlive()) return false;
        return !(e instanceof OwnableEntity o && d.owner() != null && d.owner().equals(o.getOwnerUUID()));
    }
}
