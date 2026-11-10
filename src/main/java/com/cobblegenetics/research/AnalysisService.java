package com.cobblegenetics.research;

import com.cobblegenetics.advancement.GeneticsTrigger;
import com.cobblegenetics.advancement.GeneticsTrigger.Action;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.data.GeneticsRegistry;
import com.cobblegenetics.data.PokemonGenetics;
import com.cobblegenetics.effect.GeneticEffect;
import com.cobblegenetics.genetics.GeneticsRules;
import com.cobblegenetics.genetics.GeneticsService;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.genetics.TraitLocus;
import com.cobblegenetics.item.ModItems;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.network.AnalysisPayload;
import com.cobblegenetics.network.AnalysisPayload.Row;
import com.cobblegenetics.network.AnalysisPayload.Section;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblegenetics.shadow.ShadowService;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds Genetic Analyzer reports, filtered by the player's research level on the server, and records the research
 * progress that analysing represents (advancements, milestones).
 *
 * <p>Research levels: 1 potential, origin, mutation count, shadow state - 2 stability, affinity, mutation names -
 * 3 susceptibility, size, expressed traits, latent count - 4 genotypes, mutation details, latent names, breeding
 * prediction with the previously analysed Pokémon - 5 exact Shadow analysis, rare combination hints.</p>
 */
public final class AnalysisService {

    private static final int GOOD = 0xFF55D66B;
    private static final int MID = 0xFFE8C547;
    private static final int BAD = 0xFFE5534B;
    private static final int INFO = 0xFF7FB8FF;

    /** Last analysis per player: the Genome for breeding predictions and the text for a Genetic Record. */
    private record LastAnalysis(UUID pokemon, Component name, Genome genome, List<Component> recordLines) {
    }

    private static final Map<UUID, LastAnalysis> LAST = new ConcurrentHashMap<>();

    private AnalysisService() {
    }

    public static void forget(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }

