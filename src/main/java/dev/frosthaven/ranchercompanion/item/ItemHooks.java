package dev.frosthaven.ranchercompanion.item;

import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/** Vanilla hooks for the items: a water cauldron washes dye off barding, and the wandering trader sells hats and the egg. */
public final class ItemHooks {
    private ItemHooks() {}

    static final int HAT_PRICE = 12, EGG_PRICE = 6;

    /** Same as leather armor: a water cauldron removes the dyed color and uses one level of water. */
    static void registerCauldronWash() {
        DrekkiItems.BARDING.values().forEach(item -> CauldronInteraction.WATER.map().put(item.get(), CauldronInteraction.DYED_ITEM));
        CauldronInteraction.WATER.map().put(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.HATCHERY.get().asItem(), LeatherWash.INSTANCE);
        CauldronInteraction.WATER.map().put(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.TROUGH.get().asItem(), LeatherWash.INSTANCE);
    }

    /**
     * One generic offer that picks one of the six trader hats at random, one use, for emeralds. It joins the generic
     * pool, so a trader carries it only when the pool draws it (the event cannot force an offer onto every trader).
     */
    static void addTraderHats(WandererTradesEvent event) {
        event.getGenericTrades().add((trader, random) -> {
            var hat = DrekkiItems.TRADER_HATS.get(random.nextInt(DrekkiItems.TRADER_HATS.size()));
            return new MerchantOffer(new ItemCost(Items.EMERALD, HAT_PRICE), new ItemStack(hat.get()), 1, 1, 0.05f);
        });
    }

    /**
     * Every wandering trader carries exactly one Intriguing Egg for 6 chicken eggs (spec, Egg). WandererTradesEvent only adds
     * to the random pools, so the offer is appended when a player opens the trader, on the server, if its list does not hold
     * one yet; one use, so each trader sells one egg. Works with the trade_rebalance feature flag too.
     */
    static void addTraderEgg(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide && event.getTarget() instanceof net.minecraft.world.entity.npc.WanderingTrader trader) ensureEgg(trader);
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
