package net.baffoon.bureau;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.mcmonkey.sentinel.SentinelTrait;

/** Only loaded when Sentinel is installed: turns a Citizens lizard into a Sentinel guard. */
final class SentinelHook {
    static void guard(NPC npc, Player client, double damage) {
        SentinelTrait s = npc.getOrAddTrait(SentinelTrait.class);
        s.setInvincible(true);
        s.addTarget("monsters");
        s.damage = damage;
        s.range = 12;
        s.attackRate = 20;
        s.closeChase = true;
        s.fightback = true;
        s.allowKnockback = false;
        s.guardDistanceMinimum = 4;
        s.speed = 1.25;
        s.setGuarding(client.getUniqueId());
    }
}
