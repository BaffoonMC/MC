package warriortale;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.block.BlockLogicBed;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;

import java.util.List;

/**
 * A villager-folk of the Warrior Tale. Genes drive health, speed and healing;
 * bread promises a pair; the union happens only at a bed, at night, together.
 * Children blend both parents. Any hostile AI added later must NEVER target
 * the young (isGrown() false).
 */
public class MobFolk extends MobPathfinder {

	// ---- the genes (0..100) ----
	public int vigor, twitch, knit, hide;
	public int growth;                       // 0..GROWN; below GROWN it is a child
	public static final int GROWN = 6000;

	/** synced to clients: 0 = standing, 1 = lying (lower, face up), 2 = lying (upper, face down) */
	public static final int DATA_LIE = 25;

	public int promisedTo = -1;              // entity id, runtime only
	public int cooldown = 0;
	private int ceremony = 0;
	private TilePos bed = null;
	private boolean bedKing = false;
	private int scanTick = 0;
	// the bed's true centre and axis, fixed when the ceremony starts
	private double bedCX, bedCY, bedCZ;
	private float bedYaw;

	/** every living folk, tracked by the mod itself - the engine's
	 *  chunk-based entity query proved unreliable for this. */
	private static final java.util.List<MobFolk> LIVING = new java.util.ArrayList<>();

	private static final String[] NAMES_F = {"Drita", "Teuta", "Besa", "Mirela", "Anja", "Vera"};
	private static final String[] NAMES_M = {"Besnik", "Agron", "Ilir", "Dren", "Luan", "Petrit"};

	public MobFolk(World world) {
		super(world);
		this.setTextureIdentifier(WarriorTaleBta.MOD_ID, "folk");
		this.setSize(0.6F, 1.8F);
		this.entityData.define(DATA_LIE, Byte.valueOf((byte) 0), Byte.class);
		rollGenes();
		this.growth = GROWN;
		this.moveSpeed = speedFromGenes();
		if (world != null) {
			String[] pool = world.rand.nextBoolean() ? NAMES_F : NAMES_M;
			this.setNickname(pool[world.rand.nextInt(pool.length)]);
			this.setSkinVariant(world.rand.nextInt(4));
		}
	}

	protected void rollGenes() {
		java.util.Random r = this.world != null ? this.world.rand : new java.util.Random();
		int base = isGharr() ? 65 : 45;
		vigor = clamp(base + r.nextInt(31) - 10);
		twitch = clamp((isGharr() ? 35 : 55) + r.nextInt(31) - 10);
		knit = clamp(45 + r.nextInt(31) - 10);
		hide = clamp(base + r.nextInt(31) - 15);
	}

	public boolean isGharr() { return false; }
	public boolean isGrown() { return growth >= GROWN; }
	private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
	protected float speedFromGenes() { return 0.9F + twitch * 0.004F; }

	@Override public int getMaxHealth() { return 14 + Math.max(0, vigor) * 16 / 100; }
	@Override protected boolean canDespawn() { return false; }
	@Override public String getLivingSound() { return null; }
	@Override protected boolean isMovementCeased() { return ceremony > 0 || lie() != 0; }

	public int lie() { return this.entityData.getByte(DATA_LIE); }
	public void setLie(int v) { this.entityData.set(DATA_LIE, Byte.valueOf((byte) v)); }

	/** the renderer reads this: child-small, growing to full size */
	public float renderScale() {
		float g = Math.min(1F, growth / (float) GROWN);
		return (isGharr() ? 1.18F : 1.0F) * (0.5F + 0.5F * g);
	}

	@Override
	protected void updateAI() {
		super.updateAI();
		if (this.world.isClientSide) return;
		if (!LIVING.contains(this)) LIVING.add(this);
		if (this.world.getWorldTime() % 100 == 0) LIVING.removeIf(f -> !f.isAlive());
		if (cooldown > 0) cooldown--;
		if (growth < GROWN) {
			growth++;
			if (growth == GROWN) this.setSize(0.6F, 1.8F);
		}
		// knit: slow mending while unhurt
		if (this.hurtTime == 0 && this.getHealth() < this.getMaxHealth()
				&& this.world.getWorldTime() % Math.max(40, 200 - knit) == 0) {
			this.heal(1);
		}
		union();
	}

