package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The price of Cold Hard Cash, in server money.
 *
 * Every half hour it wobbles a little and is pulled back towards the base price; buying nudges it up and selling
 * nudges it down; it can't leave [min, max], or move more than daily-limit from where the day opened. Events push it
 * hard for a while: a rally (Moloch waking, or /bureau event boom) and a crash (Moloch put down, or /bureau event crash).
 * A history of half-hourly prices (the last day) feeds the chart and the ticker.
 */
final class Exchange {
    private final Bureau plugin;
    double price, dayOpen;
    long day;
    final List<Double> history = new ArrayList<>();
    String event = "";          // "RALLY", "CRASH" or ""
    double eventTarget;
    long eventUntil;
    private String lastMoloch = "";

    Exchange(Bureau plugin) { this.plugin = plugin; }

    private ConfigurationSection c() { return plugin.getConfig().getConfigurationSection("exchange"); }
    private double cfg(String k, double d) { return plugin.getConfig().getDouble("exchange." + k, d); }

    void load(ConfigurationSection d) {
        price = d.getDouble("price", cfg("start-price", 25));
        dayOpen = d.getDouble("day-open", price);
        day = d.getLong("day", today());
        history.clear();
        for (Object o : d.getList("history", List.of())) if (o instanceof Number n) history.add(n.doubleValue());
        if (history.isEmpty()) history.add(price);
    }

    void save(ConfigurationSection d) {
        d.set("price", price);
        d.set("day-open", dayOpen);
        d.set("day", day);
        d.set("history", new ArrayList<>(history));
    }

    long today() {
        ZoneId z;
        try { z = ZoneId.of(plugin.getConfig().getString("exchange.timezone", "Europe/London")); } catch (Exception e) { z = ZoneId.of("Europe/London"); }
        return LocalDate.now(z).toEpochDay();
    }

    double buyPrice() { return buyPrice(cfg("spread", 0.05)); }
    double sellPrice() { return sellPrice(cfg("spread", 0.05)); }
    double buyPrice(double spread) { return round(price * (1 + spread)); }
    double sellPrice(double spread) { return round(price * (1 - spread)); }

    private Double queued;   // the next half hour's wobble, rolled early so a Market Report can know it

    /** Roughly how much the next half-hour tick will move the price (sign is what matters). */
    double nextMove() {
        if (queued == null) queued = ThreadLocalRandom.current().nextGaussian() * cfg("drift", 0.02);
        return price * queued + (cfg("base-price", 25) - price) * cfg("pull", 0.10);
    }
    static double round(double v) { return Math.round(v * 100) / 100.0; }

    /** Change since the day opened, as a fraction. */
    double change() { return dayOpen <= 0 ? 0 : price / dayOpen - 1; }

    private void clamp() {
        double lim = cfg("daily-limit", 0.20);
        price = Math.max(dayOpen * (1 - lim), Math.min(dayOpen * (1 + lim), price));
        price = Math.max(cfg("min-price", 10), Math.min(cfg("max-price", 60), price));
        price = round(price);
    }

    private void newDay() {
        long t = today();
        if (t != day) { day = t; dayOpen = price; plugin.newDay(); }
    }

    /** Buying or selling n notes moves the price a hair. */
    void pressure(int n, boolean buy) {
        newDay();
        double k = cfg("pressure", 0.0005) * n;
        price *= buy ? 1 + k : 1 - k;
        clamp();
        plugin.dirty();
    }

    /** Every half hour: a wobble, a pull back towards base, and a point on the chart. */
    void halfHour() {
        newDay();
        if (event.isEmpty()) {
            double base = cfg("base-price", 25);
            double wobble = queued != null ? queued : ThreadLocalRandom.current().nextGaussian() * cfg("drift", 0.02);
            queued = null;
            price = price * (1 + wobble) + (base - price) * cfg("pull", 0.10);
            clamp();
        }
        history.add(price);
        while (history.size() > 48) history.remove(0);
        plugin.dirty();
    }

    /** Every minute: carry an event along, and watch Moloch. */
    void minute() {
        newDay();
        if (!event.isEmpty()) {
            if (System.currentTimeMillis() > eventUntil) { event = ""; }
            else { price += (eventTarget - price) * 0.25; clamp(); plugin.dirty(); }
        }
        if (plugin.getConfig().getBoolean("exchange.moloch-events", true)) watchMoloch();
    }

    /** RALLY (up 30%) or CRASH (down 20%) over the next ten minutes, with a headline. */
    void event(String kind, String why) {
        kind = kind.toUpperCase(java.util.Locale.ROOT);
        if (kind.equals("CALM")) { event = ""; return; }
        event = kind;
        eventTarget = price * (kind.equals("RALLY") ? 1 + cfg("rally", 0.30) : 1 - cfg("crash", 0.20));
        eventUntil = System.currentTimeMillis() + 10 * 60_000L;
        Component head = Component.text("▲ ", NamedTextColor.GREEN);
        if (kind.equals("CRASH")) head = Component.text("▼ ", NamedTextColor.RED);
        Bukkit.broadcast(head.append(Component.text("THE EXCHANGE: ", NamedTextColor.GOLD))
                .append(Component.text(why, NamedTextColor.GRAY)));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, kind.equals("RALLY") ? 1.4f : 0.6f);
    }

    private void watchMoloch() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        String now;
        try { now = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, "%soulbound_moloch%"); }
        catch (Throwable t) { return; }
        now = now.replaceAll("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-or])", "").trim().toUpperCase(java.util.Locale.ROOT);
        if (now.startsWith("%")) return;
        boolean awake = now.contains("AWAKE"), was = lastMoloch.contains("AWAKE");
        if (awake && !was && !lastMoloch.isEmpty()) event("RALLY", "Moloch is awake. The lizards are selling him supplies. Cash is climbing.");
        if (!awake && was) event("CRASH", "Moloch has gone quiet. Business is bad. Cash is falling.");
        lastMoloch = now;
    }

    /** "▁▂▃▅▇" style line of the last n points. */
    String sparkline(int n) {
        String bars = "▁▂▃▄▅▆▇█";
        List<Double> h = history.subList(Math.max(0, history.size() - n), history.size());
        double lo = h.stream().mapToDouble(d -> d).min().orElse(price), hi = h.stream().mapToDouble(d -> d).max().orElse(price);
        StringBuilder sb = new StringBuilder();
        for (double v : h) sb.append(bars.charAt(hi - lo < 1e-9 ? 3 : (int) Math.round((v - lo) / (hi - lo) * 7)));
        return sb.toString();
    }

    double high() { return history.stream().mapToDouble(d -> d).max().orElse(price); }
    double low() { return history.stream().mapToDouble(d -> d).min().orElse(price); }
}
