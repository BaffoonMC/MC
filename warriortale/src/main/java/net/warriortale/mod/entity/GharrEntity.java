package net.warriortale.mod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The Gharr: tall raid-folk. Stronger and tougher, slower.
 * v0.1 ships them peaceful; the raid AI arrives in v0.2 and must
 * NEVER target children (filter isBaby() in every target selector).
 */
public class GharrEntity extends FolkEntity {
    public GharrEntity(EntityType<? extends FolkEntity> type, Level level) {
        super(type, level);
    }

    @Override public boolean isGharr() { return true; }

    public static AttributeSupplier.Builder createAttributes() {
        return FolkEntity.createAttributes()
            .add(Attributes.MAX_HEALTH, 30.0)
            .add(Attributes.MOVEMENT_SPEED, 0.27)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.ATTACK_DAMAGE, 6.0);
    }
}
