package com.lirdi.oldschoollighting.client;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;

import com.lirdi.oldschoollighting.LightingConfig;
import com.lirdi.oldschoollighting.OldSchoolLighting;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;

/** Adds a Config button for this mod in the Mods menu. */
public final class GuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigScreen.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    public static final class ConfigScreen extends GuiConfig {

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public ConfigScreen(GuiScreen parent) {
            super(
                parent,
                new ConfigElement(LightingConfig.config.getCategory(Configuration.CATEGORY_GENERAL))
                    .getChildElements(),
                OldSchoolLighting.MODID,
                false,
                false,
                "Old School Lighting");
        }
    }
}
