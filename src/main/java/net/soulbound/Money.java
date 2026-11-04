package net.soulbound;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

/**
 * Essentials' money, found by name (no Vault needed): balance, take, give. Amounts are kept to the cent.
 * Everything here returns false / -1 if Essentials isn't running or doesn't know the player.
 */
final class Money {
    private static Method getMoney, add, subtract;
    private static boolean tried;

    private static boolean hook() {
        if (getMoney != null) return true;
        if (tried) return false;
        tried = true;
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) { Bukkit.getLogger().warning("[Soulbound] Essentials isn't running, so the shops can't use money."); return false; }
        try {
            Class<?> eco = Class.forName("com.earth2me.essentials.api.Economy", true, ess.getClass().getClassLoader());
            getMoney = eco.getMethod("getMoneyExact", UUID.class);
            add = eco.getMethod("add", UUID.class, BigDecimal.class);
            subtract = eco.getMethod("subtract", UUID.class, BigDecimal.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Bukkit.getLogger().warning("[Soulbound] Couldn't reach Essentials' money: " + ex);
            getMoney = null;
            return false;
        }
    }

    static boolean available() { return hook(); }

    private static BigDecimal cents(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP); }

    /** Their balance, or -1 if it can't be read. */
    static double balance(UUID id) {
        if (!hook()) return -1;
        try { return ((BigDecimal) getMoney.invoke(null, id)).doubleValue(); }
        catch (ReflectiveOperationException | RuntimeException ex) { return -1; }
    }

    /** Take money if they have it all; false (and nothing taken) otherwise. */
    static boolean take(UUID id, double amount) {
        if (amount <= 0) return true;
        if (!hook()) return false;
        double has = balance(id);
        if (has < 0 || has + 1e-9 < cents(amount).doubleValue()) return false;
        try { subtract.invoke(null, id, cents(amount)); return true; }
        catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }

    /** Give money; false if it couldn't be paid (they've never joined, Essentials is off...). */
    static boolean give(UUID id, double amount) {
        if (amount <= 0) return true;
        if (!hook()) return false;
        try { add.invoke(null, id, cents(amount)); return true; }
        catch (ReflectiveOperationException | RuntimeException ex) { return false; }
    }

    /** "$1,234" or "$12.50". */
    static String fmt(double v) {
        double c = cents(v).doubleValue();
        return c == Math.rint(c) ? String.format(Locale.ROOT, "$%,d", (long) c) : String.format(Locale.ROOT, "$%,.2f", c);
    }

    /** A price someone typed: "120", "12.5", "1,000", "$50". Negative or nonsense = NaN. */
    static double parse(String s) {
        try {
            double v = Double.parseDouble(s.replace(",", "").replace("$", "").trim());
            return v < 0 || Double.isNaN(v) || Double.isInfinite(v) ? Double.NaN : cents(v).doubleValue();
        } catch (NumberFormatException ex) { return Double.NaN; }
    }
}
