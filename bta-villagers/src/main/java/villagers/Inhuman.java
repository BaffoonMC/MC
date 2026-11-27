package villagers;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.enums.HumanArmorShape;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.helper.MathHelper;
import turniplabs.halplibe.helper.EnvironmentHelper;
import turniplabs.halplibe.helper.network.NetworkHandler;

/**
 * The Heart of Darkness and what eating it makes you: no longer human.
 *
 * Monsters leave you be (unless you strike them) and you carry three more hearts, but farm animals and wolves
 * attack you on sight. Raw meat and raw fish feed you
 * better than they feed anyone else; cooked meat hurts; every other food turns to ash in your mouth. But the sun
 * burns a bare head, you can't sleep, and villagers won't deal with you unless you cover your face with something on
 * your head - and then they charge half as much again. A golden apple makes you human again.
 */
public final class Inhuman {
	private Inhuman() {}

	public static final int EXTRA_HEALTH = 6;          // three hearts
	public static final int COVERED_PRICE_PERCENT = 150;

	public static boolean is(Object player) { return player instanceof InhumanHolder && ((InhumanHolder) player).vil$inhuman(); }

	public static void set(Player p, boolean inhuman) {
		if (!(p instanceof InhumanHolder)) return;
		((InhumanHolder) p).vil$setInhuman(inhuman);
		if (!inhuman && p.getHealth() > p.getMaxHealth()) p.setHealthRaw(p.getMaxHealth());
		sync(p);
	}

	public static boolean headCovered(Player p) { return p.inventory.armorItemInSlot(HumanArmorShape.HEAD) != null; }

	static void sync(Player p) {
		if (EnvironmentHelper.isMultiplayerServer() && NetworkHandler.canReceiveNativePackets(p)) NetworkHandler.sendToPlayer(p, new InhumanSyncMsg(is(p)));
	}

	/** Server side, every tick of every player. */
	public static void tick(Player p) {
		if (p.tickCount % 40 == 0) sync(p);
		if (!is(p) || p.tickCount % 20 != 0 || !p.isAlive()) return;
		// the sun burns a bare head: daylight, open sky above, not in water or rain, nothing worn on the head
		if (p.world.isDaytime() && !headCovered(p) && !p.isInWaterOrRain()
				&& p.world.canBlockSeeTheSky(MathHelper.floor(p.x), MathHelper.floor(p.y), MathHelper.floor(p.z))) {
			if (p.remainingFireTicks <= 0) p.sendStatusMessage("The sun burns you. Cover your head, or find shade.");
			p.remainingFireTicks = Math.max(p.remainingFireTicks, 60);
		}
	}

	public enum Food { NORMAL, RAW, COOKED, ASH, CURE }

	/** What this food is to the inhuman. */
	public static Food judge(ItemStack stack) {
		Item item = stack.getItem();
		if (item == Items.FOOD_APPLE_GOLD) return Food.CURE;
		String key = String.valueOf(item.getKey()).toLowerCase();
		if (key.contains("raw")) return Food.RAW;
		if (key.contains("cooked")) return Food.COOKED;
		return Food.ASH;
	}
}
