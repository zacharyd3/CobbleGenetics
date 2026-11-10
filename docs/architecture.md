# Architecture, extension points and limitations

CobbleGenetics extends Cobblemon. It does not replace any Cobblemon class, and its only mixin is a client-side render hook (colour tint, plus the light and halo of glowing Pokémon). Everything else goes through Cobblemon's public extension points, all checked with `javap` against the Cobblemon **1.7.3** jar.

## What Cobblemon 1.7.3 offers, and what we use

| Need | Cobblemon mechanism | Used in |
|------|---------------------|---------|
| Per-Pokémon persistent data | `Pokemon.getPersistentData()`: saved as `PersistentData` with the Pokémon in party, PC and entity NBT, and kept through evolution and trades | `data/PokemonGenetics` |
| Visuals synced to clients | `AspectProvider` (aspects reach every client and Cobblemon resolvers) | `data/GeneticsAspects` |
| Spawning special Pokémon from commands and datapacks | `CustomPokemonProperty` (`cg_shadow`, `cg_mutation`, `cg_genome`) | `data/GeneticsProperties` |
| Wild spawns | `POKEMON_ENTITY_SPAWN` (`SpawnEvent`, with the spawn position) | `CobblemonHooks` |
| Catching | `POKEMON_CAPTURED`, `POKEMON_CATCH_RATE` | `CobblemonHooks` |
| Breeding | **None built in.** 1.7.3 only fires `COLLECT_EGG` (both parents + egg properties) and `HATCH_EGG_PRE/POST` for breeding addons | `breeding/BreedingService` |
| Evolution | `EVOLUTION_TESTED` (block Shadow evolution), `EVOLUTION_COMPLETE` (mutation roll). The same `Pokemon` object evolves, so its data stays | `CobblemonHooks` |
| Friendship / EXP / level | `FRIENDSHIP_UPDATED` (adjustable), `EXPERIENCE_GAINED_EVENT_PRE` (adjustable), `LEVEL_UP_EVENT` | `CobblemonHooks` |
| Battles | `BATTLE_STARTED_POST`, `BATTLE_VICTORY` (winners and losers with their Pokémon) | `CobblemonHooks` |
| Party selection UI | `PartySelectCallbacks.createFromPokemon` (Cobblemon's own screen) | `item/PartySelect` |
| Battle-relevant stats | IVs (`setIV`), Tera Type (`setTeraType`), ability (`updateAbility`), scale (`setScaleModifier`): all are sent to Showdown or the client by Cobblemon | `effect/BuiltinEffects` |

## Data flow

```
          wild spawn / hatch / capture / gained / analyse / any hook
                                   │
                     GeneticsService.ensure(pokemon)  ── lazy and idempotent
          ┌────────────────────────┼─────────────────────────────┐
  pending genome from egg   roll fresh genome             already has one
  (cg_genome property)      (GeneticsRules.roll)          (fill new trait loci)
          └────────────────────────┼─────────────────────────────┘
                     one-shot phenotype, fresh Pokémon only
              (IV floor from potential, size gene, allele and mutation effects)
                                   │
              persistentData["cobblegenetics"] = { genome, shadow }
                                   │
          AspectProvider → cg-shadow-*, cg-tint-*, cg-glow, cg-mutation-* → clients
```

* **Lazy genetics.** Pokémon from before the mod (or from other mods) get a genome the first time something needs it. Those genomes are `RETROACTIVE`: they **never** change existing IVs or size. Only mutations gained later apply their effects. Wild spawns and hatches are `WILD` / `HATCHED` / `BRED` and get the full phenotype once.
* **Event-driven.** Nothing scans loaded Pokémon. The only periodic work is one check per minute (configurable) per online player, over their party of at most six: passive purification and environmental exposure.
* **Server authority.** Research-level filtering happens on the server when the analysis is built, so a modified client cannot see more than it should.

## Packages

| Package | Responsibility |
|---------|----------------|
| `genetics` | `Genome` (data), `GeneticsRules` (pure formulas, unit-tested), `TraitLocus` (Mendelian loci), `GeneticsService` (live Pokémon) |
| `mutation` | `MutationDef`, conditions, tiers, categories, pathways, `MutationRoller` (pure) |
| `effect` | `GeneticEffect` hooks, `EffectTypes` registry, built-in effect types |
| `data` | persistence (`PokemonGenetics`, `GenomeNbt`), datapack loading (`GeneticsRegistry`), aspects, properties |
| `shadow` | `ShadowData`, corruption states, `ShadowService` (corruption, purification, rewards), natural spawning |
| `breeding` | inheritance through addon eggs (`COLLECT_EGG`) and the built-in Genetic Incubator |
| `research` | per-player research attachment, milestones, `AnalysisService` (Analyzer reports, records) |
| `advancement` | the `cobblegenetics:genetics` criterion used by advancements and FTB Quests |
| `item`, `block`, `network`, `command` | content, Analyzer payloads, admin commands |
| `client` | Analyzer screen and its guide (Pokédex look, 3D portrait), render tint and glow (mixin), payload handler |
| `village` | Day Care Attendant profession (job site: the Incubator's lower half), its trades |

## How to extend

* **New mutation or trait:** a JSON file in a datapack. See [data-format.md](data-format.md).
* **New effect type:** `EffectTypes.register(...)` from an addon. See data-format.md.
* **New Shadow behaviour:** `ShadowService` is the single place where corruption changes. Add a call from a new event hook (for example, purify on a Cobblemon event) or a new `Activity` value. `ShadowSpawning` decides natural spawns.
* **New research milestone:** add a counter to `ResearchData`, bump it where the action happens, and add a `GeneticsTrigger.Action`. Advancements and quests can then use it.
* **Visuals:** resource packs can target the synced aspects (see [resource-packs.md](resource-packs.md)). New built-in visuals go in `GeneticsTint` / `WorldHooks.clientParticles`.

## Limitations, and what we do instead

| Requested | Limitation in Cobblemon 1.7.3 | What CobbleGenetics does |
|-----------|-------------------------------|--------------------------|
| Integrate with Cobblemon breeding | Cobblemon 1.7.3 has no breeding, only egg events for addons, and the target server has no breeding addon | Built-in **Genetic Incubator** with main-series compatibility rules and walk-to-hatch Pokémon Eggs (which also fire Cobblemon's `HATCH_EGG_POST`, so its hatch stats and advancements count them), plus the `COLLECT_EGG` hook for addons that use it. Offspring arrive at level 1 in the standard form of the base species (regional forms are not carried over) |
| Mutations that change secondary typing | Showdown reads types from the species; a per-individual type change would need a custom Showdown species for every mutated Pokémon (what Fusionmon does for fusions), with every battle registering it | Elemental mutations change the **Tera Type** (per-individual, battle-ready) and climate EXP behaviour. A true type change could be added later following Fusionmon's `FusionShowdown` approach |
| Shadow moves and abilities | New moves need Showdown data and client move data; Shadow-only moves would need both | Shadow Pokémon get corruption, aura, darkening, blocked evolution and breeding, lower catch rate, friendship damping and a darkness aura when highly corrupted. Purification can grant ability mutations (hidden ability, resistances) |
| Shadow stats | Battle stats come from Showdown (IVs and EVs), not from Cobblemon's stat provider | Shadow corruption does not change battle stats. Genetics changes stats only through IVs |
| Shadow status in Cobblemon's summary screen | Would need mixins into Cobblemon's GUI | Shown in the Genetic Analyzer, as a visual aura in the world, and as `cg-shadow` aspects |
| Unique eye colours and markings | Needs textures per species | Mutations add aspects (`cg-heterochromia`, `cg-markings`) that resource packs target. Built-in mutations show as colour tints |

## Compatibility notes (for the target server's mod list)

* **CobblemonSizeVariation** also sets the scale modifier. While it is installed, the genetic size gene is recorded but does not overwrite its scale; size mutations still multiply on top (config `sizeConflictMods`).
* **Fusionmon** keeps the visible (head) Pokémon's `persistentData`, so a fusion shows the head's genetics. Unfusing restores each part from its saved NBT, including its own genetics.
* **Mega Showdown, Pokérus, RCT, Raid Dens:** no shared state. Their Pokémon get retroactive genomes the first time they are analysed or caught. Datapack bosses can use `cg_shadow=...` in their properties.
* **Mixins:** one, client side, optional (`require = 0`). If a future Cobblemon changes `PokemonRenderer.render`, Pokémon simply render without the tint and glow.
