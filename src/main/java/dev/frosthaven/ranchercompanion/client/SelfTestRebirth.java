package dev.frosthaven.ranchercompanion.client;

import static dev.frosthaven.ranchercompanion.client.SelfTest.mc;
import static dev.frosthaven.ranchercompanion.client.SelfTest.quiet;
import static dev.frosthaven.ranchercompanion.client.SelfTest.step;
import static dev.frosthaven.ranchercompanion.client.SelfTest.useOn;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.LotusCropBlock;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen;
import dev.frosthaven.ranchercompanion.client.screen.HatcheryScreen;
import dev.frosthaven.ranchercompanion.client.screen.TombstoneScreen;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.CreativeEggItem;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Lineage;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * The way back from death in the real game (with -Drancher_companion.selftest.rebirth=true; spec, Tombstone: the way
 * back): Wisp (Mature) dies and leaves a tombstone; Drekki Lotus Seeds planted on soul soil by it grow in a day on the
 * clock and give a blossom; the blossom in a slot runs the meter, lights the candle with soul fire and raises the see-through
 * ghost (five seconds on each); a Mature at the hatchery 9 blocks away and the ghost preview a Bakeneko;
 * a day later the candle is melted and Reclaim shows; refused while a drekki is out, then done: Wisp alive, with Soulflame on its screen; a Mature Bakeneko
 * ridden: its rider invisible (Spirit Veil); the tombstone's Ponder scenes.
 */
final class SelfTestRebirth {
    private SelfTestRebirth() {}

