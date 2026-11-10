package com.cobblegenetics.mutation;

import java.util.List;
import java.util.Set;

/**
 * When a mutation may appear. Every field is optional (null / empty = no restriction). Read from the "conditions"
 * object of a mutation JSON; see docs/data-format.md for the field names.
 */
public record MutationConditions(List<String> biomes, Double minTemperature, Double maxTemperature, String time,
                                 String weather, Integer maxLight, List<String> types, List<String> affinities,
                                 Double minInstability, List<String> requiresMutations, List<String> requiresTraits,
                                 boolean requiresShadowHistory, Double minPeakCorruption, Integer minFriendship,
                                 Integer minGeneration) {

    public static final MutationConditions NONE = new MutationConditions(List.of(), null, null, null, null, null,
            List.of(), List.of(), null, List.of(), List.of(), false, null, null, null);

    /** True when this mutation depends on other mutations or traits: a "rare genetic combination". */
    public boolean isCombination() {
        return !requiresMutations.isEmpty() || !requiresTraits.isEmpty();
    }

    /** True when the conditions look at the surroundings (these mutations can come from environmental exposure). */
    public boolean isEnvironmental() {
        return !biomes.isEmpty() || minTemperature != null || maxTemperature != null || time != null
                || weather != null || maxLight != null;
    }

    public boolean matches(MutationContext ctx) {
        if (!biomes.isEmpty() && biomes.stream().noneMatch(ctx.biomeKeys()::contains)) {
            return false;
        }
        if (minTemperature != null && ctx.temperature() < minTemperature) {
            return false;
        }
        if (maxTemperature != null && ctx.temperature() > maxTemperature) {
            return false;
        }
        if (time != null && !time.equals("any") && (time.equals("night") != ctx.night())) {
            return false;
        }
        if (weather != null && !weather.equals("any") && !weatherMatches(weather, ctx.weather())) {
            return false;
        }
        if (maxLight != null && (ctx.light() < 0 || ctx.light() > maxLight)) {
            return false;
        }
        if (!types.isEmpty() && types.stream().noneMatch(ctx.speciesTypes()::contains)) {
            return false;
        }
        if (!affinities.isEmpty() && !affinities.contains(ctx.affinity())) {
            return false;
        }
        if (minInstability != null && ctx.instability() < minInstability) {
            return false;
        }
        if (!ctx.mutations().containsAll(requiresMutations)) {
            return false;
        }
        if (!ctx.expressedAlleles().containsAll(requiresTraits)) {
            return false;
        }
        if (requiresShadowHistory && !ctx.shadowHistory()) {
            return false;
        }
        if (minPeakCorruption != null && ctx.peakCorruption() < minPeakCorruption) {
            return false;
        }
        if (minFriendship != null && ctx.friendship() < minFriendship) {
            return false;
        }
        return minGeneration == null || ctx.generation() >= minGeneration;
    }

    /** "rain" also accepts a thunderstorm (it rains during one). */
    private static boolean weatherMatches(String wanted, String actual) {
        return wanted.equals(actual) || (wanted.equals("rain") && actual.equals("thunder"));
    }

    /** How many of the combination requirements are met: the research level 5 "combination hints". */
    public int metRequirements(Set<String> mutations, Set<String> alleles) {
        int met = 0;
        for (String id : requiresMutations) {
            if (mutations.contains(id)) {
                met++;
            }
        }
        for (String id : requiresTraits) {
            if (alleles.contains(id)) {
                met++;
            }
        }
        return met;
    }

    public int totalRequirements() {
        return requiresMutations.size() + requiresTraits.size();
    }
}
