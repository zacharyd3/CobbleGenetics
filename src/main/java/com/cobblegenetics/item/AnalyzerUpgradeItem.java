package com.cobblegenetics.item;

import com.cobblegenetics.research.Research;
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

/** Raises research from level-1 to level. Quests hand these out; used upgrades are consumed. */
public class AnalyzerUpgradeItem extends Item {

    private final int level;

    public AnalyzerUpgradeItem(int level, Properties properties) {
        super(properties);
        this.level = level;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            int current = Research.level(serverPlayer);
            if (current >= level) {
                serverPlayer.displayClientMessage(Component.translatable("message.cobblegenetics.upgrade_known")
                        .withStyle(ChatFormatting.YELLOW), true);
                return InteractionResultHolder.fail(stack);
            }
            if (current != level - 1) {
                serverPlayer.displayClientMessage(Component.translatable("message.cobblegenetics.upgrade_requires",
                        level - 1).withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            Research.upgrade(serverPlayer, current);
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cobblegenetics.analyzer_upgrade.tooltip", level,
                Component.translatable("research.cobblegenetics.level" + level)).withStyle(ChatFormatting.GRAY));
    }
}
