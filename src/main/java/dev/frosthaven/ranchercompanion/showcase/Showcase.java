package dev.frosthaven.ranchercompanion.showcase;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.look.Gear;
import dev.frosthaven.ranchercompanion.look.Lineage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Spawns the two showcase groups: every lineage at every stage, and every loop at every stage that can do it,
 * with stand-ins for what the abilities play against. With {@code -Drancher_companion.showcase=true} it also runs
 * on login and walks the player through the camera shots while the client takes the screenshots.
 */
public final class Showcase {
    private Showcase() {}

    public static final boolean AUTO = Boolean.getBoolean("rancher_companion.showcase");
    private static ServerPlayer driver;
    private static int tick;

    /** Both groups at the layout's fixed places; the origin is ignored when the world is the showcase world. */
    public static int spawn(ServerLevel level, Vec3 origin) {
        int n = 0;
        for (int fam = 0; fam < Lineage.FAMILIES; fam++) for (int tier = 0; tier < Lineage.TIERS; tier++) for (int stage = 1; stage <= 3; stage++) {
            int line = fam * Lineage.TIERS + tier; Lineage L = Lineage.of(line);
            n += drekki(level, Layout.AX + tier * Layout.TIER_DX + Layout.STAGE_DX[stage - 1], Layout.rowZ(fam), Layout.A_YAW, stage, line, tier, L.astral(), tier, Anim.IDLE, 0, L.name() + " " + stage) ? 1 : 0;
        }
        for (int r = 0; r < Lineage.SPECIALS; r++) for (int stage = 1; stage <= 3; stage++) {
            int line = Lineage.BLOODLINES + r; Lineage L = Lineage.of(line);
            n += drekki(level, Layout.AX + (r % 5) * Layout.TIER_DX + Layout.STAGE_DX[stage - 1], Layout.rareRowZ(r), Layout.A_YAW, stage, line, r % 4, L.astral(), r % 5, Anim.IDLE, 0, L.name() + " " + stage) ? 1 : 0;
        }
        n += gear(level);
        for (int stage = 1; stage <= 3; stage++) {
            List<Anim> anims = Layout.animsAt(stage); double k = Anim.worldK(stage), z = Layout.stageRowZ(stage);
            for (int i = 0; i < anims.size(); i++) {
                Anim a = anims.get(i); double x = Layout.BX + i * Layout.ANIM_DX[stage - 1];
                int diet = switch (a) { case BITE, POUNCE, RUN, FALL, GLIDE, DASH, SLEEP -> 0; case BALL, BREATH -> 1; case GUARD, WALK, JUMP, FEED -> 2; case REJUV -> 3; default -> 0; };
                float lane = a.speed != 0 ? (float) Layout.LANE[stage - 1] : 0;
                if (a == Anim.FEED) {
                    double dist = Anim.feedWalk(k) + Anim.feedReach(stage) + (stage < 3 ? 0.52 : 0);
                    double bx = Math.floor(x) + 4; x = bx - dist;  /* spawnDrekki adds half a block, so the drekki lands dist short of the trough's center */
                    level.setBlock(new BlockPos((int) bx, (int) Layout.GROUND, (int) Math.floor(z)), DrekkiBlocks.TROUGH.get().defaultBlockState(), 3);  /* the page's hollow feeder, open toward the drekki */
                }
                double[] f = Layout.forward(Layout.B_YAW), r = Layout.right(Layout.B_YAW);
                for (double[] p : Layout.props(a, k)) standIn(level, x + f[0] * p[0] + r[0] * p[1], z + f[2] * p[0] + r[2] * p[1], Layout.B_YAW + 180, Layout.props(a, k).length > 1);
                n += drekki(level, x, z, Layout.B_YAW, stage, 1 + 4 * (i % 9) + 3, diet, 1 + i % 6, i % 5, a, lane, a.key + " " + stage) ? 1 : 0;
            }
        }
        return n;
    }

