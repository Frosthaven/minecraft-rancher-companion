package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's container screens: the trough. (The hatchery screen has no inventory; it is a packet-driven screen.) */
public final class DrekkiMenus {
    private DrekkiMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RancherCompanion.ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TroughMenu>> TROUGH = MENUS.register("trough",
        () -> IMenuTypeExtension.create((id, inv, buf) -> new TroughMenu(id, inv, buf.readBlockPos())));

    public static void register(IEventBus modBus) { MENUS.register(modBus); }
}
