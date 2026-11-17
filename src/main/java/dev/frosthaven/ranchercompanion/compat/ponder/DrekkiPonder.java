package dev.frosthaven.ranchercompanion.compat.ponder;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.block.TroughBlock;
import dev.frosthaven.ranchercompanion.block.TroughBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Astral;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Ponder build guides (optional: registered only when Ponder is loaded, from the client): a stable around the
 * hatchery and its wander area, and the trough's lines, diets and feeding. The schematics are tools/ponder_scenes.py's;
 * the scene texts are mirrored into en_us.json by the same tool (Ponder reads them from the lang file).
 */
public final class DrekkiPonder implements PonderPlugin {
    public static void register() { PonderIndex.addPlugin(new DrekkiPonder()); }

    /** for the self test: opens a block's Ponder scenes */
    public static void open(ResourceLocation block) {
        net.createmod.catnip.gui.ScreenOpener.open(net.createmod.ponder.foundation.ui.PonderUI.of(block));
    }

    /** for the self test: the open Ponder screen's next scene (PonderUI.scroll is not public) */
    public static void next() {
        try {
            var m = net.createmod.ponder.foundation.ui.PonderUI.class.getDeclaredMethod("scroll", boolean.class);
            m.setAccessible(true);
            m.invoke(net.minecraft.client.Minecraft.getInstance().screen, true);
        } catch (ReflectiveOperationException e) { RancherCompanion.LOG.warn("[selftest] no next ponder scene", e); }
    }

