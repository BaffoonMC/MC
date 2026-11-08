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

    private static String hm(long ms) {
        long m = Math.max(1, (ms + 59_999) / 60_000), h = m / 60;
        return h > 0 ? h + "h " + (m % 60) + "m" : m + "m";
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
        List<String> sites = plugin.getConfig().getStringList("vote-ranks.sites");
        Player p = s instanceof Player pl ? pl : null;
        long now = System.currentTimeMillis(), cool = (long) (plugin.getConfig().getDouble("vote-ranks.cooldown-hours", 24) * 3_600_000L);
        int done = 0;
        List<Component> rows = new ArrayList<>();
        for (String site : sites) {
            String[] w = site.split("\\|", 2);
            String name = w[0].trim(), url = w.length > 1 ? w[1].trim() : w[0].trim();
            long last = p == null ? 0 : plugin.voteShop().lastVote(p.getUniqueId(), name);
            long left = last == 0 ? 0 : last + cool - now;
            if (left > 0) {   // done today: a red bar, and when it opens again
                done++;
                rows.add(Component.text(" ██████ ", TextColor.color(0xD83A3A)).append(Component.text(name, TextColor.color(0xFF8A8A)))
                        .append(Component.text("  ✔ voted · again in " + hm(left), NamedTextColor.GRAY))
                        .clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Component.text("Already voted here today. Open the page anyway", NamedTextColor.GRAY))));
            } else {          // open: a green bar to click
                rows.add(Component.text(" ██████ ", TextColor.color(0x3FCF5A)).append(Component.text(name, TextColor.color(0x8DF59D)))
                        .append(Component.text("  » click to vote", NamedTextColor.GREEN))
                        .clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Component.text("Click to vote on " + name, NamedTextColor.GRAY))));
            }
        }
        Component head = Component.text("Vote for BaffoonMC", GOLD);
        if (!sites.isEmpty() && p != null) {
            Component prog = Component.text("  ");
            for (int i = 0; i < sites.size(); i++) prog = prog.append(Component.text("■", i < done ? TextColor.color(0xD83A3A) : TextColor.color(0x3FCF5A)));
            head = head.append(prog).append(Component.text("  " + done + "/" + sites.size() + " done today", NamedTextColor.GRAY));
        }
        s.sendMessage(Component.empty());
        s.sendMessage(head);
        if (sites.isEmpty()) s.sendMessage(Component.text(" Voting links are coming soon.", NamedTextColor.GRAY));
        rows.forEach(s::sendMessage);
        if (p == null) return true;
        int v = plugin.voteShop().votes(p.getUniqueId());
        Step now2 = null, next = null;
        for (Step st : ladder()) { if (v >= st.votes()) now2 = st; else if (next == null) next = st; }
        Component line = Component.text("You've voted " + v + " time" + (v == 1 ? "" : "s"), NamedTextColor.GRAY);
        if (now2 != null) line = line.append(Component.text(" and you're ", NamedTextColor.GRAY)).append(Component.text(nice(now2.group()), NamedTextColor.WHITE));
        if (next != null) line = line.append(Component.text(". " + (next.votes() - v) + " more to ", NamedTextColor.GRAY)).append(Component.text(nice(next.group()), GOLD));
        s.sendMessage(line.append(Component.text(".", NamedTextColor.GRAY)));
        s.sendMessage(Component.text("Points to spend: " + plugin.voteShop().points(p.getUniqueId()) + "  ", NamedTextColor.GRAY)
                .append(Component.text("/voteshop", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/voteshop"))));
        return true;
    }
}
