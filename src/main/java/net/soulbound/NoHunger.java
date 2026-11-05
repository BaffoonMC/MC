package net.soulbound;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.List;

/**
 * No hunger in the worlds listed under no-hunger.worlds (spawn, by default): the hunger bar simply doesn't go down there.
 * Nothing is topped up either, so it doesn't heal anyone; eating still works as normal.
 */
final class NoHunger implements Listener {
    private final Soulbound plugin;

    NoHunger(Soulbound plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        List<String> worlds = plugin.getConfig().getStringList("no-hunger.worlds");
        if (worlds.isEmpty() && !plugin.getConfig().isSet("no-hunger.worlds")) worlds = List.of("spawn");
        if (!worlds.contains(p.getWorld().getName())) return;
        if (e.getFoodLevel() < p.getFoodLevel()) {                 // only the going-down is stopped
            e.setCancelled(true);
            p.setSaturation(Math.max(p.getSaturation(), 0f));
        }
    }
}
