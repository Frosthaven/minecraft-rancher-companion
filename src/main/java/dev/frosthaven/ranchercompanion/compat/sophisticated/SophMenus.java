package dev.frosthaven.ranchercompanion.compat.sophisticated;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Sophisticated bag screen's menu type, registered only when Sophisticated Backpacks is loaded. */
public final class SophMenus {
    private SophMenus() {}

    static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RancherCompanion.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<SophBagMenu>> BAG = MENUS.register("sophisticated_bag",
        () -> IMenuTypeExtension.create(SophBagMenu::client));

    public static void register(IEventBus bus) { MENUS.register(bus); }
}
