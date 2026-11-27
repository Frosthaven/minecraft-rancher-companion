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
    public static final ModConfigSpec.IntValue CONFIG_VERSION;
    public static final ModConfigSpec.BooleanValue AGE_AT_WORK, AGE_AT_HOME;
    /** the defaults' version: a file below it has each value still at an older default moved to the new one (upgrade) */
    public static final int VERSION = 1;

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
        CONFIG_VERSION = b.comment("Which defaults this file has been upgraded to. On load, a value still at an older default moves to",
            "the new one, once; a value you changed is kept. Leave this as it is.").defineInRange("config_version", 0, 0, 1000);
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
            "Travel counts while it is checked out, out of its carrier and not employed.").push("growth");
        STAGE_DAYS = b.defineInRange("days_per_stage", 6.0, 0.01, 1000.0);
        AGE_AT_WORK = b.comment("Whether a drekki at work (a Create job seat) ages too. Off: it ages only while out with its owner.")
            .define("age_at_work", false);
        AGE_AT_HOME = b.comment("Whether a drekki at its hatchery ages too. Off: it ages only while out with its owner.")
            .define("age_at_home", false);
        b.pop();
        b.comment("Hunger (spec, Hunger): shanks lost per game day, out of ten, while the drekki is live (its chunk ticking).",
            "Saturation is spent first. Travelling (checked out) drains at its own rate. A food gives its own nutrition",
            "(2 points to a shank) and saturation.").push("hunger");
        HUNGER_PER_DAY = b.defineInRange("shanks_per_day", 0.75, 0.0, 100.0);   /* 0.5 until 0.8.1 (Shane, 2026-11-27: 1.5x) */
        TRAVEL_HUNGER_PER_DAY = b.defineInRange("travel_shanks_per_day", 1.5, 0.0, 100.0);   /* 1.0 until 0.8.1 */
        b.pop();
        SPEC = b.build();
    }

    /**
     * On load: a file below {@link #VERSION} has each value still at the default it was written with moved to today's
     * default, then the version is set, so it runs once; a value an admin changed is kept, and so is one set back later.
     * Admins tune the file (world/serverconfig/rancher_companion-server.toml); a new default reaches a server whose admin
     * never touched that value (Shane, 2026-11-27: no renamed keys).
     */
    public static void upgrade(net.neoforged.fml.event.config.ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() != SPEC || CONFIG_VERSION.get() >= VERSION) return;
        int from = CONFIG_VERSION.get();
        if (from < 1) {   /* 0.8.1: hunger 1.5x */
            moved(HUNGER_PER_DAY, 0.5, 0.75);
            moved(TRAVEL_HUNGER_PER_DAY, 1.0, 1.5);
        }
        CONFIG_VERSION.set(VERSION);
        SPEC.save();
        RancherCompanion.LOG.info("[config] defaults upgraded from version {} to {}", from, VERSION);
    }

    static boolean moved(ModConfigSpec.DoubleValue v, double old, double now) {
        if (Math.abs(v.get() - old) > 1e-9) return false;
        v.set(now);
        RancherCompanion.LOG.info("[config] {} was the old default {}: now {}", String.join(".", v.getPath()), old, now);
        return true;
    }
}
