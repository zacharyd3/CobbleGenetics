package com.cobblegenetics.item;

import com.cobblemon.mod.common.pokemon.Pokemon;
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
 * Base for consumables used on a party Pokémon: right-click opens Cobblemon's party selection with the valid targets,
 * then {@link #apply} runs; one item is consumed only when it returns true.
 */
public abstract class PartyUseItem extends Item {

    protected PartyUseItem(Properties properties) {
        super(properties);
    }

    /** Which party members can be chosen. */
    protected abstract boolean canTarget(Pokemon pokemon);

    /** Does the work. Return false to keep the item (with a message explaining why). */
    protected abstract boolean apply(ServerPlayer player, Pokemon pokemon);

    protected abstract String noTargetKey();

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            boolean opened = PartySelect.open(serverPlayer, getDescription(), this::canTarget, pokemon -> {
                ItemStack held = serverPlayer.getItemInHand(hand);
                if (!held.is(this)) {
                    return;
                }
                if (apply(serverPlayer, pokemon) && !serverPlayer.isCreative()) {
                    held.shrink(1);
                }
            });
            if (!opened) {
                serverPlayer.displayClientMessage(Component.translatable(noTargetKey()).withStyle(ChatFormatting.YELLOW),
                        true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
