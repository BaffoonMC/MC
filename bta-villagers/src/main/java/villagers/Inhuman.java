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

	// players who died inhuman and haven't come back yet (by name): the new body is inhuman too
	private static final java.util.Set<String> DEAD = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static void rememberDead(Player p) { if (p.username != null) DEAD.add(p.username); }

	/** Eating the heart: the change plays out over the next few seconds (see rite). */
	public static void begin(Player p) {
		set(p, true);
		if (p instanceof InhumanHolder) ((InhumanHolder) p).vil$setRite(0);
	}

	private static void beat(Player p, float volume, float pitch) { p.world.playSoundAtEntity(null, p, "btavillagers:heartbeat", volume, pitch); }
	private static void whisper(Player p, String line) { p.sendMessage("\u00a74\u00a7oThe Priest whispers: \u00a7c\u00a7o" + line); }

	/** Three heartbeats, each slower and fainter than the last; then it stops. The same order as on the server. */
	private static void rite(Player p, InhumanHolder h) {
		int t = h.vil$rite();
		switch (t) {
			case 0: beat(p, 1.2F, 1.0F); break;
			case 10: whisper(p, "Shh. Let it stop. Let it all stop."); break;
			case 14: beat(p, 1.0F, 0.8F); break;
			case 34: beat(p, 0.8F, 0.6F); break;
			case 44: p.sendMessage("\u00a72\u00a7oYour heart swells with bile and poison. You've never felt so full."); p.heal(EXTRA_HEALTH); break;
			case 60: p.world.playSoundAtEntity(null, p, "btavillagers:heart_stop", 1.0F, 1.0F); break;
			case 80: whisper(p, "Welcome home, brother."); break;
			case 90: p.sendMessage("\u00a74You are no longer human. Monsters know you now; the living don't."); break;
			default: break;
		}
		h.vil$setRite(t >= 90 ? -1 : t + 1);
	}

	/** Server side, every tick of every player. */
	public static void tick(Player p) {
		if (!is(p) && p.username != null && p.isAlive() && DEAD.remove(p.username)) set(p, true);
		if (p instanceof InhumanHolder && ((InhumanHolder) p).vil$rite() >= 0) rite(p, (InhumanHolder) p);
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
