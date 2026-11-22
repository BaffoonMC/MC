package com.lirdi.oldschoollighting.client;

/**
 * Rebuilds Minecraft Beta 1.7.3 lighting on top of the 1.7.10 lightmap.
 *
 * <p>
 * How Beta 1.7.3 lit a block:
 * <ul>
 * <li>light level = max(skyLight - skylightSubtracted, blockLight)</li>
 * <li>brightness = lightBrightnessTable[level], using an ambient floor of 0.05 (0.1 in the Nether)</li>
 * <li>the result was plain grey: no warm torch tint, no blue night, no torch flicker</li>
 * <li>day/night changed in whole light-level steps (skylightSubtracted is 0..11)</li>
 * </ul>
 *
 * <p>
 * Release 1.7.10 instead mixes sky and block light in colour on a 16x16 lightmap texture
 * (pixel index = sky * 16 + block). This class fills that texture with Beta-style values.
 * Everything here is plain maths with no Minecraft classes, so it is easy to test.
 */
public final class BetaLightmap {

    /** Beta's skylightSubtracted at full night. */
    public static final float MAX_SKY_DARKENING = 11.0F;

    private BetaLightmap() {}

    /**
     * Beta 1.7.3's WorldProvider.generateLightBrightnessTable() formula. A fractional level is
     * allowed so the optional smooth day/night mode can blend between steps.
     */
    public static float brightness(float level, float ambient) {
        if (level < 0.0F) level = 0.0F;
        if (level > 15.0F) level = 15.0F;
        float f1 = 1.0F - level / 15.0F;
        return (1.0F - f1) / (f1 * 3.0F + 1.0F) * (1.0F - ambient) + ambient;
    }

    /**
     * Beta's World.calculateSkylightSubtracted() before its final cast to int.
     *
     * @param celestialAngle world.getCelestialAngle(partialTicks)
     * @param rain           world.getRainStrength(partialTicks), 0..1
     * @param thunder        world.getWeightedThunderStrength(partialTicks), 0..1
     * @return how many sky light levels to remove, 0 (noon) .. 11 (night)
     */
    public static float skyDarkening(float celestialAngle, float rain, float thunder) {
        float f = 1.0F - ((float) Math.cos(celestialAngle * (float) Math.PI * 2.0F) * 2.0F + 0.5F);
        if (f < 0.0F) f = 0.0F;
        if (f > 1.0F) f = 1.0F;
        f = 1.0F - f;
        f = (float) (f * (1.0D - rain * 5.0F / 16.0D));
        f = (float) (f * (1.0D - thunder * 5.0F / 16.0D));
        f = 1.0F - f;
        return f * MAX_SKY_DARKENING;
    }

    /**
     * Fills a 256-entry ARGB lightmap.
     *
     * @param out          the array to fill (index = sky * 16 + block)
     * @param hasSky       false for skyless dimensions such as the Nether and the End
     * @param skyDarkening value from {@link #skyDarkening}; pass a whole number for Beta-style steps
     * @param ambient      brightness of light level 0
     * @param gamma        the vanilla Brightness slider (0 = Moody, 1 = Bright); pass 0 to ignore it
     * @param nightVision  night vision strength, 0..1 (Beta had none, but the potion should still work)
     */
    public static void fill(int[] out, boolean hasSky, float skyDarkening, float ambient, float gamma,
        float nightVision) {
        for (int sky = 0; sky < 16; sky++) {
            float skyLevel = hasSky ? Math.max(0.0F, sky - skyDarkening) : 0.0F;
            for (int block = 0; block < 16; block++) {
                float v = brightness(Math.max(skyLevel, block), ambient);

                // Night vision: vanilla scales every channel towards full brightness.
                if (nightVision > 0.0F) {
                    v = v * (1.0F - nightVision) + nightVision;
                }

                // Same curve vanilla uses for the Brightness slider.
                if (gamma > 0.0F) {
                    float inv = 1.0F - v;
                    float bright = 1.0F - inv * inv * inv * inv;
                    v = v * (1.0F - gamma) + bright * gamma;
                }

                if (v < 0.0F) v = 0.0F;
                if (v > 1.0F) v = 1.0F;
                int c = (int) (v * 255.0F);
                out[sky * 16 + block] = 0xFF000000 | c << 16 | c << 8 | c;
            }
        }
    }
}
