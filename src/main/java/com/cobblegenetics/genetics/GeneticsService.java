package com.cobblegenetics.genetics;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.GeneticsRegistry;
import com.cobblegenetics.data.GenomeNbt;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.effect.GeneticEffect;
import com.cobblegenetics.mutation.MutationContext;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.mutation.MutationRoller;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything that reads or changes the genetics of a live Pokémon. Event hooks call into here; the formulas live in
 * {@link GeneticsRules} and {@link MutationRoller}.
 */
public final class GeneticsService {

    private static Boolean sizeModPresent;

    private GeneticsService() {
    }

    static Random random() {
        return ThreadLocalRandom.current();
    }

    // ------------------------------------------------------------------ genome lifecycle

    /**
     * Returns the Pokémon's genome, creating it if needed. Idempotent and cheap when the genome exists, so every hook
     * can call it ("lazy genetics": Pokémon from before the mod was installed get one the first time they matter).
     *
     * @param origin used only when a new genome is rolled. {@link Genome.Origin#RETROACTIVE} never changes IVs/size.
     */
    public static Genome ensure(Pokemon pokemon, Genome.Origin origin) {
        if (pokemon.getPersistentData() == null) {
            return null;
        }
        Genome genome = PokemonGenetics.genome(pokemon);
        boolean changed = false;
        if (genome == null) {
            CompoundTag pending = PokemonGenetics.takePending(pokemon);
            if (pending != null) {
                genome = GenomeNbt.read(pending);
            } else {
                genome = GeneticsRules.roll(random(), GeneticsConfig.get(), typesOf(pokemon), allTypes(),
                        GeneticsRegistry.loci(), origin);
            }
            changed = true;
        } else if (GeneticsRules.completeTraits(genome, GeneticsRegistry.loci(), random())) {
            changed = true;
        }
        if (!genome.phenotypeApplied) {
            applyPhenotype(pokemon, genome);
            changed = true;
        }
        if (changed) {
            PokemonGenetics.setGenome(pokemon, genome);
        }
        resolveRequests(pokemon, genome);
        return genome;
    }

    /** Applies cg_shadow / cg_mutation property requests now that the Pokémon is complete. */
    private static void resolveRequests(Pokemon pokemon, Genome genome) {
        for (String id : PokemonGenetics.takeMutationRequests(pokemon)) {
            MutationDef def = GeneticsRegistry.mutation(id.contains(":") ? id : CobbleGenetics.MOD_ID + ":" + id);
            if (def == null) {
                CobbleGenetics.LOGGER.warn("CobbleGenetics: cg_mutation asks for unknown mutation {}", id);
            } else {
                addMutation(pokemon, genome, def, MutationSource.COMMAND);
            }
        }
        double shadow = PokemonGenetics.takeShadowRequest(pokemon);
        if (!Double.isNaN(shadow)) {
            if (shadow > 0) {
                com.cobblegenetics.shadow.ShadowService.makeShadow(pokemon, shadow);
            }
        }
    }

