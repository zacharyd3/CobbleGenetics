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
import net.minecraft.server.level.ServerPlayer;

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
 *   <li><b>The Genetic Incubator</b> (built in, for packs without a breeding addon): pick two compatible party
 *       Pokémon and the offspring arrives at level 1 in the party (or PC).</li>
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
        Genome child = inherit(mother, father, childTypes(event.getEgg()));
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

    private static boolean isDitto(Pokemon pokemon) {
        return pokemon.getForm().getEggGroups().contains(EggGroup.DITTO);
    }

    private static boolean isUndiscovered(Pokemon pokemon) {
        return pokemon.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED);
    }

    /**
     * Breeds with the Incubator: creates the level 1 offspring, applies inheritance and gives it to the player.
     * The caller has already checked {@link #incompatibility} and taken the cost.
     */
    public static Pokemon incubate(ServerPlayer player, Pokemon a, Pokemon b) {
        // Offspring species: the mother's line (or the non-Ditto parent), from its first stage
        Pokemon mother;
        Pokemon father;
        if (isDitto(a)) {
            mother = b;
            father = a;
        } else if (isDitto(b)) {
            mother = a;
            father = b;
        } else {
            mother = a.getGender() == Gender.FEMALE ? a : b;
            father = mother == a ? b : a;
        }
        Species species = baseSpecies(mother.getSpecies());
        PokemonProperties properties = PokemonProperties.Companion.parse(
                "species=" + species.getResourceIdentifier() + " level=1");
        Pokemon child = properties.create(player);

        // Main-series IV inheritance: three random stats take the IV of a random parent
        Random random = ThreadLocalRandom.current();
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
            Genome genome = inherit(mother, father, GeneticsService.typesOf(child));
            PokemonGenetics.setPending(child, GenomeNbt.write(genome));
        }
        GeneticsService.ensure(child, Genome.Origin.HATCHED);
        MutationDef mutation = null;
        if (GeneticsService.anyFor(MutationSource.BREEDING)) {
            mutation = GeneticsService.roll(child, MutationSource.BREEDING, GeneticsConfig.get().breedingMutationChance,
                    false, player.serverLevel(), player.blockPosition());
        }

        long now = player.serverLevel().getGameTime();
        PokemonGenetics.setLastBred(mother, now);
        PokemonGenetics.setLastBred(father, now);
        Cobblemon.INSTANCE.getStorage().getParty(player).add(child);
        countBreeding(player, mother, father);

        player.sendSystemMessage(Component.translatable("message.cobblegenetics.bred", mother.getDisplayName(false),
                father.getDisplayName(false), child.getDisplayName(false)).withStyle(ChatFormatting.GREEN));
        if (mutation != null) {
            player.sendSystemMessage(Component.translatable("message.cobblegenetics.bred_mutation")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return child;
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
    static Genome inherit(Pokemon mother, Pokemon father, List<String> childTypes) {
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
