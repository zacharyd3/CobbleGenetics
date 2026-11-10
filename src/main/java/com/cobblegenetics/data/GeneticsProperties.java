package com.cobblegenetics.data;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.config.GeneticsConfig;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblemon.mod.common.api.properties.CustomPokemonProperty;
import com.cobblemon.mod.common.api.properties.CustomPokemonPropertyType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Cobblemon properties added by CobbleGenetics. They work everywhere Cobblemon reads Pokémon properties: commands
 * ({@code /pokespawn zubat cg_shadow=80}), spawn pools of datapacks (special structures, events), NPC/trainer
 * teams, boss mods, and FTB Quests command rewards.
 * <ul>
 *   <li>{@code cg_shadow=true|<corruption 1-100>} - a Shadow Pokémon (true = random corruption from the config range).
 *       As a condition: is a Shadow Pokémon.</li>
 *   <li>{@code cg_mutation=<id>} - with that mutation ("albinism" or "namespace:id"). As a condition: has it.</li>
 *   <li>{@code cg_genome=<data>} - an inherited genome, written by the breeding hook into egg properties so it
 *       survives until the egg hatches. Not meant to be typed by hand.</li>
 * </ul>
 * Properties are applied while Cobblemon is still building the Pokémon, so they only leave a request that
 * GeneticsService.ensure resolves once the Pokémon is complete.
 */
public final class GeneticsProperties {

    private GeneticsProperties() {
    }

    public static void register() {
        CustomPokemonProperty.Companion.register(new ShadowType());
        CustomPokemonProperty.Companion.register(new MutationType());
        CustomPokemonProperty.Companion.register(new GenomeType());
    }

    // ------------------------------------------------------------------ cg_shadow

    public record ShadowProperty(double corruption) implements CustomPokemonProperty {
        @Override
        public String asString() {
            return "cg_shadow=" + (corruption < 0 ? "true" : String.valueOf((int) corruption));
        }

        @Override
        public void apply(Pokemon pokemon) {
            double value = corruption;
            if (value < 0) {
                GeneticsConfig config = GeneticsConfig.get();
                value = config.shadowMinCorruption
                        + new Random().nextInt(config.shadowMaxCorruption - config.shadowMinCorruption + 1);
            }
            PokemonGenetics.requestShadow(pokemon, value);
        }

        @Override
        public boolean matches(Pokemon pokemon) {
            ShadowData data = PokemonGenetics.shadow(pokemon);
            return data != null && data.active && (corruption < 0 || data.corruption >= corruption);
        }
    }

    public static final class ShadowType implements CustomPokemonPropertyType<ShadowProperty> {
        @Override
        public Iterable<String> getKeys() {
            return List.of("cg_shadow");
        }

        @Override
        public boolean getNeedsKey() {
            return true;
        }

        @Override
        public ShadowProperty fromString(String value) {
            if (value == null || value.isBlank() || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
                return new ShadowProperty(-1);
            }
            if (value.equalsIgnoreCase("false")) {
                return new ShadowProperty(0);
            }
            try {
                return new ShadowProperty(Math.max(1, Math.min(100, Double.parseDouble(value))));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public Collection<String> examples() {
            return List.of("true", "50", "90");
        }
    }

    // ------------------------------------------------------------------ cg_mutation

    public record MutationProperty(String mutation) implements CustomPokemonProperty {
        @Override
        public String asString() {
            return "cg_mutation=" + mutation;
        }

        @Override
        public void apply(Pokemon pokemon) {
            PokemonGenetics.requestMutation(pokemon, mutation);
        }

        @Override
        public boolean matches(Pokemon pokemon) {
            Genome genome = PokemonGenetics.genome(pokemon);
            String id = mutation.contains(":") ? mutation : CobbleGenetics.MOD_ID + ":" + mutation;
            return genome != null && genome.hasMutation(id);
        }
    }

    public static final class MutationType implements CustomPokemonPropertyType<MutationProperty> {
        @Override
        public Iterable<String> getKeys() {
            return List.of("cg_mutation");
        }

        @Override
        public boolean getNeedsKey() {
            return true;
        }

        @Override
        public MutationProperty fromString(String value) {
            return value == null || value.isBlank() ? null : new MutationProperty(value.trim());
        }

        @Override
        public Collection<String> examples() {
            List<String> ids = new ArrayList<>();
            for (MutationDef def : GeneticsRegistry.mutations()) {
                ids.add(def.id().startsWith(CobbleGenetics.MOD_ID + ":")
                        ? def.id().substring(CobbleGenetics.MOD_ID.length() + 1) : def.id());
            }
            return ids;
        }
    }

    // ------------------------------------------------------------------ cg_genome

    public record GenomeProperty(String encoded) implements CustomPokemonProperty {
        @Override
        public String asString() {
            return "cg_genome=" + encoded;
        }

        @Override
        public void apply(Pokemon pokemon) {
            CompoundTag tag = decode(encoded);
            if (tag != null && !PokemonGenetics.hasGenome(pokemon)) {
                PokemonGenetics.setPending(pokemon, tag);
            }
        }

        @Override
        public boolean matches(Pokemon pokemon) {
            return PokemonGenetics.hasGenome(pokemon);
        }
    }

    public static final class GenomeType implements CustomPokemonPropertyType<GenomeProperty> {
        @Override
        public Iterable<String> getKeys() {
            return List.of("cg_genome");
        }

        @Override
        public boolean getNeedsKey() {
            return true;
        }

        @Override
        public GenomeProperty fromString(String value) {
            return value == null || value.isBlank() || decode(value) == null ? null : new GenomeProperty(value);
        }

        @Override
        public Collection<String> examples() {
            return List.of();
        }
    }

    /** Compressed NBT in URL-safe base64 without padding: no spaces or '=' that would break property parsing. */
    public static String encode(Genome genome) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(GenomeNbt.write(genome), bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static CompoundTag decode(String text) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(text.trim());
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(64 * 1024));
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }
}
