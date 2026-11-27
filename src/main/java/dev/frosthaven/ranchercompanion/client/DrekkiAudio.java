package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.DrekkiSounds;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Anim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The design page's sound scores, one for one: each client tick reads a drekki's loop between the last tick and this one and plays
 * what the page plays in that span, at the page's levels and stage pitches. Cues the page delays are queued by ticks; the wind and
 * the breath's hiss are looping sounds whose volume follows the score.
 */
public final class DrekkiAudio {
    private DrekkiAudio() {}

    private static final Random R = new Random();
    private static final double TAU = 6.283;
    private static final float[] V = {0.55f, 0.8f, 1f}, P = {1.7f, 1.3f, 1f}, BITE_RATE = {1.15f, 0.95f, 0.75f}, FIRE_RATE = {1.35f, 1.12f, 0.68f};
    /** the page's gains run through its master at 0.6; a full-scale game sound is 1 */
    private static final float MASTER = 0.6f;

    private record Pending(DrekkiEntity d, DeferredHolder<SoundEvent, SoundEvent> ev, float vol, float pitch, int ticks) {}
    private static final List<Pending> QUEUE = new ArrayList<>();
    /** one bed per drekki and loop sound ("<entity id>:<sound>"): the wind and the hiss once shared one, so a breath after a glide hissed with the wind */
    private static final Map<String, Bed> BEDS = new HashMap<>();

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { QUEUE.clear(); BEDS.clear(); return; }
        if (mc.isPaused()) return;
        for (Iterator<Pending> it = QUEUE.iterator(); it.hasNext(); ) { Pending q = it.next(); if (q.ticks <= 0) { emit(mc.level, q.d, q.ev, q.vol, q.pitch); it.remove(); } }
        for (int i = 0; i < QUEUE.size(); i++) { Pending q = QUEUE.get(i); QUEUE.set(i, new Pending(q.d, q.ev, q.vol, q.pitch, q.ticks - 1)); }
        for (Entity en : mc.level.entitiesForRendering()) if (en instanceof DrekkiEntity d) d.trackSpeed();
        /* a bed the score does not set this tick falls silent: the glide's wind once played on after landing (the score skips
           an idle drekki and a walk never sets the wind), until the drekki unloaded (2026-11-27) */
        for (Bed b : BEDS.values()) b.target = 0;
        for (Entity en : mc.level.entitiesForRendering())
            if (en instanceof DrekkiEntity d && d.anim() != Anim.IDLE && d.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < 32 * 32) score(mc.level, d);
        BEDS.values().removeIf(b -> b.isStopped());
    }

    /* ---- the page's crossing tests over the span [a, b) of loop time ---- */
    private static boolean at(double a, double b, double period, double phase) {
        double fa = Anim.loop(a, period), fb = Anim.loop(b, period);
        return fa <= fb ? fa < phase && fb >= phase : fa < phase || fb >= phase;
    }
    private static boolean flip(double a, double b, double w) { return Math.sin(a * w) > 0 != Math.sin(b * w) > 0; }
    private static boolean count(double a, double b, double per) { return Math.floor(a / per) != Math.floor(b / per); }
    private static boolean stride(double wa, double wb) { return Math.floor((wa - Math.PI / 2) / Math.PI) != Math.floor((wb - Math.PI / 2) / Math.PI); }
    private static double ease(double u) { return u <= 0 ? 0 : u >= 1 ? 1 : 0.5 - 0.5 * Math.cos(u * Math.PI); }

    private static void emit(ClientLevel level, DrekkiEntity d, DeferredHolder<SoundEvent, SoundEvent> ev, float vol, float pitch) {
        level.playLocalSound(d.getX(), d.getY() + 0.5, d.getZ(), ev.get(), SoundSource.NEUTRAL, Math.min(1, vol), Math.max(0.5f, Math.min(2, pitch)), false);
    }
    /** a recorded game sound at the page's gain */
    private static void S(ClientLevel L, DrekkiEntity d, DeferredHolder<SoundEvent, SoundEvent> ev, double gain, double rate, double delay) {
        float vol = (float) (gain * MASTER); int ticks = (int) Math.round(delay * 20);
        if (ticks <= 0) emit(L, d, ev, vol, (float) rate); else QUEUE.add(new Pending(d, ev, vol, (float) rate, ticks));
    }
    private static void S(ClientLevel L, DrekkiEntity d, DeferredHolder<SoundEvent, SoundEvent> ev, double gain, double rate) { S(L, d, ev, gain, rate, 0); }
    /** a rendered cue: the page's X(name, vol, rate), at its rendered level */
    private static void X(ClientLevel L, DrekkiEntity d, DeferredHolder<SoundEvent, SoundEvent> ev, String name, double vol, double rate) { S(L, d, ev, vol * DrekkiSounds.GAIN.get(name), rate, 0); }
    private static final List<DeferredHolder<SoundEvent, SoundEvent>> BITE = List.of(DrekkiSounds.BITE1, DrekkiSounds.BITE2, DrekkiSounds.BITE3);
    /** the page's chime(vol, semi, delay): an octave up and turned well up; past double speed, the take baked two octaves up */
    private static void chime(ClientLevel L, DrekkiEntity d, double vol, double semi, double delay) {
        double r = Math.pow(2, (semi + 12) / 12);
        if (r > 2) S(L, d, DrekkiSounds.CHIME_HI, vol * 3.2 * DrekkiSounds.GAIN.get("chime_hi"), r / 4, delay); else S(L, d, DrekkiSounds.CHIME, vol * 3.2 * DrekkiSounds.GAIN.get("chime"), r, delay);
    }
    private static void chime(ClientLevel L, DrekkiEntity d, double vol, double semi) { chime(L, d, vol, semi, 0); }

    private static void score(ClientLevel L, DrekkiEntity d) {
        double b = d.time(0), a = b - 0.05; int s = d.stage() - 1;
        float v = V[s], p = P[s], br = BITE_RATE[s], r = FIRE_RATE[s];
        double jit = 0.95 + R.nextDouble() * 0.1, peak = DrekkiSounds.PEAK.get("bite" + (s + 1));
        switch (d.anim()) {
            case WALK -> { if (stride(a * 1.4 * TAU, b * 1.4 * TAU)) X(L, d, DrekkiSounds.STEP_LIGHT, "step_light", v, p); }
            case RUN -> { if (stride(a * 2.4 * TAU, b * 2.4 * TAU)) { X(L, d, DrekkiSounds.STEP_MID, "step_mid", v, p); if (((int) Math.floor((b * 2.4 * TAU - Math.PI / 2) / Math.PI)) % 2 == 0) X(L, d, DrekkiSounds.HUFF, "huff", v, p); } }
            case DASH -> { double f = Anim.loop(b, 3.4), dd = f < 0.45 ? Math.sin(f / 0.45 * Math.PI) : 0;
                if (stride(DrekkiAnimations.dashPhase(a) * TAU, DrekkiAnimations.dashPhase(b) * TAU)) X(L, d, DrekkiSounds.STEP_MID, "step_mid", (1 + 0.5 * dd) * v, p);
                if (at(a, b, 3.4, 0.0001)) { X(L, d, DrekkiSounds.WHOOSH_BURST, "whoosh_burst", v, p); X(L, d, DrekkiSounds.GROWL_DASH, "growl_dash", v, p); }
                bed(d, DrekkiSounds.LOOP_WIND, "loop_wind", 0.25 * dd); }
            case JUMP -> { if (at(a, b, 2.2, 0.1)) { X(L, d, DrekkiSounds.GRUNT, "grunt", v, p); X(L, d, DrekkiSounds.WHOOSH_UP, "whoosh_up", v, p); }
                if (at(a, b, 2.2, 0.19)) X(L, d, DrekkiSounds.WHOOSH_BEAT, "whoosh_beat", 0.4 * v, p);
                if (at(a, b, 2.2, 0.42)) X(L, d, DrekkiSounds.STEP_HEAVY, "step_heavy", 0.75 * v, p * 1.1); }
            case FALL -> { double f = Anim.loop(b, 2.6); bed(d, DrekkiSounds.LOOP_WIND, "loop_wind", f < 0.36 ? 0.1 * (f / 0.36) : 0);
                if (at(a, b, 2.6, 0.36)) { X(L, d, DrekkiSounds.STEP_HEAVY, "step_heavy", v, p); X(L, d, DrekkiSounds.YELP, "yelp", v, p); } }
            case GLIDE -> { if (!dev.frosthaven.ranchercompanion.entity.Riding.glideSounds(d)) break;  /* a short drop is quiet */
                bed(d, DrekkiSounds.LOOP_WIND, "loop_wind", 0.175); if (flip(a, b, 1.6)) X(L, d, DrekkiSounds.WHOOSH_ROCK, "whoosh_rock", v, p);
                if (count(a, b, 3.5)) X(L, d, DrekkiSounds.CALL, "call", v, p); }
            case SLEEP -> { double sa = ease((d.sleepTime(a) - 0.6) / 1.8), sb = ease((d.sleepTime(b) - 0.6) / 1.8);
                if (sa < 0.5 && sb >= 0.5) X(L, d, DrekkiSounds.STEP_LIGHT, "step_light", 0.83 * v, 0.8 * p);
                if (sa < 0.95 && sb >= 0.95) S(L, d, DrekkiSounds.NOSE, 0.9 * v, r * 0.9);
                if (sb > 0.99 && flip(a, b, 1.4) && Math.sin(b * 1.4) < 0) { S(L, d, DrekkiSounds.NOSE, 0.7 * v, r * jit); if (count(a * 1.4 / TAU, b * 1.4 / TAU, 3)) X(L, d, DrekkiSounds.SNORE, "snore", v, p); } }
            case FEED -> { double W = Anim.FEED_W, per = W + 4.2, T = Anim.loop(b, per) * per, f = (T - W) / 4.2;
                if (T < W) { if (stride(a * 1.4 * TAU, b * 1.4 * TAU)) X(L, d, DrekkiSounds.STEP_LIGHT, "step_light", v, p); return; }
                if (at(a, b, per, W / per + 0.0001)) X(L, d, DrekkiSounds.SNIFF, "sniff", v, p);
                if (f > 0.14 && f < 0.48 && flip(a, b, 7)) S(L, d, DrekkiSounds.EAT, 0.35 * v, r * jit);
                if (f > 0.6 && f < 0.86 && flip(a, b, 5)) S(L, d, DrekkiSounds.EAT, 0.22 * v, r * 0.9);
                }
            case BITE -> { if (at(a, b, 1.6, 0.21 - peak / 1.6)) X(L, d, BITE.get(s), "bite" + (s + 1), 1.95 * v, 1);   /* started so the take's snap lands as the jaw shuts at 0.21 */
                if (at(a, b, 1.6, 0.2)) S(L, d, DrekkiSounds.PIERCE, 0.8 * v, br); }
            case POUNCE -> { if (at(a, b, 2.4, 0.04)) { X(L, d, DrekkiSounds.WHOOSH_LEAP, "whoosh_leap", v, p); X(L, d, DrekkiSounds.WHOOSH_BEAT, "whoosh_beat", v, p); X(L, d, DrekkiSounds.STEP_MID, "step_mid", 0.875 * v, p); }
                if (at(a, b, 2.4, 0.18)) X(L, d, DrekkiSounds.STEP_LIGHT, "step_light", v, 0.8 * p);
                if (at(a, b, 2.4, 0.2364 - peak / 2.4)) X(L, d, BITE.get(s), "bite" + (s + 1), 1.95 * v, 1);
                if (at(a, b, 2.4, 0.225)) S(L, d, DrekkiSounds.PIERCE, 0.9 * v, br); }
            case BALL -> { if (at(a, b, 2.8, 0.14)) { X(L, d, DrekkiSounds.FIRE_SHOT, "fire_shot", 1.4 * v, r * 2.7 / 2); X(L, d, DrekkiSounds.FIRE_IGNITE, "fire_ignite", 0.7 * v, r * 2.7 / 2); X(L, d, DrekkiSounds.BURST_SHORT, "burst_short", v, p); }
                if (at(a, b, 2.8, 0.5)) { X(L, d, DrekkiSounds.FIRE_HIT, "fire_hit", 0.6 * v, r * 2.4 / 2); X(L, d, DrekkiSounds.BURST_LONG, "burst_long", v, p); } }
            case BREATH -> { double f = Anim.loop(b, 3.8); boolean on = f >= 0.1 && f < 0.62; double fade = f >= 0.62 && f < 0.72 ? 1 - (f - 0.62) / 0.1 : 0;
                bed(d, DrekkiSounds.LOOP_HISS, "loop_hiss", on ? 0.7 : 0.7 * fade);
                if (at(a, b, 3.8, 0.0001)) X(L, d, DrekkiSounds.WHOOSH_INHALE, "whoosh_inhale", v, p);
                if (at(a, b, 3.8, 0.1)) X(L, d, DrekkiSounds.GROWL_ROAR, "growl_roar", v, p); }
            case GUARD -> { double fb = Anim.loop(b, 4.4); int base = new int[]{7, 3, 0}[s];
                if (at(a, b, 4.4, 0.1)) { X(L, d, DrekkiSounds.STEP_HEAVY, "step_heavy", 0.75 * v, 0.875 * p); S(L, d, DrekkiSounds.SHIELD, 0.5 * v, r); S(L, d, DrekkiSounds.ARMOR, 1.4 * v, r, 0.06); chime(L, d, 0.7 * v, base + 7, 0.2); }
                int ia = (int) Math.floor((Anim.loop(a, 4.4) - 0.1) * 4.4 / 0.55), ib = (int) Math.floor((fb - 0.1) * 4.4 / 0.55);
                if (fb >= 0.2 && fb < 0.66 && ia != ib) { int[][] fig = {{12, 7}, {10, 5}, {7, 12}}; chime(L, d, 0.3 * v, base - 5 + fig[Math.floorMod(ib, 3)][1]); S(L, d, DrekkiSounds.CHAIN, 0.6 * v, r * jit); } }
            case REJUV -> { double fb = Anim.loop(b, 4.2); int base = new int[]{9, 5, 2}[s];
                if (at(a, b, 4.2, 0.06)) { S(L, d, DrekkiSounds.RUSTLE, 1.3 * v, r); S(L, d, DrekkiSounds.GROW, 1.4 * v, r, 0.05); chime(L, d, 0.5 * v, base + 12, 0.3); }
                int ia = (int) Math.floor((Anim.loop(a, 4.2) - 0.1) * 4.2 / 0.55), ib = (int) Math.floor((fb - 0.1) * 4.2 / 0.55);
                if (fb >= 0.2 && fb < 0.62 && ia != ib) { int[][] fig = {{12, 7}, {14, 9}, {16, 12}}; int[] g = fig[Math.floorMod(ib, 3)];
                    chime(L, d, 0.55 * v, base + g[0] - 24); chime(L, d, 0.42 * v, base + g[1] - 24, 0.1); S(L, d, DrekkiSounds.RUSTLE, 0.7 * v, r * (0.95 + R.nextDouble() * 0.15)); } }
            default -> {}
        }
    }

    /* ---- looping beds: one per drekki at its own pitch (the page does not pitch them), its volume following the score ---- */
    private static void bed(DrekkiEntity d, DeferredHolder<SoundEvent, SoundEvent> ev, String name, double level) {
        String key = d.getId() + ":" + name;
        Bed bd = BEDS.get(key);
        if (bd == null || bd.isStopped()) { if (level <= 0.001) return; bd = new Bed(d, ev.get()); BEDS.put(key, bd); Minecraft.getInstance().getSoundManager().play(bd); }
        bd.target = (float) Math.min(1, level * DrekkiSounds.GAIN.get(name) * MASTER);
    }

    /** the loudest loop this drekki plays now (self-tests: nothing after a landing) */
    static float loopVolume(DrekkiEntity d) {
        float v = 0; String pre = d.getId() + ":";
        for (var e : BEDS.entrySet()) if (e.getKey().startsWith(pre) && !e.getValue().isStopped()) v = Math.max(v, e.getValue().getVolume());
        return v;
    }

    private static final class Bed extends AbstractTickableSoundInstance {
        private final DrekkiEntity d; float target; private int quiet;
        Bed(DrekkiEntity d, SoundEvent ev) {
            super(ev, SoundSource.NEUTRAL, RandomSource.create()); this.d = d; looping = true; delay = 0; volume = 0.0001f; pitch = 1;
            attenuation = SoundInstance.Attenuation.LINEAR; x = d.getX(); y = d.getY() + 0.5; z = d.getZ();
        }
        @Override public void tick() {
            if (d.isRemoved()) { stop(); return; }
            x = d.getX(); y = d.getY() + 0.5; z = d.getZ(); volume += (Math.max(0.0001f, target) - volume) * 0.63f;   /* the page eases its level with a 50 ms time constant: 1 - e^-1 per tick */
            quiet = target <= 0.001 ? quiet + 1 : 0; if (quiet > 40) stop();
        }
        @Override public boolean canStartSilent() { return true; }
    }
}
