package com.cobblegenetics.mutation;

import java.util.Set;

/**
 * Everything a mutation condition can look at, gathered once per roll by GeneticsService (from the world and the
 * Pokémon). Kept as plain values so conditions and the roller can be tested outside the game.
 *
 * @param biomeKeys    the biome id and the ids of every biome tag it is in, tags written as "#namespace:path"
 * @param weather      "clear", "rain" or "thunder"
 * @param light        block light + sky light at the Pokémon (0-15), or -1 when unknown
 * @param instability  1 - effective stability / 100 (0 = rock solid, 1 = chaotic)
 * @param expressedAlleles "locus:allele" for every expressed allele
 */
public record MutationContext(MutationSource source, Set<String> biomeKeys, double temperature, boolean night,
                              String weather, int light, Set<String> speciesTypes, String affinity,
                              double instability, Set<String> mutations, Set<String> expressedAlleles,
                              boolean shadowHistory, double peakCorruption, int friendship, int generation) {
}
