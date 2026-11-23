package dev.frosthaven.ranchercompanion.combat;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

/**
 * The Elemental ball (spec, Combat: Cooked and Root drekki from stage 2): a magenta ball shot from the mouth, flying
 * straight, magic damage on a hit. It never sets anything on fire. It passes through its drekki and the drekki's owner.
 */
public final class ElementalBall extends ThrowableProjectile {
    public static final float DAMAGE = 3;
    private static final Vector3f MAGIC = new Vector3f(1f, 0.247f, 0.839f), MAGIC_L = new Vector3f(1f, 0.69f, 0.933f);

    public ElementalBall(EntityType<? extends ElementalBall> type, Level level) { super(type, level); }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder b) {}

    @Override
    protected double getDefaultGravity() { return 0; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 2; i++) level().addParticle(new DustParticleOptions(random.nextBoolean() ? MAGIC : MAGIC_L, 1.1f),
                getX() + (random.nextDouble() - 0.5) * 0.2, getY() + 0.15 + (random.nextDouble() - 0.5) * 0.2, getZ() + (random.nextDouble() - 0.5) * 0.2, 0, 0, 0);
        } else if (tickCount > 60) discard();
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

    /** a hit at once, never added to the world: the target is too close for a ball in flight */
    public void strike(LivingEntity t) {
        land(t);
        burst();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        burst();
    }

    private void burst() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new DustParticleOptions(MAGIC, 1.2f), getX(), getY() + 0.15, getZ(), 16, 0.2, 0.2, 0.2, 0.05);
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.15, getZ(), 4, 0.1, 0.1, 0.1, 0.05);
            playSound(dev.frosthaven.ranchercompanion.DrekkiSounds.FIRE_HIT.get(), 0.6f, 1.2f);
            discard();
        }
    }
}
