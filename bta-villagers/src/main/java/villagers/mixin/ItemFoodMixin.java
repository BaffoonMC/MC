package villagers.mixin;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import villagers.Inhuman;

/** What the inhuman can stomach: raw flesh feeds them more, cooked flesh hurts, anything else is ash. A golden apple cures. */
@Mixin(ItemFood.class)
public abstract class ItemFoodMixin {
	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private void vil$eat(ItemStack stack, World world, Player player, CallbackInfoReturnable<ItemStack> cir) {
		if (!Inhuman.is(player)) return;
		boolean server = !world.isClientSide;
		switch (Inhuman.judge(stack)) {
			case CURE:
				if (server) {
					Inhuman.set(player, false);
					player.sendMessage("§6Warmth floods back into you. You are human again.");
				}
				return;                                   // and the apple is eaten as usual
			case RAW:
				if (server && player.getHealth() < player.getMaxHealth()) player.heal(3);
				return;
			case COOKED:
				if (server) {
					stack.consumeItem(player);
					player.hurt(null, 4, DamageType.COMBAT);
					player.sendStatusMessage("Cooked flesh burns going down. You need it raw.");
				}
				cir.setReturnValue(stack);
				return;
			default:
				if (server) player.sendStatusMessage("It turns to ash in your mouth. Only raw flesh feeds you now.");
				cir.setReturnValue(stack);
		}
	}
}
