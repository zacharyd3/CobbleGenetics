package com.cobblegenetics.shadow;

import java.util.Locale;

/** The corruption scale, as named in the Analyzer and in messages. */
public enum ShadowState {
    DORMANT(0, 0xFF8E7CC3),
    UNSTABLE(21, 0xFF7B5EA7),
    AGGRESSIVE(41, 0xFF6A3D9A),
    HIGHLY_CORRUPTED(61, 0xFF552C85),
    FULLY_CORRUPTED(81, 0xFF3B1366);

    public final int from;
    public final int color;

    ShadowState(int from, int color) {
        this.from = from;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ShadowState of(double corruption) {
        ShadowState result = DORMANT;
        for (ShadowState state : values()) {
            if (corruption >= state.from) {
                result = state;
            }
        }
        return result;
    }
}
