package com.cobblegenetics.shadow;

import com.cobblegenetics.config.GeneticsConfig;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Natural Shadow spawns. Rare by design (config shadowSpawnChance, default 0.4 %), more frequent at night and much
 * more on a new moon (the "Dark Moon" world event). Special structures, NPCs, bosses and events use the
 * {@code cg_shadow} property instead, and the Shadow Beacon guarantees an encounter for the questline.
 */
public final class ShadowSpawning {

    private static final Set<String> NEVER_SHADOW = Set.of("legendary", "mythical", "ultra_beast", "paradox",
            "restricted");

    private ShadowSpawning() {
    }

    public static void maybeShadow(Pokemon pokemon, ServerLevel level, BlockPos pos) {
        GeneticsConfig config = GeneticsConfig.get();
        if (!config.shadowEnabled || config.shadowSpawnChance <= 0) {
            return;
        }
        String speciesId = pokemon.getSpecies().getResourceIdentifier().toString();
        if (config.shadowSpeciesBlacklist.contains(speciesId)
                || config.shadowSpeciesBlacklist.contains(pokemon.getSpecies().getResourceIdentifier().getPath())) {
            return;
        }
        if (config.shadowSpawnsExcludeLegendaries
                && pokemon.getSpecies().getLabels().stream().anyMatch(NEVER_SHADOW::contains)) {
            return;
        }
        double chance = config.shadowSpawnChance;
        if (level.isNight()) {
            chance *= config.shadowNightMultiplier;
            if (level.getMoonPhase() == 4) {
                chance *= config.shadowNewMoonMultiplier;
            }
        }
        if (level.random.nextDouble() >= chance) {
            return;
        }
        int corruption = config.shadowMinCorruption
                + level.random.nextInt(config.shadowMaxCorruption - config.shadowMinCorruption + 1);
        ShadowService.makeShadow(pokemon, corruption);
    }
}
