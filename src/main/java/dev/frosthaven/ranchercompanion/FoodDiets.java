package dev.frosthaven.ranchercompanion;

import dev.frosthaven.ranchercompanion.look.Diet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which diet a food is. The server config's lists, resolved once into one map (rebuilt when the config or the tags
 * reload): 'never' first (never food), then raw, cooked, seed, root, each top to bottom, a tag expanded in place; the first
 * diet an item is read into is its only one, and a later list naming it again is skipped with a warning. Last, the crop
 * rule on the convention tags: a c:crops/<name> item is Seed when c:seeds/<name> has items, else Root. The client, which has no server config,
 * uses the spec's default lists to predict an interaction; the server decides.
 */
public final class FoodDiets {
    private FoodDiets() {}

    private static volatile Map<Item, Diet> server, defaults;

    public static void invalidate() {
        server = null;
    }

    /** the diet of a food by the server's lists, or null when no drekki eats it */
    public static Diet of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Map<Item, Diet> m = server;
        if (m == null) server = m = build(DrekkiConfig.NEVER.get(), List.of(DrekkiConfig.RAW.get(), DrekkiConfig.COOKED.get(), DrekkiConfig.SEED.get(), DrekkiConfig.ROOT.get()),
            DrekkiConfig.CROPS_BY_SEEDS.get(), true);
        return m.get(stack.getItem());
    }

    /** the diet by the spec's default lists, for the client's prediction */
    public static Diet byDefault(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Map<Item, Diet> m = defaults;
        if (m == null) defaults = m = build(DrekkiConfig.NEVER_DEFAULT, List.of(DrekkiConfig.RAW_DEFAULT, DrekkiConfig.COOKED_DEFAULT, DrekkiConfig.SEED_DEFAULT, DrekkiConfig.ROOT_DEFAULT), true, false);
        return m.get(stack.getItem());
    }

    private static Map<Item, Diet> build(List<? extends String> never, List<? extends List<? extends String>> lists, boolean cropsBySeeds, boolean log) {
        Map<Item, Diet> m = new HashMap<>();
        java.util.Set<Item> no = new java.util.HashSet<>();
        for (String entry : never) items(entry, "never", log).forEach(no::add);
        Diet[] order = {Diet.RAW, Diet.COOKED, Diet.SEED, Diet.ROOT};
        for (int i = 0; i < order.length; i++) {
            Diet d = order[i];
            for (String entry : lists.get(i)) for (Item it : items(entry, d.label.toLowerCase(), log)) if (!no.contains(it)) put(m, it, d, log);
        }
        if (cropsBySeeds) {  /* the convention tags: a crop with a matching seed tag grows from a seed (Seed), else it is planted itself (Root) */
            BuiltInRegistries.ITEM.getTagNames().filter(t -> t.location().getNamespace().equals("c") && t.location().getPath().startsWith("crops/")).forEach(t -> {
                String name = t.location().getPath().substring("crops/".length());
                boolean seeded = BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "seeds/" + name))).iterator().hasNext();
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(t)) if (!no.contains(holder.value())) m.putIfAbsent(holder.value(), seeded ? Diet.SEED : Diet.ROOT);
            });
        }
        if (log) {  /* one line per build, so a server can see what its lists came to */
            java.util.EnumMap<Diet, Integer> n = new java.util.EnumMap<>(Diet.class);
            m.values().forEach(d -> n.merge(d, 1, Integer::sum));
            RancherCompanion.LOG.info("[food lists] raw {}, cooked {}, seed {}, root {} foods; {} never", n.getOrDefault(Diet.RAW, 0),
                n.getOrDefault(Diet.COOKED, 0), n.getOrDefault(Diet.SEED, 0), n.getOrDefault(Diet.ROOT, 0), no.size());
        }
        return m;
    }

    /** an entry's items: an item id, or every item in a tag (#namespace:path) */
    private static List<Item> items(String entry, String list, boolean log) {
        List<Item> out = new java.util.ArrayList<>();
        if (entry.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
            if (id == null) { warn(log, "bad tag " + entry + " in the " + list + " list, skipped"); return out; }
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) out.add(holder.value());
        } else {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            var item = id == null ? java.util.Optional.<Item>empty() : BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty()) warn(log, "unknown item " + entry + " in the " + list + " list, skipped");
            else out.add(item.get());
        }
        return out;
    }

    private static void put(Map<Item, Diet> m, Item item, Diet d, boolean log) {
        Diet first = m.putIfAbsent(item, d);
        if (first != null && first != d) warn(log, BuiltInRegistries.ITEM.getKey(item) + " is in the " + first.label.toLowerCase()
            + " list and the " + d.label.toLowerCase() + " list: kept " + first.label.toLowerCase());
    }

    private static void warn(boolean log, String msg) {
        if (log) RancherCompanion.LOG.warn("[food lists] {}", msg);
    }
}
