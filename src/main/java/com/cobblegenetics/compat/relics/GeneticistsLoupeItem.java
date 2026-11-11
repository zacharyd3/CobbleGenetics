package com.cobblegenetics.compat.relics;

import it.hurts.sskirillss.relics.api.relics.AbilityMetricTemplate;
import it.hurts.sskirillss.relics.api.relics.AbilityStatisticTemplate;
import it.hurts.sskirillss.relics.api.relics.RelicTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.AbilitiesTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.AbilityTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.ExperienceSourceTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.ExperienceSourcesTemplate;
import it.hurts.sskirillss.relics.api.relics.abilities.stats.AbilityStatTemplate;
import it.hurts.sskirillss.relics.init.RelicsScalingModels;
import it.hurts.sskirillss.relics.items.relics.base.data.leveling.LevelingTemplate;
import it.hurts.sskirillss.relics.items.relics.base.data.loot.LootTemplate;
import it.hurts.sskirillss.relics.items.relics.base.data.loot.misc.LootEntries;
import it.hurts.sskirillss.relics.items.relics.base.data.research.ResearchTemplate;
import it.hurts.sskirillss.relics.utils.MathUtils;

/**
 * Geneticist's Loupe (head): look at a Pokémon to read its breeding value on screen, without an Analyzer. Rank 3
 * (deep_reading): its Genetic Potential and Stability too. Levels up with each Pokémon read.
 */
public class GeneticistsLoupeItem extends GeneRelicItem {

    public static final String ABILITY = "keen_eye";

    @Override
    protected String ability() {
        return ABILITY;
    }

    @Override
    public RelicTemplate constructDefaultRelicTemplate() {
        return RelicTemplate.builder()
                .abilities(AbilitiesTemplate.builder()
                        .ability(AbilityTemplate.builder(ABILITY)
                                .initialMaxLevel(10)
                                .rankModifier(3, "deep_reading")
                                .stat(AbilityStatTemplate.builder("range")
                                        .initialValue(6, 8)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 24)
                                        .formatValue(value -> (int) MathUtils.round(value, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("reading").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("pokemon_read")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Research stars: a lens with its handle
                                .research(ResearchTemplate.builder()
                                        .star(0, 11, 3).star(1, 6, 7).star(2, 6, 13).star(3, 11, 17).star(4, 16, 13).star(5, 16, 7).star(6, 17, 22)
                                        .link(0, 1).link(1, 2).link(2, 3).link(3, 4).link(4, 5).link(5, 0).link(3, 6)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.VILLAGE).entry(LootEntries.CAVE).build())
                .build();
    }
}
