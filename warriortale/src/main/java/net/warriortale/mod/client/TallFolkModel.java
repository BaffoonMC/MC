package net.warriortale.mod.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The Gharr body plan: same 64x64 skin UV as a player, but the cubes are
 * declared longer - legs 16 deep, arms 14 - so the texture stretches over
 * a genuinely taller frame. This is the "mods can change the model" part.
 * Part names must match HumanoidModel's expectations exactly.
 */
public final class TallFolkModel {
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the whole frame rides 6px higher than vanilla so the long legs
        // still land on the ground plane
        root.addOrReplaceChild("head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
            PartPose.offset(0, -6, 0));
        root.addOrReplaceChild("hat",
            CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new net.minecraft.client.model.geom.builders.CubeDeformation(0.5f)),
            PartPose.offset(0, -6, 0));
        root.addOrReplaceChild("body",
            CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4),
            PartPose.offset(0, -6, 0));
        root.addOrReplaceChild("right_arm",
            CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 14, 4),
            PartPose.offset(-5, -4, 0));
        root.addOrReplaceChild("left_arm",
            CubeListBuilder.create().texOffs(32, 48).addBox(-1, -2, -2, 4, 14, 4),
            PartPose.offset(5, -4, 0));
        root.addOrReplaceChild("right_leg",
            CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 16, 4),
            PartPose.offset(-1.9f, 6, 0));
        root.addOrReplaceChild("left_leg",
            CubeListBuilder.create().texOffs(16, 48).addBox(-2, 0, -2, 4, 16, 4),
            PartPose.offset(1.9f, 6, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }
    private TallFolkModel() {}
}
