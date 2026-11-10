package com.cobblegenetics.mutation;

import java.util.Locale;

/** What a mutation changes. Negative mutations also have a category; "negative" is a flag on the definition. */
public enum MutationCategory {
    PHYSICAL, ELEMENTAL, BIOLOGICAL, ABILITY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MutationCategory parse(String text) {
        for (MutationCategory category : values()) {
            if (category.id().equalsIgnoreCase(text)) {
                return category;
            }
        }
        return null;
    }
}
