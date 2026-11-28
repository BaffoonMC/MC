package net.warriortale.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.warriortale.mod.entity.FolkEntity;
import net.warriortale.mod.entity.GharrEntity;

public class WarriorTale implements ModInitializer {
    public static final String MODID = "warriortale";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    // ---- entities ----
    public static final ResourceKey<EntityType<?>> FOLK_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE, id("folk"));
    public static final EntityType<FolkEntity> FOLK = Registry.register(
        BuiltInRegistries.ENTITY_TYPE, FOLK_KEY,
        EntityType.Builder.of(FolkEntity::new, MobCategory.CREATURE)
            .sized(0.6f, 1.9f).build(FOLK_KEY));

    public static final ResourceKey<EntityType<?>> GHARR_KEY =
        ResourceKey.create(Registries.ENTITY_TYPE, id("gharr"));
    public static final EntityType<FolkEntity> GHARR = Registry.register(
        BuiltInRegistries.ENTITY_TYPE, GHARR_KEY,
        EntityType.Builder.<FolkEntity>of(GharrEntity::new, MobCategory.CREATURE)
            .sized(0.68f, 2.3f).build(GHARR_KEY));

    // ---- sounds ----
    public static final SoundEvent BED_CREAK = Registry.register(
        BuiltInRegistries.SOUND_EVENT, id("bed_creak"),
        SoundEvent.createVariableRangeEvent(id("bed_creak")));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(FOLK, FolkEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GHARR, GharrEntity.createAttributes());
    }
}
