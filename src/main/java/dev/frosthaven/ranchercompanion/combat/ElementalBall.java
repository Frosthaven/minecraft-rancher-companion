package dev.frosthaven.ranchercompanion.combat;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Elemental ball (spec, Combat: Cooked and Root drekki from stage 2): a magenta ball shot from the mouth, flying
 * straight, magic damage on a hit. It never sets anything on fire. It passes through its drekki and the drekki's owner.
 */
public final class ElementalBall extends ThrowableProjectile {
    public static final float DAMAGE = 3;

    public ElementalBall(EntityType<? extends ElementalBall> type, Level level) { super(type, level); }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {}

    @Override
    protected double getDefaultGravity() { return 0; }

    /** the game's side: the ball's look, each tick it flies (false) and where it ends (true). The page's magenta cube
     *  and its cube sparks (client/DrekkiParticles), not the game's dust */
    public static java.util.function.BiConsumer<ElementalBall, Boolean> look;
    /** a hit at once: in the world for one tick only, so every game near sees it burst */
    private boolean spent;

    @Override
    public void tick() {
        if (!level().isClientSide && spent) { discard(); return; }
        super.tick();
        if (level().isClientSide) { if (look != null) look.accept(this, false); }
        else if (tickCount > 60) discard();
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        if (look != null) look.accept(this, true);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = getOwner();
        if (e == owner) return false;
        if (owner instanceof DrekkiEntity d && d.owner() != null && d.owner().equals(e.getUUID())) return false;
        return super.canHitEntity(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity owner = getOwner();
        if (hit.getEntity() instanceof LivingEntity t) land(t);
    }

    /** the ball's hit; a drekki's carries its barding's sword enchantments (item/BardingEnchants) */
    private void land(LivingEntity t) {
        var source = damageSources().indirectMagic(this, getOwner());
        if (getOwner() instanceof dev.frosthaven.ranchercompanion.entity.DrekkiEntity d) dev.frosthaven.ranchercompanion.item.BardingEnchants.strike(d, t, source, DAMAGE, false);
        else t.hurt(source, DAMAGE);
    }

    /** a hit at once: the target is too close for a ball in flight. It bursts on the target */
    public void strike(LivingEntity t) {
        land(t);
        setPos(t.getX(), t.getY(0.5), t.getZ());
        spent = true;
        level().addFreshEntity(this);
        playSound(dev.frosthaven.ranchercompanion.DrekkiSounds.FIRE_HIT.get(), 0.6f, 1.2f);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        burst();
    }

    private void burst() {
        if (level() instanceof ServerLevel) {
            playSound(dev.frosthaven.ranchercompanion.DrekkiSounds.FIRE_HIT.get(), 0.6f, 1.2f);
            discard();
        }
    }
}
