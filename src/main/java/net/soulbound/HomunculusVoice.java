package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What a homunculus says: small, naive, grateful for being alive, easily frightened. A line shows as a little speech
 * bubble over its head for a few seconds. Each kind of moment has its own lines; {name} is its name (or "me").
 */
final class HomunculusVoice {
    private static final TextColor SPEECH = TextColor.color(0xFFE3EA);
    private final Soulbound plugin;
    private final Map<UUID, Long> lastSpoke = new HashMap<>();
    private final Map<String, Long> lastOfKind = new HashMap<>();   // husk + kind -> when
    private final Map<UUID, TextDisplay> bubbles = new HashMap<>();

    static final Map<String, List<String>> LINES = Map.ofEntries(
        Map.entry("idle", List.of(
            "Is the sun a big egg? When will it hatch?",
            "When I grow up, I want to be a cow.",
            "Do trees sleep standing up? That seems hard.",
            "I waved at a zombie. He didn't wave back. Maybe he's shy.",
            "Where do people go when they log off? Do they go in a jar too?",
            "I found a hole. I said hello into it. Nobody said hello back.",
            "Are clouds just sheep that got lost?",
            "What does 'griefing' mean? It sounds sad.",
            "I think the creeper wanted a hug. It looked very excited.",
            "If I eat enough carrots, will I see in the dark like you said?",
            "I was nothing in a jar. Now I'm here. Thank you for that.",
            "The grass is soft. Did you know the grass was soft?",
            "I can feel the wind. It's like the world is breathing on me.",
            "Every morning I wake up and I'm still here. I'm so glad.",
            "I love the sound rain makes. It sounds like the sky is clapping.",
            "I don't need much. Just this. Just you nearby.",
            "Is everyone this lucky? To be alive and have someone?",
            "I saw a flower today. I didn't pick it. It looked happy where it was.",
            "Thank you for making me. I know I'm strange. I'm still glad I'm me.",
            "Some days I just want to sit and look at things. Everything is so new.",
            "Sunsets are my favourite. The sky goes soft, like it's saying goodnight.",
            "I love being alive. I didn't know about it before, but I love it.",
            "Look! A butterfly! Do you think it likes me?",
            "The grass tickles my feet. I think it's saying hello.",
            "I'm so lucky I got born. Not everybody gets born, you know.",
            "I learned a new word today. 'Home.' It means here.",
            "When it rains, I stand very still so the drops can find me.",
            "I gave a bug a name. His name is Steve too. Everybody is Steve.",
            "If I'm very good, can I stay forever?",
            "I counted the stars. I got to eleven, then I fell asleep.",
            "The sky is so big. I'm glad it has room for me.",
            "Every day is my favourite day.")),
        Map.entry("follow", List.of(
            "Where are we going? I hope it's somewhere with flowers.",
            "I'll stay close. The world is very big and I am very small.",
            "Why do you walk so fast? Are we late for something?",
            "I like being where you are. It feels like the right place.",
            "Even if we're just walking, I'm happy. Walking with you is enough.",
            "I'm right behind you! I'm always right behind you!",
            "Hold my hand? I don't have to. But I'd like to.",
            "When I grow up, I want to be just like you.")),
        Map.entry("pet", List.of(
            "Is this what being loved feels like? It's nice.",
            "I didn't know hands could be gentle. Yours are.",
            "Thank you for touching me like I'm worth something.",
            "I'm going to remember this one. I'm keeping it.",
            "My chest feels warm. I think that's you.",
            "Your hand is warm. Did you make it warm just for me?",
            "Again? Please? That was my favourite one.",
            "I think my heart did a little jump. Is that allowed?",
            "When you pat me, I feel like a real person.")),
        Map.entry("feed", List.of(
            "You saved this for me? For me?",
            "Thank you. Nobody ever had to feed me before. Nobody ever wanted to.",
            "It's good. Everything is good when you give it to me.",
            "You brought me food? You remembered me!",
            "My tummy says thank you. It's very polite.",
            "I'm full. I think my tummy is happy. It made a noise.")),
        Map.entry("feed_tumour", List.of(
            "Thank you! It wiggled a bit going down. Is that normal?",
            "This is the best tumour I've ever had. It's also the only one. Still the best.")),
        Map.entry("feed_egg", List.of(
            "Will a chicken grow inside me now? I hope it's friendly.",
            "An egg! Thank you! I'll say sorry to it first.")),
        Map.entry("hungry", List.of(
            "My tummy keeps talking. I don't know what it's saying, but it sounds cross.",
            "Is it dinner time? I don't know how time works.")),
        Map.entry("starving", List.of("I'm so hungry... did you forget about me?")),
        Map.entry("farm", List.of(
            "I picked all the wheat! Was it sad? It didn't say anything.",
            "I put the carrots in the chest so they can have a little rest.",
            "I like looking after the plants. It's nice to help something grow, like you helped me.",
            "I'm helping! Am I helping? I think I'm helping!",
            "I said thank you to every carrot before I picked it.")),
        Map.entry("guard", List.of(
            "I'll keep your home safe. It's the only thing I can give back.",
            "I'm guarding your house. I'm very fierce. Rawr.")),
        Map.entry("stranger", List.of(
            "Someone's here! Are they a friend? They didn't say.",
            "Someone's here... I don't know them. I'm hiding.",
            "I told the stranger this is your house. They said 'okay' and took a chest.")),
        Map.entry("monster", List.of(
            "There's something in the dark. Is it a friend? Please say it's a friend.",
            "That zombie is looking at me. I don't think it wants a hug.",
            "The spider has too many legs. Why does it need so many legs?",
            "Can I hide behind you? Just for a little bit?",
            "I'm not scared. I'm just standing very close to you.")),
        Map.entry("hurt", List.of(
            "Ow. Was that on purpose? I don't think it was on purpose.",
            "I'm being brave. That's when you're scared but you stay.",
            "It's okay. I'm still alive. That's the important part.",
            "Being hurt makes me remember how nice it is not to be hurt.",
            "Ow... it's okay. Hurting means I'm still here.",
            "I'm being brave for you.",
            "Ow! Why did it do that? I didn't do anything!",
            "It hurts... but you're here, so it's okay. It's okay, right?",
            "I want to be brave, but my legs are shaking.",
            "Please don't let it happen again.")),
        Map.entry("alone", List.of(
            "Where did you go? You were here a minute ago.",
            "Hello? Are you coming back? I'll wait right here.",
            "It's very quiet without you. I don't like quiet.",
            "I'll count to ten, and then you'll be back. One... two...")),
        Map.entry("night", List.of(
            "Why does the sky turn off at night? Did it get tired?",
            "The stars are so far away. I'm glad I'm down here with you.",
            "Goodnight, sky. Goodnight, grass. Goodnight, you.",
            "Why is it so dark? Did someone turn the sky off?",
            "Can we stay where the torches are? The light feels safer.")),
        Map.entry("storm", List.of("The thunder is so loud. Is the sky angry at us?")),
        Map.entry("blood_moon", List.of(
            "Why is the moon that colour? Did it hurt itself?",
            "Even when the moon is scary, I feel safe if you're close.",
            "The moon looks poorly. Should we give it a cuddle?",
            "The moon is red. Something bad is going to happen, isn't it?",
            "Everything sounds hungry tonight.",
            "Please hold me. I don't want to see the red moon any more.")),
        Map.entry("moloch_realm", List.of(
            "Everyone here looks very angry. Maybe they need a nap.",
            "It's so hot here. And everything is shouting.",
            "Who's Moloch? Why does everyone whisper his name?",
            "I don't like this place. Can we go home? Please?")),
        Map.entry("fire_pit", List.of("Please don't put me in there. I'll be good. I promise I'll be good.")),
        Map.entry("heaven", List.of("Is this where good people go? Am I allowed in?")),
        Map.entry("pvp", List.of("Why are they hitting each other? Aren't they friends?")),
        Map.entry("named", List.of(
            "{name}! I have a name now. Does that mean I'm real?",
            "A name! Now when you call me, I'll know you mean me.",
            "{name}? I'm {name}? I love it. Thank you for my name!")),
        Map.entry("flask_in", List.of(
            "Okay. I'll wait here. You'll come back, won't you?",
            "I'll wait. I don't mind waiting. Waiting means you're coming back.",
            "Okay. I'll be good in here. Don't forget about me!",
            "It's dark in there. You'll come back, won't you?")),
        Map.entry("flask_out", List.of(
            "You came back! You came back.",
            "You came back! I knew you would! I hoped you would!")),
        Map.entry("dressed", List.of("Do I look like a person now?")),
        Map.entry("owner_died", List.of("Get up! Please get up! I don't know what to do without you!")),
        Map.entry("owner_back", List.of("You're back! I was so scared! Don't do that again!")),
        Map.entry("death", List.of(
            "...thank you for letting me be alive. It was the best thing.",
            "...thank you for letting me be little with you.")));

