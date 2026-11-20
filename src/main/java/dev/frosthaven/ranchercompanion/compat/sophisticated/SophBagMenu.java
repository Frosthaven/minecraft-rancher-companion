package dev.frosthaven.ranchercompanion.compat.sophisticated;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;
import net.p3pp3rf1y.sophisticatedcore.util.NoopStorageWrapper;

/**
 * The bag a drekki wears, in Sophisticated's own storage screen (spec, Accessory: bags): its slots, its upgrade slots and
 * the upgrades' tabs, sorting, all Sophisticated's. Built on Core's StorageContainerMenuBase, the base its storage-on-an-
 * entity screens use; this class only says where the bag lives (the drekki). The client gets the bag stack in the
 * opening packet and makes its own wrapper from it, as for a backpack held in hand.
 */
public final class SophBagMenu extends StorageContainerMenuBase<IBackpackWrapper> {
    private final DrekkiEntity drekki;

    SophBagMenu(int id, Player player, DrekkiEntity d, IBackpackWrapper wrapper) {
        super(SophMenus.BAG.get(), id, player, wrapper, NoopStorageWrapper.INSTANCE, -1, false);
        this.drekki = d;
    }

    static SophBagMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        Entity e = inv.player.level().getEntity(buf.readVarInt());
        ItemStack bag = ItemStack.STREAM_CODEC.decode(buf);
        return new SophBagMenu(id, inv.player, e instanceof DrekkiEntity d ? d : null, BackpackWrapper.fromStack(bag));
    }

    static void open(ServerPlayer p, DrekkiEntity d) {
        ItemStack bag = d.accessory();
        Component title = bag.getHoverName();
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new SophBagMenu(id, pl, d, BackpackWrapper.fromStack(bag)), title),
            buf -> { buf.writeVarInt(d.getId()); ItemStack.STREAM_CODEC.encode(buf, bag); });
    }

    @Override public Optional<BlockPos> getBlockPosition() { return Optional.empty(); }
    @Override public Optional<Entity> getEntity() { return Optional.ofNullable(drekki); }

    @Override
    protected StorageUpgradeSlot instantiateUpgradeSlot(UpgradeHandler handler, int slot) {
        return new StorageUpgradeSlot(handler, slot) {
            @Override protected void onUpgradeChanged() {}
        };
    }

    /** Sophisticated's settings screen (sort order, item display) opens for a held, worn or placed backpack only; hold
     *  the bag and use it for those */
    @Override public void openSettings() {}
    @Override protected boolean storageItemHasChanged() { return false; }
    @Override public boolean detectSettingsChangeAndReload() { return false; }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (drekki != null && !drekki.level().isClientSide) Ranch.touch(drekki);
    }

    @Override
    public boolean stillValid(Player player) {
        return drekki != null && drekki.isAlive() && !drekki.isRemoved() && drekki.bagStorage() != null
            && player.distanceToSqr(drekki) <= 144 && player.getUUID().equals(drekki.owner());
    }
}
