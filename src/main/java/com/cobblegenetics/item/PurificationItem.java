package com.cobblegenetics.item;

import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblegenetics.shadow.ShadowService;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Purifying Scent (small, any corruption) and Purification Incense (large, only below a corruption limit: the
 * finishing step, so purification still needs time, battles and friendship first).
 */
public class PurificationItem extends PartyUseItem {

    private final boolean incense;

    public PurificationItem(boolean incense, Properties properties) {
        super(properties);
        this.incense = incense;
    }

    @Override
    protected boolean canTarget(Pokemon pokemon) {
        return PokemonGenetics.isShadow(pokemon);
    }

    @Override
    protected boolean apply(ServerPlayer player, Pokemon pokemon) {
        GeneticsConfig config = GeneticsConfig.get();
        ShadowData data = PokemonGenetics.shadow(pokemon);
        if (data == null || !data.active) {
            return false;
        }
        if (incense && data.corruption > config.incenseMaxCorruption) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.incense_too_corrupt",
                    Math.round(config.incenseMaxCorruption)).withStyle(ChatFormatting.RED), true);
            return false;
        }
        double amount = incense ? config.incensePurification : config.scentPurification;
        ShadowService.purify(pokemon, amount, ShadowService.Activity.ITEM, player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS,
                0.7f, 1.3f);
        return true;
    }

    @Override
    protected String noTargetKey() {
        return "message.cobblegenetics.no_shadow";
    }
}