    /**
     * Genetic Potential while training: per level gained, with config.potentialTrainingChance, the lowest IV below the
     * training ceiling rises by 1 (ties broken at random). Shadow Pokémon do not grow this way.
     */
    public static void train(Pokemon pokemon, int levels) {
        GeneticsConfig config = GeneticsConfig.get();
        if (config.potentialTrainingChance <= 0 || PokemonGenetics.isShadow(pokemon)) {
            return;
        }
        Genome genome = PokemonGenetics.genome(pokemon);
        if (genome == null) {
            return;
        }
        int ceiling = GeneticsRules.trainingCeiling(genome, config);
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < Math.min(levels, 100); i++) {
            if (random.nextDouble() >= config.potentialTrainingChance) {
                continue;
            }
            Stat lowest = null;
            int lowestValue = Integer.MAX_VALUE;
            int ties = 0;
            for (Stat stat : MAIN_STATS) {
                Integer iv = pokemon.getIvs().get(stat);
                int value = iv == null ? 0 : iv;
                if (value < lowestValue) {
                    lowest = stat;
                    lowestValue = value;
                    ties = 1;
                } else if (value == lowestValue && random.nextInt(++ties) == 0) {
                    lowest = stat;
                }
            }
            if (lowest == null || lowestValue >= ceiling) {
                return;
            }
            pokemon.setIV(lowest, Math.min(31, lowestValue + 1));
        }
    }

    /**
     * One-shot effects of the genome: IV floor from potential, genetic size, allele effects and the effects of
     * mutations it was born with. Only for fresh Pokémon; retroactive genomes only get marked as applied.
     */
    static void applyPhenotype(Pokemon pokemon, Genome genome) {
        genome.phenotypeApplied = true;
        if (!genome.origin.fresh()) {
            return;
        }
        GeneticsConfig config = GeneticsConfig.get();
        int floor = GeneticsRules.ivFloor(genome, config);
        if (floor > 0) {
            for (Stat stat : MAIN_STATS) {
                Integer iv = pokemon.getIvs().get(stat);
                if (iv == null || iv < floor) {
                    pokemon.setIV(stat, floor);
                }
            }
        }
        if (config.sizeVariance > 0 && !sizeModPresent()) {
            pokemon.setScaleModifier((float) (1 + genome.size * config.sizeVariance));
        }
        for (TraitLocus locus : GeneticsRegistry.loci()) {
            Genome.Genotype genotype = genome.traits.get(locus.id());
            if (genotype != null) {
                for (TraitLocus.Allele allele : locus.expressed(genotype)) {
                    allele.effects().forEach(effect -> safeAcquire(effect, pokemon));
                }
            }
        }
        for (String id : genome.mutations) {
            MutationDef def = GeneticsRegistry.mutation(id);
            if (def != null) {
                def.effects().forEach(effect -> safeAcquire(effect, pokemon));
            }
        }
    }

    private static final List<Stat> MAIN_STATS = List.of(
            com.cobblemon.mod.common.api.pokemon.stats.Stats.HP,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED);

    static boolean sizeModPresent() {
        if (sizeModPresent == null) {
            boolean found = false;
            for (String id : GeneticsConfig.get().sizeConflictMods) {
                if (ModList.get() != null && ModList.get().isLoaded(id)) {
                    found = true;
                }
            }
            sizeModPresent = found;
        }
        return sizeModPresent;
    }

    private static void safeAcquire(GeneticEffect effect, Pokemon pokemon) {
        try {
            effect.onAcquire(pokemon);
        } catch (RuntimeException e) {
            CobbleGenetics.LOGGER.warn("CobbleGenetics: effect {} failed on {}", effect.type(), pokemon.getUuid(), e);
        }
    }

    // ------------------------------------------------------------------ mutations

    /** Adds a mutation (applying its one-shot effects) and saves. Returns false if it was already there or full. */
    public static boolean addMutation(Pokemon pokemon, Genome genome, MutationDef def, MutationSource source) {
        if (genome.hasMutation(def.id())) {
            return false;
        }
        if (genome.mutations.size() >= GeneticsConfig.get().maxMutations && source != MutationSource.COMMAND) {
            // No room: it stays latent, still inheritable
            genome.latent.add(def.id());
            PokemonGenetics.setGenome(pokemon, genome);
            return false;
        }
        genome.addMutation(def.id(), source.id());
        def.effects().forEach(effect -> safeAcquire(effect, pokemon));
        PokemonGenetics.setGenome(pokemon, genome);
        return true;
    }

    /**
     * Rolls a mutation through a pathway: chance check (unless {@code guaranteed}), then MutationRoller. The location
     * is used for environmental conditions; pass null when unknown. Returns the new mutation, or null.
     */
    public static MutationDef roll(Pokemon pokemon, MutationSource source, double baseChance, boolean guaranteed,
                                   ServerLevel level, BlockPos pos) {
        GeneticsConfig config = GeneticsConfig.get();
        if (!config.mutationsEnabled) {
            return null;
        }
        Genome genome = ensure(pokemon, Genome.Origin.RETROACTIVE);
        if (genome == null) {
            return null;
        }
        ShadowData shadow = PokemonGenetics.shadow(pokemon);
        double instability = GeneticsRules.instability(effectiveStability(pokemon, genome, shadow));
        if (!guaranteed && random().nextDouble() >= GeneticsRules.mutationChance(baseChance, genome, instability)) {
            return null;
        }
        MutationContext ctx = context(pokemon, genome, shadow, source, instability, level, pos);
        List<MutationDef> eligible = MutationRoller.eligible(GeneticsRegistry.mutations(), genome, ctx);
        MutationDef def = MutationRoller.pick(eligible, genome, instability, random(), config);
        if (def == null || !addMutation(pokemon, genome, def, source)) {
            return null;
        }
        return def;
    }

    /** True if any loaded mutation could appear through this pathway at all (skips work when none can). */
    public static boolean anyFor(MutationSource source) {
        for (MutationDef def : GeneticsRegistry.mutations()) {
            if (def.allows(source)) {
                return true;
            }
        }
        return false;
    }

    public static MutationContext context(Pokemon pokemon, Genome genome, ShadowData shadow, MutationSource source,
                                          double instability, ServerLevel level, BlockPos pos) {
        Set<String> biomeKeys = new HashSet<>();
        double temperature = 0.8;
        boolean night = false;
        String weather = "clear";
        int light = -1;
        if (level != null && pos != null) {
            Holder<Biome> biome = level.getBiome(pos);
            biome.unwrapKey().ifPresent(key -> biomeKeys.add(key.location().toString()));
            biome.tags().forEach(tag -> biomeKeys.add("#" + tag.location()));
            temperature = biome.value().getBaseTemperature();
            night = level.isNight();
            weather = level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
            light = level.getMaxLocalRawBrightness(pos);
        }
        return new MutationContext(source, biomeKeys, temperature, night, weather, light,
                new HashSet<>(typesOf(pokemon)), genome.affinity, instability, new HashSet<>(genome.mutations),
                expressedAlleles(genome), shadow != null && shadow.history(),
                shadow == null ? 0 : shadow.peakCorruption, pokemon.getFriendship(), genome.generation);
    }

    // ------------------------------------------------------------------ effects

    /** All effects active on a genome: expressed trait alleles plus mutations. */
    public static List<GeneticEffect> effects(Genome genome) {
        List<GeneticEffect> result = new ArrayList<>();
        if (genome == null) {
            return result;
        }
        for (TraitLocus locus : GeneticsRegistry.loci()) {
            Genome.Genotype genotype = genome.traits.get(locus.id());
            if (genotype != null) {
                for (TraitLocus.Allele allele : locus.expressed(genotype)) {
                    result.addAll(allele.effects());
                }
            }
        }
        for (String id : genome.mutations) {
            MutationDef def = GeneticsRegistry.mutation(id);
            if (def != null) {
                result.addAll(def.effects());
            }
        }
        return result;
    }

    public static Set<String> expressedAlleles(Genome genome) {
        Set<String> result = new HashSet<>();
        for (TraitLocus locus : GeneticsRegistry.loci()) {
            Genome.Genotype genotype = genome.traits.get(locus.id());
            if (genotype != null) {
                for (TraitLocus.Allele allele : locus.expressed(genotype)) {
                    result.add(locus.id() + ":" + allele.id());
                }
            }
        }
        return result;
    }

    public static double stabilityShift(Genome genome) {
        double shift = 0;
        for (GeneticEffect effect : effects(genome)) {
            shift += effect.stabilityShift();
        }
        return shift;
    }

    public static double effectiveStability(Pokemon pokemon, Genome genome, ShadowData shadow) {
        double corruption = shadow != null && shadow.active ? shadow.corruption : 0;
        return GeneticsRules.effectiveStability(genome, corruption, stabilityShift(genome), GeneticsConfig.get());
    }

    /** Combined corruption resistance (up to 0.95; negative = vulnerable): stacks multiplicatively. */
    public static double corruptionResistance(Genome genome) {
        double remaining = 1;
        for (GeneticEffect effect : effects(genome)) {
            remaining *= 1 - effect.corruptionResistance();
        }
        return Math.min(0.95, 1 - remaining);
    }

    public static double friendshipMultiplier(Genome genome) {
        double multiplier = 1;
        for (GeneticEffect effect : effects(genome)) {
            multiplier *= effect.friendshipMultiplier();
        }
        return multiplier;
    }

    public static double purificationMultiplier(Genome genome) {
        double multiplier = 1;
        for (GeneticEffect effect : effects(genome)) {
            multiplier *= effect.purificationMultiplier();
        }
        return multiplier;
    }

    public static double regeneration(Genome genome) {
        double total = 0;
        for (GeneticEffect effect : effects(genome)) {
            total += effect.regeneration();
        }
        return Math.min(0.5, total);
    }

    public static double expMultiplier(Genome genome, double temperature) {
        double multiplier = 1;
        GeneticEffect.ExpContext context = new GeneticEffect.ExpContext(temperature, random());
        for (GeneticEffect effect : effects(genome)) {
            multiplier *= effect.expMultiplier(context);
        }
        return multiplier;
    }

    // ------------------------------------------------------------------ helpers

    public static List<String> typesOf(Pokemon pokemon) {
        List<String> types = new ArrayList<>();
        for (ElementalType type : pokemon.getTypes()) {
            types.add(type.getName().toLowerCase(java.util.Locale.ROOT));
        }
        return types;
    }

    static List<String> allTypes() {
        List<String> types = new ArrayList<>();
        for (ElementalType type : ElementalTypes.all()) {
            types.add(type.getName().toLowerCase(java.util.Locale.ROOT));
        }
        return types;
    }

    /** Where a Pokémon "is" for environmental rules: its entity if sent out, else its owner. */
    public static BlockPos positionOf(Pokemon pokemon, ServerPlayer owner) {
        PokemonEntity entity = pokemon.getEntity();
        if (entity != null) {
            return entity.blockPosition();
        }
        return owner == null ? null : owner.blockPosition();
    }
}
