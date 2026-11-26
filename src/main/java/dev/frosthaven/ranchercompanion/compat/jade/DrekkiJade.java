package dev.frosthaven.ranchercompanion.compat.jade;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

/**
 * Jade (optional): a drekki's food as a row of vanilla shanks under Jade's hearts (spec, Growth release), and a
 * tombstone's drekki and date (spec, Tombstone). Jade finds this
 * class by its annotation and loads it only when Jade is installed; the food is the entity's synched value.
 */
@WailaPlugin
public final class DrekkiJade implements IWailaPlugin {
    static final ResourceLocation FOOD_UID = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "food");

    @Override
    public void registerClient(IWailaClientRegistration r) {
        r.registerEntityComponent(Food.INSTANCE, DrekkiEntity.class);
        r.registerBlockComponent(Tomb.INSTANCE, dev.frosthaven.ranchercompanion.block.TombstoneBlock.class);
    }

    static final ResourceLocation TOMB_UID = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "tombstone");

    /** the name and date the client already has (the block entity's update tag) */
    enum Tomb implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity be)
                dev.frosthaven.ranchercompanion.item.TombstoneItem.lines(be.name(), be.date(), be.message()).forEach(tooltip::add);
        }

        @Override
        public ResourceLocation getUid() { return TOMB_UID; }
    }

    enum Food implements IEntityComponentProvider {
        INSTANCE;

        private static final ResourceLocation FULL = ResourceLocation.withDefaultNamespace("hud/food_full"),
            HALF = ResourceLocation.withDefaultNamespace("hud/food_half"), EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            if (!(accessor.getEntity() instanceof DrekkiEntity d) || !d.live()) return;
            IElementHelper h = IElementHelper.get();
            float food = Life.shownFood(d.food());
            for (int i = 0; i < Life.FOOD_MAX; i++) {
                ResourceLocation s = food >= i + 1 ? FULL : food >= i + 0.5f ? HALF : EMPTY;
                var el = h.sprite(s, 9, 9).size(new net.minecraft.world.phys.Vec2(8, 9));
                if (i == 0) tooltip.add(el); else tooltip.append(el);
            }
        }

        @Override
        public ResourceLocation getUid() { return FOOD_UID; }

        /** after Jade's health (its own entity health provider sits at -4500) */
        @Override
        public int getDefaultPriority() { return -4400; }
    }
}
