package dev.frosthaven.ranchercompanion.life;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.look.Astral;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.common.Tags;

/**
 * Where a drekki is, as the affinities count it (spec, Astral affinity; Growth release: every biome counts). Astral by the
 * six tests, with other dimensions by the {@code rancher_companion:astral/<name>} dimension type tags; biome category by
 * the {@code rancher_companion:teaches/<category>} biome tags, then the convention tags, then Plains.
 */
public final class Places {
    private Places() {}

    public static final List<String> CATEGORIES = List.of("snow_ice", "plains", "dry", "forest", "taiga", "jungle", "wet",
        "hills_mountains", "water_coast", "ocean", "cave", "nether_forest", "nether_barren", "end", "end_exploration");

    private static final Map<String, TagKey<Biome>> TEACHES = new java.util.LinkedHashMap<>();
    static { for (String c : CATEGORIES) TEACHES.put(c, TagKey.create(Registries.BIOME, id("teaches/" + c))); }

    private static final String[] ASTRAL_TAGGED = {"underworld", "end", "deep", "aero", "lunar", "solar"};
    private static final int[] ASTRAL_OF = {Astral.UNDERWORLD, Astral.END, Astral.DEEP, Astral.AERO, Astral.LUNAR, Astral.SOLAR};

    /** the convention tags, in order, for a biome no category names */
    private static final List<Map.Entry<TagKey<Biome>, String>> FALLBACK = List.of(
        Map.entry(Tags.Biomes.IS_NETHER_FOREST, "nether_forest"), Map.entry(Tags.Biomes.IS_NETHER, "nether_barren"),
        Map.entry(Tags.Biomes.IS_END, "end_exploration"), Map.entry(Tags.Biomes.IS_CAVE, "cave"),
        Map.entry(Tags.Biomes.IS_UNDERGROUND, "cave"), Map.entry(Tags.Biomes.IS_OCEAN, "ocean"),
        Map.entry(Tags.Biomes.IS_RIVER, "water_coast"), Map.entry(Tags.Biomes.IS_BEACH, "water_coast"),
        Map.entry(Tags.Biomes.IS_SNOWY, "snow_ice"), Map.entry(Tags.Biomes.IS_ICY, "snow_ice"),
        Map.entry(Tags.Biomes.IS_DESERT, "dry"), Map.entry(Tags.Biomes.IS_BADLANDS, "dry"), Map.entry(Tags.Biomes.IS_SAVANNA, "dry"),
        Map.entry(Tags.Biomes.IS_DRY, "dry"), Map.entry(Tags.Biomes.IS_JUNGLE, "jungle"), Map.entry(Tags.Biomes.IS_SWAMP, "wet"),
        Map.entry(Tags.Biomes.IS_WET, "wet"), Map.entry(Tags.Biomes.IS_TAIGA, "taiga"), Map.entry(Tags.Biomes.IS_CONIFEROUS_TREE, "taiga"),
        Map.entry(Tags.Biomes.IS_MOUNTAIN, "hills_mountains"), Map.entry(Tags.Biomes.IS_FOREST, "forest"), Map.entry(Tags.Biomes.IS_PLAINS, "plains"));

    private static final Map<ResourceKey<Biome>, String> CACHE = new ConcurrentHashMap<>();

    public static void invalidate() { CACHE.clear(); }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, path); }

    /** the astral place of a spot (Astral index), by the first test that matches */
    public static int astral(Level level, BlockPos pos) {
        if (level.dimension() == Level.NETHER) return Astral.UNDERWORLD;
        if (level.dimension() == Level.END) return Astral.END;
        Holder<DimensionType> type = level.dimensionTypeRegistration();
        for (int i = 0; i < ASTRAL_TAGGED.length; i++)
            if (type.is(TagKey.create(Registries.DIMENSION_TYPE, id("astral/" + ASTRAL_TAGGED[i])))) return ASTRAL_OF[i];
        /* the Overworld, and any other dimension, by height and the time of day */
        if (pos.getY() < 0) return Astral.DEEP;
        if (pos.getY() >= 128) return Astral.AERO;
        return level.isNight() ? Astral.LUNAR : Astral.SOLAR;
    }

    /** the biome category of a spot: Overworld time below y 0 is Cave, whatever the biome (spec, Rules: Underground) */
    public static String category(Level level, BlockPos pos) {
        if (level.dimension() == Level.OVERWORLD && pos.getY() < 0) return "cave";
        Holder<Biome> b = level.getBiome(pos);
        return b.unwrapKey().map(k -> CACHE.computeIfAbsent(k, key -> categoryOf(b))).orElseGet(() -> categoryOf(b));
    }

    private static String categoryOf(Holder<Biome> b) {
        for (var e : TEACHES.entrySet()) if (b.is(e.getValue())) return e.getKey();
        for (var e : FALLBACK) if (b.is(e.getKey())) return e.getValue();
        return "plains";  /* the rule for a biome nothing names */
    }
}
