package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's container screens: the trough and the drekki. (The hatchery screen has no inventory; it is a packet-driven screen.) */
public final class DrekkiMenus {
    private DrekkiMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, RancherCompanion.ID);

    public static final DeferredHolder<MenuType<?>, MenuType<TroughMenu>> TROUGH = MENUS.register("trough",
        () -> IMenuTypeExtension.create((id, inv, buf) -> new TroughMenu(id, inv, buf.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<dev.frosthaven.ranchercompanion.entity.DrekkiMenu>> DREKKI = MENUS.register("drekki",
        () -> IMenuTypeExtension.create((id, inv, buf) -> new dev.frosthaven.ranchercompanion.entity.DrekkiMenu(id, inv,
            inv.player.level().getEntity(buf.readVarInt()) instanceof dev.frosthaven.ranchercompanion.entity.DrekkiEntity d ? d : null,
            buf.isReadable() && buf.readBoolean())));

    /** the bag a drekki wears (spec, Accessory: bags) */
    public static final DeferredHolder<MenuType<?>, MenuType<dev.frosthaven.ranchercompanion.entity.BagMenu>> BAG = MENUS.register("drekki_bag",
        () -> IMenuTypeExtension.create(dev.frosthaven.ranchercompanion.entity.BagMenu::client));

    /** a drekki's tombstone (spec, Tombstone) */
    public static final DeferredHolder<MenuType<?>, MenuType<TombstoneMenu>> TOMBSTONE = MENUS.register("tombstone",
        () -> IMenuTypeExtension.create((id, inv, buf) -> new TombstoneMenu(id, inv, buf.readBlockPos())));

    public static void register(IEventBus modBus) { MENUS.register(modBus); }
}
