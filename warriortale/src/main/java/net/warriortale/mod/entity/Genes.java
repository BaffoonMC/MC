package net.warriortale.mod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

/**
 * The gene block every Folk carries: the Warrior Tale stat schema in NBT.
 * Values run 0..100. Children blend parents with jitter (the drawChild rule).
 */
public final class Genes {
    public int vigor;   // mass + health
    public int twitch;  // speed
    public int knit;    // regen after hurt
    public int hide;    // armor-ish toughness
    public int tone;    // skin-variant index helper (0..100 -> texture pool)
    public int variant; // which texture of the pool this folk wears

    public static Genes roll(RandomSource r, boolean gharr) {
        Genes g = new Genes();
        int base = gharr ? 65 : 45;
        g.vigor  = clamp(base + r.nextInt(31) - 10);
        g.twitch = clamp((gharr ? 35 : 55) + r.nextInt(31) - 10);
        g.knit   = clamp(45 + r.nextInt(31) - 10);
        g.hide   = clamp(base + r.nextInt(31) - 15);
        g.tone   = r.nextInt(101);
        g.variant = r.nextInt(4);
        return g;
    }

    /** gaussian-ish blend of two parents, +-7 jitter: the drawChild rule. */
    public static Genes child(Genes a, Genes b, RandomSource r) {
        Genes g = new Genes();
        g.vigor  = blend(a.vigor,  b.vigor,  r);
        g.twitch = blend(a.twitch, b.twitch, r);
        g.knit   = blend(a.knit,   b.knit,   r);
        g.hide   = blend(a.hide,   b.hide,   r);
        g.tone   = blend(a.tone,   b.tone,   r);
        g.variant = r.nextBoolean() ? a.variant : b.variant;
        return g;
    }

    private static int blend(int a, int b, RandomSource r) {
        return clamp((a + b) / 2 + r.nextInt(15) - 7);
    }
    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    public void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putInt("vigor", vigor); t.putInt("twitch", twitch);
        t.putInt("knit", knit);   t.putInt("hide", hide);
        t.putInt("tone", tone);   t.putInt("variant", variant);
        tag.put("wt_genes", t);
    }

    public static Genes load(CompoundTag tag, RandomSource r, boolean gharr) {
        if (!tag.contains("wt_genes")) return roll(r, gharr);
        CompoundTag t = tag.getCompound("wt_genes");
        Genes g = new Genes();
        g.vigor = t.getInt("vigor"); g.twitch = t.getInt("twitch");
        g.knit = t.getInt("knit");   g.hide = t.getInt("hide");
        g.tone = t.getInt("tone");   g.variant = t.getInt("variant");
        return g;
    }
}
