package dev.frosthaven.ranchercompanion.client;

import static dev.frosthaven.ranchercompanion.client.SelfTest.*;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.mixin.FishingHookAccess;
import dev.frosthaven.ranchercompanion.trait.Trait;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Every activity trait in the real game (-Drancher_companion.selftest.traits=true): a checked-out pup watching its
 * owner learns each one from the activity itself (real kills, falls, drowning, fire, powder snow, milk, fishing,
 * trades, XP, a totem, a worn-out tool), then the trait's ability is checked the way a player meets it. Mood is kept
 * Thriving (petted), so one-off traits teach at 75% and are tried until learned. Traits are cleared between checks
 * (a pup holds 3). Hearty and Phoenix Heart run last on a Fledgling (a pup takes no damage).
 */
final class SelfTestTraits {
    private SelfTestTraits() {}

    private static BlockPos o, pool, slime, snow, fire;
    private static boolean autoFish;
    private static int fishWait, lastLured = -1, xpBefore;
    private static long lastLuredAt;

    // ---- helpers ----

    /** the pup as the server sees it */
    private static DrekkiEntity sd() {
        var srv = mc().getSingleplayerServer(); var c = pup();
        if (srv == null || c == null) return null;
        for (var l : srv.getAllLevels()) if (l.getEntity(c.getUUID()) instanceof DrekkiEntity d) return d;
        return null;
    }

    private static void server(java.util.function.Consumer<ServerPlayer> f) { var m = me(); if (m != null) m.server.execute(() -> f.accept(m)); }

    private static boolean has(Trait t) {
        var c = pup();
        if (c == null) return false;
        for (Tag s : c.traitView().getList("traits", Tag.TAG_STRING)) if (s.getAsString().equals(t.id)) return true;
        return false;
    }

    /** a clean slate for the next trait: no traits, no counts, no cooldowns, petted (Thriving), beside the player */
    private static void fresh(String name) {
        quiet("fresh_" + name, 10, () -> {
            if (mc().player.isDeadOrDying()) mc().player.respawn();  /* a mob may have killed the test player */
        });
        quiet("fresh_" + name + "_site", 30, () -> {
            tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
            cmd("fill " + at(o.offset(-12, 0, -12)) + " " + at(o.offset(12, 6, 12)) + " air replace fire");
            server(m -> { m.setHealth(20); m.getFoodData().setFoodLevel(20); m.clearFire(); m.removeAllEffects(); m.setTicksFrozen(0); m.setAirSupply(300); });
            life(d -> { d.life.traits.clear(); d.life.progress.clear(); d.life.cooldowns.clear(); Vitals.pet(d); d.applyStageHealth(false); });
            pupHere();
        });
    }

    /** the pup to just beside the player (within the 10 blocks it learns and helps in) */
    private static void pupHere() {
        life(d -> { var m = me(); if (m != null) d.teleportTo(m.getX() + 1.5, Math.max(m.getY(), o.getY()), m.getZ() + 1.5); });
    }

    private static void tp(double x, double y, double z) { cmd("tp @s " + x + " " + y + " " + z + " -90 0"); }

    /** repeats the activity every `every` seconds until the trait is learned (at most `tries` times), so the next
     *  step starts right after it is learned */
    private static void learn(Trait t, int tries, int every, Runnable act) {
        int[] polls = {0};
        stepUntil("learns_" + t.id, 10, tries * every, act, () -> {
            if (has(t)) return true;
            if (pup() != null && t.id.equals(pup().traitView().getString("offer"))) { life(d -> dev.frosthaven.ranchercompanion.trait.Traits.answer(d, true)); return false; }
            if (++polls[0] % every == 0) act.run();
            return false;
        });
    }

    /** the pup's server state, logged beside each ability check: its traits, cooldowns, and distance to the player */
    private static String state() {
        DrekkiEntity d = sd(); var m = me();
        if (d == null || m == null) return "no pup";
        return "traits " + d.life.traits + " progress " + d.life.progress + " cooldowns " + d.life.cooldowns.keySet() + " (now " + Vitals.now(d) + " " + d.life.cooldowns + ") dist " + String.format("%.1f", d.distanceTo(m)) + " band " + d.band();
    }

    /** an ability, the way a player meets it after learning: cooldowns cleared, the pup beside them, then the trigger */
    private static void ability(String name, Runnable reset, Runnable trigger, int wait, int patience, String what, java.util.function.Supplier<Object> seen, BooleanSupplier ok) {
        quiet(name + "_reset", 30, () -> { reset.run(); life(d -> d.life.cooldowns.clear()); pupHere(); });
        check(name, wait, patience, trigger, what, () -> seen.get() + " | " + state(), ok);
    }

