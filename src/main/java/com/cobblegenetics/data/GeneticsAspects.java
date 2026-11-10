package com.cobblegenetics.data;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.HashSet;
import java.util.Set;

/**
 * Aspects are Cobblemon's per-Pokémon tags that the server syncs to every client (entity, party, PC, battles) and
 * that resolvers use to pick models and textures. CobbleGenetics uses them to tell clients what to draw:
 * <ul>
 *   <li>{@code cg-shadow}, {@code cg-shadow-<state>}: Shadow Pokémon (dark tint and aura scaled by state)</li>
 *   <li>{@code cg-purified}: purified Pokémon (faint sparkles)</li>
 *   <li>{@code cg-mutation-<name>}: one per expressed mutation, for resource packs</li>
 *   <li>{@code cg-tint-rrggbb}, {@code cg-glow} and custom aspects from mutation/trait effects</li>
 * </ul>
 * A resource pack can give e.g. albino Pokémon a real texture with a resolver variation
 * {@code {"aspects": ["cg-mutation-albinism"], "texture": ...}}: see docs/resource-packs.md.
 */
public final class GeneticsAspects implements AspectProvider {

    @Override
    public Set<String> provide(Pokemon pokemon) {
        Set<String> aspects = new HashSet<>();
        try {
            // Called whenever Cobblemon recalculates aspects: reads only, never creates data
            Genome genome = PokemonGenetics.genome(pokemon);
            if (genome != null) {
                for (String id : genome.mutations) {
                    MutationDef def = GeneticsRegistry.mutation(id);
                    if (def != null) {
                        aspects.add("cg-mutation-" + id.substring(id.indexOf(':') + 1));
                    }
                }
                GeneticsService.effects(genome).forEach(effect -> effect.aspects(aspects));
            }
            ShadowData shadow = PokemonGenetics.shadow(pokemon);
            if (shadow != null) {
                if (shadow.active) {
                    aspects.add("cg-shadow");
                    aspects.add("cg-shadow-" + shadow.state().id());
                } else if (shadow.purified) {
                    aspects.add("cg-purified");
                }
            }
        } catch (RuntimeException e) {
            // Aspects are computed while loading parties and PCs: never let a bad entry stop that
            CobbleGenetics.LOGGER.warn("CobbleGenetics: could not compute aspects for {}", pokemon.getUuid(), e);
        }
        return aspects;
    }

    @Override
    public Set<String> provide(PokemonProperties properties) {
        return new HashSet<>();
    }
}
