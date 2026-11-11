# CobbleGenetics quest chapters for FTB Quests

Seven ready-made chapters (47 quests) that teach players the genetics, mutation and Shadow systems as they play. Built for **FTB Quests 2101.1.x** (Minecraft 1.21.1).

| # | Chapter | What it teaches |
|---|---------|-----------------|
| 1 | Genetics 101 | The Analyzer, Genetic Potential and training, how individuals differ, affinity resonance, Genetic Records |
| 2 | Heredity | The Genetic Incubator, Day Care Aides, inheritance, Mendelian traits, rare recessives, breeding value |
| 3 | Mutations | Tiers, Mutagens, natural and inherited mutations |
| 4 | Advanced Mutation Research | Instability, environmental, elemental and ability mutations |
| 5 | Shadow Research | Finding, catching and measuring Shadow Pokémon |
| 6 | Purification | Every purification activity, the Chamber, Incense, rewards |
| 7 | Advanced Genetic Research | Combinations, shadow-resistant genetics, bloodlines, legendary mutations |

## How the tasks complete

Every task is either an **item task** or an **advancement task** pointing at a CobbleGenetics advancement (`cobblegenetics:analyze_first`, `cobblegenetics:purify`...). Those advancements are granted by the mod's own `cobblegenetics:genetics` criterion, which fires from real gameplay: analysing, breeding, purifying, discovering mutations. There are no fake objectives, and the mod does not depend on FTB Quests.

Quests that hinge on rare luck (a natural mutation, a rare recessive trait, Epic/Legendary mutations) are marked **optional**. The main path always has a reliable route: Mutagens for mutations, and the Shadow Beacon for Shadow encounters.

## Install (modpack makers)

Do these steps **with the game or server closed**. FTB Quests rewrites its lang files from memory when it saves, so text pasted while it runs can be lost.

The chapters go in their own chapter group, **Cobblemods** (id `639C8B4E3836D569`), meant to hold Cobblemon addon chapters. Fusionmon's chapter belongs first in it (`order_index: 0`); the CobbleGenetics chapters are 1-7.

1. Copy the files in `chapters/` to `config/ftbquests/quests/chapters/`.
2. Create the group: in `config/ftbquests/quests/chapter_groups.snbt`, add the line from `chapter_group-entry.snbt` to the `chapter_groups` list, at the position where the group should appear (for example right after the Cobblemon group). To use an existing group instead, change `group` in each chapter file.
3. Add the text. Pick the layout your pack uses:
   * **`config/ftbquests/quests/lang/en_us/` is a folder** (packs with *FTB Quests Lang Splitter*, with `chapters/`, `chapter.snbt`, `chapter_group.snbt`...):
     * copy the files in `lang/en_us/chapters/` to `config/ftbquests/quests/lang/en_us/chapters/`;
     * paste the lines of `lang/en_us/chapter-lines.snbt` into `lang/en_us/chapter.snbt`, and the line of `lang/en_us/chapter_group-lines.snbt` into `lang/en_us/chapter_group.snbt`, each just before the last `}`.
   * **`config/ftbquests/quests/lang/en_us.snbt` is a single file:** paste the lines of `lang-en_us-lines.snbt` into it, just before its last `}` (they include the group title).

   Never put the files from `chapters/` (step 1) in the lang folder: they define quests, not text.
4. Start the game. If it was already running, use `/ftbquests reload` without editing any quest first.

## Progression notes

- Research levels rise by **milestones** (analyses, offspring studied, purifications: see `config/cobblegenetics.json`) **or** by the Analyzer Upgrade items the quests hand out. If a player already reached a level through milestones, the matching upgrade simply says so and stays in their inventory.
- Chapters 2 and 3 open after research level 2. Chapter 5 (Shadow) opens after research level 3, chapter 4 after level 4, chapter 7 after level 5.
- Chapter 2 uses the built-in **Genetic Incubator**. If your pack has a breeding addon that fires Cobblemon's `COLLECT_EGG` event, eggs from it count too: the "breed" advancement fires for both.
