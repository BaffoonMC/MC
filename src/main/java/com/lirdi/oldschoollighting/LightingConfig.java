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
            "true = the vanilla Brightness slider (Moody..Bright) still works.\n"
                + "false = pure Beta 1.7.3, which ignores the slider (set it to Moody for the authentic look).");

        skyAmbient = number(
            "skyAmbient",
            0.05,
            "Brightness of a light level 0 block in dimensions with a sky. Beta 1.7.3 = 0.05, vanilla 1.7.10 = 0.0.");

        netherAmbient = number(
            "netherAmbient",
            0.10,
            "Brightness of a light level 0 block in skyless dimensions such as the Nether. Beta 1.7.3 = 0.1.");

        endAmbient = number(
            "endAmbient",
            0.22,
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

    private static float number(String name, double def, String comment) {
        Property p = config.get(CAT, name, def, comment + "\n[range: 0.0 ~ 1.0, default: " + def + "]");
        p.setLanguageKey(LANG + name);
        p.setMinValue(0.0D);
        p.setMaxValue(1.0D);
        double v = p.getDouble(def);
        if (v < 0.0D) v = 0.0D;
        if (v > 1.0D) v = 1.0D;
        return (float) v;
    }
}
