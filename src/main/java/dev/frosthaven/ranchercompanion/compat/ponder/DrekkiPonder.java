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
        /* the way back from death: the same three scenes on the tombstone, the seeds and the blossom */
        helper.forComponents(DrekkiBlocks.TOMBSTONE.getId(), dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_SEEDS.getId(),
                dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.getId())
            .addStoryBoard("tombstone/rebirth", DrekkiPonder::tombGhost)
            .addStoryBoard("tombstone/rebirth", DrekkiPonder::lotus)
            .addStoryBoard("tombstone/rebirth", DrekkiPonder::reclaim);
        /* the whistle: its commands, and hiring for Create's jobs (Shane, 2026-11-26) */
        helper.forComponents(dev.frosthaven.ranchercompanion.item.DrekkiItems.WHISTLE.getId())
            .addStoryBoard("whistle/commands", DrekkiPonder::whistleCommands)
            .addStoryBoard("whistle/commands", DrekkiPonder::whistleJobs);
    }

    // ---- the way back from death (spec, Tombstone: the way back): three scenes, on the tombstone, the seeds and the blossom ----

    /* tools/ponder_scenes.py rebirth(): the tombstone at (3, 1, 3) facing west (toward the camera) on soul soil; soul soil at
       (2, 0, 3) under the lotus in front of it at (2, 1, 3) */
    /** the camera turn that brings the west-facing tombstone's face straight toward the viewer */
    private static final float TOMB_ZOOM = 1.6f;
    private static final float TOMB_TURN = -45f;  /* +45 turned it side-on (seen in game, 2026-11-26) */
    private static final int[] TOMB = {3, 1, 3}, CROP = {2, 1, 3}, SOIL = {2, 0, 3};

    private static net.createmod.ponder.api.element.ElementLink<net.createmod.ponder.api.element.EntityElement> drekki(SceneBuilder scene, boolean ghost) {
        return scene.world().createEntity(level -> {
            DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
            d.setLook(3, 2 * 4 + 2, 2, Astral.SOLAR, 1);
            d.setGhostView(ghost);
            d.moveTo(1.5, 1, 2.5, -60, 0);
            d.setYBodyRot(-60); d.setYHeadRot(-60); d.yBodyRotO = -60; d.yHeadRotO = -60;
            return d;
        });
    }

    // ---- the whistle (spec, Go here): its commands, and hiring for Create's jobs ----

    private static ItemStack whistleStack() { return new ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.WHISTLE.get()); }

    /** a Mature drekki standing at x, y, z, facing yaw */
    private static net.createmod.ponder.api.element.ElementLink<net.createmod.ponder.api.element.EntityElement> drekkiAt(SceneBuilder scene, double x, double y, double z, float yaw) {
        return scene.world().createEntity(level -> {
            DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
            d.setLook(3, 2 * 4 + 2, 2, Astral.SOLAR, 1);
            d.moveTo(x, y, z, yaw, 0);
            d.setYBodyRot(yaw); d.setYHeadRot(yaw); d.yBodyRotO = yaw; d.yHeadRotO = yaw;
            return d;
        });
    }

    /** walks it to x, z in a few steps (Ponder moves an entity by setting its place) */
    private static void walk(SceneBuilder scene, net.createmod.ponder.api.element.ElementLink<net.createmod.ponder.api.element.EntityElement> d, double x, double y, double z, int steps) {
        for (int i = 1; i <= steps; i++) {
            final int k = i;
            scene.world().modifyEntity(d, e -> {
                double fx = e.getX() + (x - e.getX()) / (steps - k + 1), fz = e.getZ() + (z - e.getZ()) / (steps - k + 1);
                float yaw = (float) Math.toDegrees(Math.atan2(-(x - e.getX()), z - e.getZ()));
                e.moveTo(fx, y, fz, yaw, 0);
                if (e instanceof DrekkiEntity de) { de.setYBodyRot(yaw); de.setYHeadRot(yaw); }
            });
            scene.idle(4);
        }
    }

    static void whistleCommands(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("whistle_commands", "Commanding your drekki");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(15);
        var d = drekkiAt(scene, 2.5, 1, 3.5, -90);
        scene.idle(10);
        scene.overlay().showControls(util.vector().of(2.5, 3.2, 3.5), Pointing.DOWN, 50).rightClick().withItem(whistleStack());
        scene.idle(10);
        scene.overlay().showText(80).text("Use the Drekki Whistle: your drekki stays where it is, or follows you again")
            .pointAt(util.vector().of(2.5, 2.5, 3.5)).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        BlockPos spot = util.grid().at(6, 0, 3);
        scene.overlay().showControls(util.vector().topOf(spot), Pointing.DOWN, 50).rightClick().whileSneaking().withItem(whistleStack());
        scene.idle(10);
        scene.overlay().showText(90).text("Sneak and use it on a block, up to 64 blocks away: it walks there, at most 15 blocks")
            .pointAt(util.vector().topOf(spot)).placeNearTarget().attachKeyFrame();
        walk(scene, d, 6.5, 1, 3.5, 8);
        scene.idle(60);
        scene.overlay().showText(80).text("There it holds the spot for 2 seconds, then follows you again, unless you tell it to stay")
            .pointAt(util.vector().of(6.5, 2.5, 3.5)).placeNearTarget();
        scene.idle(90);
        var zombie = scene.world().createEntity(level -> {
            var z = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            z.moveTo(3.5, 1, 1.5, 90, 0);
            z.setYHeadRot(90); z.setYBodyRot(90);
            return z;
        });
        scene.idle(15);
        scene.overlay().showControls(util.vector().of(3.5, 3, 1.5), Pointing.DOWN, 50).rightClick().whileSneaking().withItem(whistleStack());
        scene.idle(10);
        scene.overlay().showText(90).text("Sneak and use it on a hostile mob: your drekki attacks it, whatever its stance. A pup is too young to fight")
            .pointAt(util.vector().of(3.5, 2, 1.5)).placeNearTarget().attachKeyFrame();
        walk(scene, d, 4.6, 1, 1.5, 6);
        scene.idle(20);
        scene.world().modifyEntity(zombie, net.minecraft.world.entity.Entity::discard);
        scene.effects().emitParticles(util.vector().of(3.5, 1.5, 1.5), scene.effects().simpleParticleEmitter(net.minecraft.core.particles.ParticleTypes.POOF, util.vector().of(0, 0.05, 0)), 6, 2);
        scene.idle(70);
        scene.markAsFinished();
    }

    static void whistleJobs(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("whistle_jobs", "Hiring for Create jobs");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(10);
        BlockPos seat = util.grid().at(6, 1, 5), job = util.grid().at(6, 1, 1), ticker = util.grid().at(7, 1, 1);
        scene.world().showSection(util.select().position(seat), Direction.DOWN);
        scene.world().showSection(util.select().position(job).add(util.select().position(ticker)), Direction.DOWN);
        scene.idle(15);
        var d = drekkiAt(scene, 2.5, 1, 3.5, -90);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(seat), Pointing.DOWN, 50).rightClick().whileSneaking().withItem(whistleStack());
        scene.idle(10);
        scene.overlay().showText(80).text("Sneak and use the whistle on a Create seat: your drekki sits on it")
            .pointAt(util.vector().topOf(seat)).placeNearTarget().attachKeyFrame();
        walk(scene, d, 6.5, 1.3, 5.5, 8);
        scene.idle(70);
        scene.overlay().showControls(util.vector().topOf(job), Pointing.DOWN, 50).rightClick().whileSneaking().withItem(whistleStack());
        scene.idle(10);
        walk(scene, d, 6.5, 1.3, 1.5, 8);
        scene.overlay().showText(100).text("On a train's seat, or a seat beside a Stock Ticker, it is hired: a Conductor or a Stock keeper")
            .pointAt(util.vector().topOf(job)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(110).text("At work it stays on while you are away and keeps Passive. When hungry it eats from its bag, then from its hatchery's troughs. You can check out another drekki meanwhile")
            .pointAt(util.vector().of(6.5, 2.6, 1.5)).placeNearTarget();
        scene.idle(120);
        scene.overlay().showText(100).text("Recall it from its screen, or check it in at its hatchery: it follows you again")
            .pointAt(util.vector().of(6.5, 2.6, 1.5)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.markAsFinished();
    }

    static void tombGhost(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("tomb_ghost", "A fallen drekki's tombstone");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.rotateCameraY(TOMB_TURN);  /* the tombstone's face toward the viewer (Shane, 2026-11-26) */
        scene.scaleSceneView(TOMB_ZOOM);  /* closer in: the tombstone is one small block on the plate */
        scene.idle(25);  /* the turn and zoom play out first: text pinned to the tombstone would slide while the view turns */
        scene.idle(10);
        BlockPos tomb = util.grid().at(TOMB[0], TOMB[1], TOMB[2]);
        scene.world().showSection(util.select().position(tomb), Direction.DOWN);
        scene.idle(15);
        scene.idle(20);  /* the Ponder screen finishes opening first */
        scene.overlay().showText(80).text("When a drekki dies, it leaves a tombstone with its gear, and the tombstone keeps the drekki")
            .pointAt(util.vector().topOf(tomb)).attachKeyFrame();  /* not placeNearTarget: it flipped side to side while the screen opened */
        scene.idle(90);
        scene.overlay().showText(70).text("Anyone can take the gear, but only its owner can bring the drekki back")
            .pointAt(util.vector().topOf(tomb)).placeNearTarget();
        scene.idle(80);
        scene.overlay().showText(70).text("Mined, the tombstone keeps its drekki: placed again, it can still bring it back")
            .pointAt(util.vector().topOf(tomb)).placeNearTarget();
        scene.idle(80);
        scene.overlay().showOutlineWithText(util.select().position(tomb.below()), 80).colored(PonderPalette.BLUE)
            .text("Its way back works only while it stands on soul soil. Anywhere else it is a decoration").placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.markAsFinished();
    }

    static void lotus(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("lotus", "Growing the Drekki Lotus");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.rotateCameraY(TOMB_TURN);  /* the tombstone's face toward the viewer (Shane, 2026-11-26) */
        scene.scaleSceneView(TOMB_ZOOM);  /* closer in: the tombstone is one small block on the plate */
        scene.idle(25);  /* the turn and zoom play out first: text pinned to the tombstone would slide while the view turns */
        scene.idle(10);
        BlockPos tomb = util.grid().at(TOMB[0], TOMB[1], TOMB[2]), crop = util.grid().at(CROP[0], CROP[1], CROP[2]), soil = util.grid().at(SOIL[0], SOIL[1], SOIL[2]);
        scene.world().showSection(util.select().position(tomb), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutlineWithText(util.select().position(soil), 80).colored(PonderPalette.BLUE)
            .text("Drekki Lotus Seeds, sold by the wandering trader, plant only on soul soil").placeNearTarget().attachKeyFrame();
        scene.idle(30);
        scene.world().showSection(util.select().position(crop), Direction.DOWN);
        scene.idle(60);
        scene.overlay().showText(80).text("It grows only in front of a drekki tombstone or behind it, not at its sides")
            .pointAt(util.vector().centerOf(crop)).placeNearTarget().attachKeyFrame();
        for (int age = 1; age <= 3; age++) {
            scene.idle(25);
            final int a = age;
            scene.world().modifyBlock(crop, st -> st.setValue(dev.frosthaven.ranchercompanion.block.LotusCropBlock.AGE, a), false);
        }
        scene.idle(20);
        scene.overlay().showText(70).text("Grown, it gives a Drekki Lotus Blossom").pointAt(util.vector().centerOf(crop)).placeNearTarget().attachKeyFrame();
        scene.idle(40);
        scene.world().destroyBlock(crop);
        scene.world().createItemEntity(util.vector().centerOf(crop), util.vector().of(0, 0.2, 0), new ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get()));
        scene.idle(50);
        scene.markAsFinished();
    }

    static void reclaim(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("reclaim", "Summoning a fallen drekki");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.rotateCameraY(TOMB_TURN);  /* the tombstone's face toward the viewer (Shane, 2026-11-26) */
        scene.scaleSceneView(TOMB_ZOOM);  /* closer in: the tombstone is one small block on the plate */
        scene.idle(25);  /* the turn and zoom play out first: text pinned to the tombstone would slide while the view turns */
        scene.idle(10);
        BlockPos tomb = util.grid().at(TOMB[0], TOMB[1], TOMB[2]);
        scene.world().showSection(util.select().position(tomb), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(tomb), Pointing.DOWN, 50).rightClick()
            .withItem(new ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get()));
        scene.idle(10);
        scene.world().modifyBlockEntityNBT(util.select().position(tomb), dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity.class,
            nbt -> nbt.merge(items(new ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get()))));  /* it lies on the tombstone's foot */
        var ghost = drekki(scene, true);
        scene.overlay().showText(90).text("A blossom in one of the tombstone's slots raises its ghost. Its owner lights the candle with flint and steel")
            .pointAt(util.vector().topOf(tomb)).placeNearTarget().attachKeyFrame();
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(tomb), Pointing.DOWN, 40).rightClick()
            .withItem(new ItemStack(net.minecraft.world.item.Items.FLINT_AND_STEEL));
        scene.idle(10);
        scene.world().modifyBlock(tomb, st -> st.setValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CANDLE, dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.LIT), false);
        var cyan = net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, 0xFF40E0E8);
        scene.effects().emitParticles(util.vector().of(3.5, 1.1, 3.5), scene.effects().simpleParticleEmitter(cyan, util.vector().of(0, 0.02, 0)), 2, 90);
        scene.idle(50);
        scene.overlay().showOutlineWithText(util.select().fromTo(0, 1, 0, 6, 1, 6), 80).colored(PonderPalette.WHITE)
            .text("Mature ghosts may be able to sense kin at a nearby hatchery.")
            .placeNearTarget();  /* a hint, no more (Shane, 2026-11-26) */
        scene.idle(90);
        scene.overlay().showText(70).text("The candle burns for one day. Taking the blossom out or putting the candle out resets the process: it can be done again")
            .pointAt(util.vector().topOf(tomb)).placeNearTarget();
        scene.idle(80);
        scene.world().modifyBlock(tomb, st -> st.setValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CANDLE, dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.MELTED), false);
        scene.overlay().showText(60).text("The day done, the candle has melted down").pointAt(util.vector().topOf(tomb)).placeNearTarget().attachKeyFrame();
        scene.idle(70);
        scene.world().modifyEntity(ghost, net.minecraft.world.entity.Entity::discard);
        scene.world().modifyBlockEntityNBT(util.select().position(tomb), dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity.class,
            nbt -> nbt.put("Items", new ListTag()));  /* the blossom is used up */
        drekki(scene, false);
        scene.effects().emitParticles(util.vector().of(1.5, 1.5, 2.5), scene.effects().simpleParticleEmitter(net.minecraft.core.particles.ParticleTypes.END_ROD, util.vector().of(0, 0.05, 0)), 6, 3);
        scene.overlay().showText(90).text("Then its owner presses Summon fallen drekki: it is alive again, at their side. The blossom is used up")
            .pointAt(util.vector().of(1.5, 2.2, 2.5)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80).text("Back from death, it carries Resurrection Fatigue for a day: its mood is two bands lower")
            .pointAt(util.vector().of(1.5, 2.2, 2.5)).placeNearTarget();
        scene.idle(90);
        /* used: the tombstone cracks and stays as decoration (Shane, 2026-11-26) */
        scene.world().modifyBlock(tomb, st -> st.setValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CRACKED, true)
            .setValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CANDLE, dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.UNLIT), false);
        scene.overlay().showText(90).text("Its work done, the tombstone cracks. It stays as decoration: a low light with a short message of your own")
            .pointAt(util.vector().topOf(tomb)).placeNearTarget().attachKeyFrame();
        scene.idle(50);
        scene.overlay().showControls(util.vector().topOf(tomb), Pointing.DOWN, 40).rightClick()
            .withItem(new ItemStack(net.minecraft.world.item.Items.FLINT_AND_STEEL));
        scene.idle(10);
        scene.world().modifyBlock(tomb, st -> st.setValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CANDLE, dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.LIT), false);
        scene.idle(60);
        scene.markAsFinished();
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
        /* tools/ponder_scenes.py stable(): the design page's decor booth, open toward the viewer (front z 3, fence side x 3) */
        BlockPos hatch = util.grid().at(6, 1, 6);
        scene.world().showSection(util.select().position(hatch), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70).text("The Drekki Hatchery is where an egg hatches, and where your drekki live when they are not with you")
            .pointAt(util.vector().topOf(hatch)).placeNearTarget().attachKeyFrame();
        scene.idle(80);

        Selection frame = util.select().fromTo(3, 1, 3, 7, 3, 7).substract(util.select().fromTo(4, 1, 4, 6, 3, 6));
        scene.world().showSection(frame, Direction.DOWN);
        scene.idle(10);
        Selection roof = util.select().fromTo(3, 4, 3, 7, 4, 7);
        scene.world().showSection(roof, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutlineWithText(util.select().fromTo(5, 4, 5, 7, 4, 7), 90).colored(PonderPalette.GREEN)
            .text("An egg hatches only under a roof: every block of a 3x3 over the hatchery covered, 3 to 10 blocks up").placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showOutlineWithText(util.select().fromTo(6, 2, 6, 6, 3, 6), 70).colored(PonderPalette.WHITE)
            .text("The two blocks right above the hatchery must stay clear").placeNearTarget();
        scene.idle(80);
        scene.overlay().showText(70).text("The 3x3 need not be centered: a hatchery in a corner of the roof works too")
            .pointAt(util.vector().topOf(6, 4, 6)).placeNearTarget();
        scene.idle(80);
        BlockPos lamp = util.grid().at(5, 3, 4);
        scene.world().showSection(util.select().position(lamp), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(60).text("It also needs some light around it").pointAt(util.vector().centerOf(lamp)).placeNearTarget();
        scene.idle(70);
        /* the roof, the lantern under it and the corner posts that hold it (above the fence's height) lift away for
           the rest of the scene, so the inside shows from Ponder's camera */
        Selection lid = util.select().fromTo(3, 4, 3, 7, 4, 7).add(util.select().position(lamp));
        for (int x : new int[]{3, 7}) for (int z : new int[]{3, 7}) lid = lid.add(util.select().fromTo(x, 2, z, x, 3, z));
        scene.world().hideSection(lid, Direction.UP);
        scene.idle(20);

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
        pup(scene, 5.5, 1, 4.6, 180, 1, 4);
        scene.idle(20);
        scene.overlay().showText(70).text("The pup appears right next to the hatchery")
            .pointAt(util.vector().of(5.5, 1.3, 4.6)).placeNearTarget();
        scene.idle(80);

        /* one outline at a time, around the anchor the text is about and 3 blocks out from it (the plate's edge
           clips it): the hatchery, then the bedding, then the path */
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "anchor", new AABB(3, 1, 3, 10, 1.1, 10), 100);
        scene.overlay().showText(90).text("Drekki that live here wander within 3 blocks of an anchor. The hatchery is the first anchor")
            .pointAt(util.vector().of(9.9, 1, 8.5)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        Selection inside = util.select().fromTo(4, 1, 4, 6, 1, 5).add(util.select().position(5, 1, 6)).add(util.select().position(4, 1, 6));
        scene.world().showSection(inside, Direction.DOWN);
        scene.idle(10);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "anchor", new AABB(1, 1, 1, 10, 1.1, 10), 100);  /* bedding x 4..6, z 4..6 */
        scene.overlay().showText(90).text("Straw Bedding is an anchor too: the area grows 3 blocks around it. Drekki like to rest on it, and sleep there at night")
            .pointAt(util.vector().topOf(5, 0, 4)).placeNearTarget();
        scene.idle(100);
        Selection path = util.select().fromTo(0, 0, 1, 4, 0, 1).add(util.select().fromTo(5, 0, 0, 5, 0, 2));
        scene.world().modifyBlocks(path, st -> net.minecraft.world.level.block.Blocks.DIRT_PATH.defaultBlockState(), false);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "anchor", new AABB(0, 1, 0, 9, 1.1, 6), 110);  /* path x 0..5, z 0..2 */
        scene.overlay().showText(100).text("So is a dirt path. Anchors chain: from the bedding a drekki may follow the path, 3 blocks either side, up to 50 blocks from the hatchery")
            .pointAt(util.vector().topOf(2, 0, 1)).placeNearTarget().attachKeyFrame();
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
        /* each trough stands alone until the next one joins it (the schematic holds the finished line) */
        scene.world().modifyBlocks(util.select().fromTo(1, 1, 3, 10, 1, 3), st -> st.setValue(TroughBlock.LEFT, false).setValue(TroughBlock.RIGHT, false), false);
        scene.idle(10);
        scene.world().showSection(util.select().position(1, 1, 3), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).text("The Drekki Trough holds food for drekki. Its low open side faces you when you place it; sneak to place it facing away")
            .pointAt(util.vector().topOf(1, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(80);
        for (int x = 2; x <= 10; x++) {
            scene.world().showSection(util.select().position(x, 1, 3), Direction.DOWN);
            scene.world().modifyBlock(util.select().position(x - 1, 1, 3).iterator().next(), st -> st.setValue(TroughBlock.LEFT, true), false);
            scene.world().modifyBlock(util.select().position(x, 1, 3).iterator().next(), st -> st.setValue(TroughBlock.RIGHT, true), false);
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
        scene.overlay().showText(110).text("A pup has no diet yet: what it eats most decides it. It eats from its troughs at random, so to steer it, give it troughs of only the diet you want")
            .pointAt(util.vector().topOf(1, 1, 2)).placeNearTarget().attachKeyFrame();
        scene.idle(120);
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
            .text("Each trough has a spot in front of it, one drekki at a time, so a line feeds many at once as long as each has room to stand")
            .placeNearTarget();
        scene.idle(100);
        scene.world().showSection(util.select().position(5, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showOutlineWithText(util.select().position(5, 1, 3), 80).colored(PonderPalette.RED)
            .text("A blocked open side feeds no pup or fledgling there: keep the spot in front clear").placeNearTarget().attachKeyFrame();
        scene.idle(90);
        pup(scene, 4.5, 1, 4.9, 180, 3, 8);
        pup(scene, 7.6, 1, 3.5, 90, 3, 20);
        scene.idle(15);
        scene.overlay().showText(100).text("Mature drekki eat over the wall: a spot behind each trough, and one at each end of a line. A lone trough feeds up to four")
            .pointAt(util.vector().of(4.5, 2.6, 4.9)).placeNearTarget().attachKeyFrame();
        scene.idle(110);
        scene.overlay().showText(90).text("A meal is one item. Then the drekki steps away and waits a moment, so another drekki gets the spot")
            .pointAt(util.vector().topOf(3, 1, 3)).placeNearTarget();
        scene.idle(100);
        scene.overlay().showText(90).text("Drekki eat only from their owner's troughs: the player who placed a trough owns it")
            .pointAt(util.vector().topOf(2, 1, 3)).placeNearTarget().attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(90).text("Drekki that live at a hatchery use the troughs inside their wander area, so place troughs along your paths and bedding")
            .pointAt(util.vector().topOf(6, 1, 3)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }
}
