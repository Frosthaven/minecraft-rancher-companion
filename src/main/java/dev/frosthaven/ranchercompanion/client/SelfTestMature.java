package dev.frosthaven.ranchercompanion.client;

import static dev.frosthaven.ranchercompanion.client.SelfTest.*;
import static dev.frosthaven.ranchercompanion.client.SelfTestTraits.*;

import dev.frosthaven.ranchercompanion.DrekkiConfig;
import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.combat.Abilities;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiMenu;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.trait.RoamTraits;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The second half of 0.5.0 in the real game (-Drancher_companion.selftest.mature=true with selftest.new, or at the end
 * of the new-traits run): each of the fifteen former biome traits learned from the activity itself (a real sprint, swim,
 * boat, horse and strider ride, real harvests, torches, nights slept, a spyglass, kills, a fall, shulker bullets) and the
 * effects a player meets; then the Mature stage: growing up, the saddle slot through the real screen, mounting by a
 * click, and riding with the real keys (jump, the second jump, the dash, flight, the slow fall, water and lava).
 */
final class SelfTestMature {
    private SelfTestMature() {}

    static final boolean ONLY = Boolean.getBoolean("rancher_companion.selftest.mature");
    private static final String DREKKI = "@e[type=rancher_companion:drekki,limit=1,sort=nearest]";
    private static double mark, peak, fastest, slowest;
    private static int turn;

    /** -Drancher_companion.selftest.sections=a,b runs only those sections (their fresh() names, and "mature") */
    private static final java.util.Set<String> WANT = new java.util.HashSet<>(java.util.Arrays.asList(System.getProperty("rancher_companion.selftest.sections", "").split("[,+]")));
    private static void sec(String name, Runnable body) { if (WANT.contains("") || WANT.contains(name)) body.run(); }

    private static void keys(boolean up, boolean sprint, boolean jump) {
        var k = mc().options;
        k.keyUp.setDown(up); k.keySprint.setDown(sprint); k.keyJump.setDown(jump);
    }

    private static int progress(Trait t) { DrekkiEntity d = sd(); return d == null ? -1 : d.life.progress.getOrDefault(t.id, 0); }

    /** an affinity that can learn the trait, as learn() gives it, for the real activity that comes first */
    private static void affinity(Trait t) {
        quiet("affinity_first_" + t.id, 10, () -> life(d -> {
            if (t.learnedBy(d.astral())) return;
            for (int a = 1; a <= 6; a++) if (t.learnedBy(a)) { d.setLook(d.stage(), d.line(), d.diet(), a, d.horn()); return; }
        }));
    }

    /** the rest of a distance count, through the game's own statistic (the real travel above proved it is counted) */
    private static void finish(Trait t, ResourceLocation stat, int cm) {
        learn(t, 60, 1, () -> server(m -> m.awardStat(Stats.CUSTOM.get(stat), cm)));
    }

    private static Entity tagged(String tag) {
        var m = me();
        if (m == null) return null;
        for (Entity e : m.serverLevel().getEntities((Entity) null, m.getBoundingBox().inflate(64), e -> e.isAlive() && e.getTags().contains(tag))) return e;
        return null;
    }

    private static void clearMobs() { cmd("kill @e[type=!player,type=!rancher_companion:drekki,type=!item,distance=..200,tag=st]"); }

    /** a ride on a summoned mount or boat down a lane toward +x: summoned, mounted, the forward key held */
    private static void ride(String name, String summon, BlockPos start, Trait t, int ticks) {
        quiet(name + "_summon", 20, () -> { cmd("tp @s " + (start.getX() + 0.5) + " " + (start.getY() + 1) + " " + (start.getZ() + 0.5) + " -90 0"); cmd("summon " + summon); });
        quiet(name + "_mount", 20, () -> { pupHere(); server(m -> { Entity e = tagged("ride"); if (e != null) m.startRiding(e, true); }); });
        quiet(name + "_go", ticks, () -> keys(true, false, false));
        check(t.id + "_counts_a_real_" + name, 10, 20, () -> {}, "counts after a real " + name + " ride", () -> progress(t) + " | " + state(), () -> progress(t) >= 1);
        quiet(name + "_stop", 20, () -> { keys(false, false, false); server(m -> m.stopRiding()); clearMobs(); });
    }

    /** samples the drekki's height and speed for some ticks: its peak, its fastest and its slowest fall */
    private static void sample(int ticks) {
        DrekkiEntity c = pup();
        peak = c == null ? 0 : c.getY(); fastest = 0; slowest = 0;
        for (int i = 1; i <= ticks; i++) after(i, () -> {
            DrekkiEntity d = pup();
            if (d == null) return;
            peak = Math.max(peak, d.getY());
            fastest = Math.max(fastest, Math.hypot(d.getX() - d.xo, d.getZ() - d.zo));
            slowest = Math.min(slowest, d.getY() - d.yo);
        });
    }

