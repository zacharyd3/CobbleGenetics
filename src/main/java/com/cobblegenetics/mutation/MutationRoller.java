package com.cobblegenetics.mutation;

import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.genetics.Genome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Picks which mutation a successful roll produces. Pure logic (no world access): GeneticsService decides whether a
 * roll happens at all (pathway chance x susceptibility) and builds the {@link MutationContext}.
 *
 * <p>Order of decisions:</p>
 * <ol>
 *   <li>Eligible = allows this pathway, conditions match, not already expressed, no exclusion conflict.</li>
 *   <li>Rare genetic combinations (mutations that require other mutations/traits) win outright half of the time when
 *       one is eligible: they are what experimenting players hunt for.</li>
 *   <li>Negative or beneficial: negative chance grows with instability and shrinks with genetic potential.</li>
 *   <li>Tier by the config weights, shifted towards higher tiers by instability; empty tiers fall back to the
 *       nearest tier that has candidates.</li>
 *   <li>Within the tier, by each mutation's own weight.</li>
 * </ol>
 */
public final class MutationRoller {

    private MutationRoller() {
    }

    public static List<MutationDef> eligible(Collection<MutationDef> all, Genome genome, MutationContext ctx) {
        List<MutationDef> result = new ArrayList<>();
        for (MutationDef def : all) {
            if (!def.allows(ctx.source()) || genome.hasMutation(def.id()) || def.weight() <= 0) {
                continue;
            }
            if (conflicts(def, genome, all)) {
                continue;
            }
            if (def.conditions().matches(ctx)) {
                result.add(def);
            }
        }
        return result;
    }

    /** True if def excludes an expressed mutation, or an expressed mutation excludes def. */
    public static boolean conflicts(MutationDef def, Genome genome, Collection<MutationDef> all) {
        for (String id : genome.mutations) {
            if (def.excludes().contains(id)) {
                return true;
            }
        }
        for (MutationDef other : all) {
            if (genome.hasMutation(other.id()) && other.excludes().contains(def.id())) {
                return true;
            }
        }
        return false;
    }

    public static MutationDef pick(List<MutationDef> eligible, Genome genome, double instability, Random random,
                                   GeneticsConfig config) {
        if (eligible.isEmpty()) {
            return null;
        }
        List<MutationDef> combinations = eligible.stream().filter(def -> def.conditions().isCombination()).toList();
        if (!combinations.isEmpty() && random.nextBoolean()) {
            return byWeight(combinations, random);
        }

        double negativeChance = config.negativeMutationChance * (1 + instability) * (1 - genome.potential / 200.0);
        boolean negative = random.nextDouble() < negativeChance;
        List<MutationDef> pool = eligible.stream().filter(def -> def.negative() == negative).toList();
        if (pool.isEmpty()) {
            pool = eligible;
        }

        MutationTier tier = pickTier(instability, random, config);
        List<MutationDef> inTier = nearestTier(pool, tier);
        return byWeight(inTier, random);
    }

    static MutationTier pickTier(double instability, Random random, GeneticsConfig config) {
        double[] base = {config.commonWeight, config.uncommonWeight, config.rareWeight, config.epicWeight,
                config.legendaryWeight};
        double[] weights = new double[base.length];
        double total = 0;
        for (int i = 0; i < base.length; i++) {
            // Instability multiplies the higher tiers: at 100% instability legendary weight is x3
            weights[i] = Math.max(0, base[i]) * (1 + Math.max(0, instability) * i * 0.5);
            total += weights[i];
        }
        if (total <= 0) {
            return MutationTier.COMMON;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return MutationTier.values()[i];
            }
        }
        return MutationTier.LEGENDARY;
    }

    /** Candidates of the wanted tier, or of the closest tier that has any (lower tiers first on a tie). */
    static List<MutationDef> nearestTier(List<MutationDef> pool, MutationTier wanted) {
        MutationTier[] tiers = MutationTier.values();
        for (int distance = 0; distance < tiers.length; distance++) {
            for (int sign : new int[]{-1, 1}) {
                int index = wanted.ordinal() + sign * distance;
                if (index < 0 || index >= tiers.length) {
                    continue;
                }
                MutationTier tier = tiers[index];
                List<MutationDef> found = pool.stream().filter(def -> def.tier() == tier).toList();
                if (!found.isEmpty()) {
                    return found;
                }
                if (distance == 0) {
                    break;
                }
            }
        }
        return pool;
    }

    static MutationDef byWeight(List<MutationDef> defs, Random random) {
        double total = 0;
        for (MutationDef def : defs) {
            total += def.weight();
        }
        double roll = random.nextDouble() * total;
        for (MutationDef def : defs) {
            roll -= def.weight();
            if (roll < 0) {
                return def;
            }
        }
        return defs.get(defs.size() - 1);
    }
}
