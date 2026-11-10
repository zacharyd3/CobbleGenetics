package com.cobblegenetics.item;

import com.cobblegenetics.config.GeneticsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Places the two-block Genetic Incubator (like a door), with a short how-to in the tooltip. */
public class IncubatorBlockItem extends DoubleHighBlockItem {

    public IncubatorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cobblegenetics.genetic_incubator.tooltip",
                GeneticsConfig.get().incubatorMinParentLevel).withStyle(ChatFormatting.GRAY));
    }
}
