package villagers;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.net.command.TextFormatting;
import net.minecraft.core.world.World;

/**
 * A villager. It wanders, never despawns, and barters: each has a list of offers for its profession (its skin variant).
 * Right-click to hear the current offer; right-click holding what it asks for to trade; sneak + right-click for the next offer.
 */
public class MobVillager extends MobPathfinder {
	private int offer;

	public MobVillager(World world) {
		super(world);
		this.setTextureIdentifier(Villagers.MOD_ID, "villager");
	}

	public int profession() { return Math.floorMod(this.getSkinVariant(), Villagers.PROFESSIONS.length); }

	public String professionName() {
		String p = Villagers.PROFESSIONS[profession()];
		return Character.toUpperCase(p.charAt(0)) + p.substring(1);
	}

	@Override public int getMaxHealth() { return 20; }
	@Override protected boolean canDespawn() { return false; }
	@Override public String getLivingSound() { return null; }

	@Override
	public boolean interact(Player player) {
		if (this.world.isClientSide) return true;
		Trades.Offer[] offers = Trades.of(profession());
		offer = Math.floorMod(offer, offers.length);
		String who = TextFormatting.LIME + professionName() + TextFormatting.WHITE + ": ";
		if (player.isSneaking()) {
			offer = (offer + 1) % offers.length;
			player.sendMessage(who + describe(offers[offer]) + TextFormatting.GRAY + "  (" + (offer + 1) + "/" + offers.length + ")");
			return true;
		}
		Trades.Offer o = offers[offer];
		ItemStack held = player.getHeldItem();
		if (held != null && held.getItem() == o.want && held.stackSize >= o.wantCount) {
			held.stackSize -= o.wantCount;
			if (held.stackSize <= 0) player.inventory.setItem(player.inventory.getCurrentSlot(), null);
			ItemStack out = o.given();
			player.inventory.insertItem(out, true);
			if (out.stackSize > 0) player.dropPlayerItem(out);
			this.world.playSoundAtEntity(null, this, "random.pop", 0.6F, 1.0F + (this.world.rand.nextFloat() - 0.5F) * 0.3F);
			player.sendMessage(who + "A pleasure doing business.");
			return true;
		}
		player.sendMessage(who + describe(o) + TextFormatting.GRAY + "  (sneak + right-click for my next offer)");
		return true;
	}

	private static String describe(Trades.Offer o) {
		return "I'll give " + TextFormatting.YELLOW + o.giveCount + " " + o.given().getDisplayName() + TextFormatting.WHITE
			+ " for " + TextFormatting.YELLOW + o.wantCount + " " + o.wanted().getDisplayName() + TextFormatting.WHITE + ".";
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Offer", offer);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		offer = tag.getInteger("Offer");
	}
}