    /** a check that logs what it saw */
    private static void check(String name, int delay, int patience, Runnable act, String what, java.util.function.Supplier<Object> seen, BooleanSupplier ok) {
        stepUntil(name, delay, patience, act, () -> { boolean r = ok.getAsBoolean(); RancherCompanion.LOG.info("[selftest] {}: {} = {}", name, what, seen.get()); return r; });
    }

    private static Entity nearest(String type) {
        Entity best = null; var p = mc().player;
        for (Entity e : mc().level.getEntities(null, new AABB(p.blockPosition()).inflate(12)))
            if (e.isAlive() && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type) && (best == null || e.distanceTo(p) < best.distanceTo(p))) best = e;
        return best;
    }

    private static Mob serverMob(String type) {
        var m = me();
        if (m == null) return null;
        for (Entity e : m.serverLevel().getEntities((Entity) null, m.getBoundingBox().inflate(24), e -> e.isAlive() && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type)))
            if (e instanceof Mob mob) return mob;
        return null;
    }

    /** a real melee kill: a 1-health mob summoned in front, hit with the sword in hotbar slot 5 */
    private static Runnable kill(String type, double ahead) {
        return () -> {
            select(5);
            cmd("summon " + type + " ^ ^ ^" + ahead + " {NoAI:1b,Health:1f,PersistenceRequired:1b,IsImmuneToZombification:1b}");
            after(6, () -> { Entity e = nearest(type); if (e != null) mc().gameMode.attack(mc().player, e); });
        };
    }

    /** actions due a number of client ticks from now */
    private static final java.util.List<Object[]> DUE = new java.util.ArrayList<>();
    private static void after(int ticks, Runnable r) { DUE.add(new Object[]{ticks, r}); }
    private static boolean fallLog, fireLog;
    private static int[] hoverAt;

    /** per client tick: deferred hits, and the fishing rod reeled in on a bite and cast again */
    static void tick() {
        for (Object[] d : new java.util.ArrayList<>(DUE)) { d[0] = (int) d[0] - 1; if ((int) d[0] <= 0) { DUE.remove(d); ((Runnable) d[1]).run(); } }
        if (!autoFish || mc().player == null) return;
        if (fishWait > 0) { fishWait--; return; }
        var m = me();
        if (m == null) return;
        if (m.fishing == null) { select(4); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); fishWait = 30; }
        else if (((FishingHookAccess) m.fishing).rancher$nibble() > 0) { select(4); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); fishWait = 15; }
    }

    // ---- the run ----

    static void build(BlockPos origin) {
        o = origin.offset(4000, 0, 0);  /* away from any colony or claim in the world the test runs in */
        pool = o.offset(-8, -1, -8);
        slime = o.offset(-8, -1, 6);
        snow = o.offset(-3, 0, 10);
        fire = o.offset(8, 0, -8);
        BlockPos pond = o.offset(7, -1, 7);
        /* the slime check's diagnostics: every fall event the player gets, cancelled ones too, first and last in line */
        for (var prio : new net.neoforged.bus.api.EventPriority[]{net.neoforged.bus.api.EventPriority.HIGHEST, net.neoforged.bus.api.EventPriority.LOWEST})
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(prio, true, net.neoforged.neoforge.event.entity.living.LivingFallEvent.class, e -> {
                if (fallLog && e.getEntity() instanceof ServerPlayer sp) RancherCompanion.LOG.info("[selftest] fall event {}: distance {} cancelled {} on {} | {}",
                    prio, e.getDistance(), e.isCanceled(), sp.level().getBlockState(sp.getOnPos()).getBlock(), state());
            });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, e -> {
            if (fireLog && e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 5 == 0)
                RancherCompanion.LOG.info("[selftest] fire tick: onFire {} fireTicks {} health {} learner {} | {}", sp.isOnFire(), sp.getRemainingFireTicks(), sp.getHealth(),
                    dev.frosthaven.ranchercompanion.trait.Traits.learner(sp) != null, state());
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, e -> {
            if (fallLog && e.getEntity() instanceof ServerPlayer sp && (sp.fallDistance > 0 || !sp.onGround()))
                RancherCompanion.LOG.info("[selftest] fall tick: y {} fallDistance {} onGround {} mayFly {} on {}", String.format("%.2f", sp.getY()), sp.fallDistance, sp.onGround(), sp.mayFly(), sp.level().getBlockState(sp.getOnPos()).getBlock());
        });
        /* ---- the Ponder stable scene: the booth, then each anchor's own square ---- */
        if (net.neoforged.fml.ModList.get().isLoaded("ponder")) {
            quiet("ponder_open_stable", 40, () -> dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.open(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.HATCHERY.get())));
            int[][] at = {{30, 0}, {140, 1}, {330, 2}, {530, 3}, {620, 4}, {720, 5}, {820, 6}, {920, 7}, {1050, 8}};
            int prev = 0;
            for (int[] a : at) { step("ponder_stable_" + a[1], a[0] - prev, () -> {}, null); prev = a[0]; }
            quiet("ponder_close", 10, () -> mc().setScreen(null));
        }
        quiet("traits_site", 60, () -> {
            cmd("forceload add " + (o.getX() - 12) + " " + (o.getZ() - 12) + " " + (o.getX() + 12) + " " + (o.getZ() + 12));
            cmd("fill " + at(o.offset(-12, -1, -12)) + " " + at(o.offset(12, -1, 12)) + " stone");
            cmd("fill " + at(o.offset(-12, 0, -12)) + " " + at(o.offset(12, 12, 12)) + " air");
            cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5) + " -90 0");
        });
        quiet("traits_site_parts", 40, () -> {
            cmd("gamemode survival");
            cmd("weather clear");
            cmd("difficulty normal");
            cmd("gamerule doFireTick false");
            cmd("fill " + at(pool.offset(-2, -8, -2)) + " " + at(pool.offset(2, 0, 2)) + " stone");
            cmd("fill " + at(pool.offset(-1, -6, -1)) + " " + at(pool.offset(1, 0, 1)) + " water");
            cmd("fill " + at(pond.offset(-5, -2, -5)) + " " + at(pond.offset(5, 0, 5)) + " stone");
            cmd("fill " + at(pond.offset(-4, -1, -4)) + " " + at(pond.offset(4, 0, 4)) + " water");
            cmd("setblock " + at(slime) + " slime_block");
            cmd("fill " + at(snow) + " " + at(snow.above()) + " powder_snow");
            hotbar(5, "netherite_sword");
            hotbar(4, "fishing_rod");
        });
        stepUntil("pup_follows_to_the_site", 20, 15, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 8);

        /* ---- counted: kills ---- */
        fresh("emperor");
        quiet("piglin_control", 70, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); pupHere(); cmd("summon piglin ^ ^ ^5 {IsImmuneToZombification:1b,PersistenceRequired:1b}"); });
        check("piglin_hostile_without_trait", 10, 5, () -> {}, "attack target",
            () -> serverMob("piglin") == null ? "none" : serverMob("piglin").getBrain().getMemory(MemoryModuleType.ATTACK_TARGET),
            () -> serverMob("piglin") instanceof Piglin pg && pg.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent());
        quiet("piglin_control_gone", 10, () -> { cmd("kill @e[type=piglin]"); server(m -> m.setHealth(20)); });
        String[] piglins = {"piglin", "piglin_brute", "zombified_piglin"};
        int[] nth = {0};
        learn(Trait.FALLEN_EMPEROR, 12, 1, () -> kill(piglins[nth[0]++ % 3], 2).run());
        quiet("piglin_again", 90, () -> { server(m -> m.setHealth(20)); cmd("summon piglin ^ ^ ^5 {IsImmuneToZombification:1b,PersistenceRequired:1b}"); });
        check("fallen_emperor_piglin_calm", 10, 0, () -> {}, "attack target",
            () -> (serverMob("piglin") == null ? "none" : serverMob("piglin").getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)) + " | " + state(),
            () -> serverMob("piglin") instanceof Piglin pg && pg.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty());
        quiet("piglin_gone", 10, () -> cmd("kill @e[type=piglin]"));

        fresh("ghast");
        /* Hide & Seek works on the game's targeting (every target change goes through it): a ghast is told to target the
           player, as its AI does on sight; it holds no AI here, so it shoots no fireballs at the test player */
        quiet("ghast_control", 30, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); pupHere(); cmd("summon ghast ^ ^ ^10 {NoAI:1b,PersistenceRequired:1b}"); });
        check("ghast_targets_without_trait", 10, 3, () -> server(m -> { if (serverMob("ghast") != null) serverMob("ghast").setTarget(m); }), "target",
            () -> serverMob("ghast") == null ? "none" : serverMob("ghast").getTarget(), () -> serverMob("ghast") != null && serverMob("ghast").getTarget() == me());
        quiet("ghast_control_gone", 10, () -> { cmd("kill @e[type=ghast]"); server(m -> m.setHealth(20)); });
        learn(Trait.HIDE_SEEK, 7, 1, kill("ghast", 3));
        quiet("ghast_again", 30, () -> { server(m -> m.setHealth(20)); cmd("summon ghast ^ ^ ^10 {NoAI:1b,PersistenceRequired:1b}"); });
        check("hide_seek_ghast_ignores", 10, 0, () -> server(m -> { if (serverMob("ghast") != null) serverMob("ghast").setTarget(m); }), "target", () -> (serverMob("ghast") == null ? "none" : serverMob("ghast").getTarget()) + " | " + state(),
            () -> serverMob("ghast") != null && serverMob("ghast").getTarget() == null);
        quiet("ghast_gone", 10, () -> { cmd("kill @e[type=ghast]"); cmd("kill @e[type=fireball]"); });

        fresh("phantom");
        learn(Trait.NIGHT_WATCH, 7, 1, kill("phantom", 2));
        check("night_watch_denies_phantoms", 10, 0, () -> {}, "state", SelfTestTraits::state,
            () -> { var m = me(); if (m == null) return false;
                var ev = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent(m, 3));
                return ev.getResult() == net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent.Result.DENY; });

        /* ---- counted: other activities ---- */
        fresh("training");
        learn(Trait.TRAINING_DAY, 5, 1, () -> cmd("xp add @s 1 levels"));
        quiet("training_due", 80, () -> { xpBefore = mc().player.totalExperience; life(d -> d.life.cooldowns.put(Trait.TRAINING_DAY.id, Vitals.now(d))); });
        check("training_day_drops_xp", 10, 5, () -> {}, "xp before/after", () -> xpBefore + " / " + mc().player.totalExperience,
            () -> mc().player.totalExperience >= xpBefore + 18 || !mc().level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, mc().player.getBoundingBox().inflate(10)).isEmpty());

        fresh("slime");
        quiet("slime_log_on", 5, () -> fallLog = true);
        learn(Trait.CLOUD_STOMPERS, 14, 2, () -> { tp(slime.getX() + 0.5, slime.getY() + 4, slime.getZ() + 0.5); pupHere(); });
        quiet("slime_log_off", 5, () -> fallLog = false);

        fresh("trade");
        quiet("villager", 20, () -> {
            tp(o.getX() + 0.5, o.getY(), o.getZ() - 5.5); pupHere();
            cmd("give @s emerald 64");
            cmd("summon villager ^ ^ ^2 {NoAI:1b,PersistenceRequired:1b,VillagerData:{profession:\"minecraft:librarian\",level:5,type:\"minecraft:plains\"},"
                + "Offers:{Recipes:[{buy:{id:\"minecraft:emerald\",count:10},sell:{id:\"minecraft:bread\",count:1},maxUses:999,priceMultiplier:0.05f,rewardExp:0b,xp:0,demand:0}]}}");
        });
        learn(Trait.CREATURE_COMFORTS, 7, 3, () -> {
            select(7); Entity v = nearest("villager"); if (v != null) mc().gameMode.interact(mc().player, v, InteractionHand.MAIN_HAND);
            after(10, () -> { if (mc().screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen ms) {
                var mm = ms.getMenu(); mm.setSelectionHint(0); mm.tryMoveItems(0); mc().player.connection.send(new net.minecraft.network.protocol.game.ServerboundSelectTradePacket(0)); } });
            after(20, () -> { if (mc().screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen ms)
                mc().gameMode.handleInventoryMouseClick(ms.getMenu().containerId, 2, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc().player); });
            after(30, () -> mc().player.closeContainer());
        });
        quiet("trade_reopen", 50, () -> { select(7); Entity v = nearest("villager"); if (v != null) mc().gameMode.interact(mc().player, v, InteractionHand.MAIN_HAND); });
        check("creature_comforts_cheaper", 10, 3, () -> {}, "price (base 10)",
            () -> (mc().screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen ms && !ms.getMenu().getOffers().isEmpty() ? ms.getMenu().getOffers().get(0).getCostA().getCount() : "no screen") + " | " + state(),
            () -> mc().screen instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen ms && !ms.getMenu().getOffers().isEmpty() && ms.getMenu().getOffers().get(0).getCostA().getCount() < 10);
        quiet("trade_done", 10, () -> { mc().player.closeContainer(); cmd("kill @e[type=villager]"); });

        fresh("fishing");
        quiet("to_the_pond", 20, () -> { cmd("tp @s " + (pond.getX() - 5.5) + " " + o.getY() + " " + (pond.getZ() + 0.5) + " facing " + (pond.getX() + 0.5) + " " + pond.getY() + " " + (pond.getZ() + 0.5)); pupHere(); });
        learn(Trait.HOOK_LINE_SINKER, 150, 1, () -> autoFish = true);
        quiet("fish_stop", 30, () -> { autoFish = false; if (me() != null && me().fishing != null) { select(4); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); } });
        quiet("fish_cast", 40, () -> { select(4); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); lastLured = -1; });
        check("hook_line_sinker_faster_bites", 20, 12, () -> {}, "last wait", () -> lastLured + " | " + state(),
            () -> {
                var m = me(); if (m == null || m.fishing == null) return false;
                int now = ((FishingHookAccess) m.fishing).rancher$timeUntilLured(); long t = m.level().getGameTime();
                boolean ok = lastLured > 0 && now > 0 && now < lastLured && (lastLured - now) / (double) (t - lastLuredAt) > 1.2;
                RancherCompanion.LOG.info("[selftest] lured {} -> {} over {} ticks", lastLured, now, t - lastLuredAt);
                if (lastLured > 0 && now == 0) { select(4); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); after(15, () -> mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND)); now = -1; }
                lastLured = now; lastLuredAt = t;
                return ok;
            });
        quiet("fish_reel", 10, () -> { if (me() != null && me().fishing != null) { select(4); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); } });

        /* ---- one-off ---- */
        fresh("milk");
        quiet("milk_spot", 10, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); pupHere(); });
        learn(Trait.BALM_SALVE, 24, 3, () -> {
            cmd("effect give @s poison 30");
            hotbar(6, "milk_bucket"); select(6);
            mc().options.keyUse.setDown(true);
            after(45, () -> mc().options.keyUse.setDown(false));
        });
        ability("balm_salve_blocks_poison", () -> server(m -> m.removeAllEffects()), () -> cmd("effect give @s poison 30"), 10, 2,
            "poisoned", () -> me() != null && me().hasEffect(MobEffects.POISON), () -> me() != null && !me().hasEffect(MobEffects.POISON));

        fresh("stack");
        for (int i = 0; i < 6; i++) quiet("last_block_" + i, 10, () -> { hotbar(0, "dirt"); select(0); useOn(o.offset(2, -1, 2), Direction.UP); cmd("setblock " + at(o.offset(2, 0, 2)) + " air"); });
        check("patchwork_not_from_a_used_up_stack", 10, 0, () -> {}, "learned", () -> has(Trait.PATCHWORK), () -> !has(Trait.PATCHWORK));
        learn(Trait.PATCHWORK, 12, 1, () -> { hotbar(0, "wooden_pickaxe[damage=58]"); select(0);
            after(5, () -> server(m -> m.getMainHandItem().hurtAndBreak(1, m.serverLevel(), m, it -> {}))); });
        quiet("worn_pick", 15, () -> { hotbar(0, "wooden_pickaxe[damage=58]"); select(0); });
        check("patchwork_saves_the_item", 15, 0, () -> server(m -> m.getMainHandItem().hurtAndBreak(1, m.serverLevel(), m, it -> {})), "durability left",
            () -> mc().player.getInventory().getItem(0).getMaxDamage() - mc().player.getInventory().getItem(0).getDamageValue(),
            () -> { ItemStack s = mc().player.getInventory().getItem(0); return s.is(Items.WOODEN_PICKAXE) && s.getMaxDamage() - s.getDamageValue() == s.getMaxDamage() / 4; });

        fresh("fire");
        quiet("fire_log_on", 5, () -> fireLog = true);
        learn(Trait.STAMP_OUT, 24, 3, () -> { cmd("setblock " + at(fire) + " fire"); tp(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5); pupHere();
            after(45, () -> { tp(fire.getX() - 2.5, fire.getY(), fire.getZ() + 0.5); server(m -> m.clearFire()); }); });  /* the game ignites a player after about a second in fire */
        ability("stamp_out_puts_it_out", () -> { tp(fire.getX() - 2.5, fire.getY(), fire.getZ() + 0.5); server(m -> { m.clearFire(); m.removeAllEffects(); m.setHealth(20); }); cmd("setblock " + at(fire) + " fire"); },
            () -> tp(fire.getX() + 0.5, fire.getY(), fire.getZ() + 0.5), 40, 2,
            "on fire / fire resistance", () -> me() == null ? "?" : me().isOnFire() + " / " + me().hasEffect(MobEffects.FIRE_RESISTANCE),
            () -> me() != null && me().hasEffect(MobEffects.FIRE_RESISTANCE));
        quiet("fire_out", 10, () -> { fireLog = false; tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); cmd("setblock " + at(fire) + " air"); server(m -> m.clearFire()); });

        fresh("freeze");
        learn(Trait.HUDDLE_UP, 20, 9, () -> { server(m -> m.setTicksFrozen(0)); tp(snow.getX() + 0.5, snow.getY(), snow.getZ() + 0.5); pupHere(); });
        ability("huddle_up_thaws", () -> { tp(snow.getX() - 2.5, snow.getY(), snow.getZ() + 0.5); server(m -> m.setTicksFrozen(0)); },
            () -> tp(snow.getX() + 0.5, snow.getY(), snow.getZ() + 0.5), 40, 0,
            "ticks frozen after 2 s in powder snow", () -> me() == null ? -1 : me().getTicksFrozen(), () -> me() != null && me().getTicksFrozen() == 0);
        quiet("out_of_snow", 10, () -> tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5));

        fresh("fall");
        learn(Trait.SOFT_LANDING, 24, 3, () -> { server(m -> m.setHealth(20)); tp(o.getX() + 0.5, o.getY() + 5, o.getZ() + 3.5); pupHere(); });
        ability("soft_landing_no_damage", () -> server(m -> m.setHealth(20)), () -> tp(o.getX() + 0.5, o.getY() + 10, o.getZ() + 3.5), 60, 2,
            "health / slow falling", () -> me() == null ? "?" : me().getHealth() + " / " + me().hasEffect(MobEffects.SLOW_FALLING),
            () -> me() != null && me().onGround() && me().getHealth() >= 20);

        fresh("drown");
        quiet("dive", 10, () -> { tp(pool.getX() + 0.5, pool.getY() - 6, pool.getZ() + 0.5); pupHere(); });
        learn(Trait.UNDERTOW, 24, 2, () -> { server(m -> m.setAirSupply(45)); tp(pool.getX() + 0.5, pool.getY() - 6, pool.getZ() + 0.5); });
        ability("undertow_waits_for_drowning", () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); server(m -> { m.removeAllEffects(); m.setAirSupply(300); }); },
            () -> { tp(pool.getX() + 0.5, pool.getY() - 6, pool.getZ() + 0.5); server(m -> m.setAirSupply(40)); pupHere(); }, 20, 0,
            "breathing before any drowning damage", () -> me() == null ? "?" : me().getAirSupply() + " air / " + me().hasEffect(MobEffects.WATER_BREATHING),
            () -> me() != null && !me().hasEffect(MobEffects.WATER_BREATHING));
        check("undertow_lifts_after_drowning_damage", 60, 2, () -> {}, "health / y (bottom " + (pool.getY() - 6) + ") / breathing",
            () -> me() == null ? "?" : me().getHealth() + " / " + me().getY() + " / " + me().hasEffect(MobEffects.WATER_BREATHING) + " | " + state(),
            () -> me() != null && me().getHealth() < 20 && me().hasEffect(MobEffects.WATER_BREATHING) && me().getY() > pool.getY() - 4.5);
        quiet("out_of_pool", 10, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); pupHere(); });

        /* ---- hand feeding: a carrot at food 9.18 (it showed an empty shank and refused); full, it says so ---- */
        quiet("almost_full", 30, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); life(d -> d.life.food = 9.18); pupHere(); hotbar(3, "carrot 2"); });
        quiet("near_to_feed", 15, () -> { select(3); near(pup()); });
        step("hand_feeds_a_carrot", 20, () -> interact("feed a carrot"),
            () -> ds() == null && pup() != null && pup().food() >= 9.9f && mc().player.getInventory().getItem(3).getCount() == 1);
        quiet("full", 20, () -> life(d -> d.life.food = 10));
        step("full_is_not_hungry", 20, () -> interact("feed when full"),
            () -> ds() == null && mc().player.getInventory().getItem(3).getCount() == 1);
        quiet("food_done", 5, () -> { if (ds() != null) mc().setScreen(null); });

        /* ---- an offer: the chat line's link opens the Traits tab, the check learns it ---- */
        fresh("offer");
        quiet("offer_xp", 20, () -> { select(8); cmd("xp add @s 3 levels"); });
        quiet("offer_chat", 20, () -> mc().setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
        step("offer_link_hovered", 20, () -> hoverAt = chatLink(true), () -> hoverAt != null && pup() != null && "training_day".equals(pup().traitView().getString("offer")));
        step("offer_link_opens_traits", 30, () -> {
            var st = mc().gui.getChat().getClickedComponentStyleAt(hoverAt[0], hoverAt[1]);
            if (mc().screen instanceof net.minecraft.client.gui.screens.ChatScreen cs && st != null) cs.handleComponentClicked(st);
        }, () -> ds() != null && ds().spot("offer_yes") != null);
        step("offer_yes_hovered", 10, () -> hoverSpot("offer_yes"), () -> ds() != null);
        step("offer_learned", 20, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; clickSpot("offer_yes"); },
            () -> has(Trait.TRAINING_DAY) && ds() != null && ds().spot("offer") == null);
        quiet("offer_close", 10, () -> mc().setScreen(null));
        quiet("offer_again", 20, () -> { life(d -> { d.life.traits.clear(); d.life.progress.clear(); }); cmd("xp add @s 3 levels"); });
        quiet("offer_near", 15, () -> near(pup()));
        step("offer_screen_again", 25, () -> interact("open its screen"), () -> ds() != null);
        step("offer_row", 10, () -> { clickSpot("tab_traits"); hoverSpot("offer"); }, () -> ds() != null && ds().spot("offer_no") != null);
        step("offer_passed", 20, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; clickSpot("offer_no"); },
            () -> pup() != null && !pup().traitView().contains("offer") && !has(Trait.TRAINING_DAY) && sd() != null && !sd().life.progress.containsKey("training_day"));
        quiet("offer_done", 10, () -> mc().setScreen(null));

        /* ---- the trait tooltips at the game's text size ---- */
        quiet("three_for_the_screen", 30, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); life(d -> { d.life.traits.clear(); d.life.traits.add("undertow"); d.life.traits.add("patchwork"); d.life.traits.add("hook_line_sinker");
            d.life.cooldowns.put("undertow", Vitals.now(d) + 1500);  /* recharging: the hourglass and the ready-in line */
            d.setLook(d.stage(), d.line(), d.diet(), dev.frosthaven.ranchercompanion.look.Astral.LUNAR, d.horn()); }); pupHere(); });
        quiet("near_for_screen", 15, () -> { select(8); near(pup()); });
        step("traits_screen", 25, () -> interact("open its screen"), () -> ds() != null);
        step("traits_tab", 10, () -> clickSpot("tab_traits"), () -> ds() != null && ds().spot("trait0") != null);
        for (int i = 0; i < 3; i++) { int k = i; step("trait_tooltip_" + k, 10, () -> hoverSpot("trait" + k), () -> ds() != null); }
        quiet("details_tab", 5, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; clickSpot("tab_details"); });
        step("mood_tooltip", 10, () -> hoverSpot("mood"), () -> ds() != null);
        step("astral_tooltip", 10, () -> hoverSpot("astral"), () -> ds() != null && ds().spot("astral") != null);
        quiet("close", 10, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; mc().setScreen(null); });

        /* ---- a Fledgling: Hearty, then Phoenix Heart (its death) ---- */
        fresh("fledgling");
        quiet("grow", 20, () -> { life(d -> { d.setLook(2, d.line(), d.diet(), d.astral(), d.horn()); d.applyStageHealth(true); }); pupHere(); });
        String id = "@e[type=rancher_companion:drekki,limit=1,sort=nearest]";
        learn(Trait.HEARTY, 8, 1, () -> { cmd("damage " + id + " 4"); life(d -> d.life.cooldowns.remove("hearty_heal"));
            after(5, () -> cmd("effect give " + id + " instant_health 1 0")); });
        check("hearty_max_health", 10, 3, () -> {}, "max health", () -> sd() == null ? -1 : sd().getMaxHealth(), () -> sd() != null && sd().getMaxHealth() == 26);

        fresh("totem");
        learn(Trait.PHOENIX_HEART, 3, 2, () -> { cmd("item replace entity @s weapon.offhand with totem_of_undying"); cmd("damage @s 100 minecraft:generic"); });
        quiet("phoenix_death", 40, () -> { server(m -> m.setHealth(20)); cmd("kill " + id); });
        check("phoenix_heart_egg", 20, 5, () -> {}, "egg in inventory", SelfTestTraits::egg, SelfTestTraits::egg);
        quiet("traits_end", 10, () -> cmd("gamemode creative"));
        /* a learned message's trait link in chat, hovered: the same lines as the screen, wrapped the same way */
        quiet("chat_open", 20, () -> mc().setScreen(new net.minecraft.client.gui.screens.ChatScreen("")));
        step("chat_trait_hover", 20, () -> {
            var chat = mc().gui.getChat(); var w = mc().getWindow();
            for (int y = w.getGuiScaledHeight() - 20; y > w.getGuiScaledHeight() - 140; y--)
                for (int x = 2; x < 320; x++) {
                    var st = chat.getClickedComponentStyleAt(x, y);
                    if (st != null && st.getHoverEvent() != null && st.getHoverEvent().getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT) != null
                        && dev.frosthaven.ranchercompanion.trait.Tips.lines(st.getHoverEvent().getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT)) != null) {
                        hoverAt = new int[]{x, y};
                        mouse(x * w.getGuiScale(), y * w.getGuiScale());
                        return;
                    }
                }
        }, () -> hoverAt != null);
        quiet("chat_close", 10, () -> mc().setScreen(null));

    }

    /** the trait screen with one trait recharging (no tooltip over the list), and a learned message's link hovered in chat */
    static void ui() {
        quiet("ui_traits", 20, () -> life(d -> { d.life.traits.clear(); d.life.traits.add("undertow"); d.life.traits.add("patchwork"); d.life.traits.add("hook_line_sinker");
            d.life.cooldowns.put("undertow", Vitals.now(d) + 1500); }));
        quiet("ui_near", 15, () -> { select(8); near(pup()); });
        step("ui_screen", 25, () -> interact("open its screen"), () -> ds() != null);
        step("ui_traits_recharging", 15, () -> { clickSpot("tab_traits"); dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = new int[]{2, 2}; },
            () -> ds() != null && pup() != null && pup().traitView().getCompound("ready").contains("undertow"));
        quiet("ui_close", 10, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; mc().setScreen(null); });
        quiet("ui_chat", 20, () -> {
            mc().gui.getChat().addMessage(Component.translatable("message.rancher_companion.trait_learned", Component.literal("Your drekki"),
                dev.frosthaven.ranchercompanion.trait.Traits.link(Trait.UNDERTOW)));
            mc().setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
        });
        step("ui_chat_hover", 20, () -> {
            var chat = mc().gui.getChat(); var w = mc().getWindow();
            for (int y = w.getGuiScaledHeight() - 14; y > w.getGuiScaledHeight() - 60; y--)
                for (int x = 2; x < 320; x++) {
                    var st = chat.getClickedComponentStyleAt(x, y);
                    var hv = st == null || st.getHoverEvent() == null ? null : st.getHoverEvent().getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT);
                    if (hv != null && dev.frosthaven.ranchercompanion.trait.Tips.lines(hv) != null) {
                        hoverAt = new int[]{x, y};
                        mouse(x * w.getGuiScale(), y * w.getGuiScale());
                        return;
                    }
                }
        }, () -> hoverAt != null);
        quiet("ui_chat_close", 10, () -> mc().setScreen(null));
    }

    /** the newest chat trait link with this mod's tooltip (clickable: the offer's) in GUI coordinates, the mouse on it */
    private static int[] chatLink(boolean clickable) {
        var chat = mc().gui.getChat(); var w = mc().getWindow();
        for (int y = w.getGuiScaledHeight() - 14; y > w.getGuiScaledHeight() - 140; y--)
            for (int x = 2; x < 320; x++) {
                var st = chat.getClickedComponentStyleAt(x, y);
                var hv = st == null || st.getHoverEvent() == null ? null : st.getHoverEvent().getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT);
                if (hv != null && dev.frosthaven.ranchercompanion.trait.Tips.lines(hv) != null && (!clickable || st.getClickEvent() != null)) {
                    mouse(x * w.getGuiScale(), y * w.getGuiScale());
                    return new int[]{x, y};
                }
            }
        return null;
    }

    /** the game's own cursor position (what screens are drawn with), set inside the game: no desktop input */
    private static void mouse(double x, double y) {
        try {
            var h = mc().mouseHandler;
            for (String f : new String[]{"xpos", "ypos"}) {
                var fld = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
                fld.setAccessible(true);
                fld.setDouble(h, f.equals("xpos") ? x : y);
            }
        } catch (ReflectiveOperationException e) { RancherCompanion.LOG.warn("[selftest] cannot set the cursor", e); }
    }

    private static boolean egg() {
        var inv = mc().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) { ItemStack s = inv.getItem(i); if (s.is(DrekkiItems.INTRIGUING_EGG.get()) && s.has(DrekkiData.CARRIED.get())) return true; }
        return false;
    }
}
