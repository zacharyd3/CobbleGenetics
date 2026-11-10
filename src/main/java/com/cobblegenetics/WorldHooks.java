package com.cobblegenetics;

import com.cobblegenetics.block.PurificationChamberBlockEntity;
import com.cobblegenetics.command.GeneticsCommands;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.GeneticsRegistry;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.item.ModItems;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.research.AnalysisService;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblegenetics.shadow.ShadowService;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.Set;

/** NeoForge (game) events: interactions, the periodic purification/exposure check, entity visuals, commands. */
public final class WorldHooks {

    private static final DustParticleOptions SHADOW_DUST = new DustParticleOptions(new Vector3f(0.35f, 0.1f, 0.55f), 1.2f);

    private WorldHooks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WorldHooks::onReload);
        NeoForge.EVENT_BUS.addListener(WorldHooks::onCommands);
        NeoForge.EVENT_BUS.addListener(WorldHooks::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(WorldHooks::onServerTick);
        NeoForge.EVENT_BUS.addListener(WorldHooks::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(WorldHooks::onEntityTick);
        NeoForge.EVENT_BUS.addListener(WorldHooks::onLogout);
        NeoForge.EVENT_BUS.addListener(WorldHooks::onServerStopped);
    }

    private static void onReload(AddReloadListenerEvent event) {
        event.addListener(new GeneticsRegistry.TraitLoader());
        event.addListener(new GeneticsRegistry.MutationLoader());
    }

    private static void onCommands(RegisterCommandsEvent event) {
        GeneticsCommands.register(event.getDispatcher());
    }

    /** The Analyzer on a Pokémon in the world. Runs before Cobblemon's own interaction (which would open menus). */
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof PokemonEntity entity) || !event.getItemStack().is(ModItems.GENETIC_ANALYZER.get())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                AnalysisService.analyze(player, entity.getPokemon());
            } catch (RuntimeException e) {
                CobbleGenetics.failed("analysing a Pokémon", e);
            }
        }
    }

    /** Pokémon created from properties (commands, datapack spawns, the Shadow Beacon) resolve their requests here. */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PokemonEntity entity)) {
            return;
        }
        try {
            Pokemon pokemon = entity.getPokemon();
            if (PokemonGenetics.hasRequests(pokemon)) {
                GeneticsService.ensure(pokemon, pokemon.isWild() ? Genome.Origin.WILD : Genome.Origin.RETROACTIVE);
            }
        } catch (RuntimeException e) {
            CobbleGenetics.failed("a Pokémon joining the world", e);
        }
    }

    // ------------------------------------------------------------------ periodic check

    /**
     * Once per exposureIntervalTicks (default: a minute): for each online player, only their party (at most six
     * Pokémon). Shadow Pokémon purify a little by spending time with the player (more when sent out, much more near a
     * Purification Chamber); others may develop an environmental mutation where they are.
     */
    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        GeneticsConfig config = GeneticsConfig.get();
        if (server.getTickCount() % config.exposureIntervalTicks != 0) {
            return;
        }
        boolean exposure = config.mutationsEnabled && config.environmentExposureMutationChance > 0
                && GeneticsService.anyFor(MutationSource.ENVIRONMENT);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(player, config, exposure);
            } catch (RuntimeException e) {
                CobbleGenetics.failed("the periodic genetics check", e);
            }
        }
    }

    private static void tickPlayer(ServerPlayer player, GeneticsConfig config, boolean exposure) {
        if (player.isSpectator()) {
            return;
        }
        boolean chamber = PurificationChamberBlockEntity.near(GlobalPos.of(player.level().dimension(),
                player.blockPosition()), config.chamberRadius);
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
            if (pokemon == null) {
                continue;
            }
            ShadowData shadow = PokemonGenetics.shadow(pokemon);
            if (shadow != null && shadow.active) {
                double amount = pokemon.getEntity() != null ? config.purifySentOutPerCheck : config.purifyPartyPerCheck;
                if (chamber) {
                    ShadowService.purify(pokemon, amount * config.chamberMultiplier, ShadowService.Activity.CHAMBER,
                            player);
                } else {
                    ShadowService.purify(pokemon, amount, ShadowService.Activity.TIME, player);
                }
            } else if (exposure && PokemonGenetics.hasGenome(pokemon)) {
                var def = GeneticsService.roll(pokemon, MutationSource.ENVIRONMENT,
                        config.environmentExposureMutationChance, false, player.serverLevel(),
                        GeneticsService.positionOf(pokemon, player));
                if (def != null) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.cobblegenetics.exposure", pokemon.getDisplayName(false)), true);
                }
            }
        }
    }

    // ------------------------------------------------------------------ entity visuals and behaviour

    /**
     * Aura particles (client) and the aggressive behaviour of wild Shadow Pokémon (server). Cheap: one aspect lookup
     * every few ticks per Pokémon entity, real work only for Shadow and glowing ones.
     */
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PokemonEntity entity)) {
            return;
        }
        Level level = entity.level();
        if (level.isClientSide) {
            if (entity.tickCount % 3 == 0) {
                clientParticles(entity, level);
            }
            return;
        }
        if (entity.tickCount % 40 != 0) {
            return;
        }
        try {
            Set<String> aspects = entity.getAspects();
            if (aspects.contains("cg-shadow-highly_corrupted") || aspects.contains("cg-shadow-fully_corrupted")) {
                Pokemon pokemon = entity.getPokemon();
                if (pokemon.isWild() && !entity.isBattling()) {
                    // Highly corrupted wild Shadow Pokémon radiate darkness at players who come close
                    for (Player player : level.getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(7))) {
                        if (!player.isCreative() && !player.isSpectator()) {
                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false));
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            CobbleGenetics.failed("Shadow Pokémon behaviour", e);
        }
    }

    private static void clientParticles(PokemonEntity entity, Level level) {
        Set<String> aspects = entity.getEntityData().get(PokemonEntity.Companion.getASPECTS());
        if (aspects.isEmpty() || entity.isInvisible()) {
            return;
        }
        double width = entity.getBbWidth();
        double height = entity.getBbHeight();
        var random = level.random;
        if (aspects.contains("cg-shadow")) {
            int count = aspects.contains("cg-shadow-fully_corrupted") ? 3
                    : aspects.contains("cg-shadow-highly_corrupted") || aspects.contains("cg-shadow-aggressive") ? 2 : 1;
            for (int i = 0; i < count; i++) {
                level.addParticle(SHADOW_DUST, entity.getX() + (random.nextDouble() - 0.5) * width * 1.4,
                        entity.getY() + random.nextDouble() * height, entity.getZ() + (random.nextDouble() - 0.5) * width * 1.4,
                        0, 0.02, 0);
            }
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.SMOKE, entity.getX(), entity.getY() + height * 0.6, entity.getZ(), 0, 0.03, 0);
            }
        }
        if (aspects.contains("cg-glow") && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.GLOW, entity.getX() + (random.nextDouble() - 0.5) * width,
                    entity.getY() + random.nextDouble() * height, entity.getZ() + (random.nextDouble() - 0.5) * width,
                    0, 0.01, 0);
        }
        if (aspects.contains("cg-purified") && random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.END_ROD, entity.getX() + (random.nextDouble() - 0.5) * width,
                    entity.getY() + height, entity.getZ() + (random.nextDouble() - 0.5) * width, 0, 0.02, 0);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AnalysisService.forget(player);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        PurificationChamberBlockEntity.clear();
    }
}
