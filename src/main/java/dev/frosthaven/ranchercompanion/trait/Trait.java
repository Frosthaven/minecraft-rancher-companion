package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.look.Astral;

/**
 * The activity traits (spec, Traits; the team's sheet, Activity Traits tab): how each is learned. Counted ones teach at
 * their count, one-off ones at the mood's chance each time, death ones always. Names and effects are in the lang file
 * ({@code trait.rancher_companion.<id>} and {@code .desc}). Riding ones are learned now and work once riding exists.
 */
public enum Trait {
    HOOK_LINE_SINKER("hook_line_sinker", Learn.COUNTED, 5, false, 0, Trait.ALL, Rarity.UNCOMMON),
    FALLEN_EMPEROR("fallen_emperor", Learn.COUNTED, 10, false, 0, of(Astral.UNDERWORLD, Astral.END), Rarity.UNCOMMON),
    BALM_SALVE("balm_salve", Learn.ONE_OFF, 1, false, 300, Trait.ALL, Rarity.RARE),
    HIDE_SEEK("hide_seek", Learn.COUNTED, 5, false, 0, of(Astral.UNDERWORLD), Rarity.EPIC),
    PATCHWORK("patchwork", Learn.ONE_OFF, 1, false, 168000, Trait.ALL, Rarity.RARE),
    STAMP_OUT("stamp_out", Learn.ONE_OFF, 1, false, 2400, of(Astral.SOLAR, Astral.UNDERWORLD), Rarity.UNCOMMON),
    HUDDLE_UP("huddle_up", Learn.ONE_OFF, 1, false, 2400, of(Astral.LUNAR, Astral.AERO), Rarity.UNCOMMON),
    PHOENIX_HEART("phoenix_heart", Learn.DEATH, 1, false, 0, of(Astral.SOLAR, Astral.END), Rarity.EPIC),
    CLOUD_STOMPERS("cloud_stompers", Learn.COUNTED, 10, true, 0, of(Astral.AERO, Astral.END), Rarity.RARE),
    TRAINING_DAY("training_day", Learn.COUNTED, 3, false, 6000, Trait.ALL, Rarity.RARE),
    CREATURE_COMFORTS("creature_comforts", Learn.COUNTED, 5, false, 0, of(Astral.SOLAR, Astral.LUNAR), Rarity.EPIC),
    NIGHT_WATCH("night_watch", Learn.COUNTED, 5, false, 0, Trait.ALL, Rarity.UNCOMMON),
    SOFT_LANDING("soft_landing", Learn.ONE_OFF, 1, false, 2400, Trait.ALL, Rarity.RARE),
    UNDERTOW("undertow", Learn.ONE_OFF, 1, false, 6000, of(Astral.LUNAR, Astral.DEEP), Rarity.RARE),
    HEARTY("hearty", Learn.COUNTED, 5, false, 0, Trait.ALL, Rarity.RARE),
    DUCK_COVER("duck_cover", Learn.COUNTED, 10, false, 120, Trait.ALL, Rarity.UNCOMMON),
    /* 0.5.0 (spec, Traits; decided 2026-11-23). The six with one affinity are the capstones. */
    PROVOKE("provoke", Learn.COUNTED, 5, false, 320, of(Astral.SOLAR), Rarity.EPIC),
    BARBED_COAT("barbed_coat", Learn.ONE_OFF, 1, false, 0, of(Astral.DEEP, Astral.UNDERWORLD), Rarity.UNCOMMON),
    POINT_BREAK("point_break", Learn.DEATH, 1, false, 72000, of(Astral.DEEP), Rarity.EPIC),
    FADE("fade", Learn.ONE_OFF, 1, false, 320, of(Astral.LUNAR), Rarity.EPIC),
    STARING_CONTEST("staring_contest", Learn.COUNTED, 5, false, 0, of(Astral.DEEP, Astral.END), Rarity.UNCOMMON),
    WEB_CUTTER("web_cutter", Learn.COUNTED, 10, false, 0, of(Astral.LUNAR, Astral.DEEP), Rarity.UNCOMMON),
    MARKED("marked", Learn.COUNTED, 10, false, 0, of(Astral.SOLAR, Astral.AERO), Rarity.UNCOMMON),
    LIVE_WIRE("live_wire", Learn.ONE_OFF, 1, false, 0, of(Astral.AERO), Rarity.EPIC),
    REWIND("rewind", Learn.ONE_OFF, 1, false, 6000, of(Astral.END), Rarity.EPIC),
    /* the former biome traits, learned from activities since 2026-11-23 (spec, Traits). Six act only while ridden. */
    OPEN_ROAD("open_road", Learn.COUNTED, 20, false, 0, Trait.ALL, Rarity.UNCOMMON),
    HOMES_GARDENS("homes_gardens", Learn.COUNTED, 64, false, 0, Trait.ALL, Rarity.UNCOMMON),
    DOWNSTREAM("downstream", Learn.COUNTED, 10, false, 0, Trait.ALL, Rarity.UNCOMMON),
    FROST_STRIDER("frost_strider", Learn.COUNTED, 10, false, 0, of(Astral.LUNAR, Astral.AERO), Rarity.UNCOMMON),
    HIBERNATION("hibernation_plan", Learn.COUNTED, 5, false, 0, of(Astral.LUNAR, Astral.DEEP), Rarity.UNCOMMON),
    PREDATORS_EYE("predators_eye", Learn.COUNTED, 10, true, 0, of(Astral.LUNAR, Astral.DEEP), Rarity.UNCOMMON),
    OCEAN_WALKER("ocean_walker", Learn.COUNTED, 10, true, 0, of(Astral.SOLAR, Astral.LUNAR), Rarity.RARE),
    FOUR_WINDS("four_winds", Learn.COUNTED, 10, true, 0, of(Astral.SOLAR, Astral.AERO), Rarity.RARE),
    NATURAL_REPELLANT("natural_repellant", Learn.COUNTED, 10, false, 0, of(Astral.SOLAR, Astral.END), Rarity.UNCOMMON),
    HOGLIN_WARD("hoglin_ward", Learn.COUNTED, 5, false, 0, of(Astral.SOLAR, Astral.UNDERWORLD), Rarity.UNCOMMON),
    LAVA_WALKER("lava_walker", Learn.COUNTED, 10, true, 0, of(Astral.DEEP, Astral.UNDERWORLD), Rarity.EPIC),
    INVERSE_LUMINANCE("inverse_luminance", Learn.COUNTED, 64, false, 0, of(Astral.DEEP, Astral.UNDERWORLD), Rarity.UNCOMMON),
    DRACONIC_VAMPIRISM("draconic_vampirism", Learn.COUNTED, 10, false, 0, of(Astral.UNDERWORLD, Astral.END), Rarity.EPIC),
    LEAP_OF_FAITH("leap_of_faith", Learn.ONE_OFF, 1, true, 0, of(Astral.AERO, Astral.END), Rarity.RARE),
    WINGS_OF_HOPE("wings_of_hope", Learn.COUNTED, 10, true, 0, of(Astral.AERO, Astral.END), Rarity.EPIC);

