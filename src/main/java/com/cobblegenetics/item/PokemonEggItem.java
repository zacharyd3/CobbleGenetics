package com.cobblegenetics.item;

import com.cobblegenetics.breeding.EggData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A Pokémon Egg from the Genetic Incubator. It hatches after the player walks with it in their inventory (see
 * EggHatching). Like the main series, the tooltip only gives a vague hint of how close it is.
 */
public class PokemonEggItem extends Item {

    public PokemonEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        EggData data = stack.get(ModItems.EGG_DATA.get());
        if (data == null) {
            return;
        }
        float progress = data.progress();
        String hint = progress < 0.34f ? "long" : progress < 0.67f ? "while" : progress < 0.9f ? "moves" : "soon";
        tooltip.add(Component.translatable("item.cobblegenetics.pokemon_egg.hint." + hint)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (!data.parents().isEmpty()) {
            tooltip.add(Component.translatable("item.cobblegenetics.pokemon_egg.parents", data.parents())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (flag.isAdvanced()) {
            tooltip.add(Component.translatable("item.cobblegenetics.pokemon_egg.steps", data.remaining(), data.total())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
