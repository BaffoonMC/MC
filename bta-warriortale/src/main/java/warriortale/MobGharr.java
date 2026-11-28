package warriortale;

import net.minecraft.core.world.World;

/**
 * The Gharr: the tall raid-folk. Stronger, tougher, slower, a head above
 * the village. Peaceful in this version; the raid AI comes later and must
 * never target the young.
 */
public class MobGharr extends MobFolk {
	public MobGharr(World world) {
		super(world);
		this.setTextureIdentifier(WarriorTaleBta.MOD_ID, "gharr");
		this.setSize(0.72F, 2.25F);
		if (world != null) this.setSkinVariant(0);
	}

	@Override public boolean isGharr() { return true; }
	@Override public int getMaxHealth() { return 22 + Math.max(0, vigor) * 16 / 100; }
	@Override protected float speedFromGenes() { return 0.8F + twitch * 0.003F; }
}
