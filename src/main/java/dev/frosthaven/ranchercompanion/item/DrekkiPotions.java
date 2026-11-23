package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Potion of Horizons (spec, Traits: caps): fed to a drekki by hand, once, it unlocks one more trait slot for good.
 * Brewed: Awkward Potion + ender pearl = Potion of Distance, + popped chorus fruit = Potion of Twilight, + emerald =
 * Potion of Horizons. All three are potion types of the game's own potion items, so every brewing method that reads the
 * game's brewing recipes makes them. They have no effects: drunk, thrown or on an arrow they do nothing.
 */
public final class DrekkiPotions {
    private DrekkiPotions() {}

    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, RancherCompanion.ID);
    /* the name is the lang key's last part: item.minecraft.potion.effect.drekki_distance and so on */
    public static final DeferredHolder<Potion, Potion> DISTANCE = POTIONS.register("distance", () -> new Potion("drekki_distance"));
    public static final DeferredHolder<Potion, Potion> TWILIGHT = POTIONS.register("twilight", () -> new Potion("drekki_twilight"));
    public static final DeferredHolder<Potion, Potion> HORIZONS = POTIONS.register("horizons", () -> new Potion("drekki_horizons"));

    public static void register(IEventBus modBus) { POTIONS.register(modBus); }

    public static void brewing(RegisterBrewingRecipesEvent e) {
        var b = e.getBuilder();
        b.addMix(Potions.AWKWARD, Items.ENDER_PEARL, DISTANCE);
        b.addMix(DISTANCE, Items.POPPED_CHORUS_FRUIT, TWILIGHT);
        b.addMix(TWILIGHT, Items.EMERALD, HORIZONS);
    }

    /** which of the three a potion item holds: 1 Distance, 2 Twilight, 3 Horizons; 0 for any other stack */
    public static int step(ItemStack stack) {
        Holder<Potion> p = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion().orElse(null);
        return p == null ? 0 : p.is(DISTANCE.getKey()) ? 1 : p.is(TWILIGHT.getKey()) ? 2 : p.is(HORIZONS.getKey()) ? 3 : 0;
    }

    /** a bottle of the Potion of Horizons to drink (not the splash or lingering one): what a drekki is fed */
    public static boolean horizons(ItemStack stack) { return stack.is(Items.POTION) && step(stack) == 3; }

    public static ItemStack stack(Holder<Potion> potion) { return PotionContents.createItemStack(Items.POTION, potion); }

    /** its owner feeds it the potion: once per drekki, the bottle comes back empty. False: it has had one. */
    public static boolean feed(DrekkiEntity d, Player owner, ItemStack stack) {
        if (d.life.horizons) {
            owner.displayClientMessage(Component.translatable("message.rancher_companion.horizons_had", dev.frosthaven.ranchercompanion.life.Vitals.who(d)), true);
            return false;
        }
        d.life.horizons = true;
        d.life.oldCaps = false;
        stack.consume(1, owner);
        if (!owner.hasInfiniteMaterials()) owner.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE));
        d.playSound(SoundEvents.GENERIC_DRINK, 0.8f, 1.1f);
        d.act(dev.frosthaven.ranchercompanion.look.Anim.FEED, 30);
        dev.frosthaven.ranchercompanion.life.Vitals.burst(d, 0x17C95C);
        d.syncLife();
        Ranch.touch(d);
        owner.displayClientMessage(Component.translatable("message.rancher_companion.horizons_fed", dev.frosthaven.ranchercompanion.life.Vitals.who(d)), true);
        return true;
    }
}
