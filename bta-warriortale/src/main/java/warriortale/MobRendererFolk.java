package warriortale;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.MobRenderer;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.core.util.helper.MathHelper;
import org.useless.dragonfly.models.entity.BoneTransform;
import org.useless.dragonfly.models.entity.StaticEntityModel;

/** Draws a folk: head turns, arms and legs swing, children render small and grow. */
@Environment(EnvType.CLIENT)
public class MobRendererFolk extends MobRenderer<MobFolk> {

	public MobRendererFolk(String geometry) {
		super(0.5F);
		this.setModel("main", geometry, 0.0D);
	}

	@Override
	protected void preRenderTransform(MobFolk mob, double x, double y, double z, float yaw, float partialTick) {
		super.preRenderTransform(mob, x, y, z, yaw, partialTick);
		GLRenderer.modelM4f().scale(mob.renderScale() * 0.9375F);
	}

	@Override
	protected StaticEntityModel getAndSetupModelForLayer(MobFolk mob, float partialTick, float scale, int layer) {
		StaticEntityModel model = this.getModel("main");
		model.resetBones();
		BoneTransform head = model.getTransform("head");
		BoneTransform rArm = model.getTransform("rightArm");
		BoneTransform lArm = model.getTransform("leftArm");
		BoneTransform rLeg = model.getTransform("rightLeg");
		BoneTransform lLeg = model.getTransform("leftLeg");
		float bodyYaw = this.getBodyYaw(mob, partialTick);
		float headYaw = this.getHeadYaw(mob, partialTick);
		float headPitch = this.getHeadPitch(mob, partialTick);
		float swing = this.getLimbSwing(mob, partialTick);
		float amount = this.getLimbYaw(mob, partialTick);
		if (head != null) { head.rotY = headYaw - bodyYaw; head.rotX = headPitch; }
		float a = MathHelper.cos(swing * 0.6662F) * 1.4F * amount;
		float b = MathHelper.cos(swing * 0.6662F + 3.1415927F) * 1.4F * amount;
		if (rLeg != null) rLeg.rotX = a;
		if (lLeg != null) lLeg.rotX = b;
		if (rArm != null) rArm.rotX = b * 0.7F;
		if (lArm != null) lArm.rotX = a * 0.7F;
		return model;
	}
}
