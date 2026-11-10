package com.cobblegenetics.item;

import com.cobblegenetics.advancement.GeneticsTrigger;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Mutagen: forces a mutation roll (the "special items" pathway) at the cost of genetic stability. The outcome is
 * random - it can be negative - and the Analyzer is needed to see what happened. Not consumed when nothing can mutate.
 */
public class MutagenItem extends PartyUseItem {

    public MutagenItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canTarget(Pokemon pokemon) {
        return !PokemonGenetics.isShadow(pokemon);
    }

    @Override
    protected boolean apply(ServerPlayer player, Pokemon pokemon) {
        GeneticsConfig config = GeneticsConfig.get();
        if (!config.mutationsEnabled) {
            return false;
        }
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        if (genome == null) {
            return false;
        }
        if (genome.mutations.size() >= config.maxMutations) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.mutation_full")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        MutationDef def = GeneticsService.roll(pokemon, MutationSource.ITEM, 1, true, player.serverLevel(),
                player.blockPosition());
        if (def == null) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.mutagen_nothing")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        Genome after = PokemonGenetics.genome(pokemon);
        after.stability = Math.max(0, after.stability - config.mutagenStabilityCost);
        PokemonGenetics.setGenome(pokemon, after);
        player.sendSystemMessage(Component.translatable("message.cobblegenetics.mutagen_done",
                pokemon.getDisplayName(false)).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS,
                0.5f, 1.6f);
        GeneticsTrigger.trigger(player, GeneticsTrigger.Action.MUTAGEN, 1, def.tier().ordinal(), def.category().id());
        return true;
    }

    @Override
    protected String noTargetKey() {
        return "message.cobblegenetics.empty_party";
    }
}
