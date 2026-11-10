package com.cobblegenetics.effect;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry of effect types: the "vocabulary" mutation and trait JSON files use. Addons register their own with
 * {@link #register} during mod construction or common setup; datapacks can then use them.
 */
public final class EffectTypes {

    private static final Map<String, Function<JsonObject, GeneticEffect>> TYPES = new ConcurrentHashMap<>();

    private EffectTypes() {
    }

    public static void register(String type, Function<JsonObject, GeneticEffect> factory) {
        TYPES.put(type, factory);
    }

    /** Builds an effect from {"type": "...", ...}. Throws IllegalArgumentException for unknown or broken entries. */
    public static GeneticEffect parse(JsonObject json) {
        if (!json.has("type")) {
            throw new IllegalArgumentException("effect without \"type\"");
        }
        String type = json.get("type").getAsString();
        Function<JsonObject, GeneticEffect> factory = TYPES.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("unknown effect type \"" + type + "\" (known: " + TYPES.keySet() + ")");
        }
        return factory.apply(json);
    }

    public static Set<String> names() {
        return Set.copyOf(TYPES.keySet());
    }

    static {
        BuiltinEffects.registerAll();
    }
}
