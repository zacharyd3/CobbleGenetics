package com.cobblegenetics.effect;

import com.cobblegenetics.config.GeneticsConfig;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The effect types that ship with CobbleGenetics. Each is a tiny record; see docs/data-format.md for their JSON.
 *
 * <p>Balance rule: stat effects work through IVs, which Cobblemon already sends to Showdown, so they matter in battle
 * and can never exceed what a perfect wild Pokémon has (31). Genetics make a Pokémon special, not unbeatable.</p>
 */
public final class BuiltinEffects {

    private BuiltinEffects() {
    }

    static void registerAll() {
        EffectTypes.register("iv", json -> new IvEffect(stat(str(json, "stat", "hp")), integer(json, "amount", 3)));
        EffectTypes.register("all_ivs", json -> new AllIvsEffect(integer(json, "amount", 2)));
        EffectTypes.register("scale", json -> new ScaleEffect((float) number(json, "multiplier", 1.2)));
        EffectTypes.register("tint", json -> new TintEffect(color(str(json, "color", "#ffffff"))));
        EffectTypes.register("glow", json -> new GlowEffect());
        EffectTypes.register("aspect", json -> new AspectEffect(str(json, "aspect", "")));
        EffectTypes.register("tera_shift", json -> new TeraShiftEffect(str(json, "tera", "affinity")));
        EffectTypes.register("hidden_ability", json -> new HiddenAbilityEffect());
        EffectTypes.register("exp_multiplier", json -> new ExpEffect(number(json, "multiplier", 1.1),
                optional(json, "min_temperature"), optional(json, "max_temperature")));
        EffectTypes.register("erratic_exp", json -> new ErraticExpEffect(number(json, "min", 0.8), number(json, "max", 1.3)));
        EffectTypes.register("friendship_multiplier", json -> new FriendshipEffect(number(json, "multiplier", 1.25)));
        EffectTypes.register("corruption_resistance", json -> new CorruptionResistanceEffect(number(json, "amount", 0.3)));
        EffectTypes.register("stability", json -> new StabilityEffect(number(json, "amount", 5)));
        EffectTypes.register("regeneration", json -> new RegenerationEffect(number(json, "fraction", 0.1)));
        EffectTypes.register("purification_speed", json -> new PurificationSpeedEffect(number(json, "multiplier", 1.5)));
    }

    // ------------------------------------------------------------------ stats (one-shot, through IVs)

    public record IvEffect(Stat stat, int amount) implements GeneticEffect {
        public String type() {
            return "iv";
        }

        public void onAcquire(Pokemon pokemon) {
            addIv(pokemon, stat, amount);
        }

        public Component describe() {
            return Component.translatable(amount >= 0 ? "effect.cobblegenetics.iv_up" : "effect.cobblegenetics.iv_down",
                    stat.getDisplayName(), Math.abs(amount));
        }
    }

    public record AllIvsEffect(int amount) implements GeneticEffect {
        public String type() {
            return "all_ivs";
        }

        public void onAcquire(Pokemon pokemon) {
            for (Stat stat : MAIN_STATS) {
                addIv(pokemon, stat, amount);
            }
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.all_ivs", amount);
        }
    }

    static final List<Stat> MAIN_STATS = List.of(Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE, Stats.SPEED);

    static void addIv(Pokemon pokemon, Stat stat, int amount) {
        Integer current = pokemon.getIvs().get(stat);
        int value = (current == null ? 0 : current) + amount;
        pokemon.setIV(stat, Math.max(0, Math.min(31, value)));
    }

    // ------------------------------------------------------------------ appearance

    public record ScaleEffect(float multiplier) implements GeneticEffect {
        public String type() {
            return "scale";
        }

        public void onAcquire(Pokemon pokemon) {
            // Multiplies whatever is there (Cobblemon default, another size mod, the genetic size gene)
            float scale = pokemon.getScaleModifier() * multiplier;
            pokemon.setScaleModifier(Math.max(0.3f, Math.min(3f, scale)));
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.scale", Math.round(multiplier * 100));
        }
    }

    /** Colour tint, sent to clients as the aspect "cg-tint-rrggbb" and applied by the client renderer. */
    public record TintEffect(int rgb) implements GeneticEffect {
        public String type() {
            return "tint";
        }

        public void aspects(Set<String> out) {
            out.add(String.format(Locale.ROOT, "cg-tint-%06x", rgb & 0xFFFFFF));
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.tint", String.format(Locale.ROOT, "#%06X", rgb));
        }
    }

    /** Glowing particles around the Pokémon (client side, aspect "cg-glow"). */
    public record GlowEffect() implements GeneticEffect {
        public String type() {
            return "glow";
        }

        public void aspects(Set<String> out) {
            out.add("cg-glow");
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.glow");
        }
    }

    /**
     * A plain aspect for resource packs: e.g. "cg-heterochromia" lets a pack give the Pokémon different eyes through a
     * Cobblemon resolver variation, without any code.
     */
    public record AspectEffect(String aspect) implements GeneticEffect {
        public String type() {
            return "aspect";
        }

        public void aspects(Set<String> out) {
            if (!aspect.isBlank()) {
                out.add(aspect);
            }
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.aspect", aspect);
        }
    }

    // ------------------------------------------------------------------ typing and abilities

    /**
     * Changes the Tera Type to a type name, or to the Pokémon's elemental affinity ("affinity"). Real secondary-type
     * changes are not offered: Showdown takes types from the species, so they would need a custom species per
     * Pokémon. The Tera Type is Cobblemon's own per-individual type and works in battle as-is.
     */
    public record TeraShiftEffect(String tera) implements GeneticEffect {
        public String type() {
            return "tera_shift";
        }

