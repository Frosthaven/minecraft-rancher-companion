package dev.frosthaven.ranchercompanion.client;

import static dev.frosthaven.ranchercompanion.client.SelfTest.*;
import static dev.frosthaven.ranchercompanion.client.SelfTestTraits.*;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.GoHere;
import dev.frosthaven.ranchercompanion.item.Barding;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.item.DrekkiPotions;
import dev.frosthaven.ranchercompanion.item.WhistleItem;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.trait.FightTraits;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * 0.5.0 in the real game (-Drancher_companion.selftest.new=true, or at the end of the traits run): the Potion of
 * Horizons (its icons, its brewing chain, feeding it, the emerald on the slot it unlocks), the whistle's expanded
 * tooltip, each of the nine new traits learned from the activity itself and then met as a player meets it, and the
 * void. Rewind is learned and used over the End's void, on a small obsidian platform far from the islands.
 */
final class SelfTestNewTraits {
    private SelfTestNewTraits() {}

    private static final String DREKKI = "@e[type=rancher_companion:drekki,limit=1,sort=nearest]";
    /** the End platform: its middle, 500 blocks out over the void */
    private static final int EX = 500, EY = 100, EZ = 0;
    private static float[] seen = new float[2];

    private static String potion(String name) { return "minecraft:potion[minecraft:potion_contents={potion:\"rancher_companion:" + name + "\"}]"; }

    /** the cursor over a hotbar slot of the open inventory screen, so its tooltip shows in the step's screenshot */
    private static void hoverHotbar(int slot) {
        if (!(mc().screen instanceof AbstractContainerScreen<?> s)) return;
        var sl = s.getMenu().getSlot(36 + slot);
        double scale = mc().getWindow().getGuiScale();
        mouse((s.getGuiLeft() + sl.x + 8) * scale, (s.getGuiTop() + sl.y + 8) * scale);
    }

    private static ItemStack held() { return mc().player.getMainHandItem(); }

    /** a mob near the player on the server, by its command tag */
    private static Mob tagged(String tag) {
        var m = me();
        if (m == null) return null;
        for (Entity e : m.serverLevel().getEntities((Entity) null, m.getBoundingBox().inflate(48), e -> e.isAlive() && e.getTags().contains(tag)))
            if (e instanceof Mob mob) return mob;
        return null;
    }

    private static void fledgling() {
        quiet("fledgling", 20, () -> { life(d -> { d.setLook(2, d.line(), d.diet(), d.astral(), d.horn()); d.applyStageHealth(true); }); pupHere(); });
    }

    private static void stance(Life.Stance s) { life(d -> d.setStance(s)); }

    private static void clearMobs() { cmd("kill @e[type=!player,type=!rancher_companion:drekki,type=!item,distance=..60,tag=st]"); }

    /** a husk that fights, 3 blocks from the drekki, sent at it */
    private static void foe(String tag) {
        DrekkiEntity c = pup();
        if (c == null) return;
        cmd("summon husk " + (c.getX() + 3) + " " + c.getY() + " " + c.getZ() + " {PersistenceRequired:1b,Tags:[\"st\",\"" + tag + "\"]}");
        after(5, () -> server(m -> { Mob z = tagged(tag); DrekkiEntity d = sd(); if (z != null && d != null) z.setTarget(d); }));
    }