    @Override
    public String getModId() { return RancherCompanion.ID; }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(DrekkiBlocks.HATCHERY.getId()).addStoryBoard("hatchery/stable", DrekkiPonder::stable);
        helper.forComponents(DrekkiBlocks.TROUGH.getId())
            .addStoryBoard("trough/line", DrekkiPonder::line)
            .addStoryBoard("trough/diets", DrekkiPonder::diets)
            .addStoryBoard("trough/feeding", DrekkiPonder::feeding);
    }

    /** a stage 1 (or bigger) drekki standing at a spot, facing a yaw */
    private static void pup(SceneBuilder scene, double x, double y, double z, float yaw, int stage, int line) {
        scene.world().createEntity(level -> {
            DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
            d.setLook(stage, line, 2, Astral.DORMANT, 1);
            d.moveTo(x, y, z, yaw, 0);
            d.setYBodyRot(yaw); d.setYHeadRot(yaw); d.yBodyRotO = yaw; d.yHeadRotO = yaw;
            return d;
        });
    }

    private static CompoundTag items(ItemStack... stacks) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            CompoundTag s = new CompoundTag();
            s.putByte("Slot", (byte) i);
            s.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stacks[i].getItem()).toString());
            s.putInt("count", stacks[i].getCount());
            list.add(s);
        }
        tag.put("Items", list);
        return tag;
    }

    // ---- the stable ----

    static void stable(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("stable", "Building a stable for your drekki");
        scene.configureBasePlate(0, 0, 11);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos hatch = util.grid().at(5, 1, 5);
        scene.world().showSection(util.select().position(hatch), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70).text("The Drekki Hatchery is where an egg hatches, and where your drekki live when they are not with you")
            .pointAt(util.vector().topOf(hatch)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        Selection posts = util.select().fromTo(4, 1, 4, 4, 4, 4).add(util.select().fromTo(6, 1, 4, 6, 4, 4))
            .add(util.select().fromTo(4, 1, 6, 4, 4, 6)).add(util.select().fromTo(6, 1, 6, 6, 4, 6));
        scene.world().showSection(posts, Direction.DOWN);
        scene.idle(10);
        Selection roof = util.select().fromTo(4, 5, 4, 6, 5, 6);
        scene.world().showSection(roof, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutlineWithText(roof, 90).colored(PonderPalette.GREEN)
            .text("An egg hatches only under a roof: every block of a 3x3 over the hatchery covered, 3 to 10 blocks up").placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showOutlineWithText(util.select().fromTo(5, 2, 5, 5, 3, 5), 70).colored(PonderPalette.WHITE)
            .text("The two blocks right above the hatchery must stay clear").placeNearTarget();
        scene.idle(80);
        scene.overlay().showText(70).text("The 3x3 need not be centered: a hatchery in a corner of the roof works too")
            .pointAt(util.vector().topOf(5, 5, 5)).placeNearTarget();
        scene.idle(80);
        BlockPos lamp = util.grid().at(3, 1, 3);
        scene.world().showSection(util.select().position(lamp), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60).text("It also needs some light around it").pointAt(util.vector().centerOf(lamp)).placeNearTarget();
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(hatch), Pointing.DOWN, 50).rightClick()
            .withItem(new ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.INTRIGUING_EGG.get()));
        scene.idle(10);
        scene.world().modifyBlockEntityNBT(util.select().position(hatch), HatcheryBlockEntity.class, nbt -> {
            CompoundTag egg = new CompoundTag();
            egg.putString("id", RancherCompanion.ID + ":intriguing_egg");
            egg.putInt("count", 1);
            nbt.put("Egg", egg);
        });
        scene.idle(10);
        scene.overlay().showText(80).text("Put an egg in the nest. After three days it is ready: right-click the nest to hatch it")
            .pointAt(util.vector().topOf(hatch)).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.world().modifyBlockEntityNBT(util.select().position(hatch), HatcheryBlockEntity.class, nbt -> nbt.remove("Egg"));
        scene.effects().emitParticles(util.vector().topOf(hatch), scene.effects().simpleParticleEmitter(net.minecraft.core.particles.ParticleTypes.POOF, util.vector().of(0, 0.05, 0)), 6, 2);
        pup(scene, 5.5, 1, 6.6, 180, 1, 4);
        scene.idle(20);
        scene.overlay().showText(70).text("The pup appears right next to the hatchery")
            .pointAt(util.vector().of(5.5, 1.3, 6.6)).placeNearTarget();
        scene.idle(80);

        AABB around = new AABB(2, 1, 2, 9, 1.1, 9);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "pen", around, 90);
        scene.overlay().showText(90).text("Drekki that live here wander within 3 blocks of an anchor. The hatchery is the first anchor")
            .pointAt(util.vector().of(8.5, 1, 5.5)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        Selection bedding = util.select().fromTo(5, 1, 8, 6, 1, 8);
        scene.world().showSection(bedding, Direction.DOWN);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "pen", new AABB(2, 1, 2, 10, 1.1, 11), 90);
        scene.overlay().showText(90).text("Straw Bedding is an anchor too: the area grows 3 blocks around it. Drekki like to rest on it, and sleep there at night")
            .pointAt(util.vector().topOf(5, 0, 8)).placeNearTarget();
        scene.idle(100);
        Selection path = util.select().fromTo(7, 0, 9, 10, 0, 9);
        scene.world().modifyBlocks(path, s -> net.minecraft.world.level.block.Blocks.DIRT_PATH.defaultBlockState(), false);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "pen", new AABB(0, 1, 2, 11, 1.1, 11), 100);
        scene.overlay().showText(100).text("So is a dirt path. Anchors chain: from the bedding a drekki may follow the path, 3 blocks either side, up to 25 blocks from the hatchery")
            .pointAt(util.vector().topOf(9, 0, 9)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(80).text("Up to 20 drekki live at one hatchery. Use its screen to check one out, check one in, or release one")
            .pointAt(util.vector().topOf(hatch)).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ---- the trough ----

    static void line(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("trough_line", "Building a line of troughs");
        scene.configureBasePlate(0, 0, 13);
        scene.scaleSceneView(0.75f);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).text("The Drekki Trough holds food for drekki. Its low open side faces you when you place it; sneak to place it facing away")
            .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(80);
        for (int x = 2; x <= 10; x++) {
            scene.world().showSection(util.select().position(x, 1, 3), Direction.DOWN);
            scene.idle(4);
        }
        scene.idle(10);
        scene.overlay().showOutlineWithText(util.select().fromTo(1, 1, 3, 10, 1, 3), 90).colored(PonderPalette.GREEN)
            .text("Troughs side by side that face the same way join into one straight line, up to 10 long").placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.world().showSection(util.select().position(11, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutlineWithText(util.select().position(11, 1, 3), 70).colored(PonderPalette.RED)
            .text("An 11th stays a trough of its own, and so does one placed around a corner").placeNearTarget();
        scene.idle(80);
        scene.world().modifyBlockEntityNBT(util.select().position(4, 1, 3), TroughBlockEntity.class, nbt -> nbt.merge(items(new ItemStack(Items.CARROT, 16))));
        scene.world().modifyBlocks(util.select().fromTo(1, 1, 3, 10, 1, 3), s -> s.setValue(TroughBlock.DIET, 4), false);
        scene.overlay().showControls(util.vector().topOf(4, 1, 3), Pointing.DOWN, 40).rightClick().withItem(new ItemStack(Items.CARROT));
        scene.idle(10);
        scene.overlay().showText(90).text("A line acts as one trough: food anywhere in it feeds a drekki at any part, and every plaque shows its diet")
            .pointAt(util.vector().topOf(8, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(70).text("Right-click with food to put it in, or with an empty hand to open the trough")
            .pointAt(util.vector().topOf(4, 1, 3)).placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    static void diets(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("trough_diets", "The four diets");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        ItemStack[] food = {new ItemStack(Items.PORKCHOP, 8), new ItemStack(Items.COOKED_CHICKEN, 8), new ItemStack(Items.WHEAT_SEEDS, 8), new ItemStack(Items.CARROT, 8)};
        for (int i = 0; i < 4; i++) {
            int x = 1 + i * 2, k = i;
            scene.world().showSection(util.select().position(x, 1, 2), Direction.DOWN);
            scene.world().modifyBlockEntityNBT(util.select().position(x, 1, 2), TroughBlockEntity.class, nbt -> nbt.merge(items(food[k])));
            scene.idle(8);
        }
        scene.overlay().showText(80).text("Every trough, or line, holds one diet. The first food put in sets it, and food of another diet is refused")
            .pointAt(util.vector().topOf(4, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(55).text("Raw: raw meat and fish").pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget();
        scene.idle(60);
        scene.overlay().showText(55).text("Cooked: cooked meat and fish").pointAt(util.vector().topOf(3, 1, 2)).placeNearTarget();
        scene.idle(60);
        scene.overlay().showText(55).text("Seed: seeds, and crops grown from a seed").pointAt(util.vector().topOf(5, 1, 2)).placeNearTarget();
        scene.idle(60);
        scene.overlay().showText(55).text("Root: roots, berries, and crops planted as themselves").pointAt(util.vector().topOf(7, 1, 2)).placeNearTarget();
        scene.idle(60);
        scene.overlay().showText(90).text("A pup has no diet yet: what it eats most decides it. Fill the troughs nearest its hatchery with the diet you want")
            .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(70).text("Once it is a fledgling its diet is set, and it eats only from troughs holding that diet")
            .pointAt(util.vector().topOf(5, 1, 2)).placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    static void feeding(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("trough_feeding", "How drekki eat from a trough");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 3, 6, 1, 3), Direction.DOWN);
        scene.idle(15);
        for (int x : new int[]{2, 3, 4, 6}) pup(scene, x + 0.5, 1, 2.3, 0, 1, (x * 4) % 36);
        scene.idle(15);
        scene.overlay().showText(90).text("Pups and fledglings eat through the low open side, standing in front of it")
            .pointAt(util.vector().of(3.5, 1.4, 2.3)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showOutlineWithText(util.select().fromTo(2, 1, 3, 6, 1, 3), 90).colored(PonderPalette.GREEN)
            .text("Each trough in a line feeds one drekki at a time, so many can eat at once: a line of 10 feeds 10, as long as each has room to stand")
            .placeNearTarget();
        scene.idle(100);
        scene.world().showSection(util.select().position(5, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutlineWithText(util.select().position(5, 1, 3), 80).colored(PonderPalette.RED)
            .text("A blocked open side feeds no pup or fledgling there: keep the spot in front clear").placeNearTarget().attachKeyFrame();
        scene.idle(90);
        pup(scene, 4.5, 1, 4.9, 180, 3, 8);
        scene.idle(15);
        scene.overlay().showText(90).text("Mature drekki are tall enough to eat from the top, from any side that is not blocked")
            .pointAt(util.vector().of(4.5, 2.6, 4.9)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90).text("A meal is one item. After it a drekki waits a moment before it eats again, so the others get a turn")
            .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget();
        scene.idle(100);
        scene.overlay().showText(90).text("Drekki that live at a hatchery use the troughs inside their wander area, so place troughs along your paths and bedding")
            .pointAt(util.vector().topOf(6, 1, 3)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }
}
