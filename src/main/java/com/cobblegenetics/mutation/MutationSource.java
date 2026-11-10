package com.cobblegenetics.mutation;

import java.util.Locale;

/** The pathways through which a Pokémon can develop a mutation. */
public enum MutationSource {
    /** Rolled when a wild Pokémon spawns. */
    NATURAL,
    /** Rolled for offspring (separately from mutations inherited from the parents). */
    BREEDING,
    /** Rolled when a Pokémon evolves. */
    EVOLUTION,
    /** Living in (or spawning in) a matching environment: biome, temperature, weather, darkness. */
    ENVIRONMENT,
    /** Induced with an item (Mutagen). */
    ITEM,
    /** Rolled when a Shadow Pokémon is purified. */
    PURIFICATION,
    /** Rare world events (thunderstorm "genetic storms", the Dark Moon). */
    EVENT,
    /** Given by an admin command. Never rolled. */
    COMMAND;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MutationSource parse(String text) {
        for (MutationSource source : values()) {
            if (source.id().equalsIgnoreCase(text)) {
                return source;
            }
        }
        return null;
    }
}