    /** group C: a Ruin at every stage in each hat, a Ruin adult in each barding tier (undyed), and every item in a frame */
    private static int gear(ServerLevel level) {
        int ruin = -1, n = 0;
        for (int i = Lineage.BLOODLINES; i < Lineage.COUNT; i++) if (Lineage.of(i).name().equals("Ruin")) ruin = i;
        Lineage L = Lineage.of(ruin);
        for (int h = 1; h < Gear.HATS.length; h++) for (int stage = 1; stage <= 3; stage++) {
            DrekkiEntity d = spawnDrekki(level, Layout.AX + (h - 1) * Layout.GEAR_DX + Layout.STAGE_DX[stage - 1], Layout.HAT_Z, Layout.A_YAW, stage, ruin, (h - 1) % 4, L.astral(), h % 5, Anim.IDLE, 0, Gear.HATS[h] + " " + stage);
            if (d != null) { d.setGear(h, 0, -1); n++; }
        }
        for (int b = 1; b < Gear.BARDING.length; b++) {
            DrekkiEntity d = spawnDrekki(level, Layout.AX + (b - 1) * Layout.BARD_DX, Layout.BARD_Z, Layout.A_YAW, 3, ruin, (b - 1) % 4, L.astral(), b % 5, Anim.IDLE, 0, Gear.BARDING[b] + " barding");
            if (d != null) { d.setGear(0, b, -1); n++; }
        }
        /* the item wall: stone bricks facing the camera, a glow frame on each */
        List<DeferredItem<? extends Item>> items = DrekkiItems.ALL;
        int rows = (items.size() + Layout.WALL_W - 1) / Layout.WALL_W, wz = (int) Layout.WALL_Z, gy = (int) Layout.GROUND;
        for (int i = 0; i < items.size(); i++) {
            int x = (int) Layout.AX + i % Layout.WALL_W, y = gy + rows - i / Layout.WALL_W;
            BlockPos wall = new BlockPos(x, y, wz);
            level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), 3);
            GlowItemFrame f = new GlowItemFrame(level, wall.south(), Direction.SOUTH);
            f.setItem(new ItemStack(items.get(i).get()), false); f.setInvulnerable(true);
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag(); f.saveWithoutId(t); t.putBoolean("Fixed", true); f.load(t);  /* fixed: the item cannot be knocked out */
            level.addFreshEntity(f);
        }
        for (int x = 0; x < Layout.WALL_W; x++) level.setBlock(new BlockPos((int) Layout.AX + x, gy, wz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        return n;
    }

    private static boolean drekki(ServerLevel level, double x, double z, float yaw, int stage, int line, int diet, int astral, int horn, Anim anim, float lane, String name) {
        return spawnDrekki(level, x, z, yaw, stage, line, diet, astral, horn, anim, lane, name) != null;
    }

    private static DrekkiEntity spawnDrekki(ServerLevel level, double x, double z, float yaw, int stage, int line, int diet, int astral, int horn, Anim anim, float lane, String name) {
        DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
        if (d == null) return null;
        d.moveTo(x + 0.5, Layout.GROUND, z + 0.5, yaw, 0);
        d.setLook(stage, line, diet, astral, horn); d.setAnim(anim, lane);
        d.setNoAi(true); d.setInvulnerable(true); d.setPersistenceRequired(); d.setSilent(true);
        d.setCustomName(Component.literal(name)); d.setCustomNameVisible(true);
        d.setYBodyRot(yaw); d.setYHeadRot(yaw);
        return level.addFreshEntity(d) ? d : null;
    }

    /** an armor stand: the training dummy, or a player stand-in with arms */
    private static void standIn(ServerLevel level, double x, double z, float yaw, boolean player) {
        ArmorStand a = new ArmorStand(EntityType.ARMOR_STAND, level);
        a.moveTo(x + 0.5, Layout.GROUND, z + 0.5, yaw, 0);
        a.setNoGravity(true); a.setInvulnerable(true); a.setShowArms(player); a.setNoBasePlate(true);
        level.addFreshEntity(a);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!AUTO || !(e.getEntity() instanceof ServerPlayer p)) return;
        ServerLevel level = p.serverLevel();
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setWeatherParameters(100000, 0, false, false);
        if (level.getEntities(RancherCompanion.DREKKI.get(), d -> true).isEmpty()) spawn(level, p.position());
        p.setGameMode(GameType.SPECTATOR);
        driver = p; tick = 0;
    }

    public static void onServerTick(ServerTickEvent.Post e) {
        if (driver == null) return;
        tick++;
        List<Layout.Shot> shots = Layout.shots();
        int i = (tick - Layout.FIRST_TICK) / Layout.SHOT_TICKS;
        if (tick >= Layout.FIRST_TICK && (tick - Layout.FIRST_TICK) % Layout.SHOT_TICKS == 0 && i < shots.size()) {
            Layout.Shot s = shots.get(i);
            driver.serverLevel().setDayTime(s.name().startsWith("night_") ? 18000 : 6000);  /* night shots show the glow */
            driver.teleportTo(driver.serverLevel(), s.x(), s.y(), s.z(), s.yaw(), s.pitch());
        }
    }
}
