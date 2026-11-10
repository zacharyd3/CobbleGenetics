package com.cobblegenetics.data;

import com.cobblegenetics.CobbleGenetics;
import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.shadow.ShadowData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;

/**
 * Where CobbleGenetics keeps its data on a Pokémon: {@code persistentData["cobblegenetics"]} with "genome" and
 * "shadow" compounds. persistentData is Cobblemon's own per-Pokémon extension storage: it is saved with the Pokémon
 * wherever it is (party, PC, an entity in the world) and moves with it when traded or evolved, so nothing is kept in
 * temporary entity NBT.
 *
 * <p>Reads never create data. Writes call {@code onChange} (so the store saves the Pokémon) and {@code updateAspects}
 * (so the visual aspects reach clients).</p>
 */
public final class PokemonGenetics {

    public static final String KEY = "cobblegenetics";

    private PokemonGenetics() {
    }

    /** Our compound, or null while Cobblemon is still constructing the Pokémon (persistentData is null then). */
    private static CompoundTag root(Pokemon pokemon, boolean create) {
        CompoundTag persistent = pokemon.getPersistentData();
        if (persistent == null) {
            return null;
        }
        if (!persistent.contains(KEY)) {
            if (!create) {
                return null;
            }
            persistent.put(KEY, new CompoundTag());
        }
        return persistent.getCompound(KEY);
    }

    public static boolean hasGenome(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        return root != null && root.contains("genome");
    }

    /** The genome, or null if this Pokémon has none yet (see GeneticsService.ensure). */
    public static Genome genome(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        if (root == null || !root.contains("genome")) {
            return null;
        }
        try {
            return GenomeNbt.read(root.getCompound("genome"));
        } catch (RuntimeException e) {
            CobbleGenetics.LOGGER.warn("CobbleGenetics: unreadable genome on {}, ignoring it", pokemon.getUuid(), e);
            return null;
        }
    }

    public static void setGenome(Pokemon pokemon, Genome genome) {
        CompoundTag root = root(pokemon, true);
        if (root == null) {
            return;
        }
        root.put("genome", GenomeNbt.write(genome));
        changed(pokemon);
    }

    public static ShadowData shadow(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        if (root == null || !root.contains("shadow")) {
            return null;
        }
        return GenomeNbt.readShadow(root.getCompound("shadow"));
    }

    public static boolean isShadow(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        return root != null && root.getCompound("shadow").getBoolean("active");
    }

    public static void setShadow(Pokemon pokemon, ShadowData data) {
        CompoundTag root = root(pokemon, true);
        if (root == null) {
            return;
        }
        root.put("shadow", GenomeNbt.writeShadow(data));
        changed(pokemon);
    }

    /** A pending genome attached to an egg/property that must be applied when the Pokémon is complete. */
    public static void setPending(Pokemon pokemon, CompoundTag genome) {
        CompoundTag root = root(pokemon, true);
        if (root != null) {
            root.put("pending", genome);
        }
    }

    public static CompoundTag takePending(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        if (root == null || !root.contains("pending")) {
            return null;
        }
        CompoundTag pending = root.getCompound("pending");
        root.remove("pending");
        return pending;
    }

    /**
     * Shadow status / mutations requested by a Cobblemon property (cg_shadow=..., cg_mutation=...) while the Pokémon
     * was being created. Resolved by GeneticsService.ensure once the Pokémon is complete.
     */
    public static void requestShadow(Pokemon pokemon, double corruption) {
        CompoundTag root = root(pokemon, true);
        if (root != null) {
            root.putDouble("pendingShadow", corruption);
        }
    }

    public static void requestMutation(Pokemon pokemon, String mutation) {
        CompoundTag root = root(pokemon, true);
        if (root != null) {
            String existing = root.getString("pendingMutations");
            root.putString("pendingMutations", existing.isEmpty() ? mutation : existing + "," + mutation);
        }
    }

    public static boolean hasRequests(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        return root != null && (root.contains("pendingShadow") || root.contains("pendingMutations")
                || root.contains("pending"));
    }

    /** Returns and clears the pending shadow corruption (NaN if none). */
    public static double takeShadowRequest(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        if (root == null || !root.contains("pendingShadow")) {
            return Double.NaN;
        }
        double value = root.getDouble("pendingShadow");
        root.remove("pendingShadow");
        return value;
    }

    public static String[] takeMutationRequests(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        if (root == null || !root.contains("pendingMutations")) {
            return new String[0];
        }
        String value = root.getString("pendingMutations");
        root.remove("pendingMutations");
        return value.isEmpty() ? new String[0] : value.split(",");
    }

    /** Last time (game time) this Pokémon was used as an Incubator parent. */
    public static long lastBred(Pokemon pokemon) {
        CompoundTag root = root(pokemon, false);
        return root == null ? Long.MIN_VALUE : (root.contains("lastBred") ? root.getLong("lastBred") : Long.MIN_VALUE);
    }

    public static void setLastBred(Pokemon pokemon, long time) {
        CompoundTag root = root(pokemon, true);
        if (root != null) {
            root.putLong("lastBred", time);
        }
    }

    public static String affinityOf(Pokemon pokemon) {
        Genome genome = genome(pokemon);
        return genome == null ? null : genome.affinity;
    }

    private static void changed(Pokemon pokemon) {
        // Tell Cobblemon the Pokémon changed so its store saves it; then recompute aspects for the visuals
        pokemon.onChange(null);
        pokemon.updateAspects();
    }
}
