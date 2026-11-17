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

    private record Step(String name, int delay, Runnable act, BooleanSupplier check, boolean shot) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer, started = -1, passed, failed;
    private static final List<String> FAILS = new ArrayList<>();
    private static BlockPos o, hatch, trough;

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static void cmd(String c) {
        Minecraft mc = mc();
        var server = mc.getSingleplayerServer();
        java.util.UUID me = mc.player.getUUID();
        if (server != null) server.execute(() -> {  /* as the player (so @s is them), with full permission */
            var p = server.getPlayerList().getPlayer(me);
            server.getCommands().performPrefixedCommand(p != null ? p.createCommandSourceStack().withPermission(4) : server.createCommandSourceStack(), c);
        });
        else mc.player.connection.sendCommand(c);
    }

    private static void step(String name, int delay, Runnable act, BooleanSupplier check) { STEPS.add(new Step(name, delay, act, check, true)); }
    private static void quiet(String name, int delay, Runnable act) { STEPS.add(new Step(name, delay, act, null, false)); }

    private static String at(BlockPos p) { return p.getX() + " " + p.getY() + " " + p.getZ(); }

    private static void hotbar(int slot, String item) { cmd("item replace entity @s hotbar." + slot + " with " + item); }

    private static void select(int slot) { mc().player.getInventory().selected = slot; }

    private static void useOn(BlockPos pos, Direction face) {
        Minecraft mc = mc();
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
    }

    private static DrekkiEntity pup() {
        Minecraft mc = mc();
        DrekkiEntity best = null;
        for (var e : mc.level.entitiesForRendering()) if (e instanceof DrekkiEntity d && d.live() && (best == null || d.distanceTo(mc.player) < best.distanceTo(mc.player))) best = d;
        return best;
    }

    private static void near(DrekkiEntity d) {
        if (d != null) cmd("tp @s " + (d.getX() + 1.2) + " " + d.getY() + " " + (d.getZ() + 1.2));
    }

    private static net.minecraft.world.InteractionResult last = net.minecraft.world.InteractionResult.PASS;

    private static void interact(String why) {
        DrekkiEntity d = pup();
        if (d == null) { RancherCompanion.LOG.warn("[selftest] no drekki to {}", why); return; }
        last = mc().gameMode.interact(mc().player, d, InteractionHand.MAIN_HAND);
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
        quiet("setup", 40, () -> {
            BlockPos p = mc().player.blockPosition();
            o = new BlockPos(p.getX(), 201, p.getZ());
            hatch = o.offset(3, 0, 0);
            trough = o.offset(-3, 0, 3);
            cmd("gamemode creative");
            cmd("time set 1000");
            cmd("gamerule doMobSpawning false");
            cmd("fill " + at(o.offset(-9, 0, -9)) + " " + at(o.offset(9, 11, 9)) + " air");
            cmd("fill " + at(o.offset(-9, -1, -9)) + " " + at(o.offset(9, -1, 9)) + " stone");
            cmd("fill " + at(o.offset(36, -1, -3)) + " " + at(o.offset(44, -1, 3)) + " stone");
            cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5) + " -90 30");
            cmd("clear @s");
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
        step("follows", 10, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 6);
        quiet("far_away", 80, () -> cmd("tp @s " + (o.getX() + 40.5) + " " + o.getY() + " " + (o.getZ() + 0.5)));
        step("teleports_to_owner", 10, () -> {}, () -> pup() != null && pup().distanceTo(mc().player) < 6);
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
        quiet("close_screen", 10, () -> mc().setScreen(null));
        quiet("give_tag2", 10, () -> { hotbar(2, "name_tag"); mc().options.keyShift.setDown(true); });
        step("easy_anvils_quick_rename", 25, () -> { select(2); mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND); },
            () -> { String n = mc().screen == null ? "none" : mc().screen.getClass().getName(); RancherCompanion.LOG.info("[selftest] screen after sneak+use name tag: {}", n); return n.contains("NameTag"); });
        quiet("close_rename", 10, () -> { mc().options.keyShift.setDown(false); mc().setScreen(null); });
        quiet("give_carrier", 15, () -> { hotbar(3, "rancher_companion:small_drekki_carrier"); near(pup()); });
        step("into_carrier", 20, () -> { select(3); interact("carry"); },
            () -> pup() == null && mc().player.getInventory().getItem(3).has(DrekkiData.CARRIED.get()));
        step("let_out", 25, () -> useOn(mc().player.blockPosition().offset(2, -1, 0), Direction.UP), () -> pup() != null && !mc().player.getInventory().getItem(3).has(DrekkiData.CARRIED.get()));
        quiet("to_hatchery", 20, () -> { select(8); cmd("tp @s " + (o.getX() + 0.5) + " " + o.getY() + " " + (o.getZ() + 0.5)); });
        quiet("open_for_check_in", 25, () -> useOn(hatch, Direction.NORTH));
        step("check_in", 30, () -> { if (hs() != null) hs().press("in"); }, () -> where(HatcheryView.HERE) && pup() != null && pup().blockPosition().distManhattan(hatch) <= 5);
        quiet("bedding_and_night", 400, () -> {
            mc().setScreen(null);
            for (int dx = -1; dx <= 1; dx++) cmd("setblock " + at(hatch.offset(dx, 0, 3)) + " rancher_companion:straw_bedding");
            cmd("time set 18000");
        });
        step("sleeps_on_bedding", 10, () -> cmd("tp @s " + (hatch.getX() - 3.5) + " " + o.getY() + " " + (hatch.getZ() + 3.5) + " -90 20"),
            () -> pup() != null && pup().isSleepingLoop() && pup().blockPosition().distManhattan(hatch.offset(0, 0, 3)) <= 2);
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
            quiet("ponder_open_trough", 450, () -> dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.open(net.minecraft.resources.ResourceLocation.parse(tr)));
            step("ponder_trough_line", 10, () -> {}, () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("PonderUI"));
            quiet("ponder_next_diets", 450, dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder::next);
            step("ponder_trough_diets", 10, () -> {}, null);
            quiet("ponder_next_feeding", 300, dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder::next);
            step("ponder_trough_feeding_side", 10, () -> {}, null);
            step("ponder_trough_feeding_top", 300, () -> {}, null);
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
        if (mc.level == null || mc.player == null) return;
        if (started < 0) { started = 0; build(); timer = 200; RancherCompanion.LOG.info("[selftest] start, {} steps", STEPS.size()); return; }
        if (--timer > 0) return;
        if (index >= 0 && index < STEPS.size()) {
            Step s = STEPS.get(index);
            if (s.check != null) {
                boolean ok;
                try { ok = s.check.getAsBoolean(); } catch (RuntimeException ex) { ok = false; RancherCompanion.LOG.error("[selftest] {} threw", s.name, ex); }
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
