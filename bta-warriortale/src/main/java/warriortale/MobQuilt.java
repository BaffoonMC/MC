package warriortale;

import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.world.World;

/**
 * The ceremony quilt: one cover over the whole King Size Bed, spawned when
 * the pair settles in and removed when the ceremony ends. It is bed dressing,
 * not a creature - it does not wander, push, or get pushed, and it breathes
 * gently in the renderer (the covers rise and fall).
 */
public class MobQuilt extends MobPathfinder {

	/** safety fuse: a quilt whose ceremony vanished still folds itself away */
	public int life = 680;

	public MobQuilt(World world) {
		super(world);
		this.setTextureIdentifier(WarriorTaleBta.MOD_ID, "quilt");
		this.setSize(1.9F, 0.8F);
		this.moveSpeed = 0F;
	}

	@Override public int getMaxHealth() { return 40; }
	@Override protected boolean canDespawn() { return false; }
	@Override public String getLivingSound() { return null; }
	@Override public String getHurtSound() { return null; }
	@Override protected boolean isMovementCeased() { return true; }

	// bed dressing takes no part in the shoving of bodies
	@Override public boolean isPushable() { return false; }
	@Override public void push(net.minecraft.core.entity.Entity e) {}

	@Override
	protected void updateAI() {
		// deliberately no super: no wandering, no looking, no jumping
		if (this.world.isClientSide) return;
		this.xd = 0; this.yd = Math.min(this.yd, 0); this.zd = 0;
		this.yRot = 0; this.yRotO = 0; this.yBodyRot = 0; this.yBodyRotO = 0;
		if (--life <= 0) this.remove();
	}
}
