package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.client.rig.Rig;
import dev.frosthaven.ranchercompanion.client.rig.RigBake;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.look.Lineage;
import dev.frosthaven.ranchercompanion.look.Palette;

import java.util.HashMap;
import java.util.Map;

/** Baked models by look, built on first use. The plains foot tint stands in until biome affinity is implemented. */
public final class DrekkiModels {
    private DrekkiModels() {}

    private static final Map<String, DrekkiModel> CACHE = new HashMap<>();
    private static final int PLAINS = 0x8a9a6a;

    public static DrekkiModel get(DrekkiEntity e) {
        boolean spread = e.anim() == Anim.GLIDE;
        int bard = e.stage() >= 2 ? e.barding() : 0, bag = e.stage() >= 2 ? e.bag() : 0;
        String key = e.stage() + "_" + e.line() + "_" + e.diet() + "_" + e.astral() + "_" + e.horn() + (spread ? "_s" : "") + "_h" + e.hat() + "_b" + bard
            + "_d" + Integer.toHexString(e.dye()) + "_" + Integer.toHexString(e.dye2()) + "_g" + bag + "_" + Integer.toHexString(e.bagDye()) + "_" + Integer.toHexString(e.bagDye2());
        DrekkiModel m = CACHE.get(key);
        if (m == null || !m.baked.alive()) {  /* new, or its textures released by another mod: baked again */
            m = build(e.stage(), e.lineage(), Diet.of(e.diet()), e.astral(), e.horn(), spread, e.hat(), bard, e.dye(), e.dye2(), bag, e.bagDye(), e.bagDye2(), key);
            CACHE.put(key, m);
        }
        return m;
    }

    public static DrekkiModel fallback() {
        DrekkiModel m = CACHE.get("fallback");
        if (m == null || !m.baked.alive()) { m = build(3, Lineage.of(0), Diet.RAW, 1, 0, false, 0, 0, -1, -1, 0, -1, -1, "fallback"); CACHE.put("fallback", m); }
        return m;
    }

    private static DrekkiModel build(int stage, Lineage line, Diet diet, int astral, int horn, boolean spread, int hat, int barding, int dye, int dye2, int bag, int bagDye, int bagDye2, String key) {
        Rig rig = new Rig(new Rig.Look(stage, line.fur(), line.trim(), Palette.legTint(PLAINS, line.fur()), astral, diet, horn, spread, hat, barding, dye, line.sheenHex(), line.sheen(), line.seeThrough(),
            dye2, bag, bagDye, bagDye2));
        return new DrekkiModel(RigBake.bake(rig, key));
    }
}
