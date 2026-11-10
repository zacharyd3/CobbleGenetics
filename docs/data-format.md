# Adding mutations, traits and effects

Mutations and inheritable traits are **datapack content**. Add or change them without touching code, and reload with `/reload`. The built-in ones live in `src/main/resources/data/cobblegenetics/cobblegenetics/`. A datapack can override them by using the same file path, or add new ones under its own namespace.

```
data/<namespace>/cobblegenetics/mutations/<name>.json   -> mutation id "<namespace>:<name>"
data/<namespace>/cobblegenetics/traits/<locus>.json     -> trait locus "<locus>" (file name only)
```

A broken file is skipped with an error in the log that names it. It never stops the server.

## Mutation

```json
{
  "name": "Ember Heart",
  "description": "Heat from its surroundings awakened a fire within.",
  "category": "elemental",
  "tier": "rare",
  "negative": false,
  "weight": 10,
  "inheritable": true,
  "inherit_chance": 0.3,
  "sources": ["environment", "natural", "evolution"],
  "conditions": { "min_temperature": 1.5 },
  "excludes": ["cobblegenetics:frost_adapted"],
  "effects": [
    { "type": "tera_shift", "tera": "fire" },
    { "type": "exp_multiplier", "multiplier": 1.15, "min_temperature": 1.0 }
  ]
}
```

| Field | Meaning |
|-------|---------|
| `category` | `physical`, `elemental`, `biological` or `ability`. |
| `tier` | `common`, `uncommon`, `rare`, `epic` or `legendary`. Tier weights are in the config. Low genetic stability shifts rolls towards higher tiers. |
| `negative` | Harmful mutation. The share of negative rolls grows with instability and shrinks with Genetic Potential. |
| `weight` | Relative chance against other mutations of the same tier (default 10). |
| `inheritable`, `inherit_chance` | Chance an expressing parent passes it on expressed. Otherwise it may pass on *latent*, and two latent carriers can produce an offspring that expresses it. |
| `sources` | Pathways that can produce it: `natural` (wild spawn), `breeding`, `evolution`, `environment`, `item` (Mutagen), `purification`, `event` (thunderstorm spawns). Default: natural, breeding, evolution, item. |
| `excludes` | Mutations that cannot coexist with this one (checked both ways). |
| `name`, `description` | English fallback. Translations use `mutation.<namespace>.<name>` and `mutation.<namespace>.<name>.desc`. |

### Conditions (all optional)

| Condition | Example | Meaning |
|-----------|---------|---------|
| `biomes` | `["minecraft:desert", "#minecraft:is_nether"]` | Biome id or `#tag` where the Pokémon is. |
| `min_temperature` / `max_temperature` | `1.5` | Biome base temperature. Desert is 2.0, plains 0.8, snowy biomes 0.0 or lower. |
| `time` | `"night"` / `"day"` | |
| `weather` | `"rain"`, `"thunder"` | `rain` also matches thunderstorms. |
| `max_light` | `7` | Light level at the Pokémon (darkness, caves). |
| `types` | `["fire"]` | The species has one of these types. |
| `affinities` | `["dragon"]` | Its genetic elemental affinity is one of these. |
| `min_instability` | `0.3` | 1 - effective stability / 100. |
| `requires_mutations` | `["cobblegenetics:melanism"]` | **Rare combination**: all of these must already be expressed. |
| `requires_traits` | `["luster:l"]` | Expressed allele(s), written `locus:allele`. |
| `requires_shadow_history` | `true` | The Pokémon is or was a Shadow Pokémon. |
| `min_peak_corruption` | `80` | Highest corruption it ever reached. |
| `min_friendship` | `200` | |
| `min_generation` | `3` | Bred generation. |

A mutation with `requires_mutations` or `requires_traits` is a **combination**. When one is eligible, it wins half of the rolls. The research level 5 Analyzer hints at combinations whose factors are partly present.

## Trait locus

