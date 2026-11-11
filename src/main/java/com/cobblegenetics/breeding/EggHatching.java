package com.cobblegenetics.breeding;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.item.ModItems;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.HatchEggEvent;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main-series style eggs: the distance a player walks (or rides) with eggs in their inventory counts down every egg
 * they carry, and an egg hatches into its Pokémon when it reaches zero.
 *
 * <p>Cheap: one subtraction per player tick, and the inventory is only looked at once a second, and only if the player
 * moved. Teleports, elytra flight and creative/spectator flying do not count.</p>
 */
public final class EggHatching {

    private static final Map<UUID, Double> WALKED = new ConcurrentHashMap<>();

    private EggHatching() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EggHatching::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> WALKED.remove(event.getEntity().getUUID()));
    }

    // ------------------------------------------------------------------ creating eggs

    /** Packs a finished offspring into an egg item. */
    public static ItemStack createEgg(net.minecraft.core.RegistryAccess registries, Pokemon child, Pokemon mother,
                                      Pokemon father) {
        GeneticsConfig config = GeneticsConfig.get();
        int cycles = Math.max(1, child.getSpecies().getEggCycles());
        int total = cycles * config.eggBlocksPerCycle;
        ElementalType type = child.getPrimaryType();
        int color = type == null ? 0x8FCF6B : type.getHue();
        String parents = mother.getSpecies().getTranslatedName().getString() + " × "
                + father.getSpecies().getTranslatedName().getString();
        ItemStack stack = new ItemStack(ModItems.POKEMON_EGG.get());
        stack.set(ModItems.EGG_DATA.get(), new EggData(child.saveToNBT(registries,
                new net.minecraft.nbt.CompoundTag()), total, total, color, parents));
        return stack;
    }

    // ------------------------------------------------------------------ walking

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        double distance = Math.sqrt(dx * dx + dz * dz);
        boolean counts = distance > 0.01 && distance < 1.5 && !player.isFallFlying() && !player.getAbilities().flying;
        if (counts) {
            WALKED.merge(player.getUUID(), distance, Double::sum);
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        Double walked = WALKED.get(player.getUUID());
        if (walked == null || walked < 1) {
            return;
        }
        int steps = (int) Math.floor(walked);
        WALKED.put(player.getUUID(), walked - steps);
        try {
            advanceEggs(player, steps);
        } catch (RuntimeException e) {
            CobbleGenetics.failed("hatching eggs", e);
        }
    }

    private static void advanceEggs(ServerPlayer player, int steps) {
        Inventory inventory = player.getInventory();
        boolean any = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.POKEMON_EGG.get())) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        if (hasSpeedAbility(player)) {
            steps *= 2;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(ModItems.POKEMON_EGG.get())) {
                continue;
            }
            EggData data = stack.get(ModItems.EGG_DATA.get());
            if (data == null) {
                continue;
            }
            int remaining = data.remaining() - steps;
            if (remaining > 0) {
                stack.set(ModItems.EGG_DATA.get(), data.withRemaining(remaining));
            } else {
                hatch(player, stack, data);
            }
        }
    }

    private static boolean hasSpeedAbility(ServerPlayer player) {
        var abilities = GeneticsConfig.get().eggSpeedAbilities;
        if (abilities.isEmpty()) {
            return false;
        }
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon != null && abilities.contains(pokemon.getAbility().getName())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ hatching

    /**
     * An Egg hatched by a Day Care Attendant in an Incubator: the baby goes to the owner's party, or PC when full
     * (online or not, as the Incubator's parents do). Returns false if the Pokémon could not be read (keep the Egg).
     */
    public static boolean hatchFor(net.minecraft.server.level.ServerLevel level, UUID owner, EggData data,
                                   net.minecraft.core.BlockPos pos) {
        Pokemon pokemon;
        try {
            pokemon = Pokemon.Companion.loadFromNBT(level.registryAccess(), data.pokemon());
        } catch (RuntimeException e) {
            CobbleGenetics.failed("hatching an egg in an Incubator (unreadable Pokémon)", e);
            return false;
        }
        pokemon.setUuid(UUID.randomUUID());
        Cobblemon.INSTANCE.getStorage().getParty(owner, level.registryAccess()).add(pokemon);
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 1, 1.2f);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.POKEMON_EGG.get())),
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.25, 0.25, 0.25, 0.05);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.cobblegenetics.egg_hatched_daycare",
                    pokemon.getDisplayName(false)).withStyle(ChatFormatting.GREEN));
            CobblemonEvents.HATCH_EGG_POST.post(new HatchEggEvent.Post(player, pokemon));
            var research = com.cobblegenetics.research.Research.of(player);
            research.daycareHatched++;
            com.cobblegenetics.research.Research.save(player, research);
            com.cobblegenetics.advancement.GeneticsTrigger.trigger(player,
                    com.cobblegenetics.advancement.GeneticsTrigger.Action.DAYCARE_HATCH, research.daycareHatched);
        }
        return true;
    }

    static void hatch(ServerPlayer player, ItemStack stack, EggData data) {
        Pokemon pokemon;
        try {
            pokemon = Pokemon.Companion.loadFromNBT(player.registryAccess(), data.pokemon());
        } catch (RuntimeException e) {
            // A species removed by a datapack since the egg was laid: keep the egg rather than lose the data
            CobbleGenetics.failed("hatching an egg (unreadable Pokémon)", e);
            return;
        }
        // A copied egg (creative pick-block) must not hatch a second Pokémon with the same UUID
        pokemon.setUuid(UUID.randomUUID());
        stack.shrink(1);
        Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);

        player.sendSystemMessage(Component.translatable("message.cobblegenetics.egg_hatched",
                pokemon.getDisplayName(false)).withStyle(ChatFormatting.GREEN));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TURTLE_EGG_HATCH, SoundSource.PLAYERS,
                1, 1.2f);
        player.serverLevel().sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.isEmpty()
                        ? new ItemStack(ModItems.POKEMON_EGG.get()) : stack.copyWithCount(1)),
                player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        // Cobblemon's own hatch event: its "eggs hatched" stats and advancements count our eggs too
        CobblemonEvents.HATCH_EGG_POST.post(new HatchEggEvent.Post(player, pokemon));
    }
}
