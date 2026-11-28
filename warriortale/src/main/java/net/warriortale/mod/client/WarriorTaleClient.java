package net.warriortale.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.HumanoidModel;
import net.warriortale.mod.WarriorTale;

public class WarriorTaleClient implements ClientModInitializer {

    public static final ModelLayerLocation FOLK_LAYER =
        new ModelLayerLocation(WarriorTale.id("folk"), "main");
    public static final ModelLayerLocation GHARR_LAYER =
        new ModelLayerLocation(WarriorTale.id("gharr"), "main");

    @Override
    public void onInitializeClient() {
        // folk: vanilla humanoid proportions
        EntityModelLayerRegistry.registerModelLayer(FOLK_LAYER,
            () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 64));
        // gharr: the tall model - legs 16, arms 14, a head above the village
        EntityModelLayerRegistry.registerModelLayer(GHARR_LAYER, TallFolkModel::createLayer);

        EntityRendererRegistry.register(WarriorTale.FOLK,
            ctx -> new FolkRenderer(ctx, FOLK_LAYER, false));
        EntityRendererRegistry.register(WarriorTale.GHARR,
            ctx -> new FolkRenderer(ctx, GHARR_LAYER, true));
    }
}
