package com.cobblegenetics.network;

import com.cobblegenetics.CobbleGenetics;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client -> server: "Record" button of the Analyzer screen. The server re-checks everything. */
public record RecordRequestPayload(UUID pokemon) implements CustomPacketPayload {

    public static final Type<RecordRequestPayload> TYPE = new Type<>(CobbleGenetics.id("record"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordRequestPayload> CODEC =
            UUIDUtil.STREAM_CODEC.map(RecordRequestPayload::new, RecordRequestPayload::pokemon).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
