package com.lirdi.oldschoollighting.client;

import java.lang.reflect.Field;
import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;

import com.lirdi.oldschoollighting.LightingConfig;
import com.lirdi.oldschoollighting.OldSchoolLighting;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Replaces the vanilla lightmap with the Beta one.
 *
 * <p>
 * Vanilla only rebuilds the lightmap inside EntityRenderer.updateCameraAndRender() when its private
 * {@code lightmapUpdateNeeded} flag is set (once per tick, by updateTorchFlicker()). Just before every frame
 * this handler writes the Beta lightmap into the texture and clears that flag, so vanilla never overwrites
 * it. No coremod or mixins are needed, only two private fields read by reflection (MCP name for the dev
 * environment, SRG name for a normal game).
 */
public final class LightmapHandler {

    private final Field lightmapTexture;
    private final Field lightmapUpdateNeeded;

    private final int[] scratch = new int[256];
    private EntityRenderer activeOn;
    private boolean broken;

    public LightmapHandler() {
        lightmapTexture = ReflectionHelper.findField(EntityRenderer.class, "lightmapTexture", "field_78513_d");
        lightmapUpdateNeeded = ReflectionHelper
            .findField(EntityRenderer.class, "lightmapUpdateNeeded", "field_78536_aa");
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || broken) return;

        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        EntityRenderer renderer = mc.entityRenderer;
        EntityPlayer player = mc.thePlayer;
        if (world == null || renderer == null || player == null) {
            activeOn = null;
            return;
        }

        try {
            boolean wanted = LightingConfig.enabled && !LightingConfig.isBlacklisted(world.provider.dimensionId);
            if (!wanted) {
                if (activeOn != null) {
                    // Hand control back to vanilla: it rebuilds its own lightmap on the next frame.
                    lightmapUpdateNeeded.setBoolean(activeOn, true);
                    activeOn = null;
                }
                return;
            }

            float partialTicks = event.renderTickTime;
            boolean hasSky = !world.provider.hasNoSky;

            float darkening = 0.0F;
            if (hasSky && world.lastLightningBolt <= 0) {
                darkening = BetaLightmap.skyDarkening(
                    world.getCelestialAngle(partialTicks),
                    world.getRainStrength(partialTicks),
                    world.getWeightedThunderStrength(partialTicks),
                    LightingConfig.nightDarkening);
                if (LightingConfig.steppedDaylight) {
                    darkening = (int) darkening;
                }
            }

            float ambient;
            if (world.provider.dimensionId == 1) {
                ambient = LightingConfig.endAmbient;
            } else if (!hasSky) {
                ambient = LightingConfig.netherAmbient;
            } else {
                ambient = LightingConfig.skyAmbient;
            }

            float gamma = LightingConfig.useBrightnessSlider
                ? mc.gameSettings.gammaSetting * LightingConfig.brightnessSliderStrength
                : 0.0F;

            BetaLightmap.fill(
                scratch,
                hasSky,
                darkening,
                ambient,
                LightingConfig.contrast,
                gamma,
                nightVision(player, partialTicks));

            DynamicTexture texture = (DynamicTexture) lightmapTexture.get(renderer);
            int[] data = texture.getTextureData();
            // Upload only when something changed (sunset step, slider moved, potion, dimension change...).
            if (activeOn != renderer || !Arrays.equals(scratch, data)) {
                System.arraycopy(scratch, 0, data, 0, scratch.length);
                texture.updateDynamicTexture();
            }
            lightmapUpdateNeeded.setBoolean(renderer, false);
            activeOn = renderer;
        } catch (Exception e) {
            broken = true;
            OldSchoolLighting.LOG.error("Old School Lighting failed and has switched itself off", e);
        }
    }

    /** Copy of vanilla's private EntityRenderer.getNightVisionBrightness(). */
    private static float nightVision(EntityPlayer player, float partialTicks) {
        PotionEffect effect = player.getActivePotionEffect(Potion.nightVision);
        if (effect == null) return 0.0F;
        int duration = effect.getDuration();
        return duration > 200 ? 1.0F
            : 0.7F + MathHelper.sin(((float) duration - partialTicks) * (float) Math.PI * 0.2F) * 0.3F;
    }
}