    private static ItemStack wornBarding() {
        ItemStack b = new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(Barding.IRON).get());
        b.setDamageValue(b.getMaxDamage() - 1);
        return b;
    }

    /** only the enchanted barding part (-Drancher_companion.selftest.enchants=true with selftest.new) */
    private static final boolean ENCHANTS_ONLY = Boolean.getBoolean("rancher_companion.selftest.enchants");

    /** diamond barding for the stage, dyed black, with a chestplate's and a sword's enchantments */
    private static ItemStack enchanted(DrekkiEntity d, int stage, boolean bound) {
        ItemStack b = new ItemStack((stage == 2 ? DrekkiItems.FLEDGLING_BARDING : DrekkiItems.BARDING).get(Barding.DIAMOND).get());
        b.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x1D1D21, false));
        var reg = d.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        b.enchant(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);
        b.enchant(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.THORNS), 3);
        b.enchant(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
        b.enchant(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT), 2);
        b.enchant(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
        if (bound) b.enchant(reg.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE), 1);
        return b;
    }

    /** enchanted barding in the real game: its tooltip, the curse that keeps it on, Thorns and Fire Aspect in a fight,
     *  and close shots of black-dyed, enchanted diamond barding on a Fledgling and a Mature */
    private static void enchants() {
        fresh("enchants");
        fledgling();
        quiet("enchanted_item", 20, () -> server(m -> { DrekkiEntity d = sd(); if (d != null) m.getInventory().setItem(0, enchanted(d, 2, false)); }));
        quiet("enchanted_inventory", 20, () -> mc().setScreen(new InventoryScreen(mc().player)));
        step("enchanted_barding_tooltip", 10, () -> hoverHotbar(0), () -> mc().player.getInventory().getItem(0).isEnchanted());
        quiet("enchanted_inventory_close", 10, () -> mc().setScreen(null));
        /* Curse of Binding: on in survival, it does not come off; in creative it does */
        quiet("bound_on", 20, () -> { cmd("gamemode survival"); life(d -> d.setBardingItem(enchanted(d, 2, true))); emptyHand(); near(pup()); });
        step("bound_screen", 25, () -> interact("open its screen"), () -> ds() != null && pup() != null && pup().barding() == Barding.DIAMOND.gearIndex());
        step("binding_keeps_it_on", 15, () -> shiftClick(1), () -> pup() != null && pup().barding() == Barding.DIAMOND.gearIndex());
        quiet("bound_creative", 15, () -> cmd("gamemode creative"));
        step("binding_off_in_creative", 15, () -> shiftClick(1), () -> pup() != null && pup().barding() == 0);
        quiet("bound_close", 10, () -> { mc().setScreen(null); cmd("clear @s #minecraft:enchantable/chest_armor"); });
        /* Thorns on a drekki that does not fight back, then Sharpness and Fire Aspect on its bite */
        quiet("thorns_setup", 20, () -> { stance(Life.Stance.PASSIVE); life(d -> { d.setBardingItem(enchanted(d, 2, false)); d.setHealth(d.getMaxHealth()); }); pupHere(); });
        quiet("thorns_foe", 20, () -> foe("thorned"));
        check("thorns_hits_back", 60, 30, () -> life(d -> d.setHealth(d.getMaxHealth())),  /* 45% a hit: given time for a dozen hits */ "the husk's health after hitting barding with Thorns III", () -> { Mob z = tagged("thorned"); return z == null ? "gone" : z.getHealth(); },
            () -> { Mob z = tagged("thorned"); return z == null || z.getHealth() < z.getMaxHealth(); });
        quiet("thorns_done", 10, () -> { clearMobs(); stance(Life.Stance.REACTIVE); });
        quiet("fire_foe", 30, () -> cmd("summon husk ^ ^ ^4 {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"lit\"]}"));
        check("fire_aspect_on_its_bite", 20, 10, () -> server(m -> { Mob z = tagged("lit"); DrekkiEntity d = sd(); if (z != null && d != null) GoHere.attack(d, z); }),
            "the bitten husk burns", () -> { Mob z = tagged("lit"); return z == null ? "dead" : z.isOnFire() + " health " + z.getHealth(); }, () -> { Mob z = tagged("lit"); return z == null || z.isOnFire(); });
        quiet("fire_done", 10, () -> { life(DrekkiEntity::disengage); clearMobs(); });
        /* the look: black-dyed diamond barding with the enchantment glint */
        quiet("enchanted_fledgling_on", 20, () -> life(d -> d.setBardingItem(enchanted(d, 2, false))));
        lookAt("enchanted_black_diamond_fledgling", 1.0);
        quiet("enchanted_mature_on", 30, () -> life(d -> { d.setLook(3, d.line(), d.diet(), d.astral(), d.horn()); d.applyStageHealth(true); d.setBardingItem(enchanted(d, 3, false)); }));
        lookAt("enchanted_black_diamond_mature", 1.7);
        quiet("enchants_done", 30, () -> { cmd("gamemode creative"); cmd("gamerule sendCommandFeedback true");
            life(d -> { d.setNoAi(false); d.setBardingItem(ItemStack.EMPTY); d.setLook(2, d.line(), d.diet(), d.astral(), d.horn()); d.applyStageHealth(true); }); });
    }

    static void steps() {
        BlockPos o = SelfTestTraits.o;
        if (ENCHANTS_ONLY) { enchants(); return; }
        quiet("new_rules", 20, () -> { cmd("gamerule doMobSpawning false"); cmd("gamerule mobGriefing false"); cmd("time set 6000"); cmd("gamerule doDaylightCycle false"); });

        /* ---- the three potions and the whistle: icons and tooltips ---- */
        quiet("items", 20, () -> { hotbar(0, potion("distance")); hotbar(1, potion("twilight")); hotbar(2, potion("horizons")); hotbar(3, "rancher_companion:drekki_whistle"); });
        quiet("inventory", 20, () -> mc().setScreen(new InventoryScreen(mc().player)));
        String[] names = {"potion_of_distance", "potion_of_twilight", "potion_of_horizons"};
        for (int i = 0; i < 3; i++) { int k = i; step(names[k] + "_tooltip", 10, () -> hoverHotbar(k), () -> DrekkiPotions.step(mc().player.getInventory().getItem(k)) == k + 1); }
        step("whistle_tooltip", 10, () -> hoverHotbar(3), () -> mc().player.getInventory().getItem(3).getItem() instanceof WhistleItem);
        step("whistle_tooltip_shift", 10, () -> { WhistleItem.testExpanded = true; hoverHotbar(3); }, () -> true);
        quiet("inventory_close", 10, () -> { WhistleItem.testExpanded = false; mc().setScreen(null); });
        check("brewing_chain", 10, 2, () -> {}, "awkward + pearl, + popped chorus fruit, + emerald", () -> {
            var b = mc().getSingleplayerServer().potionBrewing();
            ItemStack one = b.mix(new ItemStack(Items.ENDER_PEARL), PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
            ItemStack two = b.mix(new ItemStack(Items.POPPED_CHORUS_FRUIT), one);
            ItemStack three = b.mix(new ItemStack(Items.EMERALD), two);
            return DrekkiPotions.step(one) + "," + DrekkiPotions.step(two) + "," + DrekkiPotions.step(three);
        }, () -> {
            var b = mc().getSingleplayerServer().potionBrewing();
            ItemStack one = b.mix(new ItemStack(Items.ENDER_PEARL), PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
            ItemStack three = b.mix(new ItemStack(Items.EMERALD), b.mix(new ItemStack(Items.POPPED_CHORUS_FRUIT), one));
            return DrekkiPotions.step(one) == 1 && DrekkiPotions.horizons(three);
        });

        /* ---- the potion fed by hand: one more slot, once, and the emerald on it ---- */
        fresh("potion");
        quiet("two_traits", 20, () -> life(d -> { d.life.horizons = false; d.life.traits.add("patchwork"); d.life.traits.add("hook_line_sinker"); }));
        quiet("near_before", 15, () -> { emptyHand(); near(pup()); });
        step("slots_before_the_potion", 25, () -> interact("open its screen"), () -> ds() != null);
        step("two_slots_no_emerald", 10, () -> clickSpot("tab_traits"), () -> ds() != null && ds().spot("trait1") != null && ds().spot("horizons") == null);
        quiet("close_before", 10, () -> mc().setScreen(null));
        quiet("potion_in_hand", 15, () -> { select(2); near(pup()); });
        step("potion_fed", 25, () -> interact("feed it the potion"), () -> sd() != null && sd().life.horizons && held().is(Items.GLASS_BOTTLE));
        quiet("second_potion", 15, () -> { hotbar(2, potion("horizons")); select(2); near(pup()); });
        step("second_potion_refused", 25, () -> interact("feed it a second potion"), () -> DrekkiPotions.horizons(held()));
        quiet("near_after", 15, () -> { emptyHand(); near(pup()); });
        step("slots_after_the_potion", 25, () -> interact("open its screen"), () -> ds() != null);
        step("three_slots_with_emerald", 10, () -> clickSpot("tab_traits"), () -> ds() != null && ds().spot("horizons") != null);
        step("emerald_tooltip", 10, () -> hoverSpot("horizons"), () -> ds() != null && ds().spot("horizons") != null);
        quiet("close_after", 10, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; mc().setScreen(null); });

        /* ---- Staring Contest ---- */
        fresh("staring");
        learn(Trait.STARING_CONTEST, 14, 2, kill("enderman", 2));
        quiet("stare_setup", 30, () -> { cmd("time set 18000"); cmd("summon enderman ^ ^ ^5 {PersistenceRequired:1b,Tags:[\"st\",\"stared\"]}"); pupHere(); });
        quiet("stare", 20, () -> cmd("tp @s ~ ~ ~ facing entity @e[tag=stared,limit=1] eyes"));
        check("staring_contest_no_anger", 100, 2, () -> cmd("tp @s ~ ~ ~ facing entity @e[tag=stared,limit=1] eyes"), "the stared enderman's target", () -> { Mob e = tagged("stared"); return e == null ? "gone" : e.getTarget(); },
            () -> { Mob e = tagged("stared"); return e != null && e.getTarget() == null; });
        quiet("stare_done", 10, () -> { clearMobs(); cmd("time set 6000"); });

        /* ---- Web Cutter ---- */
        fresh("webs");
        learn(Trait.WEB_CUTTER, 16, 2, kill("spider", 2));
        quiet("webs_up", 20, () -> { cmd("fill " + at(o.offset(6, 0, 0)) + " " + at(o.offset(6, 2, 0)) + " cobweb"); pupHere(); });
        check("web_cutter_falls_through", 30, 2, () -> cmd("tp @s " + (o.getX() + 6.5) + " " + (o.getY() + 4) + " " + (o.getZ() + 0.5)), "on the ground under three cobwebs",
            () -> mc().player.onGround() + " at y " + String.format("%.2f", mc().player.getY()), () -> mc().player.onGround() && mc().player.getY() < o.getY() + 0.2);
        quiet("webs_down", 10, () -> cmd("fill " + at(o.offset(6, 0, 0)) + " " + at(o.offset(6, 2, 0)) + " air"));

        /* ---- Marked: arrows of the player's from 31 blocks, then the ordered mob ---- */
        fresh("marked");
        quiet("far_husk", 20, () -> cmd("summon husk " + (o.getX() + 31.5) + " " + o.getY() + " " + (o.getZ() + 0.5) + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\"]}"));
        learn(Trait.MARKED, 16, 1, () -> {
            int[] id = UUIDUtil.uuidToIntArray(mc().player.getUUID());
            cmd("summon arrow " + (o.getX() + 31.5) + " " + (o.getY() + 3.5) + " " + (o.getZ() + 0.5) + " {Owner:[I;" + id[0] + "," + id[1] + "," + id[2] + "," + id[3] + "],Motion:[0.0d,-2.0d,0.0d],damage:0.5d}");
        });
        fledgling();
        quiet("marked_mobs", 30, () -> {
            clearMobs();
            cmd("summon husk ^ ^ ^5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"marked\"]}");
            cmd("summon husk ^4 ^ ^5 {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"plain\"]}");
        });
        check("marked_takes_more", 20, 2, () -> server(m -> {
            Mob a = tagged("marked"), b = tagged("plain"); DrekkiEntity d = sd();
            if (a == null || b == null || d == null) return;
            GoHere.attack(d, a);
            float ha = a.getHealth(), hb = b.getHealth();
            a.hurt(a.damageSources().magic(), 4); b.hurt(b.damageSources().magic(), 4);
            seen = new float[]{ha - a.getHealth(), hb - b.getHealth()};
        }), "damage to the ordered mob against the same hit on another", () -> seen[0] + " against " + seen[1], () -> seen[1] > 0 && Math.abs(seen[0] - seen[1] * FightTraits.MARKED) < 0.05f);
        check("marked_glows", 30, 3, () -> {}, "the ordered mob glows", () -> { Mob a = tagged("marked"); return a == null ? "gone" : a.hasEffect(MobEffects.GLOWING); },
            () -> { Mob a = tagged("marked"); return a == null || a.hasEffect(MobEffects.GLOWING); });
        quiet("marked_done", 10, () -> { life(DrekkiEntity::disengage); clearMobs(); });

        /* ---- Provoke: whistle attack orders on different mobs, then a mob that was after the player ---- */
        fresh("provoke");
        int[] n = {0};
        learn(Trait.PROVOKE, 14, 4, () -> {
            String tag = "p" + n[0]++;
            cmd("summon husk ^ ^ ^3 {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"" + tag + "\"]}");
            after(8, () -> server(m -> { Mob z = tagged(tag); DrekkiEntity d = sd(); if (z != null && d != null) GoHere.attack(d, z); }));
        });
        quiet("provoke_clear", 20, () -> { life(DrekkiEntity::disengage); clearMobs(); });
        quiet("provoke_foe", 30, () -> cmd("summon husk ^ ^ ^6 {PersistenceRequired:1b,Tags:[\"st\",\"held\"]}"));
        check("provoke_holds_the_mob", 60, 8, () -> server(m -> { Mob z = tagged("held"); DrekkiEntity d = sd(); if (z != null && d != null && d.ordered != z) { d.life.cooldowns.clear(); GoHere.attack(d, z); } }),
            "the husk that was after the player targets", () -> { Mob z = tagged("held"); return z == null ? "gone" : z.getTarget(); }, () -> { Mob z = tagged("held"); return z != null && z.getTarget() == sd(); });
        quiet("provoke_done", 10, () -> { life(DrekkiEntity::disengage); clearMobs(); server(m -> m.setHealth(20)); });

        /* ---- Fade: hit at half health by a husk (the player in creative, so the husk stays on the drekki) ---- */
        fresh("fade");
        quiet("fade_setup", 20, () -> { cmd("gamemode creative"); stance(Life.Stance.PASSIVE); });
        learn(Trait.FADE, 20, 3, () -> {
            life(d -> d.setHealth(d.getMaxHealth() / 2 - 1));
            if (tagged("fader") == null) foe("fader");
        });
        quiet("fade_clear", 20, () -> { clearMobs(); cmd("gamemode survival"); life(d -> { d.setHealth(d.getMaxHealth()); d.life.cooldowns.clear(); }); pupHere(); });
        quiet("fade_foe", 30, () -> cmd("summon husk ^ ^ ^6 {PersistenceRequired:1b,Tags:[\"st\",\"turned\"]}"));
        check("fade_turns_the_mob", 20, 4, () -> server(m -> { Mob z = tagged("turned"); DrekkiEntity d = sd(); if (z != null && d != null) z.setTarget(d); }),
            "the husk sent at the drekki targets", () -> { Mob z = tagged("turned"); return z == null ? "gone" : z.getTarget() + " faded " + (sd() != null && FightTraits.faded(sd())); },
            () -> { Mob z = tagged("turned"); return z != null && z.getTarget() == me() && sd() != null && FightTraits.faded(sd()); });
        quiet("fade_done", 10, () -> { clearMobs(); cmd("gamemode creative"); server(m -> m.setHealth(20)); });

        /* ---- Barbed Coat: cactus damage to the player, then a husk's hits on the Passive drekki ---- */
        fresh("barbed");
        quiet("barbed_setup", 20, () -> cmd("gamemode survival"));
        learn(Trait.BARBED_COAT, 14, 2, () -> { server(m -> { m.setHealth(20); m.invulnerableTime = 0; }); after(3, () -> cmd("damage @s 1 minecraft:cactus")); });
        if (net.neoforged.fml.ModList.get().isLoaded("biomesoplenty"))
            check("bramble_counts_as_spikes", 10, 2, () -> {}, "Biomes O' Plenty's bramble damage is in the spikes tag", () -> "checked", () -> {
                var m = me();
                var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("biomesoplenty", "bramble"));
                return m != null && m.damageSources().source(key).is(FightTraits.SPIKES);
            });
        quiet("barbed_foe_setup", 20, () -> { cmd("gamemode creative"); stance(Life.Stance.PASSIVE); life(d -> d.setHealth(d.getMaxHealth())); });
        quiet("barbed_foe", 20, () -> foe("pricked"));
        check("barbed_coat_returns_damage", 60, 8, () -> life(d -> d.setHealth(d.getMaxHealth())), "the husk's health after hitting a drekki that does not fight back",
            () -> { Mob z = tagged("pricked"); DrekkiEntity d = sd(); return z == null ? "gone" : z.getHealth() + ", its target " + z.getTarget() + ", " + (d == null ? "?" : String.format("%.1f blocks off, drekki health %.1f last hurt by %s", z.distanceTo(d), d.getHealth(), d.getLastHurtByMob())) + " | " + state(); },
            () -> { Mob z = tagged("pricked"); return z == null || z.getHealth() < z.getMaxHealth(); });
        quiet("barbed_done", 10, SelfTestNewTraits::clearMobs);

        /* ---- Point Break: a creeper's blast breaks its barding; then barding broken by a husk blasts ---- */
        fresh("point_break");
        quiet("point_setup", 20, () -> { cmd("gamemode creative"); stance(Life.Stance.PASSIVE); });
        learn(Trait.POINT_BREAK, 6, 4, () -> {
            life(d -> { d.setBardingItem(wornBarding()); d.setHealth(d.getMaxHealth()); });
            DrekkiEntity c = pup();
            if (c != null) after(5, () -> cmd("summon creeper " + (c.getX() + 1.5) + " " + c.getY() + " " + c.getZ() + " {Fuse:2s,ignited:1b,Tags:[\"st\"]}"));
        });
        quiet("point_floor", 20, () -> { clearMobs(); cmd("fill " + at(o.offset(-12, -1, -12)) + " " + at(o.offset(12, -1, 12)) + " stone"); life(d -> { d.life.cooldowns.clear(); d.setBardingItem(wornBarding()); d.setHealth(d.getMaxHealth()); }); pupHere(); });
        quiet("point_foe", 20, () -> foe("blasted"));
        check("point_break_blasts", 60, 8, () -> {}, "the blast's cooldown is running and the husk is hurt or dead",
            () -> { Mob z = tagged("blasted"); DrekkiEntity d = sd(); return (z == null ? "husk gone" : "husk " + z.getHealth()) + ", barding " + (d == null ? "?" : d.bardingItem()) ; },
            () -> { Mob z = tagged("blasted"); DrekkiEntity d = sd(); return d != null && d.bardingItem().isEmpty() && d.life.cooldowns.containsKey(Trait.POINT_BREAK.id) && (z == null || z.getHealth() < z.getMaxHealth() - 5); });
        check("point_break_breaks_no_block", 10, 2, () -> {}, "the floor under the drekki", () -> { DrekkiEntity d = sd(); return d == null ? "?" : d.level().getBlockState(d.blockPosition().below()); },
            () -> { DrekkiEntity d = sd(); return d != null && d.level().getBlockState(d.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.STONE); });
        quiet("point_done", 10, SelfTestNewTraits::clearMobs);

        /* ---- Live Wire: a strike beside the player, then a thunderstorm ---- */
        fresh("live_wire");
        quiet("wire_setup", 20, () -> { cmd("gamemode creative"); stance(Life.Stance.REACTIVE); });
        learn(Trait.LIVE_WIRE, 14, 2, () -> cmd("summon lightning_bolt ~3 ~ ~"));
        quiet("storm", 20, () -> { cmd("weather thunder"); life(d -> d.setHealth(d.getMaxHealth())); pupHere(); });  /* the learning bolts were real ones and hurt it */
        quiet("storm_builds", 160, () -> {});  /* the thunder rises over about 5 seconds; a step waits after its act */
        check("live_wire_draws_the_bolt", 20, 6, () -> cmd("summon lightning_bolt ~10 ~ ~"), "surged, and the owner's Night Vision",
            () -> (sd() != null && sd().surged()) + ", " + mc().player.hasEffect(MobEffects.NIGHT_VISION), () -> sd() != null && sd().surged() && mc().player.hasEffect(MobEffects.NIGHT_VISION) && sd().getHealth() == sd().getMaxHealth());
        quiet("storm_over", 10, () -> { cmd("weather clear"); cmd("effect clear @s"); });

        /* ---- Rewind, over the End's void ---- */
        fresh("rewind");
        quiet("end_loaded", 60, () -> cmd("execute in minecraft:the_end run forceload add " + (EX - 8) + " " + (EZ - 8) + " " + (EX + 8) + " " + (EZ + 8)));
        quiet("end_floor", 20, () -> cmd("execute in minecraft:the_end run fill " + (EX - 4) + " " + EY + " " + (EZ - 4) + " " + (EX + 4) + " " + EY + " " + (EZ + 4) + " obsidian"));
        quiet("end_platform", 60, () -> cmd("execute in minecraft:the_end run tp @s " + (EX + 0.5) + " " + (EY + 1) + " " + (EZ + 0.5)));
        stepUntil("pup_comes_to_the_end", 20, 20, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 8);
        quiet("pearls", 10, () -> { hotbar(6, "ender_pearl 16"); life(d -> d.teleportTo(EX + 1.5, EY + 1, EZ + 1.5)); });
        learn(Trait.REWIND, 12, 5, () -> {
            /* off the edge, falling, the platform ahead and below: the pearl goes when the fall is 2 blocks deep */
            cmd("tp @s " + (EX + 7.5) + " " + (EY + 6) + " " + (EZ + 0.5) + " facing " + (EX + 0.5) + " " + (EY + 1) + " " + (EZ + 0.5));
            after(9, () -> { select(6); mc().gameMode.useItem(mc().player, net.minecraft.world.InteractionHand.MAIN_HAND); });
        });
        quiet("rewind_ready", 40, () -> { cmd("gamemode survival"); cmd("tp @s " + (EX + 0.5) + " " + (EY + 1) + " " + (EZ + 0.5)); life(d -> { d.life.cooldowns.clear(); d.teleportTo(EX + 1.5, EY + 1, EZ + 1.5); }); server(m -> m.setHealth(20)); });
        check("rewind_returns_the_owner", 60, 10, () -> { if (mc().player.getY() > EY) cmd("tp @s " + (EX + 9.5) + " -60 " + (EZ + 0.5)); }, "the owner after falling out of the world",
            () -> String.format("y %.1f health %.1f", mc().player.getY(), mc().player.getHealth()) + " | " + state(),
            () -> sd() != null && sd().life.cooldowns.containsKey(Trait.REWIND.id) && Math.abs(mc().player.getY() - (EY + 1)) < 1.5 && mc().player.getHealth() == 20);
        quiet("void_heal", 10, () -> life(d -> d.setHealth(d.getMaxHealth())));
        quiet("void_drekki", 30, () -> { cmd("gamemode creative"); cmd("tp " + DREKKI + " " + (EX + 9.5) + " -70 " + (EZ + 0.5)); });
        check("the_void_never_kills_a_drekki", 40, 5, () -> {}, "the drekki after falling out of the world", () -> { DrekkiEntity d = sd(); return d == null ? "gone" : String.format("y %.1f health %.1f", d.getY(), d.getHealth()); },
            () -> { DrekkiEntity d = sd(); return d != null && d.isAlive() && d.getY() >= EY && d.getHealth() == d.getMaxHealth(); });
        quiet("home", 60, () -> cmd("execute in minecraft:overworld run tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5)));
        stepUntil("pup_comes_home", 20, 20, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 8);
        enchants();
        quiet("new_rules_back", 10, () -> { cmd("gamerule doMobSpawning true"); cmd("gamerule mobGriefing true"); cmd("gamerule doDaylightCycle true"); RancherCompanion.LOG.info("[selftest] 0.5.0 section done"); });
    }
}
