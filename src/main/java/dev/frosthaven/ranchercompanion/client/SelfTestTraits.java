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

    static BlockPos o;
    private static BlockPos pool, slime, snow, fire;
    private static boolean autoFish;
    private static int fishWait, lastLured = -1, xpBefore;
    private static long lastLuredAt;

    // ---- helpers ----

    /** the pup as the server sees it */
    static DrekkiEntity sd() {
        var srv = mc().getSingleplayerServer(); var c = pup();
        if (srv == null || c == null) return null;
        for (var l : srv.getAllLevels()) if (l.getEntity(c.getUUID()) instanceof DrekkiEntity d) return d;
        return null;
    }

    static void server(java.util.function.Consumer<ServerPlayer> f) { var m = me(); if (m != null) m.server.execute(() -> f.accept(m)); }

    static boolean has(Trait t) {
        var c = pup();
        if (c == null) return false;
        for (Tag s : c.traitView().getList("traits", Tag.TAG_STRING)) if (s.getAsString().equals(t.id)) return true;
        return false;
    }

    /** a clean slate for the next trait: no traits, no counts, no cooldowns, petted (Thriving), beside the player */
    static void fresh(String name) {
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
    static void pupHere() {
        life(d -> { var m = me(); if (m != null) d.teleportTo(m.getX() + 1.5, Math.max(m.getY(), o.getY()), m.getZ() + 1.5); });
    }

    static void tp(double x, double y, double z) { cmd("tp @s " + x + " " + y + " " + z + " -90 0"); }

    /** repeats the activity every `every` seconds until the trait is learned (at most `tries` times), so the next
     *  step starts right after it is learned */
    static void learn(Trait t, int tries, int every, Runnable act) {
        /* most traits are for some astral affinities only (spec, Traits: affinity limits): it gets one that can learn this */
        quiet("affinity_for_" + t.id, 10, () -> life(d -> {
            if (t.learnedBy(d.astral())) return;
            for (int a = 1; a <= 6; a++) if (t.learnedBy(a)) { d.setLook(d.stage(), d.line(), d.diet(), a, d.horn()); return; }
        }));
        int[] polls = {0};
        stepUntil("learns_" + t.id, 10, tries * every, act, () -> {
            if (has(t)) return true;
            if (pup() != null && t.id.equals(pup().traitView().getString("offer"))) { life(d -> dev.frosthaven.ranchercompanion.trait.Traits.answer(d, true)); return false; }
            if (++polls[0] % every == 0) act.run();
            return false;
        });
    }

    /** hotbar slot 9 emptied and selected: a right-click opens the screen (a fish left there from fishing would feed it) */
    static void emptyHand() { cmd("item replace entity @s hotbar.8 with air"); select(8); }

    /** the pup's server state, logged beside each ability check: its traits, cooldowns, and distance to the player */
    static String state() {
        DrekkiEntity d = sd(); var m = me();
        if (d == null || m == null) return "no pup";
        return "traits " + d.life.traits + " progress " + d.life.progress + " cooldowns " + d.life.cooldowns.keySet() + " (now " + Vitals.now(d) + " " + d.life.cooldowns + ") dist " + String.format("%.1f", d.distanceTo(m)) + " band " + d.band();
    }

    /** an ability, the way a player meets it after learning: cooldowns cleared, the pup beside them, then the trigger */
    static void ability(String name, Runnable reset, Runnable trigger, int wait, int patience, String what, java.util.function.Supplier<Object> seen, BooleanSupplier ok) {
        quiet(name + "_reset", 30, () -> { reset.run(); life(d -> d.life.cooldowns.clear()); pupHere(); });
        check(name, wait, patience, trigger, what, () -> seen.get() + " | " + state(), ok);
    }

    /** a check that logs what it saw */
    static void check(String name, int delay, int patience, Runnable act, String what, java.util.function.Supplier<Object> seen, BooleanSupplier ok) {
        stepUntil(name, delay, patience, act, () -> { boolean r = ok.getAsBoolean(); RancherCompanion.LOG.info("[selftest] {}: {} = {}", name, what, seen.get()); return r; });
    }

    static Entity nearest(String type) {
        Entity best = null; var p = mc().player;
        for (Entity e : mc().level.getEntities(null, new AABB(p.blockPosition()).inflate(12)))
            if (e.isAlive() && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type) && (best == null || e.distanceTo(p) < best.distanceTo(p))) best = e;
        return best;
    }

    static Mob serverMob(String type) {
        var m = me();
        if (m == null) return null;
        for (Entity e : m.serverLevel().getEntities((Entity) null, m.getBoundingBox().inflate(24), e -> e.isAlive() && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type)))
            if (e instanceof Mob mob) return mob;
        return null;
    }

    /** a real melee kill: a 1-health mob summoned in front, hit with the sword in hotbar slot 5 */
    static Runnable kill(String type, double ahead) {
        return () -> {
            select(5);
            cmd("summon " + type + " ^ ^ ^" + ahead + " {NoAI:1b,Health:1f,PersistenceRequired:1b,IsImmuneToZombification:1b}");
            after(6, () -> { Entity e = nearest(type); if (e != null) mc().gameMode.attack(mc().player, e); });
        };
    }

    /** actions due a number of client ticks from now */
    private static final java.util.List<Object[]> DUE = new java.util.ArrayList<>();
    static void after(int ticks, Runnable r) { DUE.add(new Object[]{ticks, r}); }
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

    /** the bare pad 4000 blocks east, for the 0.5.0 section run by itself (SelfTestNewTraits) */
    static void site(BlockPos origin) {
        o = origin.offset(4000, 0, 0);
        quiet("new_site", 60, () -> {
            cmd("forceload add " + (o.getX() - 12) + " " + (o.getZ() - 12) + " " + (o.getX() + 12) + " " + (o.getZ() + 12));
            cmd("fill " + at(o.offset(-12, -1, -12)) + " " + at(o.offset(12, -1, 12)) + " stone");
            cmd("fill " + at(o.offset(-12, 0, -12)) + " " + at(o.offset(12, 12, 12)) + " air");
            cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5) + " -90 0");
        });
        quiet("new_site_rules", 40, () -> {
            cmd("gamemode survival"); cmd("weather clear"); cmd("difficulty normal"); cmd("gamerule doFireTick false");
            hotbar(5, "netherite_sword");
        });
        stepUntil("pup_follows_to_the_site", 20, 15, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 8);
    }

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
        ability("balm_salve_blocks_poison", () -> server(m -> m.removeAllEffects()), () -> { cmd("effect give @s poison 30"); after(20, () -> cmd("effect give @s poison 30")); }, 30, 2,  /* applied again 1 s later, as a cloud would */
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
            () -> me() != null && me().hasEffect(MobEffects.FIRE_RESISTANCE) && !me().isOnFire());  /* standing in the fire still: kept put out */
        quiet("fire_out", 10, () -> { fireLog = false; tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); cmd("setblock " + at(fire) + " air"); server(m -> m.clearFire()); });

        fresh("freeze");
        learn(Trait.HUDDLE_UP, 20, 9, () -> { server(m -> m.setTicksFrozen(0)); tp(snow.getX() + 0.5, snow.getY(), snow.getZ() + 0.5); pupHere(); });
        ability("huddle_up_thaws", () -> { tp(snow.getX() - 2.5, snow.getY(), snow.getZ() + 0.5); server(m -> m.setTicksFrozen(0)); },
            () -> tp(snow.getX() + 0.5, snow.getY(), snow.getZ() + 0.5), 40, 0,
            "ticks frozen after 2 s in powder snow", () -> me() == null ? -1 : me().getTicksFrozen(), () -> me() != null && me().getTicksFrozen() == 0);
        quiet("out_of_snow", 10, () -> tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5));

        /* Duck & Cover: arrows stopped by a raised shield teach it; then an arrow bounces off with no shield */
        fresh("arrows");
        quiet("shield_up", 20, () -> { cmd("item replace entity @s weapon.offhand with shield"); emptyHand();
            tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); cmd("tp @s ~ ~ ~ 180 0"); pupHere(); mc().options.keyUse.setDown(true); });
        learn(Trait.DUCK_COVER, 20, 6, () -> cmd("summon arrow " + (o.getX() + 0.5) + " " + (o.getY() + 1.5) + " " + (o.getZ() - 3.5) + " {Motion:[0.0,0.05,1.2d],pickup:0b}"));
        quiet("shield_down", 10, () -> { mc().options.keyUse.setDown(false); cmd("item replace entity @s weapon.offhand with air"); });
        ability("duck_cover_bounces_an_arrow", () -> { server(m -> m.setHealth(20)); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); cmd("tp @s ~ ~ ~ 180 0"); },
            () -> cmd("summon arrow " + (o.getX() + 0.5) + " " + (o.getY() + 1.5) + " " + (o.getZ() - 3.5) + " {Motion:[0.0,0.05,1.2d],pickup:0b}"), 15, 0,
            "health / arrow moving away", () -> { Entity a = nearest("arrow"); return (me() == null ? -1 : me().getHealth()) + " / " + (a == null ? "none" : a.getDeltaMovement().z); },
            () -> me() != null && me().getHealth() == 20 && nearest("arrow") != null && nearest("arrow").getDeltaMovement().z < 0);
        check("duck_cover_recharging_next_hits", 10, 2, () -> cmd("summon arrow " + (o.getX() + 0.5) + " " + (o.getY() + 1.5) + " " + (o.getZ() - 3.5) + " {Motion:[0.0,0.05,1.2d],pickup:0b}"),
            "health", () -> me() == null ? -1 : me().getHealth(), () -> me() != null && me().getHealth() < 20);
        quiet("duck_screen_setup", 30, () -> { cmd("kill @e[type=arrow,distance=..16]"); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
            life(d -> d.life.cooldowns.put(Trait.DUCK_COVER.id, Vitals.now(d) + 1500)); pupHere(); });
        quiet("duck_screen_near", 15, () -> { emptyHand(); near(pup()); });
        step("duck_cover_screen", 25, () -> interact("open its screen"), () -> ds() != null);
        step("duck_cover_tab", 10, () -> clickSpot("tab_traits"), () -> ds() != null && ds().spot("trait0") != null);
        step("duck_cover_tooltip", 10, () -> hoverSpot("trait0"), () -> ds() != null);
        quiet("duck_close", 10, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; mc().setScreen(null); });

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
        quiet("offer_near", 15, () -> { emptyHand(); near(pup()); });
        step("offer_screen_again", 25, () -> interact("open its screen"), () -> ds() != null);
        step("offer_row", 10, () -> { clickSpot("tab_traits"); hoverSpot("offer"); }, () -> ds() != null && ds().spot("offer_no") != null);
        step("offer_passed", 20, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; clickSpot("offer_no"); },
            () -> pup() != null && !pup().traitView().contains("offer") && !has(Trait.TRAINING_DAY) && sd() != null && !sd().life.progress.containsKey("training_day"));
        quiet("offer_done", 10, () -> mc().setScreen(null));

        /* ---- the trait tooltips at the game's text size ---- */
        quiet("three_for_the_screen", 30, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); life(d -> { d.life.traits.clear(); d.life.traits.add("undertow"); d.life.traits.add("patchwork"); d.life.traits.add("hook_line_sinker");
            d.life.cooldowns.put("undertow", Vitals.now(d) + 1500);  /* recharging: the hourglass and the ready-in line */
            d.setLook(d.stage(), d.line(), d.diet(), dev.frosthaven.ranchercompanion.look.Astral.LUNAR, d.horn()); }); pupHere(); });
        quiet("near_for_screen", 15, () -> { emptyHand(); near(pup()); });
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
        check("hearty_max_health", 10, 3, () -> {}, "max health", () -> sd() == null ? -1 : sd().getMaxHealth(), () -> sd() != null && sd().getMaxHealth() == 36);

        fresh("totem");
        learn(Trait.PHOENIX_HEART, 3, 2, () -> { cmd("item replace entity @s weapon.offhand with totem_of_undying"); cmd("damage @s 100 minecraft:generic"); });
        quiet("phoenix_death", 40, () -> { server(m -> m.setHealth(20)); cmd("kill " + id); });
        check("phoenix_heart_egg", 20, 5, () -> {}, "egg in inventory", SelfTestTraits::egg, SelfTestTraits::egg);
        SelfTestNewTraits.steps();  /* 0.5.0: the potion, the new traits, the void */
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
        quiet("ui_near", 15, () -> { emptyHand(); near(pup()); });
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

    /** a right click on a spot of the drekki screen */
    private static void rightClickSpot(String key) {
        var ds = ds(); if (ds == null || ds.spot(key) == null) return;
        int[] c = ds.spot(key); ds.mouseClicked(c[0], c[1], 1);
    }

    /** a plain click on a menu slot (button 0 left, 1 right), as a player does */
    private static void click(int slot, int button) {
        if (mc().player != null && mc().gameMode != null)
            mc().gameMode.handleInventoryMouseClick(mc().player.containerMenu.containerId, slot, button, net.minecraft.world.inventory.ClickType.PICKUP, mc().player);
    }

    private static int countOf(net.neoforged.neoforge.items.IItemHandler h, net.minecraft.world.item.Item item) {
        int n = 0; for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).is(item)) n += h.getStackInSlot(i).getCount(); return n;
    }

    /** shift-click a menu slot, as a player does */
    private static void shiftClick(int slot) {
        if (mc().player != null && mc().gameMode != null)
            mc().gameMode.handleInventoryMouseClick(mc().player.containerMenu.containerId, slot, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc().player);
    }

    /** a recipe's result for a grid, from the server's recipe manager */
    private static ItemStack craft(net.minecraft.world.item.crafting.CraftingInput in) {
        var srv = mc().getSingleplayerServer(); if (srv == null) return ItemStack.EMPTY;
        var l = srv.overworld();
        return l.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, in, l).map(h -> h.value().assemble(in, l.registryAccess())).orElse(ItemStack.EMPTY);
    }

    /** -Drancher_companion.selftest.gear: barding and bags in the real game (spec, Barding; Accessory: bags; Dyeing) with
     *  whatever storage mod is installed: the screen's slots by shift-click, the bag screen, a hit into the barding, the
     *  dye recipes, and the look at both stages */
    static void gear(BlockPos site) {
        o = site;
        String id = "@e[type=rancher_companion:drekki,limit=1,sort=nearest]";
        quiet("gear_fledgling", 30, () -> { tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
            life(d -> { d.setLook(2, 32, 0, 2, 0); d.applyStageHealth(true); d.teleportTo(o.getX() + 2.5, o.getY(), o.getZ() + 0.5); });
            hotbar(0, "rancher_companion:fledgling_iron_barding"); hotbar(1, "rancher_companion:satchel"); hotbar(2, "minecraft:apple 5"); hotbar(3, "rancher_companion:iron_barding"); });
        quiet("gear_near", 15, () -> { emptyHand(); near(pup()); });
        step("gear_screen_empty", 25, () -> interact("open its screen"), () -> ds() != null && ds().getMenu().gear);
        step("gear_wrong_stage_refused", 10, () -> shiftClick(3 + 27 + 3), () -> pup() != null && pup().barding() == 0);
        step("gear_barding_on", 15, () -> shiftClick(3 + 27), () -> pup() != null && pup().barding() == 4);
        step("gear_satchel_on", 15, () -> shiftClick(3 + 27 + 1), () -> pup() != null && pup().bag() == 1);
        step("gear_bag_tooltip", 10, () -> hoverSpot("accessory"), () -> ds() != null);
        boolean soph = dev.frosthaven.ranchercompanion.item.Bags.sophisticated();
        step("gear_bag_opens", 20, () -> rightClickSpot("accessory"), () -> mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.BagScreen
            || soph && mc().screen != null && mc().screen.getClass().getSimpleName().equals("SophBagScreen"));
        if (!soph) step("gear_apples_in", 20, () -> shiftClick(9 + 27 + 2), () -> sd() != null && sd().bagStorage() != null && hasItem(sd().bagStorage(), Items.APPLE));
        else {
            /* Sophisticated: a feeding upgrade in the bag's upgrade slot, carrots in the bag, a hungry Fledgling eats; the
               screen again with the upgrade and its tab */
            quiet("gear_soph_close", 10, () -> mc().setScreen(null));
            quiet("gear_soph_feeding", 20, () -> life(d -> {
                dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.installForTest(d.accessory(), "sophisticatedbackpacks:feeding_upgrade");
                d.bagStorage().insertItem(0, new ItemStack(Items.BEEF, 4), false);
                d.life.food = 5; }));
            stepUntil("gear_soph_eats_with_feeding", 10, 20, () -> {}, () -> sd() != null && sd().life.food > 5.5);
            /* the feeding upgrade feeds the drekki only: a hungry player right beside it gets nothing from the bag */
            /* a control first: the same hungry player far from the drekki, so the pack's own hunger rules are known */
            quiet("gear_soph_survival", 15, () -> cmd("gamemode survival"));
            quiet("gear_soph_far_hungry", 10, () -> { life(d -> d.teleportTo(o.getX() + 6.5, o.getY(), o.getZ() + 6.5)); cmd("tp @s " + (o.getX() - 7.5) + " " + o.getY() + " " + (o.getZ() - 7.5)); });  /* on the platform, well past the 3-block reach */
            quiet("gear_soph_far_set", 5, () -> server(m -> m.getFoodData().setFoodLevel(6)));
            quiet("gear_soph_far_wait", 120, () -> {});
            quiet("gear_soph_far_read", 5, () -> farFood = me() == null ? -1 : me().getFoodData().getFoodLevel());
            quiet("gear_soph_near_hungry", 15, () -> near(pup()));
            quiet("gear_soph_near_set", 5, () -> server(m -> m.getFoodData().setFoodLevel(6)));
            quiet("gear_soph_full_drekki", 5, () -> life(d -> { d.life.food = 10; beefBefore = countOf(d.bagStorage(), Items.BEEF); }));
            check("gear_soph_player_not_fed", 120, 0, () -> {}, "beef in the bag before and after a hungry player stood by a full drekki (equal = it fed nobody)",
                () -> beefBefore + " -> " + count("minecraft:beef") + " (player food " + (me() == null ? "-" : me().getFoodData().getFoodLevel()) + ", far " + farFood + ")",
                () -> beefBefore > 0 && count("minecraft:beef") == beefBefore);
            /* and the reason: on a drekki the feeding upgrade never runs its own tick, the one that feeds players nearby */
            check("gear_soph_feeding_never_ticked", 5, 0, () -> {}, "the worn bag's feeding upgrade cooldown (0 = its tick never ran)",
                () -> sd() == null ? "-" : String.valueOf(dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.feedingNeverTicked(sd().accessory())),
                () -> sd() != null && dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.feedingNeverTicked(sd().accessory()));
            quiet("gear_soph_creative_again", 5, () -> { cmd("gamemode creative"); server(m -> m.getFoodData().setFoodLevel(20)); });
            quiet("gear_soph_near", 10, () -> near(pup()));
            step("gear_soph_screen_again", 25, () -> interact("open its screen"), () -> ds() != null);
            step("gear_soph_bag_with_upgrade", 20, () -> rightClickSpot("accessory"), () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("SophBagScreen"));
            /* Sophisticated's stashing works on the worn bag: apples dropped on it (right-click with them on the cursor), and
               the bag on the cursor right-clicking apples in the inventory */
            quiet("gear_soph_stash_close", 10, () -> mc().setScreen(null));
            quiet("gear_soph_stash_give", 15, () -> { hotbar(4, "minecraft:apple 3"); hotbar(5, "minecraft:apple 2"); near(pup()); });
            step("gear_soph_stash_screen", 25, () -> interact("open its screen"), () -> ds() != null);
            step("gear_soph_drop_onto_bag", 20, () -> { click(3 + 27 + 4, 0); click(2, 1); },
                () -> sd() != null && sd().bagStorage() != null && countOf(sd().bagStorage(), Items.APPLE) >= 3);
            quiet("gear_soph_put_down", 10, () -> { if (!mc().player.containerMenu.getCarried().isEmpty()) click(3 + 27 + 4, 0); });
            step("gear_soph_bag_on_cursor", 20, () -> { click(2, 0); click(3 + 27 + 5, 1); },
                () -> !mc().player.containerMenu.getCarried().isEmpty() && mc().player.getInventory().getItem(5).isEmpty());
            step("gear_soph_bag_back", 15, () -> click(2, 0), () -> pup() != null && pup().bag() == 1 && mc().player.containerMenu.getCarried().isEmpty()
                && sd() != null && sd().bagStorage() != null && countOf(sd().bagStorage(), Items.APPLE) >= 5);
            /* Sophisticated's Open Backpack key over the worn bag in the drekki screen opens it */
            step("gear_soph_key_in_screen", 20, () -> dev.frosthaven.ranchercompanion.compat.sophisticated.client.SophKeys.pressInScreenForTest(
                    mc().player.containerMenu.slots.get(2)),
                () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("SophBagScreen"));
            /* and over a bag in one's own inventory, as over a backpack */
            quiet("gear_soph_key_inv_close", 10, () -> mc().setScreen(null));
            quiet("gear_soph_key_inv_give", 10, () -> { hotbar(6, "rancher_companion:satchel"); hotbar(7, "rancher_companion:netherite_saddlebag");
                hotbar(8, "rancher_companion:small_drekki_carrier"); hotbar(3, "rancher_companion:large_drekki_carrier"); });
            quiet("gear_soph_key_inv_open", 10, () -> mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player)));
            step("gear_icons_3d", 10, () -> {}, () -> mc().player.getInventory().getItem(8).is(DrekkiItems.SMALL_CARRIER.get()));  /* the 3D bag and carrier icons */
            step("gear_soph_key_in_inventory", 20, () -> dev.frosthaven.ranchercompanion.compat.sophisticated.client.SophKeys.pressInScreenForTest(
                    mc().player.inventoryMenu.slots.stream().filter(sl -> sl.container == mc().player.getInventory() && sl.getContainerSlot() == 6).findFirst().orElseThrow()),
                () -> mc().screen != null && mc().screen.getClass().getName().contains("sophisticatedbackpacks"));
            quiet("gear_soph_key_inv_clear", 10, () -> { mc().setScreen(null); for (int h : new int[]{3, 6, 7, 8}) hotbar(h, "minecraft:air"); });
            /* Sophisticated's own Open Backpack key, looking at the drekki, opens its bag too */
            quiet("gear_soph_key_close", 10, () -> mc().setScreen(null));
            quiet("gear_soph_key_aim", 15, () -> { var d = pup(); if (d != null) cmd("tp @s " + (d.getX() + 2.5) + " " + d.getY() + " " + d.getZ() + " 90 20"); });
            step("gear_soph_key_opens", 20, dev.frosthaven.ranchercompanion.compat.sophisticated.client.SophKeys::pressForTest,
                () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("SophBagScreen"));
        }
        quiet("gear_close", 10, () -> mc().setScreen(null));
        check("gear_hit_into_barding", 10, 3, () -> { server(m -> { var d = sd(); if (d != null) { d.invulnerableTime = 0; d.hurt(d.damageSources().generic(), 10); } }); },
            "barding wear and health", () -> sd() == null ? "-" : sd().bardingItem().getDamageValue() + " " + sd().getHealth(),
            () -> sd() != null && sd().bardingItem().getDamageValue() == 10 && sd().getHealth() == sd().getMaxHealth());
        check("gear_dye_barding", 5, 0, () -> {}, "barding dyed red left, blue right", () -> {
                var r = craft(net.minecraft.world.item.crafting.CraftingInput.of(3, 1, java.util.List.of(new ItemStack(Items.RED_DYE), new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get()), new ItemStack(Items.BLUE_DYE))));
                return Integer.toHexString(dev.frosthaven.ranchercompanion.item.Dyeing.main(r)) + "/" + Integer.toHexString(dev.frosthaven.ranchercompanion.item.Dyeing.trim(r)); },
            () -> { var r = craft(net.minecraft.world.item.crafting.CraftingInput.of(3, 1, java.util.List.of(new ItemStack(Items.RED_DYE), new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get()), new ItemStack(Items.BLUE_DYE))));
                return dev.frosthaven.ranchercompanion.item.Dyeing.main(r) >= 0 && dev.frosthaven.ranchercompanion.item.Dyeing.trim(r) >= 0 && dev.frosthaven.ranchercompanion.item.Dyeing.main(r) != dev.frosthaven.ranchercompanion.item.Dyeing.trim(r); });
        check("gear_dye_satchel", 5, 0, () -> {}, "satchel dyed (Sophisticated's recipe when it is installed)", () -> {
                var r = craft(net.minecraft.world.item.crafting.CraftingInput.of(3, 1, java.util.List.of(new ItemStack(Items.LIME_DYE), new ItemStack(DrekkiItems.BAGS.get(0).get()), new ItemStack(Items.PURPLE_DYE))));
                return r.getItem() + " " + Integer.toHexString(dev.frosthaven.ranchercompanion.item.Dyeing.main(r)) + "/" + Integer.toHexString(dev.frosthaven.ranchercompanion.item.Dyeing.trim(r)); },
            () -> { var r = craft(net.minecraft.world.item.crafting.CraftingInput.of(3, 1, java.util.List.of(new ItemStack(Items.LIME_DYE), new ItemStack(DrekkiItems.BAGS.get(0).get()), new ItemStack(Items.PURPLE_DYE))));
                return dev.frosthaven.ranchercompanion.item.Dyeing.main(r) >= 0 && dev.frosthaven.ranchercompanion.item.Dyeing.trim(r) >= 0; });
        lookAt("gear_fledgling", 1.0);
        /* a Mature: Mature barding and an iron saddlebag, dyed */
        quiet("gear_mature", 30, () -> { cmd("gamemode creative"); life(d -> {
            d.setNoAi(false); d.setBardingItem(ItemStack.EMPTY); d.setLook(3, 32, 0, 2, 0); d.applyStageHealth(true);
            ItemStack bard = new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.DIAMOND).get());
            dev.frosthaven.ranchercompanion.item.Dyeing.set(bard, -1, 0x3c44aa); d.setBardingItem(bard);
            d.setAccessory(new ItemStack(DrekkiItems.BAGS.get(2).get())); d.teleportTo(o.getX() + 2.5, o.getY(), o.getZ() + 0.5); }); });
        quiet("gear_mature_near", 15, () -> { emptyHand(); near(pup()); });
        step("gear_mature_screen", 25, () -> interact("open its screen"), () -> ds() != null && pup() != null && pup().bag() == 3 && pup().barding() == 6);
        if (dev.frosthaven.ranchercompanion.item.Bags.sophisticated()) {
            /* Sophisticated's rule for packs in packs: refused by default, taken with an Inception upgrade in the outer one */
            check("gear_soph_no_backpack_in_bag", 10, 0, () -> {}, "a Sophisticated backpack into the saddlebag (refused)",
                () -> sd() == null ? "-" : String.valueOf(sd().bagStorage().insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("sophisticatedbackpacks:backpack"))), true).isEmpty()),
                () -> sd() != null && !sd().bagStorage().insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("sophisticatedbackpacks:backpack"))), true).isEmpty()
                    && !sd().bagStorage().insertItem(0, new ItemStack(DrekkiItems.BAGS.get(0).get()), true).isEmpty());
            quiet("gear_soph_inception", 10, () -> life(d -> dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.installForTest(d.accessory(), "sophisticatedbackpacks:inception_upgrade", 0)));
            check("gear_soph_inception_takes_backpack", 10, 0, () -> {}, "a Sophisticated backpack and a satchel into the saddlebag with Inception (taken)",
                () -> sd() == null ? "-" : sd().bagStorage().insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("sophisticatedbackpacks:backpack"))), true).isEmpty()
                    + " " + sd().bagStorage().insertItem(1, new ItemStack(DrekkiItems.BAGS.get(0).get()), true).isEmpty(),
                () -> sd() != null && sd().bagStorage().insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("sophisticatedbackpacks:backpack"))), true).isEmpty()
                    && sd().bagStorage().insertItem(1, new ItemStack(DrekkiItems.BAGS.get(0).get()), true).isEmpty());
        }
        step("gear_mature_bag", 20, () -> rightClickSpot("accessory"), () -> mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.BagScreen bs && bs.getMenu().rows == 4
            || mc().screen != null && mc().screen.getClass().getSimpleName().equals("SophBagScreen"));
        quiet("gear_mature_close", 10, () -> mc().setScreen(null));
        lookAt("gear_mature", 1.7);
        if (dev.frosthaven.ranchercompanion.item.Bags.sophisticated()) { upgrades(); placed(); }
        tomb();
        bleach();
    }

    /** the Haven pack's bleach (haven_compat, when installed) takes both dyes off barding and a bag */
    private static String bleached = "";

    private static void bleach() {
        /* the 3D barding heaps in the inventory: undyed, dyed two colors, Fledgling and Mature */
        quiet("barding_icons_give", 15, () -> { cmd("gamemode survival"); server(m -> {
            var inv = m.getInventory(); inv.clearContent();
            ItemStack a = new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get());
            ItemStack b = a.copy(); dev.frosthaven.ranchercompanion.item.Dyeing.set(b, 0xb02e26, 0x3c44aa);
            ItemStack c = new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.GOLD).get());
            ItemStack d = new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.NETHERITE).get());
            ItemStack e = new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.DIAMOND).get()); dev.frosthaven.ranchercompanion.item.Dyeing.set(e, 0x80c71f, 0xf9fffe);
            ItemStack f = new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.WOOD).get());
            ItemStack g = new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.LEATHER).get());
            ItemStack h = new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.STONE).get());
            ItemStack[] all = {a, b, c, d, e, f, g, h};
            for (int i = 0; i < all.length; i++) { inv.setItem(i, all[i]); inv.setItem(9 + i, all[i].copy()); } }); });
        quiet("barding_icons_open", 15, () -> mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player)));
        step("barding_icons_3d", 15, () -> {}, () -> mc().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen);
        quiet("barding_icons_close", 10, () -> { mc().setScreen(null); cmd("gamemode creative"); });
        /* in the world: Fledgling (small) and Mature (large) barding in item frames on a wall, and lying on the ground */
        quiet("barding_world_setup", 20, () -> server(m -> {
            var l = m.level(); BlockPos b = o.offset(9, 0, -9);
            for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) l.setBlockAndUpdate(b.offset(x, y, -1), net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState());
            for (int x = -2; x <= 2; x++) for (int z = 0; z <= 3; z++) for (int y = 0; y <= 2; y++) if (!(z == 0 && y == 0)) l.setBlockAndUpdate(b.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            ItemStack small = new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get());
            ItemStack large = new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.GOLD).get());
            ItemStack smallDyed = small.copy(); dev.frosthaven.ranchercompanion.item.Dyeing.set(smallDyed, 0xb02e26, 0x3c44aa);
            ItemStack largeDyed = new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.DIAMOND).get()); dev.frosthaven.ranchercompanion.item.Dyeing.set(largeDyed, 0x80c71f, 0xf9fffe);
            int i = 0;
            for (ItemStack it : new ItemStack[]{small, large, smallDyed, largeDyed}) {
                var f = new net.minecraft.world.entity.decoration.ItemFrame(l, b.offset(-1 + (i % 2) * 2 + (i / 2 == 1 ? 0 : 0), 1 + (i / 2 == 0 ? 1 : 0), 0), Direction.SOUTH);
                f.setItem(it.copy(), false); l.addFreshEntity(f);
                var e = new net.minecraft.world.entity.item.ItemEntity(l, b.getX() - 1.2 + i * 0.8 + 0.5, b.getY() + 0.05, b.getZ() + 1.6, it.copy(), 0, 0, 0);
                e.setPickUpDelay(Short.MAX_VALUE); e.setUnlimitedLifetime(); e.setNoGravity(true); l.addFreshEntity(e);
                i++;
            }
            m.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 3.6); m.setYRot(180); m.setXRot(22); }));
        quiet("barding_world_look", 25, () -> cmd("tp @s " + (o.getX() + 9.5) + " " + o.getY() + " " + (o.getZ() - 9 + 3.6) + " 180 22"));
        step("barding_world_frames_and_ground", 20, () -> {}, () -> true);
        quiet("barding_world_down", 20, () -> cmd("tp @s " + (o.getX() + 9.5) + " " + (o.getY() + 0.4) + " " + (o.getZ() - 9 + 3.0) + " 180 55"));
        step("barding_world_ground_close", 20, () -> {}, () -> true);
        if (!net.neoforged.fml.ModList.get().isLoaded("haven_compat")) return;
        check("bleach_dyeables", 10, 3, () -> server(m -> {
                StringBuilder out = new StringBuilder();
                for (ItemStack s : java.util.List.of(new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get()),
                        new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.GOLD).get()), new ItemStack(DrekkiItems.BAGS.get(0).get()),
                        new ItemStack(DrekkiItems.BAGS.get(5).get()))) {
                    if (s.getItem() instanceof dev.frosthaven.ranchercompanion.item.DrekkiBag && dev.frosthaven.ranchercompanion.item.Bags.sophisticated())
                        dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.dyeForTest(s, 0xff0000, 0x0000ff);
                    else dev.frosthaven.ranchercompanion.item.Dyeing.set(s, 0xff0000, 0x0000ff);
                    try {
                        var r = (java.util.List<?>) Class.forName("dev.frosthaven.havencompat.bleach.BleachProcess")
                            .getMethod("process", ItemStack.class, net.minecraft.world.level.Level.class).invoke(null, s, m.level());
                        ItemStack b = r == null || r.isEmpty() ? ItemStack.EMPTY : (ItemStack) r.get(0);
                        out.append(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()).append(':')
                            .append(b.isEmpty() ? "unchanged" : dev.frosthaven.ranchercompanion.item.Dyeing.main(b) + "/" + dev.frosthaven.ranchercompanion.item.Dyeing.trim(b)).append(' ');
                    } catch (ReflectiveOperationException e) { out.append(e).append(' '); }
                }
                bleached = out.toString().trim(); }),
            "each item after bleach (main/trim, -1 is undyed)", () -> bleached,
            () -> !bleached.isEmpty() && !bleached.contains("unchanged") && !bleached.contains("Exception") && bleached.split("-1/-1", -1).length == 5);
    }

    private static net.minecraft.world.item.Item item(String id) { return BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)); }

    private static int count(String id) {
        var d = sd(); if (d == null || d.bagStorage() == null) return -1;
        return countOf(d.bagStorage(), item(id));
    }

    /** Sophisticated's upgrades on a bag a Mature wears: every upgrade accepted or refused as a backpack of the same tier
     *  does, then the ones that act on their own working where the drekki stands (magnet, compacting, auto-smelting,
     *  stack), the screen with their tabs, and one held in a player's inventory (pickup) */
    private static void upgrades() {
        quiet("upg_bag", 20, () -> { cmd("gamemode creative"); mc().options.hideGui = false;
            life(d -> { d.setNoAi(false); d.setAccessory(new ItemStack(DrekkiItems.BAGS.get(5).get())); d.teleportTo(o.getX() + 2.5, o.getY(), o.getZ() + 0.5); }); });
        check("upg_parity", 10, 0, () -> {}, "netherite saddlebag vs Sophisticated's netherite backpack",
            () -> sd() == null ? "-" : dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.upgradeParity(sd().accessory(), new ItemStack(item("sophisticatedbackpacks:netherite_backpack"))),
            () -> sd() != null && dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.upgradeParity(sd().accessory(), new ItemStack(item("sophisticatedbackpacks:netherite_backpack"))).endsWith("none"));
        quiet("upg_install", 15, () -> life(d -> {
            String[] ups = {"magnet_upgrade", "advanced_compacting_upgrade", "auto_smelting_upgrade", "stack_upgrade_tier_1"};
            for (int i = 0; i < ups.length; i++) dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.installForTest(d.accessory(), "sophisticatedbackpacks:" + ups[i], i);
            dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.openFiltersForTest(d.accessory()); }));  /* their filters start as an empty allow list */
        check("upg_stack", 10, 0, () -> {}, "slot limit with a stack upgrade (over 64)",
            () -> sd() == null ? "-" : sd().bagStorage().getSlotLimit(0), () -> sd() != null && sd().bagStorage().getSlotLimit(0) > 64);
        /* the advanced compacting upgrade does 3x3 recipes that undo: nine nuggets make an ingot */
        check("upg_compacting", 10, 3, () -> life(d -> d.bagStorage().insertItem(10, new ItemStack(Items.IRON_NUGGET, 9), false)),
            "9 nuggets in: an ingot", () -> count("minecraft:iron_ingot") + " ingots, " + count("minecraft:iron_nugget") + " nuggets", () -> count("minecraft:iron_ingot") >= 1);
        quiet("upg_smelt_in", 10, () -> life(d -> {
            d.bagStorage().insertItem(20, new ItemStack(Items.RAW_GOLD, 2), false); d.bagStorage().insertItem(21, new ItemStack(Items.COAL, 2), false); }));
        stepUntil("upg_auto_smelting", 20, 20, () -> {}, () -> count("minecraft:gold_ingot") >= 1);  /* 20 tries a second apart: a smelt takes 10 s */
        quiet("upg_magnet_drop", 10, () -> server(m -> { var d = sd(); if (d != null) {
            var e = new net.minecraft.world.entity.item.ItemEntity(m.level(), d.getX() + 2, d.getY() + 0.5, d.getZ(), new ItemStack(Items.EMERALD, 3));  /* the basic magnet reaches 3 blocks */
            m.level().addFreshEntity(e); } }));
        stepUntil("upg_magnet", 10, 30, () -> {}, () -> count("minecraft:emerald") >= 3);
        quiet("upg_near", 15, () -> { emptyHand(); near(pup()); });
        step("upg_screen", 25, () -> interact("open its screen"), () -> ds() != null);
        step("upg_tabs", 25, () -> rightClickSpot("accessory"), () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("SophBagScreen"));
        quiet("upg_close", 10, () -> mc().setScreen(null));
        /* held: a satchel with a pickup upgrade in the player's inventory picks up as a backpack does */
        quiet("upg_held_away", 15, () -> cmd("tp @s " + (o.getX() - 7.5) + " " + o.getY() + " " + (o.getZ() - 7.5)));  /* away from the drekki's magnet */
        quiet("upg_held", 15, () -> server(m -> {
            ItemStack bag = new ItemStack(DrekkiItems.BAGS.get(0).get());
            dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.installForTest(bag, "sophisticatedbackpacks:pickup_upgrade", 0);
            dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.openFiltersForTest(bag);
            m.getInventory().setItem(7, bag);  /* ours alone (a backpack earlier in the inventory takes the pickup first) */
            for (int i = 0; i < 36; i++) if (i != 7 && i != 6 && m.getInventory().getItem(i).isEmpty()) m.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));  /* a full inventory: the bag must take it */
            var e = new net.minecraft.world.entity.item.ItemEntity(m.level(), m.getX(), m.getY() + 0.5, m.getZ(), new ItemStack(Items.LAPIS_LAZULI, 5));
            m.level().addFreshEntity(e); }));
        check("upg_held_pickup", 40, 3, () -> {}, "lapis picked into the held satchel", () -> heldLapis(7), () -> heldLapis(7) >= 5);
        quiet("upg_held_clear", 10, () -> cmd("clear @s minecraft:dirt"));
    }

    private static int heldLapis(int slot) {
        var m = me(); if (m == null) return -1;
        ItemStack bag = m.getInventory().getItem(slot);
        if (bag.isEmpty()) return -2;
        return countOf(dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.handler(bag), Items.LAPIS_LAZULI);
    }

    /** a satchel placed in the world (Sophisticated's backpack block), carried round on a Create bearing with its colors,
     *  then broken back into the same satchel with what it holds */
    private static void placed() {
        BlockPos bearing = o.offset(-6, 0, 4), bagAt = bearing.above();
        quiet("place_setup", 30, () -> { cmd("gamemode creative"); mc().options.hideGui = false;
            cmd("setblock " + at(bearing.below()) + " create:creative_motor[facing=up]");
            cmd("setblock " + at(bearing) + " create:mechanical_bearing[facing=up]"); });
        quiet("place_bag", 20, () -> {
            server(m -> {
                ItemStack bag = new ItemStack(DrekkiItems.BAGS.get(0).get());
                dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.dyeForTest(bag, 0x80c71f, 0x8932b8);
                m.setItemInHand(InteractionHand.MAIN_HAND, bag); m.setShiftKeyDown(true);
                var hit = new net.minecraft.world.phys.BlockHitResult(bearing.getCenter().add(0, 0.5, 0), Direction.UP, bearing, false);
                bag.useOn(new net.minecraft.world.item.context.UseOnContext(m, InteractionHand.MAIN_HAND, hit));
                m.setShiftKeyDown(false);
                m.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);  /* an empty hand assembles the bearing */
            });
            cmd("tp @s " + (bearing.getX() + 3.5) + " " + (bearing.getY() + 1) + " " + (bearing.getZ() + 0.5) + " 90 20"); });
        check("place_is_a_backpack_block", 10, 3, () -> {}, "the satchel placed as our satchel block (Sophisticated's backpack block entity)",
            () -> { var l = mc().getSingleplayerServer() == null ? null : mc().getSingleplayerServer().overworld(); return l == null ? "-" : l.getBlockState(bagAt).toString(); },
            () -> { var l = mc().getSingleplayerServer() == null ? null : mc().getSingleplayerServer().overworld();
                return l != null && BuiltInRegistries.BLOCK.getKey(l.getBlockState(bagAt).getBlock()).getPath().equals("placed_satchel")
                    && mc().level.getBlockEntity(bagAt) instanceof net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity; });
        quiet("place_items", 10, () -> server(m -> RancherCompanion.LOG.info("[selftest] placed bag after apples: {}",
            dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.insertPlacedForTest(m.level(), bagAt, new ItemStack(Items.APPLE, 9)))));
        quiet("place_close", 10, () -> cmd("tp @s " + (bearing.getX() + 2.2) + " " + (bearing.getY() + 1.4) + " " + (bearing.getZ() + 0.5) + " 90 25"));
        step("place_still", 15, () -> {}, () -> true);
        step("place_assemble", 20, () -> { select(8); useOn(bearing, Direction.EAST); server(m -> assemble(m.level(), bearing)); }, () -> {
            var l = mc().getSingleplayerServer() == null ? null : mc().getSingleplayerServer().overworld();
            return l != null && l.getBlockState(bagAt).isAir(); });
        quiet("place_turning", 25, () -> {});
        step("place_on_contraption", 5, () -> {}, () -> true);
        quiet("place_turning_more", 15, () -> {});
        step("place_on_contraption_later", 5, () -> {}, () -> true);
        step("place_disassemble", 40, () -> server(m -> disassemble(m.level(), bearing)), () -> {
            var l = mc().getSingleplayerServer() == null ? null : mc().getSingleplayerServer().overworld();
            return l != null && !l.getBlockState(bagAt).isAir(); });
        quiet("place_before_break", 10, () -> server(m -> { breakDrops = "";
            RancherCompanion.LOG.info("[selftest] after the ride: {} holds {}", m.level().getBlockState(bagAt),
                dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.insertPlacedForTest(m.level(), bagAt, ItemStack.EMPTY)); }));
        check("place_breaks_into_satchel", 10, 3, () -> server(m -> { m.level().destroyBlock(bagAt, true); breakDrops = dropAt(m.level(), bagAt); }),
            "the dropped satchel, its colors and apples", () -> breakDrops, () -> breakDrops.contains("satchel") && breakDrops.contains("apple"));
    }

    private static BlockPos tombAt;
    private static int marksBefore;

    private static int marks() {
        return net.neoforged.fml.ModList.get().isLoaded("journeymap") ? dev.frosthaven.ranchercompanion.compat.journeymap.DrekkiMap.marksForTest().size() : -1;
    }

    /** the drekki dies for good (spec, Tombstone): its tombstone holds its gear, opens in our screen, shows its name and
     *  date on Jade's hover, the owner's map gets the magenta death X, and emptied it mines into the named item */
    private static void tomb() {
        quiet("tomb_setup", 20, () -> { mc().setScreen(null); cmd("gamemode creative"); mc().options.hideGui = false; cmd("time set 6000");
            marksBefore = marks();
            server(m -> { var d = sd(); if (d == null) return;
                d.setCustomName(net.minecraft.network.chat.Component.literal("Ember"));
                d.setGear(3, d.barding(), d.dye());
                if (d.bardingItem().isEmpty()) d.setBardingItem(new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get()));
                m.teleportTo(d.getX() + 3, d.getY(), d.getZ()); }); });
        check("tomb_placed", 20, 3, () -> server(m -> { var d = sd(); if (d == null) return;
                BlockPos at = d.blockPosition();
                dev.frosthaven.ranchercompanion.ranch.Death.turnGhost(d);  /* a ghost wears nothing: its gear goes to a tombstone */
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 6, 2)))
                    if (m.level().getBlockEntity(p) instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity) { tombAt = p.immutable(); break; } }),
            "the tombstone and what it holds", () -> {
                var be = tombAt == null || mc().level == null ? null : mc().level.getBlockEntity(tombAt) instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity b ? b : null;
                return be == null ? "none" : be.name() + " " + be.date() + " " + be.items().getItem(0) + " " + be.items().getItem(1) + " " + be.items().getItem(2); },
            () -> tombAt != null && mc().level.getBlockEntity(tombAt) instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity b
                && b.name().equals("Ember") && !b.items().getItem(1).isEmpty());
        /* the ghost's screen: read-only (spec, Ghost drekki) */
        quiet("ghost_near", 15, () -> { hotbar(7, "rancher_companion:fledgling_iron_barding"); emptyHand(); near(pup()); });
        step("ghost_screen", 25, () -> interact("open its screen"), () -> ds() != null && ds().getMenu().locked());
        step("ghost_stance_locked", 15, () -> hoverSpot("stance0"), () -> ds() != null && ds().getMenu().locked());
        check("ghost_gear_locked", 10, 0, () -> { click(3 + 27 + 7, 0); click(1, 0); click(3 + 27 + 7, 0); }, "the barding slot after a click with a barding on the cursor",
            () -> mc().player.containerMenu.getSlot(1).getItem(), () -> mc().player.containerMenu.getSlot(1).getItem().isEmpty());
        quiet("ghost_close", 10, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; mc().setScreen(null); });
        /* the ghost dies for good, wearing nothing: no second tombstone */
        check("ghost_dies_no_second_tomb", 25, 3, () -> server(m -> { var d = sd(); if (d == null) return;
                d.setHealth(0); d.die(d.damageSources().genericKill()); }),
            "tombstones around", () -> tombsAround(), () -> tombsAround() == 1 && sd() == null);
        /* in front of its carved face, looking at it: the model, and Jade's hover with the name and date */
        quiet("tomb_look", 25, () -> { if (tombAt == null) return;
            var f = mc().level.getBlockState(tombAt).getValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.FACING);
            var eye = tombAt.getCenter().add(f.getStepX() * 2.2, 0.1, f.getStepZ() * 2.2);
            float yaw = f.getOpposite().toYRot();
            cmd("tp @s " + eye.x + " " + (tombAt.getY()) + " " + eye.z + " " + yaw + " 25"); });
        step("tomb_model_and_jade", 45, () -> {}, () -> mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult h && h.getBlockPos().equals(tombAt));
        if (net.neoforged.fml.ModList.get().isLoaded("journeymap"))
            check("tomb_journeymap_x", 5, 3, () -> {}, "JourneyMap markers from this mod, before and now",
                () -> marksBefore + " -> " + marks(), () -> marks() > marksBefore);
        step("tomb_screen", 25, () -> useOn(tombAt, Direction.UP),
            () -> mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.TombstoneScreen);
        check("tomb_full_wont_mine", 10, 0, () -> {}, "destroy progress while full (what the client mines by)", () -> tombAt == null ? "-"
                : mc().level.getBlockState(tombAt).getDestroyProgress(mc().player, mc().level, tombAt),
            () -> tombAt != null && mc().level.getBlockState(tombAt).getDestroyProgress(mc().player, mc().level, tombAt) == 0);
        check("tomb_take_all", 15, 3, () -> { for (int i = 0; i < 3; i++) shiftClick(i); }, "what is left in it",
            () -> mc().level.getBlockEntity(tombAt) instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity b ? b.items().isEmpty() : "-",
            () -> mc().level.getBlockEntity(tombAt) instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity b && b.items().isEmpty());
        quiet("tomb_close", 10, () -> { mc().setScreen(null); cmd("gamemode survival");
            for (String b : new String[]{"satchel", "saddlebag", "iron_saddlebag", "gold_saddlebag", "diamond_saddlebag", "netherite_saddlebag"})
                cmd("clear @s rancher_companion:" + b);  /* the taken bag's magnet would pull the mined tombstone into it */ hotbar(0, "minecraft:netherite_pickaxe"); select(0); });
        check("tomb_mines_into_item", 20, 3, () -> server(m -> m.gameMode.destroyBlock(tombAt)), "the tombstone item, its name and date",
            () -> tombItem(), () -> tombItem().startsWith("Ember "));
        quiet("tomb_pick_up", 25, () -> server(m -> {
            for (var e : m.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(tombAt).inflate(3)))
                if (e.getItem().is(DrekkiItems.TOMBSTONE.get())) { m.getInventory().setItem(1, e.getItem().copy()); e.discard(); }
            for (int i = 0; i < 36; i++) if (i != 1 && m.getInventory().getItem(i).is(DrekkiItems.TOMBSTONE.get())) {
                m.getInventory().setItem(1, m.getInventory().getItem(i).copy()); m.getInventory().setItem(i, ItemStack.EMPTY); } }));
        quiet("tomb_inv", 15, () -> mc().setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc().player)));  /* survival: the plain inventory */
        step("tomb_item_tooltip", 20, () -> {
            if (!(mc().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen s)) return;
            var slot = mc().player.inventoryMenu.slots.stream().filter(sl -> sl.container == mc().player.getInventory() && sl.getContainerSlot() == 1).findFirst().orElseThrow();
            var w = mc().getWindow();
            double x = (s.getGuiLeft() + slot.x + 8) * w.getGuiScale(), y = (s.getGuiTop() + slot.y + 8) * w.getGuiScale();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(w.getWindow(), x, y);
            try {  /* the game's own copy of the cursor, which the screen reads (the headless display sends no move event) */
                var mh = net.minecraft.client.MouseHandler.class;
                var fx = mh.getDeclaredField("xpos"); fx.setAccessible(true); fx.setDouble(mc().mouseHandler, x);
                var fy = mh.getDeclaredField("ypos"); fy.setAccessible(true); fy.setDouble(mc().mouseHandler, y);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }, () -> mc().player.getInventory().getItem(1).is(DrekkiItems.TOMBSTONE.get())
            && mc().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs && cs.getSlotUnderMouse() != null
            && cs.getSlotUnderMouse().getItem().is(DrekkiItems.TOMBSTONE.get()));
        quiet("tomb_done", 10, () -> { mc().setScreen(null); cmd("gamemode creative"); });
    }

    private static int tombsAround() {
        if (tombAt == null) return -1;
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(tombAt.offset(-4, -3, -4), tombAt.offset(4, 6, 4)))
            if (mc().level.getBlockEntity(p) instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity) n++;
        return n;
    }

    /** the tombstone item lying where it was mined: "name date", or what is there */
    private static String tombItem() {
        var m = me(); if (m == null || tombAt == null) return "-";
        for (var e : m.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(tombAt).inflate(3)))
            if (e.getItem().is(DrekkiItems.TOMBSTONE.get()))
                return e.getItem().get(dev.frosthaven.ranchercompanion.item.DrekkiData.TOMB_NAME.get()) + " " + e.getItem().get(dev.frosthaven.ranchercompanion.item.DrekkiData.TOMB_DATE.get());
        for (int i = 0; i < 36; i++) { ItemStack it = m.getInventory().getItem(i);  /* picked up at once */
            if (it.is(DrekkiItems.TOMBSTONE.get())) return it.get(dev.frosthaven.ranchercompanion.item.DrekkiData.TOMB_NAME.get()) + " " + it.get(dev.frosthaven.ranchercompanion.item.DrekkiData.TOMB_DATE.get()); }
        return "none, block " + mc().level.getBlockState(tombAt);
    }

    /** Create's bearing: assemble on its next tick (what an empty-hand click does), and log why when it cannot */
    private static void assemble(net.minecraft.world.level.Level l, BlockPos bearing) {
        try {
            var be = l.getBlockEntity(bearing);
            if (be == null) { RancherCompanion.LOG.info("[selftest] no bearing block entity"); return; }
            field(be.getClass(), "assembleNextTick").setBoolean(be, true);
            l.getServer().execute(() -> l.getServer().execute(() -> {
                try {
                    var ex = be.getClass().getMethod("getLastAssemblyException").invoke(be);
                    RancherCompanion.LOG.info("[selftest] bearing running={} assembly problem={}", field(be.getClass(), "running").getBoolean(be), ex);
                } catch (ReflectiveOperationException e) { RancherCompanion.LOG.info("[selftest] bearing state unreadable: {}", e.toString()); }
            }));
        } catch (ReflectiveOperationException e) { RancherCompanion.LOG.info("[selftest] bearing not driven: {}", e.toString()); }
    }

    /** Create's bearing: back into blocks (what a second empty-hand click does) */
    private static void disassemble(net.minecraft.world.level.Level l, BlockPos bearing) {
        try { var be = l.getBlockEntity(bearing); if (be != null) be.getClass().getMethod("disassemble").invoke(be); }
        catch (ReflectiveOperationException e) { RancherCompanion.LOG.info("[selftest] bearing not stopped: {}", e.toString()); }
    }

    private static java.lang.reflect.Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            try { var f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }

    private static String breakDrops = "";

    /** on the server thread: the items lying around a spot, a bag's contents and colors spelled out */
    private static String dropAt(net.minecraft.world.level.Level l, BlockPos p) {
        StringBuilder b = new StringBuilder();
        for (var e : l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(p).inflate(3))) {
            ItemStack s = e.getItem();
            b.append(BuiltInRegistries.ITEM.getKey(s.getItem())).append(' ');
            if (s.getItem() instanceof dev.frosthaven.ranchercompanion.item.DrekkiBag) {
                var h = dev.frosthaven.ranchercompanion.item.Dyeing.storage(s);
                for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) b.append(BuiltInRegistries.ITEM.getKey(h.getStackInSlot(i).getItem())).append(' ');
                b.append(Integer.toHexString(dev.frosthaven.ranchercompanion.item.Dyeing.main(s))).append('/').append(Integer.toHexString(dev.frosthaven.ranchercompanion.item.Dyeing.trim(s)));
            }
        }
        return b.toString();
    }

    /** close shots of the drekki's back with its gear, the HUD hidden: from behind and above, then from the side; k scales
     *  the distance to its size. It stands still, facing south, away from the test hatchery */
    private static void lookAt(String name, double k) {
        quiet(name + "_place", 20, () -> { cmd("gamerule sendCommandFeedback false");
            life(d -> { d.setNoAi(true); d.teleportTo(o.getX() - 3.5, o.getY(), o.getZ() - 2.5); d.setYRot(0); d.yBodyRot = 0; d.setYHeadRot(0); });
            cmd("gamemode spectator"); mc().options.hideGui = true;
            cmd("tp @s " + (o.getX() - 3.5 + 1.3 * k) + " " + (o.getY() + 1.2 * k) + " " + (o.getZ() - 2.5 - 1.4 * k) + " 43 32"); });
        step(name + "_back", 20, () -> {}, () -> true);
        quiet(name + "_side", 15, () -> cmd("tp @s " + (o.getX() - 3.5 + 2.2 * k) + " " + (o.getY() + 0.6 * k) + " " + (o.getZ() - 2.5 + 0.2 * k) + " 95 12"));
        step(name + "_side_shot", 15, () -> {}, () -> true);
        quiet(name + "_hud", 5, () -> mc().options.hideGui = false);
    }

    private static int farFood = -1, beefBefore = -1;

    private static boolean hasItem(net.neoforged.neoforge.items.IItemHandler h, net.minecraft.world.item.Item item) {
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).is(item)) return true;
        return false;
    }

    /** the game's own cursor position (what screens are drawn with), set inside the game: no desktop input */
    static void mouse(double x, double y) {
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
