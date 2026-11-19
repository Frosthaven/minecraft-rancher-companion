package dev.frosthaven.ranchercompanion.life;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** The game events a drekki's mood listens to (spec, Mood): its own damage, and hostile mobs dying near it. */
public final class LifeEvents {
    private LifeEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(LivingDamageEvent.Post.class, e -> {
            if (e.getEntity() instanceof DrekkiEntity d && d.owned() && !d.level().isClientSide && e.getNewDamage() > 0) Vitals.hurt(d, e.getNewDamage());
        });
        bus.addListener(LivingDeathEvent.class, e -> {
            if (!e.getEntity().level().isClientSide && e.getEntity().getType().getCategory() == MobCategory.MONSTER) Vitals.hostileDied(e.getEntity());
        });
    }
}
