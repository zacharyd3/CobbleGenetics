package com.cobblegenetics.breeding;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.advancement.GeneticsTrigger;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.GeneticsProperties;
import com.cobblegenetics.data.GeneticsRegistry;
import com.cobblegenetics.data.GenomeNbt;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.GeneticsRules;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.research.Research;
import com.cobblegenetics.research.ResearchData;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.pokemon.CollectEggEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.evolution.PreEvolution;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Genetic inheritance. Cobblemon 1.7.3 has no breeding of its own: it only fires COLLECT_EGG / HATCH_EGG for breeding
 * addons. CobbleGenetics therefore supports two paths that share the same inheritance code:
 * <ol>
 *   <li><b>Breeding addons</b> that fire {@code COLLECT_EGG}: the offspring genome is computed from both parents and
 *       written into the egg's properties ({@code cg_genome=...}), so it survives until the egg hatches.</li>
 *   <li><b>The Genetic Incubator</b> block (built in, for packs without a breeding addon): two compatible party
 *       Pokémon stay inside it for a while, then return with a Pokémon Egg that hatches after walking.</li>
 * </ol>
 * Compatibility follows the main series: shared egg group and opposite genders, or Ditto with anything breedable;
 * the "Undiscovered" egg group cannot breed.
 */
public final class BreedingService {

    private BreedingService() {
    }

    // ------------------------------------------------------------------ addon path

    /** COLLECT_EGG: attach the inherited genome to the egg. */
    public static void onCollectEgg(CollectEggEvent event) {
        if (!GeneticsConfig.get().geneticInheritance) {
            return;
        }
        Pokemon mother = event.getFemaleParent();
        Pokemon father = event.getMaleParent();
        if (mother == null || father == null) {
            return;
        }
        Genome child = inherit(mother.getOwnerPlayer(), mother, father, childTypes(event.getEgg()));
        PokemonProperties egg = event.getEgg();
        List<CustomPokemonProperty> properties = new ArrayList<>(egg.getCustomProperties());
        properties.removeIf(p -> p instanceof GeneticsProperties.GenomeProperty);
        properties.add(new GeneticsProperties.GenomeProperty(GeneticsProperties.encode(child)));
        egg.setCustomProperties(properties);
        countBreeding(event.getPlayer(), mother, father);
    }

    private static List<String> childTypes(PokemonProperties egg) {
        List<String> types = new ArrayList<>();
        String speciesName = egg.getSpecies();
        if (speciesName == null) {
            return types;
        }
        Species species = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.getByName(
                speciesName.contains(":") ? speciesName.substring(speciesName.indexOf(':') + 1) : speciesName);
        if (species != null) {
            for (ElementalType type : species.getTypes()) {
                types.add(type.getName().toLowerCase(Locale.ROOT));
            }
        }
        return types;
    }

    // ------------------------------------------------------------------ incubator path

    /** Why two Pokémon cannot breed, or null if they can. */
    public static Component incompatibility(Pokemon a, Pokemon b, long gameTime) {
        GeneticsConfig config = GeneticsConfig.get();
        if (a.getUuid().equals(b.getUuid())) {
            return Component.translatable("breeding.cobblegenetics.same");
        }
        if (PokemonGenetics.isShadow(a) || PokemonGenetics.isShadow(b)) {
            return Component.translatable("breeding.cobblegenetics.shadow");
        }
        if (a.getLevel() < config.incubatorMinParentLevel || b.getLevel() < config.incubatorMinParentLevel) {
            return Component.translatable("breeding.cobblegenetics.level", config.incubatorMinParentLevel);
        }
        long cooldown = config.incubatorParentCooldownMinutes * 1200L;
        for (Pokemon parent : List.of(a, b)) {
            long last = PokemonGenetics.lastBred(parent);
            if (last != Long.MIN_VALUE && gameTime - last < cooldown) {
                long minutes = (cooldown - (gameTime - last)) / 1200 + 1;
                return Component.translatable("breeding.cobblegenetics.cooldown", parent.getDisplayName(false), minutes);
            }
        }
        if (isUndiscovered(a) || isUndiscovered(b)) {
            return Component.translatable("breeding.cobblegenetics.undiscovered");
        }
        boolean dittoA = isDitto(a);
        boolean dittoB = isDitto(b);
        if (dittoA && dittoB) {
            return Component.translatable("breeding.cobblegenetics.two_ditto");
        }
        if (dittoA || dittoB) {
            return null;
        }
        if (isCrossSpecies(a, b)) {
            // Different families make a Fusionmon fusion baby: no egg group or gender rules, like fusing
            return null;
        }
        if (Collections.disjoint(a.getForm().getEggGroups(), b.getForm().getEggGroups())) {
            return Component.translatable("breeding.cobblegenetics.egg_groups");
        }
        boolean opposite = (a.getGender() == Gender.MALE && b.getGender() == Gender.FEMALE)
                || (a.getGender() == Gender.FEMALE && b.getGender() == Gender.MALE);
        if (!opposite) {
            return Component.translatable("breeding.cobblegenetics.genders");
        }
        return null;
    }

