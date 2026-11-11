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
 * Training Weights (feet): the wearer's Pokémon raise IVs through Genetic Potential more often when they level up.
 * Rank 3 (beyond_limits): the training ceiling is 3 higher. Levels up with each IV raised.
 */
public class TrainingWeightsItem extends GeneRelicItem {

    public static final String ABILITY = "conditioning";

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
                                .rankModifier(3, "beyond_limits")
                                .stat(AbilityStatTemplate.builder("chance")
                                        .initialValue(0.05, 0.10)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 0.30)
                                        .formatValue(value -> (int) MathUtils.round(value * 100.0, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("iv_raised").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("ivs_raised")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Research stars: a dumbbell
                                .research(ResearchTemplate.builder()
                                        .star(0, 5, 6).star(1, 5, 16).star(2, 8, 11).star(3, 14, 11).star(4, 17, 6).star(5, 17, 16)
                                        .link(0, 1).link(0, 2).link(1, 2).link(2, 3).link(3, 4).link(3, 5).link(4, 5)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.MOUNTAIN).entry(LootEntries.MINESHAFT).build())
                .build();
    }
}
