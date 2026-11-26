package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.LotusCropBlock;
import dev.frosthaven.ranchercompanion.block.TombstoneBlock;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.ranch.Pairing;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Rebirth;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The way back from death (spec, Tombstone: the way back; Shane, 2026-11-26): every death leaves a tombstone with its
 * record; only its owner summons its ghost, which stays within 3 blocks and can pair at a hatchery within 24; a Drekki
 * Lotus Blossom runs a day, then its owner (with no drekki out) reclaims it alive at their side; the lotus plants only on
 * soul soil and grows only beside a tombstone.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class RebirthTests {
    private RebirthTests() {}

    private static TombstoneBlockEntity fallen(GameTestHelper t, ServerPlayer p) {
        DrekkiEntity d = CombatTests.out(t, p, 3, Diet.RAW, Life.Stance.PASSIVE);
        d.setCustomName(net.minecraft.network.chat.Component.literal("Wisp"));
        return TombTests.dieForGood(t, d);
    }

    @GameTest(template = "floor", timeoutTicks = 60)
    public static void aDeathAlwaysLeavesATombstoneThatKeepsItsDrekki(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        t.assertTrue(be.hasRecord() && p.getUUID().equals(be.recordOwner()), "no gear worn, still a tombstone with its record and owner");
        t.assertTrue(Ranch.roster(t.getLevel().getServer()).of(p.getUUID()).isEmpty(), "gone from the roster");
        t.succeed();
    }

    /** soul soil under it: the rebirth functions work only there */
    private static void soil(TombstoneBlockEntity be) { be.getLevel().setBlock(be.getBlockPos().below(), Blocks.SOUL_SOIL.defaultBlockState(), 3); }

    private static void blossomIn(TombstoneBlockEntity be) { soil(be); be.items().setItem(0, new ItemStack(DrekkiItems.LOTUS_BLOSSOM.get())); }

    /** sneak + empty hand on the tombstone, as a player does */
    private static void putOut(GameTestHelper t, ServerPlayer p, TombstoneBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(true);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.NORTH, pos, false);
        t.getLevel().getBlockState(pos).useWithoutItem(t.getLevel(), p, hit);
        p.setShiftKeyDown(false);
    }

    /** flint and steel used on the tombstone, as a player does; returns the flint and steel after */
    private static ItemStack strike(GameTestHelper t, ServerPlayer p, TombstoneBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.FLINT_AND_STEEL));
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.NORTH, pos, false);
        t.getLevel().getBlockState(pos).useItemOn(p.getMainHandItem(), t.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return p.getMainHandItem();
    }

    private static TombstoneBlock.Candle candle(GameTestHelper t, TombstoneBlockEntity be) {
        return t.getLevel().getBlockState(be.getBlockPos()).getValue(TombstoneBlock.CANDLE);
    }

    @GameTest(template = "floor", timeoutTicks = 80)
    public static void theBlossomRaisesItsGhostAndOnlyItsOwnerMayPutItIn(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        BlockPos pos = be.getBlockPos();
        @SuppressWarnings("removal") ServerPlayer other = t.makeMockServerPlayerInLevel();
        ItemStack blossom = new ItemStack(DrekkiItems.LOTUS_BLOSSOM.get());
        t.assertFalse(new dev.frosthaven.ranchercompanion.block.TombstoneMenu(0, other.getInventory(), pos, false).slots.get(0).mayPlace(blossom), "another player cannot put a blossom in");
        t.assertTrue(new dev.frosthaven.ranchercompanion.block.TombstoneMenu(0, p.getInventory(), pos, true).slots.get(0).mayPlace(blossom), "its owner can, in any free slot");
        t.assertTrue(candle(t, be) == TombstoneBlock.Candle.UNLIT && !be.summoned(), "before: no ghost, the candle unlit");
        t.assertTrue(strike(t, p, be).getDamageValue() == 0 && be.reviveStart() == 0, "no blossom: flint and steel lights nothing");
        t.assertTrue(t.getLevel().getBlockState(pos.north()).isAir() && t.getLevel().getBlockState(pos.above()).isAir(), "and sets no fire");
        blossomIn(be);
        Roster.Entry e = Ranch.roster(t.getLevel().getServer()).get(be.drekki().getUUID("DrekkiId"));
        t.assertTrue(be.summoned() && e != null && e.where == Roster.Where.TOMB, "the blossom alone raises its ghost");
        t.assertTrue(be.reviveStart() == 0 && candle(t, be) == TombstoneBlock.Candle.UNLIT, "but the day waits for the candle");
        t.assertTrue(strike(t, other, be).getDamageValue() == 0 && be.reviveStart() == 0, "another player cannot light it");
        t.assertTrue(!Ranch.liveOf(e.id).ghostFlame(), "no soul-fire flames on the ghost before the candle burns");
        t.assertTrue(strike(t, p, be).getDamageValue() == 1 && be.reviveStart() != 0, "its owner lights it with flint and steel: the day starts");
        t.assertTrue(Ranch.liveOf(e.id).ghostFlame(), "the candle burning: the ghost sheds soul-fire flames");
        t.assertTrue(candle(t, be) == TombstoneBlock.Candle.LIT, "the candle lit");
        long started = be.reviveStart();
        t.assertTrue(strike(t, p, be).getDamageValue() == 0 && be.reviveStart() == started, "lit already: nothing more");
        t.runAfterDelay(2, () -> {
            DrekkiEntity g = Ranch.liveOf(e.id);
            t.assertTrue(g != null && g.ghost() && pos.equals(g.home()), "a ghost, anchored to the tombstone");
            t.assertTrue(t.getLevel().getEntity(g.getUUID()) == g, "the ghost is in the world (it ticks: it walks)");
            t.assertTrue(Pairing.band(e) == -1, "a ghost has no mood for a pairing");
            t.assertTrue(!g.inPen(pos.above()) && !g.inPen(pos), "never on top of the tombstone");
            t.assertTrue(g.inPen(pos.offset(3, 0, 0)) && !g.inPen(pos.offset(5, 0, 0)), "its ground: within 3 blocks of it");
            be.items().setItem(0, ItemStack.EMPTY);
            t.assertTrue(!be.summoned() && be.reviveStart() == 0 && Ranch.roster(t.getLevel().getServer()).get(e.id) == null, "the blossom out: the ghost laid to rest, the day stopped");
            t.assertTrue(t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(16), x -> x.ghost()).isEmpty(), "the ghost gone from the world");
            t.assertTrue(candle(t, be) == TombstoneBlock.Candle.UNLIT, "the candle out");
            blossomIn(be);
            t.assertTrue(be.summoned() && be.reviveStart() == 0, "back in: the ghost rises again, the candle waits to be lit");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 80)
    public static void aGhostPairsAtAHatcheryWithin24Blocks(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        blossomIn(be);
        Roster.Entry e = Ranch.roster(t.getLevel().getServer()).get(be.drekki().getUUID("DrekkiId"));
        var dim = t.getLevel().dimension();
        long now = Roster.now(t.getLevel().getServer());
        t.assertTrue(Pairing.fit(e, net.minecraft.core.GlobalPos.of(dim, be.getBlockPos().offset(20, 0, 10)), now), "a hatchery 22 blocks away lists it");
        t.assertFalse(Pairing.fit(e, net.minecraft.core.GlobalPos.of(dim, be.getBlockPos().offset(30, 0, 0)), now), "one 30 blocks away does not");
        t.succeed();
    }

    /** a ghost pairs only when it died Mature */
    @GameTest(template = "floor", timeoutTicks = 80)
    public static void aFledglingsGhostCannotPair(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity young = TombTests.dieForGood(t, CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE));
        blossomIn(young);
        Roster.Entry y = Ranch.roster(t.getLevel().getServer()).get(young.drekki().getUUID("DrekkiId"));
        t.assertTrue(y != null && y.where == Roster.Where.TOMB, "the Fledgling's ghost is risen");
        t.assertFalse(Pairing.fit(y, net.minecraft.core.GlobalPos.of(t.getLevel().dimension(), young.getBlockPos().offset(5, 0, 0)),
            Roster.now(t.getLevel().getServer())), "but it cannot pair");
        t.succeed();
    }

    /** putting the candle out (sneak + empty hand; its owner only) cancels the day: the ghost stays while the blossom is in,
     *  and lighting it again starts a new day; it also cancels the ghost's pairing at a hatchery, the tonic back */
    @GameTest(template = "floor", timeoutTicks = 120)
    public static void puttingTheCandleOutCancelsTheDayAndThePairing(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        blossomIn(be);
        UUID id = be.drekki().getUUID("DrekkiId");
        strike(t, p, be);
        t.assertTrue(be.reviveStart() != 0 && candle(t, be) == TombstoneBlock.Candle.LIT, "lit: the day runs");
        @SuppressWarnings("removal") ServerPlayer other = t.makeMockServerPlayerInLevel();
        putOut(t, other, be);
        t.assertTrue(be.reviveStart() != 0, "another player cannot put it out");
        /* its ghost pairs at a hatchery nearby */
        BlockPos h = be.getBlockPos().offset(4, 0, 0);
        t.getLevel().setBlock(h, DrekkiBlocks.HATCHERY.get().defaultBlockState(), 3);
        var hatch = (dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity) t.getLevel().getBlockEntity(h);
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry mate = r.add(UUID.randomUUID(), p.getUUID(), new net.minecraft.nbt.CompoundTag(), Roster.Where.HOME, Roster.at(t.getLevel(), h));
        ItemStack tonic = new ItemStack(DrekkiItems.TONICS.get(3).get());
        hatch.startBreeding(id, mate.id, Roster.now(t.getLevel().getServer()), tonic.copy(), "");
        r.startBreeding(r.get(id), Roster.now(t.getLevel().getServer()));
        r.startBreeding(mate, Roster.now(t.getLevel().getServer()));
        t.assertTrue(hatch.breeding() && Rebirth.breeding(t.getLevel().getServer(), be), "its ghost is breeding");
        int tonicsBefore = p.getInventory().countItem(tonic.getItem());
        putOut(t, p, be);
        t.assertTrue(be.reviveStart() == 0 && candle(t, be) == TombstoneBlock.Candle.UNLIT, "its owner puts it out: the day stops");
        t.assertTrue(be.summoned() && r.get(id) != null, "the ghost stays while the blossom is in");
        t.assertTrue(!hatch.breeding() && !r.get(id).breeding(Roster.now(t.getLevel().getServer())), "and its pairing is cancelled");
        t.assertTrue(p.getInventory().countItem(tonic.getItem()) == tonicsBefore + 1, "its tonic comes back");
        t.assertTrue(strike(t, p, be).getDamageValue() == 1 && be.reviveStart() != 0, "lit again: a new day");
        t.succeed();
    }

    /** a ghost and players pass through each other (Shane, 2026-11-26): no push either way; other mobs still push it */
    @GameTest(template = "floor", timeoutTicks = 80)
    public static void aGhostAndPlayersPassThroughEachOther(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        blossomIn(be);
        t.runAfterDelay(2, () -> {
            DrekkiEntity g = Ranch.liveOf(be.drekki().getUUID("DrekkiId"));
            t.assertTrue(g != null && g.ghost(), "its ghost");
            g.setDeltaMovement(Vec3.ZERO);
            g.push(p);
            t.assertTrue(g.getDeltaMovement().horizontalDistanceSqr() == 0, "a player does not push a ghost");
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(t.getLevel());
            zombie.moveTo(g.position().add(0.3, 0, 0));
            g.push(zombie);
            t.assertTrue(g.getDeltaMovement().horizontalDistanceSqr() > 0, "another mob still does");
            g.setDeltaMovement(Vec3.ZERO);
            @SuppressWarnings("removal") ServerPlayer walker = t.makeMockServerPlayerInLevel();
            walker.moveTo(g.position().add(0.2, 0, 0));  /* standing inside it while it ticks: its push would show in the player's motion */
            walker.setDeltaMovement(Vec3.ZERO);
            t.runAfterDelay(10, () -> { t.assertTrue(walker.getDeltaMovement().horizontalDistanceSqr() == 0, "nor does it push a player: " + walker.getDeltaMovement()); t.succeed(); });
        });
    }

    /** soul soil (Shane, 2026-11-26): off it a tombstone is decoration (no ghost, no candle, no Reclaim); soul soil put under
     *  it raises the ghost; taken away, the ghost rests and the candle goes out */
    @GameTest(template = "floor", timeoutTicks = 120)
    public static void theRebirthWorksOnlyOnSoulSoil(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        BlockPos pos = be.getBlockPos();
        t.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        be.items().setItem(0, new ItemStack(DrekkiItems.LOTUS_BLOSSOM.get()));
        t.assertTrue(!be.summoned() && "tomb.soul_soil".equals(Rebirth.lightWhy(p, be)) && "tomb.soul_soil".equals(Rebirth.reclaimWhy(p, be)),
            "on stone: no ghost, no candle, no Reclaim");
        t.assertTrue(strike(t, p, be).getDamageValue() == 0 && be.reviveStart() == 0, "flint and steel lights nothing");
        soil(be);
        t.runAfterDelay(25, () -> {
            t.assertTrue(be.summoned(), "soul soil put under it: the ghost rises");
            t.assertTrue(strike(t, p, be).getDamageValue() == 1 && be.reviveStart() != 0, "and the candle lights");
            t.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            t.runAfterDelay(25, () -> {
                t.assertTrue(!be.summoned() && be.reviveStart() == 0 && candle(t, be) == TombstoneBlock.Candle.UNLIT, "the soul soil gone: the ghost rests, the candle out");
                t.succeed();
            });
        });
    }

    /** automation (Shane, 2026-11-26): a blossom pushed in starts the day; gear comes out, the blossom never does; an
     *  inert tombstone takes no blossom */
    @GameTest(template = "floor", timeoutTicks = 80)
    public static void automationPushesABlossomInAndPullsGearOut(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 3, Diet.RAW, Life.Stance.PASSIVE);
        d.setBardingItem(new ItemStack(DrekkiItems.BARDING.get(dev.frosthaven.ranchercompanion.item.Barding.IRON).get()));  /* gear to pull out */
        TombstoneBlockEntity be = TombTests.dieForGood(t, d);
        soil(be);
        var h = t.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
        t.assertTrue(h != null, "a tombstone is an inventory to automation");
        int free = -1;
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).isEmpty()) { free = i; break; }
        t.assertTrue(free >= 0, "a free slot");
        t.assertTrue(h.insertItem(free, new ItemStack(net.minecraft.world.item.Items.DIRT), false).getCount() == 1, "anything else is refused");
        UUID id = be.drekki().getUUID("DrekkiId");
        ItemStack left = h.insertItem(free, new ItemStack(DrekkiItems.LOTUS_BLOSSOM.get(), 2), false);
        t.assertTrue(left.getCount() == 1 && be.blossomSlot() == free, "one blossom goes in");
        Roster.Entry e = Ranch.roster(t.getLevel().getServer()).get(id);
        t.assertTrue(be.summoned() && be.reviveStart() == 0 && e != null && e.where == Roster.Where.TOMB, "and the ghost rises (the candle waits for its owner)");
        t.assertTrue(h.extractItem(free, 1, false).isEmpty() && be.blossomSlot() == free, "automation never takes the blossom out");
        for (int i = 0; i < h.getSlots(); i++) if (i != free) t.assertTrue(h.insertItem(i, left, true).getCount() == 1, "no second blossom");
        boolean gear = false;
        for (int i = 0; i < h.getSlots(); i++)
            if (i != free && !h.getStackInSlot(i).isEmpty()) { gear = true; t.assertTrue(!h.extractItem(i, 1, false).isEmpty() && h.getStackInSlot(i).isEmpty(), "gear comes out"); }
        t.assertTrue(gear, "it held the drekki's gear");
        be.clearRecord();
        int empty = -1;
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).isEmpty()) { empty = i; break; }
        t.assertTrue(h.insertItem(empty, new ItemStack(DrekkiItems.LOTUS_BLOSSOM.get()), true).getCount() == 1, "an inert tombstone takes no blossom");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 80)
    public static void minedItKeepsItsDrekkiButNotItsDay(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        BlockPos pos = be.getBlockPos();
        java.util.UUID id = be.drekki().getUUID("DrekkiId");
        blossomIn(be);
        var record = be.drekki();
        ItemStack drop = Block.getDrops(t.getLevel().getBlockState(pos), t.getLevel(), pos, be).get(0);
        t.getLevel().destroyBlock(pos, false);
        t.assertTrue(Ranch.roster(t.getLevel().getServer()).get(id) == null, "mined: the ghost laid to rest");
        t.assertTrue(record.equals(drop.get(dev.frosthaven.ranchercompanion.item.DrekkiData.TOMB_DREKKI.get())), "the item keeps the drekki");
        t.getLevel().setBlock(pos, DrekkiBlocks.TOMBSTONE.get().defaultBlockState(), 3);
        TombstoneBlockEntity again = (TombstoneBlockEntity) t.getLevel().getBlockEntity(pos);
        again.applyComponentsFromItemStack(drop);
        t.assertTrue(again.hasRecord() && again.reviveStart() == 0 && !again.summoned(), "placed again: its drekki, no progress");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 80)
    public static void theLotusRunsADayThenItsOwnerReclaimsIt(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        TombstoneBlockEntity be = fallen(t, p);
        BlockPos pos = be.getBlockPos();
        UUID id = be.drekki().getUUID("DrekkiId");
        soil(be);
        t.assertTrue("tomb.need_lotus".equals(Rebirth.reclaimWhy(p, be)), "no blossom: no reclaim");
        t.assertFalse(be.setMessage("hello"), "a tombstone with a drekki keeps its inscription: no message");
        blossomIn(be);
        t.assertTrue("tomb.not_lit".equals(Rebirth.reclaimWhy(p, be)), "not before the candle is lit");
        t.assertTrue(be.light(), "lit");
        t.assertTrue("tomb.not_ready".equals(Rebirth.reclaimWhy(p, be)), "not before the day is out");
        be.setReviveStart(Roster.now(t.getLevel().getServer()) - Rebirth.DAY);
        be.candle();
        t.assertTrue(candle(t, be) == TombstoneBlock.Candle.MELTED, "the day done: the candle melted down");
        @SuppressWarnings("removal") ServerPlayer other = t.makeMockServerPlayerInLevel();
        t.assertTrue("tomb.not_owner".equals(Rebirth.reclaimWhy(other, be)), "only its owner reclaims it");
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry second = r.add(UUID.randomUUID(), p.getUUID(), new net.minecraft.nbt.CompoundTag(), Roster.Where.OUT, null);
        t.assertTrue("tomb.one_out".equals(Rebirth.reclaimWhy(p, be)), "not while another drekki is with its owner");
        r.remove(second.id);
        /* its ghost pairing at a hatchery: the blossom stays and Reclaim waits (no breeding lost to a reclaim) */
        Roster.Entry g = r.get(id);
        g.breedUntil = Roster.now(t.getLevel().getServer()) + Rebirth.DAY;
        t.assertTrue("tomb.ghost_breeding".equals(Rebirth.reclaimWhy(p, be)), "not while its ghost is breeding");
        var menu = new dev.frosthaven.ranchercompanion.block.TombstoneMenu(0, p.getInventory(), pos, true);
        t.assertTrue(!menu.getSlot(be.blossomSlot()).mayPickup(p), "the blossom stays in while its ghost is breeding");
        g.breedUntil = 0;
        t.assertTrue(menu.getSlot(be.blossomSlot()).mayPickup(p), "and comes out again after");
        Rebirth.reclaim(p, pos, be);
        Roster.Entry e = r.get(id);
        t.assertTrue(e != null && e.where == Roster.Where.OUT && r.out(p.getUUID()) == e, "back, and with its owner");
        t.assertTrue(!be.hasRecord() && !be.summoned() && be.blossomSlot() < 0, "the blossom used up");
        t.assertTrue(be.name().isEmpty() && be.date().isEmpty() && !be.natural(), "inert: no inscription, and it stays as decoration");
        BlockState used = t.getLevel().getBlockState(pos);
        t.assertTrue(used.getValue(TombstoneBlock.CRACKED), "used: cracked");
        be.candle();
        t.assertTrue(candle(t, be) == TombstoneBlock.Candle.UNLIT, "and its candle is no longer the day's: it stays as it is");
        ItemStack usedItem = Block.getDrops(used, t.getLevel(), pos, be).get(0);
        var props = usedItem.get(net.minecraft.core.component.DataComponents.BLOCK_STATE);
        t.assertTrue(props != null && Boolean.TRUE.equals(props.get(TombstoneBlock.CRACKED)), "mined, its item places cracked again");
        /* decorative now: its candle whole and unlit; anyone lights it (a light source) and puts it out */
        t.assertTrue(used.getValue(TombstoneBlock.CANDLE) == TombstoneBlock.Candle.UNLIT, "used: the candle whole again, unlit");
        @SuppressWarnings("removal") ServerPlayer passer = t.makeMockServerPlayerInLevel();
        passer.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);  /* a mock player starts in creative: no wear on flint and steel */
        t.assertTrue(strike(t, passer, be).getDamageValue() == 1 && candle(t, be) == TombstoneBlock.Candle.LIT, "anyone lights a decorative candle");
        t.assertTrue(t.getLevel().getBlockState(pos).getLightEmission(t.getLevel(), pos) == 3, "light 3, a fifth of a torch");
        be.candle();
        t.assertTrue(candle(t, be) == TombstoneBlock.Candle.LIT && be.reviveStart() == 0, "it stays lit: no day, nothing to undo it");
        putOut(t, passer, be);
        t.assertTrue(candle(t, be) == TombstoneBlock.Candle.UNLIT, "sneak + empty hand puts it out");
        /* a message on the decorative one (Shane, 2026-11-26): formatting stripped, 32 characters at most, kept on the item */
        passer.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(pos.east(2)));  /* within reach */
        TombstoneBlockEntity.editMessage(passer, pos, "\u00a7cRest well, old friend. You carried us far and true.");
        t.assertTrue(be.message().equals("Rest well, old friend. You carri") && be.message().length() == TombstoneBlockEntity.MESSAGE_MAX,
            "its message, formatting stripped, cut to 32: " + be.message());
        ItemStack withMsg = Block.getDrops(t.getLevel().getBlockState(pos), t.getLevel(), pos, be).get(0);
        t.assertTrue(be.message().equals(withMsg.get(dev.frosthaven.ranchercompanion.item.DrekkiData.TOMB_MESSAGE.get())), "mined, its item keeps the message");
        t.assertTrue(t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(16), x -> x.ghost()).isEmpty(),
            "its ghost is gone from the world");
        t.runAfterDelay(2, () -> {
            DrekkiEntity d = Ranch.liveOf(id);
            t.assertTrue(d != null && !d.ghost() && d.isOut() && d.getHealth() == d.getMaxHealth(), "alive, at full health, travelling with its owner");
            long now = Roster.now(t.getLevel().getServer());
            t.assertTrue(d.life.soulflame(now) && !d.life.soulflame(now + Life.SOULFLAME), "Soulflame for a day, on the clock");
            d.syncLife();
            t.assertTrue(d.soulflameLeft() > 0 && d.band().ordinal() <= Life.Band.UNEASY.ordinal(), "its mood two bands down: " + d.band());
            t.succeed();
        });
    }

    /** the update's grace: a ghost saved before 0.7 is alive again, Phoenix Heart is gone; a ghost saved since stays one */
    @GameTest(template = "floor", timeoutTicks = 20)
    public static void theUpdateBringsOldGhostsBackAndDropsPhoenixHeart(GameTestHelper t) {
        Life l = new Life();
        l.ghost = true;
        l.traits.add("phoenix_heart");
        l.traits.add("hearty");
        var old = dev.frosthaven.ranchercompanion.life.LifeTag.save(l);
        old.remove("rev");  /* saved before 0.7 */
        Life back = new Life();
        dev.frosthaven.ranchercompanion.life.LifeTag.load(back, old);
        t.assertTrue(!back.ghost && back.traits.equals(java.util.List.of("hearty")), "alive again, no Phoenix Heart: " + back.traits);
        Life now = new Life();
        dev.frosthaven.ranchercompanion.life.LifeTag.load(now, dev.frosthaven.ranchercompanion.life.LifeTag.save(l));
        t.assertTrue(now.ghost, "a ghost saved since (a summoned one) stays a ghost");
        t.succeed();
    }

    /** soul soil only; grows only in front of a tombstone or behind it, not at its sides (Shane, 2026-11-26); on the clock;
     *  no bone meal; a harvest always gives a seed back, and the blossom when grown */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void theLotusPlantsOnSoulSoilAndGrowsOnlyByATombstone(GameTestHelper t) {
        BlockState lotus = DrekkiBlocks.LOTUS.get().defaultBlockState();
        BlockState tombN = DrekkiBlocks.TOMBSTONE.get().defaultBlockState().setValue(TombstoneBlock.FACING, net.minecraft.core.Direction.NORTH);
        BlockPos front = new BlockPos(2, 2, 2), side = new BlockPos(5, 2, 2), behind = new BlockPos(8, 2, 4), alone = new BlockPos(5, 2, 6);
        t.setBlock(new BlockPos(5, 1, 4), Blocks.DIRT);
        t.assertFalse(((LotusCropBlock) lotus.getBlock()).isValidBonemealTarget(t.getLevel(), t.absolutePos(front), lotus), "no bone meal");
        t.assertFalse(lotus.canSurvive(t.getLevel(), t.absolutePos(new BlockPos(5, 2, 4))), "not on dirt");
        for (BlockPos c : new BlockPos[]{front, side, behind, alone}) t.setBlock(c.below(), Blocks.SOUL_SOIL);
        t.assertTrue(lotus.canSurvive(t.getLevel(), t.absolutePos(front)), "on soul soil");
        for (BlockPos c : new BlockPos[]{front, side, behind, alone}) t.setBlock(c, lotus);
        t.setBlock(front.south(), tombN);  /* its front faces north: the crop is in front of it */
        t.setBlock(side.east(), tombN);    /* the crop is at its west side */
        t.setBlock(behind.north(), tombN); /* the crop is behind it */
        var level = t.getLevel();
        ItemStack youngDrop = Block.getDrops(level.getBlockState(t.absolutePos(alone)), level, t.absolutePos(alone), level.getBlockEntity(t.absolutePos(alone))).get(0);
        t.assertTrue(Block.getDrops(level.getBlockState(t.absolutePos(alone)), level, t.absolutePos(alone), null).size() == 1
            && youngDrop.is(DrekkiItems.LOTUS_SEEDS.get()) && youngDrop.getCount() == 1, "young: its seed back");
        /* a day on the clock for each (as if counted while unloaded), then each counts it */
        for (BlockPos c : new BlockPos[]{front, side, behind, alone}) {
            var lbe = (dev.frosthaven.ranchercompanion.block.LotusBlockEntity) level.getBlockEntity(t.absolutePos(c));
            lbe.count(level);
            lbe.countedAgo(Roster.DAY);
            lbe.count(level);
        }
        java.util.function.Function<BlockPos, Integer> age = c -> level.getBlockState(t.absolutePos(c)).getValue(LotusCropBlock.AGE);
        t.assertTrue(age.apply(front) == 3, "in front of a tombstone: grown");
        t.assertTrue(age.apply(behind) == 3, "behind a tombstone: grown");
        t.assertTrue(age.apply(side) == 0, "at a tombstone's side: it does not grow");
        t.assertTrue(age.apply(alone) == 0, "no tombstone: it does not grow");
        var drops = Block.getDrops(level.getBlockState(t.absolutePos(front)), level, t.absolutePos(front), null);
        t.assertTrue(drops.size() == 2 && drops.stream().anyMatch(d -> d.is(DrekkiItems.LOTUS_SEEDS.get()) && d.getCount() == 1)
            && drops.stream().anyMatch(d -> d.is(DrekkiItems.LOTUS_BLOSSOM.get()) && d.getCount() == 1), "grown: a seed back and the blossom: " + drops);
        t.succeed();
    }
}
