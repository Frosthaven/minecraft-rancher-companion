package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What the activity traits do (the sheet's effects, spec numbers), for every player within 10 blocks of a drekki that
 * holds them (no stacking: the first ready copy acts). Cooldowns belong to the drekki. The few vanilla rules no event
 * reaches go through the mixins, which call the hooks at the bottom.
 */
public final class Effects {
    private Effects() {}

    /** Huddle Up: the game time each thawed player stops being kept unfrozen */
    private static final Map<UUID, Long> THAWED = new HashMap<>();
    /** Undertow: players being lifted to the surface */
    private static final Map<UUID, Long> LIFTING = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(MobEffectEvent.Applicable.class, Effects::balm);
        bus.addListener(LivingChangeTargetEvent.class, e -> {
            if (e.getEntity() instanceof Ghast && e.getNewAboutToBeSetTarget() instanceof Player p && Traits.near(p, Trait.HIDE_SEEK) != null) e.setCanceled(true);
        });
        bus.addListener(PlayerSpawnPhantomsEvent.class, e -> {
            if (nightWatch(e.getEntity())) e.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
        });
        bus.addListener(EntityTickEvent.Pre.class, e -> { if (e.getEntity() instanceof FishingHook h && !h.level().isClientSide) hook(h); });
        bus.addListener(PlayerTickEvent.Post.class, e -> { if (e.getEntity() instanceof ServerPlayer p) tick(p); });
        bus.addListener(net.neoforged.neoforge.event.entity.living.LivingFallEvent.class, Effects::landing);
        bus.addListener(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post.class, Effects::drowned);
        bus.addListener(net.neoforged.neoforge.event.entity.ProjectileImpactEvent.class, Effects::duck);
    }

    /** Duck & Cover: an arrow or trident (not the owner's own) about to hit its owner bounces off as from a shield; 6 s
     *  between uses, so about 1 in 3 of a skeleton's arrows on Normal */
    private static void duck(net.neoforged.neoforge.event.entity.ProjectileImpactEvent e) {
        if (!(e.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow a) || a.level().isClientSide) return;
        if (!(e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer p) || a.getOwner() == p) return;
        DrekkiEntity d = Traits.ready(p, Trait.DUCK_COVER);
        if (d == null) return;
        e.setCanceled(true);
        a.deflect(net.minecraft.world.entity.projectile.ProjectileDeflection.REVERSE, p, a.getOwner(), false);
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.8f + p.getRandom().nextFloat() * 0.4f);
        Traits.cool(d, Trait.DUCK_COVER);
    }

    /** Night Watch: 30 blocks horizontally, at any height */
    private static boolean nightWatch(Player p) {
        for (DrekkiEntity d : Traits.holders(Trait.NIGHT_WATCH))
            if (d.level() == p.level() && Math.hypot(d.getX() - p.getX(), d.getZ() - p.getZ()) <= 30) return true;
        return false;
    }

    /** Balm & Salve: a harmful effect milk would cure does not land; 15 s between uses */
    private static void balm(MobEffectEvent.Applicable e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MobEffectInstance m = e.getEffectInstance();
        if (m.getEffect().value().isBeneficial() || !m.getCures().contains(EffectCures.MILK)) return;
        DrekkiEntity d = Traits.ready(p, Trait.BALM_SALVE);
        if (d == null) return;
        e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        Traits.cool(d, Trait.BALM_SALVE);
    }

    /** Hook, Line & Sinker: each tick, a 50% chance of one extra tick off the wait for a bite */
    public static void hook(FishingHook h) {
        Player p = h.getPlayerOwner();
        if (p == null || h.getRandom().nextBoolean() || Traits.near(p, Trait.HOOK_LINE_SINKER) == null) return;
        var a = (dev.frosthaven.ranchercompanion.mixin.FishingHookAccess) h;
        if (a.rancher$timeUntilLured() > 1) a.rancher$setTimeUntilLured(a.rancher$timeUntilLured() - 1);
    }

    public static void tick(ServerPlayer p) {
        long now = p.level().getGameTime();
        /* Stamp Out: the fire out and 5 s of Fire Resistance; 2 minutes */
        if (p.isOnFire() && !p.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            DrekkiEntity d = Traits.ready(p, Trait.STAMP_OUT);
            if (d != null) { p.clearFire(); p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100)); Traits.cool(d, Trait.STAMP_OUT); }
        }
        /* Huddle Up: thawed at once and kept from freezing for 5 s; 2 minutes */
        Long thawed = THAWED.get(p.getUUID());
        if (thawed != null && thawed > now) p.setTicksFrozen(0);
        else {
            if (thawed != null) THAWED.remove(p.getUUID());
            if (p.getTicksFrozen() > 0) {  /* starting to freeze, wherever (powder snow, a mod's cold) */
                DrekkiEntity d = Traits.ready(p, Trait.HUDDLE_UP);
                if (d != null) { p.setTicksFrozen(0); THAWED.put(p.getUUID(), now + 100); Traits.cool(d, Trait.HUDDLE_UP); }
            }
        }
        /* Soft Landing: falling more than 6 blocks, Slow Falling; 2 minutes */
        if (p.fallDistance > 6 && !p.onGround() && !p.isFallFlying() && !p.getAbilities().flying && !p.hasEffect(MobEffects.SLOW_FALLING) && !p.isInWater()) {
            DrekkiEntity d = Traits.ready(p, Trait.SOFT_LANDING);
            if (d != null) { p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200)); p.fallDistance = 0; Traits.cool(d, Trait.SOFT_LANDING); }
        }
        undertow(p, now);
    }

    /** Soft Landing, at the landing itself: a fall the tick missed (the client's move packet carried the whole last
     *  stretch and the landing at once) is cushioned the same way, once per cooldown */
    private static void landing(net.neoforged.neoforge.event.entity.living.LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getDistance() <= 6) return;
        DrekkiEntity d = Traits.ready(p, Trait.SOFT_LANDING);
        if (d == null) return;
        e.setDamageMultiplier(0);
        Traits.cool(d, Trait.SOFT_LANDING);
    }

    /** Undertow, once the player has taken drowning damage: a current lifts them to open water's surface with Water
     *  Breathing; under a solid ceiling, 10 s of Water Breathing instead; 5 minutes. The lift itself runs per tick
     *  ({@link #undertow}). */
    private static void drowned(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getNewDamage() <= 0
            || !e.getSource().is(net.minecraft.world.damagesource.DamageTypes.DROWN) || LIFTING.containsKey(p.getUUID())) return;
        DrekkiEntity d = Traits.ready(p, Trait.UNDERTOW, true);
        if (d == null) return;
        Traits.cool(d, Trait.UNDERTOW);
        p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200));
        if (openAbove(p)) LIFTING.put(p.getUUID(), p.level().getGameTime() + 200);
    }

    /** Undertow's lift: up toward the surface each tick while in water, for at most 10 s */
    private static void undertow(ServerPlayer p, long now) {
        Long lifting = LIFTING.get(p.getUUID());
        if (lifting != null) {
            if (!p.isInWater() || lifting < now) { LIFTING.remove(p.getUUID()); return; }
            var v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, Math.max(v.y, 0.7), v.z);
            p.hurtMarked = true;
        }
    }

    /** water straight up to air, no solid block on the way */
    private static boolean openAbove(Player p) {
        BlockPos.MutableBlockPos m = p.blockPosition().mutable();
        for (int i = 0; i < 64; i++) {
            m.move(0, 1, 0);
            var s = p.level().getBlockState(m);
            if (s.isAir()) return true;
            if (!s.getFluidState().is(Fluids.WATER) && !s.getFluidState().is(Fluids.FLOWING_WATER) || s.blocksMotion()) return false;
        }
        return false;
    }

    /** Training Day: every 6,000 ticks, 18 XP dropped by the drekki (each drekki drops its own) */
    public static void trainingDay(DrekkiEntity d) {
        if (d.life.ghost || !d.life.traits.contains(Trait.TRAINING_DAY.id) || !(d.level() instanceof ServerLevel sl)) return;
        long now = Vitals.now(d), next = d.life.cooldowns.getOrDefault(Trait.TRAINING_DAY.id, now + Trait.TRAINING_DAY.cooldown);
        if (next > now) { d.life.cooldowns.putIfAbsent(Trait.TRAINING_DAY.id, next); return; }
        ExperienceOrb.award(sl, d.position().add(0, d.getBbHeight() * 0.5, 0), 18);
        Traits.cool(d, Trait.TRAINING_DAY);
    }

    // ---- hooks the mixins call ----

    /** The Fallen Emperor: piglins treat the owner as if wearing gold (the owner only: they killed the piglins) */
    public static boolean emperor(LivingEntity e) {
        if (!(e instanceof Player p) || p.level().isClientSide) return false;
        for (DrekkiEntity d : Traits.holders(Trait.FALLEN_EMPEROR)) if (p.getUUID().equals(d.owner())) return true;
        return false;
    }

    /** Creature Comforts: +75 reputation with villagers within 10 blocks of the drekki */
    public static int comforts(LivingEntity villager) {
        return villager.level().isClientSide || Traits.near(villager, Trait.CREATURE_COMFORTS) == null ? 0 : 75;
    }

    /** Patchwork: an item about to break is restored to 25% durability instead, once per 7 days of game time */
    public static boolean patch(ItemStack stack, LivingEntity holder) {
        if (!(holder instanceof ServerPlayer p)) return false;
        DrekkiEntity d = Traits.ready(p, Trait.PATCHWORK);
        if (d == null) { Traits.activity(p, Trait.PATCHWORK); return false; }  /* it breaks: the activity that teaches it */
        stack.setDamageValue(stack.getMaxDamage() - Math.max(1, stack.getMaxDamage() / 4));
        Traits.cool(d, Trait.PATCHWORK);
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
        return true;
    }

    public static void forget(UUID player) { THAWED.remove(player); LIFTING.remove(player); }
}
