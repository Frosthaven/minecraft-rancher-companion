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

/** Baked models by look, built on first use. The foot tint is the biome it hatched in (look.FootTint); a showcase drekki has the plains tint. */
public final class DrekkiModels {
    private DrekkiModels() {}

    private static final Map<String, DrekkiModel> CACHE = new HashMap<>();

    public static DrekkiModel get(DrekkiEntity e) {
        boolean spread = e.anim() == Anim.GLIDE;
        int bard = e.stage() >= 2 ? e.barding() : 0, bag = e.stage() >= 2 ? e.bag() : 0;
        String key = e.stage() + "_" + e.line() + "_" + e.diet() + "_" + e.astral() + (e.astral2() != 0 ? "x" + e.astral2() : "") + "_" + e.horn() + (spread ? "_s" : "") + "_h" + e.hat() + "_b" + bard
            + "_d" + Integer.toHexString(e.dye()) + "_" + Integer.toHexString(e.dye2()) + "_g" + bag + "_" + Integer.toHexString(e.bagDye()) + "_" + Integer.toHexString(e.bagDye2()) + (e.saddled() ? "" : "_ns") + "_f" + (e.live() ? e.birth() : dev.frosthaven.ranchercompanion.look.FootTint.PLAINS);
        DrekkiModel m = CACHE.get(key);
        if (m == null || !m.baked.alive()) {  /* new, or its textures released by another mod: baked again */
            dev.frosthaven.ranchercompanion.Debug.log("game: {} model {}", m == null ? "baking" : "baking again (textures were released)", key);
            m = build(e.stage(), e.lineage(), Diet.of(e.diet()), e.astral() | e.astral2() << 4, e.horn(), spread, e.hat(), bard, e.dye(), e.dye2(), bag, e.bagDye(), e.bagDye2(), e.saddled(), e.live() ? e.birth() : dev.frosthaven.ranchercompanion.look.FootTint.PLAINS, key);
            CACHE.put(key, m);
        }
        return m;
    }

    public static DrekkiModel fallback() {
        DrekkiModel m = CACHE.get("fallback");
        if (m == null || !m.baked.alive()) { m = build(3, Lineage.of(0), Diet.RAW, 1, 0, false, 0, 0, -1, -1, 0, -1, -1, true, dev.frosthaven.ranchercompanion.look.FootTint.PLAINS, "fallback"); CACHE.put("fallback", m); }
        return m;
    }

    private static DrekkiModel build(int stage, Lineage line, Diet diet, int astral, int horn, boolean spread, int hat, int barding, int dye, int dye2, int bag, int bagDye, int bagDye2, boolean saddle, int birth, String key) {
        Rig rig = new Rig(new Rig.Look(stage, line.fur(), line.trim(), Palette.legTint(dev.frosthaven.ranchercompanion.look.FootTint.of(birth), line.fur()), astral, diet, horn, spread, hat, barding, dye, line.sheenHex(), line.sheen(), line.seeThrough(),
            dye2, bag, bagDye, bagDye2, saddle));
        return new DrekkiModel(RigBake.bake(rig, key));
    }
}
