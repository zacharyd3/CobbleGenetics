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
 * Heirloom Locket (back): offspring the wearer breeds get extra Genetic Potential. Rank 3 (harmony): opposed
 * affinities cost them only half the stability. Levels up with each offspring bred.
 */
public class HeirloomLocketItem extends GeneRelicItem {

    public static final String ABILITY = "legacy";

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
                                .rankModifier(3, "harmony")
                                .stat(AbilityStatTemplate.builder("potential")
                                        .initialValue(1, 2)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 6)
                                        .formatValue(value -> (int) MathUtils.round(value, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("breed").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("offspring")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Research stars: a locket on its chain
                                .research(ResearchTemplate.builder()
                                        .star(0, 11, 2).star(1, 7, 8).star(2, 6, 14).star(3, 11, 20).star(4, 16, 14).star(5, 15, 8)
                                        .link(0, 1).link(0, 5).link(1, 2).link(2, 3).link(3, 4).link(4, 5)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.VILLAGE).entry(LootEntries.TAIGA).build())
                .build();
    }
}
