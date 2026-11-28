# Warrior Tale: The Folk — v0.1 (Minecraft 26.3, Fabric)

Village folk and the tall Gharr raid-folk, with genes, courtship,
the bed union, blended children, and weight-scaled bed creaks.

## What's in v0.1
- **Village Folk** (`warriortale:folk`) — vanilla-sized, 4 bundled skins,
  genes rolled at spawn (vigor / twitch / knit / hide), names from the pool.
- **Gharr** (`warriortale:gharr`) — the tall model (16-long legs, 14 arms),
  stronger and tougher, slower. Peaceful in v0.1; raids come in v0.2.
- **Genes** — stored in NBT, drive max health, speed, armor and
  out-of-combat regen. Children blend both parents ±7 (the drawChild rule).
- **The union** — right-click two adult folk with **bread** to promise them.
  At night they walk to a bed together. Both lie down (vanilla sleep pose —
  the blanket does the censoring), climb-in creaks scaled by body weight,
  one strained settle creak, hearts, and after a short while a child —
  cross-folk pairs can produce either people. **No bed, no child.**
- **Sounds** — three procedural timber-creak variants, played only on
  weight events (climb in, settle). Subtitle: "Bed strains".

## Build (on your machine — needs internet for Mojang/Fabric downloads)
1. Install a JDK 21+ and Gradle 9.x (or just open the folder in IntelliJ
   IDEA with the Minecraft Development plugin — it handles Gradle itself).
2. In the project folder: `gradle wrapper --gradle-version 9.6.0`
   then `./gradlew build`
3. The mod jar lands in `build/libs/warriortale-0.1.0.jar` —
   drop it in your `.minecraft/mods` with Fabric Loader 0.19.5+ and
   Fabric API for 26.3.
4. If `fabric_version` fails to resolve, copy the current 26.3 line from
   https://fabricmc.net/develop into `gradle.properties`.

## Try it in game
- `/summon warriortale:folk` twice, give yourself bread, right-click each.
- Place a bed nearby, set time to night: `/time set night`. Watch.
- `/summon warriortale:gharr` for the tall one; promise a Gharr and a
  villager for a cross-folk child.

## Honest note on version drift
This was written against Minecraft 26.3 / Fabric without being able to
compile (the sandbox it was written in can't reach Mojang's servers).
The most drift-prone spots are marked with comments:
- `FolkRenderer` (the render-state API),
- `WarriorTale` entity registration,
- `UnionGoal`'s `MoveToBlockGoal` overrides.
If the first build throws errors, paste them back to Claude — they'll be
small signature fixes, not design problems.

## Design rules baked in (do not remove)
- Raider AI (v0.2) must never target `isBaby()` entities.
- The union is only ever depicted as: walk together → lie in bed →
  blanket → hearts → creaks on weight events only. Nothing further.
- No stat may ever key off a real-world nationality; culture layers are
  cosmetic, the fictional folk carry the biology.
