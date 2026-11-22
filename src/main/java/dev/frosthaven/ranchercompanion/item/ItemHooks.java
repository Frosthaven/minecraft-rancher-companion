package dev.frosthaven.ranchercompanion.item;

import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Vanilla hooks for the items: a water cauldron washes dye off barding, bags, hatcheries and troughs, and the wandering trader sells hats and the egg (both added when a player opens it). */
public final class ItemHooks {
    private ItemHooks() {}

    static final int HAT_PRICE = 12, EGG_PRICE = 6;

    static void registerCauldronWash() {
        /* one named action for every dyeable item, so the Haven pack's bleach recognizes them all (LeatherWash) */
        DrekkiItems.allBarding().forEach(item -> CauldronInteraction.WATER.map().put(item.get(), LeatherWash.INSTANCE));
        DrekkiItems.BAGS.forEach(item -> CauldronInteraction.WATER.map().put(item.get(), LeatherWash.INSTANCE));
        CauldronInteraction.WATER.map().put(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.HATCHERY.get().asItem(), LeatherWash.INSTANCE);
        CauldronInteraction.WATER.map().put(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.TROUGH.get().asItem(), LeatherWash.INSTANCE);
    }

    /** hats per visit, and how many of each the trader has */
    static final int HAT_OFFERS = 2, HAT_STOCK = 3;

    /**
     * Every wandering trader sells two different hats, picked at random from all of them, three of each (spec, Pup
     * release). Added with the egg when a player first opens the trader; a trader that already has hat offers keeps them.
     */
    public static void ensureHats(net.minecraft.world.entity.npc.WanderingTrader trader) {
        var offers = trader.getOffers();
        for (MerchantOffer o : offers) if (DrekkiItems.hatIndex(o.getResult()) > 0) return;
        var hats = new java.util.ArrayList<>(DrekkiItems.allHats());
        /* the conductor's and stock keeper's caps are Create's models: offered only with Create */
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) hats.removeIf(h -> DrekkiItems.hatIndex(new ItemStack(h.get())) >= 16 && DrekkiItems.hatIndex(new ItemStack(h.get())) <= 17);
        java.util.Collections.shuffle(hats, new java.util.Random(trader.getRandom().nextLong()));
        for (int i = 0; i < HAT_OFFERS && i < hats.size(); i++)
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, HAT_PRICE), new ItemStack(hats.get(i).get()), HAT_STOCK, 1, 0.05f));
    }

    /**
     * Every wandering trader carries exactly one Intriguing Egg for 6 chicken eggs (spec, Egg). WandererTradesEvent only adds
     * to the random pools, so the offer is appended when a player opens the trader, on the server, if its list does not hold
     * one yet; one use, so each trader sells one egg. Works with the trade_rebalance feature flag too.
     */
    static void addTraderEgg(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide && event.getTarget() instanceof net.minecraft.world.entity.npc.WanderingTrader trader) { ensureEgg(trader); ensureHats(trader); }
    }

    /**
     * One egg per trader per visit: the offer has one use, and it is added only when the trader holds no egg offer at all,
     * a sold-out one included (wandering traders never restock), so a trader never sells a second. It goes first in the list.
     */
    public static void ensureEgg(net.minecraft.world.entity.npc.WanderingTrader trader) {
        var offers = trader.getOffers();
        for (MerchantOffer o : offers) if (o.getResult().is(DrekkiItems.INTRIGUING_EGG.get())) return;
        offers.add(0, new MerchantOffer(new ItemCost(Items.EGG, EGG_PRICE), new ItemStack(DrekkiItems.INTRIGUING_EGG.get()), 1, 1, 0.05f));
    }
}
