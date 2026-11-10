package com.cobblegenetics;

import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.genetics.GeneticsRules;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.genetics.TraitLocus;
import com.cobblegenetics.mutation.MutationCategory;
import com.cobblegenetics.mutation.MutationConditions;
import com.cobblegenetics.mutation.MutationContext;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.mutation.MutationRoller;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.mutation.MutationTier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure genetics rules, run outside the game. */
class GeneticsRulesTest {

    private static final GeneticsConfig CONFIG = new GeneticsConfig();

    private static final TraitLocus LUSTER = new TraitLocus("luster", "Luster", List.of(
            new TraitLocus.Allele("N", "Ordinary", "", 2, 0.9, List.of()),
            new TraitLocus.Allele("l", "Lustrous", "", 1, 0.1, List.of())));

    private static final TraitLocus TEMPERAMENT = new TraitLocus("temperament", "Temperament", List.of(
            new TraitLocus.Allele("B", "Balanced", "", 2, 0.6, List.of()),
            new TraitLocus.Allele("A", "Affectionate", "", 2, 0.3, List.of()),
            new TraitLocus.Allele("w", "Wary", "", 1, 0.1, List.of())));

    private static MutationDef mutation(String id, MutationTier tier, boolean negative, MutationConditions conditions,
                                        Set<MutationSource> sources, double inheritChance) {
        return new MutationDef(id, id, "", MutationCategory.PHYSICAL, tier, negative, 10, true, inheritChance,
                sources, conditions, List.of(), Set.of());
    }

    private static MutationContext context(MutationSource source, Set<String> mutations) {
        return new MutationContext(source, Set.of("minecraft:plains", "#minecraft:is_overworld"), 0.8, false, "clear",
                12, Set.of("fire"), "fire", 0.2, mutations, Set.of(), false, 0, 50, 0);
    }

    @Test
    void rolledGenomesStayInRange() {
        Random random = new Random(1);
        for (int i = 0; i < 2000; i++) {
            Genome genome = GeneticsRules.roll(random, CONFIG, List.of("fire"), List.of("fire", "water", "grass"),
                    List.of(LUSTER), Genome.Origin.WILD);
            assertTrue(genome.potential >= 0 && genome.potential <= 100);
            assertTrue(genome.stability >= 0 && genome.stability <= 100);
            assertTrue(genome.susceptibility >= 0 && genome.susceptibility <= 100);
            assertTrue(genome.size >= -1 && genome.size <= 1);
            assertNotNull(genome.traits.get("luster"));
        }
    }

    @Test
    void dominantAlleleIsExpressedRecessiveNeedsTwoCopies() {
        assertEquals("N", LUSTER.expressed(new Genome.Genotype("N", "l")).get(0).id());
        assertEquals("l", LUSTER.expressed(new Genome.Genotype("l", "l")).get(0).id());
        assertTrue(LUSTER.expressesRecessive(new Genome.Genotype("l", "l")));
        assertFalse(LUSTER.expressesRecessive(new Genome.Genotype("N", "l")));
        // Equal dominance: co-dominant, both expressed
        assertEquals(2, TEMPERAMENT.expressed(new Genome.Genotype("B", "A")).size());
    }

    @Test
    void twoCarriersGiveAboutAQuarterRecessiveOffspring() {
        Genome mother = new Genome();
        Genome father = new Genome();
        mother.traits.put("luster", new Genome.Genotype("N", "l"));
        father.traits.put("luster", new Genome.Genotype("l", "N"));
        Random random = new Random(7);
        int recessive = 0;
        int runs = 8000;
        for (int i = 0; i < runs; i++) {
            Genome child = GeneticsRules.breed(mother, father, random, CONFIG, List.of(), List.of(LUSTER),
                    id -> null).genome();
            if (LUSTER.expressesRecessive(child.traits.get("luster"))) {
                recessive++;
            }
        }
        double fraction = recessive / (double) runs;
        assertTrue(fraction > 0.22 && fraction < 0.28, "expected ~25 %, got " + fraction);
    }

    @Test
    void offspringDoesNotCopyAParent() {
        Genome mother = new Genome();
        mother.potential = 90;
        mother.stability = 80;
        mother.affinity = "fire";
        Genome father = new Genome();
        father.potential = 30;
        father.stability = 60;
        father.affinity = "grass";
        Random random = new Random(3);
        double total = 0;
        for (int i = 0; i < 1000; i++) {
            Genome child = GeneticsRules.breed(mother, father, random, CONFIG, List.of("fire"), List.of(),
                    id -> null).genome();
            total += child.potential;
            assertEquals(1, child.generation);
            assertEquals(Genome.Origin.BRED, child.origin);
        }
        double mean = total / 1000;
        // Between the parents (plus hybrid vigour), never a copy of either
        assertTrue(mean > 50 && mean < 70, "mean potential " + mean);
    }

    @Test
    void opposedAffinitiesLowerStability() {
        Genome a = new Genome();
        a.affinity = "fire";
        a.stability = 70;
        Genome b = new Genome();
        b.affinity = "water";
        b.stability = 70;
        a.affinityStrength = 50;
        b.affinityStrength = 50;
        assertTrue(GeneticsRules.opposed("fire", "water", CONFIG));
        assertTrue(GeneticsRules.opposed("water", "fire", CONFIG));
        assertFalse(GeneticsRules.opposed("fire", "fire", CONFIG));
        GeneticsRules.Prediction prediction = GeneticsRules.predict(a, b, CONFIG, List.of());
        assertTrue(prediction.incompatible());
        assertTrue(prediction.stability().max() < 70);
    }

