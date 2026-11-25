package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every item the spec defines, the "Drekki Rancher" creative tab, and their hooks (dye wash, trader hats, the trader's egg). */
public final class DrekkiItems {
    private DrekkiItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RancherCompanion.ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RancherCompanion.ID);

    /** Every item in display order: barding by tier, carriers, crafted hats, trader hats, tonics, egg, then the blocks. */
    public static final List<DeferredItem<? extends Item>> ALL = new ArrayList<>();

    /** one barding per tier for Fledglings and Matures (Shane, 2026-11-25; split by stage from 0.4.0 to 0.5.x) */
    public static final Map<Barding, DeferredItem<BardingItem>> BARDING = new java.util.EnumMap<>(Barding.class);
    static {
        for (Barding tier : Barding.values()) {
            Item.Properties props = new Item.Properties();
            if (tier == Barding.NETHERITE) props.fireResistant();
            BARDING.put(tier, add(ITEMS.registerItem(tier.id(), p -> new BardingItem(tier, p), props)));
        }
    }

    /** The Fledgling barding ids of 0.4.0 to 0.5.x, kept registered for good: the same barding (both stages, same name),
     *  out of the creative tab, without a recipe and hidden from recipe viewers (#c:hidden_from_recipe_viewers). Saved
     *  stacks load as these and work as they are; they turn into the one barding when touched (BardingItem.legacy). Not
     *  registry aliases: a game whose server still has these ids is refused with "unknown keys" when its own registry
     *  lacks them (traintest rig, 2026-11-25), and a pack update reaches players minutes before the server's restart. */
    public static final Map<Barding, DeferredItem<BardingItem>> LEGACY_FLEDGLING_BARDING = new java.util.EnumMap<>(Barding.class);
    static {
        for (Barding tier : Barding.values()) {
            Item.Properties props = new Item.Properties();
            if (tier == Barding.NETHERITE) props.fireResistant();
            LEGACY_FLEDGLING_BARDING.put(tier, ITEMS.registerItem(tier.fledglingId(), p -> new BardingItem(tier, p), props));
        }
    }

    /** every barding item, the legacy ids included (tints, washing) */
    public static java.util.stream.Stream<DeferredItem<BardingItem>> allBarding() {
        return java.util.stream.Stream.concat(BARDING.values().stream(), LEGACY_FLEDGLING_BARDING.values().stream());
    }

    /** the bags by Gear.BAGS index (1..6): the satchel, then the saddlebag's tiers (spec, Accessory: bags) */
    public static final List<DeferredItem<? extends Item>> BAGS = java.util.stream.IntStream.range(1, dev.frosthaven.ranchercompanion.look.Gear.BAGS.length)
        .mapToObj(k -> add(ITEMS.registerItem(dev.frosthaven.ranchercompanion.look.Gear.BAGS[k], p -> dev.frosthaven.ranchercompanion.item.Bags.create(k, p),
            k == 6 ? new Item.Properties().fireResistant() : new Item.Properties()))).collect(java.util.stream.Collectors.toList());

    public static final DeferredItem<CarrierItem> SMALL_CARRIER = add(ITEMS.registerItem("small_drekki_carrier", p -> new CarrierItem(p, 1), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CarrierItem> LARGE_CARRIER = add(ITEMS.registerItem("large_drekki_carrier", p -> new CarrierItem(p, 2), new Item.Properties().stacksTo(1)));

    public static final DeferredItem<WhistleItem> WHISTLE = add(ITEMS.registerItem("drekki_whistle", WhistleItem::new, new Item.Properties().stacksTo(1)));

    public static final List<DeferredItem<Item>> CRAFTED_HATS = List.of("straw_hat", "cowboy_hat", "chef_toque", "flower_crown",
        "miner_helmet", "viking_helmet", "party_cone", "bucket_hat", "drinking_hat").stream().map(DrekkiItems::single).toList();
    /** Sold only by the wandering trader, one at random per offer. */
    public static final List<DeferredItem<Item>> TRADER_HATS = List.of("crown", "top_hat", "wizard_hat", "pirate_tricorne",
        "jester_cap", "propeller_beanie", "conductor_cap", "stock_keeper_cap", "aviator_cap", "satellite_hat").stream().map(DrekkiItems::single).toList();

    public static final List<DeferredItem<Item>> TONICS = List.of("bloodline_tonic", "heirloom_tonic", "clean_slate_tonic",
        "starlight_tonic", "purity_tonic", "rarity_tonic").stream()
        .map(id -> add(ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(16)))).toList();

    public static final DeferredItem<IntriguingEggItem> INTRIGUING_EGG =
        add(ITEMS.registerItem("intriguing_egg", IntriguingEggItem::new, new Item.Properties().stacksTo(1)));
    /** testing only (creative tab, JEI): each turns into the egg at the start of that incubation day once in an inventory */
    public static final List<DeferredItem<CreativeEggItem>> CREATIVE_EGGS = java.util.stream.IntStream.rangeClosed(1, 4)
        .mapToObj(n -> add(ITEMS.registerItem("creative_egg_day_" + n, p -> new CreativeEggItem(p, n), new Item.Properties().stacksTo(1)))).toList();

    /** one item per block, in the blocks' tab order (hatchery, trough, then the decor set) */
    public static final List<DeferredItem<net.minecraft.world.item.BlockItem>> BLOCK_ITEMS =
        dev.frosthaven.ranchercompanion.block.DrekkiBlocks.ITEM_ORDER.stream().map(b -> add(ITEMS.registerSimpleBlockItem(b))).toList();

    /** a drekki's tombstone: only a death makes one, so it is not in the tab */
    public static final DeferredItem<TombstoneItem> TOMBSTONE = ITEMS.registerItem("tombstone",
        p -> new TombstoneItem(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.TOMBSTONE.get(), p), new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("drekki_rancher", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup." + RancherCompanion.ID))
        .icon(() -> INTRIGUING_EGG.get().getDefaultInstance())
        .displayItems((params, out) -> ALL.forEach(item -> out.accept(item.get())))
        .build());

    /** the hat items by Gear.HATS index (index 0 is no hat) */
    private static final String[] HAT_IDS = {"", "top_hat", "drinking_hat", "viking_helmet", "wizard_hat", "straw_hat", "crown", "party_cone",
        "bucket_hat", "cowboy_hat", "chef_toque", "miner_helmet", "pirate_tricorne", "propeller_beanie", "flower_crown", "jester_cap",
        "conductor_cap", "stock_keeper_cap", "aviator_cap", "satellite_hat"};

    /** the Gear.HATS index of a hat item, 0 for anything else */
    public static int hatIndex(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!key.getNamespace().equals(RancherCompanion.ID)) return 0;
        for (int i = 1; i < HAT_IDS.length; i++) if (HAT_IDS[i].equals(key.getPath())) return i;
        return 0;
    }

    public static net.minecraft.world.item.ItemStack hatStack(int index) {
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, HAT_IDS[index])));
    }

    /** every hat, crafted and trader: the trader sells them all now (spec, Pup release) */
    public static List<DeferredItem<Item>> allHats() {
        List<DeferredItem<Item>> all = new ArrayList<>(CRAFTED_HATS);
        all.addAll(TRADER_HATS);
        return all;
    }

    private static DeferredItem<Item> single(String id) {
        return add(ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(1)));
    }

    private static <T extends DeferredItem<? extends Item>> T add(T item) {
        ALL.add(item);
        return item;
    }

    /** Registers the items, the tab, the cauldron wash and the trader hat offer. */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        DrekkiData.COMPONENTS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(ItemHooks::registerCauldronWash));
        /* hats come only from the wandering trader (ItemHooks.ensureHats); the tonics and
           the hats have no recipe (their recipe files carry neoforge:false) */
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, ItemHooks::addTraderEgg);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent.class, CarrierItem::onCrafted);
    }
}
