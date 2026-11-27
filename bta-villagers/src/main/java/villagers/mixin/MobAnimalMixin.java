package villagers.mixin;

import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.animal.MobAnimal;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.DamageType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import villagers.Inhuman;

/** The living hate the inhuman: pigs, cows, sheep, chickens, deer and wolves go for one that comes within ten blocks. */
@Mixin(MobAnimal.class)
public abstract class MobAnimalMixin {
	@Unique private Player vil$prey;

	@Inject(method = "updateAI", at = @At("TAIL"))
	private void vil$hate(CallbackInfo ci) {
		MobAnimal self = (MobAnimal) (Object) this;
		if (self.world == null || self.world.isClientSide) return;
		if (self.tickCount % 10 == 0) {
			Player best = null; float bd = 10.0F;
			for (Player p : self.world.players) {
				if (!Inhuman.is(p) || !p.isAlive()) continue;
				float d = self.distanceTo(p);
				if (d < bd) { bd = d; best = p; }
			}
			if (best == null && vil$prey != null && ((MobPathfinder) self).getTarget() == vil$prey) ((MobPathfinder) self).setTarget(null);
			vil$prey = best;
		}
		Player prey = vil$prey;
		if (prey == null) return;
		if (!prey.isAlive() || !Inhuman.is(prey)) { vil$prey = null; ((MobPathfinder) self).setTarget(null); return; }
		((MobPathfinder) self).setTarget(prey);
		if (self.attackTime <= 0 && self.distanceTo(prey) < 1.8F) {
			self.attackTime = 20;
			prey.hurt(self, 2, DamageType.COMBAT);
		}
	}
}
