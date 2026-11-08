package net.soulbound;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat fix: for a player whose game won't show other players' chat (but does show server messages and DMs), send
 * them everyone's chat as server messages instead. Exactly the same text, rank tags and all; only how it's delivered
 * changes. Switched on per player: /sb player <name> chatfix on (off to undo). chat-fix.everyone: true does it for all.
 */
final class ChatFix implements Listener {
    private final Soulbound plugin;
    private final NamespacedKey key;

    ChatFix(Soulbound plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "chat_fix");
    }

    boolean is(Player p) {
        return plugin.getConfig().getBoolean("chat-fix.everyone", false) || p.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    void set(Player p, boolean on) {
        if (on) p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        else p.getPersistentDataContainer().remove(key);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        List<Player> fix = new ArrayList<>();
        for (Audience a : e.viewers()) if (a instanceof Player p && !p.equals(e.getPlayer()) && is(p)) fix.add(p);
        if (fix.isEmpty()) return;
        for (Player p : fix) {
            e.viewers().remove(p);
            Component line = e.renderer().render(e.getPlayer(), e.getPlayer().displayName(), e.message(), p);
            p.sendMessage(line);   // a plain server message: shown even when the game hides player chat
        }
    }
}
