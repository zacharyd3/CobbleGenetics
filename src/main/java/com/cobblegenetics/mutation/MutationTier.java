package com.cobblegenetics.mutation;

import java.util.Locale;

/** Rarity tiers. Their weights live in the config; low genetic stability shifts rolls towards higher tiers. */
public enum MutationTier {
    COMMON(0xFFB0B0B0),
    UNCOMMON(0xFF55D66B),
    RARE(0xFF4FA3FF),
    EPIC(0xFFB46CFF),
    LEGENDARY(0xFFFFB82E);

    /** ARGB colour used by the Analyzer and tooltips. */
    public final int color;

    MutationTier(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MutationTier parse(String text) {
        for (MutationTier tier : values()) {
            if (tier.id().equalsIgnoreCase(text)) {
                return tier;
            }
        }
        return null;
    }
}
