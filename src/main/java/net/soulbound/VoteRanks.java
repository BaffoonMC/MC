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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vote ranks: every vote counts towards your level (Newcomer up to Ancient), and you're moved up the LuckPerms track
 * the moment you reach the number. Lifetime votes are kept by the Vote Shop and never go down when you spend points.
 * /vote shows the voting sites, your count and how far it is to the next level.
 */
final class VoteRanks implements CommandExecutor, org.bukkit.event.Listener {
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

    private record Site(String name, String url) {}

    private List<Site> sites() {
        List<Site> l = new ArrayList<>();
        for (String site : plugin.getConfig().getStringList("vote-ranks.sites")) {
            String[] w = site.split("\\|", 2);
            l.add(new Site(w[0].trim(), w.length > 1 ? w[1].trim() : w[0].trim()));
        }
        return l;
    }

    /** Milliseconds until they can vote on this site again (0 = they can now). */
    private long wait(Player p, Site s) {
        long last = plugin.voteShop().lastVote(p.getUniqueId(), s.name());
        long cool = (long) (plugin.getConfig().getDouble("vote-ranks.cooldown-hours", 24) * 3_600_000L);
        return last == 0 ? 0 : Math.max(0, last + cool - System.currentTimeMillis());
    }

    private static final class Menu implements InventoryHolder {
        Inventory inv;
        final java.util.Map<Integer, Site> slots = new java.util.HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private static Component plain(String t, TextColor c) { return Component.text(t, c).decoration(TextDecoration.ITALIC, false); }

    private static ItemStack icon(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        it.setItemMeta(meta);
        return MenuIcons.quiet(it);
    }

    /** The vote menu: a chest with a block per site, green to vote on, red once done for the day. */
    void open(Player p) {
        List<Site> list = sites();
        Menu menu = new Menu();
        int done = 0;
        for (Site st : list) if (wait(p, st) > 0) done++;
        Inventory inv = Bukkit.createInventory(menu, 27, Component.text("Vote for BaffoonMC  ·  " + done + "/" + list.size() + " today", NamedTextColor.DARK_GRAY));
        menu.inv = inv;
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        double cash = plugin.getConfig().getDouble("voteshop.money-per-vote", 100);
        int per = plugin.getConfig().getInt("voteshop.points-per-vote", 1);
        Component prog = Component.empty();
        for (int i = 0; i < list.size(); i++) prog = prog.append(Component.text("■ ", i < done ? TextColor.color(0xD83A3A) : TextColor.color(0x3FCF5A)));
        List<Component> head = new ArrayList<>();
        head.add(prog.decoration(TextDecoration.ITALIC, false));
        head.add(plain(done + " of " + list.size() + " sites done today", NamedTextColor.GRAY));
        head.add(Component.empty());
        head.add(plain("Every vote:", NamedTextColor.GRAY));
        if (per > 0) head.add(plain(" +" + per + " point" + (per == 1 ? "" : "s") + " for the Vote Shop", NamedTextColor.GREEN));
        if (cash > 0) head.add(plain(" +" + Money.fmt(cash), NamedTextColor.GREEN));
        head.add(plain(" one step closer to your next rank", NamedTextColor.GREEN));
        inv.setItem(4, icon(Material.SUNFLOWER, Component.text("Vote for BaffoonMC", GOLD), head));

        int start = 13 - list.size() / 2;   // centre the sites on the middle row
        int n = 0;
        for (Site st : list) {
            int slot = Math.min(17, start + n++);
            long left = wait(p, st);
            ItemStack it = left > 0
                    ? icon(Material.RED_CONCRETE, Component.text(st.name(), TextColor.color(0xFF6B6B)), List.of(
                            plain("✔ Voted today", NamedTextColor.RED),
                            plain("Vote again in " + hm(left), NamedTextColor.GRAY),
                            Component.empty(),
                            plain("Click for the link anyway", NamedTextColor.DARK_GRAY)))
                    : icon(Material.LIME_CONCRETE, Component.text(st.name(), TextColor.color(0x5CE27A)), List.of(
                            plain("Not voted yet today", NamedTextColor.GRAY),
                            Component.empty(),
                            plain("» Click to vote", NamedTextColor.GREEN)));
            inv.setItem(slot, it);
            menu.slots.put(slot, st);
        }

        int v = plugin.voteShop().votes(p.getUniqueId());
        Step now = null, next = null;
        for (Step st : ladder()) { if (v >= st.votes()) now = st; else if (next == null) next = st; }
        List<Component> rank = new ArrayList<>();
        rank.add(plain(v + " vote" + (v == 1 ? "" : "s") + " so far", NamedTextColor.GRAY));
        if (next != null) {
            int from = now == null ? 0 : now.votes(), span = Math.max(1, next.votes() - from), got = Math.max(0, v - from);
            Component bar = Component.empty();
            for (int i = 0; i < 10; i++) bar = bar.append(Component.text("|", i < got * 10 / span ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            rank.add(bar.decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            rank.add(plain((next.votes() - v) + " more to " + nice(next.group()), NamedTextColor.YELLOW));
        } else rank.add(plain("You've reached the top!", NamedTextColor.YELLOW));
        inv.setItem(21, icon(Material.EXPERIENCE_BOTTLE, Component.text("Your rank: " + (now == null ? "Newcomer" : nice(now.group())), NamedTextColor.WHITE), rank));
        int pts = plugin.voteShop().points(p.getUniqueId());
        inv.setItem(23, icon(Material.EMERALD, Component.text("Vote Shop", NamedTextColor.GREEN), List.of(
                plain("You have " + pts + " point" + (pts == 1 ? "" : "s"), NamedTextColor.GRAY), Component.empty(), plain("» Click to open", NamedTextColor.GREEN))));
        p.openInventory(inv);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) { if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        if (e.getRawSlot() == 23) { p.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> p.performCommand("voteshop")); return; }
        Site st = m.slots.get(e.getRawSlot());
        if (st == null) return;
        p.closeInventory();
        // a menu can't open a web page, so hand them the link in chat, one click away
        p.sendMessage(Component.text("» ", NamedTextColor.GREEN).append(Component.text("Click here to vote on " + st.name(), NamedTextColor.GREEN, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(st.url())).hoverEvent(HoverEvent.showText(Component.text(st.url(), NamedTextColor.GRAY))))
                .append(Component.text("  then /vote for the next one", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (s instanceof Player p) { open(p); return true; }
        s.sendMessage(Component.text("Vote for BaffoonMC:", GOLD));
        for (Site st : sites()) s.sendMessage(Component.text(" " + st.name() + ": " + st.url(), NamedTextColor.GRAY));
        return true;
    }
}
