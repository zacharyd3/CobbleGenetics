# Testing

## Automated

```bash
bash ./gradlew build      # compiles and runs the unit tests
```

`src/test/java/.../GeneticsRulesTest.java` checks the pure rules without starting Minecraft:

* rolled genomes stay inside their ranges;
* dominance, recessiveness and co-dominance of alleles;
* two carrier parents give about 25% recessive offspring (Mendel);
* offspring mix their parents and never copy one;
* opposed affinities lower predicted stability;
* latent mutations re-emerge from two carriers (about 25%);
* the mutation roller respects pathways, conditions, exclusions and combinations;
* the IV floor, the effect of corruption on stability, and genotype encoding.

## Server smoke test (done during development)

`bash ./gradlew runServer` with Cobblemon 1.7.3 and Kotlin for Forge 5.12.0 starts cleanly: "hooked into Cobblemon", 6 trait loci and 33 mutations loaded, no recipe or advancement errors. On that server:

```
/forceload add 0 0
/spawnpokemonat 0 -60 0 zubat level=20 cg_shadow=85 cg_mutation=albinism
/data get entity @e[type=cobblemon:pokemon,limit=1] Pokemon.PersistentData
```

This showed a `WILD` genome with the size gene applied, the albinism mutation, Shadow corruption 85 with stability damage for 4 thresholds, and every IV at or above the potential floor. All of it was stored in Cobblemon's own `PersistentData`.

## In-game checklist (needs a client)

Use a test world with cheats. Party slots are 1-6.

**Genetics and persistence**

1. Craft or `/give @s cobblegenetics:genetic_analyzer`. Right-click a wild Pokémon: the Analyzer screen opens with potential, and greyed rows for higher levels.
2. Catch it and analyse it again: the values are identical (same genome).
3. Put it in the PC, log out, restart the server, take it back and analyse it: still identical.
4. Trade it to a second player (Cobblemon trade) and analyse it: identical.
5. Evolve it (Rare Candies or an evolution stone) and analyse it: identical, and there is a chance of an evolution mutation message.

**Research**

6. Analyse 5 different Pokémon: you reach research level 2 (chat message plus advancement). Or `/cobblegenetics research 5` to see everything.

**Breeding**

7. Two compatible Pokémon of level 15+ (for example `/pokegive eevee level=20` twice, with opposite genders) and an Egg. Place the Genetic Incubator (2 blocks tall), right-click it and pick both: they leave your party and appear inside the glass. Right-click it with a berry twice (one per parent, love hearts); breeding then takes `incubatorBreedingMinutes` (default 5). With Fusionmon installed, also try two different families (e.g. Pikachu + Charmander): the egg hatches a fusion. Afterwards they are back in your party and an Egg sits inside; right-click to take it (spots coloured by type) and walk until it hatches into a level 1 Eevee (F3+H shows the exact blocks left). Break an occupied Incubator once to check the parents come back. Analyse it: Origin "Bred (generation 1)", parents listed.
8. At research level 4, analyse parent A, then parent B: a "Breeding Prediction" section appears.

**Mutations**

9. Use a Mutagen on a party Pokémon: "Something changed...". Analyse it: a new mutation, and stability lower by 10.
10. `/cobblegenetics mutate 1 gigantism`: the Pokémon is visibly bigger. `/cobblegenetics mutate 1 melanism`: it is darker (client tint).
11. `/cobblegenetics mutate 1 latent_potential`: its ability becomes the hidden ability (check the Cobblemon summary).

**Shadow and purification**

12. Use a Shadow Beacon: a dark Pokémon with a purple aura appears nearby. Approaching a highly corrupted one gives Darkness. Battle it (quest advancement "Darkness Stirs"), then catch it: friendship is 0.
13. `/cobblegenetics shadow 1 80` makes a party Pokémon Shadow. It cannot evolve. Use a Purifying Scent: progress message. Win a battle: corruption falls. Place a Purification Chamber and stand near it: faster passive purification each minute.
14. `/cobblegenetics shadow 1 0` (or keep purifying): "heart has opened". Analyse it: Purified, and possibly a purification mutation.

**FTB Quests**

15. Install the chapters (see `ftbquests/README.md`), then `/ftbquests reload`. Analysing should tick "First Sample".

## Performance

Nothing scans loaded Pokémon. Per minute, each online player's party (at most 6) is checked once. Per Pokémon entity, the client spawns aura particles every 3 ticks only when an aspect asks for them, and the server checks Shadow aggression every 40 ticks for highly corrupted wild ones only. To measure: `spark` (installed on the target server), `/spark profiler` around 60 Shadow Pokémon spawned with `/spawnpokemonat ~ ~ ~ zubat cg_shadow=90`.
