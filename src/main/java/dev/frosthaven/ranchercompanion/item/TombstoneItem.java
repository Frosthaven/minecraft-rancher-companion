package dev.frosthaven.ranchercompanion.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A mined tombstone (spec, Tombstone): its drekki's name and the date it died, on hover. */
public final class TombstoneItem extends BlockItem {
    public TombstoneItem(Block block, Properties p) { super(block, p); }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.addAll(lines(s.get(DrekkiData.TOMB_NAME.get()), s.get(DrekkiData.TOMB_DATE.get()), s.get(DrekkiData.TOMB_MESSAGE.get())));
    }

    public static List<Component> lines(String name, String date) { return lines(name, date, null); }

    /** the name and date lines (a decorative one's message instead), shared by the item and Jade's hover on a placed tombstone */
    public static List<Component> lines(String name, String date, String message) {
        List<Component> out = new java.util.ArrayList<>();
        if (name != null && !name.isEmpty()) out.add(Component.translatable("screen.rancher_companion.tombstone.title", name).withStyle(ChatFormatting.GRAY));
        if (date != null && !date.isEmpty()) out.add(Component.literal(date).withStyle(ChatFormatting.DARK_GRAY));
        if (message != null && !message.isEmpty()) out.add(Component.literal("\u201C" + message + "\u201D").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return out;
    }
}
