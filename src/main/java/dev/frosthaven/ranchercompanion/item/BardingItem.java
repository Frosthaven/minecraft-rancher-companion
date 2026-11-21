package dev.frosthaven.ranchercompanion.item;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Drekki armor for one stage (spec, Barding): Fledgling barding fits stage 2, Mature barding stage 3. It soaks up damage
 * into its durability (see DrekkiEntity.actuallyHurt) and is repaired on an anvil with its material.
 */
public class BardingItem extends Item {
    public final Barding tier;
    public final int stage;

    public BardingItem(Barding tier, int stage, Properties props) {
        super(props.stacksTo(1).durability(tier.durability(stage)));
        this.tier = tier;
        this.stage = stage;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.rancher_companion.barding.knockback").withStyle(net.minecraft.ChatFormatting.BLUE));
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
