package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The activities that teach each activity trait (the sheet's Activity Traits tab), as game events on the owner. Each
 * calls {@link Traits#activity}, which finds the learning drekki (checked out, within 10 blocks) and applies the rules.
 */
public final class Learning {
    private Learning() {}

    /** a one-off that starts and keeps going (on fire, frozen, out of air) teaches when it starts, not every tick */
    private static final Map<UUID, Set<Trait>> ONGOING = new HashMap<>();
    /** the tick a milk drink last counted, so several cured effects count once */
    private static final Map<UUID, Long> MILK = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(ItemFishedEvent.class, e -> { if (!e.getDrops().isEmpty()) Traits.activity(e.getEntity(), Trait.HOOK_LINE_SINKER); });
        bus.addListener(LivingDeathEvent.class, Learning::onDeath);
        bus.addListener(MobEffectEvent.Remove.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer p) || e.getCure() != EffectCures.MILK || e.getEffect() == null || e.getEffect().value().isBeneficial()) return;
            long now = p.level().getGameTime();
            if (MILK.getOrDefault(p.getUUID(), -1L) == now) return;
            MILK.put(p.getUUID(), now);
            Traits.activity(p, Trait.BALM_SALVE);
        });
        /* Patchwork is learned in Effects.patch, where an item breaks from wear: the game's item-destroyed event also
           fires when the last item of a stack is used up (a block placed, food eaten), which is not a break */
        bus.addListener(LivingUseTotemEvent.class, e -> { if (e.getEntity() instanceof ServerPlayer p) Traits.activity(p, Trait.PHOENIX_HEART); });
        bus.addListener(LivingFallEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getDistance() > 1.5f) bounce(p);
        });
        bus.addListener(PlayerXpEvent.LevelChange.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) for (int i = 0; i < e.getLevels(); i++) Traits.activity(p, Trait.TRAINING_DAY);
        });
        bus.addListener(TradeWithVillagerEvent.class, e -> {
            var o = e.getMerchantOffer();
            if (e.getEntity() instanceof ServerPlayer p && o.getCostA().getCount() >= o.getBaseCostA().getCount()) Traits.activity(p, Trait.CREATURE_COMFORTS);
        });
        bus.addListener(LivingDamageEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getSource().is(DamageTypeTags.IS_FALL) && e.getNewDamage() > 0) Traits.activity(p, Trait.SOFT_LANDING);
        });
        /* Duck & Cover: an arrow or trident stopped by the owner's shield */
        bus.addListener(net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getBlocked() && e.getDamageSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow)
                Traits.activity(p, Trait.DUCK_COVER);
        });
        bus.addListener(LivingHealEvent.class, e -> {
            if (e.getEntity() instanceof DrekkiEntity d && !d.level().isClientSide && !d.selfHeal && d.getHealth() < d.getMaxHealth()) heal(d);
        });
        bus.addListener(PlayerTickEvent.Post.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            if (p.tickCount % 2 == 0) ongoing(p);
            /* a landing on slime, seen from the player's tick as well: some mods take over slime landings, and the game's
               fall event then never fires (seen in the Haven pack) */
            if (p.onGround() && p.fallDistance > 1.5f) bounce(p);
        });
    }

    private static void onDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide) return;
        var killer = e.getSource().getEntity();
        if (killer instanceof ServerPlayer p) {
            if (e.getEntity() instanceof Ghast) Traits.activity(p, Trait.HIDE_SEEK);
            /* piglins, brutes and zombified piglins */
            if (e.getEntity() instanceof AbstractPiglin || e.getEntity() instanceof net.minecraft.world.entity.monster.ZombifiedPiglin) Traits.activity(p, Trait.FALLEN_EMPEROR);
            if (e.getEntity() instanceof Phantom) Traits.activity(p, Trait.NIGHT_WATCH);
        }
    }

    /** the states a player enters: catching fire, freezing, the last air bubble */
    public static void ongoing(ServerPlayer p) {
        Set<Trait> on = ONGOING.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        state(p, on, Trait.STAMP_OUT, p.isOnFire() && !p.fireImmune());
        state(p, on, Trait.HUDDLE_UP, p.isFullyFrozen());
        state(p, on, Trait.UNDERTOW, p.isUnderWater() && p.getAirSupply() <= 30);
    }

    private static void state(Player p, Set<Trait> on, Trait t, boolean now) {
        if (now && on.add(t)) Traits.activity(p, t);
        else if (!now) on.remove(t);
    }

    /** Hearty: a heal from outside the mod while it is missing health, at most once per 30 s (the sheet's row) */
    private static void heal(DrekkiEntity d) {
        long now = dev.frosthaven.ranchercompanion.life.Vitals.now(d);
        if (d.life.cooldowns.getOrDefault("hearty_heal", Long.MIN_VALUE) > now) return;
        d.life.cooldowns.put("hearty_heal", now + 600);
        Player owner = d.ownerPlayer();
        if (owner != null) Traits.activity(owner, Trait.HEARTY);
    }

    /** Cloud Stompers: a bounce on slime (not sneaking), counted once per landing whichever way it was seen */
    private static void bounce(ServerPlayer p) {
        if (p.isSuppressingBounce() || !p.level().getBlockState(p.getOnPos()).is(Blocks.SLIME_BLOCK)) return;
        long now = p.level().getGameTime();
        if (BOUNCED.getOrDefault(p.getUUID(), Long.MIN_VALUE) >= now - 5) return;
        BOUNCED.put(p.getUUID(), now);
        Traits.activity(p, Trait.CLOUD_STOMPERS);
    }

    /** the game time of each player's last counted slime landing */
    private static final Map<UUID, Long> BOUNCED = new HashMap<>();

    public static void forget(UUID player) { ONGOING.remove(player); MILK.remove(player); BOUNCED.remove(player); WorldTraits.forget(player); RoamTraits.forget(player); }
}
