package dev.frosthaven.ranchercompanion.showcase;

import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.item.ItemHooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The block showcase (-Drancher_companion.blockshowcase=true): a flat world's player builds, through the real placement and
 * use code (so the trough's facing, joining, line diet and dye rules run as a player would meet them), four stables in
 * different leather dyes around a hatchery and a fed trough line, the trough cases (a full line of 10, the 11th and a
 * corner piece staying apart, one trough per diet), every decor block, and a wandering trader. The client takes the
 * pictures (client/BlockShowcaseClient).
 */
public final class BlockShowcase {
    private BlockShowcase() {}

    public static final boolean AUTO = Boolean.getBoolean("rancher_companion.blockshowcase");
    /** far from the drekki showcase's rows; the flat world's ground top is y -61 */
    public static final int OX = 1000, OY = -60, OZ = 0;
    public static final int[] STABLE_X = {0, 9, 18, 27};
    public static final int TROUGH_Z = 14, SINGLES_Z = 19, DECOR_Z = 25, LEATHER_Z = 30;

    public record Shot(String name, double x, double y, double z, double tx, double ty, double tz) {}

    /** the camera: an eye and the point it looks at, relative to the origin */
    public static List<Shot> shots() {
        List<Shot> s = new ArrayList<>();
        s.add(new Shot("stables", 13.5, 10, 13, 13.5, 1, -1));
        String[] names = {"stable_undyed", "stable_red", "stable_cyan", "stable_yellow"};
        for (int i = 0; i < 4; i++) {
            int x = STABLE_X[i];
            s.add(new Shot(names[i], x + 4.6, 2.6, 6.6, x + 0.2, 1.0, -0.4));
        }
        s.add(new Shot("stable_inside", 0.5, 1.7, 2.9, 0.2, 0.5, -1.2));
        for (int i = 0; i < 2; i++) {  /* the hatchery in its corner and the fed trough in the other, up close */
            int x = STABLE_X[i];
            s.add(new Shot("hatchery_" + names[i].substring(7), x + 0.6, 1.5, 1.5, x - 0.5, 0.45, -0.5));
            s.add(new Shot("trough_" + names[i].substring(7), x + 1.4, 2.0, 1.5, x + 1.5, 0.35, -0.5));
        }
        s.add(new Shot("trough_singles_top", 5, 3.6, SINGLES_Z + 2.6, 5, 0.3, SINGLES_Z + 0.5));
        s.add(new Shot("trough_line", 5.5, 4.2, TROUGH_Z + 7.5, 5.5, 0.4, TROUGH_Z));
        s.add(new Shot("trough_line_close", 1.5, 2.2, TROUGH_Z + 3.4, 0.5, 0.4, TROUGH_Z + 0.2));
        s.add(new Shot("trough_line_end", 10.6, 2.4, TROUGH_Z + 3.6, 10, 0.4, TROUGH_Z));
        s.add(new Shot("trough_line_back", 4.5, 3.2, TROUGH_Z - 4.8, 4.5, 0.4, TROUGH_Z));
        s.add(new Shot("trough_singles", 5, 2.6, SINGLES_Z + 4.4, 5, 0.3, SINGLES_Z));
        s.add(new Shot("decor", 9, 3.4, DECOR_Z + 7, 9, 0.6, DECOR_Z));
        s.add(new Shot("decor_left", 3, 2, DECOR_Z + 4.2, 3, 0.6, DECOR_Z));
        s.add(new Shot("decor_right", 15, 2, DECOR_Z + 4.2, 15, 0.6, DECOR_Z));
        s.add(new Shot("tufted_leather", 8.5, 3.2, LEATHER_Z + 7, 8.5, 0.5, LEATHER_Z + 0.5));
        return s;
    }

    public static void prepare(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (int cx = (OX - 16) >> 4; cx <= (OX + 48) >> 4; cx++) for (int cz = (OZ - 16) >> 4; cz <= (OZ + 48) >> 4; cz++) level.setChunkForced(cx, cz, true);
        p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.teleportTo(level, OX + 13.5, OY + 9, OZ + 12, 180, 30);
    }

    /** builds everything; the player must stand near the origin (its chunks are forced in prepare) */
    public static void build(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        int[] colors = {0, DyeColor.RED.getId() + 1, DyeColor.CYAN.getId() + 1, DyeColor.YELLOW.getId() + 1};
        Item[][] foods = {{Items.WHEAT, Items.APPLE, Items.BEETROOT}, {Items.BEEF, Items.PORKCHOP, Items.CHICKEN},
            {Items.CARROT, Items.POTATO, Items.SWEET_BERRIES}, {Items.COOKED_BEEF, Items.COOKED_CHICKEN, Items.COOKED_PORKCHOP}};
        for (int i = 0; i < 4; i++) stable(p, at(STABLE_X[i], 0, 0), colors[i], foods[i]);
        troughs(p);
        decor(p);
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        if (trader != null) {
            trader.moveTo(OX - 6.5, OY, OZ + 8.5, 225, 0);
            trader.setNoAi(true);
            trader.setDespawnDelay(0);
            level.addFreshEntity(trader);
            ItemHooks.ensureEgg(trader);
            traderId = trader.getUUID();
        }
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(false);
    }

    public static java.util.UUID traderId;

    private static BlockPos at(int x, int y, int z) { return new BlockPos(OX + x, OY + y, OZ + z); }

    /** a stable with a 3 x 3 inside, in the design page's style: plank floor, fence corner posts, plank and leather walls
        (back and left), a fence along the right, an open front, and a ceiling of beam and shingle slabs over the 5 x 5 (a
        3 x 3 of roof that holds the hatchery's column need not be centered on it, so a corner needs no overhang). The
        hatchery stands in the back-left inside corner; one trough at the other end of the same back wall, its open side
        toward the stable's opening; straw bedding on every other inside block. A lantern; leather in the given dye */
    private static void stable(ServerPlayer p, BlockPos o, int color, Item[] food) {
        ServerLevel level = p.serverLevel();
        for (int i = -2; i <= 2; i++) for (int k = -2; k <= 2; k++) level.setBlock(o.offset(i, -1, k), DrekkiBlocks.PLANKS.get().defaultBlockState(), 3);
        Item leather = DrekkiBlocks.TUFTED.get(color).get().asItem();
        for (int n = -1; n <= 1; n++) {
            place(p, o.offset(n, 0, -2), DrekkiBlocks.PLANKS.get().asItem(), Direction.NORTH, false);
            place(p, o.offset(-2, 0, n), DrekkiBlocks.PLANKS.get().asItem(), Direction.NORTH, false);
            for (int y = 1; y <= 2; y++) { place(p, o.offset(n, y, -2), leather, Direction.NORTH, false); place(p, o.offset(-2, y, n), leather, Direction.NORTH, false); }
            place(p, o.offset(2, 0, n), DrekkiBlocks.FENCE.get().asItem(), Direction.NORTH, false);
        }
        for (int y = 0; y < 3; y++) for (int i : new int[]{-2, 2}) for (int k : new int[]{-2, 2}) place(p, o.offset(i, y, k), DrekkiBlocks.FENCE.get().asItem(), Direction.NORTH, false);
        for (int i = -2; i <= 2; i++) for (int k = -2; k <= 2; k++)  /* beam slabs as joists (every other row) lying along x, shingle slabs between */
            place(p, o.offset(i, 3, k), (k % 2 == 0 ? DrekkiBlocks.BEAM_SLAB : DrekkiBlocks.SHINGLES_SLAB).get().asItem(), Direction.EAST, false);
        for (int i = -1; i <= 1; i++) for (int k = -1; k <= 1; k++)  /* bedding on every inside block but the hatchery's and the trough's */
            if (k != -1 || i == 0) place(p, o.offset(i, 0, k), DrekkiBlocks.STRAW_BEDDING.get().asItem(), Direction.NORTH, false);
        place(p, o.offset(-1, 0, -1), DrekkiBlocks.HATCHERY.get().asItem(), Direction.NORTH, false);  /* back-left inside corner, facing the front */
        dye(p, o.offset(-1, 0, -1), color);
        place(p, o.offset(1, 0, -1), DrekkiBlocks.TROUGH.get().asItem(), Direction.NORTH, false);  /* back-right corner, open toward the front */
        dye(p, o.offset(1, 0, -1), color);
        feed(p, o.offset(1, 0, -1), food[0], 24); feed(p, o.offset(1, 0, -1), food[1], 6); feed(p, o.offset(1, 0, -1), food[2], 12);
        level.setBlock(o.offset(0, 2, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
    }

    /** the trough cases: a full line of 10, an 11th that stays apart, a corner piece that stays apart, one trough per diet */
    private static void troughs(ServerPlayer p) {
        Item trough = DrekkiBlocks.TROUGH.get().asItem();
        for (int x = 0; x <= 10; x++) place(p, at(x, 0, TROUGH_Z), trough, Direction.NORTH, false);  /* open toward the camera (south) */
        place(p, at(0, 0, TROUGH_Z - 1), trough, Direction.EAST, false);  /* behind the first, turned: a corner never joins */
        feed(p, at(2, 0, TROUGH_Z), Items.COOKED_BEEF, 20); feed(p, at(5, 0, TROUGH_Z), Items.COOKED_SALMON, 8); feed(p, at(8, 0, TROUGH_Z), Items.COOKED_MUTTON, 30);
        feed(p, at(10, 0, TROUGH_Z), Items.MELON_SLICE, 16);  /* the 11th is its own trough: another diet is fine there */
        Item[][] per = {{}, {Items.BEEF, Items.SALMON}, {Items.COOKED_PORKCHOP, Items.COOKED_COD}, {Items.WHEAT_SEEDS, Items.PUMPKIN}, {Items.CARROT, Items.GLOW_BERRIES}};
        int[] dyes = {0, DyeColor.ORANGE.getId() + 1, DyeColor.LIME.getId() + 1, DyeColor.BLUE.getId() + 1, DyeColor.BLACK.getId() + 1};
        for (int i = 0; i < per.length; i++) {
            BlockPos pos = at(i * 2, 0, SINGLES_Z);
            place(p, pos, trough, Direction.NORTH, false);
            dye(p, pos, dyes[i]);
            for (Item f : per[i]) feed(p, pos, f, 20);
        }
        place(p, at(10, 0, SINGLES_Z), trough, Direction.NORTH, true);  /* sneak-placed: open away from the player */
        feed(p, at(0, 0, SINGLES_Z), Items.BEEF, 5);  /* empty trough: raw goes in */
        feed(p, at(0, 0, SINGLES_Z), Items.WHEAT, 5);  /* then a seed food is refused (the trough now holds raw) */
    }

    private static void decor(ServerPlayer p) {
        int x = 0;
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.PLANKS.get().asItem(), Direction.NORTH, false);
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.PLANKS_SLAB.get().asItem(), Direction.NORTH, false);
        placeTop(p, at(x++, 0, DECOR_Z), DrekkiBlocks.PLANKS_SLAB.get().asItem());
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.PLANKS_STAIRS.get().asItem(), Direction.NORTH, false);
        x++;
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.BEAM.get().asItem(), Direction.NORTH, false);
        placeSide(p, at(x++, 0, DECOR_Z), DrekkiBlocks.BEAM.get().asItem(), Direction.EAST);
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.BEAM_SLAB.get().asItem(), Direction.EAST, false);
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.BEAM_SLAB.get().asItem(), Direction.NORTH, false);
        x++;
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.SHINGLES.get().asItem(), Direction.NORTH, false);
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.SHINGLES_SLAB.get().asItem(), Direction.NORTH, false);
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.SHINGLES_STAIRS.get().asItem(), Direction.NORTH, false);
        x++;
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.STRAW_BEDDING.get().asItem(), Direction.NORTH, false);
        x++;
        for (int i = 0; i < 3; i++) place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.FENCE.get().asItem(), Direction.NORTH, false);
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.FENCE_GATE.get().asItem(), Direction.NORTH, false);
        place(p, at(x++, 0, DECOR_Z), DrekkiBlocks.FENCE.get().asItem(), Direction.NORTH, false);
        for (int i = 0; i < DrekkiBlocks.TUFTED.size(); i++) {
            place(p, at(i, 0, LEATHER_Z), DrekkiBlocks.TUFTED.get(i).get().asItem(), Direction.NORTH, false);
            place(p, at(i, 0, LEATHER_Z + 1), DrekkiBlocks.TUFTED_SLAB.get(i).get().asItem(), Direction.NORTH, false);
        }
    }

    // ---- acting as the player ----

    private static float yaw(Direction look) { return look.toYRot(); }

    /** places as a player looking `look` would (sneaking or not), clicking the top of the spot itself */
    private static void place(ServerPlayer p, BlockPos pos, Item item, Direction look, boolean sneak) {
        click(p, pos, item, look, sneak, Direction.UP, new Vec3(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5));
    }

    private static void placeTop(ServerPlayer p, BlockPos pos, Item item) {
        click(p, pos, item, Direction.NORTH, false, Direction.DOWN, new Vec3(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5));
    }

    private static void placeSide(ServerPlayer p, BlockPos pos, Item item, Direction face) {
        click(p, pos, item, Direction.NORTH, false, face, Vec3.atCenterOf(pos));
    }

    private static void click(ServerPlayer p, BlockPos pos, Item item, Direction look, boolean sneak, Direction face, Vec3 hitAt) {
        p.setYRot(yaw(look));
        p.setXRot(10);
        p.setShiftKeyDown(sneak);
        ItemStack stack = new ItemStack(item);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        if (item instanceof BlockItem bi) bi.place(new BlockPlaceContext(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hitAt, face, pos, false))));
        p.setShiftKeyDown(false);
    }

    /** right-clicks the block with a stack, through the block's own use code */
    private static void use(ServerPlayer p, BlockPos pos, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var state = p.serverLevel().getBlockState(pos);
        state.useItemOn(stack, p.serverLevel(), p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void dye(ServerPlayer p, BlockPos pos, int color) {
        if (color > 0) use(p, pos, new ItemStack(DyeItem.byColor(DyeColor.byId(color - 1))));
    }

    private static void feed(ServerPlayer p, BlockPos pos, Item food, int n) {
        use(p, pos, new ItemStack(food, n));
    }
}
