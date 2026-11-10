package com.cobblegenetics.item;

import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Repairs genetic damage left by Shadow corruption (up to 10 points). Does nothing to an undamaged genome. */
public class GeneStabilizerItem extends PartyUseItem {

    public GeneStabilizerItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canTarget(Pokemon pokemon) {
        Genome genome = PokemonGenetics.genome(pokemon);
        return genome == null || genome.stabilityDamage > 0;
    }

    @Override
    protected boolean apply(ServerPlayer player, Pokemon pokemon) {
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        if (genome == null || genome.stabilityDamage <= 0) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.no_damage")
                    .withStyle(ChatFormatting.YELLOW), true);
            return false;
        }
        genome.stabilityDamage = Math.max(0, genome.stabilityDamage - 10);
        PokemonGenetics.setGenome(pokemon, genome);
        player.displayClientMessage(Component.translatable("message.cobblegenetics.stabilized",
                pokemon.getDisplayName(false)).withStyle(ChatFormatting.AQUA), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                1, 1.2f);
        return true;
    }

    @Override
    protected String noTargetKey() {
        return "message.cobblegenetics.no_damage";
    }
}
