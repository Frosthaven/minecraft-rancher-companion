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
        String kind = kind(e.getItemStack());
        if (kind == null || e.getToolTip().isEmpty()) return;
        e.getToolTip().add(1, Component.translatable("tooltip.rancher_companion.kind." + kind).withStyle(ChatFormatting.GRAY));
    }

    private static String kind(ItemStack s) {
        var key = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (!key.getNamespace().equals(RancherCompanion.ID)) return null;
        String p = key.getPath();
        if (DrekkiItems.hatIndex(s) > 0) return "hat";
        if (p.endsWith("_barding")) return "barding";
        if (p.endsWith("_carrier")) return "carrier";
        if (p.endsWith("_tonic")) return "tonic";
        if (p.equals("intriguing_egg") || p.startsWith("creative_egg")) return "egg";
        return null;
    }
}
