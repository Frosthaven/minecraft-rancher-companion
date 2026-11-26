package dev.frosthaven.ranchercompanion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A breeding tonic (spec, Breeding: tonics): it goes in the hatchery's tonic slot and acts on the next pairing. Its
 *  tooltip says what it does, for the tonics that have an effect so far. */
public final class TonicItem extends Item {
    public TonicItem(Properties props) { super(props); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String key = getDescriptionId() + ".desc";
        if (Language.getInstance().has(key)) lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
