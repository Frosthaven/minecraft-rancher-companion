package dev.frosthaven.ranchercompanion.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * No player hurts a drekki (spec, Combat: players), nor anything a player owns or fires (decided 2026-11-22: a player
 * who hit her own drekki by accident snared it with her sword's spell, and her Wixie familiar took it as a target and
 * debuffed it). A player's melee hit is stopped before the attack: the drekki is only pushed, as by the hit's
 * knockback, with no hurt flash, no on-hit enchantment or spell, and it does not become the player's last-hurt mob
 * (which summons and pets attack). Harmful effects from a player or a player's summon, pet or projectile are refused,
 * and so are sourceless ones while a player's spell or hit lands on it (Ars Nouveau's effects and familiars give none).
 * A mob a player owns never takes a drekki as its target. Beneficial effects (a healing spell) still apply.
 */
public final class FriendlyFire {
    private FriendlyFire() {}

    /** an entity's getOwner(), per class, for summons that use neither vanilla interface (Ars Nouveau's familiars) */
    private static final Map<Class<?>, Method> OWNER = new ConcurrentHashMap<>();
    private static final Method NONE;
    static { try { NONE = Object.class.getMethod("hashCode"); } catch (NoSuchMethodException e) { throw new IllegalStateException(e); } }

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, (AttackEntityEvent e) -> {
            if (!(e.getTarget() instanceof DrekkiEntity d) || !d.owned()) return;
            e.setCanceled(true);
            Player p = e.getEntity();
            if (p.level().isClientSide) return;
            float yaw = p.getYRot() * ((float) Math.PI / 180f);
            d.knockback(p.isSprinting() ? 0.9 : 0.4, Math.sin(yaw), -Math.cos(yaw));
            if (p.isSprinting()) p.setSprinting(false);
            d.playerTouchedAt = d.tickCount;
        });
        bus.addListener((MobEffectEvent.Applicable e) -> {
            if (!(e.getEntity() instanceof DrekkiEntity d) || !d.owned() || d.level().isClientSide) return;
            if (e.getEffectInstance().getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
            Entity source = e.getEffectSource();
            if (byPlayer(source) || source == null && d.tickCount - d.playerTouchedAt <= 1)
                e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        });
        bus.addListener((LivingChangeTargetEvent e) -> {
            if (e.getNewAboutToBeSetTarget() instanceof DrekkiEntity d && d.owned() && !(e.getEntity() instanceof Player) && byPlayer(e.getEntity()))
                e.setCanceled(true);
        });
        if (ModList.get().isLoaded("ars_nouveau")) arsSpells(bus);
    }

    /** a player, or an entity a player owns or fired */
    static boolean byPlayer(Entity e) {
        if (e == null) return false;
        if (e instanceof Player) return true;
        if (e instanceof OwnableEntity o && o.getOwner() instanceof Player) return true;
        if (e instanceof TraceableEntity t && t.getOwner() instanceof Player) return true;
        Method m = OWNER.computeIfAbsent(e.getClass(), c -> {
            try {
                Method g = c.getMethod("getOwner");
                return g.getParameterCount() == 0 && Entity.class.isAssignableFrom(g.getReturnType()) ? g : NONE;
            } catch (NoSuchMethodException ex) { return NONE; }
        });
        if (m == NONE) return false;
        try { return m.invoke(e) instanceof Player; } catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }

    /** Ars Nouveau's spells resolve their effects on an entity with no source: a player's spell on a drekki is marked */
    @SuppressWarnings("unchecked")
    private static void arsSpells(IEventBus bus) {
        try {
            Class<? extends Event> pre = (Class<? extends Event>) Class.forName("com.hollingsworth.arsnouveau.api.event.EffectResolveEvent$Pre");
            Field shooter = pre.getField("shooter"), hit = pre.getField("rayTraceResult");
            bus.addListener(EventPriority.NORMAL, false, (Class<Event>) pre, e -> {
                try {
                    if (hit.get(e) instanceof EntityHitResult r && r.getEntity() instanceof DrekkiEntity d && byPlayer((Entity) shooter.get(e)))
                        d.playerTouchedAt = d.tickCount;
                } catch (IllegalAccessException ex) { /* public fields */ }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            dev.frosthaven.ranchercompanion.RancherCompanion.LOG.warn("[friendly fire] Ars Nouveau's spell event is not where it was; its debuffs on a drekki are not refused: {}", e.toString());
        }
    }
}
