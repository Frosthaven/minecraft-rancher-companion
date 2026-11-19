package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.client.screen.HatcheryScreen;
import dev.frosthaven.ranchercompanion.client.screen.TroughScreen;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.net.HatcheryView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The pup release's in-game test (-Drancher_companion.selftest=true): in single player or on a server where the player
 * is an operator, it builds a test stable 200 blocks up and plays the whole flow through the game's own client
 * interactions (the same packets a click sends; no desktop input): egg, incubation tooltips, a failed and a good hatch,
 * check out, follow, pet, hat, name tag, Easy Anvils' quick rename, carrier, check in, sleep on bedding, the trough
 * screen, the trader's hats and release. Each step logs "[selftest] PASS|FAIL <step>" and takes a screenshot
 * selftest_<n>_<step>.png; at the end it logs a summary and closes the game.
 */
public final class SelfTest {
    private SelfTest() {}

    public static final boolean ON = Boolean.getBoolean("rancher_companion.selftest");
    /** with -Drancher_companion.selftest.traits=true: hatch and check out a pup, then only the trait checks */
    private static final boolean TRAITS = Boolean.getBoolean("rancher_companion.selftest.traits");
    /** with -Drancher_companion.selftest.ui=true: hatch and check out a pup, then only the trait screen and chat shots */
    private static final boolean UI = Boolean.getBoolean("rancher_companion.selftest.ui");

    /** patience: for a check that waits on the server (movement, AI), how many more 20-tick tries before it fails */
    private record Step(String name, int delay, Runnable act, BooleanSupplier check, boolean shot, int patience) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer, started = -1, passed, failed, waited;
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos o, hatch, trough;
    /** the relog step: the world folder to open again once the title screen shows */
    private static String reopen;

    static Minecraft mc() { return Minecraft.getInstance(); }

    static void cmd(String c) {
        Minecraft mc = mc();
        var server = mc.getSingleplayerServer();
        java.util.UUID me = mc.player.getUUID();
        if (server != null) server.execute(() -> {  /* as the player (so @s is them), with full permission */
            var p = server.getPlayerList().getPlayer(me);
            server.getCommands().performPrefixedCommand(p != null ? p.createCommandSourceStack().withPermission(4) : server.createCommandSourceStack(), c);
        });
        else mc.player.connection.sendCommand(c);
    }

    static void step(String name, int delay, Runnable act, BooleanSupplier check) { STEPS.add(new Step(name, delay, act, check, true, 0)); }
    private static void shiftClick(int slot) {
        var sc = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc().screen;
        mc().gameMode.handleInventoryMouseClick(sc.getMenu().containerId, slot, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc().player);
    }
    /** a 9 x 9 stone pad at y 100 (the level's y for the overworld's pad is o's) with air above, its chunk kept loaded */
    private static void pad(String dim, int x, int z) {
        int y = dim.endsWith("overworld") ? o.getY() : 100;
        cmd("execute in " + dim + " run forceload add " + x + " " + z);
        cmd("execute in " + dim + " run fill " + (x - 4) + " " + y + " " + (z - 4) + " " + (x + 4) + " " + y + " " + (z + 4) + " minecraft:stone");
        cmd("execute in " + dim + " run fill " + (x - 4) + " " + (y + 1) + " " + (z - 4) + " " + (x + 4) + " " + (y + 4) + " " + (z + 4) + " minecraft:air");
    }

    static void stepUntil(String name, int delay, int patience, Runnable act, BooleanSupplier check) { STEPS.add(new Step(name, delay, act, check, true, patience)); }
    static void quiet(String name, int delay, Runnable act) { STEPS.add(new Step(name, delay, act, null, false, 0)); }

