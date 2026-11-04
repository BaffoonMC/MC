package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * What you keep when you die, whatever the keepInventory game rule says:
 *   the world kills you (mobs, falls, lava, drowning, Moloch, God...)  - you keep everything, levels too
 *   another player kills you                                            - everything drops (death.pvp-drops: all), or
 *                                                                         just the backpack (the 27 slots above the hotbar)
 * Moloch's Curse still drops everything, and the sacrificed still keep theirs (their own handlers run after this one).
 */
final class DeathDrops implements Listener {
    private final Soulbound plugin;

    DeathDrops(Soulbound plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.getConfig().getBoolean("death.enabled", true)) return;
        Player p = e.getEntity();
        if (plugin.captures() != null) plugin.captures().unwrap(p);   // hoods and chains drop as the real helmet/chestplate
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        Player killer = p.getKiller();
        if (killer == null && e.getDamageSource().getCausingEntity() instanceof Player k) killer = k;   // arrows, bolts, tridents...
        boolean logged = plugin.combatLog() != null && plugin.combatLog().isLoggingOut(p);   // logged out mid-fight
        if (logged) e.deathMessage(Component.text(p.getName() + " logged out in the middle of a fight", NamedTextColor.WHITE));
        if (!logged && (killer == null || killer.equals(p))) return;
        String mode = plugin.getConfig().getString("death.pvp-drops", "all").toLowerCase(java.util.Locale.ROOT);
        if (mode.equals("none")) return;
        PlayerInventory inv = p.getInventory();
        int from = mode.equals("all") ? 0 : 9, to = mode.equals("all") ? inv.getSize() : 36;
        boolean dropped = false;
        for (int i = from; i < to; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            inv.setItem(i, null);
            if (it.containsEnchantment(org.bukkit.enchantments.Enchantment.VANISHING_CURSE)) continue;
            // straight onto the floor ourselves: with keepInventory on, the game doesn't always drop the event's list
            p.getWorld().dropItemNaturally(p.getLocation(), it);
            dropped = true;
        }
        if (dropped && e.deathMessage() != null)
            e.deathMessage(e.deathMessage().append(Component.text(mode.equals("all") ? " and dropped everything" : " and dropped their pack", NamedTextColor.GRAY)));
    }
}
