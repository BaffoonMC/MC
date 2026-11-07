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
 * (island.radius), nor inside anyone's GriefPrevention claim (pvp-off.claims). Hits, arrows, thrown potions and tamed
 * pets all count; it's enough for either player to be inside.
 * One exception: anyone dragging a captive can always be hit, so a sacrifice at the summit fire can still be stopped.
 */
final class SafeZones implements Listener {
    private final Soulbound plugin;

    SafeZones(Soulbound plugin) { this.plugin = plugin; }

    /** The no-PvP place this spot is in ("Moloch's temple", "God's island", "claims"), or null. */
    String zone(Location l) {
        if (plugin.getConfig().getBoolean("pvp-off.temple", true) && plugin.zones() != null && plugin.zones().inTemple(l)) return "Moloch's temple";
        if (plugin.getConfig().getBoolean("pvp-off.gods-island", true) && plugin.island() != null && plugin.island().inZone(l)) return "God's island";
        if (plugin.getConfig().getBoolean("pvp-off.claims", true) && inClaim(l)) return "claims";
        return null;
    }

    // ---------------------------------------------------------------- GriefPrevention, found by name
    private Object dataStore; private java.lang.reflect.Method getClaimAt; private boolean gpTried;

    private boolean inClaim(Location l) { return claimAt(l) != null; }

    /** False when GriefPrevention has land claims switched off in this world (its Claims.Mode is Disabled). */
    boolean claimsAllowed(org.bukkit.World w) {
        org.bukkit.plugin.Plugin gp = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gp == null || !gp.isEnabled()) return false;
        try { return (Boolean) gp.getClass().getMethod("claimsEnabledForWorld", org.bukkit.World.class).invoke(gp, w); }
        catch (ReflectiveOperationException | RuntimeException ex) { return true; }
    }

    /** Who owns the claim here ("an administrator" for admin claims), or null outside claims. */
    String claimOwner(Location l) {
        Object c = claimAt(l);
        if (c == null) return null;
        try { return String.valueOf(c.getClass().getMethod("getOwnerName").invoke(c)); }
        catch (ReflectiveOperationException | RuntimeException ex) { return "someone"; }
    }

    /** The GriefPrevention claim at this spot (the same object for every spot in one claim), or null. */
    Object claimAt(Location l) {
        if (dataStore == null) {
            if (gpTried) return null;
            gpTried = true;
            org.bukkit.plugin.Plugin gp = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
            if (gp == null || !gp.isEnabled()) return null;
            try {
                Class<?> claimC = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, gp.getClass().getClassLoader());
                Object store = gp.getClass().getField("dataStore").get(gp);
                getClaimAt = store.getClass().getMethod("getClaimAt", Location.class, boolean.class, claimC);
                dataStore = store;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                plugin.getLogger().warning("No-PvP in claims: couldn't read GriefPrevention claims (" + ex + ").");
                return null;
            }
        }
        try { return getClaimAt.invoke(dataStore, l, true, null); }
        catch (ReflectiveOperationException | RuntimeException ex) { return null; }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) src = shooter;
        if (src instanceof Tameable t && t.getOwner() instanceof Player owner) src = owner;
        if (!(src instanceof Player) && e.getDamageSource().getCausingEntity() instanceof Player cause) src = cause;   // TNT, crystals, potion clouds...
        if (!(src instanceof Player attacker) || attacker.equals(victim)) return;
        if (plugin.captures() != null && plugin.captures().isCaptor(victim)) return;   // rescuing someone from a captor is always allowed
        String where = zone(victim.getLocation());
        if (where == null) where = zone(attacker.getLocation());
        if (where == null) return;
        e.setCancelled(true);
        attacker.sendActionBar(Component.text("No fighting in " + where + ".", NamedTextColor.GRAY));
    }
}
