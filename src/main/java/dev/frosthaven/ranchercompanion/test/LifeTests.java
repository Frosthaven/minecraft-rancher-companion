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
        TroughBlock.lineInsert(t.getLevel(), t.absolutePos(rel), food, false);
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

    /** a food from the hand heals 8, on a full bar too, and a full unhurt drekki takes none (spec, Hunger) */
    @GameTest(template = "floor")
    public static void aFoodFromTheHandHealsEvenOnAFullBar(GameTestHelper t) {
        ServerPlayer p = player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.ROOT, Life.Stance.PASSIVE);
        d.setHealth(10);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT, 2));
        d.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
        t.assertTrue(d.getHealth() == 18 && p.getMainHandItem().getCount() == 1, "hurt and full: health " + d.getHealth() + ", carrots " + p.getMainHandItem().getCount());
        d.setHealth(d.getMaxHealth());
        d.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND);
        t.assertTrue(p.getMainHandItem().getCount() == 1, "full and unhurt: it took a carrot");
        t.succeed();
    }

    /** two joined troughs of the owner's, empty; returns the first (the second is the next one along the line) */
    private static BlockPos[] troughLine(GameTestHelper t, ServerPlayer owner) {
        BlockPos a = H.offset(2, 0, 0), b = a.relative(Direction.WEST.getClockWise());
        var st = DrekkiBlocks.TROUGH.get().defaultBlockState().setValue(TroughBlock.FACING, Direction.WEST);
        t.setBlock(a, st.setValue(TroughBlock.LEFT, true));
        t.setBlock(b, st.setValue(TroughBlock.RIGHT, true));
        for (BlockPos p : new BlockPos[]{a, b}) ((TroughBlockEntity) t.getBlockEntity(p)).claim(owner.getUUID());
        return new BlockPos[]{t.absolutePos(a), t.absolutePos(b)};
    }

    private static ItemStack slot(GameTestHelper t, BlockPos trough) { return ((TroughBlockEntity) t.getLevel().getBlockEntity(trough)).items().get(0); }

    /** a pipe sees a line as one inventory, a slot per trough: it fills from any trough, and food of another diet, a
     *  thing that is no food and food beyond the room are refused, so they wait in the pipe (spec, Drekki Trough) */
    @GameTest(template = "floor")
    public static void aPipeFillsATroughLineAndIsRefusedTheRest(GameTestHelper t) {
        BlockPos[] line = troughLine(t, player(t));
        var cap = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        var first = t.getLevel().getCapability(cap, line[0], Direction.DOWN);
        var second = t.getLevel().getCapability(cap, line[1], Direction.UP);
        t.assertTrue(first != null && second != null && first.getSlots() == 2 && second.getSlots() == 2, "both troughs give the line's two slots");
        var put = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(first, new ItemStack(Items.CARROT, 64), false);
        put = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(second, new ItemStack(Items.CARROT, 36), false).getCount() == 0 ? put : new ItemStack(Items.STICK);
        t.assertTrue(put.isEmpty() && slot(t, line[0]).getCount() + slot(t, line[1]).getCount() == 100, "100 carrots went in: " + slot(t, line[0]) + " " + slot(t, line[1]));
        t.assertTrue(t.getLevel().getBlockState(line[1]).getValue(TroughBlock.DIET) == Diet.ROOT.ordinal() + 1, "the line took the carrots' diet");
        t.assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(first, new ItemStack(Items.COOKED_BEEF, 3), false).getCount() == 3, "food of another diet is refused");
        t.assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(first, new ItemStack(Items.STICK, 3), false).getCount() == 3, "a stick is refused");
        t.assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(second, new ItemStack(Items.CARROT, 64), false).getCount() == 36, "only 28 more carrots fit");
        t.assertTrue(first.extractItem(0, 10, true).getCount() == 10 && slot(t, line[0]).getCount() + slot(t, line[1]).getCount() == 128, "a simulated pull takes nothing");
        ItemStack pulled = second.extractItem(0, 64, false);
        pulled.grow(first.extractItem(1, 64, false).getCount());
        t.assertTrue(pulled.is(Items.CARROT) && pulled.getCount() == 128 && slot(t, line[0]).isEmpty() && slot(t, line[1]).isEmpty(), "a pipe pulls the food out of both slots: " + pulled);
        t.assertTrue(t.getLevel().getBlockState(line[0]).getValue(TroughBlock.DIET) == 0, "the empty line has no diet again");
        t.assertTrue(net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(first, new ItemStack(Items.COOKED_BEEF, 3), false).isEmpty(), "and now takes beef");
        t.succeed();
    }

    /** a trough saved with four stacks (before 0.4.9) loses nothing: one stays, the rest go into the line, and what
     *  does not fit drops on it */
    @GameTest(template = "floor")
    public static void anOldTroughKeepsItsFourStacks(GameTestHelper t) {
        BlockPos[] line = troughLine(t, player(t));
        var old = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
        old.set(0, new ItemStack(Items.CARROT, 10)); old.set(1, new ItemStack(Items.POTATO, 5)); old.set(2, new ItemStack(Items.BEETROOT, 3)); old.set(3, new ItemStack(Items.CARROT, 7));
        var tag = net.minecraft.world.ContainerHelper.saveAllItems(new net.minecraft.nbt.CompoundTag(), old, t.getLevel().registryAccess());
        t.getLevel().getBlockEntity(line[0]).loadWithComponents(tag, t.getLevel().registryAccess());
        t.runAfterDelay(5, () -> {
            t.assertTrue(slot(t, line[0]).is(Items.CARROT) && slot(t, line[0]).getCount() == 17, "the carrots are one stack: " + slot(t, line[0]));
            t.assertTrue(slot(t, line[1]).is(Items.POTATO) && slot(t, line[1]).getCount() == 5, "the potatoes moved along the line: " + slot(t, line[1]));
            var dropped = t.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(line[0]).inflate(3), e -> e.getItem().is(Items.BEETROOT));
            t.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 3, "the beetroot dropped: " + dropped.size());
            t.succeed();
        });
    }

    /** a drekki whose diet is set never eats from a trough of another diet (spec, Feeding at troughs) */
    @GameTest(template = "floor", timeoutTicks = 500)
    public static void aDrekkiLeavesATroughOfAnotherDiet(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity d = hatchOne(t, p, h);
        d.setLook(2, d.line(), Diet.ROOT.ordinal(), d.astral(), d.horn());
        BlockPos tr = trough(t, p, new ItemStack(Items.COOKED_BEEF, 2));
        d.life.food = 3;
        t.runAfterDelay(450, () -> { t.assertTrue(food(t, tr) == 2 && d.life.food < 3.5, "the beef is untouched: " + food(t, tr) + ", its food " + d.life.food); t.succeed(); });
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

    /** at night two drekki lie on two bedding blocks, each on its own block, not beside them (Shane, 2026-11-27) */
    @GameTest(template = "floor", timeoutTicks = 600, batch = "beds_by_night")
    public static void twoDrekkiEachLieOnTheirOwnBedding(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        DrekkiEntity a = hatchOne(t, p, h);
        Ranch.putEgg(p, h, CreativeEggItem.egg(4));
        Ranch.hatch(p, h);
        List<DrekkiEntity> mine = t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(h).inflate(20), d -> p.getUUID().equals(d.owner()));
        t.assertTrue(mine.size() == 2, "two drekki: " + mine.size());
        /* a row of four: two drekki spread out onto the ends' side, never side by side (b1 x 2, b2 x 4: one block apart at least) */
        BlockPos b1 = H.offset(-3, 0, 3), b2 = H.offset(3, 0, 3);
        for (int dx = -3; dx <= 3; dx++) if (dx == -3 || dx == -2 || dx == 2 || dx == 3) t.setBlock(H.offset(dx, 0, 3), DrekkiBlocks.STRAW_BEDDING.get());
        t.getLevel().setDayTime(14000);
        t.runAfterDelay(590, () -> t.getLevel().setDayTime(1000));   /* morning again even when it fails: later batches need the day */
        BlockPos a1 = t.absolutePos(b1), a2 = t.absolutePos(b2);
        t.succeedWhen(() -> {
            for (DrekkiEntity d : mine) t.assertTrue(d.isSleepingLoop(), "asleep: " + d.blockPosition());
            java.util.List<BlockPos> on = new java.util.ArrayList<>();
            for (DrekkiEntity d : mine) on.add(d.blockPosition());
            for (BlockPos o : on) t.assertTrue(t.getLevel().getBlockState(o).is(DrekkiBlocks.STRAW_BEDDING.get()), "on bedding: " + on);
            t.assertTrue(Math.abs(on.get(0).getX() - on.get(1).getX()) > 1 || Math.abs(on.get(0).getZ() - on.get(1).getZ()) > 1, "spread out, not side by side: " + on);
            t.getLevel().setDayTime(1000);
            for (DrekkiEntity d : mine) Ranch.roster(t.getLevel().getServer()).remove(d.drekkiId());
        });
    }

    /** Lunar, Underworld and Deep first eyes are nocturnal: a Lunar drekki sleeps by day while a Solar one is awake, and at
     *  night the other way round (Shane, 2026-11-27) */
    @GameTest(template = "floor", timeoutTicks = 800, batch = "bedtime")
    public static void aLunarDrekkiSleepsByDay(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        hatchOne(t, p, h);
        Ranch.putEgg(p, h, CreativeEggItem.egg(4));
        Ranch.hatch(p, h);
        List<DrekkiEntity> mine = t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(h).inflate(20), d -> p.getUUID().equals(d.owner()));
        t.assertTrue(mine.size() == 2, "two drekki: " + mine.size());
        DrekkiEntity lunar = mine.get(0), solar = mine.get(1);
        lunar.setLook(2, lunar.line(), lunar.diet(), dev.frosthaven.ranchercompanion.look.Astral.LUNAR, lunar.horn());
        solar.setLook(2, solar.line(), solar.diet(), dev.frosthaven.ranchercompanion.look.Astral.SOLAR, solar.horn());
        t.getLevel().setDayTime(1000);
        boolean[] dayOk = {false};
        t.runAfterDelay(300, () -> {
            t.assertTrue(lunar.isSleepingLoop() && !solar.isSleepingLoop(), "by day the Lunar one sleeps, the Solar one is awake: " + lunar.isSleepingLoop() + ", " + solar.isSleepingLoop());
            dayOk[0] = true;
            t.getLevel().setDayTime(14000);
        });
        t.runAfterDelay(790, () -> t.getLevel().setDayTime(1000));
        t.succeedWhen(() -> {
            t.assertTrue(dayOk[0], "the day check first");
            t.assertTrue(!lunar.isSleepingLoop() && solar.isSleepingLoop(), "at night the Solar one sleeps, the Lunar one is awake: " + lunar.isSleepingLoop() + ", " + solar.isSleepingLoop());
            t.getLevel().setDayTime(1000);
            for (DrekkiEntity d : mine) Ranch.roster(t.getLevel().getServer()).remove(d.drekkiId());
        });
    }
}
