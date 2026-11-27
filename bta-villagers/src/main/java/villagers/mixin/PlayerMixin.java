package villagers.mixin;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.enums.EnumSleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import villagers.Inhuman;
import villagers.InhumanHolder;

@Mixin(Player.class)
public abstract class PlayerMixin implements InhumanHolder {
	@Unique private boolean vil$isInhuman;
	@Unique private int vil$riteTicks = -1;

	@Override public boolean vil$inhuman() { return vil$isInhuman; }
	@Override public void vil$setInhuman(boolean inhuman) { vil$isInhuman = inhuman; }
	@Override public int vil$rite() { return vil$riteTicks; }
	@Override public void vil$setRite(int ticks) { vil$riteTicks = ticks; }

	@Inject(method = "tick", at = @At("TAIL"))
	private void vil$tick(CallbackInfo ci) {
		Player p = (Player) (Object) this;
		if (p.world != null && !p.world.isClientSide) Inhuman.tick(p);
	}

	/** Dying doesn't free you: remember it for the body you come back in. */
	@Inject(method = "onDeath", at = @At("HEAD"))
	private void vil$death(net.minecraft.core.entity.Entity killer, CallbackInfo ci) {
		Player p = (Player) (Object) this;
		if (vil$isInhuman && p.world != null && !p.world.isClientSide) Inhuman.rememberDead(p);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void vil$save(CompoundTag tag, CallbackInfo ci) { tag.putBoolean("btavillagers_inhuman", vil$isInhuman); }

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void vil$load(CompoundTag tag, CallbackInfo ci) { vil$isInhuman = tag.getBoolean("btavillagers_inhuman"); }

	/** Three more hearts. */
	@Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
	private void vil$hearts(CallbackInfoReturnable<Integer> cir) {
		if (vil$isInhuman) cir.setReturnValue(cir.getReturnValue() + Inhuman.EXTRA_HEALTH);
	}

	/** The inhuman don't sleep. */
	@Inject(method = "sleepInBedAt", at = @At("HEAD"), cancellable = true)
	private void vil$sleep(int x, int y, int z, CallbackInfoReturnable<EnumSleepStatus> cir) {
		if (!vil$isInhuman) return;
		Player p = (Player) (Object) this;
		if (!p.world.isClientSide) p.sendStatusMessage("You lie awake. The inhuman don't sleep.");
		cir.setReturnValue(EnumSleepStatus.OTHER_PROBLEM);
	}
}
