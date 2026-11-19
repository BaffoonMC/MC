package net.baffoon.bureau;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * %bureau_price%   today's Cold Hard Cash price, e.g. $26.40     %bureau_change%  today's move, e.g. +4.2%
 * %bureau_vault%   Cash in your vault                            %bureau_cash%    Cash in your pockets (online only)
 */
final class BureauPlaceholders extends PlaceholderExpansion {
    private final Bureau plugin;

    BureauPlaceholders(Bureau plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "bureau"; }
    @Override public String getAuthor() { return "BaffoonMC"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer p, String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "price" -> Money.fmt(plugin.exchange().price);
            case "change" -> String.format(Locale.ROOT, "%+.1f%%", plugin.exchange().change() * 100);
            case "vault" -> p == null ? "0" : String.valueOf(plugin.vaults().held(p.getUniqueId()));
            case "cash" -> p == null || p.getPlayer() == null ? "0" : String.valueOf(Cash.count(p.getPlayer()));
            default -> null;
        };
    }
}