        public void onAcquire(Pokemon pokemon) {
            if (!GeneticsConfig.get().mutationsCanChangeTeraType) {
                return;
            }
            String name = tera;
            if (name.equals("affinity")) {
                name = com.cobblegenetics.data.PokemonGenetics.affinityOf(pokemon);
            }
            ElementalType type = name == null ? null : ElementalTypes.get(name);
            if (type != null) {
                TeraType teraType = TeraTypes.forElementalType(type);
                if (teraType != null) {
                    pokemon.setTeraType(teraType);
                }
            }
        }

        public Component describe() {
            return tera.equals("affinity") ? Component.translatable("effect.cobblegenetics.tera_affinity")
                    : Component.translatable("effect.cobblegenetics.tera", tera);
        }
    }

    /** Unlocks the species' hidden ability, if it has one (Cobblemon's own ability system: works in battle). */
    public record HiddenAbilityEffect() implements GeneticEffect {
        public String type() {
            return "hidden_ability";
        }

        public void onAcquire(Pokemon pokemon) {
            if (!GeneticsConfig.get().mutationsCanModifyAbilities) {
                return;
            }
            for (PotentialAbility potential : pokemon.getForm().getAbilities()) {
                if (potential instanceof HiddenAbility hidden) {
                    pokemon.updateAbility(hidden.getTemplate().create(false, hidden.getPriority()));
                    return;
                }
            }
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.hidden_ability");
        }
    }

    // ------------------------------------------------------------------ passive

    public record ExpEffect(double multiplier, Double minTemperature, Double maxTemperature) implements GeneticEffect {
        public String type() {
            return "exp_multiplier";
        }

        public double expMultiplier(ExpContext context) {
            if (minTemperature != null && context.temperature() < minTemperature) {
                return 1;
            }
            if (maxTemperature != null && context.temperature() > maxTemperature) {
                return 1;
            }
            return multiplier;
        }

        public Component describe() {
            boolean conditional = minTemperature != null || maxTemperature != null;
            String key = conditional ? "effect.cobblegenetics.exp_climate" : "effect.cobblegenetics.exp";
            String climate = minTemperature != null && minTemperature >= 1.0 ? "hot"
                    : maxTemperature != null && maxTemperature <= 0.3 ? "cold" : "temperate";
            return Component.translatable(key, signedPercent(multiplier),
                    Component.translatable("climate.cobblegenetics." + climate));
        }
    }

    /** "Unpredictable behaviour": every EXP gain is multiplied by a random value. */
    public record ErraticExpEffect(double min, double max) implements GeneticEffect {
        public String type() {
            return "erratic_exp";
        }

        public double expMultiplier(ExpContext context) {
            return min + context.random().nextDouble() * (max - min);
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.erratic_exp", Math.round(min * 100),
                    Math.round(max * 100));
        }
    }

    public record FriendshipEffect(double multiplier) implements GeneticEffect {
        public String type() {
            return "friendship_multiplier";
        }

        public double friendshipMultiplier() {
            return multiplier;
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.friendship", signedPercent(multiplier));
        }
    }

    public record CorruptionResistanceEffect(double amount) implements GeneticEffect {
        public String type() {
            return "corruption_resistance";
        }

        public double corruptionResistance() {
            // Negative = more vulnerable (Fragile Genome)
            return Math.max(-1, Math.min(0.95, amount));
        }

        public Component describe() {
            return Component.translatable(amount >= 0 ? "effect.cobblegenetics.corruption_resistance"
                    : "effect.cobblegenetics.corruption_weakness", Math.round(Math.abs(amount) * 100));
        }
    }

    public record StabilityEffect(double amount) implements GeneticEffect {
        public String type() {
            return "stability";
        }

        public double stabilityShift() {
            return amount;
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.stability", (amount >= 0 ? "+" : "") + Math.round(amount));
        }
    }

    public record RegenerationEffect(double fraction) implements GeneticEffect {
        public String type() {
            return "regeneration";
        }

        public double regeneration() {
            return fraction;
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.regeneration", Math.round(fraction * 100));
        }
    }

    public record PurificationSpeedEffect(double multiplier) implements GeneticEffect {
        public String type() {
            return "purification_speed";
        }

        public double purificationMultiplier() {
            return multiplier;
        }

        public Component describe() {
            return Component.translatable("effect.cobblegenetics.purification_speed", signedPercent(multiplier));
        }
    }

    // ------------------------------------------------------------------ JSON helpers

    static String signedPercent(double multiplier) {
        long percent = Math.round((multiplier - 1) * 100);
        return (percent >= 0 ? "+" : "") + percent + "%";
    }

    static Stat stat(String name) {
        return switch (name.toLowerCase(Locale.ROOT).replace("-", "_")) {
            case "hp" -> Stats.HP;
            case "attack", "atk" -> Stats.ATTACK;
            case "defence", "defense", "def" -> Stats.DEFENCE;
            case "special_attack", "spa", "sp_atk" -> Stats.SPECIAL_ATTACK;
            case "special_defence", "special_defense", "spd", "sp_def" -> Stats.SPECIAL_DEFENCE;
            case "speed", "spe" -> Stats.SPEED;
            default -> throw new IllegalArgumentException("unknown stat \"" + name + "\"");
        };
    }

    static String str(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    static Double optional(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsDouble() : null;
    }

    static int color(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        return Integer.parseInt(hex, 16) & 0xFFFFFF;
    }
}
