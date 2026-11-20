package dev.frosthaven.ranchercompanion.compat.sophisticated;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiBag;
import dev.frosthaven.ranchercompanion.look.Gear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;

/**
 * The drekki bags as Sophisticated Backpacks items (spec, Accessory: bags), loaded only when that mod is: its storage,
 * its upgrades and its dye recipe (the same column rule as ours) come with them. Held, a bag opens Sophisticated's own
 * screen, where its upgrades are added; on a drekki, the drekki screen opens our bag screen over the same storage and the
 * drekki ticks the upgrades in its place (magnet, pickup). Sophisticated's screens open only for a backpack a player
 * holds, wears or has placed (a closed list of contexts); on a drekki SophBagMenu shows the same storage.
 */
public final class SophBags {
    private SophBags() {}

    public static Item create(int kind, Item.Properties ignored) { return new Bag(kind); }

    public static IItemHandler handler(ItemStack stack) { return BackpackWrapper.fromStack(stack).getInventoryHandler(); }

    public static void open(net.minecraft.server.level.ServerPlayer p, DrekkiEntity d) { SophBagMenu.open(p, d); }

    /** the self test's setup: an upgrade straight into the bag's first upgrade slot */
    public static void installForTest(ItemStack bag, String upgradeId) { installForTest(bag, upgradeId, 0); }

    public static void installForTest(ItemStack bag, String upgradeId, int slot) {
        var w = BackpackWrapper.fromStack(bag);
        w.getInventoryHandler();  /* gives a new bag its storage id: until then it has no upgrade slots */
        w.getUpgradeHandler().setStackInSlot(slot,
            new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(upgradeId))));
    }

    /** the self test's setup: colors, and items into the bag */
    public static void dyeForTest(ItemStack bag, int main, int trim) { BackpackItem.setColors(bag, main, trim); }

    /** both colors off: the bag's own leather again */
    public static void undye(ItemStack bag) {
        bag.remove(net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents.MAIN_COLOR.get());
        bag.remove(net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents.ACCENT_COLOR.get());
    }

    /** placed in the world it is Sophisticated's backpack block holding the bag */
    public static boolean placedHere(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return level.getBlockEntity(pos) instanceof net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
    }

    /** the self test: items into a placed bag */
    public static String insertPlacedForTest(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity be)) return "no backpack block";
        var h = be.getBackpackWrapper().getInventoryHandler();
        ItemStack left = h.insertItem(0, stack, false);
        return "left " + left + ", slot0 " + h.getStackInSlot(0) + ", bag " + be.getBackpackWrapper().getBackpack().getItem();
    }

    /** the self test: its auto-smelting upgrade smelts anything (an empty deny list; its default list allows nothing) */
    public static void openFiltersForTest(ItemStack bag) {
        var h = BackpackWrapper.fromStack(bag).getUpgradeHandler();
        for (var u : h.getWrappersThatImplement(net.p3pp3rf1y.sophisticatedcore.upgrades.cooking.AutoCookingUpgradeWrapper.class)) {
            u.getInputFilterLogic().setAllowList(false); u.getFuelFilterLogic().setAllowList(false); }
        for (var u : h.getWrappersThatImplement(net.p3pp3rf1y.sophisticatedcore.upgrades.IFilteredUpgrade.class)) u.getFilterLogic().setAllowList(false);
    }

    /** the self test: the bag's feeding upgrade has never run its own tick (its tick always starts a cooldown) */
    public static boolean feedingNeverTicked(ItemStack bag) {
        var f = feeding(bag);
        return f != null && f.getCooldownTime() == 0;
    }

    /** every Sophisticated Backpacks upgrade item */
    public static java.util.List<net.minecraft.world.item.Item> allUpgrades() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> {
            var k = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i);
            return k.getNamespace().equals("sophisticatedbackpacks") && k.getPath().contains("upgrade") && !k.getPath().equals("upgrade_base");
        }).toList();
    }

    /** the self test's parity check: every upgrade offered to the bag and to Sophisticated's backpack of the same tier;
     *  the ones they disagree on (none means the bag takes upgrades exactly as a backpack does) */
    public static String upgradeParity(ItemStack bag, ItemStack backpack) {
        var ours = BackpackWrapper.fromStack(bag).getUpgradeHandler();
        var theirs = BackpackWrapper.fromStack(backpack).getUpgradeHandler();
        StringBuilder b = new StringBuilder(); int n = 0;
        for (var item : allUpgrades()) {
            n++;
            ItemStack u = new ItemStack(item);
            boolean a = ours.isItemValid(0, u), c = theirs.isItemValid(0, u);
            if (a != c) b.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath()).append(a ? "(only ours) " : "(only theirs) ");
        }
        return n + " upgrades; disagree: " + (b.length() == 0 ? "none" : b);
    }

    /** its feeding upgrade, enabled, or null: the drekki eats from the bag only with one (spec, Accessory: bags) */
    public static net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper feeding(ItemStack stack) {
        for (var f : BackpackWrapper.fromStack(stack).getUpgradeHandler().getTypeWrappers(net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeItem.TYPE))
            if (f.isEnabled()) return f;
        return null;
    }

    /** a food the bag's feeding upgrade lets it eat (its filter) */
    public static boolean feeds(ItemStack bag, ItemStack food) {
        var f = feeding(bag);
        return f != null && f.getFilterLogic().matchesFilter(food);
    }

    /** the bag's upgrades act where the drekki is, as they do for a placed backpack; all but feeding, which on a
     *  drekki feeds the drekki only (its pack meals), never the players around it */
    public static void tick(ItemStack stack, DrekkiEntity d) {
        BackpackWrapper.fromStack(stack).getUpgradeHandler().getWrappersThatImplement(ITickableUpgrade.class).forEach(u -> {
            if (!(u instanceof net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper)) u.tick(d, d.level(), d.blockPosition());
        });
    }

    /** main and trim colors, -1 when undyed (Sophisticated's default colors) */
    public static int main(ItemStack s) { int c = BackpackItem.getMainColor(s) & 0xFFFFFF; return c == (BackpackItem.DEFAULT_MAIN_COLOR & 0xFFFFFF) ? -1 : c; }
    public static int trim(ItemStack s) { int c = BackpackItem.getAccentColor(s) & 0xFFFFFF; return c == (BackpackItem.DEFAULT_ACCENT_COLOR & 0xFFFFFF) ? -1 : c; }

    /** the placed block for each bag (Gear.BAGS index): sneak + right-click places it, as a backpack; it is Sophisticated's
     *  backpack block drawn as the bag (SophBlocks), so Sophisticated's Create integration carries it on contraptions */
    private static java.util.function.Supplier<net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock> block(int kind) {
        return () -> SophBlocks.PLACED.get(kind).get();  /* Sophisticated's backpack block, drawn as this bag */
    }

    /** a drekki bag as a backpack: placeable as Sophisticated's backpack block, never worn by a player */
    static final class Bag extends BackpackItem implements DrekkiBag {
        private final int kind;

        Bag(int kind) {
            super(() -> Gear.BAG_SLOTS[kind], () -> Gear.BAG_UPGRADES[kind], block(kind), p -> kind == 6 ? p.fireResistant() : p);
            this.kind = kind;
        }

        @Override public int kind() { return kind; }
        @Override public EquipmentSlot getEquipmentSlot(ItemStack stack) { return null; }
        /* bags in bags and backpacks: Sophisticated's own rule (only with an Inception upgrade), its storage applies it */
    }
}
