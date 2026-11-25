package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.Barding;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A drekki's tombstone (spec, Tombstone). */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class TombTests {
    private TombTests() {}

    /** a drekki gone for good: dies (a ghost does not roll again), and its tombstone is the nearest one around it */
    static TombstoneBlockEntity dieForGood(GameTestHelper t, DrekkiEntity d) {
        d.life.ghost = true;
        BlockPos at = d.blockPosition();
        d.die(d.damageSources().genericKill());
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 6, 2)))
            if (t.getLevel().getBlockEntity(p) instanceof TombstoneBlockEntity be) return be;
        throw new net.minecraft.gametest.framework.GameTestAssertException("no tombstone around " + at);
    }

    /** its tombstone holds its barding and bag, carries its name and the date, and cannot be mined while full */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void aTombstoneHoldsItsGearAndStaysUntilEmptied(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        d.setBardingItem(new ItemStack(DrekkiItems.BARDING.get(Barding.IRON).get()));
        d.setAccessory(GearTests.bag(1));
        d.setCustomName(net.minecraft.network.chat.Component.literal("Ember"));
        String name = "Ember";
        TombstoneBlockEntity be = dieForGood(t, d);
        BlockPos pos = be.getBlockPos();
        t.assertTrue(be.items().getItem(1).is(DrekkiItems.BARDING.get(Barding.IRON).get()) && be.items().getItem(2).is(DrekkiItems.BAGS.get(0).get()),
            "the barding and the satchel are in it");
        t.assertTrue(be.name().equals(name) && be.date().matches("\\d\\d-\\d\\d-\\d{4}"), "name " + be.name() + ", date " + be.date());
        t.assertTrue(Ranch.roster(t.getLevel().getServer()).get(d.drekkiId()) == null, "its record is gone");
        var rec = be.drekki();
        t.assertTrue(rec.getInt("Stage") == 2 && rec.contains("Line") && rec.contains("Life") && rec.getInt("Barding") > 0
            && !rec.contains("BardingItem") && !rec.contains("Accessory"), "the tombstone keeps its look and life, not its worn items: " + rec.getAllKeys());
        t.assertFalse(be.getUpdateTag(t.getLevel().registryAccess()).contains("Drekki"), "the record is not sent to clients");
        var state = t.getLevel().getBlockState(pos);
        t.assertTrue(state.getDestroyProgress(p, t.getLevel(), pos) == 0, "it cannot be mined while it holds gear");
        t.assertFalse(state.onDestroyedByPlayer(t.getLevel(), pos, p, true, t.getLevel().getFluidState(pos)), "nor broken in creative");
        be.items().clearContent();
        t.assertTrue(state.getDestroyProgress(p, t.getLevel(), pos) > 0, "empty, it mines");
        /* mined empty: itself, with the name and date */
        var drops = Block.getDrops(state, t.getLevel(), pos, be);
        t.assertTrue(drops.size() == 1 && name.equals(drops.get(0).get(DrekkiData.TOMB_NAME.get())) && be.date().equals(drops.get(0).get(DrekkiData.TOMB_DATE.get())),
            "it drops itself with the name and date: " + drops);
        t.assertTrue(rec.equals(drops.get(0).get(DrekkiData.TOMB_DREKKI.get())), "and with the drekki's record");
        t.succeed();
    }

    /** turning into a ghost: its gear goes into a tombstone; its screen is read-only, it is never hungry, no mob targets it
     *  and only its owner is sent it (spec, Ghost drekki) */
    @GameTest(template = "floor", timeoutTicks = 60)
    public static void aGhostWearsNothingAndIsReadOnly(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        d.setBardingItem(new ItemStack(DrekkiItems.BARDING.get(Barding.IRON).get()));
        d.setAccessory(GearTests.bag(1));
        BlockPos at = d.blockPosition();
        dev.frosthaven.ranchercompanion.ranch.Death.turnGhost(d);
        TombstoneBlockEntity be = null;
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 6, 2)))
            if (t.getLevel().getBlockEntity(q) instanceof TombstoneBlockEntity b) be = b;
        t.assertTrue(d.ghost() && d.isAlive() && d.bardingItem().isEmpty() && d.accessory().isEmpty(), "a living ghost that wears nothing");
        t.assertTrue(be != null && !be.items().getItem(1).isEmpty() && !be.items().getItem(2).isEmpty(), "its barding and bag are in a tombstone");
        var menu = new dev.frosthaven.ranchercompanion.entity.DrekkiMenu(0, p.getInventory(), d);
        t.assertTrue(menu.locked() && !menu.getSlot(1).mayPlace(new ItemStack(DrekkiItems.BARDING.get(Barding.IRON).get()))
            && !menu.clickMenuButton(p, Life.Stance.AGGRESSIVE.ordinal()), "its screen is read-only: no gear, no stance");
        /* no mob targets it, and a zombie already after it lets go; only its owner is sent it */
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(t.getLevel());
        t.assertTrue(zombie != null && !zombie.canAttack(d), "a mob cannot target a ghost");
        var other = net.minecraft.world.entity.player.Player.class.cast(t.makeMockServerPlayerInLevel());
        t.assertTrue(d.broadcastToPlayer(p) && !d.broadcastToPlayer((ServerPlayer) other), "its owner is sent it, another player is not");
        float food = d.food();
        d.life.stamp = dev.frosthaven.ranchercompanion.life.Vitals.now(d) - 100;  /* 5 s of live time */
        dev.frosthaven.ranchercompanion.life.Vitals.update(d);
        t.assertTrue(d.food() == food, "it does not get hungry: " + food + " -> " + d.food());
        t.succeed();
    }

    /** a death's tombstone vanishes once emptied, as the Corpse mod's corpses; one a player places stays */
    @GameTest(template = "floor", timeoutTicks = TombstoneBlockEntity.VANISH * 2 + 60)
    public static void anEmptiedTombstoneVanishesButAPlacedOneStays(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        d.setBardingItem(new ItemStack(DrekkiItems.BARDING.get(Barding.IRON).get()));
        TombstoneBlockEntity be = dieForGood(t, d);
        BlockPos pos = be.getBlockPos();
        ItemStack mined = Block.getDrops(t.getLevel().getBlockState(pos), t.getLevel(), pos, be).get(0);
        /* a mined one placed again nearby: the item's name and date come back, and it never vanishes */
        BlockPos placed = t.absolutePos(new BlockPos(0, 2, 0));
        if (placed.equals(pos)) placed = placed.east();
        t.getLevel().setBlock(placed, DrekkiBlocks.TOMBSTONE.get().defaultBlockState(), 3);
        TombstoneBlockEntity again = (TombstoneBlockEntity) t.getLevel().getBlockEntity(placed);
        again.applyComponentsFromItemStack(mined);
        t.assertTrue(again.name().equals(be.name()) && again.date().equals(be.date()) && again.drekki().equals(be.drekki()) && !again.drekki().isEmpty(),
            "the placed one reads its name, date and record back");
        t.runAfterDelay(TombstoneBlockEntity.VANISH / 2, () -> {
            t.assertTrue(t.getLevel().getBlockState(pos).is(DrekkiBlocks.TOMBSTONE.get()), "it stays while it holds gear");
            be.items().clearContent();
        });
        final BlockPos kept = placed;
        t.runAfterDelay(TombstoneBlockEntity.VANISH / 2 + TombstoneBlockEntity.VANISH + 10, () -> {
            t.assertTrue(t.getLevel().getBlockState(pos).is(Blocks.AIR), "emptied, it vanished");
            t.assertTrue(t.getLevel().getBlockState(kept).is(DrekkiBlocks.TOMBSTONE.get()), "the placed one stays");
            t.succeed();
        });
    }
}
