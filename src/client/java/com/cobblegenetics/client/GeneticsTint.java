package com.cobblegenetics.client;

import java.util.Set;

/**
 * Colour multiplier for a Pokémon from its aspects: Shadow darkening (stronger with corruption) and mutation tints.
 * Returns null when the Pokémon is drawn normally. Pure, so it is cheap and testable.
 */
public final class GeneticsTint {

    private GeneticsTint() {
    }

    public static float[] of(Set<String> aspects) {
        if (aspects == null || aspects.isEmpty()) {
            return null;
        }
        float r = 1;
        float g = 1;
        float b = 1;
        boolean any = false;
        for (String aspect : aspects) {
            if (aspect.startsWith("cg-tint-") && aspect.length() == 14) {
                try {
                    int rgb = Integer.parseInt(aspect.substring(8), 16);
                    // Blend 65 % towards the tint so the original texture still reads through
                    r *= 1 - 0.65f * (1 - ((rgb >> 16) & 0xFF) / 255f);
                    g *= 1 - 0.65f * (1 - ((rgb >> 8) & 0xFF) / 255f);
                    b *= 1 - 0.65f * (1 - (rgb & 0xFF) / 255f);
                    any = true;
                } catch (NumberFormatException ignored) {
                    // a malformed aspect from a datapack: ignore it
                }
            }
        }
        if (aspects.contains("cg-shadow")) {
            float strength = aspects.contains("cg-shadow-fully_corrupted") ? 1f
                    : aspects.contains("cg-shadow-highly_corrupted") ? 0.85f
                    : aspects.contains("cg-shadow-aggressive") ? 0.7f
                    : aspects.contains("cg-shadow-unstable") ? 0.55f : 0.4f;
            r *= 1 - 0.55f * strength;
            g *= 1 - 0.68f * strength;
            b *= 1 - 0.38f * strength;
            any = true;
        }
        return any ? new float[]{r, g, b} : null;
    }
}
