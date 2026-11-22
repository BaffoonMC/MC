package com.lirdi.oldschoollighting;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Settings stored in config/oldschoollighting.cfg, also editable in-game via Mods > Old School Lighting > Config. */
public final class LightingConfig {

    public static Configuration config;

    public static boolean enabled = true;
    public static boolean steppedDaylight = true;
    public static boolean useBrightnessSlider = true;
    public static float skyAmbient = 0.05F;
    public static float netherAmbient = 0.10F;
    public static float endAmbient = 0.22F;
    public static float contrast = 1.6F;
    public static float nightDarkening = 13.0F;
    public static float brightnessSliderStrength = 0.5F;
    public static int[] dimensionBlacklist = new int[0];

    private static final String CAT = Configuration.CATEGORY_GENERAL;
    private static final String LANG = "oldschoollighting.config.";

    private LightingConfig() {}

    public static void load(File file) {
        config = new Configuration(file);
        sync();
    }

    public static void sync() {
        enabled = bool(
            "enabled",
            true,
            "Turn the Beta 1.7.3 lighting on or off. You can also toggle it in-game (default key: L).");

        steppedDaylight = bool(
            "steppedDaylight",
            true,
            "true = Beta-style sunrise/sunset that darkens in whole light-level steps.\n"
                + "false = the same Beta brightness, but fading smoothly.");

        useBrightnessSlider = bool(
            "useBrightnessSlider",
            true,
            "true = the vanilla Brightness slider (Moody..Bright) still works (see brightnessSliderStrength).\n"
                + "false = pure Beta 1.7.3, which ignores the slider.");

        brightnessSliderStrength = number(
            "brightnessSliderStrength",
            0.5,
            0.0,
            1.0,
            "How much the Brightness slider can brighten things. 1 = like vanilla (washes out the old-school look),\n"
                + "0.5 = half (default), 0 = ignored.");

        contrast = number(
            "contrast",
            1.6,
            0.5,
            3.0,
            "How strong the old-school shadows are. 1.0 = exact Beta 1.7.3 (subtle). 1.6 = default, deeper shadows\n"
                + "and shorter torch glow. 2.0+ = very dark and moody. Below 1 = brighter, flatter.");

        nightDarkening = number(
            "nightDarkening",
            13.0,
            0.0,
            15.0,
            "How many sky light levels night removes. 11 = exact Beta 1.7.3, 13 = default (darker nights),\n"
                + "15 = pitch-black nights where only torches help.");

        skyAmbient = number(
            "skyAmbient",
            0.05,
            0.0,
            1.0,
            "Brightness of a light level 0 block in dimensions with a sky. Beta 1.7.3 = 0.05, vanilla 1.7.10 = 0.0.");

        netherAmbient = number(
            "netherAmbient",
            0.10,
            0.0,
            1.0,
            "Brightness of a light level 0 block in skyless dimensions such as the Nether. Beta 1.7.3 = 0.1.");

        endAmbient = number(
            "endAmbient",
            0.22,
            0.0,
            1.0,
            "Brightness of a light level 0 block in the End (Beta had no End; 0.22 roughly matches vanilla).");

        Property p = config.get(
            CAT,
            "dimensionBlacklist",
            new int[0],
            "Dimension IDs that keep vanilla 1.7.10 lighting, e.g. 1 for the End or a modded dimension's ID.");
        p.setLanguageKey(LANG + "dimensionBlacklist");
        dimensionBlacklist = p.getIntList();

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static boolean isBlacklisted(int dimensionId) {
        for (int id : dimensionBlacklist) {
            if (id == dimensionId) return true;
        }
        return false;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        config.get(CAT, "enabled", true)
            .set(value);
        config.save();
    }

    private static boolean bool(String name, boolean def, String comment) {
        Property p = config.get(CAT, name, def, comment);
        p.setLanguageKey(LANG + name);
        return p.getBoolean(def);
    }

    private static float number(String name, double def, double min, double max, String comment) {
        Property p = config.get(CAT, name, def, comment + "\n[range: " + min + " ~ " + max + ", default: " + def + "]");
        p.setLanguageKey(LANG + name);
        p.setMinValue(min);
        p.setMaxValue(max);
        double v = p.getDouble(def);
        if (v < min) v = min;
        if (v > max) v = max;
        return (float) v;
    }
}