    /**
     * True when the pair would make a fusion baby: two different evolutionary families (neither a Ditto), with
     * Fusionmon installed and crossSpeciesFusions on. Same-family pairs always breed by the main-series rules.
     */
    public static boolean isCrossSpecies(Pokemon a, Pokemon b) {
        return crossSpeciesFusions() && !isDitto(a) && !isDitto(b)
                && baseSpecies(a.getSpecies()) != baseSpecies(b.getSpecies());
    }

    /** Fusion babies need Fusionmon: its "fusion_body" Cobblemon property does the fusing, so there is no code link. */
    public static boolean crossSpeciesFusions() {
        return GeneticsConfig.get().crossSpeciesFusions && net.neoforged.fml.ModList.get() != null
                && net.neoforged.fml.ModList.get().isLoaded("fusionmon");
    }

    private static boolean isDitto(Pokemon pokemon) {
        return pokemon.getForm().getEggGroups().contains(EggGroup.DITTO);
    }

    private static boolean isUndiscovered(Pokemon pokemon) {
        return pokemon.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED);
    }

    /** The pair as (mother, father): the mother's line, or the non-Ditto parent, decides the offspring species. */
    public static Pokemon[] order(Pokemon a, Pokemon b) {
        if (isDitto(a)) {
            return new Pokemon[]{b, a};
        }
        if (isDitto(b)) {
            return new Pokemon[]{a, b};
        }
        return a.getGender() == Gender.FEMALE ? new Pokemon[]{a, b} : new Pokemon[]{b, a};
    }

    /** The finished breeding: offspring Pokémon and whether it was born with a mutation. */
    public record Offspring(Pokemon child, Pokemon mother, Pokemon father, MutationDef mutation, boolean fusion) {
    }

    /**
     * Creates the level 1 offspring of two compatible Pokémon, with inheritance (IVs and genome) and a breeding
     * mutation roll in the surroundings of {@code pos}. Updates both parents' genomes (offspring count) and cooldown;
     * the caller decides where the parents and the offspring (or its egg) go. {@code owner} may be offline (null).
     */
    public static Offspring breed(ServerPlayer owner, ServerLevel level, BlockPos pos, Pokemon a, Pokemon b) {
        Random random = ThreadLocalRandom.current();
        boolean fusion = isCrossSpecies(a, b);
        Pokemon mother;
        Pokemon father;
        PokemonProperties properties;
        if (fusion) {
            // Fusion baby: a random parent's first stage is the head, the other's the body. Fusionmon reads its own
            // "fusion_body" property and fuses the Pokémon when it is created
            boolean firstIsHead = random.nextBoolean();
            mother = firstIsHead ? a : b;
            father = firstIsHead ? b : a;
            properties = PokemonProperties.Companion.parse("species=" + baseSpecies(mother.getSpecies())
                    .getResourceIdentifier() + " level=1 fusion_body=" + baseSpecies(father.getSpecies())
                    .getResourceIdentifier());
        } else {
            Pokemon[] pair = order(a, b);
            mother = pair[0];
            father = pair[1];
            properties = PokemonProperties.Companion.parse(
                    "species=" + baseSpecies(mother.getSpecies()).getResourceIdentifier() + " level=1");
        }
        Pokemon child = owner != null ? properties.create(owner) : properties.create();

        // Main-series IV inheritance: three random stats take the IV of a random parent
        List<Stat> stats = new ArrayList<>(MAIN_STATS);
        Collections.shuffle(stats, random);
        for (Stat stat : stats.subList(0, 3)) {
            Pokemon from = random.nextBoolean() ? mother : father;
            Integer iv = from.getIvs().get(stat);
            if (iv != null) {
                child.setIV(stat, iv);
            }
        }

        if (GeneticsConfig.get().geneticInheritance) {
            Genome genome = inherit(owner, mother, father, GeneticsService.typesOf(child));
            PokemonGenetics.setPending(child, GenomeNbt.write(genome));
        }
        GeneticsService.ensure(child, Genome.Origin.HATCHED);
        MutationDef mutation = null;
        if (GeneticsService.anyFor(MutationSource.BREEDING)) {
            mutation = GeneticsService.roll(child, MutationSource.BREEDING, GeneticsConfig.get().breedingMutationChance,
                    false, level, pos);
        }

        long now = level.getGameTime();
        PokemonGenetics.setLastBred(mother, now);
        PokemonGenetics.setLastBred(father, now);
        countBreeding(owner, mother, father);
        return new Offspring(child, mother, father, mutation, fusion);
    }

    static Species baseSpecies(Species species) {
        Species current = species;
        for (int guard = 0; guard < 10; guard++) {
            PreEvolution pre = current.getPreEvolution();
            if (pre == null || pre.getSpecies() == null) {
                break;
            }
            current = pre.getSpecies();
        }
        return current;
    }

    private static final List<Stat> MAIN_STATS = List.of(
            com.cobblemon.mod.common.api.pokemon.stats.Stats.HP,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE,
            com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED);

    // ------------------------------------------------------------------ shared

    /** The offspring genome. Ensures both parents have one first (lazy genetics). */
    static Genome inherit(ServerPlayer owner, Pokemon mother, Pokemon father, List<String> childTypes) {
        Genome m = GeneticsService.ensure(mother, Genome.Origin.RETROACTIVE);
        Genome f = GeneticsService.ensure(father, Genome.Origin.RETROACTIVE);
        GeneticsRules.BreedResult result = GeneticsRules.breed(m, f, ThreadLocalRandom.current(), GeneticsConfig.get(),
                childTypes, GeneticsRegistry.loci(), GeneticsRegistry::mutation);
        Genome child = result.genome();
        child.parents.add(mother.getSpecies().getResourceIdentifier().toString());
        child.parents.add(father.getSpecies().getResourceIdentifier().toString());
        // Purified parents pass on a steadier genome
        for (Pokemon parent : List.of(mother, father)) {
            var shadow = PokemonGenetics.shadow(parent);
            if (shadow != null && shadow.purified) {
                child.stability = Math.min(100, child.stability + 2.5);
            }
        }
        m.offspring++;
        f.offspring++;
        PokemonGenetics.setGenome(mother, m);
        PokemonGenetics.setGenome(father, f);
        if (result.incompatible()) {
            CobbleGenetics.LOGGER.debug("CobbleGenetics: incompatible affinities {} x {}", m.affinity, f.affinity);
        }
        // Optional relics (Heirloom Locket): extra potential, half the opposed-affinity penalty
        var hooks = com.cobblegenetics.compat.RelicHooks.get();
        if (owner != null) {
            child.potential = Math.min(100, child.potential + Math.max(0, hooks.breedingPotential(owner)));
            if (result.incompatible() && hooks.breedingHarmony(owner)) {
                child.stability = Math.min(100, child.stability + GeneticsConfig.get().incompatibilityPenalty
                        * GeneticsRules.affinityFactor(m, f) / 2);
            }
            hooks.bred(owner);
        }
        return child;
    }

    private static void countBreeding(ServerPlayer player, Pokemon mother, Pokemon father) {
        if (player == null) {
            return;
        }
        ResearchData research = Research.of(player);
        research.bred++;
        Research.save(player, research);
        GeneticsTrigger.trigger(player, GeneticsTrigger.Action.BREED, research.bred);
    }
}
