# CobbleGenetics quest chapters for FTB Quests

Seven ready-made chapters (43 quests) that teach players the genetics, mutation and Shadow systems as they play. Built for **FTB Quests 2101.1.x** (Minecraft 1.21.1).

| # | Chapter | What it teaches |
|---|---------|-----------------|
| 1 | Genetics 101 | The Analyzer, Genetic Potential, how individuals differ, Genetic Records |
| 2 | Heredity | The Genetic Incubator, inheritance, Mendelian traits, rare recessives |
| 3 | Mutations | Tiers, Mutagens, natural and inherited mutations |
| 4 | Advanced Mutation Research | Instability, environmental, elemental and ability mutations |
| 5 | Shadow Research | Finding, catching and measuring Shadow Pokémon |
| 6 | Purification | Every purification activity, the Chamber, Incense, rewards |
| 7 | Advanced Genetic Research | Combinations, shadow-resistant genetics, bloodlines, legendary mutations |

## How the tasks complete

Every task is either an **item task** or an **advancement task** pointing at a CobbleGenetics advancement (`cobblegenetics:analyze_first`, `cobblegenetics:purify`...). Those advancements are granted by the mod's own `cobblegenetics:genetics` criterion, which fires from real gameplay: analysing, breeding, purifying, discovering mutations. There are no fake objectives, and the mod does not depend on FTB Quests.

Quests that hinge on rare luck (a natural mutation, a rare recessive trait, Epic/Legendary mutations) are marked **optional**. The main path always has a reliable route: Mutagens for mutations, and the Shadow Beacon for Shadow encounters.

## Install (modpack makers)

1. Copy the files in `chapters/` to `config/ftbquests/quests/chapters/`.
2. Open `config/ftbquests/quests/lang/en_us.snbt` and paste the lines of `lang-en_us-lines.snbt` just before its last `}`. **Do not replace that file**: it holds the text of every other quest in the pack. If your pack uses *FTB Quests Lang Splitter*, run `/ftbquests reload` after pasting and let it split the lines as it does for the rest of the pack.
3. As an op, in game: `/ftbquests reload`.

The ids are fixed, so pasting the chapters again overwrites the first copy instead of duplicating it. Rewards are ordinary item rewards. Change them freely with the in-game editor.

## Progression notes

- Research levels rise by **milestones** (analyses, offspring studied, purifications: see `config/cobblegenetics.json`) **or** by the Analyzer Upgrade items the quests hand out. If a player already reached a level through milestones, the matching upgrade simply says so and stays in their inventory.
- Chapters 2 and 3 open after research level 2. Chapter 5 (Shadow) opens after research level 3, chapter 4 after level 4, chapter 7 after level 5.
- Chapter 2 uses the built-in **Genetic Incubator**. If your pack has a breeding addon that fires Cobblemon's `COLLECT_EGG` event, eggs from it count too: the "breed" advancement fires for both.
