package net.warriortale.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.warriortale.mod.WarriorTale;

public class WarriorTaleClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 26.x fabric-rendering no longer ships EntityModelLayerRegistry;
        // we bake our LayerDefinitions directly into ModelParts instead.
        final LayerDefinition folkLayer =
            LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 64);
        final LayerDefinition gharrLayer = TallFolkModel.createLayer();

        EntityRendererRegistry.register(WarriorTale.FOLK,
            ctx -> new FolkRenderer(ctx, new HumanoidModel<>(folkLayer.bakeRoot()), false));
        EntityRendererRegistry.register(WarriorTale.GHARR,
            ctx -> new FolkRenderer(ctx, new HumanoidModel<>(gharrLayer.bakeRoot()), true));
    }
}
