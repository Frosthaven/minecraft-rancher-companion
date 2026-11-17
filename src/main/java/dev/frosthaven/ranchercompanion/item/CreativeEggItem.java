package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.look.Incubation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A test egg (spec, Pup release): in the creative tab and JEI, no recipe. Once in a player's inventory it turns into
 * the Intriguing Egg at the start of incubation day n, so it behaves as a real egg from then on.
 */
public final class CreativeEggItem extends Item {
    public final int day;

    public CreativeEggItem(Properties props, int day) {
        super(props);
        this.day = day;
    }

    public static ItemStack egg(int day) {
        ItemStack egg = new ItemStack(DrekkiItems.INTRIGUING_EGG.get());
        egg.set(DrekkiData.INCUBATION.get(), Incubation.creativeDay(day));
        return egg;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!level.isClientSide && holder instanceof Player p && p.getInventory().getItem(slot) == stack)
            p.getInventory().setItem(slot, egg(day));
    }
}