    /** the rider and the drekki to a spot, facing +x, mounted again */
    private static void rideAt(double x, double y, double z) {
        keys(false, false, false);
        /* down first, then moved, then up again some ticks later: the rider's game steers its mount, and a move it
           sent before it knew of the jump would carry the drekki back */
        server(m -> m.stopRiding());
        after(4, () -> server(m -> {
            DrekkiEntity d = sd();
            if (d == null) return;
            d.teleportTo(x, y, z);
            m.teleportTo(m.serverLevel(), x, y, z, -90, 0);
            d.setYRot(-90);
            d.fallDistance = 0; m.fallDistance = 0;
        }));
        after(12, () -> server(m -> { DrekkiEntity d = sd(); if (d != null) { d.teleportTo(x, y, z); m.startRiding(d, true); } }));
    }

    private static boolean riding() { return mc().player.getVehicle() instanceof DrekkiEntity; }

    private static void give(Trait... ts) { life(d -> { d.life.traits.clear(); for (Trait t : ts) d.life.traits.add(t.id); d.syncLife(); }); }

    /** this section runs on the flat world's ground under the pad: the lanes need room, and a fall needs somewhere to land */
    private static BlockPos pad, ground;

    static void steps() {
        pad = SelfTestTraits.o;
        ground = new BlockPos(pad.getX(), -60, pad.getZ() - 80);  /* clear of the pad's shadow: Inverse Luminance reads the daylight */
        quiet("to_the_ground", 60, () -> { SelfTestTraits.o = ground;
            cmd("forceload add " + (ground.getX() - 16) + " " + (ground.getZ() - 16) + " " + (ground.getX() + 352) + " " + (ground.getZ() + 48));
            tp(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5); });
        quiet("pup_to_the_ground", 40, SelfTestTraits::pupHere);
        roam();
        sec("mature", SelfTestMature::mature);
        quiet("back_to_the_pad", 40, () -> { SelfTestTraits.o = pad; cmd("forceload remove " + (ground.getX() - 16) + " " + (ground.getZ() - 16) + " " + (ground.getX() + 352) + " " + (ground.getZ() + 48));
            tp(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5); pupHere(); });
        quiet("mature_rules_back", 10, () -> { cmd("gamerule doMobSpawning true"); cmd("gamerule doDaylightCycle true"); RancherCompanion.LOG.info("[selftest] roam and Mature section done"); });
    }

    // ---- the fifteen former biome traits ----

