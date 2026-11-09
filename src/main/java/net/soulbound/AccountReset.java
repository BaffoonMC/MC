package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * /sb player <name> reset: wipes a player back to brand new. Their inventory, ender chest, XP, advancements and stats
 * (the game's own files), everything Soulbound keeps on them (soul, curse, homunculus, wardrobe, vote points...),
 * plus whatever other plugins' commands are listed under account-reset.commands (claims, homes, money...).
 * Asks for confirmation first; kicks them if they're online.
 */
final class AccountReset implements Listener {
    private final Soulbound plugin;
    private final Map<String, Object[]> pending = new HashMap<>();   // sender -> {uuid, name, until}
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();     // being wiped right now: can't log in

    AccountReset(Soulbound plugin) { this.plugin = plugin; }

    void command(CommandSender s, String name, boolean confirm) {
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer t = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        if (t == null || (!t.hasPlayedBefore() && online == null)) { s.sendMessage(Component.text("Nobody called " + name + " has played here.", NamedTextColor.RED)); return; }
        String key = s.getName();
        Object[] p = pending.get(key);
        if (!confirm || p == null || !p[0].equals(t.getUniqueId()) || (long) p[2] < System.currentTimeMillis()) {
            pending.put(key, new Object[]{t.getUniqueId(), t.getName(), System.currentTimeMillis() + 30_000});
            s.sendMessage(Component.text("Reset " + t.getName() + "'s whole account?", NamedTextColor.GOLD));
            s.sendMessage(Component.text("This wipes their inventory, ender chest, XP, advancements and stats; their soul (Graced/Inhuman), curse, "
                    + "Forsaken, homunculus, hat wardrobe" + (plugin.getConfig().getBoolean("account-reset.keep-wardrobe", false) ? " (kept: keep-wardrobe is on)" : "")
                    + ", vote points and everything else Soulbound remembers about them"
                    + (plugin.getConfig().getStringList("account-reset.commands").isEmpty() ? "." : ", and runs the other plugins' reset commands (claims, homes, money...)."), NamedTextColor.GRAY));
            s.sendMessage(Component.text("It can't be undone. To go ahead, within 30 seconds: ", NamedTextColor.RED)
                    .append(Component.text("/sb player " + t.getName() + " reset confirm", NamedTextColor.YELLOW)));
            return;
        }
        pending.remove(key);
        UUID id = t.getUniqueId();
        String nm = t.getName() != null ? t.getName() : name;
        busy.add(id);
        if (online != null) online.kick(Component.text("Your BaffoonMC account has been reset.\nJoin again to start fresh.", NamedTextColor.GOLD));
        // a second for the game to finish saving them on the way out, then wipe
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try { s.sendMessage(Component.text(wipe(id, nm), NamedTextColor.GREEN)); }
            finally { busy.remove(id); }
        }, 30L);
    }

    @EventHandler
    public void onLogin(AsyncPlayerPreLoginEvent e) {
        if (busy.contains(e.getUniqueId()))
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("Your account is being reset. Try again in a few seconds.", NamedTextColor.GOLD));
    }

    private String wipe(UUID id, String name) {
        List<String> done = new ArrayList<>();
        String u = id.toString();

        // Soulbound's own memory first, so nothing writes it back afterwards
        if (plugin.curses() != null) plugin.curses().forget(id);
        if (plugin.inhumanHearts() != null) plugin.inhumanHearts().forget(id);
        if (Bukkit.getPluginManager().getPlugin("BaffoonVotes") != null)   // votes, points and vote history live in BaffoonVotes now
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "voteshop forget " + id);
        int strays = 0;
        if (plugin.homunculus() != null) for (var w : Bukkit.getWorlds()) for (Husk h : w.getEntitiesByClass(Husk.class))
            if (id.equals(plugin.homunculus().ownerOf(h))) { h.remove(); strays++; }
        int removed = strip(plugin.store().data, u);
        plugin.store().set("account-reset.homunculi-of." + u, System.currentTimeMillis());   // any of theirs still in unloaded land goes when it loads
        plugin.store().save();
        done.add("Soulbound data (" + removed + " entries" + (strays > 0 ? ", homunculus removed" : "") + ")");

        File df = plugin.getDataFolder();
        if (!plugin.getConfig().getBoolean("account-reset.keep-wardrobe", false) && new File(df, "wardrobe/" + u + ".yml").delete()) done.add("hat wardrobe");
        if (new File(df, "creative/" + u + ".yml").delete()) done.add("creative inventory");

        // the game's own files: player data (inventory, ender chest, XP, effects, where they are), advancements, stats
        int files = 0;
        Set<String> names = Set.of(u + ".dat", u + ".dat_old", u + ".json", u + ".nbt");
        for (var w : Bukkit.getWorlds()) {
            Path root = w.getWorldFolder().toPath();
            try (Stream<Path> st = Files.walk(root, 3)) {
                for (Path f : st.filter(f -> names.contains(f.getFileName().toString())).toList()) {
                    String dir = f.getParent().getFileName().toString();
                    if (!(dir.equals("playerdata") || dir.equals("advancements") || dir.equals("stats") || dir.equals("data") || f.getParent().toString().contains("players"))) continue;
                    try { Files.deleteIfExists(f); files++; } catch (IOException ex) { plugin.getLogger().warning("Account reset: couldn't delete " + f + ": " + ex.getMessage()); }
                }
            } catch (IOException ex) { plugin.getLogger().warning("Account reset: couldn't look through " + root + ": " + ex.getMessage()); }
        }
        done.add(files + " game file" + (files == 1 ? "" : "s") + " (inventory, ender chest, XP, advancements, stats)");

        // Essentials: homes, money, nickname, mail... (its own reset: deletes their userdata file and forgets them)
        if (plugin.getConfig().getBoolean("account-reset.essentials", true)) {
            var ess = Bukkit.getPluginManager().getPlugin("Essentials");
            if (ess != null && ess.isEnabled()) try {
                Object user = ess.getClass().getMethod("getUser", UUID.class).invoke(ess, id);
                if (user != null) { user.getClass().getMethod("reset").invoke(user); done.add("Essentials (homes, money, nickname, mail)"); }
            } catch (ReflectiveOperationException | RuntimeException ex) { plugin.getLogger().warning("Account reset: Essentials didn't reset " + name + ": " + ex); }
        }

        // other plugins
        int ran = 0;
        for (String c : plugin.getConfig().getStringList("account-reset.commands")) {
            if (c == null || c.isBlank()) continue;
            String cmd = c.replace("{player}", name).replace("{uuid}", u);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            try { if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) ran++; }
            catch (RuntimeException ex) { plugin.getLogger().warning("Account reset: '" + cmd + "' failed: " + ex.getMessage()); }
        }
        if (ran > 0) done.add(ran + " other plugin command" + (ran == 1 ? "" : "s"));

        plugin.getLogger().info("Account reset for " + name + " (" + u + "): " + String.join(", ", done));
        return name + "'s account is reset: " + String.join(", ", done) + ". They'll start fresh next time they join.";
    }

    /** Remove every key that names this player, and every list entry that does, anywhere in data.yml. */
    private int strip(ConfigurationSection sec, String u) {
        int n = 0;
        for (String k : new ArrayList<>(sec.getKeys(false))) {
            if (k.contains(u)) { sec.set(k, null); n++; continue; }
            Object v = sec.get(k);
            if (v instanceof ConfigurationSection child) n += strip(child, u);
            else if (v instanceof List<?> l && l.stream().anyMatch(x -> x != null && String.valueOf(x).contains(u))) {
                List<Object> keep = new ArrayList<>();
                for (Object x : l) if (x == null || !String.valueOf(x).contains(u)) keep.add(x); else n++;
                sec.set(k, keep);
            } else if (v instanceof String str && str.equals(u)) { sec.set(k, null); n++; }
        }
        return n;
    }

    /** A homunculus of someone who was reset, found in land that has just loaded: it goes. */
    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        ConfigurationSection gone = plugin.store().data.getConfigurationSection("account-reset.homunculi-of");
        if (gone == null || plugin.homunculus() == null) return;
        for (Entity en : e.getEntities()) {
            UUID o = plugin.homunculus().ownerOf(en);
            if (o == null) continue;
            long resetAt = gone.getLong(o.toString(), 0);
            if (resetAt > 0 && plugin.homunculus().bornOf(en) < resetAt) en.remove();   // older than the reset: theirs from before
        }
    }
}
