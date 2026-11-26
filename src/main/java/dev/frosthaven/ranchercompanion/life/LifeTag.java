package dev.frosthaven.ranchercompanion.life;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** {@link Life} in the drekki's save, under "Life"; a drekki saved before 0.4.0 has none and starts fresh. */
public final class LifeTag {
    private LifeTag() {}

    public static CompoundTag save(Life l) {
        CompoundTag t = new CompoundTag();
        t.putLong("travel", l.travel);
        t.putLongArray("astral", l.astral);
        t.putIntArray("meals", l.meals);
        CompoundTag b = new CompoundTag();
        l.biome.forEach(b::putLong);
        t.put("biome", b);
        t.putDouble("food", l.food);
        t.putDouble("sat", l.sat);
        t.putDouble("mood", l.mood);
        t.putLong("stamp", l.stamp);
        t.putLong("petUntil", l.petUntil);
        if (l.soulflameUntil != 0) t.putLong("soulflame", l.soulflameUntil);
        ListTag tr = new ListTag();
        for (String s : l.traits) tr.add(StringTag.valueOf(s));
        t.put("traits", tr);
        CompoundTag pr = new CompoundTag();
        l.progress.forEach(pr::putInt);
        t.put("progress", pr);
        if (l.offer != null) t.putString("offer", l.offer);
        t.putBoolean("ghost", l.ghost);
        t.putBoolean("horizons", l.horizons);
        if (!l.oldCaps) t.putInt("caps", 1);
        t.putInt("rev", 2);  /* 2 (0.7): a ghost saved before that came back to life at the update */  /* settled under the 2/4/5 caps; absent in a save from before them */
        CompoundTag cd = new CompoundTag();
        l.cooldowns.forEach(cd::putLong);
        t.put("cooldowns", cd);
        t.putString("stance", l.stance.name());
        return t;
    }

    public static void load(Life l, CompoundTag t) {
        l.travel = t.getLong("travel");
        long[] a = t.getLongArray("astral");
        System.arraycopy(a, 0, l.astral, 0, Math.min(a.length, l.astral.length));
        int[] m = t.getIntArray("meals");
        System.arraycopy(m, 0, l.meals, 0, Math.min(m.length, l.meals.length));
        l.biome.clear();
        CompoundTag b = t.getCompound("biome");
        for (String k : b.getAllKeys()) l.biome.put(k, b.getLong(k));
        l.food = t.contains("food") ? t.getDouble("food") : Life.FOOD_MAX;
        l.sat = Math.min(l.food, t.getDouble("sat"));
        l.mood = t.contains("mood") ? t.getDouble("mood") : Life.MOOD_START;
        l.stamp = t.contains("stamp") ? t.getLong("stamp") : Life.NEVER;
        l.petUntil = t.getLong("petUntil");
        l.soulflameUntil = t.getLong("soulflame");
        l.traits.clear();
        for (Tag s : t.getList("traits", Tag.TAG_STRING)) if (!s.getAsString().equals("phoenix_heart")) l.traits.add(s.getAsString());  /* removed in 0.7 */
        l.progress.clear();
        CompoundTag pr = t.getCompound("progress");
        for (String k : pr.getAllKeys()) l.progress.put(k, pr.getInt(k));
        l.offer = t.contains("offer") ? t.getString("offer") : null;
        l.ghost = t.getBoolean("ghost") && t.getInt("rev") >= 2;  /* the update's grace (Shane, 2026-11-26): every ghost from before comes back to life */
        l.horizons = t.getBoolean("horizons");
        l.oldCaps = !t.contains("caps");
        l.cooldowns.clear();
        CompoundTag cd = t.getCompound("cooldowns");
        for (String k : cd.getAllKeys()) l.cooldowns.put(k, cd.getLong(k));
        try { l.stance = Life.Stance.valueOf(t.getString("stance")); } catch (IllegalArgumentException e) { l.stance = Life.Stance.REACTIVE; }
    }
}
