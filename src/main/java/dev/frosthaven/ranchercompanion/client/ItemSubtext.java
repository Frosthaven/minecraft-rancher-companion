package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** The grey kind line under a drekki item's name: "Drekki Hat", "Drekki Barding", "Drekki Carrier", "Drekki Tonic", "Drekki Egg". */
public final class ItemSubtext {
    private ItemSubtext() {}

    public static void onTooltip(ItemTooltipEvent e) {
        if (wornBag(e)) e.getToolTip().add(1, Component.translatable("screen.rancher_companion.drekki.bag").withStyle(ChatFormatting.YELLOW));
        /* the Potion of Horizons says what it is for, in place of the game's "No Effects" */
        if (dev.frosthaven.ranchercompanion.item.DrekkiPotions.step(e.getItemStack()) == 3 && !e.getToolTip().isEmpty())
            e.getToolTip().add(1, Component.translatable("tooltip.rancher_companion.horizons").withStyle(ChatFormatting.GREEN));
        String kind = kind(e.getItemStack());
        if (kind == null || e.getToolTip().isEmpty()) return;
        e.getToolTip().add(1, Component.translatable("tooltip.rancher_companion.kind." + kind).withStyle(ChatFormatting.GRAY));
        if (dev.frosthaven.ranchercompanion.item.CarrierItem.notYours(e.getItemStack(), e.getEntity()))
            e.getToolTip().add(Component.translatable("item.rancher_companion.small_drekki_carrier.not_yours",
                e.getItemStack().getOrDefault(dev.frosthaven.ranchercompanion.item.DrekkiData.CARRIED_OWNER_NAME.get(), "?")).withStyle(ChatFormatting.RED));
    }

    /** on the bag a drekki wears, in its screen's accessory slot: how to open it */
    private static boolean wornBag(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.screen instanceof dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen ds && ds.getSlotUnderMouse() != null
            && ds.getSlotUnderMouse().index == 2 && ds.getMenu().gear && ds.getSlotUnderMouse().getItem() == e.getItemStack();
    }

    private static String kind(ItemStack s) {
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (!key.getNamespace().equals(RancherCompanion.ID)) return null;
        String p = key.getPath();
        if (DrekkiItems.hatIndex(s) > 0) return "hat";
        if (p.endsWith("_barding")) return "barding";
        if (p.endsWith("_carrier")) return "carrier";
        if (s.getItem() instanceof dev.frosthaven.ranchercompanion.item.DrekkiBag) return "accessory";
        if (p.endsWith("_tonic")) return "tonic";
        if (p.equals("intriguing_egg") || p.startsWith("creative_egg")) return "egg";
        return null;
    }
}