    private static void roam() {
        BlockPos o = ground;
        quiet("roam_rules", 20, () -> { cmd("gamerule doMobSpawning false"); cmd("time set 6000"); cmd("gamerule doDaylightCycle false"); cmd("gamemode survival"); cmd("kill @e[type=!player,type=!rancher_companion:drekki,distance=..200]"); });
        quiet("roam_on_foot", 20, () -> cmd("gamemode creative"));  /* through survival: a player who was flying (a spectator shot before) walks again */

        sec("open_road", () -> {
            /* The Open Road: a real sprint */
            fresh("open_road");
            affinity(Trait.OPEN_ROAD);
            quiet("sprint_go", 440, () -> { mc().player.getAbilities().flying = false; keys(true, true, false); });
            check("open_road_counts_a_real_sprint", 10, 20, () -> {}, "counts after 20 s at a sprint", () -> progress(Trait.OPEN_ROAD) + " | " + state(), () -> progress(Trait.OPEN_ROAD) >= 1);
            quiet("sprint_stop", 20, () -> { keys(false, false, false); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); pupHere(); });
            finish(Trait.OPEN_ROAD, Stats.SPRINT_ONE_CM, 20000);
            check("open_road_speeds_its_owner", 30, 3, SelfTestTraits::pupHere, "the owner's movement speed", () -> me() == null ? "?" : me().getAttributeValue(Attributes.MOVEMENT_SPEED),
                () -> me() != null && Math.abs(me().getAttributeValue(Attributes.MOVEMENT_SPEED) / me().getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 1.1) < 0.001);
        });
        sec("frost_strider", () -> {
            /* Frost Strider: a real run over snow, all 300 blocks; then powder snow */
            fresh("frost_strider");
            affinity(Trait.FROST_STRIDER);
            quiet("snow_lane", 30, () -> { cmd("fill " + at(o.offset(14, -1, 9)) + " " + at(o.offset(180, -1, 11)) + " snow_block"); cmd("fill " + at(o.offset(181, -1, 9)) + " " + at(o.offset(345, -1, 11)) + " snow_block"); });
            quiet("snow_go", 20, () -> { cmd("tp @s " + (o.getX() + 15.5) + " " + o.getY() + " " + (o.getZ() + 10.5) + " -90 0"); pupHere(); });
            quiet("snow_run", 10, () -> keys(true, true, false));
            learn(Trait.FROST_STRIDER, 90, 1, () -> {});
            quiet("snow_stop", 20, () -> { keys(false, false, false); cmd("fill " + at(o.offset(8, -3, 0)) + " " + at(o.offset(8, -1, 0)) + " powder_snow"); cmd("gamemode survival"); });
            quiet("powder_on", 20, () -> { cmd("tp @s " + (o.getX() + 8.5) + " " + (o.getY() + 0.2) + " " + (o.getZ() + 0.5)); life(d -> d.teleportTo(o.getX() + 6.5, o.getY(), o.getZ() + 1.5)); });
            check("frost_strider_stands_on_powder_snow", 60, 2, () -> {}, "the owner's height on powder snow", () -> String.format("%.2f (ground %d)", mc().player.getY(), o.getY()),
                () -> mc().player.getY() >= o.getY() - 0.05);
            quiet("powder_without", 20, () -> life(d -> { d.life.traits.clear(); d.syncLife(); }));
            check("without_it_the_owner_sinks", 60, 3, () -> {}, "the owner's height on powder snow with no trait", () -> String.format("%.2f", mc().player.getY()), () -> mc().player.getY() < o.getY() - 0.5);
            quiet("powder_off", 20, () -> { cmd("gamemode creative"); cmd("fill " + at(o.offset(8, -3, 0)) + " " + at(o.offset(8, -1, 0)) + " stone"); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); });
        });
        sec("downstream", () -> {
            /* Downstream: a real swim down a channel */
            fresh("downstream");
            affinity(Trait.DOWNSTREAM);
            quiet("channel", 30, () -> cmd("fill " + at(o.offset(14, -4, 20)) + " " + at(o.offset(170, -1, 22)) + " water"));
            quiet("swim_in", 30, () -> { cmd("tp @s " + (o.getX() + 16.5) + " " + (o.getY() - 3) + " " + (o.getZ() + 21.5) + " -90 0"); pupHere(); });
            quiet("swim_go", 300, () -> keys(true, true, false));
            check("downstream_counts_a_real_swim", 10, 20, () -> {}, "counts after 15 s of swimming", () -> progress(Trait.DOWNSTREAM) + " | " + state(), () -> progress(Trait.DOWNSTREAM) >= 1);
            quiet("swim_stop", 20, () -> { keys(false, false, false); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); pupHere(); });
            finish(Trait.DOWNSTREAM, Stats.SWIM_ONE_CM, 10000);
            check("downstream_swim_and_air", 30, 3, SelfTestTraits::pupHere, "swim speed and air bonus", () -> me() == null ? "?" : me().getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED) + ", " + me().getAttributeValue(Attributes.OXYGEN_BONUS),
                () -> me() != null && Math.abs(me().getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED) - 1.25) < 0.001 && me().getAttributeValue(Attributes.OXYGEN_BONUS) == 0.25);
        });
        sec("ocean_walker", () -> {
            /* Ocean Walker: a real boat ride down the channel */
            fresh("ocean_walker");
            affinity(Trait.OCEAN_WALKER);
            ride("boat", "boat " + (o.getX() + 18.5) + " " + o.getY() + " " + (o.getZ() + 21.5) + " {Type:\"oak\",Rotation:[-90f,0f],Tags:[\"st\",\"ride\"]}", o.offset(18, 0, 21), Trait.OCEAN_WALKER, 400);
            finish(Trait.OCEAN_WALKER, Stats.BOAT_ONE_CM, 20000);
        });
        sec("four_winds", () -> {
            /* Four Winds: a real horse ride */
            fresh("four_winds");
            affinity(Trait.FOUR_WINDS);
            ride("horse", "horse " + (o.getX() + 2.5) + " " + o.getY() + " " + (o.getZ() - 5.5) + " {Tame:1b,Rotation:[-90f,0f],SaddleItem:{id:\"minecraft:saddle\",count:1},Tags:[\"st\",\"ride\"]}", o.offset(2, 0, -6), Trait.FOUR_WINDS, 400);
            finish(Trait.FOUR_WINDS, Stats.HORSE_ONE_CM, 20000);
        });
        sec("lava_walker", () -> {
            /* Lava Walker: a real strider ride over lava */
            fresh("lava_walker");
            affinity(Trait.LAVA_WALKER);
            quiet("lava_lane", 30, () -> { cmd("fill " + at(o.offset(14, -2, 30)) + " " + at(o.offset(110, -1, 32)) + " lava"); hotbar(4, "warped_fungus_on_a_stick"); select(4); });
            ride("strider", "strider " + (o.getX() + 16.5) + " " + o.getY() + " " + (o.getZ() + 31.5) + " {Saddle:1b,Rotation:[-90f,0f],Tags:[\"st\",\"ride\"]}", o.offset(16, 0, 31), Trait.LAVA_WALKER, 700);
            quiet("lava_gone", 20, () -> { cmd("fill " + at(o.offset(14, -2, 30)) + " " + at(o.offset(110, -1, 32)) + " stone"); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); pupHere(); });
            finish(Trait.LAVA_WALKER, Stats.STRIDER_ONE_CM, 6000);
        });
        sec("homes_gardens", () -> {
            /* Homes & Gardens: 64 grown wheat broken by hand */
            fresh("homes_gardens");
            affinity(Trait.HOMES_GARDENS);
            quiet("field", 30, () -> { cmd("fill " + at(o.offset(-4, -1, -4)) + " " + at(o.offset(3, -1, 3)) + " farmland"); cmd("fill " + at(o.offset(-4, 0, -4)) + " " + at(o.offset(3, 0, 3)) + " wheat[age=7]"); emptyHand(); });
            quiet("harvest", 220, () -> { int n = 0; for (int x = -4; x <= 3; x++) for (int z = -4; z <= 3; z++) { BlockPos p = o.offset(x, 0, z); after(3 * ++n, () -> mc().gameMode.startDestroyBlock(p, Direction.UP)); } });
            check("harvest_counted", 5, 1, () -> {}, "counts after 64 grown wheat by hand, and a corner of the field", () -> progress(Trait.HOMES_GARDENS) + " " + mc().level.getBlockState(o.offset(-4, 0, -4)) + " " + mc().level.getBlockState(o.offset(0, 0, 1)) + " mode " + mc().gameMode.getPlayerMode() + " | " + state(), () -> true);
        learn(Trait.HOMES_GARDENS, 10, 1, () -> {});
            quiet("field_gone", 20, () -> { cmd("fill " + at(o.offset(-4, -1, -4)) + " " + at(o.offset(3, 0, 3)) + " air"); cmd("fill " + at(o.offset(-4, -1, -4)) + " " + at(o.offset(3, -1, 3)) + " stone"); cmd("kill @e[type=item,distance=..20]"); });
        });
        sec("inverse_luminance", () -> {
            /* Inverse Luminance: 64 torches placed by hand; then its light at night */
            fresh("inverse_luminance");
            affinity(Trait.INVERSE_LUMINANCE);
            quiet("torches", 280, () -> { hotbar(3, "torch"); select(3); BlockPos p = o.offset(2, 0, 0);
                for (int i = 0; i < 66; i++) { after(4 * i + 1, () -> useOn(p.below(), Direction.UP)); after(4 * i + 3, () -> mc().gameMode.startDestroyBlock(p, Direction.UP)); } });
            learn(Trait.INVERSE_LUMINANCE, 10, 1, () -> {});
            quiet("night", 60, () -> { cmd("time set 18000"); cmd("kill @e[type=item,distance=..20]"); emptyHand(); pupHere(); });
            check("inverse_luminance_lights_the_night", 40, 4, () -> {}, "the block at its head", () -> { DrekkiEntity d = sd(); return d == null ? "?" : d.level().getBlockState(BlockPos.containing(d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ())); },
                () -> { DrekkiEntity d = sd(); return d != null && d.level().getBlockState(BlockPos.containing(d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ())).is(Blocks.LIGHT); });
            quiet("day", 40, () -> cmd("time set 6000"));
            check("and_gives_none_by_day", 40, 3, () -> {}, "lights it keeps by day", () -> me() == null ? "?" : RoamTraits.lamps(me().serverLevel()), () -> me() != null && RoamTraits.lamps(me().serverLevel()) == 0);
        });
        sec("hibernation", () -> {
            /* Hibernation Plan: five nights slept in a real bed */
            fresh("hibernation");
            affinity(Trait.HIBERNATION);
            quiet("bed", 30, () -> { cmd("gamemode survival"); cmd("setblock " + at(o.offset(3, 0, 3)) + " red_bed[part=foot,facing=east]"); cmd("setblock " + at(o.offset(4, 0, 3)) + " red_bed[part=head,facing=east]"); emptyHand(); });
            learn(Trait.HIBERNATION, 8, 9, () -> { cmd("time set 13000"); cmd("tp @s " + (o.getX() + 2.5) + " " + o.getY() + " " + (o.getZ() + 2.5)); pupHere(); after(10, () -> useOn(o.offset(3, 0, 3), Direction.UP)); });
            quiet("bed_gone", 40, () -> { cmd("setblock " + at(o.offset(4, 0, 3)) + " air"); cmd("setblock " + at(o.offset(3, 0, 3)) + " air"); cmd("time set 6000"); cmd("gamemode creative"); cmd("kill @e[type=item,distance=..20]"); });
        });
        sec("predators_eye", () -> {
            /* Predator's Eye: ten different hostile mobs in a real spyglass */
            fresh("predators_eye");
            affinity(Trait.PREDATORS_EYE);
            quiet("watched", 40, () -> { for (int i = 0; i < 10; i++) cmd("summon husk " + (o.getX() + 9.5) + " " + o.getY() + " " + (o.getZ() - 9 + 2 * i + 0.5) + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"eye" + i + "\"]}");
                hotbar(2, "spyglass"); select(2); turn = 0; });
            quiet("scope", 20, () -> mc().options.keyUse.setDown(true));
            check("scoping", 5, 1, () -> {}, "the owner is looking through the spyglass", () -> me() == null ? "?" : me().isScoping() + " using " + me().getUseItem() + " held " + me().getMainHandItem() + " client using " + mc().player.isUsingItem(), () -> true);
        learn(Trait.PREDATORS_EYE, 40, 1, () -> cmd("execute anchored eyes run tp @s ~ ~ ~ facing entity @e[tag=eye" + (turn++ % 10) + ",limit=1] eyes"));
        check("spotted", 5, 1, () -> {}, "mobs counted", () -> progress(Trait.PREDATORS_EYE) + " scoping " + (me() != null && me().isScoping()) + " yaw " + (me() == null ? "?" : me().getYRot() + " pitch " + me().getXRot() + " at " + me().position() + " husks " + me().serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class, me().getBoundingBox().inflate(30)).stream().map(h -> h.position().toString()).toList()) + " | " + state(), () -> true);
            quiet("scope_down", 20, () -> { mc().options.keyUse.setDown(false); clearMobs(); emptyHand(); });
        });
        sec("natural_repellant", () -> {
            /* Natural Repellant: ten creepers by the sword; then a creeper beside the drekki */
            fresh("natural_repellant");
            learn(Trait.NATURAL_REPELLANT, 30, 2, kill("creeper", 2));
            quiet("creeper_near", 20, () -> { DrekkiEntity c = pup(); if (c != null) cmd("summon creeper " + (c.getX() + 2) + " " + c.getY() + " " + c.getZ() + " {PersistenceRequired:1b,Tags:[\"st\",\"shy\"]}"); });
            check("natural_repellant_turns_a_creeper_away", 60, 6, () -> {}, "the creeper's distance from the drekki", () -> { Entity c = tagged("shy"); DrekkiEntity d = sd(); return c == null || d == null ? "gone" : String.format("%.1f", c.distanceTo(d)); },
                () -> { Entity c = tagged("shy"); DrekkiEntity d = sd(); return c != null && d != null && c.distanceTo(d) > 4.5; });
            quiet("creeper_done", 10, SelfTestMature::clearMobs);
        });
        sec("hoglin_ward", () -> {
            /* Hoglin Ward: five hoglins; then a hoglin beside the drekki */
            fresh("hoglin_ward");
            learn(Trait.HOGLIN_WARD, 20, 2, kill("hoglin", 2));
            quiet("hoglin_near", 20, () -> { DrekkiEntity c = pup(); if (c != null) cmd("summon hoglin " + (c.getX() + 3) + " " + c.getY() + " " + c.getZ() + " {PersistenceRequired:1b,IsImmuneToZombification:1b,Tags:[\"st\",\"warded\"]}"); });
            check("hoglin_ward_repels_a_hoglin", 60, 6, () -> {}, "the hoglin's repellent memory or distance", () -> { Entity h = tagged("warded"); DrekkiEntity d = sd(); return h == null || d == null ? "gone" : ((Hoglin) h).getBrain().getMemory(MemoryModuleType.NEAREST_REPELLENT) + String.format(" %.1f", h.distanceTo(d)); },
                () -> { Entity h = tagged("warded"); DrekkiEntity d = sd(); return h instanceof Hoglin g && d != null && (g.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT) || g.distanceTo(d) > 8); });
            quiet("hoglin_done", 10, SelfTestMature::clearMobs);
        });
        sec("draconic_vampirism", () -> {
            /* Draconic Vampirism: ten kills at 3 hearts; then a hit heals */
            fresh("draconic_vampirism");
            Runnable husk = kill("husk", 2);
            learn(Trait.DRACONIC_VAMPIRISM, 30, 2, () -> { server(m -> m.setHealth(6)); husk.run(); });
            quiet("vampire_foe", 30, () -> { clearMobs(); server(m -> m.setHealth(6)); cmd("summon husk ^ ^ ^2 {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"bled\"]}"); select(5); pupHere(); });
            check("draconic_vampirism_heals_its_owner", 20, 4, () -> { Entity e = nearest("husk"); if (e != null) mc().gameMode.attack(mc().player, e); }, "the owner's health after a sword hit, from 6",
                () -> me() == null ? "?" : me().getHealth(), () -> me() != null && me().getHealth() > 6.3f);
            quiet("vampire_done", 10, () -> { clearMobs(); server(m -> m.setHealth(20)); });
        });
        sec("leap_of_faith", () -> {
            /* Leap of Faith: real 35 block falls into water, one in four */
            fresh("leap_of_faith");
            quiet("pool", 20, () -> { cmd("gamemode survival"); cmd("setblock " + at(o.offset(-6, -1, 6)) + " water"); });
            learn(Trait.LEAP_OF_FAITH, 40, 5, () -> { pupHere(); cmd("tp @s " + (o.getX() - 5.5) + " " + (o.getY() + 36) + " " + (o.getZ() + 6.5)); });
            quiet("pool_gone", 20, () -> { cmd("gamemode creative"); cmd("setblock " + at(o.offset(-6, -1, 6)) + " stone"); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); });
        });
        sec("wings_of_hope", () -> {
            /* Wings of Hope: ten real shulker bullets */
            fresh("wings_of_hope");
            learn(Trait.WINGS_OF_HOPE, 30, 2, () -> cmd("summon shulker_bullet ~ ~2.6 ~ {Motion:[0.0d,-0.5d,0.0d]}"));
            quiet("bullets_done", 20, () -> { cmd("effect clear @s"); cmd("kill @e[type=shulker_bullet]"); tp(o.getX() + 0.5, o.getY(), o.getZ() + 0.5); });
        
        });
    }

    // ---- the Mature stage ----

    private static void restore() {
        quiet("restore", 30, () -> { cmd("gamemode creative"); cmd("gamerule sendCommandFeedback true"); life(d -> d.setNoAi(false)); SelfTestTraits.tp(SelfTestTraits.o.getX() + 0.5, SelfTestTraits.o.getY(), SelfTestTraits.o.getZ() + 0.5); pupHere(); });
    }

    private static void mature() {
        BlockPos o = ground;
        int ground = o.getY();
        fresh("mature");
        /* growing up, by its travel: a Fledgling a few ticks short of Mature */
        quiet("almost_grown", 20, () -> life(d -> { d.setSaddle(ItemStack.EMPTY); d.setLook(2, d.line(), Diet.RAW.ordinal(), d.astral(), d.horn()); d.applyStageHealth(true);
            d.life.travel = Life.stageTicks(DrekkiConfig.STAGE_DAYS.get()) * 2 - 10; }));
        stepUntil("grows_into_a_mature", 40, 10, () -> {}, () -> pup() != null && pup().stage() == 3 && pup().getMaxHealth() == 50);
        lookAt("mature_without_a_saddle", 1.7);
        restore();
        /* the saddle slot, through the real screen */
        quiet("saddle_in_hand", 15, () -> { hotbar(0, "saddle"); emptyHand(); near(pup()); });
        step("unsaddled_click_opens_the_screen", 25, () -> interact("open its screen"), () -> ds() != null && ds().getMenu().saddled && !riding());
        step("saddle_shift_clicked_on", 15, () -> shiftClick(4 + 27), () -> pup() != null && pup().saddled() && ds() != null && ds().getMenu().slots.get(3).getItem().is(Items.SADDLE));
        quiet("screen_closed", 10, () -> mc().setScreen(null));
        lookAt("mature_with_a_saddle", 1.7);
        restore();
        /* mounting by a click, and its screen by the inventory key */
        quiet("to_mount", 15, () -> { emptyHand(); near(pup()); });
        step("mounts_with_an_empty_hand", 25, () -> interact("mount it"), SelfTestMature::riding);
        /* any item with no use of its own on a drekki mounts too */
        quiet("down_again", 20, () -> { mc().options.keyShift.setDown(true); after(4, () -> mc().options.keyShift.setDown(false)); });
        quiet("sword_in_hand", 15, () -> { hotbar(1, "diamond_sword"); select(1); near(pup()); });
        step("mounts_with_an_item_in_hand", 25, () -> interact("mount it with a sword in hand"), SelfTestMature::riding);
        step("inventory_key_opens_its_screen", 20, () -> mc().player.sendOpenInventory(), () -> ds() != null && ds().getMenu().saddled);
        quiet("riding_screen_closed", 10, () -> { mc().setScreen(null); mc().options.setCameraType(CameraType.THIRD_PERSON_BACK); });
        /* riding with the real keys */
        quiet("ride_start", 35, () -> { rideAt(o.getX() + 0.5, ground, o.getZ() - 8.5); });
        quiet("ride_forward", 60, () -> { DrekkiEntity c = pup(); mark = c == null ? 0 : c.getX(); keys(true, false, false); });
        check("rides_forward", 5, 2, () -> {}, "blocks ridden in 3 s", () -> pup() == null ? "?" : String.format("%.1f", pup().getX() - mark), () -> riding() && pup().getX() - mark > 12);
        quiet("ride_halt", 20, () -> keys(false, false, false));
        quiet("jump", 30, () -> { sample(28); keys(false, false, true); after(3, () -> keys(false, false, false)); });
        check("jumps_about_two_blocks", 5, 1, () -> {}, "the jump's height", () -> String.format("%.2f", peak - ground), () -> peak - ground > 1.6 && peak - ground < 2.6);
        quiet("stompers", 10, () -> give(Trait.CLOUD_STOMPERS));
        quiet("second_jump", 45, () -> { sample(42); keys(false, false, true); after(3, () -> keys(false, false, false)); after(12, () -> keys(false, false, true)); after(15, () -> keys(false, false, false)); });
        check("cloud_stompers_jumps_again_in_the_air", 5, 1, () -> {}, "the height of two jumps", () -> String.format("%.2f", peak - ground), () -> peak - ground > 3.2);
        quiet("winds", 35, () -> { give(Trait.FOUR_WINDS); rideAt(o.getX() + 0.5, ground, o.getZ() - 8.5); });
        quiet("dash", 40, () -> { DrekkiEntity c = pup(); mark = c == null ? 0 : c.getX(); sample(36); keys(false, true, false); after(3, () -> keys(false, false, false)); });
        step("four_winds_dash_shot", 2, () -> {}, () -> true);
        check("four_winds_dashes", 5, 1, () -> {}, "the dash: top speed a tick, and blocks covered", () -> String.format("%.2f, %.1f", fastest, pup() == null ? 0 : pup().getX() - mark), () -> fastest > 1.5 && pup() != null && pup().getX() - mark > 10);
        /* flight: the jump key held; looking down flies forward; let go, it falls and the fall is the rider's */
        quiet("wings", 35, () -> { give(Trait.WINGS_OF_HOPE); cmd("gamemode survival"); rideAt(o.getX() + 0.5, ground, o.getZ() - 8.5); server(m -> m.setHealth(20)); });
        quiet("take_off", 120, () -> keys(false, false, true));
        step("wings_of_hope_climbs", 2, () -> {}, () -> riding() && pup().getY() - ground > 12);
        quiet("lean_forward", 60, () -> { DrekkiEntity c = pup(); mark = c == null ? 0 : c.getX(); mc().player.setXRot(55); sample(58); });
        check("looking_down_flies_forward", 2, 1, () -> {}, "forward speed a tick, and blocks flown", () -> String.format("%.2f, %.1f", fastest, pup() == null ? 0 : pup().getX() - mark), () -> riding() && fastest > 0.5 && pup().getX() - mark > 15);
        quiet("let_go", 10, () -> { mc().player.setXRot(0); keys(false, false, false); });
        check("it_falls_and_the_fall_is_the_riders", 60, 6, () -> {}, "on the ground, the rider's health, the drekki's", () -> (pup() != null && pup().onGround()) + ", " + (me() == null ? "?" : me().getHealth()) + ", " + (sd() == null ? "?" : sd().getHealth()), () -> pup() != null && pup().onGround() && me() != null && me().getHealth() < 20 && sd() != null && sd().getHealth() == sd().getMaxHealth());
        /* Leap of Faith: the same fall, slow and safe */
        quiet("leap", 35, () -> { give(Trait.WINGS_OF_HOPE, Trait.LEAP_OF_FAITH); server(m -> m.setHealth(20)); rideAt(o.getX() + 0.5, ground, o.getZ() - 8.5); });
        quiet("up_again", 110, () -> keys(false, false, true));
        quiet("let_go_again", 40, () -> { keys(false, false, false); after(25, () -> sample(12)); });
        step("leap_of_faith_falls_slowly", 2, () -> {}, () -> riding() && !pup().onGround() && slowest > -0.25);
        stepUntil("and_lands_unhurt", 40, 20, () -> {}, () -> pup() != null && pup().onGround() && me() != null && me().getHealth() == 20);
        /* water: Ocean Walker walks on it; without it the rider is put off */
        quiet("pond", 30, () -> { cmd("gamemode creative"); cmd("fill " + at(o.offset(6, -3, -10)) + " " + at(o.offset(60, -1, -6)) + " water"); give(Trait.OCEAN_WALKER); });
        quiet("to_the_pond", 30, () -> rideAt(o.getX() + 2.5, ground, o.getZ() - 7.5));
        quiet("onto_the_pond", 28, () -> keys(true, false, false));
        check("ocean_walker_walks_on_water", 2, 0, () -> {}, "riding, and where", () -> riding() + (pup() == null ? "" : String.format(" x %.1f y %.2f", pup().getX() - o.getX(), pup().getY() - ground)), () -> riding() && pup().getX() > o.getX() + 8 && pup().getX() < o.getX() + 58 && pup().getY() > ground - 0.6 && pup().getY() < ground - 0.3);
        quiet("off_the_pond", 20, () -> { keys(false, false, false); give(); rideAt(o.getX() + 2.5, ground, o.getZ() - 7.5); });
        quiet("pond_wait", 20, () -> {});
        quiet("into_the_pond", 60, () -> keys(true, false, false));
        stepUntil("deep_water_puts_the_rider_off", 2, 4, () -> {}, () -> !riding() && me() != null && me().getVehicle() == null);
        /* lava: Lava Walker walks on it */
        quiet("pond_drained", 30, () -> { keys(false, false, false); cmd("fill " + at(o.offset(6, -3, -10)) + " " + at(o.offset(60, -1, -6)) + " stone"); });  /* lava poured on water turns to obsidian */
        quiet("lava_pond", 30, () -> { cmd("fill " + at(o.offset(6, -3, -10)) + " " + at(o.offset(60, -1, -6)) + " lava"); give(Trait.LAVA_WALKER); life(d -> d.teleportTo(o.getX() + 2.5, ground, o.getZ() - 7.5)); });
        quiet("to_the_lava", 30, () -> rideAt(o.getX() + 2.5, ground, o.getZ() - 7.5));
        quiet("onto_the_lava", 28, () -> keys(true, false, false));
        check("lava_walker_walks_on_lava", 2, 0, () -> {}, "riding, and where", () -> riding() + (pup() == null ? "" : String.format(" x %.1f y %.2f", pup().getX() - o.getX(), pup().getY() - ground)), () -> riding() && pup().getX() > o.getX() + 8 && pup().getX() < o.getX() + 58 && pup().getY() > ground - 0.6 && pup().getY() < ground - 0.3 && !mc().player.isOnFire() && sd() != null && sd().getHealth() == sd().getMaxHealth() && !sd().isOnFire());
        quiet("lava_gone", 40, () -> { keys(false, false, false); rideAt(o.getX() + 0.5, ground, o.getZ() + 0.5); cmd("fill " + at(o.offset(6, -3, -10)) + " " + at(o.offset(60, -1, -6)) + " stone"); });
        /* Predator's Eye from the saddle */
        quiet("eye", 20, () -> { give(Trait.PREDATORS_EYE); cmd("summon husk ^ ^ ^12 {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"seen\"]}"); });
        check("predators_eye_marks_hostile_mobs", 30, 3, () -> {}, "the husk glows", () -> { Entity e = tagged("seen"); return e == null ? "gone" : ((Mob) e).hasEffect(net.minecraft.world.effect.MobEffects.GLOWING); },
            () -> tagged("seen") instanceof Mob m && m.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING));
        /* the saddle taken off under the rider */
        quiet("down", 20, () -> { clearMobs(); mc().options.setCameraType(CameraType.FIRST_PERSON); });
        step("no_saddle_no_rider", 20, () -> life(d -> d.setSaddle(ItemStack.EMPTY)), () -> !riding() && pup() != null && !pup().saddled());
        /* it never suffocates */
        quiet("no_air", 80, () -> life(d -> { d.setAirSupply(0); d.setHealth(d.getMaxHealth()); d.setNoAi(true); d.level().setBlockAndUpdate(d.blockPosition().above(), Blocks.STONE.defaultBlockState()); }));
        step("it_never_suffocates", 2, () -> {}, () -> sd() != null && sd().getHealth() == sd().getMaxHealth());
        quiet("air_back", 20, () -> life(d -> { d.level().setBlockAndUpdate(d.blockPosition().above(), Blocks.AIR.defaultBlockState()); d.setNoAi(false); }));
        /* the abilities: the Pounce and the breath on real husks */
        quiet("pounce_setup", 30, () -> { give(); life(d -> { d.abilityAt = 0; d.setStance(Life.Stance.AGGRESSIVE); }); pupHere(); });
        quiet("pounce_foe", 14, () -> { DrekkiEntity c = pup(); if (c != null) cmd("summon husk " + (c.getX() + 4) + " " + c.getY() + " " + c.getZ() + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"pounced\"]}"); });
        step("pounce_shot", 2, () -> {}, () -> true);
        check("the_pounce_lands", 20, 4, () -> {}, "the husk's health and the ability's cooldown", () -> { Entity e = tagged("pounced"); DrekkiEntity d = sd(); return (e == null ? "dead" : String.valueOf(((Mob) e).getHealth())) + ", ready in " + (d == null ? "?" : d.abilityAt - Vitals.now(d)); },
            () -> { Entity e = tagged("pounced"); DrekkiEntity d = sd(); return d != null && d.abilityAt > Vitals.now(d) && (e == null || ((Mob) e).getHealth() <= 20 - Abilities.POUNCE + 2.5f); });
        quiet("breath_setup", 30, () -> { clearMobs(); life(d -> { d.disengage(); d.setLook(3, d.line(), Diet.COOKED.ordinal(), d.astral(), d.horn()); d.abilityAt = 0; }); pupHere(); });
        quiet("breath_foe", 24, () -> { DrekkiEntity c = pup(); if (c != null) cmd("summon husk " + (c.getX() + 3) + " " + c.getY() + " " + c.getZ() + " {NoAI:1b,PersistenceRequired:1b,Tags:[\"st\",\"breathed\"]}"); });
        step("breath_shot", 2, () -> {}, () -> true);
        check("the_breath_burns", 20, 4, () -> {}, "the husk's health and the ability's cooldown", () -> { Entity e = tagged("breathed"); DrekkiEntity d = sd(); return (e == null ? "dead" : String.valueOf(((Mob) e).getHealth())) + ", ready in " + (d == null ? "?" : d.abilityAt - Vitals.now(d)); },
            () -> { Entity e = tagged("breathed"); DrekkiEntity d = sd(); return d != null && d.abilityAt > Vitals.now(d) && (e == null || ((Mob) e).getHealth() < 20); });
        quiet("abilities_done", 20, () -> { clearMobs(); life(d -> { d.disengage(); d.setStance(Life.Stance.PASSIVE); }); });
        /* the rarity colors on the Traits tab: one of each tier, a recharging one, an offer */
        quiet("tiers", 20, () -> { life(d -> { d.setSaddle(new ItemStack(Items.SADDLE)); d.life.horizons = true; d.life.traits.clear(); d.life.cooldowns.clear();
            for (Trait t : new Trait[]{Trait.WINGS_OF_HOPE, Trait.LEAP_OF_FAITH, Trait.OPEN_ROAD, Trait.REWIND, Trait.OCEAN_WALKER}) d.life.traits.add(t.id);
            d.life.cooldowns.put(Trait.REWIND.id, Vitals.now(d) + 4000); d.life.offer = Trait.DRACONIC_VAMPIRISM.id; d.syncLife(); }); hotbar(0, "stick"); select(0); near(pup()); });
        quiet("sneak_for_the_screen", 8, () -> mc().options.keyShift.setDown(true));
        step("mature_screen", 25, () -> interact("open its screen with a sneak click, an item in hand"), () -> ds() != null && !riding());
        quiet("unsneak", 5, () -> mc().options.keyShift.setDown(false));
        step("rarity_colors_on_the_traits_tab", 10, () -> clickSpot("tab_traits"), () -> ds() != null && ds().spot("trait4") != null && ds().spot("offer") != null);
        step("epic_tooltip", 10, () -> hoverSpot("trait0"), () -> ds() != null);
        step("rare_tooltip", 10, () -> hoverSpot("trait1"), () -> ds() != null);
        step("uncommon_tooltip", 10, () -> hoverSpot("trait2"), () -> ds() != null);
        step("offer_tooltip", 10, () -> hoverSpot("offer"), () -> ds() != null);
        quiet("tiers_done", 20, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; mc().setScreen(null); life(d -> { d.life.offer = null; d.life.cooldowns.clear(); d.syncLife(); }); emptyHand(); });
    }
}
