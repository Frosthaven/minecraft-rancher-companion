package dev.frosthaven.ranchercompanion.look;

/** The numbers of barding's enchantments, as the game counts them on a player's armor and sword. Pure, unit tested. */
public final class EnchantMath {
    private EnchantMath() {}

    /** the share of a hit the protections take off: 4% a level of Protection, 8% a level of the one that fits the
     *  damage, 80% at most */
    public static float guard(int protection, int fire, int blast, int projectile, boolean isFire, boolean isBlast, boolean isProjectile) {
        return Math.min(0.8f, 0.04f * protection + 0.08f * ((isFire ? fire : 0) + (isBlast ? blast : 0) + (isProjectile ? projectile : 0)));
    }

    /** the damage Sharpness, Smite and Bane of Arthropods add to a hit */
    public static float bonus(int sharpness, int smite, int bane, boolean undead, boolean arthropod) {
        return (sharpness > 0 ? 0.5f * sharpness + 0.5f : 0) + (undead ? 2.5f * smite : 0) + (arthropod ? 2.5f * bane : 0);
    }

    /** the share of a hit that Sweeping Edge gives the mobs beside the target */
    public static float sweep(int level) { return level <= 0 ? 0 : level / (level + 1f); }
}