```json
{
  "name": "Luster",
  "alleles": [
    { "id": "N", "name": "Ordinary", "dominance": 2, "frequency": 0.9, "effects": [] },
    { "id": "l", "name": "Lustrous", "description": "A faint golden sheen.", "dominance": 1, "frequency": 0.1,
      "effects": [{ "type": "tint", "color": "#fff1b8" }] }
  ]
}
```

Each Pokémon carries two alleles per locus, one from each parent. The allele with the highest `dominance` is expressed. Alleles with equal dominance are co-dominant, so both are expressed. An allele with `frequency` ≤ 0.15 counts as *rare*. When a rare allele is expressed recessively (two copies), the "rare trait" research milestone fires. Wild Pokémon draw their alleles by `frequency`. Translations: `trait.<locus>` and `trait.<locus>.<allele>` (under `cobblegenetics`).

## Effect types

Effects are shared by mutations and alleles. **One-shot** effects change Cobblemon's own stored data once, when the mutation or allele is acquired. **Passive** effects are read when an event happens.

| Type | Kind | Fields | What it does |
|------|------|--------|--------------|
| `iv` | one-shot | `stat`, `amount` | Adds to an IV, clamped to 0-31. Stats: `hp`, `attack`, `defence`, `special_attack`, `special_defence`, `speed`. |
| `all_ivs` | one-shot | `amount` | Adds to every IV, clamped to 0-31. |
| `scale` | one-shot | `multiplier` | Multiplies Cobblemon's scale modifier (size and hitbox). |
| `tera_shift` | one-shot | `tera` (type name or `affinity`) | Sets the Tera Type. Config `mutationsCanChangeTeraType`. |
| `hidden_ability` | one-shot | | Gives the species' hidden ability. Config `mutationsCanModifyAbilities`. |
| `tint` | visual | `color` | Colour multiplier drawn on the client (aspect `cg-tint-rrggbb`). |
| `glow` | visual | `color` (optional) | Bioluminescence: the body stays dimly lit in the dark, a faint pulsing halo surrounds it and glow particles drift off (aspects `cg-glow` and, with a colour, `cg-glow-rrggbb`). Without `color` the halo takes the Pokémon's tint colour. |
| `aspect` | visual | `aspect` | A plain aspect for resource packs (see resource-packs.md). |
| `exp_multiplier` | passive | `multiplier`, optional `min_temperature` / `max_temperature` | EXP gained, optionally only in some climates. |
| `erratic_exp` | passive | `min`, `max` | Each EXP gain is multiplied by a random value. |
| `friendship_multiplier` | passive | `multiplier` | Friendship gains. |
| `corruption_resistance` | passive | `amount` (-1..0.95) | Share of incoming corruption resisted. Negative means more vulnerable. Effects stack multiplicatively. |
| `stability` | passive | `amount` | Added to genetic stability. |
| `regeneration` | passive | `fraction` | Share of max HP healed after each won battle. |
| `purification_speed` | passive | `multiplier` | Corruption removed by every activity. |

IV-based stats are deliberate. Cobblemon already sends IVs to Showdown, so the effects matter in battle, and a genetic Pokémon can never exceed a perfect wild one (31 per stat).

### New effect types from code

An addon registers a factory during mod construction:

```java
EffectTypes.register("myaddon:night_vision", json -> new MyEffect(json.get("power").getAsDouble()));
```

`MyEffect implements GeneticEffect` and overrides only the hooks it needs (`onAcquire`, `expMultiplier`, `aspects`...). Mutation JSON can then use `{"type": "myaddon:night_vision", "power": 2}`.

## Spawning genetically special Pokémon

CobbleGenetics registers Cobblemon properties, so anything that reads Pokémon properties can use them: commands, spawn pools in datapacks (special structures), NPC trainer teams, raid bosses, quest command rewards.

| Property | Example |
|----------|---------|
| `cg_shadow=true` or `cg_shadow=<1-100>` | `/pokespawn zubat cg_shadow=85` |
| `cg_mutation=<id>` (repeatable) | `/pokegive eevee cg_mutation=albinism cg_mutation=latent_potential` |

As conditions (for example in spawn rules or other mods' filters), `cg_shadow=true` matches Shadow Pokémon and `cg_mutation=x` matches Pokémon with that mutation.
