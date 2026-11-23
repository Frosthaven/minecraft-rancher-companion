package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiMenu;
import dev.frosthaven.ranchercompanion.item.Barding;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.item.Dyeing;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Diet;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Barding and the accessory slot (spec, Barding; Accessory: bags; Dyeing). */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class GearTests {
    private GearTests() {}

    static ItemStack fledglingBarding(Barding tier) { return new ItemStack(DrekkiItems.FLEDGLING_BARDING.get(tier).get()); }
    static ItemStack bag(int kind) { return new ItemStack(DrekkiItems.BAGS.get(kind - 1).get()); }

    /** as wolf armor: hits go into the barding's durability until it breaks; starving goes through (a damage that passes armor) */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void bardingSoaksUpDamageUntilItBreaks(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        d.setBardingItem(fledglingBarding(Barding.IRON));
        float full = d.getHealth();
        d.hurt(d.damageSources().generic(), 10);
        t.assertTrue(d.getHealth() == full && d.bardingItem().getDamageValue() == 10, "the barding took the hit: health " + d.getHealth() + ", wear " + d.bardingItem().getDamageValue());
        d.invulnerableTime = 0;
        d.hurt(d.damageSources().starve(), 4);
        t.assertTrue(d.getHealth() == full - 4 && d.bardingItem().getDamageValue() == 10, "starving goes through: health " + d.getHealth());
        d.invulnerableTime = 0;
        d.hurt(d.damageSources().generic(), 70);  /* 74 durability: 10 + 70 breaks it */
        t.assertTrue(d.bardingItem().isEmpty() && d.barding() == 0, "it broke and comes off");
        d.invulnerableTime = 0;
        d.hurt(d.damageSources().generic(), 5);
        t.assertTrue(d.getHealth() == full - 9, "now hits land: health " + d.getHealth());
        t.succeed();
    }

    /** each slot takes only what fits the stage: barding of its own stage, the satchel from 2, saddlebags from 3 */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void slotsTakeTheirStage(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        ItemStack mature = new ItemStack(DrekkiItems.BARDING.get(Barding.IRON).get());
        t.assertTrue(DrekkiMenu.fitsBarding(d, fledglingBarding(Barding.IRON)) && !DrekkiMenu.fitsBarding(d, mature), "a Fledgling wears Fledgling barding only");
        t.assertTrue(DrekkiMenu.fitsAccessory(d, bag(1)) && !DrekkiMenu.fitsAccessory(d, bag(2)) && !DrekkiMenu.fitsAccessory(d, new ItemStack(Items.CHEST)),
            "a Fledgling wears a satchel, not a saddlebag or anything else");
        d.setLook(3, d.line(), d.diet(), d.astral(), d.horn());
        t.assertTrue(DrekkiMenu.fitsBarding(d, mature) && !DrekkiMenu.fitsBarding(d, fledglingBarding(Barding.IRON)) && DrekkiMenu.fitsAccessory(d, bag(1))
            && DrekkiMenu.fitsAccessory(d, bag(6)), "a Mature wears Mature barding, a satchel or any saddlebag");
        t.succeed();
    }

    /** growing out of Fledgling barding gives it back to its owner */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void outgrownBardingGoesBackToItsOwner(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        d.setBardingItem(fledglingBarding(Barding.GOLD));
        d.setLook(3, d.line(), d.diet(), d.astral(), d.horn());
        d.outgrowBarding();
        t.assertTrue(d.bardingItem().isEmpty() && p.getInventory().contains(fledglingBarding(Barding.GOLD)), "the owner has it back");
        t.succeed();
    }

    /** a bag keeps what it holds when taken off, as a shulker box does */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void aBagKeepsItsContents(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        d.setAccessory(bag(1));
        t.assertTrue(d.bagStorage().getSlots() == 9, "a satchel holds 9");
        d.bagStorage().insertItem(4, new ItemStack(Items.APPLE, 5), false);
        ItemStack off = d.accessory();
        d.setAccessory(ItemStack.EMPTY);
        t.assertTrue(d.bagStorage() == null, "no bag, no storage");
        t.assertTrue(off.get(net.minecraft.core.component.DataComponents.CONTAINER).nonEmptyStream().anyMatch(s -> s.is(Items.APPLE) && s.getCount() == 5), "the apples are in the bag item");
        d.setAccessory(off);
        t.assertTrue(d.bagStorage().getStackInSlot(4).is(Items.APPLE) && d.bagStorage().getStackInSlot(4).getCount() == 5, "back on, the apples are there");
        t.succeed();
    }

    /** without Sophisticated a bag is a shulker box of its size: no bag and no shulker box goes inside one */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void noBagsInBags(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        d.setAccessory(bag(1));
        var h = d.bagStorage();
        t.assertTrue(!h.insertItem(0, bag(1), true).isEmpty() && !h.insertItem(0, new ItemStack(Items.SHULKER_BOX), true).isEmpty()
            && h.insertItem(0, new ItemStack(Items.APPLE), true).isEmpty(), "a satchel takes apples, not a satchel or a shulker box");
        t.succeed();
    }

    /** a plain bag placed in the world (sneak + right-click), opened as a shulker box, broken back into the same bag */
    @GameTest(template = "floor", timeoutTicks = 60)
    public static void aBagPlacesAndBreaksLikeAShulkerBox(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        ItemStack sb = bag(2);
        Dyeing.set(sb, DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF, -1);
        sb.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE, 7))));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sb);
        p.setShiftKeyDown(true);
        var ground = t.absolutePos(LifeTests.H.offset(-3, -1, 0));
        t.getLevel().setBlockAndUpdate(ground, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());  /* something to place it on */
        var hit = new net.minecraft.world.phys.BlockHitResult(ground.getCenter().add(0, 0.5, 0), net.minecraft.core.Direction.UP, ground, false);
        var result = sb.useOn(new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        var at = ground.above();
        t.assertTrue(result.consumesAction() && t.getLevel().getBlockEntity(at) instanceof dev.frosthaven.ranchercompanion.block.BagBlockEntity, "sneak + right-click placed it: " + result);
        var be = (dev.frosthaven.ranchercompanion.block.BagBlockEntity) t.getLevel().getBlockEntity(at);
        t.assertTrue(be.getBlockState().getValue(dev.frosthaven.ranchercompanion.block.BagBlock.KIND) == 2 && be.storage().getStackInSlot(0).is(Items.APPLE),
            "the placed saddlebag holds its apples");
        be.storage().insertItem(1, new ItemStack(Items.BREAD), false);
        t.getLevel().destroyBlock(at, true);
        var drops = t.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(2));
        t.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(DrekkiItems.BAGS.get(1).get()) && Dyeing.main(drops.get(0).getItem()) >= 0
            && drops.get(0).getItem().get(net.minecraft.core.component.DataComponents.CONTAINER).nonEmptyStream().anyMatch(s -> s.is(Items.BREAD)),
            "broken, it drops itself with its dye, apples and bread: " + drops);
        t.succeed();
    }

    /** dyes left of the item color the main part, right of it the trim; re-dyeing blends; a tier up keeps it all */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void dyeColumnsAndTierUps(GameTestHelper t) {
        var level = t.getLevel();
        ItemStack red = new ItemStack(Items.RED_DYE), blue = new ItemStack(Items.BLUE_DYE);
        int r = DyeColor.RED.getTextureDiffuseColor() & 0xFFFFFF, b = DyeColor.BLUE.getTextureDiffuseColor() & 0xFFFFFF;
        ItemStack barding = fledglingBarding(Barding.IRON);
        ItemStack out = craft(t, CraftingInput.of(3, 1, List.of(red, barding, blue)));
        t.assertTrue(Dyeing.main(out) == r && Dyeing.trim(out) == b, "red left is the main, blue right the trim: " + Integer.toHexString(Dyeing.main(out)) + " " + Integer.toHexString(Dyeing.trim(out)));
        ItemStack again = craft(t, CraftingInput.of(2, 1, List.of(blue, out)));
        t.assertTrue(Dyeing.main(again) != r && Dyeing.main(again) != b && Dyeing.trim(again) == b, "blue over red blends the main, the trim stays");
        ItemStack column = craft(t, CraftingInput.of(1, 2, List.of(red, barding)));
        t.assertTrue(Dyeing.main(column) == r && Dyeing.trim(column) == r, "in its column a dye colors both");
        /* a saddlebag with apples and a dye, ringed by iron: the iron saddlebag keeps both */
        ItemStack sb = bag(2);
        Dyeing.set(sb, r, -1);
        sb.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE, 3))));
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack up = craft(t, CraftingInput.of(3, 3, List.of(iron, iron, iron, iron, sb, iron, iron, iron, iron)));
        t.assertTrue(up.is(DrekkiItems.BAGS.get(2).get()) && Dyeing.main(up) == r
            && up.get(net.minecraft.core.component.DataComponents.CONTAINER).nonEmptyStream().anyMatch(s -> s.is(Items.APPLE)), "the iron saddlebag kept its dye and apples");
        t.succeed();
    }

    static ItemStack craft(GameTestHelper t, CraftingInput in) {
        var level = t.getLevel();
        var hit = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, in, level);
        t.assertTrue(hit.isPresent(), "a recipe matches");
        return hit.get().value().assemble(in, level.registryAccess());
    }

    /** a water cauldron washes both dyes off barding and bags with the one named action the Haven pack's bleach runs */
    @GameTest(template = "floor", timeoutTicks = 20)
    public static void aCauldronWashesBothDyesOff(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        net.minecraft.core.BlockPos c = t.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        for (ItemStack s : List.of(fledglingBarding(Barding.IRON), bag(1))) {
            t.getLevel().setBlock(c, net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3), 3);
            Dyeing.set(s, 0xff0000, 0x0000ff);
            var action = net.minecraft.core.cauldron.CauldronInteraction.WATER.map().get(s.getItem());
            t.assertTrue(action instanceof dev.frosthaven.ranchercompanion.item.LeatherWash, s.getItem() + " uses the named wash: " + action);
            action.interact(t.getLevel().getBlockState(c), t.getLevel(), c, p, net.minecraft.world.InteractionHand.MAIN_HAND, s);
            t.assertTrue(Dyeing.main(s) < 0 && Dyeing.trim(s) < 0, s.getItem() + " is undyed");
            t.assertTrue(t.getLevel().getBlockState(c).getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL) == 2, "one level of water used");
        }
        t.succeed();
    }
}
