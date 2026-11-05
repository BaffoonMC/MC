package net.soulbound;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Players only see the commands they can use. Some plugins (WorldEdit above all) list every command to everyone, even
 * though only staff can run them: here those are taken out of the list a player's game is sent (the / suggestions and
 * tab completion). Staff with soulbound.commands.seeall see everything. Nothing is blocked from running: each command
 * still checks its own permission as before.
 */
final class CommandHider implements Listener {
    private final Soulbound plugin;

    CommandHider(Soulbound plugin) { this.plugin = plugin; }

    @EventHandler
    public void onSend(PlayerCommandSendEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getConfig().getBoolean("hide-commands.enabled", true) || p.hasPermission("soulbound.commands.seeall")) return;
        Set<String> plugins = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("hide-commands.plugins")) plugins.add(s.toLowerCase(Locale.ROOT));
        Set<String> names = new HashSet<>();
        for (String s : plugin.getConfig().getStringList("hide-commands.names")) names.add(s.toLowerCase(Locale.ROOT).replaceFirst("^/", ""));
        boolean hideNamespaced = plugin.getConfig().getBoolean("hide-commands.namespaced", true);
        e.getCommands().removeIf(label -> {
            String l = label.toLowerCase(Locale.ROOT);
            if (hideNamespaced && l.contains(":")) return true;                       // worldedit:brush, minecraft:give...
            if (names.contains(l)) return true;
            Command c = Bukkit.getCommandMap().getCommand(l);
            if (c == null) return false;
            if (!c.testPermissionSilent(p)) return true;                             // one it can't run anyway
            return c instanceof PluginIdentifiableCommand pc && plugins.contains(pc.getPlugin().getName().toLowerCase(Locale.ROOT));
        });
    }
}
