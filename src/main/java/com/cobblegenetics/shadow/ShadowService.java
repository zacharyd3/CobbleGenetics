package com.cobblegenetics.shadow;

import com.cobblegenetics.advancement.GeneticsTrigger;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.genetics.GeneticsRules;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.research.Research;
import com.cobblegenetics.research.ResearchData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Shadow corruption and purification. Purification is gradual: every activity removes a little corruption, the
 * player gets feedback at each 10 % of progress and at each state change, and the Pokémon is purified at 0 %.
 */
public final class ShadowService {

    /** What removed the corruption: tracked separately for the research level 5 breakdown. */
    public enum Activity {
        TIME, BATTLE, FRIENDSHIP, LEVEL, ITEM, CHAMBER
    }

    private ShadowService() {
    }

    /** Turns a Pokémon into a Shadow Pokémon (or changes the corruption of one). */
    public static void makeShadow(Pokemon pokemon, double corruption) {
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        ShadowData data = PokemonGenetics.shadow(pokemon);
        if (data == null) {
            data = new ShadowData();
        }
        data.active = true;
        data.purified = false;
        data.corruption = GeneticsRules.clamp(corruption, 1, 100);
        data.startingCorruption = data.corruption;
        data.peakCorruption = Math.max(data.peakCorruption, data.corruption);
        applyDamage(pokemon, genome, data);
        PokemonGenetics.setShadow(pokemon, data);
    }

