package com.lirdi.oldschoollighting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Old School Lighting: Beta 1.7.3 lighting (the look Better Than Adventure is built on) for 1.7.10.
 * Client-side only, so it can join any server, with or without the mod installed there.
 */
@Mod(
    modid = OldSchoolLighting.MODID,
    name = "Old School Lighting",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    guiFactory = "com.lirdi.oldschoollighting.client.GuiFactory")
public class OldSchoolLighting {

    public static final String MODID = "oldschoollighting";
    public static final Logger LOG = LogManager.getLogger(MODID);

    private static boolean isClient() {
        return FMLCommonHandler.instance()
            .getSide() == Side.CLIENT;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (!isClient()) {
            LOG.info("Old School Lighting is client-side only; nothing to do on a dedicated server.");
            return;
        }
        LightingConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (isClient()) {
            com.lirdi.oldschoollighting.client.ClientEvents.init();
        }
    }
}