    HomunculusVoice(Soulbound plugin) { this.plugin = plugin; }

    static String pick(String kind) {
        List<String> l = LINES.get(kind);
        return l == null || l.isEmpty() ? null : l.get(ThreadLocalRandom.current().nextInt(l.size()));
    }

    /**
     * Say a line of this kind, unless it spoke a moment ago or said this kind of thing recently.
     * @param kindCooldownSeconds how long before it says this kind of thing again (0 = no limit)
     */
    boolean say(Husk h, String kind, String name, int kindCooldownSeconds) {
        if (!h.isValid() || !plugin.getConfig().getBoolean("homunculus.speech", true)) return false;
        long now = System.currentTimeMillis();
        if (now - lastSpoke.getOrDefault(h.getUniqueId(), 0L) < 4000) return false;
        String k = h.getUniqueId() + ":" + kind;
        if (kindCooldownSeconds > 0 && now - lastOfKind.getOrDefault(k, 0L) < kindCooldownSeconds * 1000L) return false;
        String line = pick(kind);
        if (line == null) return false;
        line = line.replace("{name}", name == null ? "Me" : name);
        lastSpoke.put(h.getUniqueId(), now);
        lastOfKind.put(k, now);
        bubble(h, line);
        return true;
    }

    /** A little speech bubble riding just over its head for a few seconds. */
    private void bubble(Husk h, String line) {
        TextDisplay old = bubbles.remove(h.getUniqueId());
        if (old != null && old.isValid()) old.remove();
        TextDisplay d = h.getWorld().spawn(h.getLocation().add(0, 1.2, 0), TextDisplay.class, t -> {
            t.text(Component.text(line, SPEECH).decoration(TextDecoration.ITALIC, true));
            t.setBillboard(Display.Billboard.CENTER);
            t.setLineWidth(160);
            t.setBackgroundColor(Color.fromARGB(110, 20, 10, 20));
            t.setPersistent(false);
            t.setTransformation(new Transformation(new Vector3f(0, 0.35f, 0), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
        });
        h.addPassenger(d);
        bubbles.put(h.getUniqueId(), d);
        h.getWorld().playSound(h.getLocation(), org.bukkit.Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.6f, 1.5f);
        long ticks = Math.max(60, Math.min(160, line.length() * 2L + 40));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.isValid()) d.remove();
            bubbles.remove(h.getUniqueId(), d);
        }, ticks);
    }

    void forget(Entity h) {
        TextDisplay d = bubbles.remove(h.getUniqueId());
        if (d != null && d.isValid()) d.remove();
        lastSpoke.remove(h.getUniqueId());
    }
}
