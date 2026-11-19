package net.baffoon.bureau;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/** Soulbound's small public API (Moloch's Curse, the Moloch fight), reached by name so the Bureau runs without it too. */
final class SoulboundHook {
    private SoulboundHook() {}

    private static Method m(String name, Class<?>... args) {
        Plugin sb = Bukkit.getPluginManager().getPlugin("Soulbound");
        if (sb == null || !sb.isEnabled()) return null;
        try { return Class.forName("net.soulbound.SoulboundApi", true, sb.getClass().getClassLoader()).getMethod(name, args); }
        catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    static int curseSeconds(Player p) {
        Method m = m("curseSeconds", Player.class);
        try { return m == null ? 0 : (int) m.invoke(null, p); } catch (ReflectiveOperationException | RuntimeException e) { return 0; }
    }

    static boolean reduceCurse(Player p, int seconds) {
        Method m = m("reduceCurse", Player.class, int.class);
        if (m == null) return false;
        try { m.invoke(null, p, seconds); return true; } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }

    static boolean molochFighting() {
        Method m = m("molochFighting");
        try { return m != null && (boolean) m.invoke(null); } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
}
