package dev.frosthaven.ranchercompanion;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config (rancher_companion-server.toml in the world's serverconfig). The four food lists decide which food
 * belongs to which diet; entries are item ids or item tags (#namespace:path). They are read raw, cooked, seed, root,
 * each top to bottom, and the first list an item is read into is its only one (see {@code FoodDiets}).
 */
public final class DrekkiConfig {
    private DrekkiConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NEVER, RAW, COOKED, SEED, ROOT;
    public static final ModConfigSpec.BooleanValue CROPS_BY_SEEDS;
    public static final ModConfigSpec.DoubleValue STAGE_DAYS, HUNGER_PER_DAY, TRAVEL_HUNGER_PER_DAY;

    /** the spec's lists (design page, Diet tab), every vanilla food, crop and seed by its rules (2026-11-16 review), then
        the convention tags for raw and cooked meat and fish, so modded meat feeds too; the plant tags are left out, as they
        would put berries and chorus fruit under the wrong diet */
    public static final List<String> RAW_DEFAULT = List.of("minecraft:beef", "minecraft:porkchop", "minecraft:mutton",
        "minecraft:chicken", "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish",
        "minecraft:pufferfish", "minecraft:rotten_flesh", "minecraft:spider_eye", "#c:foods/raw_meat", "#c:foods/raw_fish");
    public static final List<String> COOKED_DEFAULT = List.of("minecraft:cooked_beef", "minecraft:cooked_porkchop",
        "minecraft:cooked_mutton", "minecraft:cooked_chicken", "minecraft:cooked_rabbit", "minecraft:cooked_cod",
        "minecraft:cooked_salmon", "#c:foods/cooked_meat", "#c:foods/cooked_fish");
    public static final List<String> SEED_DEFAULT = List.of("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:beetroot",
        "minecraft:beetroot_seeds", "minecraft:melon_slice", "minecraft:melon_seeds", "minecraft:pumpkin",
        "minecraft:pumpkin_seeds", "minecraft:melon", "minecraft:apple", "#c:seeds");
    public static final List<String> ROOT_DEFAULT = List.of("minecraft:potato", "minecraft:baked_potato",
        "minecraft:poisonous_potato", "minecraft:carrot", "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:kelp",
        "minecraft:dried_kelp", "minecraft:cocoa_beans", "minecraft:red_mushroom", "minecraft:brown_mushroom",
        "minecraft:chorus_fruit", "minecraft:bamboo", "minecraft:cactus", "minecraft:sugar_cane", "minecraft:crimson_fungus",
        "minecraft:warped_fungus", "minecraft:nether_wart", "#c:foods/berry");
    /** never food, read first: the golden foods and the two seeds the spec leaves out (they come in with #c:seeds) */
    public static final List<String> NEVER_DEFAULT = List.of("#c:foods/golden", "minecraft:torchflower_seeds", "minecraft:pitcher_pod");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Which food belongs to which diet: item ids or item tags (#namespace:path).",
            "Read raw, cooked, seed, root, each top to bottom. The first list an item is read into is its only one;",
            "a later list naming it again is skipped, with a warning in the log. Items in 'never' are never food.").push("diet");
        NEVER = b.defineListAllowEmpty("never", NEVER_DEFAULT, () -> "", o -> o instanceof String);
        RAW = b.defineListAllowEmpty("raw", RAW_DEFAULT, () -> "", o -> o instanceof String);
        COOKED = b.defineListAllowEmpty("cooked", COOKED_DEFAULT, () -> "", o -> o instanceof String);
        SEED = b.defineListAllowEmpty("seed", SEED_DEFAULT, () -> "", o -> o instanceof String);
        ROOT = b.defineListAllowEmpty("root", ROOT_DEFAULT, () -> "", o -> o instanceof String);
        CROPS_BY_SEEDS = b.comment("After the lists: every item in a c:crops/<name> tag is Seed food when a c:seeds/<name> tag",
            "has items (it grows from a separate seed), else Root (the crop itself is planted). Covers modded crops that",
            "follow the convention tags; anything the lists already named keeps its diet.").define("crops_by_seed_tags", true);
        b.pop();
        b.comment("Growth (spec, Growth release): travel time a drekki needs per stage, in game days (24000 ticks).",
            "Travel counts while it is checked out and out of its carrier.").push("growth");
        STAGE_DAYS = b.defineInRange("days_per_stage", 6.0, 0.01, 1000.0);
        b.pop();
        b.comment("Hunger (spec, Hunger): shanks lost per game day, out of ten, while the drekki is live (its chunk ticking).",
            "Saturation is spent first. Travelling (checked out) drains at its own rate. A food gives its own nutrition",
            "(2 points to a shank) and saturation.").push("hunger");
        HUNGER_PER_DAY = b.defineInRange("shanks_per_day", 0.5, 0.0, 100.0);
        TRAVEL_HUNGER_PER_DAY = b.defineInRange("travel_shanks_per_day", 1.0, 0.0, 100.0);
        b.pop();
        SPEC = b.build();
    }
}
