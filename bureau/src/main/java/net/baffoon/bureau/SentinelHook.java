package net.baffoon.bureau;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.mcmonkey.sentinel.SentinelTrait;

/** Only loaded when Sentinel is installed: turns a Citizens lizard into a Sentinel guard. */
final class SentinelHook {
    static void guard(NPC npc, Player client, double damage, double health, boolean players) {
        SentinelTrait s = npc.getOrAddTrait(SentinelTrait.class);
        s.setInvincible(false);          // he can be killed...
        s.setHealth(health);
        s.respawnTime = -1;              // ...and doesn't come back
        s.healRate = 0;
        s.addTarget("monsters");
        if (!players) s.addIgnore("players");
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

    /** Go for this player or mob (someone the client is fighting). */
    static void target(NPC npc, java.util.UUID who) {
        SentinelTrait s = npc.getTraitNullable(SentinelTrait.class);
        if (s != null) s.targetingHelper.addTarget(who);
    }
}
