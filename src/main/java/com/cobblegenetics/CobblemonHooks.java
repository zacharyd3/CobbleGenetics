package com.cobblegenetics;

import com.cobblegenetics.advancement.GeneticsTrigger;
import com.cobblegenetics.breeding.BreedingService;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.GeneticsAspects;
import com.cobblegenetics.data.GeneticsProperties;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.shadow.ShadowService;
import com.cobblegenetics.shadow.ShadowSpawning;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.aspect.AspectProvider;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Every Cobblemon event CobbleGenetics listens to. All of them are event-driven (nothing scans loaded Pokémon), and
 * each listener is wrapped so that a failure is logged once and never breaks Cobblemon or another addon.
 */
public final class CobblemonHooks {

    private CobblemonHooks() {
    }

    /** Called on the main thread in common setup (Cobblemon's registries are plain lists). */
    public static void register() {
        AspectProvider.Companion.register(new GeneticsAspects());
        GeneticsProperties.register();
        GeneticsConfig config = GeneticsConfig.get();

        // Wild spawns: genome (fresh), natural / environmental / storm mutations, Shadow chance
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.LOW, safe("spawn", event -> {
            if (event.isCanceled()) {
                return;
            }
            PokemonEntity entity = event.getEntity();
            Pokemon pokemon = entity.getPokemon();
            if (!pokemon.isWild()) {
                return;
            }
            ServerLevel level = event.getSpawnablePosition().getWorld();
            var pos = event.getSpawnablePosition().getPosition();
            GeneticsService.ensure(pokemon, Genome.Origin.WILD);
            double storm = level.isThundering() ? GeneticsConfig.get().stormMutationMultiplier : 1;
            if (GeneticsService.roll(pokemon, level.isThundering() ? MutationSource.EVENT : MutationSource.NATURAL,
                    GeneticsConfig.get().naturalMutationChance * storm, false, level, pos) == null) {
                GeneticsService.roll(pokemon, MutationSource.ENVIRONMENT,
                        GeneticsConfig.get().environmentSpawnMutationChance, false, level, pos);
            }
            ShadowSpawning.maybeShadow(pokemon, level, pos);
        }));

