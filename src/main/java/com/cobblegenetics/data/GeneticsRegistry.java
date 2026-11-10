package com.cobblegenetics.data;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.effect.EffectTypes;
import com.cobblegenetics.effect.GeneticEffect;
import com.cobblegenetics.genetics.TraitLocus;
import com.cobblegenetics.mutation.MutationCategory;
import com.cobblegenetics.mutation.MutationConditions;
import com.cobblegenetics.mutation.MutationDef;
import com.cobblegenetics.mutation.MutationSource;
import com.cobblegenetics.mutation.MutationTier;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutations and inheritable traits, loaded from datapacks on server start and on {@code /reload}:
 * <pre>
 *   data/&lt;namespace&gt;/cobblegenetics/mutations/&lt;name&gt;.json   -> mutation "namespace:name"
 *   data/&lt;namespace&gt;/cobblegenetics/traits/&lt;locus&gt;.json     -> trait locus "locus"
 * </pre>
 * A broken file is skipped with a log line naming it; it never stops the server. Removing a mutation from the datapack
 * leaves its id on Pokémon that have it (harmless: it is ignored and shown as "unknown" until the datapack returns).
 */
public final class GeneticsRegistry {

    private static final Gson GSON = new Gson();

    private static volatile Map<String, MutationDef> mutations = Map.of();
    private static volatile Map<String, TraitLocus> traits = Map.of();

    private GeneticsRegistry() {
    }

    public static MutationDef mutation(String id) {
        return mutations.get(id);
    }

    public static Collection<MutationDef> mutations() {
        return mutations.values();
    }

    public static TraitLocus locus(String id) {
        return traits.get(id);
    }

    public static Collection<TraitLocus> loci() {
        return traits.values();
    }

    /** For tests. */
    public static void setForTests(Map<String, MutationDef> newMutations, Map<String, TraitLocus> newTraits) {
        mutations = Map.copyOf(newMutations);
        traits = Map.copyOf(newTraits);
    }

    // ------------------------------------------------------------------ reload listeners

