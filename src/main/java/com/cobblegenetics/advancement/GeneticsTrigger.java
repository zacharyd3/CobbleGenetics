package com.cobblegenetics.advancement;

import com.cobblegenetics.CobbleGenetics;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.Optional;

/**
 * Advancement criterion {@code cobblegenetics:genetics}. Every research milestone fires it from real gameplay code
 * (analysing, breeding, purifying...), so advancements and FTB Quests "advancement" tasks complete by themselves and
 * CobbleGenetics does not need to depend on FTB Quests.
 *
 * <p>Conditions (all optional): {@code action}, {@code min_count} (meaning depends on the action, see docs),
 * {@code max_count}, {@code tier} (minimum mutation tier), {@code category} (mutation category).</p>
 */
public class GeneticsTrigger extends SimpleCriterionTrigger<GeneticsTrigger.Instance> {

    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, CobbleGenetics.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, GeneticsTrigger> GENETICS =
            TRIGGERS.register("genetics", GeneticsTrigger::new);

    public static void initialize(IEventBus modBus) {
        TRIGGERS.register(modBus);
    }

    public enum Action implements StringRepresentable {
        /** count = Pokémon analysed in total. */
        ANALYZE,
        /** Analysed a Pokémon with potential >= highPotentialThreshold. count = its potential. */
        HIGH_POTENTIAL,
        /** Saved a Genetic Record. count = records made. */
        RECORD,
        /** Bred an offspring. count = offspring bred. */
        BREED,
        /** Analysed a Pokémon that has produced offspring. */
        ANALYZE_PARENT,
        /** Analysed a bred Pokémon. count = its generation. */
        ANALYZE_OFFSPRING,
        /** Analysed a bred Pokémon at research 3+ (traits visible): identified an inherited trait. */
        INHERITED_TRAIT,
        /** Analysed a bred Pokémon expressing a rare recessive allele. */
        RARE_TRAIT,
        /** Analysed a mutated Pokémon (tier/category = best mutation). count = distinct mutations discovered. */
        MUTATION,
        /** Analysed a Pokémon whose mutation came naturally (wild spawn, environment, event). */
        NATURAL_MUTATION,
        /** Analysed a mutated Pokémon at research 4+ (mutation details visible). */
        STUDY_MUTATION,
        /** Analysed a Pokémon with an inherited (or re-emerged) mutation. */
        INHERITED_MUTATION,
        /** Analysed a Pokémon with an environmental mutation. */
        ENVIRONMENTAL_MUTATION,
        /** Analysed a Pokémon with a combination mutation. */
        COMBINATION,
        /** Analysed a Pokémon with low effective stability (< 35). */
        UNSTABLE,
        /** Analysed a Pokémon with corruption resistance >= 40 %. */
        SHADOW_RESISTANT,
        /** Took part in a battle against a Shadow Pokémon (or analysed a wild one). */
        SHADOW_ENCOUNTER,
        SHADOW_CAPTURE,
        /** Analysed a Shadow Pokémon. count = its corruption. */
        SHADOW_ANALYZE,
        /** Lowered corruption for the first time. */
        PURIFICATION_START,
        /** Corruption was removed by an activity: category = time, battle, friendship, level, item, chamber. */
        PURIFY_ACTIVITY,
        /** Corruption of one of the player's Pokémon changed. count = corruption now. Use max_count. */
        CORRUPTION,
        /** Completed a purification. count = Pokémon purified in total. */
        PURIFY,
        /** Purification produced a mutation (tier/category of it). */
        PURIFIED_MUTATION,
        /** Analysed a purified Pokémon. */
        ANALYZE_PURIFIED,
        /** Research level reached. count = level. */
        RESEARCH_LEVEL,
        /** A mutation was induced with a Mutagen. */
        MUTAGEN,
        /** Levelling up raised an IV through Genetic Potential. count = the IV now. */
        POTENTIAL_TRAINING,
        /** Analysed (research 2+) a Pokémon whose affinity matches one of its own types. */
        RESONANCE,
        /** Analysed (research 2+) a Pokémon. count = its breeding value (0-100). */
        BREEDING_VALUE,
        /** A Day Care Aide hatched one of the player's Eggs. count = Eggs hatched that way. */
        DAYCARE_HATCH;

        public static final Codec<Action> CODEC = StringRepresentable.fromEnum(Action::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** tier/category may be null. tier is the ordinal of MutationTier (0 = common) or -1. */
    public static void trigger(ServerPlayer player, Action action, int count, int tier, String category) {
        GENETICS.get().trigger(player, instance -> instance.matches(action, count, tier, category));
    }

    public static void trigger(ServerPlayer player, Action action, int count) {
        trigger(player, action, count, -1, null);
    }

    public record Instance(Optional<ContextAwarePredicate> player, Optional<Action> action, Optional<Integer> minCount,
                           Optional<Integer> maxCount, Optional<String> tier, Optional<String> category)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Action.CODEC.optionalFieldOf("action").forGetter(Instance::action),
                Codec.INT.optionalFieldOf("min_count").forGetter(Instance::minCount),
                Codec.INT.optionalFieldOf("max_count").forGetter(Instance::maxCount),
                Codec.STRING.optionalFieldOf("tier").forGetter(Instance::tier),
                Codec.STRING.optionalFieldOf("category").forGetter(Instance::category)
        ).apply(builder, Instance::new));

        boolean matches(Action done, int count, int doneTier, String doneCategory) {
            if (action.isPresent() && action.get() != done) {
                return false;
            }
            if (minCount.isPresent() && count < minCount.get()) {
                return false;
            }
            if (maxCount.isPresent() && count > maxCount.get()) {
                return false;
            }
            if (tier.isPresent()) {
                com.cobblegenetics.mutation.MutationTier wanted = com.cobblegenetics.mutation.MutationTier.parse(tier.get());
                if (wanted == null || doneTier < wanted.ordinal()) {
                    return false;
                }
            }
            return category.map(c -> c.equalsIgnoreCase(doneCategory)).orElse(true);
        }
    }
}
