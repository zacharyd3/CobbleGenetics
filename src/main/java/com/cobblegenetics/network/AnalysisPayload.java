package com.cobblegenetics.network;

import com.cobblegenetics.CobbleGenetics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> client: an analysis, already filtered by the player's research level (the client only draws it). Texts
 * are Components so they are translated on the client.
 */
public record AnalysisPayload(UUID pokemon, Component title, Component subtitle, int researchLevel, boolean canRecord,
                              List<Section> sections) implements CustomPacketPayload {

    public static final Type<AnalysisPayload> TYPE = new Type<>(CobbleGenetics.id("analysis"));

    /** bar: 0-1 fill, or negative for none. locked: shown as "requires research level N". */
    public record Row(Component label, Component value, float bar, int color, boolean locked) {
    }

    public record Section(Component title, List<Row> rows) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalysisPayload> CODEC = StreamCodec.of(
            AnalysisPayload::write, AnalysisPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AnalysisPayload payload) {
        buf.writeUUID(payload.pokemon);
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.title);
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.subtitle);
        buf.writeVarInt(payload.researchLevel);
        buf.writeBoolean(payload.canRecord);
        buf.writeVarInt(payload.sections.size());
        for (Section section : payload.sections) {
            ComponentSerialization.STREAM_CODEC.encode(buf, section.title());
            buf.writeVarInt(section.rows().size());
            for (Row row : section.rows()) {
                ComponentSerialization.STREAM_CODEC.encode(buf, row.label());
                ComponentSerialization.STREAM_CODEC.encode(buf, row.value());
                buf.writeFloat(row.bar());
                buf.writeInt(row.color());
                buf.writeBoolean(row.locked());
            }
        }
    }

    private static AnalysisPayload read(RegistryFriendlyByteBuf buf) {
        UUID pokemon = buf.readUUID();
        Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
        Component subtitle = ComponentSerialization.STREAM_CODEC.decode(buf);
        int level = buf.readVarInt();
        boolean canRecord = buf.readBoolean();
        int sectionCount = Math.min(buf.readVarInt(), 32);
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < sectionCount; i++) {
            Component sectionTitle = ComponentSerialization.STREAM_CODEC.decode(buf);
            int rowCount = Math.min(buf.readVarInt(), 64);
            List<Row> rows = new ArrayList<>();
            for (int j = 0; j < rowCount; j++) {
                rows.add(new Row(ComponentSerialization.STREAM_CODEC.decode(buf),
                        ComponentSerialization.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readInt(),
                        buf.readBoolean()));
            }
            sections.add(new Section(sectionTitle, rows));
        }
        return new AnalysisPayload(pokemon, title, subtitle, level, canRecord, sections);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
