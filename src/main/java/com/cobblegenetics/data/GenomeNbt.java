package com.cobblegenetics.data;

import com.cobblegenetics.genetics.Genome;
import com.cobblegenetics.shadow.ShadowData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;

/** Reads and writes {@link Genome} and {@link ShadowData} as NBT. Unknown keys are ignored, missing ones defaulted. */
public final class GenomeNbt {

    public static final int VERSION = 1;

    private GenomeNbt() {
    }

    public static CompoundTag write(Genome genome) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", VERSION);
        tag.putDouble("potential", genome.potential);
        tag.putDouble("stability", genome.stability);
        tag.putDouble("susceptibility", genome.susceptibility);
        tag.putDouble("damage", genome.stabilityDamage);
        tag.putString("affinity", genome.affinity);
        tag.putDouble("affinityStrength", genome.affinityStrength);
        tag.putDouble("size", genome.size);
        CompoundTag traits = new CompoundTag();
        genome.traits.forEach((locus, genotype) -> traits.putString(locus, genotype.encode()));
        tag.put("traits", traits);
        CompoundTag mutations = new CompoundTag();
        for (String id : genome.mutations) {
            mutations.putString(id, genome.mutationSources.getOrDefault(id, "unknown"));
        }
        tag.put("mutations", mutations);
        tag.put("latent", strings(genome.latent));
        tag.put("inherited", strings(genome.inheritedMutations));
        tag.put("parents", strings(genome.parents));
        tag.putInt("generation", genome.generation);
        tag.putString("origin", genome.origin.name());
        tag.putBoolean("applied", genome.phenotypeApplied);
        tag.putInt("offspring", genome.offspring);
        return tag;
    }

    public static Genome read(CompoundTag tag) {
        Genome genome = new Genome();
        genome.potential = tag.getDouble("potential");
        genome.stability = tag.getDouble("stability");
        genome.susceptibility = tag.getDouble("susceptibility");
        genome.stabilityDamage = tag.getDouble("damage");
        genome.affinity = tag.contains("affinity") ? tag.getString("affinity") : "normal";
        genome.affinityStrength = tag.getDouble("affinityStrength");
        genome.size = tag.getDouble("size");
        CompoundTag traits = tag.getCompound("traits");
        for (String locus : traits.getAllKeys()) {
            Genome.Genotype genotype = Genome.Genotype.decode(traits.getString(locus));
            if (genotype != null) {
                genome.traits.put(locus, genotype);
            }
        }
        CompoundTag mutations = tag.getCompound("mutations");
        for (String id : mutations.getAllKeys()) {
            genome.addMutation(id, mutations.getString(id));
        }
        readStrings(tag, "latent", genome.latent);
        readStrings(tag, "inherited", genome.inheritedMutations);
        readStrings(tag, "parents", genome.parents);
        genome.generation = tag.getInt("generation");
        try {
            genome.origin = Genome.Origin.valueOf(tag.getString("origin"));
        } catch (IllegalArgumentException e) {
            genome.origin = Genome.Origin.RETROACTIVE;
        }
        genome.phenotypeApplied = tag.getBoolean("applied");
        genome.offspring = tag.getInt("offspring");
        return genome;
    }

    public static CompoundTag writeShadow(ShadowData data) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", data.active);
        tag.putDouble("corruption", data.corruption);
        tag.putDouble("peak", data.peakCorruption);
        tag.putDouble("start", data.startingCorruption);
        tag.putBoolean("purified", data.purified);
        tag.putDouble("fromTime", data.fromTime);
        tag.putDouble("fromBattles", data.fromBattles);
        tag.putDouble("fromFriendship", data.fromFriendship);
        tag.putDouble("fromItems", data.fromItems);
        tag.putDouble("fromChamber", data.fromChamber);
        tag.putInt("damageSteps", data.damageSteps);
        return tag;
    }

    public static ShadowData readShadow(CompoundTag tag) {
        ShadowData data = new ShadowData();
        data.active = tag.getBoolean("active");
        data.corruption = tag.getDouble("corruption");
        data.peakCorruption = tag.getDouble("peak");
        data.startingCorruption = tag.getDouble("start");
        data.purified = tag.getBoolean("purified");
        data.fromTime = tag.getDouble("fromTime");
        data.fromBattles = tag.getDouble("fromBattles");
        data.fromFriendship = tag.getDouble("fromFriendship");
        data.fromItems = tag.getDouble("fromItems");
        data.fromChamber = tag.getDouble("fromChamber");
        data.damageSteps = tag.getInt("damageSteps");
        return data;
    }

    private static ListTag strings(Collection<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    private static void readStrings(CompoundTag tag, String key, Collection<String> into) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            into.add(list.getString(i));
        }
    }
}