    public static void analyze(ServerPlayer player, Pokemon pokemon) {
        Genome genome = GeneticsService.ensure(pokemon, Genome.Origin.RETROACTIVE);
        if (genome == null) {
            return;
        }
        ShadowData shadow = PokemonGenetics.shadow(pokemon);
        ResearchData research = Research.of(player);
        progress(player, pokemon, genome, shadow, research);
        int level = research.level;

        GeneticsConfig config = GeneticsConfig.get();
        List<Section> sections = new ArrayList<>();
        List<Component> record = new ArrayList<>();
        double effective = GeneticsService.effectiveStability(pokemon, genome, shadow);

        // ---------------- genetic profile
        List<Row> profile = new ArrayList<>();
        String grade = GeneticsRules.grade(genome.potential);
        Component potentialText = level >= 2
                ? Component.literal(grade + "  (" + round(genome.potential) + ")")
                : Component.literal(grade);
        profile.add(row("potential", potentialText, (float) (genome.potential / 100), gradeColor(genome.potential)));
        record.add(line("potential", potentialText));
        if (level >= 2) {
            MutableComponent stabilityText = Component.literal(round(effective) + "");
            if (level >= 4 && (genome.stabilityDamage > 0 || effective != genome.stability)) {
                stabilityText.append(Component.translatable("analysis.cobblegenetics.stability_detail",
                        round(genome.stability), round(genome.stabilityDamage)).withStyle(ChatFormatting.GRAY));
            }
            profile.add(row("stability", stabilityText, (float) (effective / 100), gradeColor(effective)));
            record.add(line("stability", Component.literal(round(effective) + "")));
            Component affinity = typeName(genome.affinity).copy().append(" (" + GeneticsRules.grade(genome.affinityStrength) + ")");
            profile.add(row("affinity", affinity, (float) (genome.affinityStrength / 100), INFO));
            record.add(line("affinity", affinity));
        } else {
            profile.add(locked("stability", 2));
            profile.add(locked("affinity", 2));
        }
        if (level >= 3) {
            profile.add(row("susceptibility", Component.literal(round(genome.susceptibility) + ""),
                    (float) (genome.susceptibility / 100), genome.susceptibility > 60 ? BAD : MID));
            String size = genome.size > 0.35 ? "large" : genome.size < -0.35 ? "small" : "average";
            profile.add(row("size", Component.translatable("analysis.cobblegenetics.size." + size), -1, INFO));
            record.add(line("susceptibility", Component.literal(round(genome.susceptibility) + "")));
        } else {
            profile.add(locked("susceptibility", 3));
        }
        Component origin = genome.origin == Genome.Origin.BRED
                ? Component.translatable("analysis.cobblegenetics.origin.bred", genome.generation)
                : Component.translatable("analysis.cobblegenetics.origin." + genome.origin.name().toLowerCase(Locale.ROOT));
        profile.add(row("origin", origin, -1, INFO));
        sections.add(new Section(Component.translatable("analysis.cobblegenetics.section.profile"), profile));

        // ---------------- traits
        List<Row> traits = new ArrayList<>();
        if (level >= 3) {
            for (TraitLocus locus : GeneticsRegistry.loci()) {
                Genome.Genotype genotype = genome.traits.get(locus.id());
                if (genotype == null) {
                    continue;
                }
                MutableComponent value = Component.empty();
                boolean rare = false;
                for (TraitLocus.Allele allele : locus.expressed(genotype)) {
                    if (!value.getSiblings().isEmpty()) {
                        value.append(" + ");
                    }
                    value.append(Component.translatableWithFallback(
                            "trait.cobblegenetics." + locus.id() + "." + allele.id(), allele.name()));
                    rare |= allele.rare();
                }
                if (level >= 4) {
                    value.append(Component.literal("  [" + genotype.first() + genotype.second() + "]")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                traits.add(row(Component.translatableWithFallback("trait.cobblegenetics." + locus.id(), locus.name()),
                        value, -1, rare ? 0xFFFFB82E : 0xFFDDDDDD));
                record.add(Component.translatableWithFallback("trait.cobblegenetics." + locus.id(), locus.name())
                        .append(": ").append(value.copy()));
            }
            if (!genome.parents.isEmpty()) {
                traits.add(row("parents", Component.literal(String.join(" × ", genome.parents.stream()
                        .map(p -> p.substring(p.indexOf(':') + 1)).toList())), -1, INFO));
            }
        } else {
            traits.add(locked("traits", 3));
        }
        sections.add(new Section(Component.translatable("analysis.cobblegenetics.section.traits"), traits));

        // ---------------- mutations
        List<Row> mutations = new ArrayList<>();
        if (genome.mutations.isEmpty()) {
            mutations.add(row("mutations", Component.translatable("analysis.cobblegenetics.none_detected"), -1, 0xFF999999));
        } else if (level < 2) {
            mutations.add(row("mutations", Component.translatable("analysis.cobblegenetics.detected",
                    genome.mutations.size()), -1, 0xFFB46CFF));
            mutations.add(locked("mutation_names", 2));
        } else {
            for (String id : genome.mutations) {
                MutationDef def = GeneticsRegistry.mutation(id);
                if (def == null) {
                    mutations.add(row(Component.literal(id), Component.translatable("analysis.cobblegenetics.unknown"),
                            -1, 0xFF999999));
                    continue;
                }
                MutableComponent value = Component.translatable("tier.cobblegenetics." + def.tier().id());
                if (def.negative()) {
                    value.append(" ").append(Component.translatable("analysis.cobblegenetics.negative"));
                }
                if (level >= 4) {
                    value.append(" · ").append(Component.translatable("category.cobblegenetics." + def.category().id()))
                            .append(" · ").append(Component.translatable("source.cobblegenetics."
                                    + genome.mutationSources.getOrDefault(id, "unknown")));
                }
                Component name = mutationName(def);
                mutations.add(row(name, value, -1, def.negative() ? BAD : def.tier().color));
                record.add(Component.translatable("analysis.cobblegenetics.record_mutation", name));
                if (level >= 4) {
                    if (!def.description().isEmpty()) {
                        mutations.add(row(Component.literal("   "), Component.translatableWithFallback(
                                "mutation." + id.replace(':', '.') + ".desc", def.description())
                                .withStyle(ChatFormatting.ITALIC), -1, 0xFFAAAAAA));
                    }
                    for (GeneticEffect effect : def.effects()) {
                        mutations.add(row(Component.literal("   •"), effect.describe(), -1, 0xFFAAAAAA));
                    }
                }
            }
        }
        if (!genome.latent.isEmpty()) {
            if (level >= 4) {
                List<String> names = genome.latent.stream().map(id -> {
                    MutationDef def = GeneticsRegistry.mutation(id);
                    return def == null ? id : mutationName(def).getString();
                }).toList();
                mutations.add(row("latent", Component.literal(String.join(", ", names)), -1, 0xFF8E7CC3));
            } else if (level >= 3) {
                mutations.add(row("latent", Component.translatable("analysis.cobblegenetics.latent_count",
                        genome.latent.size()), -1, 0xFF8E7CC3));
            }
        }
        sections.add(new Section(Component.translatable("analysis.cobblegenetics.section.mutations"), mutations));

        // ---------------- shadow
        if (shadow != null && shadow.history()) {
            List<Row> rows = new ArrayList<>();
            if (shadow.active) {
                Component state = ShadowService.stateName(shadow.state());
                Component amount = level >= 5 ? Component.literal(round(shadow.corruption) + "%")
                        : Component.literal("~" + (int) (Math.round(shadow.corruption / 10.0) * 10) + "%");
                rows.add(row("corruption", state.copy().append("  ").append(amount),
                        (float) (shadow.corruption / 100), shadow.state().color | 0xFF000000));
                rows.add(row("purification", Component.literal(Math.round(shadow.progress() * 100) + "%"),
                        (float) shadow.progress(), 0xFFE0D2FF));
                record.add(line("corruption", state));
                if (level >= 5) {
                    rows.add(row("peak", Component.literal(round(shadow.peakCorruption) + "%"), -1, 0xFF8E7CC3));
                    rows.add(row("resistance", Component.literal(
                            Math.round(GeneticsService.corruptionResistance(genome) * 100) + "%"), -1, INFO));
                    rows.add(row("drain", Component.literal("-" + round(shadow.corruption
                            * config.corruptionStabilityDrain)), -1, BAD));
                    rows.add(row("breakdown", Component.translatable("analysis.cobblegenetics.breakdown",
                            round(shadow.fromTime), round(shadow.fromBattles), round(shadow.fromFriendship),
                            round(shadow.fromItems), round(shadow.fromChamber)), -1, 0xFFAAAAAA));
                } else {
                    rows.add(locked("shadow_details", 5));
                }
            } else {
                rows.add(row("corruption", Component.translatable("analysis.cobblegenetics.purified"), 1, 0xFFFFE680));
                if (level >= 5) {
                    rows.add(row("peak", Component.literal(round(shadow.peakCorruption) + "%"), -1, 0xFF8E7CC3));
                }
            }
            sections.add(new Section(Component.translatable("analysis.cobblegenetics.section.shadow"), rows));
        }

        // ---------------- breeding prediction (level 4)
        LastAnalysis previous = LAST.get(player.getUUID());
        if (level >= 4 && previous != null && !previous.pokemon().equals(pokemon.getUuid())) {
            sections.add(new Section(Component.translatable("analysis.cobblegenetics.section.prediction",
                    previous.name()), prediction(previous.genome(), genome)));
        }

        // ---------------- rare combinations (level 5)
        if (level >= 5) {
            List<Row> hints = combinationHints(genome);
            if (!hints.isEmpty()) {
                sections.add(new Section(Component.translatable("analysis.cobblegenetics.section.combinations"), hints));
            }
        }

        Component title = pokemon.getDisplayName(false).copy().append(Component.literal("  Lv." + pokemon.getLevel())
                .withStyle(ChatFormatting.GRAY));
        Component subtitle = pokemon.getSpecies().getTranslatedName().copy().append(" · ").append(
                Component.translatable(pokemon.isWild() ? "analysis.cobblegenetics.wild" : "analysis.cobblegenetics.owned"));
        LAST.put(player.getUUID(), new LastAnalysis(pokemon.getUuid(), pokemon.getDisplayName(false), genome.copy(),
                record));

        boolean canRecord = !pokemon.isWild();
        if (player.connection.hasChannel(AnalysisPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new AnalysisPayload(pokemon.getUuid(), title, subtitle, level,
                    canRecord, sections));
        } else {
            // A client without the mod (should not happen: the mod is required on both sides) still gets the gist
            player.sendSystemMessage(title);
            record.forEach(player::sendSystemMessage);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.8f, 1.2f);
    }

    /** Research progress from one analysis: counters, milestones, advancements. */
    private static void progress(ServerPlayer player, Pokemon pokemon, Genome genome, ShadowData shadow,
                                 ResearchData research) {
        GeneticsConfig config = GeneticsConfig.get();
        boolean counted = research.countAnalysis(pokemon.getUuid());
        if (counted && genome.origin == Genome.Origin.BRED) {
            research.offspringAnalyzed++;
        }
        int discoveredBefore = research.mutationsDiscovered.size();
        for (String id : genome.mutations) {
            if (GeneticsRegistry.mutation(id) != null) {
                research.mutationsDiscovered.add(id);
            }
        }
        Research.save(player, research);
        if (research.mutationsDiscovered.size() > discoveredBefore) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.new_mutation")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }

        GeneticsTrigger.trigger(player, Action.ANALYZE, research.analyses);
        if (genome.potential >= config.highPotentialThreshold) {
            GeneticsTrigger.trigger(player, Action.HIGH_POTENTIAL, (int) genome.potential);
        }
        if (genome.offspring > 0) {
            GeneticsTrigger.trigger(player, Action.ANALYZE_PARENT, genome.offspring);
        }
        if (genome.origin == Genome.Origin.BRED) {
            GeneticsTrigger.trigger(player, Action.ANALYZE_OFFSPRING, genome.generation);
            if (research.level >= 3) {
                GeneticsTrigger.trigger(player, Action.INHERITED_TRAIT, 1);
            }
            for (TraitLocus locus : GeneticsRegistry.loci()) {
                Genome.Genotype genotype = genome.traits.get(locus.id());
                if (genotype != null && locus.expressesRecessive(genotype)) {
                    TraitLocus.Allele allele = locus.allele(genotype.first());
                    if (allele != null && allele.rare()) {
                        GeneticsTrigger.trigger(player, Action.RARE_TRAIT, 1);
                    }
                }
            }
        }
        for (String id : genome.mutations) {
            MutationDef def = GeneticsRegistry.mutation(id);
            if (def == null) {
                continue;
            }
            int tier = def.tier().ordinal();
            String category = def.category().id();
            String source = genome.mutationSources.getOrDefault(id, "");
            GeneticsTrigger.trigger(player, Action.MUTATION, research.mutationsDiscovered.size(), tier, category);
            if (source.equals("natural") || source.equals("environment") || source.equals("event")) {
                GeneticsTrigger.trigger(player, Action.NATURAL_MUTATION, 1, tier, category);
            }
            if (source.equals("environment") || def.conditions().isEnvironmental()) {
                GeneticsTrigger.trigger(player, Action.ENVIRONMENTAL_MUTATION, 1, tier, category);
            }
            if (research.level >= 4) {
                GeneticsTrigger.trigger(player, Action.STUDY_MUTATION, 1, tier, category);
            }
            if (genome.inheritedMutations.contains(id)) {
                GeneticsTrigger.trigger(player, Action.INHERITED_MUTATION, 1, tier, category);
            }
            if (def.conditions().isCombination()) {
                GeneticsTrigger.trigger(player, Action.COMBINATION, 1, tier, category);
            }
        }
        if (GeneticsService.effectiveStability(pokemon, genome, shadow) < 35) {
            GeneticsTrigger.trigger(player, Action.UNSTABLE, 1);
        }
        if (GeneticsService.corruptionResistance(genome) >= 0.4) {
            GeneticsTrigger.trigger(player, Action.SHADOW_RESISTANT, 1);
        }
        if (shadow != null && shadow.active) {
            GeneticsTrigger.trigger(player, Action.SHADOW_ANALYZE, (int) Math.round(shadow.corruption));
            if (pokemon.isWild()) {
                GeneticsTrigger.trigger(player, Action.SHADOW_ENCOUNTER, 1);
            }
        }
        if (shadow != null && shadow.purified) {
            GeneticsTrigger.trigger(player, Action.ANALYZE_PURIFIED, 1);
        }
        Research.checkMilestones(player);
    }

    private static List<Row> prediction(Genome a, Genome b) {
        GeneticsRules.Prediction p = GeneticsRules.predict(a, b, GeneticsConfig.get(), GeneticsRegistry.loci());
        List<Row> rows = new ArrayList<>();
        rows.add(row("potential", Component.literal(round(p.potential().min()) + " – " + round(p.potential().max())),
                -1, INFO));
        rows.add(row("stability", Component.literal(round(p.stability().min()) + " – " + round(p.stability().max())),
                -1, p.incompatible() ? BAD : INFO));
        if (p.incompatible()) {
            rows.add(row("compatibility", Component.translatable("analysis.cobblegenetics.incompatible"), -1, BAD));
        }
        for (GeneticsRules.TraitOdds odds : p.traits()) {
            TraitLocus locus = GeneticsRegistry.locus(odds.locus());
            if (locus == null) {
                continue;
            }
            MutableComponent text = Component.empty();
            for (Map.Entry<String, Double> entry : odds.odds().entrySet()) {
                TraitLocus.Allele allele = locus.allele(entry.getKey());
                if (!text.getSiblings().isEmpty()) {
                    text.append(", ");
                }
                text.append(Component.translatableWithFallback("trait.cobblegenetics." + locus.id() + "."
                        + entry.getKey(), allele == null ? entry.getKey() : allele.name()))
                        .append(" " + Math.round(Math.min(1, entry.getValue()) * 100) + "%");
            }
            rows.add(row(Component.translatableWithFallback("trait.cobblegenetics." + locus.id(), locus.name()), text,
                    -1, 0xFFDDDDDD));
        }
        for (String id : p.possibleEmergence()) {
            MutationDef def = GeneticsRegistry.mutation(id);
            rows.add(row("emergence", def == null ? Component.literal(id) : mutationName(def), -1, 0xFFB46CFF));
        }
        return rows;
    }

    private static List<Row> combinationHints(Genome genome) {
        List<Row> rows = new ArrayList<>();
        var alleles = GeneticsService.expressedAlleles(genome);
        for (MutationDef def : GeneticsRegistry.mutations()) {
            if (!def.conditions().isCombination() || genome.hasMutation(def.id())) {
                continue;
            }
            int met = def.conditions().metRequirements(genome.mutations, alleles);
            int total = def.conditions().totalRequirements();
            if (met > 0) {
                rows.add(row(Component.translatable("analysis.cobblegenetics.unknown_combination",
                        Component.translatable("tier.cobblegenetics." + def.tier().id())),
                        Component.translatable("analysis.cobblegenetics.factors", met, total),
                        (float) met / total, def.tier().color));
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ Genetic Record

    /** "Record" button: writes the last analysis onto a Genetic Record, using one paper. */
    public static void record(ServerPlayer player, UUID pokemon) {
        LastAnalysis last = LAST.get(player.getUUID());
        if (last == null || !last.pokemon().equals(pokemon)) {
            return;
        }
        if (!player.isCreative() && !consume(player, new ItemStack(Items.PAPER))) {
            player.displayClientMessage(Component.translatable("message.cobblegenetics.record_needs_paper")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack stack = new ItemStack(ModItems.GENETIC_RECORD.get());
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.cobblegenetics.genetic_record.named",
                last.name()).withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.AQUA)));
        List<Component> lines = new ArrayList<>();
        for (Component line : last.recordLines()) {
            lines.add(line.copy().withStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.GRAY)));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines.subList(0, Math.min(lines.size(), 16))));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        ResearchData research = Research.of(player);
        research.records++;
        Research.save(player, research);
        GeneticsTrigger.trigger(player, Action.RECORD, research.records);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 1);
    }

    static boolean consume(ServerPlayer player, ItemStack wanted) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(wanted.getItem())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    public static Component mutationName(MutationDef def) {
        return Component.translatableWithFallback("mutation." + def.id().replace(':', '.'), def.name());
    }

    static Component typeName(String type) {
        var elemental = ElementalTypes.get(type);
        return elemental == null ? Component.literal(type) : elemental.getDisplayName();
    }

    private static Row row(String key, Component value, float bar, int color) {
        return new Row(Component.translatable("analysis.cobblegenetics." + key), value, bar, color, false);
    }

    private static Row row(Component label, Component value, float bar, int color) {
        return new Row(label, value, bar, color, false);
    }

    private static Row locked(String key, int level) {
        return new Row(Component.translatable("analysis.cobblegenetics." + key),
                Component.translatable("analysis.cobblegenetics.locked", level), -1, 0xFF666666, true);
    }

    private static Component line(String key, Component value) {
        return Component.translatable("analysis.cobblegenetics." + key).append(": ").append(value.copy());
    }

    private static int gradeColor(double value) {
        return value >= 70 ? GOOD : value >= 40 ? MID : BAD;
    }

    private static long round(double value) {
        return Math.round(value);
    }
}