	// ---- the union: bed-gated, together, at night ----
	private void union() {
		MobFolk partner = partner();
		// one of the pair orchestrates (the elder id); the other follows
		if (partner != null && partner.id < this.id) return;
		if (ceremony > 0) {
			ceremony--;
			if (partner == null) { ceremony = 0; setLie(0); return; }
			// hold them on the bed: centred on it, lying along its axis,
			// heads toward the pillow, body yaw pinned. In a mixed pair
			// the Gharr takes the top; the blanket drapes over the top one.
			MobFolk top = partner;
			if (this.isGharr() && !partner.isGharr()) top = this;
			MobFolk bottom = (top == this) ? partner : this;
			bottom.setPos(bedCX, bedCY, bedCZ);
			top.setPos(bedCX, bedCY + (top.isGharr() ? 0.4 : 0.33), bedCZ);
			pinPose(this); pinPose(partner);
			if (bottom.lie() != 1) { bottom.setLie(1); top.setLie(2); }
			if (ceremony == 560) creak(0.65F);                        // the settle
			if (ceremony == 400 || ceremony == 220) creak(0.8F);      // turning over
			if (ceremony % 30 == 0) hearts(2);
			if (ceremony == 0) {
				setLie(0); partner.setLie(0);
				MobFolk child = makeChild(partner);
				if (child != null) {
					child.setPos(this.x, this.y, this.z);
					child.spawnInit();
					this.world.entityJoinedWorld(child);
					child.hearts(7);
				}
				cooldown = 6000; partner.cooldown = 6000;
				promisedTo = -1; partner.promisedTo = -1;
				bed = null; partner.bed = null;
			}
			return;
		}
		if (partner == null || cooldown > 0 || !isGrown() || !isNight()) return;
		if (bed == null) {
			if (--scanTick > 0) return;
			scanTick = 40;
			bed = findBed();
			if (bed != null) { hearts(1); if (partner != null) partner.hearts(1); }  // bed found
			return;
		}
		double d2 = this.distanceToSqr(bed.x + 0.5, bed.y + 0.5, bed.z + 0.5);
		if (d2 > 6.25) {
			if (this.world.getWorldTime() % 20 == 0) {
				this.setPathToEntity(this.world.getEntityPathToTilePos(this, bed, 16F));
				if (partner.distanceTo(this) > 4F)
					partner.setPathToEntity(partner.world.getEntityPathToTilePos(partner, bed, 16F));
			}
			return;
		}
		if (partner.distanceTo(this) < 3F) {
			if (bedKing) {
				// the 2x2 King Size Bed: its centre is the seam of the four
				// quarters; pillows span the north pair
				bedCX = bed.x + 1.0;
				bedCZ = bed.z + 1.0 + 0.12;
				bedCY = bed.y + 0.72;
				bedYaw = 0F;
				creak(pitchFor(this)); partner.creak(pitchFor(partner));
				ceremony = 600;
				return;
			}
			// an ordinary bed: read its direction, heads at the pillow
			int meta = this.world.getBlockData(bed);
			int dir = BlockLogicBed.DIRECTION.get(meta);
			boolean isHead = BlockLogicBed.IS_HEAD.get(meta) != 0;
			Side toHead = BlockLogicBed.footToHeadMap[Math.floorMod(dir, BlockLogicBed.footToHeadMap.length)];
			// true centre of the two-block bed, then a nudge toward the foot
			// so heads sit on the pillow instead of overhanging it
			bedCX = bed.x + 0.5 + toHead.offsetX() * ((isHead ? -0.5 : 0.5) - 0.22);
			bedCZ = bed.z + 0.5 + toHead.offsetZ() * ((isHead ? -0.5 : 0.5) - 0.22);
			bedCY = bed.y + 0.62;
			// the engine renders mobs with a hidden half-turn, so add 180
			// to make the lying head point at the pillow, not the foot
			bedYaw = (float) Math.toDegrees(Math.atan2(-toHead.offsetX(), toHead.offsetZ())) + 180F;
			creak(pitchFor(this)); partner.creak(pitchFor(partner));   // climbing in
			ceremony = 600;
		}
	}

	private void pinPose(MobFolk m) {
		m.yRot = bedYaw; m.yRotO = bedYaw;
		m.yBodyRot = bedYaw; m.yBodyRotO = bedYaw;
		m.xRot = 0; m.xRotO = 0;
		m.xd = 0; m.yd = 0; m.zd = 0;   // no drift: the pair stays as one
	}

	private static float pitchFor(MobFolk m) { return Math.max(0.55F, 1.35F - m.bbHeight * 0.3F); }

	private boolean isNight() {
		long t = this.world.getWorldTime() % 24000L;
		return t >= 12000 && t <= 23999;
	}