        // Catching: lazy genome; Shadow Pokémon arrive distrustful
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, safe("capture", event -> {
            Pokemon pokemon = event.getPokemon();
            GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
            if (PokemonGenetics.isShadow(pokemon)) {
                pokemon.setFriendship(GeneticsConfig.get().shadowCaptureFriendship, true);
                GeneticsTrigger.trigger(event.getPlayer(), GeneticsTrigger.Action.SHADOW_CAPTURE, 1);
            }
        }));

        CobblemonEvents.POKEMON_CATCH_RATE.subscribe(Priority.NORMAL, safe("catch rate", event -> {
            if (PokemonGenetics.isShadow(event.getPokemonEntity().getPokemon())) {
                event.setCatchRate((float) (event.getCatchRate() * GeneticsConfig.get().shadowCatchRateMultiplier));
            }
        }));

        // Any Pokémon a player gains (starters, gifts, trades, hatched eggs with a pending inherited genome)
        CobblemonEvents.POKEMON_GAINED.subscribe(Priority.LOW, safe("gained",
                event -> GeneticsService.ensure(event.getPokemon(), Genome.Origin.RETROACTIVE)));

        // Breeding addons: inheritance through the egg's properties; eggs without parents roll a fresh genome
        CobblemonEvents.COLLECT_EGG.subscribe(Priority.LOW, safe("collect egg", event -> {
            if (!event.isCanceled()) {
                BreedingService.onCollectEgg(event);
            }
        }));
        CobblemonEvents.HATCH_EGG_POST.subscribe(Priority.HIGH, safe("hatch",
                event -> GeneticsService.ensure(event.getPokemon(), Genome.Origin.HATCHED)));

        // Evolution: Shadow Pokémon cannot evolve (config); evolving can trigger a mutation
        CobblemonEvents.EVOLUTION_TESTED.subscribe(Priority.NORMAL, safe("evolution test", event -> {
            if (GeneticsConfig.get().shadowBlocksEvolution && PokemonGenetics.isShadow(event.getPokemon())) {
                event.setResult(false);
            }
        }));
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL, safe("evolution", event -> {
            Pokemon pokemon = event.getPokemon();
            ServerPlayer owner = pokemon.getOwnerPlayer();
            GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
            if (GeneticsService.roll(pokemon, MutationSource.EVOLUTION, GeneticsConfig.get().evolutionMutationChance,
                    false, owner == null ? null : owner.serverLevel(), GeneticsService.positionOf(pokemon, owner)) != null
                    && owner != null) {
                owner.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.cobblegenetics.evolution_mutation", pokemon.getDisplayName(false))
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            }
        }));

        // Friendship: genetic multipliers, Shadow damping, and friendship as a purification activity
        CobblemonEvents.FRIENDSHIP_UPDATED.subscribe(Priority.NORMAL, safe("friendship", event -> {
            Pokemon pokemon = event.getPokemon();
            int gain = event.getNewFriendship() - pokemon.getFriendship();
            if (gain <= 0) {
                return;
            }
            Genome genome = PokemonGenetics.genome(pokemon);
            double multiplier = genome == null ? 1 : GeneticsService.friendshipMultiplier(genome);
            int scaled = (int) Math.round(gain * multiplier);
            if (PokemonGenetics.isShadow(pokemon)) {
                ShadowService.purify(pokemon, gain * GeneticsConfig.get().purifyPerFriendship,
                        ShadowService.Activity.FRIENDSHIP, pokemon.getOwnerPlayer());
                if (PokemonGenetics.isShadow(pokemon)) {
                    scaled = ShadowService.dampFriendship(pokemon, scaled);
                }
            }
            if (scaled != gain) {
                event.setNewFriendship(Math.max(pokemon.getFriendship(), Math.min(255, pokemon.getFriendship() + scaled)));
            }
        }));

        // EXP: genetic multipliers (climate affinities, quick metabolism, erratic metabolism)
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL, safe("experience", event -> {
            Pokemon pokemon = event.getPokemon();
            Genome genome = PokemonGenetics.genome(pokemon);
            if (genome == null) {
                return;
            }
            ServerPlayer owner = pokemon.getOwnerPlayer();
            double temperature = 0.8;
            var pos = GeneticsService.positionOf(pokemon, owner);
            if (owner != null && pos != null) {
                temperature = owner.serverLevel().getBiome(pos).value().getBaseTemperature();
            }
            double multiplier = GeneticsService.expMultiplier(genome, temperature);
            if (multiplier != 1) {
                event.setExperience(Math.max(1, (int) Math.round(event.getExperience() * multiplier)));
            }
        }));

        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL, safe("level up", event -> {
            Pokemon pokemon = event.getPokemon();
            if (PokemonGenetics.isShadow(pokemon)) {
                ShadowService.purify(pokemon, GeneticsConfig.get().purifyPerLevelUp
                        * Math.max(1, event.getNewLevel() - event.getOldLevel()), ShadowService.Activity.LEVEL,
                        pokemon.getOwnerPlayer());
            }
        }));

        // Battles: Shadow encounters, purification by winning, corruption by losing, regeneration
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, safe("battle start", event -> {
            boolean shadowPresent = false;
            for (BattleActor actor : event.getBattle().getActors()) {
                for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                    Pokemon pokemon = battlePokemon.getOriginalPokemon();
                    if (pokemon.isWild() && PokemonGenetics.isShadow(pokemon)) {
                        shadowPresent = true;
                    }
                }
            }
            if (shadowPresent) {
                for (ServerPlayer player : event.getBattle().getPlayers()) {
                    GeneticsTrigger.trigger(player, GeneticsTrigger.Action.SHADOW_ENCOUNTER, 1);
                }
            }
        }));
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, safe("battle victory", event -> {
            for (BattleActor actor : event.getWinners()) {
                ServerPlayer player = playerOf(actor);
                if (player == null) {
                    continue;
                }
                for (Pokemon pokemon : pokemonOf(actor)) {
                    if (PokemonGenetics.isShadow(pokemon)) {
                        ShadowService.purify(pokemon, GeneticsConfig.get().purifyPerBattleWon,
                                ShadowService.Activity.BATTLE, player);
                    }
                    Genome genome = PokemonGenetics.genome(pokemon);
                    double regeneration = genome == null ? 0 : GeneticsService.regeneration(genome);
                    if (regeneration > 0 && pokemon.getCurrentHealth() > 0) {
                        int heal = (int) Math.ceil(pokemon.getMaxHealth() * regeneration);
                        pokemon.setCurrentHealth(Math.min(pokemon.getMaxHealth(), pokemon.getCurrentHealth() + heal));
                    }
                }
            }
            for (BattleActor actor : event.getLosers()) {
                ServerPlayer player = playerOf(actor);
                if (player == null) {
                    continue;
                }
                for (Pokemon pokemon : pokemonOf(actor)) {
                    if (PokemonGenetics.isShadow(pokemon)) {
                        ShadowService.addCorruption(pokemon, GeneticsConfig.get().corruptionOnDefeat, player);
                    }
                }
            }
        }));

        CobbleGenetics.LOGGER.info("CobbleGenetics: hooked into Cobblemon (mutations {}, shadow {}, built-in breeding {})",
                config.mutationsEnabled, config.shadowEnabled, config.builtInBreeding);
    }

    private static ServerPlayer playerOf(BattleActor actor) {
        for (UUID id : actor.getPlayerUUIDs()) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                return server.getPlayerList().getPlayer(id);
            }
        }
        return null;
    }

    private static List<Pokemon> pokemonOf(BattleActor actor) {
        List<Pokemon> result = new ArrayList<>();
        for (BattlePokemon battlePokemon : actor.getPokemonList()) {
            result.add(battlePokemon.getOriginalPokemon());
        }
        return result;
    }

    /** Wraps a listener so an exception is logged (once per hook) instead of propagating into Cobblemon. */
    private static <T> Consumer<T> safe(String name, Consumer<T> listener) {
        return event -> {
            try {
                listener.accept(event);
            } catch (RuntimeException | LinkageError e) {
                CobbleGenetics.failed("the " + name + " hook", e);
            }
        };
    }
}
