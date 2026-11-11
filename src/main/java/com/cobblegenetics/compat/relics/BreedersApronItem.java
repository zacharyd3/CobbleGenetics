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
 * Breeder's Apron (body): the wearer's Day Care Aides work faster. Rank 3 (pocket_warmth): Eggs the wearer carries
 * count extra blocks walked. Levels up when Eggs hatch, by an aide or by walking.
 */
public class BreedersApronItem extends GeneRelicItem {

    public static final String ABILITY = "nurture";

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
                                .rankModifier(3, "pocket_warmth")
                                .stat(AbilityStatTemplate.builder("speed")
                                        .initialValue(0.25, 0.50)
                                        .targetValue(RelicsScalingModels.ADDITIVE.get(), 1.50)
                                        .formatValue(value -> (int) MathUtils.round(value * 100.0, 0))
                                        .build())
                                .experienceSources(ExperienceSourcesTemplate.builder()
                                        .source(ExperienceSourceTemplate.builder("aide_hatch").build())
                                        .source(ExperienceSourceTemplate.builder("egg_hatched").build())
                                        .build())
                                .statistic(AbilityStatisticTemplate.builder()
                                        .metric(AbilityMetricTemplate.builder("eggs_hatched")
                                                .formatValue(value -> String.valueOf(value.intValue())).build())
                                        .build())
                                // Research stars: an egg
                                .research(ResearchTemplate.builder()
                                        .star(0, 11, 3).star(1, 6, 8).star(2, 5, 15).star(3, 8, 21).star(4, 14, 21).star(5, 17, 15).star(6, 16, 8)
                                        .link(0, 1).link(1, 2).link(2, 3).link(3, 4).link(4, 5).link(5, 6).link(6, 0)
                                        .build())
                                .build())
                        .build())
                .leveling(LevelingTemplate.builder().initialCost(100.0).step(100.0).build())
                .loot(LootTemplate.builder().entry(LootEntries.VILLAGE).entry(LootEntries.PLAINS).build())
                .build();
    }
}
