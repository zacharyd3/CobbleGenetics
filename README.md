# CobbleGenetics

Individual genetics, mutations and Shadow Pokémon for **Cobblemon 1.7.3** on **NeoForge, Minecraft 1.21.1**, with a seven-chapter FTB Quests research progression.

Every Pokémon gets its own genetic profile. Two Pidgey from the same route are not the same Pidgey. Breed for rare traits, discover mutations through nature, evolution, storms and science, and purify corrupted Shadow Pokémon to unlock genes no breeding can produce.

CobbleGenetics is a separate mod from [Fusionmon](https://github.com/zacharyd3/fusionmon-fabric). It reuses the same Cobblemon integration patterns, and the two run together.

## Features

**Genetics.** Every Pokémon has:

| Gene | Effect |
|------|--------|
| Genetic Potential | Freshly spawned and hatched Pokémon get a minimum for every IV (up to 12 at potential 100). |
| Genetic Stability | Low stability means more mutations, a larger share of them harmful, and higher tiers. |
| Elemental Affinity | A hidden type leaning. Some mutations shift the Tera Type to it. |
| Mutation Susceptibility | How often mutation rolls happen. |
| Size gene | Physical size. |
| Inheritable traits | Six Mendelian loci (dominant, recessive and co-dominant alleles), such as the rare recessive *Shadowproof*. |

**Breeding and inheritance.** Offspring mix their parents' genes with noise. Each trait takes one allele from each parent. Opposed affinities make offspring unstable, while compatible ones give hybrid vigour. Mutations pass on directly or *latent*, and two latent carriers can make them reappear. Breeding uses the built-in **Genetic Incubator**, because Cobblemon 1.7.3 has no breeding of its own. Breeding addons that fire Cobblemon's egg events are supported too.

**Mutations.** 33 data-driven mutations in four categories (physical, elemental, biological, ability) plus negative ones, across five tiers from Common to Legendary. They arise from wild spawns, breeding, evolution, the environment (heat, cold, sea, darkness), Mutagens, purification, and thunderstorm "genetic storms". Rare combinations need other mutations first: *Void Luminescence* requires Melanism plus Bioluminescence. Mutations show as colour tints, glow, size and aspects for resource packs.

**Shadow Pokémon.** Rare corrupted Pokémon with five corruption states, a darkened look, a purple aura, darkness around the most corrupted wild ones, a lower catch rate, no evolution or breeding, and damped friendship. Corruption drains genetic stability and leaves lasting damage.

**Purification.** A gradual process with feedback at every 10%. Corruption falls through time together (more when the Pokémon is sent out), winning battles, friendship, levelling, Purifying Scent and Incense, and the Purification Chamber. Purified Pokémon gain friendship and stability, and can receive purification-only mutations.

**Genetic Analyzer.** Use it on any Pokémon in the world, or on a party member. It shows a report gated by **five research levels**. Levels rise from milestones or from Analyzer Upgrades. Level 4 adds breeding predictions between the last two analysed Pokémon, and level 5 adds exact Shadow analysis and hints at rare combinations.

**FTB Quests.** Seven chapters and 43 quests, all completed by real gameplay through the mod's advancement criterion. See [`ftbquests/`](ftbquests/README.md).

## Items

| Item | Use |
|------|-----|
| Genetic Analyzer | Analyse a Pokémon (right-click it, or use in the air for a party member). |
| Analyzer Upgrade (Stability, Heredity, Mutation, Shadow modules) | Raise research to level 2 / 3 / 4 / 5. |
| Genetic Record | Saved analysis (Record button on the Analyzer screen; uses 1 paper). |
| Genetic Incubator | Breed two compatible party Pokémon (uses one Egg). |
| Mutagen | Force a mutation at the cost of stability. |
| Gene Stabilizer | Repair corruption damage. |
| Purifying Scent / Purification Incense | Lower corruption (the Incense only below 60%). |
| Shadow Beacon | Call a wild Shadow Pokémon (a guaranteed encounter for quests). |
| Purification Chamber (block) | Triples passive purification nearby. |

All items have recipes (see JEI), and the quests hand most of them out.

## Installation

1. Requires NeoForge 21.1.x, **Cobblemon 1.7.3** and Kotlin for Forge (already a Cobblemon requirement).
2. Put `cobblegenetics-<version>.jar` in `mods/` on the **server and every client** (the client draws the Analyzer screen and the Shadow and mutation visuals).
3. Start once: `config/cobblegenetics.json` is created with every option.
4. Optional: install the quest chapters from [`ftbquests/`](ftbquests/README.md).

## Configuration

`config/cobblegenetics.json` covers genetic ranges, the IV floor, size variance, inheritance noise and penalties, opposed affinities, mutation switches (Tera Type and ability changes, inheritance), per-pathway mutation chances, tier weights, Shadow spawn rates and multipliers, corruption and purification rates, item strengths, research milestones and built-in breeding. Each value is documented in [`GeneticsConfig.java`](src/main/java/com/cobblegenetics/config/GeneticsConfig.java). Admins can reload it with `/cobblegenetics reloadconfig`. Mutations and traits are datapacks and reload with `/reload`.

## Commands (permission level 2)

```
/cobblegenetics analyze <slot>                 Analyzer report for a party slot
/cobblegenetics info <slot>                    raw genome in chat
/cobblegenetics mutate <slot> <mutation>       add a mutation
/cobblegenetics roll <slot> <source>           force a roll through a pathway
/cobblegenetics shadow <slot> <0-100>          make Shadow / set corruption (0 purifies)
/cobblegenetics research <level> [player]      set research level
/cobblegenetics mutations                      list loaded mutations
/cobblegenetics reloadconfig
```

Any Cobblemon command or spawn pool accepts the properties `cg_shadow=<true|1-100>` and `cg_mutation=<id>`, for example `/pokespawn zubat cg_shadow=85`.

## Documentation

* [Architecture, extension points and limitations](docs/architecture.md): what Cobblemon 1.7.3 allows, and what was done instead where it doesn't.
* [Adding mutations, traits and effect types](docs/data-format.md)
* [Resource packs: textures for mutations and Shadow Pokémon](docs/resource-packs.md)
* [Testing](docs/testing.md)

## Building

```bash
bash ./gradlew build
```

The jar ends up in `build/libs/`. `bash ./gradlew runClient` / `runServer` start a development game, with Kotlin for Forge added for development only.

## License

MPL-2.0, like Cobblemon.
