package com.cobblegenetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Server options, stored in {@code config/cobblegenetics.json}. The file is created with every option on first start
 * and rewritten (keeping the values already there) when a newer version adds options.
 *
 * <p>This class deliberately does not touch Minecraft classes, so the pure rule classes ({@code GeneticsRules},
 * {@code MutationRoller}) and their unit tests can use it outside the game.</p>
 */
public final class GeneticsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblegenetics");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static GeneticsConfig current = new GeneticsConfig();

    public static GeneticsConfig get() {
        return current;
    }

    /** For tests and the reload command. */
    public static void set(GeneticsConfig config) {
        current = config;
    }

    // ---------------------------------------------------------------- genetics

    /** Mean and spread (standard deviation) of freshly rolled genetic values, all on a 0-100 scale. */
    public double potentialMean = 50;
    public double potentialSpread = 15;
    public double stabilityMean = 65;
    public double stabilitySpread = 14;
    public double susceptibilityMean = 30;
    public double susceptibilitySpread = 12;
    /** How much low stability raises susceptibility when a genome is rolled (per point of missing stability). */
    public double instabilityToSusceptibility = 0.3;

    /**
     * Genetic Potential gives freshly generated Pokémon (wild spawns and hatched eggs) a floor for every IV:
     * floor = potential / 100 * this. 12 means a perfect-potential Pokémon never rolls an IV below 12. Existing Pokémon
     * that receive a genome later never have their IVs touched.
     */
    public int potentialIvFloorMax = 12;
    /** Size gene range: scale modifier = 1 + size * this (size is -1..1). 0 disables genetic size. */
    public double sizeVariance = 0.12;
    /** Chance that a rolled affinity is not one of the species' own types. */
    public double offTypeAffinityChance = 0.25;

    // ---------------------------------------------------------------- inheritance

    /** Master switch: when false, offspring get a fresh random genome instead of an inherited one. */
    public boolean geneticInheritance = true;
    /** Random noise (standard deviation) added to inherited potential / stability / susceptibility. */
    public double inheritanceNoise = 7;
    /** Bonus to offspring potential when the parents' affinities differ and are not opposed (hybrid vigour). */
    public double hybridVigour = 3;
    /** Stability lost by offspring of parents whose affinities oppose each other (see opposedAffinities). */
    public double incompatibilityPenalty = 15;
    /** Affinity pairs that are genetically incompatible. Order does not matter. */
    public List<String> opposedAffinities = new ArrayList<>(List.of(
            "fire/water", "grass/fire", "electric/ground", "dark/psychic", "dragon/fairy", "ghost/normal", "ice/fire"));
    /** Chance the offspring's affinity is recombined from its own species instead of copied from a parent. */
    public double affinityRecombinationChance = 0.15;

    /**
     * Built-in breeding with the Genetic Incubator. Cobblemon 1.7.3 has no breeding of its own; packs with a breeding
     * addon that fires Cobblemon's COLLECT_EGG event get inheritance through that addon too, and can turn this off.
     */
    public boolean builtInBreeding = true;
    /** Minimum level of both parents for the Incubator. */
    public int incubatorMinParentLevel = 15;
    /** Cooldown per parent, in minutes, before it can be bred again with the Incubator. */
    public int incubatorParentCooldownMinutes = 20;
    /**
     * Two Pokémon of different evolutionary families can breed, and their egg hatches a fusion of both (head = one
     * parent's first stage, body = the other's). Needs Fusionmon installed; without it, the main-series rules apply.
     */
    public boolean crossSpeciesFusions = true;
    /** Minutes the parents stay inside the Genetic Incubator (after both were fed) before the egg is ready. */
    public double incubatorBreedingMinutes = 5;
    /**
     * The Incubator gives a Pokémon Egg that hatches after walking, like the main series. false = when breeding
     * finishes, the offspring goes straight to the party with its parents.
     */
    public boolean incubatorEggs = true;
    /**
     * Blocks to walk per egg cycle of the species (Cobblemon's eggCycles: 20 for most starters, 40 for Dratini...).
     * 40 means a 20-cycle egg needs 800 blocks, about three minutes of walking.
     */
    public int eggBlocksPerCycle = 40;
    /** Abilities in the party that halve the distance, as in the main series (Showdown ids). */
    public List<String> eggSpeedAbilities = new ArrayList<>(List.of("flamebody", "magmaarmor", "steamengine"));

    /**
     * Mods that already randomise the size (scale modifier) of Pokémon. While one is installed, the genetic size gene
     * is still rolled and inherited (and shown in the Analyzer) but does not overwrite the scale; size mutations still
     * apply on top of it.
     */
    public List<String> sizeConflictMods = new ArrayList<>(List.of("cobblemonsizevariation", "sizevariation"));

    // ---------------------------------------------------------------- mutations

    /** Master switches. */
    public boolean mutationsEnabled = true;
    public boolean mutationsInheritable = true;
    /** Allow mutations that change the Tera Type (the only type change that is clean in Cobblemon 1.7.3). */
    public boolean mutationsCanChangeTeraType = true;
    /** Allow mutations that unlock the species' hidden ability. */
    public boolean mutationsCanModifyAbilities = true;
    /** Most expressed mutations one Pokémon can carry. */
    public int maxMutations = 4;

    /** Base chance (0-1) that a mutation roll happens for each pathway, before susceptibility. */
    public double naturalMutationChance = 0.03;
    public double breedingMutationChance = 0.06;
    public double evolutionMutationChance = 0.08;
    public double environmentSpawnMutationChance = 0.02;
    /** Per party Pokémon, per exposure check (see exposureIntervalTicks), in a matching environment. */
    public double environmentExposureMutationChance = 0.002;
    public double purificationMutationChance = 0.35;
    /** Multiplier on natural spawn mutation chances during a thunderstorm ("genetic storm" world event). */
    public double stormMutationMultiplier = 2.0;
    /** Stability lost when a Mutagen is used. */
    public double mutagenStabilityCost = 10;

    /** Tier weights when a roll succeeds. Low stability shifts weight towards the higher tiers. */
    public double commonWeight = 60;
    public double uncommonWeight = 25;
    public double rareWeight = 10;
    public double epicWeight = 4;
    public double legendaryWeight = 1;
    /** Base chance a mutation roll yields a negative mutation; scaled up by instability, down by potential. */
    public double negativeMutationChance = 0.2;

    // ---------------------------------------------------------------- shadow

    public boolean shadowEnabled = true;
    /** Chance a natural wild spawn is a Shadow Pokémon. */
    public double shadowSpawnChance = 0.004;
    /** Multiplier at night, and again on a new moon (the "Dark Moon" world event). */
    public double shadowNightMultiplier = 2.0;
    public double shadowNewMoonMultiplier = 2.5;
    /** Legendary/mythical species are never made Shadow by natural spawns (commands and datapacks still can). */
    public boolean shadowSpawnsExcludeLegendaries = true;
    public List<String> shadowSpeciesBlacklist = new ArrayList<>();
    /** Corruption rolled for a natural Shadow spawn. */
    public int shadowMinCorruption = 45;
    public int shadowMaxCorruption = 95;
    /** Catch rate multiplier for wild Shadow Pokémon. */
    public double shadowCatchRateMultiplier = 0.8;
    /** Friendship a Shadow Pokémon has when caught. */
    public int shadowCaptureFriendship = 0;
    /** Shadow Pokémon cannot evolve until purified (as in the Orre games). */
    public boolean shadowBlocksEvolution = true;
    /** Corruption gained when a Shadow Pokémon is on the losing side of a battle. */
    public double corruptionOnDefeat = 2;
    /** Each point of corruption lowers effective genetic stability by this much. */
    public double corruptionStabilityDrain = 0.3;
    /** Permanent stability damage per 20 corruption reached (applied once per threshold). */
    public double corruptionGeneticDamage = 2;

    // ---------------------------------------------------------------- purification

    /** Ticks between passive purification / environmental exposure checks for online players (1200 = 1 minute). */
    public int exposureIntervalTicks = 1200;
    /** Corruption removed per check while the Shadow Pokémon is in the party / sent out beside you. */
    public double purifyPartyPerCheck = 0.4;
    public double purifySentOutPerCheck = 0.8;
    /** Multiplier for passive purification near a Purification Chamber. */
    public double chamberMultiplier = 3.0;
    public int chamberRadius = 6;
    public double purifyPerBattleWon = 3;
    public double purifyPerLevelUp = 1;
    /** Corruption removed per point of friendship gained. */
    public double purifyPerFriendship = 0.15;
    /** Friendship gains of Shadow Pokémon are multiplied by (1 - corruption / this). 0 disables the penalty. */
    public double shadowFriendshipDamping = 130;
    public double scentPurification = 8;
    public double incensePurification = 20;
    /** Purification Incense only works below this corruption (it is the finishing step, not a shortcut). */
    public double incenseMaxCorruption = 60;
    /** Rewards when purification completes. */
    public int purifiedFriendshipBonus = 70;
    public double purifiedStabilityBonus = 10;

    // ---------------------------------------------------------------- research

    /** Research levels are gained from milestones (true) and/or Analyzer Upgrade items (always). */
    public boolean researchMilestones = true;
    public int level2Analyses = 5;
    public int level3Analyses = 15;
    public int level3Mutations = 1;
    public int level4Offspring = 2;
    public int level5Purified = 1;

    /** Potential considered "above average" for advancements and quests. */
    public double highPotentialThreshold = 70;

    // ---------------------------------------------------------------- load / save

    public static void load(Path file) {
        GeneticsConfig config = new GeneticsConfig();
        boolean rewrite = true;
        if (Files.exists(file)) {
            try {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                GeneticsConfig read = GSON.fromJson(json, GeneticsConfig.class);
                if (read != null) {
                    config = read;
                    config.sanitize();
                }
                // Rewrite only when the file is missing options (a newer version added some)
                JsonObject full = GSON.toJsonTree(config).getAsJsonObject();
                rewrite = !json.keySet().containsAll(full.keySet());
            } catch (IOException | RuntimeException e) {
                LOGGER.error("CobbleGenetics: could not read {}, using defaults (the file is left untouched)", file, e);
                current = config;
                return;
            }
        }
        current = config;
        if (rewrite) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(config), StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("CobbleGenetics: could not write {}", file, e);
            }
        }
    }

    /** Keeps values inside sensible ranges, so a typo cannot crash spawning or breeding. */
    void sanitize() {
        potentialIvFloorMax = Math.max(0, Math.min(31, potentialIvFloorMax));
        maxMutations = Math.max(0, maxMutations);
        shadowMinCorruption = clampInt(shadowMinCorruption, 1, 100);
        shadowMaxCorruption = clampInt(shadowMaxCorruption, shadowMinCorruption, 100);
        exposureIntervalTicks = Math.max(200, exposureIntervalTicks);
        chamberRadius = Math.max(1, Math.min(32, chamberRadius));
        if (opposedAffinities == null) {
            opposedAffinities = new ArrayList<>();
        }
        if (sizeConflictMods == null) {
            sizeConflictMods = new ArrayList<>();
        }
        incubatorMinParentLevel = clampInt(incubatorMinParentLevel, 1, 100);
        incubatorParentCooldownMinutes = Math.max(0, incubatorParentCooldownMinutes);
        eggBlocksPerCycle = Math.max(1, eggBlocksPerCycle);
        incubatorBreedingMinutes = Math.max(0.05, incubatorBreedingMinutes);
        if (eggSpeedAbilities == null) {
            eggSpeedAbilities = new ArrayList<>();
        }
        if (shadowSpeciesBlacklist == null) {
            shadowSpeciesBlacklist = new ArrayList<>();
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
