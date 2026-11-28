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
		int lie = mob.lie();
		if (lie > 0) {
			// lay the whole model down: lower face-up, upper face-down over her,
			// both heads toward the same end of the bed
			GLRenderer.modelM4f().translate(0.0F, 0.14F, 0.0F);
			if (lie == 2) GLRenderer.modelM4f().rotateY((float) Math.PI);
			GLRenderer.modelM4f().rotateX(lie == 1 ? (float) (Math.PI / 2) : (float) (-Math.PI / 2));
			// recentre the body on the bed: the model pivots at the feet,
			// so shift it half its length back along its own axis
			GLRenderer.modelM4f().translate(0.0F, -0.85F, 0.0F);
		}
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
		BoneTransform blanket = model.getTransform("blanket");
		int lie = mob.lie();
		if (lie > 0) {
			// in the bed: no walking, the blanket shows on the lower one,
			// her chin tips up, his head bends back to meet her eyes
			if (blanket != null) blanket.visible = (lie == 2);   // draped over the top of the pair
			if (head != null) head.rotX = (lie == 1 ? -14 : -32);
			if (rArm != null) rArm.rotZ = -8;
			if (lArm != null) lArm.rotZ = 8;
			return model;
		}
		if (blanket != null) blanket.visible = false;
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