    @Test
    void affinityStrengthScalesBreedingAndResonance() {
        Genome weak = new Genome();
        weak.affinity = "fire";
        weak.affinityStrength = 0;
        Genome strong = new Genome();
        strong.affinity = "fire";
        strong.affinityStrength = 100;
        assertEquals(0.5, GeneticsRules.affinityFactor(weak, weak), 1e-9);
        assertEquals(1.5, GeneticsRules.affinityFactor(strong, strong), 1e-9);
        // Resonance only with its own type, scaled by strength
        assertEquals(1 + CONFIG.affinityResonanceExp, GeneticsRules.resonance(strong, List.of("fire"), CONFIG), 1e-9);
        assertEquals(1, GeneticsRules.resonance(strong, List.of("water"), CONFIG), 1e-9);
        assertEquals(1, GeneticsRules.resonance(weak, List.of("fire"), CONFIG), 1e-9);
        // The stronger parent's affinity is passed on far more often
        Genome water = new Genome();
        water.affinity = "water";
        water.affinityStrength = 10;
        Random random = new Random(5);
        int fire = 0;
        for (int i = 0; i < 2000; i++) {
            if (GeneticsRules.breed(strong, water, random, CONFIG, List.of(), List.of(), id -> null).genome()
                    .affinity.equals("fire")) {
                fire++;
            }
        }
        assertTrue(fire > 1700, "fire " + fire);
    }

    @Test
    void latentMutationEmergesFromTwoCarriers() {
        MutationDef albinism = mutation("test:albinism", MutationTier.UNCOMMON, false, MutationConditions.NONE,
                EnumSet.allOf(MutationSource.class), 0.0);
        Map<String, MutationDef> defs = new HashMap<>();
        defs.put(albinism.id(), albinism);
        Genome a = new Genome();
        Genome b = new Genome();
        a.latent.add(albinism.id());
        b.latent.add(albinism.id());
        Random random = new Random(11);
        int emerged = 0;
        for (int i = 0; i < 4000; i++) {
            GeneticsRules.BreedResult result = GeneticsRules.breed(a, b, random, CONFIG, List.of(), List.of(),
                    defs::get);
            if (result.genome().hasMutation(albinism.id())) {
                emerged++;
                assertTrue(result.emergedLatent().contains(albinism.id()));
            }
        }
        // Each parent passes the latent copy half of the time: ~25 % of offspring express it
        double fraction = emerged / 4000.0;
        assertTrue(fraction > 0.2 && fraction < 0.3, "emergence " + fraction);
    }

    @Test
    void rollerRespectsConditionsSourcesAndExclusions() {
        MutationDef cold = mutation("test:cold", MutationTier.COMMON, false, new MutationConditions(List.of(), null,
                0.2, null, null, null, List.of(), List.of(), null, List.of(), List.of(), false, null, null, null),
                EnumSet.of(MutationSource.NATURAL), 0.3);
        MutationDef any = mutation("test:any", MutationTier.COMMON, false, MutationConditions.NONE,
                EnumSet.of(MutationSource.NATURAL), 0.3);
        MutationDef purifyOnly = mutation("test:purify", MutationTier.RARE, false, MutationConditions.NONE,
                EnumSet.of(MutationSource.PURIFICATION), 0.3);
        List<MutationDef> all = List.of(cold, any, purifyOnly);
        Genome genome = new Genome();

        List<MutationDef> eligible = MutationRoller.eligible(all, genome, context(MutationSource.NATURAL, Set.of()));
        // 0.8 temperature is too warm for "cold"; purification-only is not offered for natural spawns
        assertEquals(List.of(any), eligible);

        genome.addMutation("test:any", "natural");
        assertTrue(MutationRoller.eligible(all, genome, context(MutationSource.NATURAL, Set.of("test:any"))).isEmpty());
    }

    @Test
    void combinationNeedsItsIngredients() {
        MutationDef combo = mutation("test:combo", MutationTier.EPIC, false, new MutationConditions(List.of(), null,
                null, null, null, null, List.of(), List.of(), null, List.of("test:a", "test:b"), List.of(), false,
                null, null, null), EnumSet.of(MutationSource.BREEDING), 0.3);
        assertTrue(combo.conditions().isCombination());
        assertFalse(combo.conditions().matches(context(MutationSource.BREEDING, Set.of("test:a"))));
        assertTrue(combo.conditions().matches(context(MutationSource.BREEDING, Set.of("test:a", "test:b"))));
        Genome genome = new Genome();
        MutationDef picked = MutationRoller.pick(List.of(combo), genome, 0.2, new Random(1), CONFIG);
        assertEquals(combo, picked);
        assertNull(MutationRoller.pick(List.of(), genome, 0.2, new Random(1), CONFIG));
    }

    @Test
    void ivFloorScalesWithPotential() {
        Genome genome = new Genome();
        genome.potential = 100;
        assertEquals(CONFIG.potentialIvFloorMax, GeneticsRules.ivFloor(genome, CONFIG));
        genome.potential = 0;
        assertEquals(0, GeneticsRules.ivFloor(genome, CONFIG));
    }

    @Test
    void corruptionLowersEffectiveStability() {
        Genome genome = new Genome();
        genome.stability = 70;
        genome.stabilityDamage = 4;
        double clean = GeneticsRules.effectiveStability(genome, 0, 0, CONFIG);
        double corrupted = GeneticsRules.effectiveStability(genome, 80, 0, CONFIG);
        assertEquals(66, clean, 1e-9);
        assertTrue(corrupted < clean);
    }

    @Test
    void genotypeEncodingRoundTrips() {
        Genome.Genotype genotype = new Genome.Genotype("N", "l");
        assertEquals(genotype, Genome.Genotype.decode(genotype.encode()));
        assertNull(Genome.Genotype.decode("broken"));
    }
}
