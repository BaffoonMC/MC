package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Bureau of Cold-Blooded Affairs: lizards in suits who run BaffoonMC's money.
 *
 * Phase one: Cold Hard Cash (a real banknote item), the Exchange (a price that moves, buy and sell for server money),
 * the vault (Cash kept safe from thieves), the lizard shop, the lizards themselves, and the lift down from the bank.
 * Contracts, companies, shares and the Taxman come later.
 */
public final class Bureau extends JavaPlugin implements TabExecutor {
    private static final TextColor GOLD = TextColor.color(0xF0C85A);
    private Exchange exchange;
    private Vaults vaults;
    private Capital capital;
    private Lizards lizards;
    private Lift lift;
    private Taxes taxes;
    private Taxman taxman;
    private Security security;
    private BureauItems items;
    private Contracts contracts;
    private Companies companies;
    private Ticker ticker;
    private Chart chart;
    /** Citizens is installed and switched on: walking lizards are Citizens NPCs. */
    boolean citizens() {
        return getConfig().getBoolean("walkers.use-citizens", true) && getServer().getPluginManager().isPluginEnabled("Citizens");
    }
    boolean spawning;   // the Bureau is placing its own walkers (spawn protection shouldn't stop them)
    private final Map<UUID, Integer> boughtToday = new HashMap<>();
    private final Map<UUID, Deque<String>> ledger = new HashMap<>();
    private boolean dirty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Cash.key = new org.bukkit.NamespacedKey(this, "cold_hard_cash");
        exchange = new Exchange(this);
        vaults = new Vaults(this);
        capital = new Capital(this);
        lizards = new Lizards(this);
        lift = new Lift(this);
        taxes = new Taxes(this);
        taxman = new Taxman(this);
        security = new Security(this);
        items = new BureauItems(this);
        contracts = new Contracts(this);
        companies = new Companies(this);
        ticker = new Ticker(this);
        chart = new Chart(this);
        Walker.key = new org.bukkit.NamespacedKey(this, "walker");
        load();
        var pm = getServer().getPluginManager();
        pm.registerEvents(capital, this);
        pm.registerEvents(lizards, this);
        pm.registerEvents(lift, this);
        pm.registerEvents(taxes, this);
        pm.registerEvents(taxman, this);
        pm.registerEvents(security, this);
        pm.registerEvents(items, this);
        pm.registerEvents(contracts, this);
        for (String c : List.of("bureau", "cash", "tax", "contracts", "company")) {
            var cmd = getCommand(c);
            if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        }
        Bukkit.getScheduler().runTaskTimer(this, exchange::halfHour, 20L * 60 * 30, 20L * 60 * 30);
        Bukkit.getScheduler().runTaskTimer(this, exchange::minute, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, lizards::watch, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, taxes::tick, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, taxman::second, 100L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, taxman::tick, 100L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, security::tick, 100L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, items::tick, 100L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, ticker::tick, 100L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, chart::second, 100L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { contracts.minute(); companies.minute(); }, 20L * 30, 20L * 60);
        // walkers aren't saved, but a crash could leave some behind: clear any strays
        for (var w : Bukkit.getWorlds()) for (var e : w.getEntities()) if (Walker.isPart(e)) e.remove();
        Bukkit.getScheduler().runTaskTimer(this, () -> { if (dirty) save(); }, 20L * 30, 20L * 30);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            try { new BureauPlaceholders(this).register(); getLogger().info("PlaceholderAPI found: %bureau_...% placeholders registered."); }
            catch (Throwable t) { getLogger().warning("Couldn't register placeholders: " + t); }
        }
        if (!Money.available()) getLogger().warning("Essentials' money isn't available, so the Exchange can't buy or sell.");
    }

    @Override
    public void onDisable() {
        if (taxman != null) taxman.endAll();
        if (security != null) security.endAll();
        if (items != null) items.endAll();
        if (ticker != null) ticker.endAll();
        if (chart != null) chart.endAll();
        save();
    }

    Exchange exchange() { return exchange; }
    Vaults vaults() { return vaults; }
    Capital capital() { return capital; }
    Lizards lizards() { return lizards; }
    Lift lift() { return lift; }
    Taxes taxes() { return taxes; }
    Taxman taxman() { return taxman; }
    Security security() { return security; }
    BureauItems items() { return items; }
    Contracts contracts() { return contracts; }
    Companies companies() { return companies; }

    void dirty() { dirty = true; }

    // ---------------------------------------------------------------- daily buy limit and the ledger

    int buyLeft(UUID id) { return Math.max(0, getConfig().getInt("exchange.buy-limit-per-day", 64) - boughtToday.getOrDefault(id, 0)); }
    void bought(UUID id, int n) { boughtToday.merge(id, n, Integer::sum); dirty(); }
    void newDay() { boughtToday.clear(); dirty(); }

    void log(UUID id, String line) {
        Deque<String> d = ledger.computeIfAbsent(id, k -> new ArrayDeque<>());
        d.addFirst(line);
        while (d.size() > 6) d.removeLast();
        dirty();
    }

    List<String> ledger(UUID id) { return new ArrayList<>(ledger.getOrDefault(id, new ArrayDeque<>())); }

    // ---------------------------------------------------------------- data.yml

    private File dataFile() { return new File(getDataFolder(), "data.yml"); }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile());
        exchange.load(sec(y, "exchange"));
        vaults.load(y.getConfigurationSection("vaults"));
        lift.load(y.getConfigurationSection("lift"));
        taxes.load(y.getConfigurationSection("taxes"));
        security.load(y.getConfigurationSection("fired"));
        items.load(y.getConfigurationSection("items"));
        contracts.load(y.getConfigurationSection("contracts"));
        companies.load(y.getConfigurationSection("companies"));
        ticker.load(y.getStringList("tickers"));
        chart.load(y.getConfigurationSection("chart"));
        boughtToday.clear();
        if (y.getLong("bought-day", -1) == exchange.today()) {
            ConfigurationSection b = y.getConfigurationSection("bought");
            if (b != null) for (String k : b.getKeys(false)) try { boughtToday.put(UUID.fromString(k), b.getInt(k)); } catch (IllegalArgumentException ignored) {}
        }
        ledger.clear();
        ConfigurationSection l = y.getConfigurationSection("ledger");
        if (l != null) for (String k : l.getKeys(false)) try { ledger.put(UUID.fromString(k), new ArrayDeque<>(l.getStringList(k))); } catch (IllegalArgumentException ignored) {}
    }

    private static ConfigurationSection sec(YamlConfiguration y, String k) {
        ConfigurationSection s = y.getConfigurationSection(k);
        return s != null ? s : y.createSection(k);
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        exchange.save(y.createSection("exchange"));
        vaults.save(y.createSection("vaults"));
        lift.save(y.createSection("lift"));
        taxes.save(y.createSection("taxes"));
        security.save(y.createSection("fired"));
        items.save(y.createSection("items"));
        contracts.save(y.createSection("contracts"));
        companies.save(y.createSection("companies"));
        y.set("tickers", ticker.save());
        chart.save(y.createSection("chart"));
        y.set("bought-day", exchange.today());
        ConfigurationSection b = y.createSection("bought");
        for (var e : boughtToday.entrySet()) b.set(e.getKey().toString(), e.getValue());
        ConfigurationSection l = y.createSection("ledger");
        for (var e : ledger.entrySet()) l.set(e.getKey().toString(), new ArrayList<>(e.getValue()));
        try { getDataFolder().mkdirs(); y.save(dataFile()); dirty = false; }
        catch (IOException ex) { getLogger().warning("Couldn't save data.yml: " + ex.getMessage()); }
    }

    // ---------------------------------------------------------------- commands

    private static Component ok(String s) { return Component.text(s, NamedTextColor.GREEN); }
    private static Component bad(String s) { return Component.text(s, NamedTextColor.RED); }

    private boolean admin(CommandSender s) { return s.hasPermission("bureau.admin"); }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (cmd.getName().equalsIgnoreCase("cash")) { cash(s); return true; }
        if (cmd.getName().equalsIgnoreCase("contracts")) {
            if (s instanceof Player p) contracts.open(p); else s.sendMessage("Players only.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("company")) {
            if (s instanceof Player p) companies.command(p, a); else s.sendMessage("Players only.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("tax")) {
            if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
            if (a.length > 0 && a[0].equalsIgnoreCase("pay")) taxes.pay(p); else taxes.status(p);
            return true;
        }
        if (a.length == 0) {
            s.sendMessage(Component.text("The Bureau of Cold-Blooded Affairs", GOLD));
            s.sendMessage(Component.text(" Cold Hard Cash is " + Money.fmt(exchange.price) + " today ("
                    + String.format(Locale.ROOT, "%+.1f%%", exchange.change() * 100) + "). Find Mr. Coldwell at the Exchange to trade.", NamedTextColor.GRAY));
            s.sendMessage(Component.text(" The lift is in the bank at spawn: crouch on it to go down.", NamedTextColor.GRAY));
            if (admin(s)) s.sendMessage(Component.text(" Admin: /bureau help", NamedTextColor.DARK_GRAY));
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("party")) {
            if (s instanceof Player p) items.goParty(p);
            return true;
        }
        if (sub.equals("hire")) {
            if (s instanceof Player p) s.sendMessage(Component.text(security.hire(p), NamedTextColor.GRAY));
            return true;
        }
        if (!admin(s) && !sub.equals("exchange")) { s.sendMessage(bad("Only the Director can do that.")); return true; }
        switch (sub) {
            case "exchange" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); break; }
                if (!admin(s) && !getConfig().getBoolean("exchange.anywhere", false)) { s.sendMessage(bad("Find Mr. Coldwell, the teller, at the Bureau.")); break; }
                Player t = a.length > 1 && admin(s) ? Bukkit.getPlayerExact(a[1]) : p;
                if (t == null) { s.sendMessage(bad("Nobody online called " + a[1] + ".")); break; }
                capital.open(t);
            }
            case "give" -> {
                if (a.length < 3) { s.sendMessage(bad("Usage: /bureau give <player> <amount>")); break; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(bad("Nobody online called " + a[1] + ".")); break; }
                int n = num(a[2]);
                if (n <= 0) { s.sendMessage(bad("Give a number above 0.")); break; }
                Cash.give(t, n);
                s.sendMessage(ok("Gave " + t.getName() + " " + n + " Cold Hard Cash."));
            }
            case "vault" -> {
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau vault <player> [set <n>]")); break; }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) { s.sendMessage(bad("Never seen " + a[1] + " here.")); break; }
                if (a.length >= 4 && a[2].equalsIgnoreCase("set")) vaults.set(t.getUniqueId(), Math.max(0, num(a[3])));
                s.sendMessage(ok(t.getName() + "'s vault holds " + vaults.held(t.getUniqueId()) + " Cold Hard Cash."));
            }
            case "price" -> {
                if (a.length >= 3 && a[1].equalsIgnoreCase("set")) {
                    try { exchange.price = Exchange.round(Double.parseDouble(a[2])); exchange.dayOpen = exchange.price; exchange.history.add(exchange.price); dirty(); }
                    catch (NumberFormatException e) { s.sendMessage(bad("That's not a price.")); break; }
                }
                s.sendMessage(ok("Cold Hard Cash: " + Money.fmt(exchange.price) + " (buy " + Money.fmt(exchange.buyPrice()) + ", sell "
                        + Money.fmt(exchange.sellPrice()) + "), today " + String.format(Locale.ROOT, "%+.1f%%", exchange.change() * 100)
                        + (exchange.event.isEmpty() ? "" : ", " + exchange.event)));
            }
            case "event" -> {
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau event <boom|crash|calm>")); break; }
                switch (a[1].toLowerCase(Locale.ROOT)) {
                    case "boom", "rally" -> exchange.event("RALLY", "The Director is in a buying mood. Cash is climbing.");
                    case "crash" -> exchange.event("CRASH", "Panic on the Exchange floor. Cash is falling.");
                    case "calm" -> { exchange.event("CALM", ""); s.sendMessage(ok("The market calms down.")); }
                    default -> s.sendMessage(bad("boom, crash or calm."));
                }
            }
            case "npc" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); break; }
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau npc <teller|receptionist|director|security|clerk|taxman|votes> [name] | remove | reskin")); break; }
                if (a[1].equalsIgnoreCase("remove")) { s.sendMessage(ok(lizards.removeNear(p))); break; }
                if (a[1].equalsIgnoreCase("reskin")) { s.sendMessage(ok(lizards.reskinAll())); break; }
                Lizards.Role r = Lizards.Role.of(a[1]);
                if (r == null) { s.sendMessage(bad("Roles: teller, receptionist, director, security, clerk, taxman, votes.")); break; }
                String name = a.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : null;
                s.sendMessage(ok(lizards.spawn(p, r, name)));
            }
            case "ticker" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); break; }
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau ticker place [width] (look at the wall) | remove   (" + ticker.count() + " placed)")); break; }
                if (a[1].equalsIgnoreCase("remove")) { s.sendMessage(ok(ticker.removeNear(p))); break; }
                int width = 9;
                if (a.length > 2) try { width = Integer.parseInt(a[2]); } catch (NumberFormatException ignored) {}
                s.sendMessage(ok(ticker.place(p, width)));
            }
            case "chart" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); break; }
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau chart place [width] [height] (look at the wall) | remove   (" + chart.count() + " placed)")); break; }
                if (a[1].equalsIgnoreCase("remove")) { s.sendMessage(ok(chart.removeNear(p))); break; }
                int width = 11, height = 5;
                if (a.length > 2) try { width = Integer.parseInt(a[2]); } catch (NumberFormatException ignored) {}
                if (a.length > 3) try { height = Integer.parseInt(a[3]); } catch (NumberFormatException ignored) {}
                s.sendMessage(ok(chart.place(p, width, height)));
            }
            case "lift" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); break; }
                if (a.length < 3 || !a[1].equalsIgnoreCase("set") || !(a[2].equalsIgnoreCase("top") || a[2].equalsIgnoreCase("bottom"))) {
                    s.sendMessage(bad("Usage: /bureau lift set <top|bottom>   (stand on the spot first)")); break;
                }
                if (a[2].equalsIgnoreCase("top")) lift.top = p.getLocation(); else lift.bottom = p.getLocation();
                dirty();
                s.sendMessage(ok("Lift " + a[2].toLowerCase(Locale.ROOT) + " set here. Players crouch on it to ride."));
            }
            case "tax" -> {
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau tax <player> [set <amount> | assign | clear | hunt]")); break; }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) { s.sendMessage(bad("Never seen " + a[1] + " here.")); break; }
                UUID id = t.getUniqueId();
                String op = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                switch (op) {
                    case "set" -> {
                        try { taxes.set(id, Double.parseDouble(a[3])); } catch (Exception e) { s.sendMessage(bad("/bureau tax <player> set <amount>")); return true; }
                    }
                    case "assign" -> taxes.assign(id);
                    case "clear" -> taxes.clear(id);
                    case "hunt" -> {
                        Player online = t.getPlayer();
                        Taxes.Case c = taxes.peek(id);
                        if (online == null || c == null || c.owed <= 0) { s.sendMessage(bad("They need to be online and owing something.")); return true; }
                        taxes.assign(id);
                        taxman.start(online, c);
                    }
                    default -> { }
                }
                Taxes.Case c = taxes.peek(id);
                s.sendMessage(ok(t.getName() + ": owes " + Money.fmt(c == null ? 0 : c.owed) + (c == null ? "" : " (" + c.stage.name().toLowerCase(Locale.ROOT) + ")")
                        + (taxman.hunting(id) ? ", the Taxman is after them" : "")));
            }
            case "fire" -> {
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau fire <player> [reason]")); break; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(bad("Nobody online called " + a[1] + ".")); break; }
                security.fire(t, a.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : null);
                s.sendMessage(ok(t.getName() + " has been fired."));
            }
            case "unfire" -> {
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau unfire <player>")); break; }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) { s.sendMessage(bad("Never seen " + a[1] + " here.")); break; }
                security.unfire(t.getUniqueId());
                s.sendMessage(ok(t.getName() + " is welcome back at the Bureau."));
            }
            case "item" -> {
                if (a.length < 3) { s.sendMessage(bad("Usage: /bureau item <player> <" + String.join("|", BureauItems.DEFS.keySet()) + "> [amount]")); break; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(bad("Nobody online called " + a[1] + ".")); break; }
                ItemStack it = items.make(a[2].toLowerCase(Locale.ROOT), a.length > 3 ? Math.max(1, num(a[3])) : 1);
                if (it == null) { s.sendMessage(bad("No such item. Items: " + String.join(", ", BureauItems.DEFS.keySet()))); break; }
                for (ItemStack l : t.getInventory().addItem(it).values()) t.getWorld().dropItemNaturally(t.getLocation(), l);
                s.sendMessage(ok("Gave " + t.getName() + " " + a[2] + "."));
            }
            case "reload" -> { reloadConfig(); s.sendMessage(ok("Bureau config reloaded.")); }
            default -> {
                s.sendMessage(Component.text("Bureau admin:", GOLD));
                for (String l : List.of(
                        "/bureau exchange [player]  open the Exchange",
                        "/bureau give <player> <n>  hand out Cold Hard Cash",
                        "/bureau item <player> <item> [n]  hand out a Bureau item",
                        "/bureau vault <player> [set <n>]",
                        "/bureau price [set <n>]  see or set today's price",
                        "/bureau event <boom|crash|calm>",
                        "/bureau npc <role> [name]  place a lizard where you stand",
                        "/bureau npc remove | reskin",
                        "/bureau lift set <top|bottom>",
                        "/bureau tax <player> [set <amount> | assign | clear | hunt]",
                        "/bureau fire <player> [reason]   /bureau unfire <player>",
                        "/bureau reload"))
                    s.sendMessage(Component.text(" " + l, NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private void cash(CommandSender s) {
        if (!(s instanceof Player p)) { s.sendMessage("Cold Hard Cash: " + Money.fmt(exchange.price)); return; }
        int pockets = Cash.count(p), held = vaults.held(p.getUniqueId());
        p.sendMessage(Component.text("✦ Cold Hard Cash ", GOLD).append(Component.text(Money.fmt(exchange.price), Cash.ICE))
                .append(Component.text(" (" + String.format(Locale.ROOT, "%+.1f%%", exchange.change() * 100) + " today)", NamedTextColor.GRAY)));
        p.sendMessage(Component.text(" Pockets " + pockets + "  ·  Vault " + held + "  ·  worth " + Money.fmt((pockets + held) * exchange.sellPrice()), NamedTextColor.GRAY));
    }

    private static int num(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; } }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (cmd.getName().equalsIgnoreCase("tax")) return a.length == 1 ? List.of("pay") : List.of();
        if (cmd.getName().equalsIgnoreCase("contracts")) return List.of();
        if (cmd.getName().equalsIgnoreCase("company")) {
            List<String> o = a.length == 1 ? List.of("create", "invite", "join", "leave", "kick", "boss", "deposit", "withdraw", "info", "top", "disband")
                    : a.length == 2 && List.of("invite", "kick", "boss").contains(a[0].toLowerCase(Locale.ROOT)) ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                    : a.length == 2 && List.of("join", "info").contains(a[0].toLowerCase(Locale.ROOT)) ? companies.names() : List.of();
            String last = a[a.length - 1].toLowerCase(Locale.ROOT);
            return o.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
        }
        if (cmd.getName().equalsIgnoreCase("cash")) return List.of();
        if (!admin(s)) return a.length == 1 ? List.of("hire", "party") : List.of();
        List<String> opts = switch (a.length) {
            case 1 -> List.of("exchange", "give", "item", "vault", "price", "event", "npc", "lift", "ticker", "chart", "tax", "fire", "unfire", "hire", "party", "reload", "help");
            case 2 -> switch (a[0].toLowerCase(Locale.ROOT)) {
                case "event" -> List.of("boom", "crash", "calm");
                case "npc" -> List.of("teller", "receptionist", "director", "security", "clerk", "taxman", "votes", "remove", "reskin");
                case "lift" -> List.of("set");
                case "ticker", "chart" -> List.of("place", "remove");
                case "price" -> List.of("set");
                case "give", "item", "vault", "exchange", "tax", "fire", "unfire" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
            case 3 -> a[0].equalsIgnoreCase("lift") ? List.of("top", "bottom") : a[0].equalsIgnoreCase("vault") ? List.of("set")
                    : a[0].equalsIgnoreCase("tax") ? List.of("set", "assign", "clear", "hunt")
                    : a[0].equalsIgnoreCase("item") ? new ArrayList<>(BureauItems.DEFS.keySet()) : List.of();
            default -> List.of();
        };
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
