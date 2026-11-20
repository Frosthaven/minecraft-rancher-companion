package dev.frosthaven.ranchercompanion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frosthaven.ranchercompanion.client.rig.Coplanar;
import dev.frosthaven.ranchercompanion.client.rig.Node;
import dev.frosthaven.ranchercompanion.client.rig.Rig;
import dev.frosthaven.ranchercompanion.look.Diet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Faces that share a plane z-fight: every clashing pair of cubes must get different growth steps (RigBake applies them). */
class CoplanarTest {
    private static void check(Rig r, String look, List<String> bad) {
        Map<Node.Box, Integer> step = Coplanar.grow(r);
        for (Node.Box[] p : Coplanar.clashes(r))
            if (step.get(p[0]).equals(step.get(p[1]))) bad.add(look + String.format(": colors %06x / %06x both step %d", p[0].color(), p[1].color(), step.get(p[0])));
    }

    @Test
    void clashingCubesNeverShareAGrowth() {
        List<String> bad = new ArrayList<>();
        int pairs = 0;
        for (Diet d : Diet.values()) for (int horn = 0; horn < 5; horn++) {
            for (int hat = 0; hat < 16; hat++) {  /* a hatchling in every hat */
                Rig r = new Rig(new Rig.Look(1, 0x7d8a5c, -1, 0x8a9a6a, 0, d, horn, false, hat, 0, -1, 0x7d8a5c, 0f, false));
                if (hat == 0) pairs += Coplanar.clashes(r).size();
                check(r, "stage 1 " + d + " horn " + horn + " hat " + hat, bad);
            }
            for (int stage = 2; stage <= 3; stage++) for (int barding : new int[]{0, 4}) for (int bag = 0; bag <= 6; bag++) {
                if (bag > 0 && dev.frosthaven.ranchercompanion.look.Gear.BAG_STAGE[bag] > stage) continue;  /* a saddlebag from stage 3 */
                Rig r = new Rig(new Rig.Look(stage, 0x7d8a5c, -1, 0x8a9a6a, 1, d, horn, false, 0, barding, -1, 0x7d8a5c, 0f, false, -1, bag, -1, -1));
                check(r, "stage " + stage + " " + d + " horn " + horn + " barding " + barding + " bag " + bag, bad);
            }
        }
        System.out.println("stage 1 clashing pairs the growth separates (all diets and horns, no hat): " + pairs);
        assertTrue(bad.isEmpty(), bad.size() + " clashes left, first: " + (bad.isEmpty() ? "" : bad.get(0)));
    }
}
