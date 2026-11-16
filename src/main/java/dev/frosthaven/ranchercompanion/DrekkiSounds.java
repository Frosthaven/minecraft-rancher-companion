package dev.frosthaven.ranchercompanion;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;

/** The drekki's sound events (assets/rancher_companion/sounds.json): the game's own recordings, and the design page's synthesized
 *  cues rendered to files under sounds/drekki. Game sounds the engine cannot play as the page
 *  does (filtered, past double speed, above full volume) are baked into files too. GAIN is each file's level before it was normalized;
 *  PEAK is how far into a baked bite its jaws meet. */
public final class DrekkiSounds {
    private DrekkiSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, RancherCompanion.ID);

    private static DeferredHolder<SoundEvent, SoundEvent> ev(String key) {
        return SOUNDS.register("drekki." + key, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "drekki." + key)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> BITE1 = ev("bite1"), BITE2 = ev("bite2"), BITE3 = ev("bite3"), PIERCE = ev("pierce"), FIRE_SHOT = ev("fire_shot"), FIRE_IGNITE = ev("fire_ignite"), FIRE_HIT = ev("fire_hit"), CHIME = ev("chime"), CHIME_HI = ev("chime_hi"), GROW = ev("grow"), RUSTLE = ev("rustle"), ARMOR = ev("armor"), CHAIN = ev("chain"), SHIELD = ev("shield"), NOSE = ev("nose"), EAT = ev("eat"), BURST_LONG = ev("burst_long"), BURST_SHORT = ev("burst_short"), CALL = ev("call"), GROWL_DASH = ev("growl_dash"), GROWL_ROAR = ev("growl_roar"), GRUNT = ev("grunt"), HUFF = ev("huff"), LOOP_HISS = ev("loop_hiss"), LOOP_WIND = ev("loop_wind"), SNIFF = ev("sniff"), SNORE = ev("snore"), STEP_HEAVY = ev("step_heavy"), STEP_LIGHT = ev("step_light"), STEP_MID = ev("step_mid"), WHOOSH_BEAT = ev("whoosh_beat"), WHOOSH_BURST = ev("whoosh_burst"), WHOOSH_INHALE = ev("whoosh_inhale"), WHOOSH_LEAP = ev("whoosh_leap"), WHOOSH_ROCK = ev("whoosh_rock"), WHOOSH_UP = ev("whoosh_up"), YELP = ev("yelp");

    public static final Map<String, Float> GAIN = Map.ofEntries(Map.entry("bite1", 1.0782f), Map.entry("bite2", 1.2272f), Map.entry("bite3", 1.38f), Map.entry("burst_long", 1.1088f), Map.entry("burst_short", 0.3565f), Map.entry("call", 0.0262f), Map.entry("chime", 0.0379f), Map.entry("chime_hi", 0.0379f), Map.entry("fire_hit", 1.0772f), Map.entry("fire_ignite", 0.8442f), Map.entry("fire_shot", 1.1247f), Map.entry("growl_dash", 0.1221f), Map.entry("growl_roar", 0.1717f), Map.entry("grunt", 0.0677f), Map.entry("huff", 0.0587f), Map.entry("loop_hiss", 0.4906f), Map.entry("loop_wind", 0.6844f), Map.entry("sniff", 0.1956f), Map.entry("snore", 0.0318f), Map.entry("step_heavy", 0.603f), Map.entry("step_light", 0.1533f), Map.entry("step_mid", 0.1864f), Map.entry("whoosh_beat", 0.1263f), Map.entry("whoosh_burst", 0.0791f), Map.entry("whoosh_inhale", 0.0773f), Map.entry("whoosh_leap", 0.2844f), Map.entry("whoosh_rock", 0.0103f), Map.entry("whoosh_up", 0.0577f), Map.entry("yelp", 0.0708f));
    public static final Map<String, Float> PEAK = Map.of("bite1", 0.087f, "bite2", 0.1053f, "bite3", 0.1333f);
}
