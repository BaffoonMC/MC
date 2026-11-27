package villagers;

import com.mojang.nbt.tags.CompoundTag;
import net.minecraft.core.entity.MobPathfinder;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import turniplabs.halplibe.helper.network.NetworkHandler;
import net.minecraft.core.world.World;

/**
 * A villager. It wanders, never despawns, and barters: each has a list of offers for its profession (its skin variant).
 * Right-click one to open its trading window.
 */
public class MobVillager extends MobPathfinder {
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
		this.lookAt(player, 30.0F, 30.0F);
		NetworkHandler.sendToPlayer(player, new OpenTradeMsg(this.id, profession()));
		return true;
	}

	/** Server side: do offer number `index` for this player, if they really carry the price. */
	public void trade(Player player, int index) {
		Trades.Offer[] offers = Trades.of(profession());
		if (index < 0 || index >= offers.length) return;
		Trades.Offer o = offers[index];
		ItemStack[] inv = player.inventory.mainInventory;
		int have = 0;
		for (ItemStack s : inv) if (s != null && s.getItem() == o.want) have += s.stackSize;
		if (have < o.wantCount) return;
		int owed = o.wantCount;
		for (int i = 0; i < inv.length && owed > 0; i++) {
			ItemStack s = inv[i];
			if (s == null || s.getItem() != o.want) continue;
			int take = Math.min(owed, s.stackSize);
			s.stackSize -= take;
			owed -= take;
			if (s.stackSize <= 0) player.inventory.setItem(i, null);
		}
		ItemStack out = o.given();
		player.inventory.insertItem(out, true);
		if (out.stackSize > 0) player.dropPlayerItem(out);
		player.inventory.setChanged();
		this.world.playSoundAtEntity(null, this, "random.pop", 0.6F, 1.0F + (this.world.rand.nextFloat() - 0.5F) * 0.3F);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
	}
}