    /** Hibernation Plan's id, which the hunger rules read */
    public static final String HIBERNATION_PLAN = "hibernation_plan";

    /** a trait's tier, by how much it changes play (spec, Traits: rarity); the color of its name. Legendary is for the
     *  traits only breeding gives, none of which is in the game yet. */
    public enum Rarity {
        UNCOMMON(0x5fd17a), RARE(0x5aa9ff), EPIC(0xc77dff), LEGENDARY(0xff9d3c);
        public final int color;
        Rarity(int color) { this.color = color; }
    }

    public enum Learn { COUNTED, ONE_OFF, DEATH }

    public final String id;
    public final Learn learn;
    public final int count;
    /** its effect needs a ridden stage 3 drekki */
    public final boolean riding;
    /** ticks between uses (half in the drekki's astral home terrain); 0 for none */
    public final int cooldown;

    /** the astral affinities that can learn it, a bit per {@link Astral} index (spec, Traits: affinity limits) */
    public final int affinities;
    public final Rarity rarity;

    /** every settled affinity; a Dormant drekki learns these only */
    private static final int ALL = 0b1111110;  /* bits 1 to 6; a constant, so the enum's own entries may name it */

    private static int of(int... astral) {
        int mask = 0;
        for (int a : astral) mask |= 1 << a;
        return mask;
    }

    Trait(String id, Learn learn, int count, boolean riding, int cooldown, int affinities, Rarity rarity) {
        this.id = id; this.learn = learn; this.count = count; this.riding = riding; this.cooldown = cooldown; this.affinities = affinities; this.rarity = rarity;
    }

    /** the color of a trait's name by its id; the old green for an id this version does not know */
    public static int color(String id) { Trait t = byId(id); return t == null ? 0x9fe3a6 : t.rarity.color; }

    /** whether a drekki of this affinity can learn it from the activity. An inherited trait does not ask. */
    public boolean learnedBy(int astral) {
        return astral == Astral.DORMANT ? affinities == ALL : (affinities & 1 << astral) != 0;
    }

    public static Trait byId(String id) {
        for (Trait t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    private static final int[] CAPS = {2, 4, 5};

    /** the most traits a drekki holds at a stage: 2, 4, 5, and one more once it has had a Potion of Horizons (spec,
     *  Traits: caps; 3, 6, 9 before 0.5.0) */
    public static int cap(int stage, boolean horizons) {
        return CAPS[Math.max(1, Math.min(CAPS.length, stage)) - 1] + (horizons ? 1 : 0);
    }
}
