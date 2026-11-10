package com.cobblegenetics.shadow;

/**
 * Shadow state of one Pokémon, stored next to its genome in persistentData. A purified Pokémon keeps this record
 * (active = false, purified = true): its history matters for purification-only mutations and for breeding.
 */
public final class ShadowData {

    public boolean active;
    /** 0-100. */
    public double corruption;
    /** Highest corruption ever reached (purification-exclusive mutations look at it). */
    public double peakCorruption;
    /** Corruption when this Pokémon became Shadow, for purification progress (0 % .. 100 %). */
    public double startingCorruption;
    public boolean purified;
    /** Total corruption removed by purification, per activity: shown at research level 5. */
    public double fromTime;
    public double fromBattles;
    public double fromFriendship;
    public double fromItems;
    public double fromChamber;
    /** Highest 20-point corruption threshold whose genetic damage was already applied (0-5). */
    public int damageSteps;

    public ShadowState state() {
        return ShadowState.of(corruption);
    }

    /** 0-1 purification progress since the Pokémon became Shadow. */
    public double progress() {
        if (!active) {
            return purified ? 1 : 0;
        }
        if (startingCorruption <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, 1 - corruption / startingCorruption));
    }

    public boolean history() {
        return active || purified;
    }
}
