package villagers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.MobRenderer;
import net.minecraft.core.util.helper.MathHelper;
import org.useless.dragonfly.models.entity.BoneTransform;
import org.useless.dragonfly.models.entity.StaticEntityModel;

/** Draws the villager: the head turns to look, the legs walk, the arms stay folded (that pose is in the model itself). */
@Environment(EnvType.CLIENT)
public class MobRendererVillager extends MobRenderer<MobVillager> {
	public MobRendererVillager() {
		super(0.5F);
		this.setModel("main", "geometry.btavillagers.villager", 0.0D);
	}

	@Override
	protected StaticEntityModel getAndSetupModelForLayer(MobVillager mob, float partialTick, float scale, int layer) {
		StaticEntityModel model = this.getModel("main");
		model.resetBones();
		BoneTransform head = model.getTransform("head");
		BoneTransform right = model.getTransform("rightLeg");
		BoneTransform left = model.getTransform("leftLeg");
		float bodyYaw = this.getBodyYaw(mob, partialTick);
		float headYaw = this.getHeadYaw(mob, partialTick);
		float headPitch = this.getHeadPitch(mob, partialTick);
		float swing = this.getLimbSwing(mob, partialTick);
		float amount = this.getLimbYaw(mob, partialTick);
		// same units as the game's own four-legged renderer: the head takes the angles as they come
		if (head != null) {
			head.rotY = headYaw - bodyYaw;
			head.rotX = headPitch;
		}
		if (right != null) right.rotX = MathHelper.cos(swing * 0.6662F) * 1.4F * amount;
		if (left != null) left.rotX = MathHelper.cos(swing * 0.6662F + 3.1415927F) * 1.4F * amount;
		return model;
	}
}
