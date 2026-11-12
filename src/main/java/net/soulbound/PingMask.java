package net.soulbound;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ping mask: the ping everyone else sees for you (tab-list bars, %ping% placeholders, /ping) is your real ping plus an
 * offset, or a fixed number. Your actual connection is untouched.
 *
 * The server keeps one latency number per player, updated from keep-alives as (old * 3 + sample) / 4, and that number is
 * what goes out in the tab list. Each tick we work the true sample back out of any update, keep our own running real
 * value, and write the masked one back. Paper has no API for this, so the field is found by reflection; if it can't be,
 * the mask simply does nothing and says so once in the console.
 */
final class PingMask implements Listener {
    private final Soulbound plugin;
    private final NamespacedKey key;
    private final Map<UUID, int[]> state = new HashMap<>();   // {real, last value we wrote}
    private boolean broken;

    PingMask(Soulbound plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ping_mask");
    }

    /** "+50" adds 50 ms, "80" always shows 80 ms, null = off. */
    String setting(Player p) { return p.getPersistentDataContainer().get(key, PersistentDataType.STRING); }

    void set(Player p, String v) {
        if (v == null) {
            p.getPersistentDataContainer().remove(key);
            int[] st = state.remove(p.getUniqueId());
            if (st != null) write(p, st[0]);   // put their real ping back
        } else p.getPersistentDataContainer().set(key, PersistentDataType.STRING, v);
    }

    /** Their real ping as best we know it (the server's own number when no mask is on). */
    int real(Player p) {
        int[] st = state.get(p.getUniqueId());
        return st != null ? st[0] : p.getPing();
    }

    private static int shown(String setting, int real) {
        try {
            if (setting.startsWith("+")) return Math.max(0, real + Integer.parseInt(setting.substring(1)));
            return Math.max(0, Integer.parseInt(setting));
        } catch (NumberFormatException ex) {
            return real;
        }
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (broken) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                String s = setting(p);
                if (s == null) continue;
                Integer cur = read(p);
                if (cur == null) return;
                int[] st = state.get(p.getUniqueId());
                if (st == null) st = new int[]{cur, -1};
                else if (cur != st[1]) {
                    // a keep-alive came back: the server did (ours * 3 + sample) / 4; recover the sample, smooth it the same way
                    int sample = Math.max(0, cur * 4 - st[1] * 3);
                    st[0] = (st[0] * 3 + sample) / 4;
                }
                int want = shown(s, st[0]);
                if (cur != want) write(p, want);
                st[1] = want;
                state.put(p.getUniqueId(), st);
            }
        }, 20L, 1L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { state.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { state.remove(e.getPlayer().getUniqueId()); }

    // ---------------------------------------------------------------- the server's latency field

    private Field field;

    private Object listener(Player p) throws Exception {
        Object h = p.getClass().getMethod("getHandle").invoke(p);
        return h.getClass().getField("connection").get(h);
    }

    private Field field(Object conn) {
        if (field != null) return field;
        for (Class<?> c = conn.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("latency");
                if (f.getType() == int.class) { f.setAccessible(true); return field = f; }
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private Integer read(Player p) {
        try {
            Object conn = listener(p);
            Field f = field(conn);
            if (f == null) throw new NoSuchFieldException("latency");
            return f.getInt(conn);
        } catch (Throwable t) {
            broken = true;
            plugin.getLogger().warning("Ping mask is off: this server build doesn't match (" + t + ").");
            return null;
        }
    }

    private void write(Player p, int v) {
        try {
            Object conn = listener(p);
            Field f = field(conn);
            if (f != null) f.setInt(conn, v);
        } catch (Throwable ignored) {}
    }
}
