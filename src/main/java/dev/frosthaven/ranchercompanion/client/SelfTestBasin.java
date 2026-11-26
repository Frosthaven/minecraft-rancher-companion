package dev.frosthaven.ranchercompanion.client;

import static dev.frosthaven.ranchercompanion.client.SelfTest.cmd;
import static dev.frosthaven.ranchercompanion.client.SelfTest.mc;
import static dev.frosthaven.ranchercompanion.client.SelfTest.quiet;
import static dev.frosthaven.ranchercompanion.client.SelfTest.step;

import com.simibubi.create.content.fluids.potion.PotionFluid;
import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.item.DrekkiPotions;
import dev.frosthaven.ranchercompanion.item.TonicFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The tonics in Create, as Create brews potions (Shane, 2026-11-26; -Drancher_companion.selftest.basin=true, Create in
 * run/mods): a heated Basin with a Mixer turns 1000 mB of Awkward potion fluid and an Amethyst Shard into 1000 mB of
 * Starlight fluid; a Spout fills a glass bottle on a Depot with 250 mB into a tonic; an Item Drain empties a tonic back
 * to 250 mB; a Spout fills a bottle with Horizons potion fluid into a Potion of Horizons. Then the look: both tonic
 * fluids poured side by side, and three Fluid Tanks (Starlight, Heirloom, Horizons).
 */
final class SelfTestBasin {
    private SelfTestBasin() {}

    private static BlockPos o, basin, depot, spoutHorizons, depotHorizons, drain, drainHorizons;

    private static void server(java.util.function.Consumer<ServerLevel> f) {
        var s = mc().getSingleplayerServer();
        if (s != null) s.execute(() -> f.accept(s.overworld()));
    }

    private static BlockState block(String id, String... props) {
        BlockState st = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState();
        for (int i = 0; i + 1 < props.length; i += 2) {
            var p = st.getBlock().getStateDefinition().getProperty(props[i]);
            if (p != null) st = set(st, p, props[i + 1]);
        }
        return st;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState st, net.minecraft.world.level.block.state.properties.Property<T> p, String v) {
        return p.getValue(v).map(x -> st.setValue(p, x)).orElse(st);
    }

    private static FluidStack tonic(TonicFluids.Tonic t, int mb) { return new FluidStack(t.source().get(), mb); }

