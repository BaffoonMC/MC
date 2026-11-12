package net.baffoon.votes;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * %baffoonvotes_votes%        lifetime votes          %baffoonvotes_points%      Vote Shop points
 * %baffoonvotes_today%        e.g. 4/7 sites today    %baffoonvotes_next%        next level's name ("" at the top)
 * %baffoonvotes_next_votes%   votes that level needs  %baffoonvotes_to_next%     votes still to go
 */
final class VotePlaceholders extends PlaceholderExpansion {
    private final BaffoonVotes plugin;

    VotePlaceholders(BaffoonVotes plugin) { this.plugin = plugin; }

    @Override public String getIdentifier() { return "baffoonvotes"; }
    @Override public String getAuthor() { return "BaffoonMC"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer p, String id) {
        if (p == null) return "";
        var uid = p.getUniqueId();
        return switch (id.toLowerCase(java.util.Locale.ROOT)) {
            case "votes" -> String.valueOf(plugin.voteShop().votes(uid));
            case "points" -> String.valueOf(plugin.voteShop().points(uid));
            case "today" -> plugin.voteRanks().votedToday(uid) + "/" + plugin.voteRanks().siteCount();
            case "next" -> { String[] n = plugin.voteRanks().next(uid); yield n == null ? "" : n[0]; }
            case "next_votes" -> { String[] n = plugin.voteRanks().next(uid); yield n == null ? "" : n[1]; }
            case "to_next" -> { String[] n = plugin.voteRanks().next(uid); yield n == null ? "0" : n[2]; }
            default -> null;
        };
    }
}
