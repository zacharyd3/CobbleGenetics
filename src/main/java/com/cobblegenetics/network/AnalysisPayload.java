package com.cobblegenetics.network;

import com.cobblegenetics.CobbleGenetics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server -> client: an analysis, already filtered by the player's research level (the client only draws it). Texts
 * are Components so they are translated on the client.
 *
 * @param name         the Pokémon's display name
 * @param level        its level
 * @param subtitle     species · wild/owned
 * @param species      species id, aspects and primary type: for the 3D portrait on its type's platform
 * @param breedingValue the breeding value grade ("B (68)"), coloured
 * @param best         how it compares with the best of its species this player has analysed (or "new best!")
 */
public record AnalysisPayload(UUID pokemon, Component name, int level, Component subtitle, String species,
                              Set<String> aspects, String primaryType, Component breedingValue, Component best,
                              int researchLevel, boolean canRecord, List<Section> sections)
        implements CustomPacketPayload {

    public static final Type<AnalysisPayload> TYPE = new Type<>(CobbleGenetics.id("analysis"));

    /**
     * bar: 0-1 fill, or negative for none. locked: shown as "requires research level N". note: what the value means
     * for this Pokémon, drawn under the row (empty for none).
     */
    public record Row(Component label, Component value, float bar, int color, boolean locked, Component note) {

        public Row withNote(Component note) {
            return new Row(label, value, bar, color, locked, note);
        }
    }

    public record Section(Component title, List<Row> rows) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalysisPayload> CODEC = StreamCodec.of(
            AnalysisPayload::write, AnalysisPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AnalysisPayload payload) {
        buf.writeUUID(payload.pokemon);
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.name);
        buf.writeVarInt(payload.level);
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.subtitle);
        buf.writeUtf(payload.species);
        buf.writeVarInt(payload.aspects.size());
        payload.aspects.forEach(buf::writeUtf);
        buf.writeUtf(payload.primaryType);
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.breedingValue);
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.best);
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
                ComponentSerialization.STREAM_CODEC.encode(buf, row.note());
            }
        }
    }

    private static AnalysisPayload read(RegistryFriendlyByteBuf buf) {
        UUID pokemon = buf.readUUID();
        Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
        int level = buf.readVarInt();
        Component subtitle = ComponentSerialization.STREAM_CODEC.decode(buf);
        String species = buf.readUtf();
        int aspectCount = Math.min(buf.readVarInt(), 256);
        Set<String> aspects = new LinkedHashSet<>();
        for (int i = 0; i < aspectCount; i++) {
            aspects.add(buf.readUtf());
        }
        String primaryType = buf.readUtf();
        Component breedingValue = ComponentSerialization.STREAM_CODEC.decode(buf);
        Component best = ComponentSerialization.STREAM_CODEC.decode(buf);
        int researchLevel = buf.readVarInt();
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
                        buf.readBoolean(), ComponentSerialization.STREAM_CODEC.decode(buf)));
            }
            sections.add(new Section(sectionTitle, rows));
        }
        return new AnalysisPayload(pokemon, name, level, subtitle, species, aspects, primaryType, breedingValue, best,
                researchLevel, canRecord, sections);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