    private static FluidStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p, int mb) {
        return PotionFluid.of(mb, new PotionContents(p), PotionFluid.BottleType.REGULAR);
    }

    private static int fill(ServerLevel level, BlockPos pos, FluidStack f) {
        var h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        return h == null ? -1 : h.fill(f, IFluidHandler.FluidAction.EXECUTE);
    }

    private static boolean put(ServerLevel level, BlockPos pos, ItemStack s) {
        var h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        return h != null && net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h, s, false).isEmpty();
    }

    /** the fluid a block holds, as "<amount> <fluid id>" summed over its tanks */
    private static String fluids(ServerLevel level, BlockPos pos) {
        var h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        if (h == null) return "none";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < h.getTanks(); i++) { var f = h.getFluidInTank(i); if (!f.isEmpty()) b.append(f.getAmount()).append(' ').append(BuiltInRegistries.FLUID.getKey(f.getFluid())).append(';'); }
        return b.toString();
    }

    /** the mixer's speed and the burner's heat, for a Basin that does not mix */
    private static String machine(ServerLevel level) {
        String speed = level.getBlockEntity(o.above(3)) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? String.valueOf(k.getSpeed()) : "no mixer";
        String cog = level.getBlockEntity(o.offset(1, 3, 0)) instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? String.valueOf(k.getSpeed()) : "no cog";
        return "mixer " + speed + " rpm, cog " + cog + " rpm, heat " + com.simibubi.create.content.processing.burner.BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(o))
            + ", items " + level.getCapability(Capabilities.ItemHandler.BLOCK, basin, Direction.UP).getStackInSlot(0) + ", " + mixer(level);
    }

    /** what the Mixer sees: running, its ticks, and which of our mixing recipes Create loaded and would match here */
    private static String mixer(ServerLevel level) {
        StringBuilder b = new StringBuilder();
        if (level.getBlockEntity(o.above(3)) instanceof com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity m)
        {
            b.append("running ").append(m.running).append(" ticks ").append(m.runningTicks).append('/').append(m.processingTicks)
             .append(" speedOk ").append(m.isSpeedRequirementFulfilled()).append(" overstressed ").append(m.isOverStressed());
            if (level.getBlockEntity(basin) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity bb2) b.append(" basinCanContinue ").append(bb2.canContinueProcessing());
            if (!m.running) { m.basinChecker.scheduleUpdate(); b.append(" (re-check asked)"); }
        }
        var basinBe = level.getBlockEntity(basin) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity bb ? bb : null;
        for (var r : level.getRecipeManager().getAllRecipesFor(com.simibubi.create.AllRecipeTypes.MIXING.<net.minecraft.world.item.crafting.RecipeInput, net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.RecipeInput>>getType())) {
            String id = r.id().toString();
            if (!id.startsWith("rancher_companion:") && !id.contains("horizons") && !id.contains("twilight")) continue;
            b.append("; ").append(id).append(basinBe != null && com.simibubi.create.content.processing.basin.BasinRecipe.match(basinBe, r.value()) ? " matches" : " no match");
        }
        return b.toString();
    }

    /** the fluid seen from a side (the Item Drain's tank) */
    private static String side(ServerLevel level, BlockPos pos) {
        var h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.NORTH);
        if (h == null) return "none";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < h.getTanks(); i++) { var f = h.getFluidInTank(i); if (!f.isEmpty()) b.append(f.getAmount()).append(' ').append(BuiltInRegistries.FLUID.getKey(f.getFluid())).append(';'); }
        return b.toString();
    }

    private static boolean holds(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> p) {
        var h = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (p.test(h.getStackInSlot(i))) return true;
        return false;
    }

    private static volatile String state = "";
    private static volatile boolean ok;

    /** a check run on the server; its answer is read on the next try */
    private static void check(String name, int patience, java.util.function.Function<ServerLevel, Boolean> test, java.util.function.Function<ServerLevel, String> say) {
        SelfTest.stepUntil(name, 20, patience, () -> server(l -> { ok = test.apply(l); state = say.apply(l); }), () -> {
            server(l -> { ok = test.apply(l); state = say.apply(l); });
            if (!ok) RancherCompanion.LOG.info("[selftest] {} waits: {}", name, state);
            return ok;
        });
    }

    private static void look(BlockPos from, BlockPos at) {
        cmd("execute anchored eyes run tp @s " + (from.getX() + 0.5) + " " + from.getY() + " " + (from.getZ() + 0.5) + " facing " + (at.getX() + 0.5) + " " + (at.getY() + 0.5) + " " + (at.getZ() + 0.5));
    }

    static void steps(BlockPos hatch) {
        o = hatch.offset(-8, 0, 6);
        basin = o.above();
        depot = o.offset(4, 0, 0);
        drain = o.offset(7, 0, 0);
        drainHorizons = o.offset(8, 0, 0);
        depotHorizons = o.offset(10, 0, 0);
        spoutHorizons = depotHorizons.above(2);
        quiet("basin_build", 40, () -> server(level -> {
            for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, 0, -14), o.offset(17, 6, 3))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos p : BlockPos.betweenClosed(o.offset(-2, -1, -14), o.offset(17, -1, 3))) level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
            /* the mixing: a lit Blaze Burner, the Basin, the Mixer two up, a Creative Motor through a cogwheel */
            level.setBlock(o, block("create:blaze_burner", "blaze", "kindled"), 3);
            if (level.getBlockEntity(o) instanceof com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity bb) bb.isCreative = true;  /* stays lit: no fuel runs out */
            level.setBlock(basin, block("create:basin"), 3);
            level.setBlock(o.above(3), block("create:mechanical_mixer"), 3);
            level.setBlock(o.offset(1, 3, 0), block("create:cogwheel", "axis", "y"), 3);
            level.setBlock(o.offset(1, 2, 0), block("create:creative_motor", "facing", "up"), 3);
            /* 64 rpm: the Mechanical Mixer needs a medium speed (its default 16 rpm leaves it standing, "speedOk false") */
            if (level.getBlockEntity(o.offset(1, 2, 0)) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor) motor.generatedSpeed.setValue(64);
            /* the bottling: a Depot under a Spout; the Item Drain; a second Depot and Spout for Horizons */
            level.setBlock(depot, block("create:depot"), 3);
            level.setBlock(depot.above(2), block("create:spout"), 3);
            level.setBlock(drain, block("create:item_drain"), 3);
            level.setBlock(drainHorizons, block("create:item_drain"), 3);
            level.setBlock(depotHorizons, block("create:depot"), 3);
            level.setBlock(spoutHorizons, block("create:spout"), 3);
            /* the look: a ledge the three fluids flow off, side by side, and a Fluid Tank of each */
            for (int x = 0; x < 9; x++) level.setBlock(o.offset(x, 2, -8), Blocks.STONE.defaultBlockState(), 3);
            for (int i = 0; i < 3; i++) level.setBlock(o.offset(11 + i * 2, 0, -8), block("create:fluid_tank"), 3);
        }));
        quiet("basin_look", 20, () -> look(o.offset(5, 1, 7), o.offset(4, 1, 0)));
        step("basin_built", 20, () -> {}, () -> BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(basin).getBlock()).toString().equals("create:basin"));
        /* 1. the Basin: Awkward potion fluid and an Amethyst Shard */
        quiet("basin_fill", 10, () -> server(level -> RancherCompanion.LOG.info("[selftest] basin filled: {} mB, shard in {}",
            fill(level, basin, potion(Potions.AWKWARD, 1000)), put(level, basin, new ItemStack(Items.AMETHYST_SHARD)))));
        check("basin_starlight_fluid", 40, l -> fluids(l, basin).contains("1000 rancher_companion:starlight_tonic"), l -> fluids(l, basin) + " " + machine(l));
        /* 2. the Spout fills a glass bottle on the Depot: a tonic */
        quiet("spout_fill", 10, () -> server(level -> {
            RancherCompanion.LOG.info("[selftest] spout fluid in: {}", fill(level, depot.above(2), tonic(TonicFluids.STARLIGHT, 1000)));
            put(level, depot, new ItemStack(Items.GLASS_BOTTLE));
        }));
        check("spout_tonic", 20, l -> holds(l, depot, s -> s.is(DrekkiItems.TONICS.get(3).get())), l -> fluids(l, depot.above(2)));
        /* 3. the Item Drain empties a Heirloom tonic back to fluid */
        quiet("drain_put", 10, () -> server(level -> {  /* the Item Drain takes items and gives fluid on its sides */
            var h = level.getCapability(Capabilities.ItemHandler.BLOCK, drain, Direction.NORTH);
            if (h != null) net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h, new ItemStack(DrekkiItems.TONICS.get(1).get()), false);
        }));
        check("drain_heirloom_fluid", 20, l -> side(l, drain).contains("250 rancher_companion:heirloom_tonic"), l -> side(l, drain));
        /* Horizons in the Basin too: Twilight fluid and an Emerald, its last brewing step */
        quiet("basin_twilight", 10, () -> server(level -> {
            var h = level.getCapability(Capabilities.FluidHandler.BLOCK, basin, Direction.UP);
            if (h != null) h.drain(4000, IFluidHandler.FluidAction.EXECUTE);
            fill(level, basin, potion(DrekkiPotions.TWILIGHT, 1000));
            put(level, basin, new ItemStack(Items.EMERALD));
        }));
        check("basin_horizons_fluid", 40, l -> fluids(l, basin).contains("1000 rancher_companion:horizons"), l -> fluids(l, basin) + " " + machine(l));
        /* 4. Horizons: the Spout bottles its fluid into a Potion of Horizons, the Item Drain empties one into it */
        quiet("horizons_fill", 10, () -> server(level -> {
            fill(level, spoutHorizons, tonic(TonicFluids.HORIZONS, 1000));
            var h = level.getCapability(Capabilities.ItemHandler.BLOCK, drainHorizons, Direction.NORTH);
            if (h != null) net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(h,
                PotionContents.createItemStack(Items.POTION, DrekkiPotions.HORIZONS), false);
            put(level, depotHorizons, new ItemStack(Items.GLASS_BOTTLE));
        }));
        check("spout_horizons", 20, l -> holds(l, depotHorizons, s -> s.is(Items.POTION)
            && s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(DrekkiPotions.HORIZONS)), l -> fluids(l, spoutHorizons));
        check("drain_horizons_fluid", 20, l -> side(l, drainHorizons).contains("250 rancher_companion:horizons"), l -> side(l, drainHorizons));
        quiet("basin_done", 60, () -> {});
        /* 5. the look: the three fluids flowing side by side off a ledge, then a tank of each */
        quiet("flow_pour", 10, () -> server(level -> {
            for (int i = 0; i < 3; i++) {
                var t = TonicFluids.ALL.get(i);
                level.setBlock(o.offset(1 + i * 3, 3, -8), t.block().get().defaultBlockState(), 3);
                fill(level, o.offset(11 + i * 2, 0, -8), tonic(t, 8000));
            }
        }));
        quiet("flow_settle", 60, () -> look(o.offset(8, 3, 0), o.offset(8, 1, -8)));
        step("flows_and_tanks", 10, () -> {}, () -> {
            for (int i = 0; i < 3; i++) if (mc().level.getFluidState(o.offset(1 + i * 3, 3, -8)).getType() != TonicFluids.ALL.get(i).source().get()) return false;
            return true;
        });
        quiet("flows_hold", 200, () -> {});
        quiet("basin_end", 10, () -> RancherCompanion.LOG.info("[selftest] basin end"));
    }
}