	private TilePos findBed() {
		int cx = (int) Math.floor(this.x), cy = (int) Math.floor(this.y), cz = (int) Math.floor(this.z);
		TilePos p = new TilePos();
		TilePos best = null;
		double bestD = Double.MAX_VALUE;
		boolean bestKing = false;
		for (int dx = -12; dx <= 12; dx++)
			for (int dz = -12; dz <= 12; dz++)
				for (int dy = -3; dy <= 3; dy++) {
					p.x = cx + dx; p.y = cy + dy; p.z = cz + dz;
					var type = this.world.getBlockType(p);
					boolean king = (WarriorTaleBta.kingBed != null && type == WarriorTaleBta.kingBed
						&& this.world.getBlockData(p) == 0);   // anchor on the NW quarter only
					if (king || type == Blocks.BED) {
						double d = dx * dx + dy * dy + dz * dz;
						// a king bed always beats an ordinary bed
						if ((king && !bestKing) || (king == bestKing && d < bestD)) {
							bestD = d; bestKing = king;
							best = new TilePos();
							best.x = p.x; best.y = p.y; best.z = p.z;
						}
					}
				}
		bedKing = bestKing;
		return best;
	}

	public MobFolk partner() {
		if (promisedTo < 0) return null;
		for (MobFolk f : LIVING)
			if (f.id == promisedTo && f.isAlive() && f.world == this.world && f.distanceTo(this) < 48F)
				return f;
		return null;
	}

	@Override
	public boolean interact(Player player) {
		if (this.world.isClientSide) return true;
		ItemStack held = player.inventory.getCurrentItem();
		if (held != null && held.getItem() == Items.FOOD_BREAD && isGrown() && cooldown == 0 && promisedTo < 0) {
			int seen = 0;
			for (MobFolk f : LIVING) {
				if (f == this || !f.isAlive() || f.world != this.world || f.distanceTo(this) > 8F) continue;
				seen++;
				if (!f.isGrown() || f.promisedTo >= 0 || f.cooldown > 0) continue;
				this.promisedTo = f.id;
				f.promisedTo = this.id;
				held.consumeItem(player);
				hearts(5); f.hearts(5);
				String a = this.nickname != null ? this.nickname : "This one";
				String b = f.nickname != null ? f.nickname : "another";
				player.sendStatusMessage(a + " and " + b + " are promised. They will seek a bed at night.");
				return true;
			}
			player.sendStatusMessage(seen == 0
				? "No other folk within eight blocks."
				: "No one near is free to be promised.");
			return true;
		}
		return super.interact(player);
	}

	public void hearts(int n) {
		for (int i = 0; i < n; i++)
			this.world.spawnParticle("heart",
				this.x + (this.world.rand.nextDouble() - 0.5) * 0.8,
				this.y + this.bbHeight + 0.3,
				this.z + (this.world.rand.nextDouble() - 0.5) * 0.8,
				0, 0.08, 0, 0, false);
	}

	public void creak(float pitch) {
		this.world.playSoundAtEntity(null, this, WarriorTaleBta.MOD_ID + ":bed_creak", 0.9F, pitch);
	}

	/** the drawChild rule: blend both parents, plus or minus seven */
	public MobFolk makeChild(MobFolk other) {
		boolean gharrChild = (this.isGharr() || other.isGharr()) && this.world.rand.nextBoolean();
		MobFolk child = gharrChild ? new MobGharr(this.world) : new MobFolk(this.world);
		child.vigor = blend(this.vigor, other.vigor);
		child.twitch = blend(this.twitch, other.twitch);
		child.knit = blend(this.knit, other.knit);
		child.hide = blend(this.hide, other.hide);
		child.moveSpeed = child.speedFromGenes();
		child.growth = 0;
		child.setSize(0.4F, 0.95F);
		child.setSkinVariant(this.world.rand.nextBoolean() ? this.getSkinVariant() : other.getSkinVariant());
		return child;
	}

	private int blend(int a, int b) {
		return clamp((a + b) / 2 + this.world.rand.nextInt(15) - 7);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("wtVigor", vigor); tag.putInt("wtTwitch", twitch);
		tag.putInt("wtKnit", knit);   tag.putInt("wtHide", hide);
		tag.putInt("wtGrowth", growth); tag.putInt("wtCooldown", cooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		vigor = tag.getIntegerOrDefault("wtVigor", vigor);
		twitch = tag.getIntegerOrDefault("wtTwitch", twitch);
		knit = tag.getIntegerOrDefault("wtKnit", knit);
		hide = tag.getIntegerOrDefault("wtHide", hide);
		growth = tag.getIntegerOrDefault("wtGrowth", GROWN);
		cooldown = tag.getIntegerOrDefault("wtCooldown", 0);
		this.moveSpeed = speedFromGenes();
		if (growth < GROWN) this.setSize(0.4F, 0.95F);
	}
}
