package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * The two lines under the server's name in the multiplayer list (motd.line1 / motd.line2), in MiniMessage, so
 * hex colours and gradients work (server.properties only knows the 16 basic colours).
 * With motd.centre on, each line is padded with spaces so it sits in the middle of the list entry.
 * Change the lines, then /sb reload. motd.enabled: false hands the MOTD back to server.properties.
 */
final class Motd implements Listener {
    static final String DEFAULT_1 = "<white><bold>BaffoonMC</bold></white> <dark_gray>|</dark_gray> <bold><gradient:#F2C94C:#FFF3B0>GRAND OPENING WEEK</gradient></bold>";
    static final String DEFAULT_2 = "<gray>A brand new world.</gray> <green>Claim the best land first!</green>";

    /** Width of the text area of a server-list entry, in GUI pixels. */
    private static final int ROW = 270;
    /** Advance widths of the default font for ' ' (32) to '~' (126), shadow pixel included. */
    private static final int[] ADV = {4,2,4,6,6,6,6,2,4,4,4,6,2,6,2,6,6,6,6,6,6,6,6,6,6,6,2,2,5,6,5,6,7,6,6,6,6,6,6,6,6,4,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,4,6,4,6,6,3,6,6,6,6,6,5,6,6,2,6,5,3,6,6,6,6,6,6,6,4,6,6,6,6,6,6,4,2,4,7};

    private final Soulbound plugin;
    private String lastSource;
    private Component cached;

    Motd(Soulbound plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent e) {
        if (!plugin.getConfig().getBoolean("motd.enabled", true)) return;
        e.motd(build());
    }

    private Component build() {
        String l1 = plugin.getConfig().getString("motd.line1", DEFAULT_1);
        String l2 = plugin.getConfig().getString("motd.line2", DEFAULT_2);
        boolean centre = plugin.getConfig().getBoolean("motd.centre", true);
        String key = l1 + "\n" + l2 + "\n" + centre;
        if (key.equals(lastSource) && cached != null) return cached;
        Component a = line(l1, centre), b = line(l2, centre);
        cached = l2 == null || l2.isBlank() ? a : a.append(Component.newline()).append(b);
        lastSource = key;
        return cached;
    }

    private static Component line(String mini, boolean centre) {
        if (mini == null || mini.isBlank()) return Component.empty();
        Component c;
        try { c = MiniMessage.miniMessage().deserialize(mini); }
        catch (Exception ex) { c = Component.text(mini); }
        if (!centre) return c;
        int pad = Math.max(0, (ROW - width(c, false)) / 2);
        int spaces = Math.round(pad / 4f);
        return spaces == 0 ? c : Component.text(" ".repeat(spaces)).append(c);
    }

    private static int width(Component c, boolean boldAbove) {
        TextDecoration.State s = c.decoration(TextDecoration.BOLD);
        boolean bold = s == TextDecoration.State.TRUE || (s == TextDecoration.State.NOT_SET && boldAbove);
        int w = 0;
        if (c instanceof TextComponent t) {
            for (char ch : t.content().toCharArray()) w += (ch >= 32 && ch <= 126 ? ADV[ch - 32] : 6) + (bold ? 1 : 0);
        }
        for (Component child : c.children()) w += width(child, bold);
        return w;
    }
}
