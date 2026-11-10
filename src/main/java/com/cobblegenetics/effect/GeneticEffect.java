package com.cobblegenetics.effect;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;

import java.util.Random;
import java.util.Set;

/**
 * One effect of a mutation or of an expressed trait allele. Effects come in two kinds, and an effect type may use both:
 * <ul>
 *   <li><b>One-shot</b> ({@link #onAcquire}): changes stored Cobblemon data once, when the mutation/allele is gained
 *       (IVs, scale, Tera Type, ability). Cobblemon then saves, syncs and battles with it like any other value.</li>
 *   <li><b>Passive</b>: numbers that CobbleGenetics queries when an event happens (EXP gained, friendship changed,
 *       corruption, after a battle). Nothing runs every tick.</li>
 * </ul>
 * New types are registered in {@link EffectTypes}; mutation JSON then refers to them by name.
 */
public interface GeneticEffect {

    /** Context for EXP effects that depend on where the Pokémon is. */
    record ExpContext(double temperature, Random random) {
    }

    String type();

    /** One-shot change, applied when the owning mutation or allele is acquired by a Pokémon. */
    default void onAcquire(Pokemon pokemon) {
    }

    default double expMultiplier(ExpContext context) {
        return 1;
    }

    default double friendshipMultiplier() {
        return 1;
    }

    /** 0-1: fraction of incoming corruption that is resisted. Values from several effects stack multiplicatively. */
    default double corruptionResistance() {
        return 0;
    }

    /** Added to genetic stability (can be negative). */
    default double stabilityShift() {
        return 0;
    }

    /** Fraction of max HP restored after each won battle. */
    default double regeneration() {
        return 0;
    }

    default double purificationMultiplier() {
        return 1;
    }

    /** Aspects this effect adds to the Pokémon (synced to clients: visuals and resource packs). */
    default void aspects(Set<String> out) {
    }

    /** Short line for the Analyzer at research level 4. */
    Component describe();
}
