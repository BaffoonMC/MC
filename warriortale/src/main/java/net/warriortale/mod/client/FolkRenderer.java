package net.warriortale.mod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;
import net.warriortale.mod.WarriorTale;
import net.warriortale.mod.entity.FolkEntity;

/**
 * NOTE: the render-state API (1.21.2+) is the most version-drift-prone
 * part of this mod. If 26.3 moved these signatures, the fixes are local
 * to this file: createRenderState / extractRenderState / getTextureLocation.
 */
public class FolkRenderer extends HumanoidMobRenderer<FolkEntity, FolkRenderer.FolkRenderState, HumanoidModel<FolkRenderer.FolkRenderState>> {

    public static class FolkRenderState extends HumanoidRenderState {
        public int variant = 0;
        public boolean gharr = false;
    }

    private static final ResourceLocation[] FOLK_SKINS = {
        WarriorTale.id("textures/entity/folk_0.png"),
        WarriorTale.id("textures/entity/folk_1.png"),
        WarriorTale.id("textures/entity/folk_2.png"),
        WarriorTale.id("textures/entity/folk_3.png"),
    };
    private static final ResourceLocation GHARR_SKIN =
        WarriorTale.id("textures/entity/gharr_0.png");

    private final boolean gharr;

    public FolkRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, boolean gharr) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(layer)), 0.5f);
        this.gharr = gharr;
    }

    @Override
    public FolkRenderState createRenderState() {
        return new FolkRenderState();
    }

    @Override
    public void extractRenderState(FolkEntity entity, FolkRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.variant = entity.genes == null ? 0 : entity.genes.variant;
        state.gharr = entity.isGharr();
    }

    @Override
    public ResourceLocation getTextureLocation(FolkRenderState state) {
        return (gharr || state.gharr) ? GHARR_SKIN
            : FOLK_SKINS[Math.floorMod(state.variant, FOLK_SKINS.length)];
    }
}
