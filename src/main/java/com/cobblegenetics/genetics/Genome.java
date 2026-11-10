package com.cobblegenetics.genetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The genetic profile of one individual Pokémon. Plain data with no Minecraft classes: {@link GenomeNbt} stores it in
 * the Pokémon's Cobblemon {@code persistentData}, which Cobblemon saves with the Pokémon itself (party, PC, entity),
 * so it follows the Pokémon through storage, trades, evolution and server restarts.
 *
 * <p>All "quality" values use a 0-100 scale so they read naturally in the Analyzer and in config files.</p>
 */
public final class Genome {

    /** How the genome came to exist. Decides whether one-shot effects (IV floor, size) may touch the Pokémon. */
    public enum Origin {
        /** Rolled when a wild Pokémon spawned. */
        WILD,
        /** Inherited from two parents (Incubator or a breeding addon). */
        BRED,
        /** Rolled for an egg/hatch that had no parent information. */
        HATCHED,
        /**
         * Rolled for a Pokémon that existed before CobbleGenetics (or came from somewhere we could not see: starters,
         * gifts, other mods). Its IVs and size are never changed: only mutations acquired later apply effects.
         */
        RETROACTIVE;

        public boolean fresh() {
            return this != RETROACTIVE;
        }
    }

    /** One locus: two alleles, one from each parent. Order does not matter for expression. */
    public record Genotype(String first, String second) {
        public boolean homozygous() {
            return first.equals(second);
        }

        public boolean has(String allele) {
            return first.equals(allele) || second.equals(allele);
        }

        public String encode() {
            return first + "/" + second;
        }

        public static Genotype decode(String text) {
            int slash = text.indexOf('/');
            if (slash <= 0 || slash == text.length() - 1) {
                return null;
            }
            return new Genotype(text.substring(0, slash), text.substring(slash + 1));
        }
    }

    public double potential;
    public double stability;
    public double susceptibility;
    /** Permanent stability lost to Shadow corruption (purification repairs part of it). */
    public double stabilityDamage;

    /** Elemental affinity: a Cobblemon type name ("fire"), plus how strongly it is expressed. */
    public String affinity = "normal";
    public double affinityStrength;

    /** Size gene, -1 (small) .. 1 (large). */
    public double size;

    public final Map<String, Genotype> traits = new LinkedHashMap<>();
    /** Expressed mutations (ids like "cobblegenetics:albinism"), in the order they were acquired. */
    public final Set<String> mutations = new LinkedHashSet<>();
    /** Pathway each expressed mutation came from ("natural", "breeding"...): shown by the Analyzer. */
    public final Map<String, String> mutationSources = new LinkedHashMap<>();
    /** Latent mutations: carried, not expressed. Two carrier parents can produce an offspring that expresses it. */
    public final Set<String> latent = new LinkedHashSet<>();

    public int generation;
    public Origin origin = Origin.RETROACTIVE;
    /** True once the one-shot effects of the genome itself (IV floor, size) were applied (or skipped for good). */
    public boolean phenotypeApplied;
    /** How many offspring this Pokémon has produced. */
    public int offspring;
    /** Species ids of the parents, for the Analyzer (empty for non-bred Pokémon). */
    public final List<String> parents = new ArrayList<>();
    /** Mutations inherited from parents (subset of mutations): "inherited mutation" research. */
    public final Set<String> inheritedMutations = new LinkedHashSet<>();

    public boolean hasMutation(String id) {
        return mutations.contains(id);
    }

    public void addMutation(String id, String source) {
        if (mutations.add(id)) {
            mutationSources.put(id, source);
        }
        latent.remove(id);
    }

    public Genome copy() {
        Genome copy = new Genome();
        copy.potential = potential;
        copy.stability = stability;
        copy.susceptibility = susceptibility;
        copy.stabilityDamage = stabilityDamage;
        copy.affinity = affinity;
        copy.affinityStrength = affinityStrength;
        copy.size = size;
        copy.traits.putAll(traits);
        copy.mutations.addAll(mutations);
        copy.mutationSources.putAll(mutationSources);
        copy.latent.addAll(latent);
        copy.generation = generation;
        copy.origin = origin;
        copy.phenotypeApplied = phenotypeApplied;
        copy.offspring = offspring;
        copy.parents.addAll(parents);
        copy.inheritedMutations.addAll(inheritedMutations);
        return copy;
    }
}
