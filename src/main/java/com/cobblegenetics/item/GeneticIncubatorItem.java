package com.cobblegenetics.item;

import com.cobblegenetics.breeding.BreedingService;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Genetic Incubator: breeding for packs without a breeding addon (Cobblemon 1.7.3 has none built in). Pick two
 * compatible party Pokémon; one Egg from the inventory is used as the vessel and the offspring hatches at once.
 * The Incubator itself is not consumed; each parent has a cooldown (config).
 */
public class GeneticIncubatorItem extends Item {

    public GeneticIncubatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (!GeneticsConfig.get().builtInBreeding) {
            serverPlayer.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_disabled")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean opened = PartySelect.open(serverPlayer, Component.translatable("gui.cobblegenetics.select_parent1"),
                pokemon -> true, first -> selectSecond(serverPlayer, first));
        if (!opened) {
            serverPlayer.displayClientMessage(Component.translatable("message.cobblegenetics.empty_party"), true);
        }
        return InteractionResultHolder.consume(stack);
    }

    private static void selectSecond(ServerPlayer player, Pokemon first) {
        long time = player.serverLevel().getGameTime();
        boolean opened = PartySelect.open(player, Component.translatable("gui.cobblegenetics.select_parent2",
                        first.getDisplayName(false)),
                pokemon -> BreedingService.incompatibility(first, pokemon, time) == null,
                second -> {
                    Component problem = BreedingService.incompatibility(first, second, player.serverLevel().getGameTime());
                    if (problem != null) {
                        player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    if (!player.isCreative() && !consumeEgg(player)) {
                        player.displayClientMessage(Component.translatable("message.cobblegenetics.incubator_needs_egg")
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    BreedingService.incubate(player, first, second);
                });
        if (!opened) {
            // Explain why nobody matches, using the first other party member as the example
            Component reason = Component.translatable("breeding.cobblegenetics.no_partner");
            for (Pokemon other : PartySelect.party(player)) {
                Component problem = BreedingService.incompatibility(first, other, time);
                if (problem != null && !other.getUuid().equals(first.getUuid())) {
                    reason = problem;
                    break;
                }
            }
            player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    private static boolean consumeEgg(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.EGG)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cobblegenetics.genetic_incubator.tooltip",
                GeneticsConfig.get().incubatorMinParentLevel).withStyle(ChatFormatting.GRAY));
    }
}
