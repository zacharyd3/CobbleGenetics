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
 * Resonance Fork (ring): affinity resonance gives the wearer's Pokémon more EXP. Rank 3 (sympathy): off-type
 * affinities resonate a third as much. Levels up each time one of their Pokémon gains EXP with resonance.
 */
public class ResonanceForkItem extends GeneRelicItem {

    public static final String ABILITY = "harmonics";

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
                                .rankModifier(3, "sympathy")
                                .stat(AbilityStatTemplate.builder("boost")
                                        .initialValue(0.25, 0.50)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 1.50)
                                        .formatValue(value -> (int) MathUtils.round(value * 100.0, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("resonant_gain").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("resonant_gains")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Research stars: a tuning fork
                                .research(ResearchTemplate.builder()
                                        .star(0, 7, 3).star(1, 7, 11).star(2, 11, 15).star(3, 15, 11).star(4, 15, 3).star(5, 11, 22)
                                        .link(0, 1).link(1, 2).link(2, 3).link(3, 4).link(2, 5)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.VILLAGE).entry(LootEntries.FOREST).build())
                .build();
    }
}
