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
import net.neoforged.neoforge.event.village.WandererTradesEvent;
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

    public static final Map<Barding, DeferredItem<Item>> BARDING = new java.util.EnumMap<>(Barding.class);
    static {
        for (Barding tier : Barding.values()) {
            Item.Properties props = new Item.Properties().stacksTo(1);
            if (tier == Barding.NETHERITE) props.fireResistant();
            BARDING.put(tier, add(ITEMS.registerSimpleItem(tier.id(), props)));
        }
    }

    public static final DeferredItem<Item> SMALL_CARRIER = single("small_drekki_carrier");
    public static final DeferredItem<Item> LARGE_CARRIER = single("large_drekki_carrier");

    public static final List<DeferredItem<Item>> CRAFTED_HATS = List.of("straw_hat", "cowboy_hat", "chef_toque", "flower_crown",
        "miner_helmet", "viking_helmet", "party_cone", "bucket_hat", "drinking_hat").stream().map(DrekkiItems::single).toList();
    /** Sold only by the wandering trader, one at random per offer. */
    public static final List<DeferredItem<Item>> TRADER_HATS = List.of("crown", "top_hat", "wizard_hat", "pirate_tricorne",
        "jester_cap", "propeller_beanie").stream().map(DrekkiItems::single).toList();

    public static final List<DeferredItem<Item>> TONICS = List.of("bloodline_tonic", "heirloom_tonic", "clean_slate_tonic",
        "starlight_tonic", "purity_tonic", "rarity_tonic").stream()
        .map(id -> add(ITEMS.registerSimpleItem(id, new Item.Properties().stacksTo(16)))).toList();

    public static final DeferredItem<IntriguingEggItem> INTRIGUING_EGG =
        add(ITEMS.registerItem("intriguing_egg", IntriguingEggItem::new, new Item.Properties().stacksTo(1)));

    /** one item per block, in the blocks' tab order (hatchery, trough, then the decor set) */
    public static final List<DeferredItem<net.minecraft.world.item.BlockItem>> BLOCK_ITEMS =
        dev.frosthaven.ranchercompanion.block.DrekkiBlocks.ITEM_ORDER.stream().map(b -> add(ITEMS.registerSimpleBlockItem(b))).toList();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("drekki_rancher", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup." + RancherCompanion.ID))
        .icon(() -> INTRIGUING_EGG.get().getDefaultInstance())
        .displayItems((params, out) -> ALL.forEach(item -> out.accept(item.get())))
        .build());

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
        TABS.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(ItemHooks::registerCauldronWash));
        /* teaser release: nothing makes the hats, barding, carriers or tonics yet (their recipes carry neoforge:false) */
        // NeoForge.EVENT_BUS.addListener(WandererTradesEvent.class, ItemHooks::addTraderHats);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, ItemHooks::addTraderEgg);
    }
}
