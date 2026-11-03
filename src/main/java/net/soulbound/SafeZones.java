package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * No PvP in Moloch's temple (the pyramid, its plaza and moat: moloch.temple-radius) or on God's main island
 * (island.radius). Hits, arrows, thrown potions and tamed pets all count; it's enough for either player to be inside.
 * One exception: anyone dragging a captive can always be hit, so a sacrifice at the summit fire can still be stopped.
 */
final class SafeZones implements Listener {
    private final Soulbound plugin;

    SafeZones(Soulbound plugin) { this.plugin = plugin; }

    private String zone(Location l) {
        if (plugin.getConfig().getBoolean("pvp-off.temple", true) && plugin.zones() != null && plugin.zones().inTemple(l)) return "Moloch's temple";
        if (plugin.getConfig().getBoolean("pvp-off.gods-island", true) && plugin.island() != null && plugin.island().inZone(l)) return "God's island";
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) src = shooter;
        if (src instanceof Tameable t && t.getOwner() instanceof Player owner) src = owner;
        if (!(src instanceof Player attacker) || attacker.equals(victim)) return;
        if (plugin.captures() != null && plugin.captures().isCaptor(victim)) return;   // rescuing someone from a captor is always allowed
        String where = zone(victim.getLocation());
        if (where == null) where = zone(attacker.getLocation());
        if (where == null) return;
        e.setCancelled(true);
        attacker.sendActionBar(Component.text("No fighting in " + where + ".", NamedTextColor.GRAY));
    }
}