    public static final class MutationLoader extends SimpleJsonResourceReloadListener {
        public MutationLoader() {
            super(GSON, "cobblegenetics/mutations");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, MutationDef> loaded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
                String id = entry.getKey().toString();
                try {
                    loaded.put(id, parseMutation(id, entry.getValue().getAsJsonObject()));
                } catch (RuntimeException e) {
                    CobbleGenetics.LOGGER.error("CobbleGenetics: skipping mutation {}: {}", id, e.getMessage());
                }
            }
            mutations = Map.copyOf(loaded);
            CobbleGenetics.LOGGER.info("CobbleGenetics: loaded {} mutations", loaded.size());
        }
    }

    public static final class TraitLoader extends SimpleJsonResourceReloadListener {
        public TraitLoader() {
            super(GSON, "cobblegenetics/traits");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, TraitLocus> loaded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
                // Loci are referenced by their file name only ("temperament"), so packs can override ours
                String id = entry.getKey().getPath();
                try {
                    loaded.put(id, parseLocus(id, entry.getValue().getAsJsonObject()));
                } catch (RuntimeException e) {
                    CobbleGenetics.LOGGER.error("CobbleGenetics: skipping trait {}: {}", entry.getKey(), e.getMessage());
                }
            }
            traits = Map.copyOf(loaded);
            CobbleGenetics.LOGGER.info("CobbleGenetics: loaded {} trait loci", loaded.size());
        }
    }

    // ------------------------------------------------------------------ parsing

    public static MutationDef parseMutation(String id, JsonObject json) {
        MutationCategory category = MutationCategory.parse(string(json, "category", ""));
        if (category == null) {
            throw new IllegalArgumentException("\"category\" must be physical, elemental, biological or ability");
        }
        MutationTier tier = MutationTier.parse(string(json, "tier", ""));
        if (tier == null) {
            throw new IllegalArgumentException("\"tier\" must be common, uncommon, rare, epic or legendary");
        }
        Set<MutationSource> sources = EnumSet.noneOf(MutationSource.class);
        if (json.has("sources")) {
            for (JsonElement element : json.getAsJsonArray("sources")) {
                MutationSource source = MutationSource.parse(element.getAsString());
                if (source == null) {
                    throw new IllegalArgumentException("unknown source \"" + element.getAsString() + "\"");
                }
                sources.add(source);
            }
        } else {
            sources.addAll(EnumSet.of(MutationSource.NATURAL, MutationSource.BREEDING, MutationSource.EVOLUTION,
                    MutationSource.ITEM));
        }
        return new MutationDef(id,
                string(json, "name", id),
                string(json, "description", ""),
                category, tier,
                json.has("negative") && json.get("negative").getAsBoolean(),
                json.has("weight") ? json.get("weight").getAsDouble() : 10,
                !json.has("inheritable") || json.get("inheritable").getAsBoolean(),
                json.has("inherit_chance") ? json.get("inherit_chance").getAsDouble() : 0.3,
                sources,
                json.has("conditions") ? parseConditions(json.getAsJsonObject("conditions")) : MutationConditions.NONE,
                parseEffects(json.getAsJsonArray("effects")),
                new LinkedHashSet<>(strings(json, "excludes")));
    }

    static MutationConditions parseConditions(JsonObject json) {
        return new MutationConditions(
                strings(json, "biomes"),
                optionalDouble(json, "min_temperature"),
                optionalDouble(json, "max_temperature"),
                json.has("time") ? json.get("time").getAsString() : null,
                json.has("weather") ? json.get("weather").getAsString() : null,
                json.has("max_light") ? json.get("max_light").getAsInt() : null,
                strings(json, "types"),
                strings(json, "affinities"),
                optionalDouble(json, "min_instability"),
                strings(json, "requires_mutations"),
                strings(json, "requires_traits"),
                json.has("requires_shadow_history") && json.get("requires_shadow_history").getAsBoolean(),
                optionalDouble(json, "min_peak_corruption"),
                json.has("min_friendship") ? json.get("min_friendship").getAsInt() : null,
                json.has("min_generation") ? json.get("min_generation").getAsInt() : null);
    }

    static TraitLocus parseLocus(String id, JsonObject json) {
        List<TraitLocus.Allele> alleles = new ArrayList<>();
        JsonArray array = json.getAsJsonArray("alleles");
        if (array == null || array.isEmpty()) {
            throw new IllegalArgumentException("a trait needs at least one allele");
        }
        for (JsonElement element : array) {
            JsonObject allele = element.getAsJsonObject();
            String alleleId = string(allele, "id", "");
            if (alleleId.isBlank() || alleleId.contains("/") || alleleId.contains(":")) {
                throw new IllegalArgumentException("allele ids must be non-empty and contain no '/' or ':'");
            }
            alleles.add(new TraitLocus.Allele(alleleId,
                    string(allele, "name", alleleId),
                    string(allele, "description", ""),
                    allele.has("dominance") ? allele.get("dominance").getAsInt() : 1,
                    allele.has("frequency") ? allele.get("frequency").getAsDouble() : 0.5,
                    parseEffects(allele.getAsJsonArray("effects"))));
        }
        return new TraitLocus(id, string(json, "name", id), List.copyOf(alleles));
    }

    static List<GeneticEffect> parseEffects(JsonArray array) {
        List<GeneticEffect> effects = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                effects.add(EffectTypes.parse(element.getAsJsonObject()));
            }
        }
        return List.copyOf(effects);
    }

    static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    static Double optionalDouble(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsDouble() : null;
    }

    static List<String> strings(JsonObject json, String key) {
        List<String> list = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                list.add(element.getAsString());
            }
        }
        return List.copyOf(list);
    }
}
