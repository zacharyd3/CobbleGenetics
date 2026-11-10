package com.cobblegenetics.mutation;

import com.cobblegenetics.effect.GeneticEffect;

import java.util.List;
import java.util.Set;

/**
 * One mutation, loaded from {@code data/<namespace>/cobblegenetics/mutations/<name>.json}. Adding a mutation never
 * needs code: the JSON picks a category, tier, pathways, conditions and a list of effects from the effect types in
 * {@link com.cobblegenetics.effect.EffectTypes} (which addons can extend).
 *
 * @param id            "namespace:name" (from the file location)
 * @param weight        relative chance against other mutations of the same tier
 * @param inheritChance chance an expressing parent passes it on expressed (otherwise it may pass as latent)
 * @param excludes      mutations that cannot coexist with this one (either way round)
 */
public record MutationDef(String id, String name, String description, MutationCategory category, MutationTier tier,
                          boolean negative, double weight, boolean inheritable, double inheritChance,
                          Set<MutationSource> sources, MutationConditions conditions, List<GeneticEffect> effects,
                          Set<String> excludes) {

    public boolean allows(MutationSource source) {
        return sources.contains(source);
    }
}
