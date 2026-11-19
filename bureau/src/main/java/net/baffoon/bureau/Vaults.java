package net.baffoon.bureau;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The Bureau's freezer: Cold Hard Cash kept safe from thieves. Taking it out costs a small fee. */
final class Vaults {
    private final Bureau plugin;
    private final Map<UUID, Integer> held = new HashMap<>();

    Vaults(Bureau plugin) { this.plugin = plugin; }

    int held(UUID id) { return held.getOrDefault(id, 0); }

    void add(UUID id, int n) {
        int v = Math.max(0, held(id) + n);
        if (v == 0) held.remove(id); else held.put(id, v);
        plugin.dirty();
    }

    void set(UUID id, int n) { if (n <= 0) held.remove(id); else held.put(id, n); plugin.dirty(); }

    int total() { int t = 0; for (int v : held.values()) t += v; return t; }

    /** The fee on taking n out: a rate, rounded up, at least 1 once there's any rate at all. */
    static int fee(int n, double rate) { return n <= 0 || rate <= 0 ? 0 : Math.max(1, (int) Math.ceil(n * rate)); }

    /** The most that can come out of a vault holding h, fee included. */
    static int maxWithdraw(int h, double rate) {
        int n = h;
        while (n > 0 && n + fee(n, rate) > h) n--;
        return n;
    }

    void load(ConfigurationSection d) {
        held.clear();
        if (d == null) return;
        for (String k : d.getKeys(false)) try { held.put(UUID.fromString(k), d.getInt(k)); } catch (IllegalArgumentException ignored) {}
    }

    void save(ConfigurationSection d) { for (var e : held.entrySet()) d.set(e.getKey().toString(), e.getValue()); }
}
