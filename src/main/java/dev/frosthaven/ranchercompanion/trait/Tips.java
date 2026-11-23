package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.look.Astral;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * The tooltip lines for a trait and an astral affinity, the same on the drekki screen and in chat: a title in its
 * color, the effect, then a gold cooldown line in real-world time and a red "ready in" line while it recharges. In chat
 * the lines travel as one hover text marked {@link #MARK}; the client draws it as the screen does (title, then the
 * rest wrapped to at most 4 rows), and a client without that draws the plain lines.
 */
public final class Tips {
    private Tips() {}

    /** the insertion that marks a hover text as this mod's tooltip lines */
    public static final String MARK = "rancher_companion:tip";

    /** a trait's lines; readyIn is the ticks left on its cooldown (0 when ready) */
    public static List<Component> trait(String id, long readyIn, boolean ghost) {
        Trait t = Trait.byId(id);
        List<Component> l = new ArrayList<>();
        /* the name in its tier's color, then the tier (spec, Traits: rarity), as the game names an item's rarity */
        Style tier = Style.EMPTY.withColor(Trait.color(id));
        l.add(Component.translatable("trait.rancher_companion." + id).withStyle(tier));
        if (t != null) l.add(Component.translatable("rarity.rancher_companion." + t.rarity.name().toLowerCase(java.util.Locale.ROOT)).withStyle(tier));
        l.add(Component.translatable("trait.rancher_companion." + id + ".desc").withStyle(ChatFormatting.GRAY));
        if (t != null && t.cooldown > 0) l.add(Component.translatable("trait.rancher_companion.cooldown", time(t.cooldown)).withStyle(ChatFormatting.GOLD));
        if (readyIn > 0) l.add(Component.translatable("trait.rancher_companion.ready_in", time(readyIn)).withStyle(ChatFormatting.RED));
        if (t != null && t.riding) l.add(Component.translatable("trait.rancher_companion.riding").withStyle(ChatFormatting.DARK_GRAY));
        if (ghost) l.add(Component.translatable("screen.rancher_companion.drekki.ghost_traits").withStyle(ChatFormatting.AQUA));
        return l;
    }

    /** an astral affinity's lines: its name in its eye color, then its home terrain */
    public static List<Component> astral(int a) {
        return List.of(Component.literal(Astral.NAMES[a]).withStyle(Style.EMPTY.withColor(Astral.iris(a))),
            Component.translatable("astral.rancher_companion." + Astral.NAMES[a].toLowerCase() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** the lines as one hover text for chat */
    public static Component hover(List<Component> lines) {
        MutableComponent c = Component.empty().withStyle(Style.EMPTY.withInsertion(MARK));
        for (int i = 0; i < lines.size(); i++) { if (i > 0) c.append("\n"); c.append(lines.get(i)); }
        return c;
    }

    /** the lines back out of a hover text made by {@link #hover}, or null for any other hover text */
    public static List<Component> lines(Component hover) {
        if (!MARK.equals(hover.getStyle().getInsertion())) return null;
        List<Component> l = new ArrayList<>();
        for (Component c : hover.getSiblings()) if (!"\n".equals(c.getString())) l.add(c);
        return l;
    }

    /** ticks as real-world time, short: 15s, 2m, 1m 12s, 2h 20m */
    public static String time(long ticks) {
        long s = (ticks + 19) / 20, h = s / 3600, m = s % 3600 / 60, sec = s % 60;
        if (h > 0) return m > 0 ? h + "h " + m + "m" : h + "h";
        if (m > 0) return sec > 0 ? m + "m " + sec + "s" : m + "m";
        return sec + "s";
    }
}
