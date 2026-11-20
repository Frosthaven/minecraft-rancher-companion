package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.TroughBlock;
import dev.frosthaven.ranchercompanion.block.TroughBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.CreativeEggItem;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world checks of a drekki's life (spec, Growth release): growing up with its diet and astral, hunger's catch-up
 * eating from its owner's troughs only, eating at a trough, and kinship.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class LifeTests {
    private LifeTests() {}

    static final BlockPos H = new BlockPos(6, 1, 6);

    static BlockPos stable(GameTestHelper t) {
        for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) t.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        t.setBlock(H, DrekkiBlocks.HATCHERY.get());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) t.setBlock(H.offset(dx, 4, dz), Blocks.OAK_PLANKS);
        t.setBlock(H.offset(-3, 0, -3), Blocks.TORCH);
        return t.absolutePos(H);
    }

    @SuppressWarnings("removal")
    static ServerPlayer player(GameTestHelper t) {
        ServerPlayer p = t.makeMockServerPlayerInLevel();
        p.moveTo(t.absoluteVec(H.offset(2, 0, 2).getCenter()));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);  /* items are used up, damage lands */
        return p;
    }

    static DrekkiEntity hatchOne(GameTestHelper t, ServerPlayer p, BlockPos h) {
        Ranch.putEgg(p, h, CreativeEggItem.egg(4));
        Ranch.hatch(p, h);
        List<DrekkiEntity> mine = t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(h).inflate(20), d -> p.getUUID().equals(d.owner()));
        if (mine.size() != 1) throw new net.minecraft.gametest.framework.GameTestAssertException("expected one drekki, got " + mine.size());
        return mine.get(0);
    }

    /** a trough of the owner's beside the hatchery, its open side toward it, holding food */
    static BlockPos trough(GameTestHelper t, ServerPlayer owner, ItemStack food) {
        BlockPos rel = H.offset(2, 0, 0);
        Diet diet = dev.frosthaven.ranchercompanion.FoodDiets.of(food);
        t.setBlock(rel, DrekkiBlocks.TROUGH.get().defaultBlockState().setValue(TroughBlock.FACING, Direction.WEST).setValue(TroughBlock.DIET, diet.ordinal() + 1));
        TroughBlockEntity be = (TroughBlockEntity) t.getBlockEntity(rel);
        be.claim(owner.getUUID());
        be.insert(food, false);
        return t.absolutePos(rel);
    }

    static int food(GameTestHelper t, BlockPos trough) {
        int n = 0;
        for (ItemStack s : ((TroughBlockEntity) t.getLevel().getBlockEntity(trough)).items()) n += s.getCount();
        return n;
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void growsToAFledglingWithItsDietAndAstral(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, Ranch.roster(t.getLevel().getServer()).of(p.getUUID()).get(0).id);
        d.life.meals[Diet.SEED.ordinal()] = 3;
        d.life.meals[Diet.RAW.ordinal()] = 1;
        d.life.astral[Astral.AERO] = 5000;
        d.life.travel = Life.stageTicks(7) - 30;
        t.succeedWhen(() -> {
            t.assertTrue(d.stage() == 2, "grown: stage " + d.stage() + ", travel " + d.life.travel);
            t.assertTrue(d.diet() == Diet.SEED.ordinal(), "the diet it ate most: " + Diet.of(d.diet()));
            t.assertTrue(d.astral() == Astral.AERO, "the astral it counted most: " + Astral.NAMES[d.astral()]);
            t.assertTrue(d.getMaxHealth() == 30 && d.getHealth() == 30, "fledgling health " + d.getHealth() + "/" + d.getMaxHealth());
        });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void astralLocksHalfwayThroughStageOne(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, Ranch.roster(t.getLevel().getServer()).of(p.getUUID()).get(0).id);
        d.life.astral[Astral.LUNAR] = 9000;
        d.life.travel = Life.stageTicks(7) / 2 - 30;
        t.runAfterDelay(5, () -> t.assertTrue(d.astral() == Astral.DORMANT, "still dormant before half"));
        t.succeedWhen(() -> {
            t.assertTrue(d.astral() == Astral.LUNAR, "locked: " + Astral.NAMES[d.astral()]);
            t.assertTrue(d.stage() == 1, "still a pup");
        });
    }

    /** time it was not ticking (unloaded, its owner offline) costs no food: it carries on where it left off */
    @GameTest(template = "floor")
    public static void timeAwayCostsNoFood(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        d.life.food = 6;
        d.life.stamp = Vitals.now(d) - 5L * Life.DAY;
        Vitals.update(d);
        t.assertTrue(d.life.food == 6 && d.life.stamp == Vitals.now(d), "5 days away: food " + d.life.food);
        t.succeed();
    }

    /** a plain bag holds food but never feeds the drekki: only a Sophisticated feeding upgrade does (spec, Accessory: bags) */
    @GameTest(template = "floor", timeoutTicks = 200)
    public static void aPlainBagNeverFeedsIt(GameTestHelper t) {
        ServerPlayer p = player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.ROOT, Life.Stance.PASSIVE);
        d.life.food = 5;
        d.setAccessory(new ItemStack(dev.frosthaven.ranchercompanion.item.DrekkiItems.BAGS.get(0).get()));
        d.bagStorage().insertItem(0, new ItemStack(Items.CARROT, 2), false);
        t.runAfterDelay(180, () -> { t.assertTrue(d.bagStorage().getStackInSlot(0).getCount() == 2,
            "the carrots stayed in the satchel: " + d.bagStorage().getStackInSlot(0).getCount()); t.succeed(); });
    }

    @GameTest(template = "floor", timeoutTicks = 600)
    public static void eatsAtATroughWhenHungry(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        BlockPos tr = trough(t, p, new ItemStack(Items.WHEAT, 2));
        d.life.food = 5;
        t.succeedWhen(() -> {
            t.assertTrue(food(t, tr) < 2, "it ate from the trough: " + food(t, tr) + " left");
            t.assertTrue(d.life.food > 5.5, "and its food rose: " + d.life.food);
        });
    }

    @GameTest(template = "floor")
    public static void nineKinKeepItFromMiserable(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        Roster r = Ranch.roster(t.getLevel().getServer());
        var home = Roster.at(t.getLevel(), h);
        d.life.mood = 0;
        d.syncLife();
        t.assertTrue(d.band() == Life.Band.MISERABLE, "alone at 0: " + d.band());
        for (int i = 0; i < 9; i++) r.setBase(r.add(UUID.randomUUID(), p.getUUID(), new CompoundTag(), Roster.Where.NONE, null), home);
        d.syncLife();
        t.assertTrue(d.kin() == 9 && d.band() == Life.Band.UNEASY, "nine kin: " + d.kin() + ", " + d.band() + " at " + d.mood());
        for (Roster.Entry e : r.of(p.getUUID())) r.remove(e.id);
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void aFledglingFindsItsBedding(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        for (int dx = -1; dx <= 1; dx++) t.setBlock(H.offset(dx, 0, 3), DrekkiBlocks.STRAW_BEDDING.get());
        t.runAfterDelay(110, () -> {
            int pup = d.bedding() == null ? -1 : d.bedding().size(), pen1 = d.pen().size();
            d.setLook(2, d.line(), d.diet(), d.astral(), d.horn());
            int fl = d.bedding() == null ? -1 : d.bedding().size(), pen2 = d.pen().size();
            t.assertTrue(pup == 3 && fl == 3, "bedding: pup " + pup + " (pen " + pen1 + "), fledgling " + fl + " (pen " + pen2 + ")");
            t.succeed();
        });
    }

    /** bedding laid after the pen was first read still counts once the pen refreshes (it once never refreshed) */
    @GameTest(template = "floor", timeoutTicks = 300)
    public static void beddingLaidLaterIsFound(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        t.assertTrue(d.bedding() != null && d.bedding().isEmpty(), "no bedding yet");
        t.runAfterDelay(5, () -> { for (int dx = -1; dx <= 1; dx++) t.setBlock(H.offset(dx, 0, 3), DrekkiBlocks.STRAW_BEDDING.get()); });
        t.succeedWhen(() -> t.assertTrue(d.bedding() != null && d.bedding().size() == 3, "bedding found: " + (d.bedding() == null ? -1 : d.bedding().size())));
    }
}
