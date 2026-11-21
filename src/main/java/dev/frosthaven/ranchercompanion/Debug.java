package dev.frosthaven.ranchercompanion;

/**
 * Extra log lines for chasing a drekki a player cannot see (2026-11-21): what the server sends to whom, and what a
 * game receives, bakes and draws. Off unless the game starts with -Drancher_companion.debug=true or the config folder
 * holds a file named rancher_companion-debug (an empty file will do; read once at start, on either side).
 */
public final class Debug {
    private Debug() {}

    public static final boolean ON = Boolean.getBoolean("rancher_companion.debug")
        || java.nio.file.Files.exists(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("rancher_companion-debug"));

    public static void log(String msg, Object... args) { if (ON) RancherCompanion.LOG.info("[drekki debug] " + msg, args); }

    /** "id 12 (Rosey) at 1, 2, 3 in minecraft:overworld" */
    public static String of(net.minecraft.world.entity.Entity e) {
        return "id " + e.getId() + (e.hasCustomName() ? " (" + e.getCustomName().getString() + ")" : "") + " at " + e.blockPosition().toShortString()
            + " in " + e.level().dimension().location();
    }
}
