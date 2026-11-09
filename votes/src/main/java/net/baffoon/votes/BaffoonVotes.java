package net.baffoon.votes;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * BaffoonMC's voting: /vote (the sites menu, vote ranks and reminders) and /voteshop (points to spend), fed by NuVotifier.
 * Split out of Soulbound; on first start it takes over Soulbound's voteshop.yml so nobody loses votes, points or stock.
 */
public final class BaffoonVotes extends JavaPlugin {
    private VoteShop voteShop;
    private VoteRanks voteRanks;

    @Override
    public void onEnable() {
        boolean fresh = !new File(getDataFolder(), "config.yml").exists();
        saveDefaultConfig();
        if (fresh) migrateConfig();
        migrate();
        voteShop = new VoteShop(this);
        getServer().getPluginManager().registerEvents(voteShop, this);
        voteShop.start();
        voteRanks = new VoteRanks(this);
        getServer().getPluginManager().registerEvents(voteRanks, this);
        var vs = getCommand("voteshop");
        if (vs != null) { vs.setExecutor(voteShop); vs.setTabCompleter(voteShop); }
        var v = getCommand("vote");
        if (v != null) v.setExecutor(voteRanks);
    }

    /** Bring Soulbound's vote data across the first time (votes, points, which sites, the shop's stock). */
    private void migrate() {
        File mine = new File(getDataFolder(), "voteshop.yml");
        File old = new File(getDataFolder().getParentFile(), "Soulbound/voteshop.yml");
        if (mine.exists() || !old.exists()) return;
        try {
            getDataFolder().mkdirs();
            Files.copy(old.toPath(), mine.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            getLogger().info("Brought votes, points and the Vote Shop's stock over from Soulbound (plugins/Soulbound/voteshop.yml).");
        } catch (IOException ex) {
            getLogger().warning("Couldn't copy Soulbound's voteshop.yml across: " + ex.getMessage());
        }
    }

    /** First start: carry over any vote settings changed in Soulbound's config (sites, ranks ladder, shop lists, locked commands). */
    private void migrateConfig() {
        File old = new File(getDataFolder().getParentFile(), "Soulbound/config.yml");
        if (!old.exists()) return;
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(old);
        boolean any = false;
        for (String sec : new String[]{"voteshop", "vote-ranks", "locked-commands"}) {
            if (!y.isConfigurationSection(sec)) continue;
            for (String key : y.getConfigurationSection(sec).getKeys(true)) {
                if (y.isConfigurationSection(sec + "." + key)) continue;
                getConfig().set(sec + "." + key, y.get(sec + "." + key));
                any = true;
            }
        }
        if (any) { saveConfig(); getLogger().info("Copied the vote settings over from Soulbound's config.yml."); }
    }

    VoteShop voteShop() { return voteShop; }
    VoteRanks voteRanks() { return voteRanks; }
}
