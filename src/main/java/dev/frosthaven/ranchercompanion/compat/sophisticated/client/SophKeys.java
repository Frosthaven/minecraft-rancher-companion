package dev.frosthaven.ranchercompanion.compat.sophisticated.client;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.net.OpenBag;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedbackpacks.client.KeybindHandler;

/**
 * Sophisticated's own "Open Backpack" key, whatever it is bound to, on one's own drekki (spec, Accessory: bags): looking
 * at it while it wears a bag, the press opens that bag instead of one's backpack; in the drekki screen, over the bag in
 * its accessory slot (Sophisticated opens only bags in the player's own inventory slots). Sophisticated reads the key after the
 * client tick; this reads it before, and takes the press only when it is meant for the drekki.
 */
public final class SophKeys {
    private SophKeys() {}

    /** a key press in a screen that is Sophisticated's Open Backpack key, whatever it is bound to */
    public static boolean isOpenKey(int keyCode, int scanCode) {
        return KeybindHandler.BACKPACK_OPEN_KEYBIND.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode));
    }

    /** the self test: a press of the key, as the keyboard makes one */
    /** the self test: the key pressed in the open screen over a slot, through the game's screen key event (as a keyboard does) */
    public static void pressInScreenForTest(net.minecraft.world.inventory.Slot over) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s)) return;
        try {
            var f = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
            f.setAccessible(true); f.set(s, over);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        int key = KeybindHandler.BACKPACK_OPEN_KEYBIND.getKey().getValue();
        if (!net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPre(s, key, 0, 0)) s.keyPressed(key, 0, 0);
    }

    public static void pressForTest() { net.minecraft.client.KeyMapping.click(KeybindHandler.BACKPACK_OPEN_KEYBIND.getKey()); }

    public static void onTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !(mc.hitResult instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof DrekkiEntity d) || d.bag() == 0 || !mc.player.getUUID().equals(d.owner())) return;
        if (KeybindHandler.BACKPACK_OPEN_KEYBIND.consumeClick()) {
            while (KeybindHandler.BACKPACK_OPEN_KEYBIND.consumeClick()) {}
            PacketDistributor.sendToServer(new OpenBag(d.getId()));
        }
    }
}
