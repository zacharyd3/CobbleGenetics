package com.cobblegenetics.research;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A player's genetic research: level and the counters milestones and advancements use. Saved with the player. */
public final class ResearchData implements INBTSerializable<CompoundTag> {

    public static final int MAX_LEVEL = 5;

    public int level = 1;
    public int analyses;
    public int offspringAnalyzed;
    public int bred;
    public int records;
    public int purified;
    /** Eggs a Day Care Aide hatched for this player. */
    public int daycareHatched;
    public final Set<String> mutationsDiscovered = new LinkedHashSet<>();
    /** Pokémon already counted for "analyses" (re-analysing the same one does not count again). Capped. */
    public final Set<String> analyzedPokemon = new LinkedHashSet<>();
    /** Best breeding value analysed per species ("cobblemon:pikachu" -> 0-100), the target to beat. Capped. */
    public final java.util.Map<String, Integer> bestBreedingValue = new java.util.LinkedHashMap<>();
    /** The previously analysed Pokémon, for breeding predictions (not saved). */
    public transient UUID lastAnalyzed;

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putInt("analyses", analyses);
        tag.putInt("offspringAnalyzed", offspringAnalyzed);
        tag.putInt("bred", bred);
        tag.putInt("records", records);
        tag.putInt("purified", purified);
        tag.putInt("daycareHatched", daycareHatched);
        ListTag mutations = new ListTag();
        mutationsDiscovered.forEach(id -> mutations.add(StringTag.valueOf(id)));
        tag.put("mutations", mutations);
        ListTag analyzed = new ListTag();
        analyzedPokemon.forEach(id -> analyzed.add(StringTag.valueOf(id)));
        tag.put("analyzed", analyzed);
        CompoundTag best = new CompoundTag();
        bestBreedingValue.forEach(best::putInt);
        tag.put("bestBreedingValue", best);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        level = Math.max(1, Math.min(MAX_LEVEL, tag.getInt("level")));
        analyses = tag.getInt("analyses");
        offspringAnalyzed = tag.getInt("offspringAnalyzed");
        bred = tag.getInt("bred");
        records = tag.getInt("records");
        purified = tag.getInt("purified");
        daycareHatched = tag.getInt("daycareHatched");
        mutationsDiscovered.clear();
        ListTag mutations = tag.getList("mutations", Tag.TAG_STRING);
        for (int i = 0; i < mutations.size(); i++) {
            mutationsDiscovered.add(mutations.getString(i));
        }
        analyzedPokemon.clear();
        ListTag analyzed = tag.getList("analyzed", Tag.TAG_STRING);
        for (int i = 0; i < analyzed.size(); i++) {
            analyzedPokemon.add(analyzed.getString(i));
        }
        bestBreedingValue.clear();
        CompoundTag best = tag.getCompound("bestBreedingValue");
        for (String species : best.getAllKeys()) {
            bestBreedingValue.put(species, best.getInt(species));
        }
    }

    public void recordBreedingValue(String species, int value) {
        bestBreedingValue.put(species, value);
        if (bestBreedingValue.size() > 2000) {
            bestBreedingValue.remove(bestBreedingValue.keySet().iterator().next());
        }
    }

    /** Remembers an analysed Pokémon; false if it was already counted. Keeps the set bounded. */
    public boolean countAnalysis(UUID pokemon) {
        if (!analyzedPokemon.add(pokemon.toString())) {
            return false;
        }
        if (analyzedPokemon.size() > 2000) {
            analyzedPokemon.remove(analyzedPokemon.iterator().next());
        }
        analyses++;
        return true;
    }
}