    static String at(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    static void hotbar(int slot, String item) { cmd("item replace entity @s hotbar." + slot + " with " + item); }

    static void select(int slot) { mc().player.getInventory().selected = slot; }

    static void useOn(BlockPos pos, Direction face) {
        Minecraft mc = mc();
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
    }

    /** the pup as the server sees it, for a failed sleep check: where it is, whether it sleeps, its pen and bedding, its goals */
    private static void sleepState() {
        var srv = mc().getSingleplayerServer();
        DrekkiEntity c = pup();
        if (srv == null || c == null) { RancherCompanion.LOG.info("[selftest] sleep state: no pup on the client"); return; }
        srv.execute(() -> {
            if (!(srv.overworld().getEntity(c.getUUID()) instanceof DrekkiEntity d)) { RancherCompanion.LOG.info("[selftest] sleep state: no server pup"); return; }
            var beds = d.bedding(); var pen = d.pen();
            StringBuilder goals = new StringBuilder();
            d.goalSelector.getAvailableGoals().forEach(w -> { if (w.isRunning()) goals.append(w.getGoal().getClass().getSimpleName()).append(' '); });
            RancherCompanion.LOG.info("[selftest] sleep state: at {} home {} out {} sleeping {} pen {} beds {} onBed {} path {} goals [{}] time {}",
                d.blockPosition(), d.home(), d.isOut(), d.isSleepingLoop(), pen == null ? -1 : pen.size(), beds == null ? -1 : beds.size(),
                beds != null && beds.contains(dev.frosthaven.ranchercompanion.ranch.Flood.pack(d.getBlockX(), d.getBlockY(), d.getBlockZ())),
                d.getNavigation().getPath() == null ? "none" : d.getNavigation().getPath().getTarget(), goals, d.level().getDayTime() % 24000);
        });
    }

    /** this player's nearest drekki (a world may hold other players' drekki near the test site) */
    static DrekkiEntity pup() {
        Minecraft mc = mc();
        DrekkiEntity best = null;
        for (var e : mc.level.entitiesForRendering()) if (e instanceof DrekkiEntity d && d.live() && mc.player.getUUID().equals(d.owner()) && (best == null || d.distanceTo(mc.player) < best.distanceTo(mc.player))) best = d;
        return best;
    }

    static void near(DrekkiEntity d) {
        if (d != null) cmd("tp @s " + (d.getX() + 1.2) + " " + d.getY() + " " + (d.getZ() + 1.2));
    }

    private static net.minecraft.world.InteractionResult last = net.minecraft.world.InteractionResult.PASS;

    static void interact(String why) {
        DrekkiEntity d = pup();
        if (d == null) { RancherCompanion.LOG.warn("[selftest] no drekki to {}", why); return; }
        last = mc().gameMode.interact(mc().player, d, InteractionHand.MAIN_HAND);
    }

    static dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen ds() {
        return mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen s ? s : null;
    }

    /** the cursor over a part of the drekki screen (its tooltip shows in the step's screenshot) */
    static void hoverSpot(String key) { if (ds() != null && ds().spot(key) != null) dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = ds().spot(key); }

    static void clickSpot(String key) { if (ds() != null && ds().spot(key) != null) { int[] c = ds().spot(key); hover(c); ds().mouseClicked(c[0], c[1], 0); } }

    /** the player as the integrated server sees it */
    static net.minecraft.server.level.ServerPlayer me() {
        var server = mc().getSingleplayerServer();
        return server == null ? null : server.getPlayerList().getPlayer(mc().player.getUUID());
    }

    /** changes the drekki's life on the integrated server (single player), to show each screen state without waiting days */
    static void life(java.util.function.Consumer<DrekkiEntity> f) {
        var server = mc().getSingleplayerServer();
        DrekkiEntity c = pup();
        if (server == null || c == null) return;
        java.util.UUID id = c.getUUID();
        server.execute(() -> { for (var l : server.getAllLevels()) if (l.getEntity(id) instanceof DrekkiEntity d) { f.accept(d); d.syncLife(); } });
    }

    /** the camera just over the drekki: it and its mood icon in view, the crosshair off it (so Jade's box stays away) */
    private static void lookOver() {
        DrekkiEntity d = pup();
        if (d != null) mc().player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, d.getBbHeight() + 1.6, 0));
    }

    /** the camera on the drekki, for Jade */
    private static void look() {
        DrekkiEntity d = pup();
        if (d != null) mc().player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, d.position().add(0, d.getBbHeight() * 0.5, 0));
    }

    private static HatcheryScreen hs() { return mc().screen instanceof HatcheryScreen s ? s : null; }

    private static boolean where(int where) {
        HatcheryScreen s = hs();
        return s != null && s.view().rows().stream().anyMatch(r -> r.where() == where);
    }

    private static void hover(int[] gui) {
        var w = mc().getWindow();
        GLFW.glfwSetCursorPos(w.getWindow(), gui[0] * w.getGuiScale(), gui[1] * w.getGuiScale());
    }

    private static void build() {
        BlockPos p0 = mc().player.blockPosition();  /* the site, known when the steps are built (later steps use it) */
        o = new BlockPos(p0.getX(), 201, p0.getZ());
        hatch = o.offset(3, 0, 0);
        trough = o.offset(-3, 0, 3);
        quiet("setup", 40, () -> {
            cmd("gamemode creative");
            cmd("time set 1000");
            cmd("gamerule doMobSpawning false");
            cmd("fill " + at(o.offset(-9, 0, -9)) + " " + at(o.offset(9, 11, 9)) + " air");
            cmd("fill " + at(o.offset(-9, -1, -9)) + " " + at(o.offset(9, -1, 9)) + " stone");
            cmd("fill " + at(o.offset(36, -1, -3)) + " " + at(o.offset(44, -1, 3)) + " stone");
            cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5) + " -90 30");
            cmd("clear @s");
            /* drekki left by an earlier run in this world: gone, so this run's checks see only its own */
            var server = mc().getSingleplayerServer();
            java.util.UUID me = mc().player.getUUID();
            if (server != null) server.execute(() -> {
                var r = dev.frosthaven.ranchercompanion.ranch.Ranch.roster(server);
                for (var e : r.of(me)) r.remove(e.id);
                for (var l : server.getAllLevels()) for (var en : l.getAllEntities()) if (en instanceof DrekkiEntity d && me.equals(d.owner())) d.discard();
            });
            cmd("setblock " + at(hatch) + " rancher_companion:hatchery[facing=west]");
            cmd("setblock " + at(o.offset(1, 0, -3)) + " torch");
        });
        step("creative_egg_turns_into_egg", 30, () -> hotbar(0, "rancher_companion:creative_egg_day_1"), () -> {
            ItemStack s = mc().player.getInventory().getItem(0);
            return s.is(DrekkiItems.INTRIGUING_EGG.get()) && s.has(DrekkiData.INCUBATION.get());
        });
        step("egg_into_nest", 30, () -> { select(0); useOn(hatch, Direction.UP); },
            () -> mc().level.getBlockEntity(hatch) instanceof HatcheryBlockEntity be && be.hasEgg());
        quiet("open_day1", 25, () -> { select(8); useOn(hatch, Direction.NORTH); });
        step("egg_tooltip_warm", 15, () -> { if (hs() != null) hover(hs().eggCenter()); }, () -> tooltip("warm"));
        for (String[] d : new String[][]{{"2", "noises"}, {"3", "active"}, {"4", "ready"}}) {
            quiet("advance_day" + d[0], 30, () -> { cmd("time add 24000"); mc().setScreen(null); });
            quiet("open_day" + d[0], 25, () -> useOn(hatch, Direction.NORTH));
            step("egg_tooltip_" + d[1], 15, () -> { if (hs() != null) hover(hs().eggCenter()); }, () -> tooltip(d[1]));
        }
        step("hatch_refused_without_roof", 30, () -> { mc().setScreen(null); useOn(hatch, Direction.UP); }, () -> pup() == null);
        quiet("roof", 20, () -> { mc().setScreen(null); cmd("fill " + at(hatch.offset(-1, 4, -1)) + " " + at(hatch.offset(1, 4, 1)) + " oak_planks"); cmd("time set 1000"); });
        step("hatch", 40, () -> { mc().setScreen(null); useOn(hatch, Direction.UP); }, () -> pup() != null && pup().stage() == 1 && hs() != null);
        step("check_out", 30, () -> { if (hs() != null) { hs().press("row0"); hs().press("out"); } }, () -> where(HatcheryView.WITH_YOU));
        quiet("walk_off", 100, () -> { mc().setScreen(null); cmd("tp @s " + (o.getX() + 7.5) + " " + o.getY() + " " + (o.getZ() + 6.5)); });
        stepUntil("follows", 10, 15, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 6);
        if (TRAITS) { SelfTestTraits.build(o); return; }
        if (UI) { SelfTestTraits.ui(); return; }
        quiet("far_away", 80, () -> cmd("tp @s " + (o.getX() + 40.5) + " " + o.getY() + " " + (o.getZ() + 0.5)));
        stepUntil("teleports_to_owner", 10, 15, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 6);
        quiet("back", 80, () -> cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() - 4.5)));
        quiet("near_for_pet", 15, () -> near(pup()));
        quiet("sneak", 5, () -> { select(8); mc().options.keyShift.setDown(true); });
        step("pet", 6, () -> {
            DrekkiEntity d = pup();
            RancherCompanion.LOG.info("[selftest] pet: shift {}, hand '{}', distance {}", mc().player.isShiftKeyDown(), mc().player.getMainHandItem(), d == null ? -1 : d.distanceTo(mc().player));
            interact("pet");
        }, () -> pup() != null && pup().petTicks() > 0 && last.shouldSwing());  /* the client swings the arm as on any use */
        quiet("unsneak", 30, () -> mc().options.keyShift.setDown(false));
        quiet("give_hat", 15, () -> { hotbar(1, "rancher_companion:straw_hat"); near(pup()); });
        step("hat_on", 20, () -> { select(1); interact("hat"); }, () -> pup() != null && pup().hat() == 5);
        quiet("give_tag", 15, () -> { hotbar(2, "name_tag[custom_name='\"Pip\"']"); near(pup()); });
        step("name_tag", 20, () -> { select(2); interact("name"); }, () -> pup() != null && pup().hasCustomName() && "Pip".equals(pup().getCustomName().getString()));
        quiet("near_for_screen", 15, () -> { select(8); near(pup()); });
        step("drekki_screen", 25, () -> interact("open its screen"),
            () -> mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen s && s.getMenu().slots.get(0).getItem().is(DrekkiItems.hatStack(5).getItem()));
        step("drekki_screen_hat_off", 15, () -> shiftClick(0),  /* the empty slot shows the dim hat */
            () -> mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen s && s.getMenu().slots.get(0).getItem().isEmpty());
        step("drekki_screen_hat_back", 15, () -> {
            var m = ((dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen) mc().screen).getMenu();
            for (int i = 1; i < m.slots.size(); i++) if (m.slots.get(i).getItem().is(DrekkiItems.hatStack(5).getItem())) { shiftClick(i); break; }
        }, () -> mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen s && s.getMenu().slots.get(0).getItem().is(DrekkiItems.hatStack(5).getItem()));
        /* the growth release's details: food, mood and its buffs, traits (set on the server to show them now) */
        quiet("life_for_screens", 20, () -> life(d -> { d.life.food = 6.5; d.life.mood = 70; dev.frosthaven.ranchercompanion.life.Vitals.pet(d);
            d.life.traits.add(dev.frosthaven.ranchercompanion.trait.Trait.HOOK_LINE_SINKER.id); d.life.traits.add(dev.frosthaven.ranchercompanion.trait.Trait.CLOUD_STOMPERS.id); }));
        step("drekki_details", 10, () -> {}, () -> ds() != null && pup().food() < 7 && pup().petLeft() > 0);
        step("drekki_mood_tooltip", 10, () -> hoverSpot("mood"), () -> ds() != null && ds().spot("mood") != null);
        step("drekki_pet_tooltip", 10, () -> hoverSpot("pet"), () -> ds() != null && ds().spot("pet") != null);
        step("drekki_traits_tab", 10, () -> clickSpot("tab_traits"), () -> ds() != null && ds().spot("trait1") != null);
        step("drekki_trait_tooltip", 10, () -> hoverSpot("trait1"), () -> ds() != null);
        quiet("details_again", 5, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; clickSpot("tab_details"); });
        quiet("close_screen", 10, () -> mc().setScreen(null));
        quiet("give_sword", 10, () -> hotbar(5, "iron_sword"));
        step("drekki_screen_with_sword", 25, () -> { select(5); interact("open its screen holding a sword"); },
            () -> mc().screen instanceof dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen);
        quiet("close_screen2", 10, () -> mc().setScreen(null));
        quiet("give_tag2", 10, () -> { hotbar(2, "name_tag"); mc().options.keyShift.setDown(true); });
        step("easy_anvils_quick_rename", 25, () -> { select(2); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); },
            () -> { String n = mc().screen == null ? "none" : mc().screen.getClass().getName(); RancherCompanion.LOG.info("[selftest] screen after sneak+use name tag: {}", n); return n.contains("NameTag"); });
        quiet("close_rename", 10, () -> { mc().options.keyShift.setDown(false); mc().setScreen(null); });
        quiet("give_carrier", 15, () -> { hotbar(3, "rancher_companion:small_drekki_carrier"); near(pup()); });
        step("into_carrier", 20, () -> { select(3); interact("carry"); },
            () -> pup() == null && mc().player.getInventory().getItem(3).has(DrekkiData.CARRIED.get()));
        step("let_out", 25, () -> useOn(mc().player.blockPosition().offset(2, -1, 0), Direction.UP), () -> pup() != null && !mc().player.getInventory().getItem(3).has(DrekkiData.CARRIED.get()));
        /* a checked-out drekki comes back to its owner on the real events: a dimension change, its chunk unloading, a respawn */
        quiet("nether_pad", 20, () -> { cmd("gamerule keepInventory true"); pad("minecraft:the_nether", 0, 0); });
        stepUntil("follows_into_the_nether", 40, 15, () -> cmd("execute in minecraft:the_nether run tp @s 0.5 101 0.5"),
            () -> mc().level.dimension() == net.minecraft.world.level.Level.NETHER && pup() != null && pup().distanceTo(mc().player) < 6);
        stepUntil("follows_back_to_the_overworld", 40, 15, () -> cmd("execute in minecraft:overworld run tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() - 4.5)),
            () -> mc().level.dimension() == net.minecraft.world.level.Level.OVERWORLD && pup() != null && pup().distanceTo(mc().player) < 6);
        quiet("far_pad", 20, () -> pad("minecraft:overworld", o.getX() + 3000, o.getZ()));
        stepUntil("back_after_its_chunk_unloads", 60, 30, () -> cmd("tp @s " + (o.getX() + 3000.5) + " " + (o.getY() + 1) + " " + (o.getZ() + 0.5)),
            () -> pup() != null && pup().distanceTo(mc().player) < 6);
        quiet("die", 40, () -> { cmd("spawnpoint @s " + o.getX() + " " + o.getY() + " " + (o.getZ() - 4)); cmd("kill @s"); });
        stepUntil("back_after_a_respawn", 40, 15, () -> mc().player.respawn(),
            () -> mc().player.isAlive() && mc().player.distanceToSqr(o.getX(), o.getY(), o.getZ() - 4) < 25 && pup() != null && pup().distanceTo(mc().player) < 6);
        /* log out and in through the game's own events: quit to the title screen (the server logs the player out) and
           open the world again (it logs them in); single player only */
        if (mc().getSingleplayerServer() != null) {
            quiet("relog", 80, () -> {
                reopen = mc().getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
                mc().level.disconnect();
                mc().disconnect(new net.minecraft.client.gui.screens.TitleScreen());
            });
            stepUntil("back_after_logging_in", 40, 15, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 6);
        }
        /* growing up on travel: the stage, the astral lock-in and their messages; then the Fledgling's screen */
        quiet("grow_soon", 10, () -> life(d -> d.life.travel = dev.frosthaven.ranchercompanion.life.Life.stageTicks(dev.frosthaven.ranchercompanion.DrekkiConfig.STAGE_DAYS.get()) - 60));
        stepUntil("grew_to_fledgling", 80, 15, () -> {}, () -> pup() != null && pup().stage() == 2 && pup().astral() != 0);
        quiet("near_fledgling", 15, () -> { select(7); near(pup()); });  /* an empty slot: slot 9 holds the hat by now */
        step("fledgling_screen", 25, () -> interact("open its screen"), () -> ds() != null && ds().getMenu().pack && ds().spot("stance0") != null);
        step("stance_tooltip", 10, () -> hoverSpot("stance0"), () -> ds() != null);
        step("stance_aggressive", 15, () -> clickSpot("stance0"), () -> pup().stance() == dev.frosthaven.ranchercompanion.life.Life.Stance.AGGRESSIVE);
        quiet("close_fledgling", 10, () -> { dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen.testMouse = null; mc().setScreen(null); });
        quiet("miserable", 30, () -> life(d -> { d.life.food = 0; d.life.mood = 5; d.life.petUntil = 0; }));
        step("mood_icon_miserable", 10, SelfTest::lookOver, () -> pup() != null && pup().band() == dev.frosthaven.ranchercompanion.life.Life.Band.MISERABLE);
        step("jade_food", 10, SelfTest::look, () -> pup() != null && pup().food() == 0);
        quiet("ghost", 30, () -> life(d -> d.life.ghost = true));
        step("ghost_look", 10, SelfTest::lookOver, () -> pup() != null && pup().ghost());
        quiet("unghost", 20, () -> life(d -> { d.life.ghost = false; d.life.food = 9; d.life.mood = 60; d.life.stance = dev.frosthaven.ranchercompanion.life.Life.Stance.PASSIVE; }));
        quiet("to_hatchery", 20, () -> { select(8); cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5)); });
        quiet("open_for_check_in", 25, () -> useOn(hatch, Direction.NORTH));
        step("check_in", 30, () -> { if (hs() != null) hs().press("in"); }, () -> where(HatcheryView.HERE) && pup() != null && pup().blockPosition().distManhattan(hatch) <= 5);
        quiet("bedding_and_night", 400, () -> {
            mc().setScreen(null);
            for (int dx = -1; dx <= 1; dx++) cmd("setblock " + at(hatch.offset(dx, 0, 3)) + " rancher_companion:straw_bedding");
            cmd("time set 18000");
        });
        stepUntil("sleeps_on_bedding", 10, 30, () -> cmd("tp @s " + (hatch.getX() - 3.5) + " " + o.getY() + " " + (hatch.getZ() + 3.5) + " -90 20"),
            () -> {
                boolean ok = pup() != null && pup().isSleepingLoop() && pup().blockPosition().distManhattan(hatch.offset(0, 0, 3)) <= 2;
                if (!ok) sleepState();
                return ok;
            });
        quiet("morning", 20, () -> cmd("time set 1000"));
        quiet("trough", 25, () -> { cmd("setblock " + at(trough) + " rancher_companion:trough"); hotbar(4, "wheat 16"); cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5)); });
        quiet("open_trough", 20, () -> { select(8); useOn(trough, Direction.UP); });
        step("trough_screen_takes_food", 25, () -> {
            if (mc().screen instanceof TroughScreen t) mc().gameMode.handleInventoryMouseClick(t.getMenu().containerId, 4 + 27 + 4, 0, ClickType.QUICK_MOVE, mc().player);
        }, () -> mc().screen instanceof TroughScreen t && t.getMenu().slots.get(0).getItem().is(net.minecraft.world.item.Items.WHEAT));
        quiet("trader", 30, () -> { mc().setScreen(null); cmd("summon wandering_trader " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() - 2.5) + " {NoAI:1b}"); });
        step("trader_two_hats_three_each", 25, () -> {
            for (var e : mc().level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.npc.WanderingTrader w) { select(8); mc().gameMode.interact(mc().player, w, InteractionHand.MAIN_HAND); break; }
        }, () -> mc().screen instanceof MerchantScreen m && m.getMenu().getOffers().stream()
            .filter(of -> DrekkiItems.hatIndex(of.getResult()) > 0 && of.getMaxUses() == 3).count() == 2);
        step("drekki_hat_subtext", 5, () -> mc().setScreen(null), () -> {
            var lines = DrekkiItems.hatStack(5).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc().level), mc().player, TooltipFlag.NORMAL);
            return lines.size() > 1 && lines.get(1).getString().equals("Drekki Hat");
        });
        if (net.neoforged.fml.ModList.get().isLoaded("ponder")) {
            String hat = RancherCompanion.ID + ":hatchery", tr = RancherCompanion.ID + ":trough";
            quiet("ponder_open_stable", 300, () -> dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.open(net.minecraft.resources.ResourceLocation.parse(hat)));
            step("ponder_stable_roof", 400, () -> {}, () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("PonderUI"));
            step("ponder_stable_wander", 500, () -> {}, null);
            step("ponder_stable_end", 10, () -> {}, null);
            quiet("ponder_open_trough", 60, () -> dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.open(net.minecraft.resources.ResourceLocation.parse(tr)));
            step("ponder_trough_alone", 46, () -> {}, () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("PonderUI"));  /* one trough, unjoined */
            step("ponder_trough_joining", 344, () -> {}, null);  /* the next troughs joining one by one */
            step("ponder_trough_line", 10, () -> {}, null);
            quiet("ponder_next_diets", 450, dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder::next);
            step("ponder_trough_diets", 10, () -> {}, null);
            quiet("ponder_next_feeding", 300, dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder::next);
            step("ponder_trough_feeding_side", 10, () -> {}, null);
            step("ponder_trough_feeding_mature", 100, () -> {}, null);  /* behind, and the line's end */
            step("ponder_trough_feeding_meal", 110, () -> {}, null);
            step("ponder_trough_feeding_owner", 80, () -> {}, null);
            quiet("ponder_close", 10, () -> mc().setScreen(null));
        }
        quiet("open_for_release", 25, () -> useOn(hatch, Direction.NORTH));
        quiet("release", 10, () -> { if (hs() != null) { hs().press("row0"); hs().press("release"); } });
        step("release_confirm", 10, () -> {}, () -> hs() != null);
        step("released", 30, () -> { if (hs() != null) hs().press("confirm"); }, () -> pup() == null && hs() != null && hs().view().rows().isEmpty());
    }

    private static boolean tooltip(String key) {
        HatcheryScreen s = hs();
        if (s == null) return false;
        var lines = s.view().egg().getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc().level), mc().player, TooltipFlag.NORMAL);
        String all = lines.stream().map(net.minecraft.network.chat.Component::getString).reduce("", (a, b) -> a + " | " + b);
        RancherCompanion.LOG.info("[selftest] egg tooltip: {}", all);
        return all.contains(net.minecraft.network.chat.Component.translatable("item.rancher_companion.intriguing_egg." + key).getString());
    }

    public static void tick(ClientTickEvent.Post e) {
        if (!ON) return;
        Minecraft mc = mc();
        if (mc.level == null || mc.player == null) {
            /* back at a menu (vanilla's title screen, or a menu mod's own), not while a world is still closing or loading */
            var sc = mc.screen;
            boolean menu = sc != null && !(sc instanceof net.minecraft.client.gui.screens.GenericMessageScreen || sc instanceof net.minecraft.client.gui.screens.ProgressScreen
                || sc instanceof net.minecraft.client.gui.screens.LevelLoadingScreen || sc instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                || sc instanceof net.minecraft.client.gui.screens.ConnectScreen);
            if (reopen != null && menu) {
                RancherCompanion.LOG.info("[selftest] at {}", sc.getClass().getName());
                String w = reopen; reopen = null;
                RancherCompanion.LOG.info("[selftest] reopening {}", w);
                mc.createWorldOpenFlows().openWorld(w, () -> RancherCompanion.LOG.error("[selftest] could not reopen {}", w));
            }
            return;
        }
        if (TRAITS || UI) SelfTestTraits.tick();
        if (started < 0) { started = 0; build(); timer = 200; RancherCompanion.LOG.info("[selftest] start, {} steps", STEPS.size()); return; }
        if (--timer > 0) return;
        if (index >= 0 && index < STEPS.size()) {
            Step s = STEPS.get(index);
            if (s.check != null) {
                boolean ok;
                try { ok = s.check.getAsBoolean(); } catch (RuntimeException ex) { ok = false; RancherCompanion.LOG.error("[selftest] {} threw", s.name, ex); }
                if (!ok && waited < s.patience) { waited++; timer = 20; return; }  /* the server may be behind: look again */
                waited = 0;
                if (ok) passed++; else { failed++; FAILS.add(s.name); }
                RancherCompanion.LOG.info("[selftest] {} {}", ok ? "PASS" : "FAIL", s.name);
            }
            if (s.shot) Screenshot.grab(mc.gameDirectory, String.format("selftest_%02d_%s.png", index, s.name), mc.getMainRenderTarget(), c -> {});
        }
        index++;
        if (index >= STEPS.size()) {
            RancherCompanion.LOG.info("[selftest] done: {} passed, {} failed {}", passed, failed, FAILS);
            index = Integer.MIN_VALUE / 2;
            timer = 40;
            return;
        }
        if (index < 0) { mc.stop(); return; }
        Step s = STEPS.get(index);
        try { s.act.run(); } catch (RuntimeException ex) { RancherCompanion.LOG.error("[selftest] {} threw", s.name, ex); }
        timer = s.delay;
    }
}
