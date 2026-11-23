package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.look.Astral;

/**
 * The activity traits (spec, Traits; the team's sheet, Activity Traits tab): how each is learned. Counted ones teach at
 * their count, one-off ones at the mood's chance each time, death ones always. Names and effects are in the lang file
 * ({@code trait.rancher_companion.<id>} and {@code .desc}). Riding ones are learned now and work once riding exists.
 */
public enum Trait {
    HOOK_LINE_SINKER("hook_line_sinker", Learn.COUNTED, 5, false, 0, Trait.ALL),
    FALLEN_EMPEROR("fallen_emperor", Learn.COUNTED, 10, false, 0, of(Astral.UNDERWORLD, Astral.END)),
    BALM_SALVE("balm_salve", Learn.ONE_OFF, 1, false, 300, Trait.ALL),
    HIDE_SEEK("hide_seek", Learn.COUNTED, 5, false, 0, of(Astral.UNDERWORLD)),
    PATCHWORK("patchwork", Learn.ONE_OFF, 1, false, 168000, Trait.ALL),
    STAMP_OUT("stamp_out", Learn.ONE_OFF, 1, false, 2400, of(Astral.SOLAR, Astral.UNDERWORLD)),
    HUDDLE_UP("huddle_up", Learn.ONE_OFF, 1, false, 2400, of(Astral.LUNAR, Astral.AERO)),
    PHOENIX_HEART("phoenix_heart", Learn.DEATH, 1, false, 0, of(Astral.SOLAR, Astral.END)),
    CLOUD_STOMPERS("cloud_stompers", Learn.COUNTED, 10, true, 0, of(Astral.AERO, Astral.END)),
    TRAINING_DAY("training_day", Learn.COUNTED, 3, false, 6000, Trait.ALL),
    CREATURE_COMFORTS("creature_comforts", Learn.COUNTED, 5, false, 0, of(Astral.SOLAR, Astral.LUNAR)),
    NIGHT_WATCH("night_watch", Learn.COUNTED, 5, false, 0, Trait.ALL),
    SOFT_LANDING("soft_landing", Learn.ONE_OFF, 1, false, 2400, Trait.ALL),
    UNDERTOW("undertow", Learn.ONE_OFF, 1, false, 6000, of(Astral.LUNAR, Astral.DEEP)),
    HEARTY("hearty", Learn.COUNTED, 5, false, 0, Trait.ALL),
    DUCK_COVER("duck_cover", Learn.COUNTED, 10, false, 120, Trait.ALL),
    /* 0.5.0 (spec, Traits; decided 2026-11-23). The six with one affinity are the capstones. */
    PROVOKE("provoke", Learn.COUNTED, 5, false, 320, of(Astral.SOLAR)),
    BARBED_COAT("barbed_coat", Learn.ONE_OFF, 1, false, 0, of(Astral.DEEP, Astral.UNDERWORLD)),
    POINT_BREAK("point_break", Learn.DEATH, 1, false, 72000, of(Astral.DEEP)),
    FADE("fade", Learn.ONE_OFF, 1, false, 320, of(Astral.LUNAR)),
    STARING_CONTEST("staring_contest", Learn.COUNTED, 5, false, 0, of(Astral.DEEP, Astral.END)),
    WEB_CUTTER("web_cutter", Learn.COUNTED, 10, false, 0, of(Astral.LUNAR, Astral.DEEP)),
    MARKED("marked", Learn.COUNTED, 10, false, 0, of(Astral.SOLAR, Astral.AERO)),
    LIVE_WIRE("live_wire", Learn.ONE_OFF, 1, false, 0, of(Astral.AERO)),
    REWIND("rewind", Learn.ONE_OFF, 1, false, 6000, of(Astral.END));

    /** a biome trait the hunger rules read (learned with the biome lock-in, a later release) */
    public static final String HIBERNATION_PLAN = "hibernation_plan";

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

    /** every settled affinity; a Dormant drekki learns these only */
    private static final int ALL = 0b1111110;  /* bits 1 to 6; a constant, so the enum's own entries may name it */

    private static int of(int... astral) {
        int mask = 0;
        for (int a : astral) mask |= 1 << a;
        return mask;
    }

    Trait(String id, Learn learn, int count, boolean riding, int cooldown, int affinities) {
        this.id = id; this.learn = learn; this.count = count; this.riding = riding; this.cooldown = cooldown; this.affinities = affinities;
    }

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
