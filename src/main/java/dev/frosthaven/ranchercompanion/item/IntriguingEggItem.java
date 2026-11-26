package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.look.Incubation;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Intriguing Egg. A new egg shows its lore; an egg that has been in a hatchery shows how far it is instead
 * (spec, Pup release: warm, noises, active, ready), also on hover in the hatchery screen.
 */
public final class IntriguingEggItem extends Item {
    public IntriguingEggItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        net.minecraft.nbt.CompoundTag bred = stack.get(DrekkiData.BRED.get());
        if (bred != null) lines.add(Component.translatable(getDescriptionId() + ".bred", bred.getString("A"), bred.getString("B")).withStyle(ChatFormatting.GOLD));
        Long t = stack.get(DrekkiData.INCUBATION.get());
        if (t == null) {
            if (bred != null) return;
            lines.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        int s = Incubation.stage(t);
        lines.add(Component.translatable(getDescriptionId() + "." + Incubation.KEYS[s]).withStyle(s == 3 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }
}
