package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Now and then (broadcasts.every-minutes), one random line from broadcasts.lines goes to everyone online: the store,
 * voting. Lines are MiniMessage, so they can have colours and things to click. Never the same line twice in a row.
 */
final class Broadcasts {
    private final Soulbound plugin;
    private BukkitTask task;
    private int last = -1;

    Broadcasts(Soulbound plugin) { this.plugin = plugin; }

    void start() {
        if (task != null) task.cancel();
        long every = Math.max(1, plugin.getConfig().getLong("broadcasts.every-minutes", 180)) * 60 * 20;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::send, every, every);
    }

    /** Send one now (also /sb world broadcast, to test). Returns false if there's nothing to send. */
    boolean send() {
        var c = plugin.getConfig();
        if (!c.getBoolean("broadcasts.enabled", true)) return false;
        List<String> lines = c.getStringList("broadcasts.lines");
        if (lines.isEmpty() || Bukkit.getOnlinePlayers().size() < Math.max(1, c.getInt("broadcasts.min-players", 1))) return false;
        int i = ThreadLocalRandom.current().nextInt(lines.size());
        if (lines.size() > 1 && i == last) i = (i + 1) % lines.size();
        last = i;
        Component msg;
        try { msg = MiniMessage.miniMessage().deserialize(c.getString("broadcasts.prefix", "<gold><bold>BaffoonMC</bold></gold> <dark_gray>></dark_gray> ") + lines.get(i)); }
        catch (RuntimeException ex) { plugin.getLogger().warning("Broadcast line " + (i + 1) + " couldn't be read: " + ex.getMessage()); return false; }
        Bukkit.getServer().sendMessage(Component.empty());
        Bukkit.getServer().sendMessage(msg);
        Bukkit.getServer().sendMessage(Component.empty());
        return true;
    }
}
