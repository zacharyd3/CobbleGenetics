package com.cobblegenetics.item;

import com.cobblegenetics.research.AnalysisService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Genetic Analyzer. Right-click a Pokémon in the world (wild or owned; handled in CobbleGeneticsHooks before
 * Cobblemon's own interaction) or use it in the air to pick one from the party.
 */
public class GeneticAnalyzerItem extends Item {

    public GeneticAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            boolean opened = PartySelect.open(serverPlayer, Component.translatable("gui.cobblegenetics.select_analyze"),
                    pokemon -> true, pokemon -> AnalysisService.analyze(serverPlayer, pokemon));
            if (!opened) {
                serverPlayer.displayClientMessage(Component.translatable("message.cobblegenetics.empty_party"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cobblegenetics.genetic_analyzer.tooltip").withStyle(ChatFormatting.GRAY));
    }

}
