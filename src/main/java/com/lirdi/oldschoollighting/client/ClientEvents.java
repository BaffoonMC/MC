package com.lirdi.oldschoollighting.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;

import org.lwjgl.input.Keyboard;

import com.lirdi.oldschoollighting.LightingConfig;
import com.lirdi.oldschoollighting.OldSchoolLighting;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;

/** Client-only setup: the lightmap handler, the toggle key and live config reloading. */
public final class ClientEvents {

    public static final KeyBinding TOGGLE = new KeyBinding(
        "key.oldschoollighting.toggle",
        Keyboard.KEY_L,
        "key.categories.oldschoollighting");

    public static void init() {
        ClientRegistry.registerKeyBinding(TOGGLE);
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientEvents());
        try {
            FMLCommonHandler.instance()
                .bus()
                .register(new LightmapHandler());
        } catch (Exception e) {
            OldSchoolLighting.LOG.error("Could not hook into the 1.7.10 lightmap; the mod will do nothing", e);
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (!TOGGLE.isPressed()) return;
        LightingConfig.setEnabled(!LightingConfig.enabled);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(
                new ChatComponentTranslation(
                    LightingConfig.enabled ? "oldschoollighting.message.on" : "oldschoollighting.message.off"));
        }
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (OldSchoolLighting.MODID.equals(event.modID)) {
            LightingConfig.sync();
        }
    }
}
