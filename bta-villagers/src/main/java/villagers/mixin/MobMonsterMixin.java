package villagers.mixin;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.monster.MobMonster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import villagers.Inhuman;

/** Monsters looking for someone to attack pass over the inhuman. (Strike one and it still turns on you.) */
@Mixin(MobMonster.class)
public abstract class MobMonsterMixin {
	@Inject(method = "findPlayerToAttack", at = @At("RETURN"), cancellable = true)
	private void vil$spare(CallbackInfoReturnable<Entity> cir) {
		if (Inhuman.is(cir.getReturnValue())) cir.setReturnValue(null);
	}
}
