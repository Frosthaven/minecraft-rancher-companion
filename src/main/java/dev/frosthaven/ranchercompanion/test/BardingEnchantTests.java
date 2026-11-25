package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.Barding;
import dev.frosthaven.ranchercompanion.item.BardingEnchants;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Diet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Enchantments on barding (spec, Barding: enchantments): what it accepts, and what each one does on a drekki. */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class BardingEnchantTests {
    private BardingEnchantTests() {}

    private static ItemStack barding(GameTestHelper t, Object... enchants) {
        ItemStack b = GearTests.barding(Barding.DIAMOND);
        var reg = t.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < enchants.length; i += 2) {
            @SuppressWarnings("unchecked") ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) enchants[i];
            b.enchant(reg.getOrThrow(key), (int) enchants[i + 1]);
        }
        return b;
    }

    private static DrekkiEntity fledgling(GameTestHelper t, ServerPlayer p, ItemStack worn) {
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.SEED, Life.Stance.PASSIVE);
        d.setBardingItem(worn);
        return d;
    }

    /** the wear arithmetic at one durability point per point (a Mature: on a Fledgling each point costs more, see
     *  GearTests.aFledglingWearsTheBardingFaster) */
    private static DrekkiEntity mature(GameTestHelper t, ServerPlayer p, ItemStack worn) {
        DrekkiEntity d = CombatTests.out(t, p, 3, Diet.SEED, Life.Stance.PASSIVE);
        d.setBardingItem(worn);
        return d;
    }

    private static Husk husk(GameTestHelper t, int dx, int dz) { return CombatTests.zombie(t, LifeTests.H.offset(dx, 0, dz)); }

    @GameTest(template = "floor")
    public static void bardingTakesAChestplatesAndASwordsEnchantments(GameTestHelper t) {
        var reg = t.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack fledgling = GearTests.barding(Barding.IRON), mature = new ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.BARDING.get(Barding.NETHERITE).get());
        var yes = java.util.List.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION, Enchantments.THORNS,
            Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.BINDING_CURSE, Enchantments.VANISHING_CURSE, Enchantments.SHARPNESS, Enchantments.SMITE,
            Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT, Enchantments.LOOTING, Enchantments.SWEEPING_EDGE);
        for (var key : yes) for (ItemStack s : new ItemStack[]{fledgling, mature})
            t.assertTrue(reg.getOrThrow(key).value().canEnchant(s), key.location() + " goes on " + s.getItem());
        for (var key : java.util.List.of(Enchantments.FEATHER_FALLING, Enchantments.RESPIRATION, Enchantments.POWER, Enchantments.EFFICIENCY, Enchantments.DENSITY))
            t.assertFalse(reg.getOrThrow(key).value().canEnchant(fledgling), key.location() + " does not");
        t.assertTrue(fledgling.isEnchantable() && fledgling.getEnchantmentValue() > 0, "an enchanting table takes it");
        var offered = net.minecraft.world.item.enchantment.EnchantmentHelper.selectEnchantment(t.getLevel().getRandom(), fledgling, 30,
            reg.get(net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow().stream());
        t.assertFalse(offered.isEmpty(), "and offers enchantments for it");
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void theProtectionsCutWhatAHitCostsTheBarding(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, barding(t));
        Husk z = husk(t, -3, 3);
        d.hurt(d.damageSources().mobAttack(z), 10);
        t.assertTrue(d.bardingItem().getDamageValue() == 10 && d.getHealth() == d.getMaxHealth(), "plain barding: 10 durability for a hit of 10");
        d.setBardingItem(barding(t, Enchantments.PROTECTION, 4)); d.invulnerableTime = 0;
        d.hurt(d.damageSources().mobAttack(z), 10);
        t.assertTrue(d.bardingItem().getDamageValue() == 9, "Protection IV: 9 (16% off, rounded up), got " + d.bardingItem().getDamageValue());
        d.setBardingItem(barding(t, Enchantments.BLAST_PROTECTION, 4)); d.invulnerableTime = 0;
        d.hurt(d.damageSources().explosion(null, null), 10);
        t.assertTrue(d.bardingItem().getDamageValue() == 7, "Blast Protection IV against a blast: 7, got " + d.bardingItem().getDamageValue());
        d.setBardingItem(barding(t, Enchantments.BLAST_PROTECTION, 4)); d.invulnerableTime = 0;
        d.hurt(d.damageSources().mobAttack(z), 10);
        t.assertTrue(d.bardingItem().getDamageValue() == 10, "and nothing off a bite");
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void thornsUnbreakingMendingAndTheGlint(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, barding(t));
        t.assertFalse(d.bardingGlint(), "plain barding has no glint");
        Husk z = husk(t, -3, 3);
        d.setBardingItem(barding(t, Enchantments.THORNS, 3));
        t.assertTrue(d.bardingGlint(), "enchanted barding glints");
        float full = z.getHealth();
        for (int i = 0; i < 40; i++) { d.invulnerableTime = 0; z.invulnerableTime = 0; d.hurt(d.damageSources().mobAttack(z), 1); }
        t.assertTrue(z.getHealth() < full && d.bardingItem().getDamageValue() > 40, "Thorns III hits back and costs the barding more (husk " + z.getHealth() + ", wear " + d.bardingItem().getDamageValue() + ")");
        d.setBardingItem(barding(t, Enchantments.UNBREAKING, 3));
        for (int i = 0; i < 60; i++) { d.invulnerableTime = 0; d.hurt(d.damageSources().mobAttack(z), 1); }
        t.assertTrue(d.bardingItem().getDamageValue() < 60, "Unbreaking III spares some wear: " + d.bardingItem().getDamageValue() + " of 60");
        ItemStack worn = barding(t, Enchantments.MENDING, 1);
        worn.setDamageValue(20);
        d.setBardingItem(worn);
        int before = p.totalExperience;
        p.takeXpDelay = 0;
        ExperienceOrb orb = new ExperienceOrb(t.getLevel(), p.getX(), p.getY(), p.getZ(), 7);
        t.getLevel().addFreshEntity(orb);
        orb.playerTouch(p);
        t.assertTrue(d.bardingItem().getDamageValue() == 6 && p.totalExperience == before, "Mending: 7 experience repairs 14 durability and none is left for the owner (wear " + d.bardingItem().getDamageValue() + ", gained " + (p.totalExperience - before) + ")");
        p.takeXpDelay = 0;
        ExperienceOrb more = new ExperienceOrb(t.getLevel(), p.getX(), p.getY(), p.getZ(), 7);
        t.getLevel().addFreshEntity(more);
        more.playerTouch(p);
        t.assertTrue(d.bardingItem().getDamageValue() == 0 && p.totalExperience == before + 4, "the next orb repairs the last 6 and gives its owner the other 4 points (gained " + (p.totalExperience - before) + ")");
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void aSwordsEnchantmentsWorkOnItsBite(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, barding(t));
        Husk plain = husk(t, -3, 3);
        float full = plain.getHealth();
        BardingEnchants.strike(d, plain, d.damageSources().mobAttack(d), 4, true);
        float base = full - plain.getHealth();
        t.assertTrue(!plain.isOnFire(), "a plain bite sets nothing on fire");
        d.setBardingItem(barding(t, Enchantments.SHARPNESS, 5, Enchantments.FIRE_ASPECT, 2, Enchantments.KNOCKBACK, 2, Enchantments.SWEEPING_EDGE, 3));
        Husk hit = husk(t, 3, -3), beside = husk(t, 4, -3), far = husk(t, -3, -3);
        BardingEnchants.strike(d, hit, d.damageSources().mobAttack(d), 4, true);
        float sharp = full - hit.getHealth();
        t.assertTrue(sharp > base + 2.5f, "Sharpness V adds 3 to the bite (" + base + " to " + sharp + ")");
        t.assertTrue(hit.isOnFire() && hit.getDeltaMovement().horizontalDistance() > 0.1, "Fire Aspect lights the target and Knockback throws it");
        t.assertTrue(beside.getHealth() < full && far.getHealth() == full, "Sweeping Edge hurts the mob beside the target, not one across the floor");
        d.setBardingItem(barding(t, Enchantments.SMITE, 5));
        Husk undead = husk(t, -3, 0);
        BardingEnchants.strike(d, undead, d.damageSources().mobAttack(d), 4, true);
        t.assertTrue(full - undead.getHealth() > base + 10, "Smite V adds 12.5 against the undead");
        var spider = t.spawn(EntityType.SPIDER, LifeTests.H.offset(0, 0, -4));
        spider.setNoAi(true);
        float spiderFull = spider.getHealth();
        BardingEnchants.strike(d, spider, d.damageSources().mobAttack(d), 4, true);
        t.assertTrue(spiderFull - spider.getHealth() < 4.5f, "and nothing against a spider");
        t.succeed();
    }

    private static int flesh(GameTestHelper t) {
        int n = 0;
        for (ItemEntity e : t.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(t.absolutePos(LifeTests.H)).inflate(16)))
            if (e.getItem().is(Items.ROTTEN_FLESH)) { n += e.getItem().getCount(); e.discard(); }
        return n;
    }

    @GameTest(template = "floor")
    public static void lootingCountsForItsKills(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, barding(t));
        t.assertTrue(d.getMainHandItem().isEmpty(), "it holds nothing");
        for (int i = 0; i < 40; i++) BardingEnchants.strike(d, husk(t, -3, 3), d.damageSources().mobAttack(d), 1000, true);
        int plain = flesh(t);
        d.setBardingItem(barding(t, Enchantments.LOOTING, 3));
        for (int i = 0; i < 40; i++) BardingEnchants.strike(d, husk(t, -3, 3), d.damageSources().mobAttack(d), 1000, true);
        int looted = flesh(t);
        t.assertTrue(looted > plain * 1.5 && d.getMainHandItem().isEmpty(), "40 kills with Looting III drop far more than 40 without (" + looted + " against " + plain + "), and its barding is in no hand after");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 60)
    public static void aVanishingBardingIsNotLeftInTheTombstone(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, barding(t, Enchantments.VANISHING_CURSE, 1));
        d.setGear(1, d.barding(), d.dye());  /* a hat, so there is a tombstone */
        t.assertTrue(BardingEnchants.vanishes(d.bardingItem()) && !BardingEnchants.vanishes(barding(t)), "the curse is read");
        TombstoneBlockEntity be = TombTests.dieForGood(t, d);
        t.assertTrue(be != null && !be.items().getItem(0).isEmpty() && be.items().getItem(1).isEmpty(), "its hat is in the tombstone, its cursed barding is gone");
        t.succeed();
    }
}
