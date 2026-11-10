package com.cobblegenetics.genetics;

import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.mutation.MutationDef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * The formulas of the genetics system: rolling a genome, inheritance, stability and predictions. Pure functions (no
 * Minecraft classes, randomness passed in) so they are unit-tested and easy to retune: this is the place to balance.
 */
public final class GeneticsRules {

    private GeneticsRules() {
    }

    // ------------------------------------------------------------------ rolling

    /**
     * A fresh genome for a Pokémon with no parents.
     *
     * @param speciesTypes the species' types ("fire", "flying"), used for the elemental affinity
     * @param allTypes     every type name, for off-type affinities
     */
    public static Genome roll(Random random, GeneticsConfig config, List<String> speciesTypes, List<String> allTypes,
                              Collection<TraitLocus> loci, Genome.Origin origin) {
        Genome genome = new Genome();
        genome.origin = origin;
        genome.potential = gaussian(random, config.potentialMean, config.potentialSpread);
        genome.stability = gaussian(random, config.stabilityMean, config.stabilitySpread);
        double susceptibility = gaussian(random, config.susceptibilityMean, config.susceptibilitySpread)
                + (100 - genome.stability) * config.instabilityToSusceptibility;
        genome.susceptibility = clamp(susceptibility, 0, 100);
        genome.affinity = rollAffinity(random, config, speciesTypes, allTypes);
        genome.affinityStrength = clamp(gaussian(random, 50, 20), 0, 100);
        genome.size = clamp(random.nextGaussian() * 0.4, -1, 1);
        for (TraitLocus locus : loci) {
            genome.traits.put(locus.id(), locus.randomGenotype(random));
        }
        return genome;
    }

    static String rollAffinity(Random random, GeneticsConfig config, List<String> speciesTypes, List<String> allTypes) {
        if (speciesTypes.isEmpty() || (!allTypes.isEmpty() && random.nextDouble() < config.offTypeAffinityChance)) {
            return allTypes.isEmpty() ? "normal" : allTypes.get(random.nextInt(allTypes.size()));
        }
        return speciesTypes.get(random.nextInt(speciesTypes.size()));
    }

