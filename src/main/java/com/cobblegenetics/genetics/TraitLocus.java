package com.cobblegenetics.genetics;

import com.cobblegenetics.effect.GeneticEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * An inheritable trait: a locus with several alleles. Loaded from datapacks
 * ({@code data/<namespace>/cobblegenetics/traits/<locus>.json}, see docs/data-format.md).
 *
 * <p>Expression follows simple Mendelian rules: the allele with the highest {@code dominance} is expressed; alleles
 * with equal dominance are co-dominant (both expressed). A recessive allele (lower dominance) is only expressed when the
 * Pokémon carries two copies of it, which is how rare traits can "skip" generations and reappear.</p>
 */
public record TraitLocus(String id, String name, List<Allele> alleles) {

    public record Allele(String id, String name, String description, int dominance, double frequency,
                         List<GeneticEffect> effects) {
        /** A rare allele: the Analyzer highlights it and research counts it as a rare inherited trait. */
        public boolean rare() {
            return frequency <= 0.15;
        }
    }

    public Allele allele(String alleleId) {
        for (Allele allele : alleles) {
            if (allele.id().equals(alleleId)) {
                return allele;
            }
        }
        return null;
    }

    /** Picks one allele from the wild population, by frequency. */
    public String randomAllele(Random random) {
        double total = 0;
        for (Allele allele : alleles) {
            total += Math.max(0, allele.frequency());
        }
        if (total <= 0) {
            return alleles.get(0).id();
        }
        double roll = random.nextDouble() * total;
        for (Allele allele : alleles) {
            roll -= Math.max(0, allele.frequency());
            if (roll < 0) {
                return allele.id();
            }
        }
        return alleles.get(alleles.size() - 1).id();
    }

    public Genome.Genotype randomGenotype(Random random) {
        return new Genome.Genotype(randomAllele(random), randomAllele(random));
    }

    /** The alleles this genotype expresses (one, or two when co-dominant). Unknown alleles are ignored. */
    public List<Allele> expressed(Genome.Genotype genotype) {
        List<Allele> result = new ArrayList<>();
        Allele a = allele(genotype.first());
        Allele b = allele(genotype.second());
        if (a == null && b == null) {
            return result;
        }
        if (a == null || b == null) {
            result.add(a == null ? b : a);
            return result;
        }
        if (a.dominance() > b.dominance()) {
            result.add(a);
        } else if (b.dominance() > a.dominance()) {
            result.add(b);
        } else {
            result.add(a);
            if (!a.id().equals(b.id())) {
                result.add(b);
            }
        }
        return result;
    }

    /**
     * True when this genotype expresses an allele that is recessive to some other allele of the locus, which needs two
     * copies: the "rare recessive trait emerged" case.
     */
    public boolean expressesRecessive(Genome.Genotype genotype) {
        if (!genotype.homozygous()) {
            return false;
        }
        Allele allele = allele(genotype.first());
        if (allele == null) {
            return false;
        }
        for (Allele other : alleles) {
            if (other.dominance() > allele.dominance()) {
                return true;
            }
        }
        return false;
    }
}
