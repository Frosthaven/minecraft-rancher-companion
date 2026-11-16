package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.showcase.BlockShowcase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The block showcase's camera (-Drancher_companion.blockshowcase=true): builds the scene on the integrated server, takes a
 * screenshot from each of BlockShowcase's shots, then the mod's creative tab page by page (one with the egg's tooltip)
 * and the wandering trader's trade screen with the egg offer, then closes the game. Screenshots: blocks_<name>.png.
 */
public final class BlockShowcaseClient {
    private BlockShowcaseClient() {}

    private record Step(int delay, Runnable run) {}

    private static List<Step> steps;
    private static int index, countdown = -1, loaded;

    public static void tick(ClientTickEvent.Post e) {
        if (!BlockShowcase.AUTO) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (steps == null) {
            if (++loaded < 60) return;
            steps = plan(mc);
            index = 0;
            countdown = steps.get(0).delay;
        }
        if (index >= steps.size()) return;
        if (--countdown > 0) return;
        try {
            steps.get(index).run.run();
        } catch (Exception x) {
            RancherCompanion.LOG.error("block showcase step {} failed", index, x);
        }
        index++;
        if (index < steps.size()) countdown = steps.get(index).delay;
    }

    private static ServerPlayer serverPlayer(Minecraft mc) {
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> work) {
        mc.getSingleplayerServer().execute(() -> work.accept(serverPlayer(mc)));
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "blocks_" + name + ".png", mc.getMainRenderTarget(), c -> {});
        RancherCompanion.LOG.info("block showcase: {}", name);
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        s.add(new Step(1, () -> { mc.options.hideGui = true; onServer(mc, BlockShowcase::prepare); }));
        s.add(new Step(60, () -> onServer(mc, BlockShowcase::build)));
        for (BlockShowcase.Shot sh : BlockShowcase.shots()) {
            s.add(new Step(sh == BlockShowcase.shots().get(0) ? 120 : 10, () -> onServer(mc, p -> {
                double x = BlockShowcase.OX + sh.x(), y = BlockShowcase.OY + sh.y(), z = BlockShowcase.OZ + sh.z();
                double dx = sh.tx() - sh.x(), dy = sh.ty() - sh.y(), dz = sh.tz() - sh.z();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
                p.teleportTo(p.serverLevel(), x, y - 1.62, z, yaw, pitch);  /* the eye sits 1.62 above the feet */
            })));
            s.add(new Step(50, () -> shot(mc, sh.name())));
        }
        // the creative tab, page by page, then the egg's tooltip
        s.add(new Step(10, () -> { mc.options.hideGui = false; mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), false)); }));
        s.add(new Step(10, () -> selectTab(mc, DrekkiItems.TAB.get())));
        int pages = 4;
        for (int i = 0; i < pages; i++) {
            float at = i / (float) (pages - 1);
            s.add(new Step(10, () -> { scroll(mc, at); move(mc, 2, 2); }));  /* the mouse off the slots: no stray tooltip */
            s.add(new Step(10, () -> shot(mc, "inventory_" + (int) (at * 100))));
        }
        s.add(new Step(10, () -> { scroll(mc, 0); hoverItem(mc, DrekkiItems.INTRIGUING_EGG.get()); }));
        s.add(new Step(10, () -> shot(mc, "inventory_egg_tooltip")));
        s.add(new Step(5, () -> hoverItem(mc, net.minecraft.world.item.Item.byBlock(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.TROUGH.get()))));
        s.add(new Step(10, () -> shot(mc, "inventory_trough_tooltip")));
        // the trader's trade screen, the egg offer first
        s.add(new Step(10, () -> { mc.setScreen(null); onServer(mc, p -> {
            if (p.serverLevel().getEntity(BlockShowcase.traderId) instanceof WanderingTrader t) {
                t.setTradingPlayer(p);
                t.openTradingScreen(p, t.getDisplayName(), 1);
            }
        }); }));
        s.add(new Step(20, () -> { if (mc.screen instanceof MerchantScreen m) move(mc, m.getGuiLeft() + 5 + 75, m.getGuiTop() + 18 + 9); }));
        s.add(new Step(10, () -> shot(mc, "trader")));
        s.add(new Step(10, () -> { mc.setScreen(null); onServer(mc, p -> p.teleportTo(p.serverLevel(), BlockShowcase.OX - 3.5, BlockShowcase.OY, BlockShowcase.OZ + 11.5, 135, 10)); }));
        s.add(new Step(50, () -> { mc.options.hideGui = true; shot(mc, "trader_world"); }));
        s.add(new Step(20, () -> mc.stop()));
        return s;
    }

    // ---- the creative screen, through its private parts ----

    private static void selectTab(Minecraft mc, CreativeModeTab tab) {
        if (!(mc.screen instanceof CreativeModeInventoryScreen scr)) return;
        try {
            Method m = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            m.setAccessible(true);
            m.invoke(scr, tab);
        } catch (ReflectiveOperationException x) { RancherCompanion.LOG.error("block showcase: selectTab", x); }
    }

    private static void scroll(Minecraft mc, float at) {
        if (!(mc.screen instanceof CreativeModeInventoryScreen scr)) return;
        try {
            Field f = CreativeModeInventoryScreen.class.getDeclaredField("scrollOffs");
            f.setAccessible(true);
            f.setFloat(scr, at);
            scr.getMenu().scrollTo(at);
        } catch (ReflectiveOperationException x) { RancherCompanion.LOG.error("block showcase: scroll", x); }
    }

    private static void hoverItem(Minecraft mc, net.minecraft.world.item.Item item) {
        if (!(mc.screen instanceof CreativeModeInventoryScreen scr)) return;
        for (int pass = 0; pass < 2; pass++) {
            for (Slot slot : scr.getMenu().slots) {
                if (slot.getItem().is(item)) { move(mc, scr.getGuiLeft() + slot.x + 8, scr.getGuiTop() + slot.y + 8); return; }
            }
            scroll(mc, 1);  /* not on the first page: try the last */
        }
    }

    /** the mouse to a GUI position, through MouseHandler as GLFW would move it */
    private static void move(Minecraft mc, double gx, double gy) {
        var w = mc.getWindow();
        double x = gx * w.getScreenWidth() / w.getGuiScaledWidth(), y = gy * w.getScreenHeight() / w.getGuiScaledHeight();
        try {
            Method m = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
            m.setAccessible(true);
            m.invoke(mc.mouseHandler, w.getWindow(), x, y);
        } catch (ReflectiveOperationException x2) { RancherCompanion.LOG.error("block showcase: mouse", x2); }
    }
}