    private static BlockPos tomb;
    private static java.util.UUID wisp, kit;

    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> f) {
        var srv = mc().getSingleplayerServer();
        java.util.UUID me = mc().player.getUUID();
        if (srv != null) srv.execute(() -> { var p = srv.getPlayerList().getPlayer(me); if (p != null) f.accept(p); });
    }

    private static TombstoneBlockEntity be() { return tomb != null && mc().level.getBlockEntity(tomb) instanceof TombstoneBlockEntity b ? b : null; }

    private static TombstoneScreen ts() { return mc().screen instanceof TombstoneScreen s ? s : null; }

    private static DrekkiEntity client(java.util.UUID id) {
        DrekkiEntity s = id == null ? null : Ranch.liveOf(id);
        if (s == null) return null;
        for (var e : mc().level.entitiesForRendering()) if (e instanceof DrekkiEntity d && d.getUUID().equals(s.getUUID())) return d;
        return null;
    }

    private static void hold(String n) { quiet(n + "_hold", 100, () -> {}); }

    /** a Mature at the hatchery, by egg and hatch, with this look and name; its roster id */
    private static java.util.UUID mature(net.minecraft.server.level.ServerPlayer p, BlockPos hatch, String name, int line, int astral) {
        Ranch.putEgg(p, hatch, CreativeEggItem.egg(4));
        Ranch.hatch(p, hatch);
        var mine = Ranch.roster(p.server).of(p.getUUID());
        Roster.Entry e = mine.get(mine.size() - 1);
        DrekkiEntity d = Ranch.liveOf(e.id);
        d.setCustomName(Component.literal(name));
        d.setLook(3, line, 1, astral, 2);
        d.applyStageHealth(true);
        d.syncLife();
        Ranch.touch(d);
        return e.id;
    }

    /** the Spirit Veil shot's client-side camera */
    private static net.minecraft.world.entity.Entity veilCam;

    /** a command; a "tp @s ... facing x y z" runs anchored at the eyes (it aims from the feet by default, so every view
     *  looked over its target) */
    private static void cmd(String c) {
        SelfTest.cmd(c.startsWith("tp @s ") && c.contains(" facing ") ? "execute anchored eyes run " + c : c);
    }

    /** sets a block on the server (a /setblock that changes nothing prints "Could not set the block" in chat) */
    private static void put(BlockPos pos, String block) {
        BlockPos at = pos.immutable();
        var b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(block));
        server(p -> p.serverLevel().setBlock(at, b.defaultBlockState(), 3));
    }

    /** forward to this time of day (never back: the rebirth day and the fatigue run on the clock) */
    private static void timeTo(long tod) { long now = mc().level.getDayTime() % 24000; cmd("time add " + ((tod - now + 24000) % 24000)); }

    private static Direction face() { return mc().level.getBlockState(tomb).getValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.FACING); }
    private static dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle candle() { return mc().level.getBlockState(tomb).getValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CANDLE); }
    private static int age(BlockPos c) { var st = mc().level.getBlockState(c); return st.getBlock() instanceof LotusCropBlock ? st.getValue(LotusCropBlock.AGE) : -1; }
    private static int count(net.minecraft.world.item.Item it) { int n = 0; for (var st : mc().player.getInventory().items) if (st.is(it)) n += st.getCount(); return n; }
    /** the item's tooltip has this line */
    private static boolean tip(net.minecraft.world.item.Item it, String line) {
        var lines = new net.minecraft.world.item.ItemStack(it).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc().level), mc().player, net.minecraft.world.item.TooltipFlag.NORMAL);
        return lines.stream().anyMatch(c -> c.getString().equals(line));
    }

    private static int seedsBefore;

    /** where the ghost stood when it rose (its wander is measured from there) */
    private static net.minecraft.world.phys.Vec3 ghostAt;

    static void steps(BlockPos hatch) {
        BlockPos spot = hatch.offset(-6, 0, -6);
        quiet("rb_setup", 60, () -> {
            SelfTest.close();
            cmd("time set 6000");
            cmd("gamerule randomTickSpeed 3");
            cmd("gamemode creative");
            cmd("tp @s " + (hatch.getX() - 2.5) + " " + hatch.getY() + " " + (hatch.getZ() + 0.5) + " -90 30");
            server(p -> {
                wisp = mature(p, hatch, "Wisp", 2 * 4 + 2, Astral.SOLAR);
                kit = mature(p, hatch, "Kit", 4, Astral.LUNAR);
                p.closeContainer();
                p.getAbilities().flying = true; p.onUpdateAbilities();  /* it hangs where each teleport puts it, no falls */
                DrekkiEntity w = Ranch.liveOf(wisp);
                /* no setNoAi: its record would carry it into the ghost, which then never wanders */
                w.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
                w.hurt(w.damageSources().genericKill(), 10000);  /* it dies for good: its tombstone */
            });
        });
        step("rb_tombstone", 40, () -> {
            for (BlockPos p : BlockPos.betweenClosed(spot.offset(-3, -1, -3), spot.offset(3, 4, 3)))
                if (mc().level.getBlockEntity(p) instanceof TombstoneBlockEntity) { tomb = p.immutable(); return; }
        }, () -> be() != null && be().showsRecord());
        quiet("rb_near_tomb", 20, () -> cmd("tp @s " + (tomb.getX() - 1.5) + " " + tomb.getY() + " " + (tomb.getZ() - 1.5) + " facing " + (tomb.getX() + 0.5) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5)));
        step("rb_open", 20, () -> useOn(tomb, Direction.NORTH), () -> ts() != null && !ts().running() && !ts().ready());  /* no meter, no button yet */
        hold("rb_open");
        /* the lotus (Shane, 2026-11-26): seeds on soul soil in front of the tombstone (grows) and at its side (does not);
           a day on the clock grows the front one; its harvest gives a seed back and the blossom */
        BlockPos[] crop = new BlockPos[2];
        quiet("rb_soil", 20, () -> { SelfTest.close();
            Direction f = face();
            crop[0] = tomb.relative(f); crop[1] = tomb.relative(f.getClockWise());
            for (BlockPos c : crop) { put(c.below(), "soul_soil"); put(c, "air"); }
            put(tomb.below(), "soul_soil");  /* the rebirth works only on soul soil */
            SelfTest.hotbar(0, "rancher_companion:drekki_lotus_seeds 2"); SelfTest.select(0);
            cmd("tp @s " + (tomb.getX() + 0.5 + f.getStepX() * 3) + " " + (tomb.getY() + 1) + " " + (tomb.getZ() + 0.5 + f.getStepZ() * 3)
                + " facing " + (tomb.getX() + 0.5) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5)); });
        step("rb_tooltips", 10, () -> {}, () -> tip(DrekkiItems.LOTUS_SEEDS.get(), "A strange, familiar scent.") && tip(DrekkiItems.LOTUS_BLOSSOM.get(), "I could decorate the tombstone with this..."));
        quiet("rb_plant_side", 10, () -> useOn(crop[1].below(), Direction.UP));
        step("rb_planted", 20, () -> useOn(crop[0].below(), Direction.UP), () -> mc().level.getBlockState(crop[0]).getBlock() instanceof LotusCropBlock
            && mc().level.getBlockState(crop[1]).getBlock() instanceof LotusCropBlock);
        hold("rb_planted");
        quiet("rb_grow_day", 60, () -> cmd("time add 24100"));
        SelfTest.stepUntil("rb_grown", 20, 10, () -> {}, () -> age(crop[0]) == 3 && age(crop[1]) == 0);  /* in front: grown; at the side: not */
        hold("rb_grown");
        quiet("rb_break", 15, () -> { SelfTest.select(5); seedsBefore = count(DrekkiItems.LOTUS_SEEDS.get()); server(p -> p.serverLevel().destroyBlock(crop[0], true, p)); });
        SelfTest.stepUntil("rb_harvest", 10, 10, () -> cmd("tp @s " + (crop[0].getX() + 0.5) + " " + crop[0].getY() + " " + (crop[0].getZ() + 0.5)),  /* onto the drops */
            () -> {  /* the blossom and one seed back (creative: the planted seeds were not used up, so count from before) */
                int b = count(DrekkiItems.LOTUS_BLOSSOM.get()), sd = count(DrekkiItems.LOTUS_SEEDS.get());
                if (b != 1 || sd != seedsBefore + 1) RancherCompanion.LOG.info("[selftest] rb_harvest waits: blossoms {} seeds {} (before {})", b, sd, seedsBefore);
                return b == 1 && sd == seedsBefore + 1;
            });
        quiet("rb_back_to_tomb", 15, () -> cmd("tp @s " + (tomb.getX() - 1.5) + " " + tomb.getY() + " " + (tomb.getZ() - 1.5) + " facing " + (tomb.getX() + 0.5) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5)));
        step("rb_open_again", 20, () -> useOn(tomb, Direction.NORTH), () -> ts() != null);
        /* the blossom in: the ghost rises, the candle waits ("The candle is ready to be lit."), no meter */
        step("rb_blossom_in", 20, () -> {  /* menu slots: 4 gear, 27 inventory, 9 hotbar */
            var inv = mc().player.getInventory().items;
            for (int i = 0; i < 36; i++) if (inv.get(i).is(DrekkiItems.LOTUS_BLOSSOM.get())) { SelfTest.shiftClick(i < 9 ? 4 + 27 + i : 4 + i - 9); return; }
        }, () -> be().blossomSlot() >= 0 && be().summoned() && be().reviveStart() == 0 && ts().unlit() && !ts().running());
        hold("rb_ready_to_light");
        /* the lotus lies on the plinth's front lip, seen close up from the front */
        quiet("rb_lip_look", 20, () -> { SelfTest.close(); mc().options.hideGui = true; Direction f = face();
            Direction side = f.getCounterClockWise();  /* a little off to one side, the eye just above the plinth: out of the ghost's way */
            server(p -> { DrekkiEntity g = Ranch.liveOf(wisp); if (g != null) g.moveTo(tomb.getX() + 0.5 - f.getStepX() * 2, tomb.getY(), tomb.getZ() + 0.5 - f.getStepZ() * 2); });
            cmd("tp @s " + (tomb.getX() + 0.5 + f.getStepX() * 1.1 + side.getStepX() * 0.7) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5 + f.getStepZ() * 1.1 + side.getStepZ() * 0.7)
                + " facing " + (tomb.getX() + 0.5 + f.getStepX() * 0.3) + " " + (tomb.getY() + 0.15) + " " + (tomb.getZ() + 0.5 + f.getStepZ() * 0.3)); });
        step("rb_lotus_on_lip", 20, () -> {}, () -> be().blossomSlot() >= 0 && candle() == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.UNLIT
            && client(wisp) != null && client(wisp).ghost() && !client(wisp).ghostFlame());  /* the ghost is up, without flames until the candle burns */
        hold("rb_lotus_on_lip");
        /* flint and steel lights the candle: the day starts */
        quiet("rb_flint", 10, () -> { mc().options.hideGui = false; SelfTest.hotbar(1, "flint_and_steel"); SelfTest.select(1);
            cmd("tp @s " + (tomb.getX() - 1.5) + " " + tomb.getY() + " " + (tomb.getZ() - 1.5) + " facing " + (tomb.getX() + 0.5) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5)); });
        step("rb_light", 20, () -> useOn(tomb, face()), () -> be().reviveStart() != 0 && candle() == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.LIT);
        quiet("rb_empty_hand", 5, () -> SelfTest.select(5));
        step("rb_meter", 20, () -> useOn(tomb, Direction.NORTH), () -> ts() != null && ts().running() && !ts().unlit());
        hold("rb_meter");
        quiet("rb_candle_look", 20, () -> { SelfTest.close(); mc().options.hideGui = true; timeTo(18000);  /* midnight: its light shows */
            cmd("tp @s " + (tomb.getX() - 2.5) + " " + (tomb.getY() + 0.3) + " " + (tomb.getZ() - 1.0) + " facing " + (tomb.getX() + 0.6) + " " + (tomb.getY() + 0.3) + " " + (tomb.getZ() + 0.4)); });
        step("rb_candle_lit", 30, () -> {}, () -> mc().level.getBlockState(tomb).getValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CANDLE)
            == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.LIT);
        hold("rb_candle_lit");
        step("rb_ghost_awake", 10, () -> {}, () -> client(wisp) != null && client(wisp).ghost() && !client(wisp).isSleepingLoop());  /* five seconds into midnight: a ghost never sleeps */
        quiet("rb_day_again", 5, () -> timeTo(6000));
        quiet("rb_ghost_look", 20, () -> cmd("tp @s " + (tomb.getX() - 4.5) + " " + (tomb.getY() + 0.8) + " " + (tomb.getZ() - 3.5) + " facing " + (tomb.getX() + 0.5) + " " + (tomb.getY() + 1) + " " + (tomb.getZ() + 0.5)));
        step("rb_ghost_risen", 30, () -> { DrekkiEntity g = client(wisp); if (g != null) ghostAt = g.position(); }, () -> client(wisp) != null && client(wisp).ghost() && client(wisp).ghostFlame());
        /* it walks: its normal wander AI, within 3 blocks of the tombstone */
        SelfTest.stepUntil("rb_ghost_wanders", 40, 25, () -> {}, () -> {
            DrekkiEntity g = client(wisp);
            if (g == null) return false;
            if (ghostAt == null) { ghostAt = g.position(); return false; }
            return g.position().distanceTo(ghostAt) > 1.0 && g.blockPosition().distManhattan(tomb) <= 8;
        });
        /* its screen: no mood, no stance, Stay or Follow buttons (a ghost takes no orders) */
        quiet("rb_ghost_near", 15, () -> { DrekkiEntity g = client(wisp); if (g != null)  /* within reach of it */
            cmd("tp @s " + (g.getX() + 1.5) + " " + g.getY() + " " + (g.getZ() + 1.5) + " facing " + g.getX() + " " + (g.getY() + 1) + " " + g.getZ()); });
        step("rb_ghost_screen", 20, () -> { DrekkiEntity g = client(wisp); if (g != null) mc().gameMode.interact(mc().player, g, net.minecraft.world.InteractionHand.MAIN_HAND); },
            () -> {
                DrekkiEntity g = client(wisp);
                boolean ok = mc().screen instanceof DrekkiScreen d && d.spot("stance0") == null && d.spot("stay") == null && d.spot("follow") == null && d.spot("mood") == null;
                if (!ok) RancherCompanion.LOG.info("[selftest] rb_ghost_screen waits: screen {} distance {}", mc().screen, g == null ? -1 : Math.sqrt(mc().player.distanceToSqr(g)));
                return ok;
            });
        hold("rb_ghost_screen");
        quiet("rb_ghost_screen_close", 10, SelfTest::close);
        hold("rb_ghost");
        quiet("rb_hud_on", 5, () -> mc().options.hideGui = false);
        /* the ghost at its tombstone pairs at the hatchery 9 blocks away: a Bakeneko */
        quiet("rb_to_hatchery", 20, () -> { mc().options.hideGui = false;
            cmd("tp @s " + (hatch.getX() - 2.5) + " " + hatch.getY() + " " + (hatch.getZ() + 0.5) + " -90 30"); });
        step("rb_hatchery", 20, () -> useOn(hatch, Direction.NORTH), () -> mc().screen instanceof HatcheryScreen);
        step("rb_breed_tab", 15, () -> ((HatcheryScreen) mc().screen).press("tab_breeding"), () -> ((HatcheryScreen) mc().screen).getMenu().breeding);
        step("rb_ghost_listed", 15, () -> ((HatcheryScreen) mc().screen).press("parent0"),
            () -> ((HatcheryScreen) mc().screen).pickerNames().contains("Wisp"));
        step("rb_bakeneko_preview", 20, () -> {
            HatcheryScreen h = (HatcheryScreen) mc().screen;
            h.press("pick" + h.pickerNames().indexOf("Wisp"));
            h.press("parent1");
            h.press("pick" + h.pickerNames().indexOf("Kit"));
        }, () -> ((HatcheryScreen) mc().screen).parentName(0).equals("Wisp") && ((HatcheryScreen) mc().screen).parentName(1).equals("Kit"));
        hold("rb_bakeneko_preview");
        /* the reclaim: refused while the test's pup is out, then done after the day */
        quiet("rb_day", 30, () -> { SelfTest.close(); cmd("time add 24100");
            cmd("tp @s " + (tomb.getX() - 1.5) + " " + tomb.getY() + " " + (tomb.getZ() - 1.5) + " facing " + (tomb.getX() + 0.5) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5)); });
        step("rb_candle_melted", 30, () -> { mc().options.hideGui = true;
                cmd("tp @s " + (tomb.getX() - 2.5) + " " + (tomb.getY() + 0.3) + " " + (tomb.getZ() - 1.0) + " facing " + (tomb.getX() + 0.6) + " " + (tomb.getY() + 0.3) + " " + (tomb.getZ() + 0.4)); },
            () -> mc().level.getBlockState(tomb).getValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CANDLE) == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.MELTED);
        hold("rb_candle_melted");
        quiet("rb_melted_hud", 10, () -> { mc().options.hideGui = false;
            cmd("tp @s " + (tomb.getX() - 1.5) + " " + tomb.getY() + " " + (tomb.getZ() - 1.5) + " facing " + (tomb.getX() + 0.5) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5)); });
        step("rb_ready", 20, () -> useOn(tomb, Direction.NORTH), () -> ts() != null && ts().ready() && !ts().running());
        hold("rb_ready");
        step("rb_reclaim_refused", 30, () -> { int[] c = ts().buttonSpot(); ts().mouseClicked(c[0], c[1], 0); },
            () -> be() != null && be().showsRecord());  /* the test's pup is out: refused, still a ghost */
        quiet("rb_pup_home", 20, () -> server(p -> { var out = Ranch.roster(p.server).out(p.getUUID()); if (out != null) Ranch.act(p, hatch, Ranch.Action.RELEASE, out.id); p.closeContainer(); }));
        step("rb_reopen", 20, () -> useOn(tomb, Direction.NORTH), () -> ts() != null && ts().ready());  /* the release opened the hatchery's screen */
        SelfTest.stepUntil("rb_reclaim", 40, 5, () -> { if (ts() != null) { int[] c = ts().buttonSpot(); ts().mouseClicked(c[0], c[1], 0); } },
            () -> {
                DrekkiEntity w = client(wisp);
                boolean inert = be() != null && !be().showsRecord(), alive = w != null && !w.ghost(), out = w != null && w.move() != 0;  /* checked out, as synched (isOut is server-side) */
                long ghosts = mc().level.getEntitiesOfClass(DrekkiEntity.class, mc().player.getBoundingBox().inflate(48), DrekkiEntity::ghost).size();
                long[] serverGhosts = {-1};
                server(pl -> serverGhosts[0] = pl.serverLevel().getEntitiesOfClass(DrekkiEntity.class, pl.getBoundingBox().inflate(48), x -> x.life.ghost).size());
                if (!(inert && alive && out && ghosts == 0)) dev.frosthaven.ranchercompanion.RancherCompanion.LOG.info("[selftest] rb_reclaim waits: inert {} alive {} out {} client ghosts {} server ghosts {}", inert, alive, out, ghosts, serverGhosts[0]);
                return inert && alive && out && ghosts == 0;
            });
        quiet("rb_close", 10, SelfTest::close);
        /* the used tombstone: cracked from the top down to the base, its candle melted for good */
        quiet("rb_crack_look", 15, () -> { mc().options.hideGui = true; Direction f = face();
            cmd("tp @s " + (tomb.getX() + 0.5 + f.getStepX() * 1.8) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5 + f.getStepZ() * 1.8)
                + " facing " + (tomb.getX() + 0.5) + " " + (tomb.getY() + 0.4) + " " + (tomb.getZ() + 0.5)); });
        step("rb_cracked", 20, () -> {}, () -> mc().level.getBlockState(tomb).getValue(dev.frosthaven.ranchercompanion.block.TombstoneBlock.CRACKED)
            && candle() == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.UNLIT);  /* its candle whole again, unlit */
        hold("rb_cracked");
        /* decorative now: anyone lights the candle (light 3, no cyan), at midnight to see its light; sneak + empty hand puts it out */
        quiet("rb_decor_flint", 10, () -> { timeTo(18000); SelfTest.hotbar(1, "flint_and_steel"); SelfTest.select(1); });
        /* the key hints (haven-create-ui-ux, when loaded): "Pay Respects" with flint and steel at the unlit candle */
        boolean hints = net.neoforged.fml.ModList.get().isLoaded("haven_create_ui_ux");
        if (hints) {
            quiet("rb_hint_look", 15, () -> { mc().options.hideGui = false; Direction f = face();
                server(p -> { DrekkiEntity w = Ranch.liveOf(wisp); if (w != null) { w.setStaying(true);  /* out of the view */
                    w.moveTo(tomb.getX() + 0.5 + f.getCounterClockWise().getStepX() * 6, tomb.getY(), tomb.getZ() + 0.5 + f.getCounterClockWise().getStepZ() * 6); } });
                cmd("tp @s " + (tomb.getX() + 0.5 + f.getStepX() * 2.2) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5 + f.getStepZ() * 2.2)
                    + " facing " + (tomb.getX() + 0.5) + " " + (tomb.getY() + 0.35) + " " + (tomb.getZ() + 0.5)); });
            step("rb_hint_pay_respects", 20, () -> {}, () -> mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult h && h.getBlockPos().equals(tomb)
                && candle() == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.UNLIT);
            hold("rb_hint_pay_respects");
        }
        step("rb_decor_lit", 20, () -> useOn(tomb, face()), () -> candle() == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.LIT
            && mc().level.getBlockState(tomb).getLightEmission(mc().level, tomb) == 3 && be().reviveStart() == 0);
        quiet("rb_decor_look", 15, () -> { Direction f = face();  /* back to the front view: the lit candle at midnight */
            cmd("tp @s " + (tomb.getX() + 0.5 + f.getStepX() * 2.2) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5 + f.getStepZ() * 2.2)
                + " facing " + (tomb.getX() + 0.5) + " " + (tomb.getY() + 0.35) + " " + (tomb.getZ() + 0.5)); });
        step("rb_decor_light", 10, () -> {}, () -> candle() == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.LIT);
        hold("rb_decor_lit");
        quiet("rb_decor_empty", 5, () -> SelfTest.select(5));
        /* "Put out candle": empty hand, sneak held, at the lit candle */
        if (hints) {
            quiet("rb_hint_sneak", 10, () -> { mc().options.hideGui = false; mc().options.keyShift.setDown(true); });
            step("rb_hint_put_out", 20, () -> {}, () -> mc().player.isShiftKeyDown() && mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult h && h.getBlockPos().equals(tomb));
            hold("rb_hint_put_out");
            quiet("rb_hint_unsneak", 5, () -> { mc().options.keyShift.setDown(false); server(p -> { DrekkiEntity w = Ranch.liveOf(wisp); if (w != null) w.setStaying(false); }); });
        }
        step("rb_decor_out", 20, () -> server(p -> {  /* sneak + empty hand, as the server sees it */
            p.setShiftKeyDown(true);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(tomb), face(), tomb, false);
            p.serverLevel().getBlockState(tomb).useWithoutItem(p.serverLevel(), p, hit);
            p.setShiftKeyDown(false);
        }), () -> candle() == dev.frosthaven.ranchercompanion.block.TombstoneBlock.Candle.UNLIT);
        quiet("rb_crack_done", 5, () -> { mc().options.hideGui = false; timeTo(6000); });
        /* its message: typed under the slots, kept by the tombstone (Jade shows it on hover) */
        step("rb_message_open", 20, () -> useOn(tomb, face()), () -> ts() != null);
        step("rb_message", 30, () -> { ts().typeMessage("Rest well, Wisp."); ts().sendMessage(); },
            () -> be() != null && "Rest well, Wisp.".equals(be().message()));
        hold("rb_message");
        quiet("rb_message_close", 10, SelfTest::close);
        /* in the world: looking at the tombstone, Jade's hover shows the message (with Jade loaded: -PwithJade) */
        if (net.neoforged.fml.ModList.get().isLoaded("jade")) {
            quiet("rb_message_look", 15, () -> { mc().options.hideGui = false; Direction f = face();
                server(p -> { DrekkiEntity w = Ranch.liveOf(wisp); if (w != null) { w.setStaying(true);  /* out of the line of sight */
                    w.moveTo(tomb.getX() + 0.5 + f.getClockWise().getStepX() * 5, tomb.getY(), tomb.getZ() + 0.5 + f.getClockWise().getStepZ() * 5); } });
                cmd("tp @s " + (tomb.getX() + 0.5 + f.getStepX() * 2.5) + " " + tomb.getY() + " " + (tomb.getZ() + 0.5 + f.getStepZ() * 2.5)
                    + " facing " + (tomb.getX() + 0.5) + " " + (tomb.getY() + 0.5) + " " + (tomb.getZ() + 0.5)); });
            step("rb_message_hover", 30, () -> {}, () -> mc().hitResult instanceof net.minecraft.world.phys.BlockHitResult h && h.getBlockPos().equals(tomb)
                && be() != null && "Rest well, Wisp.".equals(be().message()));
            hold("rb_message_hover");
            quiet("rb_message_done", 5, () -> server(p -> { DrekkiEntity w = Ranch.liveOf(wisp); if (w != null) w.setStaying(false); }));
        }
        step("rb_soulflame", 30, () -> { DrekkiEntity w = client(wisp); if (w == null) return;
                cmd("tp @s " + (w.getX() + 1.5) + " " + w.getY() + " " + (w.getZ() + 1.5));
                mc().gameMode.interact(mc().player, w, net.minecraft.world.InteractionHand.MAIN_HAND); },
            () -> mc().screen instanceof DrekkiScreen d && d.spot("soulflame") != null);
        step("rb_soulflame_tip", 15, () -> DrekkiScreen.testMouse = ((DrekkiScreen) mc().screen).spot("soulflame"),
            () -> mc().screen instanceof DrekkiScreen d && d.shownTip() != null && d.shownTip().get(0).getString().equals("Resurrection Fatigue"));
        hold("rb_soulflame");
        /* Spirit Veil, seen from outside (a client-side camera 5 blocks off; the rider's own view sits in the Bakeneko):
           first ridden without the trait, the rider shows; then with it, the real Invisibility effect (its icon on the
           HUD) and no rider drawn */
        quiet("rb_bakeneko", 30, () -> { DrekkiScreen.testMouse = null; SelfTest.close(); server(p -> {
            DrekkiEntity w = Ranch.liveOf(wisp);
            if (w == null) return;
            w.setNoAi(true);  /* it stands still for the camera */
            w.setLook(3, Lineage.BAKENEKO, w.diet(), w.astral(), w.horn());
            w.life.traits.clear();
            w.setSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE));
            w.syncLife(); Ranch.touch(w);
            p.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            p.startRiding(w);
        }); });
        quiet("rb_veil_cam", 10, () -> {
            var pl = mc().player;
            var cam = new net.minecraft.world.entity.decoration.ArmorStand(mc().level, pl.getX() + 4, pl.getY() + 0.5, pl.getZ() + 4);
            cam.setInvisible(true); cam.setNoGravity(true);
            cam.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, pl.position().add(0, 0.6, 0));
            cam.yRotO = cam.getYRot(); cam.xRotO = cam.getXRot(); cam.yHeadRot = cam.yHeadRotO = cam.getYRot();
            mc().level.addEntity(cam);
            veilCam = cam;
            mc().setCameraEntity(cam);
        });
        step("rb_rider_seen", 20, () -> {}, () -> mc().getCameraEntity() == veilCam && mc().player.isPassenger()
            && !mc().player.isInvisible() && !mc().player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY));
        hold("rb_rider_seen");
        quiet("rb_veil_on", 5, () -> server(p -> {
            DrekkiEntity w = Ranch.liveOf(wisp);
            if (w == null) return;
            w.life.traits.add("spirit_veil");
            w.syncLife(); Ranch.touch(w);
        }));
        step("rb_spirit_veil", 30, () -> {}, () -> {
            var fx = mc().player.getEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            return mc().getCameraEntity() == veilCam && mc().player.isPassenger() && fx != null && fx.showIcon() && mc().player.isInvisible();
        });
        hold("rb_spirit_veil");
        quiet("rb_dismount", 20, () -> { mc().setCameraEntity(mc().player); if (veilCam != null) veilCam.discard(); veilCam = null; server(p -> p.stopRiding()); });
        /* the Ponder scenes: the tombstone's three (only with Ponder loaded: the Haven pack, not the plain dev run) */
        if (net.neoforged.fml.ModList.get().isLoaded("ponder")) {
            step("rb_ponder", 40, () -> dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.open(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.TOMBSTONE.getId()),
                () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("PonderUI"));
            quiet("rb_ponder_wait1", 375, () -> {});
            step("rb_ponder_soul_soil", 10, () -> {}, () -> true);  /* scene 1's last line: soul soil */
            quiet("rb_ponder_hold1", 90, () -> {});
            quiet("rb_ponder_next", 10, dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder::next);
            quiet("rb_ponder_wait2", 165, () -> {});
            step("rb_ponder_lotus", 10, () -> {}, () -> true);  /* scene 2: in front of the tombstone, growing */
            quiet("rb_ponder_hold2", 160, () -> {});
            quiet("rb_ponder_next2", 10, dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder::next);
            quiet("rb_ponder_wait3", 115, () -> {});
            step("rb_ponder_ghost", 10, () -> {}, () -> true);  /* scene 3: the blossom on the grave, the ghost */
            quiet("rb_ponder_wait4", 120, () -> {});
            step("rb_ponder_sense_kin", 10, () -> {}, () -> true);  /* "Mature ghosts may be able to sense kin at a nearby hatchery." */
            quiet("rb_ponder_wait5", 280, () -> {});
            step("rb_ponder_fatigue", 10, () -> {}, () -> true);  /* "Back from death, it carries Resurrection Fatigue for a day..." */
            quiet("rb_ponder_wait6", 120, () -> {});
            step("rb_ponder_cracked", 10, () -> {}, () -> true);  /* the cracked tombstone, decoration now */
            quiet("rb_ponder_hold3", 120, () -> {});
            /* the whistle's scenes: its commands, then Create's jobs */
            quiet("rb_ponder_close_tomb", 10, SelfTest::close);
            step("rb_ponder_whistle", 40, () -> dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.open(dev.frosthaven.ranchercompanion.item.DrekkiItems.WHISTLE.getId()),
                () -> mc().screen != null && mc().screen.getClass().getSimpleName().equals("PonderUI"));
            quiet("rb_whistle_wait1", 100, () -> {});
            step("rb_whistle_stay", 10, () -> {}, () -> true);  /* use: stay or follow */
            quiet("rb_whistle_wait2", 110, () -> {});
            step("rb_whistle_go_here", 10, () -> {}, () -> true);  /* sneak use on a block */
            quiet("rb_whistle_wait3", 230, () -> {});
            step("rb_whistle_attack", 10, () -> {}, () -> true);  /* sneak use on a hostile mob */
            quiet("rb_whistle_wait4", 120, () -> {});
            quiet("rb_whistle_next", 10, dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder::next);
            quiet("rb_whistle_wait5", 90, () -> {});
            step("rb_whistle_seat", 10, () -> {}, () -> true);  /* sits on a seat */
            quiet("rb_whistle_wait6", 130, () -> {});
            step("rb_whistle_hired", 10, () -> {}, () -> true);  /* a Stock keeper */
            quiet("rb_whistle_wait7", 110, () -> {});
            step("rb_whistle_hunger", 10, () -> {}, () -> true);  /* no hunger at work */
            quiet("rb_whistle_wait8", 120, () -> {});
            step("rb_whistle_recall", 10, () -> {}, () -> true);  /* recall */
            quiet("rb_whistle_hold", 60, () -> {});
            quiet("rb_ponder_close", 10, SelfTest::close);
        }
        quiet("rb_end", 10, () -> { mc().options.hideGui = false; SelfTest.close(); RancherCompanion.LOG.info("[selftest] rebirth end"); });
    }
}
