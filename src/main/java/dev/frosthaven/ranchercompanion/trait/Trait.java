package dev.frosthaven.ranchercompanion.trait;

/**
 * The activity traits (spec, Traits; the team's sheet, Activity Traits tab): how each is learned. Counted ones teach at
 * their count, one-off ones at the mood's chance each time, death ones always. Names and effects are in the lang file
 * ({@code trait.rancher_companion.<id>} and {@code .desc}). Riding ones are learned now and work once riding exists.
 */
public enum Trait {
    HOOK_LINE_SINKER("hook_line_sinker", Learn.COUNTED, 5, false, 0),
    FALLEN_EMPEROR("fallen_emperor", Learn.COUNTED, 10, false, 0),
    BALM_SALVE("balm_salve", Learn.ONE_OFF, 1, false, 300),
    HIDE_SEEK("hide_seek", Learn.COUNTED, 5, false, 0),
    PATCHWORK("patchwork", Learn.ONE_OFF, 1, false, 168000),
    STAMP_OUT("stamp_out", Learn.ONE_OFF, 1, false, 2400),
    HUDDLE_UP("huddle_up", Learn.ONE_OFF, 1, false, 2400),
    PHOENIX_HEART("phoenix_heart", Learn.DEATH, 1, false, 0),
    CLOUD_STOMPERS("cloud_stompers", Learn.COUNTED, 10, true, 0),
    TRAINING_DAY("training_day", Learn.COUNTED, 3, false, 6000),
    CREATURE_COMFORTS("creature_comforts", Learn.COUNTED, 5, false, 0),
    NIGHT_WATCH("night_watch", Learn.COUNTED, 5, false, 0),
    SOFT_LANDING("soft_landing", Learn.ONE_OFF, 1, false, 2400),
    UNDERTOW("undertow", Learn.ONE_OFF, 1, false, 6000),
    HEARTY("hearty", Learn.COUNTED, 5, false, 0);

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

    Trait(String id, Learn learn, int count, boolean riding, int cooldown) { this.id = id; this.learn = learn; this.count = count; this.riding = riding; this.cooldown = cooldown; }

    public static Trait byId(String id) {
        for (Trait t : values()) if (t.id.equals(id)) return t;
        return null;
    }

    /** the most traits a drekki holds at a stage (spec, Traits: caps) */
    public static int cap(int stage) { return stage * 3; }
}
