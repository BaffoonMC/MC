package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vote ranks: every vote counts towards your level (Newcomer up to Ancient), and you're moved up the LuckPerms track
 * the moment you reach the number. Lifetime votes are kept by the Vote Shop and never go down when you spend points.
 * /vote shows the voting sites, your count and how far it is to the next level.
 */
final class VoteRanks implements CommandExecutor {
    private static final TextColor GOLD = TextColor.color(0xF2C04A);
    private record Step(String group, int votes) {}

    private final Soulbound plugin;

    VoteRanks(Soulbound plugin) { this.plugin = plugin; }

    private List<Step> ladder() {
        List<Step> l = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("vote-ranks.ladder")) {
            String[] w = line.trim().split("\\s+");
            if (w.length < 2) continue;
            try { l.add(new Step(w[0].toLowerCase(Locale.ROOT), Integer.parseInt(w[1]))); } catch (NumberFormatException ignored) {}
        }
        l.sort((a, b) -> Integer.compare(a.votes(), b.votes()));
        return l;
    }

    private static String nice(String group) { return Character.toUpperCase(group.charAt(0)) + group.substring(1); }

    /** Move them up if their votes have earned a level they don't have yet. Never moves anyone down. */
    void check(Player p) {
        if (!plugin.getConfig().getBoolean("vote-ranks.enabled", true)) return;
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) return;
        int v = plugin.voteShop().votes(p.getUniqueId());
        Step target = null;
        for (Step s : ladder()) if (v >= s.votes()) target = s;
        if (target == null || p.hasPermission("group." + target.group())) return;   // already there (or higher, or staff with *)
        String track = plugin.getConfig().getString("vote-ranks.track", "ranks");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getName() + " parent settrack " + track + " " + target.group());
        String name = nice(target.group());
        p.sendMessage(Component.text("You're now ", GOLD).append(Component.text(name, NamedTextColor.WHITE)).append(Component.text("! ", GOLD))
                .append(Component.text("(" + v + " votes)  ", NamedTextColor.GRAY)).append(Component.text("See what you unlocked on the wiki.", NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.openUrl(plugin.getConfig().getString("vote-ranks.levels-url", "https://store.playbaffoonmc.net/wiki.html#levels")))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        if (plugin.getConfig().getBoolean("vote-ranks.announce", true))
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p)
                o.sendMessage(Component.text(p.getName(), NamedTextColor.WHITE).append(Component.text(" voted their way up to ", NamedTextColor.GRAY))
                        .append(Component.text(name, GOLD)).append(Component.text(". ", NamedTextColor.GRAY))
                        .append(Component.text("/vote", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/vote"))));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        s.sendMessage(Component.text("Vote for BaffoonMC", GOLD).append(Component.text("  (each site once a day)", NamedTextColor.GRAY)));
        List<String> sites = plugin.getConfig().getStringList("vote-ranks.sites");
        if (sites.isEmpty()) s.sendMessage(Component.text(" Voting links are coming soon.", NamedTextColor.GRAY));
        int i = 1;
        for (String site : sites) {
            String[] w = site.split("\\|", 2);
            String name = w[0].trim(), url = w.length > 1 ? w[1].trim() : w[0].trim();
            s.sendMessage(Component.text(" " + i++ + ". ", NamedTextColor.GRAY).append(Component.text(name, NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Component.text("Click to vote on " + name, NamedTextColor.GRAY)))));
        }
        if (!(s instanceof Player p)) return true;
        int v = plugin.voteShop().votes(p.getUniqueId());
        Step now = null, next = null;
        for (Step st : ladder()) { if (v >= st.votes()) now = st; else if (next == null) next = st; }
        Component line = Component.text("You've voted " + v + " time" + (v == 1 ? "" : "s"), NamedTextColor.GRAY);
        if (now != null) line = line.append(Component.text(" and you're ", NamedTextColor.GRAY)).append(Component.text(nice(now.group()), NamedTextColor.WHITE));
        s.sendMessage(line.append(Component.text(".", NamedTextColor.GRAY)));
        if (next != null) s.sendMessage(Component.text((next.votes() - v) + " more to ", NamedTextColor.GRAY).append(Component.text(nice(next.group()), GOLD))
                .append(Component.text(".  Points to spend: " + plugin.voteShop().points(p.getUniqueId()) + " ", NamedTextColor.GRAY))
                .append(Component.text("/voteshop", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/voteshop"))));
        else s.sendMessage(Component.text("You've reached the top. Points to spend: " + plugin.voteShop().points(p.getUniqueId()) + " ", NamedTextColor.GRAY)
                .append(Component.text("/voteshop", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/voteshop"))));
        return true;
    }
}