    /** Fills in loci added by a datapack after this genome was created (and drops nothing). */
    public static boolean completeTraits(Genome genome, Collection<TraitLocus> loci, Random random) {
        boolean changed = false;
        for (TraitLocus locus : loci) {
            if (!genome.traits.containsKey(locus.id())) {
                genome.traits.put(locus.id(), locus.randomGenotype(random));
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ inheritance

    /**
     * Result of breeding: the offspring genome, which parent mutations it inherited expressed, and which recessive
     * traits/mutations emerged (expressed although neither parent expressed them).
     */
    public record BreedResult(Genome genome, List<String> inheritedMutations, List<String> emergedLatent,
                              boolean incompatible) {
    }

    /**
     * Offspring genome from two parents. The child does not copy either parent: each value is a random mix plus noise,
     * each trait locus takes one random allele from each parent, and mutations are passed on by their own chances.
     *
     * @param childTypes the offspring species' types (affinity recombination)
     * @param mutations  lookup of mutation definitions (unknown ids, e.g. from a removed datapack, are not inherited)
     */
    public static BreedResult breed(Genome mother, Genome father, Random random, GeneticsConfig config,
                                    List<String> childTypes, Collection<TraitLocus> loci,
                                    Function<String, MutationDef> mutations) {
        Genome child = new Genome();
        child.origin = Genome.Origin.BRED;
        child.generation = Math.max(mother.generation, father.generation) + 1;

        boolean incompatible = opposed(mother.affinity, father.affinity, config);
        double mix = 0.3 + random.nextDouble() * 0.4;
        double noise = config.inheritanceNoise;

        double potential = mix * mother.potential + (1 - mix) * father.potential + random.nextGaussian() * noise;
        double affinityFactor = affinityFactor(mother, father);
        if (!incompatible && !mother.affinity.equals(father.affinity)) {
            potential += config.hybridVigour * affinityFactor;
        }
        child.potential = clamp(potential, 0, 100);

        // Stability comes from the parents' effective (damaged) stability: corruption scars are partly heritable
        double parentStability = mix * (mother.stability - mother.stabilityDamage * 0.5)
                + (1 - mix) * (father.stability - father.stabilityDamage * 0.5);
        double stability = parentStability + random.nextGaussian() * noise * 0.8;
        if (incompatible) {
            stability -= config.incompatibilityPenalty * affinityFactor;
        }
        child.stability = clamp(stability, 0, 100);

        double susceptibility = mix * mother.susceptibility + (1 - mix) * father.susceptibility
                + random.nextGaussian() * noise + (100 - child.stability) * config.instabilityToSusceptibility * 0.5;
        child.susceptibility = clamp(susceptibility, 0, 100);

        if (!childTypes.isEmpty() && random.nextDouble() < config.affinityRecombinationChance) {
            child.affinity = childTypes.get(random.nextInt(childTypes.size()));
        } else {
            // The stronger affinity is the likelier to be passed on
            double total = mother.affinityStrength + father.affinityStrength;
            double motherShare = total <= 0 ? 0.5 : mother.affinityStrength / total;
            child.affinity = random.nextDouble() < motherShare ? mother.affinity : father.affinity;
        }
        child.affinityStrength = clamp((mother.affinityStrength + father.affinityStrength) / 2
                + random.nextGaussian() * noise, 0, 100);
        child.size = clamp((mother.size + father.size) / 2 + random.nextGaussian() * 0.2, -1, 1);

        // Mendel: one allele from each parent, per locus
        for (TraitLocus locus : loci) {
            Genome.Genotype m = mother.traits.get(locus.id());
            Genome.Genotype f = father.traits.get(locus.id());
            String fromMother = m == null ? locus.randomAllele(random) : (random.nextBoolean() ? m.first() : m.second());
            String fromFather = f == null ? locus.randomAllele(random) : (random.nextBoolean() ? f.first() : f.second());
            child.traits.put(locus.id(), new Genome.Genotype(fromMother, fromFather));
        }

        List<String> inherited = new ArrayList<>();
        List<String> emerged = new ArrayList<>();
        if (config.mutationsInheritable) {
            Map<String, Integer> latentCopies = new LinkedHashMap<>();
            for (Genome parent : List.of(mother, father)) {
                for (String id : parent.mutations) {
                    MutationDef def = mutations.apply(id);
                    if (def == null || !def.inheritable()) {
                        continue;
                    }
                    if (random.nextDouble() < def.inheritChance()) {
                        if (!child.mutations.contains(id)) {
                            inherited.add(id);
                        }
                        child.addMutation(id, "inherited");
                    } else if (random.nextBoolean()) {
                        latentCopies.merge(id, 1, Integer::sum);
                    }
                }
                for (String id : parent.latent) {
                    MutationDef def = mutations.apply(id);
                    if (def != null && def.inheritable() && random.nextBoolean()) {
                        latentCopies.merge(id, 1, Integer::sum);
                    }
                }
            }
            for (Map.Entry<String, Integer> entry : latentCopies.entrySet()) {
                String id = entry.getKey();
                if (child.mutations.contains(id)) {
                    continue;
                }
                if (entry.getValue() >= 2) {
                    // One copy from each parent: the recessive mutation surfaces
                    child.addMutation(id, "inherited");
                    emerged.add(id);
                } else {
                    child.latent.add(id);
                }
            }
            child.inheritedMutations.addAll(inherited);
            child.inheritedMutations.addAll(emerged);
            // Too many inherited mutations: keep the first ones, the rest stay latent
            trimMutations(child, config.maxMutations);
        }
        return new BreedResult(child, inherited, emerged, incompatible);
    }

    static void trimMutations(Genome genome, int max) {
        if (genome.mutations.size() <= max) {
            return;
        }
        List<String> extra = new ArrayList<>(genome.mutations).subList(max, genome.mutations.size());
        for (String id : List.copyOf(extra)) {
            genome.mutations.remove(id);
            genome.mutationSources.remove(id);
            genome.inheritedMutations.remove(id);
            genome.latent.add(id);
        }
    }

    public static boolean opposed(String a, String b, GeneticsConfig config) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        for (String pair : config.opposedAffinities) {
            String p = pair.toLowerCase(Locale.ROOT).trim();
            if (p.equals(x + "/" + y) || p.equals(y + "/" + x)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ derived values

    /**
     * Stability after Shadow corruption, permanent damage and mutation effects. This, not the raw value, decides
     * mutation behaviour.
     */
    public static double effectiveStability(Genome genome, double corruption, double stabilityShift,
                                            GeneticsConfig config) {
        return clamp(genome.stability - genome.stabilityDamage + stabilityShift
                - corruption * config.corruptionStabilityDrain, 0, 100);
    }

    public static double instability(double effectiveStability) {
        return 1 - effectiveStability / 100.0;
    }

    /** Final chance of a mutation roll for a pathway: susceptibility 50 = the base chance, 0 = half, 100 = 1.5x. */
    public static double mutationChance(double base, Genome genome, double instability) {
        return clamp(base * mutationFactor(genome, instability), 0, 1);
    }

    /** Minimum IV for freshly generated Pokémon (see config.potentialIvFloorMax). */
    public static int ivFloor(Genome genome, GeneticsConfig config) {
        return (int) Math.round(clamp(genome.potential, 0, 100) / 100.0 * config.potentialIvFloorMax);
    }

    /**
     * How strongly two parents' affinities act in breeding, from their average affinity strength: 0.5x at strength
     * 0, 1x at 50, 1.5x at 100. Scales both hybrid vigour (different, compatible affinities) and the incompatibility
     * penalty (opposed ones): strong affinities are a bigger reward and a bigger risk.
     */
    public static double affinityFactor(Genome a, Genome b) {
        return 0.5 + clamp((a.affinityStrength + b.affinityStrength) / 2, 0, 100) / 100.0;
    }

    /**
     * Resonance: an affinity that matches one of the Pokémon's own types makes it learn faster. EXP multiplier
     * 1 + strength / 100 * config.affinityResonanceExp (1 when the affinity is off-type).
     */
    public static double resonance(Genome genome, Collection<String> ownTypes, GeneticsConfig config) {
        if (genome.affinity == null || !ownTypes.contains(genome.affinity.toLowerCase(Locale.ROOT))) {
            return 1;
        }
        return 1 + clamp(genome.affinityStrength, 0, 100) / 100.0 * config.affinityResonanceExp;
    }

    /** IV ceiling that levelling up can raise IVs to (config.potentialTrainingChance). */
    public static int trainingCeiling(Genome genome, GeneticsConfig config) {
        return (int) Math.round(clamp(genome.potential, 0, 100) / 100.0 * config.potentialTrainingIvMax);
    }

    /** Share (0-1) of mutations that come out harmful, as MutationRoller decides it. */
    public static double harmfulShare(Genome genome, double instability, GeneticsConfig config) {
        return clamp(config.negativeMutationChance * (1 + instability) * (1 - genome.potential / 200.0), 0, 1);
    }

    /** How much more (or less) often mutation rolls succeed than the base chance (mutationChance without the base). */
    public static double mutationFactor(Genome genome, double instability) {
        return (0.5 + genome.susceptibility / 100.0) * (1 + instability * 0.5);
    }

    /**
     * One number for "how good is this Pokémon to breed from", 0-100: mostly potential (better IVs for every baby),
     * then stability (fewer harmful mutations, stable babies).
     */
    public static double breedingValue(Genome genome, double effectiveStability) {
        return clamp(0.6 * genome.potential + 0.4 * effectiveStability, 0, 100);
    }

    /** The affinities opposed to this one in config.opposedAffinities. */
    public static List<String> opposedTo(String affinity, GeneticsConfig config) {
        List<String> out = new java.util.ArrayList<>();
        String a = affinity == null ? "" : affinity.toLowerCase(Locale.ROOT);
        for (String pair : config.opposedAffinities) {
            String[] parts = pair.toLowerCase(Locale.ROOT).trim().split("/");
            if (parts.length == 2) {
                if (parts[0].equals(a) && !out.contains(parts[1])) {
                    out.add(parts[1]);
                } else if (parts[1].equals(a) && !out.contains(parts[0])) {
                    out.add(parts[0]);
                }
            }
        }
        return out;
    }

    /** Letter grade shown by the Analyzer. */
    public static String grade(double value) {
        if (value >= 90) {
            return "S";
        }
        if (value >= 75) {
            return "A";
        }
        if (value >= 55) {
            return "B";
        }
        if (value >= 35) {
            return "C";
        }
        return "D";
    }

    // ------------------------------------------------------------------ predictions (research level 4)

    public record Range(double min, double max) {
    }

    /** Chance per expressed allele (0-1), for one locus. */
    public record TraitOdds(String locus, Map<String, Double> odds) {
    }

    public record Prediction(Range potential, Range stability, boolean incompatible, List<TraitOdds> traits,
                             List<String> possibleEmergence) {
    }

    /**
     * What breeding these two would likely give. Ranges cover the parent mix plus about two standard deviations of
     * noise; trait odds are exact Punnett squares.
     */
    public static Prediction predict(Genome a, Genome b, GeneticsConfig config, Collection<TraitLocus> loci) {
        boolean incompatible = opposed(a.affinity, b.affinity, config);
        double noise = config.inheritanceNoise * 2;
        double factor = affinityFactor(a, b);
        double vigour = !incompatible && !a.affinity.equals(b.affinity) ? config.hybridVigour * factor : 0;
        double lowP = Math.min(a.potential, b.potential) * 0.7 + Math.max(a.potential, b.potential) * 0.3;
        double highP = Math.max(a.potential, b.potential) * 0.7 + Math.min(a.potential, b.potential) * 0.3;
        Range potential = new Range(clamp(lowP - noise + vigour, 0, 100), clamp(highP + noise + vigour, 0, 100));

        double sa = a.stability - a.stabilityDamage * 0.5;
        double sb = b.stability - b.stabilityDamage * 0.5;
        double penalty = incompatible ? config.incompatibilityPenalty * factor : 0;
        double lowS = Math.min(sa, sb) * 0.7 + Math.max(sa, sb) * 0.3 - penalty;
        double highS = Math.max(sa, sb) * 0.7 + Math.min(sa, sb) * 0.3 - penalty;
        Range stability = new Range(clamp(lowS - noise * 0.8, 0, 100), clamp(highS + noise * 0.8, 0, 100));

        List<TraitOdds> traits = new ArrayList<>();
        for (TraitLocus locus : loci) {
            Genome.Genotype ga = a.traits.get(locus.id());
            Genome.Genotype gb = b.traits.get(locus.id());
            if (ga == null || gb == null) {
                continue;
            }
            Map<String, Double> odds = new LinkedHashMap<>();
            for (String x : List.of(ga.first(), ga.second())) {
                for (String y : List.of(gb.first(), gb.second())) {
                    for (TraitLocus.Allele allele : locus.expressed(new Genome.Genotype(x, y))) {
                        odds.merge(allele.id(), 0.25, Double::sum);
                    }
                }
            }
            traits.add(new TraitOdds(locus.id(), odds));
        }

        List<String> emergence = new ArrayList<>();
        for (String id : a.latent) {
            if (b.latent.contains(id) || b.mutations.contains(id)) {
                emergence.add(id);
            }
        }
        for (String id : a.mutations) {
            if (b.latent.contains(id) && !emergence.contains(id)) {
                emergence.add(id);
            }
        }
        return new Prediction(potential, stability, incompatible, traits, emergence);
    }

    // ------------------------------------------------------------------ helpers

    static double gaussian(Random random, double mean, double spread) {
        return clamp(mean + random.nextGaussian() * spread, 0, 100);
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
