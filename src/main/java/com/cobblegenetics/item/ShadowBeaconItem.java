package com.cobblegenetics.item;

import com.cobblegenetics.config.GeneticsConfig;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Set;

/**
 * Shadow Beacon: calls a wild Shadow Pokémon to the player (a guaranteed encounter for the questline, so the main
 * progression never depends on the rare natural Shadow spawn). The Pokémon's level follows the player's party.
 */
public class ShadowBeaconItem extends Item {

    private static final Set<String> EXCLUDED_LABELS = Set.of("legendary", "mythical", "ultra_beast", "paradox",
            "restricted");

    public ShadowBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }
        if (!GeneticsConfig.get().shadowEnabled) {
            return InteractionResultHolder.fail(stack);
        }
        List<Species> candidates = PokemonSpecies.getImplemented().stream()
                .filter(species -> species.getLabels().stream().noneMatch(EXCLUDED_LABELS::contains))
                .toList();
        if (candidates.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        Species species = candidates.get(serverLevel.random.nextInt(candidates.size()));
        int levelValue = Math.max(5, Math.min(100, averagePartyLevel(serverPlayer) + serverLevel.random.nextInt(5) - 2));

        double angle = serverLevel.random.nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * 6);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * 6);
        BlockPos pos = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));

        PokemonProperties properties = PokemonProperties.Companion.parse("species=" + species.getResourceIdentifier()
                + " level=" + levelValue + " cg_shadow=true");
        PokemonEntity entity = properties.createEntity(serverLevel);
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, serverLevel.random.nextFloat() * 360, 0);
        serverLevel.addFreshEntity(entity);
        serverLevel.sendParticles(ParticleTypes.SQUID_INK, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.6,
                0.8, 0.6, 0.02);
        serverLevel.playSound(null, pos, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.6f);
        serverPlayer.sendSystemMessage(Component.translatable("message.cobblegenetics.beacon")
                .withStyle(ChatFormatting.DARK_PURPLE));
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, 100);
        return InteractionResultHolder.consume(stack);
    }

    private static int averagePartyLevel(ServerPlayer player) {
        int total = 0;
        int count = 0;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null) {
                total += pokemon.getLevel();
                count++;
            }
        }
        return count == 0 ? 10 : total / count;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cobblegenetics.shadow_beacon.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
