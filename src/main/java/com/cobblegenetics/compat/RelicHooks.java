package com.cobblegenetics.compat;

import net.minecraft.server.level.ServerPlayer;

/**
 * Where optional integrations change genetics. The core mod calls these at the right moments; without the integration
 * (no Relics installed) they are the neutral defaults below. compat/relics replaces {@link #hooks} at startup, so the
 * core never touches a Relics class. Every argument player may be null (owner offline): implementations must handle
 * it, and must not throw (they catch their own errors).
 */
public final class RelicHooks {

    public interface Hooks {
        /** Day Care Aide speed multiplier for an Incubator whose owner is this player. */
        default double aideSpeed(ServerPlayer owner) {
            return 1;
        }

        /** A Day Care Aide hatched an Egg for this player. */
        default void aideHatched(ServerPlayer owner) {
        }

        /** Multiplier on the blocks walked that count for Eggs this player carries. */
        default double carriedEggSteps(ServerPlayer player) {
            return 1;
        }

        /** An Egg hatched from this player's inventory. */
        default void walkedHatch(ServerPlayer player) {
        }

        /** Added to the chance (0-1) that a level up raises an IV through Genetic Potential. */
        default double trainingChance(ServerPlayer owner) {
            return 0;
        }

        /** Added to the training IV ceiling. */
        default int trainingCeiling(ServerPlayer owner) {
            return 0;
        }

        /** Training raised an IV of one of this player's Pokémon. */
        default void trained(ServerPlayer owner) {
        }

        /** Multiplier on the resonance EXP bonus (the part above 1). */
        default double resonanceBoost(ServerPlayer owner) {
            return 1;
        }

        /** Share (0-1) of the resonance an off-type affinity still gets. */
        default double offTypeResonance(ServerPlayer owner) {
            return 0;
        }

        /** One of this player's Pokémon gained EXP with resonance. */
        default void resonated(ServerPlayer owner) {
        }

        /** Potential added to offspring bred by this player. */
        default double breedingPotential(ServerPlayer owner) {
            return 0;
        }

        /** Whether this player's offspring lose only half the opposed-affinity stability penalty. */
        default boolean breedingHarmony(ServerPlayer owner) {
            return false;
        }

        /** This player bred an offspring. */
        default void bred(ServerPlayer owner) {
        }

        /** Multiplier on the environmental mutation chance of this player's party. */
        default double environmentChance(ServerPlayer player) {
            return 1;
        }

        /** The periodic environment check ran for this player (with a party to check). */
        default void environmentCheck(ServerPlayer player) {
        }

        /** One of this player's Pokémon developed an environmental mutation. */
        default void environmentMutation(ServerPlayer player) {
        }
    }

    private static final Hooks NONE = new Hooks() {
    };

    private static volatile Hooks hooks = NONE;

    private RelicHooks() {
    }

    public static Hooks get() {
        return hooks;
    }

    public static void set(Hooks newHooks) {
        hooks = newHooks == null ? NONE : newHooks;
    }
}
