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
		this.setSize(0.6F, 1.95F);
	}

	public int profession() { return Math.floorMod(this.getSkinVariant(), Villagers.PROFESSIONS.length); }

	public String professionName() {
		String p = Villagers.PROFESSIONS[profession()];
		return Character.toUpperCase(p.charAt(0)) + p.substring(1);
	}

	@Override public int getMaxHealth() { return 20; }
	@Override protected boolean canDespawn() { return false; }
	@Override public String getLivingSound() { return null; }

	// who it is dealing with: it stands still and faces them until they walk off
	private Player customer;
	private int customerTicks;

	@Override
	protected boolean isMovementCeased() { return customer != null; }

	/** Wandering as usual, plus what makes it feel alive: it turns its head, a little each tick, to watch whoever is near. */
	@Override
	protected void updateAI() {
		super.updateAI();
		if (customer != null && (--customerTicks <= 0 || !customer.isAlive() || this.distanceToSqr(customer) > 6 * 6)) customer = null;
		Player watch = customer != null ? customer : (this.hasPath() ? null : this.world.getClosestPlayerToEntity(this, 6.0));
		if (watch != null) this.lookAt(watch, 8.0F, 20.0F);
	}

	@Override
	public boolean interact(Player player) {
		if (this.world.isClientSide) return true;
		customer = player;
		customerTicks = 20 * 30;
		if (Inhuman.is(player) && !Inhuman.headCovered(player)) {
			player.sendStatusMessage("The villager shrinks from you. Cover your face and they may still deal.");
			return true;
		}
		NetworkHandler.sendToPlayer(player, new OpenTradeMsg(this.id, profession(), pricePercent(player)));
		return true;
	}

	private static int pricePercent(Player player) { return Inhuman.is(player) ? Inhuman.COVERED_PRICE_PERCENT : 100; }

	/** Server side: do offer number `index` for this player, if they really carry the price. */
	public void trade(Player player, int index) {
		Trades.Offer[] offers = Trades.of(profession());
		if (index < 0 || index >= offers.length) return;
		Trades.Offer o = offers[index];
		if (Inhuman.is(player) && !Inhuman.headCovered(player)) return;
		int price = o.price(pricePercent(player));
		ItemStack[] inv = player.inventory.mainInventory;
		int have = 0;
		for (ItemStack s : inv) if (s != null && s.getItem() == o.want) have += s.stackSize;
		if (have < price) return;
		int owed = price;
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
