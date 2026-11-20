package dev.frosthaven.ranchercompanion.compat.sophisticated.client;

import dev.frosthaven.ranchercompanion.compat.sophisticated.SophBagMenu;
import dev.frosthaven.ranchercompanion.compat.sophisticated.SophMenus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;

/** Sophisticated's storage screen for the bag a drekki wears: everything drawn by Core's StorageScreenBase. */
public final class SophBagScreen extends StorageScreenBase<SophBagMenu> {
    SophBagScreen(SophBagMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Override
    protected String getStorageSettingsTabTooltip() { return "gui.sophisticatedbackpacks.settings.tooltip"; }

    public static void register(RegisterMenuScreensEvent e) { e.register(SophMenus.BAG.get(), SophBagScreen::new); }
}
