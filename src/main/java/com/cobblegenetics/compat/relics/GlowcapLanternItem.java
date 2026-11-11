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
 * Glowcap Lantern (belt): the wearer's party develops environmental mutations more often. Rank 3 (living_light):
 * night vision while one of their glowing Pokémon is sent out. Levels up with time and each mutation it brings.
 */
public class GlowcapLanternItem extends GeneRelicItem {

    public static final String ABILITY = "lure_light";

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
                                .rankModifier(3, "living_light")
                                .stat(AbilityStatTemplate.builder("chance")
                                        .initialValue(0.25, 0.50)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 1.50)
                                        .formatValue(value -> (int) MathUtils.round(value * 100.0, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("exposure").build())
                                        .source(ExperienceSourceTemplate.builder("mutation").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("mutations_found")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Research stars: a lantern
                                .research(ResearchTemplate.builder()
                                        .star(0, 11, 2).star(1, 7, 7).star(2, 6, 15).star(3, 8, 21).star(4, 14, 21).star(5, 16, 15).star(6, 15, 7).star(7, 11, 13)
                                        .link(0, 1).link(0, 6).link(1, 2).link(2, 3).link(3, 4).link(4, 5).link(5, 6).link(1, 7).link(6, 7)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.CAVE).entry(LootEntries.SWAMP).build())
                .build();
    }
}
