package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.combat.CombatGoals;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.trait.Traits;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Enchantments on barding (spec, Barding: enchantments): it takes what a chestplate takes and what a sword takes, at an
 * enchanting table or on an anvil (the item tags enchantable/chest_armor and enchantable/sword). A drekki does not wear
 * its barding in one of the game's equipment slots, and barding soaks a hit into its durability, so the game's own
 * enchantment effects do not reach it; each enchantment's effect is given here, as close to the game's as barding allows.
 * Unbreaking alone needs nothing: the game applies it wherever an item loses durability.
 */
public final class BardingEnchants {
    private BardingEnchants() {}

    public static void register(IEventBus bus) { bus.addListener(PlayerXpEvent.PickupXp.class, BardingEnchants::mend); }

    public static int level(ItemStack stack, ResourceKey<Enchantment> key) {
        for (var e : stack.getEnchantments().entrySet()) if (e.getKey().is(key)) return e.getIntValue();
        return 0;
    }

    // ---- the armor's: what the barding soaks up ----

    /** the durability a hit of this size costs the barding */
    public static int wear(ItemStack barding, DamageSource s, float amount) {
        float g = dev.frosthaven.ranchercompanion.look.EnchantMath.guard(level(barding, Enchantments.PROTECTION), level(barding, Enchantments.FIRE_PROTECTION), level(barding, Enchantments.BLAST_PROTECTION),
            level(barding, Enchantments.PROJECTILE_PROTECTION), s.is(DamageTypeTags.IS_FIRE), s.is(DamageTypeTags.IS_EXPLOSION), s.is(DamageTypeTags.IS_PROJECTILE));
        return Math.max(1, net.minecraft.util.Mth.ceil(amount * (1 - g)));
    }

    /** Thorns: 15% a level that a mob's melee hit costs it 1 to 4 health, and the barding 2 more durability, as the
     *  game's. The durability it costs, 0 when it did not fire. */
    public static int thorns(DrekkiEntity d, DamageSource s) {
        int lvl = level(d.bardingItem(), Enchantments.THORNS);
        if (lvl <= 0 || !(s.getEntity() instanceof LivingEntity attacker) || attacker instanceof Player || attacker != s.getDirectEntity()
            || s.is(net.minecraft.world.damagesource.DamageTypes.THORNS) || d.getRandom().nextFloat() >= 0.15f * lvl) return 0;
        attacker.hurt(d.damageSources().thorns(d), 1 + d.getRandom().nextInt(4));
        return 2;
    }

    /** Curse of Binding: once worn, the barding stays on until it breaks (not for a player in creative) */
    public static boolean bound(ItemStack barding, Player p) { return !p.hasInfiniteMaterials() && level(barding, Enchantments.BINDING_CURSE) > 0; }

    /** Curse of Vanishing: barding that is gone with its drekki, not left in the tombstone */
    public static boolean vanishes(ItemStack barding) { return level(barding, Enchantments.VANISHING_CURSE) > 0; }

    /** Mending: experience its owner picks up within 10 blocks of it repairs the barding first, 2 durability a point, and
     *  only what is left of the orb goes to the owner */
    private static void mend(PlayerXpEvent.PickupXp e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        DrekkiEntity d = Traits.learner(p);
        if (d == null) return;
        ItemStack b = d.bardingItem();
        var orb = e.getOrb();
        if (b.isEmpty() || !b.isDamaged() || level(b, Enchantments.MENDING) <= 0 || orb.getValue() <= 0) return;
        int repair = Math.min(b.getDamageValue(), orb.getValue() * 2);
        b.setDamageValue(b.getDamageValue() - repair);
        ((dev.frosthaven.ranchercompanion.mixin.ExperienceOrbAccess) orb).rancher$setValue(orb.getValue() - (repair + 1) / 2);
        Ranch.touch(d);
    }

    // ---- the sword's: what its attacks do ----

    /**
     * One attack of the drekki's, a bite or the Elemental ball: the hit with its barding's sharpness, then Fire Aspect, and
     * for a bite Knockback and Sweeping Edge. While the hit lands the barding stands in for a held weapon, so a kill's
     * loot counts its Looting.
     */
    public static boolean strike(DrekkiEntity d, LivingEntity t, DamageSource source, float base, boolean bite) {
        ItemStack b = d.bardingItem();
        float damage = base + dev.frosthaven.ranchercompanion.look.EnchantMath.bonus(level(b, Enchantments.SHARPNESS), level(b, Enchantments.SMITE), level(b, Enchantments.BANE_OF_ARTHROPODS),
            t.getType().is(EntityTypeTags.SENSITIVE_TO_SMITE), t.getType().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS));
        boolean hit;
        d.striking = true;
        try { hit = t.hurt(source, damage); } finally { d.striking = false; }
        if (!hit || b.isEmpty()) return hit;
        int fire = level(b, Enchantments.FIRE_ASPECT);
        if (fire > 0) t.igniteForSeconds(4 * fire);
        if (!bite) return true;
        int knock = level(b, Enchantments.KNOCKBACK);
        if (knock > 0) t.knockback(0.5 * knock, d.getX() - t.getX(), d.getZ() - t.getZ());
        float share = dev.frosthaven.ranchercompanion.look.EnchantMath.sweep(level(b, Enchantments.SWEEPING_EDGE));
        if (share > 0)
            for (LivingEntity o : d.level().getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(1.5, 0.25, 1.5), o -> o != t && o instanceof Enemy && CombatGoals.fair(d, o))) {
                d.striking = true;
                try { o.hurt(source, damage * share); } finally { d.striking = false; }
            }
        return true;
    }
}
