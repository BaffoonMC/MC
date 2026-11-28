package net.warriortale.mod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.warriortale.mod.WarriorTale;

/**
 * A villager-folk of the Warrior Tale. Carries genes, can be promised to a
 * partner with bread, and completes the union only at a bed (the bed valve).
 * Children blend both parents' genes. Raiders never target children:
 * any hostile AI added later MUST filter out isBaby().
 */
public class FolkEntity extends AgeableMob {

    public Genes genes = null;
    public int promisedTo = -1;      // entity id of the promised partner (runtime only)
    public int unionCooldown = 0;

    private static final String[] NAMES_F = {"Drita","Teuta","Besa","Mirela","Anja","Vera"};
    private static final String[] NAMES_M = {"Besnik","Agron","Ilir","Dren","Luan","Petrit"};

    public FolkEntity(EntityType<? extends FolkEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AgeableMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ARMOR, 0.0);
    }

    public boolean isGharr() { return false; }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new UnionGoal(this, 0.6));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    protected void ensureGenes() {
        if (genes == null) {
            genes = Genes.roll(this.random, isGharr());
            applyGenes();
            if (!this.hasCustomName()) {
                String[] pool = this.random.nextBoolean() ? NAMES_F : NAMES_M;
                this.setCustomName(Component.literal(pool[this.random.nextInt(pool.length)]));
            }
        }
    }

    protected void applyGenes() {
        if (genes == null) return;
        var hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(14 + genes.vigor * 0.16);   // 14..30
        var sp = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null) sp.setBaseValue(0.22 + genes.twitch * 0.0016);
        var ar = this.getAttribute(Attributes.ARMOR);
        if (ar != null) ar.setBaseValue(genes.hide * 0.05);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            ensureGenes();
            if (unionCooldown > 0) unionCooldown--;
            // knit: slow regen out of combat, scaled by the gene
            if (genes != null && this.tickCount % Math.max(40, 200 - genes.knit) == 0
                    && this.getHealth() < this.getMaxHealth() && this.hurtTime == 0) {
                this.heal(1.0f);
            }
        }
    }

    /** Bread promises this folk to the nearest other promised-less adult folk. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.BREAD) && !this.isBaby()) {
            if (!this.level().isClientSide) {
                FolkEntity partner = findPromiseCandidate();
                if (partner != null) {
                    this.promisedTo = partner.getId();
                    partner.promisedTo = this.getId();
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    spawnHearts((ServerLevel) this.level(), this, 5);
                    spawnHearts((ServerLevel) this.level(), partner, 5);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    private FolkEntity findPromiseCandidate() {
        var list = this.level().getEntitiesOfClass(FolkEntity.class,
                this.getBoundingBox().inflate(8.0),
                e -> e != this && !e.isBaby() && e.promisedTo < 0 && e.unionCooldown == 0);
        return list.isEmpty() ? null : list.get(0);
    }

    public FolkEntity promisedPartner() {
        if (promisedTo < 0) return null;
        var e = this.level().getEntity(promisedTo);
        return (e instanceof FolkEntity f && f.isAlive()) ? f : null;
    }

    public static void spawnHearts(ServerLevel level, FolkEntity at, int n) {
        level.sendParticles(ParticleTypes.HEART,
            at.getX(), at.getY() + at.getBbHeight() + 0.3, at.getZ(), n, 0.4, 0.3, 0.4, 0.02);
    }

    public void playCreak(BlockPos pos, float weight) {
        // weight-scaled pitch: the heavier the folk, the deeper the timber groan
        this.level().playSound(null, pos, WarriorTale.BED_CREAK, SoundSource.BLOCKS,
            0.9f, Math.max(0.55f, 1.35f - weight * 0.5f));
    }

    /** The union's result: a child whose genes blend both parents. */
    public FolkEntity makeChild(ServerLevel level, FolkEntity other) {
        boolean gharrChild = (this.isGharr() || other.isGharr()) && this.random.nextBoolean();
        EntityType<? extends FolkEntity> type = gharrChild ? WarriorTale.GHARR : WarriorTale.FOLK;
        FolkEntity child = type.create(level);
        if (child == null) return null;
        child.ensureGenes();
        child.genes = Genes.child(this.genes, other.genes, this.random);
        child.applyGenes();
        child.setBaby(true);
        String[] pool = this.random.nextBoolean() ? NAMES_F : NAMES_M;
        child.setCustomName(Component.literal(pool[this.random.nextInt(pool.length)]));
        return child;
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return (other instanceof FolkEntity f) ? makeChild(level, f) : null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ensureGenes();
        genes.save(tag);
        tag.putInt("wt_cooldown", unionCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        genes = Genes.load(tag, this.random, isGharr());
        unionCooldown = tag.getInt("wt_cooldown");
        applyGenes();
    }
}
