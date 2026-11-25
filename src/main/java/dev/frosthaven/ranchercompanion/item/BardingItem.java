package dev.frosthaven.ranchercompanion.item;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Drekki armor (spec, Barding): one item per tier that Fledglings and Matures both wear (since 0.6.0; Fledgling and
 * Mature barding before). It soaks up damage into its durability (see DrekkiEntity.actuallyHurt; the Mature's durability,
 * worn faster on a Fledgling by {@link Barding#wearScale}) and is repaired on an anvil with its material.
 */
public class BardingItem extends Item {
    public final Barding tier;

    public BardingItem(Barding tier, Properties props) {
        super(props.stacksTo(1).durability(tier.matureDurability));
        this.tier = tier;
    }

    /** A legacy Fledgling barding id (0.4.0 to 0.5.x) as the one barding of its tier, every component kept (both dye
     *  channels, enchantments, wear); any other stack as it is. Its damage then counts against the Mature's durability:
     *  an old Fledgling barding comes back a little less worn, never more. */
    public static ItemStack current(ItemStack s) {
        if (!(s.getItem() instanceof BardingItem b) || s.getItem() != DrekkiItems.LEGACY_FLEDGLING_BARDING.get(b.tier).get()) return s;
        return s.transmuteCopy(DrekkiItems.BARDING.get(b.tier).get(), s.getCount());
    }

    /** a legacy stack in a player's inventory turns into the one barding there */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof net.minecraft.world.entity.player.Player p)) return;
        ItemStack now = current(stack);
        if (now != stack && p.getInventory().getItem(slot) == stack) p.getInventory().setItem(slot, now);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.rancher_companion.barding.knockback").withStyle(net.minecraft.ChatFormatting.BLUE));
    }

    /** how well it takes enchantments at a table, its material's armor value in the game (spec, Barding: enchantments) */
    @Override
    public int getEnchantmentValue() {
        return switch (tier) { case WOOD, LEATHER, NETHERITE -> 15; case STONE -> 5; case IRON -> 9; case GOLD -> 25; case DIAMOND -> 10; };
    }

    @Override
    public boolean isValidRepairItem(ItemStack self, ItemStack repair) {
        return switch (tier) {
            case WOOD -> repair.is(ItemTags.PLANKS);
            case LEATHER -> repair.is(Items.LEATHER);
            case STONE -> repair.is(ItemTags.STONE_TOOL_MATERIALS);
            case IRON -> repair.is(Items.IRON_INGOT);
            case GOLD -> repair.is(Items.GOLD_INGOT);
            case DIAMOND -> repair.is(Items.DIAMOND);
            case NETHERITE -> repair.is(Items.NETHERITE_INGOT);
        };
    }
}
