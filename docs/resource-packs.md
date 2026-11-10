# Resource packs: textures for mutations and Shadow Pokémon

CobbleGenetics tells clients about genetics through **aspects**, Cobblemon's per-Pokémon tags that the server syncs everywhere a Pokémon is drawn. Cobblemon resolvers already choose models and textures by aspect, so a resource pack can give any mutation a real look without code.

| Aspect | When |
|--------|------|
| `cg-mutation-<name>` | The Pokémon expresses that mutation, e.g. `cg-mutation-albinism`. |
| `cg-shadow`, `cg-shadow-<state>` | Shadow Pokémon. State is `dormant`, `unstable`, `aggressive`, `highly_corrupted` or `fully_corrupted`. |
| `cg-purified` | A purified former Shadow Pokémon. |
| `cg-heterochromia`, `cg-markings`, `cg-lustrous`, `cg-purified-aura` | Cosmetic hooks added by built-in mutations and traits. |
| `cg-tint-rrggbb`, `cg-glow` | Used by the built-in renderer (tint and particles). |

## Example: an albino Eevee texture

Add a resolver file with a high `order` for the species, in a file of your own so nothing is overwritten:

`assets/mypack/bedrock/pokemon/resolvers/0133_eevee/9_eevee_albino.json`

```json
{
  "species": "cobblemon:eevee",
  "order": 100,
  "variations": [
    {
      "aspects": ["cg-mutation-albinism"],
      "texture": "mypack:textures/pokemon/eevee_albino.png"
    }
  ]
}
```

Cobblemon merges every resolver of a species by `order` and uses the last matching variation, so a high `order` wins without replacing anything. Shiny and other aspects still combine as usual: add `"shiny"` to the aspect list for a shiny albino variant.

The built-in colour tint is applied on top of whatever texture is chosen. For a mutation that you texture fully, you can remove its `tint` effect with a datapack override of the mutation JSON (same path as the built-in file).
