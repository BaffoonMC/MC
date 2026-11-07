package net.soulbound;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI values for scoreboards, tab lists and chat (only registered when PlaceholderAPI is installed):
 *   %soulbound_state%           Human / Graced / Inhuman (coloured)
 *   %soulbound_realm%           Overworld / Nether / Heaven / Moloch's Realm (coloured), or the world's name
 *   %soulbound_moloch_hunger%   Moloch's hunger, 0-100
 *   %soulbound_moloch%          Sleeping / Rising / AWAKE (coloured)
 *   %soulbound_god%             Waiting / Fighting / Resting (after His first defeat), coloured
 */
final class SoulboundPlaceholders extends PlaceholderExpansion {
    private final Soulbound plugin;

    SoulboundPlaceholders(Soulbound plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "soulbound"; }
    @Override public String getAuthor() { return "Soulbound"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer off, String id) {
        Player p = off == null ? null : off.getPlayer();
        switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "state" -> {
                if (p == null) return "";
                if (plugin.deeperStill() != null && plugin.deeperStill().is(p)) return plugin.getConfig().getString("placeholders.forsaken", "&#8A2BE2Forsaken");
                if (plugin.inhuman() != null && plugin.inhuman().is(p)) return plugin.getConfig().getString("placeholders.inhuman", "&#B3122EInhuman");
                if (plugin.grace() != null && plugin.grace().is(p)) return plugin.getConfig().getString("placeholders.graced", "&#F0D987Graced");
                return plugin.getConfig().getString("placeholders.human", "&fHuman");
            }
            case "claim" -> {
                if (p == null || plugin.safeZones() == null) return "";
                var c = plugin.getConfig();
                if (!plugin.safeZones().claimsAllowed(p.getWorld())) return c.getString("placeholders.claim-none", "&8No claims here");
                String owner = plugin.safeZones().claimOwner(p.getLocation());
                if (owner == null) return c.getString("placeholders.claim-wild", "&7Wilderness");
                if (owner.equalsIgnoreCase(p.getName())) return c.getString("placeholders.claim-yours", "&#FBE5ABYours");
                return c.getString("placeholders.claim-owner", "&#FBE5AB{owner}").replace("{owner}", owner);
            }
            case "realm" -> {
                if (p == null) return "";
                World w = p.getWorld();
                if (plugin.zones() != null && plugin.zones().inRealm(p.getLocation())) return plugin.getConfig().getString("placeholders.realm-sacrifice", "&#C83232Moloch's Realm");
                // named worlds (the spawn hub, by default): placeholders.realm-worlds.<world name> = label
                String named = realmName(w);
                if (named != null) return named;
                return switch (w.getEnvironment()) {
                    case NORMAL -> plugin.getConfig().getString("placeholders.realm-overworld", "&#4FE04FOverworld");
                    case NETHER -> plugin.getConfig().getString("placeholders.realm-nether", "&#F07A6ANether");
                    case THE_END -> plugin.getConfig().getString("placeholders.realm-heaven", "&#F0D987Heaven");
                    default -> "&f" + w.getName();
                };
            }
            case "moloch_hunger" -> {
                return plugin.hunger() == null ? "0" : String.valueOf((int) Math.floor(plugin.hunger().value()));
            }
            case "moloch" -> {
                if (plugin.boss() != null && plugin.boss().active()) return "&#C83232&lAWAKE";
                if (plugin.hunger() != null && plugin.hunger().awakenAt() > 0) return "&#F07A6ARising";
                return "&7Sleeping";
            }
            case "god" -> {
                if (plugin.god() != null && plugin.god().fighting()) return "&#F0D987&lFighting";
                return plugin.store().data.getBoolean("god.defeated-once", false) ? "&7Resting" : "&7Waiting";
            }
            default -> { return null; }
        }
    }

    /** A label for a world listed under placeholders.realm-worlds (matched by its name or key, any case); Spawn by default. */
    private String realmName(World w) {
        var sec = plugin.getConfig().getConfigurationSection("placeholders.realm-worlds");
        String name = w.getName().toLowerCase(java.util.Locale.ROOT), key = w.getKey().getKey().toLowerCase(java.util.Locale.ROOT);
        // the creative world(s) count as a realm of their own
        java.util.List<String> creative = plugin.getConfig().getStringList("creative-world.worlds");
        for (String c : creative.isEmpty() ? java.util.List.of("creative") : creative)
            if (c.equalsIgnoreCase(name) || c.equalsIgnoreCase(key)) return plugin.getConfig().getString("placeholders.realm-creative", "&#7FD3EACreative");
        if (sec == null) return name.equals("spawn") || key.equals("spawn") ? "&#F5C351Spawn" : null;
        for (String k : sec.getKeys(false)) {
            String kk = k.toLowerCase(java.util.Locale.ROOT);
            if (kk.equals(name) || kk.equals(key) || kk.equals(w.getKey().toString().toLowerCase(java.util.Locale.ROOT))) return sec.getString(k);
        }
        return null;
    }
}
