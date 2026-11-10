package com.cobblegenetics.breeding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Pokémon Egg item holds: the complete offspring (Cobblemon's own Pokémon NBT, genome included), how many
 * blocks are still to be walked, the total (for the progress hints), the spot colour and the parents' names.
 *
 * <p>The whole record is synced to clients: in creative mode the client sends item stacks back to the server, and an
 * egg synced without its Pokémon would come back empty.</p>
 */
public record EggData(CompoundTag pokemon, int remaining, int total, int color, String parents) {

    public static final Codec<EggData> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            CompoundTag.CODEC.fieldOf("pokemon").forGetter(EggData::pokemon),
            Codec.INT.fieldOf("remaining").forGetter(EggData::remaining),
            Codec.INT.fieldOf("total").forGetter(EggData::total),
            Codec.INT.fieldOf("color").forGetter(EggData::color),
            Codec.STRING.fieldOf("parents").forGetter(EggData::parents)
    ).apply(builder, EggData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, EggData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, EggData::pokemon,
            ByteBufCodecs.VAR_INT, EggData::remaining,
            ByteBufCodecs.VAR_INT, EggData::total,
            ByteBufCodecs.INT, EggData::color,
            ByteBufCodecs.STRING_UTF8, EggData::parents,
            EggData::new);

    public EggData withRemaining(int newRemaining) {
        return new EggData(pokemon, newRemaining, total, color, parents);
    }

    /** 0 (just laid) .. 1 (about to hatch). */
    public float progress() {
        return total <= 0 ? 1 : 1 - Math.max(0, remaining) / (float) total;
    }
}
