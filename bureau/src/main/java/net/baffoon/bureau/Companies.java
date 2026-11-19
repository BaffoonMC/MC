package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Simple companies: a team with a name, a boss, up to five members and a shared vault of Cold Hard Cash.
 * Members can have their contract pay go to the company (with a bonus). Every week the company with the fullest vault
 * is Company of the Week: a headline and a bonus from the Bureau.
 *
 *   /company create <name>   (costs create-price Cold Hard Cash)      /company invite <player> · join <name> · leave
 *   /company kick <player> · disband (boss)                          /company deposit <n> · withdraw <n> (boss)
 *   /company info [name] · top
 */
final class Companies {
    private static final TextColor GOLD = TextColor.color(0xF0C85A), RED = TextColor.color(0xE05050), GREEN = TextColor.color(0x7BD36A), ICE = Cash.ICE;

    static final class Company {
        String name;
        UUID boss;
        final Set<UUID> members = new LinkedHashSet<>();
        final Set<UUID> invited = new HashSet<>();
        int vault;
        int wins;
    }

    private final Bureau plugin;
    private final Map<String, Company> byName = new HashMap<>();   // lower-case name -> company
    private long week = -1;

    Companies(Bureau plugin) { this.plugin = plugin; }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("companies." + k, d); }

    Company get(String name) { return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT)); }

    /** The company this player is in, by its proper name, or null. */
    String of(UUID id) {
        for (Company c : byName.values()) if (c.members.contains(id)) return c.name;
        return null;
    }

    private Company mine(UUID id) { String n = of(id); return n == null ? null : get(n); }

    void addVault(String name, int n) { Company c = get(name); if (c != null) { c.vault = Math.max(0, c.vault + n); plugin.dirty(); } }

    private static Component ok(String s) { return Component.text(s, GREEN); }
    private static Component bad(String s) { return Component.text(s, RED); }
    private static String nameOf(UUID id) { OfflinePlayer o = Bukkit.getOfflinePlayer(id); return o.getName() == null ? "?" : o.getName(); }

    /** Pay n Cold Hard Cash from pockets then vault; false (nothing taken) if they can't. */
    private boolean payCash(Player p, int n) {
        int pockets = Cash.count(p), held = plugin.vaults().held(p.getUniqueId());
        if (pockets + held < n) return false;
        int fromPockets = Math.min(pockets, n);
        Cash.take(p, fromPockets);
        if (n > fromPockets) plugin.vaults().add(p.getUniqueId(), -(n - fromPockets));
        return true;
    }

    void command(Player p, String[] a) {
        String sub = a.length == 0 ? "info" : a[0].toLowerCase(Locale.ROOT);
        Company c = mine(p.getUniqueId());
        switch (sub) {
            case "create" -> {
                if (c != null) { p.sendMessage(bad("You're already in " + c.name + ". Leave it first.")); return; }
                if (a.length < 2) { p.sendMessage(bad("Usage: /company create <name>")); return; }
                String name = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)).trim();
                if (!name.matches("[A-Za-z0-9 &'.-]{3,24}")) { p.sendMessage(bad("Names are 3 to 24 letters, numbers, spaces and & ' . -")); return; }
                if (get(name) != null) { p.sendMessage(bad("There's already a company called that.")); return; }
                int price = (int) cfg("create-price", 100);
                if (!payCash(p, price)) { p.sendMessage(bad("Founding a company costs " + price + " Cold Hard Cash.")); return; }
                Company n = new Company();
                n.name = name; n.boss = p.getUniqueId(); n.members.add(p.getUniqueId());
                byName.put(name.toLowerCase(Locale.ROOT), n);
                plugin.dirty();
                Bukkit.broadcast(Component.text("✦ ", GOLD).append(Component.text(p.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" has founded ", NamedTextColor.GRAY)).append(Component.text(name, GOLD, TextDecoration.BOLD))
                        .append(Component.text(". The Bureau wishes them profitable fortunes.", NamedTextColor.GRAY)));
            }
            case "invite" -> {
                if (c == null || !c.boss.equals(p.getUniqueId())) { p.sendMessage(bad("Only a company's boss can invite.")); return; }
                if (a.length < 2) { p.sendMessage(bad("Usage: /company invite <player>")); return; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { p.sendMessage(bad("Nobody online called " + a[1] + ".")); return; }
                if (c.members.size() >= (int) cfg("max-members", 5)) { p.sendMessage(bad("Your company is full (" + (int) cfg("max-members", 5) + ").")); return; }
                if (of(t.getUniqueId()) != null) { p.sendMessage(bad(t.getName() + " is already in a company.")); return; }
                c.invited.add(t.getUniqueId());
                p.sendMessage(ok("Invited " + t.getName() + "."));
                t.sendMessage(Component.text(p.getName() + " invites you to join ", NamedTextColor.GRAY).append(Component.text(c.name, GOLD))
                        .append(Component.text(". ", NamedTextColor.GRAY)).append(Component.text("[Join]", GREEN, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/company join " + c.name))));
            }
            case "join" -> {
                if (c != null) { p.sendMessage(bad("You're already in " + c.name + ".")); return; }
                if (a.length < 2) { p.sendMessage(bad("Usage: /company join <name>")); return; }
                Company t = get(String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
                if (t == null || !t.invited.contains(p.getUniqueId())) { p.sendMessage(bad("You need an invite from that company's boss.")); return; }
                if (t.members.size() >= (int) cfg("max-members", 5)) { p.sendMessage(bad(t.name + " is full.")); return; }
                t.invited.remove(p.getUniqueId());
                t.members.add(p.getUniqueId());
                plugin.dirty();
                tell(t, p.getName() + " has joined " + t.name + ".");
            }
            case "leave" -> {
                if (c == null) { p.sendMessage(bad("You're not in a company.")); return; }
                if (c.boss.equals(p.getUniqueId())) { p.sendMessage(bad("The boss can't leave. /company disband, or hand it on with /company boss <member>.")); return; }
                c.members.remove(p.getUniqueId());
                plugin.dirty();
                p.sendMessage(ok("You've left " + c.name + "."));
                tell(c, p.getName() + " has left the company.");
            }
            case "kick" -> {
                if (c == null || !c.boss.equals(p.getUniqueId())) { p.sendMessage(bad("Only the boss can do that.")); return; }
                if (a.length < 2) { p.sendMessage(bad("Usage: /company kick <player>")); return; }
                UUID t = null;
                for (UUID m : c.members) if (nameOf(m).equalsIgnoreCase(a[1])) t = m;
                if (t == null || t.equals(p.getUniqueId())) { p.sendMessage(bad("No member called " + a[1] + ".")); return; }
                c.members.remove(t);
                plugin.dirty();
                tell(c, nameOf(t) + " has been let go.");
                Player tp = Bukkit.getPlayer(t);
                if (tp != null) tp.sendMessage(bad("You've been let go from " + c.name + ". Cold-blooded. Nothing personal."));
            }
            case "boss" -> {
                if (c == null || !c.boss.equals(p.getUniqueId())) { p.sendMessage(bad("Only the boss can do that.")); return; }
                if (a.length < 2) { p.sendMessage(bad("Usage: /company boss <member>")); return; }
                UUID t = null;
                for (UUID m : c.members) if (nameOf(m).equalsIgnoreCase(a[1])) t = m;
                if (t == null) { p.sendMessage(bad("No member called " + a[1] + ".")); return; }
                c.boss = t;
                plugin.dirty();
                tell(c, nameOf(t) + " is the new boss of " + c.name + ".");
            }
            case "deposit" -> {
                if (c == null) { p.sendMessage(bad("You're not in a company.")); return; }
                int n = a.length > 1 ? num(a[1]) : Cash.count(p);
                if (n <= 0 || Cash.count(p) < n) { p.sendMessage(bad("You're not carrying that much Cold Hard Cash.")); return; }
                Cash.take(p, n);
                c.vault += n;
                plugin.dirty();
                tell(c, p.getName() + " paid " + n + " Cold Hard Cash into the company vault (" + c.vault + ").");
            }
            case "withdraw" -> {
                if (c == null || !c.boss.equals(p.getUniqueId())) { p.sendMessage(bad("Only the boss can take money out.")); return; }
                int n = a.length > 1 ? num(a[1]) : -1;
                if (n <= 0 || n > c.vault) { p.sendMessage(bad("The vault holds " + c.vault + ".")); return; }
                c.vault -= n;
                Cash.give(p, n);
                plugin.dirty();
                tell(c, p.getName() + " took " + n + " Cold Hard Cash from the company vault (" + c.vault + " left).");
            }
            case "disband" -> {
                if (c == null || !c.boss.equals(p.getUniqueId())) { p.sendMessage(bad("Only the boss can disband it.")); return; }
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) { p.sendMessage(bad("This closes " + c.name + " for good; the vault (" + c.vault + ") goes to you. /company disband confirm")); return; }
                if (c.vault > 0) Cash.give(p, c.vault);
                tell(c, c.name + " has been wound up.");
                byName.remove(c.name.toLowerCase(Locale.ROOT));
                plugin.dirty();
            }
            case "top" -> {
                List<Company> all = new ArrayList<>(byName.values());
                all.sort((x, y) -> Integer.compare(y.vault, x.vault));
                p.sendMessage(Component.text("The richest companies (this week's winner gets a bonus):", GOLD));
                if (all.isEmpty()) p.sendMessage(Component.text(" none yet. /company create <name>", NamedTextColor.GRAY));
                for (int i = 0; i < Math.min(10, all.size()); i++) {
                    Company x = all.get(i);
                    p.sendMessage(Component.text(" " + (i + 1) + ". ", NamedTextColor.GRAY).append(Component.text(x.name, i == 0 ? GOLD : NamedTextColor.WHITE))
                            .append(Component.text("  " + x.vault + " Cold Hard Cash · " + x.members.size() + " member" + (x.members.size() == 1 ? "" : "s")
                                    + (x.wins > 0 ? " · " + x.wins + "× Company of the Week" : ""), NamedTextColor.GRAY)));
                }
            }
            default -> {   // info [name]
                Company x = a.length > 1 ? get(String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length))) : c;
                if (x == null) {
                    p.sendMessage(Component.text("Companies: a team with a shared vault. Contracts done for it pay " + Math.round(plugin.getConfig().getDouble("contracts.company-bonus", 0.10) * 100) + "% more.", GOLD));
                    for (String l : List.of("/company create <name>  found one (" + (int) cfg("create-price", 100) + " Cold Hard Cash)",
                            "/company invite <player>  /company join <name>  /company leave",
                            "/company deposit [n]  /company withdraw <n> (boss)",
                            "/company kick <player>  /company boss <member>  /company disband",
                            "/company info [name]  /company top"))
                        p.sendMessage(Component.text(" " + l, NamedTextColor.GRAY));
                    return;
                }
                p.sendMessage(Component.text(x.name, GOLD, TextDecoration.BOLD).append(Component.text("  vault: " + x.vault + " Cold Hard Cash", ICE)));
                p.sendMessage(Component.text(" Boss: " + nameOf(x.boss), NamedTextColor.GRAY));
                List<String> names = new ArrayList<>();
                for (UUID m : x.members) names.add(nameOf(m) + " (" + plugin.contracts().rankName(m) + ")");
                p.sendMessage(Component.text(" Members: " + String.join(", ", names), NamedTextColor.GRAY));
                if (x.wins > 0) p.sendMessage(Component.text(" Company of the Week " + x.wins + " time" + (x.wins == 1 ? "" : "s"), GOLD));
            }
        }
    }

    private void tell(Company c, String msg) {
        for (UUID m : c.members) { Player p = Bukkit.getPlayer(m); if (p != null) p.sendMessage(Component.text("[" + c.name + "] ", GOLD).append(Component.text(msg, NamedTextColor.GRAY))); }
    }

    private static int num(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; } }

    /** Every minute: when the week turns over, crown the Company of the Week. */
    void minute() {
        ZoneId z;
        try { z = ZoneId.of(plugin.getConfig().getString("exchange.timezone", "Europe/London")); } catch (Exception e) { z = ZoneId.of("Europe/London"); }
        LocalDate d = LocalDate.now(z);
        long w = d.get(IsoFields.WEEK_BASED_YEAR) * 100L + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        if (week < 0) { week = w; plugin.dirty(); return; }
        if (w == week) return;
        week = w;
        plugin.dirty();
        Company best = null;
        for (Company c : byName.values()) if (c.vault > 0 && (best == null || c.vault > best.vault)) best = c;
        if (best == null) return;
        int bonus = (int) cfg("weekly-bonus", 10);
        best.vault += bonus;
        best.wins++;
        Bukkit.broadcast(Component.text("✦ COMPANY OF THE WEEK: ", GOLD, TextDecoration.BOLD).append(Component.text(best.name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" with " + (best.vault - bonus) + " Cold Hard Cash in the vault. The Bureau adds " + bonus + ".", NamedTextColor.GRAY)));
    }

    // ---------------------------------------------------------------- saving

    void load(ConfigurationSection s) {
        byName.clear();
        if (s == null) return;
        week = s.getLong("week", -1);
        ConfigurationSection list = s.getConfigurationSection("list");
        if (list == null) return;
        for (String k : list.getKeys(false)) {
            ConfigurationSection c = list.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Company x = new Company();
                x.name = c.getString("name", k);
                x.boss = UUID.fromString(c.getString("boss", ""));
                for (String m : c.getStringList("members")) x.members.add(UUID.fromString(m));
                x.vault = c.getInt("vault");
                x.wins = c.getInt("wins");
                byName.put(x.name.toLowerCase(Locale.ROOT), x);
            } catch (RuntimeException ignored) {}
        }
    }

    void save(ConfigurationSection s) {
        s.set("week", week);
        ConfigurationSection list = s.createSection("list");
        int i = 0;
        for (Company x : byName.values()) {
            ConfigurationSection c = list.createSection("c" + (i++));
            c.set("name", x.name);
            c.set("boss", x.boss.toString());
            List<String> m = new ArrayList<>(); for (UUID u : x.members) m.add(u.toString());
            c.set("members", m);
            c.set("vault", x.vault);
            c.set("wins", x.wins);
        }
    }

    List<String> names() { List<String> n = new ArrayList<>(); for (Company c : byName.values()) n.add(c.name); return n; }
}
