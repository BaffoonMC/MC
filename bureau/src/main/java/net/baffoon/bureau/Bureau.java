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
        load();
        var pm = getServer().getPluginManager();
        pm.registerEvents(capital, this);
        pm.registerEvents(lizards, this);
        pm.registerEvents(lift, this);
        for (String c : List.of("bureau", "cash")) {
            var cmd = getCommand(c);
            if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        }
        Bukkit.getScheduler().runTaskTimer(this, exchange::halfHour, 20L * 60 * 30, 20L * 60 * 30);
        Bukkit.getScheduler().runTaskTimer(this, exchange::minute, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, lizards::watch, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, () -> { if (dirty) save(); }, 20L * 30, 20L * 30);
        if (pm.getPlugin("PlaceholderAPI") != null) {
            try { new BureauPlaceholders(this).register(); getLogger().info("PlaceholderAPI found: %bureau_...% placeholders registered."); }
            catch (Throwable t) { getLogger().warning("Couldn't register placeholders: " + t); }
        }
        if (!Money.available()) getLogger().warning("Essentials' money isn't available, so the Exchange can't buy or sell.");
    }

    @Override
    public void onDisable() { save(); }

    Exchange exchange() { return exchange; }
    Vaults vaults() { return vaults; }
    Capital capital() { return capital; }

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
        if (a.length == 0) {
            s.sendMessage(Component.text("The Bureau of Cold-Blooded Affairs", GOLD));
            s.sendMessage(Component.text(" Cold Hard Cash is " + Money.fmt(exchange.price) + " today ("
                    + String.format(Locale.ROOT, "%+.1f%%", exchange.change() * 100) + "). Find Mr. Coldwell at the Exchange to trade.", NamedTextColor.GRAY));
            s.sendMessage(Component.text(" The lift is in the bank at spawn: crouch on it to go down.", NamedTextColor.GRAY));
            if (admin(s)) s.sendMessage(Component.text(" Admin: /bureau help", NamedTextColor.DARK_GRAY));
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
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
                if (a.length < 2) { s.sendMessage(bad("Usage: /bureau npc <teller|receptionist|director|security|clerk|taxman> [name] | remove | reskin")); break; }
                if (a[1].equalsIgnoreCase("remove")) { s.sendMessage(ok(lizards.removeNear(p))); break; }
                if (a[1].equalsIgnoreCase("reskin")) { s.sendMessage(ok(lizards.reskinAll())); break; }
                Lizards.Role r = Lizards.Role.of(a[1]);
                if (r == null) { s.sendMessage(bad("Roles: teller, receptionist, director, security, clerk, taxman.")); break; }
                String name = a.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : null;
                s.sendMessage(ok(lizards.spawn(p, r, name)));
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
            case "reload" -> { reloadConfig(); s.sendMessage(ok("Bureau config reloaded.")); }
            default -> {
                s.sendMessage(Component.text("Bureau admin:", GOLD));
                for (String l : List.of(
                        "/bureau exchange [player]  open the Exchange",
                        "/bureau give <player> <n>  hand out Cold Hard Cash",
                        "/bureau vault <player> [set <n>]",
                        "/bureau price [set <n>]  see or set today's price",
                        "/bureau event <boom|crash|calm>",
                        "/bureau npc <role> [name]  place a lizard where you stand",
                        "/bureau npc remove | reskin",
                        "/bureau lift set <top|bottom>",
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
        if (cmd.getName().equalsIgnoreCase("cash") || !admin(s)) return List.of();
        List<String> opts = switch (a.length) {
            case 1 -> List.of("exchange", "give", "vault", "price", "event", "npc", "lift", "reload", "help");
            case 2 -> switch (a[0].toLowerCase(Locale.ROOT)) {
                case "event" -> List.of("boom", "crash", "calm");
                case "npc" -> List.of("teller", "receptionist", "director", "security", "clerk", "taxman", "remove", "reskin");
                case "lift" -> List.of("set");
                case "price" -> List.of("set");
                case "give", "vault", "exchange" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                default -> List.of();
            };
            case 3 -> a[0].equalsIgnoreCase("lift") ? List.of("top", "bottom") : a[0].equalsIgnoreCase("vault") ? List.of("set") : List.of();
            default -> List.of();
        };
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
