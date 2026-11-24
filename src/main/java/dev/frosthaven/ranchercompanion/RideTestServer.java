package dev.frosthaven.ranchercompanion;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The server's part of client/RideTest (-Drancher_companion.ridetest=true): each player's own drekki near them is a saddled Mature. */
final class RideTestServer {
    private RideTestServer() {}
    static final boolean ON = Boolean.getBoolean("rancher_companion.ridetest");

    static void tick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers())
            for (DrekkiEntity d : p.serverLevel().getEntitiesOfClass(DrekkiEntity.class, p.getBoundingBox().inflate(48), d -> p.getUUID().equals(d.owner()) && d.isOut() && !d.rideable())) {
                d.life.ghost = false; d.syncLife();
                d.setLook(3, d.line(), d.diet(), d.astral(), d.horn());
                d.applyStageHealth(true);
                d.setSaddle(new ItemStack(Items.SADDLE));
                d.setStaying(false);
            }
    }
}