    /** Adds corruption to an active Shadow Pokémon, reduced by its genetic corruption resistance. */
    public static void addCorruption(Pokemon pokemon, double amount, ServerPlayer owner) {
        ShadowData data = PokemonGenetics.shadow(pokemon);
        if (data == null || !data.active || amount <= 0) {
            return;
        }
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        double resisted = amount * (1 - GeneticsService.corruptionResistance(genome));
        ShadowState before = data.state();
        data.corruption = Math.min(100, data.corruption + resisted);
        data.peakCorruption = Math.max(data.peakCorruption, data.corruption);
        // Corruption above the starting point extends the road to purification
        data.startingCorruption = Math.max(data.startingCorruption, data.corruption);
        applyDamage(pokemon, genome, data);
        PokemonGenetics.setShadow(pokemon, data);
        if (owner != null && data.state() != before) {
            owner.sendSystemMessage(Component.translatable("message.cobblegenetics.corruption_worse",
                    pokemon.getDisplayName(false), stateName(data.state())).withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (owner != null) {
            GeneticsTrigger.trigger(owner, GeneticsTrigger.Action.CORRUPTION, (int) Math.round(data.corruption));
        }
    }

    /**
     * Permanent genetic damage: each 20 points of corruption reached costs some stability, once. Purification repairs
     * half of it, the Gene Stabilizer more.
     */
    static void applyDamage(Pokemon pokemon, Genome genome, ShadowData data) {
        if (genome == null) {
            return;
        }
        int steps = (int) Math.floor(data.peakCorruption / 20.0);
        if (steps > data.damageSteps) {
            genome.stabilityDamage += (steps - data.damageSteps) * GeneticsConfig.get().corruptionGeneticDamage;
            data.damageSteps = steps;
            PokemonGenetics.setGenome(pokemon, genome);
        }
    }

    /**
     * Removes corruption. {@code amount} is before the Pokémon's genetic purification multiplier. Returns the
     * corruption actually removed.
     */
    public static double purify(Pokemon pokemon, double amount, Activity activity, ServerPlayer owner) {
        ShadowData data = PokemonGenetics.shadow(pokemon);
        if (data == null || !data.active || amount <= 0) {
            return 0;
        }
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        double effective = amount * GeneticsService.purificationMultiplier(genome);
        double before = data.corruption;
        ShadowState stateBefore = data.state();
        boolean firstTime = data.fromTime + data.fromBattles + data.fromFriendship + data.fromItems
                + data.fromChamber <= 0;
        data.corruption = Math.max(0, data.corruption - effective);
        double removed = before - data.corruption;
        switch (activity) {
            case TIME, LEVEL -> data.fromTime += removed;
            case BATTLE -> data.fromBattles += removed;
            case FRIENDSHIP -> data.fromFriendship += removed;
            case ITEM -> data.fromItems += removed;
            case CHAMBER -> data.fromChamber += removed;
        }
        if (data.corruption <= 0.0001) {
            PokemonGenetics.setShadow(pokemon, data);
            complete(pokemon, owner);
            return removed;
        }
        PokemonGenetics.setShadow(pokemon, data);

        if (owner != null) {
            if (removed > 0) {
                GeneticsTrigger.trigger(owner, GeneticsTrigger.Action.PURIFY_ACTIVITY, 1, -1,
                        activity.name().toLowerCase(java.util.Locale.ROOT));
            }
            if (firstTime && removed > 0) {
                GeneticsTrigger.trigger(owner, GeneticsTrigger.Action.PURIFICATION_START, 1);
            }
            GeneticsTrigger.trigger(owner, GeneticsTrigger.Action.CORRUPTION, (int) Math.round(data.corruption));
            int tenthBefore = (int) Math.floor(before / 10);
            int tenthAfter = (int) Math.floor(data.corruption / 10);
            if (data.state() != stateBefore) {
                owner.sendSystemMessage(Component.translatable("message.cobblegenetics.corruption_better",
                        pokemon.getDisplayName(false), stateName(data.state())).withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (tenthAfter < tenthBefore) {
                owner.displayClientMessage(Component.translatable("message.cobblegenetics.purify_progress",
                        pokemon.getDisplayName(false), Math.round(data.progress() * 100)), true);
            }
        }
        return removed;
    }

    /** The Pokémon's heart opens: rewards and a chance of a purification-exclusive mutation. */
    static void complete(Pokemon pokemon, ServerPlayer owner) {
        GeneticsConfig config = GeneticsConfig.get();
        ShadowData data = PokemonGenetics.shadow(pokemon);
        data.active = false;
        data.purified = true;
        data.corruption = 0;
        PokemonGenetics.setShadow(pokemon, data);

        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        genome.stability = Math.min(100, genome.stability + config.purifiedStabilityBonus);
        genome.stabilityDamage = genome.stabilityDamage * 0.5;
        PokemonGenetics.setGenome(pokemon, genome);
        pokemon.setFriendship(Math.min(255, pokemon.getFriendship() + config.purifiedFriendshipBonus), true);

        MutationDef mutation = null;
        if (GeneticsService.anyFor(MutationSource.PURIFICATION)) {
            mutation = GeneticsService.roll(pokemon, MutationSource.PURIFICATION, config.purificationMutationChance,
                    false, owner == null ? null : owner.serverLevel(),
                    owner == null ? null : GeneticsService.positionOf(pokemon, owner));
        }

        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.cobblegenetics.purified",
                    pokemon.getDisplayName(false)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            if (mutation != null) {
                owner.sendSystemMessage(Component.translatable("message.cobblegenetics.purified_mutation",
                        pokemon.getDisplayName(false), Component.translatableWithFallback(
                                "mutation." + mutation.id().replace(':', '.'), mutation.name()))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                GeneticsTrigger.trigger(owner, GeneticsTrigger.Action.PURIFIED_MUTATION, 1,
                        mutation.tier().ordinal(), mutation.category().id());
            }
            ServerLevel level = owner.serverLevel();
            level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
            var entity = pokemon.getEntity();
            if (entity != null) {
                level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + entity.getBbHeight() / 2,
                        entity.getZ(), 40, 0.5, 0.6, 0.5, 0.05);
            }
            ResearchData research = Research.of(owner);
            research.purified++;
            Research.save(owner, research);
            GeneticsTrigger.trigger(owner, GeneticsTrigger.Action.PURIFY, research.purified);
            Research.checkMilestones(owner);
        }
    }

    public static Component stateName(ShadowState state) {
        return Component.translatable("shadow.cobblegenetics." + state.id());
    }

    /** Scales a friendship gain for a Shadow Pokémon: the more corrupted, the harder its heart is to reach. */
    public static int dampFriendship(Pokemon pokemon, int gain) {
        ShadowData data = PokemonGenetics.shadow(pokemon);
        double damping = GeneticsConfig.get().shadowFriendshipDamping;
        if (data == null || !data.active || damping <= 0 || gain <= 0) {
            return gain;
        }
        double factor = Math.max(0, 1 - data.corruption / damping);
        return (int) Math.floor(gain * factor);
    }
}
