package warriortale;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.MobRenderer;
import net.minecraft.client.render.renderer.GLRenderer;
import net.minecraft.core.util.helper.MathHelper;
import org.useless.dragonfly.models.entity.StaticEntityModel;

/** Draws the ceremony quilt over the King Size Bed, breathing gently. */
@Environment(EnvType.CLIENT)
public class MobRendererQuilt extends MobRenderer<MobQuilt> {

	public MobRendererQuilt(String geometry) {
		super(0.0F);
		this.setModel("main", geometry, 0.0D);
	}

	@Override
	protected void preRenderTransform(MobQuilt mob, double x, double y, double z, float yaw, float partialTick) {
		super.preRenderTransform(mob, x, y, z, yaw, partialTick);
		// the covers rise and fall: a slow breath, about three and a half seconds
		float t = (mob.world != null ? (float) (mob.world.getWorldTime() % 100000L) : 0F) + partialTick;
		float breathe = MathHelper.sin(t * 0.09F) * 0.5F + 0.5F;   // 0..1
		GLRenderer.modelM4f().scale(1.0F, 1.0F + breathe * 0.045F, 1.0F);
	}

	@Override
	protected StaticEntityModel getAndSetupModelForLayer(MobQuilt mob, float partialTick, float scale, int layer) {
		StaticEntityModel model = this.getModel("main");
		model.resetBones();
		return model;
	}
}
