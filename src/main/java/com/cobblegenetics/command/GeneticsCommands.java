package com.cobblegenetics.command;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.GeneticsRegistry;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.genetics.GeneticsRules;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.research.AnalysisService;
import com.cobblegenetics.research.Research;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblegenetics.shadow.ShadowService;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Admin and testing commands (permission level 2). Slots are party slots 1-6 of the player running the command.
 * <pre>
 *   /cobblegenetics analyze &lt;slot&gt;             open the Analyzer report (no item needed)
 *   /cobblegenetics info &lt;slot&gt;                raw genome in chat (ignores research level)
 *   /cobblegenetics mutate &lt;slot&gt; &lt;mutation&gt;    add a mutation
 *   /cobblegenetics roll &lt;slot&gt; &lt;source&gt;       force a mutation roll through a pathway
 *   /cobblegenetics shadow &lt;slot&gt; &lt;corruption&gt;  make Shadow / set corruption (0 = purify instantly)
 *   /cobblegenetics research &lt;level&gt; [player]  set research level
 *   /cobblegenetics mutations                  list loaded mutations
 *   /cobblegenetics reloadconfig               re-read config/cobblegenetics.json
 * </pre>
 */
public final class GeneticsCommands {

    private static final SimpleCommandExceptionType NO_POKEMON = new SimpleCommandExceptionType(
            Component.translatable("command.cobblegenetics.no_pokemon"));
    private static final SimpleCommandExceptionType UNKNOWN_MUTATION = new SimpleCommandExceptionType(
            Component.translatable("command.cobblegenetics.unknown_mutation"));

    private GeneticsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobblegenetics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("analyze").then(slot().executes(ctx -> {
                    AnalysisService.analyze(ctx.getSource().getPlayerOrException(), pokemon(ctx));
                    return 1;
                })))
                .then(Commands.literal("info").then(slot().executes(GeneticsCommands::info)))
                .then(Commands.literal("mutate").then(slot().then(Commands.argument("mutation",
                                StringArgumentType.string())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                GeneticsRegistry.mutations().stream().map(d -> "\"" + d.id() + "\""), builder))
                        .executes(GeneticsCommands::mutate))))
                .then(Commands.literal("roll").then(slot().then(Commands.argument("source", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(
                                MutationSource.values()).map(MutationSource::id), builder))
                        .executes(GeneticsCommands::roll))))
                .then(Commands.literal("shadow").then(slot().then(Commands.argument("corruption",
                        IntegerArgumentType.integer(0, 100)).executes(GeneticsCommands::shadow))))
                .then(Commands.literal("research").then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> research(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> research(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("mutations").executes(GeneticsCommands::list))
                .then(Commands.literal("reloadconfig").executes(ctx -> {
                    GeneticsConfig.load(FMLPaths.CONFIGDIR.get().resolve(CobbleGenetics.MOD_ID + ".json"));
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.cobblegenetics.config_reloaded"),
                            true);
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> slot() {
        return Commands.argument("slot", IntegerArgumentType.integer(1, 6));
    }

    private static Pokemon pokemon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player)
                .get(IntegerArgumentType.getInteger(ctx, "slot") - 1);
        if (pokemon == null) {
            throw NO_POKEMON.create();
        }
        return pokemon;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Pokemon pokemon = pokemon(ctx);
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        ShadowData shadow = PokemonGenetics.shadow(pokemon);
        String text = String.format(java.util.Locale.ROOT,
                "%s | potential %.1f (%s) | stability %.1f (damage %.1f, effective %.1f) | susceptibility %.1f | "
                        + "affinity %s %.0f | size %.2f | gen %d %s | traits %s | mutations %s | latent %s%s",
                pokemon.getSpecies().getName(), genome.potential, GeneticsRules.grade(genome.potential),
                genome.stability, genome.stabilityDamage, GeneticsService.effectiveStability(pokemon, genome, shadow),
                genome.susceptibility, genome.affinity, genome.affinityStrength, genome.size, genome.generation,
                genome.origin, genome.traits.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().encode())
                        .toList(), genome.mutationSources, genome.latent,
                shadow == null ? "" : String.format(java.util.Locale.ROOT, " | shadow active=%s corruption=%.1f peak=%.1f",
                        shadow.active, shadow.corruption, shadow.peakCorruption));
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int mutate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Pokemon pokemon = pokemon(ctx);
        String id = StringArgumentType.getString(ctx, "mutation");
        MutationDef def = GeneticsRegistry.mutation(id.contains(":") ? id : CobbleGenetics.MOD_ID + ":" + id);
        if (def == null) {
            throw UNKNOWN_MUTATION.create();
        }
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        boolean added = GeneticsService.addMutation(pokemon, genome, def, MutationSource.COMMAND);
        ctx.getSource().sendSuccess(() -> Component.translatable(added ? "command.cobblegenetics.mutated"
                : "command.cobblegenetics.already", def.id()), true);
        return added ? 1 : 0;
    }

    private static int roll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Pokemon pokemon = pokemon(ctx);
        MutationSource source = MutationSource.parse(StringArgumentType.getString(ctx, "source"));
        if (source == null || source == MutationSource.COMMAND) {
            throw UNKNOWN_MUTATION.create();
        }
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MutationDef def = GeneticsService.roll(pokemon, source, 1, true, player.serverLevel(), player.blockPosition());
        ctx.getSource().sendSuccess(() -> def == null ? Component.translatable("command.cobblegenetics.no_eligible")
                : Component.translatable("command.cobblegenetics.mutated", def.id()), true);
        return def == null ? 0 : 1;
    }

    private static int shadow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Pokemon pokemon = pokemon(ctx);
        int corruption = IntegerArgumentType.getInteger(ctx, "corruption");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (corruption == 0) {
            if (PokemonGenetics.isShadow(pokemon)) {
                ShadowService.purify(pokemon, 1000, ShadowService.Activity.ITEM, player);
            }
        } else {
            ShadowService.makeShadow(pokemon, corruption);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.cobblegenetics.shadow_set", corruption), true);
        return 1;
    }

    private static int research(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        int level = IntegerArgumentType.getInteger(ctx, "level");
        Research.setLevel(target, level);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.cobblegenetics.research_set",
                target.getDisplayName(), level), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        for (MutationDef def : GeneticsRegistry.mutations()) {
            String line = def.id() + " [" + def.tier().id() + ", " + def.category().id()
                    + (def.negative() ? ", negative" : "") + "] sources=" + def.sources();
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return GeneticsRegistry.mutations().size();
    }
}
